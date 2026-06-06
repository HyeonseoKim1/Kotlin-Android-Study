# Sequence

Sequence는 컬렉션 연산을 지연(Lazy) 실행하기 위한 기능이다.

일반 List는 연산이 실행될 때마다 새로운 컬렉션을 생성하지만,

Sequence는 최종 결과가 필요할 때까지 연산을 미룬다.

<br>

## 목차

1. Sequence란?
2. List의 동작 방식
3. Sequence의 동작 방식
4. asSequence
5. toList
6. 실행 순서 비교
7. 언제 사용해야 할까?
8. 실무 예제
9. 자주 하는 실수

<br>

# 1. Sequence란?

컬렉션 함수는 편리하지만 연산이 많아질수록 중간 컬렉션이 계속 생성된다.

예를 들어

```kotlin
val result = numbers
    .filter { it > 5 }
    .map { it * 10 }
```

위 코드는

1. filter 결과 List 생성
2. map 결과 List 생성

과정을 거친다.

<br>

데이터가 적으면 문제가 없지만 데이터가 많아지면 불필요한 메모리 사용이 발생할 수 있다.

이때 Sequence를 사용한다.

<br>

# 2. List의 동작 방식

```kotlin
val result = listOf(
    1, 2, 3, 4, 5
)
    .filter {
        println("filter : $it")
        it > 2
    }
    .map {
        println("map : $it")
        it * 10
    }
```

실행 결과

```text
filter : 1
filter : 2
filter : 3
filter : 4
filter : 5

map : 3
map : 4
map : 5
```

<br>

List는

1. filter 전체 수행
2. 중간 List 생성
3. map 전체 수행

순서로 동작한다.

<br>

# 3. Sequence의 동작 방식

```kotlin
val result = listOf(
    1, 2, 3, 4, 5
)
    .asSequence()
    .filter {
        println("filter : $it")
        it > 2
    }
    .map {
        println("map : $it")
        it * 10
    }
    .toList()
```

실행 결과

```text
filter : 1
filter : 2
filter : 3
map : 3
filter : 4
map : 4
filter : 5
map : 5
```

<br>

List와 실행 순서가 다르다.

Sequence는 요소 하나를 처리한 후 다음 연산으로 넘긴다.

```text
3 → filter → map
4 → filter → map
5 → filter → map
```

<br>

중간 컬렉션을 만들지 않는다.

<br>

# 4. asSequence

List를 Sequence로 변환한다.

```kotlin
val sequence = listOf(
    1,
    2,
    3
).asSequence()
```

타입

```kotlin
Sequence<Int>
```

<br>

대부분 Sequence 시작은

```kotlin
.asSequence()
```

로 시작한다.

<br>

# 5. toList

Sequence는 최종 결과를 만들기 위해 컬렉션으로 변환해야 한다.

```kotlin
val result = listOf(
    1,
    2,
    3
)
    .asSequence()
    .map {
        it * 10
    }
    .toList()
```

결과

`[10, 20, 30]`

<br>

실무에서는 대부분

```kotlin
.asSequence()
...
.toList()
```

형태로 사용한다.

<br>

# 6. 실행 순서 비교

### List

```kotlin
numbers
    .filter { ... }
    .map { ... }
    .first()
```

실행 순서

```text
filter 전체
↓
map 전체
↓
first
```

<br>

### Sequence

```kotlin
numbers
    .asSequence()
    .filter { ... }
    .map { ... }
    .first()
```

실행 순서

```text
첫 번째 요소
↓
filter
↓
map
↓
first 검사
```

<br>

조건을 만족하면 즉시 종료될 수 있다.

<br>

# 7. 언제 사용해야 할까?

### 사용하면 좋은 경우

데이터가 많고 연산 체인이 길 때

```kotlin
list
    .asSequence()
    .filter { ... }
    .map { ... }
    .distinct()
    .sorted()
    .toList()
```

<br>

### 굳이 사용할 필요 없는 경우

```kotlin
list
    .map {
        it * 10
    }
```

연산이 하나뿐이면 차이가 거의 없다.

<br>

오히려 Sequence 생성 비용이 추가될 수 있다.

<br>

# 8. 실무 예제

### API 응답 가공

```kotlin
val result = users
    .asSequence()
    .filter {
        it.isActive
    }
    .map {
        it.name
    }
    .toList()
```

<br>

### 특정 데이터 찾기

```kotlin
val user = users
    .asSequence()
    .filter {
        it.isActive
    }
    .firstOrNull()
```

<br>

조건을 만족하는 사용자를 찾으면 즉시 종료한다.

<br>

### 로그 분석

```kotlin
logs
    .asSequence()
    .filter {
        it.level == ERROR
    }
    .map {
        it.message
    }
    .take(10)
    .toList()
```

<br>

대량 데이터 처리에서 유용하다.

<br>

# 9. 자주 하는 실수

### 1) Sequence가 항상 빠르다고 생각함

그렇지 않다.

```kotlin
list
    .map {
        it * 10
    }
```

처럼 간단한 경우는 오히려 List가 더 단순하다.

<br>

Sequence는

* 데이터가 많고
* 연산이 여러 개 연결될 때

효과가 크다.

<br>

### 2) toList()를 잊음

```kotlin
val result = listOf(
    1,
    2,
    3
)
    .asSequence()
    .map {
        it * 10
    }
```

타입

```kotlin
Sequence<Int>
```

<br>

List가 아니다.

필요하면

```kotlin
.toList()
```

를 호출해야 한다.

<br>

### 3) Sequence는 Lazy라는 사실을 잊음

```kotlin
val result = listOf(
    1,
    2,
    3
)
    .asSequence()
    .map {
        println(it)
        it * 10
    }
```

아직 아무것도 실행되지 않는다.

<br>

```kotlin
result.toList()
```

를 호출하는 순간 실행된다.

<br>

# 정리

List

* 즉시 실행(Eager)
* 중간 컬렉션 생성
* 데이터가 적을 때 적합

<br>

Sequence

* 지연 실행(Lazy)
* 중간 컬렉션 생성 최소화
* 대량 데이터 처리에 적합

<br>

실무에서는

```kotlin
.asSequence()
.filter { ... }
.map { ... }
.firstOrNull()
```

패턴을 자주 볼 수 있다.

Collection Function을 이해했다면 Sequence는 "성능을 위한 컬렉션 함수"라고 생각하면 된다.
