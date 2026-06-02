# 함수 (Function)

<br>

## 목차

1. 함수란?
2. 기본 함수 선언
3. 반환 타입
4. 단일 표현식 함수
5. 매개변수(Parameter)
6. 기본값(Default Argument)
7. 이름 있는 인자(Named Argument)
8. 가변 인자(Vararg)
9. 지역 함수(Local Function)
10. 정리

<br>

## 1. 함수란?

함수(Function)는 특정 작업을 수행하는 코드 블록이다.

중복되는 코드를 하나의 함수로 분리하면 재사용성과 가독성을 높일 수 있다.

```kotlin
fun printHello() {
    println("Hello")
}
```

함수는 호출되어야 실행된다.

```kotlin
printHello()
```

<br>

## 2. 기본 함수 선언

코틀린 함수는 `fun` 키워드를 사용한다.

```kotlin
fun sum(a: Int, b: Int): Int {
    return a + b
}
```

호출:

```kotlin
val result = sum(10, 20)

println(result)
```

결과:

```text
30
```


### 구성 요소

```kotlin
fun sum(a: Int, b: Int): Int {
    return a + b
}
```

| 요소     | 설명    |
| ------ | ----- |
| fun    | 함수 선언 |
| sum    | 함수 이름 |
| a, b   | 매개변수  |
| Int    | 반환 타입 |
| return | 반환값   |

<br>

## 3. 반환 타입

반환값이 있는 경우 반환 타입을 명시한다.

```kotlin
fun multiply(a: Int, b: Int): Int {
    return a * b
}
```

### 반환값이 없는 경우

코틀린에서는 `Unit`을 사용한다.

```kotlin
fun printMessage(): Unit {
    println("Hello")
}
```

일반적으로 `Unit`은 생략한다.

```kotlin
fun printMessage() {
    println("Hello")
}
```

### Unit

`Unit`은 Java의 `void`와 비슷한 역할을 한다.

```kotlin
fun printMessage(): Unit
```

<br>

## 4. 단일 표현식 함수

함수 본문이 하나의 표현식으로 구성된다면 중괄호와 `return`을 생략할 수 있다.

```kotlin
fun sum(a: Int, b: Int): Int = a + b
```

반환 타입 추론도 가능하다.

```kotlin
fun sum(a: Int, b: Int) = a + b
```

### 비교

일반 함수

```kotlin
fun sum(a: Int, b: Int): Int {
    return a + b
}
```

단일 표현식 함수

```kotlin
fun sum(a: Int, b: Int) = a + b
```

코틀린에서 자주 사용하는 문법이다.

<br>

## 5. 매개변수(Parameter)

함수는 여러 개의 매개변수를 가질 수 있다.

```kotlin
fun introduce(name: String, age: Int) {
    println("이름: $name")
    println("나이: $age")
}
```

호출:

```kotlin
introduce("Kim", 20)
```

<br>

## 6. 기본값(Default Argument)

매개변수에 기본값을 지정할 수 있다.

```kotlin
fun greet(name: String = "Guest") {
    println("Hello $name")
}
```

호출:

```kotlin
greet()
```

결과:

```text
Hello Guest
```

값을 전달하면 기본값 대신 사용된다.

```kotlin
greet("Kim")
```

결과:

```text
Hello Kim
```

### 여러 기본값

```kotlin
fun createUser(
    name: String = "Unknown",
    age: Int = 0
) {
    println("$name / $age")
}
```

```kotlin
createUser()
```

결과:

```text
Unknown / 0
```

### 특징

Java에서는 오버로딩으로 처리하던 경우를 기본값으로 대체할 수 있다.

```java
User()
User(String name)
User(String name, int age)
```

↓

```kotlin
fun createUser(
    name: String = "Unknown",
    age: Int = 0
)
```

<br>

## 7. 이름 있는 인자(Named Argument)

인자의 이름을 명시하여 전달할 수 있다.

```kotlin
fun createUser(
    name: String,
    age: Int
) {
    println("$name / $age")
}
```

호출:

```kotlin
createUser(
    name = "Kim",
    age = 20
)
```

순서를 변경할 수도 있다.

```kotlin
createUser(
    age = 20,
    name = "Kim"
)
```

### 장점

매개변수가 많은 함수에서 가독성이 좋아진다.

```kotlin
createUser(
    name = "Kim",
    age = 20,
    isAdmin = true,
    isVerified = false
)
```

<br>

## 8. 가변 인자 (Vararg)

전달받는 인자의 개수가 정해져 있지 않을 때 사용한다.

```kotlin
fun printNumbers(vararg numbers: Int) {
    for (number in numbers) {
        println(number)
    }
}
```

호출:

```kotlin
printNumbers(1, 2, 3, 4, 5)
```

결과:

```text
1
2
3
4
5
```

### 배열 전달

배열을 전달할 때는 Spread Operator(`*`)를 사용한다.

```kotlin
val numbers = intArrayOf(1, 2, 3)

printNumbers(*numbers)
```

<br>

## 9. 지역 함수 (Local Function)

함수 내부에 함수를 선언할 수 있다.

```kotlin
fun validateUser(name: String) {

    fun validateName() {
        require(name.isNotBlank())
    }

    validateName()
}
```

외부 함수 내부에서만 사용되는 로직을 분리할 때 활용한다.

### 사용 예시

```kotlin
fun registerUser(name: String, age: Int) {

    fun validate() {
        require(name.isNotBlank())
        require(age > 0)
    }

    validate()

    println("회원 등록")
}
```

<br>

## 정리

| 기능               | 설명       |
| ---------------- | -------- |
| fun              | 함수 선언    |
| Unit             | 반환값 없음   |
| 반환 타입 추론         | 타입 생략 가능 |
| 단일 표현식 함수        | `=` 사용   |
| Default Argument | 기본값 지정   |
| Named Argument   | 이름 기반 호출 |
| Vararg           | 가변 인자    |
| Spread Operator  | `*array` |
| Local Function   | 함수 내부 함수 |

## 참고

* Kotlin 공식 문서: Functions
* Kotlin 공식 문서: Named Arguments
* Kotlin 공식 문서: Varargs
