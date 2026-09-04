# Paging3

<br>

## 목차

1. Paging3란 무엇인가
2. PagingSource로 데이터 소스 정의하기
3. Room과 연동하기
4. Pager와 Flow<PagingData> 만들기
5. Compose에서 LazyPagingItems 활용하기
6. PagingDataAdapter로 RecyclerView에 표시하기
7. RemoteMediator로 네트워크+로컬 결합하기
8. LoadState로 로딩 상태 처리하기
9. 새로고침과 재시도
10. 데이터 변환 (map, filter)
11. 헤더/푸터 아이템 추가하기
12. 테스트 작성하기
13. Hilt와 Paging3 연동
14. 주의사항과 자주 하는 실수
15. 정리

<br>

## Paging3란 무엇인가

Paging3는 대량의 데이터를 한 번에 메모리에 올리지 않고, 화면에 필요한 만큼만 나눠서 점진적으로 불러오는 Jetpack 라이브러리다. 게시글 목록, 검색 결과, 채팅 내역처럼 수백~수만 건에 이를 수 있는 데이터를 다룰 때, 전체를 한 번에 조회하면 메모리 사용량과 초기 로딩 시간이 급격히 늘어난다. Paging3는 사용자가 스크롤하는 시점에 맞춰 다음 페이지를 자동으로 요청하고, 이미 스크롤을 벗어난 오래된 페이지는 메모리에서 정리해준다.

핵심 구성 요소는 세 가지다: 실제로 한 페이지씩 데이터를 가져오는 **PagingSource**, 이를 설정과 함께 감싸서 `Flow<PagingData>`를 만들어주는 **Pager**, 그리고 이 스트림을 구독해 화면에 표시하는 **PagingDataAdapter**(RecyclerView) 또는 **LazyPagingItems**(Compose)다.

```kotlin
// build.gradle.kts (Module: app)
dependencies {
    val paging_version = "3.3.2"

    implementation("androidx.paging:paging-runtime-ktx:$paging_version")
    implementation("androidx.paging:paging-compose:$paging_version")
    // Room과 함께 쓸 경우
    implementation("androidx.room:room-paging:2.6.1")
}
```

| 구성 요소 | 역할 |
|---|---|
| `PagingSource` | 한 페이지 단위로 실제 데이터를 로드하는 로직 |
| `Pager` | `PagingSource`와 페이징 설정(`PagingConfig`)을 묶어 `Flow<PagingData>` 생성 |
| `PagingDataAdapter` / `LazyPagingItems` | 스트림을 구독해 UI에 리스트를 렌더링 |

**실전 팁**: Paging3를 처음 배울 때는 "PagingSource=페이지 하나를 가져오는 함수, Pager=그 함수를 반복 호출하며 스트림으로 만들어주는 관리자"라고 단순화해서 이해하면 전체 그림이 잡히기 쉽다.

<br>

## PagingSource로 데이터 소스 정의하기

`PagingSource<Key, Value>`는 페이지를 식별하는 키(`Key`)와 페이지에 담길 데이터 타입(`Value`)을 제네릭으로 받는다. `load()` 함수를 오버라이드해서 주어진 키를 기준으로 한 페이지 분량의 데이터를 가져오고, 이전/다음 페이지 키를 계산해서 반환한다.

```kotlin
class ArticlePagingSource(
    private val apiService: ApiService
) : PagingSource<Int, ArticleDto>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ArticleDto> {
        val page = params.key ?: 1
        return try {
            val response = apiService.getArticles(page = page, size = params.loadSize)
            LoadResult.Page(
                data = response.items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (response.items.isEmpty()) null else page + 1
            )
        } catch (e: IOException) {
            LoadResult.Error(e)
        } catch (e: HttpException) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ArticleDto>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
```

| 함수 | 역할 |
|---|---|
| `load(params)` | 주어진 키로 한 페이지 데이터를 로드하고 `LoadResult` 반환 |
| `getRefreshKey(state)` | 새로고침 시 어느 페이지부터 다시 불러올지 결정 |
| `LoadResult.Page` | 성공 시 데이터와 이전/다음 키 반환 |
| `LoadResult.Error` | 실패 시 예외 전달, UI의 `LoadState.Error`로 노출됨 |

**실전 팁**: `getRefreshKey`는 사용자가 현재 보고 있는 위치(`anchorPosition`) 근처의 페이지부터 새로고침하기 위한 함수다. 항상 1페이지부터 새로고침하도록 `null`을 반환하면, 스크롤을 많이 내린 상태에서 새로고침했을 때 사용자가 갑자기 맨 위로 이동한 것처럼 보이는 경험을 하게 된다.

<br>

## Room과 연동하기

Room은 `PagingSource`를 자동으로 생성해주는 기능을 내장하고 있다. DAO 메서드의 반환 타입을 `PagingSource<Int, T>`로 선언하기만 하면, Room이 내부적으로 `LIMIT`/`OFFSET` 기반의 페이징 쿼리를 알아서 생성해준다.

```kotlin
@Dao
interface ArticleDao {
    @Query("SELECT * FROM article ORDER BY created_at DESC")
    fun pagingSource(): PagingSource<Int, ArticleEntity>

    @Query("SELECT * FROM article WHERE title LIKE '%' || :keyword || '%' ORDER BY created_at DESC")
    fun searchPagingSource(keyword: String): PagingSource<Int, ArticleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(articles: List<ArticleEntity>)

    @Query("DELETE FROM article")
    suspend fun clearAll()
}

class ArticleRepository(private val articleDao: ArticleDao) {
    fun getArticlesStream(): Flow<PagingData<ArticleEntity>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = { articleDao.pagingSource() }
        ).flow
    }
}
```

| 방식 | 장점 |
|---|---|
| Room 자동 생성 `PagingSource` | `LIMIT`/`OFFSET` 쿼리를 직접 작성할 필요 없음 |
| 직접 작성한 `PagingSource` | 네트워크 API처럼 SQL이 아닌 소스도 페이징 가능 |

**실전 팁**: Room 기반 `PagingSource`는 테이블 데이터가 바뀌면 자동으로 무효화(invalidate)되어 최신 데이터를 다시 로드한다. 이는 Room의 `Flow` 관찰 메커니즘과 동일한 `InvalidationTracker`를 사용하기 때문이며, 별도의 새로고침 로직 없이도 로컬 DB 변경이 화면에 즉시 반영된다.

<br>

## Pager와 Flow<PagingData> 만들기

`Pager`는 `PagingSource`와 `PagingConfig`를 결합해 `Flow<PagingData<T>>`를 만들어주는 진입점이다. `PagingConfig`로 페이지 크기, 프리페치 거리, 플레이스홀더 사용 여부 등을 조정한다.

```kotlin
class ArticleRepository(
    private val apiService: ApiService
) {
    fun getArticlesStream(): Flow<PagingData<ArticleDto>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 5,
                enablePlaceholders = false,
                initialLoadSize = 40
            ),
            pagingSourceFactory = { ArticlePagingSource(apiService) }
        ).flow
    }
}

class ArticleViewModel(private val repository: ArticleRepository) : ViewModel() {
    val articles: Flow<PagingData<ArticleDto>> = repository
        .getArticlesStream()
        .cachedIn(viewModelScope)
}
```

| `PagingConfig` 옵션 | 설명 |
|---|---|
| `pageSize` | 한 번에 로드할 아이템 개수 |
| `prefetchDistance` | 리스트 끝에서 몇 개 남았을 때 다음 페이지를 미리 요청할지 |
| `enablePlaceholders` | 아직 로드되지 않은 자리를 `null`로 표시할지 여부 |
| `initialLoadSize` | 최초 로드 시에만 적용되는 아이템 개수 (보통 `pageSize`의 2~3배) |

**실전 팁**: `.cachedIn(viewModelScope)`를 반드시 붙여야 한다. 이게 없으면 화면 회전 등으로 Flow가 다시 구독될 때마다 처음부터 데이터를 재요청하게 되는데, `cachedIn`은 이미 로드한 페이지를 `viewModelScope` 생명주기 동안 캐시해서 불필요한 재요청을 막아준다.

<br>

## Compose에서 LazyPagingItems 활용하기

Compose에서는 `collectAsLazyPagingItems()` 확장 함수로 `Flow<PagingData<T>>`를 `LazyPagingItems<T>`로 변환한 뒤, `LazyColumn`의 `items()`에 바로 연결해서 사용한다.

```kotlin
@Composable
fun ArticleListScreen(viewModel: ArticleViewModel) {
    val articles = viewModel.articles.collectAsLazyPagingItems()

    LazyColumn {
        items(
            count = articles.itemCount,
            key = articles.itemKey { it.id }
        ) { index ->
            val article = articles[index]
            if (article != null) {
                ArticleRow(article = article)
            } else {
                ArticlePlaceholderRow()
            }
        }

        articles.apply {
            when {
                loadState.append is LoadState.Loading -> {
                    item { LoadingIndicator() }
                }
                loadState.append is LoadState.Error -> {
                    item {
                        RetryButton(onClick = { retry() })
                    }
                }
            }
        }
    }
}
```

| API | 설명 |
|---|---|
| `collectAsLazyPagingItems()` | `Flow<PagingData<T>>`를 Compose 상태로 변환 |
| `itemCount` | 현재까지 로드된 전체 아이템 개수 |
| `itemKey { }` | 리컴포지션 최적화를 위한 안정적인 key 지정 |
| `loadState` | 새로고침/추가로드 각각의 로딩 상태 확인 |

**실전 팁**: `items()`에 `key`를 지정하지 않으면 스크롤 위치가 바뀔 때마다 인덱스 기반으로 재구성되어 불필요한 리컴포지션이 발생할 수 있다. 아이템의 고유 ID로 `itemKey`를 지정하는 습관을 들이는 것이 성능에 유리하다.

<br>

## PagingDataAdapter로 RecyclerView에 표시하기

View 시스템(RecyclerView) 기반 화면에서는 `PagingDataAdapter`를 사용한다. 일반 `ListAdapter`와 비슷하지만, `DiffUtil.ItemCallback`을 통해 페이지 단위로 들어오는 데이터를 효율적으로 비교하고 갱신한다.

```kotlin
class ArticleAdapter : PagingDataAdapter<ArticleDto, ArticleAdapter.ViewHolder>(DIFF_CALLBACK) {

    class ViewHolder(private val binding: ItemArticleBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(article: ArticleDto) {
            binding.title.text = article.title
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemArticleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ArticleDto>() {
            override fun areItemsTheSame(old: ArticleDto, new: ArticleDto) = old.id == new.id
            override fun areContentsTheSame(old: ArticleDto, new: ArticleDto) = old == new
        }
    }
}

// Fragment/Activity에서 구독
lifecycleScope.launch {
    viewModel.articles.collectLatest { pagingData ->
        adapter.submitData(pagingData)
    }
}
```

| 구성 요소 | 역할 |
|---|---|
| `PagingDataAdapter` | `RecyclerView.Adapter`의 페이징 특화 버전 |
| `submitData()` | 새 `PagingData`를 어댑터에 제출, 내부적으로 diff 계산 |
| `DiffUtil.ItemCallback` | 아이템 동일성/내용 비교 기준 정의 |

**실전 팁**: `submitData()`는 `collectLatest`와 함께 사용하는 것이 일반적이다. `collect` 대신 `collectLatest`를 쓰면, 새로운 `PagingData`가 도착했을 때 이전 수집 작업을 취소하고 최신 데이터로 즉시 갈아타므로 빠른 연속 새로고침 상황에서도 안정적으로 동작한다.

<br>

## RemoteMediator로 네트워크+로컬 결합하기

`RemoteMediator`는 네트워크 API와 로컬 DB(Room)를 함께 페이징 소스로 결합할 때 사용한다. 화면에는 항상 Room의 `PagingSource`가 데이터를 공급하고, `RemoteMediator`는 그 뒤에서 필요한 시점에 네트워크로 다음 페이지를 받아와 Room에 저장하는 역할을 한다. 이 구조를 "오프라인 우선(offline-first)" 페이징이라 부른다.

```kotlin
@OptIn(ExperimentalPagingApi::class)
class ArticleRemoteMediator(
    private val apiService: ApiService,
    private val database: AppDatabase
) : RemoteMediator<Int, ArticleEntity>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ArticleEntity>
    ): MediatorResult {
        val page = when (loadType) {
            LoadType.REFRESH -> 1
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                val lastItem = state.lastItemOrNull()
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
                (lastItem.page) + 1
            }
        }

        return try {
            val response = apiService.getArticles(page = page, size = state.config.pageSize)

            database.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    database.articleDao().clearAll()
                }
                database.articleDao().insertAll(
                    response.items.map { it.toEntity(page = page) }
                )
            }

            MediatorResult.Success(endOfPaginationReached = response.items.isEmpty())
        } catch (e: IOException) {
            MediatorResult.Error(e)
        } catch (e: HttpException) {
            MediatorResult.Error(e)
        }
    }
}
```

| LoadType | 의미 |
|---|---|
| `REFRESH` | 최초 로드 또는 새로고침 |
| `APPEND` | 리스트 끝에 도달해 다음 페이지 요청 |
| `PREPEND` | 리스트 맨 위로 스크롤해 이전 페이지 요청 (역방향 페이징) |

**실전 팁**: `RemoteMediator`는 `@OptIn(ExperimentalPagingApi::class)`가 필요한 실험적 API다. 대부분의 앱에서 위로 스크롤해 이전 페이지를 불러올 일은 없으므로, `PREPEND`는 위 예시처럼 바로 종료 처리해도 무방한 경우가 많다.

<br>

## LoadState로 로딩 상태 처리하기

`LoadState`는 페이징의 세 가지 로드 시점(`refresh`, `append`, `prepend`) 각각에 대해 로딩 중/에러/완료 상태를 노출한다. 이를 통해 최초 로딩 스피너, 스크롤 하단 로딩 인디케이터, 에러 시 재시도 버튼 등을 세밀하게 구현할 수 있다.

```kotlin
@Composable
fun ArticleListScreen(viewModel: ArticleViewModel) {
    val articles = viewModel.articles.collectAsLazyPagingItems()

    when (val refreshState = articles.loadState.refresh) {
        is LoadState.Loading -> FullScreenLoading()
        is LoadState.Error -> FullScreenError(
            message = refreshState.error.message.orEmpty(),
            onRetry = { articles.retry() }
        )
        is LoadState.NotLoading -> {
            LazyColumn {
                items(count = articles.itemCount) { index ->
                    articles[index]?.let { ArticleRow(it) }
                }

                item {
                    when (articles.loadState.append) {
                        is LoadState.Loading -> LoadingIndicator()
                        is LoadState.Error -> RetryButton(onClick = { articles.retry() })
                        else -> {}
                    }
                }
            }
        }
    }
}
```

| LoadState 종류 | 대응하는 UI |
|---|---|
| `refresh` | 최초 로딩 스피너, 전체 화면 에러 |
| `append` | 리스트 하단 로딩 인디케이터, 하단 재시도 버튼 |
| `prepend` | 리스트 상단 로딩 인디케이터 (역방향 페이징 시) |

**실전 팁**: `refresh`와 `append`의 에러를 구분해서 처리해야 한다. `refresh` 에러는 화면에 데이터가 하나도 없는 상태이므로 전체 화면 에러 UI가 적합하고, `append` 에러는 이미 일부 데이터가 보이는 상태이므로 리스트 하단에만 작은 재시도 UI를 보여주는 것이 자연스럽다.

<br>

## 새로고침과 재시도

Paging3는 새로고침과 재시도를 위한 명확한 API를 제공한다. `refresh()`는 첫 페이지부터 완전히 새로 로드하고, `retry()`는 실패한 마지막 로드 요청만 다시 시도한다.

```kotlin
@Composable
fun ArticleListScreen(viewModel: ArticleViewModel) {
    val articles = viewModel.articles.collectAsLazyPagingItems()
    val isRefreshing = articles.loadState.refresh is LoadState.Loading

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { articles.refresh() }
    ) {
        LazyColumn {
            items(count = articles.itemCount) { index ->
                articles[index]?.let { ArticleRow(it) }
            }
        }
    }
}
```

| 메서드 | 동작 |
|---|---|
| `refresh()` | `getRefreshKey()`가 계산한 위치부터 전체 새로고침 |
| `retry()` | 가장 최근에 실패한 `load()` 호출만 재시도 |

**실전 팁**: `retry()`는 실패했던 요청만 정확히 다시 시도하므로, 사용자가 스크롤을 많이 내린 상태에서 `append` 에러가 났을 때도 처음부터 다시 로드하지 않는다. 반면 `refresh()`는 항상 전체를 다시 불러오므로, 풀-투-리프레시(pull-to-refresh) 같은 명시적 새로고침 동작에만 사용하는 것이 적절하다.

<br>

## 데이터 변환 (map, filter)

`PagingData`는 `map`, `filter`, `insertSeparators` 같은 변환 연산자를 제공한다. 이 연산자들은 Flow의 `map`처럼 각 페이지가 로드될 때마다 지연 적용되며, 전체 데이터를 한 번에 메모리에 올려서 처리하지 않는다는 점이 특징이다.

```kotlin
class ArticleRepository(private val articleDao: ArticleDao) {

    fun getArticlesStream(): Flow<PagingData<ArticleUiModel>> {
        return Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = { articleDao.pagingSource() }
        ).flow.map { pagingData ->
            pagingData
                .map { entity -> entity.toUiModel() } // Entity -> UI 모델 변환
                .filter { it.title.isNotBlank() }       // 빈 제목 제외
        }
    }
}

// 날짜별 구분선 삽입 예시
fun Flow<PagingData<ArticleUiModel>>.withDateSeparators(): Flow<PagingData<ListItem>> {
    return map { pagingData ->
        pagingData
            .map { ListItem.Article(it) as ListItem }
            .insertSeparators { before, after ->
                if (before?.dateLabel != after?.dateLabel && after != null) {
                    ListItem.DateHeader(after.dateLabel)
                } else {
                    null
                }
            }
    }
}
```

| 연산자 | 용도 |
|---|---|
| `map` | 각 아이템을 다른 타입으로 변환 (Entity → UI 모델 등) |
| `filter` | 조건에 맞지 않는 아이템 제외 |
| `insertSeparators` | 인접한 두 아이템 사이에 구분선/헤더 아이템 삽입 |

**실전 팁**: `map`으로 Entity를 UI 모델로 변환하는 작업은 Repository 계층에서 미리 해두는 것이 좋다. Compose나 Adapter 쪽에서 매번 변환하면, 화면을 새로 구독할 때마다 변환 로직이 반복 실행될 수 있다.

<br>

## 헤더/푸터 아이템 추가하기

리스트의 맨 위나 맨 아래에 별도의 헤더/푸터 아이템(예: "더 이상 결과가 없습니다" 안내)을 추가하고 싶다면, `LoadState`를 감지해서 `LazyColumn`의 `item {}` 블록으로 직접 조합하는 방식이 Compose에서 가장 흔히 쓰인다.

```kotlin
@Composable
fun ArticleListWithFooter(viewModel: ArticleViewModel) {
    val articles = viewModel.articles.collectAsLazyPagingItems()

    LazyColumn {
        item { HeaderBanner() }

        items(count = articles.itemCount) { index ->
            articles[index]?.let { ArticleRow(it) }
        }

        item {
            val appendState = articles.loadState.append
            when {
                appendState is LoadState.Loading -> LoadingIndicator()
                appendState is LoadState.NotLoading && appendState.endOfPaginationReached -> {
                    Text("더 이상 결과가 없습니다", modifier = Modifier.padding(16.dp))
                }
                appendState is LoadState.Error -> RetryButton(onClick = { articles.retry() })
            }
        }
    }
}
```

| 방식 | 적합한 경우 |
|---|---|
| `LazyColumn`의 `item {}`으로 직접 조합 | Compose 기반, 유연한 커스터마이징 |
| `ConcatAdapter` + `LoadStateAdapter` | View 시스템(RecyclerView) 기반 |

**실전 팁**: `endOfPaginationReached`는 `LoadResult.Page`에서 `nextKey`가 `null`일 때, 또는 `RemoteMediator`가 `endOfPaginationReached = true`를 반환했을 때 켜진다. 이 값을 확인해서 "마지막 페이지에 도달했다"는 안내를 자연스럽게 보여줄 수 있다.

<br>

## 테스트 작성하기

`PagingSource`는 `PagingSource.LoadParams`와 `LoadResult`를 직접 만들어 단위 테스트할 수 있다. Paging 라이브러리는 이를 돕는 `TestPager` 유틸리티도 제공한다.

```kotlin
class ArticlePagingSourceTest {

    private val fakeApiService = FakeApiService()
    private val pagingSource = ArticlePagingSource(fakeApiService)

    @Test
    fun `첫 페이지 로드 시 nextKey가 2다`() = runTest {
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false)
        )

        assertTrue(result is PagingSource.LoadResult.Page)
        result as PagingSource.LoadResult.Page
        assertEquals(2, result.nextKey)
        assertNull(result.prevKey)
    }

    @Test
    fun `네트워크 실패 시 LoadResult Error를 반환한다`() = runTest {
        fakeApiService.shouldThrowError = true

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false)
        )

        assertTrue(result is PagingSource.LoadResult.Error)
    }
}
```

| 테스트 대상 | 확인 포인트 |
|---|---|
| 정상 로드 | `nextKey`/`prevKey`가 올바르게 계산되는지 |
| 마지막 페이지 | 빈 목록 응답 시 `nextKey`가 `null`인지 |
| 에러 상황 | 예외 발생 시 `LoadResult.Error`를 반환하는지 |

**실전 팁**: `PagingSource.LoadParams.Refresh`, `Append`, `Prepend`는 각각 생성자가 달라 실수하기 쉽다. 세 가지 로드 타입 각각에 대한 테스트 케이스를 분리해서 작성하면 `getRefreshKey`나 페이지 계산 로직의 버그를 미리 잡을 수 있다.

<br>

## Hilt와 Paging3 연동

Repository에서 `Pager`를 생성하는 부분을 Hilt로 주입받는 의존성(DAO, ApiService)과 조합해서 구성하는 것이 실무에서 흔한 패턴이다. `Pager` 자체는 상태를 갖지 않는 팩토리 성격이므로 매번 새로 만들어도 무방하다.

```kotlin
class ArticleRepository @Inject constructor(
    private val articleDao: ArticleDao,
    private val apiService: ApiService,
    private val database: AppDatabase
) {
    @OptIn(ExperimentalPagingApi::class)
    fun getArticlesStream(): Flow<PagingData<ArticleEntity>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            remoteMediator = ArticleRemoteMediator(apiService, database),
            pagingSourceFactory = { articleDao.pagingSource() }
        ).flow
    }
}

@HiltViewModel
class ArticleViewModel @Inject constructor(
    repository: ArticleRepository
) : ViewModel() {
    val articles: Flow<PagingData<ArticleEntity>> = repository
        .getArticlesStream()
        .cachedIn(viewModelScope)
}
```

| 구성 요소 | Hilt 등록 필요 여부 |
|---|---|
| `ArticleDao`, `ApiService`, `AppDatabase` | 필요 (Room/Network 모듈에서 이미 제공) |
| `Pager`, `PagingConfig` | 불필요, Repository 내부에서 직접 생성 |
| `RemoteMediator` | 불필요, Repository 내부에서 의존성 조합해 생성 |

**실전 팁**: `Pager`를 별도로 Hilt 모듈에 등록하려는 시도는 대부분 과한 설계다. `Pager`는 가벼운 팩토리이므로 Repository 메서드 안에서 필요할 때마다 생성하는 것으로 충분하며, 진짜 싱글톤이 필요한 대상은 그 안에 들어가는 DAO나 ApiService 쪽이다.

<br>

## 주의사항과 자주 하는 실수

1. `.cachedIn(viewModelScope)`를 빠뜨리면 화면 회전이나 재구독 시마다 페이징이 처음부터 다시 시작된다. Repository에서 ViewModel로 넘어가는 지점에 반드시 붙여야 한다.
2. `getRefreshKey()`를 항상 `null`로 반환하면, 스크롤을 많이 내린 상태에서 새로고침했을 때 사용자가 갑자기 맨 위로 이동하는 어색한 경험을 하게 된다.
3. Compose `LazyColumn`의 `items()`에 `key`를 지정하지 않으면 불필요한 리컴포지션이 발생해 스크롤 성능이 저하될 수 있다.
4. `PagingSource`와 `RemoteMediator`를 함께 쓸 때, `RemoteMediator`가 Room에 데이터를 쓰는 트랜잭션과 `PagingSource`가 읽는 시점이 꼬이면 중복 아이템이 보일 수 있다. `withTransaction`으로 삭제와 삽입을 원자적으로 묶어야 한다.
5. `refresh`와 `append`의 에러 UI를 구분하지 않고 동일하게 처리하면, 이미 일부 데이터가 보이는 상태인데도 전체 화면 에러로 덮어버려 사용자 경험이 나빠진다.
6. `enablePlaceholders = true`로 설정했는데 전체 아이템 개수(`itemCount`)를 서버가 제공하지 않는 API에 사용하면 플레이스홀더 계산이 어긋날 수 있다. 전체 개수를 모르는 API라면 `false`로 두는 것이 안전하다.
7. `PagingSource`의 `load()` 안에서 발생하는 예외를 잡지 않고 그대로 던지면 앱이 크래시한다. `IOException`, `HttpException` 등을 반드시 `try-catch`로 감싸 `LoadResult.Error`로 변환해야 한다.
8. `RemoteMediator`가 실험적 API(`@OptIn(ExperimentalPagingApi::class)`)라는 점을 인지하지 못하고 프로덕션에 그대로 사용하다가, 라이브러리 업데이트 시 API가 바뀌어 당황하는 경우가 있다.
9. 페이지 크기(`pageSize`)를 너무 작게 잡으면 스크롤할 때마다 네트워크 요청이 너무 잦아지고, 너무 크게 잡으면 초기 로딩이 느려진다. 화면에 보이는 아이템 수의 2~3배 정도로 잡는 것이 일반적인 시작점이다.
10. `collect` 대신 `collectLatest`를 써야 하는 지점(어댑터에 `submitData` 하는 부분)에서 `collect`를 사용해, 빠른 연속 새로고침 시 이전 요청과 최신 요청이 뒤섞이는 문제를 겪는 경우가 있다.

<br>

## 정리

Paging3는 대량의 데이터를 한 번에 불러오지 않고, 화면에 필요한 만큼만 점진적으로 로드하도록 도와주는 라이브러리다. 
`PagingSource`가 한 페이지를 가져오는 최소 단위 로직을 담당하고, `Pager`가 이를 `PagingConfig`와 결합해 `Flow<PagingData>`로 만들어주며, 
Compose에서는 `LazyPagingItems`, View 시스템에서는 `PagingDataAdapter`가 이 스트림을 구독해 리스트를 렌더링한다. 

Room과 연동하면 로컬 DB 페이징을 별도 코드 없이 자동으로 처리할 수 있고, 네트워크와 로컬을 함께 다뤄야 한다면 
`RemoteMediator`로 오프라인 우선 구조를 만들 수 있다. 
`LoadState`로 새로고침/추가로드 각각의 로딩·에러 상태를 세밀하게 노출해주므로, 이를 잘 활용하면 사용자에게 자연스러운 로딩 경험을 제공할 수 있다. 
처음에는 Room 기반의 간단한 `PagingSource`와 Compose의 `LazyPagingItems`만으로 작은 목록 화면을 만들어보고, 
이후 네트워크 연동과 `RemoteMediator`, 헤더/푸터 처리로 점차 범위를 넓혀가는 것을 추천한다.
