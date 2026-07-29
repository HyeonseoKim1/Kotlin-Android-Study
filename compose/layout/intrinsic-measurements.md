# Intrinsic Measurements

## 목차

1. Intrinsic Measurement란?
2. Compose의 측정 과정 복습
3. Intrinsic이 필요한 이유
4. IntrinsicWidth와 IntrinsicHeight
5. IntrinsicSize.Min
6. IntrinsicSize.Max
7. Divider 높이 맞추기
8. Text 길이에 따른 레이아웃
9. Row와 Column에서의 동작
10. Layout에서 Intrinsic 계산
11. 성능상 주의사항
12. 실무에서 사용하는 경우
13. 자주 하는 실수
14. 질문
15. 정리

<br>

# Intrinsic Measurement란?

Intrinsic Measurement는 **자식이 "최소한 어느 정도 크기가 필요한지" 미리 물어보는 과정**이다.

Compose는 기본적으로 부모가 자식에게 Constraints를 전달하고, 자식이 그 안에서 자신의 크기를 결정한다.

하지만 어떤 경우에는

> "실제로 측정하기 전에 이 View는 최소한 얼마나 커야 하지?"

라는 정보가 필요하다.

이때 사용하는 것이 Intrinsic Measurement이다.

예를 들어 Text는

```
안녕하세요
```

라는 문자열을 표시하려면 최소 너비와 최소 높이가 존재한다.

Compose는 필요하다면 먼저 이 값을 계산할 수 있다.

<br>

# Compose의 측정 과정 복습

Compose Layout은 크게 두 단계로 동작한다.

1. 부모가 Constraints 전달
2. 자식이 Measure 수행

예시

```text
Parent

↓

Constraints
(minWidth, maxWidth
 minHeight, maxHeight)

↓

Child.measure()

↓

Placeable 반환

↓

배치(place)
```

일반적으로는 이것만으로 충분하다.

하지만 부모가

> "얘가 얼마나 커질지 먼저 알고 싶은데?"

라고 생각하는 순간 Intrinsic Measurement가 수행된다.

<br>

# Intrinsic이 필요한 이유

대표적인 예이다.

```kotlin
Row {
    Text("Hello")

    Divider(
        modifier = Modifier.width(1.dp)
    )

    Text("Compose")
}
```

실행하면 Divider는 생각보다 길게 나온다.

왜냐하면 Divider는 부모(Row)의 높이를 모르기 때문이다.

```
──────────────
Hello | Compose
──────────────
```

원하는 것은

```
Hello | Compose
```

처럼 Text 높이에 맞는 Divider이다.

이때 Intrinsic Measurement가 필요하다.

<br>

# IntrinsicWidth와 IntrinsicHeight

Intrinsic에는 두 종류가 있다.

### Intrinsic Width

높이가 정해졌을 때 필요한 최소 너비

### Intrinsic Height

너비가 정해졌을 때 필요한 최소 높이

예를 들어

```kotlin
Text("Hello Compose")
```

Text는

- 최소 너비
- 최대 너비
- 최소 높이
- 최대 높이

를 모두 계산할 수 있다.

Compose는 필요한 경우 이 값을 먼저 가져온다.

<br>

# IntrinsicSize.Min

가장 많이 사용하는 Intrinsic이다.

예제

```kotlin
Row(
    modifier = Modifier.height(IntrinsicSize.Min)
) {
    Text("Hello")

    Divider(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
    )

    Text("Compose")
}
```

결과

```
Hello | Compose
```

Divider가 Text 높이에 딱 맞게 된다.

왜냐하면

```
Row

↓

자식들의 최소 높이를 계산

↓

가장 큰 최소 높이 선택

↓

Row 높이 결정

↓

Divider가 fillMaxHeight()
```

순서로 동작하기 때문이다.

<br>

# IntrinsicSize.Max

반대로 최대 크기를 사용할 수도 있다.

```kotlin
Modifier.height(IntrinsicSize.Max)
```

의미는

> 자식들이 요구하는 최대 높이를 기준으로 부모 크기를 결정한다.

실무에서는 Min을 훨씬 많이 사용한다.

<br>

# Divider 높이 맞추기

Intrinsic이 가장 유명한 예제이다.

잘못된 코드

```kotlin
Row {
    Text("A")

    Divider(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
    )

    Text("BBBBB")
}
```

Divider 높이가 제대로 계산되지 않는다.

수정

```kotlin
Row(
    modifier = Modifier.height(IntrinsicSize.Min)
) {
    Text("A")

    Divider(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
    )

    Text("BBBBB")
}
```

이제 Divider가 Text 높이에 맞춰진다.

실무에서 가장 자주 사용하는 Intrinsic 예제이다.

<br>

# Text 길이에 따른 레이아웃

Intrinsic은 Text에서도 자주 사용된다.

```kotlin
Text(
    text = "Compose Layout"
)
```

Compose는 먼저

- 몇 줄이 필요한지
- 최소 높이
- 최소 너비

등을 계산한다.

그 후 실제 Measure를 수행한다.

따라서 Text는 Intrinsic을 매우 잘 지원하는 컴포저블이다.

<br>

# Row와 Column에서의 동작

예제

```kotlin
Row(
    modifier = Modifier.height(IntrinsicSize.Min)
)
```

동작 순서

```text
Row

↓

모든 자식에게

minIntrinsicHeight()

질문

↓

가장 큰 값 선택

↓

Row 높이 결정

↓

실제 Measure
```

Column도 동일하다.

```kotlin
Column(
    modifier = Modifier.width(IntrinsicSize.Min)
)
```

라면

모든 자식에게

```
minIntrinsicWidth()
```

를 먼저 질문한다.

<br>

# Layout에서 Intrinsic 계산

Custom Layout을 만들면 Intrinsic을 직접 구현할 수도 있다.

예시

```kotlin
Layout(
    content = content
) { measurables, constraints ->

    val placeables = measurables.map {
        it.measure(constraints)
    }

    layout(
        constraints.maxWidth,
        constraints.maxHeight
    ) {

    }
}
```

MeasurePolicy에는

```kotlin
minIntrinsicWidth()

maxIntrinsicWidth()

minIntrinsicHeight()

maxIntrinsicHeight()
```

를 구현할 수 있다.

이를 통해 부모가 필요한 크기를 미리 계산할 수 있다.

<br>

# 성능상 주의사항

Intrinsic은 일반 Measure보다 비용이 크다.

이유는

```
Intrinsic 계산

↓

실제 Measure

↓

배치
```

처럼 **한 번 더 계산하기 때문**이다.

즉

```
Measure

↓

끝
```

이 아니라

```
Intrinsic

↓

Measure

↓

Place
```

순서가 된다.

따라서 복잡한 Layout에서 Intrinsic을 남용하면 성능이 저하될 수 있다.

<br>

# 실무에서 사용하는 경우

대표적인 사례

### Divider 높이 맞추기

가장 흔한 사용 사례이다.

### Text 크기에 맞는 Card

텍스트 크기에 따라 Card 높이를 맞춰야 하는 경우 사용한다.

### Custom Layout

직접 Layout을 구현할 때 필요한 최소 크기를 계산하기 위해 사용한다.

### 복잡한 UI 정렬

여러 컴포저블의 크기를 맞춰야 할 때 활용한다.

<br>

# 자주 하는 실수

### fillMaxHeight만 사용하면 해결된다고 생각하기

```kotlin
Divider(
    modifier = Modifier.fillMaxHeight()
)
```

부모 높이가 정해지지 않았다면 아무 효과가 없다.

Intrinsic이 먼저 필요하다.

<br>

### 모든 Layout에 Intrinsic 사용하기

Intrinsic은 비용이 있기 때문에 정말 필요한 경우에만 사용한다.

<br>

### Intrinsic이 크기를 직접 결정한다고 생각하기

Intrinsic은 **실제 크기를 결정하는 것이 아니라, 필요한 크기를 미리 계산하는 과정**이다.

최종 크기는 여전히 Constraints와 Measure 결과에 의해 결정된다.

<br>

### IntrinsicSize.Min과 Max를 혼동하기

- `IntrinsicSize.Min`: 자식들이 필요로 하는 최소 크기를 기준으로 부모 크기 결정
- `IntrinsicSize.Max`: 자식들이 필요로 하는 최대 크기를 기준으로 부모 크기 결정

실무에서는 `IntrinsicSize.Min`이 훨씬 많이 사용된다.

<br>

# 질문 정리

### Q1. Intrinsic Measurement는 무엇인가?

실제 Measure 이전에 컴포저블의 최소 또는 최대 필요 크기를 계산하는 과정이다.

### Q2. 언제 사용하는가?

부모가 자식의 필요한 크기를 먼저 알아야 하는 경우 사용한다.

### Q3. 대표적인 사용 사례는?

`Row`에서 `Divider`의 높이를 `Text` 높이에 맞추는 경우이다.

### Q4. 성능에 영향이 있는가?

있다. Intrinsic 계산이 추가되므로 일반 Measure보다 비용이 높다.

### Q5. Custom Layout에서도 사용할 수 있는가?

가능하다. `MeasurePolicy`에서 Intrinsic 관련 함수를 구현하여 부모가 필요한 크기를 계산할 수 있다.

<br>

# 정리

- Intrinsic Measurement는 실제 측정 전에 필요한 최소·최대 크기를 계산하는 과정이다.
- 부모가 자식의 크기를 미리 알아야 할 때 사용된다.
- `IntrinsicSize.Min`은 실무에서 가장 많이 사용하는 형태이다.
- `Divider` 높이를 `Text`에 맞추는 것이 대표적인 활용 사례이다.
- `IntrinsicSize.Max`도 존재하지만 사용 빈도는 낮다.
- `fillMaxHeight()`는 부모 높이가 결정된 이후에만 의미가 있다.
- Intrinsic은 추가 계산이 발생하므로 성능 비용이 있으며, 필요한 경우에만 사용하는 것이 좋다.
- Custom Layout에서는 `minIntrinsicWidth`, `maxIntrinsicWidth`, `minIntrinsicHeight`, `maxIntrinsicHeight`를 구현하여 Intrinsic 동작을 제어할 수 있다.
