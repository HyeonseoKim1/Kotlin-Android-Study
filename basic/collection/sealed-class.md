# sealed class (실드 클래스)

## 목차
1. sealed class란?
2. 기본 사용법
3. when과 함께 사용하기 (else 불필요)
4. sealed interface
5. 하위 타입의 다양한 형태
6. object vs data class 하위 타입
7. enum class와의 차이
8. 중첩 계층 구조
9. Android/Compose에서의 활용 (UiState 패턴)
10. 자주 하는 실수
11. 정리

<br>

## 1. sealed class란?

sealed class는 하위 타입의 종류를 제한된 범위 안에서만 정의할 수 있게 하는 클래스이다.

일반 상속은 어디서든 하위 클래스를 추가할 수 있지만, sealed class는 같은 파일(또는 같은 패키지) 안에서만 상속이 가능하다.

```kotlin
sealed class UiState

class Loading : UiState()
class Success(val data: List<String>) : UiState()
class Error(val message: String) : UiState()
```

컴파일러는 `UiState`의 모든 하위 타입을 알고 있기 때문에, `when`에서 모든 case를 처리했는지 검사할 수 있다.

<br>

## 2. 기본 사용법

```kotlin
sealed class UiState {
    object Loading : UiState()
    data class Success(val data: List<String>) : UiState()
    data class Error(val message: String) : UiState()
}
```

사용

```kotlin
val state: UiState = UiState.Success(listOf("a", "b"))
```

각 하위 타입은 필요에 따라 `object`, `data class`, `class` 중 알맞은 형태로 선언한다.

<br>

## 3. when과 함께 사용하기 (else 불필요)

```kotlin
fun render(state: UiState) {
    when (state) {
        is UiState.Loading -> println("로딩 중")
        is UiState.Success -> println("데이터: ${state.data}")
        is UiState.Error -> println("에러: ${state.message}")
    }
}
```

모든 하위 타입을 처리하면 `else`가 필요 없다.

```kotlin
// 모든 하위 타입 처리 → else 불필요
when (state) {
    is UiState.Loading -> {}
    is UiState.Success -> {}
    is UiState.Error -> {}
}
```

새로운 하위 타입을 추가하면, 처리하지 않은 `when` 분기가 컴파일 오류로 드러난다.

이 덕분에 상태 추가를 누락 없이 관리할 수 있다.

<br>

## 4. sealed interface

Kotlin에서는 sealed class 대신 sealed interface도 사용할 수 있다.

```kotlin
sealed interface UiState {
    object Loading : UiState
    data class Success(val data: List<String>) : UiState
    data class Error(val message: String) : UiState
}
```

sealed class와 동작은 거의 같지만, 인터페이스이기 때문에 하위 타입이 다른 클래스를 함께 상속받을 수 있다는 차이가 있다.

```kotlin
class NetworkError(val code: Int) : Exception(), UiState
```

<br>

## 5. 하위 타입의 다양한 형태

sealed class의 하위 타입은 상황에 맞게 선택한다.

```kotlin
sealed class Result {
    object Empty : Result()
    data class Value(val amount: Int) : Result()
    class Failure(val cause: Throwable) : Result()
}
```

- `object` : 데이터가 필요 없는 단일 상태
- `data class` : 값 비교나 복사가 필요한 상태
- `class` : 값 비교가 필요 없는 상태

<br>

## 6. object vs data class 하위 타입

```kotlin
sealed class UiState {
    object Loading : UiState()
    data class Success(val data: List<String>) : UiState()
}
```

`object`로 선언하면 인스턴스가 하나만 생성된다.

```kotlin
val a = UiState.Loading
val b = UiState.Loading

println(a === b)
```

결과

```
true
```

`data class`는 매번 새로운 인스턴스를 생성하고, 값 기준으로 비교된다.

```kotlin
val a = UiState.Success(listOf("x"))
val b = UiState.Success(listOf("x"))

println(a == b)
```

결과

```
true
```

<br>

## 7. enum class와의 차이

enum class와 sealed class는 비슷해 보이지만 목적이 다르다.

```kotlin
// enum: 각 값이 같은 구조의 데이터만 가짐 (또는 데이터 없음)
enum class LoadState {
    LOADING, SUCCESS, ERROR
}

// sealed class: 하위 타입마다 다른 구조의 데이터를 가짐
sealed class UiState {
    object Loading : UiState()
    data class Success(val data: List<String>) : UiState()
    data class Error(val message: String) : UiState()
}
```

- enum class : 값의 목록이 고정되어 있고, 각 값이 같은 형태의 데이터를 가짐
- sealed class : 하위 타입마다 서로 다른 프로퍼티 구조를 가질 수 있음

`ERROR`일 때만 메시지가 필요하고 `SUCCESS`일 때만 데이터가 필요한 것처럼, 상태마다 필요한 데이터가 다르다면 sealed class가 더 적합하다.

<br>

## 8. 중첩 계층 구조

sealed class는 하위 타입이 또 다른 sealed class일 수도 있다.

```kotlin
sealed class NetworkResult {
    data class Success(val body: String) : NetworkResult()

    sealed class Failure : NetworkResult() {
        data class Http(val code: Int) : Failure()
        data class Network(val cause: Throwable) : Failure()
    }
}
```

```kotlin
when (result) {
    is NetworkResult.Success -> println(result.body)
    is NetworkResult.Failure.Http -> println("HTTP ${result.code}")
    is NetworkResult.Failure.Network -> println("네트워크 오류")
}
```

세분화된 에러 처리가 필요할 때 유용한 패턴이다.

<br>

## 9. Android/Compose에서의 활용 (UiState 패턴)

화면 상태를 표현할 때 가장 많이 쓰이는 패턴이다.

```kotlin
sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val items: List<Item>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
```

```kotlin
@Composable
fun HomeScreen(state: HomeUiState) {
    when (state) {
        is HomeUiState.Loading -> CircularProgressIndicator()
        is HomeUiState.Success -> ItemList(state.items)
        is HomeUiState.Error -> ErrorMessage(state.message)
    }
}
```

ViewModel에서 상태를 발행할 때도 자주 사용된다.

```kotlin
private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
val uiState: StateFlow<HomeUiState> = _uiState
```

<br>

## 10. 자주 하는 실수

### 1. else를 습관적으로 추가한다

```kotlin
when (state) {
    is UiState.Loading -> {}
    is UiState.Success -> {}
    else -> {} // 불필요
}
```

모든 하위 타입을 이미 처리했다면 `else`는 오히려 새로운 상태 추가를 놓치게 만들 수 있다.

<br>

### 2. 데이터가 없는 상태를 class로 선언한다

```kotlin
class Loading : UiState() // 매번 새 인스턴스 생성
```

데이터가 필요 없는 상태는 `object`로 선언하는 것이 효율적이다.

```kotlin
object Loading : UiState()
```

<br>

### 3. 모든 상태를 enum으로 표현하려 한다

```kotlin
enum class UiState {
    LOADING, SUCCESS, ERROR
}
```

`SUCCESS`나 `ERROR`마다 다른 데이터가 필요하다면 enum보다 sealed class가 적합하다.

<br>

### 4. 하위 타입을 다른 파일에 흩어 놓는다

sealed class의 장점은 컴파일러가 모든 하위 타입을 파악할 수 있다는 것이다.

관련 하위 타입은 같은 파일이나 sealed class 내부에 함께 두는 것이 관리에 유리하다.

<br>

## 11. 정리

- sealed class는 하위 타입의 종류를 제한된 범위 안에서만 정의하게 하는 클래스이다.
- `when`에서 모든 하위 타입을 처리하면 `else` 없이도 컴파일이 가능하다.
- 하위 타입은 `object`, `data class`, `class` 중 필요에 맞는 형태로 선언한다.
- sealed interface를 사용하면 하위 타입이 다른 클래스를 함께 상속받을 수 있다.
- 하위 타입마다 다른 구조의 데이터가 필요하다면 enum class보다 sealed class가 적합하다.
- sealed class를 중첩하면 세분화된 에러 처리 같은 계층 구조를 표현할 수 있다.
- Android/Compose에서는 화면의 UiState를 표현하는 대표적인 패턴으로 사용된다.
