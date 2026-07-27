# MVI 아키텍처 기초

<br>

## 목차

1. MVI란
2. 왜 MVI가 필요한가 (MVVM과 비교)
3. MVI의 핵심 구성요소
4. 단방향 데이터 흐름 (Unidirectional Data Flow)
5. Compose + Hilt로 구현하기
6. 실전 예제 (로그인 화면)
7. Effect(SideEffect) 처리하기
8. 자주 하는 실수
9. 헷갈리기 쉬운 부분

<br>

# 1. MVI란

Model-View-Intent의 약자로, 화면의 상태(State)를 하나의 불변 객체로 관리하고, 모든 사용자 행동을 Intent(또는 Action)라는 이벤트로 통일해서 처리하는 아키텍처 패턴이다.

- Model: 화면이 가진 상태 전체 (State)
- View: State를 그려서 보여주는 역할, 사용자 입력을 Intent로 변환해서 보냄
- Intent: 사용자의 "의도" (버튼 클릭, 텍스트 입력, 새로고침 등)

핵심 규칙은 하나다. **State는 절대 직접 바꾸지 않고, 항상 새로운 State를 통째로 만들어서 교체한다.**

<br>

# 2. 왜 MVI가 필요한가 (MVVM과 비교)

우체국 창구로 비유하면 이해하기 쉽다.

- MVVM: 창구 직원(ViewModel)이 여러 개의 개별 서류(LiveData 여러 개: `isLoading`, `userName`, `errorMessage`...)를 각각 따로 관리한다. 서류가 늘어날수록 어떤 조합이 가능한지 파악하기 어려워진다. 예를 들어 `isLoading = true`인데 `errorMessage`도 동시에 차 있는, 말이 안 되는 상태가 실수로 생길 수 있다.
- MVI: 창구 직원이 서류를 딱 하나의 파일철(단일 State 객체)로 묶어서 관리한다. 파일철 전체를 새로 갈아 끼우는 방식이라, 항상 "지금 이 순간의 상태가 정확히 무엇인지" 한 번에 알 수 있다. 말이 안 되는 상태 조합 자체가 만들어지기 어렵다.

즉 MVVM이 상태 조각들을 여러 개 흩어놓고 각자 업데이트하는 방식이라면, MVI는 상태를 하나로 묶어서 통째로 교체하는 방식이다. 상태가 복잡해질수록 MVI의 예측 가능성이 빛을 발한다.

<br>

# 3. MVI의 핵심 구성요소

| 구성요소 | 역할 | 예시 |
|---|---|---|
| State | 화면이 가질 수 있는 모든 상태를 담은 불변(immutable) 데이터 클래스 | `LoginState(isLoading: Boolean, email: String, error: String?)` |
| Intent | 사용자가 할 수 있는 모든 행동을 표현한 sealed class/interface | `LoginIntent.EmailChanged`, `LoginIntent.SubmitClicked` |
| Effect(SideEffect) | State에는 안 담기는 일회성 이벤트 | 토스트 메시지, 화면 이동, 스낵바 |
| Reducer | (Intent + 현재 State) -> 새로운 State를 만드는 순수 함수 | ViewModel 내부의 `reduce()` 함수 |

State와 Effect를 나누는 이유: State는 화면 회전 등으로 다시 그려져도 유지되어야 하지만, "토스트 메시지 띄우기" 같은 건 한 번만 실행되고 사라져야 한다. State에 넣으면 화면 회전마다 토스트가 또 뜨는 버그가 생긴다.

<br>

# 4. 단방향 데이터 흐름 (Unidirectional Data Flow)

MVI의 데이터는 항상 한 방향으로만 흐른다. 이게 MVI의 핵심 철학이다.

```
사용자 액션
    │
    ▼
Intent 발생  (예: SubmitClicked)
    │
    ▼
ViewModel이 Intent를 받음
    │
    ▼
Reducer가 (기존 State + Intent)로 새 State 생성
    │
    ▼
StateFlow가 새 State를 emit
    │
    ▼
Compose UI가 State를 관찰하고 다시 그려짐
    │
    └──── (다시 사용자 액션으로 순환) ────┘
```

이 흐름에서 중요한 건, View가 State를 직접 수정할 방법이 없다는 것이다. View는 오직 Intent를 "보내기"만 할 수 있고, State를 바꾸는 건 전적으로 ViewModel(Reducer)의 몫이다. 그래서 버그가 생겨도 "어디서 State가 바뀌었는지" 추적하기 쉽다 — State를 바꿀 수 있는 곳이 Reducer 한 곳뿐이기 때문이다.

<br>

# 5. Compose + Hilt로 구현하기

```kotlin
// 1) State 정의 - 불변 데이터 클래스
data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

// 2) Intent 정의 - sealed interface로 가능한 행동을 전부 나열
sealed interface LoginIntent {
    data class EmailChanged(val value: String) : LoginIntent
    data class PasswordChanged(val value: String) : LoginIntent
    object SubmitClicked : LoginIntent
}

// 3) Effect 정의 - 일회성 이벤트
sealed interface LoginEffect {
    object NavigateToHome : LoginEffect
    data class ShowToast(val message: String) : LoginEffect
}

// 4) ViewModel - Intent를 받아서 State를 만들고 Effect를 내보냄
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<LoginEffect>()
    val effect: SharedFlow<LoginEffect> = _effect.asSharedFlow()

    fun onIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.EmailChanged ->
                _state.update { it.copy(email = intent.value) }

            is LoginIntent.PasswordChanged ->
                _state.update { it.copy(password = intent.value) }

            LoginIntent.SubmitClicked -> submitLogin()
        }
    }

    private fun submitLogin() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = loginUseCase(_state.value.email, _state.value.password)
            result.onSuccess {
                _state.update { it.copy(isLoading = false) }
                _effect.emit(LoginEffect.NavigateToHome)
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
                _effect.emit(LoginEffect.ShowToast("로그인 실패"))
            }
        }
    }
}
```

<br>

# 6. 실전 예제 (로그인 화면)

```kotlin
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onNavigateToHome: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                LoginEffect.NavigateToHome -> onNavigateToHome()
                is LoginEffect.ShowToast -> {
                    // Toast.makeText(...) 또는 SnackbarHostState 사용
                }
            }
        }
    }

    Column {
        TextField(
            value = state.email,
            onValueChange = { viewModel.onIntent(LoginIntent.EmailChanged(it)) }
        )
        TextField(
            value = state.password,
            onValueChange = { viewModel.onIntent(LoginIntent.PasswordChanged(it)) }
        )
        if (state.error != null) {
            Text(text = state.error, color = Color.Red)
        }
        Button(
            onClick = { viewModel.onIntent(LoginIntent.SubmitClicked) },
            enabled = !state.isLoading
        ) {
            if (state.isLoading) CircularProgressIndicator() else Text("로그인")
        }
    }
}
```

View는 `viewModel.onIntent(...)`를 호출하는 것 외에는 아무것도 직접 하지 않는다는 점이 핵심이다. State가 바뀌면 Compose가 자동으로 다시 그려준다.

<br>

# 7. Effect(SideEffect) 처리하기

Effect를 `StateFlow`가 아니라 `SharedFlow`로 만드는 이유가 중요하다.

- `StateFlow`는 항상 마지막 값을 들고 있다가, 새로 구독하는 쪽에게 즉시 다시 전달한다(replay). 그래서 "네비게이션 이동" 같은 일회성 이벤트에 쓰면, 화면 회전 후 재구독될 때 똑같은 이벤트가 다시 발생해버린다.
- `SharedFlow`(replay = 0)는 발생한 순간에 구독 중인 곳에만 전달하고 끝난다. 그래서 일회성 이벤트에 적합하다.

`LaunchedEffect(Unit)`으로 Compose 생명주기 동안 한 번만 collect를 시작하게 하는 것도 중요한 디테일이다. 이걸 빼먹고 매 recomposition마다 collect를 새로 시작하면 이벤트가 여러 번 처리되는 버그가 생긴다.

<br>

# 8. 자주 하는 실수

- **State를 var로 여러 개 나눠서 관리하기**: `var isLoading`, `var email`을 ViewModel에 따로따로 두면 MVI를 쓰는 의미가 없다. 반드시 하나의 State 데이터 클래스로 묶어야 한다.
- **State를 직접 mutate하기**: `state.email = "abc"`처럼 State 내부 값을 직접 바꾸면 안 된다. 항상 `copy()`로 새 객체를 만들어서 교체해야 Compose가 변경을 제대로 감지한다.
- **Effect를 State에 넣기**: "다이얼로그 띄우기" 같은 일회성 이벤트를 State 필드로 넣으면, 화면 회전마다 다이얼로그가 계속 다시 뜨는 문제가 생긴다. Effect는 반드시 별도 Flow로 분리한다.
- **Intent 없이 View에서 직접 로직 처리**: `onClick = { viewModel.someInternalFunction() }`처럼 View가 ViewModel의 세부 함수를 직접 호출하면 단방향 흐름이 깨진다. View는 오직 `onIntent(...)`만 호출해야 한다.

<br>

# 9. 헷갈리기 쉬운 부분

- MVI의 "Model"은 MVC의 Model(데이터베이스/서버 모델)과 다르다. 여기서 Model은 순수하게 "화면의 현재 상태(State)"를 의미한다.
- Reducer라는 이름의 클래스가 따로 없어도 된다. ViewModel 안에서 `when (intent) { ... }`으로 처리하는 로직 자체가 Reducer 역할을 한다.
- MVI라고 해서 무조건 별도 라이브러리(Orbit, MVIKotlin 등)가 필요한 건 아니다. `StateFlow` + `sealed interface`만으로도 충분히 MVI 패턴을 구현할 수 있다.
- State가 커질수록 `copy()` 호출이 많아지는데, 이건 MVI의 자연스러운 특성이다. 오히려 "지금 무엇이 바뀌었는지"가 코드에 명시적으로 드러나는 것이 장점으로 여겨진다.

<br>

# 정리

- MVI는 화면의 모든 상태를 하나의 불변 State로 묶고, 모든 사용자 행동을 Intent로 통일해서 처리하는 아키텍처다. 
- 데이터는 항상 Intent -> Reducer -> State -> View의 한 방향으로만 흐르며, View는 State를 직접 바꿀 수 없고 오직 Intent를 보내기만 한다.
- State는 화면에 계속 보여줄 값, Effect는 한 번만 발생하고 사라질 값으로 명확히 분리하는 것이 핵심이며, Effect는 `SharedFlow`로 구현해야 화면 회전 시 중복 발생을 막을 수 있다. 
- MVVM 대비 상태 예측 가능성이 높아 상태가 복잡한 화면일수록 이점이 커진다.
