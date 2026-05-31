# Kotlin 기본 문법

## 목차

1. 주석(Comment)
2. 변수 정의
3. 식별자(Identifier)
4. 가변 변수(var)와 불변 변수(val)
5. 식(Expression)과 연산자(Operator)

<br>

## 1. 주석(Comment)

주석은 코드 실행에 영향을 주지 않으며, 코드에 대한 설명을 작성할 때 사용한다.

### 1-1. 한 줄 주석

```kotlin
// 사용자 이름
val name = "Yulma"
```

### 1-2. 여러 줄 주석

```kotlin
/*
사용자 정보를 저장하는 변수
이름과 나이를 관리한다.
*/
val age = 20
```

### 1-3. 중첩 주석

```kotlin
/*
외부 주석

/*
내부 주석
*/

외부 주석 종료
*/
```

<br>

## 2. 변수 정의

프로그램에서 데이터를 저장하기 위해 사용하는 공간이다.

```kotlin
var name = "Yulma"
val age = 20
```

### 2-1. 변수 선언

```kotlin
var 변수명 = 값
val 변수명 = 값
```

### 2-2. 자료형 명시

```kotlin
var name: String = "Yulma"
val age: Int = 20
```

### 2-3. 타입 추론(Type Inference)

```kotlin
val name = "Yulma"
val age = 20
val height = 180.5
```

<br>

## 3. 식별자(Identifier)

식별자는 변수, 함수, 클래스 등을 구분하기 위해 사용하는 이름이다.

### 3-1. 식별자 작성 규칙

#### 영문자, 숫자, 밑줄(_) 사용 가능

```kotlin
val userName = ""
val user_name = ""
val user1 = ""
```

#### 숫자로 시작 불가

```kotlin
// 오류
val 1user = ""
```

#### 공백 사용 불가

```kotlin
// 오류
val user name = ""
```

#### 예약어 사용 불가

```kotlin
// 오류
val class = ""
```

#### Camel Case 사용 권장

```kotlin
val userName = ""
val userAge = 20
```

<br>

## 4. 가변 변수(var)와 불변 변수(val)

### 4-1. var

값 변경이 가능한 변수이다.

```kotlin
var age = 20

age = 21
```

### 4-2. val

값 변경이 불가능한 변수이다.

```kotlin
val age = 20

// 오류
age = 21
```

### 4-3. var와 val 비교

| 구분 | var | val |
|--------|--------|--------|
| 값 변경 | 가능 | 불가능 |
| 재할당 | 가능 | 불가능 |
| 권장 여부 | 필요 시 사용 | 기본 사용 권장 |

<br>

## 5. 식(Expression)과 연산자(Operator)

### 5-1. 식(Expression)

값을 만들어내는 코드를 의미한다.

```kotlin
10 + 20
```

```kotlin
val result = if (10 > 5) {
    "참"
} else {
    "거짓"
}
```

### 5-2. 산술 연산자

| 연산자 | 설명 |
|----------|----------|
| + | 더하기 |
| - | 빼기 |
| * | 곱하기 |
| / | 나누기 |
| % | 나머지 |

### 5-3. 비교 연산자

| 연산자 | 설명 |
|----------|----------|
| == | 같다 |
| != | 다르다 |
| > | 크다 |
| < | 작다 |
| >= | 크거나 같다 |
| <= | 작거나 같다 |

### 5-4. 논리 연산자

| 연산자 | 설명 |
|----------|----------|
| && | AND |
| \|\| | OR |
| ! | NOT |

### 5-5. 대입 연산자

```kotlin
var count = 10

count += 5
count -= 2
```

### 5-6. 증감 연산자

```kotlin
var count = 0

count++
count--
```

<br>

## 정리

- 주석은 코드 설명을 위해 사용한다.
- 변수는 `var` 또는 `val`로 선언한다.
- Kotlin은 타입 추론을 지원한다.
- 식별자는 변수, 함수, 클래스 등의 이름이다.
- `var`는 값 변경이 가능하다.
- `val`은 값 변경이 불가능하다.
- Kotlin은 대부분의 구문을 식(Expression)으로 처리한다.
- 연산자는 산술, 비교, 논리, 대입 연산 등으로 구분된다.
