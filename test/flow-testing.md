# Turbine으로 Flow 테스트하기

1. 개요
2. Turbine 아키텍처
3. 기본 사용법
4. test() 함수와 TurbineTestContext
5. awaitItem, awaitComplete, awaitError
6. expectMostRecentItem과 skipItems
7. StateFlow / SharedFlow 테스트
8. 타임아웃과 turbineScope
9. 여러 Flow를 동시에 테스트하기
10. MVI ViewModel 테스트 패턴
11. Dispatcher와 코루틴 테스트 환경 구성
12. Cold Flow vs Hot Flow 테스트 차이
13. 자주 쓰는 매처/어서션 조합
14. 테스트 더블(Fake Repository)과의 조합
15. 자주 겪는 이슈
16. 정리

# 개요

Turbine은 Cash App에서 만든 Kotlin Flow 테스트 전용 라이브러리다. Flow는 비동기 스트림이라 `collect`로 직접 테스트하려면 `CoroutineScope`, `Job` 취소, 콜백 누적 등을 손수 관리해야 해서 테스트 코드가 장황해지기 쉽다. Turbine은 이 과정을 `test { }` DSL 하나로 감싸서, Flow가 방출하는 값을 순서대로 `await`하는 선언적인 방식으로 테스트할 수 있게 해준다.

MVI 아키텍처에서는 ViewModel의 `StateFlow<UiState>`나 `SharedFlow<UiEffect>`를 검증하는 일이 잦은데, Turbine은 이런 상태/이벤트 스트림 테스트에 특히 잘 맞는다.

<br>

```gradle
testImplementation "app.cash.turbine:turbine:1.1.0"
testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0"
```

Turbine 단독으로는 Flow 수집을 도와줄 뿐이고, 실제 코루틴 스케줄링 제어(가상 시간 진행 등)는 `kotlinx-coroutines-test`의 `runTest`와 함께 써야 완전해진다.

# Turbine 아키텍처

Turbine의 핵심은 `Flow<T>.test { }` 확장 함수다. 이 함수는 내부적으로 별도 코루틴에서 Flow를 `collect`하면서, 방출된 값을 채널(Channel)에 순서대로 쌓아둔다. 테스트 코드는 이 채널에서 `awaitItem()` 등을 호출해 값을 하나씩 꺼내 검증한다.

```
Flow<T>.test { }
  → 내부 코루틴에서 collect 시작
    → 방출값을 내부 Channel에 큐잉
      → 테스트 코드가 awaitItem()/awaitComplete()/awaitError()로 소비
        → block이 끝나면 collect 코루틴을 자동 취소하고 남은 이벤트 검증
```

`test` 블록이 끝나는 시점에 Turbine은 기본적으로 "아직 소비되지 않은 이벤트가 남아있는지"를 검사해서, 검증하지 않고 버려진 이벤트가 있으면 테스트를 실패시킨다. 이 덕분에 예상치 못한 추가 emit을 놓치지 않고 잡아낼 수 있다.

# 기본 사용법

```kotlin
@Test
fun `flow가 순서대로 값을 방출한다`() = runTest {
    val flow = flowOf(1, 2, 3)

    flow.test {
        assertThat(awaitItem()).isEqualTo(1)
        assertThat(awaitItem()).isEqualTo(2)
        assertThat(awaitItem()).isEqualTo(3)
        awaitComplete()
    }
}
```

`test { }` 블록 안에서는 `awaitItem()`을 호출한 순서대로 Flow가 실제 방출한 값과 매칭된다. 만약 실제 방출 순서와 기대한 순서가 다르면 즉시 실패한다. 블록이 끝나기 전에 `awaitComplete()`나 `cancelAndIgnoreRemainingEvents()`로 명시적으로 마무리하지 않으면, 소비되지 않은 이벤트가 있을 때 에러가 발생한다.

# test() 함수와 TurbineTestContext

`test { }`의 람다는 `TurbineTestContext<T>`를 리시버로 받는다. 이 컨텍스트가 `awaitItem`, `awaitComplete`, `awaitError`, `expectMostRecentItem`, `skipItems`, `cancelAndIgnoreRemainingEvents` 등의 함수를 제공한다.

```kotlin
flow.test {
    val item1 = awaitItem()      // 다음 값을 기다림
    // ... 여기서 item1을 검증하거나 다른 로직 수행
    val item2 = awaitItem()

    cancel() // 수집을 중단하고 이후 이벤트는 무시
}
```

타임아웃은 기본적으로 3초(기본값)이며, `test(timeout = 5.seconds) { }`처럼 인자로 조정할 수 있다. `runTest`의 가상 시간과 함께 쓰면 대부분의 경우 타임아웃 걱정 없이 즉시 결과를 얻을 수 있다.

# awaitItem, awaitComplete, awaitError

세 함수는 Flow의 세 가지 종료 이벤트(값 방출, 정상 종료, 예외 종료)에 각각 대응한다.

```kotlin
@Test
fun `정상 종료하는 flow`() = runTest {
    flowOf(1, 2).test {
        assertThat(awaitItem()).isEqualTo(1)
        assertThat(awaitItem()).isEqualTo(2)
        awaitComplete() // onCompletion, 더 이상 이벤트 없음을 확인
    }
}

@Test
fun `예외로 종료하는 flow`() = runTest {
    val flow = flow<Int> {
        emit(1)
        throw IllegalStateException("network error")
    }

    flow.test {
        assertThat(awaitItem()).isEqualTo(1)
        val error = awaitError()
        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error.message).isEqualTo("network error")
    }
}
```

`awaitComplete()`을 호출하지 않고 블록을 끝내면, Flow가 아직 종료되지 않았거나 종료 이벤트를 검증하지 않았다는 이유로 실패할 수 있다. 반대로 Flow가 무한히 값을 방출하는 경우(`StateFlow`처럼)는 `awaitComplete()`을 호출할 필요가 없고, 대신 `cancelAndIgnoreRemainingEvents()`로 마무리한다.

# expectMostRecentItem과 skipItems

`StateFlow`처럼 중간 값에는 관심이 없고 "현재 가장 최신 값"만 확인하고 싶을 때 유용하다.

```kotlin
@Test
fun `가장 최근 상태만 확인`() = runTest {
    val stateFlow = MutableStateFlow(0)

    stateFlow.test {
        assertThat(awaitItem()).isEqualTo(0) // 초기값

        stateFlow.value = 1
        stateFlow.value = 2
        stateFlow.value = 3

        assertThat(expectMostRecentItem()).isEqualTo(3)

        cancelAndIgnoreRemainingEvents()
    }
}
```

`skipItems(n)`은 다음 n개의 이벤트를 검증 없이 그냥 건너뛰고 싶을 때 사용한다. 로딩 상태처럼 매번 검증할 필요가 없는 중간 이벤트를 스킵할 때 코드가 간결해진다.

```kotlin
viewModel.uiState.test {
    skipItems(1) // 초기 Loading 상태는 건너뜀
    val successState = awaitItem()
    assertThat(successState).isInstanceOf(UiState.Success::class.java)
    cancelAndIgnoreRemainingEvents()
}
```

# StateFlow / SharedFlow 테스트

`StateFlow`는 항상 초기값을 즉시 방출하는 Hot Flow이므로, `test { }` 진입 직후 첫 `awaitItem()`이 곧바로 현재 값을 반환한다.

```kotlin
@Test
fun `StateFlow는 구독 즉시 현재값을 방출한다`() = runTest {
    val state = MutableStateFlow("idle")

    state.test {
        assertThat(awaitItem()).isEqualTo("idle")

        state.value = "loading"
        assertThat(awaitItem()).isEqualTo("loading")

        cancelAndIgnoreRemainingEvents()
    }
}
```

`SharedFlow`는 `replay` 설정에 따라 동작이 달라진다. `replay = 0`인 `SharedFlow`(주로 일회성 이벤트, 예: `UiEffect`)는 구독 이전에 emit된 값은 받을 수 없으므로, 반드시 `test { }`로 구독을 시작한 뒤에 emit을 트리거해야 한다.

```kotlin
@Test
fun `SharedFlow 이벤트는 구독 이후 emit만 받는다`() = runTest {
    val effectFlow = MutableSharedFlow<UiEffect>()

    effectFlow.test {
        effectFlow.emit(UiEffect.ShowToast("저장 완료"))
        assertThat(awaitItem()).isEqualTo(UiEffect.ShowToast("저장 완료"))
        cancelAndIgnoreRemainingEvents()
    }
}
```

만약 emit을 먼저 하고 `test { }`를 나중에 호출하면, 구독 시점이 emit 이후이기 때문에 해당 이벤트를 영영 받지 못한다. 이는 실제 프로덕션 코드에서 `SharedFlow`를 다룰 때도 동일하게 발생하는 함정이므로, 테스트가 이 문제를 미리 드러내주는 셈이다.

# 타임아웃과 turbineScope

여러 개의 `test { }`를 중첩하거나 동시에 다뤄야 할 때는 `turbineScope { }`로 감싼다. `turbineScope`는 내부의 모든 `test` 블록이 끝날 때까지 기다렸다가 정리하며, 타임아웃도 스코프 단위로 관리할 수 있다.

```kotlin
@Test
fun `여러 flow를 turbineScope로 묶어서 테스트`() = runTest {
    turbineScope {
        val stateTurbine = viewModel.uiState.testIn(this)
        val effectTurbine = viewModel.uiEffect.testIn(this)

        assertThat(stateTurbine.awaitItem()).isEqualTo(UiState.Loading)

        viewModel.onSaveClicked()

        assertThat(stateTurbine.awaitItem()).isEqualTo(UiState.Success)
        assertThat(effectTurbine.awaitItem()).isEqualTo(UiEffect.ShowToast("저장 완료"))

        stateTurbine.cancelAndIgnoreRemainingEvents()
        effectTurbine.cancelAndIgnoreRemainingEvents()
    }
}
```

`testIn(scope)`는 `test { }`처럼 람다 블록으로 감싸지 않고, `ReceiveTurbine` 객체를 직접 반환받아 여러 Flow를 나란히 놓고 조작할 수 있게 해준다. 상태(State)와 이벤트(Effect)를 동시에 검증해야 하는 MVI 테스트에서 특히 유용한 패턴이다.

# 여러 Flow를 동시에 테스트하기

두 개 이상의 Flow가 서로 영향을 주는 시나리오(예: 검색어 Flow가 바뀌면 결과 Flow가 바뀌는 경우)는 `turbineScope` 안에서 각각을 `testIn`으로 구독해두고, 이벤트를 순서대로 트리거하며 검증한다.

```kotlin
@Test
fun `검색어 변경에 따라 결과 상태가 갱신된다`() = runTest {
    turbineScope {
        val query = viewModel.searchQuery.testIn(this)
        val results = viewModel.searchResults.testIn(this)

        assertThat(query.awaitItem()).isEqualTo("")
        assertThat(results.awaitItem()).isEqualTo(emptyList<Item>())

        viewModel.onQueryChanged("kotlin")

        assertThat(query.awaitItem()).isEqualTo("kotlin")
        assertThat(results.awaitItem()).isEqualTo(fakeResultsFor("kotlin"))

        query.cancelAndIgnoreRemainingEvents()
        results.cancelAndIgnoreRemainingEvents()
    }
}
```

# MVI ViewModel 테스트 패턴

MVI 구조에서는 보통 다음 세 가지를 하나의 테스트 흐름으로 묶어 검증한다: 초기 상태, 인텐트(Intent) 처리 후 상태 변화, 그리고 부수 효과(Effect).

```kotlin
@Test
fun `저장 버튼 클릭 시 저장 성공 상태와 토스트 이벤트가 발생한다`() = runTest {
    val fakeRepository = FakeRecordingRepository()
    val viewModel = RecordingViewModel(fakeRepository)

    turbineScope {
        val stateTurbine = viewModel.uiState.testIn(this)
        val effectTurbine = viewModel.uiEffect.testIn(this)

        assertThat(stateTurbine.awaitItem()).isEqualTo(RecordingUiState.Idle)

        viewModel.onIntent(RecordingIntent.Save(recordingId = "rec_1"))

        assertThat(stateTurbine.awaitItem()).isEqualTo(RecordingUiState.Saving)
        assertThat(stateTurbine.awaitItem()).isEqualTo(RecordingUiState.Saved)
        assertThat(effectTurbine.awaitItem()).isEqualTo(RecordingUiEffect.ShowToast("저장 완료"))

        stateTurbine.cancelAndIgnoreRemainingEvents()
        effectTurbine.cancelAndIgnoreRemainingEvents()
    }
}
```

이런 구조에서는 `awaitItem()`을 호출할 때마다 상태 전이의 각 단계를 명시적으로 검증하게 되므로, 중간에 예상치 못한 상태가 끼어들면 테스트가 바로 실패하며 어느 지점에서 어긋났는지 파악하기 쉽다.

# Dispatcher와 코루틴 테스트 환경 구성

ViewModel 내부에서 `viewModelScope`나 `Dispatchers.IO`를 직접 사용하는 경우, 테스트에서는 `Dispatchers.setMain`으로 메인 디스패처를 테스트 디스패처로 교체해야 한다.

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class RecordingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `테스트`() = runTest(testDispatcher) {
        // ...
    }
}
```

`StandardTestDispatcher`는 코루틴을 즉시 실행하지 않고 큐에 쌓아두므로, `runCurrent()`나 `advanceUntilIdle()`로 명시적으로 진행시켜야 할 때가 있다. Turbine의 `awaitItem()`은 내부적으로 필요한 만큼 가상 시간을 진행시켜주기 때문에 대부분의 경우 별도 처리 없이도 잘 동작하지만, `delay()`가 섞인 로직(디바운스 등)을 테스트할 때는 `advanceTimeBy()`를 함께 써야 한다.

```kotlin
@Test
fun `디바운스된 검색어는 300ms 후 반영된다`() = runTest {
    viewModel.searchResults.test {
        viewModel.onQueryChanged("ko")
        viewModel.onQueryChanged("kot")
        viewModel.onQueryChanged("kotlin")

        advanceTimeBy(300)
        runCurrent()

        assertThat(awaitItem()).isEqualTo(fakeResultsFor("kotlin"))
        cancelAndIgnoreRemainingEvents()
    }
}
```

# Cold Flow vs Hot Flow 테스트 차이

Cold Flow(`flow { }`, `flowOf`, Room DAO의 `Flow` 반환값 등)는 구독할 때마다 처음부터 다시 실행되므로, `test { }` 블록마다 독립적인 결과를 얻는다. 반면 Hot Flow(`StateFlow`, `SharedFlow`)는 구독 시점 이후의 값만 받을 수 있고, 구독자 수와 무관하게 하나의 스트림을 공유한다.

```kotlin
// Cold Flow: 매번 새로 실행되므로 두 test 블록 모두 1,2,3을 처음부터 받는다
val coldFlow = flow {
    emit(1); emit(2); emit(3)
}

coldFlow.test { assertThat(awaitItem()).isEqualTo(1); cancelAndIgnoreRemainingEvents() }
coldFlow.test { assertThat(awaitItem()).isEqualTo(1); cancelAndIgnoreRemainingEvents() }
```

```kotlin
// Hot Flow: 이미 emit된 값은 다시 받을 수 없다 (replay 설정에 따라 다름)
val hotFlow = MutableSharedFlow<Int>()
hotFlow.emit(1) // 구독자가 없는 상태에서 emit되어 유실됨

hotFlow.test {
    // 위에서 emit한 1은 받지 못함
}
```

이 차이를 이해하지 못하면 "분명 emit 했는데 테스트에서 값을 못 받는다"는 혼란을 겪기 쉽다. Hot Flow를 테스트할 때는 항상 구독(`test`/`testIn`)을 먼저 시작한 뒤 emit을 트리거하는 순서를 지켜야 한다.

# 자주 쓰는 매처/어서션 조합

Turbine 자체는 어서션 라이브러리가 아니므로, 보통 AssertJ나 Kotlin 표준 `assert`, Truth 등을 함께 쓴다.

```kotlin
// AssertJ 스타일
assertThat(awaitItem()).isEqualTo(expected)
assertThat(awaitItem()).isInstanceOf(UiState.Error::class.java)

// Truth 스타일
assertThat(awaitItem()).isEqualTo(expected)

// data class의 특정 필드만 검증
val item = awaitItem() as UiState.Success
assertThat(item.recordings).hasSize(3)
assertThat(item.recordings.first().title).isEqualTo("녹음 1")
```

리스트나 컬렉션을 담은 상태를 검증할 때는 전체 객체를 `isEqualTo`로 비교하기보다, 필요한 필드만 뽑아 비교하는 편이 상태 클래스가 커질 때 테스트 유지보수가 쉬워진다.

# 테스트 더블(Fake Repository)과의 조합

ViewModel의 Flow 테스트는 대부분 실제 Repository 대신 Fake 구현체를 주입해서, Repository가 특정 타이밍에 값을 방출하도록 직접 제어한다.

```kotlin
class FakeRecordingRepository : RecordingRepository {
    private val recordingsFlow = MutableStateFlow<List<Recording>>(emptyList())

    override fun observeRecordings(): Flow<List<Recording>> = recordingsFlow

    fun emit(recordings: List<Recording>) {
        recordingsFlow.value = recordings
    }
}

@Test
fun `Repository가 새 목록을 방출하면 상태가 갱신된다`() = runTest {
    val fakeRepository = FakeRecordingRepository()
    val viewModel = RecordingListViewModel(fakeRepository)

    viewModel.uiState.test {
        assertThat(awaitItem()).isEqualTo(RecordingListUiState(emptyList()))

        fakeRepository.emit(listOf(Recording("rec_1", "녹음 1")))

        assertThat(awaitItem()).isEqualTo(
            RecordingListUiState(listOf(Recording("rec_1", "녹음 1")))
        )

        cancelAndIgnoreRemainingEvents()
    }
}
```

Mockito/MockK로 Flow를 `every { } returns flowOf(...)`처럼 목킹할 수도 있지만, 시간에 따라 여러 번 방출하는 시나리오는 Fake 클래스가 훨씬 다루기 쉽다. Flow 테스트에서는 목(mock)보다 Fake를 우선 고려하는 편이 일반적이다.

# 자주 겪는 이슈

- `test { }` 블록이 끝나기 전에 `awaitComplete()`나 `cancelAndIgnoreRemainingEvents()`를 호출하지 않으면 "소비되지 않은 이벤트가 남아있다"는 이유로 테스트가 실패한다.
- `SharedFlow`(replay = 0)에 대해 구독 전에 emit한 값은 영원히 유실된다. 반드시 `test { }`/`testIn` 구독을 먼저 시작하고 나서 emit해야 한다.
- `runTest` 없이 `test { }`만 사용하면 일반 코루틴 컨텍스트에서 동작해 `delay()`가 포함된 로직에서 실제로 몇 초씩 기다리게 되거나, 가상 시간 제어가 되지 않아 타임아웃이 발생할 수 있다.
- `StandardTestDispatcher` 환경에서 `viewModelScope.launch { }`로 실행되는 로직이 `awaitItem()` 호출 시점까지 아직 실행되지 않아 값을 못 받는 경우가 있다. 이럴 땐 `runCurrent()`를 명시적으로 호출해 대기 중인 코루틴을 진행시켜야 한다.
- 여러 `test { }` 블록을 동시에 열어야 하는데 `turbineScope` 없이 중첩해서 쓰면 내부 스코프 관리가 꼬여 예기치 않은 타임아웃이 발생할 수 있다. 두 개 이상의 Flow를 동시에 다뤄야 한다면 `turbineScope` + `testIn`을 쓰는 것이 안전하다.
- `expectMostRecentItem()`은 최소 하나의 이벤트가 큐에 있어야 동작한다. 아직 아무 이벤트도 방출되지 않은 시점에 호출하면 예외가 발생하므로, 먼저 `awaitItem()`으로 최소 한 번은 받은 뒤에 쓰는 것이 안전하다.
- Fake Repository의 `MutableStateFlow`에 값을 대입(`value = ...`)할 때, 이전 값과 동일하면 `StateFlow`의 conflation 특성상 새로운 emit으로 간주되지 않아 `awaitItem()`이 멈춰 대기하게 된다. 상태가 바뀌지 않는 케이스를 테스트할 때는 이 점을 감안해야 한다.

# 정리

Turbine은 `test { }` / `testIn(scope)` DSL로 Flow의 방출값을 순서대로 `await`하며 검증하는 방식을 제공해, 콜백 기반 Flow 테스트 코드를 크게 줄여준다. `awaitItem`, `awaitComplete`, `awaitError`가 기본 3종 세트이고, `StateFlow`처럼 최신 값만 중요한 경우엔 `expectMostRecentItem`, 관심 없는 중간값은 `skipItems`로 건너뛴다.

MVI 구조에서는 `uiState`(StateFlow)와 `uiEffect`(SharedFlow, replay = 0)를 `turbineScope` 안에서 각각 `testIn`으로 구독해두고, 인텐트를 트리거한 뒤 상태 전이와 이벤트 발생을 나란히 검증하는 패턴이 가장 실무적이다. Cold/Hot Flow의 구독 시점 차이, `kotlinx-coroutines-test`의 가상 시간 제어(`runCurrent`, `advanceTimeBy`)를 함께 이해하고 있어야 타이밍 관련 테스트 실패를 피할 수 있다.
