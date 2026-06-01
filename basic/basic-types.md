# 기본 타입 (Basic Types)

코틀린은 다양한 숫자 타입과 문자, 불(Boolean) 타입을 제공한다.

자바와 달리 자동 형 변환을 지원하지 않으며, 타입 안정성을 중요하게 고려하여 설계되었다.

<br>

# 목차

1. 정수 타입
2. 부동소수점 수
3. 산술 연산
4. 비트 연산
5. 문자 타입(Char)
6. 수 변환
7. 불 타입과 논리 연산
8. 비교와 동등성

<br>

# 1. 정수 타입

정수를 저장하기 위한 타입이다.

| 타입 | 크기 | 범위 |
|--------|--------|--------|
| Byte | 8bit | -128 ~ 127 |
| Short | 16bit | -32,768 ~ 32,767 |
| Int | 32bit | -2,147,483,648 ~ 2,147,483,647 |
| Long | 64bit | -9,223,372,036,854,775,808 ~ 9,223,372,036,854,775,807 |

대부분의 경우 Int를 사용하며, 더 큰 범위가 필요할 경우 Long을 사용한다.

```kotlin
val byteValue: Byte = 100
val shortValue: Short = 1000
val intValue: Int = 100000
val longValue: Long = 10000000000L
```

Long 타입은 숫자 뒤에 `L`을 붙여야 한다.

```kotlin
val count = 10L
```

<br>

## 타입 추론

코틀린은 변수 선언 시 타입을 자동으로 추론한다.

```kotlin
val age = 20
val distance = 100L
```

```kotlin
age      // Int
distance // Long
```

<br>

# 2. 부동소수점 수

실수(소수)를 저장하기 위한 타입이다.

| 타입 | 크기 |
|--------|--------|
| Float | 32bit |
| Double | 64bit |

코틀린에서 소수는 기본적으로 Double로 추론된다.

```kotlin
val pi = 3.14
```

```kotlin
pi // Double
```

Float를 사용하려면 숫자 뒤에 `F`를 붙여야 한다.

```kotlin
val score = 99.5F
```

<br>

## Double과 Float 차이

```kotlin
val doubleValue = 0.1
val floatValue = 0.1F
```

Double이 Float보다 더 높은 정밀도를 제공한다.

따라서 특별한 이유가 없다면 Double 사용을 권장한다.

<br>

# 3. 산술 연산

숫자 타입은 기본적인 산술 연산을 지원한다.

| 연산자 | 설명 |
|----------|----------|
| + | 덧셈 |
| - | 뺄셈 |
| * | 곱셈 |
| / | 나눗셈 |
| % | 나머지 |

```kotlin
val a = 10
val b = 3

println(a + b)
println(a - b)
println(a * b)
println(a / b)
println(a % b)
```

결과

```text
13
7
30
3
1
```

<br>

## 정수 나눗셈

정수끼리 나누면 결과도 정수가 된다.

```kotlin
println(10 / 3)
```

결과

```text
3
```

실수 결과가 필요하다면 하나 이상의 값을 실수 타입으로 만들어야 한다.

```kotlin
println(10.0 / 3)
```

결과

```text
3.3333333333333335
```

<br>

# 4. 비트 연산

코틀린은 자바와 달리 비트 연산자를 함수 형태로 제공한다.

| 함수 | 설명 |
|--------|--------|
| shl | 왼쪽 시프트 |
| shr | 오른쪽 시프트 |
| ushr | 부호 없는 오른쪽 시프트 |
| and | AND |
| or | OR |
| xor | XOR |
| inv | NOT |

<br>

## 시프트 연산

```kotlin
val number = 5

println(number shl 1)
println(number shr 1)
```

결과

```text
10
2
```

<br>

## AND 연산

```kotlin
val result = 5 and 3

println(result)
```

결과 : 1


<br>

## OR 연산

```kotlin
val result = 5 or 3

println(result)
```

결과 : 7

```text
101   (5)
011   (3)
------
111
```

<br>

# 5. 문자 타입 (Char)

문자 하나를 저장하는 타입이다.

Char는 작은따옴표(`'`)를 사용한다.

```kotlin
val grade: Char = 'A'
```

```kotlin
val alphabet = 'K'
```

문자열(String)과는 다르다.

```kotlin
val charValue = 'A'
val stringValue = "A"
```

<br>

## 유니코드 문자

유니코드 값을 사용하여 문자를 표현할 수 있다.

```kotlin
val heart = '\u2665'

println(heart)
```

결과

```text
♥
```

<br>

# 6. 수 변환

코틀린은 자동 형 변환(Implicit Conversion)을 지원하지 않는다.

다음 코드는 컴파일 오류가 발생한다.

```kotlin
val number: Int = 10
val longValue: Long = number
```
<br>

## 명시적 변환

타입 변환 함수를 사용해야 한다.

```kotlin
val number = 10

val longValue = number.toLong()
val doubleValue = number.toDouble()
val floatValue = number.toFloat()
```

주요 변환 함수

```kotlin
toByte()
toShort()
toInt()
toLong()
toFloat()
toDouble()
```

<br>

## 문자열 → 숫자 변환

```kotlin
val age = "20"

val result = age.toInt()
```

안전하게 변환하려면 `toIntOrNull()`을 사용한다.

```kotlin
val age = "abc"

val result = age.toIntOrNull()

println(result)
```

결과

```text
null
```

<br>

# 7. 불 타입과 논리 연산

Boolean은 참(True) 또는 거짓(False)을 표현하는 타입이다.

```kotlin
val isLogin = true
val isAdmin = false
```

<br>

## 논리 연산자

| 연산자 | 설명 |
|----------|----------|
| && | AND |
| \|\| | OR |
| ! | NOT |

```kotlin
val isLogin = true
val isAdmin = false

println(isLogin && isAdmin)
println(isLogin || isAdmin)
println(!isLogin)
```

결과

```text
false
true
false
```

<br>

## Short Circuit Evaluation

논리 연산자는 불필요한 계산을 수행하지 않는다.

```kotlin
if (isLogin && checkPermission()) {

}
```

앞 조건이 false라면 뒤 함수는 실행되지 않는다.

이를 Short Circuit Evaluation이라고 한다.

<br>

# 8. 비교와 동등성

코틀린은 값 비교와 참조 비교를 구분한다.

<br>

## 값 비교 (==)

`==` 는 두 객체의 값이 같은지 비교한다.

```kotlin
val a = "Hello"
val b = "Hello"

println(a == b)
```

결과

```text
true
```

내부적으로 `equals()`를 호출한다.

<br>

## 참조 비교 (===)

`===` 는 두 변수가 같은 객체를 참조하는지 비교한다.

```kotlin
val a = String(charArrayOf('H'))
val b = String(charArrayOf('H'))

println(a === b)
```

결과

```text
false
```

두 객체의 내용은 같지만 서로 다른 객체이기 때문이다.

<br>

## == 와 === 차이

```kotlin
val a = "Kotlin"
val b = "Kotlin"

println(a == b)
println(a === b)
```

결과

```text
true
true
```

문자열 풀(String Pool)에 의해 동일한 객체를 참조할 수 있다.

일반적인 객체의 값 비교에는 `==`를 사용하고, 같은 객체인지 확인할 때만 `===`를 사용한다.

<br>

# 정리

- 정수 타입은 Byte, Short, Int, Long을 제공한다.
- 실수 타입은 Float, Double을 제공하며 기본 타입은 Double이다.
- 산술 연산자는 +, -, *, /, %를 사용한다.
- 비트 연산은 shl, shr, and, or 등의 함수를 사용한다.
- Char는 문자 하나를 저장하는 타입이다.
- 코틀린은 자동 형 변환을 지원하지 않는다.
- Boolean은 true 또는 false 값을 가진다.
- `==`는 값 비교, `===`는 참조 비교를 수행한다.
