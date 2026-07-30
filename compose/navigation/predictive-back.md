# Predictive Back Gesture

<br>

## 목차

1. Predictive Back이란
2. 왜 도입됐는가
3. 기존 뒤로가기 처리와의 차이
4. 기본 설정 (Manifest)
5. View 시스템에서 처리하기
6. Compose에서 처리하기 (BackHandler)
7. 애니메이션 진행률 활용하기 (Predictive Back 미리보기)
8. Navigation Compose와 함께 사용하기
9. 뒤로가기 스택(Back Stack) 처리
10. 여러 BackHandler가 존재할 때 우선순위
11. Confirm Dialog로 뒤로가기 막기
12. 실전 예제 (스와이프로 닫히는 바텀시트)
13. 자주 하는 실수
14. 헷갈리기 쉬운 부분

<br>

# 1. Predictive Back이란

Android 13(API 33)에서 처음 도입되고 Android 14(API 34)에서 정식 지원되는 뒤로가기 UX로, 사용자가 뒤로가기 제스처(엣지 스와이프)를 하는 동안 화면이 실제로 전환되기 전에 "뒤로 가면 어떤 화면이 나올지"를 미리 애니메이션으로 보여주는 기능이다.

기존 흐름과 비교하면 이렇다.

```
기존 방식:
뒤로가기 -> 현재 화면 즉시 종료 -> 이전 화면 표시

Predictive Back:
뒤로가기 제스처 시작 -> 이전 화면 미리 보여줌 -> 손을 떼면 완료 (또는 취소하면 원래 화면 유지)
```

사용자는 뒤로가기 결과를 눈으로 확인한 뒤에 손을 떼거나 취소할 수 있어서, 실수로 화면을 나가는 상황이 줄어든다.

<br>

# 2. 왜 도입됐는가

기존 뒤로가기 제스처는 다음과 같은 문제가 있었다.

- 뒤로가기를 누르기 전까지 어디로 이동하는지 전혀 알 수 없었다
- 그래서 실수로 뒤로가기를 눌러 작성 중이던 내용을 잃는 경우가 많았다
- 앱마다 뒤로가기 시 전환 애니메이션이 제각각이라 경험이 일관되지 않았다

Android는 Predictive Back으로 일관된 UX, 사용자 실수 감소, 자연스러운 화면 전환을 목표로 이 기능을 추가했다.

<br>

# 3. 기존 뒤로가기 처리와의 차이

| 구분 | 기존 방식 | Predictive Back |
|---|---|---|
| 전환 시점 | 뒤로가기 이벤트 발생 즉시 | 제스처가 끝나야(손을 뗀 시점) 확정 |
| 콜백 | `onBackPressed()` (deprecated) | `OnBackPressedCallback` / Compose `BackHandler` |
| 취소 가능 여부 | 불가능 | 스와이프 도중 취소 가능 |
| 애니메이션 정보 | 없음 | 진행률(progress) 등 제공, 시스템이 자동 처리 |
| 화면 표시 | 현재 화면만 보임 | 이전 화면이 미리 비쳐 보임 |

중요한 점은 `BackHandler` 자체의 코드 작성 방식은 Predictive Back 이전과 크게 다르지 않다는 것이다. 다만 실행되는 시점이 "제스처 시작 -> 애니메이션 진행 -> 완료 시점"을 거친 뒤로 바뀌었을 뿐이고, 애니메이션 자체는 개발자가 직접 그리는 게 아니라 시스템이 화면 캡처, 진행도 계산, 렌더링을 자동으로 수행한다.

<br>

# 4. 기본 설정 (Manifest)

앱이 Predictive Back 애니메이션을 지원하려면 Manifest에 플래그를 켜야 한다.

```xml
<application
    android:enableOnBackInvokedCallback="true"
    ... >
</application>
```

이 플래그를 켜지 않으면 targetSdk가 33 이상이어도 기존 방식(즉시 전환)으로 동작한다. 반대로 이 플래그를 켰는데 앱 안에 `onBackPressed()`를 오버라이드한 레거시 코드가 남아있으면 정상 동작하지 않을 수 있어서, `OnBackPressedCallback` 기반으로 전부 마이그레이션되어 있어야 한다.

지원 버전은 다음과 같다.

| Android 버전 | 지원 여부 |
|---|---|
| Android 13 | 개발자 옵션에서 켜야 테스트 가능 |
| Android 14 이상 | 기본 지원 |

<br>

# 5. View 시스템에서 처리하기

```kotlin
class MainActivity : AppCompatActivity() {

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // 뒤로가기가 확정됐을 때 실행
            finish()
        }

        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            // 스와이프 제스처 시작
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            // 스와이프 진행 중 - backEvent.progress (0.0 ~ 1.0)
        }

        override fun handleOnBackCancelled() {
            // 스와이프 도중 취소됨
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, backCallback)
    }
}
```

`isEnabled` 값을 상황에 따라 `true`/`false`로 바꿔가며 콜백을 켜고 끌 수 있다.

<br>

# 6. Compose에서 처리하기 (BackHandler)

Compose에서는 `BackHandler` composable로 간단하게 처리한다.

```kotlin
@Composable
fun DetailScreen(navController: NavController) {
    BackHandler {
        navController.popBackStack()
    }

    Text("Detail")
}
```

동작 순서는 다음과 같다.

```
뒤로가기 -> BackHandler 호출 -> popBackStack() -> 이전 화면
```

특정 조건에서만 뒤로가기를 가로채고 싶다면 `enabled` 파라미터를 쓴다.

```kotlin
BackHandler(enabled = isEditMode) {
    showExitDialog = true
}
```

수정 중일 때, 로그인 중일 때, 입력 중일 때처럼 필요한 상황에서만 활성화하면 된다.

진행률(progress) 기반 애니메이션까지 다루고 싶다면 `PredictiveBackHandler`(Compose 1.7+)를 사용한다.

```kotlin
@Composable
fun DetailScreen(onBack: () -> Unit) {
    PredictiveBackHandler { progress: Flow<BackEventCompat> ->
        try {
            progress.collect { backEvent ->
                // 스와이프 진행 중, backEvent.progress로 애니메이션 값 갱신
            }
            // 여기까지 collect가 끝나면 뒤로가기 확정
            onBack()
        } catch (e: CancellationException) {
            // 스와이프 도중 취소됨
        }
    }
}
```

`BackHandler`는 확정된 뒤로가기 이벤트만 다루고, `PredictiveBackHandler`는 스와이프 도중의 진행 상태(Flow)까지 관찰할 수 있다는 차이가 있다.

<br>

# 7. 애니메이션 진행률 활용하기 (Predictive Back 미리보기)

`BackEventCompat`이 제공하는 정보로 스와이프 중 실시간 애니메이션을 만들 수 있다.

```kotlin
data class BackEventCompat(
    val touchX: Float,
    val touchY: Float,
    val progress: Float,     // 0.0 ~ 1.0, 스와이프가 얼마나 진행됐는지
    val swipeEdge: Int       // 왼쪽/오른쪽 엣지 중 어디서 시작했는지
)
```

`progress` 값을 `scale`이나 `alpha`, `translationX`에 매핑하면, 사용자가 스와이프하는 만큼 화면이 축소되거나 밀려나는 자연스러운 전환 효과를 만들 수 있다. 시스템 기본 애니메이션(카드가 살짝 줄어들며 뒤 화면이 비치는 효과)도 이 원리로 동작한다.

<br>

# 8. Navigation Compose와 함께 사용하기

가장 흔히 쓰는 패턴은 다음과 같다.

```kotlin
BackHandler {
    navController.popBackStack()
}
```

하지만 사실 Navigation Compose에서는 `BackHandler` 없이도 대부분 잘 동작한다. `NavHost`가 이미 시스템 뒤로가기를 자체적으로 처리하기 때문이다.

```
NavHost(...) 만 있어도
뒤로가기 -> BackStack 제거 -> 이전 Destination 이동
```

이 자동으로 수행된다. 그래서 `BackHandler`는 Navigation 자체를 대신하기 위해서가 아니라, 화면의 특정 상태를 제어하기 위한 목적으로 쓰는 것이 맞다. 대표적으로 다음과 같은 경우다.

- Dialog 닫기
- BottomSheet 닫기
- 종료 확인
- 작성 중인 데이터 저장 여부 확인

<br>

# 9. 뒤로가기 스택(Back Stack) 처리

Navigation은 내부적으로 Back Stack을 관리한다. 예를 들어 Home -> List -> Detail -> Setting 순서로 이동했다면 Stack은 이렇게 쌓인다.

```
Home
List
Detail
Setting   <- 현재 화면
```

뒤로가기를 하면 Setting이 제거되고 Detail이 보인다. Compose에서는 `navController.popBackStack()` 한 줄로 처리한다.

특정 화면까지 한 번에 이동하고 싶다면 route를 지정한다.

```kotlin
navController.popBackStack(
    route = "home",
    inclusive = false
)
```

결과: `Home, List, Detail` 스택에서 `List`, `Detail`이 한 번에 제거되고 `Home`만 남는다.

`inclusive` 옵션에 따라 결과가 달라진다.

- `inclusive = false`: 지정한 route(`home`)는 스택에 남는다
- `inclusive = true`: 지정한 route(`home`)까지 함께 제거된다

실무에서는 이 `inclusive` 값을 잘못 지정해서 "뒤로가기 했는데 엉뚱한 화면이 나온다" 같은 버그가 자주 발생하므로, 항상 의도(이 화면까지 남길지, 이 화면도 지울지)를 명확히 하고 써야 한다.

<br>

# 10. 여러 BackHandler가 존재할 때 우선순위

Compose는 가장 안쪽(가장 나중에 컴포지션된) `BackHandler`가 우선 실행된다.

```
Activity
  └─ Screen
       └─ Dialog
            └─ BottomSheet   <- 이 BackHandler가 먼저 호출됨
```

즉 화면 위에 Dialog가 떠 있고 그 위에 BottomSheet가 떠 있다면, 뒤로가기를 눌렀을 때 가장 바깥의 Screen이 아니라 가장 안쪽의 BottomSheet가 먼저 반응한다. 여러 개의 `BackHandler`를 같은 레벨에 중복 등록하면 그중 가장 안쪽 것만 실행되므로, 불필요한 중복 등록은 피하는 게 좋다.

<br>

# 11. Confirm Dialog로 뒤로가기 막기

회원가입, 게시글 작성, 결제처럼 반드시 입력을 완료해야 하거나 실수로 나가면 안 되는 화면에서 자주 쓰는 패턴이다.

```kotlin
var showDialog by remember { mutableStateOf(false) }

BackHandler(enabled = true) {
    showDialog = true
}

if (showDialog) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            Button(onClick = { navController.popBackStack() }) {
                Text("나가기")
            }
        },
        dismissButton = {
            Button(onClick = { showDialog = false }) {
                Text("취소")
            }
        },
        title = { Text("작성 중인 내용이 있습니다.") },
        text = { Text("정말 나가시겠습니까?") }
    )
}
```

흐름은 다음과 같다.

```
뒤로가기 -> Dialog 표시
    취소 선택 -> 현재 화면 유지
    확인 선택 -> popBackStack()
```

이 패턴으로 사용자가 실수로 입력 내용을 잃는 것을 방지할 수 있다.

<br>

# 12. 실전 예제 (스와이프로 닫히는 바텀시트)

```kotlin
@Composable
fun PredictiveBottomSheet(
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }

    PredictiveBackHandler { progress ->
        try {
            progress.collect { backEvent ->
                scale = 1f - (backEvent.progress * 0.2f) // 최대 20% 축소
            }
            onDismiss()
        } catch (e: CancellationException) {
            scale = 1f // 취소되면 원래 크기로 복구
        }
    }

    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        // 바텀시트 컨텐츠
    }
}
```

스와이프 도중 취소되면 `CancellationException`이 발생하고, 이때 UI 상태(scale 등)를 원래대로 되돌려야 자연스럽다. 이 복구 로직을 빼먹으면 취소했는데도 화면이 계속 작아진 상태로 남는 버그가 생긴다.

<br>

# 13. 자주 하는 실수

- **Manifest 플래그만 켜고 콜백은 정리 안 하는 경우**: `enableOnBackInvokedCallback="true"`만 켜면 시스템 기본 애니메이션은 나오지만, 앱 안에 남아있는 `onBackPressed()` 오버라이드가 있으면 예상과 다르게 동작할 수 있다.
- **Navigation과 BackHandler를 동시에 처리하는 경우**: `NavHost`가 이미 뒤로가기를 처리하는데, `BackHandler`에서 또 `popBackStack()`을 호출하면 화면이 두 번 이동하거나 스택이 꼬일 수 있다. 둘 중 하나만 책임지게 해야 한다.
- **Dialog를 닫지 않고 바로 popBackStack()하는 경우**: Dialog가 떠 있는 상태에서 뒤로가기를 누르면, Dialog부터 먼저 닫고 그 다음에 화면을 종료하는 것이 자연스럽다. 순서를 지키지 않으면 화면과 Dialog가 동시에 사라져 어색하게 느껴진다.
- **enabled를 안 쓰고 항상 true로 두는 경우**: 뒤로가기가 필요 없는 화면에서도 계속 가로채면, 원래 Navigation의 기본 뒤로가기 동작을 모두 막아버릴 수 있다. `enabled = isEditing`처럼 필요한 순간에만 활성화해야 한다.
- **취소 케이스를 처리하지 않는 경우**: `progress.collect`가 정상 종료됐다고만 가정하고 취소(`CancellationException`) 처리를 빼먹으면, 스와이프하다 만 상태에서 UI가 어정쩡하게 남는다.
- **버전별 차이를 고려하지 않는 경우**: Predictive Back 애니메이션은 Android 14에서 가장 자연스럽게 동작한다. Android 13 이하에서는 기존 방식으로 동작할 수 있으므로, 특정 애니메이션이나 진행률 값에 화면 로직 전체를 의존시키지 않도록 설계하는 것이 좋다.

<br>

# 14. 헷갈리기 쉬운 부분

- `BackHandler`와 `PredictiveBackHandler`는 둘 다 Compose에서 쓰지만, 전자는 "뒤로가기가 일어났다"는 결과만 받고, 후자는 "뒤로가기가 진행되는 과정"까지 관찰한다. 애니메이션이 필요 없으면 `BackHandler`만으로 충분하다.
- Predictive Back은 시스템 제스처 내비게이션(엣지 스와이프)에서만 의미가 있다. 3버튼 내비게이션을 쓰는 기기에서는 버튼을 누르는 즉시 확정되므로 진행률 개념 자체가 없다.
- targetSdk 33 이상이라고 자동으로 Predictive Back이 적용되는 게 아니다. Manifest 플래그를 명시적으로 켜야 하고, Android 14(API 34)부터는 시스템 차원의 back-to-home 애니메이션도 이 플래그에 의존한다.
- `NavHost`가 뒤로가기를 자동으로 처리해준다고 해서 `BackHandler`가 필요 없는 건 아니다. Navigation 자체(화면 이동)는 `NavHost`가, 화면 내부 상태(Dialog, 편집 모드 등)는 `BackHandler`가 담당한다는 역할 구분을 명확히 하는 것이 좋다.

<br>

# 정리

Predictive Back은 사용자가 뒤로가기 제스처를 하는 동안 전환될 화면을 미리 보여주고, 손을 떼기 전까지는 취소도 가능하게 하는 Android 13+(정식 지원은 14) 기능이다. Manifest에 `enableOnBackInvokedCallback`을 켜고, 기존 `onBackPressed()` 대신 `OnBackPressedCallback`(View) 또는 `BackHandler`/`PredictiveBackHandler`(Compose)로 마이그레이션해야 정상 동작한다. Navigation Compose를 쓸 때는 `NavHost`가 기본 뒤로가기를 이미 처리해주므로, `BackHandler`는 화면 이동 자체가 아니라 Dialog 닫기·작성 중 이탈 확인 같은 화면 내부 상태 제어에 쓰는 것이 자연스럽다. 여러 `BackHandler`가 겹칠 때는 가장 안쪽 것이 우선하며, 진행률 기반 애니메이션을 만들 때는 취소 케이스에서 UI를 원상복구하는 로직을 반드시 함께 작성해야 한다.
