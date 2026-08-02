# LookaheadLayout

## 목차

1. LookaheadLayout이란?
2. 왜 등장했는가?
3. 일반 Layout과의 차이
4. 내부 동작 원리
5. Lookahead Pass와 Measure Pass
6. LookaheadScope란?
7. Modifier.animateBounds()와의 관계
8. SharedTransition과의 관계
9. 내부 구현 구조 분석
10. 실전 예제 1 - 크기 변경 애니메이션
11. 실전 예제 2 - 위치 변경 애니메이션
12. LookaheadLayout을 사용하면 안 되는 경우
13. 성능과 주의사항
14. 자주 하는 실수
15. 주요 질문
16. 정리

<br>

## 1. LookaheadLayout이란?

Jetpack Compose에서 레이아웃은 **현재 상태(Current State)** 를 기준으로 크기와 위치를 계산한다.

예를 들어 Box의 크기가 변경되면 Compose는 새로운 상태를 기준으로 다시 Measure와 Layout을 수행한다.

```kotlin
Box(
    modifier = Modifier.size(
        if (expanded) 200.dp else 100.dp
    )
)
```

`expanded` 값이 변경되면 Compose는 Box의 크기를 다시 측정하고 새로운 위치를 계산한다.

일반적인 화면에서는 문제가 없지만, 여러 컴포저블이 함께 움직이는 애니메이션에서는 부자연스러운 화면이 만들어질 수 있다.

예를 들어 다음과 같은 화면을 생각해 보자.

```text
A
B
C

↓

B의 크기 변경

↓

A
BBBBB
C
```

B가 커지면 C도 아래로 이동해야 한다.

하지만 기존 Compose는 **크기가 변경된 이후** 새로운 레이아웃을 계산하기 때문에 다른 컴포저블이 갑자기 이동하는 것처럼 보일 수 있다.

이러한 현상을 흔히 **Layout Jump**라고 한다.

LookaheadLayout은 이러한 문제를 해결하기 위해 추가된 레이아웃이다.

핵심 아이디어는 매우 단순하다.

> 최종 레이아웃을 먼저 계산한 뒤 현재 위치에서 목표 위치까지 자연스럽게 이동한다.

```text
현재 Layout
↓

미래 Layout 계산

↓

애니메이션

↓

최종 Layout
```

즉, 현재 상태만 계산하는 것이 아니라 **미래 레이아웃까지 미리 계산**하여 자연스러운 화면 전환을 만든다.

<br>

## 2. 왜 등장했는가?

Compose는 선언형 UI 프레임워크이다.

State가 변경되면 화면은 다음 순서로 다시 그려진다.

```text
State 변경
↓

Recomposition
↓

Measure
↓

Layout
↓

Draw
```

이 방식은 대부분의 화면에서는 충분히 효율적이다.

하지만 화면 전체가 재배치되는 애니메이션에서는 한계가 있다.

예를 들어 카드를 확장하는 화면을 생각해 보자.

```text
작은 카드

↓

큰 카드
```

카드가 커지면 아래에 있는 컴포저블도 새로운 위치로 이동해야 한다.

하지만 기존 Layout 시스템은 현재 상태만 알고 있기 때문에 **최종 위치를 미리 알 수 없다.**

따라서

```text
State 변경
↓

새로운 Layout 계산
↓

애니메이션 시작
```

순서로 동작한다.

이 때문에 위치가 순간적으로 변경되거나 애니메이션이 끊기는 것처럼 보일 수 있다.

LookaheadLayout은 먼저 미래 레이아웃을 계산한 뒤 애니메이션을 시작한다.

```text
State 변경
↓

미래 Layout 계산
↓

현재 위치와 비교
↓

애니메이션
```

현재 위치와 목표 위치를 모두 알고 있기 때문에 중간 과정을 자연스럽게 만들어 낼 수 있다.

특히 다음과 같은 UI에서 효과를 볼 수 있다.

- Shared Element Transition
- 카드 확장 애니메이션
- Grid ↔ List 전환
- Drag & Drop
- 화면 재배치 애니메이션

최근 Compose에서 제공하는 `SharedTransitionLayout`도 이러한 개념을 기반으로 동작한다.

<br>

## 3. 일반 Layout과의 차이

기존 Layout과 LookaheadLayout의 가장 큰 차이는 **미래 레이아웃을 계산하는지 여부**이다.

기존 Layout은 현재 상태만 기준으로 화면을 배치한다.

```text
State 변경
↓

Measure

↓

Layout

↓

Draw
```

예를 들어 버튼의 크기가 변경되면 새로운 크기로 다시 Measure를 수행하고 바로 화면을 그린다.

반면 LookaheadLayout은 현재 Layout을 수행하기 전에 **미래 Layout을 한 번 더 계산**한다.

```text
State 변경
↓

Lookahead Measure

↓

Lookahead Layout

↓

Measure

↓

Layout

↓

Draw
```

처음 보면 Measure를 두 번 수행하기 때문에 비효율적으로 느껴질 수 있다.

하지만 첫 번째 Measure는 화면을 그리기 위한 과정이 아니라 **최종 위치를 미리 계산하기 위한 과정**이다.

즉,

```text
현재 위치

↓

목표 위치 계산

↓

현재 → 목표까지 애니메이션
```

을 가능하게 만들기 위한 준비 과정이라고 이해하면 된다.

이 덕분에 현재 위치와 최종 위치를 모두 알고 있으므로 부드러운 애니메이션을 만들 수 있다.

<br>

## 4. 내부 동작 원리

LookaheadLayout은 기존 Layout 시스템을 대체하는 기능이 아니다.

기존 Layout 시스템 앞에 **미래 레이아웃을 계산하는 단계**가 하나 추가된 구조이다.

기존 Compose의 흐름은 다음과 같다.

```text
Measure

↓

Layout

↓

Draw
```

LookaheadLayout에서는 다음과 같이 동작한다.

```text
Lookahead Measure

↓

Lookahead Layout

↓

Measure

↓

Layout

↓

Draw
```

여기서 중요한 점은 **첫 번째 Measure는 화면을 그리지 않는다**는 것이다.

첫 번째 Measure에서는

- 최종 크기
- 최종 위치

만 계산한다.

그 이후 실제 화면을 그릴 때 현재 Layout을 수행하면서 두 결과를 비교한다.

예를 들어 현재 위치가

```text
x = 100
```

이고 최종 위치가

```text
x = 400
```

이라면 Compose는 두 위치를 모두 알고 있다.

그래서

```text
100

↓

180

↓

260

↓

330

↓

400
```

처럼 자연스럽게 이동할 수 있다.

즉, LookaheadLayout의 핵심은 **현재 Layout과 미래 Layout을 동시에 관리하는 것**이다.

<br>

## 5. Lookahead Pass와 Measure Pass

LookaheadLayout에는 두 종류의 Measure 과정이 존재한다.

### Lookahead Pass

첫 번째는 **Lookahead Pass**이다.

이 단계에서는 화면을 그리지 않는다.

대신 최종 레이아웃만 계산한다.

예를 들어 현재 크기가 100dp이고 상태 변경 후 200dp가 된다면 Lookahead Pass에서는 **200dp**만 계산한다.

```text
현재

100dp

↓

Lookahead Pass

↓

200dp 계산
```

### Measure Pass

다음은 일반적인 Measure Pass이다.

이 단계에서는 현재 화면을 실제로 그린다.

```text
현재

100dp

↓

Measure

↓

Layout

↓

Draw
```

하지만 이미 Lookahead Pass에서 목표 크기인 200dp를 계산해 두었기 때문에 Compose는 현재 상태와 미래 상태를 모두 알고 있다.

```text
현재 크기 : 100dp

목표 크기 : 200dp
```

이 정보를 이용해 위치와 크기를 자연스럽게 보간(Interpolation)한다.

따라서 개발자는 복잡한 애니메이션을 직접 계산하지 않아도 된다.

LookaheadLayout이 미래 레이아웃을 계산하고, Compose가 두 상태 사이를 자연스럽게 연결해 주기 때문이다.


<br>

## 6. LookaheadScope란?

LookaheadLayout을 사용하다 보면 `LookaheadScope`를 함께 사용하는 코드를 자주 볼 수 있다.

처음에는 단순히 LookaheadLayout 내부에서 사용하는 Scope처럼 보이지만, 실제로는 **현재 레이아웃과 미래 레이아웃을 연결하는 역할**을 한다.

기존 Compose에서는 현재 위치만 알 수 있다.

```text
현재 Layout

↓

현재 위치 계산

↓

화면 표시
```

하지만 LookaheadLayout에서는 미래 레이아웃도 함께 계산한다.

```text
현재 Layout

↓

미래 Layout 계산

↓

현재 → 미래까지 애니메이션
```

이때 미래 위치 정보를 Modifier가 사용할 수 있도록 전달하는 객체가 `LookaheadScope`이다.

쉽게 말해 LookaheadScope는 **"미래 레이아웃 정보를 제공하는 창구"** 라고 생각하면 된다.

<br>

### 왜 필요한가?

애니메이션을 만들기 위해서는 현재 위치와 목표 위치를 모두 알아야 한다.

예를 들어 현재 위치가

```text
x = 100
```

이고,

최종 위치가

```text
x = 350
```

이라면 Compose는 두 위치를 이용해

```text
100

↓

180

↓

250

↓

320

↓

350
```

처럼 중간 위치를 계산한다.

하지만 Modifier는 미래 위치를 직접 계산하지 않는다.

대신 LookaheadScope가 계산해 둔 정보를 가져와 애니메이션을 수행한다.

<br>

### 기본 사용법

```kotlin
LookaheadLayout { content ->

    Box(
        modifier = Modifier.animateBounds(this)
    )

}
```

여기서 `this`는 `LookaheadScope`이다.

`animateBounds()`는 이 Scope를 통해 미래 위치와 크기 정보를 전달받는다.

따라서 LookaheadScope 없이 `animateBounds()`만 사용하는 것은 불가능하다.

<br>

## 7. Modifier.animateBounds()와의 관계

LookaheadLayout을 사용할 때 가장 많이 함께 사용하는 Modifier가 `animateBounds()`이다.

이 Modifier는 **위치와 크기를 함께 애니메이션**하는 기능을 제공한다.

여기서 Bounds란 다음 두 가지 정보를 의미한다.

- Position(위치)
- Size(크기)

예를 들어 현재 상태가

```text
위치 : (100, 100)

크기 : 100 × 100
```

이고,

최종 상태가

```text
위치 : (300, 250)

크기 : 180 × 180
```

이라면 `animateBounds()`는 위치와 크기를 동시에 보간한다.

```text
현재 Bounds

↓

중간 Bounds

↓

최종 Bounds
```

덕분에 화면 전체가 자연스럽게 이동하는 것처럼 보인다.

<br>

### 기본 사용법

```kotlin
LookaheadLayout {

    Box(
        modifier = Modifier
            .animateBounds(this)
            .size(100.dp)
    )

}
```

`animateBounds()`의 인자로 `LookaheadScope`를 전달하는 이유는 미래 Bounds 정보를 가져오기 위해서이다.

만약 LookaheadLayout 밖에서 사용한다면 목표 위치를 알 수 없기 때문에 정상적으로 동작하지 않는다.

<br>

### animateContentSize()와 차이점

많은 개발자가 `animateBounds()`와 `animateContentSize()`를 헷갈린다.

두 API는 목적이 다르다.

| animateContentSize() | animateBounds() |
|----------------------|-----------------|
| 크기만 애니메이션 | 위치와 크기를 함께 애니메이션 |
| 현재 Layout 기준 | 미래 Layout 기준 |
| 일반 Compose에서도 사용 가능 | LookaheadLayout 필요 |

예를 들어 카드가 커지는 화면을 생각해 보자.

`animateContentSize()`는 카드의 크기만 부드럽게 변경한다.

```text
100dp

↓

150dp

↓

200dp
```

하지만 아래에 있는 다른 컴포저블은 새로운 위치가 계산된 뒤 이동하기 때문에 순간적으로 움직이는 것처럼 보일 수 있다.

반면 `animateBounds()`는

- 카드의 크기
- 카드의 위치
- 주변 컴포저블의 위치

까지 함께 고려한다.

그래서 레이아웃 전체가 하나의 애니메이션처럼 자연스럽게 움직인다.

<br>

### 언제 사용하면 좋을까?

다음과 같은 화면에서 특히 효과적이다.

- 카드 확장/축소
- Grid ↔ List 전환
- Shared Element Transition
- Drag & Drop
- 화면 재배치 애니메이션

반대로 단순히 컴포저블의 크기만 변경하는 경우에는 `animateContentSize()`만으로도 충분한 경우가 많다.

<br>

## 8. SharedTransition과의 관계

Compose 1.7부터는 `SharedTransitionLayout`이 추가되었다.

화면이 전환될 때 같은 요소가 자연스럽게 이어지는 애니메이션을 쉽게 만들 수 있는 기능이다.

예를 들어 목록 화면의 이미지를 클릭했을 때 상세 화면으로 확대되는 효과를 생각해 보자.

```text
목록 화면

□ □ □

↓

상세 화면

□□□□□□
```

사용자는 하나의 이미지가 자연스럽게 커졌다고 느끼지만, 실제로는 서로 다른 화면이다.

이러한 애니메이션을 만들기 위해서는

- 현재 위치
- 최종 위치
- 현재 크기
- 최종 크기

를 모두 알고 있어야 한다.

이 개념이 바로 LookaheadLayout과 동일하다.

즉, SharedTransition은 내부적으로 **미래 레이아웃을 먼저 계산하는 방식**을 활용해 자연스러운 화면 전환을 구현한다.

개발자가 직접 LookaheadLayout을 사용하는 경우는 많지 않지만, SharedTransition을 이해하려면 LookaheadLayout의 동작 원리를 알고 있는 것이 도움이 된다.

<br>

## 9. 내부 구현 구조 분석

LookaheadLayout은 기존 Layout 시스템을 새로 만든 것이 아니라 **기존 Layout 시스템을 확장한 기능**이다.

동작 과정은 다음과 같다.

```text
State 변경

↓

Lookahead Pass
(미래 Layout 계산)

↓

Measure Pass
(현재 Layout 계산)

↓

Layout

↓

Draw
```

가장 중요한 점은 **현재 상태와 미래 상태를 동시에 관리한다는 것**이다.

```text
현재 위치

↓

미래 위치 계산

↓

두 위치를 이용해 애니메이션
```

기존 Compose에서는 현재 Layout만 존재했지만, LookaheadLayout에서는 미래 Layout도 함께 유지한다.

이 덕분에 Compose는 개발자가 직접 위치를 계산하지 않아도 자연스러운 애니메이션을 만들어 낼 수 있다.

즉, LookaheadLayout의 핵심은 새로운 애니메이션 API가 아니라 **"미래 레이아웃을 미리 계산하는 Layout 시스템"** 이라는 점이다.

<br>

## 10. 실전 예제 1 - 크기 변경 애니메이션

앞에서 살펴본 내용은 LookaheadLayout이 어떻게 동작하는지에 대한 개념이었다.

이번에는 실제로 어떤 상황에서 사용하는지 살펴보자.

가장 대표적인 예시는 **카드 확장 애니메이션**이다.

예를 들어 뉴스 카드나 음악 플레이어처럼 버튼을 누르면 카드가 확장되는 화면을 생각해 보자.

```text
접힌 상태

┌──────┐
│ Card │
└──────┘

↓

펼친 상태

┌──────────────┐
│              │
│     Card     │
│              │
└──────────────┘
```

이때 카드 아래에 다른 컴포저블이 있다면 함께 아래로 이동해야 한다.

기존에는 카드의 크기만 애니메이션되고 아래 컴포저블은 새로운 위치가 계산된 후 이동하기 때문에 어색한 경우가 있었다.

LookaheadLayout을 사용하면 카드와 주변 레이아웃이 하나의 애니메이션처럼 움직인다.

```kotlin
LookaheadLayout {

    Box(
        modifier = Modifier
            .animateBounds(this)
            .size(
                if (expanded) 200.dp else 100.dp
            )
    )

}
```

코드는 단순하지만 내부에서는 다음 순서로 동작한다.

```text
State 변경

↓

최종 크기 계산

↓

현재 크기와 비교

↓

중간 크기 계산

↓

애니메이션
```

개발자는 크기 변화만 작성하면 되고, 현재 크기와 목표 크기 사이의 계산은 Compose가 수행한다.

<br>

### animateContentSize()와 무엇이 다를까?

비슷한 기능으로 `animateContentSize()`가 있다.

```kotlin
Modifier.animateContentSize()
```

이 Modifier는 **자신의 크기 변화만** 애니메이션한다.

반면 `animateBounds()`는

- 자신의 크기
- 자신의 위치
- 부모 Layout 변화

까지 함께 고려한다.

따라서 주변 컴포저블도 자연스럽게 이동한다.

실무에서 카드 확장처럼 화면 전체의 레이아웃이 변경된다면 `animateBounds()`가 더 적합하다.

<br>

## 11. 실전 예제 2 - 위치 변경 애니메이션

LookaheadLayout이 가장 강력한 이유는 **위치 변경 애니메이션**이다.

예를 들어 Grid 화면을 List 화면으로 변경한다고 생각해 보자.

```text
Grid

□ □
□ □

↓

List

□

□

□

□
```

각 아이템은 새로운 위치로 이동해야 한다.

기존 Layout에서는 새로운 위치를 계산한 뒤 화면을 다시 그리기 때문에 아이템이 갑자기 이동하는 것처럼 보일 수 있다.

LookaheadLayout에서는 먼저 모든 아이템의 최종 위치를 계산한다.

```text
현재 위치

↓

목표 위치 계산

↓

현재 → 목표 위치 애니메이션
```

따라서 사용자는 아이템이 자연스럽게 이동하는 것처럼 느끼게 된다.

이러한 방식은 다음과 같은 화면에서도 자주 사용된다.

- Drag & Drop
- 리스트 정렬
- 카드 위치 변경
- Shared Element Transition

<br>

## 12. LookaheadLayout을 사용하면 안 되는 경우

LookaheadLayout은 강력한 기능이지만 모든 화면에서 사용할 필요는 없다.

다음과 같은 경우에는 일반적인 Modifier만으로 충분하다.

### 단순 크기 변경

```kotlin
Modifier.animateContentSize()
```

텍스트가 길어져 높이가 조금 변경되는 정도라면 LookaheadLayout은 과한 선택일 수 있다.

<br>

### 단순 위치 이동

```kotlin
Modifier.offset()
```

버튼 하나를 살짝 이동하는 정도라면 일반 애니메이션으로도 충분하다.

<br>

### 레이아웃이 거의 변경되지 않는 화면

미래 Layout을 계산하는 과정이 추가되므로 일반 Layout보다 계산 비용이 조금 더 발생한다.

따라서 정적인 화면에서는 사용할 이유가 거의 없다.

<br>

## 13. 성능과 주의사항

LookaheadLayout은 Measure를 두 번 수행한다.

```text
Lookahead Measure

↓

Measure
```

이 때문에 일반 Layout보다 계산량이 증가한다.

하지만 대부분의 경우 애니메이션이 발생하는 동안만 추가 계산이 이루어지므로 성능에 큰 영향을 주지는 않는다.

다만 매우 복잡한 화면에서는 다음 사항을 고려하는 것이 좋다.

- 필요한 레이아웃에만 적용하기
- 불필요한 중첩 사용 피하기
- 단순 애니메이션에는 일반 Modifier 사용하기

LookaheadLayout은 **복잡한 레이아웃 전환을 위한 기능**이라는 점을 기억하면 된다.

<br>

## 14. 자주 하는 실수

### 1. animateBounds()만 사용하면 된다고 생각하는 경우

`animateBounds()`는 반드시 LookaheadLayout 안에서 사용해야 한다.

LookaheadLayout이 없다면 미래 레이아웃 정보를 계산할 수 없기 때문이다.

<br>

### 2. animateContentSize()와 같은 기능이라고 생각하는 경우

두 API는 목적이 다르다.

- animateContentSize() → 크기 변화
- animateBounds() → 위치와 크기 변화

프로젝트 상황에 맞는 API를 선택하는 것이 중요하다.

<br>

### 3. 모든 화면에 적용하는 경우

LookaheadLayout은 미래 Layout을 계산하는 기능이다.

단순한 버튼이나 아이콘까지 모두 적용하면 오히려 불필요한 비용만 증가한다.

레이아웃 전체가 변경되는 화면에서 사용하는 것이 가장 효과적이다.

<br>

## 15. 주요 질문

### Q1. LookaheadLayout은 왜 등장했나요?

기존 Layout 시스템은 현재 상태만 계산하기 때문에 레이아웃 전체가 변경되는 애니메이션에서 부자연스러운 움직임이 발생할 수 있다.

LookaheadLayout은 미래 레이아웃을 먼저 계산하여 현재 위치에서 목표 위치까지 자연스럽게 애니메이션하기 위해 등장했다.

<br>

### Q2. LookaheadLayout과 animateContentSize()의 차이는 무엇인가요?

`animateContentSize()`는 자신의 크기만 애니메이션한다.

반면 LookaheadLayout은 미래 레이아웃을 계산하고 `animateBounds()`를 통해 위치와 크기를 함께 애니메이션한다.

<br>

### Q3. LookaheadLayout은 언제 사용하는 것이 좋나요?

레이아웃 전체가 재배치되는 애니메이션에서 사용하면 효과적이다.

대표적으로 카드 확장, Grid ↔ List 전환, Drag & Drop, Shared Element Transition 등이 있다.

<br>

## 정리

LookaheadLayout은 기존 Layout 시스템을 대체하는 기능이 아니라 **미래 레이아웃을 미리 계산하는 Layout 시스템**이다.

- 현재 Layout과 미래 Layout을 함께 계산한다.
- 현재 위치와 목표 위치를 모두 알고 애니메이션을 수행한다.
- `LookaheadScope`를 통해 미래 레이아웃 정보를 Modifier에 전달한다.
- `animateBounds()`를 사용해 위치와 크기를 함께 애니메이션할 수 있다.
- `SharedTransitionLayout`의 기반이 되는 핵심 개념이다.

평소에는 직접 사용할 일이 많지 않을 수 있지만, **Compose의 레이아웃 시스템과 화면 전환 애니메이션을 이해하는 데 중요한 개념**이다.
