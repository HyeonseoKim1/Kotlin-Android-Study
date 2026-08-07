# ActivityResult API

## 목차

1. ActivityResult API란?
2. startActivityForResult가 deprecated된 이유
3. ActivityResultContract란?
4. registerForActivityResult 사용 방법
5. 자주 사용하는 기본 Contract
6. Compose에서 사용하는 방법
7. 권한 요청(RequestPermission, RequestMultiplePermissions)
8. 사진 및 파일 선택(PickVisualMedia, OpenDocument)
9. Lifecycle과 내부 동작 원리
10. 실무에서 자주 사용하는 패턴 및 주의사항

<br>

## 1. ActivityResult API란?

`androidx.activity.result` 패키지에서 제공하는, 액티비티/프래그먼트 간 결과를 주고받기 위한 API다. 기존의 `startActivityForResult` + `onActivityResult`를 대체하며, "요청을 만드는 시점"과 "콜백을 등록하는 시점"을 하나로 묶어서 처리하는 방식으로 설계되어 있다.

쉽게 비유하면, 예전 방식은 "우체국에 여러 소포를 보내놓고, 나중에 편지함 하나에서 어떤 소포에 대한 답장인지 번호표로 구분하는" 구조였다면, ActivityResult API는 "소포를 보낼 때 이 소포 전용 반송함을 하나 만들어서 그 소포의 답장만 거기로 오게 하는" 구조다.

핵심 구성 요소는 다음과 같다.

- `ActivityResultContract`: 입력을 받아 Intent를 만들고, 결과를 다시 원하는 타입으로 파싱하는 "규약(계약서)". "무엇을 넣으면 무엇이 나오는지"를 타입으로 명시한다.
- `ActivityResultLauncher`: 실제로 요청을 시작(launch)하는 객체. Contract와 1:1로 짝지어진 실행기라고 보면 된다.
- `ActivityResultRegistry`: 요청마다 고유 key를 발급하고, 결과가 돌아왔을 때 어떤 콜백으로 전달할지 관리하는 내부 저장소.
- `ActivityResultCallback`: 결과를 받았을 때 실행되는 콜백 함수(람다).

이 네 가지가 서로 어떻게 연결되는지 그림으로 정리하면 다음과 같다.

```
[내 코드] --launch(입력)--> [Launcher] --createIntent--> [실제 Activity 실행]
                                                              |
                                                          결과 반환
                                                              v
[내 콜백] <--parseResult(결과)-- [Registry] <----------------+
```

즉 Contract는 "Intent를 만드는 방법 + 결과를 해석하는 방법"을 정의하고, Launcher는 그 Contract를 실제로 실행하는 손잡이 역할을 한다.

<br>

## 2. startActivityForResult가 deprecated된 이유

기존 방식은 다음과 같이 작성했다.

```kotlin
// 예전 방식 (deprecated)
startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)

override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK) {
        val uri = data?.data
    }
}
```

이 방식은 다음과 같은 문제들을 가지고 있었다.

- **요청 코드 충돌**: requestCode를 정수 상수로 직접 관리해야 했다. 여러 라이브러리, 여러 화면에서 각자 requestCode를 정의하다 보면 값이 겹치는 경우가 실제로 발생했다.
- **콜백이 한 곳에 몰림**: 액티비티 하나에 권한 요청, 갤러리 선택, 카메라 촬영 등 여러 요청이 있으면 `onActivityResult` 안에 `if-else`가 계속 늘어나면서 "이 요청이 무슨 요청이었는지"를 requestCode 숫자만 보고 유추해야 했다. 가독성과 유지보수성이 나빠졌다.
- **요청과 콜백이 코드상 멀리 떨어짐**: 요청을 시작하는 코드는 버튼 클릭 리스너 안에 있고, 그 결과를 처리하는 콜백은 파일 맨 아래 `onActivityResult`에 따로 있어서 하나의 흐름을 이해하려면 코드를 왔다 갔다 봐야 했다.
- **프로세스 재생성 시 유실 위험**: 메모리 부족 등으로 앱 프로세스가 죽었다가 시스템이 재생성하는 경우, 요청 상태를 개발자가 직접 `onSaveInstanceState`에 저장하고 복원하지 않으면 결과가 유실될 수 있었다.
- **반복되는 보일러플레이트**: 권한 요청, 사진 촬영, 파일 선택처럼 자주 쓰는 패턴도 매번 Intent를 직접 만들고 결과를 직접 파싱해야 했다.

ActivityResult API는 이 모든 문제를 "요청 하나 = Contract 하나 = Launcher 하나 = 콜백 하나"로 캡슐화해서 해결한다. 요청과 콜백이 같은 위치(필드 선언부)에 붙어 있어서 코드를 읽기도 쉽고, requestCode는 내부적으로 문자열 key로 자동 관리되며, 프로세스 재생성 대응도 프레임워크가 알아서 처리해준다.

<br>

## 2. startActivityForResult가 deprecated된 이유 (계속: 실무 체감 차이)

가장 크게 체감되는 차이는 "결과를 어디서 받을지 미리 알 수 있다"는 점이다. 예전 방식은 요청을 아무리 잘 봐도 콜백이 `onActivityResult`에 있다는 것을 알아야만 결과 처리 코드를 찾을 수 있었다. 새 방식은 요청을 정의하는 순간 바로 옆에 콜백이 있기 때문에, 코드 리뷰나 유지보수 시 훨씬 빠르게 흐름을 파악할 수 있다.

<br>

## 3. ActivityResultContract란?

입력(I)을 Intent로 변환하고, 결과(O)를 원하는 타입으로 파싱하는 추상 클래스다. 제네릭 타입 두 개(`I`, `O`)를 가지므로 "무엇을 넣고 무엇을 받는지"가 타입 시그니처만 봐도 명확하다.

```kotlin
abstract class ActivityResultContract<I, O> {
    abstract fun createIntent(context: Context, input: I): Intent
    abstract fun parseResult(resultCode: Int, intent: Intent?): O

    open fun getSynchronousResult(
        context: Context,
        input: I
    ): SynchronousResult<O>? = null
}
```

각 요소의 역할을 하나씩 풀어보면 다음과 같다.

- `I` (입력 타입): launch할 때 넘겨주는 값의 타입. 예를 들어 권한 요청이면 `String`(권한 이름), 커스텀 액티비티 실행이면 `Intent` 자체가 될 수도 있다.
- `O` (출력 타입): 결과로 돌려받는 값의 타입. `Boolean`(권한 허용 여부), `Uri?`(선택한 파일), `ActivityResult`(범용 결과 객체) 등 Contract마다 다르다.
- `createIntent`: 입력값을 바탕으로 실제로 시스템에 전달할 `Intent`를 만드는 부분. "무엇을 실행할 것인가"를 정의한다.
- `parseResult`: 시스템에서 돌아온 `resultCode`와 `Intent`를 원하는 타입(`O`)으로 변환하는 부분. "돌아온 결과를 어떻게 해석할 것인가"를 정의한다.
- `getSynchronousResult`: 시스템 다이얼로그를 띄울 필요 없이 즉시 결과를 알 수 있는 경우(예: 이미 권한이 허용되어 있는 상태)를 위한 최적화 지점이다. 이 값을 반환하면 실제 Activity 전환 없이 바로 결과가 콜백으로 전달된다.

실제로 이미 정의된 Contract를 쓰는 경우가 대부분이지만, 커스텀 Contract를 직접 만들 수도 있다. 예를 들어 연락처를 선택하는 Contract는 다음과 같이 작성할 수 있다.

```kotlin
class PickContactContract : ActivityResultContract<Unit, Uri?>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data
    }
}
```

이렇게 만들어두면 사용하는 쪽에서는 `Uri?`라는 명확한 타입으로 결과를 받을 수 있고, `Intent`를 직접 다루거나 `resultCode`를 매번 체크하는 코드를 반복 작성하지 않아도 된다. Contract는 재사용 가능한 단위이므로 여러 화면에서 같은 요청 로직을 쓰고 싶다면 이렇게 별도 클래스로 분리해두는 것이 유리하다.

<br>

## 4. registerForActivityResult 사용 방법

`ComponentActivity`, `Fragment`에서 제공하는 확장 함수다. 이 함수는 **반드시 Activity/Fragment가 `RESUMED` 상태가 되기 이전 시점**, 즉 `onCreate`(Activity)나 필드 초기화, `onAttach`/`onCreate`(Fragment) 같은 생명주기 이른 단계에서 호출되어야 한다.

```kotlin
class MainActivity : ComponentActivity() {

    // 필드로 선언 - onCreate가 실행되기 전에 이미 등록됨
    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            // 결과 처리
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<Button>(R.id.btn_start).setOnClickListener {
            startSomeActivity()
        }
    }

    private fun startSomeActivity() {
        val intent = Intent(this, DetailActivity::class.java)
        launcher.launch(intent)
    }
}
```

왜 이런 제약이 있는지 이해하면 실수를 줄일 수 있다. `ActivityResultRegistry`는 내부적으로 각 launcher에 고유한 key를 발급하고, 이 key와 콜백을 매핑해서 관리한다. 만약 Activity가 이미 `RESUMED`된 이후(예: 버튼 클릭 리스너 안)에 등록하려고 하면, 프로세스가 재생성되었을 때 이 key가 이전 상태와 일치하지 않을 수 있어 시스템이 아예 등록을 막아버린다. 이때 발생하는 예외가 `IllegalStateException: LifecycleOwner ... is attempting to register while current state is RESUMED`이다.

정리하면 다음과 같은 규칙으로 기억하면 된다.

- launcher는 클래스의 프로퍼티(필드)로 선언한다.
- 버튼 클릭 리스너, 코루틴 블록, `onResume` 이후 시점에서 `registerForActivityResult`를 호출하지 않는다.
- launch(요청을 실제로 시작하는 것)는 언제든 원하는 시점(버튼 클릭 등)에 호출해도 무방하다. 제약이 있는 것은 "등록(register)" 시점이지 "실행(launch)" 시점이 아니다.

<br>

## 5. 자주 사용하는 기본 Contract

`ActivityResultContracts` 오브젝트 안에 자주 쓰는 Contract들이 미리 정의되어 있다. 매번 직접 Contract를 만들 필요 없이 아래 표에 있는 것들을 바로 가져다 쓸 수 있다.

| Contract | 입력 | 출력 | 용도 |
|---|---|---|---|
| `StartActivityForResult` | `Intent` | `ActivityResult` | 범용 액티비티 실행 (가장 기본형) |
| `RequestPermission` | `String` | `Boolean` | 단일 권한 요청 |
| `RequestMultiplePermissions` | `Array<String>` | `Map<String, Boolean>` | 다중 권한 요청 |
| `TakePicturePreview` | `Unit` | `Bitmap?` | 카메라 앱 실행 후 썸네일급 Bitmap 반환 |
| `TakePicture` | `Uri` | `Boolean` | 지정한 Uri 위치에 원본 크기 사진 저장 |
| `PickVisualMedia` | `PickVisualMediaRequest` | `Uri?` | 사진/동영상 선택 (Android 13+ Photo Picker) |
| `OpenDocument` | `Array<String>` | `Uri?` | 문서 선택 (Storage Access Framework) |
| `OpenMultipleDocuments` | `Array<String>` | `List<Uri>` | 다중 문서 선택 |
| `CreateDocument` | `String` | `Uri?` | 새 문서 생성 (파일명 입력) |
| `GetContent` | `String` | `Uri?` | 단일 콘텐츠(미디어 포함) 선택, 가장 범용적 |

표를 볼 때 주목할 점은, 출력 타입이 Contract마다 다르다는 것이다. `RequestPermission`은 단순히 `Boolean` 하나만 돌려주지만 `RequestMultiplePermissions`는 어떤 권한이 허용/거부되었는지 `Map`으로 세세하게 알려준다. 이렇게 "필요한 만큼만 딱 맞는 타입으로" 결과를 받을 수 있다는 것이 Contract 방식의 장점이다.

<br>

## 6. Compose에서 사용하는 방법

Compose에서는 `registerForActivityResult` 대신 `rememberLauncherForActivityResult`를 사용한다. 개념은 동일하지만, Composable 함수의 생명주기(Composition)에 맞춰 등록과 해제를 자동으로 처리해준다는 점이 다르다.

```kotlin
@Composable
fun PermissionScreen() {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // 권한 결과 처리
    }

    Button(onClick = {
        launcher.launch(Manifest.permission.CAMERA)
    }) {
        Text("카메라 권한 요청")
    }
}
```

내부적으로 어떤 일이 일어나는지 조금 더 풀어보면 다음과 같다.

- Composable이 처음 실행될 때 `remember`를 통해 launcher 인스턴스가 한 번만 생성된다. recomposition이 일어나도 같은 launcher 인스턴스가 재사용된다.
- 등록에 필요한 `ActivityResultRegistry`는 `LocalActivityResultRegistryOwner`라는 CompositionLocal을 통해 현재 Activity/Fragment로부터 가져온다.
- Composable이 Composition을 떠날 때(화면에서 사라질 때) `DisposableEffect`가 자동으로 `unregister()`를 호출해서 등록을 해제한다. 개발자가 직접 해제 코드를 작성할 필요가 없다.

주의할 점은 Compose의 규칙을 그대로 따른다는 것이다.

- `rememberLauncherForActivityResult`는 Composable 함수의 최상위(top-level)에서 호출해야 한다.
- `if`문 안, 반복문 안, 콜백(람다) 안에서 호출하면 "Composable 호출 순서가 매번 달라질 수 있다"는 규칙을 위반해서 런타임에 문제가 생길 수 있다.
- 조건에 따라 다른 launcher가 필요하다면, launcher 자체는 항상 선언해두고 launch할 때 넘기는 입력값이나 launch 여부만 조건으로 분기하는 방식을 권장한다.

<br>

## 7. 권한 요청(RequestPermission, RequestMultiplePermissions)

단일 권한을 요청할 때는 `RequestPermission`을 사용한다.

```kotlin
val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) {
        // 허용됨 - 카메라 기능 실행
    } else {
        // 거부됨 - 대체 UI 또는 안내 문구 표시
    }
}

launcher.launch(Manifest.permission.CAMERA)
```

여러 권한을 한 번에 요청해야 한다면 `RequestMultiplePermissions`를 사용한다. 결과는 `Map<String, Boolean>` 형태로 각 권한이 개별적으로 허용되었는지 여부를 담고 있다.

```kotlin
val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { resultMap: Map<String, Boolean> ->
    val cameraGranted = resultMap[Manifest.permission.CAMERA] ?: false
    val audioGranted = resultMap[Manifest.permission.RECORD_AUDIO] ?: false

    if (cameraGranted && audioGranted) {
        // 영상 녹화 기능 실행
    }
}

launcher.launch(
    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
)
```

실무에서는 여기서 한 단계 더 나아가서 "거부된 이유"를 구분하는 것이 중요하다. 안드로이드는 사용자가 권한을 거부할 때 두 가지 상태를 만든다.

1. 그냥 거부(다시 물어볼 수 있는 상태)
2. "다시 묻지 않음"을 함께 선택해서 거부(시스템 다이얼로그가 다시 뜨지 않는 상태)

이 둘을 구분하려면 `shouldShowRequestPermissionRationale(permission)`을 함께 사용해야 한다.

```kotlin
if (!granted) {
    val activity = context as Activity
    val canAskAgain = activity.shouldShowRequestPermissionRationale(
        Manifest.permission.CAMERA
    )
    if (canAskAgain) {
        // 다시 요청 가능 - 권한이 필요한 이유를 설명하는 다이얼로그 표시
    } else {
        // 다시 묻지 않음 상태 - 앱 설정 화면으로 유도
    }
}
```

ActivityResult API 자체는 이 두 상태를 구분해주지 않으므로, 이 로직은 항상 직접 작성해야 한다는 점을 기억해두는 것이 좋다.

<br>

## 8. 사진 및 파일 선택(PickVisualMedia, OpenDocument)

**사진/동영상 선택**에는 Android 13(API 33)부터 도입된 Photo Picker인 `PickVisualMedia`를 우선적으로 사용하는 것이 권장된다. 이 방식은 별도의 `READ_MEDIA_IMAGES` 같은 저장소 권한 없이도 사용자가 특정 사진/동영상만 선택적으로 앱에 공유할 수 있게 해준다는 장점이 있다.

```kotlin
val pickMediaLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
) { uri: Uri? ->
    uri?.let {
        // 선택된 이미지 Uri 처리 (예: AsyncImage로 표시)
    }
}

pickMediaLauncher.launch(
    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
)
```

`ActivityResultContracts.PickVisualMedia.ImageOnly` 대신 `VideoOnly`, `ImageAndVideo` 등 원하는 미디어 타입을 지정할 수 있다.

**임의 파일(PDF, 문서 등)을 선택**할 때는 Storage Access Framework(SAF) 기반의 `OpenDocument`를 사용한다.

```kotlin
val openDocLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
) { uri: Uri? ->
    uri?.let {
        // 앱 재시작 후에도 접근하려면 영구 권한을 별도로 획득해야 한다
        contentResolver.takePersistableUriPermission(
            it,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

openDocLauncher.launch(arrayOf("application/pdf"))
```

`launch`에 넘기는 배열은 MIME 타입 필터다. 예를 들어 `arrayOf("image/*", "application/pdf")`처럼 여러 타입을 동시에 허용할 수도 있다.

여기서 실무에서 자주 놓치는 부분이 하나 있다. `OpenDocument`로 받은 Uri는 기본적으로 "일시적인" 접근 권한만 가지고 있어서, 앱을 재시작하면 더 이상 그 Uri에 접근할 수 없다. 만약 그 Uri를 나중에 다시 사용해야 한다면(예: 앱을 껐다 켠 뒤에도 이전에 선택한 파일을 보여줘야 한다면) `takePersistableUriPermission`을 호출해서 영구 권한을 명시적으로 얻어야 한다.

<br>

## 9. Lifecycle과 내부 동작 원리

내부적으로는 다음과 같은 흐름으로 동작한다.

1. `registerForActivityResult` 또는 `rememberLauncherForActivityResult`가 호출되면, `ActivityResultRegistry`가 해당 요청에 대해 고유한 문자열 key(예: `"activity_rq#0"`)를 발급한다.
2. 이 key는 Contract, 콜백과 함께 내부 맵에 저장된다.
3. `launch()`가 호출되면 registry는 이 key를 이용해 실제 시스템 Activity를 실행한다.
4. 결과가 돌아오면(`onActivityResult`에 해당하는 시스템 콜백), registry는 key를 통해 어떤 Contract와 어떤 콜백이 이 결과를 처리해야 하는지 찾아서 `parseResult`로 변환한 뒤 콜백을 호출한다.

Lifecycle과의 연동도 중요한 부분이다. 등록된 콜백은 `LifecycleOwner`의 상태를 관찰하고 있다가, 만약 결과가 `STARTED` 상태가 아닌 시점(예: 화면이 백그라운드로 내려간 사이)에 도착했다면 즉시 콜백을 호출하지 않고 대기시킨다. 그리고 다시 `ON_START` 시점이 되면 그때 대기 중이던 결과를 디스패치한다. 이 덕분에 화면이 보이지 않는 상태에서 콜백이 실행되어 예상치 못한 UI 처리가 일어나는 것을 방지할 수 있다.

프로세스 재생성에 대한 대응도 자동화되어 있다. registry는 요청 key와 Contract 정보를 `savedInstanceState`에 저장해두기 때문에, 메모리 부족 등으로 앱 프로세스가 죽었다가 시스템이 다시 살리는 경우에도(단, `onSaveInstanceState`가 호출되는 정상적인 종료 경로인 경우) 이전 요청과 결과를 다시 연결할 수 있다. 이는 예전 `startActivityForResult` 방식에서 개발자가 직접 처리해야 했던 부분이 프레임워크 레벨로 옮겨진 것이다.

마지막으로 `unregister()`는 더 이상 이 launcher가 필요하지 않을 때 key를 반납하는 역할을 한다. 일반 View 기반 Activity/Fragment에서는 `Lifecycle`이 `DESTROYED` 상태가 될 때 자동으로 unregister가 호출되고, Compose에서는 `rememberLauncherForActivityResult`가 이를 `DisposableEffect`로 감싸서 Composition이 끝날 때 자동 처리한다.

<br>

## 10. 실무에서 자주 사용하는 패턴 및 주의사항

**등록 위치 관련**

- launcher는 반드시 클래스의 필드(프로퍼티)로 선언하고, `onCreate`나 Composable 최상위처럼 이른 시점에 등록한다.
- 버튼 클릭 리스너나 코루틴 블록 안에서 `registerForActivityResult`를 호출하면 예외가 발생한다는 것을 항상 기억한다.

**여러 요청을 다룰 때**

- 하나의 화면에서 카메라, 권한, 파일 선택처럼 여러 종류의 요청이 동시에 필요하다면, launcher를 목적별로 각각 선언하는 것이 하나의 launcher를 재사용하며 조건 분기하는 것보다 코드가 명확해진다.

**Compose 관련**

- launcher를 `remember` 없이 매 recomposition마다 새로 만들면 등록/해제가 꼬여서 예기치 않은 동작이 생길 수 있으므로 반드시 `rememberLauncherForActivityResult`를 사용한다.
- `rememberLauncherForActivityResult`는 조건문이나 반복문 내부에서 호출하지 않는다.

**권한 처리 관련**

- 권한 거부 후 "다시 묻지 않음"을 선택한 경우를 `shouldShowRequestPermissionRationale`로 구분해서, 이 경우에는 시스템 다이얼로그 대신 앱 설정 화면으로 유도하는 UX를 마련해야 한다.

**아키텍처(MVI) 관련**

- launcher 자체는 Android 프레임워크에 종속된 객체이므로 ViewModel이 직접 소유하면 생명주기 문제(메모리 누수, Context 참조 등)가 생긴다.
- 실무에서는 launcher를 Composable/Activity 레벨에 선언해두고, 결과가 돌아오면 그 값을 Intent(사용자 액션) 또는 Effect/Event 형태로 변환해서 ViewModel에 전달하는 패턴을 사용한다. 즉 "권한이 허용되었다"는 사실 자체는 ViewModel의 상태로 관리하되, 그 권한을 요청하는 launcher는 View 레이어에 둔다.

**결과 코드 해석 관련**

- `ActivityResult`의 `resultCode`는 `RESULT_OK`, `RESULT_CANCELED`뿐 아니라 대상 액티비티가 `setResult(customCode, intent)`로 자유롭게 커스텀 코드를 반환할 수도 있으므로, 상대 액티비티가 실제로 무엇을 `setResult`로 반환하는지 함께 확인하는 습관이 필요하다.

## 정리

- ActivityResult API는 요청(Contract)과 콜백을 하나의 단위로 캡슐화해서, 예전 `startActivityForResult` 방식의 requestCode 충돌, 콜백 분산, 프로세스 재생성 시 결과 유실 문제를 해결한 API다. 
- Compose에서는 동일한 개념을 `rememberLauncherForActivityResult`로 제공하며 등록/해제가 Composition 생명주기에 맞춰 자동으로 처리된다.
- 실무에서는 권한 요청과 Photo Picker/SAF 기반 파일 선택이 가장 자주 쓰이는 영역이며, launcher는 항상 이른 시점에 등록하고 MVI 구조에서는 launcher를 View 레이어에 두고 결과만 이벤트로 변환해 ViewModel에 전달하는 방식이 권장된다.
