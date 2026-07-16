# Paging3

대용량 데이터를 한 번에 모두 가져오지 않고 필요한 만큼만 불러오기 위한 Jetpack 라이브러리이다.

RecyclerView뿐만 아니라 Jetpack Compose에서도 공식적으로 지원하며, 네트워크 API나 Room과 함께 자주 사용된다.

<br>

## 목차

1. Paging3란?
2. 왜 사용하는가?
3. Paging3의 구성 요소
4. 동작 과정
5. PagingSource 작성
6. Pager 생성
7. ViewModel에서 사용
8. Compose에서 사용
9. LoadState 처리
10. RemoteMediator
11. PagingConfig 옵션
12. Paging2와 차이점
13. 사용할 때 주의사항
14. 정리

<br>

## 1. Paging3란?

Paging3는 필요한 데이터만 조금씩 가져오는 라이브러리이다.

예를 들어 게시글이 10만 개 있다고 하자.

모든 데이터를 한 번에 가져오면

- 메모리 사용량 증가
- 네트워크 낭비
- 초기 로딩 시간 증가

라는 문제가 발생한다.

Paging3는 화면에 필요한 만큼만 데이터를 가져오고 스크롤하면 다음 데이터를 자동으로 요청한다.

```
1~20개 로드

↓

사용자가 스크롤

↓

21~40개 로드

↓

계속 반복
```

<br>

## 2. 왜 사용하는가?

기존 방식은 다음과 같다.

```kotlin
val posts = api.getPosts()
```

문제점

- 모든 데이터를 기다려야 한다.
- 메모리를 많이 사용한다.
- 서버 부하가 커질 수 있다.

Paging3는

```text
필요한 만큼만 요청

↓

사용자가 스크롤

↓

다음 페이지 요청
```

이라는 방식으로 동작한다.

대표적인 사용 사례

- 게시판
- 쇼핑몰 상품 목록
- 검색 결과
- 댓글 목록
- 채팅 기록
- 뉴스 목록

<br>

## 3. Paging3의 구성 요소

### PagingSource

데이터를 실제로 가져오는 클래스

```text
API

↓

PagingSource

↓

PagingData
```

<br>

### Pager

PagingSource를 생성하고 PagingData를 만든다.

```text
PagingSource

↓

Pager

↓

Flow<PagingData<T>>
```

<br>

### PagingData

페이지 단위 데이터

```kotlin
PagingData<Post>
```

<br>

### PagingDataAdapter

RecyclerView에서 사용하는 Adapter

Compose에서는 사용하지 않는다.

<br>

### LazyPagingItems

Compose에서 사용하는 객체

```kotlin
val items = flow.collectAsLazyPagingItems()
```

<br>

## 4. 동작 과정

```
사용자

↓

스크롤

↓

LazyColumn

↓

LazyPagingItems

↓

Pager

↓

PagingSource

↓

API
```

새로운 데이터가 필요하면 PagingSource의 `load()`가 자동 호출된다.

<br>

## 5. PagingSource 작성

예를 들어 페이지 기반 API라고 가정해보자.

```kotlin
class PostPagingSource(
    private val api: PostApi
) : PagingSource<Int, Post>() {

    override suspend fun load(
        params: LoadParams<Int>
    ): LoadResult<Int, Post> {

        return try {

            val page = params.key ?: 1

            val response = api.getPosts(
                page = page,
                size = params.loadSize
            )

            LoadResult.Page(
                data = response.items,
                prevKey = if (page == 1) null else page - 1,
                nextKey =
                    if (response.items.isEmpty())
                        null
                    else
                        page + 1
            )

        } catch (e: Exception) {

            LoadResult.Error(e)

        }
    }

    override fun getRefreshKey(
        state: PagingState<Int, Post>
    ): Int? {

        return state.anchorPosition?.let {

            state.closestPageToPosition(it)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(it)?.nextKey?.minus(1)

        }
    }
}
```

### load()

데이터를 가져오는 핵심 함수이다.

```kotlin
override suspend fun load(...)
```

자동으로 호출된다.

<br>

### params.key

현재 요청할 페이지 번호이다.

```kotlin
val page = params.key ?: 1
```

처음에는 null이므로 1페이지부터 시작한다.

<br>

### params.loadSize

이번에 몇 개를 가져올지 나타낸다.

```kotlin
params.loadSize
```

PagingConfig에 따라 달라진다.

<br>

### LoadResult.Page

성공 시 반환한다.

```kotlin
LoadResult.Page(
    data,
    prevKey,
    nextKey
)
```

<br>

### LoadResult.Error

실패 시 반환한다.

```kotlin
LoadResult.Error(exception)
```

<br>

## 6. Pager 생성

Repository에서 생성하는 경우가 가장 많다.

```kotlin
class PostRepository(
    private val api: PostApi
) {

    fun getPosts() =
        Pager(

            config = PagingConfig(
                pageSize = 20
            ),

            pagingSourceFactory = {
                PostPagingSource(api)
            }

        ).flow
}
```

반환 타입

```kotlin
Flow<PagingData<Post>>
```

<br>

## 7. ViewModel에서 사용

```kotlin
@HiltViewModel
class PostViewModel @Inject constructor(
    repository: PostRepository
) : ViewModel() {

    val posts =
        repository.getPosts()
            .cachedIn(viewModelScope)

}
```

### cachedIn()

```kotlin
.cachedIn(viewModelScope)
```

매우 중요한 함수이다.

화면 회전 등으로 Activity가 다시 생성되어도 이미 받아온 데이터를 재사용한다.

없으면 다시 처음부터 요청한다.

<br>

## 8. Compose에서 사용

```kotlin
@Composable
fun PostScreen(
    viewModel: PostViewModel = hiltViewModel()
) {

    val posts =
        viewModel.posts.collectAsLazyPagingItems()

    LazyColumn {

        items(posts.itemCount) { index ->

            posts[index]?.let {

                Text(it.title)

            }

        }

    }

}
```

### collectAsLazyPagingItems()

Flow를 Compose에서 사용할 수 있도록 변환한다.

```kotlin
val items =
    pagingData.collectAsLazyPagingItems()
```

<br>

### itemCount

현재 로드된 개수이다.

```kotlin
items.itemCount
```

<br>

### items[index]

필요한 순간 데이터를 가져온다.

```kotlin
items[index]
```

이 과정에서 다음 페이지 로드가 자동으로 시작될 수도 있다.

<br>

## 9. LoadState 처리

로딩 화면과 에러 화면은 LoadState로 처리한다.

```kotlin
when (posts.loadState.refresh) {

    is LoadState.Loading -> {

        CircularProgressIndicator()

    }

    is LoadState.Error -> {

        Text("에러 발생")

    }

    else -> Unit

}
```

<br>

추가 페이지 로딩

```kotlin
when (posts.loadState.append) {

    is LoadState.Loading -> {

        CircularProgressIndicator()

    }

    else -> Unit

}
```

대표 상태

|상태|설명|
|---|---|
|refresh|첫 로딩|
|append|다음 페이지|
|prepend|이전 페이지|

<br>

## 10. RemoteMediator

Paging3는 네트워크와 Room을 함께 사용할 수도 있다.

```
API

↓

RemoteMediator

↓

Room

↓

PagingSource

↓

UI
```

동작 순서

```
API 요청

↓

Room 저장

↓

Room에서 Paging

↓

UI 표시
```

장점

- 오프라인 지원
- 캐싱
- 데이터 재사용
- 네트워크 감소

대부분의 실무에서는 Room과 함께 사용하는 경우 RemoteMediator를 활용한다.

<br>

## 11. PagingConfig 옵션

```kotlin
PagingConfig(

    pageSize = 20,

    initialLoadSize = 40,

    prefetchDistance = 5,

    enablePlaceholders = false

)
```

### pageSize

한 번에 가져올 개수

```kotlin
pageSize = 20
```

<br>

### initialLoadSize

처음 가져올 개수

```kotlin
initialLoadSize = 40
```

보통 pageSize의 2~3배를 사용한다.

<br>

### prefetchDistance

남은 아이템이 몇 개일 때 다음 페이지를 요청할지 결정한다.

```kotlin
prefetchDistance = 5
```

예를 들어 20개씩 가져오고 `prefetchDistance = 5`라면, 15번째 아이템 부근에 도달했을 때 다음 페이지를 미리 요청한다.

<br>

### enablePlaceholders

아직 로드되지 않은 데이터를 빈 자리(placeholder)로 표시할지 여부이다.

```kotlin
enablePlaceholders = false
```

Compose에서는 일반적으로 `false`를 많이 사용한다.

<br>

## 12. Paging2와 차이점

|Paging2|Paging3|
|---|---|
|Coroutine 미지원|Coroutine 지원|
|Flow 미지원|Flow 지원|
|Rx 중심|Coroutine 중심|
|LoadState 없음|LoadState 지원|
|RemoteMediator 없음|RemoteMediator 지원|
|Compose 공식 지원 없음|Compose 공식 지원|

<br>

## 13. 사용할 때 주의사항

### cachedIn()을 사용하는 것이 좋다.

```kotlin
.cachedIn(viewModelScope)
```

화면 재생성 시 불필요한 재요청을 방지할 수 있다.

<br>

### PagingSource는 상태를 저장하지 않는다.

PagingSource는 필요할 때마다 새로 생성되므로 내부에 상태를 저장하지 않는 것이 좋다.

<br>

### Flow를 여러 번 collect하지 않는다.

```kotlin
val paging =
    repository.getPosts()
```

한 데이터를 여러 곳에서 수집하면 의도치 않은 재요청이 발생할 수 있다. 필요하다면 `cachedIn()`을 함께 사용한다.

<br>

### UI에서는 LazyPagingItems를 사용한다.

Compose에서는 `PagingData`를 직접 사용하는 것이 아니라 `collectAsLazyPagingItems()`로 변환하여 사용한다.

<br>

## 14. 정리

- Paging3는 대용량 데이터를 필요한 만큼만 가져오기 위한 Jetpack 라이브러리이다.
- `PagingSource`는 데이터를 가져오는 역할을 한다.
- `Pager`는 `Flow<PagingData<T>>`를 생성한다.
- Compose에서는 `collectAsLazyPagingItems()`를 사용한다.
- `LoadState`로 로딩과 에러 UI를 처리할 수 있다.
- `cachedIn()`을 사용하면 화면 재생성 시 데이터를 재사용할 수 있다.
- Room과 함께 사용할 경우 `RemoteMediator`를 통해 네트워크와 로컬 데이터를 효율적으로 동기화할 수 있다.
