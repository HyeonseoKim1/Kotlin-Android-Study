# 앱 업데이트 / 강제 업데이트 안내: In-App Update, Remote Config

## 목차

1. 개요: 왜 "업데이트 안내"가 복잡한가
2. 구성 요소
3. 가장 기본적인 구현
4. 상태 설계: 진실의 원천은 UpdateStage
5. In-App Update 기본: Flexible vs Immediate
6. 업데이트 가능 여부 확인: AppUpdateInfo
7. Flexible 플로우: 백그라운드 다운로드
8. Immediate 플로우: 전체 화면 업데이트
9. Remote Config: 최소 버전 / 권장 버전 정책
10. 정책 결정 로직
11. 폴백: Play Store 이동
12. MVI에서의 상태 관리
13. UI 연결
14. 테스트
15. 흔한 실수
16. 전체 예제 (복붙용)

<br>

# 1. 개요: 왜 "업데이트 안내"가 복잡한가

업데이트 안내는 "새 버전이 있으면 스토어로 보낸다"로 끝나지 않는다. 실제로는 서로 다른 세 가지 요구가 섞여 있다.

1. **권장 업데이트**: 새 버전이 나왔으니 해보라고 알려주되, 사용자가 거절할 수 있다.
2. **강제 업데이트**: 이 버전 아래로는 더 이상 쓸 수 없다. 서버 API가 바뀌었거나 치명적인 버그가 있을 때 쓴다.
3. **매끄러운 설치**: 스토어로 나가지 않고 앱 안에서 다운로드/설치까지 끝낸다.

이 중 "언제, 누구에게, 얼마나 강하게 안내할지"는 배포 후에도 바꿀 수 있어야 하므로 앱 코드에 박아두면 안 된다. "어떻게 설치할지"는 Google Play가 제공하는 In-App Update가 담당한다.

해결 원칙은 세 가지다.

- **정책**(강제할지, 권장할지)은 Remote Config가 결정한다. 앱을 다시 배포하지 않고 바꿀 수 있다.
- **설치 방식**(Flexible / Immediate / 스토어 이동)은 In-App Update가 결정한다.
- 화면은 현재 단계를 나타내는 `UpdateStage` 하나만 읽는다.

데이터 흐름은 다음과 같다.

```text
앱 시작 / 포그라운드 복귀
   -> Remote Config로 정책 조회 (min / latest 버전)
   -> 현재 버전과 비교 -> None / Optional / Force
   -> In-App Update 가능 여부 확인
        가능   -> Flexible(권장) 또는 Immediate(강제)
        불가능 -> Play Store로 이동
```

<br>

# 2. 구성 요소

| 구성 요소 | 역할 |
| --- | --- |
| Remote Config | 최소 지원 버전, 최신 버전, 안내 문구를 서버에서 내려줌 |
| `AppUpdateManager` | Play Core의 In-App Update 진입점. 업데이트 확인, 시작, 설치 완료 |
| `AppUpdateInfo` | Play가 알려주는 업데이트 정보 (가능 여부, 버전 코드, 우선순위 등) |
| `UpdatePolicyRepository` | Remote Config를 감싸서 `UpdatePolicy`를 돌려줌 |
| `decideUpdateLevel()` | 현재 버전과 정책을 비교하는 순수 함수 |
| `UpdateDismissStore` | 권장 업데이트를 "나중에"로 넘긴 기록 (매번 노출 방지) |
| `UpdateViewModel` | 위 구성 요소를 조합해서 `UpdateStage` 결정 |
| `UpdateHost` | 루트에서 Stage에 따라 강제 화면, 다이얼로그, 진행률, 재시작 안내를 표시 |

## 2-1. 정책을 누가 정하는가: Remote Config vs Play의 updatePriority

| 방식 | 장점 | 한계 |
| --- | --- | --- |
| Remote Config (min / latest 버전) | 앱 재배포 없이 즉시 변경. 조건(앱 버전, 사용자 비율)으로 세밀하게 제어. Play 외 스토어에도 적용 | 정책값만 알려줄 뿐, 설치는 별도 구현 필요 |
| Play `updatePriority` (0~5) | Play 배포에 붙어서 내려옴. 버전별 우선순위 지정 | 주로 Play Developer API로 설정해야 해서 다루기 번거롭고, Play 설치 앱에서만 의미 있음 |

이 문서는 **Remote Config로 정책을 정하고 In-App Update로 설치한다**를 기본으로 한다. `updatePriority`는 보조 신호로만 언급한다.

<br>

# 3. 가장 기본적인 구현

가장 단순한 형태는 현재 버전을 하드코딩된 값과 비교해서 스토어로 보내는 것이다.

```kotlin
@Composable
fun SimpleUpdateDialog(onUpdate: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("업데이트 안내") },
        text = { Text("새 버전이 나왔어요. 업데이트해주세요.") },
        confirmButton = { TextButton(onClick = onUpdate) { Text("업데이트") } },
    )
}

fun Context.openStoreSimple() {
    startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
    )
}
```

동작은 하지만 곧 문제가 보인다.

- 강제인지 권장인지 구분할 수 없고, 바꾸려면 앱을 다시 배포해야 한다.
- 매번 스토어로 나가야 해서 사용자 이탈이 크다.
- 스토어에 `market://`을 처리할 앱이 없으면 크래시가 난다.
- 사용자가 거절한 뒤에도 계속 같은 안내가 뜬다.

이후 챕터는 이 기본형을 보완해 나간다.

<br>

# 4. 상태 설계: 진실의 원천은 UpdateStage

## 4-1. 플래그를 따로 두면 안 되는 이유

```kotlin
// 안티패턴
data class State(
    val showForceDialog: Boolean = false,
    val showOptionalDialog: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val isDownloaded: Boolean = false,
)
```

- 강제 화면과 권장 다이얼로그가 동시에 뜰 수 있다.
- 다운로드가 끝났는데 `isDownloading`이 true로 남는 식의 불일치가 생긴다.
- 화면 쪽 분기가 `if` 연쇄로 늘어난다.

## 4-2. 단계를 sealed로 표현

```kotlin
sealed interface UpdateStage {
    data object None : UpdateStage
    data class Optional(val latestVersionCode: Long, val message: String) : UpdateStage
    data class Force(val message: String) : UpdateStage
    data class Downloading(val progress: Float) : UpdateStage   // Flexible 다운로드 중
    data object ReadyToInstall : UpdateStage                    // Flexible 다운로드 완료, 재시작 대기
}

data class UpdateState(
    val stage: UpdateStage = UpdateStage.None,
)
```

한 시점에 하나의 단계만 존재하므로 불가능한 조합이 타입 수준에서 사라지고, UI는 `when (stage)` 하나로 끝난다.

<br>

# 5. In-App Update 기본: Flexible vs Immediate

In-App Update는 사용자가 Play Store 앱으로 나가지 않고 업데이트하게 해주는 Play Core 기능이다. 두 가지 방식이 있다.

| 구분 | Flexible | Immediate |
| --- | --- | --- |
| 화면 | 앱을 쓰는 동안 백그라운드로 다운로드 | Play가 전체 화면 UI를 띄우고 업데이트가 끝날 때까지 앱 사용 불가 |
| 설치 | 다운로드 후 `completeUpdate()`를 호출하면 앱 재시작과 함께 설치 | 다운로드 후 자동으로 설치하고 앱 재시작 |
| 진행률 | 앱이 직접 그림 (`InstallStateUpdatedListener`) | Play UI가 처리 |
| 적합한 경우 | 권장 업데이트 | 강제 업데이트 |

## 5-1. 사용 전 알아둘 제약

- **Google Play에서 설치한 앱에서만 동작한다.** Android Studio로 설치한 디버그 빌드나 APK 직접 설치(사이드로딩)에서는 업데이트가 없다고 나온다.
- 현재 버전보다 높은 `versionCode`가 Play에 배포되어 있어야 한다.
- 새 버전을 올린 직후에는 사용자 기기에 반영되기까지 시간이 걸릴 수 있다. 단계적 출시(staged rollout) 중이면 일부 사용자에게는 아직 업데이트가 보이지 않는다.
- 원스토어, 갤럭시 스토어 등 Play 외 스토어에서 설치한 앱에서는 쓸 수 없다. 이 경우 11장의 스토어 이동으로 폴백한다.

## 5-2. 의존성

```kotlin
implementation("com.google.android.play:app-update:<최신 버전>")
implementation("com.google.android.play:app-update-ktx:<최신 버전>")
```

`app-update-ktx`는 `requestAppUpdateInfo()` 같은 코루틴 확장을 제공한다.

<br>

# 6. 업데이트 가능 여부 확인: AppUpdateInfo

```kotlin
val info: AppUpdateInfo = appUpdateManager.requestAppUpdateInfo()   // suspend (ktx)
```

## 6-1. 주요 필드

| 필드 | 의미 |
| --- | --- |
| `updateAvailability()` | `UPDATE_AVAILABLE`, `UPDATE_NOT_AVAILABLE`, `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS`, `UNKNOWN` |
| `isUpdateTypeAllowed(type)` | 해당 방식(Flexible / Immediate)으로 업데이트할 수 있는지 |
| `availableVersionCode()` | Play에 올라와 있는 새 버전 코드 |
| `updatePriority()` | 개발자가 지정한 우선순위 (0~5) |
| `clientVersionStalenessDays()` | 새 버전이 사용자에게 제공된 지 며칠 지났는지 (`null`일 수 있음) |
| `installStatus()` | Flexible 다운로드/설치 진행 상태 |

## 6-2. 판단 규칙

```kotlin
private fun AppUpdateInfo.canUpdate(type: Int): Boolean =
    updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && isUpdateTypeAllowed(type)
```

`isUpdateTypeAllowed()`만 보면 안 된다. 업데이트가 없는데도 허용 여부만 true일 수 있으므로 반드시 `UPDATE_AVAILABLE`과 함께 확인한다.

`requestAppUpdateInfo()`는 Play 서비스 연결이 안 되는 기기(Play 없는 기기, 네트워크 오류)에서 예외를 던진다. `runCatching`으로 감싸서 실패하면 "인앱 업데이트 불가"로 취급하고 스토어 이동으로 폴백한다.

## 6-3. 우선순위와 경과 일수는 보조 신호

`clientVersionStalenessDays()`는 "새 버전이 나온 지 3일이 지났을 때부터 권장 안내를 띄운다" 같은 완화 정책에 쓸 수 있다. 다만 이 문서는 정책의 기준을 Remote Config 하나로 통일해서 판단 지점을 단순하게 유지한다.

<br>

# 7. Flexible 플로우: 백그라운드 다운로드

## 7-1. 흐름

```text
Optional 안내 -> 사용자가 "업데이트" -> Play 확인 다이얼로그 -> 다운로드 시작(백그라운드)
   -> Downloading(progress) -> DOWNLOADED -> ReadyToInstall -> "재시작" -> completeUpdate()
```

## 7-2. 다운로드 상태를 Flow로 받기

```kotlin
fun AppUpdateManager.installStateFlow(): Flow<InstallState> = callbackFlow {
    val listener = InstallStateUpdatedListener { state -> trySend(state) }
    registerListener(listener)
    awaitClose { unregisterListener(listener) }
}
```

`awaitClose`에서 리스너를 해제하지 않으면 ViewModel이 사라진 뒤에도 리스너가 남는다.

## 7-3. 상태 매핑

```kotlin
private fun onInstallState(state: InstallState) {
    when (state.installStatus()) {
        InstallStatus.DOWNLOADING -> {
            val total = state.totalBytesToDownload()
            val progress = if (total > 0) state.bytesDownloaded().toFloat() / total else 0f
            setStage(UpdateStage.Downloading(progress))
        }
        InstallStatus.DOWNLOADED -> setStage(UpdateStage.ReadyToInstall)
        InstallStatus.FAILED, InstallStatus.CANCELED -> setStage(UpdateStage.None)
        else -> Unit
    }
}
```

## 7-4. 설치 완료

```kotlin
appUpdateManager.completeUpdate()   // 앱이 재시작되면서 설치됨
```

다운로드가 끝난 상태에서 사용자가 재시작 안내를 놓치면 업데이트는 다운로드만 되고 설치되지 않는다. 그래서 `ReadyToInstall`은 스낵바나 배너로 계속 보여주고, 앱이 포그라운드로 돌아올 때도 `installStatus() == DOWNLOADED`인지 다시 확인한다. (10장 `check()`)

<br>

# 8. Immediate 플로우: 전체 화면 업데이트

## 8-1. 시작

```kotlin
appUpdateManager.startUpdateFlowForResult(
    info,
    updateLauncher,   // ActivityResultLauncher<IntentSenderRequest>
    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
)
```

런처는 Composable에서 만든다.

```kotlin
val updateLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult()
) { result ->
    viewModel.onIntent(UpdateIntent.InAppUpdateResult(result.resultCode))
}
```

## 8-2. 결과 코드

| 결과 | 의미 | 처리 |
| --- | --- | --- |
| `RESULT_OK` | 사용자가 수락 | Immediate는 Play가 설치 후 앱을 재시작하므로 보통 여기까지 오지 않음. Flexible은 다운로드가 시작됨 |
| `RESULT_CANCELED` | 사용자가 거절/취소 | 강제: 강제 화면 유지(다시 시도 버튼). 권장: "나중에"로 기록 |
| `ActivityResult.RESULT_IN_APP_UPDATE_FAILED` | 인앱 업데이트 실패 | 강제인 경우 스토어 이동으로 폴백 |

## 8-3. 진행 중이던 업데이트 이어가기

사용자가 Immediate 업데이트 도중에 앱을 백그라운드로 보냈다가 돌아오면 Play 화면이 사라질 수 있다. 앱이 포그라운드로 돌아올 때마다 확인해서 이어서 시작한다.

```kotlin
if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
    // Immediate 업데이트가 진행 중이던 경우 -> 다시 시작해서 Play UI를 복원
    startInAppUpdate(info, AppUpdateType.IMMEDIATE)
}
```

이 처리가 없으면 강제 업데이트 중에 앱 화면이 그냥 보이는 구멍이 생긴다.

<br>

# 9. Remote Config: 최소 버전 / 권장 버전 정책

## 9-1. 키 설계

Firebase 콘솔에 아래 파라미터를 만든다.

| 키 | 타입 | 예시 | 의미 |
| --- | --- | --- | --- |
| `min_version_code` | Number | `105` | 현재 버전이 이보다 낮으면 **강제** 업데이트 |
| `latest_version_code` | Number | `108` | 현재 버전이 이보다 낮으면 **권장** 업데이트 |
| `update_message` | String | `안정성 개선이 포함된 업데이트예요` | 안내 문구 |

버전 이름("1.10.0")이 아니라 **versionCode(Long)** 로 비교한다. 문자열로 비교하면 "1.10.0"이 "1.9.0"보다 작다고 판단하는 문제가 생기고, 숫자 비교는 파싱 규칙이 필요 없다.

## 9-2. 모델과 Repository

```kotlin
data class UpdatePolicy(
    val minVersionCode: Long,
    val latestVersionCode: Long,
    val message: String,
)

@Singleton
class UpdatePolicyRepository @Inject constructor() {

    private val remoteConfig = FirebaseRemoteConfig.getInstance().apply {
        setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0 else 3_600)
                .build()
        )
        // 기본값은 "아무것도 강제하지 않음"
        setDefaultsAsync(
            mapOf(
                KEY_MIN to 0L,
                KEY_LATEST to 0L,
                KEY_MESSAGE to DEFAULT_MESSAGE,
            )
        )
    }

    suspend fun fetch(): UpdatePolicy {
        // 느린 네트워크에서 앱 시작이 막히지 않도록 시간 제한
        withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            runCatching { remoteConfig.fetchAndActivate().await() }
        }
        return UpdatePolicy(
            minVersionCode = remoteConfig.getLong(KEY_MIN),
            latestVersionCode = remoteConfig.getLong(KEY_LATEST),
            message = remoteConfig.getString(KEY_MESSAGE),
        )
    }

    private companion object {
        const val KEY_MIN = "min_version_code"
        const val KEY_LATEST = "latest_version_code"
        const val KEY_MESSAGE = "update_message"
        const val DEFAULT_MESSAGE = "새로운 버전이 나왔어요. 업데이트해주세요."
        const val FETCH_TIMEOUT_MS = 3_000L
    }
}
```

`await()`는 `kotlinx-coroutines-play-services`의 확장이다.

## 9-3. 기본값은 반드시 0 (fail-open)

기본값을 높게 두면 fetch에 실패한 사용자 전원이 강제 업데이트 화면에 갇힌다. 기본값은 "아무것도 강제하지 않음"(0)으로 두고, 마지막으로 활성화된 값은 Remote Config가 기기에 저장해 두므로 오프라인에서도 직전 정책이 유지된다.

## 9-4. 반영 속도

- 기본 최소 fetch 간격은 12시간이다. 긴급 강제 업데이트가 12시간 뒤에 반영되면 곤란하므로 릴리즈에서도 1시간 정도로 낮추거나, 앱 시작/복귀 시마다 확인하도록 한다.
- 디버그에서는 간격을 0으로 두면 되지만, 짧은 시간에 너무 자주 fetch하면 Firebase가 호출을 제한(throttle)한다.

## 9-5. 실시간 반영 (선택)

앱을 쓰는 도중에도 정책 변경을 즉시 받고 싶다면 Real-time Remote Config를 쓴다.

```kotlin
remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
    override fun onUpdate(configUpdate: ConfigUpdate) {
        remoteConfig.activate().addOnCompleteListener {
            // 정책을 다시 읽어서 UpdateStage 재계산
        }
    }

    override fun onError(error: FirebaseRemoteConfigException) {
        // 실패해도 다음 포그라운드 복귀 때 다시 확인하므로 무시 가능
    }
})
```

사용 중에 갑자기 강제 업데이트 화면이 뜨는 것이 허용되는 서비스인지 먼저 판단한다. 대부분은 "앱 시작/포그라운드 복귀 시점에 확인"으로 충분하다.

## 9-6. 점진 적용

Remote Config의 조건(앱 버전, 사용자 비율 등)을 쓰면 `min_version_code`를 일부 사용자에게만 먼저 적용해 영향을 확인할 수 있다.

<br>

# 10. 정책 결정 로직

## 10-1. 순수 함수로 분리

현재 버전과 정책을 비교하는 부분은 외부 의존이 없는 순수 함수로 둔다. 테스트가 쉽고, 규칙이 한 곳에 모인다.

```kotlin
enum class UpdateLevel { None, Optional, Force }

fun decideUpdateLevel(currentVersionCode: Long, policy: UpdatePolicy): UpdateLevel = when {
    currentVersionCode < policy.minVersionCode -> UpdateLevel.Force
    currentVersionCode < policy.latestVersionCode -> UpdateLevel.Optional
    else -> UpdateLevel.None
}
```

정책이 잘못 설정되어 `min > latest`가 되어도 `Force`가 우선하므로 안전한 방향으로 동작한다.

## 10-2. 현재 버전 가져오기

```kotlin
@Singleton
class AppVersionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val versionCode: Long by lazy {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        PackageInfoCompat.getLongVersionCode(info)
    }
}
```

`PackageInfoCompat`을 쓰면 API 28 전후의 `versionCode`/`longVersionCode` 차이를 신경 쓰지 않아도 된다.

## 10-3. 레벨별 동작

| 레벨 | 단계 | In-App 방식 | 인앱이 불가능하면 |
| --- | --- | --- | --- |
| `Force` | `Force` 화면 | Immediate를 즉시 시작 | 스토어 이동 버튼만 있는 강제 화면 |
| `Optional` | `Optional` 다이얼로그 (거절한 적 없을 때만) | 수락 시 Flexible | 수락 시 스토어 이동 |
| `None` | `None` | - | - |

## 10-4. 서버 방어선 (선택)

Remote Config 기반 강제 업데이트는 클라이언트가 정책을 지켜준다는 전제가 있다. 구버전이 반드시 막혀야 하는 상황(API 호환 불가, 보안 이슈)이라면 서버에서도 앱 버전 헤더를 검사해서 최소 버전 미만이면 `426 Upgrade Required` 같은 응답을 내려주는 이중 방어를 고려한다. 앱은 이 응답을 받으면 같은 `Force` 화면으로 연결할 수 있다.

<br>

# 11. 폴백: Play Store 이동

인앱 업데이트가 불가능한 경우(Play 외 설치, Play 서비스 오류, 단계적 출시로 아직 미제공, Immediate 실패)에는 스토어로 보낸다.

```kotlin
fun Context.openStore() {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
    val web = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
    )
    try {
        startActivity(market)
    } catch (e: ActivityNotFoundException) {
        startActivity(web)   // 스토어 앱이 없으면 브라우저로
    }
}
```

`try/catch`로 처리하면 `resolveActivity`를 쓸 때 필요한 `<queries>` 선언(패키지 가시성)을 따로 추가하지 않아도 된다.

## 11-1. 배포 채널이 여러 개라면

원스토어, 갤럭시 스토어 등으로도 배포한다면 빌드 flavor별로 스토어 URL을 분리한다.

```kotlin
// flavor별로 buildConfigField를 다르게 정의
// googlePlay: market://details?id=...
// oneStore:  onestore://common/product/...
```

Play 외 채널 flavor에서는 In-App Update를 아예 건너뛰고 바로 스토어 이동으로 처리하면 불필요한 실패 로그를 줄일 수 있다.

<br>

# 12. MVI에서의 상태 관리

## 12-1. 무엇을 어디에 두는가

| 종류 | 위치 | 이유 |
| --- | --- | --- |
| 현재 단계(`UpdateStage`) | `UiState` | 화면이 다시 그려져도 유지되어야 함 |
| 인앱 업데이트 시작, 스토어 이동 | `Effect` | 1회성 명령. 재구독 시 다시 실행되면 안 됨 |
| 인앱 업데이트 결과(`resultCode`) | `Intent` | 시스템이 발생시킨 입력 |
| 다운로드 진행 상태 | ViewModel이 `installStateFlow`를 수집해서 `UiState`에 반영 | 사용자 입력이 아니라 외부 이벤트 |
| "나중에" 기록 | `UpdateDismissStore` (DataStore) | 앱을 껐다 켜도 유지되어야 함 |

## 12-2. 계약

```kotlin
sealed interface UpdateIntent {
    data object Check : UpdateIntent                 // 앱 시작 / 포그라운드 복귀
    data object AcceptOptional : UpdateIntent
    data object DismissOptional : UpdateIntent
    data object ClickForceUpdate : UpdateIntent      // 강제 화면의 업데이트 버튼
    data class InAppUpdateResult(val resultCode: Int) : UpdateIntent
    data object CompleteUpdate : UpdateIntent        // 재시작 안내에서 "재시작"
}

sealed interface UpdateEffect {
    data class StartInAppUpdate(val info: AppUpdateInfo, val type: Int) : UpdateEffect
    data object OpenStore : UpdateEffect
}
```

## 12-3. "나중에" 기록

권장 업데이트를 매 실행마다 보여주면 사용자는 금방 짜증을 낸다. 거절한 버전과 시각을 저장해서 같은 버전은 일정 기간 다시 묻지 않는다.

```kotlin
private val Context.updateDataStore by preferencesDataStore(name = "update")

@Singleton
class UpdateDismissStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.updateDataStore

    /** 같은 버전을 SNOOZE 기간 안에 거절했다면 false. 더 새로운 버전이 나왔다면 true */
    suspend fun shouldShow(latestVersionCode: Long, now: Long = System.currentTimeMillis()): Boolean {
        val prefs = dataStore.data.first()
        val dismissedVersion = prefs[KEY_VERSION] ?: return true
        val dismissedAt = prefs[KEY_AT] ?: return true
        return dismissedVersion != latestVersionCode || now - dismissedAt >= SNOOZE_MILLIS
    }

    suspend fun dismiss(latestVersionCode: Long) {
        dataStore.edit {
            it[KEY_VERSION] = latestVersionCode
            it[KEY_AT] = System.currentTimeMillis()
        }
    }

    private companion object {
        val KEY_VERSION = longPreferencesKey("dismissed_version")
        val KEY_AT = longPreferencesKey("dismissed_at")
        const val SNOOZE_MILLIS = 3L * 24 * 60 * 60 * 1000   // 3일
    }
}
```

강제 업데이트에는 이 기록을 적용하지 않는다.

## 12-4. ViewModel

```kotlin
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val appUpdateManager: AppUpdateManager,
    private val policyRepository: UpdatePolicyRepository,
    private val dismissStore: UpdateDismissStore,
    private val versionProvider: AppVersionProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateState())
    val state = _state.asStateFlow()

    private val _effect = Channel<UpdateEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var checkJob: Job? = null
    private var lastRequestedType = AppUpdateType.FLEXIBLE

    init {
        viewModelScope.launch {
            appUpdateManager.installStateFlow().collect(::onInstallState)
        }
    }

    fun onIntent(intent: UpdateIntent) {
        when (intent) {
            UpdateIntent.Check -> check()

            UpdateIntent.AcceptOptional -> viewModelScope.launch {
                setStage(UpdateStage.None)   // 다이얼로그 닫기. 이후 진행은 Downloading으로 표시됨
                val info = loadInfo()
                if (info != null && info.canUpdate(AppUpdateType.FLEXIBLE)) {
                    send(UpdateEffect.StartInAppUpdate(info, AppUpdateType.FLEXIBLE))
                } else {
                    send(UpdateEffect.OpenStore)
                }
            }

            UpdateIntent.DismissOptional -> {
                val stage = _state.value.stage
                if (stage is UpdateStage.Optional) {
                    viewModelScope.launch { dismissStore.dismiss(stage.latestVersionCode) }
                }
                setStage(UpdateStage.None)
            }

            UpdateIntent.ClickForceUpdate -> viewModelScope.launch {
                val info = loadInfo()
                if (info != null && info.canUpdate(AppUpdateType.IMMEDIATE)) {
                    send(UpdateEffect.StartInAppUpdate(info, AppUpdateType.IMMEDIATE))
                } else {
                    send(UpdateEffect.OpenStore)
                }
            }

            is UpdateIntent.InAppUpdateResult -> onInAppUpdateResult(intent.resultCode)

            UpdateIntent.CompleteUpdate -> appUpdateManager.completeUpdate()
        }
    }

    /** UI가 Effect를 받았을 때 호출. 런처는 전달만 하고 보관하지 않는다. */
    fun launchInAppUpdate(
        info: AppUpdateInfo,
        type: Int,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        lastRequestedType = type
        appUpdateManager.startUpdateFlowForResult(
            info,
            launcher,
            AppUpdateOptions.newBuilder(type).build(),
        )
    }

    private fun check() {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            val policy = policyRepository.fetch()
            val info = loadInfo()

            // 1. 이미 진행 중인 인앱 업데이트 이어가기
            if (info != null) {
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    setStage(UpdateStage.ReadyToInstall)
                    return@launch
                }
                if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    send(UpdateEffect.StartInAppUpdate(info, AppUpdateType.IMMEDIATE))
                    return@launch
                }
            }

            // 2. 정책 판단
            when (decideUpdateLevel(versionProvider.versionCode, policy)) {
                UpdateLevel.Force -> {
                    setStage(UpdateStage.Force(policy.message))
                    if (info != null && info.canUpdate(AppUpdateType.IMMEDIATE)) {
                        send(UpdateEffect.StartInAppUpdate(info, AppUpdateType.IMMEDIATE))
                    }
                }
                UpdateLevel.Optional -> {
                    if (dismissStore.shouldShow(policy.latestVersionCode)) {
                        setStage(UpdateStage.Optional(policy.latestVersionCode, policy.message))
                    } else {
                        setStage(UpdateStage.None)
                    }
                }
                UpdateLevel.None -> {
                    // 다운로드 중이거나 설치 대기 중인 Flexible 단계를 덮어쓰지 않는다
                    val current = _state.value.stage
                    if (current !is UpdateStage.Downloading && current !is UpdateStage.ReadyToInstall) {
                        setStage(UpdateStage.None)
                    }
                }
            }
        }
    }

    private fun onInAppUpdateResult(resultCode: Int) {
        when (resultCode) {
            Activity.RESULT_OK -> Unit   // Flexible: 다운로드가 시작됨. Immediate: 보통 앱이 재시작됨

            Activity.RESULT_CANCELED -> {
                // 강제: Force 화면이 그대로 남아 있어서 사용자가 다시 시도할 수 있음
                // 권장: 거절로 기록
                if (lastRequestedType == AppUpdateType.FLEXIBLE) {
                    val latest = (_state.value.stage as? UpdateStage.Optional)?.latestVersionCode
                    latest?.let { viewModelScope.launch { dismissStore.dismiss(it) } }
                }
            }

            else -> {
                // ActivityResult.RESULT_IN_APP_UPDATE_FAILED 등: 강제인 경우만 스토어로 폴백
                if (_state.value.stage is UpdateStage.Force) send(UpdateEffect.OpenStore)
            }
        }
    }

    private fun onInstallState(state: InstallState) {
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                val total = state.totalBytesToDownload()
                val progress = if (total > 0) state.bytesDownloaded().toFloat() / total else 0f
                setStage(UpdateStage.Downloading(progress))
            }
            InstallStatus.DOWNLOADED -> setStage(UpdateStage.ReadyToInstall)
            InstallStatus.FAILED, InstallStatus.CANCELED -> setStage(UpdateStage.None)
            else -> Unit
        }
    }

    private suspend fun loadInfo(): AppUpdateInfo? =
        try {
            appUpdateManager.requestAppUpdateInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null   // Play 서비스 없음, 네트워크 오류 등 -> 인앱 업데이트 불가로 취급
        }

    private fun AppUpdateInfo.canUpdate(type: Int): Boolean =
        updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && isUpdateTypeAllowed(type)

    private fun setStage(stage: UpdateStage) = _state.update { it.copy(stage = stage) }

    private fun send(effect: UpdateEffect) {
        viewModelScope.launch { _effect.send(effect) }
    }
}
```

`loadInfo()`에서 `CancellationException`을 다시 던지는 것을 잊지 않는다. `catch (e: Exception)`만 두면 코루틴 취소를 삼킨다.

<br>

# 13. UI 연결

## 13-1. 루트에서 한 번만 호스팅

```kotlin
// MainActivity
setContent {
    AppTheme {
        Box(Modifier.fillMaxSize()) {
            AppRoot()
            UpdateHost()   // 앱 전체 위에 덮어서 표시
        }
    }
}
```

`UpdateHost`를 화면마다 두지 않고 루트에 하나만 둔다. 어느 화면에 있든 강제 업데이트가 가장 위에서 표시된다.

## 13-2. UpdateHost

```kotlin
@Composable
fun UpdateHost(viewModel: UpdateViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val updateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onIntent(UpdateIntent.InAppUpdateResult(result.resultCode))
    }

    // 시작할 때와 스토어/Play 화면에서 돌아올 때마다 확인
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onIntent(UpdateIntent.Check)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is UpdateEffect.StartInAppUpdate ->
                    viewModel.launchInAppUpdate(effect.info, effect.type, updateLauncher)
                UpdateEffect.OpenStore -> context.openStore()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val stage = state.stage) {
            UpdateStage.None -> Unit

            is UpdateStage.Force -> ForceUpdateScreen(
                message = stage.message,
                onUpdateClick = { viewModel.onIntent(UpdateIntent.ClickForceUpdate) },
            )

            is UpdateStage.Optional -> OptionalUpdateDialog(
                message = stage.message,
                onUpdate = { viewModel.onIntent(UpdateIntent.AcceptOptional) },
                onLater = { viewModel.onIntent(UpdateIntent.DismissOptional) },
            )

            is UpdateStage.Downloading -> LinearProgressIndicator(
                progress = { stage.progress },
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            )

            UpdateStage.ReadyToInstall -> LaunchedEffect(Unit) {
                val result = snackbarHostState.showSnackbar(
                    message = "업데이트가 준비됐어요",
                    actionLabel = "재시작",
                    duration = SnackbarDuration.Indefinite,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.onIntent(UpdateIntent.CompleteUpdate)
                }
            }
        }

        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}
```

`ON_RESUME`마다 `Check`를 호출해도 부담이 크지 않다. Remote Config는 최소 fetch 간격 안에서는 캐시된 값을 바로 돌려준다.

## 13-3. 강제 업데이트 화면

```kotlin
@Composable
fun ForceUpdateScreen(
    message: String,
    onUpdateClick: () -> Unit,
) {
    // 뒤로 가기로 빠져나가지 못하게 막는다
    BackHandler(enabled = true) {}

    Surface(modifier = Modifier.fillMaxSize()) {   // 아래 화면의 터치를 막는다
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text("업데이트가 필요해요", style = MaterialTheme.typography.headlineSmall)
            Text(message, textAlign = TextAlign.Center)
            Button(onClick = onUpdateClick, modifier = Modifier.fillMaxWidth()) {
                Text("업데이트")
            }
        }
    }
}
```

강제 업데이트는 다이얼로그가 아니라 **전체 화면**으로 만드는 것이 안전하다. 다이얼로그는 바깥 터치, 뒤로 가기, 시스템 동작으로 닫힐 여지가 있다.

## 13-4. 권장 업데이트 다이얼로그

```kotlin
@Composable
fun OptionalUpdateDialog(
    message: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("새 버전이 나왔어요") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onUpdate) { Text("업데이트") } },
        dismissButton = { TextButton(onClick = onLater) { Text("나중에") } },
    )
}
```

<br>

# 14. 테스트

## 14-1. 정책 판단: 순수 함수 단위 테스트

```kotlin
class DecideUpdateLevelTest {

    private val policy = UpdatePolicy(minVersionCode = 105, latestVersionCode = 108, message = "")

    @Test fun 최소_버전_미만이면_Force() =
        assertEquals(UpdateLevel.Force, decideUpdateLevel(104, policy))

    @Test fun 최소_버전과_같으면_Force가_아니다() =
        assertEquals(UpdateLevel.Optional, decideUpdateLevel(105, policy))

    @Test fun 최신_버전_미만이면_Optional() =
        assertEquals(UpdateLevel.Optional, decideUpdateLevel(107, policy))

    @Test fun 최신_버전이면_None() =
        assertEquals(UpdateLevel.None, decideUpdateLevel(108, policy))

    @Test fun 기본값_0이면_아무것도_강제하지_않는다() =
        assertEquals(UpdateLevel.None, decideUpdateLevel(1, UpdatePolicy(0, 0, "")))

    @Test fun 정책이_min이_latest보다_커도_Force가_우선() =
        assertEquals(UpdateLevel.Force, decideUpdateLevel(100, UpdatePolicy(110, 108, "")))
}
```

## 14-2. 나중에 기록

`UpdateDismissStore.shouldShow()`는 `now`를 인자로 받으므로 시간을 고정해서 테스트할 수 있다.

- 기록이 없으면 `true`
- 같은 버전을 거절한 직후에는 `false`
- 같은 버전이라도 3일이 지나면 `true`
- 더 새로운 버전이 나오면 `true`

## 14-3. In-App Update 흐름: FakeAppUpdateManager

Play Core는 테스트용 `FakeAppUpdateManager`를 제공한다. Hilt 모듈을 테스트 모듈로 교체해서 주입한다. (Context가 필요하므로 계측 테스트나 Robolectric에서 사용)

```kotlin
val fake = FakeAppUpdateManager(context).apply {
    setUpdateAvailable(110, AppUpdateType.IMMEDIATE)
}

// 앱 실행 -> Force 화면 + Immediate 플로우 시작
assertTrue(fake.isImmediateFlowVisible)

fake.userAcceptsUpdate()
fake.downloadStarts()
fake.downloadCompletes()
fake.installCompletes()
```

Flexible은 `setUpdateAvailable(110, AppUpdateType.FLEXIBLE)`로 시작해서 `isConfirmationDialogVisible` → `userAcceptsUpdate()` → `downloadStarts()` → `downloadCompletes()` 순서로 진행하며 `Downloading`, `ReadyToInstall` 단계가 UI에 반영되는지 확인한다. `userRejectsUpdate()`는 거절 시 "나중에" 기록이 저장되는지, `downloadFails()`는 단계가 `None`으로 돌아오는지 검증하는 데 쓴다.

## 14-4. 실제 기기에서 확인하는 방법

| 방법 | 설명 |
| --- | --- |
| 내부 앱 공유 / 내부 테스트 트랙 | 낮은 `versionCode`를 Play로 설치한 뒤, 높은 `versionCode`를 같은 트랙에 올려서 확인 |
| Remote Config 조건 | 테스트 기기에만 `min_version_code`를 높게 적용해서 강제 화면 확인 |
| 디버그 빌드 | In-App Update는 동작하지 않으므로 Remote Config 정책과 스토어 이동 폴백까지만 확인 |

## 14-5. 시나리오 체크리스트

- 강제: 뒤로 가기, 홈으로 갔다가 복귀, 앱 재실행 모두에서 강제 화면이 유지된다.
- 강제: Play 업데이트 화면에서 취소하면 강제 화면의 버튼으로 다시 시도할 수 있다.
- 강제: Immediate 도중 백그라운드로 갔다 돌아오면 Play UI가 복원된다.
- 권장: "나중에"를 누르면 같은 버전은 3일 동안 다시 뜨지 않고, 더 새로운 버전에서는 바로 뜬다.
- 권장: Flexible 다운로드 후 재시작 안내가 뜨고, 안내를 놓쳐도 앱 복귀 시 다시 뜬다.
- 오프라인: Remote Config fetch 실패 시 마지막 정책(없으면 기본값 0)으로 동작하고 앱 시작이 막히지 않는다.
- Play 외 설치: 인앱 업데이트 없이 스토어 이동으로 폴백된다.

<br>

# 15. 흔한 실수

## 15-1. Remote Config만 올리고 Play 배포가 끝나기 전에 강제

가장 치명적인 실수다. `min_version_code`를 올렸는데 Play의 단계적 출시가 아직 100%가 아니면, 일부 사용자는 강제 화면에 갇히는데 스토어에도 업데이트가 아직 없다. **Play 출시가 100% 완료된 것을 확인한 뒤에 `min_version_code`를 올린다.**

## 15-2. 강제 업데이트를 닫을 수 있는 다이얼로그로 구현

뒤로 가기, 바깥 터치로 닫혀서 강제가 풀린다. 전체 화면 + `BackHandler`로 막는다.

## 15-3. Remote Config 기본값을 높게 설정

fetch에 실패한 사용자 전원이 강제 업데이트 화면에 갇힌다. 기본값은 0으로 두고, 정책 조회 실패는 "아무것도 강제하지 않음"으로 처리한다.

## 15-4. fetch를 무제한 기다림

스플래시나 앱 시작에서 `fetchAndActivate()`를 제한 없이 `await()`하면 느린 네트워크에서 앱이 시작되지 않는다. `withTimeoutOrNull`로 시간을 제한한다.

## 15-5. 최소 fetch 간격 때문에 긴급 정책이 반영되지 않음

기본 12시간이면 긴급 강제 업데이트가 늦게 반영된다. 릴리즈 간격을 줄이거나 Real-time Remote Config를 쓴다.

## 15-6. 버전 이름 문자열로 비교

"1.10.0"과 "1.9.0"을 문자열로 비교하면 순서가 뒤집힌다. `versionCode`(Long)로 비교한다.

## 15-7. 디버그/사이드로딩에서 In-App Update를 테스트하고 "안 된다"고 판단

Play에서 설치하지 않은 앱은 업데이트를 감지하지 못한다. 내부 앱 공유나 내부 테스트 트랙, `FakeAppUpdateManager`로 확인한다.

## 15-8. Immediate 재개 처리 누락

Immediate 업데이트 도중 앱이 백그라운드로 갔다가 돌아오면 Play 화면이 사라져서 구버전 앱이 그대로 보인다. 복귀 시 `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS`를 확인해서 다시 시작한다.

## 15-9. Flexible 다운로드 후 `completeUpdate()` 누락

다운로드만 되고 설치는 되지 않는다. `DOWNLOADED` 상태에서 재시작 안내를 계속 보여주고, 복귀 시에도 상태를 다시 확인한다.

## 15-10. 설치 상태 리스너를 해제하지 않음

`registerListener` 후 `unregisterListener`를 빼먹으면 누수가 생긴다. `callbackFlow`의 `awaitClose`에서 해제한다.

## 15-11. 권장 업데이트를 매 실행마다 노출

사용자 피로도가 높아지고 결국 앱 평점에 영향을 준다. 거절한 버전과 시각을 저장해서 일정 기간 다시 묻지 않는다.

## 15-12. 스토어 인텐트 예외 미처리

`market://`을 처리할 앱이 없으면 `ActivityNotFoundException`으로 크래시한다. `try/catch`로 웹 URL로 폴백한다.

## 15-13. `isUpdateTypeAllowed()`만 확인

업데이트가 없는데도 허용 여부는 true일 수 있다. `updateAvailability() == UPDATE_AVAILABLE`과 함께 확인한다.

## 15-14. 클라이언트 정책만 믿음

Remote Config 기반 강제는 앱이 정책을 지킨다는 전제다. 구버전이 반드시 막혀야 한다면 서버에서도 앱 버전을 검사해서 `426` 등으로 방어한다.

<br>

# 16. 전체 예제 (복붙용)

## 16-1. 계약 / 모델

```kotlin
sealed interface UpdateStage {
    data object None : UpdateStage
    data class Optional(val latestVersionCode: Long, val message: String) : UpdateStage
    data class Force(val message: String) : UpdateStage
    data class Downloading(val progress: Float) : UpdateStage
    data object ReadyToInstall : UpdateStage
}

data class UpdateState(val stage: UpdateStage = UpdateStage.None)

sealed interface UpdateIntent {
    data object Check : UpdateIntent
    data object AcceptOptional : UpdateIntent
    data object DismissOptional : UpdateIntent
    data object ClickForceUpdate : UpdateIntent
    data class InAppUpdateResult(val resultCode: Int) : UpdateIntent
    data object CompleteUpdate : UpdateIntent
}

sealed interface UpdateEffect {
    data class StartInAppUpdate(val info: AppUpdateInfo, val type: Int) : UpdateEffect
    data object OpenStore : UpdateEffect
}

data class UpdatePolicy(
    val minVersionCode: Long,
    val latestVersionCode: Long,
    val message: String,
)

enum class UpdateLevel { None, Optional, Force }
```

## 16-2. 코드 위치

| 코드 | 위치 |
| --- | --- |
| `installStateFlow()` | 7-2 |
| `UpdatePolicyRepository` | 9-2 |
| `decideUpdateLevel()` | 10-1 |
| `AppVersionProvider` | 10-2 |
| `openStore()` | 11 |
| `UpdateDismissStore` | 12-3 |
| `UpdateViewModel` | 12-4 |
| `UpdateHost`, `ForceUpdateScreen`, `OptionalUpdateDialog` | 13 |

위 코드를 그대로 사용한다.

## 16-3. Hilt 모듈

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    fun provideAppUpdateManager(@ApplicationContext context: Context): AppUpdateManager =
        AppUpdateManagerFactory.create(context)
}
```

테스트에서는 `@TestInstallIn`으로 이 모듈을 교체해서 `FakeAppUpdateManager`를 주입한다.

## 16-4. 의존성

```kotlin
// build.gradle.kts (app)

// In-App Update
implementation("com.google.android.play:app-update:<최신 버전>")
implementation("com.google.android.play:app-update-ktx:<최신 버전>")

// Remote Config (Firebase BoM 사용 권장)
implementation(platform("com.google.firebase:firebase-bom:<최신 버전>"))
implementation("com.google.firebase:firebase-config")
implementation("com.google.firebase:firebase-analytics")   // 조건/타깃팅에 사용
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:<최신 버전>")

// DataStore, lifecycle
implementation("androidx.datastore:datastore-preferences:<최신 버전>")
implementation("androidx.lifecycle:lifecycle-runtime-compose:<최신 버전>")
implementation("androidx.core:core-ktx:<최신 버전>")
```

Firebase 프로젝트 설정(`google-services.json`, `com.google.gms.google-services` 플러그인)이 먼저 되어 있어야 한다.

<br>

# 정리

1. 업데이트 안내에는 권장, 강제, 매끄러운 설치라는 서로 다른 요구가 섞여 있다. **정책은 Remote Config**, **설치 방식은 In-App Update**, **화면은 `UpdateStage` 하나**로 역할을 나눈다.
2. 단계는 `None / Optional / Force / Downloading / ReadyToInstall`로 sealed 표현한다. 플래그를 따로 두면 강제 화면과 다이얼로그가 동시에 뜨는 식의 불가능한 조합이 생긴다.
3. In-App Update는 Play 설치 앱에서만 동작한다. 권장은 **Flexible**(백그라운드 다운로드 후 `completeUpdate()`), 강제는 **Immediate**(Play 전체 화면)로 쓴다. 가능 여부는 `UPDATE_AVAILABLE`과 `isUpdateTypeAllowed()`를 함께 확인한다.
4. Remote Config는 `min_version_code`, `latest_version_code`, `update_message`로 설계하고 **versionCode(Long)** 로 비교한다. 기본값은 반드시 0(fail-open)으로 두고, fetch에는 시간 제한을 둔다. 긴급 반영이 필요하면 fetch 간격을 줄이거나 Real-time Remote Config를 쓴다.
5. 현재 버전과 정책의 비교는 순수 함수 `decideUpdateLevel()`로 분리해서 테스트한다. 강제는 Immediate를 즉시 시작하고, 권장은 사용자가 수락하면 Flexible을 시작한다.
6. 인앱 업데이트가 불가능하거나 실패하면 `market://`로 스토어 이동하고, `ActivityNotFoundException`이면 웹 URL로 폴백한다. Play 외 배포 채널은 flavor별로 스토어 URL을 분리한다.
7. 강제 업데이트는 다이얼로그가 아니라 전체 화면 + `BackHandler`로 막고, Immediate 도중 백그라운드 복귀, Play 화면 취소 후 재시도까지 처리한다. 권장 업데이트는 "나중에"를 거절한 버전과 시각으로 기록해서 매번 노출하지 않는다.
8. 확인은 `ON_RESUME`마다 한다. 앱 시작뿐 아니라 스토어나 Play 화면에서 돌아올 때도 상태를 다시 확인해야 한다.
9. 가장 큰 사고 원인은 **Play 단계적 출시가 끝나기 전에 `min_version_code`를 올리는 것**이다. 출시 100% 확인 후 정책을 올리고, 구버전이 반드시 막혀야 한다면 서버(`426` 등)에서도 방어한다.
10. 흔한 실수는 닫히는 강제 다이얼로그, 높은 Remote Config 기본값, 무제한 fetch 대기, 버전 이름 문자열 비교, 디버그 빌드로 인앱 업데이트 테스트, Immediate 재개 누락, `completeUpdate()` 누락, 리스너 미해제, 매 실행마다 권장 안내, 스토어 인텐트 예외 미처리다.
