# 예외 처리 (Exception)

## 목차

1. 예외(Exception)란?
2. try-catch
3. finally
4. try 표현식
5. throw
6. require
7. check
8. runCatching
9. 코틀린의 Checked Exception
10. 정리

<br>

## 1. 예외(Exception)란?

예외(Exception)는 프로그램 실행 중 발생하는 오류 상황을 의미한다.

예를 들어 다음 코드는 0으로 나누기 때문에 예외가 발생한다.

```kotlin
val result = 10 / 0
```

실행 결과

```text
ArithmeticException
```

예외가 발생하면 프로그램이 정상적으로 동작하지 못할 수 있으므로 적절한 처리가 필요하다.

<br>

## 2. try-catch

예외가 발생할 수 있는 코드를 `try` 블록에 작성한다.

```kotlin
try {
    val result = 10 / 0
} catch (e: Exception) {
    println("예외 발생")
}
```

실행 결과

```text
예외 발생
```

### 특정 예외 처리

```kotlin
try {
    val result = 10 / 0
} catch (e: ArithmeticException) {
    println("0으로 나눌 수 없습니다.")
}
```

### 여러 예외 처리

```kotlin
try {
    // 코드
} catch (e: ArithmeticException) {
    println("산술 오류")
} catch (e: NumberFormatException) {
    println("형식 오류")
}
```

<br>

## 3. finally

예외 발생 여부와 상관없이 항상 실행된다.

```kotlin
try {
    println("작업 수행")
} catch (e: Exception) {
    println("예외 발생")
} finally {
    println("항상 실행")
}
```

실행 결과

```text
작업 수행
항상 실행
```

주로 리소스 정리 작업에 사용된다.

```kotlin
try {
    // 파일 읽기
} finally {
    // 파일 닫기
}
```

<br>

## 4. try 표현식

코틀린의 `try-catch`는 표현식(Expression)이다.

값을 반환할 수 있다.

```kotlin
val result = try {
    "100".toInt()
} catch (e: NumberFormatException) {
    0
}

println(result)
```

결과

```text
100
```

### 실패하는 경우

```kotlin
val result = try {
    "abc".toInt()
} catch (e: NumberFormatException) {
    0
}

println(result)
```

결과

```text
0
```

### 특징

Java

```java
int result;

try {
    result = Integer.parseInt(value);
} catch (...) {
    result = 0;
}
```

코틀린

```kotlin
val result = try {
    value.toInt()
} catch (e: Exception) {
    0
}
```

`if`, `when`과 마찬가지로 표현식으로 사용할 수 있다.

<br>

## 5. throw

예외를 직접 발생시킬 수 있다.

```kotlin
throw IllegalArgumentException("잘못된 값입니다.")
```

함수에서 사용할 수 있다.

```kotlin
fun validate(age: Int) {
    if (age < 0) {
        throw IllegalArgumentException("나이는 0 이상이어야 합니다.")
    }
}
```

<br>

## 6. require

함수의 입력값 검증에 사용한다.

조건이 거짓이면 `IllegalArgumentException`을 발생시킨다.

```kotlin
fun register(age: Int) {
    require(age > 0) {
        "나이는 0보다 커야 합니다."
    }
}
```

### 동작

```kotlin
register(-1)
```

실행 결과

```text
IllegalArgumentException
```

### 장점

```kotlin
if (age <= 0) {
    throw IllegalArgumentException(...)
}
```

↓

```kotlin
require(age > 0)
```

더 간결하게 작성할 수 있다.

<br>

## 7. check

객체 상태를 검증할 때 사용한다.

조건이 거짓이면 `IllegalStateException`을 발생시킨다.

```kotlin
val isInitialized = false

check(isInitialized) {
    "초기화되지 않았습니다."
}
```

실행 결과

```text
IllegalStateException
```

### require vs check

| 함수      | 사용 목적  | 발생 예외                    |
| ------- | ------ | ------------------------ |
| require | 입력값 검증 | IllegalArgumentException |
| check   | 상태 검증  | IllegalStateException    |

<br>

## 8. runCatching

예외를 Result 객체로 처리한다.

```kotlin
val result = runCatching {
    "100".toInt()
}
```

성공

```kotlin
result.onSuccess {
    println(it)
}
```

실패

```kotlin
result.onFailure {
    println(it.message)
}
```

### getOrNull

```kotlin
val value = runCatching {
    "100".toInt()
}.getOrNull()
```

### getOrDefault

```kotlin
val value = runCatching {
    "abc".toInt()
}.getOrDefault(0)
```

결과

```text
0
```

Android 실무에서도 자주 사용된다.

<br>

## 9. 코틀린의 Checked Exception

Java에는 Checked Exception이 존재한다.

```java
public void read() throws IOException {
}
```

호출 시 반드시 처리해야 한다.

```java
try {
    read();
} catch (IOException e) {
}
```

하지만 코틀린은 Checked Exception을 지원하지 않는다.

```kotlin
fun read() {
}
```

컴파일러가 예외 처리를 강제하지 않는다.

### 특징

* Checked Exception 없음
* 모든 예외는 Runtime Exception처럼 동작
* 코드가 간결해짐

코틀린의 대표적인 특징 중 하나다.

<br>

## 정리

| 기능                | 설명           |
| ----------------- | ------------ |
| try-catch         | 예외 처리        |
| finally           | 항상 실행        |
| try 표현식           | 값 반환 가능      |
| throw             | 예외 발생        |
| require           | 입력값 검증       |
| check             | 상태 검증        |
| runCatching       | Result 기반 처리 |
| Checked Exception | 지원하지 않음      |

<br>

## 참고

* Kotlin 공식 문서: Exceptions
* Kotlin 공식 문서: Preconditions
* Kotlin 공식 문서: Result
