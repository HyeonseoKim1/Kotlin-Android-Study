# SubcomposeLayout

## 목차

9. Constraints를 활용한 동적 UI 생성
10. slotId란 무엇인가?
11. 왜 subcompose()를 여러 번 호출할까?
12. 앞에서 측정한 결과를 뒤에서 사용할 수 있다
13. TabRow가 SubcomposeLayout을 사용하는 이유
14. BoxWithConstraints와의 관계
15. LazyColumn도 같은 원리를 사용한다
16. SubcomposeLayout이 성능에 좋은 이유
17. 언제 사용하는 것이 좋을까?
18. SubcomposeLayout을 남용하면 안 되는 이유
19. Part 2 정리

## 9. Constraints를 활용한 동적 UI 생성

SubcomposeLayout의 가장 큰 장점은 부모로부터 전달받은 `Constraints`를 이용해 어떤 UI를 생성할지 결정할 수 있다는 점이다.

일반적인 Layout에서는 이미 모든 Composable이 Composition된 이후 Measure가 진행되므로 부모의 크기에 따라 서로 다른 Composable을 생성하기 어렵다.

하지만 SubcomposeLayout은 Measure 과정에서 Composition을 수행하기 때문에 다음과 같은 코드가 가능하다.

```kotlin
SubcomposeLayout { constraints ->

    val measurables =
        if (constraints.maxWidth > 600.dp.roundToPx()) {
            subcompose("tablet") {
                TabletLayout()
            }
        } else {
            subcompose("phone") {
                PhoneLayout()
            }
        }

    val placeables = measurables.map {
        it.measure(constraints)
    }

    layout(
        constraints.maxWidth,
        constraints.maxHeight
    ) {
        placeables.forEach {
            it.place(0, 0)
        }
    }
}
```

일반 Layout에서는 이미 `TabletLayout()`과 `PhoneLayout()` 모두 Composition된 상태에서 Measure가 진행된다.

반면 SubcomposeLayout은 부모의 크기를 확인한 후 필요한 UI만 Composition한다.

즉

```
Constraints 확인

↓

Tablet인가?

↓

TabletLayout 생성

또는

PhoneLayout 생성
```

이런 흐름이 가능하다.

<br>

## 10. slotId란 무엇인가?

SubcomposeLayout을 사용할 때 항상 등장하는 것이 `slotId`이다.

```kotlin
subcompose("header") {
    Header()
}

subcompose("content") {
    Content()
}

subcompose("footer") {
    Footer()
}
```

처음 보면 단순한 문자열처럼 보이지만 실제로는 매우 중요한 역할을 한다.

slotId는

"이 Composition이 어떤 영역인지"

를 Compose에게 알려주는 식별자이다.

Compose는 slotId를 이용하여

- 기존 Composition 재사용
- State 유지
- 필요 없는 Composition 제거

등을 수행한다.

<br>

예를 들어

```kotlin
subcompose("header") {
    Header()
}
```

다음 Measure에서도

```kotlin
subcompose("header") {
    Header()
}
```

를 호출하면

새로 만드는 것이 아니라 기존 Composition을 재사용한다.

반대로

```kotlin
subcompose("title") {
    Header()
}
```

처럼 slotId가 변경되면

Compose는 새로운 Composition이라고 판단한다.

즉

```
같은 slotId

↓

기존 Composition 재사용
```

```
다른 slotId

↓

새 Composition 생성
```

이라는 차이가 있다.

<br>

## 11. 왜 subcompose()를 여러 번 호출할까?

SubcomposeLayout에서는 `subcompose()`를 여러 번 호출하는 경우가 매우 많다.

예를 들어

```kotlin
SubcomposeLayout { constraints ->

    val header = subcompose("header") {
        Header()
    }

    val body = subcompose("body") {
        Body()
    }

    val footer = subcompose("footer") {
        Footer()
    }

    ...
}
```

각 영역을 독립적으로 Composition할 수 있기 때문이다.

이렇게 하면

- Header만 다시 Composition
- Body만 다시 Composition
- Footer만 다시 Composition

하는 것이 가능하다.

즉 하나의 큰 UI를 여러 개의 작은 Composition으로 나누어 관리할 수 있다.

<br>

## 12. 앞에서 측정한 결과를 뒤에서 사용할 수 있다

SubcomposeLayout의 가장 강력한 기능은

"앞에서 만든 UI의 크기를 이용해 뒤의 UI를 생성"

하는 것이다.

예를 들어

```kotlin
val title = subcompose("title") {
    Title()
}.first().measure(constraints)

val indicator = subcompose("indicator") {
    Indicator(title.width)
}
```

순서는

```
Title 생성

↓

Title 측정

↓

Title의 width 획득

↓

Indicator 생성
```

이다.

일반 Layout에서는 불가능한 흐름이다.

<br>

## 13. TabRow가 SubcomposeLayout을 사용하는 이유

Compose의 `TabRow`는 내부적으로 SubcomposeLayout을 사용한다.

예를 들어

```kotlin
TabRow(
    selectedTabIndex = selected
) {

    Tab(...)

    Tab(...)

    Tab(...)
}
```

Indicator의 길이는

현재 선택된 Tab의 너비와 위치를 알아야 결정된다.

하지만 일반 Layout에서는

Indicator가 Tab의 크기를 알 수 없다.

SubcomposeLayout에서는

```
Tab 생성

↓

Tab 측정

↓

선택된 Tab의 width 확인

↓

Indicator 생성

↓

Indicator 배치
```

가 가능하다.

그래서 Indicator가 항상 선택된 Tab의 길이에 맞춰 움직일 수 있다.

<br>

## 14. BoxWithConstraints와의 관계

많은 사람들이

```kotlin
BoxWithConstraints
```

를 사용하면서 내부 구현을 궁금해한다.

BoxWithConstraints 역시 SubcomposeLayout을 기반으로 만들어져 있다.

예를 들어

```kotlin
BoxWithConstraints {

    if (maxWidth > 600.dp) {
        TabletScreen()
    } else {
        PhoneScreen()
    }
}
```

겉보기에는 단순한 Box처럼 보인다.

하지만 내부에서는

```
Constraints 측정

↓

maxWidth 계산

↓

Composable 생성
```

순서로 동작한다.

즉 부모의 Constraints를 먼저 얻기 위해 SubcomposeLayout을 활용한다.

<br>

## 15. LazyColumn도 같은 원리를 사용한다

LazyColumn이 수천 개의 아이템을 모두 Composition하지 않는 이유도 SubcomposeLayout 덕분이다.

예를 들어

```kotlin
LazyColumn {

    items(10000) {

        Text("$it")
    }
}
```

실제로는

10000개의 Text를 모두 만드는 것이 아니다.

현재 화면에 필요한 아이템만

```
subcompose()

↓

measure()

↓

배치
```

한다.

스크롤하면

화면 밖의 아이템은 제거하고

새로운 아이템만 Composition한다.

그래서 매우 많은 데이터를 표시해도 성능이 유지된다.

<br>

## 16. SubcomposeLayout이 성능에 좋은 이유

SubcomposeLayout은 필요 없는 UI를 생성하지 않는다.

예를 들어

```
1000개 아이템

↓

현재 화면에는 12개만 보임
```

일반 Column이라면

```
1000개 모두 Composition
```

하지만 LazyColumn은

```
12개만 Composition

↓

스크롤

↓

필요한 것만 추가 Composition
```

한다.

그래서 메모리 사용량과 Composition 비용이 크게 감소한다.

다만 SubcomposeLayout 자체가 항상 성능이 좋은 것은 아니다.

Composition을 Measure 과정에서 수행하기 때문에 일반 Layout보다 비용이 더 크다.

따라서 단순한 레이아웃을 만들기 위해 사용하는 것은 권장되지 않는다.

<br>

## 17. 언제 사용하는 것이 좋을까?

다음과 같은 상황에서 사용하는 것이 적합하다.

### 부모 크기에 따라 UI가 달라질 때

```kotlin
Constraints 확인

↓

Tablet UI

또는

Phone UI
```

<br>

### 첫 번째 컴포넌트의 크기로 두 번째를 생성해야 할 때

```
Text 측정

↓

Text Width

↓

Indicator 생성
```

<br>

### 필요한 UI만 Composition해야 할 때

```
LazyColumn

LazyRow

Pager
```

<br>

### 여러 영역을 독립적으로 Composition해야 할 때

```
Header

Content

Footer
```

각 영역을 별도의 slot으로 관리할 수 있다.

<br>

## 18. SubcomposeLayout을 남용하면 안 되는 이유

SubcomposeLayout은 매우 강력하지만 비용도 크다.

매번 Measure 과정에서 새로운 Composition이 발생할 수 있기 때문이다.

다음과 같은 단순한 레이아웃이라면

```kotlin
Row

Column

Box
```

또는

```kotlin
Layout
```

만으로 충분하다.

SubcomposeLayout은

- 부모 크기를 먼저 알아야 하는 경우
- 다른 Composable의 측정 결과가 필요한 경우
- Lazy 방식의 Composition이 필요한 경우

처럼 일반 Layout으로 해결할 수 없는 문제에서 사용하는 것이 좋다.

<br>

# Part 2 정리

- Constraints를 이용해 Measure 단계에서 동적으로 UI를 생성할 수 있다.
- `slotId`는 Composition을 식별하고 재사용하기 위한 핵심 키이다.
- `subcompose()`를 여러 번 호출하여 영역별로 독립적인 Composition을 관리할 수 있다.
- 앞에서 측정한 결과를 이용해 뒤의 Composable을 생성하는 것이 SubcomposeLayout의 핵심이다.
- `TabRow`, `BoxWithConstraints`, `LazyColumn` 등 Compose 내부의 다양한 컴포넌트가 SubcomposeLayout을 기반으로 구현되어 있다.
- 일반 Layout보다 비용이 크므로 꼭 필요한 상황에서만 사용하는 것이 좋다.
