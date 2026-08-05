# Compose Compiler

1. Compose Compiler란?
2. 왜 Kotlin Compiler Plugin이 필요한가?
3. @Composable은 어떻게 컴파일되는가?
4. Composer 객체의 역할
5. Slot Table
6. Stability Analysis
7. Restartable Function
8. Restart Group
9. Skippable Group
10. Generated Code 살펴보기
11. Compiler Metrics 보는 방법
12. Compiler Reports
13. Compose Compiler 최적화
14. 실무에서 Compose Compiler를 이해해야 하는 이ㄷ유
15. 면접 예상 질문
16. 핵심 정리

## 1. Compose Compiler란?

Jetpack Compose는 **선언형(Declarative) UI 프레임워크**이다.

Compose에서는 XML을 사용하지 않고 `@Composable` 함수를 작성하여 UI를 구성한다.

```kotlin
@Composable
fun Greeting(name: String) {
    Text("Hello $name")
}
```

겉으로 보기에는 단순한 Kotlin 함수처럼 보이지만, 실제로는 일반 함수와 완전히 다른 방식으로 동작한다.

이러한 동작을 가능하게 하는 핵심이 바로 **Compose Compiler**이다.

Compose Compiler는 Kotlin Compiler Plugin으로 동작하며, `@Composable` 함수를 Compose Runtime이 이해할 수 있는 형태로 변환한다.

즉, 개발자가 작성한 코드를 그대로 실행하는 것이 아니라 컴파일 과정에서 코드를 변환하여 다음과 같은 기능을 추가한다.

- UI 상태(State) 추적
- Recomposition 관리
- Slot Table 생성
- Composition 관리
- Restart Group 생성
- Skippable Group 생성
- Stability Analysis 수행

만약 Compose Compiler가 없다면 `@Composable` 함수는 일반 Kotlin 함수일 뿐이며, Compose는 어떤 UI를 생성해야 하는지 알 수 없다.

---

### Compose Compiler의 역할

```
개발자가 작성한 코드

↓

Compose Compiler

↓

변환된 Kotlin 코드

↓

Compose Runtime 실행

↓

UI 생성
```

Compose Compiler는 Runtime과 개발자 코드 사이에서 **중간 다리 역할**을 수행한다.

---

## 2. 왜 Kotlin Compiler Plugin이 필요한가?

Compose는 단순한 라이브러리만으로 구현할 수 없다.

예를 들어 다음 코드를 살펴보자.

```kotlin
@Composable
fun Greeting() {
    Text("Hello")
}
```

겉으로 보기에는 일반 함수와 거의 동일하다.

하지만 Compose는 다음과 같은 정보를 알아야 한다.

- 현재 Composition 중인지
- 이전 Composition과 비교해야 하는지
- 어떤 State를 읽었는지
- 어떤 Composable이 다시 실행되어야 하는지
- 어떤 Composable을 Skip할 수 있는지

이러한 정보는 개발자가 직접 작성하지 않는다.

대신 Compose Compiler가 컴파일 과정에서 필요한 코드를 자동으로 삽입한다.

즉,

```
작성한 코드

↓

Compose Compiler

↓

숨겨진 코드 자동 생성
```

이라는 과정이 수행된다.

---

### 왜 라이브러리만으로는 불가능할까?

일반 Kotlin 라이브러리는 함수 내부를 수정할 수 없다.

예를 들어

```kotlin
fun hello() {
    println("Hello")
}
```

라이브러리는 이 함수의 내부를 변경할 수 없다.

반면 Compiler Plugin은

```
소스 코드

↓

컴파일

↓

새로운 코드 생성
```

과정을 수행할 수 있다.

Compose는 이 기능을 이용하여 모든 `@Composable` 함수를 다시 작성한다.

---

### Compose Compiler가 수행하는 작업

컴파일 과정에서 다음과 같은 작업이 자동으로 수행된다.

- Composer 파라미터 추가
- Restart Group 생성
- Slot Table 관리 코드 생성
- State 추적 코드 생성
- Stability Analysis 수행
- Recomposition 코드 생성

개발자는 이러한 코드를 직접 작성하지 않아도 된다.

---

## 3. @Composable은 어떻게 컴파일되는가?

Compose Compiler의 가장 중요한 역할은 `@Composable` 함수를 변환하는 것이다.

예를 들어 다음과 같은 코드가 있다고 가정하자.

```kotlin
@Composable
fun Greeting(
    name: String
) {
    Text("Hello $name")
}
```

개발자는 단순한 함수처럼 작성하지만, 실제 컴파일 후에는 훨씬 복잡한 형태로 변경된다.

개념적으로는 다음과 비슷한 코드가 생성된다.

```kotlin
fun Greeting(
    name: String,
    composer: Composer,
    changed: Int
) {
    ...
}
```

실제 Generated Code는 훨씬 복잡하지만 핵심은 다음 세 가지가 추가된다는 것이다.

- Composer
- 변경 여부(changed)
- Default 값 처리 정보

---

### Composer

Composer는 현재 Composition 상태를 관리한다.

```
Greeting()

↓

Composer 전달

↓

Composition 관리
```

모든 Composable은 동일한 Composer를 공유하며 UI 트리를 구성한다.

---

### changed 파라미터

Compose는 매번 모든 UI를 다시 계산하지 않는다.

대신

```
이 값이 변경되었는가?
```

를 확인한다.

이를 위해 Compiler는 변경 여부를 나타내는 비트 플래그를 추가한다.

개념적으로는 다음과 같다.

```kotlin
changed = 0010
```

각 비트는 특정 파라미터가 변경되었는지를 의미한다.

```
name 변경

↓

changed 갱신

↓

Greeting 다시 실행 여부 결정
```

이러한 방식 덕분에 Compose는 매우 빠르게 변경 여부를 판단할 수 있다.

---

### Generated Code가 필요한 이유

만약 Compiler가 코드를 변환하지 않는다면

```kotlin
Greeting("Compose")
```

는 일반 함수 호출과 다를 것이 없다.

Compose는

- Composition 생성
- Recomposition
- State 추적

을 수행할 수 없다.

즉, Compose Compiler가 있어야만 선언형 UI가 동작한다.

---

## 4. Composer 객체의 역할

Composer는 Compose Runtime에서 가장 중요한 객체이다.

쉽게 말하면

> **현재 UI의 상태를 관리하는 관리자**

라고 생각하면 된다.

모든 Composable은 결국 Composer를 통해 실행된다.

```
Composable

↓

Composer

↓

UI 생성
```

---

### Composer가 수행하는 역할

Composer는 다음과 같은 정보를 관리한다.

- 현재 Composition 위치
- Slot Table 접근
- State 변경 여부
- Recomposition 대상
- Restart Group 관리
- Skipping 여부

즉, Compose Runtime의 대부분의 기능은 Composer를 중심으로 동작한다.

---

### Composition 트리 생성

다음과 같은 코드가 있다고 가정하자.

```kotlin
Column {
    Text("A")
    Text("B")
}
```

Composer는 내부적으로 트리 구조를 생성한다.

```
Column
 ├── Text("A")
 └── Text("B")
```

그리고 각 노드의 위치를 기억한다.

이 정보는 이후 Recomposition에서 사용된다.

---

### State 추적

예를 들어

```kotlin
Text("$count")
```

를 실행하면

Composer는

```
count를 읽음
```

이라는 사실을 저장한다.

이후

```
count 변경

↓

Composer 확인

↓

Text만 다시 실행
```

이라는 과정이 수행된다.

이것이 Compose가 필요한 UI만 다시 그릴 수 있는 이유이다.

---

### Recomposition 관리

Composer는 이전 Composition과 현재 Composition을 비교한다.

```
이전 UI

↓

현재 UI

↓

변경 여부 비교

↓

필요한 부분만 갱신
```

이 과정을 통해 불필요한 UI 업데이트를 방지한다.

---

## 5. Slot Table

Slot Table은 Compose Runtime이 UI의 상태를 저장하는 자료구조이다.

Compose에서는 View 객체를 계속 수정하는 대신, Slot Table에 Composition 정보를 저장한다.

쉽게 말하면

> **UI를 기억하는 메모리 공간**

이라고 이해하면 된다.

---

### 왜 Slot Table이 필요한가?

다음 코드를 살펴보자.

```kotlin
Column {
    Text("Hello")
    Button(
        onClick = { }
    ) {
        Text("Click")
    }
}
```

Compose는 Recomposition이 발생했을 때

```
현재 UI

↓

이전 UI
```

를 비교해야 한다.

이전 정보를 저장하지 않으면 비교가 불가능하다.

따라서 Slot Table에 Composition 정보를 저장한다.

---

### Slot Table이 저장하는 정보

Slot Table에는 다양한 정보가 저장된다.

- Composable 위치
- Composition 순서
- remember 값
- State 정보
- Group 정보
- Node 정보

예를 들어

```
Column

↓

Text

↓

Button

↓

Text
```

와 같은 구조를 순서대로 저장한다.

---

### remember와 Slot Table

다음 코드를 보자.

```kotlin
val count = remember {
    mutableIntStateOf(0)
}
```

`remember`는 값을 Slot Table에 저장한다.

```
remember

↓

Slot Table 저장

↓

Recomposition

↓

기존 값 재사용
```

따라서 Recomposition이 발생해도 새로운 객체를 생성하지 않는다.

---

### Slot Table의 중요성

Slot Table이 없다면 Compose는 매번 UI를 처음부터 생성해야 한다.

하지만 Slot Table 덕분에

- 이전 Composition 기억
- remember 값 유지
- Recomposition 최적화
- State 유지

가 가능하다.

즉, Slot Table은 Compose Runtime의 핵심 자료구조이며, Compose Compiler가 생성하는 코드도 결국 Slot Table을 효율적으로 활용할 수 있도록 구성된다.


## 6. Stability Analysis

Compose Compiler는 모든 Composable을 컴파일하기 전에 **Stability Analysis**를 수행한다.

Stability Analysis란

> **"이 객체는 변경 여부를 신뢰할 수 있는가?"**

를 분석하는 과정이다.

이 분석 결과는 이후

- Restartable Function 생성
- Skippable Group 생성
- Recomposition 여부

를 결정하는 핵심 기준이 된다.

즉,

```
Composable

↓

Stability Analysis

↓

Restartable 생성

↓

Skippable 생성
```

순서로 동작한다.

---

### 왜 Stability를 분석할까?

Compose는 가능한 한 Recomposition을 줄이려고 한다.

예를 들어

```kotlin
@Composable
fun Profile(user: User) {
    Text(user.name)
}
```

만약 `user`가 이전과 동일하다면

```
Profile()

↓

Skip
```

하는 것이 성능상 훨씬 유리하다.

하지만 Compiler는 먼저

```
User가 믿을 수 있는 객체인가?
```

를 알아야 한다.

그래서 Stability Analysis를 수행한다.

---

### Stable

```kotlin
@Immutable
data class User(
    val name: String,
    val age: Int
)
```

이러한 객체는

- 변경 여부를 알 수 있고
- 값 비교가 가능하며
- 예측 가능한 동작을 한다.

따라서

```
Stable

↓

Skipping 가능
```

으로 판단한다.

---

### Unstable

```kotlin
class User(
    var name: String
)
```

이 객체는

언제 값이 변경되는지 Compiler가 알 수 없다.

따라서

```
Unstable

↓

Skip하지 않음
```

으로 판단한다.

---

### Stability Analysis 결과

Compiler는 내부적으로

```
Stable

Immutable

Unstable
```

중 하나로 분류한다.

이 결과는 Generated Code에도 반영된다.

---

## 7. Restartable Function

Restartable Function은

> **필요할 때 다시 실행할 수 있는 Composable**

이다.

Compose의 대부분의 Composable은 Restartable이다.

예를 들어

```kotlin
@Composable
fun Counter(
    count: Int
) {
    Text("$count")
}
```

count가 변경되면

```
Counter()

↓

다시 실행
```

된다.

이러한 함수를 Restartable Function이라고 한다.

---

### 왜 Restartable이 필요한가?

Compose는 화면 전체를 다시 그리지 않는다.

대신

```
Counter만

↓

Restart
```

한다.

즉,

```
Activity

↓

Column

↓

Counter

↓

Text
```

구조에서 Counter만 다시 실행된다.

이것이 Compose가 빠른 이유 중 하나이다.

---

### Compiler는 어떻게 처리할까?

Compiler는 내부적으로

```
Restart Group
```

을 생성하여

필요한 부분만 다시 실행할 수 있도록 만든다.

즉

Restartable Function과 Restart Group은 함께 동작한다.

---

## 8. Restart Group

Restart Group은

> **Recomposition의 최소 단위**

이다.

Compiler는 Composable마다 Restart Group을 생성한다.

예를 들어

```kotlin
Column {

    Title()

    Profile()

    Footer()

}
```

개념적으로는

```
Restart Group

↓

Title

Restart Group

↓

Profile

Restart Group

↓

Footer
```

와 같이 그룹을 생성한다.

---

### 왜 Group이 필요한가?

예를 들어

```kotlin
Profile(user)
```

에서 user만 변경되었다.

그러면

```
Title

↓

Skip

Profile

↓

Restart

Footer

↓

Skip
```

가 가능하다.

Group이 없다면

Column 전체를 다시 실행해야 한다.

---

### Restart Group의 역할

Restart Group은

- 시작 위치 저장
- 종료 위치 저장
- Slot Table 위치 저장
- Recomposition 범위 저장

을 수행한다.

Composer는 이 정보를 이용하여

필요한 Group만 다시 실행한다.

---

## 9. Skippable Group

Restart Group이

"다시 실행 가능한 영역"

이라면

Skippable Group은

"실행하지 않아도 되는 영역"

이다.

예를 들어

```kotlin
Profile(user)
```

에서

user가 이전과 동일하다면

```
Profile

↓

Skip
```

한다.

---

### Skipping 조건

Skipping은 아무 때나 발생하지 않는다.

대표적인 조건은

- Stable 객체
- 동일한 값
- 변경되지 않은 State

이다.

예를 들어

```kotlin
@Immutable
data class User(
    val name: String
)
```

라면

```kotlin
Profile(user)
```

는 대부분 Skip된다.

---

### Restart와 Skip의 차이

Restart

```
다시 실행
```

Skip

```
실행 자체를 생략
```

이다.

예를 들어

```
Restart

↓

Composable 실행

↓

UI 계산
```

반면

```
Skip

↓

Composable 호출 안 함
```

이다.

Skip은 Restart보다 훨씬 비용이 적다.

---

### Strong Skipping과의 관계

Strong Skipping은

기존 Skipping을 더욱 적극적으로 수행하도록 개선한 기능이다.

즉,

```
Compose Compiler

↓

Skippable Group

↓

Strong Skipping
```

이라는 관계이다.

Strong Skipping은 다음 문서에서 자세히 다룬다.

---

## 10. Generated Code 살펴보기

Compose Compiler는 우리가 작성한 코드를 그대로 실행하지 않는다.

예를 들어

```kotlin
@Composable
fun Greeting(
    name: String
) {
    Text(name)
}
```

는 개념적으로 다음과 같이 변환된다.

```kotlin
fun Greeting(
    name: String,
    composer: Composer,
    changed: Int
) {

    composer.startRestartGroup(...)

    if (changed ...) {
        Text(name)
    }

    composer.endRestartGroup()
}
```

실제 Generated Code는 훨씬 복잡하지만 핵심 구조는 비슷하다.

---

### startRestartGroup()

```kotlin
composer.startRestartGroup(...)
```

Restart Group의 시작을 의미한다.

Composer는

```
현재 위치

↓

Group 시작
```

을 기록한다.

---

### changed 비트 확인

```kotlin
if (...)
```

부분에서는

```
파라미터 변경 여부
```

를 확인한다.

변경되지 않았다면

```
Skip
```

을 수행할 수 있다.

---

### endRestartGroup()

```kotlin
composer.endRestartGroup()
```

은

Restart Group의 종료를 의미한다.

Compiler는

```
시작

↓

Composable 실행

↓

종료
```

구조를 자동으로 생성한다.

---

### Generated Code를 보는 이유

Generated Code를 이해하면

- 왜 Recomposition이 발생하는가
- 왜 Skip이 가능한가
- Composer가 무엇을 하는가
- Strong Skipping이 어떻게 동작하는가

를 훨씬 쉽게 이해할 수 있다.

실무에서 Compose 성능을 분석하는 개발자라면 Generated Code의 구조를 한 번쯤은 살펴보는 것이 큰 도움이 된다.

---
## 11. Compiler Metrics 보는 방법

Compose Compiler는 컴파일 과정에서 다양한 최적화를 수행한다.

하지만 실제로

- 어떤 Composable이 Stable인지
- 어떤 Composable이 Skip 가능한지
- 어떤 객체가 Unstable인지

를 눈으로 확인하기는 어렵다.

이를 위해 Compose Compiler는 **Compiler Metrics** 기능을 제공한다.

Compiler Metrics를 활성화하면 프로젝트의 Compose 코드가 어떻게 분석되었는지 확인할 수 있다.

---

### Compiler Metrics에서 확인할 수 있는 정보

대표적으로 다음과 같은 정보를 확인할 수 있다.

- Restartable Composable 수
- Skippable Composable 수
- Stable 클래스
- Unstable 클래스
- Immutable 클래스
- Static Composable 수
- Lambda 분석 결과

즉,

```
Compose Compiler

↓

Stability Analysis

↓

Metrics 생성
```

과정을 통해 내부 분석 결과를 파일로 확인할 수 있다.

---

### Metrics 활성화하기

Compose Compiler의 Metrics는 Gradle 설정을 통해 활성화할 수 있다.

```kotlin
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_reports")
    metricsDestination = layout.buildDirectory.dir("compose_metrics")
}
```

빌드가 완료되면

```
build/

 ├── compose_metrics
 └── compose_reports
```

폴더가 생성된다.

---

### Metrics 파일 예시

예를 들어 Metrics에는 다음과 같은 내용이 포함된다.

```
restartableComposables: 42

skippableComposables: 35

readonlyComposables: 8

totalComposables: 50
```

이를 통해

- 얼마나 많은 Composable이 Restart 가능한지
- 얼마나 많은 Composable이 Skip 가능한지

를 확인할 수 있다.

---

### Metrics를 언제 사용할까?

실무에서는

- Recomposition이 너무 많이 발생할 때
- 성능 최적화를 수행할 때
- Stable 객체가 제대로 인식되지 않을 때

주로 확인한다.

---

## 12. Compiler Reports

Metrics가 숫자 중심이라면

Compiler Reports는

**왜 그런 결과가 나왔는지**를 설명해 주는 보고서이다.

Report에는 각 Composable의 분석 결과가 자세하게 기록된다.

---

### Report에서 확인할 수 있는 내용

예를 들어

```kotlin
@Composable
fun Profile(
    user: User
)
```

에 대해

다음과 같은 정보를 확인할 수 있다.

- Restartable 여부
- Skippable 여부
- Stable 여부
- Parameter Stability
- Lambda Stability

즉,

```
Composable

↓

Compiler Analysis

↓

Report 출력
```

과정을 거친다.

---

### Report 예시

개념적으로는 다음과 같은 형태이다.

```
restartable scheme()

skippable

fun Profile(
    stable user: User
)
```

위 결과는

- Restart 가능
- Skip 가능
- user는 Stable

이라는 의미이다.

반대로

```
unstable user
```

라고 표시된다면

Compiler는 해당 객체를 신뢰하지 못한다는 뜻이다.

---

### Report를 활용하는 방법

Report는 다음과 같은 상황에서 매우 유용하다.

- Stable 객체인데 Skip되지 않는 이유 확인
- Unstable 객체 탐색
- 불필요한 Recomposition 원인 분석
- Compose 성능 개선

실무에서는 Metrics보다 Report를 더 자주 확인하는 경우도 많다.

---

## 13. Compose Compiler 최적화

Compose Compiler는 자동으로 많은 최적화를 수행하지만, 개발자의 코드 작성 방식에 따라 성능이 크게 달라질 수 있다.

---

### 1) Immutable 객체 사용

가장 좋은 방법은 UI State를 Immutable하게 만드는 것이다.

```kotlin
@Immutable
data class UiState(
    val name: String,
    val age: Int
)
```

Immutable 객체는 Compiler가 적극적으로 Skip할 수 있다.

---

### 2) State를 작은 단위로 분리하기

좋지 않은 예

```kotlin
UiState(
    name,
    age,
    address,
    phone,
    image,
    ...
)
```

작은 변경에도 전체 객체가 변경된 것으로 인식될 수 있다.

필요에 따라 상태를 적절히 분리하면 Recomposition 범위를 줄일 수 있다.

---

### 3) 불필요한 객체 생성 줄이기

좋지 않은 예

```kotlin
Text(
    text = user.name,
    modifier = Modifier.padding(8.dp)
)
```

`Modifier.padding()` 자체는 문제가 아니지만,

매 Recomposition마다 새로운 객체를 생성하거나 복잡한 계산을 수행하면 비용이 증가할 수 있다.

필요한 경우 `remember`를 활용하여 객체 생성을 최소화한다.

---

### 4) Stable Collection 사용 고려

일반 `List`나 `MutableList`는 상황에 따라 Stability 판단이 어려울 수 있다.

Compose에서는 Immutable Collection을 사용하는 것이 성능상 유리한 경우가 많다.

예를 들어 Kotlinx Immutable Collections를 사용할 수 있다.

---

### 5) Strong Skipping 활용

최신 Compose에서는 Strong Skipping을 통해

기존보다 더 많은 Composable이 Skip된다.

Strong Skipping은 다음 문서에서 자세히 다룬다.

---

## 14. 실무에서 Compose Compiler를 이해해야 하는 이유

Compose Compiler는 평소에는 보이지 않는다.

하지만 Compose의 거의 모든 성능 최적화는 Compiler에서 시작된다.

즉,

```
Compiler

↓

Generated Code

↓

Runtime

↓

Recomposition

↓

UI 성능
```

이라는 흐름으로 이어진다.

---

### 실무에서 도움이 되는 이유

#### Recomposition 원인을 빠르게 찾을 수 있다.

단순히

"왜 다시 그려질까?"

가 아니라

- Stable이 아닌가?
- Restart Group 때문인가?
- Parameter가 변경되었는가?

를 분석할 수 있다.

---

#### Stability Report를 해석할 수 있다.

Compiler Report를 보면

```
stable

unstable

skippable
```

등이 표시된다.

이를 통해 성능 병목을 쉽게 찾을 수 있다.

---

#### Strong Skipping을 이해하기 쉬워진다.

Strong Skipping은 Compose Compiler가 수행하는 최적화 기능이다.

Compiler를 이해하면 Strong Skipping도 자연스럽게 이해할 수 있다.

---

#### Compose 성능을 체계적으로 개선할 수 있다.

단순히 `remember`를 추가하는 것이 아니라

Compiler가 어떤 코드를 생성하는지 이해하고 적절한 구조를 설계할 수 있다.

---

## 15. 면접 예상 질문

### Q1. Compose Compiler의 역할은 무엇인가?

> `@Composable` 함수를 Compose Runtime이 실행할 수 있는 형태로 변환하고, Recomposition과 Skipping을 위한 코드를 자동으로 생성하는 Kotlin Compiler Plugin이다.

---

### Q2. 왜 Compose는 Kotlin Compiler Plugin이 필요한가?

> 일반 라이브러리만으로는 함수의 내부를 수정하거나 숨겨진 파라미터를 추가할 수 없기 때문이다. Compose는 컴파일 시점에 `Composer`와 Recomposition 관련 코드를 삽입해야 하므로 Compiler Plugin이 필요하다.

---

### Q3. Composer는 어떤 역할을 하는가?

> Composition 상태를 관리하고, Slot Table 접근, State 추적, Recomposition 및 Skipping을 제어하는 핵심 객체이다.

---

### Q4. Slot Table은 무엇인가?

> Composition 구조와 `remember` 값 등을 저장하는 Runtime의 핵심 자료구조이다.

---

### Q5. Restart Group과 Skippable Group의 차이는 무엇인가?

> Restart Group은 다시 실행할 수 있는 영역이고, Skippable Group은 변경이 없을 경우 실행 자체를 생략할 수 있는 영역이다.

---

### Q6. Stability Analysis는 왜 필요한가?

> 객체가 Stable인지 Unstable인지 분석하여 Recomposition과 Skipping 여부를 결정하기 위해 필요하다.

---

### Q7. Compiler Metrics와 Compiler Reports의 차이는 무엇인가?

> Metrics는 수치 기반의 통계 정보를 제공하고, Reports는 각 Composable의 Stability와 Restart/Skip 여부를 상세하게 분석한 결과를 제공한다.

---

## 16. 핵심 정리

- Compose Compiler는 `@Composable` 함수를 Compose Runtime이 이해할 수 있는 형태로 변환하는 Kotlin Compiler Plugin이다.
- 컴파일 과정에서 `Composer`, `changed` 파라미터, Restart Group, Skippable Group 등을 자동으로 생성한다.
- Stability Analysis를 통해 객체를 Stable, Immutable, Unstable로 분류하고 Recomposition 전략을 결정한다.
- Compiler Metrics와 Compiler Reports를 활용하면 Compose의 내부 분석 결과를 확인하고 성능 병목을 찾을 수 있다.
- Compose Compiler를 이해하면 Recomposition, Strong Skipping, Stability 최적화까지 하나의 흐름으로 이해할 수 있으며, 실무에서도 성능 문제를 분석하고 개선하는 데 큰 도움이 된다.
