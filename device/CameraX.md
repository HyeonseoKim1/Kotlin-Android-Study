# CameraX

<br>

## 목차

1. CameraX란 무엇인가
2. CameraX 아키텍처 개요
3. 기본 설정과 권한 처리
4. Preview로 카메라 미리보기 표시하기
5. ImageCapture로 사진 촬영하기
6. ImageAnalysis로 실시간 프레임 분석하기
7. CameraSelector와 전/후면 카메라 전환
8. Compose에서 CameraX 사용하기
9. VideoCapture로 동영상 녹화하기
10. 줌, 포커스, 플래시 제어하기
11. 생명주기 바인딩과 리소스 해제
12. ML Kit과 연동해 실시간 인식하기
13. 테스트와 에뮬레이터 이슈
14. 주의사항과 자주 하는 실수
15. 정리

<br>

## CameraX란 무엇인가

CameraX는 안드로이드의 카메라 기능을 다루기 위한 Jetpack 라이브러리다. 기존의 `Camera2` API는 기능이 강력한 만큼 상태 머신이 복잡하고, 제조사별 기기 파편화 문제(같은 코드가 기기마다 다르게 동작하는 것)를 개발자가 직접 처리해야 했다. CameraX는 `Camera2` 위에 얹힌 추상화 계층으로, 이런 파편화 문제를 라이브러리 내부에서 흡수하면서도 일관된 API를 제공한다.

CameraX의 핵심 개념은 "UseCase"다. 미리보기(Preview), 사진 촬영(ImageCapture), 실시간 프레임 분석(ImageAnalysis), 동영상 녹화(VideoCapture)라는 네 가지 UseCase를 필요한 만큼 조합해서 카메라 생명주기에 바인딩하는 방식으로 동작한다.

```kotlin
// build.gradle.kts (Module: app)
dependencies {
    val camerax_version = "1.4.0"

    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
    implementation("androidx.camera:camera-video:$camerax_version")
}
```

| 항목 | Camera2 (저수준 API) | CameraX |
|---|---|---|
| 추상화 수준 | 낮음, 상태 머신 직접 관리 | 높음, UseCase 단위로 선언적 사용 |
| 기기 파편화 대응 | 개발자가 직접 예외 처리 | 라이브러리가 내부적으로 흡수 |
| 생명주기 연동 | 수동으로 열고 닫아야 함 | `LifecycleOwner`에 자동 바인딩 |
| 코드량 | 많음 | 상대적으로 적음 |

**실전 팁**: CameraX는 내부적으로 Camera2를 사용하기 때문에, 아주 특수한 저수준 제어(RAW 센서 데이터 직접 접근 등)가 필요한 경우가 아니라면 대부분의 카메라 기능은 CameraX만으로 충분히 구현할 수 있다.

<br>

## CameraX 아키텍처 개요

CameraX는 `ProcessCameraProvider`를 통해 카메라 하드웨어에 접근하고, 여기에 하나 이상의 `UseCase`를 바인딩하는 구조로 동작한다. 각 UseCase는 독립적인 역할을 담당하며, 필요한 조합만 골라 동시에 사용할 수 있다.

```kotlin
// 전체 아키텍처를 요약한 개념적 흐름
val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

cameraProviderFuture.addListener({
    val cameraProvider = cameraProviderFuture.get()

    val preview = Preview.Builder().build()
    val imageCapture = ImageCapture.Builder().build()
    val imageAnalysis = ImageAnalysis.Builder().build()

    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    cameraProvider.unbindAll()
    cameraProvider.bindToLifecycle(
        lifecycleOwner,
        cameraSelector,
        preview,
        imageCapture,
        imageAnalysis
    )
}, ContextCompat.getMainExecutor(context))
```

| UseCase | 역할 |
|---|---|
| `Preview` | 카메라 화면을 실시간으로 보여줌 |
| `ImageCapture` | 정지 사진 촬영 |
| `ImageAnalysis` | 실시간 프레임에 접근해 분석 (바코드 인식, ML 등) |
| `VideoCapture` | 동영상 녹화 |

**실전 팁**: 한 번에 바인딩할 수 있는 UseCase 조합에는 기기 하드웨어 성능에 따른 제약이 있다. 예를 들어 `ImageCapture`와 `VideoCapture`를 동시에 고해상도로 바인딩하면 일부 저사양 기기에서 실패할 수 있으므로, 실제 타겟 기기에서 조합을 테스트해보는 것이 중요하다.

<br>

## 기본 설정과 권한 처리

카메라를 사용하려면 매니페스트에 카메라 권한을 선언하고, 런타임에 사용자로부터 권한을 승인받아야 한다. Android 6.0(API 23) 이상에서는 위험 권한(dangerous permission)이므로 런타임 권한 요청이 필수다.

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera.any" android:required="true" />
```

```kotlin
class CameraActivity : AppCompatActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            }
        }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> startCamera()

            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}
```

| 단계 | 설명 |
|---|---|
| 매니페스트 선언 | `<uses-permission>`으로 카메라 권한 명시 |
| 런타임 권한 확인 | `ContextCompat.checkSelfPermission()`으로 승인 여부 확인 |
| 권한 요청 | `ActivityResultContracts.RequestPermission()`으로 요청 후 콜백 처리 |

**실전 팁**: `uses-feature`에 `android:required="true"`를 지정하면 카메라가 없는 기기(일부 태블릿 등)에서는 Play Store에 앱 자체가 노출되지 않는다. 카메라가 필수 기능이 아니라면 `required="false"`로 지정하고, 앱 내부에서 카메라 하드웨어 존재 여부를 별도로 체크하는 방식을 고려해야 한다.

<br>

## Preview로 카메라 미리보기 표시하기

`Preview` UseCase는 카메라로 들어오는 영상을 화면에 실시간으로 보여주는 역할을 한다. `PreviewView`라는 전용 View에 `SurfaceProvider`를 연결하는 방식으로 동작한다.

```kotlin
class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch (e: Exception) {
                Log.e("CameraX", "카메라 바인딩 실패", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }
}
```

```xml
<!-- activity_camera.xml -->
<androidx.camera.view.PreviewView
    android:id="@+id/previewView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

| 구성 요소 | 역할 |
|---|---|
| `PreviewView` | 카메라 영상을 화면에 렌더링하는 전용 View |
| `Preview.Builder()` | Preview UseCase 생성 |
| `setSurfaceProvider()` | `PreviewView`와 `Preview` UseCase를 연결 |

**실전 팁**: `cameraProvider.unbindAll()`을 바인딩 직전에 호출하는 습관을 들이면, 이전에 바인딩된 UseCase가 남아있어 발생하는 "이미 바인딩된 카메라" 관련 오류를 예방할 수 있다.

<br>

## ImageCapture로 사진 촬영하기

`ImageCapture` UseCase는 정지 사진을 파일로 저장하는 기능을 담당한다. `takePicture()` 메서드에 저장 옵션과 콜백을 전달하면, 촬영이 완료되거나 실패했을 때 결과를 알려준다.

```kotlin
private var imageCapture: ImageCapture? = null

private fun takePhoto() {
    val imageCapture = imageCapture ?: return

    val photoFile = File(
        outputDirectory,
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.KOREA)
            .format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(this),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Log.d("CameraX", "사진 저장 완료: ${photoFile.absolutePath}")
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraX", "사진 촬영 실패", exception)
            }
        }
    )
}
```

| API | 설명 |
|---|---|
| `ImageCapture.Builder()` | 해상도, 플래시 모드 등 촬영 옵션 설정 |
| `takePicture()` | 실제 촬영 실행, 파일 저장 방식 또는 메모리 버퍼 방식 선택 가능 |
| `OnImageSavedCallback` | 촬영 성공/실패 결과를 받는 콜백 |

**실전 팁**: `ImageCapture.OutputFileOptions`는 파일 경로 대신 `MediaStore` 기반 옵션도 지원한다. Android 10(API 29) 이상의 스코프드 스토리지(Scoped Storage) 정책 하에서는, 갤러리 앱에서 사진이 바로 보이게 하려면 앱 전용 디렉터리보다 `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`를 사용하는 방식이 더 적합하다.

<br>

## ImageAnalysis로 실시간 프레임 분석하기

`ImageAnalysis` UseCase는 카메라로 들어오는 매 프레임을 코드에서 직접 받아 처리할 수 있게 해준다. 바코드 스캔, 얼굴 인식, 실시간 필터 적용 같은 기능을 구현할 때 사용한다.

```kotlin
private val imageAnalysis = ImageAnalysis.Builder()
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build()
    .also {
        it.setAnalyzer(cameraExecutor) { imageProxy ->
            analyzeFrame(imageProxy)
        }
    }

private fun analyzeFrame(imageProxy: ImageProxy) {
    try {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        // 실제 분석 로직 (예: 밝기 계산, ML Kit 전달 등)
        val luminance = calculateAverageLuminance(imageProxy)
        Log.d("CameraX", "평균 밝기: $luminance, 회전각: $rotationDegrees")
    } finally {
        imageProxy.close() // 반드시 닫아야 다음 프레임이 전달됨
    }
}

private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
```

| 설정 항목 | 설명 |
|---|---|
| `STRATEGY_KEEP_ONLY_LATEST` | 분석이 느릴 때 오래된 프레임은 버리고 최신 프레임만 유지 |
| `STRATEGY_BLOCK_PRODUCER` | 모든 프레임을 순서대로 처리 (분석이 느리면 카메라 자체가 지연됨) |
| `setAnalyzer(executor, analyzer)` | 프레임이 도착할 때마다 호출될 콜백과 실행 스레드 지정 |

**실전 팁**: `imageProxy.close()`를 호출하지 않으면 다음 프레임이 전달되지 않아 프리뷰가 멈춘 것처럼 보이는 문제가 발생한다. `try-finally`로 반드시 닫아주는 패턴을 습관화해야 한다.

<br>

## CameraSelector와 전/후면 카메라 전환

`CameraSelector`는 후면/전면 카메라 중 어느 렌즈를 사용할지 지정하는 역할을 한다. 사용자가 버튼을 눌러 카메라를 전환하는 기능은 `CameraSelector`를 바꿔 다시 바인딩하는 방식으로 구현한다.

```kotlin
private var lensFacing = CameraSelector.LENS_FACING_BACK

private fun toggleCamera() {
    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
        CameraSelector.LENS_FACING_FRONT
    } else {
        CameraSelector.LENS_FACING_BACK
    }
    bindCameraUseCases()
}

private fun bindCameraUseCases() {
    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(lensFacing)
        .build()

    cameraProvider.unbindAll()
    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
}
```

| CameraSelector 상수 | 의미 |
|---|---|
| `DEFAULT_BACK_CAMERA` | 기본 후면 카메라 |
| `DEFAULT_FRONT_CAMERA` | 기본 전면 카메라 |
| `LENS_FACING_BACK` / `LENS_FACING_FRONT` | `Builder`와 조합해 세밀한 조건(줌 범위 등)까지 지정할 때 사용 |

**실전 팁**: 카메라를 전환할 때마다 `unbindAll()` 후 재바인딩하는 것이 안전하다. 기존 UseCase를 유지한 채 `CameraSelector`만 바꾸려고 시도하면 일부 기기에서 예외가 발생할 수 있다.

<br>

## Compose에서 CameraX 사용하기

Compose는 아직 CameraX 전용 컴포저블을 공식 제공하지 않으므로, `AndroidView`로 `PreviewView`를 감싸서 사용하는 것이 일반적인 패턴이다.

```kotlin
@Composable
fun CameraPreviewScreen(
    modifier: Modifier = Modifier,
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageCapture = ImageCapture.Builder().build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
                onImageCaptureReady(imageCapture)
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}
```

| 방식 | 설명 |
|---|---|
| `AndroidView` | View 시스템의 `PreviewView`를 Compose 트리 안에 임베딩 |
| `factory` 람다 | View 생성과 카메라 바인딩 로직을 한 번만 실행 |
| `update` 람다 (필요시) | Compose 상태 변화에 따라 View 속성을 갱신할 때 사용 |

**실전 팁**: `bindToLifecycle`에 `LocalLifecycleOwner.current`를 넘겨주면, 화면이 Compose 트리에서 사라질 때(예: 다른 화면으로 이동) 자동으로 카메라 리소스가 해제된다. 별도로 `onDispose`에서 수동 해제 코드를 작성하지 않아도 되는 경우가 많다.

<br>

## VideoCapture로 동영상 녹화하기

`VideoCapture` UseCase는 `Recorder`와 조합해서 동영상 녹화를 처리한다. 녹화 시작/중지는 `PendingRecording`을 통해 이루어지며, 녹화 상태 변화는 콜백으로 전달된다.

```kotlin
private var recording: Recording? = null

private fun setupVideoCapture(): VideoCapture<Recorder> {
    val recorder = Recorder.Builder()
        .setQualitySelector(QualitySelector.from(Quality.HD))
        .build()
    return VideoCapture.withOutput(recorder)
}

private fun startRecording(videoCapture: VideoCapture<Recorder>) {
    val videoFile = File(outputDirectory, "video_${System.currentTimeMillis()}.mp4")
    val outputOptions = FileOutputOptions.Builder(videoFile).build()

    recording = videoCapture.output
        .prepareRecording(this, outputOptions)
        .apply {
            if (ContextCompat.checkSelfPermission(this@CameraActivity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                withAudioEnabled()
            }
        }
        .start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Finalize -> {
                    Log.d("CameraX", "녹화 완료: ${event.outputResults.outputUri}")
                }
            }
        }
}

private fun stopRecording() {
    recording?.stop()
    recording = null
}
```

| 구성 요소 | 역할 |
|---|---|
| `Recorder` | 실제 인코딩 품질, 해상도 등을 설정하는 객체 |
| `VideoCapture.withOutput(recorder)` | Recorder를 UseCase로 감쌈 |
| `prepareRecording()` / `start()` | 녹화 준비 및 시작 |
| `VideoRecordEvent` | 녹화 진행 상태(시작, 일시정지, 완료 등) 콜백 |

**실전 팁**: 오디오를 함께 녹화하려면 `RECORD_AUDIO` 권한이 별도로 필요하다. 권한이 없는 상태에서 `withAudioEnabled()`를 호출하면 예외가 발생하므로, 반드시 권한 체크 후 조건부로 호출해야 한다.

<br>

## 줌, 포커스, 플래시 제어하기

`Camera` 객체(바인딩 결과로 반환됨)의 `cameraControl`을 통해 줌, 포커스, 플래시 같은 실시간 제어가 가능하다.

```kotlin
private var camera: Camera? = null

private fun bindCamera() {
    camera = cameraProvider.bindToLifecycle(
        this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
    )
}

// 줌 제어 (0.0 ~ 1.0 선형 줌)
fun setZoom(linearZoom: Float) {
    camera?.cameraControl?.setLinearZoom(linearZoom)
}

// 특정 지점 탭 포커스
fun focusOnPoint(x: Float, y: Float, previewView: PreviewView) {
    val factory = previewView.meteringPointFactory
    val point = factory.createPoint(x, y)
    val action = FocusMeteringAction.Builder(point).build()
    camera?.cameraControl?.startFocusAndMetering(action)
}

// 플래시(손전등) 토글
fun toggleTorch(enabled: Boolean) {
    camera?.cameraControl?.enableTorch(enabled)
}
```

| 제어 항목 | API |
|---|---|
| 줌 | `setLinearZoom(0f~1f)` 또는 `setZoomRatio(배율)` |
| 포커스 | `FocusMeteringAction` + `startFocusAndMetering()` |
| 플래시(손전등) | `enableTorch(Boolean)` |
| 촬영 시 플래시 | `ImageCapture.Builder().setFlashMode(FLASH_MODE_AUTO)` |

**실전 팁**: 손전등(`enableTorch`)과 사진 촬영 시 플래시(`setFlashMode`)는 서로 다른 API다. 전자는 지속적으로 켜두는 조명이고, 후자는 촬영 순간에만 터지는 플래시라는 점을 구분해야 한다.

<br>

## 생명주기 바인딩과 리소스 해제

CameraX의 가장 큰 장점 중 하나는 `bindToLifecycle()`을 통해 카메라 리소스 해제를 `LifecycleOwner`에 자동으로 위임할 수 있다는 점이다. Activity/Fragment가 `DESTROYED` 상태가 되면 카메라도 자동으로 닫힌다.

```kotlin
class CameraFragment : Fragment() {

    private lateinit var cameraExecutor: ExecutorService

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera() // viewLifecycleOwner에 바인딩
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            // viewLifecycleOwner 사용 (Fragment의 생명주기가 아님에 주의)
            cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown() // 수동으로 만든 Executor는 직접 정리 필요
    }
}
```

| 자동 관리되는 리소스 | 수동으로 관리해야 하는 리소스 |
|---|---|
| 카메라 세션 (bindToLifecycle 대상) | 직접 생성한 `ExecutorService` |
| Preview/ImageCapture 등 UseCase 자체 | 진행 중인 `Recording` 객체 |

**실전 팁**: Fragment에서는 `this`(Fragment 자체)가 아니라 `viewLifecycleOwner`를 `bindToLifecycle()`에 넘겨야 한다. Fragment의 뷰가 파괴되었다가 다시 생성되는 상황(백스택 등)에서 `this`를 쓰면 이미 파괴된 뷰에 대한 참조로 인해 크래시가 발생할 수 있다.

<br>

## ML Kit과 연동해 실시간 인식하기

`ImageAnalysis`의 프레임을 Google의 ML Kit에 전달하면 바코드 인식, 얼굴 인식, 텍스트 인식(OCR) 같은 기능을 실시간으로 구현할 수 있다.

```kotlin
dependencies {
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
}

private val barcodeScanner = BarcodeScanning.getClient()

private val imageAnalysis = ImageAnalysis.Builder()
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build()
    .also {
        it.setAnalyzer(cameraExecutor) { imageProxy ->
            processImageForBarcode(imageProxy)
        }
    }

@androidx.camera.core.ExperimentalGetImage
private fun processImageForBarcode(imageProxy: ImageProxy) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(
            mediaImage, imageProxy.imageInfo.rotationDegrees
        )
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let { value ->
                    Log.d("CameraX", "인식된 바코드: $value")
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}
```

| 인식 종류 | ML Kit 라이브러리 |
|---|---|
| 바코드/QR코드 | `barcode-scanning` |
| 얼굴 인식 | `face-detection` |
| 텍스트(OCR) | `text-recognition` |

**실전 팁**: ML Kit 처리는 비동기(`addOnSuccessListener`)로 이루어지므로 `imageProxy.close()`를 `addOnCompleteListener`(성공/실패 모두 처리)에서 호출해야 한다. `process()` 호출 직후 바로 닫아버리면 아직 분석 중인 이미지가 조기에 해제되어 오류가 발생할 수 있다.

<br>

## 테스트와 에뮬레이터 이슈

카메라 하드웨어에 의존하는 코드는 일반적인 단위 테스트로 검증하기 어렵다. 대부분 실제 기기에서의 계측 테스트(Instrumented Test)나 수동 QA에 의존하게 되며, 에뮬레이터에서는 몇 가지 제약을 미리 알아두는 것이 좋다.

```kotlin
// UseCase 바인딩 로직 자체는 실제 카메라 없이도 단위 테스트하기 어려운 영역이다.
// 대신 UseCase를 감싸는 상위 로직(파일명 생성, 상태 관리 등)을 분리해서 테스트한다.
class PhotoFileNamerTest {
    @Test
    fun `파일명은 지정된 형식을 따른다`() {
        val namer = PhotoFileNamer(clock = fixedClock)
        val fileName = namer.generate()
        assertTrue(fileName.matches(Regex("\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}\\.jpg")))
    }
}
```

| 항목 | 설명 |
|---|---|
| 에뮬레이터 카메라 | 가상 카메라(체크무늬 패턴 등)만 제공, 실제 렌즈 동작(줌/포커스)은 검증 불가 |
| 계측 테스트 | 실제 기기 또는 실제 카메라를 지원하는 환경에서만 의미 있는 검증 가능 |
| 단위 테스트 가능 영역 | 파일명 생성, 상태 관리, ViewModel 로직 등 카메라 자체와 분리된 부분 |

**실전 팁**: 카메라 관련 로직을 작성할 때부터 "UseCase 바인딩/카메라 제어"와 "파일 저장 규칙, UI 상태 관리" 부분을 분리해서 설계해두면, 후자만이라도 단위 테스트로 커버할 수 있어 전체 코드의 신뢰도를 높일 수 있다.

<br>

## 주의사항과 자주 하는 실수

1. `imageProxy.close()`를 호출하지 않아 `ImageAnalysis`의 다음 프레임이 전달되지 않고 프리뷰가 멈춘 것처럼 보이는 문제가 흔하게 발생한다.
2. Fragment에서 `bindToLifecycle()`에 `viewLifecycleOwner` 대신 Fragment 자신을 넘겨서, 뷰가 재생성되는 상황(백스택 복귀 등)에서 크래시가 발생하는 경우가 있다.
3. 카메라 전환이나 재바인딩 시 `unbindAll()`을 생략하면 "이미 바인딩된 카메라" 관련 오류나 예상치 못한 UseCase 조합 문제가 발생할 수 있다.
4. `RECORD_AUDIO` 권한 없이 `withAudioEnabled()`를 호출해 동영상 녹화 시작 시 예외가 발생하는 경우가 있다.
5. 손전등 제어(`enableTorch`)와 촬영 시 플래시(`setFlashMode`)를 혼동해서, 의도한 것과 다른 동작을 구현하는 경우가 있다.
6. Android 10 이상의 스코프드 스토리지를 고려하지 않고 앱 전용 디렉터리에만 사진을 저장해, 갤러리 앱에서 사진이 보이지 않는다는 문의를 받는 경우가 있다.
7. 여러 UseCase(특히 `ImageCapture`와 `VideoCapture`)를 동시에 고해상도로 바인딩했을 때 일부 저사양 기기에서 실패하는 것을 실제 기기 테스트 없이 놓치는 경우가 있다.
8. ML Kit 등 비동기 분석 처리 중 `imageProxy.close()` 호출 시점을 잘못 잡아, 분석이 끝나기 전에 이미지를 닫아버리는 실수를 한다.
9. 에뮬레이터에서만 테스트하고 실제 기기 검증을 생략해, 줌/포커스/플래시처럼 에뮬레이터에서 제대로 재현되지 않는 기능의 버그를 배포 이후에 발견하는 경우가 있다.
10. `ImageAnalysis`의 백프레셔 전략을 기본값 그대로 사용하다가, 분석 로직이 무거운 경우 `STRATEGY_BLOCK_PRODUCER`로 인해 카메라 프리뷰 자체가 버벅이는 것을 뒤늦게 알아채는 경우가 있다.

<br>

## 정리

CameraX는 Camera2 위에 놓인 추상화 계층으로, `Preview`, `ImageCapture`, `ImageAnalysis`, `VideoCapture`라는 네 가지 UseCase를 조합해 카메라 기능을 선언적으로 구현할 수 있게 해준다. 
`ProcessCameraProvider`와 `bindToLifecycle()`을 통해 카메라 리소스 관리를 `LifecycleOwner`에 위임할 수 있어, 화면 생명주기에 맞춰 카메라를 열고 닫는 코드를 직접 작성할 필요가 없다는 점이 가장 큰 장점이다. 
Compose 프로젝트에서는 `AndroidView`로 `PreviewView`를 감싸는 패턴이 표준적이며, `ImageAnalysis`를 ML Kit과 연동하면 바코드 인식이나 얼굴 인식 같은 실시간 기능도 비교적 쉽게 구현할 수 있다. 
다만 카메라는 기기별 하드웨어 차이가 크게 드러나는 영역이므로, 에뮬레이터만으로 검증을 마치지 말고 실제 타겟 기기에서 UseCase 조합과 성능을 반드시 확인하는 습관을 들이는 것이 중요하다.
