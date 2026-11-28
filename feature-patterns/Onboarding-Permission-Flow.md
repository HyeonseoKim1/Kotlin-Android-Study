# 온보딩 / 권한 요청 화면: 단계별 안내, 권한 거부 처리

## 목차

1. 개요: 왜 "권한 요청 화면"이 복잡한가
2. 구성 요소
3. 가장 기본적인 구현
4. 상태 설계: 진실의 원천은 PermissionStatus
5. 권한 상태 판별: "처음 요청 전"과 "영구 거부" 구분하기
6. 요청 시점 설계: 온보딩 vs 맥락 기반
7. 단계 모델링: OnboardingStep
8. 요청 플로우와 거부 처리
9. 설정에서 돌아왔을 때
10. 특수한 권한들
11. MVI에서의 상태 관리
12. 페이저 UI 연결
13. 온보딩 완료 저장과 네비게이션
14. 테스트
15. 흔한 실수
16. 전체 예제 (복붙용)

<br>

# 1. 개요: 왜 "권한 요청 화면"이 복잡한가

권한 요청은 `launcher.launch(permission)` 한 줄이지만, 그 결과가 돌아온 뒤의 경우의 수가 많다.

1. 허용했다.
2. 거부했다. 다시 요청하면 시스템 다이얼로그가 또 뜬다.
3. 거부했는데 다시 요청해도 다이얼로그가 **뜨지 않는다.** (영구 거부)
4. 사용자가 설정 앱에 가서 권한을 바꾸고 돌아왔다.
5. "이번에만 허용"이었거나, 오래 쓰지 않아 시스템이 권한을 회수했다.

여기에 온보딩이 겹치면 "여러 단계 안내 + 단계마다 다른 권한 + 거부해도 이어지는 흐름"까지 관리해야 한다. 상태를 `isGranted`, `showRationale`, `showSettingsGuide` 같은 플래그로 따로 들고 있으면 금방 꼬인다.

해결 원칙은 세 가지다.

- 권한의 상태는 `PermissionStatus` 하나(`Granted / NotRequested / Denied / PermanentlyDenied`)로 표현한다.
- 온보딩의 현재 위치는 `currentIndex` 하나가 진실의 원천이고, 페이저는 그 값을 따라가기만 한다.
- 시스템 다이얼로그를 띄우기 **전에** 우리 화면에서 이유를 먼저 설명하고, 거부된 뒤에는 상태별로 다른 안내와 버튼을 보여준다.

데이터 흐름은 다음과 같다.

```text
단계 진입 -> (이미 허용이면 건너뜀) -> 설명 화면 -> [허용하기] -> 시스템 다이얼로그
   -> 허용        -> 다음 단계
   -> 거부        -> Denied: 이유 다시 안내 + [다시 요청하기]
   -> 영구 거부   -> PermanentlyDenied: [설정에서 허용하기] -> 설정 -> 복귀(ON_RESUME) -> 재확인
```

<br>

# 2. 구성 요소

| 구성 요소 | 역할 |
| --- | --- |
| `AppPermission` | 앱이 다루는 권한을 하나로 묶은 enum (매니페스트 권한, 필수 여부, 최소 SDK) |
| `PermissionStatus` | 권한의 현재 상태 4종 |
| `resolveStatus()` | "허용 여부 / 거부 이력 / 요청 이력"으로 상태를 계산하는 순수 함수 |
| `PermissionHistoryRepository` | 이 권한을 이전에 요청한 적이 있는지 저장 (DataStore) |
| `OnboardingStep` | 온보딩의 한 화면 (소개 화면 / 권한 화면) |
| `OnboardingViewModel` | 현재 단계, 권한 상태, 건너뛰기/진행 규칙을 관리 |
| `OnboardingScreen` | `HorizontalPager`로 단계를 보여주고 런처를 실행 |
| `OnboardingRepository` | 온보딩 완료 여부 저장 (DataStore) |

`shouldShowRequestPermissionRationale()`은 `Activity`가 필요하기 때문에 ViewModel이 직접 호출할 수 없다. 그래서 UI가 "허용됨 / 설명 필요 여부"라는 **사실(facts)** 만 모아서 ViewModel에 전달하고, 상태 판단은 ViewModel(순수 함수)이 한다.

<br>

# 3. 가장 기본적인 구현

한 화면에서 권한을 한 번 요청하는 최소 형태다.

```kotlin
@Composable
fun SimplePermissionButton(onGranted: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onGranted()
    }

    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
        Text("카메라 허용")
    }
}
```

동작은 하지만 곧 문제가 보인다.

- 거부하면 아무 안내가 없다. 사용자는 왜 기능이 안 되는지 모른다.
- Android 11부터는 같은 권한을 두 번 거부하면 시스템이 더 이상 다이얼로그를 띄우지 않는다. 버튼을 눌러도 아무 반응이 없는 것처럼 보인다.
- 설정 앱에서 권한을 켜고 돌아와도 앱은 그 사실을 알지 못한다.
- 온보딩 첫 화면에서 권한을 연달아 요청하면 사용자는 이유를 모른 채 "거부"를 누른다.

이후 챕터는 이 기본형을 보완해 나간다.

<br>

# 4. 상태 설계: 진실의 원천은 PermissionStatus

## 4-1. 플래그를 따로 두면 안 되는 이유

```kotlin
// 안티패턴
data class State(
    val isGranted: Boolean = false,
    val isDenied: Boolean = false,
    val showRationale: Boolean = false,
    val showSettingsGuide: Boolean = false,
)
```

- `isGranted = true`인데 `showSettingsGuide = true`인 식의 불가능한 조합이 생긴다.
- 어떤 플래그를 언제 초기화해야 하는지 코드 곳곳에서 신경 써야 한다.
- 화면 분기가 `if` 연쇄가 된다.

## 4-2. 상태를 sealed가 아니라 enum 4종으로

권한 상태는 추가 데이터가 필요 없으므로 enum으로 충분하다.

```kotlin
enum class PermissionStatus {
    NotRequested,       // 아직 한 번도 요청하지 않음
    Denied,             // 거부했지만 다시 요청할 수 있음 (시스템 다이얼로그가 다시 뜸)
    PermanentlyDenied,  // 더 이상 다이얼로그가 뜨지 않음 -> 설정으로 안내
    Granted,
}
```

UI는 이 값 하나로 문구, 버튼, 다음 동작을 모두 결정한다. `when`이 exhaustive라서 상태를 추가하면 빠진 분기를 컴파일러가 알려준다.

<br>

# 5. 권한 상태 판별: "처음 요청 전"과 "영구 거부" 구분하기

## 5-1. 안드로이드가 알려주는 것

| API | 알려주는 것 |
| --- | --- |
| `ContextCompat.checkSelfPermission()` | 지금 허용되어 있는가 |
| `shouldShowRequestPermissionRationale()` | 이전에 거부한 적이 있고, 다시 요청하면 다이얼로그가 뜨는가 |

문제는 `shouldShowRequestPermissionRationale()`이 **"아직 한 번도 요청하지 않았을 때"와 "다시 묻지 않음으로 거부했을 때" 모두 `false`** 를 돌려준다는 점이다. 이 값만으로는 두 상태를 구분할 수 없다.

## 5-2. 해결: 요청한 적이 있는지 직접 기록

권한 요청의 결과가 돌아올 때마다 "이 권한은 요청한 적 있음"을 DataStore에 저장하고, 세 값을 조합해서 상태를 계산한다.

| 허용 | 설명 필요(rationale) | 요청 이력 | 상태 |
| --- | --- | --- | --- |
| true | - | - | `Granted` |
| false | true | - | `Denied` |
| false | false | true | `PermanentlyDenied` |
| false | false | false | `NotRequested` |

```kotlin
fun resolveStatus(
    granted: Boolean,
    shouldShowRationale: Boolean,
    requestedBefore: Boolean,
): PermissionStatus = when {
    granted -> PermissionStatus.Granted
    shouldShowRationale -> PermissionStatus.Denied
    requestedBefore -> PermissionStatus.PermanentlyDenied
    else -> PermissionStatus.NotRequested
}
```

외부 의존이 없는 순수 함수라서 표 그대로 단위 테스트할 수 있다. (14장)

## 5-3. UI가 모아서 보내는 사실(facts)

```kotlin
data class PermissionFacts(
    val granted: Boolean,
    val shouldShowRationale: Boolean,
)

fun Activity.permissionFacts(permission: AppPermission): PermissionFacts = PermissionFacts(
    granted = permission.isGranted(this),
    shouldShowRationale = permission.manifestPermissions.any {
        ActivityCompat.shouldShowRequestPermissionRationale(this, it)
    },
)
```

## 5-4. 요청 이력 저장

```kotlin
interface PermissionHistoryRepository {
    val requestedPermissions: Flow<Set<AppPermission>>
    suspend fun markRequested(permission: AppPermission)
}

private val Context.permissionHistoryDataStore by preferencesDataStore(name = "permission_history")

@Singleton
class DataStorePermissionHistoryRepository @Inject constructor(
    @ApplicationContext context: Context,
) : PermissionHistoryRepository {

    private val dataStore = context.permissionHistoryDataStore

    override val requestedPermissions: Flow<Set<AppPermission>> = dataStore.data.map { prefs ->
        val names = prefs[KEY_REQUESTED] ?: emptySet()
        AppPermission.entries.filter { it.name in names }.toSet()
    }

    override suspend fun markRequested(permission: AppPermission) {
        dataStore.edit { prefs ->
            prefs[KEY_REQUESTED] = (prefs[KEY_REQUESTED] ?: emptySet()) + permission.name
        }
    }

    private companion object {
        val KEY_REQUESTED = stringSetPreferencesKey("requested")
    }
}
```

기록 시점은 시스템 다이얼로그를 **띄울 때가 아니라 결과가 돌아왔을 때**로 한다. 띄운 직후 프로세스가 죽는 경우에도 결과를 못 받았다면 "요청했다"고 볼 수 없기 때문이다.

## 5-5. 이력은 백업에서 제외

자동 백업이 켜져 있으면 요청 이력이 새 기기로 복원된다. 새 기기의 권한은 모두 초기화되어 있으므로 "요청 이력 있음 + rationale false"가 되어 아직 한 번도 묻지 않은 권한이 `PermanentlyDenied`로 오판된다.

```xml
<!-- res/xml/data_extraction_rules.xml (Android 12+) -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="datastore/permission_history.preferences_pb" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="datastore/permission_history.preferences_pb" />
    </device-transfer>
</data-extraction-rules>
```

```xml
<!-- res/xml/backup_rules.xml (Android 11 이하) -->
<full-backup-content>
    <exclude domain="file" path="datastore/permission_history.preferences_pb" />
</full-backup-content>
```

## 5-6. 권한 모델

```kotlin
enum class AppPermission(
    val manifestPermissions: List<String>,
    val required: Boolean,
    val minSdk: Int = 1,
    val anyGrantedIsEnough: Boolean = false,
) {
    NOTIFICATION(
        manifestPermissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
        required = false,
        minSdk = 33,
    ),
    CAMERA(
        manifestPermissions = listOf(Manifest.permission.CAMERA),
        required = true,   // 예시: 앱의 핵심 기능에 꼭 필요한 권한
    ),
    LOCATION(
        manifestPermissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
        required = false,
        anyGrantedIsEnough = true,   // 사용자가 "대략적인 위치"만 허용해도 허용으로 본다
    );

    fun isApplicable(sdkInt: Int): Boolean = sdkInt >= minSdk

    fun isGranted(context: Context): Boolean {
        val results = manifestPermissions.map {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        return if (anyGrantedIsEnough) results.any { it } else results.all { it }
    }
}
```

매니페스트에 선언하지 않은 권한은 요청해도 다이얼로그 없이 바로 거부된다. 사용하는 권한은 모두 `<uses-permission>`으로 선언한다. (16장)

<br>

# 6. 요청 시점 설계: 온보딩 vs 맥락 기반

## 6-1. 언제 요청할 것인가

| 방식 | 장점 | 단점 |
| --- | --- | --- |
| 온보딩에서 미리 요청 | 한 번에 끝나고 이후 흐름이 매끄러움 | 맥락이 없어서 거부율이 높음 |
| 맥락 기반 (기능을 쓰는 순간 요청) | "왜 필요한지"가 자연스럽게 전달되어 허용률이 높음 | 사용 흐름이 중간에 끊김 |
| 혼합 (권장) | 앱의 핵심 기능과 알림처럼 가치 설명이 쉬운 권한만 온보딩에서 설명 후 요청, 나머지는 사용하는 순간 요청 | 어떤 권한을 어디서 받을지 설계가 필요 |

권한마다 요청 시점을 정하는 기준은 다음과 같다.

- 앱의 핵심 기능에 꼭 필요한가 → 온보딩에서 설명하고 요청
- 가치를 한 문장으로 설명할 수 있는가(예: 알림) → 온보딩에서 요청해도 좋음
- 특정 기능에서만 쓰이는가 → 그 기능을 처음 쓰는 순간 요청

## 6-2. 온보딩의 권한 화면은 "사전 설명"이다

시스템 다이얼로그는 문구를 바꿀 수 없고 한 번 거부되면 되살리기 어렵다. 그래서 우리 화면에서 먼저 설명하고, 사용자가 "허용하기"를 눌렀을 때만 시스템 다이얼로그를 띄운다. 설명 화면에서 "나중에 하기"를 누른 사용자는 시스템 다이얼로그를 아직 소모하지 않은 상태로 남는다.

## 6-3. 설명 문구에 들어갈 세 가지

| 항목 | 예시 (알림) |
| --- | --- |
| 무엇을 | 새 메시지가 도착하면 알림으로 알려드려요 |
| 왜 필요한지 (사용자에게 이익) | 앱을 열지 않아도 답장을 놓치지 않아요 |
| 거부하면 | 알림 없이도 앱은 쓸 수 있고, 나중에 설정에서 켤 수 있어요 |

"앱 개선을 위해 필요합니다" 같은 모호한 문구는 거부율을 높인다. 또한 Play 정책도 앱 기능에 필요한 권한만 요청하고 그 목적을 사용자에게 알리는 것을 요구한다.

## 6-4. 거부해도 앱은 계속 쓸 수 있어야 한다

권한이 없다고 앱 전체를 막는 것이 아니라 **그 권한이 필요한 기능만** 제한한다. 정말로 권한 없이는 앱이 성립하지 않는 경우(카메라 앱의 카메라 등)에만 `required = true`로 두고, 그 외에는 건너뛰기를 허용한다.

<br>

# 7. 단계 모델링: OnboardingStep

온보딩의 한 화면은 두 종류다.

```kotlin
sealed interface OnboardingStep {

    /** 서비스 소개 화면 */
    data class Intro(
        val title: String,
        val description: String,
        @DrawableRes val imageRes: Int,
    ) : OnboardingStep

    /** 권한 요청 화면 */
    data class Permission(
        val permission: AppPermission,
        val title: String,
        val description: String,
        @DrawableRes val imageRes: Int,
    ) : OnboardingStep
}
```

## 7-1. 단계 구성

```kotlin
fun buildSteps(sdkInt: Int): List<OnboardingStep> = buildList {
    add(
        OnboardingStep.Intro(
            title = "기록을 한곳에",
            description = "흩어져 있던 기록을 한 앱에서 관리해요",
            imageRes = R.drawable.onboarding_intro_1,
        )
    )
    add(
        OnboardingStep.Intro(
            title = "언제든 꺼내 보기",
            description = "필요한 순간에 바로 찾아볼 수 있어요",
            imageRes = R.drawable.onboarding_intro_2,
        )
    )

    // 권한 단계: 해당 SDK에서 적용되는 것만
    if (AppPermission.NOTIFICATION.isApplicable(sdkInt)) {
        add(
            OnboardingStep.Permission(
                permission = AppPermission.NOTIFICATION,
                title = "알림을 받아보세요",
                description = "새 소식이 도착하면 알려드려요. 거부해도 앱은 그대로 쓸 수 있어요",
                imageRes = R.drawable.onboarding_notification,
            )
        )
    }
    add(
        OnboardingStep.Permission(
            permission = AppPermission.CAMERA,
            title = "카메라 접근이 필요해요",
            description = "사진을 찍어 바로 기록하려면 카메라 권한이 필요해요",
            imageRes = R.drawable.onboarding_camera,
        )
    )
    add(
        OnboardingStep.Permission(
            permission = AppPermission.LOCATION,
            title = "위치를 함께 기록해요",
            description = "기록한 장소를 자동으로 남겨드려요. 나중에 설정에서 바꿀 수 있어요",
            imageRes = R.drawable.onboarding_location,
        )
    )
}
```

실제 앱에서는 문구를 `string` 리소스로 둔다. 예시 코드의 편의를 위해 문자열을 직접 적었다.

`sdkInt`를 인자로 받으면 "Android 13 미만에서는 알림 단계가 생략된다" 같은 규칙을 단위 테스트에서 직접 검증할 수 있다.

<br>

# 8. 요청 플로우와 거부 처리

## 8-1. 상태별 화면과 버튼

| 상태 | 안내 문구 | 주 버튼 | 보조 버튼 |
| --- | --- | --- | --- |
| `NotRequested` | 단계의 설명 문구 | 허용하기 → 시스템 다이얼로그 | 나중에 하기 (선택 권한만) |
| `Denied` | 이유를 다시 안내. 필수라면 "이 권한이 없으면 핵심 기능을 쓸 수 없어요" | 다시 요청하기 → 시스템 다이얼로그 | 나중에 하기 (선택 권한만) |
| `PermanentlyDenied` | "시스템 설정에서 직접 켜야 해요" | 설정에서 허용하기 → 앱 설정 화면 | 나중에 하기 (선택 권한만) |
| `Granted` | 허용됐어요 | 다음 | - |

같은 버튼 자리에 상태에 따라 다른 동작이 연결된다. 영구 거부 상태에서 "허용하기"를 눌러도 시스템 다이얼로그는 뜨지 않으므로, 이때는 반드시 설정으로 안내해야 한다.

## 8-2. 필수 / 선택 권한의 처리

| 구분 | 건너뛰기 | 거부 후 동작 |
| --- | --- | --- |
| 선택 (`required = false`) | "나중에 하기" 버튼 제공 | 다음 단계로 진행. 해당 기능을 처음 쓸 때 다시 요청 |
| 필수 (`required = true`) | 버튼 없음 | 해당 단계에 머물며 다시 요청/설정 이동만 가능 |

필수 권한 단계에서 사용자를 영원히 붙잡아 두는 것이 부담스러운 서비스라면 필수 권한도 건너뛸 수 있게 하되, 기능 진입점에서 다시 안내하는 방식으로 완화한다.

## 8-3. 시스템 다이얼로그가 뜨지 않고 바로 "거부"가 돌아오는 경우

영구 거부 상태에서 `launch()`를 호출하면 다이얼로그 없이 곧바로 거부 결과가 돌아온다. 결과 콜백에서 다시 상태를 계산하면 `PermanentlyDenied`로 판정되어 화면이 "설정에서 허용하기"로 바뀐다. 사용자 입장에서는 버튼을 눌렀는데 아무 반응이 없는 것처럼 느낄 수 있으므로, 결과가 `PermanentlyDenied`로 바뀌는 순간 문구가 함께 바뀌도록 한다.

## 8-4. 설정 화면 열기

```kotlin
fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
    )
}

fun Context.openNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
    )
}
```

알림 권한은 앱 정보 화면보다 알림 설정 화면으로 바로 보내는 편이 사용자의 클릭 수를 줄인다. (`ACTION_APP_NOTIFICATION_SETTINGS`는 API 26+)

<br>

# 9. 설정에서 돌아왔을 때

## 9-1. ON_RESUME에서 반드시 다시 확인

사용자가 설정에서 권한을 켜고 앱으로 돌아오면 `Activity`는 `onResume`을 거친다. 이때 권한 상태를 다시 읽어서 ViewModel에 전달하면, 현재 단계가 허용 상태로 바뀐 것을 감지해 자동으로 다음 단계로 넘어갈 수 있다.

```kotlin
LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    viewModel.onIntent(OnboardingIntent.FactsUpdated(collectFacts()))
}
```

권한 상태를 한 번 읽고 계속 쓰면 안 된다. 다음 이유로 앱이 보이지 않는 사이에 값이 바뀔 수 있다.

- 설정 앱에서 사용자가 직접 켜거나 끔
- "이번에만 허용"(카메라, 마이크, 위치. Android 11+)은 앱이 백그라운드로 가면 회수됨
- 수개월간 사용하지 않은 앱은 시스템이 권한을 자동으로 초기화할 수 있음

## 9-2. 권한을 끄면 프로세스가 종료될 수 있다

사용자가 설정에서 권한을 **끄면** 시스템이 앱 프로세스를 종료할 수 있다. (켤 때는 종료되지 않는다.) 온보딩 도중에 이 일이 생기면 앱이 처음부터 다시 시작된다. 현재 단계를 `SavedStateHandle`에 저장해 두면 같은 단계에서 이어갈 수 있다. (11장)

## 9-3. 자동 전환 규칙

설정 복귀 시 자동으로 다음 단계로 넘어가는 동작은 아래 규칙으로 제한한다.

| 상황 | 동작 |
| --- | --- |
| 현재 단계가 `Granted`로 **바뀌었다** (이전엔 아니었음) | 자동으로 다음 단계 |
| 이미 `Granted`인 단계에 "이전" 버튼으로 돌아옴 | 자동 전환하지 않음. 허용됨 표시와 "다음" 버튼 |
| 앞으로 진행하다가 이미 허용된 권한 단계를 만남 | 그 단계를 건너뜀 |

세 번째 규칙은 재설치/권한 복원 후 이미 허용된 권한을 다시 설명하는 낭비를 없앤다. 두 번째 규칙은 "이전"으로 돌아왔다가 곧바로 앞으로 튕겨 나가는 함정을 막는다.

<br>

# 10. 특수한 권한들

| 권한 | 특징 | 처리 |
| --- | --- | --- |
| 알림 (`POST_NOTIFICATIONS`) | API 33+에서 런타임 권한. 그 미만은 선언만으로 허용되지만 사용자가 시스템 설정에서 끌 수 있음 | 33 미만은 온보딩 단계에서 생략하고, 알림 기능에서 `NotificationManagerCompat.areNotificationsEnabled()`로 확인 |
| 위치 (`FINE` / `COARSE`) | 함께 요청하며, 사용자가 "대략적인 위치"만 허용할 수 있음 | 둘 중 하나라도 허용되면 허용으로 취급하고, 정밀 위치가 필요한 기능에서 `FINE`을 따로 확인 |
| 백그라운드 위치 (`ACCESS_BACKGROUND_LOCATION`) | 포그라운드 위치 허용 후에 **별도로** 요청해야 하고 동시에 요청할 수 없음. API 30+에서는 다이얼로그가 아니라 설정 화면에서 "항상 허용"을 고르는 방식 | 별도 단계로 분리하고 사전 고지를 충분히 하며, Play 정책 심사 대상인지 확인 |
| 사진/미디어 | Photo Picker를 쓰면 권한이 필요 없음 | 권한 요청 대신 Photo Picker 사용 (이미지 업로드 문서 참고) |
| 카메라 / 마이크 | API 30+에서 "이번에만 허용" 선택 가능 | 사용 직전에 매번 상태 확인 |
| 정확한 알람 (`SCHEDULE_EXACT_ALARM`) | 런타임 다이얼로그가 없는 "특수 앱 액세스". 버전과 용도에 따라 기본 허용 여부가 다름 | `AlarmManager.canScheduleExactAlarms()`로 확인하고, 없으면 `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`으로 설정 화면 안내 |

특수 앱 액세스류는 `RequestPermission` 런처로 처리되지 않으므로, 이 문서의 `PermissionStatus` 흐름과 분리해서 다룬다.

<br>

# 11. MVI에서의 상태 관리

## 11-1. 무엇을 어디에 두는가

| 종류 | 위치 | 이유 |
| --- | --- | --- |
| 현재 단계(`currentIndex`) | `UiState` + `SavedStateHandle` | 화면이 다시 그려져도, 프로세스가 죽었다 살아나도 유지 |
| 권한별 상태(`statuses`) | `UiState` | UI가 문구와 버튼을 결정하는 근거 |
| 권한 사실(`facts`), 요청 이력(`requested`) | ViewModel 내부 필드 | `statuses`를 계산하는 재료. UI가 직접 알 필요 없음 |
| 시스템 다이얼로그 실행, 설정 이동 | `Effect` | 1회성 명령 |
| 권한 요청 결과, 설정에서 복귀 | `Intent` | 시스템/사용자가 발생시킨 입력 |
| 온보딩 완료 이후 화면 전환 | 별도 이벤트 없음 | 완료 여부 값을 루트가 읽어 자동 전환 (13장) |

## 11-2. 계약

```kotlin
data class OnboardingState(
    val steps: List<OnboardingStep>,
    val currentIndex: Int = 0,
    val statuses: Map<AppPermission, PermissionStatus> = emptyMap(),
) {
    val currentStep: OnboardingStep get() = steps[currentIndex]
    val isLast: Boolean get() = currentIndex == steps.lastIndex

    val currentStatus: PermissionStatus?
        get() = (currentStep as? OnboardingStep.Permission)?.let { statuses[it.permission] }

    /** 선택 권한 단계에서만 건너뛸 수 있다 */
    val canSkip: Boolean
        get() = (currentStep as? OnboardingStep.Permission)?.permission?.required == false
}

sealed interface OnboardingIntent {
    data object ClickPrimary : OnboardingIntent
    data object ClickSkip : OnboardingIntent
    data object ClickBack : OnboardingIntent
    data class FactsUpdated(val facts: Map<AppPermission, PermissionFacts>) : OnboardingIntent
    data class PermissionResult(val permission: AppPermission, val facts: PermissionFacts) : OnboardingIntent
}

sealed interface OnboardingEffect {
    data class RequestPermission(val permission: AppPermission) : OnboardingEffect
    data class OpenSettings(val permission: AppPermission) : OnboardingEffect
}
```

## 11-3. ViewModel

```kotlin
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val onboardingRepository: OnboardingRepository,
    private val permissionHistory: PermissionHistoryRepository,
) : ViewModel() {

    private val steps = buildSteps(Build.VERSION.SDK_INT)

    private val _state = MutableStateFlow(
        OnboardingState(
            steps = steps,
            currentIndex = (savedStateHandle.get<Int>(KEY_STEP) ?: 0).coerceIn(0, steps.lastIndex),
        )
    )
    val state = _state.asStateFlow()

    private val _effect = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var facts: Map<AppPermission, PermissionFacts> = emptyMap()
    private var requested: Set<AppPermission> = emptySet()

    init {
        viewModelScope.launch {
            requested = permissionHistory.requestedPermissions.first()
            recompute()
        }
    }

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.ClickPrimary -> onPrimary()

            OnboardingIntent.ClickSkip -> if (_state.value.canSkip) advance()

            OnboardingIntent.ClickBack -> {
                val index = _state.value.currentIndex
                if (index > 0) moveTo(index - 1)
            }

            is OnboardingIntent.FactsUpdated -> {
                val before = _state.value.currentStatus
                facts = intent.facts
                recompute()
                advanceIfJustGranted(before)
            }

            is OnboardingIntent.PermissionResult -> viewModelScope.launch {
                val before = _state.value.currentStatus
                permissionHistory.markRequested(intent.permission)   // 결과를 받은 시점에 기록
                requested = requested + intent.permission
                facts = facts + (intent.permission to intent.facts)
                recompute()
                advanceIfJustGranted(before)
            }
        }
    }

    private fun onPrimary() {
        when (val step = _state.value.currentStep) {
            is OnboardingStep.Intro -> advance()
            is OnboardingStep.Permission -> {
                val status = _state.value.statuses[step.permission] ?: PermissionStatus.NotRequested
                when (status) {
                    PermissionStatus.Granted -> advance()
                    PermissionStatus.NotRequested,
                    PermissionStatus.Denied -> send(OnboardingEffect.RequestPermission(step.permission))
                    PermissionStatus.PermanentlyDenied -> send(OnboardingEffect.OpenSettings(step.permission))
                }
            }
        }
    }

    /** 현재 권한 단계가 방금 Granted가 되었다면 다음 단계로 */
    private fun advanceIfJustGranted(before: PermissionStatus?) {
        val after = _state.value.currentStatus
        if (before != PermissionStatus.Granted && after == PermissionStatus.Granted) advance()
    }

    private fun advance() {
        val next = nextIndex(_state.value.currentIndex)
        if (next > steps.lastIndex) {
            viewModelScope.launch { onboardingRepository.setCompleted() }   // 루트가 감지해서 화면 전환
        } else {
            moveTo(next)
        }
    }

    /** 앞으로 갈 때 이미 허용된 권한 단계는 건너뛴다 */
    private fun nextIndex(from: Int): Int {
        var i = from + 1
        while (i <= steps.lastIndex) {
            val step = steps[i]
            val alreadyGranted = step is OnboardingStep.Permission &&
                _state.value.statuses[step.permission] == PermissionStatus.Granted
            if (!alreadyGranted) break
            i++
        }
        return i
    }

    private fun moveTo(index: Int) {
        savedStateHandle[KEY_STEP] = index
        _state.update { it.copy(currentIndex = index) }
    }

    private fun recompute() {
        val statuses = facts.mapValues { (permission, f) ->
            resolveStatus(
                granted = f.granted,
                shouldShowRationale = f.shouldShowRationale,
                requestedBefore = permission in requested,
            )
        }
        _state.update { it.copy(statuses = statuses) }
    }

    private fun send(effect: OnboardingEffect) {
        viewModelScope.launch { _effect.send(effect) }
    }

    private companion object {
        const val KEY_STEP = "step"
    }
}
```

`advance()`에서 온보딩 완료를 저장하는 시점은 **마지막 단계의 버튼을 눌렀을 때**다. 마지막 화면에 진입한 순간 저장하면 그 화면에서 앱을 종료한 사용자는 온보딩을 끝낸 것으로 처리된다.

<br>

# 12. 페이저 UI 연결

## 12-1. 페이저는 상태를 따라가기만 한다

```kotlin
val pagerState = rememberPagerState(initialPage = state.currentIndex) { state.steps.size }

// currentIndex가 진실의 원천. 페이저는 그 값을 따라간다
LaunchedEffect(state.currentIndex) {
    pagerState.animateScrollToPage(state.currentIndex)
}

HorizontalPager(
    state = pagerState,
    userScrollEnabled = false,   // 스와이프로 단계를 건너뛰지 못하게 막는다
    modifier = Modifier.weight(1f),
) { page ->
    OnboardingPage(step = state.steps[page], status = ...)
}
```

`userScrollEnabled = false`가 중요하다. 스와이프를 허용하면 권한 단계를 건너뛰거나 `currentIndex`와 페이저 위치가 어긋난다. 단계 이동은 항상 ViewModel(`currentIndex`)을 거치게 한다.

## 12-2. 런처 구성

모든 권한을 `RequestMultiplePermissions` 하나로 처리하면 위치(FINE + COARSE)처럼 여러 권한이 묶인 경우도 같은 코드로 다룰 수 있다.

```kotlin
var pendingPermission by rememberSaveable { mutableStateOf<AppPermission?>(null) }

val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) {
    pendingPermission?.let { permission ->
        viewModel.onIntent(
            OnboardingIntent.PermissionResult(permission, activity.permissionFacts(permission))
        )
    }
    pendingPermission = null
}
```

`pendingPermission`을 `rememberSaveable`로 두는 이유는 시스템 다이얼로그가 떠 있는 동안 프로세스가 죽었다 살아나도 결과를 올바른 권한에 연결하기 위해서다.

## 12-3. 화면

```kotlin
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(initialPage = state.currentIndex) { state.steps.size }

    var pendingPermission by rememberSaveable { mutableStateOf<AppPermission?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        pendingPermission?.let { permission ->
            viewModel.onIntent(
                OnboardingIntent.PermissionResult(permission, activity.permissionFacts(permission))
            )
        }
        pendingPermission = null
    }

    fun collectFacts(): Map<AppPermission, PermissionFacts> =
        AppPermission.entries.associateWith { activity.permissionFacts(it) }

    // 시작할 때, 그리고 설정 앱에서 돌아올 때마다 다시 확인
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onIntent(OnboardingIntent.FactsUpdated(collectFacts()))
    }

    LaunchedEffect(state.currentIndex) {
        pagerState.animateScrollToPage(state.currentIndex)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is OnboardingEffect.RequestPermission -> {
                    pendingPermission = effect.permission
                    permissionLauncher.launch(effect.permission.manifestPermissions.toTypedArray())
                }
                is OnboardingEffect.OpenSettings ->
                    if (effect.permission == AppPermission.NOTIFICATION) context.openNotificationSettings()
                    else context.openAppSettings()
            }
        }
    }

    Scaffold(
        bottomBar = {
            OnboardingBottomBar(
                state = state,
                onPrimary = { viewModel.onIntent(OnboardingIntent.ClickPrimary) },
                onSkip = { viewModel.onIntent(OnboardingIntent.ClickSkip) },
                onBack = { viewModel.onIntent(OnboardingIntent.ClickBack) },
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) { page ->
            val step = state.steps[page]
            OnboardingPage(
                step = step,
                status = (step as? OnboardingStep.Permission)?.let { state.statuses[it.permission] },
            )
        }
    }
}
```

## 12-4. 각 단계의 내용

```kotlin
@Composable
private fun OnboardingPage(step: OnboardingStep, status: PermissionStatus?) {
    val (title, description, imageRes) = when (step) {
        is OnboardingStep.Intro -> Triple(step.title, step.description, step.imageRes)
        is OnboardingStep.Permission -> Triple(step.title, step.description, step.imageRes)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            modifier = Modifier.size(200.dp),
        )
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(description, textAlign = TextAlign.Center)

        if (step is OnboardingStep.Permission) {
            statusNote(status, step.permission.required)?.let {
                Text(
                    text = it,
                    color = if (status == PermissionStatus.Granted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 상태별 안내 문구. 순수 함수라 테스트하기 쉽다 */
fun statusNote(status: PermissionStatus?, required: Boolean): String? = when (status) {
    PermissionStatus.Denied ->
        if (required) "이 권한이 없으면 핵심 기능을 사용할 수 없어요"
        else "나중에 설정에서 언제든 켤 수 있어요"
    PermissionStatus.PermanentlyDenied -> "시스템 설정에서 직접 켜야 해요"
    PermissionStatus.Granted -> "허용됐어요"
    PermissionStatus.NotRequested, null -> null
}
```

## 12-5. 하단 바

```kotlin
@Composable
private fun OnboardingBottomBar(
    state: OnboardingState,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 진행 표시
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(state.steps.size) { index ->
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == state.currentIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
            Text(primaryLabel(state.currentStep, state.currentStatus, state.isLast))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (state.currentIndex > 0) TextButton(onClick = onBack) { Text("이전") } else Spacer(Modifier)
            if (state.canSkip) TextButton(onClick = onSkip) { Text("나중에 하기") }
        }
    }
}

fun primaryLabel(step: OnboardingStep, status: PermissionStatus?, isLast: Boolean): String = when (step) {
    is OnboardingStep.Intro -> if (isLast) "시작하기" else "다음"
    is OnboardingStep.Permission -> when (status ?: PermissionStatus.NotRequested) {
        PermissionStatus.Granted -> if (isLast) "시작하기" else "다음"
        PermissionStatus.NotRequested -> "허용하기"
        PermissionStatus.Denied -> "다시 요청하기"
        PermissionStatus.PermanentlyDenied -> "설정에서 허용하기"
    }
}
```

`primaryLabel()`도 순수 함수라서 상태별 문구를 단위 테스트로 고정할 수 있다.

<br>

# 13. 온보딩 완료 저장과 네비게이션

## 13-1. 완료 여부 저장

```kotlin
interface OnboardingRepository {
    val completed: Flow<Boolean>
    suspend fun setCompleted()
}

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

@Singleton
class DataStoreOnboardingRepository @Inject constructor(
    @ApplicationContext context: Context,
) : OnboardingRepository {

    private val dataStore = context.onboardingDataStore

    override val completed: Flow<Boolean> = dataStore.data.map { it[KEY_COMPLETED] ?: false }

    override suspend fun setCompleted() {
        dataStore.edit { it[KEY_COMPLETED] = true }
    }

    private companion object {
        val KEY_COMPLETED = booleanPreferencesKey("completed")
    }
}
```

온보딩에 새 기능 소개를 추가해서 기존 사용자에게도 다시 보여주고 싶다면 `Boolean` 대신 "완료한 온보딩 버전(Int)"을 저장하고, 현재 버전보다 낮으면 다시 보여준다.

## 13-2. 루트에서 화면 전환

로그인/인증 플로우와 같은 방식으로, 루트가 값을 읽어 그래프를 통째로 바꾼다. 온보딩 화면은 "끝나면 어디로 가야 하는지" 알 필요가 없다.

```kotlin
@HiltViewModel
class RootViewModel @Inject constructor(
    onboardingRepository: OnboardingRepository,
) : ViewModel() {
    // null = 아직 저장소를 읽는 중
    val onboardingCompleted: StateFlow<Boolean?> = onboardingRepository.completed
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun AppRoot(viewModel: RootViewModel = hiltViewModel()) {
    val completed by viewModel.onboardingCompleted.collectAsStateWithLifecycle()

    when (completed) {
        null -> Unit                       // 스플래시가 가리는 구간
        false -> OnboardingScreen()
        true -> MainNavGraph()
    }
}
```

인증 상태(`AuthState`)가 있는 앱이라면 두 값을 함께 본다. 순서는 온보딩 → 로그인 → 메인이다.

```kotlin
when {
    completed == null || authState is AuthState.Unknown -> Unit
    completed == false -> OnboardingScreen()
    authState is AuthState.Unauthenticated -> LoginNavGraph()
    else -> MainNavGraph()
}
```

## 13-3. 스플래시 대기

```kotlin
splash.setKeepOnScreenCondition { rootViewModel.onboardingCompleted.value == null }
```

저장소를 읽는 동안(`null`)만 스플래시를 유지한다. 이 값을 읽기 전에 온보딩 화면을 먼저 그리면 이미 끝낸 사용자에게 온보딩이 잠깐 깜빡인다.

<br>

# 14. 테스트

## 14-1. 상태 판별: 순수 함수 테이블 테스트

```kotlin
class ResolveStatusTest {

    @Test fun 허용되어_있으면_Granted() =
        assertEquals(Granted, resolveStatus(granted = true, shouldShowRationale = false, requestedBefore = false))

    @Test fun 거부했고_rationale이_true면_Denied() =
        assertEquals(Denied, resolveStatus(granted = false, shouldShowRationale = true, requestedBefore = true))

    @Test fun 요청했는데_rationale이_false면_영구_거부() =
        assertEquals(PermanentlyDenied, resolveStatus(granted = false, shouldShowRationale = false, requestedBefore = true))

    @Test fun 한번도_요청하지_않았으면_NotRequested() =
        assertEquals(NotRequested, resolveStatus(granted = false, shouldShowRationale = false, requestedBefore = false))
}
```

`buildSteps()`도 `sdkInt`를 인자로 받으므로 "32에서는 알림 단계가 없고 33에서는 있다"를 바로 검증할 수 있다.

## 14-2. ViewModel: Fake로 시나리오 검증

```kotlin
class FakePermissionHistory(initial: Set<AppPermission> = emptySet()) : PermissionHistoryRepository {
    private val flow = MutableStateFlow(initial)
    override val requestedPermissions: Flow<Set<AppPermission>> = flow
    override suspend fun markRequested(permission: AppPermission) { flow.value = flow.value + permission }
}

class FakeOnboardingRepository : OnboardingRepository {
    private val _completed = MutableStateFlow(false)
    override val completed: Flow<Boolean> = _completed
    override suspend fun setCompleted() { _completed.value = true }
}

private fun facts(granted: Boolean = false, rationale: Boolean = false) = PermissionFacts(granted, rationale)
```

단위 테스트에서는 `Build.VERSION.SDK_INT`가 0이라서 `buildSteps()` 결과가 `Intro, Intro, CAMERA, LOCATION` 순서가 된다. (알림 단계 생략)

```kotlin
private fun OnboardingViewModel.passIntros() {
    onIntent(OnboardingIntent.ClickPrimary)
    onIntent(OnboardingIntent.ClickPrimary)
}

@Test
fun 허용하기를_누르면_권한_요청_Effect가_나간다() = runTest {
    val vm = OnboardingViewModel(SavedStateHandle(), FakeOnboardingRepository(), FakePermissionHistory())
    vm.onIntent(OnboardingIntent.FactsUpdated(AppPermission.entries.associateWith { facts() }))
    vm.passIntros()

    vm.onIntent(OnboardingIntent.ClickPrimary)

    assertEquals(OnboardingEffect.RequestPermission(AppPermission.CAMERA), vm.effect.first())
}

@Test
fun 허용되면_자동으로_다음_단계로_넘어간다() = runTest {
    val vm = OnboardingViewModel(SavedStateHandle(), FakeOnboardingRepository(), FakePermissionHistory())
    vm.onIntent(OnboardingIntent.FactsUpdated(AppPermission.entries.associateWith { facts() }))
    vm.passIntros()

    vm.onIntent(OnboardingIntent.PermissionResult(AppPermission.CAMERA, facts(granted = true)))

    val step = vm.state.value.currentStep as OnboardingStep.Permission
    assertEquals(AppPermission.LOCATION, step.permission)
}

@Test
fun 한번_거부하면_Denied로_같은_단계에_머문다() = runTest {
    val vm = OnboardingViewModel(SavedStateHandle(), FakeOnboardingRepository(), FakePermissionHistory())
    vm.onIntent(OnboardingIntent.FactsUpdated(AppPermission.entries.associateWith { facts() }))
    vm.passIntros()

    vm.onIntent(OnboardingIntent.PermissionResult(AppPermission.CAMERA, facts(rationale = true)))

    assertEquals(PermissionStatus.Denied, vm.state.value.currentStatus)
    assertEquals(AppPermission.CAMERA, (vm.state.value.currentStep as OnboardingStep.Permission).permission)
}

@Test
fun 영구_거부이면_클릭할_때_설정_이동_Effect가_나간다() = runTest {
    val vm = OnboardingViewModel(SavedStateHandle(), FakeOnboardingRepository(), FakePermissionHistory())
    vm.onIntent(OnboardingIntent.FactsUpdated(AppPermission.entries.associateWith { facts() }))
    vm.passIntros()
    vm.onIntent(OnboardingIntent.PermissionResult(AppPermission.CAMERA, facts(rationale = false)))  // 요청 이력 + rationale false

    vm.onIntent(OnboardingIntent.ClickPrimary)

    assertEquals(OnboardingEffect.OpenSettings(AppPermission.CAMERA), vm.effect.first())
}

@Test
fun 설정에서_허용하고_돌아오면_다음_단계로_넘어간다() = runTest {
    val vm = OnboardingViewModel(SavedStateHandle(), FakeOnboardingRepository(), FakePermissionHistory())
    vm.onIntent(OnboardingIntent.FactsUpdated(AppPermission.entries.associateWith { facts() }))
    vm.passIntros()
    vm.onIntent(OnboardingIntent.PermissionResult(AppPermission.CAMERA, facts(rationale = false)))

    // 설정에서 카메라를 켜고 앱으로 복귀 (ON_RESUME)
    vm.onIntent(
        OnboardingIntent.FactsUpdated(
            AppPermission.entries.associateWith { facts(granted = it == AppPermission.CAMERA) }
        )
    )

    val step = vm.state.value.currentStep as OnboardingStep.Permission
    assertEquals(AppPermission.LOCATION, step.permission)
}

@Test
fun 필수_권한은_건너뛸_수_없고_선택_권한은_건너뛸_수_있다() = runTest {
    val vm = OnboardingViewModel(SavedStateHandle(), FakeOnboardingRepository(), FakePermissionHistory())
    vm.passIntros()

    assertFalse(vm.state.value.canSkip)          // CAMERA (required)
    vm.onIntent(OnboardingIntent.ClickSkip)       // 무시되어야 함
    assertEquals(AppPermission.CAMERA, (vm.state.value.currentStep as OnboardingStep.Permission).permission)

    vm.onIntent(OnboardingIntent.PermissionResult(AppPermission.CAMERA, facts(granted = true)))
    assertTrue(vm.state.value.canSkip)           // LOCATION (optional)
}

@Test
fun 마지막_단계를_넘기면_온보딩_완료가_저장된다() = runTest {
    val onboarding = FakeOnboardingRepository()
    val vm = OnboardingViewModel(SavedStateHandle(), onboarding, FakePermissionHistory())
    vm.passIntros()
    vm.onIntent(OnboardingIntent.PermissionResult(AppPermission.CAMERA, facts(granted = true)))

    vm.onIntent(OnboardingIntent.ClickSkip)      // LOCATION 건너뛰기 = 마지막 단계 통과

    assertTrue(onboarding.completed.first())
}
```

ViewModel을 `viewModelScope`로 테스트하므로 `Dispatchers.setMain(UnconfinedTestDispatcher())`를 테스트 규칙으로 설정한다.

## 14-3. UI 계측 테스트

- 이미 허용된 상태에서 시작하는 시나리오는 `GrantPermissionRule.grant(...)`로 권한을 미리 부여한다.
- 시스템 다이얼로그를 직접 누르는 테스트는 UiAutomator를 쓴다. 다이얼로그의 문구는 언어와 OS 버전마다 달라서 텍스트보다 리소스 ID로 찾는 편이 안정적이지만, 이 ID도 OS 버전에 따라 달라질 수 있으니 대상 버전에서 확인한다.
- 시스템 다이얼로그 자체를 자동화하는 비용이 크다면 UI 테스트는 "버튼 클릭 → 상태별 문구/버튼 표시"까지만 검증하고, 권한 결과 이후의 분기는 14-2의 ViewModel 테스트에 맡긴다.

## 14-4. 수동 확인 체크리스트

- 처음 설치 후 모든 권한을 허용하는 정상 흐름
- 권한을 한 번 거부한 뒤 "다시 요청하기"로 허용
- 두 번 거부한 뒤 "설정에서 허용하기"로 이동해서 켜고 돌아오면 자동으로 다음 단계로 이동
- 설정에서 권한을 켠 뒤 "이전"으로 돌아와도 앞으로 튕겨 나가지 않음
- 선택 권한은 "나중에 하기"로 넘어가고, 이후 해당 기능에서 다시 요청됨
- 필수 권한은 건너뛸 수 없음
- 온보딩 도중 설정에서 권한을 끄고(프로세스 종료) 돌아와도 같은 단계에서 이어짐
- 앱 데이터 삭제 후 재실행하면 처음부터 시작
- Android 13 미만 기기에서 알림 단계가 나타나지 않음
- 위치에서 "대략적인 위치"만 허용해도 허용으로 처리됨

<br>

# 15. 흔한 실수

## 15-1. 시작하자마자 모든 권한을 연달아 요청

사용자는 이유를 모른 채 "거부"를 누르고, 한 번 거부된 권한은 되살리기 어렵다. 한 단계에 하나씩, 설명 후에 요청한다.

## 15-2. 설명 없이 시스템 다이얼로그부터 띄움

시스템 다이얼로그는 문구를 바꿀 수 없다. 우리 화면에서 "무엇을, 왜, 거부하면 어떻게 되는지"를 먼저 보여준다.

## 15-3. 거부와 영구 거부를 구분하지 않음

Android 11부터 두 번 거부하면 다이얼로그가 뜨지 않는다. 같은 "허용하기" 버튼이 아무 반응이 없으면 사용자는 앱이 고장 났다고 느낀다. 영구 거부 상태에서는 설정 이동 버튼으로 바꾼다.

## 15-4. `shouldShowRequestPermissionRationale`만으로 영구 거부 판단

이 값은 "요청 전"과 "다시 묻지 않음"에서 모두 `false`다. 요청 이력을 별도로 기록해서 함께 판단한다.

## 15-5. 요청 이력을 백업에 포함

새 기기에서 아직 묻지 않은 권한이 영구 거부로 오판된다. 이력 파일은 자동 백업에서 제외한다.

## 15-6. 권한 상태를 한 번만 확인하고 캐시

설정 변경, "이번에만 허용", 미사용 앱의 권한 자동 회수로 상태가 바뀐다. `ON_RESUME`마다 다시 확인한다.

## 15-7. 선택 권한인데 온보딩을 막음

권한을 거부했다는 이유로 앱을 쓰지 못하게 하면 이탈한다. 권한이 필요한 기능만 제한하고, 건너뛴 사용자는 해당 기능에서 다시 요청한다.

## 15-8. 설정에서 돌아와도 다음으로 넘어가지 않음

`ON_RESUME` 재확인이 없으면 사용자가 권한을 켰는데도 화면은 "설정에서 허용하기"에 머문다.

## 15-9. 위치 권한 중 하나만 확인

`FINE`만 확인하면 사용자가 "대략적인 위치"를 허용했을 때 허용 상태가 거부로 오판된다. 둘 중 하나라도 허용되면 허용으로 보고, 정밀 위치가 필요한 기능에서 따로 확인한다.

## 15-10. 백그라운드 위치를 포그라운드 위치와 함께 요청

동시에 요청할 수 없다. 포그라운드 위치를 받은 뒤 별도 단계로 분리하고, 사전 고지를 충분히 한다.

## 15-11. 페이저 스와이프를 허용

권한 단계를 건너뛰거나 `currentIndex`와 페이저 위치가 어긋난다. `userScrollEnabled = false`로 막고 단계 이동은 상태를 통해서만 한다.

## 15-12. 현재 단계를 저장하지 않음

설정에서 권한을 끄면 프로세스가 종료될 수 있고, 돌아오면 처음부터 시작한다. 현재 단계를 `SavedStateHandle`에 저장한다.

## 15-13. 온보딩 완료를 마지막 화면 진입 시점에 저장

마지막 화면에서 앱을 종료한 사용자가 온보딩을 마친 것으로 처리된다. 마지막 단계의 버튼을 눌렀을 때 저장한다.

## 15-14. 이미 허용된 권한 단계를 다시 보여줌

재설치나 권한 복원 후에도 이미 허용된 권한을 설명하게 된다. 앞으로 진행할 때 허용된 권한 단계는 건너뛴다. 단, "이전"으로 돌아온 경우에는 자동 전환하지 않는다.

## 15-15. 매니페스트에 권한 선언 누락

선언하지 않은 권한은 요청해도 다이얼로그 없이 바로 거부된다. 사용하는 권한을 모두 `<uses-permission>`으로 선언한다.

## 15-16. `Activity`가 아닌 `Context`로 rationale 확인

`shouldShowRequestPermissionRationale`은 `Activity`가 필요하다. `LocalContext.current`는 `ContextWrapper`일 수 있으므로 `findActivity()`로 감싼 `Activity`를 찾아서 쓴다.

<br>

# 16. 전체 예제 (복붙용)

## 16-1. 계약 / 모델

```kotlin
enum class PermissionStatus { NotRequested, Denied, PermanentlyDenied, Granted }

data class PermissionFacts(
    val granted: Boolean,
    val shouldShowRationale: Boolean,
)

// AppPermission, resolveStatus(), permissionFacts()  -> 5장
// OnboardingStep, buildSteps()                       -> 7장
// OnboardingState, OnboardingIntent, OnboardingEffect -> 11-2
```

## 16-2. 코드 위치

| 코드 | 위치 |
| --- | --- |
| `AppPermission`, `resolveStatus()`, `permissionFacts()` | 5장 |
| `PermissionHistoryRepository`, `DataStorePermissionHistoryRepository` | 5-4 |
| `OnboardingStep`, `buildSteps()` | 7장 |
| `openAppSettings()`, `openNotificationSettings()` | 8-4 |
| `OnboardingViewModel` | 11-3 |
| `OnboardingScreen`, `OnboardingPage`, `OnboardingBottomBar`, `statusNote()`, `primaryLabel()` | 12장 |
| `OnboardingRepository`, `RootViewModel`, `AppRoot` | 13장 |

`findActivity()`는 이전 문서(이미지 업로드)와 같다.

```kotlin
private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Activity를 찾을 수 없어요")
}
```

## 16-3. 매니페스트

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<application
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules" ...>
```

백업 제외 규칙은 5-5의 XML을 사용한다.

## 16-4. Hilt 바인딩

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingModule {

    @Binds
    abstract fun bindOnboardingRepository(
        impl: DataStoreOnboardingRepository,
    ): OnboardingRepository

    @Binds
    abstract fun bindPermissionHistoryRepository(
        impl: DataStorePermissionHistoryRepository,
    ): PermissionHistoryRepository
}
```

## 16-5. 의존성

```kotlin
// build.gradle.kts (app)
implementation("androidx.datastore:datastore-preferences:<최신 버전>")
implementation("androidx.lifecycle:lifecycle-runtime-compose:<최신 버전>")   // LifecycleEventEffect
implementation("androidx.hilt:hilt-navigation-compose:<최신 버전>")
implementation("androidx.core:core-splashscreen:<최신 버전>")
// HorizontalPager는 compose foundation에 포함

// 테스트
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:<최신 버전>")
androidTestImplementation("androidx.test:rules:<최신 버전>")   // GrantPermissionRule
```

`AppPermission.entries`는 Kotlin 1.9 이상에서 사용할 수 있다. 낮은 버전이라면 `values()`로 바꾼다.

<br>

# 정리

1. 권한 요청이 복잡한 이유는 결과가 돌아온 뒤의 경우의 수(허용, 거부, 영구 거부, 설정 변경, 일회성 허용, 자동 회수)가 많기 때문이다. 권한의 상태는 `PermissionStatus`(`NotRequested / Denied / PermanentlyDenied / Granted`) 하나로, 온보딩의 위치는 `currentIndex` 하나로 표현한다.
2. `shouldShowRequestPermissionRationale()`은 "요청 전"과 "다시 묻지 않음"을 구분하지 못한다. 요청 결과가 돌아올 때마다 이력을 저장하고 `resolveStatus(granted, rationale, requestedBefore)`로 상태를 계산한다. 이 함수는 순수 함수라서 표 그대로 테스트할 수 있다.
3. 요청 이력은 자동 백업에서 제외한다. 새 기기로 복원되면 아직 묻지 않은 권한이 영구 거부로 오판된다.
4. 온보딩의 권한 화면은 시스템 다이얼로그 전의 "사전 설명"이다. 무엇을, 왜, 거부하면 어떻게 되는지를 보여주고, 사용자가 허용하기를 눌렀을 때만 시스템 다이얼로그를 띄운다. 앱의 핵심 기능과 설명이 쉬운 권한만 온보딩에서 받고 나머지는 사용하는 순간에 요청한다.
5. 거부 처리는 상태별로 다르다. `Denied`는 이유를 다시 안내하고 다시 요청하며, `PermanentlyDenied`는 같은 버튼을 설정 이동으로 바꾼다. 선택 권한은 건너뛸 수 있고 필수 권한은 건너뛸 수 없다. 거부해도 앱은 계속 쓸 수 있어야 한다.
6. `ON_RESUME`마다 권한 상태를 다시 읽는다. 설정에서 켠 권한은 자동으로 다음 단계로 이어지고, 이미 허용된 단계는 앞으로 갈 때 건너뛰며, "이전"으로 돌아온 경우에는 자동 전환하지 않는다. 설정에서 권한을 끄면 프로세스가 종료될 수 있으므로 현재 단계는 `SavedStateHandle`에 저장한다.
7. 알림(API 33+), 위치(FINE/COARSE, 백그라운드 위치 분리), 사진(Photo Picker로 권한 불필요), 일회성 허용, 정확한 알람 같은 특수 권한은 각자 규칙이 다르다. 위치는 둘 중 하나만 허용돼도 허용으로 보고, 백그라운드 위치는 별도 단계로 분리한다.
8. MVI에서 현재 단계와 권한 상태는 `UiState`에, 시스템 다이얼로그 실행과 설정 이동은 `Effect`에, 권한 결과와 복귀는 `Intent`에 둔다. `HorizontalPager`는 `userScrollEnabled = false`로 두고 `currentIndex`를 따라가기만 한다.
9. 온보딩 완료는 마지막 단계의 버튼을 눌렀을 때 저장하고, 루트가 완료 여부를 읽어 그래프를 전환한다. 온보딩 화면은 끝난 뒤 어디로 갈지 알 필요가 없다.
10. 흔한 실수는 권한 연속 요청, 설명 없는 시스템 다이얼로그, 거부/영구 거부 미구분, rationale만으로 영구 거부 판단, 이력 백업 포함, 상태 캐시, 선택 권한으로 앱 차단, 복귀 재확인 누락, 위치 권한 하나만 확인, 페이저 스와이프 허용, 단계 미저장, 이른 완료 저장, 매니페스트 선언 누락이다.
