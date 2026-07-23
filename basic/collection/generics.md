# enum class (열거형 클래스)

## 목차
1. enum class란?
2. 기본 사용법
3. when과 함께 사용하기
4. 생성자와 프로퍼티를 가지는 enum
5. 메서드를 가지는 enum
6. 추상 메서드와 enum
7. 인터페이스 구현
8. enum의 내장 프로퍼티와 메서드
9. companion object와 함께 사용
10. Android/Compose에서의 활용
11. sealed class와의 차이
12. 자주 하는 실수
13. 정리

<br>

## 1. enum class란?

enum class는 정해진 값들의 집합을 표현하는 타입이다.

예를 들어 요일, 상태값, 방향처럼 "미리 정해진 선택지" 안에서만 값을 가져야 할 때 사용한다.

```kotlin
enum class Direction {
    NORTH, SOUTH, EAST, WEST
}
```

이렇게 하면 `Direction` 타입은 저 네 가지 값 외에는 가질 수 없다.

일반 문자열이나 상수로 관리할 때보다 실수를 컴파일 단계에서 막을 수 있다는 장점이 있다.

<br>

## 2. 기본 사용법

```kotlin
enum class Direction {
    NORTH, SOUTH, EAST, WEST
}

fun main() {
    val dir = Direction.NORTH
    println(dir)
}
```

결과

```
NORTH
```

각 값은 `Direction` 타입의 인스턴스이다.

```kotlin
val dir: Direction = Direction.EAST
```

문자열과 달리 오타를 내면 컴파일 오류가 발생한다.

```kotlin
val dir = Direction.NORTHH // 컴파일 오류
```

<br>

## 3. when과 함께 사용하기

enum은 `when`과 함께 사용할 때 진가를 발휘한다.

```kotlin
fun move(direction: Direction) {
    when (direction) {
        Direction.NORTH -> println("위로 이동")
        Direction.SOUTH -> println("아래로 이동")
        Direction.EAST -> println("오른쪽으로 이동")
        Direction.WEST -> println("왼쪽으로 이동")
    }
}
```

모든 case를 다루면 `else`가 필요 없다.

```kotlin
// 모든 case 처리 → else 불필요
when (direction) {
    Direction.NORTH -> {}
    Direction.SOUTH -> {}
    Direction.EAST -> {}
    Direction.WEST -> {}
}
```

만약 새로운 값을 enum에 추가하면 `when`에서 처리하지 않은 case가 컴파일 오류로 드러난다.

이 덕분에 값 추가를 누락 없이 관리할 수 있다.

<br>

## 4. 생성자와 프로퍼티를 가지는 enum

각 enum 값은 생성자를 통해 고유한 데이터를 가질 수 있다.

```kotlin
enum class Direction(val degree: Int) {
    NORTH(0),
    SOUTH(180),
    EAST(90),
    WEST(270)
}
```

사용

```kotlin
val dir = Direction.EAST
println(dir.degree)
```

결과

```
90
```

<br>

프로퍼티를 여러 개 가질 수도 있다.

```kotlin
enum class HttpStatus(val code: Int, val message: String) {
    OK(200, "성공"),
    NOT_FOUND(404, "찾을 수 없음"),
    SERVER_ERROR(500, "서버 오류")
}
```

사용

```kotlin
val status = HttpStatus.NOT_FOUND
println("${status.code} ${status.message}")
```

결과

```
404 찾을 수 없음
```

<br>

## 5. 메서드를 가지는 enum

enum class 내부에 일반 함수도 정의할 수 있다.

```kotlin
enum class HttpStatus(val code: Int) {
    OK(200),
    NOT_FOUND(404),
    SERVER_ERROR(500);

    fun isSuccess(): Boolean {
        return code in 200..299
    }
}
```

사용

```kotlin
println(HttpStatus.OK.isSuccess())
println(HttpStatus.NOT_FOUND.isSuccess())
```

결과

```
true
false
```

프로퍼티나 메서드를 정의할 때는 값 목록 뒤에 세미콜론(`;`)이 필요하다.

<br>

## 6. 추상 메서드와 enum

각 값마다 다른 동작을 구현하고 싶을 때는 추상 메서드를 사용한다.

```kotlin
enum class Operation {
    PLUS {
        override fun apply(a: Int, b: Int) = a + b
    },
    MINUS {
        override fun apply(a: Int, b: Int) = a - b
    };

    abstract fun apply(a: Int, b: Int): Int
}
```

사용

```kotlin
println(Operation.PLUS.apply(3, 2))
println(Operation.MINUS.apply(3, 2))
```

결과

```
5
1
```

값마다 별도의 로직이 필요할 때 `when`보다 이 방식이 더 명확할 수 있다.

<br>

## 7. 인터페이스 구현

enum class는 인터페이스를 구현할 수 있다.

```kotlin
interface Describable {
    fun describe(): String
}

enum class Direction : Describable {
    NORTH {
        override fun describe() = "북쪽"
    },
    SOUTH {
        override fun describe() = "남쪽"
    }
}
```

사용

```kotlin
println(Direction.NORTH.describe())
```

결과

```
북쪽
```

단, enum class는 다른 클래스를 상속할 수는 없다. 인터페이스 구현만 가능하다.

<br>

## 8. enum의 내장 프로퍼티와 메서드

enum class는 기본적으로 몇 가지 기능을 제공한다.

```kotlin
val dir = Direction.EAST

println(dir.name)
println(dir.ordinal)
```

결과

```
EAST
2
```

- `name` : 선언한 이름 그대로의 문자열
- `ordinal` : 선언 순서 (0부터 시작)

<br>

모든 값을 가져올 때

```kotlin
Direction.values().forEach {
    println(it)
}
```

문자열로부터 enum 값 찾기

```kotlin
val dir = Direction.valueOf("NORTH")
```

존재하지 않는 이름이면 예외가 발생한다.

```kotlin
Direction.valueOf("UP") // IllegalArgumentException
```

최신 Kotlin에서는 `values()` 대신 `entries`를 사용하는 것이 권장된다.

```kotlin
Direction.entries.forEach {
    println(it)
}
```

<br>

## 9. companion object와 함께 사용

enum class 안에 companion object를 두면 값 검색 같은 로직을 함께 관리할 수 있다.

```kotlin
enum class HttpStatus(val code: Int) {
    OK(200),
    NOT_FOUND(404);

    companion object {
        fun fromCode(code: Int): HttpStatus {
            return entries.first { it.code == code }
        }
    }
}
```

사용

```kotlin
val status = HttpStatus.fromCode(404)
println(status)
```

결과

```
NOT_FOUND
```

<br>

## 10. Android/Compose에서의 활용

화면 상태를 표현할 때 자주 사용된다.

```kotlin
enum class LoadState {
    LOADING, SUCCESS, ERROR
}
```

```kotlin
when (uiState.loadState) {
    LoadState.LOADING -> CircularProgressIndicator()
    LoadState.SUCCESS -> ContentScreen()
    LoadState.ERROR -> ErrorScreen()
}
```

네비게이션 탭이나 정렬 옵션처럼 선택지가 고정된 값에도 자주 쓰인다.

```kotlin
enum class SortOrder {
    LATEST, POPULAR, NAME
}
```

<br>

## 11. sealed class와의 차이

enum class와 sealed class는 비슷해 보이지만 목적이 다르다.

- enum class : 각 값이 데이터를 가지지 않거나, 고정된 형태의 데이터만 가짐
- sealed class : 각 하위 타입이 서로 다른 구조의 데이터를 가질 수 있음

```kotlin
// enum: 상태 이름만 필요할 때
enum class LoadState {
    LOADING, SUCCESS, ERROR
}

// sealed class: 상태마다 다른 데이터가 필요할 때
sealed class UiState {
    object Loading : UiState()
    data class Success(val data: List<String>) : UiState()
    data class Error(val message: String) : UiState()
}
```

`ERROR` 상태에서 에러 메시지처럼 값마다 다른 데이터를 담아야 한다면 sealed class가 더 적합하다.

<br>

## 12. 자주 하는 실수

### 1. enum 값 뒤에 세미콜론을 빼먹는다

```kotlin
enum class Status(val code: Int) {
    ACTIVE(1),
    INACTIVE(0)

    fun isActive() = this == ACTIVE // 컴파일 오류
}
```

프로퍼티나 메서드를 추가할 때는 값 목록 뒤에 `;`이 반드시 필요하다.

<br>

### 2. valueOf에서 예외 처리를 안 한다

```kotlin
val status = HttpStatus.valueOf(input) // 잘못된 문자열이면 예외 발생
```

외부 입력값을 변환할 때는 예외 처리나 nullable 대안을 함께 고려해야 한다.

<br>

### 3. 데이터가 다양한 상태를 enum으로 표현하려 한다

```kotlin
enum class UiState {
    LOADING, SUCCESS, ERROR
}
```

`SUCCESS`일 때 데이터를, `ERROR`일 때 메시지를 함께 담아야 한다면 enum보다 sealed class가 적합하다.

<br>

### 4. values() 남용

```kotlin
Direction.values() // 매번 배열을 새로 생성
```

반복 호출이 잦다면 `entries`를 사용하는 것이 더 효율적이다.

<br>

## 13. 정리

- enum class는 정해진 값들의 집합을 표현하는 타입이다.
- `when`과 함께 사용하면 모든 case를 강제로 처리하게 만들어 실수를 줄여준다.
- 생성자를 통해 각 값마다 고유한 프로퍼티를 가질 수 있다.
- 추상 메서드를 선언하면 값마다 다른 동작을 구현할 수 있다.
- 인터페이스는 구현할 수 있지만 클래스 상속은 불가능하다.
- `name`, `ordinal`, `entries`, `valueOf()` 등의 내장 기능을 제공한다.
- 값마다 서로 다른 구조의 데이터가 필요하다면 enum class보다 sealed class가 적합하다.
- Android/Compose에서는 화면 상태나 고정된 선택지를 표현할 때 자주 사용된다.
