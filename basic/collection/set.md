# Set

## 목차

1. Set이란?
2. Set 생성
3. 중복 제거
4. Set과 MutableSet
5. MutableSet
6. Set 순회
7. 주요 함수
8. Set 사용 예제
9. List와 Set 비교
10. 정리

<br>

## 1. Set이란?

Set은 중복을 허용하지 않는 컬렉션이다.

특징

* 같은 값을 하나만 저장한다.
* 중복 데이터가 자동으로 제거된다.
* 데이터 존재 여부를 확인할 때 자주 사용된다.

```kotlin
val fruits = setOf(
    "Apple",
    "Banana",
    "Orange"
)
```

<br>

## 2. Set 생성

### 읽기 전용 Set

```kotlin
val numbers = setOf(1, 2, 3)
```

### 빈 Set 생성

```kotlin
val emptySet = emptySet<Int>()
```

### 타입 추론

코틀린은 대부분 타입을 자동으로 추론한다.

```kotlin
val fruits = setOf(
    "Apple",
    "Banana"
)
```

위 코드는 다음과 같다.

```kotlin
val fruits: Set<String> = setOf(
    "Apple",
    "Banana"
)
```

<br>

## 3. 중복 제거

Set은 동일한 값을 여러 번 저장할 수 없다.

```kotlin
val numbers = setOf(
    1,
    2,
    2,
    3,
    3,
    3
)

println(numbers)
```

결과

```text
[1, 2, 3]
```

중복 값은 자동으로 제거된다.

오류가 발생하는 것은 아니다.

<br>

## 4. Set과 MutableSet

Set은 읽기 전용 컬렉션이다.

```kotlin
val fruits = setOf(
    "Apple",
    "Banana"
)
```

아래 코드는 사용할 수 없다.

```kotlin
fruits.add("Orange") // 오류
fruits.remove("Apple") // 오류
```

### MutableSet

MutableSet은 요소 추가와 삭제가 가능하다.

```kotlin
val fruits = mutableSetOf(
    "Apple",
    "Banana"
)

fruits.add("Orange")
```

### val과 MutableSet

많이 헷갈리는 부분이다.

```kotlin
val fruits = mutableSetOf(
    "Apple",
    "Banana"
)

fruits.add("Orange")
```

위 코드는 정상 동작한다.

이유는 val이 변수 재할당만 막기 때문이다.

```text
val
→ 변수 재할당 불가

MutableSet
→ 내부 요소 변경 가능
```

따라서 아래 코드는 오류가 발생한다.

```kotlin
val fruits = mutableSetOf(
    "Apple",
    "Banana"
)

fruits = mutableSetOf("Orange")
```

### Set과 MutableSet 비교

| 구분    | Set | MutableSet |
| ----- | --- | ---------- |
| 요소 조회 | O   | O          |
| 요소 추가 | X   | O          |
| 요소 삭제 | X   | O          |
| 중복 허용 | X   | X          |

<br>

## 5. MutableSet

### 요소 추가

```kotlin
val numbers = mutableSetOf(1, 2, 3)

numbers.add(4)

println(numbers)
```

결과

```text
[1, 2, 3, 4]
```

### 중복 추가

```kotlin
numbers.add(4)

println(numbers)
```

결과

```text
[1, 2, 3, 4]
```

이미 존재하는 값은 추가되지 않는다.

### 요소 삭제

```kotlin
numbers.remove(2)

println(numbers)
```

결과

```text
[1, 3, 4]
```

<br>

## 6. Set 순회

```kotlin
val fruits = setOf(
    "Apple",
    "Banana",
    "Orange"
)

for (fruit in fruits) {
    println(fruit)
}
```

<br>

## 7. 주요 함수

### contains()

```kotlin
println(fruits.contains("Apple"))
```

결과

```text
true
```

### size

```kotlin
println(fruits.size)
```

결과

```text
3
```

### isEmpty()

```kotlin
println(fruits.isEmpty())
```

결과

```text
false
```

<br>

## 8. Set 사용 예제

중복 사용자 제거

```kotlin
val users = listOf(
    "Kim",
    "Lee",
    "Kim",
    "Park"
)

val uniqueUsers = users.toSet()

println(uniqueUsers)
```

결과

```text
[Kim, Lee, Park]
```

<br>

## 9. List와 Set 비교

| 구분     | List   | Set   |
| ------ | ------ | ----- |
| 순서 유지  | O      | O     |
| 중복 허용  | O      | X     |
| 인덱스 접근 | O      | X     |
| 주요 용도  | 데이터 목록 | 중복 제거 |

### 헷갈리기 쉬운 부분

파이썬을 사용해봤다면 Set은 순서가 없다고 알고 있을 수 있다.

하지만 코틀린의 기본 Set 구현은 삽입 순서를 유지하는 경우가 많다.

```kotlin
val numbers = setOf(
    3,
    1,
    2
)

println(numbers)
```

결과

```text
[3, 1, 2]
```

다만 Set의 핵심 특징은 순서가 아니라 중복을 허용하지 않는다는 점이다.

따라서 순서에 의존하는 코드는 작성하지 않는 것이 좋다.

<br>

## 10. 정리

* Set은 중복을 허용하지 않는다.
* Set은 읽기 전용 컬렉션이다.
* MutableSet은 요소 추가와 삭제가 가능하다.
* 중복된 값을 추가해도 오류가 발생하지 않는다.
* Set의 핵심 목적은 중복 제거와 빠른 조회이다.
* val은 재할당을 막을 뿐 내부 요소 변경까지 막지는 않는다.
