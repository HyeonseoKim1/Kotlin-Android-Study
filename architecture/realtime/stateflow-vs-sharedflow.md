# StateFlow vs SharedFlow

StateFlow와 SharedFlow는 Kotlin Coroutines에서 제공하는 Hot Stream이다.

둘 다 Flow 기반이지만 목적이 다르다.

- StateFlow → 현재 상태(State)를 관리
- SharedFlow → 이벤트(Event)를 전달

Android에서는 ViewModel과 Compose를 사용할 때 거의 항상 함께 사용된다.

<br>

# 목차

1. StateFlow와 SharedFlow란?
2. Cold Flow와 Hot Flow
3. StateFlow
4. SharedFlow
5. 차이점 비교
6. 언제 무엇을 사용해야 하는가
7. Compose에서 사용하는 방법
8. ViewModel 예제
9. 실무에서 자주 하는 실수
10. 정리

<br>

## 1. StateFlow와 SharedFlow란?

Flow는 데이터를 비동기로 전달하는 방법이다.

하지만 Flow에는 크게 두 종류가 있다.

- Cold Flow
- Hot Flow

StateFlow와 SharedFlow는 Hot Flow에 속한다.

즉, 데이터를 계속 가지고 있으며 여러 곳에서 동시에 구독할 수 있다.

<br>

## 2. Cold Flow와 Hot Flow

### Cold Flow

Flow를 collect해야 데이터가 생성된다.

```kotlin
val flow = flow {
    println("Start")
    emit(1)
}
```

```kotlin
flow.collect()
flow.collect()
```

실행 결과

```
Start
Start
```

collect할 때마다 처음부터 실행된다.

<br>

### Hot Flow

StateFlow와 SharedFlow는 collect 여부와 상관없이 계속 살아있다.

```kotlin
private val state = MutableStateFlow(0)
```

이미 값이 저장되어 있으며,
새로운 구독자는 현재 값을 바로 받을 수 있다.

<br>

## 3. StateFlow

StateFlow는 이름 그대로 "상태"를 저장한다.

예를 들어

- 로그인 여부
- 화면 데이터
- 로딩 상태
- 사용자 정보

처럼 현재 상태를 표현할 때 사용한다.

<br>

### 특징

- 항상 현재 값을 가진다.
- 초기값이 반드시 필요하다.
- 같은 값을 넣으면 다시 전달하지 않는다.
- 새로운 구독자는 현재 값을 즉시 받는다.

<br>

### 생성

```kotlin
private val _uiState = MutableStateFlow(HomeUiState())
```

읽기 전용으로 노출한다.

```kotlin
val uiState = _uiState.asStateFlow()
```

<br>

### 값 변경

```kotlin
_uiState.value = HomeUiState(
    isLoading = true
)
```

또는

```kotlin
_uiState.update {
    it.copy(
        isLoading = true
    )
}
```

update를 사용하는 것이 안전하다.

<br>

### Compose에서 수집

```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

StateFlow는 Compose와 가장 궁합이 좋다.

<br>

## 4. SharedFlow

SharedFlow는 상태가 아니라 이벤트를 전달한다.

예를 들어

- Toast
- Snackbar
- Navigation
- Dialog 표시
- 화면 이동

같은 일회성 이벤트를 전달한다.

<br>

### 특징

- 초기값이 없다.
- 이벤트를 여러 구독자에게 전달한다.
- 상태를 저장하지 않는다.
- 같은 이벤트도 계속 전달할 수 있다.

<br>

### 생성

```kotlin
private val _event = MutableSharedFlow<UiEvent>()
```

읽기 전용으로 공개한다.

```kotlin
val event = _event.asSharedFlow()
```

<br>

### 이벤트 전송

```kotlin
viewModelScope.launch {
    _event.emit(UiEvent.ShowToast("저장 완료"))
}
```

<br>

### 이벤트 수집

```kotlin
LaunchedEffect(Unit) {
    viewModel.event.collect { event ->
        when (event) {
            is UiEvent.ShowToast -> {
                Toast.makeText(
                    context,
                    event.message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
```

이벤트는 collectAsState()가 아니라 collect()로 받는다.

<br>

## 5. 차이점 비교

|항목|StateFlow|SharedFlow|
|---|---|---|
|목적|상태 관리|이벤트 전달|
|초기값|필수|없음|
|현재 값 보관|가능|기본적으로 없음|
|새 구독자|현재 값 즉시 전달|기본적으로 전달 안 함|
|같은 값 전달|안 함|가능|
|대표 용도|UI State|Toast, Navigation|

<br>

## 6. 언제 무엇을 사용해야 하는가

### StateFlow 사용

```text
화면에 표시되는 값
```

예시

- 사용자 이름
- 로딩 여부
- 게시글 목록
- 로그인 상태

<br>

```kotlin
data class HomeUiState(
    val isLoading: Boolean = false,
    val users: List<User> = emptyList()
)
```

<br>

### SharedFlow 사용

```text
한 번만 실행되는 동작
```

예시

- Toast
- Snackbar
- 화면 이동
- Dialog 열기

<br>

```kotlin
sealed interface UiEvent {

    data class ShowToast(
        val message: String
    ) : UiEvent

    data object NavigateDetail : UiEvent
}
```

<br>

## 7. Compose에서 사용하는 방법

### StateFlow

```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

화면 상태를 자동으로 Compose State로 변환한다.

StateFlow는 recomposition을 발생시킨다.

<br>

### SharedFlow

```kotlin
LaunchedEffect(Unit) {
    viewModel.event.collect {
        // Event 처리
    }
}
```

이벤트는 화면을 다시 그릴 필요가 없기 때문에 collectAsState()를 사용하지 않는다.

<br>

## 8. ViewModel 예제

```kotlin
class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<UiEvent>()
    val event = _event.asSharedFlow()

    fun load() {

        _uiState.update {
            it.copy(isLoading = true)
        }

        viewModelScope.launch {

            delay(1000)

            _uiState.update {
                it.copy(isLoading = false)
            }

            _event.emit(
                UiEvent.ShowToast("불러오기 완료")
            )
        }
    }
}
```

Compose

```kotlin
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel()
) {

    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->

            when (event) {

                is UiEvent.ShowToast -> {
                    Toast.makeText(
                        context,
                        event.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                UiEvent.NavigateDetail -> {

                }
            }
        }
    }

    if (uiState.isLoading) {
        CircularProgressIndicator()
    }
}
```

<br>

## 9. replay란?

SharedFlow에는 replay 옵션이 있다.

```kotlin
MutableSharedFlow<String>(
    replay = 1
)
```

replay가 1이면

새로운 구독자가 가장 최근 이벤트 하나를 받을 수 있다.

기본값은

```kotlin
replay = 0
```

즉,

이전에 발생한 이벤트는 새 구독자가 받지 못한다.

Toast나 Navigation처럼 한 번만 처리해야 하는 이벤트는 대부분 `replay = 0`을 사용한다.

반대로 가장 최근 이벤트를 새 구독자에게도 전달해야 하는 경우에는 `replay`를 늘려 사용할 수 있다.

<br>

## 10. 실무에서 자주 하는 실수

### StateFlow로 Toast 전달

```kotlin
private val _toast =
    MutableStateFlow("")
```

문제점

- 화면 회전 시 다시 Toast가 출력될 수 있다.
- 이벤트가 상태처럼 남아 있게 된다.

Toast는 SharedFlow를 사용한다.

<br>

### SharedFlow로 화면 상태 관리

```kotlin
private val users =
    MutableSharedFlow<List<User>>()
```

새로운 화면은 현재 데이터를 받을 수 없다.

화면 상태는 StateFlow를 사용한다.

<br>

### Mutable 타입을 그대로 노출

잘못된 예

```kotlin
val uiState = MutableStateFlow(...)
```

외부에서 값을 변경할 수 있다.

올바른 방법

```kotlin
private val _uiState = MutableStateFlow(HomeUiState())

val uiState = _uiState.asStateFlow()
```

<br>

### update() 대신 value 사용

```kotlin
_uiState.value =
    _uiState.value.copy(...)
```

가능하지만 동시에 여러 곳에서 수정하면 값을 잃어버릴 수 있다.

실무에서는

```kotlin
_uiState.update {
    it.copy(...)
}
```

를 사용하는 경우가 많다.

<br>

## 정리

- StateFlow는 현재 상태를 저장하는 Hot Flow다.
- SharedFlow는 이벤트를 전달하는 Hot Flow다.
- UI State는 StateFlow를 사용한다.
- Toast, Snackbar, Navigation은 SharedFlow를 사용한다.
- Compose에서는 StateFlow는 `collectAsStateWithLifecycle()`, SharedFlow는 `LaunchedEffect + collect()` 조합을 사용한다.
- `MutableStateFlow`와 `MutableSharedFlow`는 private으로 선언하고, 외부에는 `asStateFlow()`, `asSharedFlow()`를 통해 읽기 전용으로 노출하는 것이 일반적인 패턴이다.
