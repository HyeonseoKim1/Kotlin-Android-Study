# Android 성능 최적화

<br>

# 목차

1. 성능 최적화 접근법
2. 측정 도구
3. 앱 시작 시간
4. 렌더링과 Jank
5. Compose 성능
6. 메모리
7. 배터리와 백그라운드 작업
8. 네트워크와 DB
9. APK 크기와 빌드
10. 정리

<br>

# 1. 성능 최적화 접근법

## 1.1 측정 먼저, 최적화는 나중

- 추측으로 최적화하지 않는다. 먼저 측정하고, 병목을 찾고, 고친 뒤 다시 측정한다.
- 기준 지표 없이 고친 최적화는 효과를 증명할 수 없다.
- 디버그 빌드는 성능이 왜곡된다. 반드시 **release 빌드(R8 적용)** 또는 `benchmark` 빌드 타입으로 측정한다.

## 1.2 핵심 지표

| 영역 | 지표 | 기준 |
| --- | --- | --- |
| 시작 | Cold start TTID / TTFD | TTID 5초 이내 (Play Console 기준), 가능하면 1초대 |
| 렌더링 | Frame time | 60Hz 16.6ms, 120Hz 8.3ms |
| 렌더링 | Slow frame / Frozen frame | Slow: 16ms 초과, Frozen: 700ms 초과 |
| 안정성 | ANR 비율, Crash 비율 | Play Console Android vitals bad behavior 임계값 이하 |
| 메모리 | Heap, PSS, Leak | OOM 없이 안정적 |
| 배터리 | Excessive wake-ups, Stuck partial wake locks | Android vitals 임계값 이하 |

- **TTID** (Time To Initial Display): 첫 프레임이 그려질 때까지
- **TTFD** (Time To Full Display): `reportFullyDrawn()` 호출 시점. 사용자가 실제로 쓸 수 있는 상태

## 1.3 최적화 사이클

```text
측정 -> 병목 식별 -> 가설 -> 수정 -> 재측정 -> 회귀 방지(CI)
```

- 마지막 단계가 중요하다. Macrobenchmark를 CI에 붙여 회귀를 막아야 최적화가 유지된다.

<br>

# 2. 측정 도구

## 2.1 Android Studio Profiler

- CPU Profiler: 메서드 트레이스, 시스템 트레이스
- Memory Profiler: Heap dump, Allocation 추적
- Network Profiler / Energy Profiler
- 빠른 확인용. 정밀 측정에는 Perfetto나 Macrobenchmark가 낫다.

## 2.2 Perfetto / System Trace

- 시스템 전체(CPU 스케줄링, 렌더링 파이프라인, Binder 호출)를 타임라인으로 본다.
- 앱 코드에 커스텀 트레이스 구간을 넣을 수 있다.

```kotlin
import androidx.tracing.trace

fun loadFeed() {
    trace("FeedRepository.loadFeed") {
        // 측정하고 싶은 작업
    }
}
```

- Compose 코드는 `androidx.compose.runtime:runtime-tracing`을 추가하면 Composable 단위로 트레이스가 보인다.

## 2.3 Macrobenchmark

- 실제 앱을 별도 프로세스에서 띄워 시작 시간, 스크롤 Jank 등을 측정한다.
- 별도 `com.android.test` 모듈로 구성한다.

```kotlin
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCold() = benchmarkRule.measureRepeated(
        packageName = "com.example.app",
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial()
    ) {
        pressHome()
        startActivityAndWait()
    }
}
```

- `StartupMode`: COLD, WARM, HOT
- `CompilationMode`: None, Partial(Baseline Profile 적용), Full
- 스크롤 측정은 `FrameTimingMetric`을 사용한다.

```kotlin
@Test
fun scrollFeed() = benchmarkRule.measureRepeated(
    packageName = "com.example.app",
    metrics = listOf(FrameTimingMetric()),
    iterations = 5,
    startupMode = StartupMode.WARM
) {
    startActivityAndWait()
    val list = device.findObject(By.res("feed_list"))
    list.setGestureMargin(device.displayWidth / 5)
    list.fling(Direction.DOWN)
    device.waitForIdle()
}
```

## 2.4 Microbenchmark

- 특정 함수 단위(파싱, 정렬 등)의 실행 시간을 정밀하게 측정한다.
- `BenchmarkRule`과 `measureRepeated {}` 블록 사용.

## 2.5 Play Console의 Android vitals

- 실사용자 데이터 기반. 랩 측정과 달리 실제 기기 분포를 반영한다.
- ANR, Crash, 시작 시간, Slow rendering, 배터리 지표 제공.

<br>

# 3. 앱 시작 시간

> App Startup 라이프사이클 자체는 Android-App-Startup 노트 참고. 여기서는 최적화 관점만 다룬다.

## 3.1 시작 유형

- **Cold**: 프로세스 생성부터 시작. 가장 느리다.
- **Warm**: 프로세스는 살아있고 Activity만 새로 생성.
- **Hot**: Activity가 메모리에 있고 앞으로만 가져옴.

## 3.2 Cold start에서 일어나는 일

```text
프로세스 생성 -> Application.onCreate -> Activity 생성 -> 첫 프레임 렌더링
```

- 최적화 대상은 `Application.onCreate`, 첫 Activity의 `onCreate`, 첫 화면 레이아웃/Compose 구성이다.

## 3.3 Application.onCreate 가볍게 하기

- 초기화를 전부 `onCreate`에 몰아넣지 않는다.
- 필수 초기화만 남기고 나머지는 **지연(lazy)** 또는 **백그라운드**로 옮긴다.

```kotlin
@HiltAndroidApp
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // 반드시 필요한 것만 동기 초기화
        initCrashReporter()

        // 나머지는 첫 프레임 이후로 미룬다
        appScope.launch(Dispatchers.Default) {
            initAnalytics()
            initImageLoader()
        }
    }
}
```

- 초기화 의존성이 복잡하면 **Jetpack App Startup** 라이브러리로 순서를 관리한다.
- ContentProvider로 자동 초기화되는 서드파티 SDK는 `tools:node="remove"`로 끄고 직접 초기화 시점을 통제한다.

## 3.4 Splash Screen API

- Android 12+는 시스템 스플래시가 기본 적용된다. 직접 만든 Splash Activity는 시작 시간만 늘린다.
- `androidx.core:core-splashscreen`으로 하위 버전까지 대응한다.
- `setKeepOnScreenCondition`은 꼭 필요한 최소 시간만 사용한다. 오래 잡으면 체감 시작 시간이 길어진다.

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        splash.setKeepOnScreenCondition { viewModel.isLoading.value }

        setContent { AppRoot() }
    }
}
```

## 3.5 Baseline Profile

- 앱 설치 시 자주 쓰는 코드 경로를 **미리 AOT 컴파일**하게 알려주는 프로필.
- JIT 워밍업 구간이 사라져서 시작 시간과 첫 스크롤 성능이 눈에 띄게 개선된다. (보통 시작 시간 약 30% 안팎 개선 사례가 많다.)
- Macrobenchmark의 `BaselineProfileRule`로 생성한다.

```kotlin
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.example.app"
    ) {
        pressHome()
        startActivityAndWait()
        // 핵심 사용자 여정(CUJ)을 재현
        device.findObject(By.text("피드")).click()
        device.waitForIdle()
    }
}
```

- 핵심 사용자 여정(CUJ)만 담는다. 모든 화면을 넣으면 프로필이 커져서 오히려 비효율적이다.
- 라이브러리(Compose, Room 등)는 자체 Baseline Profile을 포함해서 배포한다.

## 3.6 Startup Profile (R8)

- R8이 DEX 배치를 최적화해서 시작 시 필요한 클래스를 한곳에 모은다.
- 디스크 I/O와 페이지 폴트가 줄어든다.

## 3.7 reportFullyDrawn

- 비동기로 데이터를 불러온 뒤 화면이 실제로 완성된 시점에 호출한다.
- TTFD가 측정되어 Android vitals와 Macrobenchmark에 반영된다.

```kotlin
// 첫 데이터가 화면에 표시된 뒤
ReportDrawnWhen { uiState is UiState.Success }
```

- Compose에서는 `ReportDrawnWhen` / `ReportDrawn` API 사용.

<br>

# 4. 렌더링과 Jank

## 4.1 렌더링 파이프라인

```text
Input -> Animation -> Measure/Layout -> Draw(RecordingCanvas) -> RenderThread(GPU) -> Display
```

- 한 프레임 안에 UI 스레드 작업(Measure/Layout/Draw 기록)과 RenderThread 작업이 모두 끝나야 한다.
- 16.6ms를 넘기면 프레임 드롭(Jank)이 발생한다.

## 4.2 Jank의 주요 원인

- 메인 스레드에서 무거운 연산, I/O, JSON 파싱
- 과도한 Recomposition / Layout 중첩
- 큰 Bitmap 디코딩, 반복적인 객체 할당으로 인한 GC
- 오버드로우, 복잡한 clip/알파 연산

## 4.3 메인 스레드 보호

- 디스크/네트워크/DB 접근은 메인에서 하지 않는다.
- Coroutine의 `Dispatchers.IO` / `Default`로 옮기고, 결과만 메인에서 받는다.

```kotlin
class FeedViewModel(
    private val repository: FeedRepository
) : ViewModel() {

    fun load() = viewModelScope.launch {
        // repository 내부에서 withContext(Dispatchers.IO) 처리
        val feed = repository.fetchFeed()
        _state.value = FeedState.Success(feed)
    }
}

class FeedRepository(
    private val api: FeedApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun fetchFeed(): List<FeedItem> = withContext(ioDispatcher) {
        api.getFeed().map { it.toDomain() }
    }
}
```

- Dispatcher를 주입받게 만들면 테스트에서 교체하기 쉽다.

## 4.4 StrictMode

- 개발 중 메인 스레드의 디스크/네트워크 접근, 리소스 누수를 로그로 잡아준다.

```kotlin
if (BuildConfig.DEBUG) {
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .penaltyLog()
            .build()
    )
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
            .detectLeakedClosableObjects()
            .detectActivityLeaks()
            .penaltyLog()
            .build()
    )
}
```

## 4.5 View 시스템 최적화 (레거시 화면)

- 레이아웃 계층을 평탄하게 유지한다. `ConstraintLayout`으로 중첩을 줄인다.
- `RecyclerView`는 `DiffUtil` / `ListAdapter`를 쓰고, `setHasFixedSize(true)`, `RecycledViewPool` 공유를 고려한다.
- 오버드로우는 개발자 옵션의 "GPU 오버드로우 디버그"로 확인한다. 불필요한 배경은 제거한다.

## 4.6 JankStats

- 실제 사용자 환경에서 프레임 드롭을 수집하는 라이브러리.
- 어느 화면/상태에서 Jank가 났는지 `PerformanceMetricsState`로 컨텍스트를 붙여 분석한다.

<br>

# 5. Compose 성능

> Stability와 Recomposition 원리는 Compose Stability & Recomposition 노트 참고. 여기서는 실전 패턴만 정리한다.

## 5.1 Recomposition을 줄이는 기본 원칙

- 상태 읽기를 **가능한 늦은 시점, 가능한 좁은 범위**로 미룬다.
- 파라미터가 **stable**하면 Compose가 건너뛸 수 있다(skipping).
- Strong Skipping Mode(Compose Compiler 최근 버전 기본값)에서는 unstable 파라미터도 인스턴스 동등성으로 skip을 시도하지만, 여전히 불변 모델이 유리하다.

## 5.2 LazyList에는 key와 contentType

```kotlin
LazyColumn {
    items(
        items = feedItems,
        key = { it.id },
        contentType = { it.type }
    ) { item ->
        FeedRow(item)
    }
}
```

- `key`가 없으면 위치 기반으로 식별되어 아이템 추가/삭제 시 불필요한 재구성이 늘어난다.
- `contentType`은 Composition 재사용 효율을 높인다.

## 5.3 derivedStateOf

- 빠르게 변하는 상태에서 **결과가 덜 자주 변하는 값**을 만들 때 사용한다.

```kotlin
val listState = rememberLazyListState()

// 나쁜 예: 스크롤할 때마다 Recomposition
// val showButton = listState.firstVisibleItemIndex > 0

// 좋은 예: 값이 true/false로 바뀔 때만 Recomposition
val showButton by remember {
    derivedStateOf { listState.firstVisibleItemIndex > 0 }
}
```

- 입력과 결과가 같은 빈도로 변한다면 `derivedStateOf`는 오버헤드만 늘린다.

## 5.4 상태 읽기 지연 (Deferred Read)

- 자주 바뀌는 값(스크롤 오프셋, 애니메이션 값)은 **람다**로 넘겨서 Layout/Draw 단계에서 읽는다.
- Composition 단계의 Recomposition을 건너뛴다.

```kotlin
// 나쁜 예: offset이 바뀔 때마다 Recomposition
Box(Modifier.offset(x = scrollOffset.dp))

// 좋은 예: Layout 단계에서만 읽음
Box(Modifier.offset { IntOffset(scrollOffset.roundToInt(), 0) })

// Draw 단계에서만 읽는 예
Canvas(Modifier.fillMaxSize()) {
    drawRect(color = animatedColor.value)
}
```

- `graphicsLayer { alpha = ... }`, `drawBehind {}`도 같은 원리다.

## 5.5 remember로 계산 캐싱

```kotlin
@Composable
fun SortedList(items: List<Item>, comparator: Comparator<Item>) {
    // items나 comparator가 바뀔 때만 재계산
    val sorted = remember(items, comparator) {
        items.sortedWith(comparator)
    }
    LazyColumn { items(sorted, key = { it.id }) { Row(it) } }
}
```

- Composable 본문에서 무거운 연산을 직접 하면 Recomposition마다 반복 실행된다.
- 정말 무겁다면 ViewModel에서 계산해서 State로 내려주는 편이 낫다.

## 5.6 불변 컬렉션과 안정성

- `List<T>`는 인터페이스라 unstable로 취급되는 경우가 있다.
- 해결책: `kotlinx.collections.immutable`의 `ImmutableList`, `@Immutable` / `@Stable` 어노테이션, 또는 Compose Compiler의 stability configuration 파일.

```kotlin
@Immutable
data class FeedUiState(
    val items: ImmutableList<FeedItem>,
    val isRefreshing: Boolean
)
```

## 5.7 람다 안정성

- Strong Skipping Mode에서는 람다가 자동으로 remember 처리된다.
- 이전 버전에서는 ViewModel 메서드 참조(`viewModel::onClick`)를 쓰거나 `remember { {...} }`로 감싼다.

## 5.8 Compose 성능 디버깅

- **Layout Inspector**: Recomposition 횟수와 skip 횟수 표시
- **Compose Compiler Metrics / Reports**: 어떤 Composable이 restartable/skippable인지, 어떤 파라미터가 unstable인지 리포트 출력

```kotlin
// build.gradle.kts (Compose Compiler 리포트)
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}
```

- 반드시 **release 빌드**로 확인한다. 디버그 빌드는 Compose가 훨씬 느리다.

## 5.9 Compose에서 흔한 안티 패턴

- Composable 안에서 객체를 매번 새로 생성 (`Modifier`, `TextStyle`, `Brush` 등을 remember 없이 반복 생성)
- `LazyColumn`을 `Column` + `verticalScroll` 안에 중첩
- 큰 리스트를 `Column`으로 한 번에 그림
- 스크롤 상태를 상위 Composable에서 읽어서 전체 하위 트리 재구성

<br>

# 6. 메모리

## 6.1 Android 메모리 구조 요약

- 앱마다 Heap 상한이 있고, 넘으면 `OutOfMemoryError`가 발생한다.
- 시스템 메모리가 부족하면 LMK(Low Memory Killer)가 백그라운드 프로세스부터 종료한다.
- 자세한 프로세스 구조는 Android Process & Memory 노트 참고.

## 6.2 대표적인 메모리 누수 패턴

| 원인 | 설명 | 해결 |
| --- | --- | --- |
| Context 장기 참조 | static/싱글톤이 Activity Context를 보유 | `applicationContext` 사용 |
| 리스너 미해제 | register 후 unregister 누락 | 라이프사이클에 맞춰 해제 |
| Handler / Runnable | 지연 메시지가 Activity 참조 유지 | `removeCallbacks`, 또는 Coroutine 사용 |
| View 참조 | Fragment가 `onDestroyView` 후에도 binding 보유 | `_binding = null` 처리 |
| Coroutine 스코프 | 잘못된 스코프(GlobalScope)에서 장기 실행 | `viewModelScope`, `lifecycleScope` |
| 익명/내부 클래스 | 외부 클래스 암시적 참조 | static 중첩 클래스, 약참조, 스코프 정리 |

```kotlin
// Fragment View Binding 누수 방지
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentProfileBinding.bind(view)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
```

```kotlin
// Flow 수집은 lifecycle-aware하게
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.state.collect { render(it) }
    }
}
```

- Compose에서는 `collectAsStateWithLifecycle()` 사용.

## 6.3 LeakCanary

- 디버그 빌드에 의존성만 추가하면 자동으로 누수를 감지하고 참조 체인을 보여준다.

```kotlin
dependencies {
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
```

## 6.4 Bitmap과 이미지

- 이미지는 메모리를 가장 많이 쓰는 자원이다. 크기 = 가로 x 세로 x 픽셀당 바이트(ARGB_8888은 4바이트).
- 화면에 표시할 크기에 맞게 **다운샘플링**한다.
- Coil / Glide 같은 라이브러리가 캐시, 다운샘플링, 라이프사이클 관리를 해준다. (Coil-glide 노트 참고)
- Coil에서 `size`를 명확히 주고, 리스트에서는 불필요한 크로스페이드를 줄인다.

## 6.5 객체 할당과 GC

- 스크롤, 애니메이션 같은 hot path에서 객체를 반복 생성하면 GC가 자주 돌고 Jank가 생긴다.
- 루프 안의 불필요한 박싱(`Int` -> `Integer`), 임시 컬렉션 생성, 문자열 연결을 줄인다.
- Memory Profiler의 Allocation 추적으로 hot spot을 찾는다.

```kotlin
// 나쁜 예: 반복마다 중간 컬렉션 생성
val result = list.filter { it.active }.map { it.name }.sorted()

// 대량 데이터라면 Sequence로 중간 컬렉션 제거
val result = list.asSequence()
    .filter { it.active }
    .map { it.name }
    .sorted()
    .toList()
```

- 데이터가 작으면 Sequence가 오히려 느릴 수 있다. 측정해서 판단한다.

## 6.6 메모리 압박 대응

```kotlin
class App : Application(), ComponentCallbacks2 {
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            imageCache.clear()
        }
    }
}
```

<br>

# 7. 배터리와 백그라운드 작업

## 7.1 원칙

- 백그라운드에서 **하지 않는 것**이 가장 좋은 최적화다.
- 해야 한다면 시스템이 작업을 묶어서 처리할 수 있도록 **WorkManager**에 위임한다.

## 7.2 작업 유형별 선택

| 작업 | 권장 API |
| --- | --- |
| 지연 가능한 보장 작업(동기화, 업로드) | WorkManager |
| 정확한 시각이 필요한 알람 | AlarmManager (`setExactAndAllowWhileIdle`, 권한 필요) |
| 즉시 시작, 사용자가 인지하는 장기 작업 | Foreground Service |
| 앱이 켜져 있을 때만 필요한 작업 | Coroutine (lifecycleScope / viewModelScope) |
| 사용자 시작 대용량 전송 | User-Initiated Data Transfer Job |

## 7.3 WorkManager 제약 조건

```kotlin
val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()
    )
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .build()

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "sync",
    ExistingPeriodicWorkPolicy.KEEP,
    request
)
```

- `enqueueUnique...`로 중복 등록을 막는다.
- 네트워크, 배터리, 충전 상태 조건을 걸어 시스템이 최적 시점에 실행하게 한다.

## 7.4 Doze와 App Standby

- 화면이 꺼지고 움직임이 없으면 Doze 모드로 들어가 네트워크, 알람, Job이 제한된다.
- 즉시 전달이 필요한 푸시는 FCM **high priority** 메시지를 사용한다. 남용하면 우선순위가 낮아진다.

## 7.5 WakeLock

- 꼭 필요한 경우가 아니면 쓰지 않는다. 사용했다면 반드시 `release()`하고, 타임아웃을 지정한다.

```kotlin
val wakeLock = powerManager.newWakeLock(
    PowerManager.PARTIAL_WAKE_LOCK, "app:sync"
)
wakeLock.acquire(10 * 60 * 1000L) // 최대 10분
try {
    doWork()
} finally {
    if (wakeLock.isHeld) wakeLock.release()
}
```

## 7.6 위치, 센서, 네트워크

- 위치는 필요한 정확도와 주기만 요청한다 (`PRIORITY_BALANCED_POWER_ACCURACY` 등). 사용 후 즉시 해제한다.
- 센서 리스너는 `onPause`에서 해제한다.
- 네트워크 요청은 묶어서(batch) 보내고, 불필요한 폴링 대신 푸시를 사용한다.

<br>

# 8. 네트워크와 DB

## 8.1 네트워크

- **OkHttp 캐시**와 HTTP 캐시 헤더를 활용한다. (Retrofit-OkHttp 노트 참고)
- 응답은 필요한 필드만 받도록 API 설계를 조정한다. 페이지네이션(Paging3)으로 한 번에 받는 양을 줄인다.
- 이미지는 CDN과 적절한 해상도/포맷(WebP, AVIF)을 사용한다.
- 요청 병렬화: 독립적인 호출은 `async`로 동시에 실행한다.

```kotlin
suspend fun loadHome(): HomeData = coroutineScope {
    val profile = async { userApi.getProfile() }
    val feed = async { feedApi.getFeed() }
    HomeData(profile.await(), feed.await())
}
```

## 8.2 직렬화

- Gson은 리플렉션 기반이라 느리다. **kotlinx.serialization** 또는 Moshi(codegen)가 시작 시간과 파싱 속도에 유리하다.
- 큰 JSON은 스트리밍 파싱을 고려한다.

## 8.3 Room / SQLite

- 쿼리는 반드시 백그라운드에서 실행한다. (Room은 suspend, Flow 반환 시 자동 처리) (Room-Database 노트 참고)
- 자주 조회하는 컬럼에 `@Index`를 추가한다.
- 한 건씩 insert하지 말고 **트랜잭션으로 묶어서** 일괄 처리한다.
- 필요한 컬럼만 select하는 projection 클래스를 쓴다. 큰 BLOB는 DB에 넣지 않는다.

```kotlin
@Entity(
    tableName = "message",
    indices = [Index(value = ["room_id", "created_at"])]
)
data class MessageEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "room_id") val roomId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val body: String
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM message WHERE room_id = :roomId ORDER BY created_at DESC")
    fun pagingSource(roomId: Long): PagingSource<Int, MessageEntity>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)
}
```

- `EXPLAIN QUERY PLAN`으로 인덱스를 타는지 확인한다.

## 8.4 SharedPreferences와 DataStore

- SharedPreferences의 `commit()`은 동기 I/O라 메인에서 호출하면 위험하다. 최초 로드도 메인을 블로킹할 수 있다.
- 새 코드는 DataStore를 쓴다. 단, 시작 경로에서 DataStore를 동기적으로 읽지 않는다.

<br>

# 9. APK 크기와 빌드

## 9.1 앱 크기가 성능에 미치는 영향

- APK/AAB가 클수록 다운로드 전환율, 설치 시간, 저장 공간 문제가 커진다.
- DEX가 많고 클수록 클래스 로딩과 시작 시간에 영향을 준다.

## 9.2 R8 (코드 축소, 난독화, 최적화)

```kotlin
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

- `isMinifyEnabled`: 사용하지 않는 코드 제거, 난독화, 최적화
- `isShrinkResources`: 사용하지 않는 리소스 제거
- 리플렉션을 쓰는 라이브러리는 keep 규칙이 필요하다. release 빌드로 반드시 동작 확인한다.
- **Full mode**(AGP 8+ 기본)는 더 공격적으로 최적화한다.

## 9.3 크기 줄이기 체크리스트

- Android App Bundle(AAB) 사용 (기기별 split 제공)
- 이미지: WebP 사용, 벡터 드로어블(`VectorDrawable`) 활용
- 사용하지 않는 라이브러리, 언어 리소스 제거 (`resourceConfigurations`)
- 큰 라이브러리 대체 검토 (예: 날짜, JSON 등 기능이 겹치는 의존성 정리)
- APK Analyzer로 크기 상위 항목 확인

## 9.4 빌드 시간 최적화 (개발 생산성)

- `gradle.properties` 설정

```properties
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
kotlin.incremental=true
android.nonTransitiveRClass=true
```

- kapt 대신 **KSP** 사용 (Hilt, Room은 KSP 지원)
- 멀티모듈에서는 모듈 간 의존성을 최소화하고 `api` 대신 `implementation`을 기본으로 사용한다.
- Build Scan 또는 Build Analyzer로 병목 태스크를 확인한다.

<br>

# 10. 정리

## 10.1 핵심 요약

1. **측정이 먼저다.** release 빌드 기준으로 Macrobenchmark, Perfetto, Profiler를 써서 병목을 확인한 뒤 고친다.
2. **시작 시간**: Application.onCreate 경량화, 지연 초기화, Splash Screen API, **Baseline Profile**이 핵심이다.
3. **렌더링**: 메인 스레드를 비우고, 프레임을 16.6ms 안에 끝낸다. JankStats로 실사용 데이터를 수집한다.
4. **Compose**: key/contentType, derivedStateOf, 상태 읽기 지연, 불변 모델, Compiler Report로 불필요한 Recomposition을 줄인다.
5. **메모리**: Context, 리스너, View 참조 누수를 막고, 이미지는 표시 크기에 맞게 다운샘플링한다. LeakCanary로 상시 감시한다.
6. **배터리**: 백그라운드 작업은 WorkManager로 위임하고, WakeLock과 위치 사용은 최소화한다.
7. **네트워크/DB**: 캐시, 페이지네이션, 병렬 호출, 인덱스, 배치 트랜잭션을 활용한다.
8. **크기/빌드**: R8 full mode, 리소스 축소, AAB, KSP, Gradle 캐시를 사용한다.
9. **회귀 방지**: Macrobenchmark를 CI에 붙이고 Android vitals를 주기적으로 확인한다.

## 10.2 한 줄 정리

- 앱 시작이 느릴 때 어떻게 접근하나? -> Perfetto/Macrobenchmark로 Cold start를 측정, onCreate 초기화 분산, Baseline Profile 적용, 재측정.
- Compose Recomposition을 줄이는 방법은? -> 상태 읽기 범위 축소, 람다로 읽기 지연, derivedStateOf, 안정적인 모델, LazyList key.
- 메모리 누수를 어떻게 찾나? -> LeakCanary로 감지, Heap dump로 참조 체인 분석, 라이프사이클에 맞는 해제.
- 백그라운드 작업은 어떻게 설계하나? -> 즉시성/보장 여부에 따라 Coroutine, WorkManager, Foreground Service를 구분하고, 제약 조건으로 시스템이 배치하게 한다.
