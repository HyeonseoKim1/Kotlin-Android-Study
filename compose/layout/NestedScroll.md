# NestedScroll

Nested Scroll은 부모와 자식 Composable이 하나의 스크롤 이벤트를 공유할 수 있도록 하는 Compose의 스크롤 시스템이다.

일반적인 스크롤은 하나의 Composable이 이벤트를 소비하지만, Nested Scroll은 부모와 자식이 이벤트를 순차적으로 처리할 수 있다.

Material3의 TopAppBar, BottomSheet, PullToRefresh 등 다양한 컴포넌트가 Nested Scroll을 기반으로 구현되어 있다.

<br>

## 목차

1. NestedScroll이란?
2. NestedScroll이 필요한 이유
3. Compose의 스크롤 구조
4. Nested Scroll 이벤트 전달 과정
5. NestedScrollConnection
6. nestedScroll Modifier
7. Offset을 반환하는 이유
8. NestedScrollDispatcher
9. remember를 사용하는 이유

<br>

## 1. NestedScroll이란?

Compose에서 스크롤 가능한 컴포넌트는 자신의 스크롤 이벤트를 직접 처리한다.

```kotlin
LazyColumn { ... }

Column(
    modifier = Modifier.verticalScroll(rememberScrollState())
)
```

이 구조에서는 부모와 자식이 서로의 스크롤 상태를 알 수 없다.

Nested Scroll은 이러한 한계를 해결하기 위해 만들어진 시스템으로, 하나의 스크롤 이벤트를 여러 Composable이 순차적으로 처리할 수 있도록 한다.

대표적인 사용 사례는 다음과 같다.

- Collapsing Toolbar
- BottomSheet
- PullToRefresh
- Pager 내부 LazyColumn
- 부모와 자식이 모두 스크롤 가능한 화면

Nested Scroll은 새로운 스크롤을 만드는 API가 아니라 **기존 스크롤 이벤트를 전달하는 시스템**이다.

<br>

## 2. NestedScroll이 필요한 이유

다음과 같은 화면을 생각해보자.

```
TopAppBar

LazyColumn
```

사용자가 위로 스크롤하면 원하는 동작은 다음과 같다.

1. TopAppBar가 먼저 접힌다.
2. AppBar가 모두 접히면 LazyColumn이 스크롤된다.

반대로 아래로 스크롤하면

1. LazyColumn이 먼저 내려온다.
2. 리스트가 시작 위치에 도달하면 AppBar가 다시 펼쳐진다.

이처럼 하나의 스크롤 이벤트를 여러 UI가 함께 처리해야 하는 경우 Nested Scroll이 필요하다.

Nested Scroll이 없다면 LazyColumn만 스크롤되고 AppBar는 스크롤 이벤트를 전달받지 못한다.

<br>

## 3. Compose의 스크롤 구조

Compose에는 다양한 스크롤 관련 API가 존재한다.

| API | 역할 |
|------|------|
| verticalScroll | Column 등의 일반 스크롤 |
| horizontalScroll | Row 등의 가로 스크롤 |
| LazyColumn | Lazy 기반 세로 리스트 |
| LazyRow | Lazy 기반 가로 리스트 |
| scrollable | 직접 스크롤 구현 |
| draggable | 드래그만 처리 |
| nestedScroll | 스크롤 이벤트 전달 |

이 API들은 역할이 다르다.

`verticalScroll()`과 `LazyColumn`은 실제 스크롤을 수행하는 API이다.

반면 `nestedScroll()`은 스크롤 이벤트를 부모와 자식 사이에서 전달하는 역할만 수행한다.

따라서 `nestedScroll()`만 추가한다고 화면이 스크롤되지는 않는다.

반드시 `LazyColumn`, `verticalScroll()` 등의 스크롤 가능한 컴포넌트와 함께 사용해야 한다.

<br>

## 4. Nested Scroll 이벤트 전달 과정

Nested Scroll은 하나의 스크롤 이벤트를 여러 단계로 전달한다.

```
사용자 드래그

↓

Parent (onPreScroll)

↓

Child Scroll

↓

Parent (onPostScroll)
```

이 과정은 크게 세 단계로 이해하면 된다.

### 1) Pre Scroll

부모가 가장 먼저 스크롤 이벤트를 전달받는다.

Toolbar 높이 변경이나 Header 애니메이션처럼 자식보다 먼저 처리해야 하는 작업을 수행한다.

### 2) Child Scroll

실제 LazyColumn이나 Scrollable이 스크롤을 수행한다.

필요한 만큼 이벤트를 소비하고 남은 값을 부모에게 전달한다.

### 3) Post Scroll

자식이 소비하지 않은 스크롤 이벤트를 부모가 다시 처리한다.

Overscroll 효과나 추가 애니메이션을 구현할 때 주로 사용된다.

<br>

## 5. NestedScrollConnection

Nested Scroll 이벤트를 처리하는 핵심 인터페이스이다.

`nestedScroll()` Modifier는 이벤트를 연결하는 역할만 수행하며, 실제 처리 로직은 `NestedScrollConnection`에서 구현한다.

```kotlin
val connection = object : NestedScrollConnection {

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        return Offset.Zero
    }

    override suspend fun onPreFling(
        available: Velocity
    ): Velocity {
        return Velocity.Zero
    }

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity
    ): Velocity {
        return Velocity.Zero
    }
}
```

각 함수의 역할은 다음과 같다.

| 함수 | 호출 시점 |
|------|-----------|
| onPreScroll | 자식이 스크롤하기 전 |
| onPostScroll | 자식이 스크롤한 후 |
| onPreFling | Fling 시작 전 |
| onPostFling | Fling 종료 후 |

실무에서는 대부분 `onPreScroll()`과 `onPostScroll()`을 사용한다.

<br>

### onPreScroll()

자식 Composable이 스크롤하기 전에 호출된다.

대표적인 사용 사례는 다음과 같다.

- Collapsing Toolbar
- Header 높이 변경
- BottomSheet 위치 변경

```kotlin
override fun onPreScroll(
    available: Offset,
    source: NestedScrollSource
): Offset {

    toolbarOffset += available.y

    return Offset.Zero
}
```

<br>

### onPostScroll()

자식이 스크롤을 처리한 이후 호출된다.

```kotlin
override fun onPostScroll(
    consumed: Offset,
    available: Offset,
    source: NestedScrollSource
): Offset
```

여기서 전달되는 값은 두 가지이다.

| 값 | 의미 |
|----|------|
| consumed | 자식이 소비한 스크롤 |
| available | 아직 소비되지 않은 스크롤 |

부모는 `available`을 이용해 남은 스크롤 이벤트를 처리할 수 있다.

<br>

## 6. nestedScroll Modifier

Nested Scroll 시스템을 Compose 트리에 연결하는 Modifier이다.

```kotlin
Modifier.nestedScroll(connection)
```

일반적으로 최상위 레이아웃에 적용한다.

```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .nestedScroll(connection)
)
```

`nestedScroll()` 아래에 위치한 모든 스크롤 가능한 Composable은 해당 Connection과 이벤트를 주고받는다.

중요한 점은 `nestedScroll()`이 스크롤을 생성하는 API는 아니라는 것이다.

실제 스크롤은 `LazyColumn`, `LazyRow`, `verticalScroll()` 등이 담당한다.

<br>

## 7. Offset을 반환하는 이유

`onPreScroll()`과 `onPostScroll()`은 `Offset`을 반환한다.

```kotlin
override fun onPreScroll(
    available: Offset,
    source: NestedScrollSource
): Offset
```

반환값은 **부모가 소비한 스크롤 양**을 의미한다.

```kotlin
return Offset.Zero
```

부모가 아무것도 소비하지 않는다.

```kotlin
return Offset(
    x = 0f,
    y = available.y
)
```

부모가 모든 스크롤을 소비한다.

이 반환값을 기반으로 Compose는 남은 스크롤을 자식에게 전달한다.

잘못된 Offset을 반환하면 스크롤 충돌이나 움직이지 않는 문제가 발생할 수 있다.

<br>

## 8. NestedScrollDispatcher

`NestedScrollConnection`이 이벤트를 **받는 역할**이라면,

`NestedScrollDispatcher`는 이벤트를 **보내는 역할**을 담당한다.

일반적인 `LazyColumn`이나 `verticalScroll()`은 내부에서 Dispatcher를 관리하므로 직접 사용할 일이 거의 없다.

직접 커스텀 스크롤 컴포넌트를 구현하거나 새로운 Gesture를 만들 때 Dispatcher를 사용한다.

```kotlin
val dispatcher = remember {
    NestedScrollDispatcher()
}
```

Dispatcher는 Connection과 함께 동작하며 스크롤 이벤트를 부모 방향으로 전달한다.

실무에서는 직접 사용하는 경우보다 라이브러리 내부 구현에서 더 자주 볼 수 있다.

<br>

## 9. remember를 사용하는 이유

`NestedScrollConnection`은 일반적으로 `remember`와 함께 생성한다.

```kotlin
val connection = remember {
    object : NestedScrollConnection {}
}
```

Composable은 Recomposition이 발생할 때마다 다시 실행된다.

`remember`를 사용하지 않으면 Recomposition마다 새로운 Connection 객체가 생성된다.

```kotlin
val connection = object : NestedScrollConnection {}
```

객체가 계속 변경되면 Modifier도 변경된 것으로 판단되어 불필요한 작업이 발생할 수 있다.

Connection은 상태를 유지하며 계속 사용하는 객체이므로 `remember`를 사용하는 것이 일반적이다.

또한 Connection 내부에서 Compose State를 사용하는 경우에도 동일한 객체를 유지하는 편이 예측 가능한 동작을 만들 수 있다.

<br>


## 10. Collapsing Toolbar 구현

Nested Scroll이 가장 많이 사용되는 사례는 Collapsing Toolbar이다.

Material3의 `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()`와 `enterAlwaysScrollBehavior()`도 Nested Scroll을 기반으로 구현되어 있다.

구조는 다음과 같다.

```
TopAppBar

↓

LazyColumn
```

스크롤 이벤트는 다음 순서로 처리된다.

1. 사용자가 위로 스크롤한다.
2. `onPreScroll()`에서 AppBar 높이를 변경한다.
3. 남은 스크롤을 LazyColumn이 소비한다.
4. AppBar가 최소 높이에 도달하면 리스트만 스크롤된다.

반대로 아래로 스크롤하면 리스트가 먼저 이동하고, 시작 위치에 도달하면 AppBar가 다시 펼쳐진다.

대표적인 사용 화면

- Gmail
- Play Store
- Google Photos
- 대부분의 Material3 화면

<br>

### Material3 사용 예시

```kotlin
val scrollBehavior =
    TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

Scaffold(
    modifier = Modifier.nestedScroll(
        scrollBehavior.nestedScrollConnection
    ),
    topBar = {
        LargeTopAppBar(
            title = {
                Text("Nested Scroll")
            },
            scrollBehavior = scrollBehavior
        )
    }
) {
    LazyColumn(
        contentPadding = it
    ) {

    }
}
```

별도의 `NestedScrollConnection`을 직접 구현하지 않아도 Material3에서 제공하는 구현을 사용할 수 있다.

<br>

## 11. BottomSheet와 Nested Scroll

BottomSheet 내부에는 대부분 스크롤 가능한 리스트가 존재한다.

```
BottomSheet

↓

LazyColumn
```

사용자가 위로 드래그하면

1. BottomSheet가 먼저 이동한다.
2. BottomSheet가 최종 위치에 도달한다.
3. LazyColumn이 스크롤된다.

아래로 드래그하면 반대 순서로 동작한다.

Nested Scroll이 없다면 BottomSheet와 LazyColumn이 서로 다른 스크롤을 처리하여 자연스럽지 않은 동작이 발생할 수 있다.

Material3의 `ModalBottomSheet`도 내부적으로 Nested Scroll을 사용한다.

<br>

## 12. PullToRefresh

Pull To Refresh 역시 Nested Scroll을 활용하는 대표적인 기능이다.

```
PullToRefresh

↓

LazyColumn
```

리스트가 시작 위치에 있을 때 아래로 당기면

1. 부모가 드래그 이벤트를 먼저 처리한다.
2. Refresh Indicator가 표시된다.
3. 일정 거리 이상 당기면 새로고침이 시작된다.
4. 리스트가 스크롤 가능한 상태라면 LazyColumn이 먼저 이벤트를 소비한다.

Nested Scroll 덕분에 Refresh와 리스트 스크롤이 충돌하지 않는다.

<br>

## 13. Pager와 Nested Scroll

Pager 내부에 LazyColumn이 있는 구조도 자주 사용된다.

```
HorizontalPager

↓

LazyColumn
```

Pager는 가로 스크롤을 처리하고,

LazyColumn은 세로 스크롤을 처리한다.

방향이 다르기 때문에 대부분 충돌하지 않지만,

대각선 드래그나 Gesture가 복잡한 화면에서는 Nested Scroll을 통해 이벤트를 조정하기도 한다.

실제 사례

- Play Store
- YouTube
- Instagram 프로필 화면

<br>

## 14. scrollable과의 차이

| API | 역할 |
|------|------|
| scrollable | 스크롤 로직 직접 구현 |
| nestedScroll | 스크롤 이벤트 전달 |

`scrollable`은 Gesture를 받아 스크롤을 직접 구현할 때 사용한다.

```kotlin
Modifier.scrollable(
    state = state,
    orientation = Orientation.Vertical
)
```

반면 `nestedScroll`은 이미 발생한 스크롤 이벤트를 부모와 자식 사이에서 전달하는 역할을 수행한다.

두 API는 함께 사용할 수도 있다.

<br>

## 15. verticalScroll과의 차이

| verticalScroll | nestedScroll |
|---------------|--------------|
| 직접 스크롤한다. | 이벤트를 전달한다. |
| ScrollState 필요 | Connection 필요 |
| 단독으로 사용 가능 | 스크롤 가능한 Composable와 함께 사용 |

`verticalScroll()`은 실제 화면을 이동시키는 API이다.

```kotlin
Column(
    modifier = Modifier.verticalScroll(
        rememberScrollState()
    )
)
```

반면

```kotlin
Modifier.nestedScroll(connection)
```

은 스크롤 이벤트만 연결한다.

따라서 `nestedScroll()`만 적용해도 화면은 스크롤되지 않는다.

<br>

## 16. Material3 내부 구현

Material3의 TopAppBar는 `NestedScrollConnection`을 직접 구현한다.

대표적인 Scroll Behavior는 다음과 같다.

| Behavior | 특징 |
|----------|------|
| pinnedScrollBehavior | 항상 고정 |
| enterAlwaysScrollBehavior | 아래로 스크롤하면 바로 표시 |
| exitUntilCollapsedScrollBehavior | 최소 높이까지 접힘 |

모든 Scroll Behavior는

```kotlin
scrollBehavior.nestedScrollConnection
```

을 제공한다.

Scaffold에서 해당 Connection을 연결하면 AppBar와 LazyColumn이 자동으로 연동된다.

<br>

## 17. 성능 고려사항

### Connection은 remember 사용

```kotlin
val connection = remember {

    object : NestedScrollConnection {}
}
```

Recomposition마다 새로운 객체를 생성하지 않도록 한다.

<br>

### 불필요한 State 변경 최소화

`onPreScroll()`은 사용자가 스크롤하는 동안 매우 자주 호출된다.

State를 과도하게 변경하면 Recomposition 횟수가 증가하여 성능에 영향을 줄 수 있다.

가능하면 필요한 값만 변경하도록 구현하는 것이 좋다.

<br>

### 무거운 작업 수행 금지

스크롤 이벤트는 프레임마다 호출될 수 있다.

다음과 같은 작업은 수행하지 않는 것이 좋다.

- 네트워크 요청
- 데이터베이스 접근
- 복잡한 계산
- Bitmap 처리

이러한 작업은 스크롤 종료 후 별도의 Coroutine에서 수행하는 것이 적절하다.

<br>

## 18. 자주 하는 실수

### nestedScroll만 추가하고 스크롤이 되지 않는 경우

`nestedScroll()`은 이벤트를 전달하는 Modifier이다.

실제 스크롤을 수행하는 `LazyColumn`이나 `verticalScroll()`이 함께 있어야 한다.

<br>

### Offset을 모두 소비하는 경우

```kotlin
return available
```

를 항상 반환하면 자식이 스크롤 이벤트를 전달받지 못한다.

실제로 소비한 양만 반환해야 한다.

<br>

### remember를 사용하지 않는 경우

```kotlin
val connection =
    object : NestedScrollConnection {}
```

Recomposition마다 새로운 Connection이 생성된다.

Connection은 `remember`를 사용하여 재사용하는 것이 일반적이다.

<br>

### Parent에 Modifier를 연결하지 않는 경우

`nestedScroll()`은 부모 레이아웃에 연결하는 것이 일반적이다.

```kotlin
Scaffold(
    modifier = Modifier.nestedScroll(connection)
)
```

잘못된 위치에 적용하면 이벤트가 전달되지 않을 수 있다.

<br>

## 19. 언제 사용하는 것이 좋을까?

다음과 같은 화면이라면 Nested Scroll을 고려할 수 있다.

- AppBar가 접히는 화면
- Header가 함께 움직이는 화면
- BottomSheet 내부 리스트
- Pull To Refresh
- 부모와 자식이 모두 스크롤 가능한 화면
- 여러 스크롤 영역이 자연스럽게 연결되어야 하는 화면

단순한 `LazyColumn`이나 `Column`만 사용하는 화면에서는 사용할 필요가 없다.

<br>

# 정리

- Nested Scroll은 부모와 자식이 하나의 스크롤 이벤트를 공유하는 시스템이다.
- `nestedScroll()`은 이벤트를 연결하는 Modifier이며, 실제 스크롤은 `LazyColumn`, `verticalScroll()` 등이 수행한다.
- 핵심 인터페이스는 `NestedScrollConnection`이다.
- `onPreScroll()`은 자식보다 먼저, `onPostScroll()`은 자식 이후에 호출된다.
- 반환하는 `Offset`은 부모가 소비한 스크롤 양을 의미한다.
- Material3의 TopAppBar, BottomSheet, PullToRefresh는 Nested Scroll을 기반으로 구현되어 있다.
- `NestedScrollConnection`은 `remember`를 사용하여 재사용하는 것이 일반적이다.
- 단순한 스크롤 화면보다 여러 스크롤 영역이 함께 동작하는 화면에서 가장 큰 효과를 발휘한다.
