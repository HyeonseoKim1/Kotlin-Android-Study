# ViewModel / MVI 테스트 (2) 이벤트, 시나리오, 동시성 테스트

1편에서 만든 `UserListViewModel`, `FakeUserRepository`, `UserFixture`, `MainDispatcherExtension`을 그대로 사용한다.
2편에서는 SideEffect, 성공 / 실패 시나리오, 디바운스, 취소와 동시성, SavedStateHandle, stateIn, Dispatcher 주입과 테스트 컨벤션을 다룬다.

<br>

# 목차

1. SideEffect 테스트
2. 로딩 / 성공 / 실패 시나리오
3. 디바운스와 가상 시간 제어
4. 취소와 동시성 테스트
5. SavedStateHandle 테스트
6. stateIn(WhileSubscribed) 테스트
7. Dispatcher 주입 패턴
8. 테스트 코드 구조와 컨벤션
9. 자주 만나는 에러와 해결법
10. 안티패턴
11. 체크리스트

<br>

# 1. SideEffect 테스트

## 1.1 SideEffect를 State와 분리하는 이유

- 화면 이동, 토스트, 스낵바는 한 번만 소비되어야 하는 이벤트다.
- State에 넣으면 화면 회전이나 재구독 때 이벤트가 다시 실행된다.
- Channel 또는 SharedFlow로 분리하고 테스트도 분리한다.

## 1.2 Channel vs SharedFlow

| 구분 | Channel + receiveAsFlow | MutableSharedFlow |
| --- | --- | --- |
| 구독자 없을 때 | 버퍼에 보관 후 첫 구독자에게 전달 | 기본 설정이면 유실 |
| 구독자 여러 명 | 하나만 소비 (fan-out) | 모두에게 전달 |
| 일회성 보장 | 높음 | replay 설정에 따라 다름 |
| 일반적 선택 | 화면 이벤트 | 여러 곳에서 구독하는 이벤트 |

## 1.3 기본 검증

```kotlin
@Test
fun `유저를 클릭하면 상세 화면으로 이동 이벤트가 발행된다`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.sideEffect.test {
        viewModel.onIntent(UserListIntent.ClickUser(id = 7))

        assertEquals(UserListSideEffect.NavigateToDetail(7), awaitItem())
        expectNoEvents()
        cancelAndIgnoreRemainingEvents()
    }
}
```

- `expectNoEvents()`로 이벤트가 중복 발행되지 않았음을 확인한다.
- Channel 버퍼 덕분에 구독 전에 발행된 이벤트도 받을 수 있다.

## 1.4 구독 전에 발행된 이벤트

```kotlin
@Test
fun `구독 전에 발행된 이벤트도 Channel 버퍼로 전달된다`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.onIntent(UserListIntent.ClickUser(id = 3))
    advanceUntilIdle()

    viewModel.sideEffect.test {
        assertEquals(UserListSideEffect.NavigateToDetail(3), awaitItem())
        cancelAndIgnoreRemainingEvents()
    }
}
```

SharedFlow(replay = 0)였다면 이 테스트는 실패한다.
이벤트 유실 정책이 코드와 테스트에서 일치하는지 확인하는 용도다.

## 1.5 이벤트 순서 검증

```kotlin
@Test
fun `이벤트가 발행 순서대로 전달된다`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.sideEffect.test {
        viewModel.onIntent(UserListIntent.ClickUser(1))
        viewModel.onIntent(UserListIntent.ClickUser(2))
        viewModel.onIntent(UserListIntent.ClickUser(3))

        assertEquals(UserListSideEffect.NavigateToDetail(1), awaitItem())
        assertEquals(UserListSideEffect.NavigateToDetail(2), awaitItem())
        assertEquals(UserListSideEffect.NavigateToDetail(3), awaitItem())
        cancelAndIgnoreRemainingEvents()
    }
}
```

## 1.6 State와 SideEffect를 함께 검증

```kotlin
@Test
fun `실패하면 에러 상태와 토스트가 함께 나온다`() = runTest {
    repository.result = Result.failure(IllegalStateException("서버 오류"))

    val viewModel = createViewModel()

    viewModel.sideEffect.test {
        assertEquals(UserListSideEffect.ShowToast("서버 오류"), awaitItem())
        cancelAndIgnoreRemainingEvents()
    }

    assertEquals("서버 오류", viewModel.state.value.errorMessage)
    assertFalse(viewModel.state.value.isLoading)
}
```

- `awaitItem()`에서 suspend되는 동안 init 코루틴이 실행된다.
- 이벤트를 받은 시점에는 이미 State도 갱신된 상태라 곧바로 검증할 수 있다.

## 1.7 헬퍼 확장 함수

```kotlin
suspend fun <T> Flow<T>.firstEvent(): T = test {
    val item = awaitItem()
    cancelAndIgnoreRemainingEvents()
    item
}
```

간단한 이벤트 한 건 검증이 반복되면 확장 함수로 줄일 수 있다.
다만 `expectNoEvents()` 같은 안전장치를 잃으므로 중요한 테스트에서는 `test {}`를 직접 쓴다.

<br>

# 2. 로딩 / 성공 / 실패 시나리오

## 2.1 시나리오 매트릭스

| 시나리오 | 준비 | 기대 State | 기대 SideEffect |
| --- | --- | --- | --- |
| 성공 | `Result.success(list)` | users = list, isLoading = false | 없음 |
| 빈 목록 | `Result.success(empty)` | users = empty, isLoading = false | 없음 |
| 실패 | `Result.failure(e)` | errorMessage = e.message | ShowToast |
| 메시지 없는 실패 | `Result.failure(Exception())` | 기본 오류 메시지 | ShowToast(기본) |
| 재시도 성공 | 실패 후 성공으로 교체 | errorMessage = null, users = list | 없음 |

## 2.2 성공

```kotlin
@Test
fun `성공하면 목록이 표시되고 로딩이 끝난다`() = runTest {
    repository.delayMillis = 100
    repository.result = Result.success(UserFixture.all)
    val viewModel = createViewModel()

    viewModel.state.test {
        assertEquals(UserListState(), awaitItem())
        assertEquals(UserListState(isLoading = true), awaitItem())
        assertEquals(
            UserListState(isLoading = false, users = UserFixture.all),
            awaitItem(),
        )
        cancelAndIgnoreRemainingEvents()
    }
}
```

## 2.3 빈 결과

```kotlin
@Test
fun `결과가 비어 있어도 정상 상태다`() = runTest {
    repository.result = Result.success(emptyList())
    val viewModel = createViewModel()

    advanceUntilIdle()

    val state = viewModel.state.value
    assertTrue(state.users.isEmpty())
    assertFalse(state.isLoading)
    assertNull(state.errorMessage)
}
```

빈 목록과 에러를 구분하는 것은 UI 분기의 핵심이라 별도 테스트로 둔다.

## 2.4 실패

```kotlin
@Test
fun `실패하면 에러 메시지가 상태에 저장된다`() = runTest {
    repository.result = Result.failure(IllegalStateException("서버 오류"))
    val viewModel = createViewModel()

    advanceUntilIdle()

    assertEquals(
        UserListState(isLoading = false, errorMessage = "서버 오류"),
        viewModel.state.value,
    )
}
```

## 2.5 메시지가 없는 예외

```kotlin
@Test
fun `예외 메시지가 없으면 기본 메시지를 사용한다`() = runTest {
    repository.result = Result.failure(RuntimeException())
    val viewModel = createViewModel()

    advanceUntilIdle()

    assertEquals(
        UserListViewModel.DEFAULT_ERROR_MESSAGE,
        viewModel.state.value.errorMessage,
    )
}
```

## 2.6 실패 후 재시도

```kotlin
@Test
fun `재시도하면 에러가 지워지고 목록이 표시된다`() = runTest {
    repository.result = Result.failure(IllegalStateException("서버 오류"))
    val viewModel = createViewModel()
    advanceUntilIdle()
    assertNotNull(viewModel.state.value.errorMessage)

    repository.result = Result.success(UserFixture.all)
    viewModel.onIntent(UserListIntent.Retry)
    advanceUntilIdle()

    assertEquals(
        UserListState(isLoading = false, users = UserFixture.all, errorMessage = null),
        viewModel.state.value,
    )
}
```

- 로딩 시작 시 `errorMessage = null`로 초기화하는 로직을 함께 검증한다.

## 2.7 Retry가 현재 검색어를 유지하는가

```kotlin
@Test
fun `재시도는 현재 검색어로 다시 조회한다`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.onIntent(UserListIntent.Search("ali"))
    advanceTimeBy(UserListViewModel.SEARCH_DEBOUNCE_MILLIS)
    runCurrent()
    repository.requests.clear()

    viewModel.onIntent(UserListIntent.Retry)
    advanceUntilIdle()

    assertEquals(listOf("ali"), repository.requests)
}
```

## 2.8 파라미터화 테스트로 여러 실패 케이스 묶기

```kotlin
@ParameterizedTest
@MethodSource("failureCases")
fun `실패 유형별 에러 메시지`(exception: Throwable, expectedMessage: String) = runTest {
    repository.result = Result.failure(exception)
    val viewModel = createViewModel()

    advanceUntilIdle()

    assertEquals(expectedMessage, viewModel.state.value.errorMessage)
}

companion object {
    @JvmStatic
    fun failureCases() = listOf(
        Arguments.of(IllegalStateException("서버 오류"), "서버 오류"),
        Arguments.of(IOException("네트워크 오류"), "네트워크 오류"),
        Arguments.of(RuntimeException(), UserListViewModel.DEFAULT_ERROR_MESSAGE),
    )
}
```

- 입력만 다르고 검증 구조가 같은 테스트는 `@ParameterizedTest`로 묶는다.
- `@MethodSource`가 가리키는 메서드는 companion object의 `@JvmStatic`이어야 한다.

## 2.9 MockK로 같은 시나리오 작성

```kotlin
class UserListViewModelMockkTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val repository = mockk<UserRepository>()

    @Test
    fun `성공 응답이면 목록이 표시된다`() = runTest {
        coEvery { repository.getUsers(any()) } returns Result.success(UserFixture.all)

        val viewModel = UserListViewModel(repository)
        advanceUntilIdle()

        assertEquals(UserFixture.all, viewModel.state.value.users)
        coVerify(exactly = 1) { repository.getUsers("") }
    }

    @Test
    fun `실패 응답이면 에러 메시지가 저장된다`() = runTest {
        coEvery { repository.getUsers(any()) } returns Result.failure(IllegalStateException("오류"))

        val viewModel = UserListViewModel(repository)
        advanceUntilIdle()

        assertEquals("오류", viewModel.state.value.errorMessage)
    }
}
```

- `coEvery`는 suspend 함수 stub, `coVerify`는 suspend 함수 호출 검증이다.
- `relaxed = true`를 쓰면 stub 없이도 기본값을 반환하지만 의도치 않은 호출을 놓치기 쉽다.

<br>

# 3. 디바운스와 가상 시간 제어

## 3.1 디바운스 로직 복습

```kotlin
queryFlow
    .drop(1)
    .debounce(SEARCH_DEBOUNCE_MILLIS)
    .distinctUntilChanged()
    .onEach { loadUsers(it) }
    .launchIn(viewModelScope)
```

- 입력이 멈춘 뒤 300ms가 지나야 조회한다.
- 연속 입력 중에는 마지막 값만 살아남는다.

## 3.2 경계값 테스트

```kotlin
@Test
fun `300ms가 지나기 전에는 검색하지 않는다`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()
    repository.requests.clear()

    viewModel.onIntent(UserListIntent.Search("a"))

    advanceTimeBy(299)
    runCurrent()
    assertTrue(repository.requests.isEmpty())

    advanceTimeBy(1)
    runCurrent()
    assertEquals(listOf("a"), repository.requests)
}
```

- 299ms 시점에는 요청이 없고 300ms 시점에 요청이 생기는 경계를 명시한다.
- `advanceTimeBy` 뒤에 `runCurrent()`를 붙여야 정각 작업이 실행된다.

## 3.3 연속 입력은 마지막 값만 조회한다

```kotlin
@Test
fun `연속 입력하면 마지막 검색어만 조회한다`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()
    repository.requests.clear()

    viewModel.onIntent(UserListIntent.Search("a"))
    advanceTimeBy(100)
    viewModel.onIntent(UserListIntent.Search("al"))
    advanceTimeBy(100)
    viewModel.onIntent(UserListIntent.Search("ali"))

    advanceUntilIdle()

    assertEquals(listOf("ali"), repository.requests)
}
```

- 이 테스트가 디바운스의 존재 이유를 직접 증명한다.
- 디바운스를 제거하면 요청이 3건이 되어 실패한다.

## 3.3.1 같은 검색어 반복은 무시된다

```kotlin
@Test
fun `같은 검색어를 반복해도 한 번만 조회한다`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()
    repository.requests.clear()

    viewModel.onIntent(UserListIntent.Search("ali"))
    advanceUntilIdle()
    viewModel.onIntent(UserListIntent.Search("ali"))
    advanceUntilIdle()

    assertEquals(listOf("ali"), repository.requests)
}
```

`distinctUntilChanged()` 동작 검증이다.

## 3.4 검색어는 즉시 State에 반영된다

```kotlin
@Test
fun `입력한 검색어는 디바운스와 무관하게 즉시 State에 반영된다`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.onIntent(UserListIntent.Search("a"))

    assertEquals("a", viewModel.state.value.query)
    assertTrue(repository.requests.size == 1) // init 요청만 존재
}
```

- 입력창 표시용 query와 서버 요청 시점을 분리한 설계를 검증한다.
- 사용자가 타이핑한 글자가 지연되어 보이면 UX 버그다.

## 3.5 delay를 직접 쓰는 코드

```kotlin
// 프로덕션 코드
private fun showTemporaryMessage() {
    viewModelScope.launch {
        reduce { copy(message = "저장됨") }
        delay(2_000)
        reduce { copy(message = null) }
    }
}
```

```kotlin
@Test
fun `메시지는 2초 후 사라진다`() = runTest {
    viewModel.showSavedMessage()
    runCurrent()
    assertEquals("저장됨", viewModel.state.value.message)

    advanceTimeBy(1_999)
    runCurrent()
    assertEquals("저장됨", viewModel.state.value.message)

    advanceTimeBy(1)
    runCurrent()
    assertNull(viewModel.state.value.message)
}
```

실제 2초를 기다리지 않고도 정확한 경계를 검증할 수 있다.

## 3.6 currentTime으로 경과 시간 검증

```kotlin
@Test
fun `재시도 백오프는 1초 2초 4초 간격이다`() = runTest {
    val attemptTimes = mutableListOf<Long>()
    val failing = object : UserRepository {
        override suspend fun getUsers(query: String): Result<List<User>> {
            attemptTimes.add(currentTime)
            return Result.failure(IOException())
        }
    }

    // retryWithBackoff 구현이 있다고 가정
    // advanceUntilIdle() 이후 attemptTimes를 검사
    advanceUntilIdle()

    assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L), attemptTimes)
}
```

- 시간 기반 로직(백오프, 타임아웃, 폴링)은 `currentTime`으로 정확히 검증한다.
- 실제 시간을 쓰는 테스트는 느리고 불안정해서 피한다.

<br>

# 4. 취소와 동시성 테스트

## 4.1 이전 요청 취소

프로덕션 코드는 새 로드가 시작되면 이전 `loadJob`을 취소한다.

```kotlin
@Test
fun `새 요청이 시작되면 이전 요청은 취소된다`() = runTest {
    repository.delayMillis = 1_000
    val viewModel = createViewModel() // init 요청 1
    runCurrent()

    advanceTimeBy(500)
    viewModel.onIntent(UserListIntent.Retry) // 요청 2, 요청 1은 취소
    advanceUntilIdle()

    assertEquals(2, repository.requests.size)
    assertEquals(1, repository.completedCount) // 완료된 건 요청 2뿐
}
```

- `completedCount`는 delay 이후에 증가하므로 취소된 요청은 반영되지 않는다.
- 취소 로직이 빠지면 `completedCount`가 2가 되어 실패한다.

## 4.2 오래된 응답이 최신 상태를 덮어쓰지 않는가

```kotlin
class SequencedFakeRepository : UserRepository {
    private val responses = ArrayDeque<Pair<Long, Result<List<User>>>>()

    fun enqueue(delayMillis: Long, result: Result<List<User>>) {
        responses.addLast(delayMillis to result)
    }

    override suspend fun getUsers(query: String): Result<List<User>> {
        val (delayMillis, result) = responses.removeFirst()
        delay(delayMillis)
        return result
    }
}
```

```kotlin
@Test
fun `느리게 도착한 이전 응답이 최신 결과를 덮어쓰지 않는다`() = runTest {
    val sequenced = SequencedFakeRepository()
    sequenced.enqueue(delayMillis = 0, result = Result.success(emptyList()))          // init
    sequenced.enqueue(delayMillis = 2_000, result = Result.success(listOf(UserFixture.alice))) // 느린 요청
    sequenced.enqueue(delayMillis = 100, result = Result.success(listOf(UserFixture.bob)))    // 빠른 요청

    val viewModel = UserListViewModel(sequenced)
    advanceUntilIdle()

    viewModel.onIntent(UserListIntent.Retry) // 느린 요청
    runCurrent()
    viewModel.onIntent(UserListIntent.Retry) // 빠른 요청, 느린 요청 취소
    advanceUntilIdle()

    assertEquals(listOf(UserFixture.bob), viewModel.state.value.users)
}
```

- race condition은 가상 시간으로 결정적으로 재현할 수 있다.
- 취소가 없다면 2초 뒤에 alice 결과가 bob을 덮어쓴다.

## 4.3 연타 방지

```kotlin
@Test
fun `같은 이벤트를 빠르게 여러 번 보내도 안전하다`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.sideEffect.test {
        repeat(5) { viewModel.onIntent(UserListIntent.ClickUser(1)) }

        repeat(5) {
            assertEquals(UserListSideEffect.NavigateToDetail(1), awaitItem())
        }
        cancelAndIgnoreRemainingEvents()
    }
}
```

- 중복 이동을 막는 로직이 있다면 기대값을 1건으로 바꿔 검증한다.
- 현재 코드는 방어 로직이 없어 5건 모두 발행된다. 이 테스트가 그 사실을 문서화한다.

## 4.4 CancellationException을 삼키지 않는가

```kotlin
// 잘못된 예: CancellationException까지 잡아버림
try {
    repository.getUsers(query)
} catch (e: Exception) {
    reduce { copy(errorMessage = e.message) }
}
```

```kotlin
@Test
fun `취소는 에러 상태로 처리되지 않는다`() = runTest {
    repository.delayMillis = 1_000
    val viewModel = createViewModel()
    runCurrent()

    viewModel.onIntent(UserListIntent.Retry) // 이전 요청 취소
    advanceUntilIdle()

    assertNull(viewModel.state.value.errorMessage)
}
```

- `catch (e: Exception)`은 `CancellationException`도 잡아 취소를 방해한다.
- `Result` 기반 반환이나 `ensureActive()`, `CancellationException` 재던지기로 처리한다.
- 테스트로 취소가 에러 상태로 새지 않음을 확인한다.

## 4.5 viewModelScope 취소 시점 검증

```kotlin
@Test
fun `ViewModel이 clear되면 진행 중 작업이 취소된다`() = runTest {
    repository.delayMillis = 10_000
    val viewModel = createViewModel()
    runCurrent()

    // onCleared는 protected라 리플렉션 대신 viewModelScope를 직접 취소
    viewModel.viewModelScope.cancel()
    advanceUntilIdle()

    assertEquals(0, repository.completedCount)
}
```

- `ViewModel.clear()`는 내부 API라 테스트에서 직접 호출하기 어렵다.
- `viewModelScope.cancel()`로 같은 효과를 낸다.

<br>

# 5. SavedStateHandle 테스트

## 5.1 프로덕션 코드

```kotlin
@HiltViewModel
class UserDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: UserDetailRepository,
) : ViewModel() {

    private val userId: Long = checkNotNull(savedStateHandle[KEY_USER_ID])

    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    fun updateQuery(value: String) {
        savedStateHandle[KEY_QUERY] = value
    }

    companion object {
        const val KEY_USER_ID = "userId"
        const val KEY_QUERY = "query"
    }
}
```

## 5.2 SavedStateHandle은 순수 JVM에서 생성 가능

```kotlin
@Test
fun `네비게이션 인자로 userId를 받는다`() = runTest {
    val handle = SavedStateHandle(mapOf(UserDetailViewModel.KEY_USER_ID to 42L))
    val viewModel = UserDetailViewModel(handle, FakeUserDetailRepository())

    // userId를 사용하는 동작 검증
    advanceUntilIdle()
}
```

- `SavedStateHandle(Map)` 생성자로 Android 없이 만들 수 있다.
- 인자가 없는 경우를 검증하려면 빈 Map으로 만들어 예외를 확인한다.

## 5.3 필수 인자 누락

```kotlin
@Test
fun `userId가 없으면 생성 시 예외가 발생한다`() {
    assertThrows<IllegalStateException> {
        UserDetailViewModel(SavedStateHandle(), FakeUserDetailRepository())
    }
}
```

## 5.4 프로세스 종료 후 복원 시뮬레이션

```kotlin
@Test
fun `프로세스 복원 후 검색어가 유지된다`() = runTest {
    val restoredHandle = SavedStateHandle(
        mapOf(
            UserDetailViewModel.KEY_USER_ID to 1L,
            UserDetailViewModel.KEY_QUERY to "ali",
        )
    )

    val viewModel = UserDetailViewModel(restoredHandle, FakeUserDetailRepository())

    viewModel.query.test {
        assertEquals("ali", awaitItem())
        cancelAndIgnoreRemainingEvents()
    }
}
```

- 이미 값이 들어 있는 `SavedStateHandle`을 넘기는 것이 프로세스 복원과 같은 상황이다.
- 복원 로직은 실기기에서 재현하기 어려워 단위 테스트의 가치가 크다.

## 5.5 값 변경이 StateFlow에 반영되는가

```kotlin
@Test
fun `updateQuery가 StateFlow에 반영된다`() = runTest {
    val handle = SavedStateHandle(mapOf(UserDetailViewModel.KEY_USER_ID to 1L))
    val viewModel = UserDetailViewModel(handle, FakeUserDetailRepository())

    viewModel.query.test {
        assertEquals("", awaitItem())

        viewModel.updateQuery("kotlin")
        assertEquals("kotlin", awaitItem())

        cancelAndIgnoreRemainingEvents()
    }
}
```

<br>

# 6. stateIn(WhileSubscribed) 테스트

## 6.1 프로덕션 코드

```kotlin
val uiState: StateFlow<UserUiState> = repository.observeUsers()
    .map<List<User>, UserUiState> { UserUiState.Success(it) }
    .catch { emit(UserUiState.Error(it.message)) }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserUiState.Loading,
    )
```

## 6.2 구독자가 없으면 upstream이 시작되지 않는다

```kotlin
@Test
fun `구독하지 않으면 초기 값만 보인다`() = runTest {
    val viewModel = UserStreamViewModel(FakeUserStreamRepository())

    assertEquals(UserUiState.Loading, viewModel.uiState.value)
}
```

`WhileSubscribed`는 구독자가 생겨야 upstream을 실행한다.
그래서 `uiState.value`만 읽으면 갱신되지 않는다.

## 6.3 backgroundScope에서 collect를 유지

```kotlin
@Test
fun `구독하면 upstream 값이 상태로 반영된다`() = runTest {
    val repo = FakeUserStreamRepository()
    val viewModel = UserStreamViewModel(repo)

    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.uiState.collect()
    }

    repo.emit(listOf(UserFixture.alice))

    assertEquals(
        UserUiState.Success(listOf(UserFixture.alice)),
        viewModel.uiState.value,
    )
}
```

- 빈 `collect()`로 구독자를 만들어 upstream을 활성화한다.
- `UnconfinedTestDispatcher(testScheduler)`로 즉시 구독을 시작한다.
- `backgroundScope`이므로 테스트 종료 시 자동으로 정리된다.

## 6.4 구독 해지 후 5초 유예

```kotlin
@Test
fun `마지막 구독자가 사라지고 5초가 지나면 upstream이 멈춘다`() = runTest {
    val repo = FakeUserStreamRepository()
    val viewModel = UserStreamViewModel(repo)

    val job = launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.uiState.collect()
    }
    assertEquals(1, repo.activeCollectors)

    job.cancel()
    advanceTimeBy(4_999)
    assertEquals(1, repo.activeCollectors)

    advanceTimeBy(1)
    runCurrent()
    assertEquals(0, repo.activeCollectors)
}
```

- 화면 회전 동안 upstream을 유지하려는 5초 유예 정책을 가상 시간으로 검증한다.

## 6.5 Turbine과 함께

```kotlin
@Test
fun `에러가 Error 상태로 변환된다`() = runTest {
    val repo = FakeUserStreamRepository()
    val viewModel = UserStreamViewModel(repo)

    viewModel.uiState.test {
        assertEquals(UserUiState.Loading, awaitItem())

        repo.fail(IllegalStateException("스트림 오류"))

        assertEquals(UserUiState.Error("스트림 오류"), awaitItem())
        cancelAndIgnoreRemainingEvents()
    }
}
```

`test {}`가 구독자 역할을 하므로 별도의 collect가 필요 없다.

<br>

# 7. Dispatcher 주입 패턴

## 7.1 하드코딩된 Dispatcher의 문제

```kotlin
// 테스트하기 어려운 코드
class UserRepositoryImpl(
    private val api: UserApi,
) : UserRepository {

    override suspend fun getUsers(query: String): Result<List<User>> =
        withContext(Dispatchers.IO) {
            runCatching { api.getUsers(query).map { it.toDomain() } }
        }
}
```

- `Dispatchers.IO`는 테스트 스케줄러와 연결되지 않는다.
- 가상 시간이 적용되지 않아 `delay`가 실제 시간으로 흐를 수 있다.
- 테스트가 실제 스레드에 의존해 불안정해진다.

## 7.2 Dispatcher를 주입받는 구조

```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
```

```kotlin
class UserRepositoryImpl @Inject constructor(
    private val api: UserApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UserRepository {

    override suspend fun getUsers(query: String): Result<List<User>> =
        withContext(ioDispatcher) {
            runCatching { api.getUsers(query).map { it.toDomain() } }
        }
}
```

## 7.3 테스트에서 스케줄러를 공유해 주입

```kotlin
@Test
fun `주입한 Dispatcher가 테스트 스케줄러를 따른다`() = runTest {
    val api = FakeUserApi(delayMillis = 5_000)
    val repository = UserRepositoryImpl(
        api = api,
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    val start = currentTime
    val result = repository.getUsers("")

    assertTrue(result.isSuccess)
    assertEquals(5_000L, currentTime - start)
}
```

- `StandardTestDispatcher(testScheduler)`로 같은 스케줄러를 공유한다.
- 5초짜리 작업이 실제로는 즉시 끝나고 `currentTime`만 5초 이동한다.

## 7.4 Hilt 테스트 모듈로 교체하지 않는 이유

- ViewModel 단위 테스트에서는 Hilt를 쓰지 않고 생성자로 직접 주입한다.
- Hilt 테스트(`@HiltAndroidTest`)는 통합 테스트 용도로 분리한다.
- 단위 테스트가 Hilt에 의존하면 느리고 설정이 복잡해진다.

## 7.5 withContext 안에서의 Main 이동

```kotlin
// 프로덕션 코드
viewModelScope.launch {
    val result = withContext(defaultDispatcher) { heavyCompute() }
    reduce { copy(result = result) }
}
```

- `withContext`로 다른 Dispatcher에 갔다 와도 결과는 viewModelScope(Main) 컨텍스트에서 이어진다.
- 테스트에서는 `defaultDispatcher`에 `StandardTestDispatcher(testScheduler)`를 주입하면 된다.

<br>

# 8. 테스트 코드 구조와 컨벤션

## 8.1 Given - When - Then

```kotlin
@Test
fun `검색어를 입력하면 해당 검색어로 목록을 조회한다`() = runTest {
    // given
    val viewModel = createViewModel()
    advanceUntilIdle()
    repository.requests.clear()

    // when
    viewModel.onIntent(UserListIntent.Search("ali"))
    advanceUntilIdle()

    // then
    assertEquals(listOf("ali"), repository.requests)
}
```

- 준비, 실행, 검증을 주석이나 빈 줄로 구분한다.
- 하나의 테스트는 하나의 동작만 검증한다.

## 8.2 테스트 이름 규칙

| 방식 | 예시 |
| --- | --- |
| 백틱 한글 문장 | `` `실패하면 에러 메시지가 상태에 저장된다` `` |
| 상태 기반 | `loadUsers_whenFailure_setsErrorMessage` |
| 시나리오 기반 | `given_network_error_when_retry_then_success` |

- 팀 컨벤션이 없으면 한글 백틱 문장이 읽기 가장 쉽다.
- 이름만 읽어도 어떤 요구사항이 깨졌는지 알 수 있게 쓴다.
- JVM 테스트에서만 백틱 이름이 가능하다. `androidTest`(Dex)에서는 쓸 수 없다.

## 8.3 중첩 클래스로 그룹화

```kotlin
class UserListViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private lateinit var repository: FakeUserRepository

    @BeforeEach
    fun setUp() {
        repository = FakeUserRepository()
    }

    private fun createViewModel() = UserListViewModel(repository)

    @Nested
    inner class 초기_로드 {

        @Test
        fun `성공하면 목록이 표시된다`() = runTest { /* ... */ }

        @Test
        fun `실패하면 에러가 표시된다`() = runTest { /* ... */ }
    }

    @Nested
    inner class 검색 {

        @Test
        fun `디바운스 후 조회한다`() = runTest { /* ... */ }

        @Test
        fun `연속 입력은 마지막 값만 조회한다`() = runTest { /* ... */ }
    }

    @Nested
    inner class 이벤트 {

        @Test
        fun `클릭하면 이동 이벤트가 발행된다`() = runTest { /* ... */ }
    }
}
```

- JUnit5 `@Nested`는 `inner class`로 선언해야 바깥 클래스의 필드를 쓴다.
- 테스트 리포트가 기능 단위로 묶여 보인다.

## 8.4 공통 유틸 분리

```kotlin
// core/testing
fun <T> Flow<T>.testIn(scope: TestScope) = testIn(scope.backgroundScope)

abstract class ViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()
}
```

```kotlin
class UserListViewModelTest : ViewModelTest() {
    // Extension 선언 없이 바로 사용
}
```

- 모든 ViewModel 테스트가 반복하는 설정은 상위 클래스로 올린다.
- 상속이 부담스러우면 Extension만 별도 파일로 두고 각 테스트에서 등록한다.

## 8.5 fixture 빌더

```kotlin
fun userState(
    isLoading: Boolean = false,
    query: String = "",
    users: List<User> = emptyList(),
    errorMessage: String? = null,
) = UserListState(isLoading, query, users, errorMessage)
```

```kotlin
assertEquals(userState(users = UserFixture.all), viewModel.state.value)
```

- 기본값을 가진 빌더를 두면 변경된 필드만 드러나 의도가 선명해진다.
- State 필드가 늘어나도 테스트 수정이 최소화된다.

## 8.6 테스트 독립성

1. 테스트 간에 공유되는 가변 상태를 만들지 않는다.
2. `@BeforeEach`에서 Fake와 ViewModel을 새로 만든다.
3. 테스트 실행 순서에 의존하지 않는다.
4. `Dispatchers.setMain`은 `resetMain`으로 반드시 정리한다.

## 8.7 어떤 수준까지 테스트할까

| 대상 | 테스트 여부 | 이유 |
| --- | --- | --- |
| 상태 전이 로직 | 필수 | 핵심 비즈니스 흐름 |
| 에러 / 빈 상태 분기 | 필수 | UI 분기의 기준 |
| 디바운스, 취소 | 권장 | 동시성 버그 방지 |
| 단순 getter, 위임 | 생략 | 가치 대비 비용이 낮음 |
| 프레임워크 동작 | 생략 | 라이브러리가 보장 |

<br>

# 9. 자주 만나는 에러와 해결법

## 9.1 Main dispatcher 초기화 실패

```text
IllegalStateException: Module with the Main dispatcher had failed to initialize.
```

- 원인: `Dispatchers.setMain`을 하지 않았다.
- 해결: `MainDispatcherExtension` 또는 `MainDispatcherRule`을 등록한다.

## 9.2 Unconsumed events found

```text
AssertionError: Unconsumed events found:
 - Item(UserListState(...))
```

- 원인: Turbine 블록이 끝났는데 소비하지 않은 emit이 남았다.
- 해결: 필요한 `awaitItem()`을 추가하거나 `cancelAndIgnoreRemainingEvents()`를 호출한다.
- 의도하지 않은 추가 emit이라면 프로덕션 코드의 불필요한 상태 갱신을 의심한다.

## 9.3 Turbine 타임아웃

```text
AssertionError: Timed out waiting for 3000 ms
```

- 원인: 기다리는 emit이 오지 않는다.
- 점검 순서:
  1. 코루틴이 아직 큐에 대기 중인가 (`runCurrent`, `advanceUntilIdle` 누락)
  2. 구독자가 없어 `WhileSubscribed` upstream이 시작되지 않았는가
  3. 다른 스케줄러를 쓰는 Dispatcher가 섞여 있는가
  4. 예외로 코루틴이 이미 종료되었는가

## 9.4 UncompletedCoroutinesError

```text
UncompletedCoroutinesError: After waiting for 60000 ms, the test coroutine is not completing
```

- 원인: 끝나지 않는 코루틴이 테스트 스코프에 남았다.
- 대표 사례: `launch { flow.collect {} }`를 `backgroundScope`가 아닌 `TestScope`에 띄움.
- 해결: `backgroundScope.launch`로 옮기거나 Turbine의 `test {}`를 쓴다.

## 9.5 IllegalStateException: This job has not completed yet

- 원인: 테스트 본문에서 아직 끝나지 않은 코루틴의 결과를 읽으려 했다.
- 해결: 결과를 읽기 전에 `advanceUntilIdle()`로 작업을 끝낸다.

## 9.6 assert는 통과하는데 가끔 실패

- 원인 후보
  1. `delay`가 없는 Fake라서 중간 상태가 conflation으로 사라짐
  2. `Dispatchers.Unconfined`와 Standard가 섞여 실행 순서가 달라짐
  3. 실제 시간(`Thread.sleep`, 실제 Dispatcher)에 의존
- 해결: 모든 Dispatcher를 `testScheduler` 기반으로 통일하고 시간 제어를 명시한다.

## 9.7 runTest 안에서 setMain을 호출한 경우

- `runTest`가 시작된 뒤에 `setMain`을 호출하면 스케줄러가 공유되지 않는다.
- 항상 Extension/Rule로 테스트 시작 전에 Main을 교체한다.

## 9.8 MockK 관련

| 에러 | 원인 | 해결 |
| --- | --- | --- |
| `no answer found for ...` | stub 없이 호출됨 | `coEvery`로 응답 지정 또는 `relaxed = true` |
| `Verification failed` | 인자가 기대와 다름 | `match {}`, `any()`로 조건 확인 |
| suspend 함수에 `every` 사용 | suspend는 `coEvery` 필요 | `coEvery`, `coVerify` 사용 |

<br>

# 10. 안티패턴

## 10.1 실제 시간을 기다리기

```kotlin
// 나쁜 예
@Test
fun slowTest() {
    viewModel.onIntent(UserListIntent.Search("a"))
    Thread.sleep(500)
    assertEquals(...)
}
```

- 느리고 환경에 따라 결과가 달라진다.
- `runTest`와 `advanceTimeBy`로 가상 시간을 쓴다.

## 10.2 runBlocking으로 대체하기

```kotlin
// 나쁜 예
@Test
fun test() = runBlocking {
    viewModel.load()
    delay(1_000)
}
```

- `runBlocking`은 가상 시간을 쓰지 못하고 실제로 1초를 기다린다.
- 코루틴 테스트에서는 `runTest`를 쓴다.

## 10.3 구현 세부사항에 결합된 검증

```kotlin
// 나쁜 예
coVerify { repository.getUsers("") }
coVerify { repository.getUsers("a") }
coVerify(exactly = 0) { repository.other() }
```

- 내부 호출 순서나 횟수가 바뀌면 동작이 같아도 테스트가 깨진다.
- 사용자가 관찰할 수 있는 결과(State, SideEffect) 중심으로 검증한다.

## 10.4 하나의 테스트에 너무 많은 검증

```kotlin
// 나쁜 예
@Test
fun everything() = runTest {
    // 로드, 검색, 재시도, 클릭, 에러를 한 테스트에서 모두 검증
}
```

- 실패했을 때 어떤 동작이 깨졌는지 알기 어렵다.
- 시나리오별로 테스트를 나눈다.

## 10.5 private 메서드 직접 테스트

- reflection으로 private 함수를 호출하는 테스트는 리팩터링에 취약하다.
- public 인터페이스(Intent 입력, State 출력)만 검증한다.

## 10.6 Mock 남발

```kotlin
// 나쁜 예: ViewModel 안의 협력 객체를 모두 mockk로 대체하고 호출만 검증
val repo = mockk<UserRepository>(relaxed = true)
```

- `relaxed = true`는 stub 누락을 가려 실제 버그를 놓치게 한다.
- 반환값이 있는 협력 객체는 Fake를 우선 고려한다.

## 10.7 Dispatchers.setMain 정리 누락

- `resetMain()`을 빼먹으면 다른 테스트에 상태가 새어나가 간헐적 실패가 생긴다.
- Extension/Rule에서 `afterEach`/`finished`로 반드시 정리한다.

## 10.8 StateFlow 중간 값에 과도하게 의존

- `StateFlow`는 conflation 때문에 중간 값이 사라질 수 있다.
- 최종 상태 중심으로 검증하고, 전이 순서가 정말 계약일 때만 Turbine으로 순서를 고정한다.

## 10.9 테스트에서 프로덕션 로직 복제

```kotlin
// 나쁜 예
val expected = users.filter { it.name.contains(query) }.sortedBy { it.name }
assertEquals(expected, state.users)
```

- 프로덕션과 같은 계산을 테스트에서 다시 하면 버그도 같이 복제된다.
- 기대값은 하드코딩된 리터럴로 명시한다.

<br>

# 11. 체크리스트

## 11.1 테스트 작성 전

1. ViewModel의 입력(Intent)과 출력(State, SideEffect)을 목록으로 정리했는가
2. 협력 객체가 인터페이스로 분리되어 Fake를 만들 수 있는가
3. Dispatcher가 하드코딩되어 있지 않은가
4. 시간에 의존하는 로직(debounce, delay, timeout)을 식별했는가

## 11.2 설정

1. `MainDispatcherExtension`(또는 Rule)을 등록했는가
2. `runTest`와 Main이 같은 스케줄러를 공유하는가
3. Fake 상태를 `@BeforeEach`에서 초기화하는가
4. Hilt 없이 생성자로 ViewModel을 만들 수 있는가

## 11.3 시나리오 커버리지

1. 초기 상태
2. 로딩 -> 성공
3. 로딩 -> 실패 (메시지 있음 / 없음)
4. 빈 결과
5. 재시도
6. 디바운스 경계 (299ms / 300ms)
7. 연속 입력과 중복 입력
8. 이전 요청 취소와 오래된 응답 덮어쓰기 방지
9. SideEffect 발행, 순서, 중복 없음
10. SavedStateHandle 복원

## 11.4 리뷰 시 점검

1. 실제 시간을 기다리는 코드(`Thread.sleep`, `runBlocking + delay`)가 없는가
2. Turbine 블록 끝에 소비되지 않은 이벤트가 없는가
3. `cancelAndIgnoreRemainingEvents()`를 습관적으로 쓰고 있지 않은가
4. Mock 검증이 구현 세부사항에 과하게 묶여 있지 않은가
5. 테스트 이름만 읽어도 요구사항이 드러나는가
6. 하나의 테스트가 하나의 동작만 검증하는가

## 11.5 자주 쓰는 코드 모음

```kotlin
// 1) 최종 상태 검증
advanceUntilIdle()
assertEquals(expectedState, viewModel.state.value)

// 2) 전이 순서 검증
viewModel.state.test {
    assertEquals(initial, awaitItem())
    assertEquals(loading, awaitItem())
    assertEquals(success, awaitItem())
    cancelAndIgnoreRemainingEvents()
}

// 3) 이벤트 검증
viewModel.sideEffect.test {
    viewModel.onIntent(intent)
    assertEquals(expectedEffect, awaitItem())
    expectNoEvents()
    cancelAndIgnoreRemainingEvents()
}

// 4) 디바운스 경계
advanceTimeBy(299); runCurrent()
// 아직 호출 없음
advanceTimeBy(1); runCurrent()
// 호출 발생

// 5) WhileSubscribed 구독 유지
backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
    viewModel.uiState.collect()
}
```

<br>

# 정리

## 핵심 개념

1. SideEffect는 State와 분리해 Channel로 발행하고 Turbine으로 발행 여부, 순서, 중복 없음을 검증한다.
2. 디바운스 같은 시간 로직은 `advanceTimeBy` + `runCurrent()`로 경계값(299ms / 300ms)을 정확히 검증한다.
3. 이전 요청 취소와 오래된 응답 덮어쓰기는 지연이 다른 Fake로 결정적으로 재현한다.
4. `stateIn(WhileSubscribed)`는 구독자가 있어야 동작하므로 `backgroundScope`에서 collect를 유지한다.
5. Dispatcher는 생성자로 주입하고 테스트에서는 `StandardTestDispatcher(testScheduler)`를 넣는다.

## 반드시 검증할 시나리오

1. 성공 / 실패 / 빈 결과의 상태 전이
2. 실패 후 재시도 시 에러 초기화
3. 디바운스 경계와 연속 입력
4. 이전 요청 취소와 race condition
5. SideEffect 중복 없음과 순서
6. SavedStateHandle 복원

## 피해야 할 것

1. `Thread.sleep`, `runBlocking + delay`
2. `relaxed` Mock 남발과 구현 세부사항 검증
3. 한 테스트에 여러 동작 섞기
4. `setMain` 정리 누락
5. 프로덕션 로직을 테스트에서 복제
