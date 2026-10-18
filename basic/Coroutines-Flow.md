# Kotlin Coroutines / Flow 심화

<br>

# 목차

1. Structured Concurrency
2. Dispatcher와 withContext
3. 예외 처리
4. 취소 협력
5. Cold Flow vs Hot Flow
6. Flow 연산자
7. stateIn / shareIn과 WhileSubscribed
8. Channel과 SideEffect 처리
9. Compose에서 Flow 수집
10. 테스트
11. 정리

<br>

# 1. Structured Concurrency

## 1.1 개념

코루틴은 반드시 **부모-자식 관계의 계층 구조** 안에서 실행되어야 한다는 원칙이다. 부모는 자식이 모두 끝날 때까지 완료되지 않고, 부모가 취소되면 자식도 모두 취소된다. 자식이 예외로 실패하면 부모에게 전파된다.

```text
viewModelScope (SupervisorJob)
   |
   +-- launch A (Job)
   |      +-- launch A-1
   |      +-- async A-2
   |
   +-- launch B (Job)
```

스레드나 콜백 방식에서는 "누가 이 작업을 끝낼 책임이 있는가"가 불명확해서 누수가 생기기 쉽다. Structured Concurrency는 이 문제를 언어 구조로 해결한다.

## 1.2 Job

모든 코루틴은 `Job`을 가지고, Job은 생명주기를 나타낸다.

```text
New --> Active --> Completing --> Completed
          |             |
          +--> Cancelling --> Cancelled
```

| 상태 | isActive | isCompleted | isCancelled |
|---|---|---|---|
| Active | true | false | false |
| Completing | true | false | false |
| Cancelling | false | false | true |
| Completed | false | true | false |
| Cancelled | false | true | true |

```kotlin
fun main() = runBlocking {
    val job = launch {
        repeat(5) { i ->
            println("working $i")
            delay(500)
        }
    }
    delay(1200)
    job.cancelAndJoin() // 취소하고 종료될 때까지 대기
    println("done")
}
```

## 1.3 CoroutineScope

코루틴 빌더(`launch`, `async`)는 `CoroutineScope`의 확장 함수이고, 스코프가 부모 Job을 제공한다.

```kotlin
class SampleScope : CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job

    fun clear() = job.cancel()
}
```

안드로이드에서는 직접 만들기보다 제공되는 스코프를 쓴다.

| 스코프 | 생명주기 | 용도 |
|---|---|---|
| `viewModelScope` | ViewModel `onCleared()` 시 취소 | 화면 데이터 로딩, Intent 처리 |
| `lifecycleScope` | Lifecycle `DESTROYED` 시 취소 | Activity/Fragment 전용 작업 |
| `rememberCoroutineScope()` | Composition 이탈 시 취소 | 이벤트 핸들러에서 launch |
| `GlobalScope` | 앱 전체 | 사용 지양 |

## 1.4 GlobalScope를 피해야 하는 이유

```kotlin
// 나쁜 예: 화면이 사라져도 계속 실행되고, 취소 수단도 없다
fun load() {
    GlobalScope.launch {
        val data = api.fetch()
        _state.value = data // ViewModel이 사라진 뒤에도 접근 가능
    }
}

// 좋은 예
fun load() {
    viewModelScope.launch {
        val data = api.fetch()
        _state.value = data
    }
}
```

앱 전체 생명주기로 돌아야 하는 작업이 필요하다면 `GlobalScope` 대신 Application 레벨에서 직접 만든 스코프를 Hilt로 주입한다.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
```

## 1.5 launch vs async

| 구분 | launch | async |
|---|---|---|
| 반환 | `Job` | `Deferred<T>` |
| 결과 | 없음 | `await()`로 받음 |
| 예외 | 즉시 부모로 전파 | `await()` 시점에도 전파, 부모로도 전파 |
| 용도 | 결과가 필요 없는 작업 | 병렬 실행 후 결과 조합 |

```kotlin
// 병렬 호출: 두 API가 동시에 실행된다
suspend fun loadHome(): HomeData = coroutineScope {
    val user = async { userRepository.getUser() }
    val feed = async { feedRepository.getFeed() }
    HomeData(user.await(), feed.await())
}

// 순차 호출: 의도치 않게 이렇게 쓰면 시간이 두 배가 된다
suspend fun loadHomeSequential(): HomeData {
    val user = userRepository.getUser()
    val feed = feedRepository.getFeed()
    return HomeData(user, feed)
}
```

## 1.6 coroutineScope vs supervisorScope

```kotlin
// coroutineScope: 자식 하나가 실패하면 나머지도 모두 취소되고 예외가 던져진다
suspend fun loadAll() = coroutineScope {
    launch { taskA() }
    launch { taskB() } // taskA가 실패하면 이것도 취소된다
}

// supervisorScope: 자식 하나가 실패해도 나머지는 계속 실행된다
suspend fun loadAllIndependent() = supervisorScope {
    launch { taskA() }
    launch { taskB() } // taskA가 실패해도 계속 실행된다
}
```

`viewModelScope`는 내부적으로 `SupervisorJob`을 쓰기 때문에, 한 `launch`가 실패해도 같은 ViewModel의 다른 `launch`는 취소되지 않는다.

<br>

# 2. Dispatcher와 withContext

## 2.1 Dispatcher 종류

Dispatcher는 코루틴이 **어떤 스레드(풀)에서 실행될지** 결정한다.

| Dispatcher | 스레드 | 용도 |
|---|---|---|
| `Dispatchers.Main` | 메인(UI) 스레드 | UI 갱신, 가벼운 작업 |
| `Dispatchers.Main.immediate` | 이미 메인이면 즉시 실행 | `viewModelScope` 기본값 |
| `Dispatchers.Default` | CPU 코어 수 기반 공유 풀 | 정렬, 파싱, 계산 같은 CPU 작업 |
| `Dispatchers.IO` | 최대 64개(기본) 스레드 풀 | 네트워크, 디스크, DB |
| `Dispatchers.Unconfined` | 호출한 스레드에서 시작 | 테스트, 특수 목적 |

`Default`와 `IO`는 스레드를 공유하기 때문에, `withContext(Dispatchers.IO)`에서 `Default`로 전환해도 실제 스레드 전환 비용이 거의 없다.

## 2.2 withContext

실행 컨텍스트를 일시적으로 바꾸고, 블록이 끝나면 원래 컨텍스트로 돌아온다.

```kotlin
suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
    File(path).readText()
}

fun load() {
    viewModelScope.launch { // Main
        val text = readFile("a.txt") // 내부에서 IO로 전환 후 복귀
        _state.value = text          // 다시 Main
    }
}
```

## 2.3 Main-safe 설계

suspend 함수는 **어떤 Dispatcher에서 호출해도 안전해야** 한다는 원칙이다. 스레드 전환의 책임은 호출자가 아니라 **suspend 함수를 구현한 쪽**이 진다.

```kotlin
// 좋은 예: Repository가 스스로 IO로 전환한다
class RecordRepository @Inject constructor(
    private val dao: RecordDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun getRecords(): List<Record> = withContext(ioDispatcher) {
        dao.getAll().map { it.toDomain() }
    }
}

// ViewModel은 어떤 스레드인지 신경 쓰지 않는다
viewModelScope.launch {
    val records = repository.getRecords()
}
```

## 2.4 Dispatcher 주입

`Dispatchers.IO`를 코드에 직접 박으면 테스트에서 교체할 수 없다. Hilt로 주입한다.

```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
```

## 2.5 흔한 실수

- 이미 main-safe한 Room(suspend DAO), Retrofit(suspend 함수)을 다시 `withContext(Dispatchers.IO)`로 감싸는 것. 해롭지는 않지만 불필요하다.
- `Dispatchers.Default`에서 블로킹 I/O를 실행하는 것. CPU 풀 스레드를 점유해서 다른 계산 작업이 밀린다.
- 블로킹 호출(`Thread.sleep`, 동기 네트워크)을 `Dispatchers.Main`에서 실행하는 것. ANR의 원인이 된다.

<br>

# 3. 예외 처리

## 3.1 launch와 async의 예외 전파 차이

```kotlin
// launch: 예외가 발생하면 부모로 즉시 전파되고, 처리되지 않으면 앱이 크래시한다
viewModelScope.launch {
    throw IllegalStateException("boom")
}

// async: 예외가 Deferred에 저장되고 await() 시점에 던져진다
// 단, 부모 Job이 있으면 부모로도 전파된다는 점에 주의
val deferred = viewModelScope.async {
    throw IllegalStateException("boom")
}
deferred.await() // 여기서 던져진다
```

## 3.2 try/catch 함정

코루틴 빌더 **바깥**에서 try/catch로 감싸도 예외를 잡지 못한다.

```kotlin
// 잡히지 않는다
try {
    viewModelScope.launch {
        throw RuntimeException("boom")
    }
} catch (e: Exception) {
    // 실행되지 않는다. launch는 즉시 반환하고 예외는 코루틴 안에서 발생한다
}

// 올바른 방법: 코루틴 안에서 잡는다
viewModelScope.launch {
    try {
        riskyCall()
    } catch (e: IOException) {
        _state.update { it.copy(error = e.message) }
    }
}
```

## 3.3 CancellationException을 삼키지 말 것

`try/catch (e: Exception)`은 `CancellationException`까지 잡는다. 이를 삼키면 코루틴 취소가 정상 동작하지 않는다.

```kotlin
// 나쁜 예: 취소 신호까지 삼켜서 코루틴이 취소되지 않는다
viewModelScope.launch {
    try {
        api.fetch()
    } catch (e: Exception) {
        showError(e)
    }
}

// 좋은 예 1: CancellationException은 다시 던진다
viewModelScope.launch {
    try {
        api.fetch()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        showError(e)
    }
}

// 좋은 예 2: 구체적인 예외만 잡는다
viewModelScope.launch {
    try {
        api.fetch()
    } catch (e: IOException) {
        showError(e)
    }
}
```

## 3.4 runCatching의 주의점

`runCatching`도 `CancellationException`을 잡아버린다. suspend 호출을 감쌀 때는 취소 예외를 다시 던지는 확장 함수를 만들어 쓰는 것이 안전하다.

```kotlin
suspend inline fun <T> suspendRunCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
```

## 3.5 CoroutineExceptionHandler

처리되지 않은 예외의 **마지막 방어선**이다. 루트 코루틴(스코프에서 직접 시작한 `launch`)에만 동작한다.

```kotlin
val handler = CoroutineExceptionHandler { _, throwable ->
    Log.e("Coroutine", "Unhandled", throwable)
}

viewModelScope.launch(handler) {
    throw RuntimeException("boom") // handler가 받는다. 앱은 크래시하지 않는다
}

// 자식 코루틴에 붙인 핸들러는 무시된다
viewModelScope.launch {
    launch(handler) { // 동작하지 않는다
        throw RuntimeException("boom")
    }
}
```

예외 처리의 기본은 try/catch이고, 핸들러는 로깅이나 크래시 리포팅 같은 최후 수단이다. 핸들러가 있어도 코루틴 자체는 이미 실패 상태라서 복구할 수는 없다.

## 3.6 supervisorScope와 예외

```kotlin
suspend fun loadWidgets() = supervisorScope {
    val a = async { loadA() }
    val b = async { loadB() }

    // supervisorScope에서도 await()에서 예외는 던져진다. 개별로 잡아야 한다
    val resultA = runCatching { a.await() }.getOrNull()
    val resultB = runCatching { b.await() }.getOrNull()
    Widgets(resultA, resultB)
}
```

<br>

# 4. 취소 협력

## 4.1 취소는 협력적이다

코루틴 취소는 강제 종료가 아니라 **취소 요청 플래그를 세우는 것**이다. 코루틴 코드가 취소 가능 지점(suspension point)에서 이를 확인해야 실제로 멈춘다.

```kotlin
// 취소되지 않는 코드: suspend 지점이 없고 isActive도 확인하지 않는다
val job = launch(Dispatchers.Default) {
    var i = 0
    while (i < 1_000_000_000) {
        heavyWork(i++)
    }
}
job.cancel() // 요청은 갔지만 루프는 계속 돈다
```

## 4.2 취소 가능하게 만드는 방법

```kotlin
// 방법 1: isActive 확인
val job = launch(Dispatchers.Default) {
    var i = 0
    while (isActive) {
        heavyWork(i++)
    }
}

// 방법 2: ensureActive() 호출 (취소되었으면 CancellationException을 던진다)
val job = launch(Dispatchers.Default) {
    for (item in items) {
        ensureActive()
        process(item)
    }
}

// 방법 3: yield() 호출 (다른 코루틴에 양보하면서 취소도 확인한다)
val job = launch(Dispatchers.Default) {
    for (item in items) {
        process(item)
        yield()
    }
}
```

`delay`, `withContext`, `await`, Flow의 `emit` 같은 표준 suspend 함수는 이미 취소를 확인한다.

## 4.3 finally에서의 정리 작업

취소되면 `CancellationException`이 던져지므로 `finally`에서 자원을 정리할 수 있다.

```kotlin
val job = launch {
    val recorder = AudioRecorder()
    try {
        recorder.start()
        awaitCancellation()
    } finally {
        recorder.release() // 취소되어도 실행된다
    }
}
```

단, 취소된 상태에서 `finally` 안에서 suspend 함수를 호출하면 바로 다시 `CancellationException`이 던져진다. 정리 작업에 suspend 호출이 필요하면 `NonCancellable`을 쓴다.

```kotlin
try {
    doWork()
} finally {
    withContext(NonCancellable) {
        saveProgress() // 취소 상태에서도 끝까지 실행된다
    }
}
```

`NonCancellable`은 정리 작업처럼 짧은 구간에만 쓰고, 일반 작업에는 쓰지 않는다.

## 4.4 타임아웃

```kotlin
// 시간 초과 시 TimeoutCancellationException을 던진다
val result = withTimeout(3_000) {
    api.fetch()
}

// 시간 초과 시 null을 반환한다
val result = withTimeoutOrNull(3_000) {
    api.fetch()
}
```

`TimeoutCancellationException`은 `CancellationException`의 하위 타입이라서, 위 3.3에서 본 것처럼 무조건 다시 던지면 타임아웃도 호출자까지 전파된다. 타임아웃을 처리하려면 `withTimeoutOrNull`이 편하다.

## 4.5 viewModelScope 취소와 진행 중인 작업

```kotlin
class SaveViewModel @Inject constructor(
    private val repository: DraftRepository,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    // 화면을 나가면 취소되어도 괜찮은 작업
    fun loadDraft() = viewModelScope.launch {
        _state.value = repository.getDraft()
    }

    // 화면을 나가도 끝까지 저장되어야 하는 작업
    fun saveDraft(text: String) {
        appScope.launch {
            repository.save(text)
        }
    }
}
```

화면 생명주기와 무관하게 반드시 끝나야 하는 작업(저장, 업로드)은 `viewModelScope` 대신 Application 스코프나 WorkManager에서 실행한다.

<br>

# 5. Cold Flow vs Hot Flow

## 5.1 비교

| 구분 | Cold Flow (`Flow`) | Hot Flow (`StateFlow`, `SharedFlow`) |
|---|---|---|
| 시작 시점 | `collect` 할 때 시작 | 수집자와 무관하게 존재 |
| 수집자별 실행 | 수집자마다 처음부터 새로 실행 | 모든 수집자가 같은 스트림 공유 |
| 값 보관 | 없음 | `StateFlow`는 최신 값 1개 보관 |
| 종료 | 완료됨 | 보통 종료되지 않음 |
| 용도 | 일회성 데이터 요청, DB 쿼리 | UI 상태, 이벤트 브로드캐스트 |

## 5.2 Cold Flow

```kotlin
val numbers: Flow<Int> = flow {
    println("flow started")
    for (i in 1..3) {
        delay(100)
        emit(i)
    }
}

fun main() = runBlocking {
    numbers.collect { println("A: $it") } // flow started, A: 1, 2, 3
    numbers.collect { println("B: $it") } // flow started, B: 1, 2, 3 (다시 처음부터)
}
```

수집자가 없으면 `flow { }` 블록은 실행되지 않는다. 수집자마다 업스트림이 독립적으로 실행되므로, 네트워크 호출이 담긴 Flow를 여러 곳에서 collect하면 호출이 여러 번 나간다.

## 5.3 StateFlow

**항상 값이 있고**, 최신 상태 하나만 유지하며, 같은 값은 무시(`distinctUntilChanged`)하는 Hot Flow다. UI 상태 표현에 적합하다.

```kotlin
class CounterViewModel : ViewModel() {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    fun increment() {
        _count.update { it + 1 } // 원자적 업데이트
    }
}
```

- 초기값이 필수이다.
- 새 수집자는 즉시 현재 값을 받는다.
- 값이 연속으로 빠르게 바뀌면 중간 값은 **conflate되어 건너뛸 수 있다**.
- `value = x`보다 `update { }`가 경쟁 상태에 안전하다.

```kotlin
// 위험: 읽기와 쓰기 사이에 다른 코루틴이 끼어들 수 있다
_count.value = _count.value + 1

// 안전: compareAndSet을 내부적으로 재시도한다
_count.update { it + 1 }
```

## 5.4 SharedFlow

수집자 여러 명에게 값을 **브로드캐스트**하는 Hot Flow다. `StateFlow`와 달리 초기값이 없고, 같은 값도 그대로 전달한다.

```kotlin
val events = MutableSharedFlow<UiEvent>(
    replay = 0,                              // 새 수집자에게 재전송할 과거 값 개수
    extraBufferCapacity = 64,                // 느린 수집자를 위한 추가 버퍼
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)
```

| 파라미터 | 의미 |
|---|---|
| `replay` | 새 수집자에게 다시 보내줄 최근 값 개수 |
| `extraBufferCapacity` | replay 외에 추가로 보관할 버퍼 |
| `onBufferOverflow` | 버퍼가 가득 찼을 때 정책 (`SUSPEND`, `DROP_OLDEST`, `DROP_LATEST`) |

`StateFlow`는 사실 `replay = 1`, `DROP_OLDEST`, `distinctUntilChanged` 조합인 `SharedFlow`의 특수 형태다.

## 5.5 어떤 것을 쓸까

| 상황 | 선택 |
|---|---|
| 화면이 보여줘야 할 현재 상태 | `StateFlow` |
| 한 번만 처리해야 하는 이벤트 (토스트, 네비게이션) | `Channel` (8장 참고) |
| 여러 수집자에게 알리는 브로드캐스트 | `SharedFlow` |
| Room, 네트워크 등 일회성 데이터 스트림 | `Flow` (cold) |

## 5.6 Room과 Flow

Room은 DAO에서 Flow를 반환하면 테이블 변경 시 자동으로 새 값을 방출한다.

```kotlin
@Dao
interface RecordDao {
    @Query("SELECT * FROM records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecordEntity>>
}

class RecordRepository @Inject constructor(private val dao: RecordDao) {
    fun observeRecords(): Flow<List<Record>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }
}
```

<br>

# 6. Flow 연산자

## 6.1 변환

```kotlin
flowOf(1, 2, 3)
    .map { it * 2 }          // 2, 4, 6
    .filter { it > 2 }       // 4, 6
    .onEach { println(it) }  // 부수효과
    .collect()
```

## 6.2 flatMap 계열

Flow를 반환하는 변환에 쓴다. 세 가지는 **이전 Flow를 어떻게 처리하느냐**가 다르다.

| 연산자 | 동작 | 용도 |
|---|---|---|
| `flatMapConcat` | 이전 Flow가 끝날 때까지 기다린 후 다음 처리 (순차) | 순서 보장이 필요한 작업 |
| `flatMapMerge` | 동시에 병렬 처리 | 독립적인 병렬 요청 |
| `flatMapLatest` | 새 값이 오면 이전 Flow를 **취소**하고 새로 시작 | 검색, 필터 변경 |

```kotlin
// 검색어 입력: 가장 최근 검색어의 결과만 필요하다
val results: Flow<List<Item>> = queryFlow
    .debounce(300)
    .distinctUntilChanged()
    .filter { it.length >= 2 }
    .flatMapLatest { query -> repository.search(query) }
```

## 6.3 combine vs zip

```kotlin
val a = flowOf(1, 2, 3)
val b = flowOf("A", "B")

// combine: 어느 한쪽이 새 값을 내면 각각의 최신 값으로 조합한다
combine(a, b) { x, y -> "$x$y" }  // 예: 1A, 2A, 3A, 3B (타이밍에 따라 다름)

// zip: 양쪽에서 같은 순번끼리 짝을 지어 조합한다
a.zip(b) { x, y -> "$x$y" }       // 1A, 2B (짧은 쪽에서 끝남)
```

UI 상태를 여러 소스에서 조합할 때는 `combine`을 쓴다.

```kotlin
val uiState: StateFlow<HomeUiState> = combine(
    userRepository.observeUser(),
    feedRepository.observeFeed(),
    settingsRepository.observeSettings()
) { user, feed, settings ->
    HomeUiState(user = user, feed = feed, showAds = settings.showAds)
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = HomeUiState()
)
```

## 6.4 debounce, sample, throttle

| 연산자 | 동작 |
|---|---|
| `debounce(t)` | 값이 들어온 후 t 동안 새 값이 없을 때만 방출 (검색어 입력) |
| `sample(t)` | t 간격마다 가장 최근 값을 방출 (스크롤 위치 샘플링) |

공식 `throttleFirst`는 없어서 필요하면 직접 만들어야 한다.

```kotlin
fun <T> Flow<T>.throttleFirst(windowMs: Long): Flow<T> = flow {
    var last = 0L
    collect { value ->
        val now = System.currentTimeMillis()
        if (now - last >= windowMs) {
            last = now
            emit(value)
        }
    }
}
```

## 6.5 buffer, conflate, collectLatest

생산자가 소비자보다 빠를 때의 전략이다.

```kotlin
flow {
    repeat(5) { emit(it); delay(100) }   // 100ms마다 방출
}
    .buffer()                            // 방출과 수집을 분리, 병렬 실행
    .collect { delay(300); println(it) } // 수집이 느려도 방출은 계속된다
```

| 연산자 | 동작 |
|---|---|
| `buffer()` | 버퍼를 두어 생산/소비를 분리. 모든 값 전달 |
| `conflate()` | 소비가 느리면 중간 값을 버리고 최신 값만 전달 |
| `collectLatest { }` | 새 값이 오면 진행 중인 처리를 취소하고 새 값으로 처리 |

## 6.6 예외 처리 연산자

```kotlin
repository.observeRecords()
    .map { it.toUiModel() }
    .retry(3) { cause -> cause is IOException } // 조건부 재시도
    .catch { e ->                               // 업스트림 예외만 잡는다
        emit(emptyList())
    }
    .onCompletion { println("done") }
    .collect { render(it) }
```

`catch`는 **자신보다 위(업스트림)에서 발생한 예외만** 잡는다. `collect` 블록 안의 예외는 잡지 못한다.

## 6.7 flowOn

업스트림이 실행될 Dispatcher를 지정한다.

```kotlin
flow { emit(heavyParse()) }
    .map { transform(it) }
    .flowOn(Dispatchers.Default) // 위쪽(flow, map)만 Default에서 실행된다
    .collect { render(it) }      // 수집은 호출한 코루틴의 컨텍스트에서 실행된다
```

Flow 안에서 `withContext`로 컨텍스트를 바꿔 `emit`하면 예외가 발생한다. 컨텍스트 전환은 `flowOn`으로 한다.

<br>

# 7. stateIn / shareIn과 WhileSubscribed

## 7.1 왜 필요한가

Cold Flow를 UI에 노출하려면 Hot Flow로 바꿔야 한다. 그렇지 않으면 수집자(화면)마다 업스트림(DB 쿼리, 네트워크)이 반복 실행된다.

```kotlin
// stateIn: Flow를 StateFlow로 변환
// shareIn: Flow를 SharedFlow로 변환
```

## 7.2 SharingStarted 정책

| 정책 | 동작 |
|---|---|
| `Eagerly` | 즉시 시작, 스코프가 끝날 때까지 유지 |
| `Lazily` | 첫 수집자가 생기면 시작, 이후 유지 |
| `WhileSubscribed(stopTimeout)` | 수집자가 있는 동안만 업스트림 유지, 없어지면 timeout 후 중단 |

## 7.3 WhileSubscribed(5_000)

```kotlin
val uiState: StateFlow<RecordListState> = repository.observeRecords()
    .map { RecordListState(records = it) }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordListState(isLoading = true)
    )
```

5초(5_000ms)를 두는 이유는 다음과 같다.

- 화면 회전 같은 **구성 변경**은 수집이 잠깐 끊겼다가 곧 재개된다. 이때 업스트림을 중단하고 재시작하면 낭비다.
- 5초는 구성 변경 시간보다 길고, 사용자가 앱을 완전히 떠났을 때는 업스트림을 중단해 자원을 아끼기에 충분히 짧은 관례적 값이다.
- 홈 버튼으로 앱을 나가면 5초 후 업스트림이 중단되고, 돌아오면 다시 시작된다.

## 7.4 MVI와 함께 쓰기

MVI에서는 `MutableStateFlow`에 `update`로 상태를 쌓는 방식과, Flow를 조합해서 `stateIn`으로 노출하는 방식이 있다. 둘을 섞을 수도 있다.

```kotlin
@HiltViewModel
class RecordListViewModel @Inject constructor(
    repository: RecordRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val isRefreshing = MutableStateFlow(false)

    val state: StateFlow<RecordListState> = combine(
        query.debounce(300).flatMapLatest { repository.search(it) },
        isRefreshing
    ) { records, refreshing ->
        RecordListState(records = records, isRefreshing = refreshing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordListState()
    )

    fun onIntent(intent: RecordListIntent) {
        when (intent) {
            is RecordListIntent.QueryChanged -> query.value = intent.query
            RecordListIntent.Refresh -> refresh()
        }
    }
}
```

## 7.5 주의점

- `stateIn`의 `initialValue`는 실제 데이터가 오기 전 UI가 그려질 값이다. 로딩 상태를 표현하도록 설계한다.
- `viewModelScope`에서 `Eagerly`를 쓰면 화면이 없어도 계속 업스트림이 돈다.
- `stateIn`은 함수 호출마다 새 Hot Flow를 만든다. 프로퍼티 getter 안에서 호출하면 접근할 때마다 새로 만들어지므로 한 번만 생성해서 프로퍼티에 저장한다.

```kotlin
// 나쁜 예: 접근할 때마다 새 StateFlow가 만들어진다
val state: StateFlow<X>
    get() = flow.stateIn(viewModelScope, SharingStarted.Eagerly, X())

// 좋은 예
val state: StateFlow<X> = flow.stateIn(viewModelScope, SharingStarted.Eagerly, X())
```

<br>

# 8. Channel과 SideEffect 처리

## 8.1 Channel이란

코루틴 간에 값을 주고받는 **큐**다. 한 값은 **한 수집자에게만** 전달된다.

```kotlin
val channel = Channel<Int>()

launch { for (i in 1..3) channel.send(i) ; channel.close() }
launch { for (v in channel) println(v) }
```

## 8.2 일회성 이벤트에 Channel을 쓰는 이유

토스트, 화면 이동, 스낵바는 **한 번만 소비되어야** 한다. `StateFlow`에 넣으면 화면 회전 후 다시 수집할 때 같은 이벤트가 또 실행된다.

```kotlin
// 문제가 되는 방식: 상태에 이벤트를 넣으면 재수집 시 중복 실행된다
data class State(val toastMessage: String? = null)

// 권장: Channel을 Flow로 노출한다
private val _effect = Channel<UiEffect>(Channel.BUFFERED)
val effect: Flow<UiEffect> = _effect.receiveAsFlow()

fun onDelete() = viewModelScope.launch {
    repository.delete()
    _effect.send(UiEffect.ShowToast("삭제되었습니다"))
}
```

## 8.3 Channel vs SharedFlow(replay = 0)

| 구분 | Channel | SharedFlow(replay=0) |
|---|---|---|
| 수집자 없을 때 | 버퍼에 쌓아두고 첫 수집자에게 전달 | 유실됨 |
| 수집자가 여러 명 | 하나에게만 전달 | 모두에게 전달 |
| 이벤트 보장 | 높음 | 낮음 (구독 전 방출분 유실) |

화면 하나가 이벤트를 소비하는 일반적인 경우에는 Channel이 안전하다. 수집자가 없는 순간(백그라운드)에 발생한 이벤트도 보관되었다가 복귀 시 전달된다.

## 8.4 Compose에서 Effect 수집

```kotlin
@Composable
fun RecordListRoute(
    viewModel: RecordListViewModel = hiltViewModel(),
    onNavigateToDetail: (Long) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is RecordListEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message)
                is RecordListEffect.NavigateToDetail ->
                    onNavigateToDetail(effect.id)
            }
        }
    }

    RecordListScreen(state = state, onIntent = viewModel::onIntent)
}
```

`LaunchedEffect(Unit)` 안에서 수집하면 Composition이 유지되는 동안 한 번만 수집이 시작된다. 백그라운드에서도 수집이 계속되는 것을 막으려면 9장의 `repeatOnLifecycle`을 같이 쓴다.

## 8.5 Channel 종류

| 용량 | 동작 |
|---|---|
| `RENDEZVOUS` (기본, 0) | 수신자가 받을 때까지 `send`가 대기 |
| `BUFFERED` | 기본 버퍼(64)를 두고, 가득 차면 대기 |
| `CONFLATED` | 최신 값 하나만 유지 |
| `UNLIMITED` | 무제한 버퍼 (메모리 주의) |

<br>

# 9. Compose에서 Flow 수집

## 9.1 collectAsStateWithLifecycle

```kotlin
@Composable
fun RecordListRoute(viewModel: RecordListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RecordListScreen(state)
}
```

- Lifecycle이 `STARTED` 이상일 때만 수집하고, 백그라운드로 가면 수집을 중단한다.
- `collectAsState()`는 Lifecycle을 모르기 때문에 백그라운드에서도 계속 수집한다. 안드로이드에서는 `collectAsStateWithLifecycle()`을 기본으로 쓴다.
- 의존성: `androidx.lifecycle:lifecycle-runtime-compose`

## 9.2 repeatOnLifecycle

Compose 밖(Activity, Fragment)이나 `LaunchedEffect` 안에서 쓴다.

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effect.collect { handle(it) }
            }
        }
    }
}
```

`STARTED`에 진입할 때마다 블록을 새로 시작하고, `STOPPED`가 되면 취소한다. 블록 안에서 `launch`를 여러 개 열어 Flow 여러 개를 동시에 수집할 수 있다.

## 9.3 lifecycleScope.launch만 쓰면 안 되는 이유

```kotlin
// 나쁜 예: 백그라운드에서도 수집이 계속된다
lifecycleScope.launch {
    viewModel.state.collect { render(it) }
}
```

앱이 백그라운드에 있어도 업스트림이 계속 동작해서 배터리와 데이터를 낭비하고, `WhileSubscribed`의 중단 효과도 사라진다.

## 9.4 Recomposition 최소화

`StateFlow`에서 큰 State 객체 전체를 받으면 일부 필드만 바뀌어도 이를 읽는 Composable이 모두 재구성될 수 있다. 필요한 값만 파생하거나 State를 안정적(Stable)으로 설계한다.

```kotlin
@Composable
fun RecordListRoute(viewModel: RecordListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 상태를 잘게 나눠 하위 Composable에 전달한다
    RecordList(
        records = state.records,
        isRefreshing = state.isRefreshing,
        onIntent = viewModel::onIntent
    )
}

@Immutable
data class RecordListState(
    val records: ImmutableList<Record> = persistentListOf(),
    val isRefreshing: Boolean = false
)
```

<br>

# 10. 테스트

## 10.1 의존성

```kotlin
// build.gradle.kts
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:<version>")
testImplementation("app.cash.turbine:turbine:<version>")
```

## 10.2 runTest

`runTest`는 `delay`를 **가상 시간**으로 건너뛰기 때문에 실제로 기다리지 않고 테스트가 빠르게 끝난다.

```kotlin
@Test
fun `delay가 가상 시간으로 건너뛰어진다`() = runTest {
    val start = currentTime
    delay(10_000)
    assertEquals(10_000, currentTime - start) // 실제로는 즉시 끝난다
}
```

## 10.3 Main Dispatcher 교체

`viewModelScope`는 `Dispatchers.Main`을 쓰는데, JVM 단위 테스트에는 메인 스레드가 없다. Rule로 교체한다.

```kotlin
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

## 10.4 ViewModel 테스트

```kotlin
class RecordListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRecordRepository()

    @Test
    fun `Load 인텐트를 보내면 목록이 상태에 반영된다`() = runTest {
        repository.setRecords(listOf(Record(1, "첫 기록")))
        val viewModel = RecordListViewModel(repository)

        viewModel.onIntent(RecordListIntent.Load)

        assertEquals(1, viewModel.state.value.records.size)
    }
}
```

## 10.5 Turbine으로 Flow 테스트

```kotlin
@Test
fun `상태가 로딩에서 성공으로 변한다`() = runTest {
    val viewModel = RecordListViewModel(repository)

    viewModel.state.test {
        assertEquals(RecordListState(isLoading = true), awaitItem()) // 초기값

        viewModel.onIntent(RecordListIntent.Load)

        assertEquals(false, awaitItem().isLoading)
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `삭제하면 토스트 이펙트가 발생한다`() = runTest {
    val viewModel = RecordListViewModel(repository)

    viewModel.effect.test {
        viewModel.onIntent(RecordListIntent.Delete(1))

        assertEquals(RecordListEffect.ShowToast("삭제되었습니다"), awaitItem())
    }
}
```

## 10.6 stateIn 테스트 시 주의

`WhileSubscribed`를 쓴 StateFlow는 **수집자가 있어야** 업스트림이 동작한다. 테스트에서 `state.value`만 읽으면 초기값만 나온다.

```kotlin
@Test
fun `stateIn 결과를 확인한다`() = runTest {
    val viewModel = RecordListViewModel(repository)

    // 수집자를 만들어서 업스트림을 시작시킨다
    val job = launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.state.collect()
    }

    assertEquals(expected, viewModel.state.value)
    job.cancel()
}
```

## 10.7 Dispatcher를 주입받는 Repository 테스트

2.4에서 Dispatcher를 주입하도록 만든 이유가 여기서 드러난다.

```kotlin
@Test
fun `Repository가 IO에서 실행된다`() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val repository = RecordRepository(FakeDao(), testDispatcher)

    val result = repository.getRecords()

    assertEquals(3, result.size)
}
```

<br>

# 11. 정리

- Structured Concurrency는 코루틴을 부모-자식 계층으로 묶어 취소와 예외가 자동으로 전파되게 한다. `GlobalScope`는 피하고 `viewModelScope`나 주입한 Application 스코프를 쓴다.
- `coroutineScope`는 자식 하나가 실패하면 전체가 취소되고, `supervisorScope`는 자식끼리 독립적이다. `viewModelScope`는 `SupervisorJob` 기반이다.
- suspend 함수는 호출자가 스레드를 신경 쓰지 않도록 내부에서 `withContext`로 전환한다(main-safe). Dispatcher는 Hilt로 주입해서 테스트에서 교체한다.
- try/catch는 코루틴 빌더 안에서 써야 하고, `catch (e: Exception)`과 `runCatching`은 `CancellationException`을 삼키지 않도록 주의한다.
- `CoroutineExceptionHandler`는 루트 코루틴에만 동작하는 최후 수단이다.
- 취소는 협력적이다. 긴 루프에서는 `isActive`, `ensureActive()`, `yield()`로 확인하고, 정리 작업의 suspend 호출은 `NonCancellable`로 감싼다.
- `Flow`는 Cold, `StateFlow`/`SharedFlow`는 Hot이다. UI 상태는 `StateFlow`, 일회성 이벤트는 `Channel`을 쓴다.
- 검색은 `debounce` + `distinctUntilChanged` + `flatMapLatest`, 여러 소스 조합은 `combine`, 예외는 `catch`(업스트림만 잡음), 스레드는 `flowOn`으로 처리한다.
- Cold Flow를 UI에 노출할 때는 `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`을 쓰고, 프로퍼티로 한 번만 생성한다.
- Compose에서는 `collectAsStateWithLifecycle()`, 그 외에는 `repeatOnLifecycle(STARTED)`로 수집해서 백그라운드 수집을 막는다.
- 테스트는 `runTest`(가상 시간), `MainDispatcherRule`, Turbine을 조합하고, `stateIn` 결과는 수집자를 만들어야 값이 갱신된다.
