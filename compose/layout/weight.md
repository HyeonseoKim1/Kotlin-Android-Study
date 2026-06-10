# weight

## 목차

1. weight란?
2. Android XML의 layout_weight와 비교
3. 기본 사용법
4. weight가 동작하는 원리
5. Row에서 사용하기
6. Column에서 사용하기
7. 비율 다르게 주기
8. fill 옵션
9. 자주 헷갈리는 부분
10. 실무 사용 예시
11. 정리

# 1. weight란?

`weight()`는 Row 또는 Column 내부에서 **남은 공간을 비율로 나누어 사용하는 Modifier**이다.

Android XML의 `layout_weight`와 비슷한 역할을 한다.

예를 들어 화면 너비가 100이라고 가정하면:

* 첫 번째 컴포넌트 weight = 1
* 두 번째 컴포넌트 weight = 1

이라면 각각 50씩 공간을 차지한다.

<br>

# 2. Android XML의 layout_weight와 비교

Android View 시스템에서는 다음과 같이 사용했다.

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal">

    <Button
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1" />

    <Button
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1" />

</LinearLayout>
```

Compose에서는 Modifier로 처리한다.

```kotlin
Row {
    Button(
        modifier = Modifier.weight(1f),
        onClick = {}
    ) {
        Text("확인")
    }

    Button(
        modifier = Modifier.weight(1f),
        onClick = {}
    ) {
        Text("취소")
    }
}
```

차이점

| XML             | Compose           |
| --------------- | ----------------- |
| layout_weight   | Modifier.weight() |
| width = 0dp 필요  | 자동 처리             |
| View 속성         | Modifier          |
| LinearLayout 전용 | Row, Column 사용    |

Compose는 훨씬 직관적이다.

<br>

# 3. 기본 사용법

```kotlin
Row {
    Box(
        modifier = Modifier.weight(1f)
    )

    Box(
        modifier = Modifier.weight(1f)
    )
}
```

결과

* 첫 번째 Box : 50%
* 두 번째 Box : 50%

<br>

# 4. weight가 동작하는 원리

weight는 전체 공간을 기준으로 계산한다.

예시

```kotlin
Row(
    modifier = Modifier.fillMaxWidth()
) {
    Box(
        modifier = Modifier.weight(1f)
    )

    Box(
        modifier = Modifier.weight(1f)
    )
}
```

화면 너비가 400dp라면

```text
400 ÷ (1 + 1)

= 200dp
```

각 Box가 200dp씩 차지한다.

<br>

# 5. Row에서 사용하기

가장 많이 사용하는 경우이다.

```kotlin
Row(
    modifier = Modifier.fillMaxWidth()
) {
    Text(
        text = "왼쪽",
        modifier = Modifier.weight(1f)
    )

    Text(
        text = "오른쪽",
        modifier = Modifier.weight(1f)
    )
}
```

결과

```text
[왼쪽          ][오른쪽          ]
```

각각 동일한 너비를 가진다.

<br>

# 6. Column에서 사용하기

세로 공간도 비율로 나눌 수 있다.

```kotlin
Column(
    modifier = Modifier.fillMaxHeight()
) {
    Box(
        modifier = Modifier.weight(1f)
    )

    Box(
        modifier = Modifier.weight(1f)
    )
}
```

결과

```text
위 영역 : 50%
아래 영역 : 50%
```

<br>

# 7. 비율 다르게 주기

weight 값은 비율이다.

```kotlin
Row(
    modifier = Modifier.fillMaxWidth()
) {
    Box(
        modifier = Modifier.weight(1f)
    )

    Box(
        modifier = Modifier.weight(2f)
    )
}
```

계산

```text
1 + 2 = 3
```

첫 번째

```text
1 / 3
```

두 번째

```text
2 / 3
```

결과

```text
33% : 67%
```

<br>

# 8. fill 옵션

기본값

```kotlin
Modifier.weight(
    weight = 1f,
    fill = true
)
```

fill = true

```kotlin
Text(
    text = "Hello",
    modifier = Modifier.weight(
        1f,
        fill = true
    )
)
```

할당받은 공간 전체를 사용한다.

<br>

fill = false

```kotlin
Text(
    text = "Hello",
    modifier = Modifier.weight(
        1f,
        fill = false
    )
)
```

weight 계산은 하지만 실제 크기는 콘텐츠 크기만 사용한다.

실무에서는 대부분 기본값인 true를 사용한다.

<br>

# 9. 자주 헷갈리는 부분

## 1) Row와 Column 안에서만 사용 가능

```kotlin
Box(
    modifier = Modifier.weight(1f)
)
```

단독으로 사용하면 동작하지 않는다.

weight는 부모가 Row 또는 Column일 때만 의미가 있다.

<br>

## 2) 크기를 지정하면 weight 의미가 달라질 수 있음

```kotlin
Modifier
    .width(100.dp)
    .weight(1f)
```

같이 사용하는 경우 예상과 다른 결과가 나올 수 있다.

weight를 사용할 때는 보통 width 또는 height를 직접 지정하지 않는다.

<br>

## 3) weight는 비율이지 크기가 아니다

```kotlin
Modifier.weight(100f)
```

100배 크기가 되는 것이 아니다.

비율 계산만 수행한다.

예시

```kotlin
1f : 1f
```

과

```kotlin
100f : 100f
```

는 결과가 같다.

<br>

# 10. 실무 사용 예시

## 버튼 반반 배치

```kotlin
Row(
    modifier = Modifier.fillMaxWidth()
) {
    Button(
        modifier = Modifier.weight(1f),
        onClick = {}
    ) {
        Text("취소")
    }

    Button(
        modifier = Modifier.weight(1f),
        onClick = {}
    ) {
        Text("확인")
    }
}
```

<br>

## 좌측 메뉴 + 우측 콘텐츠

```kotlin
Row(
    modifier = Modifier.fillMaxSize()
) {
    NavigationRail(
        modifier = Modifier.weight(1f)
    ) {}

    Column(
        modifier = Modifier.weight(3f)
    ) {
        Text("Content")
    }
}
```

결과

```text
메뉴 25%
콘텐츠 75%
```

<br>

## 상단 영역 + 하단 영역

```kotlin
Column(
    modifier = Modifier.fillMaxSize()
) {
    Box(
        modifier = Modifier.weight(2f)
    )

    Box(
        modifier = Modifier.weight(1f)
    )
}
```

결과

```text
상단 66%
하단 33%
```

<br>

# 11. 정리

* weight는 남은 공간을 비율로 분배한다.
* Android XML의 layout_weight와 같은 역할이다.
* Row에서는 너비를 분배한다.
* Column에서는 높이를 분배한다.
* 1f : 1f 는 50 : 50 이다.
* 1f : 2f 는 33 : 67 이다.
* 부모가 Row 또는 Column일 때만 동작한다.
* 실무에서는 버튼 균등 배치, 화면 분할, 메뉴/콘텐츠 영역 분할에 자주 사용한다.
* Compose에서는 XML보다 간단하게 Modifier.weight()로 사용할 수 있다.
