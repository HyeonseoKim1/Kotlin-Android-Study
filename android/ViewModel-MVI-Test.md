# ViewModel / MVI 테스트 (1) 코루틴 테스트 기본과 상태 검증

Android ViewModel과 MVI 구조를 JVM 단위 테스트로 검증하는 방법을 정리한다.
1편에서는 테스트 환경 설정, 코루틴 테스트 API, 예제 ViewModel, Fake와 Mock, 상태 검증, Turbine 사용법을 다룬다.
이벤트, 디바운스, 취소, SavedStateHandle 같은 심화 시나리오는 2편에서 이어진다.

<br>

# 목차

1. 왜 ViewModel 테스트인가
2. 의존성 설정
3. 코루틴 테스트 기본
4. TestDispatcher 종류와 선택 기준
5. Dispatchers.setMain 규칙
6. MVI 구조 복습
7. 테스트 대상 예제 코드
8. Fake와 Mock 선택 기준
9. 상태(State) 테스트
10. Turbine 사용법

<br>

# 1. 왜 ViewModel 테스트인가

## 1.1 ViewModel이 테스트 가치가 높은 이유

- 화면의 상태(State) 전이와 사용자 입력(Intent) 처리 로직이 모여 있다.
- Android 프레임워크 의존이 적어 JVM 로컬 테스트로 빠르게 검증할 수 있다.
- UI 테스트보다 빠르고 안정적이며 실패 원인 파악이 쉽다.
- MVI에서는 `Intent -> State` 변환이 순수 함수에 가까워 테스트가 명확하다.

## 1.2 테스트 피라미드에서의 위치

| 계층 | 도구 | 속도 | 비용 |
| --- | --- | --- | --- |
| 단위 테스트 (ViewModel, UseCase) | JUnit, MockK, Turbine | 매우 빠름 | 낮음 |
| 통합 테스트 (Repository + Room) | Robolectric, in-memory DB | 보통 | 보통 |
| UI 테스트 (Compose) | createComposeRule | 느림 | 높음 |
| E2E | 실제 기기, UI Automator | 매우 느림 | 매우 높음 |

## 1.3 ViewModel 테스트에서 검증할 것

1. 초기 상태가 올바른가
2. Intent를 받았을 때 State가 기대한 순서로 바뀌는가
3. 일회성 이벤트(SideEffect)가 정확히 한 번 발행되는가
4. 실패 시 에러 상태와 이벤트가 올바른가
5. Repository 같은 협력 객체를 올바른 인자로 호출하는가
6. 연속 입력, 취소, 재시도 같은 동시성 상황에서 결과가 꼬이지 않는가

## 1.4 ViewModel 테스트에서 검증하지 않을 것

- Compose 화면이 어떻게 그려지는지 (UI 테스트의 영역)
- Retrofit, Room 같은 라이브러리 내부 동작
- Repository 구현체의 네트워크 통신 (별도 테스트)
- 프레임워크가 보장하는 동작 (`viewModelScope`가 취소되는 시점 등)

<br>

# 2. 의존성 설정

## 2.1 버전 카탈로그 (libs.versions.toml)

```toml
[versions]
kotlin = "2.0.21"
coroutines = "1.9.0"
turbine = "1.2.0"
mockk = "1.13.13"
junit5 = "5.11.3"
truth = "1.4.4"

[libraries]
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
junit5-api = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "junit5" }
junit5-engine = { module = "org.junit.jupiter:junit-jupiter-engine", version.ref = "junit5" }
junit5-params = { module = "org.junit.jupiter:junit-jupiter-params", version.ref = "junit5" }
truth = { module = "com.google.truth:truth", version.ref = "truth" }
```

버전은 작성 시점 기준 예시이므로 실제 적용 전에 최신 버전을 확인한다.

## 2.2 모듈 build.gradle.kts

```kotlin
dependencies {
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.truth)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

## 2.3 Android 모듈에서 JUnit5를 쓸 때 주의

- Android Gradle Plugin은 기본적으로 JUnit4 기준이다.
- 순수 Kotlin(JVM) 모듈이면 `useJUnitPlatform()`만으로 JUnit5가 동작한다.
- Android 라이브러리 / 앱 모듈에서는 `mannodermaus/android-junit5` 플러그인이 필요하다.
- Compose UI 테스트(`createComposeRule`)는 JUnit4 Rule 기반이라 `androidTest`에서는 JUnit4를 쓴다.
- ViewModel 단위 테스트는 `test` 소스셋에서 JUnit5를 써도 문제없다.

## 2.4 멀티모듈에서의 위치

- 테스트 대상 ViewModel이 있는 feature 모듈의 `src/test`에 둔다.
- 공통 테스트 유틸(MainDispatcherExtension, Fake, fixture)은 `core:testing` 모듈로 분리한다.
- `core:testing`은 `testImplementation(projects.core.testing)`으로 각 모듈에서 가져온다.

```kotlin
// core/testing/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.junit5.api)
    api(libs.mockk)
}
```

<br>

# 3. 코루틴 테스트 기본

## 3.1 runTest

`runTest`는 코루틴 테스트의 진입점이다.

- 테스트 본문을 `TestScope`에서 실행한다.
- `delay`를 실제로 기다리지 않고 가상 시간으로 건너뛴다.
- 테스트 본문이 끝날 때까지 실행 중인 자식 코루틴이 완료되길 기다린다.
- 끝나지 않는 코루틴이 남아 있으면 실패한다.

```kotlin
@Test
fun `delay는 가상 시간으로 건너뛴다`() = runTest {
    val start = currentTime

    delay(10_000)

    assertEquals(10_000L, currentTime - start)
}
```

위 테스트는 실제로는 몇 ms 만에 끝난다.

## 3.2 TestScope와 TestCoroutineScheduler

| 구성 요소 | 역할 |
| --- | --- |
| `TestScope` | 테스트용 CoroutineScope. `runTest`의 receiver |
| `TestCoroutineScheduler` | 가상 시간과 대기 중인 작업 큐를 관리 |
| `TestDispatcher` | 스케줄러에 작업을 올리는 Dispatcher |
| `testScheduler` | `TestScope`가 가진 스케줄러. 다른 Dispatcher와 공유할 때 사용 |
| `currentTime` | 현재 가상 시간(ms) |
| `backgroundScope` | 테스트 종료 시 자동 취소되는 스코프 |

## 3.3 시간 제어 함수

```kotlin
@Test
fun `시간 제어 함수 비교`() = runTest {
    var count = 0
    launch {
        delay(1_000)
        count++
    }

    // 아직 아무것도 실행되지 않음
    assertEquals(0, count)

    advanceTimeBy(999)   // 999ms 시점까지 이동, 그 시각에 예정된 작업은 실행하지 않음
    assertEquals(0, count)

    advanceTimeBy(1)     // 1000ms 시점으로 이동하지만 정확히 1000ms 작업은 아직 대기
    runCurrent()         // 현재 시각에 예정된 작업 실행
    assertEquals(1, count)
}
```

| 함수 | 동작 |
| --- | --- |
| `advanceTimeBy(ms)` | 가상 시간을 ms만큼 이동. 이동한 시각 이전에 예정된 작업만 실행 |
| `runCurrent()` | 현재 가상 시각에 예정된 작업을 실행 |
| `advanceUntilIdle()` | 대기 중인 모든 작업이 끝날 때까지 시간을 진행 |

핵심은 `advanceTimeBy`가 목표 시각 정각의 작업은 실행하지 않는다는 점이다.
정각 작업까지 실행하려면 `runCurrent()`를 이어서 호출한다.

## 3.4 backgroundScope

테스트 안에서 끝나지 않는 collect를 돌려야 할 때 사용한다.

```kotlin
@Test
fun `backgroundScope는 테스트 종료 시 자동 취소된다`() = runTest {
    val values = mutableListOf<Int>()
    val flow = MutableSharedFlow<Int>()

    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        flow.collect { values.add(it) }
    }

    flow.emit(1)
    flow.emit(2)

    assertEquals(listOf(1, 2), values)
}
```

- `launch`로 무한 collect를 띄우면 `runTest`가 끝나지 않아 실패한다.
- `backgroundScope`는 테스트 종료 시점에 자동으로 취소해 준다.

## 3.5 예외 처리

```kotlin
@Test
fun `예외는 테스트 실패로 전파된다`() = runTest {
    assertThrows<IllegalStateException> {
        error("boom")
    }
}
```

- `runTest` 안에서 처리되지 않은 예외는 테스트를 실패시킨다.
- 자식 코루틴이 던진 예외도 `TestScope`로 전파된다.
- 예외 자체를 검증할 때는 `assertThrows`(JUnit5) 또는 `Result` 변환을 쓴다.

## 3.6 withTimeout과 가상 시간

```kotlin
@Test
fun `withTimeout도 가상 시간으로 동작한다`() = runTest {
    assertThrows<TimeoutCancellationException> {
        withTimeout(1_000) {
            delay(2_000)
        }
    }
}
```

`withTimeout`도 가상 시간 기준이라 실제로 1초를 기다리지 않는다.

<br>

# 4. TestDispatcher 종류와 선택 기준

## 4.1 두 가지 구현

| 구분 | StandardTestDispatcher | UnconfinedTestDispatcher |
| --- | --- | --- |
| 실행 시점 | 큐에 쌓고 스케줄러가 돌릴 때 실행 | 호출 즉시 현재 스레드에서 실행 |
| 실행 순서 | 예측 가능, 명시적 제어 | 즉시 실행이라 간단하지만 순서가 암묵적 |
| 시간 제어 | `advanceUntilIdle`, `runCurrent` 필요 | 첫 suspend 지점까지 즉시 실행 |
| 실제 환경과 유사성 | 높음 (Main은 보통 디스패치가 필요) | 낮음 |
| 권장 용도 | 기본 선택 | 단순한 collect 시작, 즉시 실행이 필요한 경우 |

## 4.2 StandardTestDispatcher 동작 예시

```kotlin
@Test
fun `Standard는 명시적으로 실행해야 한다`() = runTest {
    val events = mutableListOf<String>()

    launch { events.add("A") }
    events.add("B")

    assertEquals(listOf("B"), events)

    runCurrent()

    assertEquals(listOf("B", "A"), events)
}
```

`runTest`의 기본 Dispatcher는 `StandardTestDispatcher`다.
`launch`한 코루틴은 본문이 suspend되거나 `runCurrent`를 호출할 때까지 대기한다.

## 4.3 UnconfinedTestDispatcher 동작 예시

```kotlin
@Test
fun `Unconfined는 즉시 실행된다`() = runTest(UnconfinedTestDispatcher()) {
    val events = mutableListOf<String>()

    launch { events.add("A") }
    events.add("B")

    assertEquals(listOf("A", "B"), events)
}
```

## 4.4 어떤 걸 고를까

1. 기본은 `StandardTestDispatcher`로 시작한다.
2. ViewModel `init`에서 코루틴이 시작되는 구조라면 테스트에서 실행 시점을 직접 제어하는 편이 안전하다.
3. Flow collect만 즉시 시작하고 싶을 때는 해당 collect에만 `UnconfinedTestDispatcher(testScheduler)`를 쓴다.
4. 두 Dispatcher는 반드시 같은 `testScheduler`를 공유하게 만든다.

```kotlin
@Test
fun `같은 스케줄러를 공유한다`() = runTest {
    val standard = StandardTestDispatcher(testScheduler)
    val unconfined = UnconfinedTestDispatcher(testScheduler)

    // 둘의 가상 시간이 하나로 묶여 있다.
    assertSame(testScheduler, standard.scheduler)
    assertSame(testScheduler, unconfined.scheduler)
}
```

스케줄러를 공유하지 않으면 한쪽의 `advanceUntilIdle`이 다른 쪽에 영향을 주지 못한다.

## 4.5 runTest와 Main의 스케줄러 공유

- `Dispatchers.setMain(StandardTestDispatcher())`를 먼저 호출하면
  이후 `runTest`는 Main의 스케줄러를 자동으로 이어받는다.
- 그래서 `viewModelScope`(Main 기반)에서 시작한 코루틴과 테스트 본문이 같은 가상 시간을 쓴다.
- 이 동작이 있어서 Main을 먼저 교체한 뒤 `runTest`를 쓰는 순서가 중요하다.

<br>

# 5. Dispatchers.setMain 규칙

## 5.1 왜 필요한가

- `viewModelScope`는 `Dispatchers.Main.immediate`를 쓴다.
- JVM 단위 테스트에는 Android Main Looper가 없어서 Main Dispatcher가 초기화되지 않는다.
- 설정하지 않으면 다음 에러가 난다.

```text
java.lang.IllegalStateException: Module with the Main dispatcher had failed to initialize.
For tests Dispatchers.setMain from kotlinx-coroutines-test module can be used
```

## 5.2 JUnit5 Extension

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun afterEach(context: ExtensionContext) {
        Dispatchers.resetMain()
    }
}
```

사용 방법은 다음과 같다.

```kotlin
class UserListViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    @Test
    fun `Main이 테스트 Dispatcher로 교체된다`() = runTest {
        // viewModelScope.launch 사용 가능
    }
}
```

- Kotlin에서는 `@JvmField`를 붙여야 JUnit이 필드로 인식한다.
- `@RegisterExtension`은 필드에 붙이는 프로그래밍 방식 등록이다.

## 5.3 JUnit4 Rule (참고)

Compose UI 테스트나 JUnit4 프로젝트에서는 `TestWatcher`를 쓴다.

```kotlin
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

```kotlin
class UserListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
}
```

## 5.4 resetMain을 반드시 호출해야 하는 이유

- Main Dispatcher는 프로세스 전역 상태다.
- 테스트 간에 교체된 Dispatcher가 남아 있으면 다른 테스트에 영향을 준다.
- `afterEach`에서 `resetMain()`을 호출해 정리한다.

## 5.5 testDispatcher를 테스트에서 꺼내 쓰기

```kotlin
@Test
fun `extension의 dispatcher로 직접 시간을 제어한다`() = runTest {
    val dispatcher = mainDispatcherExtension.testDispatcher

    // 필요하면 dispatcher.scheduler 로 접근
    dispatcher.scheduler.advanceUntilIdle()
}
```

대부분의 경우 `runTest`가 Main 스케줄러를 이어받기 때문에
`advanceUntilIdle()`을 `TestScope`에서 바로 호출해도 된다.

<br>

# 6. MVI 구조 복습

## 6.1 구성 요소

| 요소 | 역할 | 특징 |
| --- | --- | --- |
| State | 화면 전체 상태 | 불변 data class, 단일 출처 |
| Intent | 사용자 입력 / 액션 | sealed interface |
| SideEffect | 일회성 이벤트 (이동, 토스트) | 상태에 저장하지 않음 |
| Reducer | 이전 State + 결과 -> 새 State | 순수 함수에 가깝게 |

## 6.2 단방향 흐름

```text
View --Intent--> ViewModel --(비동기 작업)--> Reducer --> State --> View
                    |
                    +--SideEffect--> View (일회성)
```

## 6.3 MVI가 테스트에 유리한 이유

1. 입력(Intent)과 출력(State, SideEffect)이 명확히 분리되어 있다.
2. 상태가 하나의 data class라 `assertEquals` 한 번으로 화면 전체를 검증할 수 있다.
3. 상태 전이가 순서를 가지므로 Turbine으로 전이 과정을 그대로 검증할 수 있다.
4. 일회성 이벤트가 State와 분리되어 있어 중복 발행 같은 버그를 따로 검증한다.

## 6.4 테스트 관점의 계약

ViewModel 하나를 이렇게 본다.

```text
입력:  onIntent(Intent)
출력1: state: StateFlow<State>
출력2: sideEffect: Flow<SideEffect>
의존:  Repository, UseCase 등 (Fake 또는 Mock으로 대체)
```

이 계약만 검증하면 내부 구현이 바뀌어도 테스트가 깨지지 않는다.

<br>

# 7. 테스트 대상 예제 코드

## 7.1 도메인 모델과 Repository

```kotlin
data class User(
    val id: Long,
    val name: String,
)

interface UserRepository {
    suspend fun getUsers(query: String): Result<List<User>>
}
```

## 7.2 MVI 계약

```kotlin
data class UserListState(
    val isLoading: Boolean = false,
    val query: String = "",
    val users: List<User> = emptyList(),
    val errorMessage: String? = null,
)

sealed interface UserListIntent {
    data object Retry : UserListIntent
    data class Search(val query: String) : UserListIntent
    data class ClickUser(val id: Long) : UserListIntent
}

sealed interface UserListSideEffect {
    data class NavigateToDetail(val id: Long) : UserListSideEffect
    data class ShowToast(val message: String) : UserListSideEffect
}
```

## 7.3 ViewModel

```kotlin
@HiltViewModel
class UserListViewModel @Inject constructor(
    private val repository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UserListState())
    val state: StateFlow<UserListState> = _state.asStateFlow()

    private val _sideEffect = Channel<UserListSideEffect>(Channel.BUFFERED)
    val sideEffect: Flow<UserListSideEffect> = _sideEffect.receiveAsFlow()

    private val queryFlow = MutableStateFlow("")
    private var loadJob: Job? = null

    init {
        loadUsers(query = "")
        observeQuery()
    }

    fun onIntent(intent: UserListIntent) {
        when (intent) {
            is UserListIntent.Retry -> loadUsers(_state.value.query)
            is UserListIntent.Search -> search(intent.query)
            is UserListIntent.ClickUser -> navigate(intent.id)
        }
    }

    private fun search(query: String) {
        reduce { copy(query = query) }
        queryFlow.value = query
    }

    @OptIn(FlowPreview::class)
    private fun observeQuery() {
        queryFlow
            .drop(1) // 초기 빈 문자열은 init의 loadUsers가 처리
            .debounce(SEARCH_DEBOUNCE_MILLIS)
            .distinctUntilChanged()
            .onEach { loadUsers(it) }
            .launchIn(viewModelScope)
    }

    private fun loadUsers(query: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            reduce { copy(isLoading = true, errorMessage = null) }

            repository.getUsers(query)
                .onSuccess { users ->
                    reduce { copy(isLoading = false, users = users) }
                }
                .onFailure { throwable ->
                    val message = throwable.message ?: DEFAULT_ERROR_MESSAGE
                    reduce { copy(isLoading = false, errorMessage = message) }
                    _sideEffect.send(UserListSideEffect.ShowToast(message))
                }
        }
    }

    private fun navigate(id: Long) {
        viewModelScope.launch {
            _sideEffect.send(UserListSideEffect.NavigateToDetail(id))
        }
    }

    private fun reduce(transform: UserListState.() -> UserListState) {
        _state.update(transform)
    }

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val DEFAULT_ERROR_MESSAGE = "알 수 없는 오류"
    }
}
```

## 7.4 이 코드에서 테스트할 포인트

| 포인트 | 설명 |
| --- | --- |
| init 로드 | 생성 직후 로딩 -> 성공 상태로 전이 |
| 실패 처리 | errorMessage 설정 + 토스트 이벤트 |
| Retry | 이전 query로 재조회 |
| Search 디바운스 | 300ms 후에 한 번만 조회 |
| 연속 입력 | 마지막 검색어만 조회 |
| 요청 취소 | 새 로드가 시작되면 이전 로드 취소 |
| ClickUser | NavigateToDetail 이벤트 발행 |

<br>

# 8. Fake와 Mock 선택 기준

## 8.1 개념 차이

| 구분 | Fake | Mock (MockK) |
| --- | --- | --- |
| 정의 | 실제처럼 동작하는 가벼운 구현체 | 호출을 기록하고 응답을 지정하는 대역 |
| 검증 대상 | 결과(State) 중심 | 상호작용(호출 여부, 인자) 중심 |
| 장점 | 구현이 바뀌어도 테스트가 덜 깨짐 | 설정이 빠름, 호출 검증이 쉬움 |
| 단점 | 만드는 비용 | 구현 세부사항에 결합되기 쉬움 |
| 권장 | Repository, DataSource | 호출 횟수 / 인자 검증이 핵심일 때 |

## 8.2 기본 원칙

1. 상태 기반 검증을 기본으로 하고 Fake를 우선한다.
2. 호출 자체가 요구사항일 때만 Mock으로 `coVerify`를 쓴다.
3. 한 테스트에서 Mock의 `coVerify`를 과하게 쓰면 구현 결합이 커진다.

## 8.3 FakeUserRepository

```kotlin
class FakeUserRepository : UserRepository {

    var result: Result<List<User>> = Result.success(emptyList())
    var delayMillis: Long = 0L

    val requests = mutableListOf<String>()
    var completedCount = 0
        private set

    override suspend fun getUsers(query: String): Result<List<User>> {
        requests.add(query)
        if (delayMillis > 0) delay(delayMillis)
        completedCount++
        return result
    }
}
```

- `requests`: 어떤 검색어로 몇 번 호출했는지 기록한다.
- `delayMillis`: 로딩 상태를 관찰하거나 취소를 검증할 때 쓴다.
- `completedCount`: delay 중 취소되면 증가하지 않는다. 취소 검증에 쓴다.

## 8.4 테스트 데이터 fixture

```kotlin
object UserFixture {
    val alice = User(id = 1, name = "alice")
    val bob = User(id = 2, name = "bob")
    val charlie = User(id = 3, name = "charlie")

    val all = listOf(alice, bob, charlie)
}
```

공통 데이터를 한 곳에 두면 테스트 가독성이 올라간다.

## 8.5 ViewModel 생성 헬퍼

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
}
```

- JUnit5는 테스트마다 새 인스턴스를 만들지만 `@BeforeEach`로 상태를 명시적으로 초기화한다.
- ViewModel은 Hilt 없이 생성자로 직접 만든다.

<br>

# 9. 상태(State) 테스트

## 9.1 가장 단순한 형태: 최종 상태만 검증

```kotlin
@Test
fun `초기 진입 시 유저 목록이 로드된다`() = runTest {
    repository.result = Result.success(UserFixture.all)
    val viewModel = createViewModel()

    advanceUntilIdle()

    assertEquals(
        UserListState(
            isLoading = false,
            users = UserFixture.all,
        ),
        viewModel.state.value,
    )
}
```

- `advanceUntilIdle()`이 init에서 시작한 코루틴을 끝까지 실행한다.
- 이후 `state.value`로 최종 상태를 비교한다.
- 전이 과정은 보지 않고 결과만 본다.

## 9.2 init 직후에는 아직 아무것도 실행되지 않는다

```kotlin
@Test
fun `StandardTestDispatcher에서는 init 직후 로딩 시작 전이다`() = runTest {
    repository.delayMillis = 100
    val viewModel = createViewModel()

    // viewModelScope.launch가 큐에 대기 중
    assertEquals(UserListState(), viewModel.state.value)

    runCurrent()

    assertTrue(viewModel.state.value.isLoading)
}
```

- Standard 환경에서는 `launch`가 즉시 실행되지 않는다.
- `runCurrent()` 또는 `advanceUntilIdle()`로 실행 시점을 명시한다.

## 9.3 단계별 상태 확인

```kotlin
@Test
fun `로딩 중에는 isLoading이 true다`() = runTest {
    repository.delayMillis = 1_000
    val viewModel = createViewModel()

    runCurrent()
    assertTrue(viewModel.state.value.isLoading)

    advanceTimeBy(1_000)
    runCurrent()
    assertFalse(viewModel.state.value.isLoading)
}
```

- delay가 있는 Fake를 쓰면 로딩 구간을 안정적으로 관찰할 수 있다.
- delay가 없으면 로딩 -> 성공이 한 번에 지나가 중간 상태를 못 볼 수 있다.

## 9.4 StateFlow의 conflation 특성

`StateFlow`는 값이 연속으로 바뀌면 중간 값을 건너뛰고 최신 값만 전달할 수 있다.

```kotlin
@Test
fun `StateFlow는 중간 값을 건너뛸 수 있다`() = runTest {
    val flow = MutableStateFlow(0)
    val received = mutableListOf<Int>()

    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        flow.collect { received.add(it) }
    }

    flow.value = 1
    flow.value = 2
    flow.value = 3

    // Unconfined collect이면 모두 받을 수도, 구성에 따라 일부만 받을 수도 있다.
    assertEquals(3, received.last())
}
```

- 중간 상태가 반드시 필요한 검증이라면 중간에 suspend 지점(delay)이 있는 구조로 만들어 둔다.
- 마지막 값만 중요하다면 `state.value`로 검증하는 편이 더 단단하다.

## 9.5 상태 비교는 data class 동등성으로

```kotlin
@Test
fun `copy로 기대 상태를 만들어 비교한다`() = runTest {
    repository.result = Result.success(UserFixture.all)
    val viewModel = createViewModel()
    advanceUntilIdle()

    val expected = UserListState().copy(users = UserFixture.all)

    assertEquals(expected, viewModel.state.value)
}
```

- 필드 하나씩 비교하는 것보다 전체 State를 한 번에 비교하면 누락이 줄어든다.
- 비교 실패 시 data class의 `toString` 덕분에 차이가 한눈에 보인다.

## 9.6 Truth를 쓴 가독성 개선

```kotlin
import com.google.common.truth.Truth.assertThat

@Test
fun `검색어가 상태에 반영된다`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.onIntent(UserListIntent.Search("ali"))

    assertThat(viewModel.state.value.query).isEqualTo("ali")
}
```

Truth는 `assertThat(actual).isEqualTo(expected)` 형태라 인자 순서 실수가 줄어든다.

<br>

# 10. Turbine 사용법

## 10.1 Turbine이 필요한 이유

- Flow가 여러 번 emit하는 과정을 순서대로 검증하기 어렵다.
- 수동으로 `toList()`나 `launch { collect }`를 쓰면 코드가 길고 취소 처리가 번거롭다.
- Turbine은 Flow를 Channel처럼 다뤄 `awaitItem()`으로 순서대로 꺼내 검증한다.

## 10.2 기본 형태

```kotlin
import app.cash.turbine.test

@Test
fun `상태 전이 순서를 검증한다`() = runTest {
    repository.delayMillis = 100
    repository.result = Result.success(UserFixture.all)
    val viewModel = createViewModel()

    viewModel.state.test {
        // 1) 현재 값 (StateFlow는 구독 즉시 현재 값을 준다)
        assertEquals(UserListState(), awaitItem())

        // 2) 로딩 시작
        assertEquals(UserListState(isLoading = true), awaitItem())

        // 3) 로딩 완료
        assertEquals(
            UserListState(isLoading = false, users = UserFixture.all),
            awaitItem(),
        )

        cancelAndIgnoreRemainingEvents()
    }
}
```

## 10.3 핵심 API

| API | 설명 |
| --- | --- |
| `awaitItem()` | 다음 emit된 값을 기다려 반환 |
| `awaitComplete()` | Flow 완료를 기다림 |
| `awaitError()` | Flow 예외 종료를 기다림 |
| `expectNoEvents()` | 이벤트가 없음을 검증 |
| `skipItems(n)` | n개 값을 건너뜀 |
| `expectMostRecentItem()` | 지금까지 쌓인 값 중 가장 최근 값 반환 |
| `cancelAndIgnoreRemainingEvents()` | 남은 이벤트를 무시하고 종료 |
| `cancelAndConsumeRemainingEvents()` | 남은 이벤트를 리스트로 반환하고 종료 |

## 10.4 Turbine의 안전장치

- `test {}` 블록이 끝날 때 소비되지 않은 이벤트가 남아 있으면 실패한다.
- 이 덕분에 의도하지 않은 추가 emit을 놓치지 않는다.
- 일부러 무시하려면 `cancelAndIgnoreRemainingEvents()`를 명시한다.

```text
AssertionError: Unconsumed events found:
 - Item(UserListState(...))
```

## 10.5 타임아웃

- `awaitItem()`은 기본 3초 타임아웃이 있다.
- 이 타임아웃은 가상 시간이 아닌 실제 시간 기준으로 동작한다.
- 필요하면 `test(timeout = 10.seconds)`로 늘린다.

```kotlin
viewModel.state.test(timeout = 10.seconds) {
    // ...
}
```

## 10.6 expectMostRecentItem

```kotlin
@Test
fun `최종 상태만 필요할 때`() = runTest {
    repository.result = Result.success(UserFixture.all)
    val viewModel = createViewModel()

    viewModel.state.test {
        advanceUntilIdle()

        val latest = expectMostRecentItem()
        assertEquals(UserFixture.all, latest.users)
    }
}
```

- 중간 전이는 필요 없고 최종 값만 보고 싶을 때 쓴다.
- 쌓인 값을 모두 건너뛰고 최신 값을 반환한다.

## 10.7 skipItems

```kotlin
@Test
fun `초기 값과 로딩 상태는 건너뛴다`() = runTest {
    repository.delayMillis = 100
    repository.result = Result.success(UserFixture.all)
    val viewModel = createViewModel()

    viewModel.state.test {
        skipItems(2) // 초기 값, 로딩 상태
        assertEquals(UserFixture.all, awaitItem().users)
        cancelAndIgnoreRemainingEvents()
    }
}
```

## 10.8 testIn으로 여러 Flow 동시 관찰

```kotlin
@Test
fun `State와 SideEffect를 동시에 관찰한다`() = runTest {
    repository.result = Result.failure(IllegalStateException("서버 오류"))
    val viewModel = createViewModel()

    val stateTurbine = viewModel.state.testIn(backgroundScope)
    val effectTurbine = viewModel.sideEffect.testIn(backgroundScope)

    advanceUntilIdle()

    assertEquals(
        UserListSideEffect.ShowToast("서버 오류"),
        effectTurbine.awaitItem(),
    )
    assertEquals("서버 오류", stateTurbine.expectMostRecentItem().errorMessage)

    stateTurbine.cancel()
    effectTurbine.cancel()
}
```

- `testIn(scope)`은 블록 대신 Turbine 객체를 반환한다.
- 두 개 이상의 Flow를 한 테스트에서 함께 볼 때 유용하다.
- 사용이 끝나면 `cancel()`로 정리한다.

## 10.9 Turbine과 StandardTestDispatcher

- Turbine은 내부적으로 Unconfined 방식으로 collect를 시작한다.
- 테스트 본문이 `awaitItem()`에서 suspend되면 `runTest`가 가상 시간을 진행한다.
- 그래서 대기 중인 `viewModelScope` 코루틴이 자연스럽게 실행된다.
- 반대로 `awaitItem()` 없이 값만 확인하려면 `advanceUntilIdle()`을 직접 호출한다.

<br>

# 정리

## 핵심 개념

1. `runTest`는 가상 시간으로 코루틴을 빠르고 결정적으로 테스트한다.
2. `advanceTimeBy`는 정각 작업을 실행하지 않으므로 `runCurrent()`와 함께 쓴다.
3. 기본 Dispatcher는 `StandardTestDispatcher`, 즉시 collect가 필요할 때만 `UnconfinedTestDispatcher`를 쓰고 항상 `testScheduler`를 공유한다.
4. `viewModelScope`가 Main을 쓰기 때문에 `Dispatchers.setMain`이 필요하며 `resetMain`으로 정리한다.
5. Turbine의 `awaitItem()`, `expectNoEvents()`, `testIn()`으로 Flow의 순서와 중복을 검증한다.

## 테스트 설계 원칙

| 항목 | 원칙 |
| --- | --- |
| 입력 | `onIntent(Intent)` 하나로 통일 |
| State 검증 | data class 전체 비교, 최종 상태 우선 |
| 전이 검증 | 순서가 계약일 때만 Turbine으로 고정 |
| 협력 객체 | Fake 우선, 호출이 요구사항일 때만 MockK |
| 로딩 구간 | delay가 있는 Fake로 중간 상태를 안정적으로 관찰 |

## 상태 테스트에서 기억할 것

1. init에서 시작한 코루틴은 Standard 환경에서 `runCurrent()` 전까지 실행되지 않는다.
2. `StateFlow`는 conflation으로 중간 값이 사라질 수 있다.
3. Turbine 첫 `awaitItem()`은 StateFlow의 현재 값이다.
4. 블록이 끝날 때 소비되지 않은 이벤트가 있으면 Turbine이 실패시킨다.
