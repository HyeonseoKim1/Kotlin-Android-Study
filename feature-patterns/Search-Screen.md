# 검색 화면 (최근 검색어, 자동완성, debounce, 결과 하이라이트)

<br>

# 목차

1. 개요
2. 화면 상태 설계
3. 전체 구조와 데이터 흐름
4. 최근 검색어 - Room
5. 자동완성 - API + debounce
6. 검색 결과 - Paging3
7. ViewModel (MVI)
8. 검색창 UI (TextField, IME, 포커스)
9. 화면 조립 (최근 / 자동완성 / 결과 분기)
10. 검색어 하이라이트
11. 키보드와 스크롤 처리
12. 테스트
13. 자주 하는 실수
14. 정리

<br>

# 1. 개요

거의 모든 앱에 들어가는 화면이고, 단순해 보여도 신경 쓸 게 많다.

- 입력 중에는 자동완성 추천어를 보여준다 (매 글자마다 API를 때리면 안 된다 → debounce)
- 입력 전에는 최근 검색어를 보여준다 (저장, 개별 삭제, 전체 삭제, 개수 제한)
- 검색 실행 시 결과 목록을 보여준다 (페이징, 로딩/에러/빈 결과 처리)
- 결과와 추천어에서 입력한 검색어를 강조 표시한다
- 키보드 검색 버튼, 포커스, 스크롤 시 키보드 숨김 같은 입력 UX를 챙긴다

한 화면이 세 가지 모드를 오간다.

| 모드 | 조건 | 보여주는 것 |
|---|---|---|
| 최근 검색어 | 입력이 비어 있음 | 최근 검색어 목록 |
| 자동완성 | 입력 중 (아직 검색 안 함) | 추천 검색어 목록 |
| 결과 | 검색 실행됨 | 페이징 결과 목록 |

<br>

# 2. 화면 상태 설계

```kotlin
data class SearchState(
    val isSearched: Boolean = false,          // true면 결과 모드
    val recentKeywords: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
)

sealed interface SearchIntent {
    data class QueryChanged(val text: String) : SearchIntent
    data object ClearQuery : SearchIntent
    data object SearchFieldFocused : SearchIntent
    data class Submit(val keyword: String) : SearchIntent   // 키보드 검색, 최근/추천어 클릭 모두 이걸로
    data class DeleteRecent(val keyword: String) : SearchIntent
    data object ClearRecent : SearchIntent
}

sealed interface SearchEffect {
    data object ClearFocus : SearchEffect     // 키보드 내리고 포커스 해제
}

data class SearchItem(
    val id: Long,
    val title: String,
    val description: String,
)
```

설계 포인트

- 검색어(`query`)는 `SearchState`에 넣지 않고 ViewModel에서 Compose State(`mutableStateOf`)로 따로 들고 있는다. 이유는 8번의 "한글 입력 주의"에서 설명한다
- 검색 결과는 `PagingData`라서 State에 넣지 않고 별도 `Flow`로 노출한다
- 화면 모드는 `isSearched`와 `query.isBlank()` 조합으로 결정한다 (모드 enum을 따로 만들지 않아도 된다)
- 최근 검색어 클릭, 추천어 클릭, 키보드 검색 버튼은 전부 `Submit(keyword)` 하나로 처리해서 로직을 한 곳에 모은다

<br>

# 3. 전체 구조와 데이터 흐름

```
[TextField 입력]
     |
QueryChanged ──> query(State, TextField 바인딩)
     |
     └──> queryFlow(StateFlow) ──> debounce(300) ──> distinct ──> flatMapLatest(API) ──> suggestions

[Submit]
     |
     ├──> 최근 검색어 저장 (Room upsert)
     ├──> submittedQuery(StateFlow) ──> flatMapLatest(Pager) ──> cachedIn ──> results
     └──> isSearched = true, ClearFocus Effect

[Room Flow] ──> recentKeywords (실시간 반영)
```

- 입력용(`query`, TextField 바인딩)과 파이프라인용(`queryFlow`, debounce 대상)을 분리한다
- 최근 검색어는 Room의 Flow를 구독하기만 하면 저장/삭제가 자동으로 화면에 반영된다
- 검색 결과는 `submittedQuery`가 바뀔 때마다 `flatMapLatest`로 새 Pager로 교체한다

<br>

# 4. 최근 검색어 - Room

## Entity / DAO

```kotlin
@Entity(tableName = "recent_search")
data class RecentSearchEntity(
    @PrimaryKey val keyword: String,     // 같은 검색어는 한 줄만 유지 (upsert로 시간만 갱신)
    val searchedAt: Long,
)

@Dao
interface RecentSearchDao {

    @Query("SELECT * FROM recent_search ORDER BY searchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentSearchEntity>>

    @Upsert
    suspend fun upsert(entity: RecentSearchEntity)

    @Query("DELETE FROM recent_search WHERE keyword = :keyword")
    suspend fun delete(keyword: String)

    @Query("DELETE FROM recent_search")
    suspend fun deleteAll()

    // 최근 N개만 남기고 오래된 것 삭제
    @Query(
        """
        DELETE FROM recent_search
        WHERE keyword NOT IN (
            SELECT keyword FROM recent_search ORDER BY searchedAt DESC LIMIT :keep
        )
        """
    )
    suspend fun trim(keep: Int)
}
```

- `keyword`를 PrimaryKey로 잡으면 같은 검색어를 다시 검색했을 때 `@Upsert`가 시간만 갱신해서 목록 맨 위로 올라온다 (중복 행이 생기지 않는다)
- 조회는 `LIMIT`으로 화면에 보여줄 개수만, 저장 시에는 `trim`으로 DB 크기를 제한한다
- `@Upsert`는 Room 2.5.0 이상에서 사용 가능하다

## Repository

```kotlin
class RecentSearchRepository @Inject constructor(
    private val dao: RecentSearchDao,
) {
    fun observeRecent(limit: Int = DISPLAY_COUNT): Flow<List<String>> =
        dao.observeRecent(limit).map { list -> list.map { it.keyword } }

    suspend fun save(keyword: String) {
        dao.upsert(RecentSearchEntity(keyword, System.currentTimeMillis()))
        dao.trim(MAX_COUNT)
    }

    suspend fun delete(keyword: String) = dao.delete(keyword)

    suspend fun clear() = dao.deleteAll()

    companion object {
        private const val DISPLAY_COUNT = 10
        private const val MAX_COUNT = 30
    }
}
```

참고

- 검색어가 단순한 문자열 목록뿐이고 Room까지는 과하다 싶으면 DataStore에 `Preferences`로 저장하는 방법도 있다. 정렬/개수 제한/개별 삭제를 직접 구현해야 해서 목록형 데이터는 Room이 편하다
- 검색 기록 저장 on/off 옵션을 제공하는 앱이 많다. 설정값은 DataStore에 두고, 꺼져 있으면 `save()`를 호출하지 않으면 된다

<br>

# 5. 자동완성 - API + debounce

## API

```kotlin
interface SearchApi {

    @GET("search/suggestions")
    suspend fun getSuggestions(
        @Query("q") query: String,
    ): SuggestionResponse

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): SearchResponse
}

@Serializable
data class SuggestionResponse(val suggestions: List<String>)
```

## Repository 인터페이스

```kotlin
interface SearchRepository {
    suspend fun getSuggestions(query: String): List<String>
    fun searchPaging(query: String): Flow<PagingData<SearchItem>>
}
```

## debounce 파이프라인

```kotlin
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
private fun observeSuggestions() {
    queryFlow
        .debounce(300)                      // 입력이 300ms 멈췄을 때만 통과
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { keyword ->          // 새 입력이 오면 이전 요청은 취소
            if (keyword.isBlank() || _state.value.isSearched) {
                flowOf(emptyList())
            } else {
                flow { emit(searchRepository.getSuggestions(keyword)) }
                    .catch { emit(emptyList()) }
            }
        }
        .onEach { list -> _state.update { it.copy(suggestions = list) } }
        .launchIn(viewModelScope)
}
```

각 연산자의 역할

| 연산자 | 역할 |
|---|---|
| `debounce(300)` | 타이핑 중에는 요청 안 보냄 (한글은 글자 조합이 느려서 300ms 정도가 무난) |
| `map { trim() }` | 앞뒤 공백만 다른 입력을 같은 입력으로 취급 |
| `distinctUntilChanged()` | 같은 검색어로 중복 요청 방지 |
| `flatMapLatest` | 이전 요청이 아직 진행 중이면 취소하고 최신 입력만 처리 (오래된 응답이 나중에 도착해서 덮어쓰는 문제 방지) |
| `catch { emit(emptyList()) }` | 자동완성 실패는 에러 화면 없이 조용히 빈 목록 처리 |

- `debounce`는 `@FlowPreview`, `flatMapLatest`는 `@ExperimentalCoroutinesApi`라서 opt-in이 필요하다
- 입력이 비어 있으면 요청하지 않고 빈 목록으로 바꾼다 (지웠을 때 이전 추천어가 남아 있지 않도록)
- 이미 검색을 실행한 상태(`isSearched`)에서는 추천어를 요청하지 않는다 (검색 직후 입력창 값이 바뀌어도 불필요한 요청 방지)

## debounce vs throttle

| 구분 | 동작 | 검색 입력에서 |
|---|---|---|
| debounce | 마지막 입력 후 일정 시간 조용하면 실행 | 자동완성에 적합 |
| throttle (sample) | 일정 주기마다 한 번 실행 | 실시간 필터링(로컬 데이터)에 가끔 사용 |

로컬 목록을 필터링하는 검색이라면 API 호출이 없으므로 debounce를 짧게(100~150ms) 주거나 생략하고 `filter`로 바로 처리해도 된다.

<br>

# 6. 검색 결과 - Paging3

## PagingSource

```kotlin
class SearchPagingSource(
    private val api: SearchApi,
    private val query: String,
) : PagingSource<Int, SearchItem>() {

    override fun getRefreshKey(state: PagingState<Int, SearchItem>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.let { page ->
                page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
            }
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SearchItem> {
        val page = params.key ?: 1
        return try {
            val response = api.search(query, page, params.loadSize)
            val items = response.items.map { it.toDomain() }
            LoadResult.Page(
                data = items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (items.isEmpty()) null else page + 1,
            )
        } catch (e: IOException) {
            LoadResult.Error(e)
        } catch (e: HttpException) {
            LoadResult.Error(e)
        }
    }
}
```

## Repository 구현

```kotlin
class SearchRepositoryImpl @Inject constructor(
    private val api: SearchApi,
) : SearchRepository {

    override suspend fun getSuggestions(query: String): List<String> =
        api.getSuggestions(query).suggestions

    override fun searchPaging(query: String): Flow<PagingData<SearchItem>> =
        Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = { SearchPagingSource(api, query) },
        ).flow
}
```

- 검색어마다 새 `PagingSource`가 필요하다. 팩토리 안에서 `query`를 캡처해서 만든다
- `loadSize`는 첫 로드에서 `pageSize * 3`이 기본이라 page 기반 API라면 서버 `size` 파라미터와 맞아떨어지는지 확인한다 (필요하면 `initialLoadSize = pageSize`로 지정)

<br>

# 7. ViewModel (MVI)

```kotlin
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val recentRepository: RecentSearchRepository,
) : ViewModel() {

    // TextField에 바인딩되는 값 (Compose State, 동기 반영)
    var query by mutableStateOf("")
        private set

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _effect = Channel<SearchEffect>(Channel.BUFFERED)
    val effect: Flow<SearchEffect> = _effect.receiveAsFlow()

    // debounce 파이프라인용
    private val queryFlow = MutableStateFlow("")

    // 검색 실행된 검색어 (null이면 아직 검색 안 함)
    private val submittedQuery = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val results: Flow<PagingData<SearchItem>> = submittedQuery
        .filterNotNull()
        .flatMapLatest { keyword -> searchRepository.searchPaging(keyword) }
        .cachedIn(viewModelScope)

    init {
        observeRecent()
        observeSuggestions()
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> changeQuery(intent.text)
            SearchIntent.ClearQuery -> changeQuery("")
            SearchIntent.SearchFieldFocused -> _state.update { it.copy(isSearched = false) }
            is SearchIntent.Submit -> submit(intent.keyword)
            is SearchIntent.DeleteRecent -> viewModelScope.launch {
                recentRepository.delete(intent.keyword)
            }
            SearchIntent.ClearRecent -> viewModelScope.launch {
                recentRepository.clear()
            }
        }
    }

    private fun changeQuery(text: String) {
        query = text
        queryFlow.value = text
        _state.update { it.copy(isSearched = false) }   // 입력을 고치면 다시 자동완성 모드
    }

    private fun submit(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return

        query = trimmed
        queryFlow.value = trimmed
        _state.update { it.copy(isSearched = true, suggestions = emptyList()) }
        submittedQuery.value = trimmed

        viewModelScope.launch {
            recentRepository.save(trimmed)
            _effect.send(SearchEffect.ClearFocus)
        }
    }

    private fun observeRecent() {
        recentRepository.observeRecent()
            .onEach { list -> _state.update { it.copy(recentKeywords = list) } }
            .launchIn(viewModelScope)
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeSuggestions() {
        queryFlow
            .debounce(300)
            .map { it.trim() }
            .distinctUntilChanged()
            .flatMapLatest { keyword ->
                if (keyword.isBlank() || _state.value.isSearched) {
                    flowOf(emptyList())
                } else {
                    flow { emit(searchRepository.getSuggestions(keyword)) }
                        .catch { emit(emptyList()) }
                }
            }
            .onEach { list -> _state.update { it.copy(suggestions = list) } }
            .launchIn(viewModelScope)
    }
}
```

포인트

- `results`는 `cachedIn(viewModelScope)`를 반드시 붙인다. 안 붙이면 화면 회전/재구성 시 페이징 데이터가 사라지고 처음부터 다시 로드한다
- `submittedQuery`가 바뀔 때마다 `flatMapLatest`가 새 Pager로 교체해서 이전 검색 결과는 자동으로 버려진다
- 검색 실행 시 `queryFlow`도 같이 갱신해서 입력창 값과 파이프라인이 어긋나지 않게 한다. 대신 `isSearched`가 true면 자동완성 요청은 건너뛴다
- 최근 검색어 저장은 `save` 완료 후 Effect를 보낸다. 저장 실패가 화면 흐름을 막지 않도록 필요하면 `runCatching`으로 감싼다

<br>

# 8. 검색창 UI (TextField, IME, 포커스)

```kotlin
@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onFocused: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() },
        singleLine = true,
        placeholder = { Text("검색어를 입력하세요") },
        leadingIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
            }
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "검색어 지우기")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
    )
}
```

## 한글 입력 주의

`TextField`의 `value`를 `StateFlow`에서 `collectAsState`로 받으면, 한글 입력 중에 글자 조합이 끊기거나 커서가 튀는 문제가 생길 수 있다.

- `collectAsState`는 값이 다음 프레임에 반영되는 비동기 방식이라 IME가 보낸 값과 화면에 그려진 값이 잠깐 어긋난다
- 영어는 티가 안 나지만 한글처럼 조합 중인 글자가 있는 입력에서는 깨진다
- 해결: TextField에 바인딩되는 값은 `mutableStateOf`(동기 반영)로 들고, debounce 같은 Flow 파이프라인용 값은 별도 `StateFlow`로 복사해서 쓴다

그래서 7번에서 `query`(State)와 `queryFlow`(StateFlow)를 따로 둔 것이다.

```kotlin
// 이렇게 하지 않는다
val query by viewModel.queryFlow.collectAsState()   // 한글 조합 깨짐 가능

// 이렇게 한다
val query = viewModel.query                          // Compose State 직접 읽기
```

## IME 옵션

- `ImeAction.Search`: 키보드 우측 하단 버튼이 돋보기로 바뀐다
- `KeyboardActions(onSearch = ...)`: 해당 버튼을 눌렀을 때 실행
- `singleLine = true`: 줄바꿈 방지 (없으면 Search 액션 대신 엔터가 나오는 키보드도 있다)
- 공백만 입력하고 검색하는 경우는 ViewModel의 `submit`에서 `isBlank()`로 걸러낸다

## 자동 포커스

화면에 진입하면 바로 키보드가 올라오게 한다.

```kotlin
LaunchedEffect(Unit) {
    if (!state.isSearched) {
        focusRequester.requestFocus()
    }
}
```

- 상세 화면에 갔다가 뒤로 돌아온 경우처럼 이미 검색된 상태(`isSearched`)면 포커스를 요청하지 않는다. 안 그러면 돌아오자마자 키보드가 올라오고 결과가 자동완성 모드로 바뀐다

<br>

# 9. 화면 조립 (최근 / 자동완성 / 결과 분기)

```kotlin
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onItemClick: (SearchItem) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items = viewModel.results.collectAsLazyPagingItems()
    val query = viewModel.query

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (!state.isSearched) focusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SearchEffect.ClearFocus -> {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            SearchTopBar(
                query = query,
                onQueryChange = { viewModel.onIntent(SearchIntent.QueryChanged(it)) },
                onSearch = { viewModel.onIntent(SearchIntent.Submit(it)) },
                onFocused = { viewModel.onIntent(SearchIntent.SearchFieldFocused) },
                onBack = onBack,
                focusRequester = focusRequester,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isSearched -> SearchResultContent(
                    items = items,
                    query = query,
                    onItemClick = onItemClick,
                )

                query.isBlank() -> RecentSection(
                    keywords = state.recentKeywords,
                    onClick = { viewModel.onIntent(SearchIntent.Submit(it)) },
                    onDelete = { viewModel.onIntent(SearchIntent.DeleteRecent(it)) },
                    onClearAll = { viewModel.onIntent(SearchIntent.ClearRecent) },
                )

                else -> SuggestionSection(
                    query = query,
                    suggestions = state.suggestions,
                    onClick = { viewModel.onIntent(SearchIntent.Submit(it)) },
                )
            }
        }
    }
}
```

## 최근 검색어

```kotlin
@Composable
private fun RecentSection(
    keywords: List<String>,
    onClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    if (keywords.isEmpty()) {
        EmptyMessage("최근 검색어가 없습니다")
        return
    }

    LazyColumn(Modifier.clearFocusOnScroll()) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("최근 검색어", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onClearAll) { Text("전체 삭제") }
            }
        }
        items(items = keywords, key = { it }) { keyword ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(keyword) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(keyword, modifier = Modifier.weight(1f))
                IconButton(onClick = { onDelete(keyword) }) {
                    Icon(Icons.Default.Close, contentDescription = "$keyword 삭제")
                }
            }
        }
    }
}
```

- `History` 아이콘은 `material-icons-extended` 의존성이 필요하다. 없다면 `Icons.Default.Search` 같은 기본 아이콘으로 대체한다
- `key = { it }`: 검색어가 PrimaryKey라서 중복이 없다. 삭제 시 애니메이션도 자연스럽게 붙는다 (`Modifier.animateItem()` 추가 가능)

## 자동완성

```kotlin
@Composable
private fun SuggestionSection(
    query: String,
    suggestions: List<String>,
    onClick: (String) -> Unit,
) {
    val highlightStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )

    LazyColumn(Modifier.clearFocusOnScroll()) {
        items(items = suggestions, key = { it }) { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(suggestion) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = remember(suggestion, query) {
                        highlightText(suggestion, query, highlightStyle)
                    },
                )
            }
        }
    }
}
```

## 검색 결과

```kotlin
@Composable
private fun SearchResultContent(
    items: LazyPagingItems<SearchItem>,
    query: String,
    onItemClick: (SearchItem) -> Unit,
) {
    val refresh = items.loadState.refresh
    val highlightStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )

    when {
        refresh is LoadState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        refresh is LoadState.Error -> {
            ErrorMessage(onRetry = { items.retry() })
        }

        refresh is LoadState.NotLoading &&
            items.loadState.append.endOfPaginationReached &&
            items.itemCount == 0 -> {
            EmptyMessage("'$query'에 대한 검색 결과가 없습니다")
        }

        else -> {
            LazyColumn(Modifier.clearFocusOnScroll()) {
                items(
                    count = items.itemCount,
                    key = items.itemKey { it.id },
                ) { index ->
                    items[index]?.let { item ->
                        ResultRow(
                            item = item,
                            query = query,
                            highlightStyle = highlightStyle,
                            onClick = { onItemClick(item) },
                        )
                    }
                }

                when (items.loadState.append) {
                    is LoadState.Loading -> item(key = "append_loading") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is LoadState.Error -> item(key = "append_error") {
                        TextButton(
                            onClick = { items.retry() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("다시 시도") }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    item: SearchItem,
    query: String,
    highlightStyle: SpanStyle,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(
            text = remember(item.title, query) { highlightText(item.title, query, highlightStyle) },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = remember(item.description, query) {
                highlightText(item.description, query, highlightStyle)
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

포인트

- "결과 없음"은 `refresh`가 `NotLoading`이고 `endOfPaginationReached`까지 확인한 뒤에만 보여준다. 확인 없이 `itemCount == 0`만 보면 로딩 직전에 잠깐 "결과 없음"이 깜빡인다
- 첫 로딩/에러는 전체 화면, 다음 페이지 로딩/에러는 리스트 맨 아래 아이템으로 나눠서 처리한다
- 결과에 하이라이트를 줄 때 `query`가 입력창 값이라서, 결과를 보다가 입력창을 고치기 시작하면 하이라이트 기준이 바뀐다. 실제 검색된 키워드를 기준으로 하이라이트하려면 `submittedQuery`를 State로 노출해서 사용한다

<br>

# 10. 검색어 하이라이트

```kotlin
fun highlightText(
    text: String,
    query: String,
    style: SpanStyle,
): AnnotatedString {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return AnnotatedString(text)

    val regex = words
        .joinToString("|") { Regex.escape(it) }
        .toRegex(RegexOption.IGNORE_CASE)

    return buildAnnotatedString {
        var last = 0
        regex.findAll(text).forEach { match ->
            append(text.substring(last, match.range.first))
            withStyle(style) { append(match.value) }
            last = match.range.last + 1
        }
        append(text.substring(last))
    }
}
```

- 검색어를 공백으로 나눠서 단어별로 모두 강조한다 ("안드로이드 검색" → "안드로이드"와 "검색" 각각 강조)
- `Regex.escape`: 사용자가 `(`, `+`, `.` 같은 특수문자를 입력해도 정규식 오류가 나지 않게 한다
- `IGNORE_CASE`: 영문 대소문자 구분 없이 매칭
- `remember(text, query)`로 감싸서 스크롤 중 불필요한 재계산을 막는다 (결과 목록이 길면 효과가 크다)

## 확장 아이디어

- 초성 검색(ㅇㄷㄹㅇㄷ → 안드로이드): 한글 유니코드에서 초성을 추출해서 비교하는 함수를 따로 만들면 된다. 하이라이트 범위 계산이 복잡해져서 먼저 검색 매칭만 지원하는 경우가 많다
- 서버가 하이라이트 구간(`<em>` 태그 등)을 내려주면 그 구간을 파싱해서 `AnnotatedString`으로 바꾸는 게 더 정확하다 (형태소 분석 기반 매칭 같은 건 클라이언트에서 흉내내기 어렵다)

<br>

# 11. 키보드와 스크롤 처리

결과 목록을 스크롤할 때 키보드가 올라와 있으면 화면이 가려진다. 스크롤을 시작하면 키보드를 내린다.

```kotlin
@Composable
fun Modifier.clearFocusOnScroll(): Modifier {
    val focusManager = LocalFocusManager.current
    val connection = remember(focusManager) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                focusManager.clearFocus()
                return Offset.Zero
            }
        }
    }
    return nestedScroll(connection)
}
```

- `clearFocus()`를 호출하면 TextField 포커스가 해제되면서 키보드도 내려간다
- 포커스가 해제된 뒤 다시 입력창을 누르면 `onFocused`가 호출되어 `isSearched = false`가 되고 자동완성 모드로 돌아간다
- 어느 리스트에서든 같은 동작을 쓰도록 `Modifier` 확장으로 만들어 둔다 (최근 검색어, 자동완성, 결과 3곳에서 재사용)

## 화면 바깥 터치로 키보드 내리기 (선택)

```kotlin
Modifier.pointerInput(Unit) {
    detectTapGestures(onTap = { focusManager.clearFocus() })
}
```

- 빈 영역 터치로 키보드를 내리고 싶을 때 루트 Box에 붙인다
- 리스트 아이템의 `clickable`과 충돌할 수 있어서 빈 상태 화면에만 붙이는 편이 안전하다

## IME 인셋 처리

- `Scaffold`와 `enableEdgeToEdge()`를 쓰는 경우 결과 리스트 하단이 키보드에 가려지지 않도록 `Modifier.imePadding()`을 컨텐츠 영역에 적용한다
- 단, 최근 검색어/자동완성 목록은 키보드가 올라온 상태가 기본이라 `imePadding()`이 있어야 마지막 항목이 키보드 뒤에 숨지 않는다

<br>

# 12. 테스트

debounce와 `flatMapLatest`는 가상 시간으로 테스트한다.

```kotlin
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

class SearchViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val searchRepository = mockk<SearchRepository>()
    private val recentRepository = mockk<RecentSearchRepository>(relaxed = true)

    private fun createViewModel(): SearchViewModel {
        every { recentRepository.observeRecent(any()) } returns flowOf(emptyList())
        return SearchViewModel(searchRepository, recentRepository)
    }

    @Test
    fun `연속 입력하면 마지막 입력으로만 자동완성을 요청한다`() = runTest {
        coEvery { searchRepository.getSuggestions(any()) } returns listOf("안드로이드")
        val viewModel = createViewModel()

        viewModel.onIntent(SearchIntent.QueryChanged("안"))
        advanceTimeBy(100)
        viewModel.onIntent(SearchIntent.QueryChanged("안드"))
        advanceTimeBy(100)
        viewModel.onIntent(SearchIntent.QueryChanged("안드로"))
        advanceTimeBy(301)
        runCurrent()

        coVerify(exactly = 1) { searchRepository.getSuggestions(any()) }
        coVerify { searchRepository.getSuggestions("안드로") }
        assertEquals(listOf("안드로이드"), viewModel.state.value.suggestions)
    }

    @Test
    fun `공백만 입력하면 자동완성을 요청하지 않는다`() = runTest {
        val viewModel = createViewModel()

        viewModel.onIntent(SearchIntent.QueryChanged("   "))
        advanceTimeBy(400)
        runCurrent()

        coVerify(exactly = 0) { searchRepository.getSuggestions(any()) }
    }

    @Test
    fun `검색을 실행하면 최근 검색어를 저장하고 결과 모드가 된다`() = runTest {
        every { searchRepository.searchPaging(any()) } returns flowOf(PagingData.empty())
        val viewModel = createViewModel()

        viewModel.onIntent(SearchIntent.Submit("  안드로이드  "))
        advanceUntilIdle()

        coVerify { recentRepository.save("안드로이드") }   // trim 확인
        assertTrue(viewModel.state.value.isSearched)
        assertEquals("안드로이드", viewModel.query)
    }

    @Test
    fun `검색 후 입력을 고치면 자동완성 모드로 돌아간다`() = runTest {
        every { searchRepository.searchPaging(any()) } returns flowOf(PagingData.empty())
        val viewModel = createViewModel()

        viewModel.onIntent(SearchIntent.Submit("안드로이드"))
        advanceUntilIdle()
        viewModel.onIntent(SearchIntent.QueryChanged("안드로이드 앱"))

        assertFalse(viewModel.state.value.isSearched)
    }
}
```

- `StandardTestDispatcher`를 Main으로 쓰면 `advanceTimeBy`로 debounce 시간을 직접 제어할 수 있다
- `advanceTimeBy(n)`은 n ms 직전까지만 진행하므로 경계값을 확인하려면 1ms를 더해주고 `runCurrent()`로 대기 중인 작업을 실행한다
- 최근 검색어 DAO는 Room in-memory DB로 별도 테스트하는 것이 좋다 (upsert 시 순서 갱신, trim 동작 확인)

<br>

# 13. 자주 하는 실수

1. 한글 입력에서 `StateFlow` + `collectAsState`로 TextField를 바인딩해서 글자가 깨진다 (`mutableStateOf` 사용)
2. debounce 없이 `onValueChange`마다 API를 호출해서 서버 부하와 깜빡임이 생긴다
3. `flatMapLatest` 대신 `launch`로 요청하다가 오래된 응답이 최신 응답을 덮어쓴다
4. `results`에 `cachedIn`을 안 붙여서 화면 회전 시 결과가 사라지고 크래시(`PagingData collected twice`)가 난다
5. 결과 없음 화면이 로딩 직전에 깜빡인다 (`endOfPaginationReached` 확인 누락)
6. 상세 화면에서 돌아왔을 때 자동 포커스가 다시 걸려서 결과 화면이 자동완성 화면으로 바뀐다
7. 공백만 입력한 검색어나 앞뒤 공백이 그대로 저장되어 최근 검색어에 중복처럼 보인다 (`trim` + `isBlank` 체크)
8. 최근 검색어의 `LazyColumn`에 `key`를 안 줘서 삭제 시 엉뚱한 항목이 사라지는 것처럼 보인다
9. 하이라이트에서 `Regex.escape`를 빼서 `(` 같은 특수문자 입력 시 앱이 죽는다
10. 자동완성 API 에러를 에러 화면으로 처리해서 타이핑 중에 계속 에러가 뜬다 (조용히 빈 목록 처리)
11. 검색 직후 `query`가 바뀌면서 자동완성 요청이 불필요하게 나간다 (`isSearched`일 때 요청 스킵)
12. 키보드가 올라온 상태에서 결과를 스크롤하면 화면이 가려진다 (스크롤 시 `clearFocus`, `imePadding` 적용)

<br>

# 정리

1. 검색 화면은 최근 검색어 / 자동완성 / 결과 3가지 모드를 `isSearched`와 `query.isBlank()` 조합으로 분기한다
2. TextField 값은 `mutableStateOf`(동기), debounce 파이프라인용 값은 `StateFlow`로 분리한다. 한글 입력이 깨지지 않게 하기 위해서다
3. 자동완성은 `debounce(300)` → `distinctUntilChanged` → `flatMapLatest` 조합으로 불필요한 요청과 오래된 응답 덮어쓰기를 막는다
4. 최근 검색어는 Room에 검색어를 PrimaryKey로 저장하고 `@Upsert`로 순서를 갱신, `trim`으로 개수를 제한하며, Flow 구독으로 화면에 자동 반영한다
5. 검색 결과는 `submittedQuery` → `flatMapLatest` → Pager → `cachedIn(viewModelScope)`로 흘리고, 로딩/에러/빈 결과/다음 페이지 상태를 나눠서 처리한다
6. 하이라이트는 공백 기준 단어 분리 + `Regex.escape` + `AnnotatedString`으로 구현하고 `remember`로 재계산을 줄인다
7. 키보드는 IME 검색 액션, 검색 실행 시 포커스 해제, 스크롤 시 `clearFocus`, `imePadding`으로 처리한다
8. debounce 테스트는 `StandardTestDispatcher`와 `advanceTimeBy`로 가상 시간을 제어해서 검증한다
