# SubcomposeLayout

## 목차

1. SubcomposeLayout이란?
2. 왜 필요한가?
3. 일반 Layout과 차이
4. Compose의 측정 과정
5. Subcompose가 필요한 이유
6. 기본 사용법
7. measurePolicy 이해하기
8. subcompose() 함수 이해하기

<br>

## 1. SubcomposeLayout이란?

SubcomposeLayout은 **측정(Measure) 도중에 새로운 Composable을 생성(Composition)할 수 있는 Layout**이다.

일반적인 Compose Layout은

1. Composition
2. Measure
3. Layout

순서로 실행된다.

즉, 이미 Composition이 끝난 상태에서 Measure가 진행된다.

하지만 SubcomposeLayout은 Measure 단계에서

"필요하면 지금 새로운 Composable을 만들어."

라는 것이 가능하다.

이것이 일반 Layout과 가장 큰 차이이다.

<br>

## 2. 왜 필요한가?

Compose에서는 보통 "무엇을 그릴지" 먼저 결정하고

그 다음 "얼마나 큰지" 측정한다.

예를 들어

```kotlin
Text("Hello")
```

Composition이 먼저 만들어지고

그 다음 Text의 크기를 측정한다.

대부분은 이 방식이면 충분하다.

하지만 다음과 같은 경우에는 문제가 생긴다.

- 부모 크기를 먼저 알아야 자식을 만들 수 있다.
- 첫 번째 컴포넌트 크기를 알아야 두 번째를 만들 수 있다.
- 화면에 보이는 아이템만 Composition 하고 싶다.
- LazyColumn처럼 필요한 것만 그리고 싶다.

이런 상황에서는 "측정이 끝난 뒤" Composition 하는 기능이 필요하다.

그래서 SubcomposeLayout이 존재한다.

<br>

## 3. 일반 Layout과 차이

일반 Layout

```
Composition

↓

Measure

↓

Layout
```

이미 모든 Composable이 생성되어 있다.

Measure에서는 크기만 계산한다.

<br>

SubcomposeLayout

```
Composition

↓

Measure 시작

↓

필요한 Composable 생성

↓

Measure

↓

필요하면 또 생성

↓

Layout
```

Measure 중간에 새로운 UI를 계속 만들 수 있다.

이것이 핵심이다.

<br>

## 4. Compose의 측정 과정

예를 들어

```kotlin
Row {
    Text("Hello")
    Button(...)
}
```

실행 순서는

### 1단계

Composition

```
Row
 ├── Text
 └── Button
```

모든 UI Tree가 생성된다.

<br>

### 2단계

Measure

```
Text 측정

↓

Button 측정

↓

Row 크기 결정
```

<br>

### 3단계

Layout

각 위치를 배치한다.

여기서는 새로운 UI를 만들 수 없다.

이미 끝났기 때문이다.

<br>

## 5. 왜 Subcompose가 필요한가?

예를 들어 Tooltip을 만든다고 해보자.

```
+--------------------+
| Hello World        |
+--------------------+

       ▲

Tooltip
```

Tooltip 크기는

Text 크기를 알아야 한다.

하지만 일반 Layout에서는

Tooltip을 먼저 만들어야 한다.

즉

```
Tooltip

↓

Text 측정
```

이 순서라 문제가 생긴다.

SubcomposeLayout은

```
Text 생성

↓

Text 측정

↓

Tooltip 생성

↓

Tooltip 측정
```

순서가 가능하다.

<br>

또 다른 예

화면 크기에 따라

```
Tablet

↓

Grid

Phone

↓

List
```

를 결정해야 한다면

먼저 부모 크기를 측정한 후

그 결과를 보고 다른 UI를 생성할 수 있다.

<br>

## 6. 기본 사용법

가장 단순한 형태이다.

```kotlin
SubcomposeLayout { constraints ->

    val placeables =
        subcompose("content") {

            Text("Hello")
        }.map {

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

처음 보면 어렵지만

실제로는 세 단계이다.

### 1

subcompose()

Composable 생성

↓

### 2

measure()

크기 측정

↓

### 3

layout()

배치

일반 Layout과 거의 같지만

Composable을 Measure 안에서 만든다는 차이만 있다.

<br>

## 7. measurePolicy 이해하기

SubcomposeLayout의 람다는

```kotlin
SubcomposeLayout { constraints ->
```

사실

MeasurePolicy이다.

여기서

```kotlin
constraints
```

는 부모가 전달한 제한이다.

예를 들어

```
최소 너비

최대 너비

최소 높이

최대 높이
```

가 들어 있다.

우리는 이 정보를 이용해

어떤 UI를 만들지 결정할 수 있다.

예를 들어

```kotlin
if (constraints.maxWidth > 600) {
    ...
}
```

처럼 Tablet 여부도 판단할 수 있다.

<br>

## 8. subcompose() 함수 이해하기

가장 중요한 함수이다.

```kotlin
subcompose(
    slotId = "header"
) {

    Header()
}
```

반환값은

```kotlin
List<Measurable>
```

이다.

즉

Composable을 바로 그리는 것이 아니다.

먼저

측정 가능한 객체를 만든다.

그 다음

```kotlin
measure()
```

를 호출해야

Placeable이 된다.

정리하면

```
Composable

↓

Measurable

↓

Placeable

↓

place()
```

이 과정이 SubcomposeLayout의 핵심이다.

<br>

# 정리

- SubcomposeLayout은 Measure 단계에서 Composition을 수행할 수 있는 Layout이다.
- 부모 크기나 다른 컴포넌트의 측정 결과를 기반으로 UI를 생성할 수 있다.
- LazyColumn, BoxWithConstraints, TabRow 등 Compose 내부에서도 많이 사용된다.
- 핵심 흐름은 subcompose → measure → place이다.
