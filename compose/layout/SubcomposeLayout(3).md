# SubcomposeLayout

## 목차

20. LayoutNode 관점에서 내부 동작
21. SubcomposeLayoutState란?
22. SubcomposeSlotReusePolicy와 Slot 재사용
23. Compose Runtime과의 관계
24. Compose 내부 구현 코드 분석
25. TabRow 내부 흐름 분석
26. LazyColumn 내부 흐름 분석
27. 실전 예제 1 - Indicator 구현
28. 실전 예제 2 - Tooltip 구현
29. 실전 예제 3 - Adaptive Layout
30. Layout vs SubcomposeLayout 비교
31. 언제 SubcomposeLayout을 선택해야 할까?
32. 자주 나오는 질문
33. 정리

<br>

## 20. LayoutNode 관점에서 내부 동작

앞에서는 SubcomposeLayout의 사용법을 살펴봤다.

이번에는 Compose 내부에서 어떤 방식으로 동작하는지 알아보자.

Compose는 모든 Composable을 `LayoutNode`라는 객체로 관리한다.

예를 들어

```kotlin
Column {
    Text("A")
    Button(...)
}
```

실제로는 다음과 같은 트리 구조가 만들어진다.

```
LayoutNode(Column)
│
├── LayoutNode(Text)
│
└── LayoutNode(Button)
```

각 LayoutNode는

- Measure
- Layout
- Draw

과정을 수행한다.

일반 Layout은 Composition 단계에서 모든 LayoutNode가 생성된다.

하지만 SubcomposeLayout은 Measure 단계에서 새로운 LayoutNode를 생성할 수 있다.

즉

```
Measure 시작

↓

새 LayoutNode 생성

↓

Measure

↓

Layout
```

라는 특별한 흐름을 가진다.

<br>

## 21. SubcomposeLayoutState란?

SubcomposeLayout 내부에는 `SubcomposeLayoutState`가 존재한다.

직접 사용할 일은 많지 않지만 내부 동작을 이해할 때 매우 중요하다.

State는

- slot 관리
- Composition 재사용
- LayoutNode 재사용
- Dispose 관리

를 담당한다.

간단히 말하면

```
slotId

↓

Composition

↓

LayoutNode
```

를 연결해 주는 관리자이다.

예를 들어

```kotlin
subcompose("header")
```

를 호출하면

State가

```
"header"

↓

기존 Composition 존재?

↓

있으면 재사용

↓

없으면 생성
```

을 수행한다.

<br>

## 22. SubcomposeSlotReusePolicy란?

LazyColumn은 계속 아이템이 생성되고 사라진다.

만약 스크롤할 때마다

```
생성

↓

삭제

↓

생성

↓

삭제
```

를 반복하면 성능이 매우 나빠진다.

그래서 Compose는 Slot을 재사용한다.

이를 담당하는 것이

```
SubcomposeSlotReusePolicy
```

이다.

예를 들어

```
Item 1

↓

화면 밖

↓

삭제하지 않음

↓

재사용 대기
```

이후

```
Item 50 등장

↓

기존 Slot 재사용
```

한다.

즉 새로운 Composition을 만들지 않고 기존 것을 재활용한다.

RecyclerView의 ViewHolder와 비슷한 개념이지만

View가 아니라 Composition을 재사용한다는 차이가 있다.

<br>

## 23. Compose Runtime과의 관계

SubcomposeLayout은 Compose Runtime과도 밀접하게 연결되어 있다.

일반 Compose에서는

```
Composition

↓

Slot Table 생성

↓

Measure
```

순서로 진행된다.

하지만 SubcomposeLayout은

```
Measure 시작

↓

새 Composition

↓

Slot Table 추가

↓

Measure
```

가 가능하다.

즉 Runtime 입장에서는

Measure 도중 새로운 Composition이 추가되는 특수한 상황이다.

그래서 일반 Layout보다 구현이 훨씬 복잡하다.

<br>

## 24. Compose 내부 구현 코드 흐름

실제 내부 흐름은 다음과 비슷하다.

```kotlin
subcompose(slotId) {

    Content()
}
```

호출하면

```
slot 존재?

↓

YES

↓

Composition 재사용

↓

Measure

↓

Place
```

또는

```
slot 존재?

↓

NO

↓

Composition 생성

↓

LayoutNode 생성

↓

Measure

↓

Place
```

라는 순서로 동작한다.

즉 `subcompose()`는 단순히 Composable을 실행하는 함수가 아니라

Composition 생명주기 전체를 관리하는 함수이다.

<br>

## 25. TabRow 내부 흐름 분석

TabRow는 대표적인 SubcomposeLayout 사용 사례이다.

동작 순서는 다음과 같다.

### 1단계

Tab들을 Composition

```
Tab1

Tab2

Tab3
```

<br>

### 2단계

각 Tab의 크기 측정

```
Tab1 Width

Tab2 Width

Tab3 Width
```

<br>

### 3단계

선택된 Tab 위치 계산

```
Selected

↓

Left

↓

Width
```

<br>

### 4단계

Indicator 생성

```
Indicator(
    width,
    offset
)
```

Indicator는 선택된 Tab의 정보를 알아야 하기 때문에 일반 Layout으로 구현하기 어렵다.

<br>

## 26. LazyColumn 내부 흐름 분석

LazyColumn도 SubcomposeLayout 기반이다.

예를 들어

```
10000개 데이터
```

가 있다고 하자.

초기 화면에서는

```
1

2

3

4

5

6

7

8
```

정도만 Composition된다.

스크롤하면

```
9

10

11
```

이 추가되고

```
1

2
```

는 화면 밖으로 나간다.

이때

```
1 제거

↓

Slot 재사용

↓

9 생성
```

이 아니라

```
1의 Slot

↓

9가 재사용
```

된다.

그래서 스크롤이 매우 빠르다.

<br>

## 27. 실전 예제 1 - Indicator 구현

다음은 선택된 Text의 길이에 맞는 Indicator를 만드는 예시이다.

```kotlin
SubcomposeLayout { constraints ->

    val title = subcompose("title") {
        Text("Compose")
    }

    val titlePlaceable =
        title.first().measure(constraints)

    val indicator =
        subcompose("indicator") {

            Indicator(
                width = titlePlaceable.width
            )
        }

    ...
}
```

흐름은

```
Title 생성

↓

Title 측정

↓

Width 획득

↓

Indicator 생성
```

이다.

이런 구조는 일반 Layout으로 구현하기 어렵다.

<br>

## 28. 실전 예제 2 - Tooltip 구현

Tooltip은 대상 View의 크기를 알아야 한다.

```
Text

↓

Measure

↓

Tooltip Width 계산

↓

Tooltip 생성
```

SubcomposeLayout을 사용하면

Tooltip을 정확한 위치에 배치할 수 있다.

그래서 Tooltip, Popup, Dropdown 등의 라이브러리에서도 비슷한 방식이 사용된다.

<br>

## 29. 실전 예제 3 - Adaptive Layout

태블릿과 모바일에서 완전히 다른 화면을 만들 수도 있다.

```kotlin
SubcomposeLayout { constraints ->

    if (constraints.maxWidth > 700.dp.roundToPx()) {

        subcompose("tablet") {
            TabletScreen()
        }

    } else {

        subcompose("phone") {
            PhoneScreen()
        }
    }

    ...
}
```

부모의 크기를 확인한 후

필요한 화면만 Composition한다.

<br>

## 30. Layout vs SubcomposeLayout 비교

|항목|Layout|SubcomposeLayout|
|---|---|---|
|Composition 시점|Measure 이전|Measure 중 가능|
|새 UI 생성|불가능|가능|
|앞의 측정 결과 활용|불가능|가능|
|부모 Constraints 활용|제한적|매우 자유로움|
|성능|더 좋음|비용이 더 큼|
|주요 사용처|일반 Layout|Lazy, TabRow, BoxWithConstraints|

<br>

## 31. 언제 SubcomposeLayout을 선택해야 할까?

다음 질문 중 하나라도 "예"라면 SubcomposeLayout을 고려할 수 있다.

### 부모 크기를 먼저 알아야 하는가?

예)

```
Phone

Tablet
```

을 나누는 경우

<br>

### 다른 Composable의 크기가 필요한가?

예)

```
Indicator

Tooltip

Badge
```

<br>

### 필요한 UI만 생성해야 하는가?

예)

```
LazyColumn

LazyRow

Pager
```

<br>

### 여러 Composition을 독립적으로 관리해야 하는가?

예)

```
Header

Body

Footer
```

<br>

위 조건이 아니라면 대부분

```
Box

Row

Column

Layout
```

만으로 충분하다.

<br>

## 32. 자주 나오는 질문

### Q. SubcomposeLayout은 왜 필요한가?

Measure 단계에서 Composition을 수행할 수 있기 때문이다.

이를 통해 부모의 Constraints나 다른 Composable의 측정 결과를 기반으로 새로운 UI를 생성할 수 있다.

<br>

### Q. Layout과 가장 큰 차이는?

Layout은 Measure 전에 Composition이 모두 끝난다.

SubcomposeLayout은 Measure 중에도 Composition을 수행할 수 있다.

<br>

### Q. LazyColumn은 왜 SubcomposeLayout을 사용할까?

화면에 보이는 아이템만 Composition하고, 화면 밖의 Slot을 재사용하기 위해서이다.

<br>

### Q. BoxWithConstraints와의 관계는?

BoxWithConstraints는 부모 Constraints를 먼저 얻은 뒤 UI를 결정해야 하므로 내부적으로 SubcomposeLayout을 사용한다.

<br>

### Q. 항상 SubcomposeLayout을 사용하면 좋은가?

아니다.

Composition을 Measure 과정에서 수행하기 때문에 일반 Layout보다 비용이 크다.

반드시 필요한 경우에만 사용하는 것이 좋다.

<br>

# 정리

- SubcomposeLayout은 Measure 단계에서 Composition을 수행할 수 있는 특수한 Layout이다.
- LayoutNode와 Slot을 동적으로 생성하고 관리할 수 있다.
- `slotId`와 `SubcomposeLayoutState`를 통해 Composition을 재사용한다.
- `SubcomposeSlotReusePolicy`는 화면 밖의 Slot을 재활용하여 성능을 높인다.
- Compose의 `LazyColumn`, `LazyRow`, `TabRow`, `BoxWithConstraints` 등이 SubcomposeLayout을 기반으로 구현되어 있다.
- 일반적인 레이아웃에서는 `Layout`을 사용하는 것이 더 효율적이며, SubcomposeLayout은 부모 크기나 다른 컴포넌트의 측정 결과가 필요한 경우에 사용하는 것이 적절하다.
