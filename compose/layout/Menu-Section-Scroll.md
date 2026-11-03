# 메뉴 > 하위메뉴 클릭 시 섹션 스크롤 이동

<br>

# 목차

1. 개요
2. 구현 방식 비교
3. 데이터 모델 설계
4. Compose - LazyColumn + animateScrollToItem
5. Compose - Column + verticalScroll
6. 스크롤 위치와 메뉴 선택 상태 동기화
7. 상단 고정 바(sticky) 오프셋 처리
8. MVI 구조로 묶기
9. 메뉴 UI - 2depth 펼침/접힘, Drawer
10. 다른 페이지의 특정 섹션으로 이동 (Navigation)
11. View 시스템 (NestedScrollView, RecyclerView)
12. WebView 앵커
13. 자주 하는 실수
14. 정리

<br>

# 1. 개요

설정 앱, 약관/가이드 화면, 문서형 화면에서 자주 나오는 패턴이다.

- 상단 또는 사이드의 메뉴(1depth)를 누르면 하위메뉴(2depth)가 펼쳐진다
- 하위메뉴를 누르면 본문에서 해당 섹션 위치로 스크롤된다
- 반대로 본문을 스크롤하면 현재 보이는 섹션에 맞춰 메뉴 선택 상태가 바뀐다 (양방향 동기화)
- 다른 화면에서 "특정 페이지의 특정 섹션"으로 바로 진입하는 경우도 있다 (딥링크 포함)

핵심은 세 가지다.

1. 섹션 id와 스크롤 위치(index 또는 y 좌표)를 어떻게 매핑할 것인가
2. 스크롤을 누가 실행할 것인가 (UI 이벤트 vs 상태)
3. 사용자 스크롤과 프로그램 스크롤이 충돌할 때 선택 상태를 어떻게 지킬 것인가

<br>

# 2. 구현 방식 비교

| 방식 | 사용처 | 핵심 API |
|---|---|---|
| LazyColumn | 섹션이 많거나 길 때 (권장) | `LazyListState.animateScrollToItem` |
| Column + verticalScroll | 섹션이 적고 정적인 화면 | `ScrollState.animateScrollTo`, `onGloballyPositioned` |
| bringIntoView | 간단하게 해당 뷰만 보이게 | `BringIntoViewRequester` |
| NestedScrollView | 기존 XML 화면 | `smoothScrollTo` |
| RecyclerView | 기존 리스트 화면 | `LinearSmoothScroller` |
| WebView | HTML 문서 | `#anchor`, `scrollIntoView` |

선택 기준은 단순하다.

- 섹션 수가 많거나 내용이 길면 LazyColumn (화면 밖 아이템은 composition 되지 않으므로 index로 이동)
- 섹션 3~5개 정도의 짧은 화면이면 Column + verticalScroll도 충분
- 새로 만드는 화면이면 Compose 우선

<br>

# 3. 데이터 모델 설계

메뉴와 섹션을 같은 id로 연결하는 것이 가장 중요하다.

```kotlin
data class DocSection(
    val id: String,
    val title: String,
    val content: String,
)

data class MenuItem(
    val id: String,
    val title: String,
    val sectionId: String? = null,        // null이면 1depth 그룹 메뉴
    val children: List<MenuItem> = emptyList(),
)
```

- 1depth 메뉴는 `children`을 가지고 펼침/접힘만 담당
- 2depth 메뉴는 `sectionId`로 본문 섹션과 연결
- 스크롤 index는 모델에 넣지 않고 `sections.indexOfFirst { it.id == sectionId }`로 계산 (데이터가 바뀌어도 어긋나지 않는다)

```kotlin
fun List<DocSection>.indexOfSection(sectionId: String): Int =
    indexOfFirst { it.id == sectionId }
```

<br>

# 4. Compose - LazyColumn + animateScrollToItem

가장 기본이 되는 형태다.

```kotlin
@Composable
fun SectionListScreen(
    sections: List<DocSection>,
    scrollTargetId: String?,
    onScrollConsumed: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(scrollTargetId) {
        val id = scrollTargetId ?: return@LaunchedEffect
        val index = sections.indexOfSection(id)
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
        onScrollConsumed()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = sections,
            key = { it.id },
        ) { section ->
            SectionItem(section)
        }
    }
}

@Composable
private fun SectionItem(section: DocSection) {
    Column(Modifier.padding(16.dp)) {
        Text(section.title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(section.content)
    }
}
```

포인트

- `key`를 반드시 지정한다 (데이터 변경 시 스크롤 위치가 엉뚱하게 튀는 것을 방지)
- `animateScrollToItem`은 suspend 함수라 LaunchedEffect 또는 `rememberCoroutineScope`에서 호출
- 즉시 이동은 `scrollToItem`, 애니메이션 이동은 `animateScrollToItem`
- 같은 섹션을 두 번 연속 눌러도 동작하게 하려면 `scrollTargetId`를 소비(consume)해서 null로 되돌려야 한다 (4번의 `onScrollConsumed`)

<br>

# 5. Compose - Column + verticalScroll

섹션이 적은 정적 화면에서 쓴다. 각 섹션의 y 좌표를 직접 기록해 둔다.

```kotlin
@Composable
fun StaticSectionScreen(
    sections: List<DocSection>,
    scrollTargetId: String?,
    onScrollConsumed: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val sectionTops = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(scrollTargetId) {
        val id = scrollTargetId ?: return@LaunchedEffect
        sectionTops[id]?.let { scrollState.animateScrollTo(it) }
        onScrollConsumed()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        sections.forEach { section ->
            Box(
                Modifier.onGloballyPositioned { coordinates ->
                    sectionTops[section.id] = coordinates.positionInParent().y.roundToInt()
                }
            ) {
                SectionItem(section)
            }
        }
    }
}
```

## BringIntoViewRequester 방식

좌표 계산 없이 "이 뷰를 화면에 보이게 해줘"라고 요청하는 방식이다.

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SectionWithRequester(
    section: DocSection,
    requester: BringIntoViewRequester,
) {
    Box(Modifier.bringIntoViewRequester(requester)) {
        SectionItem(section)
    }
}

// 호출
scope.launch { requester.bringIntoView() }
```

- 장점: 코드가 짧다
- 단점: "맨 위로 정렬"이 아니라 "화면에 보이게"만 보장한다. 이미 일부가 보이면 조금만 움직일 수 있어서, 섹션 시작점으로 정확히 맞추려면 좌표 방식이 낫다

<br>

# 6. 스크롤 위치와 메뉴 선택 상태 동기화

본문을 스크롤하면 현재 섹션에 맞춰 메뉴 하이라이트가 바뀌어야 한다.

```kotlin
@Composable
fun rememberCurrentSectionId(
    listState: LazyListState,
    sections: List<DocSection>,
): State<String?> {
    return remember(listState, sections) {
        derivedStateOf {
            val canScrollForward = listState.canScrollForward
            when {
                sections.isEmpty() -> null
                !canScrollForward -> sections.last().id   // 맨 끝에 닿으면 마지막 섹션
                else -> sections.getOrNull(listState.firstVisibleItemIndex)?.id
            }
        }
    }
}
```

- `derivedStateOf`를 써서 index가 바뀔 때만 recomposition 되게 한다 (스크롤 픽셀마다 갱신되지 않는다)
- 마지막 섹션이 짧으면 맨 위까지 올라오지 못해서 `firstVisibleItemIndex`가 마지막 섹션에 도달하지 못한다. `canScrollForward == false`일 때 마지막 섹션으로 보정한다

## 프로그램 스크롤 중 충돌 방지

메뉴를 눌러 애니메이션 스크롤하는 동안에는 중간에 지나가는 섹션들이 선택 상태를 덮어쓰면 안 된다.

```kotlin
var selectedId by remember { mutableStateOf<String?>(null) }
var isProgrammaticScroll by remember { mutableStateOf(false) }

// 메뉴 클릭
fun onMenuClick(sectionId: String) {
    selectedId = sectionId
    isProgrammaticScroll = true
    scope.launch {
        listState.animateScrollToItem(sections.indexOfSection(sectionId))
        isProgrammaticScroll = false
    }
}

// 사용자 스크롤 반영
LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemIndex }
        .distinctUntilChanged()
        .collect { index ->
            if (!isProgrammaticScroll) {
                selectedId = sections.getOrNull(index)?.id
            }
        }
}
```

- 클릭 시점에 선택 상태를 먼저 확정하고, 스크롤이 끝날 때까지 스크롤 기반 갱신을 막는다
- `snapshotFlow`는 Compose State를 Flow로 바꿔서 `distinctUntilChanged`, `debounce` 같은 연산자를 쓸 수 있게 해준다

<br>

# 7. 상단 고정 바(sticky) 오프셋 처리

상단에 TopAppBar나 탭이 고정되어 있으면 섹션 제목이 가려진다. 이동 위치에 오프셋을 준다.

```kotlin
val density = LocalDensity.current
val topOffsetPx = with(density) { 56.dp.roundToPx() }

listState.animateScrollToItem(
    index = index,
    scrollOffset = -topOffsetPx,
)
```

- LazyListState의 `scrollOffset`은 양수면 아이템이 위로 더 올라가고, 음수면 위쪽에 여백이 남는다
- 상단 여백이 필요하면 음수를 준다
- 또는 LazyColumn의 `contentPadding = PaddingValues(top = 56.dp)`를 쓰는 방법도 있다. 이 경우 `animateScrollToItem`이 padding을 고려해서 맞춰준다

`stickyHeader`로 섹션 제목을 고정하는 경우에는 헤더가 별도 아이템이므로 index 계산에 주의한다.

```kotlin
LazyColumn(state = listState) {
    sections.forEach { section ->
        stickyHeader(key = "header_${section.id}") {
            SectionHeader(section.title)
        }
        item(key = "body_${section.id}") {
            SectionBody(section.content)
        }
    }
}
```

- 이 구조에서는 섹션 하나가 아이템 2개를 차지한다. 헤더 index는 `sectionIndex * 2`가 된다
- index 매핑이 복잡해지면 id → index 맵을 별도로 만들어 둔다

```kotlin
val headerIndexMap = remember(sections) {
    sections.mapIndexed { i, s -> s.id to i * 2 }.toMap()
}
```

<br>

# 8. MVI 구조로 묶기

스크롤은 일회성 이벤트이므로 State가 아니라 Effect(SideEffect)로 처리하는 것이 안정적이다.

```kotlin
data class DocState(
    val menus: List<MenuItem> = emptyList(),
    val sections: List<DocSection> = emptyList(),
    val expandedMenuIds: Set<String> = emptySet(),
    val selectedSectionId: String? = null,
)

sealed interface DocIntent {
    data class ToggleMenu(val menuId: String) : DocIntent
    data class ClickSubMenu(val sectionId: String) : DocIntent
    data class VisibleSectionChanged(val sectionId: String) : DocIntent
}

sealed interface DocEffect {
    data class ScrollToSection(val sectionId: String) : DocEffect
}
```

```kotlin
@HiltViewModel
class DocViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(DocState())
    val state: StateFlow<DocState> = _state.asStateFlow()

    private val _effect = Channel<DocEffect>(Channel.BUFFERED)
    val effect: Flow<DocEffect> = _effect.receiveAsFlow()

    fun onIntent(intent: DocIntent) {
        when (intent) {
            is DocIntent.ToggleMenu -> _state.update {
                val expanded = it.expandedMenuIds
                it.copy(
                    expandedMenuIds = if (intent.menuId in expanded) expanded - intent.menuId
                    else expanded + intent.menuId
                )
            }

            is DocIntent.ClickSubMenu -> {
                _state.update { it.copy(selectedSectionId = intent.sectionId) }
                viewModelScope.launch {
                    _effect.send(DocEffect.ScrollToSection(intent.sectionId))
                }
            }

            is DocIntent.VisibleSectionChanged -> _state.update {
                it.copy(selectedSectionId = intent.sectionId)
            }
        }
    }
}
```

Screen에서는 Effect를 수집해서 스크롤을 실행한다.

```kotlin
@Composable
fun DocScreen(viewModel: DocViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is DocEffect.ScrollToSection -> {
                    val index = state.sections.indexOfSection(effect.sectionId)
                    if (index >= 0) {
                        isProgrammaticScroll = true
                        listState.animateScrollToItem(index)
                        isProgrammaticScroll = false
                    }
                }
            }
        }
    }

    LaunchedEffect(listState, state.sections) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (!isProgrammaticScroll) {
                    state.sections.getOrNull(index)?.let {
                        viewModel.onIntent(DocIntent.VisibleSectionChanged(it.id))
                    }
                }
            }
    }

    DocContent(
        state = state,
        listState = listState,
        onIntent = viewModel::onIntent,
    )
}
```

포인트

- ViewModel은 LazyListState 같은 UI 객체를 모른다. "어느 섹션으로 가라"만 Effect로 전달하고 실제 스크롤은 Composable이 한다
- Effect를 `Channel` + `receiveAsFlow`로 만들면 화면 회전 시 중복 실행되지 않고 한 번만 소비된다
- 4번에서 쓴 `scrollTargetId` 방식(State로 전달 후 consume)보다 MVI에서는 Effect 방식이 깔끔하다

<br>

# 9. 메뉴 UI - 2depth 펼침/접힘, Drawer

## 2depth 메뉴

```kotlin
@Composable
fun MenuList(
    menus: List<MenuItem>,
    expandedIds: Set<String>,
    selectedSectionId: String?,
    onToggle: (String) -> Unit,
    onSubClick: (String) -> Unit,
) {
    LazyColumn {
        menus.forEach { menu ->
            item(key = menu.id) {
                MenuRow(
                    title = menu.title,
                    expanded = menu.id in expandedIds,
                    onClick = { onToggle(menu.id) },
                )
            }
            items(
                items = menu.children,
                key = { it.id },
            ) { child ->
                AnimatedVisibility(visible = menu.id in expandedIds) {
                    SubMenuRow(
                        title = child.title,
                        selected = child.sectionId == selectedSectionId,
                        onClick = { child.sectionId?.let(onSubClick) },
                    )
                }
            }
        }
    }
}
```

- 접힌 하위메뉴는 `AnimatedVisibility`로 숨긴다. 숨겨진 아이템도 LazyColumn 아이템으로는 남아 있으므로 빈 공간이 생기지 않는지 확인한다
- 하위 아이템이 많으면 `if (expanded)` 조건으로 아이템 자체를 추가/제거하는 방식이 더 단순하다

```kotlin
menus.forEach { menu ->
    item(key = menu.id) { MenuRow(/* ... */) }
    if (menu.id in expandedIds) {
        items(menu.children, key = { it.id }) { child ->
            SubMenuRow(/* ... */)
        }
    }
}
```

## ModalNavigationDrawer와 조합

메뉴를 Drawer에 넣는 경우, 하위메뉴 클릭 후 Drawer를 닫고 스크롤한다.

```kotlin
@Composable
fun DocDrawerScreen(
    state: DocState,
    onIntent: (DocIntent) -> Unit,
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                MenuList(
                    menus = state.menus,
                    expandedIds = state.expandedMenuIds,
                    selectedSectionId = state.selectedSectionId,
                    onToggle = { onIntent(DocIntent.ToggleMenu(it)) },
                    onSubClick = { sectionId ->
                        onIntent(DocIntent.ClickSubMenu(sectionId))
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
        content = content,
    )
}
```

- Drawer 닫기와 스크롤이 동시에 진행되어도 문제없다. 순서가 중요하면 닫기 완료 후 Effect를 보내도록 바꾼다

<br>

# 10. 다른 페이지의 특정 섹션으로 이동 (Navigation)

"A 화면에서 메뉴를 누르면 B 페이지로 이동하면서 해당 섹션으로 스크롤"하는 경우다. 설정 검색 결과를 누르면 해당 항목으로 가는 것과 같다.

## NavGraph

```kotlin
composable(
    route = "doc/{pageId}?section={section}",
    arguments = listOf(
        navArgument("pageId") { type = NavType.StringType },
        navArgument("section") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
    ),
    deepLinks = listOf(
        navDeepLink { uriPattern = "myapp://doc/{pageId}?section={section}" }
    ),
) {
    DocScreen()
}

// 이동
navController.navigate("doc/terms?section=privacy")
```

## ViewModel에서 초기 섹션 처리

`SavedStateHandle`로 인자를 받고, 데이터 로딩이 끝난 뒤에 스크롤 Effect를 보낸다.

```kotlin
@HiltViewModel
class DocViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DocRepository,
) : ViewModel() {

    private val pageId: String = checkNotNull(savedStateHandle["pageId"])
    private val initialSectionId: String? = savedStateHandle["section"]

    init {
        viewModelScope.launch {
            val sections = repository.getSections(pageId)
            _state.update { it.copy(sections = sections) }

            initialSectionId?.let {
                _effect.send(DocEffect.ScrollToSection(it))
            }
        }
    }
    // ... 나머지는 8번과 동일
}
```

핵심은 "섹션 데이터가 로드되어 리스트에 그려진 뒤"에 스크롤해야 한다는 점이다.

- 데이터 로딩 전에 `animateScrollToItem`을 호출하면 index가 범위를 벗어나 무시된다
- 위 코드처럼 로딩 완료 후에 Effect를 보내면 안전하다
- Effect Channel은 버퍼가 있어서 Composable이 수집을 시작하기 전에 보낸 Effect도 유지된다 (`Channel.BUFFERED`)
- 화면 진입 시에는 애니메이션 없이 `scrollToItem`으로 바로 이동하는 쪽이 자연스럽다 (이동 후 애니메이션이 보이면 어색함)

## 도착한 섹션 강조(선택)

이동 후 잠깐 배경색을 깜빡여서 어디인지 알려주는 패턴이다.

```kotlin
val highlightColor by animateColorAsState(
    targetValue = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer
    else Color.Transparent,
    animationSpec = tween(durationMillis = 600),
    label = "sectionHighlight",
)

// 도착 후 1.5초 뒤 해제
LaunchedEffect(highlightedId) {
    if (highlightedId != null) {
        delay(1500)
        onHighlightEnd()
    }
}
```

<br>

# 11. View 시스템 (NestedScrollView, RecyclerView)

## NestedScrollView

```kotlin
fun NestedScrollView.smoothScrollToView(target: View, offsetPx: Int = 0) {
    val rect = Rect()
    target.getDrawingRect(rect)
    offsetDescendantRectToMyCoords(target, rect)
    smoothScrollTo(0, rect.top - offsetPx)
}

// 사용
binding.scrollView.smoothScrollToView(binding.sectionPrivacy)
```

- `target.top`은 바로 윗 부모 기준 좌표라서 섹션이 중첩된 레이아웃 안에 있으면 어긋난다
- `offsetDescendantRectToMyCoords`로 ScrollView 기준 좌표로 변환하는 것이 안전하다
- 레이아웃이 아직 그려지지 않았을 때(Fragment 진입 직후)는 `post { }` 안에서 호출한다

```kotlin
binding.scrollView.post {
    binding.scrollView.smoothScrollToView(binding.sectionPrivacy)
}
```

## RecyclerView

```kotlin
fun RecyclerView.smoothScrollToTop(position: Int) {
    val scroller = object : LinearSmoothScroller(context) {
        override fun getVerticalSnapPreference(): Int = SNAP_TO_START
    }
    scroller.targetPosition = position
    layoutManager?.startSmoothScroll(scroller)
}
```

- `smoothScrollToPosition`은 기본적으로 "화면에 보이게"만 해서 맨 위 정렬이 안 된다
- `SNAP_TO_START`를 지정한 `LinearSmoothScroller`를 쓰면 해당 아이템이 맨 위에 맞춰진다
- 즉시 이동 + 오프셋은 `(layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, offset)`

<br>

# 12. WebView 앵커

본문이 HTML인 경우다.

```kotlin
// URL 앵커
webView.loadUrl("file:///android_asset/terms.html#privacy")

// 페이지 로드 후 JS로 이동
webView.evaluateJavascript(
    "document.getElementById('privacy')?.scrollIntoView({ behavior: 'smooth' });",
    null,
)
```

- 페이지 로드가 끝난 뒤(`onPageFinished`)에 JS를 실행해야 한다
- 섹션 id가 HTML 요소 id와 일치해야 하므로 메뉴 모델의 `sectionId`와 맞춰 둔다

<br>

# 13. 자주 하는 실수

1. LazyColumn에서 `key`를 안 줘서 데이터 갱신 시 스크롤 위치가 튄다
2. 데이터 로딩 전에 스크롤을 호출해서 아무 일도 안 일어난다
3. 같은 메뉴를 두 번 눌러도 이동하지 않는다 (State 기반으로 전달하면서 consume을 안 한 경우)
4. 메뉴 클릭 후 애니메이션 중에 스크롤 기반 선택 갱신이 덮어써서 하이라이트가 깜빡인다
5. 마지막 섹션이 짧아서 클릭해도 맨 위에 도달하지 못하고, 선택 상태가 이전 섹션으로 돌아간다 (`canScrollForward` 보정 필요)
6. `stickyHeader`를 쓰면서 index 계산에 헤더 아이템을 빼먹는다
7. `onGloballyPositioned`로 좌표를 기록할 때 화면 회전/폰트 크기 변경 후 값이 갱신되지 않을 거라 착각한다 (레이아웃이 바뀌면 다시 호출되므로 자동 갱신됨)
8. ViewModel에서 `LazyListState`를 들고 있다가 메모리 누수/회전 시 상태 불일치가 생긴다 (State는 Composable에서 `rememberLazyListState`로 관리)
9. 스크롤 이동을 State 필드로 처리해서 화면 복귀 시 이미 소비한 이동이 다시 실행된다 (Effect로 처리)

<br>

# 정리

1. 메뉴와 섹션은 같은 `sectionId`로 연결하고, 스크롤 index는 데이터에서 계산한다
2. 섹션이 많으면 LazyColumn + `animateScrollToItem`, 적으면 Column + `verticalScroll` + `onGloballyPositioned` 좌표 기록
3. 스크롤 실행은 일회성 이벤트이므로 MVI에서는 Effect(`Channel` + `receiveAsFlow`)로 보내고 실제 스크롤은 Composable에서 한다
4. 본문 스크롤 → 메뉴 선택 동기화는 `snapshotFlow` + `distinctUntilChanged`, 프로그램 스크롤 중에는 갱신을 막는다
5. 마지막 섹션은 `canScrollForward`로 보정하고, 고정 바가 있으면 `scrollOffset` 또는 `contentPadding`으로 가림을 막는다
6. 다른 페이지의 섹션으로 진입할 때는 Navigation 인자(`section`)를 SavedStateHandle로 받고, 데이터 로딩이 끝난 뒤에 Effect를 보낸다
7. View 시스템은 `offsetDescendantRectToMyCoords`(ScrollView), `LinearSmoothScroller` + `SNAP_TO_START`(RecyclerView), `scrollIntoView`(WebView)
8. 자주 터지는 문제는 key 누락, 로딩 전 스크롤, 연속 클릭 무시, 선택 상태 충돌 네 가지
