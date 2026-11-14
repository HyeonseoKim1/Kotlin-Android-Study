# Pull-to-Refresh + 무한 스크롤 + 스켈레톤 로딩

목록 화면에서 거의 항상 같이 쓰이는 세 가지 패턴을 Compose + MVI + Hilt 기준으로 정리한다.

1. 전체 그림과 상태 설계
2. 의존성
3. 데이터 계층 (Repository)
4. MVI 계약 (State / Intent / Effect)
5. ViewModel
6. Pull-to-Refresh
7. 무한 스크롤
8. 스켈레톤 로딩
9. 화면 조립
10. Paging3와 직접 구현 비교
11. 엣지 케이스와 주의점
12. ViewModel 테스트
13. 정리

<br>

# 1. 전체 그림과 상태 설계

세 기능은 UI로 보면 따로지만, 결국 하나의 "목록 로딩 상태 머신"이다.

| 상황 | 트리거 | 보여줄 UI |
|---|---|---|
| 최초 진입 | 화면 생성 | 스켈레톤 (리스트 전체) |
| 당겨서 새로고침 | 사용자 제스처 | 상단 인디케이터 (기존 리스트 유지) |
| 다음 페이지 | 스크롤이 끝에 근접 | 하단 스켈레톤/로딩 푸터 |
| 마지막 페이지 | hasNext = false | 푸터에 "마지막입니다" |
| 실패 | 네트워크 에러 | 최초: 전체 에러 / 추가: 푸터 재시도 |

핵심은 로딩을 `Boolean` 하나로 뭉치지 않고 종류별로 분리하는 것이다.

- `isInitialLoading`: 최초 로딩. 스켈레톤을 보여준다.
- `isRefreshing`: 당겨서 새로고침. 기존 목록 위에 인디케이터만 띄운다.
- `isAppending`: 다음 페이지 로딩. 목록 하단에 푸터를 보여준다.

하나의 `isLoading`으로 합치면 "새로고침 중인데 스켈레톤이 목록을 덮어버리는" 문제가 생긴다.

## 1.1 데이터 흐름

```text
UI (Compose)
  ├─ PullToRefreshBox.onRefresh  ──► Intent.Refresh
  ├─ LazyListState 끝 근접 감지   ──► Intent.LoadMore
  └─ 푸터 재시도 버튼            ──► Intent.Retry
        │
        ▼
ViewModel.onIntent()  ── 중복 요청 가드 ──► Repository.getPosts(page, size)
        │
        ▼
StateFlow<PostListState>  ──► UI 재구성
Channel<PostListEffect>   ──► 스낵바 등 일회성 이벤트
```

<br>

# 2. 의존성

`PullToRefreshBox`는 Material3 1.3.0 이상에서 사용 가능하다. BOM을 쓰면 버전은 BOM이 맞춰준다.

```kotlin
// build.gradle.kts (app)
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")

    // collectAsStateWithLifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-compose")

    // hiltViewModel()
    implementation("androidx.hilt:hilt-navigation-compose")
}
```

<br>

# 3. 데이터 계층 (Repository)

서버 대신 지연과 실패를 흉내 내는 Fake 구현으로 시작한다. 실제로는 Retrofit API로 바꾸면 된다.

```kotlin
data class Post(
    val id: Long,
    val title: String,
    val content: String,
)

data class PageResult(
    val items: List<Post>,
    val hasNext: Boolean,
)

interface PostRepository {
    suspend fun getPosts(page: Int, size: Int): PageResult
}
```

```kotlin
import kotlinx.coroutines.delay
import javax.inject.Inject

class FakePostRepository @Inject constructor() : PostRepository {

    private val totalCount = 47

    override suspend fun getPosts(page: Int, size: Int): PageResult {
        delay(1_500) // 네트워크 지연 흉내

        val start = page * size
        val end = minOf(start + size, totalCount)

        val items = (start until end).map { index ->
            Post(
                id = index.toLong(),
                title = "게시글 제목 #${index + 1}",
                content = "게시글 ${index + 1}번의 본문 미리보기 텍스트입니다. 두 줄 이상 길어질 수 있습니다.",
            )
        }

        return PageResult(
            items = items,
            hasNext = end < totalCount,
        )
    }
}
```

```kotlin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindPostRepository(impl: FakePostRepository): PostRepository
}
```

<br>

# 4. MVI 계약 (State / Intent / Effect)

```kotlin
data class PostListState(
    val items: List<Post> = emptyList(),
    val page: Int = 0,                      // 마지막으로 로딩 성공한 페이지
    val hasNext: Boolean = true,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isAppending: Boolean = false,
    val initialError: Boolean = false,      // 최초 로딩 실패 (전체 에러 화면)
    val appendError: Boolean = false,       // 추가 로딩 실패 (푸터 재시도)
) {
    val isBusy: Boolean
        get() = isInitialLoading || isRefreshing || isAppending
}

sealed interface PostListIntent {
    data object Refresh : PostListIntent
    data object LoadMore : PostListIntent
    data object Retry : PostListIntent
}

sealed interface PostListEffect {
    data class ShowMessage(val message: String) : PostListEffect
}
```

- `isBusy`는 중복 요청을 막는 가드용 파생 값이다.
- 에러는 "최초 실패"와 "추가 실패"를 구분한다. 처리 방식이 완전히 다르기 때문이다.
- 스낵바처럼 한 번만 소비되어야 하는 것은 State가 아니라 Effect(Channel)로 보낸다.

<br>

# 5. ViewModel

로딩 종류를 `LoadType`으로 구분하고, 하나의 `load()` 함수로 세 경우를 모두 처리한다.

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PostListViewModel @Inject constructor(
    private val repository: PostRepository,
) : ViewModel() {

    private enum class LoadType { INITIAL, REFRESH, APPEND }

    private val _state = MutableStateFlow(PostListState())
    val state: StateFlow<PostListState> = _state.asStateFlow()

    private val _effect = Channel<PostListEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load(LoadType.INITIAL)
    }

    fun onIntent(intent: PostListIntent) {
        when (intent) {
            PostListIntent.Refresh -> load(LoadType.REFRESH)
            PostListIntent.LoadMore -> load(LoadType.APPEND)
            PostListIntent.Retry -> {
                // 실패한 종류에 맞게 재시도
                if (_state.value.items.isEmpty()) load(LoadType.INITIAL) else load(LoadType.APPEND)
            }
        }
    }

    private fun load(type: LoadType) {
        val current = _state.value

        when (type) {
            // 추가 로딩은 이미 뭔가 진행 중이거나 마지막 페이지면 무시
            LoadType.APPEND -> if (current.isBusy || !current.hasNext) return
            // 새로고침은 진행 중이던 추가 로딩을 취소하고 덮어쓴다
            LoadType.REFRESH -> if (current.isRefreshing || current.isInitialLoading) return
            LoadType.INITIAL -> Unit
        }

        if (type != LoadType.APPEND) loadJob?.cancel()

        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isInitialLoading = type == LoadType.INITIAL,
                    isRefreshing = type == LoadType.REFRESH,
                    isAppending = type == LoadType.APPEND,
                    initialError = false,
                    appendError = false,
                )
            }

            val targetPage = if (type == LoadType.APPEND) current.page + 1 else FIRST_PAGE

            try {
                val result = repository.getPosts(targetPage, PAGE_SIZE)

                _state.update {
                    it.copy(
                        items = if (type == LoadType.APPEND) {
                            (it.items + result.items).distinctBy { post -> post.id }
                        } else {
                            result.items
                        },
                        page = targetPage,
                        hasNext = result.hasNext,
                        isInitialLoading = false,
                        isRefreshing = false,
                        isAppending = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e // 코루틴 취소는 삼키면 안 된다
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isInitialLoading = false,
                        isRefreshing = false,
                        isAppending = false,
                        initialError = type == LoadType.INITIAL && it.items.isEmpty(),
                        appendError = type == LoadType.APPEND,
                    )
                }
                _effect.send(PostListEffect.ShowMessage("불러오지 못했어요. 잠시 후 다시 시도해주세요."))
            }
        }
    }

    private companion object {
        const val FIRST_PAGE = 0
        const val PAGE_SIZE = 10
    }
}
```

포인트 정리:

- `runCatching`은 `CancellationException`까지 잡아버리므로 `try/catch`로 취소를 다시 던진다.
- 새로고침이 시작되면 진행 중이던 추가 로딩 Job을 취소한다. 안 그러면 새 목록 뒤에 옛 페이지가 붙는 레이스가 생긴다.
- 추가 로딩 결과는 `distinctBy { id }`로 중복을 제거한다. 서버 데이터가 로딩 사이에 밀리면 같은 항목이 두 번 올 수 있다.
- 새로고침 실패 시 기존 목록은 그대로 유지하고 스낵바만 띄운다.

<br>

# 6. Pull-to-Refresh

## 6.1 PullToRefreshBox 기본형

Material3의 `PullToRefreshBox`는 자식 스크롤 컨테이너와 제스처 연동, 인디케이터 위치를 한 번에 처리해준다.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshSample(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            // ...
        }
    }
}
```

- `isRefreshing`은 반드시 ViewModel의 State로 관리한다. 로컬 `remember`로 관리하면 화면 회전이나 프로세스 복구에서 상태가 어긋난다.
- `onRefresh`는 사용자가 임계값 이상 당겼다 놓는 순간 한 번 호출된다. 이후 `isRefreshing = true`가 되면 인디케이터가 유지되고, `false`가 되면 사라진다.
- 자식은 스크롤 가능한 컴포저블(`LazyColumn`, `verticalScroll`)이어야 한다. 스크롤 불가능한 에러 화면에서는 당기기가 동작하지 않으므로 재시도 버튼을 따로 둔다.

## 6.2 인디케이터 커스터마이징

`indicator` 파라미터로 모양과 위치를 바꿀 수 있다.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomIndicatorSample(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    val pullState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        },
    ) {
        content()
    }
}
```

## 6.3 최초 로딩과 구분하기

최초 로딩에서는 당기기 인디케이터를 쓰지 않는다. 이미 스켈레톤이 보이는데 인디케이터까지 돌면 중복이다.
그래서 State에서 `isInitialLoading`과 `isRefreshing`을 분리했고, `PullToRefreshBox`에는 `isRefreshing`만 연결한다.

<br>

# 7. 무한 스크롤

## 7.1 원리

`LazyListState.layoutInfo`로 "현재 화면에 보이는 마지막 아이템의 index"를 알 수 있다. 이 값이 전체 개수 끝에서 N개 이내로 들어오면 다음 페이지를 요청한다. N을 버퍼(prefetch distance)라고 부른다.

- 버퍼가 0이면 끝에 닿은 뒤에야 로딩이 시작돼서 스크롤이 멈칫한다.
- 버퍼를 3~5 정도 주면 끝에 도달하기 전에 미리 로딩해서 자연스럽다.

## 7.2 snapshotFlow 기반 확장 함수

스크롤 위치는 매 프레임 바뀌는 값이라 `LaunchedEffect` 안에서 `snapshotFlow`로 변환해서 감지한다. 컴포지션에서 직접 읽으면 스크롤마다 리컴포지션이 일어난다.

```kotlin
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun LazyListState.OnReachEnd(
    buffer: Int = 3,
    onLoadMore: () -> Unit,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    LaunchedEffect(this, buffer) {
        snapshotFlow {
            val info = layoutInfo
            val total = info.totalItemsCount
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1

            // 끝에 근접했으면 현재 전체 개수를, 아니면 -1을 방출
            if (total > 0 && lastVisible >= total - 1 - buffer) total else -1
        }
            .distinctUntilChanged()
            .filter { it >= 0 }
            .collect { currentOnLoadMore() }
    }
}
```

여기서 중요한 설계가 두 가지 있다.

1. Boolean이 아니라 전체 개수(`total`)를 방출한다.
   Boolean + `distinctUntilChanged`를 쓰면, 새 페이지가 로딩된 뒤에도 조건이 계속 `true`일 때 다음 방출이 일어나지 않아 로딩이 멈춘다. 개수를 방출하면 아이템이 늘어날 때마다 값이 바뀌므로 다시 트리거된다.
2. 추가 로딩이 실패했을 때는 개수가 그대로라 재방출되지 않는다. 그래서 실패 후 무한 재시도 루프에 빠지지 않고, 사용자가 푸터의 재시도 버튼을 눌러야 다시 시도한다.

`rememberUpdatedState`는 람다가 바뀌어도 `LaunchedEffect`를 재시작하지 않고 최신 람다를 호출하기 위해 쓴다.

## 7.3 중복 호출 방어

`OnReachEnd`는 조건만 감지하고, 실제 중복 방어는 ViewModel의 `load(APPEND)` 가드(`isBusy`, `hasNext`)가 맡는다. UI에서 막으려고 `enabled` 플래그를 넣으면 State와 이중으로 관리돼서 복잡해진다. 가드는 한 곳(ViewModel)에 두는 게 단순하다.

## 7.4 푸터 구성

푸터는 상태에 따라 세 가지 중 하나를 보여준다.

```kotlin
@Composable
fun PostListFooter(
    isAppending: Boolean,
    appendError: Boolean,
    hasNext: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isAppending -> {
                Column {
                    PostItemSkeleton()
                    PostItemSkeleton()
                }
            }

            appendError -> {
                TextButton(onClick = onRetry) {
                    Text("불러오기에 실패했어요. 다시 시도")
                }
            }

            !hasNext -> {
                Text(
                    text = "마지막 게시글입니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
```

<br>

# 8. 스켈레톤 로딩

## 8.1 왜 스켈레톤인가

- 스피너는 "기다리는 중"이라는 정보만 준다.
- 스켈레톤은 "곧 이런 모양의 콘텐츠가 나온다"를 미리 보여줘서 체감 대기 시간이 짧고, 로딩 완료 시 레이아웃이 덜 튄다.
- 그래서 스켈레톤의 모양은 실제 아이템 레이아웃과 최대한 비슷해야 한다.

## 8.2 Shimmer 효과

그라데이션을 좌에서 우로 흘려보내는 방식이다. 애니메이션 값은 `State` 객체 그대로 들고 있다가 그리기 단계(draw phase)에서만 읽는다. 컴포지션에서 `.value`를 읽으면 프레임마다 리컴포지션이 일어난다.

```kotlin
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )

    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface

    return this.drawWithCache {
        val width = size.width

        onDrawBehind {
            // -width ~ +width 범위로 하이라이트 밴드를 이동
            val startX = progress.value * 2f * width - width

            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(startX, 0f),
                    end = Offset(startX + width, 0f),
                ),
            )
        }
    }
}
```

- `progress`를 `by`로 위임하지 않고 `State` 그대로 둔 것이 핵심이다. `onDrawBehind` 안에서 `.value`를 읽으면 그리기 단계만 다시 실행된다.
- `Modifier.shimmer()`는 `@Composable` 확장 함수다. `rememberInfiniteTransition`이 컴포저블이기 때문이다.
- 모양(둥근 모서리)은 `clip`을 shimmer 앞에 붙여서 적용한다. 순서가 바뀌면 clip이 적용되지 않는다.

## 8.3 스켈레톤 아이템

실제 `PostItem`과 같은 구조(제목 1줄 + 본문 2줄)로 만든다.

```kotlin
@Composable
fun PostItemSkeleton(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 제목
        Box(
            Modifier
                .fillMaxWidth(0.6f)
                .height(20.dp)
                .clip(shape)
                .shimmer(),
        )
        // 본문 1줄
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(shape)
                .shimmer(),
        )
        // 본문 2줄
        Box(
            Modifier
                .fillMaxWidth(0.8f)
                .height(14.dp)
                .clip(shape)
                .shimmer(),
        )
    }
}
```

## 8.4 실제 아이템

```kotlin
@Composable
fun PostItem(
    post: Post,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = post.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = post.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

## 8.5 성능 메모

- 스켈레톤 아이템마다 `rememberInfiniteTransition`을 따로 돌리면 개수만큼 애니메이션이 생긴다. 6~8개 수준이면 문제없지만, 더 많거나 복잡해지면 리스트 최상단에서 transition을 하나만 만들고 값을 내려주는 방식으로 바꾼다.
- 스켈레톤을 `LazyColumn`의 `items(count)`로 그리면 화면 밖 아이템은 컴포지션되지 않아서 비용이 거의 없다.

<br>

# 9. 화면 조립

## 9.1 Route (상태 수집 + Effect 처리)

```kotlin
@Composable
fun PostListRoute(
    viewModel: PostListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PostListEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    PostListScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::onIntent,
    )
}
```

Route는 ViewModel과 닿는 얇은 층이고, `PostListScreen`은 State와 콜백만 받는 순수 컴포저블이라서 Preview와 UI 테스트가 쉽다.

## 9.2 Screen

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val SKELETON_COUNT = 8
private const val FOOTER_KEY = "footer"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostListScreen(
    state: PostListState,
    snackbarHostState: SnackbarHostState,
    onIntent: (PostListIntent) -> Unit,
) {
    val listState = rememberLazyListState()

    listState.OnReachEnd(buffer = 3) {
        onIntent(PostListIntent.LoadMore)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("게시글") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onIntent(PostListIntent.Refresh) },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                // 최초 로딩 실패: 전체 에러
                state.initialError -> {
                    FullErrorContent(onRetry = { onIntent(PostListIntent.Retry) })
                }

                // 로딩이 끝났는데 비어 있음
                !state.isInitialLoading && state.items.isEmpty() -> {
                    EmptyContent()
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.isInitialLoading) {
                            items(SKELETON_COUNT) {
                                PostItemSkeleton()
                                HorizontalDivider()
                            }
                        } else {
                            items(
                                items = state.items,
                                key = { it.id },
                                contentType = { "post" },
                            ) { post ->
                                PostItem(post)
                                HorizontalDivider()
                            }

                            item(key = FOOTER_KEY, contentType = "footer") {
                                PostListFooter(
                                    isAppending = state.isAppending,
                                    appendError = state.appendError,
                                    hasNext = state.hasNext,
                                    onRetry = { onIntent(PostListIntent.Retry) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

```kotlin
@Composable
private fun FullErrorContent(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("목록을 불러오지 못했어요")
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("다시 시도") }
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text("게시글이 없습니다")
    }
}
```

필요한 import는 `androidx.compose.foundation.shape.RoundedCornerShape`, `androidx.compose.ui.draw.clip`, `androidx.compose.ui.text.style.TextOverflow`, `androidx.hilt.navigation.compose.hiltViewModel`, `androidx.lifecycle.compose.collectAsStateWithLifecycle` 등이 더 있다. IDE의 자동 import로 채우면 된다.

## 9.3 동작 순서 요약

1. 화면 진입 → `init`에서 `INITIAL` 로딩 → 스켈레톤 8개 표시
2. 로딩 완료 → 스켈레톤이 실제 목록으로 교체
3. 스크롤이 끝에서 3개 이내로 들어오면 `LoadMore` → 푸터에 스켈레톤 2개 → 목록에 추가
4. 마지막 페이지(`hasNext = false`) → 푸터에 종료 문구
5. 위로 당기면 `Refresh` → 상단 인디케이터, 목록은 유지 → 완료 시 첫 페이지로 교체

<br>

# 10. Paging3와 직접 구현 비교

이미 정리한 Paging3 노트와 비교하면 다음과 같다.

| 항목 | 직접 구현 (이 노트) | Paging3 |
|---|---|---|
| 학습 비용 | 낮음, 코드가 눈에 보임 | 높음 (PagingSource, RemoteMediator, LoadState) |
| 상태 제어 | 모든 상태를 직접 관리 | `LoadState`로 refresh/append/prepend 제공 |
| 새로고침 | `Refresh` Intent + State | `pagingItems.refresh()` |
| 중복/레이스 방어 | 직접 가드 작성 | 라이브러리가 처리 |
| 로컬 캐시 연동 | 직접 작성 | RemoteMediator로 Room 연동 |
| 목록 편집(삭제, 좋아요 즉시 반영) | State를 직접 수정해서 쉬움 | `PagingData`를 map/filter로 변환해야 해서 번거로움 |
| 적합한 경우 | 단순한 페이징, 목록 편집이 많은 화면 | 대용량, 오프라인 캐시, 양방향 페이징 |

판단 기준은 이렇게 잡으면 된다.

- 서버 페이지네이션만 있고 목록을 자주 수정한다면 직접 구현이 단순하다.
- Room 캐시와 오프라인 지원이 필요하거나 데이터가 크다면 Paging3를 쓴다.

Paging3를 쓸 때도 스켈레톤과 새로고침은 같은 방식으로 붙는다.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagingSample(pagingItems: LazyPagingItems<Post>) {
    PullToRefreshBox(
        isRefreshing = pagingItems.loadState.refresh is LoadState.Loading &&
            pagingItems.itemCount > 0,
        onRefresh = { pagingItems.refresh() },
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            // 최초 로딩: itemCount == 0 이면서 refresh가 Loading
            if (pagingItems.loadState.refresh is LoadState.Loading && pagingItems.itemCount == 0) {
                items(8) { PostItemSkeleton() }
            }

            items(
                count = pagingItems.itemCount,
                key = pagingItems.itemKey { it.id },
            ) { index ->
                pagingItems[index]?.let { PostItem(it) }
            }

            if (pagingItems.loadState.append is LoadState.Loading) {
                item { PostItemSkeleton() }
            }
        }
    }
}
```

최초 로딩과 새로고침을 구분하는 조건(`itemCount > 0` 여부)을 빼먹으면, 새로고침할 때 스켈레톤이 목록을 덮는 문제가 생긴다.

<br>

# 11. 엣지 케이스와 주의점

## 11.1 스크롤 위치

- 새로고침이 끝나 목록이 통째로 교체되면 스크롤이 맨 위로 간다. 보통은 자연스럽지만, 필요하면 `listState.animateScrollToItem(0)`을 호출한다.
- 화면 회전이나 백스택 복귀에서는 `rememberLazyListState()`가 `rememberSaveable`로 저장되어 위치가 유지된다. 다만 ViewModel 데이터가 살아 있어야 위치 복원이 의미가 있다.

## 11.2 key 지정

`items(key = { it.id })`를 지정하지 않으면 새로고침이나 추가 로딩 시 아이템 상태(예: 확장 여부, 애니메이션)가 index 기준으로 섞인다. 항상 고유 key를 준다.

## 11.3 짧은 첫 페이지

첫 페이지 아이템이 화면을 다 채우지 못하면 스크롤이 불가능해서 `OnReachEnd`의 조건이 처음부터 `true`다. 이 경우 `snapshotFlow`가 첫 방출에서 바로 `LoadMore`를 호출하고, 개수가 늘 때마다 다시 방출되므로 화면이 찰 때까지 자동으로 이어서 로딩된다. 개수 기반 방출로 설계한 이유 중 하나다.

## 11.4 연타와 레이스

| 상황 | 대응 |
|---|---|
| 새로고침 중 추가 로딩 요청 | `isBusy` 가드로 무시 |
| 추가 로딩 중 새로고침 | 기존 Job 취소 후 새로고침 실행 |
| 추가 로딩이 느린 사이 새로고침 완료 | Job 취소로 옛 응답이 State에 반영되지 않음 |
| 같은 페이지가 두 번 응답 | `distinctBy { id }`로 방어 |

## 11.5 접근성

- 스켈레톤은 시각 정보일 뿐이라 TalkBack에는 "로딩 중" 안내가 없다. 리스트 컨테이너에 `Modifier.semantics { contentDescription = "목록을 불러오는 중" }`을 주거나 `liveRegion`을 쓰면 도움이 된다.
- 푸터의 재시도 버튼은 텍스트 버튼이라 기본 접근성이 충분하다.

## 11.6 다크 모드

Shimmer 색을 하드코딩하지 말고 `MaterialTheme.colorScheme`에서 가져온다. 위 구현은 `surfaceVariant`와 `surface`를 써서 라이트/다크 모두 대응된다.

## 11.7 흔한 실수

- 로딩을 `isLoading` 하나로 합쳐서 새로고침 중 스켈레톤이 목록을 덮는다.
- `runCatching`으로 취소 예외까지 삼켜서 Job 취소가 오류 처리로 흘러간다.
- 스크롤 위치를 컴포지션에서 직접 읽어서 스크롤마다 리컴포지션이 발생한다.
- `isRefreshing`을 로컬 상태로 관리해서 실제 로딩 상태와 어긋난다.
- 아이템에 key를 주지 않아서 갱신 시 상태가 섞인다.
- 추가 로딩 실패 후 자동 재시도 루프가 도는 설계(Boolean + distinct 없이 매번 트리거)를 만든다.

<br>

# 12. ViewModel 테스트

로직이 전부 ViewModel에 있어서 가드와 상태 전이를 단위 테스트로 검증하기 쉽다.
아래는 MockK + `runTest` 기준이며, `MainDispatcherRule`(Dispatchers.setMain을 설정하는 JUnit Rule)이 있다고 가정한다.

```kotlin
class PostListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<PostRepository>()

    private fun page(range: IntRange, hasNext: Boolean) = PageResult(
        items = range.map { Post(it.toLong(), "title $it", "content $it") },
        hasNext = hasNext,
    )

    @Test
    fun `초기 로딩이 끝나면 스켈레톤이 꺼지고 목록이 채워진다`() = runTest {
        coEvery { repository.getPosts(0, any()) } returns page(0..9, hasNext = true)

        val viewModel = PostListViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isInitialLoading)
        assertEquals(10, state.items.size)
        assertTrue(state.hasNext)
    }

    @Test
    fun `LoadMore는 다음 페이지를 뒤에 붙인다`() = runTest {
        coEvery { repository.getPosts(0, any()) } returns page(0..9, hasNext = true)
        coEvery { repository.getPosts(1, any()) } returns page(10..19, hasNext = false)

        val viewModel = PostListViewModel(repository)
        advanceUntilIdle()

        viewModel.onIntent(PostListIntent.LoadMore)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(20, state.items.size)
        assertFalse(state.hasNext)
    }

    @Test
    fun `마지막 페이지에서는 LoadMore가 요청을 보내지 않는다`() = runTest {
        coEvery { repository.getPosts(0, any()) } returns page(0..4, hasNext = false)

        val viewModel = PostListViewModel(repository)
        advanceUntilIdle()

        viewModel.onIntent(PostListIntent.LoadMore)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getPosts(any(), any()) }
    }

    @Test
    fun `Refresh는 목록을 첫 페이지로 교체한다`() = runTest {
        coEvery { repository.getPosts(0, any()) } returns page(0..9, hasNext = true)
        coEvery { repository.getPosts(1, any()) } returns page(10..19, hasNext = true)

        val viewModel = PostListViewModel(repository)
        advanceUntilIdle()
        viewModel.onIntent(PostListIntent.LoadMore)
        advanceUntilIdle()

        viewModel.onIntent(PostListIntent.Refresh)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(10, state.items.size)
        assertEquals(0, state.page)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `추가 로딩 실패 시 기존 목록은 유지되고 appendError가 켜진다`() = runTest {
        coEvery { repository.getPosts(0, any()) } returns page(0..9, hasNext = true)
        coEvery { repository.getPosts(1, any()) } throws IOException()

        val viewModel = PostListViewModel(repository)
        advanceUntilIdle()

        viewModel.onIntent(PostListIntent.LoadMore)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(10, state.items.size)
        assertTrue(state.appendError)
        assertFalse(state.isAppending)
    }
}
```

UI 쪽은 `PostListScreen`이 State와 콜백만 받기 때문에 Compose UI 테스트에서 State를 바꿔가며 스켈레톤/푸터/빈 화면 분기를 검증하면 된다.

<br>

# 정리

- 목록 로딩은 최초 / 새로고침 / 추가 세 종류로 State를 분리한다. `isLoading` 하나로 합치지 않는 것이 출발점이다.
- Pull-to-Refresh는 Material3 `PullToRefreshBox`에 ViewModel의 `isRefreshing`만 연결한다. 최초 로딩에는 쓰지 않고, 자식은 스크롤 가능한 컨테이너여야 한다.
- 무한 스크롤은 `snapshotFlow`로 `layoutInfo`를 감시한다. Boolean 대신 전체 개수를 방출해야 페이지가 추가된 뒤에도 다시 트리거되고, 실패 후 무한 재시도도 막는다.
- 중복 요청 방어는 UI가 아니라 ViewModel 한 곳(`isBusy`, `hasNext`)에서 한다.
- 새로고침은 진행 중인 추가 로딩 Job을 취소하고, 추가 결과는 `distinctBy`로 중복을 제거해서 레이스를 막는다.
- 스켈레톤은 실제 아이템과 같은 구조로 만들고, shimmer 애니메이션 값은 draw phase에서만 읽어 리컴포지션을 피한다.
- 에러는 최초 실패(전체 화면 + 재시도)와 추가 실패(푸터 재시도)를 구분하고, 새로고침 실패는 기존 목록을 유지한 채 스낵바만 띄운다.
- `CancellationException`은 반드시 다시 던진다.
- 목록 편집이 많고 단순한 페이지네이션이면 직접 구현, 오프라인 캐시나 대용량이면 Paging3를 선택한다.
