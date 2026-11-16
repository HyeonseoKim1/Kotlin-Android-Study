# 탭 + HorizontalPager 연동: 탭 클릭과 스와이프 동기화

## 목차

1. 개요: 왜 "동기화"가 문제인가
2. 구성 요소
3. 가장 기본적인 구현
4. 동기화 원리: 진실의 원천은 PagerState
5. currentPage / settledPage / targetPage
6. 탭 클릭 처리: animateScrollToPage
7. 먼 페이지로 점프할 때의 깜빡임
8. 스와이프를 따라가는 인디케이터
9. ScrollableTabRow
10. Pager 옵션: 스와이프 제어, 프리로드, key
11. 페이지 안의 스크롤 상태 유지
12. MVI에서의 상태 관리
13. 딥링크 / 외부 이벤트로 탭 이동
14. 테스트
15. 흔한 실수
16. 전체 예제 (복붙용)

<br>

# 1. 개요: 왜 "동기화"가 문제인가

탭과 페이저는 화면에서 하나처럼 보이지만 상태는 두 군데에서 생긴다.

1. 탭을 누르면 "선택된 탭"이 바뀐다.
2. 페이지를 스와이프하면 "현재 페이지"가 바뀐다.

이 둘을 각각 따로 상태로 들고 있으면 반드시 어긋난다. 예를 들어 `var selectedTab by remember { mutableIntStateOf(0) }`를 두고 페이저는 `pagerState`를 쓰면, 스와이프했을 때 탭은 그대로이고 탭을 눌렀을 때 페이지는 그대로인 상황이 생긴다. 양쪽에서 서로를 갱신해주는 코드를 짜기 시작하면 무한 루프, 깜빡임, 중복 애니메이션이 따라온다.

해결 원칙은 하나다.

- 상태의 원천(source of truth)은 `PagerState` 하나만 둔다.
- 탭의 선택 표시는 `PagerState`에서 읽기만 한다.
- 탭 클릭은 "상태를 바꾸는 것"이 아니라 "페이저를 움직이는 명령"으로 처리한다.

이렇게 하면 데이터 흐름이 단방향이 된다.

```text
탭 클릭 -> pagerState.animateScrollToPage(i) -> pagerState 변경 -> 탭 선택 표시 갱신
스와이프 -> pagerState 변경 -> 탭 선택 표시 갱신
```

<br>

# 2. 구성 요소

| 구성 요소 | 역할 |
| --- | --- |
| `PagerState` | 현재 페이지, 오프셋, 스크롤 명령을 가진 상태 객체 |
| `rememberPagerState(pageCount = { n })` | PagerState 생성. 페이지 수는 람다로 전달 |
| `HorizontalPager` | 가로 스와이프 페이지 컨테이너 |
| `TabRow` / `ScrollableTabRow` | 탭 목록 컨테이너 |
| `Tab` | 탭 하나. `selected`, `onClick`, `text`로 구성 |
| `rememberCoroutineScope` | 탭 클릭 시 suspend 함수(`animateScrollToPage`) 호출용 |

`animateScrollToPage`와 `scrollToPage`는 suspend 함수라서 `onClick` 안에서 직접 호출할 수 없고 코루틴 스코프가 필요하다.

<br>

# 3. 가장 기본적인 구현

```kotlin
@Composable
fun SimpleTabPager(modifier: Modifier = Modifier) {
    val tabs = listOf("홈", "검색", "알림")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    text = { Text(title) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("${tabs[page]} 화면")
            }
        }
    }
}
```

핵심은 세 줄이다.

1. `selectedTabIndex = pagerState.currentPage`: 탭 상태를 pager에서 읽는다.
2. `selected = pagerState.currentPage == index`: 개별 탭도 마찬가지.
3. `onClick`에서 `animateScrollToPage(index)`: 상태를 직접 바꾸지 않고 pager를 움직인다.

이것만으로 탭 클릭과 스와이프가 양방향으로 동기화된다. 이후 챕터는 이 기본형에서 생기는 문제를 하나씩 다룬다.

<br>

# 4. 동기화 원리: 진실의 원천은 PagerState

## 4-1. 별도 상태를 두면 안 되는 이유

```kotlin
// 안티패턴
var selectedTab by remember { mutableIntStateOf(0) }
val pagerState = rememberPagerState(pageCount = { tabs.size })

// 탭 클릭 시: selectedTab = index + pager 이동
// 스와이프 시: LaunchedEffect(pagerState.currentPage) { selectedTab = pagerState.currentPage }
```

동작은 하지만 문제가 있다.

- 상태가 두 개라서 한 프레임 동안 서로 다른 값을 가질 수 있다.
- 탭 클릭으로 `selectedTab`을 바꾸고 pager가 애니메이션하는 동안 `currentPage`가 중간 값을 지나가며 `selectedTab`을 다시 덮어쓴다.
- 동기화 코드(LaunchedEffect)가 늘어날수록 디버깅이 어려워진다.

## 4-2. 읽기 전용 파생으로 해결

탭 쪽 값은 모두 `pagerState`에서 파생한다. 별도의 `selectedTab`이 필요 없다.

```kotlin
val selectedIndex = pagerState.currentPage
```

화면 밖(ViewModel 등)에서 "현재 탭"이 필요하면 상태를 복제하지 말고, pager의 값을 Flow로 내보낸다.

```kotlin
LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.settledPage }
        .distinctUntilChanged()
        .collect { page -> onTabSettled(page) }
}
```

<br>

# 5. currentPage / settledPage / targetPage

세 값은 같은 "페이지 번호"이지만 갱신 시점이 다르다.

| 값 | 의미 | 갱신 시점 |
| --- | --- | --- |
| `currentPage` | 스냅 위치에 가장 가까운 페이지 | 절반 이상 넘어가는 순간 바뀜 |
| `settledPage` | 스크롤이 완전히 멈춘 뒤의 페이지 | 애니메이션/플링이 끝난 뒤 |
| `targetPage` | 스크롤이 끝났을 때 도착할 것으로 예상되는 페이지 | 드래그/플링 속도에 따라 예측, `animateScrollToPage`는 즉시 목적지 |
| `currentPageOffsetFraction` | currentPage 기준 오프셋 비율 (-0.5 ~ 0.5) | 스크롤 중 매 프레임 |

용도별 선택 기준은 다음과 같다.

- 탭 선택 표시(색, 글자 굵기): `currentPage` 또는 `targetPage`
- 페이지 이동이 끝난 뒤 데이터를 로드하거나 로그를 남길 때: `settledPage`
- 인디케이터가 손가락을 따라가게 만들 때: `currentPage` + `currentPageOffsetFraction`

`currentPage`는 스와이프 도중에 바뀌기 때문에, 이 값으로 네트워크 요청 같은 무거운 작업을 트리거하면 사용자가 페이지를 반쯤 넘겼다가 되돌릴 때 불필요한 호출이 생긴다. 그런 용도에는 `settledPage`를 쓴다.

<br>

# 6. 탭 클릭 처리: animateScrollToPage

```kotlin
Tab(
    selected = pagerState.currentPage == index,
    onClick = {
        scope.launch { pagerState.animateScrollToPage(index) }
    },
    text = { Text(title) }
)
```

## 6-1. animateScrollToPage vs scrollToPage

| 함수 | 동작 | 사용처 |
| --- | --- | --- |
| `animateScrollToPage(page)` | 애니메이션으로 이동 | 일반적인 탭 클릭 |
| `scrollToPage(page)` | 애니메이션 없이 즉시 이동 | 초기 위치 복원, 접근성 설정(애니메이션 줄이기) |

## 6-2. 연속 클릭

탭을 빠르게 연속으로 누르면 `scope.launch`가 여러 번 실행된다. `PagerState`의 스크롤은 `MutatePriority` 기반이라 새 스크롤이 이전 스크롤을 취소하고 이어받으므로, 대부분 마지막으로 누른 탭으로 자연스럽게 이동한다. 별도의 Job 관리 코드는 보통 필요 없다.

<br>

# 7. 먼 페이지로 점프할 때의 깜빡임

0번 탭에서 4번 탭을 누르면 `animateScrollToPage(4)`는 1, 2, 3번 페이지를 지나간다. 이때 `currentPage`가 1, 2, 3으로 차례로 바뀌므로 탭 선택 표시가 중간 탭에서 깜빡인다. 또한 중간 페이지들이 실제로 컴포지션되어 불필요한 비용이 든다.

## 7-1. 선택 표시를 targetPage로 사용

`targetPage`는 `animateScrollToPage`를 호출한 순간 목적지로 바뀌므로 중간 탭이 깜빡이지 않는다.

```kotlin
val selectedIndex = pagerState.targetPage

TabRow(selectedTabIndex = selectedIndex) {
    tabs.forEachIndexed { index, title ->
        Tab(
            selected = selectedIndex == index,
            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
            text = { Text(title) }
        )
    }
}
```

주의할 점은 드래그 중에는 `targetPage`가 "예상 도착 페이지"라서, 사용자가 반쯤 넘기다가 놓는 속도에 따라 값이 바뀔 수 있다는 것이다. 일반적인 UI에서는 문제가 되지 않지만, 탭 선택 표시와 정확히 같은 타이밍을 원하면 `currentPage`를 유지하고 아래 방식으로 점프 거리를 줄인다.

## 7-2. 먼 점프는 이웃 페이지에서 시작

```kotlin
suspend fun PagerState.animateScrollToPageSafely(page: Int) {
    val distance = abs(page - currentPage)
    if (distance > 1) {
        // 목적지의 바로 옆 페이지까지 즉시 이동한 뒤 한 칸만 애니메이션
        val neighbor = if (page > currentPage) page - 1 else page + 1
        scrollToPage(neighbor)
    }
    animateScrollToPage(page)
}
```

```kotlin
onClick = { scope.launch { pagerState.animateScrollToPageSafely(index) } }
```

탭이 3~4개 이하인 화면에서는 7-1만으로 충분하고, 탭이 많거나 페이지가 무거울 때 7-2를 추가한다.

<br>

# 8. 스와이프를 따라가는 인디케이터

기본 `TabRow` 인디케이터는 `selectedTabIndex`가 바뀌면 그 위치로 애니메이션할 뿐, 스와이프 중에 손가락을 따라 연속적으로 움직이지 않는다. 손가락을 따라가는 인디케이터는 `currentPageOffsetFraction`으로 직접 계산해서 그린다.

## 8-1. 원리

- 인디케이터의 왼쪽 위치(left)와 너비(width)를 "현재 탭"과 "다음/이전 탭" 사이에서 선형 보간(lerp)한다.
- 보간 비율은 `abs(currentPageOffsetFraction)`.
- `currentPageOffsetFraction`이 양수이면 다음 페이지 쪽, 음수이면 이전 페이지 쪽이다.

## 8-2. 구현

```kotlin
@Composable
fun PagerTabIndicator(
    tabPositions: List<TabPosition>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 3.dp
) {
    val currentPage = pagerState.currentPage
    val fraction = pagerState.currentPageOffsetFraction

    val currentTab = tabPositions.getOrNull(currentPage) ?: return
    val neighborIndex = if (fraction >= 0f) currentPage + 1 else currentPage - 1
    val neighborTab = tabPositions.getOrNull(neighborIndex) ?: currentTab
    val progress = abs(fraction)

    val left = lerp(currentTab.left, neighborTab.left, progress)
    val width = lerp(currentTab.width, neighborTab.width, progress)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.BottomStart)
            .offset(x = left)
            .width(width)
            .height(height)
            .background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
    )
}
```

사용:

```kotlin
TabRow(
    selectedTabIndex = pagerState.currentPage,
    indicator = { tabPositions ->
        PagerTabIndicator(tabPositions = tabPositions, pagerState = pagerState)
    },
    divider = {}
) {
    // Tab들...
}
```

여기서 `lerp(Dp, Dp, Float)`는 `androidx.compose.ui.unit.lerp`를 import 해야 한다. 탭 클릭으로 이동하는 중에도 `currentPageOffsetFraction`이 변하므로 인디케이터가 부드럽게 미끄러진다.

## 8-3. 탭 글자 색 보간

인디케이터와 마찬가지로 탭 글자 색도 스와이프 진행도에 따라 바꾸고 싶다면 같은 방식으로 `lerp(Color, Color, fraction)`을 쓸 수 있다. 다만 대부분의 앱은 "선택/비선택" 이분법이면 충분하므로, 필요할 때만 추가한다.

<br>

# 9. ScrollableTabRow

탭이 많아 화면 너비를 넘으면 `ScrollableTabRow`를 쓴다. 이때 추가로 챙길 점은 두 가지다.

1. 선택된 탭이 화면 밖에 있으면 탭 행도 같이 스크롤되어야 한다. `ScrollableTabRow`는 `selectedTabIndex`가 바뀌면 해당 탭이 보이도록 자동으로 스크롤해준다.
2. `edgePadding`으로 양 끝 여백을 조절한다. 기본값이 커서 첫 탭이 안쪽으로 밀려 보일 수 있다.

```kotlin
ScrollableTabRow(
    selectedTabIndex = pagerState.currentPage,
    edgePadding = 16.dp,
    indicator = { tabPositions ->
        PagerTabIndicator(tabPositions, pagerState)
    },
    divider = {}
) {
    tabs.forEachIndexed { index, title ->
        Tab(
            selected = pagerState.currentPage == index,
            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
            text = { Text(title, maxLines = 1) }
        )
    }
}
```

`TabRow`는 모든 탭이 같은 너비로 화면을 나눠 갖고, `ScrollableTabRow`는 각 탭이 내용만큼의 너비를 가진다. 탭이 2~4개이고 글자가 짧으면 `TabRow`, 그 이상이거나 글자 길이가 제각각이면 `ScrollableTabRow`를 고른다.

Material3 최신 버전에서는 `PrimaryTabRow`, `SecondaryTabRow`, `PrimaryScrollableTabRow`, `SecondaryScrollableTabRow`로 분리되었고 기존 `TabRow`는 점차 대체되는 방향이다. 사용 중인 Material3 버전에서 deprecated 경고가 뜨면 같은 구조(`selectedTabIndex` + `Tab` 목록)로 새 컴포저블로 옮기면 된다. 단, 손가락을 따라가는 커스텀 인디케이터(8장)는 버전에 따라 `indicator` 시그니처가 다르므로 그대로 옮겨지지 않을 수 있다.

<br>

# 10. Pager 옵션: 스와이프 제어, 프리로드, key

```kotlin
HorizontalPager(
    state = pagerState,
    modifier = Modifier.weight(1f),
    userScrollEnabled = true,
    beyondViewportPageCount = 1,
    key = { page -> tabs[page].id }
) { page ->
    TabPage(tab = tabs[page])
}
```

## 10-1. userScrollEnabled

`false`로 두면 스와이프가 막히고 탭 클릭으로만 이동한다. 지도나 가로 스크롤 요소가 페이지 안에 있어 제스처가 충돌할 때 쓴다. 이 경우에도 `animateScrollToPage`는 정상 동작한다.

## 10-2. beyondViewportPageCount

기본값은 0이라 화면에 보이는 페이지만 컴포지션된다. 값을 1로 두면 좌우 한 페이지를 미리 컴포지션해서 스와이프 시작 시 렌더링 지연이 줄지만 그만큼 메모리와 초기 비용이 든다. 페이지가 무겁거나 네트워크 호출이 있으면 0을 유지하고 데이터는 ViewModel에서 미리 준비하는 편이 낫다.

## 10-3. key

탭 목록이 동적으로 바뀌거나(필터에 따라 탭이 추가/삭제) 페이지별 상태를 보존해야 하면 `key`를 지정한다. 지정하지 않으면 인덱스 기반이라 탭 순서가 바뀔 때 상태가 엉뚱한 페이지에 붙을 수 있다.

<br>

# 11. 페이지 안의 스크롤 상태 유지

탭을 오가면 페이지가 컴포지션에서 빠졌다가 다시 들어오면서 `LazyColumn`의 스크롤 위치가 초기화될 수 있다. 해결 방법은 두 가지다.

## 11-1. rememberLazyListState는 페이지 안에서

```kotlin
@Composable
fun TabPage(tab: Tab) {
    val listState = rememberLazyListState()
    LazyColumn(state = listState) { /* ... */ }
}
```

페이지가 컴포지션에 남아 있는 동안은 유지되지만, `beyondViewportPageCount` 범위를 벗어나 사라지면 `remember`가 날아간다. 위치까지 보존하려면 아래 방법을 쓴다.

## 11-2. 상태를 hoisting

각 탭의 `LazyListState`를 페이저 바깥에서 만들어 전달한다.

```kotlin
val listStates = remember(tabs) { tabs.map { LazyListState() } }

HorizontalPager(state = pagerState) { page ->
    TabPage(tab = tabs[page], listState = listStates[page])
}
```

화면 회전이나 프로세스 종료까지 복원하려면 `rememberSaveable(saver = LazyListState.Saver)`를 사용한다. 데이터 자체는 ViewModel이 들고 있으므로 스크롤 위치만 UI 상태로 관리하면 된다.

<br>

# 12. MVI에서의 상태 관리

MVI 구조에서 "선택된 탭"을 `UiState`에 넣고 싶은 유혹이 생기지만, 앞에서 본 것처럼 pager 상태와 이중 관리가 된다. 기준은 아래와 같다.

| 상태 | 위치 | 이유 |
| --- | --- | --- |
| 현재 페이지, 스크롤 오프셋 | `PagerState` (UI) | 프레임 단위로 바뀌는 UI 상태 |
| 탭 목록, 각 탭의 데이터 | `UiState` (ViewModel) | 비즈니스/데이터 상태 |
| 페이지가 정착한 뒤 필요한 동작 | `Intent`로 ViewModel에 전달 | 데이터 로드, 분석 로그 |
| 외부에서 탭 이동 요청 | `Effect`로 UI에 전달 | 일회성 명령 |

```kotlin
data class TabUiState(
    val tabs: List<TabItem> = emptyList(),
    val isLoading: Boolean = false
)

sealed interface TabIntent {
    data class PageSettled(val page: Int) : TabIntent
}

sealed interface TabEffect {
    data class ScrollToPage(val page: Int) : TabEffect
}
```

UI에서는 settled 시점만 ViewModel에 알린다.

```kotlin
@Composable
fun TabPagerScreen(viewModel: TabViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { state.tabs.size })
    val scope = rememberCoroutineScope()

    // 스크롤이 끝난 시점에만 Intent 전달 (스와이프 중간 값은 무시)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { viewModel.onIntent(TabIntent.PageSettled(it)) }
    }

    // 일회성 이동 명령 수신
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is TabEffect.ScrollToPage -> pagerState.animateScrollToPage(effect.page)
            }
        }
    }

    // TabRow + HorizontalPager ...
}
```

주의할 점은 `rememberPagerState(pageCount = { state.tabs.size })`에서 탭이 로드되기 전 `size`가 0이면 pager가 비어 있다가 늘어난다는 것이다. 탭 목록이 비어 있을 때는 로딩 UI를 보여주고, 탭이 준비된 뒤에 `TabRow`와 `HorizontalPager`를 그리는 것이 안전하다.

<br>

# 13. 딥링크 / 외부 이벤트로 탭 이동

알림이나 딥링크로 "두 번째 탭으로 진입"해야 하는 경우는 두 가지로 나뉜다.

## 13-1. 첫 진입 시 초기 페이지

```kotlin
val pagerState = rememberPagerState(
    initialPage = initialTabIndex,
    pageCount = { tabs.size }
)
```

초기 페이지는 처음 생성될 때만 적용된다. `rememberPagerState`는 내부적으로 `rememberSaveable`을 쓰므로 회전 후에는 마지막 위치가 복원된다.

## 13-2. 이미 화면이 떠 있을 때

`initialPage`를 바꿔도 반영되지 않는다. 12장처럼 Effect로 `animateScrollToPage`를 호출하거나, 값이 바뀔 때마다 이동시키는 `LaunchedEffect(targetTab)`을 쓴다.

```kotlin
LaunchedEffect(requestedTab) {
    requestedTab?.let { pagerState.animateScrollToPage(it) }
}
```

Navigation과 함께 쓸 때는 route의 인자(`?tab=1`)를 `initialPage`로 쓰는 방식이 가장 단순하다.

<br>

# 14. 테스트

Tab은 `selected` 상태가 시맨틱스에 노출되므로 `assertIsSelected()`로 검증할 수 있다.

```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun 탭을_클릭하면_해당_페이지로_이동한다() {
    composeTestRule.setContent { SimpleTabPager() }

    composeTestRule.onNodeWithText("검색").performClick()

    composeTestRule.onNodeWithText("검색").assertIsSelected()
    composeTestRule.onNodeWithText("검색 화면").assertIsDisplayed()
}

@Test
fun 스와이프하면_탭_선택이_함께_바뀐다() {
    composeTestRule.setContent { SimpleTabPager() }

    composeTestRule.onNodeWithText("홈 화면").performTouchInput { swipeLeft() }

    composeTestRule.onNodeWithText("검색").assertIsSelected()
}
```

애니메이션이 끝나기 전에 단언하면 불안정해질 수 있으므로, `composeTestRule.waitForIdle()`을 쓰거나 테스트에서 `mainClock`을 제어한다.

<br>

# 15. 흔한 실수

## 15-1. 탭 상태를 별도로 두기

`selectedTab`을 따로 `remember`로 들고 pager와 맞추려는 구조. 4장에서 본 것처럼 항상 어긋날 여지가 있다. `PagerState` 하나만 쓴다.

## 15-2. onClick에서 코루틴 없이 호출

`animateScrollToPage`는 suspend 함수다. `rememberCoroutineScope`로 얻은 `scope.launch`를 써야 한다. `LaunchedEffect` 안에서 클릭 이벤트를 처리하려고 하면 구조가 꼬인다.

## 15-3. currentPage로 무거운 작업 트리거

스와이프 도중 값이 바뀌므로 데이터 로드는 `settledPage`에서 한다.

## 15-4. pageCount를 고정값으로 전달

`rememberPagerState(pageCount = { tabs.size })`처럼 람다로 넘겨야 탭 개수가 바뀔 때 반영된다. 구버전 API(`pageCount` 파라미터를 `HorizontalPager`에 직접 전달)와 헷갈리지 않도록 사용 중인 Foundation 버전을 확인한다.

## 15-5. 페이지 안의 가로 스크롤과 충돌

페이지 안에 가로로 스크롤되는 `LazyRow`가 있으면 끝에 도달했을 때 pager로 스크롤이 넘어가는 것이 정상 동작이다. 이게 문제라면 해당 영역에서만 `userScrollEnabled`를 제한하거나 `nestedScroll`로 소비 방식을 조정한다.

## 15-6. 탭 개수가 많은데 TabRow 사용

글자가 줄바꿈되거나 잘린다. `ScrollableTabRow`로 바꾼다.

## 15-7. 인덱스 범위 초과

탭 목록이 비동기로 줄어들 때 `pagerState.currentPage`가 새 범위를 넘을 수 있다. 목록이 바뀔 때 `coerceIn`으로 보정하거나, 탭이 확정된 뒤에 UI를 그린다.

<br>

# 16. 전체 예제 (복붙용)

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

private val tabTitles = listOf("홈", "검색", "알림", "내 정보")

@Composable
fun TabPagerScreen(
    onPageSettled: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()

    // 스크롤이 완전히 끝난 시점에만 외부로 알림
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { onPageSettled(it) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            indicator = { tabPositions ->
                PagerTabIndicator(tabPositions = tabPositions, pagerState = pagerState)
            },
            divider = {}
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPageSafely(index) }
                    },
                    text = { Text(title, maxLines = 1) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            key = { page -> tabTitles[page] }
        ) { page ->
            TabPageContent(title = tabTitles[page])
        }
    }
}

@Composable
private fun TabPageContent(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$title 화면")
    }
}

@Composable
fun PagerTabIndicator(
    tabPositions: List<TabPosition>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 3.dp
) {
    val currentPage = pagerState.currentPage
    val fraction = pagerState.currentPageOffsetFraction

    val currentTab = tabPositions.getOrNull(currentPage) ?: return
    val neighborIndex = if (fraction >= 0f) currentPage + 1 else currentPage - 1
    val neighborTab = tabPositions.getOrNull(neighborIndex) ?: currentTab
    val progress = abs(fraction)

    val left = lerp(currentTab.left, neighborTab.left, progress)
    val width = lerp(currentTab.width, neighborTab.width, progress)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.BottomStart)
            .offset(x = left)
            .width(width)
            .height(height)
            .background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
    )
}

/** 먼 페이지로 이동할 때 중간 페이지를 건너뛰어 깜빡임/불필요한 컴포지션을 줄인다. */
suspend fun PagerState.animateScrollToPageSafely(page: Int) {
    if (abs(page - currentPage) > 1) {
        val neighbor = if (page > currentPage) page - 1 else page + 1
        scrollToPage(neighbor)
    }
    animateScrollToPage(page)
}
```

<br>

# 정리

1. 탭과 페이저의 동기화 문제는 상태가 두 군데에 생기는 데서 시작된다. 해결은 `PagerState`를 유일한 원천으로 두는 것이다.
2. 탭의 선택 표시는 `pagerState.currentPage`(또는 `targetPage`)에서 읽기만 하고, 탭 클릭은 `animateScrollToPage`로 페이저를 움직이는 명령으로 처리한다. 그러면 데이터 흐름이 단방향이 되어 양방향 동기화가 자연스럽게 된다.
3. `currentPage`는 절반을 넘기는 순간, `settledPage`는 멈춘 뒤, `targetPage`는 도착 예정 페이지다. 화면 표시에는 `currentPage`/`targetPage`, 데이터 로드나 로그에는 `settledPage`를 쓴다.
4. 먼 탭으로 점프할 때 중간 탭이 깜빡이면 `targetPage`를 선택 표시에 쓰거나, 목적지 이웃 페이지까지 `scrollToPage`로 건너뛴 뒤 한 칸만 애니메이션한다.
5. 스와이프를 따라가는 인디케이터는 `currentPageOffsetFraction`과 `TabPosition`을 lerp로 보간해서 직접 그린다.
6. 탭이 많으면 `ScrollableTabRow`, 동적 탭이면 `key`, 페이지별 스크롤 위치 보존이 필요하면 `LazyListState` hoisting을 쓴다.
7. MVI에서는 페이지 위치 같은 프레임 단위 UI 상태는 `PagerState`에 두고, 탭 데이터는 `UiState`, 정착 시점 알림은 `Intent`, 외부 이동 요청은 `Effect`로 나눈다.
8. 흔한 실수는 별도 `selectedTab` 상태 추가, 코루틴 없이 suspend 함수 호출, `currentPage`로 무거운 작업 트리거, 탭 목록 크기 변경 시 인덱스 범위 초과다.
