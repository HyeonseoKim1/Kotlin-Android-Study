# FlowRow

## 목차

1. FlowRow란?
2. 왜 사용하는가?
3. Row와의 차이
4. 기본 사용법
5. 주요 속성
6. 실전 예제 - 태그(Chip) 목록
7. 실전 예제 - 반응형 버튼 배치
8. FlowColumn과의 차이
9. 사용 시 주의사항
10. 언제 사용하면 좋을까?
11. 정리

<br>

## 1. FlowRow란?

`FlowRow`는 컴포저블을 가로 방향으로 배치하다가 더 이상 공간이 부족하면 자동으로 다음 줄로 이동시키는 레이아웃이다.

웹의 CSS `flex-wrap`과 비슷한 방식으로 동작하며, 화면 크기에 따라 자연스럽게 줄바꿈이 이루어진다.

대표적으로 다음과 같은 UI에서 많이 사용된다.

- 태그(Chip)
- 카테고리 목록
- 검색 추천어
- 필터 버튼
- 해시태그

<br>

## 2. 왜 사용하는가?

예를 들어 여러 개의 태그를 한 줄에 표시한다고 가정해 보자.

`Row`는 모든 컴포저블을 한 줄에만 배치하기 때문에 화면 너비를 초과하면 잘리거나 직접 줄바꿈을 구현해야 한다.

반면 `FlowRow`는 남은 공간이 부족하면 자동으로 다음 줄로 이동한다.

따라서 화면 크기가 달라져도 별도의 계산 없이 자연스러운 반응형 UI를 만들 수 있다.

<br>

## 3. Row와의 차이

| Row | FlowRow |
|------|----------|
| 한 줄만 배치 | 여러 줄 자동 배치 |
| 줄바꿈 불가능 | 자동 줄바꿈 가능 |
| 단순 가로 배치 | 반응형 가로 배치 |
| 공간 부족 시 잘림 | 공간 부족 시 다음 줄 이동 |

### Row

```kotlin
Row {
    Item1()
    Item2()
    Item3()
    Item4()
}
```

결과

```
[1][2][3][4]
```

### FlowRow

```kotlin
FlowRow {
    Item1()
    Item2()
    Item3()
    Item4()
}
```

결과

```
[1][2]
[3][4]
```

화면 너비가 줄어들면 자동으로 다음 줄로 이동한다.

<br>

## 4. 기본 사용법

### Import

```kotlin
import androidx.compose.foundation.layout.FlowRow
```

기본 예제

```kotlin
@Composable
fun FlowRowExample() {

    FlowRow {
        Text("Android")
        Text("Compose")
        Text("Kotlin")
        Text("Hilt")
        Text("Room")
        Text("Retrofit")
    }
}
```

별도의 줄바꿈 로직 없이 화면 크기에 맞게 자동으로 배치된다.

<br>

## 5. 주요 속성

### horizontalArrangement

같은 줄 안에서 아이템 사이의 가로 간격을 지정한다.

```kotlin
FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    ...
}
```

<br>

### verticalArrangement

줄과 줄 사이의 세로 간격을 지정한다.

```kotlin
FlowRow(
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    ...
}
```

<br>

### maxItemsInEachRow

한 줄에 배치할 최대 아이템 개수를 제한한다.

```kotlin
FlowRow(
    maxItemsInEachRow = 3
) {
    ...
}
```

결과

```
1 2 3
4 5 6
7 8
```

<br>

### modifier

크기나 padding 등을 지정할 수 있다.

```kotlin
FlowRow(
    modifier = Modifier.fillMaxWidth()
) {
    ...
}
```

<br>

## 6. 실전 예제 - 태그(Chip) 목록

```kotlin
@Composable
fun TagList() {

    val tags = listOf(
        "Android",
        "Compose",
        "Kotlin",
        "Hilt",
        "Room",
        "Retrofit",
        "Coroutine",
        "Flow"
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        tags.forEach { tag ->

            AssistChip(
                onClick = {},
                label = {
                    Text(tag)
                }
            )
        }
    }
}
```

태그 개수가 늘어나도 자동으로 다음 줄에 배치되므로 별도의 줄바꿈 로직이 필요 없다.

<br>

## 7. 실전 예제 - 반응형 버튼 배치

```kotlin
@Composable
fun ButtonGroup() {

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        repeat(10) { index ->

            Button(
                onClick = {}
            ) {
                Text("버튼 ${index + 1}")
            }
        }
    }
}
```

화면이 넓으면 한 줄에 많이 배치되고,

화면이 좁아지면 자동으로 여러 줄로 변경된다.

<br>

## 8. FlowColumn과의 차이

| FlowRow | FlowColumn |
|----------|------------|
| 가로 방향으로 배치 | 세로 방향으로 배치 |
| 가로 공간 부족 시 다음 줄 이동 | 세로 공간 부족 시 다음 열 이동 |
| 태그, 버튼 목록에 적합 | 세로 방향 레이아웃에 적합 |

대부분의 경우 `FlowRow`를 더 자주 사용한다.

<br>

## 9. 사용 시 주의사항

### 1) Lazy 레이아웃이 아니다

`FlowRow`는 모든 자식을 한 번에 Composition 한다.

```kotlin
FlowRow {
    items.forEach {
        Item(it)
    }
}
```

아이템이 수백~수천 개라면 성능에 영향을 줄 수 있다.

많은 데이터를 보여줄 경우에는 `LazyColumn`이나 `LazyVerticalGrid`를 사용하는 것이 좋다.

<br>

### 2) 스크롤을 제공하지 않는다

`FlowRow` 자체에는 스크롤 기능이 없다.

필요하다면 `verticalScroll()`과 함께 사용한다.

```kotlin
Column(
    modifier = Modifier.verticalScroll(
        rememberScrollState()
    )
) {

    FlowRow {
        ...
    }
}
```

<br>

### 3) 너무 많은 데이터에는 적합하지 않다

검색 태그처럼 수십 개 정도는 적합하지만,

상품 목록이나 게시글 목록처럼 많은 데이터를 보여줄 경우에는 Lazy 계열 레이아웃이 더 적합하다.

<br>

## 10. 언제 사용하면 좋을까?

다음과 같은 UI에서 사용하면 좋다.

- 태그(Chip)
- 카테고리
- 검색 추천어
- 해시태그
- 필터 버튼
- 반응형 버튼 그룹
- 화면 크기에 따라 자동 줄바꿈이 필요한 UI

반대로 일반적인 리스트 화면이라면 `LazyColumn`이 더 적합하다.

<br>

## 11. 정리

- `FlowRow`는 공간이 부족하면 자동으로 다음 줄로 이동하는 레이아웃이다.
- `Row`와 달리 여러 줄을 자동으로 만들 수 있다.
- 태그, Chip, 버튼 그룹처럼 크기가 일정하지 않은 컴포저블을 배치할 때 많이 사용한다.
- `horizontalArrangement`와 `verticalArrangement`를 이용해 간격을 쉽게 조절할 수 있다.
- `maxItemsInEachRow`를 사용하면 한 줄에 배치할 최대 개수를 제한할 수 있다.
- Lazy 레이아웃이 아니므로 많은 데이터를 표시할 때는 `LazyColumn`이나 `LazyVerticalGrid`를 사용하는 것이 좋다.
