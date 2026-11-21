# 이미지 업로드 플로우: 갤러리/카메라 선택, 권한, 크롭, 업로드 진행률

## 목차

1. 개요: 왜 "이미지 업로드"가 복잡한가
2. 구성 요소
3. 가장 기본적인 구현
4. 상태 설계: 진실의 원천은 UploadStage
5. 갤러리 선택: Photo Picker
6. 카메라 촬영: FileProvider + TakePicture
7. 권한 처리: CAMERA와 영구 거부
8. 크롭
9. 압축 / 리사이즈
10. 업로드 진행률: ProgressRequestBody
11. MVI에서의 상태 관리
12. 취소 / 재시도 / 백그라운드 업로드
13. 프로세스 데스와 화면 회전 복원
14. 테스트
15. 흔한 실수
16. 전체 예제 (복붙용)

<br>

# 1. 개요: 왜 "이미지 업로드"가 복잡한가

이미지 업로드는 버튼 하나처럼 보이지만 실제로는 여러 단계가 이어진 파이프라인이다.

```text
선택(갤러리/카메라) -> 권한 -> 크롭 -> 압축 -> 업로드(진행률) -> 결과
```

각 단계마다 비동기 결과(Activity Result)가 돌아오고, 중간에 사용자가 취소하거나 앱이 죽거나 네트워크가 끊길 수 있다. 이걸 `isLoading`, `isCompressing`, `progress`, `error` 같은 플래그로 따로 들고 있으면 "로딩 중인데 에러도 있음" 같은 불가능한 조합이 생긴다.

해결 원칙은 세 가지다.

- 현재 단계는 `UploadStage` 하나로만 표현한다. (sealed interface)
- 갤러리/카메라/권한/크롭 런처는 UI 레이어가 들고 있고, ViewModel은 "런처를 실행해라"는 명령(Effect)만 보낸다.
- 런처의 결과(Uri, 권한 허용 여부)는 다시 Intent로 ViewModel에 돌려준다.

이렇게 하면 흐름이 단방향이 된다.

```text
버튼 클릭 -> Intent -> ViewModel -> Effect(런처 실행) -> 결과 -> Intent -> State 갱신
```

<br>

# 2. 구성 요소

| 구성 요소 | 역할 |
| --- | --- |
| `PickVisualMedia` | 갤러리 선택. 시스템 Photo Picker를 띄우고 권한이 필요 없음 |
| `TakePicture` | 카메라 앱을 호출해서 지정한 Uri에 촬영 결과를 저장 |
| `FileProvider` | 카메라가 쓸 수 있는 `content://` Uri 생성 |
| `RequestPermission` | `CAMERA` 런타임 권한 요청 |
| `CropImageContract` | 크롭 화면 실행 (android-image-cropper) |
| `ImageProcessor` | 리사이즈 + JPEG 압축 (IO 스레드) |
| `ProgressRequestBody` | 업로드 바이트 진행률을 콜백으로 전달하는 OkHttp RequestBody |
| `UploadStage` | 파이프라인의 현재 단계를 나타내는 sealed 상태 |

모든 `rememberLauncherForActivityResult`는 Composable 안에서 만들어야 하므로, 런처 호출은 UI 쪽 책임이다.

<br>

# 3. 가장 기본적인 구현

갤러리에서 한 장 골라 바로 올리는 최소 형태다.

```kotlin
@Composable
fun SimpleImagePicker(onPicked: (Uri) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(onPicked)
    }

    Button(
        onClick = {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    ) {
        Text("사진 선택")
    }
}
```

핵심은 세 줄이다.

1. `PickVisualMedia`: 권한 없이 시스템 피커로 선택한다.
2. 결과는 `Uri?`이고, 취소하면 `null`이다.
3. 받은 Uri는 `content://`라서 파일 경로로 쓰면 안 되고 `contentResolver`로 읽는다.

이후 챕터는 이 기본형에 카메라, 권한, 크롭, 진행률을 붙이면서 생기는 문제를 하나씩 다룬다.

<br>

# 4. 상태 설계: 진실의 원천은 UploadStage

## 4-1. 플래그를 따로 두면 안 되는 이유

```kotlin
// 안티패턴
data class State(
    val isCompressing: Boolean = false,
    val isUploading: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val uploadedUrl: String? = null,
)
```

- `isUploading = true`인데 `error != null`인 상태가 만들어질 수 있다.
- 재시도할 때 어떤 플래그를 초기화해야 하는지 매번 신경 써야 한다.
- UI에서 `if (isUploading) ... else if (error != null) ...` 분기가 늘어난다.

## 4-2. 단계를 sealed로 표현

```kotlin
sealed interface UploadStage {
    data object Idle : UploadStage
    data object Processing : UploadStage                    // 압축 중
    data class Uploading(val progress: Float) : UploadStage // 0f..1f
    data class Success(val url: String) : UploadStage
    data class Failed(val message: String) : UploadStage
}

data class ImageUploadState(
    val sheetVisible: Boolean = false,
    val croppedUri: Uri? = null,
    val stage: UploadStage = UploadStage.Idle,
)
```

한 시점에 하나의 단계만 존재하므로 불가능한 조합이 타입 수준에서 사라지고, UI는 `when (stage)` 하나로 끝난다. `when`이 exhaustive라서 단계를 추가하면 컴파일러가 빠뜨린 분기를 알려준다.

<br>

# 5. 갤러리 선택: Photo Picker

```kotlin
val galleryLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
) { uri -> viewModel.onIntent(ImageUploadIntent.ImageSelected(uri)) }

// 실행
galleryLauncher.launch(
    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
)
```

## 5-1. 왜 Photo Picker인가

| 방식 | 권한 | 비고 |
| --- | --- | --- |
| Photo Picker (`PickVisualMedia`) | 불필요 | 사용자가 고른 항목에만 접근. 권장 방식 |
| `GetContent` / `OpenDocument` | 불필요 | 파일 앱 UI. 갤러리 느낌이 약함 |
| `READ_MEDIA_IMAGES` + 직접 쿼리 | 필요 | 전체 사진 접근이 필요한 앱(갤러리 앱 등)만 |

Photo Picker는 지원 기기에서는 시스템 피커를, 지원하지 않는 기기에서는 자동으로 `ACTION_OPEN_DOCUMENT`로 폴백한다. 프로필 사진처럼 한 장 고르는 용도에서는 권한 선언 자체를 하지 않아도 된다.

## 5-2. 여러 장 선택

```kotlin
val multiLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
) { uris -> /* List<Uri> */ }
```

`maxItems`는 2 이상이어야 하고, 시스템 상한(`MediaStore.getPickImagesMaxLimit()`)을 넘지 않아야 한다.

## 5-3. 선택한 Uri의 수명

피커가 준 Uri는 일시적 접근 권한이 부여된 것이다. 앱이 재시작되면 접근이 사라질 수 있으므로, 나중에 다시 쓸 이미지라면 바로 앱 캐시로 복사하거나(크롭/압축 단계가 이미 그 역할을 한다) 업로드까지 한 번에 진행한다.

<br>

# 6. 카메라 촬영: FileProvider + TakePicture

## 6-1. 매니페스트

```xml
<uses-feature android:name="android.hardware.camera" android:required="false" />

<application>
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.fileprovider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths" />
    </provider>
</application>
```

```xml
<!-- res/xml/file_paths.xml -->
<paths>
    <cache-path name="camera" path="camera/" />
</paths>
```

## 6-2. 촬영 Uri 만들기

```kotlin
fun createCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
```

## 6-3. 런처

```kotlin
var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

val cameraLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.TakePicture()
) { success ->
    if (success) viewModel.onIntent(ImageUploadIntent.ImageSelected(cameraUri))
}

// 실행
cameraUri = createCameraUri(context)
cameraLauncher.launch(cameraUri!!)
```

`TakePicture`는 성공 여부(`Boolean`)만 돌려주고 이미지는 우리가 넘긴 Uri에 저장된다. 그래서 그 Uri를 따로 기억해야 하고, 촬영 중 프로세스가 죽을 수 있으므로 `remember`가 아니라 `rememberSaveable`로 들고 있어야 한다. (13장)

## 6-4. 카메라 앱이 없는 기기

에뮬레이터나 일부 태블릿에서는 카메라 앱이 없어 `ActivityNotFoundException`이 난다. `uses-feature`를 `required="false"`로 두었다면 이 경우를 처리해야 한다.

```kotlin
runCatching { cameraLauncher.launch(uri) }
    .onFailure { viewModel.onIntent(ImageUploadIntent.CameraUnavailable) }
```

<br>

# 7. 권한 처리: CAMERA와 영구 거부

## 7-1. 언제 CAMERA 권한이 필요한가

- 앱이 `AndroidManifest.xml`에 `CAMERA` 권한을 선언했다면, 카메라 앱을 호출(`TakePicture`)할 때도 런타임 권한이 허용되어 있어야 한다. 허용 전에 호출하면 `SecurityException`이 난다.
- 매니페스트에 `CAMERA`를 아예 선언하지 않고 카메라 앱에 위임만 하면 권한이 필요 없다.
- 앱 안에서 CameraX로 직접 프리뷰를 그리는 경우에는 당연히 필요하다.

이 문서는 "다른 라이브러리/모듈이 `CAMERA`를 선언하고 있을 가능성"까지 생각해서 권한 확인 단계를 포함하는 방식으로 정리한다.

## 7-2. 권한 확인 → 요청 → 결과 처리

```kotlin
val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    val activity = context.findActivity()
    val permanentlyDenied = !granted &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)

    viewModel.onIntent(ImageUploadIntent.CameraPermissionResult(granted, permanentlyDenied))
}
```

```kotlin
ImageUploadEffect.CheckCameraPermission -> {
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    if (granted) viewModel.onIntent(ImageUploadIntent.CameraPermissionResult(true, false))
    else permissionLauncher.launch(Manifest.permission.CAMERA)
}
```

## 7-3. 결과별 분기

| 상황 | 처리 |
| --- | --- |
| 허용 | 카메라 실행 |
| 1회 거부 | 안내 메시지 후 종료. 다음 클릭에 다시 요청 가능 |
| 영구 거부 (`shouldShowRationale == false`) | 앱 설정 화면으로 이동 유도 |

영구 거부 판단은 "요청 직후 결과 콜백에서 거부이면서 rationale이 false"일 때만 의미가 있다. 요청 전에 `shouldShowRequestPermissionRationale`을 보면 "아직 한 번도 안 물어봤을 때"도 false라서 구분이 안 된다.

## 7-4. 설정 화면 이동

```kotlin
ImageUploadEffect.OpenAppSettings -> context.startActivity(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
)
```

<br>

# 8. 크롭

선택/촬영이 끝난 Uri를 크롭 화면에 넘기고, 결과 Uri를 업로드 단계로 넘긴다.

```kotlin
val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
    if (result.isSuccessful) {
        result.uriContent?.let { viewModel.onIntent(ImageUploadIntent.ImageCropped(it)) }
    } else {
        viewModel.onIntent(ImageUploadIntent.CropCancelled)
    }
}

// 실행
cropLauncher.launch(
    CropImageContractOptions(
        uri = sourceUri,
        cropImageOptions = CropImageOptions(
            guidelines = CropImageView.Guidelines.ON,
            fixAspectRatio = true,
            aspectRatioX = 1,
            aspectRatioY = 1,
            outputCompressFormat = Bitmap.CompressFormat.JPEG,
            outputCompressQuality = 90,
        ),
    )
)
```

## 8-1. 옵션 선택 기준

| 용도 | 옵션 |
| --- | --- |
| 프로필 사진 | `fixAspectRatio = true`, 1:1 |
| 배너/커버 | `fixAspectRatio = true`, 16:9 등 |
| 자유 크롭 | `fixAspectRatio = false` |
| 원형 미리보기 | `cropShape = CropImageView.CropShape.OVAL` (결과 파일은 여전히 사각형) |

## 8-2. 크롭 결과의 특징

- 결과 Uri(`uriContent`)는 앱 캐시에 저장된 새 파일이라 원본 Uri의 접근 권한 만료 문제에서 벗어난다.
- EXIF 회전이 반영된 상태로 저장되므로 별도 회전 보정이 필요 없다.
- 크롭을 건너뛰는 옵션이 생긴다면(크롭 없이 업로드) 이 두 가지를 직접 처리해야 한다.

<br>

# 9. 압축 / 리사이즈

휴대폰 사진은 수 MB에서 10MB를 넘는다. 그대로 올리면 업로드가 느리고 서버/스토리지 비용이 늘어난다. 장변 기준으로 줄이고 JPEG로 압축한 뒤 올린다.

```kotlin
class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun compress(
        uri: Uri,
        maxSide: Int = 1280,
        quality: Int = 85,
    ): File = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // 1. 크기만 읽기
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        // 2. 큰 이미지는 다운샘플링해서 디코딩 (OOM 방지)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("이미지를 읽을 수 없어요")

        // 3. 장변을 maxSide에 맞춰 정확히 리사이즈
        val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height)
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt(),
                (decoded.height * scale).toInt(),
                true,
            )
        } else decoded

        // 4. JPEG 저장
        val out = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { resized.compress(Bitmap.CompressFormat.JPEG, quality, it) }

        if (resized !== decoded) resized.recycle()
        decoded.recycle()
        out
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        val longSide = maxOf(width, height)
        while (longSide / (sample * 2) >= maxSide) sample *= 2
        return sample
    }
}
```

## 9-1. 파라미터 가이드

| 용도 | maxSide | quality |
| --- | --- | --- |
| 프로필/썸네일 | 512 ~ 1024 | 85 |
| 일반 게시물 사진 | 1280 ~ 1920 | 80 ~ 85 |
| 원본 보관이 중요한 경우 | 압축 없이 원본 업로드 | - |

수치는 서비스 요구에 맞게 조정한다. 압축은 반드시 `Dispatchers.IO`에서 하고, 메인 스레드에서 비트맵을 디코딩하지 않는다.

<br>

# 10. 업로드 진행률: ProgressRequestBody

OkHttp의 `RequestBody.writeTo`를 감싸서 쓴 바이트 수를 콜백으로 전달한다.

## 10-1. RequestBody

```kotlin
class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {

    override fun contentType() = contentType
    override fun contentLength() = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var written = 0L
        val buffer = ByteArray(8 * 1024)

        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                written += read
                onProgress(written.toFloat() / total)
            }
        }
    }
}
```

## 10-2. API

```kotlin
interface ImageApi {
    @Multipart
    @POST("images")
    suspend fun upload(@Part image: MultipartBody.Part): UploadResponse
}

data class UploadResponse(val url: String)
```

## 10-3. Repository: 진행률을 Flow로 방출

```kotlin
sealed interface UploadResult {
    data class Progress(val value: Float) : UploadResult
    data class Done(val url: String) : UploadResult
}

class ImageUploadRepository @Inject constructor(
    private val api: ImageApi,
) {
    fun upload(file: File): Flow<UploadResult> = channelFlow {
        var lastPercent = -1

        val body = ProgressRequestBody(file, "image/jpeg".toMediaType()) { progress ->
            val percent = (progress * 100).toInt()
            if (percent != lastPercent) {        // 1% 단위로만 방출
                lastPercent = percent
                trySend(UploadResult.Progress(progress))
            }
        }
        val part = MultipartBody.Part.createFormData("image", file.name, body)

        val response = api.upload(part)
        send(UploadResult.Done(response.url))
    }.flowOn(Dispatchers.IO)
}
```

## 10-4. 진행률의 한계

`writeTo`에서 센 바이트는 "OkHttp 버퍼에 쓴 양"이라서 실제 네트워크 전송보다 살짝 앞설 수 있다. 특히 작은 파일은 100%가 먼저 찍히고 서버 응답을 기다리는 시간이 생긴다. 그래서 UI에서는 `progress == 1f`가 되어도 `Success`가 오기 전까지는 "마무리 중" 정도로 두는 것이 자연스럽다.

<br>

# 11. MVI에서의 상태 관리

## 11-1. 계약

```kotlin
sealed interface ImageUploadIntent {
    data object ClickAdd : ImageUploadIntent
    data object DismissSheet : ImageUploadIntent
    data object SelectGallery : ImageUploadIntent
    data object SelectCamera : ImageUploadIntent
    data class CameraPermissionResult(val granted: Boolean, val permanentlyDenied: Boolean) : ImageUploadIntent
    data object CameraUnavailable : ImageUploadIntent
    data class ImageSelected(val uri: Uri?) : ImageUploadIntent
    data class ImageCropped(val uri: Uri) : ImageUploadIntent
    data object CropCancelled : ImageUploadIntent
    data object Retry : ImageUploadIntent
    data object Cancel : ImageUploadIntent
}

sealed interface ImageUploadEffect {
    data object LaunchGallery : ImageUploadEffect
    data object CheckCameraPermission : ImageUploadEffect
    data object LaunchCamera : ImageUploadEffect
    data class LaunchCrop(val uri: Uri) : ImageUploadEffect
    data object OpenAppSettings : ImageUploadEffect
    data class ShowMessage(val message: String) : ImageUploadEffect
}
```

## 11-2. 무엇을 어디에 두는가

| 종류 | 위치 | 이유 |
| --- | --- | --- |
| 현재 단계, 진행률, 크롭된 이미지 | `UiState` | 화면이 다시 그려져도 유지되어야 함 |
| 시트 표시 여부 | `UiState` | 선택 UI의 상태 |
| 런처 실행 / 설정 이동 / 스낵바 | `Effect` | 1회성 이벤트. 재구독 시 다시 실행되면 안 됨 |
| 런처 결과(Uri, 권한 결과) | `Intent` | 사용자/시스템이 발생시킨 입력 |
| `cameraUri` (촬영 대상 Uri) | UI의 `rememberSaveable` | 런처와 같은 생명주기, 도메인 상태가 아님 |

## 11-3. ViewModel

```kotlin
@HiltViewModel
class ImageUploadViewModel @Inject constructor(
    private val imageProcessor: ImageProcessor,
    private val repository: ImageUploadRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ImageUploadState())
    val state = _state.asStateFlow()

    private val _effect = Channel<ImageUploadEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var uploadJob: Job? = null

    fun onIntent(intent: ImageUploadIntent) {
        when (intent) {
            ImageUploadIntent.ClickAdd -> _state.update { it.copy(sheetVisible = true) }
            ImageUploadIntent.DismissSheet -> _state.update { it.copy(sheetVisible = false) }

            ImageUploadIntent.SelectGallery -> {
                _state.update { it.copy(sheetVisible = false) }
                send(ImageUploadEffect.LaunchGallery)
            }
            ImageUploadIntent.SelectCamera -> {
                _state.update { it.copy(sheetVisible = false) }
                send(ImageUploadEffect.CheckCameraPermission)
            }

            is ImageUploadIntent.CameraPermissionResult -> when {
                intent.granted -> send(ImageUploadEffect.LaunchCamera)
                intent.permanentlyDenied -> send(ImageUploadEffect.OpenAppSettings)
                else -> send(ImageUploadEffect.ShowMessage("카메라 권한이 필요해요"))
            }
            ImageUploadIntent.CameraUnavailable ->
                send(ImageUploadEffect.ShowMessage("카메라를 사용할 수 없어요"))

            is ImageUploadIntent.ImageSelected ->
                intent.uri?.let { send(ImageUploadEffect.LaunchCrop(it)) }

            is ImageUploadIntent.ImageCropped -> upload(intent.uri)
            ImageUploadIntent.CropCancelled -> Unit

            ImageUploadIntent.Retry -> _state.value.croppedUri?.let(::upload)
            ImageUploadIntent.Cancel -> {
                uploadJob?.cancel()
                _state.update { it.copy(stage = UploadStage.Idle) }
            }
        }
    }

    private fun upload(uri: Uri) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            _state.update { it.copy(croppedUri = uri, stage = UploadStage.Processing) }

            var file: File? = null
            try {
                file = imageProcessor.compress(uri)
                _state.update { it.copy(stage = UploadStage.Uploading(0f)) }

                repository.upload(file).collect { result ->
                    _state.update {
                        when (result) {
                            is UploadResult.Progress -> it.copy(stage = UploadStage.Uploading(result.value))
                            is UploadResult.Done -> it.copy(stage = UploadStage.Success(result.url))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(stage = UploadStage.Failed(e.toUserMessage())) }
            } finally {
                file?.delete()
            }
        }
    }

    private fun send(effect: ImageUploadEffect) {
        viewModelScope.launch { _effect.send(effect) }
    }
}

private fun Throwable.toUserMessage(): String = when (this) {
    is IOException -> "네트워크 연결을 확인해주세요"
    is HttpException -> "서버 오류가 발생했어요 (${code()})"
    else -> "이미지를 업로드하지 못했어요"
}
```

`CancellationException`을 반드시 다시 던져야 한다. `catch (e: Exception)`이 취소까지 삼키면 코루틴 취소가 깨지고, 취소 직후에 `Failed` 상태가 덮어써진다.

<br>

# 12. 취소 / 재시도 / 백그라운드 업로드

## 12-1. 취소

`uploadJob.cancel()`이면 충분하다. suspend Retrofit 호출은 코루틴이 취소되면 내부의 OkHttp `Call`도 함께 취소된다. 취소 후에는 `finally`에서 압축 파일이 정리되고, 상태는 `Cancel` 처리에서 `Idle`로 돌린다.

## 12-2. 재시도

재시도는 크롭된 Uri(`croppedUri`)부터 다시 시작한다. 압축 파일은 실패 시 이미 삭제했으므로 압축부터 다시 한다. 크롭 결과 파일은 캐시에 남아 있어서 사용자가 다시 선택/크롭할 필요가 없다.

## 12-3. 화면을 벗어나도 업로드를 이어가야 한다면

`viewModelScope`는 화면(ViewModel)이 사라지면 취소된다. 아래 경우에는 WorkManager로 옮긴다.

- 사용자가 업로드 중에 화면을 나가도 계속 올려야 할 때
- 이미지 여러 장이나 큰 파일(동영상 포함)을 올릴 때
- 네트워크가 끊기면 복구 후 자동 재시도가 필요할 때

```kotlin
@HiltWorker
class ImageUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ImageUploadRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val path = inputData.getString(KEY_PATH) ?: return Result.failure()
        val file = File(path)

        return try {
            repository.upload(file).collect { result ->
                if (result is UploadResult.Progress) {
                    setProgress(workDataOf(KEY_PROGRESS to result.value))
                }
            }
            file.delete()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.retry()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_PATH = "path"
        const val KEY_PROGRESS = "progress"
    }
}
```

```kotlin
val request = OneTimeWorkRequestBuilder<ImageUploadWorker>()
    .setInputData(workDataOf(ImageUploadWorker.KEY_PATH to file.absolutePath))
    .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
    .build()

WorkManager.getInstance(context).enqueue(request)
```

UI에서는 `WorkManager.getWorkInfoByIdFlow(request.id)`의 `progress`를 읽어 진행률을 그린다. 오래 걸리는 업로드는 `setForeground`로 포그라운드 알림을 띄우는 것을 고려한다.

<br>

# 13. 프로세스 데스와 화면 회전 복원

이미지 선택 플로우는 다른 앱(카메라, 피커)으로 나갔다가 돌아오는 구조라서, 그 사이 메모리 부족으로 프로세스가 죽는 일이 현실적으로 발생한다.

| 값 | 보관 위치 | 이유 |
| --- | --- | --- |
| `cameraUri` | `rememberSaveable` | 촬영 결과가 돌아올 때 어떤 Uri에 저장했는지 알아야 함 |
| `croppedUri`, `stage` | ViewModel `StateFlow` | 회전에는 유지됨. 프로세스 데스까지 복원하려면 `SavedStateHandle` |
| 진행 중인 업로드 | 복원하지 않음 | 프로세스가 죽으면 업로드도 끝난 것. WorkManager를 쓰면 이어짐 |

```kotlin
// 프로세스 데스까지 복원하려면
class ImageUploadViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    ...
) : ViewModel() {
    private val croppedUri: StateFlow<Uri?> = savedStateHandle.getStateFlow("croppedUri", null)
}
```

`rememberSaveable`에 `Uri`는 `Parcelable`이라서 별도의 Saver 없이 저장된다.

<br>

# 14. 테스트

## 14-1. ViewModel: Fake로 단계 전이 검증

Repository와 ImageProcessor를 인터페이스로 분리하면 Fake를 주입할 수 있다.

```kotlin
class FakeUploadRepository(private val results: Flow<UploadResult>) : UploadRepository {
    override fun upload(file: File) = results
}

@Test
fun 업로드가_끝나면_Success가_된다() = runTest {
    val repo = FakeUploadRepository(
        flowOf(UploadResult.Progress(0.5f), UploadResult.Progress(1f), UploadResult.Done("https://img/1.jpg"))
    )
    val viewModel = ImageUploadViewModel(FakeImageProcessor(), repo)

    viewModel.onIntent(ImageUploadIntent.ImageCropped(mockk<Uri>()))

    val stage = viewModel.state.value.stage
    assertTrue(stage is UploadStage.Success)
    assertEquals("https://img/1.jpg", (stage as UploadStage.Success).url)
}

@Test
fun 영구_거부이면_설정_이동_Effect가_발생한다() = runTest {
    val viewModel = ImageUploadViewModel(FakeImageProcessor(), FakeUploadRepository(emptyFlow()))

    viewModel.onIntent(ImageUploadIntent.CameraPermissionResult(granted = false, permanentlyDenied = true))

    assertEquals(ImageUploadEffect.OpenAppSettings, viewModel.effect.first())
}
```

`viewModelScope`를 쓰므로 `Dispatchers.setMain(UnconfinedTestDispatcher())`를 테스트 규칙으로 설정한다. `Uri`는 안드로이드 프레임워크 클래스라 단위 테스트에서는 mockk로 대체하거나 Robolectric을 쓴다.

## 14-2. Repository: MockWebServer로 진행률 검증

`MockWebServer`로 응답을 내려주고 `upload(file)`을 `toList()` 했을 때 `Progress`가 단조 증가하고 마지막이 `Done`인지 확인한다. 진행률은 1%마다 방출하므로 단언은 "개수"가 아니라 "증가 순서와 마지막 값" 중심으로 한다.

## 14-3. UI: 런처를 직접 띄우지 않고 검증

Photo Picker나 카메라는 시스템 화면이라 계측 테스트에서 직접 띄우기 어렵다. `ActivityResultRegistry`를 테스트용으로 교체해 결과를 바로 돌려주거나, UI 테스트는 "버튼 클릭 → 시트 표시 → 선택 항목 노출"까지만 검증하고 런처 이후는 ViewModel 테스트에 맡긴다.

<br>

# 15. 흔한 실수

## 15-1. 플래그 여러 개로 상태 관리

`isLoading`, `isUploading`, `error`를 따로 두면 불가능한 조합이 생긴다. `UploadStage` 하나로 표현한다.

## 15-2. content:// Uri를 File 경로로 취급

`uri.path`로 `File`을 만들면 대부분 실패한다. `contentResolver.openInputStream(uri)`로 읽거나, 크롭/압축 단계에서 앱 캐시 파일로 만든 뒤 그 파일을 쓴다.

## 15-3. cameraUri를 remember로 보관

촬영하는 동안 프로세스가 죽으면 결과가 돌아와도 어디에 저장했는지 알 수 없다. `rememberSaveable`을 쓴다.

## 15-4. 권한 영구 거부를 구분하지 않음

거부할 때마다 같은 토스트만 띄우면 사용자가 막다른 길에 갇힌다. 영구 거부는 설정 이동으로 유도한다.

## 15-5. 원본 이미지를 그대로 업로드

12MP 사진은 5~10MB다. 업로드 시간과 비용이 모두 늘어난다. 장변 1280~1920 정도로 줄이고 JPEG 80~85로 압축한다.

## 15-6. 메인 스레드에서 비트맵 디코딩

ANR의 원인이 된다. 디코딩, 리사이즈, 압축은 모두 `Dispatchers.IO`에서 한다.

## 15-7. 로깅 인터셉터가 BODY 레벨이면 진행률이 두 번 올라감

`HttpLoggingInterceptor(Level.BODY)`는 바디를 로그로 남기려고 `writeTo`를 한 번 더 호출한다. 그래서 진행률이 0→100%를 두 번 반복한다. 업로드 API에는 로깅 레벨을 `HEADERS` 이하로 두거나 멀티파트 요청은 제외한다. 연결 실패 후 OkHttp가 자동 재시도할 때도 `writeTo`가 다시 호출되어 진행률이 되돌아갈 수 있다.

## 15-8. 진행률을 매 호출마다 State에 반영

8KB 버퍼 단위로 콜백이 오면 수천 번 State가 갱신되어 리컴포지션이 낭비된다. 10장처럼 1% 단위로 줄여서 방출한다.

## 15-9. CancellationException을 삼킴

`catch (e: Exception)`만 두면 취소가 에러로 처리된다. 먼저 `CancellationException`을 다시 던진다.

## 15-10. 캐시 파일 정리 누락

촬영 원본(`cache/camera`), 크롭 결과, 압축 파일이 계속 쌓인다. 업로드 후 압축 파일은 `finally`에서 삭제하고, 촬영/크롭 캐시는 앱 시작 시 오래된 파일을 정리한다.

<br>

# 16. 전체 예제 (복붙용)

## 16-1. Contract

```kotlin
sealed interface UploadStage {
    data object Idle : UploadStage
    data object Processing : UploadStage
    data class Uploading(val progress: Float) : UploadStage
    data class Success(val url: String) : UploadStage
    data class Failed(val message: String) : UploadStage
}

data class ImageUploadState(
    val sheetVisible: Boolean = false,
    val croppedUri: Uri? = null,
    val stage: UploadStage = UploadStage.Idle,
)

sealed interface ImageUploadIntent {
    data object ClickAdd : ImageUploadIntent
    data object DismissSheet : ImageUploadIntent
    data object SelectGallery : ImageUploadIntent
    data object SelectCamera : ImageUploadIntent
    data class CameraPermissionResult(val granted: Boolean, val permanentlyDenied: Boolean) : ImageUploadIntent
    data object CameraUnavailable : ImageUploadIntent
    data class ImageSelected(val uri: Uri?) : ImageUploadIntent
    data class ImageCropped(val uri: Uri) : ImageUploadIntent
    data object CropCancelled : ImageUploadIntent
    data object Retry : ImageUploadIntent
    data object Cancel : ImageUploadIntent
}

sealed interface ImageUploadEffect {
    data object LaunchGallery : ImageUploadEffect
    data object CheckCameraPermission : ImageUploadEffect
    data object LaunchCamera : ImageUploadEffect
    data class LaunchCrop(val uri: Uri) : ImageUploadEffect
    data object OpenAppSettings : ImageUploadEffect
    data class ShowMessage(val message: String) : ImageUploadEffect
}
```

## 16-2. ViewModel / ImageProcessor / ProgressRequestBody / Repository

위 11-3(ViewModel), 9장(ImageProcessor), 10장(ProgressRequestBody, ImageApi, Repository) 코드를 그대로 사용한다.

## 16-3. 의존성

```kotlin
// build.gradle.kts (app)
implementation("com.vanniktech:android-image-cropper:<최신 버전>")
implementation("com.squareup.retrofit2:retrofit:<최신 버전>")
implementation("com.squareup.okhttp3:okhttp:<최신 버전>")
implementation("io.coil-kt.coil3:coil-compose:<최신 버전>")
// 백그라운드 업로드가 필요하면
implementation("androidx.work:work-runtime-ktx:<최신 버전>")
implementation("androidx.hilt:hilt-work:<최신 버전>")
```

## 16-4. 화면

```kotlin
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageUploadScreen(
    modifier: Modifier = Modifier,
    viewModel: ImageUploadViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 촬영 결과가 저장될 Uri (프로세스 데스 대비)
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.onIntent(ImageUploadIntent.ImageSelected(uri)) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) viewModel.onIntent(ImageUploadIntent.ImageSelected(cameraUri))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val permanentlyDenied = !granted &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                context.findActivity(), Manifest.permission.CAMERA
            )
        viewModel.onIntent(ImageUploadIntent.CameraPermissionResult(granted, permanentlyDenied))
    }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        val uri = result.uriContent
        if (result.isSuccessful && uri != null) {
            viewModel.onIntent(ImageUploadIntent.ImageCropped(uri))
        } else {
            viewModel.onIntent(ImageUploadIntent.CropCancelled)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ImageUploadEffect.LaunchGallery -> galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )

                ImageUploadEffect.CheckCameraPermission -> {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    if (granted) {
                        viewModel.onIntent(ImageUploadIntent.CameraPermissionResult(true, false))
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                ImageUploadEffect.LaunchCamera -> {
                    val uri = createCameraUri(context)
                    cameraUri = uri
                    runCatching { cameraLauncher.launch(uri) }
                        .onFailure { viewModel.onIntent(ImageUploadIntent.CameraUnavailable) }
                }

                is ImageUploadEffect.LaunchCrop -> cropLauncher.launch(
                    CropImageContractOptions(
                        uri = effect.uri,
                        cropImageOptions = CropImageOptions(
                            guidelines = CropImageView.Guidelines.ON,
                            fixAspectRatio = true,
                            aspectRatioX = 1,
                            aspectRatioY = 1,
                            outputCompressFormat = Bitmap.CompressFormat.JPEG,
                            outputCompressQuality = 90,
                        ),
                    )
                )

                ImageUploadEffect.OpenAppSettings -> context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )

                is ImageUploadEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ImagePreview(
                uri = state.croppedUri,
                enabled = state.stage !is UploadStage.Processing && state.stage !is UploadStage.Uploading,
                onClick = { viewModel.onIntent(ImageUploadIntent.ClickAdd) },
            )

            when (val stage = state.stage) {
                UploadStage.Idle -> Unit

                UploadStage.Processing -> Text("이미지 최적화 중…")

                is UploadStage.Uploading -> {
                    LinearProgressIndicator(
                        progress = { stage.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${(stage.progress * 100).toInt()}%")
                    TextButton(onClick = { viewModel.onIntent(ImageUploadIntent.Cancel) }) {
                        Text("취소")
                    }
                }

                is UploadStage.Success -> Text("업로드 완료")

                is UploadStage.Failed -> {
                    Text(stage.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.onIntent(ImageUploadIntent.Retry) }) {
                        Text("다시 시도")
                    }
                }
            }
        }
    }

    if (state.sheetVisible) {
        ModalBottomSheet(onDismissRequest = { viewModel.onIntent(ImageUploadIntent.DismissSheet) }) {
            ListItem(
                headlineContent = { Text("갤러리에서 선택") },
                modifier = Modifier.clickable { viewModel.onIntent(ImageUploadIntent.SelectGallery) },
            )
            ListItem(
                headlineContent = { Text("카메라로 촬영") },
                modifier = Modifier.clickable { viewModel.onIntent(ImageUploadIntent.SelectCamera) },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ImagePreview(
    uri: Uri?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = "선택한 이미지",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("+ 사진 추가")
        }
    }
}

private fun createCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Activity를 찾을 수 없어요")
}
```

<br>

# 정리

1. 이미지 업로드는 선택, 권한, 크롭, 압축, 업로드가 이어진 파이프라인이다. 상태는 `UploadStage` sealed 하나로 표현해서 불가능한 조합을 없앤다.
2. 런처(갤러리/카메라/권한/크롭)는 Composable이 들고 있고, ViewModel은 Effect로 "실행해라"를 보내고 결과는 Intent로 돌려받는다. 흐름이 단방향이 된다.
3. 갤러리는 `PickVisualMedia`(Photo Picker)를 쓰면 권한이 필요 없다. 카메라는 `TakePicture` + `FileProvider`를 쓰고, `CAMERA`가 선언되어 있으면 런타임 권한을 확인한다.
4. 권한은 "요청 직후 거부이면서 rationale이 false"일 때만 영구 거부다. 영구 거부는 앱 설정 화면으로 이동시킨다.
5. 크롭 결과는 앱 캐시의 새 파일이라 원본 Uri의 접근 만료와 EXIF 회전 문제에서 벗어난다. 이후 장변 1280~1920, JPEG 80~85로 압축해서 올린다.
6. 진행률은 `ProgressRequestBody.writeTo`에서 센 바이트로 만들고, 1% 단위로만 Flow에 방출한다. 로깅 인터셉터가 BODY 레벨이면 진행률이 두 번 올라가니 주의한다.
7. 취소는 `Job.cancel()`로, 재시도는 크롭된 Uri부터 다시 시작한다. 화면을 나가도 이어가야 하면 WorkManager로 옮긴다.
8. 흔한 실수는 플래그 여러 개로 상태 관리, `content://`를 파일 경로로 취급, `cameraUri`를 `remember`로 보관, 영구 거부 미처리, `CancellationException` 삼키기, 캐시 파일 정리 누락이다.
