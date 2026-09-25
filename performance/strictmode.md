# StrictMode로 메인 스레드 위반 탐지

<br>

## 목차

1. 개요
2. StrictMode 동작 원리
3. 설치 및 활성화
4. ThreadPolicy 상세
5. VmPolicy 상세
6. 위반 감지 리포트 읽는 법
7. 흔한 위반 패턴 — Disk I/O
8. 흔한 위반 패턴 — Network
9. 흔한 위반 패턴 — 리소스 미해제
10. Compose/코루틴 환경에서의 StrictMode
11. Custom Slow Method 탐지
12. 프로덕션 환경에서의 활용
13. CI 통합
14. 실전 사례 분석
15. 다른 도구와의 비교
16. 정리

<br>

## 1. 개요

StrictMode는 Android SDK에 기본 내장된 개발자 도구로, 메인(UI) 스레드에서 수행되어서는 안 되는 무거운 작업(디스크 I/O, 네트워크 호출 등)이나, 해제되지 않은 리소스(Closable, Cursor 등) 같은 흔한 실수를 앱 실행 중에 자동으로 감지해서 로그나 크래시로 알려주는 기능임. LeakCanary가 "객체가 회수되지 않는" 메모리 누수를 잡는 도구라면, StrictMode는 "메인 스레드가 잘못된 작업으로 블로킹되는" 성능 문제와 "리소스를 닫지 않는" 실수를 잡는 도구로, 서로 겹치지 않는 별개의 문제 영역을 커버함.

별도 라이브러리 설치 없이 `android.os.StrictMode` 클래스만으로 바로 사용할 수 있다는 점이 가장 큰 특징이며, `Application.onCreate()`에 몇 줄만 추가하면 디버그 빌드 전반에 걸쳐 상시 감시가 가능함.

<br>

## 2. StrictMode 동작 원리

StrictMode는 크게 두 가지 정책(Policy)으로 나뉨.

- **ThreadPolicy**: 특정 스레드(주로 메인 스레드)에서 실행되어서는 안 되는 작업을 감지. 디스크 읽기/쓰기, 네트워크 호출, 커스텀으로 지정한 느린 호출 등을 스레드 단위로 감시.
- **VmPolicy**: 프로세스(VM) 전체 단위로 감지. 닫히지 않은 리소스(Closable), 해제되지 않은 SQLite 객체, 등록만 되고 해제되지 않은 리스너, 앱 간 파일 공유 시 `file://` URI 노출(`FileUriExposedException`) 등을 감시.

내부적으로는 각 정책에 해당하는 작업이 호출될 때마다(예: `File.read()`, `HttpURLConnection.connect()`) 위반 여부를 체크하는 후킹이 걸려있고, 위반이 감지되면 등록된 Penalty(처벌 방식)에 따라 로그 출력, 화면 깜빡임, 또는 크래시(`death`)를 유발함.

ThreadPolicy는 "이 작업을 이 스레드에서 하면 안 된다"는 즉시성 문제를, VmPolicy는 "이 리소스를 언젠가 정리했어야 하는데 안 했다"는 지연성 문제를 다룬다고 구분해서 이해하면 됨.

<br>

## 3. 설치 및 활성화

별도 의존성 없이 SDK 기본 클래스만으로 사용 가능. 보통 `Application.onCreate()`에서, 디버그 빌드에서만 활성화되도록 `BuildConfig.DEBUG` 조건으로 감쌈.

```kotlin
// MyApplication.kt
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build()
            )

            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .detectLeakedRegistrationObjects()
                    .detectFileUriExposure()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
```

`detectAll()` / `penaltyDeath()`를 함께 쓰면 위반이 일어나는 순간 즉시 앱을 크래시시켜 가장 엄격하게 강제할 수 있지만, 초반에는 로그만 남기는 `penaltyLog()`로 시작해 기존 코드베이스에서 얼마나 많은 위반이 있는지 먼저 파악한 뒤 점진적으로 강화하는 것이 실무에서 무난함.

```kotlin
// 엄격 모드 (권장: 신규 프로젝트 초기, 혹은 특정 모듈만 우선 적용)
StrictMode.setThreadPolicy(
    StrictMode.ThreadPolicy.Builder()
        .detectAll()
        .penaltyLog()
        .penaltyDeath()
        .build()
)
```

<br>

## 4. ThreadPolicy 상세

`ThreadPolicy.Builder()`가 제공하는 주요 감지 옵션.

- `detectDiskReads()` / `detectDiskWrites()`: 메인 스레드에서 파일 읽기/쓰기 감지. SharedPreferences의 동기 커밋, 파일 직접 접근 등이 대표적으로 걸림.
- `detectNetwork()`: 메인 스레드에서 소켓/HTTP 호출 감지. 대부분의 네트워크 라이브러리는 비동기로 동작하지만, 레거시 코드에서 `HttpURLConnection`을 직접 동기 호출하면 걸림.
- `detectCustomSlowCalls()`: `StrictMode.noteSlowCall()`로 개발자가 직접 표시한 구간의 느린 호출 감지(11장 참고).
- `detectResourceMismatches()`: `Resources.getDrawable()` 같은 구식 API에서 발생하는 리소스 타입 불일치 감지 (구버전 API 대상).
- `detectUnbufferedIo()`: 버퍼링되지 않은 I/O 스트림 사용 감지. `FileInputStream`을 직접 쓰지 않고 `BufferedInputStream`으로 감싸야 함을 알려줌.

`Builder`에는 `permitX()` 계열 메서드도 있어서, 특정 항목만 예외적으로 허용하고 나머지는 엄격하게 적용하는 세밀한 조정도 가능함.

```kotlin
StrictMode.ThreadPolicy.Builder()
    .detectAll()
    .permitDiskReads() // 예: 초기화 단계에서 의도적으로 허용해야 하는 디스크 읽기가 있는 경우
    .penaltyLog()
    .build()
```

### Penalty 종류

- `penaltyLog()`: Logcat에 위반 내역 출력. 가장 기본적이고 안전한 선택.
- `penaltyDialog()`: 위반 발생 시 다이얼로그를 띄워 개발 중 즉시 인지하게 함.
- `penaltyDeath()`: 위반 발생 시 앱을 크래시시킴. CI에서 위반을 강제로 막고 싶을 때 사용.
- `penaltyFlashScreen()`: 화면을 짧게 깜빡여 위반을 시각적으로 알림 (API 28+).
- `penaltyDropBox()`: 시스템 DropBox 서비스에 위반 내역 기록.

<br>

## 5. VmPolicy 상세

`VmPolicy.Builder()`가 제공하는 주요 감지 옵션.

- `detectLeakedSqlLiteObjects()`: `Cursor`, `SQLiteDatabase` 등을 닫지 않고 GC되도록 방치한 경우 감지.
- `detectLeakedClosableObjects()`: `Closeable`을 구현한 객체(FileInputStream, Socket 등)를 `close()`하지 않은 경우 감지.
- `detectActivityLeaks()`: Activity 인스턴스가 예상 밖으로 오래 살아있는 경우 감지 (LeakCanary와 겹치는 영역이지만 더 가볍고 단순한 방식).
- `detectLeakedRegistrationObjects()`: `registerReceiver()`, `Cursor.registerContentObserver()` 등으로 등록만 하고 해제하지 않은 경우 감지.
- `detectFileUriExposure()`: `file://` URI를 다른 앱에 그대로 노출하는 경우 감지 (`FileUriExposedException` 유발, Android 7.0 이상에서는 이 자체가 크래시로 이어지므로 FileProvider로 전환해야 함).
- `detectCleartextNetwork()`: 암호화되지 않은(HTTP) 네트워크 트래픽 감지.
- `detectContentUriWithoutPermission()`: 권한 없이 `content://` URI를 다른 앱과 공유하는 경우 감지.
- `detectUntaggedSockets()`: `TrafficStats.setThreadStatsTag()`로 태깅되지 않은 소켓 사용 감지 (네트워크 사용량 분석 시 태깅 누락 방지 목적).
- `detectNonSdkApiUsage()`: 숨겨진(hidden) 또는 비공개 API 사용 감지 (Android 9 이상 정책 대응용).

```kotlin
StrictMode.setVmPolicy(
    StrictMode.VmPolicy.Builder()
        .detectAll()
        .penaltyLog()
        .build()
)
```

ThreadPolicy와 달리 VmPolicy 위반은 발생 즉시가 아니라 GC 시점에 감지되는 경우가 많음(`detectLeakedClosableObjects` 등). 즉 리소스를 닫지 않은 코드가 실행된 시점과 실제 로그가 찍히는 시점 사이에 시간차가 있을 수 있으므로, 로그에 찍힌 스택 트레이스의 시점보다는 "어떤 클래스의 어떤 메서드"가 리소스를 열었는지에 집중해서 원인을 추적해야 함.

<br>

## 6. 위반 감지 리포트 읽는 법

Logcat에 출력되는 StrictMode 위반 로그는 다음과 같은 형태임.

```text
StrictMode policy violation: android.os.strictmode.DiskReadViolation
    at android.os.StrictMode$AndroidBlockGuardPolicy.onReadFromDisk(StrictMode.java:1234)
    at java.io.FileInputStream.<init>(FileInputStream.java:160)
    at com.example.app.data.LocalConfigReader.readConfig(LocalConfigReader.kt:22)
    at com.example.app.home.HomeViewModel.<init>(HomeViewModel.kt:15)
    at com.example.app.home.HomeFragment.onViewCreated(HomeFragment.kt:40)
    at ...
```

읽는 방법은 일반 스택 트레이스와 동일하지만, 맨 위 두 줄(`StrictMode$...onReadFromDisk`)은 프레임워크 내부 후킹 코드이므로 무시하고, 그 아래부터 실제 앱 코드가 시작되는 지점(`LocalConfigReader.readConfig`)이 위반이 발생한 지점임. 그 아래로 호출 스택을 따라가면 어떤 흐름에서 이 호출이 트리거되었는지(`HomeViewModel` 생성자 → `HomeFragment.onViewCreated`) 알 수 있음.

VmPolicy 위반의 경우 리소스를 "연" 시점의 스택 트레이스가 표시되며, 이를 통해 어느 코드에서 `close()`를 빠뜨렸는지 역추적함.

```text
StrictMode policy violation: android.os.strictmode.LeakedClosableViolation;
    A resource was acquired at attached stack trace but never released.
    See java.io.Closeable for information on avoiding resource leaks.
    at java.io.FileOutputStream.<init>(FileOutputStream.java:233)
    at com.example.app.cache.DiskCache.writeEntry(DiskCache.kt:48)
```

<br>

## 7. 흔한 위반 패턴 — Disk I/O

### SharedPreferences 동기 커밋

```kotlin
// 위반 코드
sharedPreferences.edit().putString("key", value).commit() // 메인 스레드 블로킹
```

```kotlin
// 수정: apply()는 비동기로 디스크에 반영되어 메인 스레드를 블로킹하지 않음
sharedPreferences.edit().putString("key", value).apply()
```

`commit()`은 반환값(성공 여부)이 필요한 경우가 아니라면 거의 항상 `apply()`로 대체 가능함. `commit()`이 필요한 경우(예: 저장 직후 바로 다른 프로세스에서 읽어야 하는 경우)라면 별도 백그라운드 스레드/코루틴으로 옮겨야 함.

### 파일 직접 읽기/쓰기

```kotlin
// 위반 코드 — Fragment의 onViewCreated 등 메인 스레드에서 직접 호출
class HomeFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val config = File(requireContext().filesDir, "config.json").readText()
    }
}
```

```kotlin
// 수정: 코루틴 Dispatchers.IO로 이동
class HomeFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val config = withContext(Dispatchers.IO) {
                File(requireContext().filesDir, "config.json").readText()
            }
            bindConfig(config)
        }
    }
}
```

### Room의 동기 쿼리를 메인 스레드에서 직접 호출

Room은 기본적으로 메인 스레드에서 쿼리를 실행하면 예외를 던지도록 설계되어 있지만, `allowMainThreadQueries()`를 실수로 켜둔 상태에서 StrictMode까지 우회되는 경우가 있으므로, 해당 옵션이 디버그 편의를 위한 임시 설정으로만 쓰이고 있는지 주기적으로 점검해야 함.

<br>

## 8. 흔한 위반 패턴 — Network

### 레거시 HttpURLConnection 동기 호출

```kotlin
// 위반 코드
fun fetchData(): String {
    val connection = URL("https://api.example.com/data").openConnection() as HttpURLConnection
    return connection.inputStream.bufferedReader().readText() // 메인 스레드에서 호출 시 위반
}
```

```kotlin
// 수정: 코루틴으로 감싸거나, Retrofit/OkHttp 같은 비동기 지원 라이브러리 사용
suspend fun fetchData(): String = withContext(Dispatchers.IO) {
    val connection = URL("https://api.example.com/data").openConnection() as HttpURLConnection
    connection.inputStream.bufferedReader().readText()
}
```

Retrofit + OkHttp 조합을 정석대로(코루틴 `suspend fun` 또는 `Call.enqueue()`) 사용하면 이 위반은 거의 발생하지 않음. 문제는 초기화 로직이나 레거시 모듈에서 급하게 동기 API를 직접 호출하는 경우인데, StrictMode가 이런 코드를 빠르게 잡아내는 역할을 함.

### 원격 설정(Remote Config)을 앱 시작 시 동기로 fetch

앱 초기화 단계에서 원격 설정값을 "무조건 최신값을 받아온 뒤 진행"하려는 의도로 동기 네트워크 호출을 `Application.onCreate()`에 넣는 경우가 종종 있음. 이는 StrictMode 위반이자 그 자체로 콜드 스타트 시간을 심각하게 늘리는 원인이므로, 캐시된 값으로 우선 시작하고 최신값은 백그라운드에서 비동기로 갱신하는 구조로 바꾸는 것이 맞음.

<br>

## 9. 흔한 위반 패턴 — 리소스 미해제

### Cursor 미해제

```kotlin
// 위반 코드
fun queryUsers(): List<User> {
    val cursor = db.query("users", null, null, null, null, null, null)
    val users = mutableListOf<User>()
    while (cursor.moveToNext()) {
        users.add(User(cursor.getString(0)))
    }
    return users // cursor.close()를 호출하지 않음
}
```

```kotlin
// 수정: use{}로 자동 close 보장
fun queryUsers(): List<User> {
    db.query("users", null, null, null, null, null, null).use { cursor ->
        val users = mutableListOf<User>()
        while (cursor.moveToNext()) {
            users.add(User(cursor.getString(0)))
        }
        return users
    }
}
```

Kotlin의 `use {}` 확장 함수는 `Closeable`을 구현한 모든 객체(Cursor, FileInputStream, Socket 등)에 대해 try-with-resources와 동일한 효과를 주므로, 리소스를 다루는 코드에서는 항상 `use {}`를 기본으로 쓰는 습관을 들이는 것이 StrictMode 위반을 원천적으로 줄이는 가장 확실한 방법.

### FileInputStream/OutputStream 미해제

```kotlin
// 위반 코드
val input = FileInputStream(file)
val bytes = input.readBytes()
// input.close() 누락
```

```kotlin
// 수정
FileInputStream(file).use { input ->
    val bytes = input.readBytes()
}
```

### BroadcastReceiver/ContentObserver 등록 후 해제 누락

`detectLeakedRegistrationObjects()`는 LeakCanary가 잡는 것과 유사한 문제(8장의 BroadcastReceiver 미해제 패턴)를 더 가볍게 감지함. StrictMode는 힙 덤프까지는 뜨지 않지만, 등록 시점의 스택 트레이스만으로도 어디서 해제를 빠뜨렸는지 바로 알 수 있어 디버깅 초기 단계에서 LeakCanary보다 빠른 피드백을 줌.

<br>

## 10. Compose/코루틴 환경에서의 StrictMode

Compose와 코루틴을 정석대로 사용하는 프로젝트에서는 StrictMode 위반이 View 기반 레거시 코드보다 훨씬 적게 발생하는 편이지만, 다음과 같은 경우는 여전히 주의가 필요함.

### remember 블록 내부에서의 동기 I/O

```kotlin
// 위반 위험 코드
@Composable
fun SettingsScreen() {
    val config = remember {
        File(LocalContext.current.filesDir, "config.json").readText() // Composition 중 메인 스레드에서 실행됨
    }
}
```

`remember` 블록은 Composition 과정에서(즉 메인 스레드에서) 즉시 실행되므로, 그 안에서 디스크/네트워크 I/O를 수행하면 바로 위반으로 잡힘. `LaunchedEffect` + `produceState` 또는 `remember { mutableStateOf(...) }` + 코루틴으로 비동기 로딩 패턴으로 바꿔야 함.

```kotlin
// 수정
@Composable
fun SettingsScreen() {
    var config by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        config = withContext(Dispatchers.IO) {
            File(context.filesDir, "config.json").readText()
        }
    }
}
```

### runBlocking으로 코루틴을 동기적으로 대기

```kotlin
// 위반 코드 — 메인 스레드에서 runBlocking으로 suspend 함수를 강제로 동기 실행
fun loadInitialData(): Data {
    return runBlocking {
        repository.fetchData() // 내부가 Dispatchers.IO를 쓰더라도 메인 스레드가 결과를 기다리며 블로킹됨
    }
}
```

`runBlocking`은 이름 그대로 현재 스레드를 블로킹하므로, 이 호출 자체가 메인 스레드에서 이루어지면 StrictMode가 아니더라도 ANR 위험이 커짐. 초기화 로직에서 "동기적으로 결과가 필요해 보이는" 상황일수록 실제로는 비동기 로딩 + 로딩 상태 UI로 바꿀 수 있는 경우가 대부분이므로, `runBlocking`이 메인 스레드에 보이면 우선적으로 의심해야 함.

<br>

## 11. Custom Slow Method 탐지

프레임워크가 기본 제공하는 디스크/네트워크 항목 외에, 앱 자체의 비즈니스 로직에서 "이 구간은 메인 스레드에서 오래 걸리면 안 된다"고 직접 표시하고 싶을 때 `noteSlowCall()`을 사용함.

```kotlin
fun parseLargeJson(raw: String): Config {
    StrictMode.noteSlowCall("parseLargeJson")
    return Json.decodeFromString<Config>(raw)
}
```

`detectCustomSlowCalls()`가 ThreadPolicy에 활성화되어 있으면, 이 지점이 실제로 임계 시간(기본 수백 ms 단위 내부 임계값)을 넘겨 실행되었을 때 위반으로 잡힘. 자동 감지 대상이 아닌 JSON 파싱, 이미지 디코딩, 복잡한 계산 로직 등을 메인 스레드에서 실행하고 있는지 점검하는 용도로 유용함.

<br>

## 12. 프로덕션 환경에서의 활용

StrictMode는 보통 디버그 빌드 전용으로 알려져 있지만, `penaltyListener()`(API 28+)를 활용하면 릴리즈 빌드에서도 위반을 감지해 사내 로깅 시스템으로 전송하는 것이 가능함. 이 경우 크래시(`penaltyDeath`)는 절대 걸지 않고, 로그만 비동기로 수집하는 방식으로 운영해야 함.

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
            .detectAll()
            .penaltyListener(Executors.newSingleThreadExecutor()) { violation ->
                FirebaseCrashlytics.getInstance().recordException(violation)
            }
            .build()
    )
}
```

### 샘플링으로 오버헤드 최소화

프로덕션에서 모든 사용자에게 StrictMode를 전면 활성화하면 감시 자체의 오버헤드가 누적될 수 있으므로, 일부 사용자 비율(예: 1~5%)에만 원격 설정(Remote Config)으로 활성화해 실사용 환경에서의 위반 데이터를 표본으로 수집하는 방식이 실무에서 흔히 쓰임.

```kotlin
if (remoteConfig.getBoolean("strict_mode_sampling_enabled")) {
    // 샘플링 대상 사용자에게만 penaltyListener 기반 StrictMode 활성화
}
```

<br>

## 13. CI 통합

`penaltyDeath()`를 Instrumentation 테스트 실행 시에만 활성화하면, PR마다 새로 추가된 메인 스레드 위반 코드를 병합 전에 자동으로 잡아낼 수 있음.

```kotlin
// androidTest
@RunWith(AndroidJUnit4::class)
class StrictModeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .penaltyDeath()
                .build()
        )
    }

    @Test
    fun launchingHomeScreen_doesNotViolateStrictMode() {
        composeRule.onNodeWithTag("home_list").assertExists()
    }
}
```

```yaml
# .github/workflows/strictmode-test.yml
name: StrictMode Violation Test

on:
  pull_request:
    branches: [ main ]

jobs:
  strictmode-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - name: Run StrictMode instrumentation tests
        run: ./gradlew connectedDebugAndroidTest --tests "*StrictModeTest"
```

이 방식의 단점은 테스트가 실제로 실행하는 코드 경로만 커버한다는 점이므로, 핵심 화면들의 진입/전환 흐름을 폭넓게 도는 테스트를 함께 구성해야 실효성이 있음.

<br>

## 14. 실전 사례 분석

### 사례 1: 앱 시작 시 SharedPreferences 초기화 위반

콜드 스타트 벤치마크(Baseline Profile 문서 참고)를 진행하던 중 `timeToInitialDisplayMs`가 예상보다 크게 나온 원인을 StrictMode 로그로 역추적했더니, `Application.onCreate()`에서 여러 개의 `SharedPreferences.getInstance()` 호출이 동기적으로 파일을 여는 지점에서 디스크 읽기 위반이 다수 발생하고 있었던 경우가 흔함. `PreferenceDataStore`(DataStore)로 전환하거나, 초기화 시점을 지연시켜 실제로 필요한 화면에서만 값을 읽도록 구조를 바꾸면 위반과 스타트업 시간이 함께 개선됨.

### 사례 2: 로그인 토큰 저장 로직에서의 동기 커밋

로그인 성공 직후 토큰을 저장하고 바로 다음 화면으로 이동하는 흐름에서, "저장이 확실히 끝난 뒤 이동해야 한다"는 생각으로 `commit()`을 메인 스레드에서 직접 호출하는 경우가 흔함. 실제로는 `apply()`로 바꿔도 다음 화면 진입 시점에는 이미 값이 반영되어 있을 확률이 매우 높고(비동기 반영이지만 매우 빠름), 정말 엄격한 순서 보장이 필요하다면 저장 완료를 명시적으로 기다리는 코루틴 기반 API로 감싸는 편이 메인 스레드 블로킹 없이 순서를 보장하는 올바른 방법.

### 사례 3: 이미지 캐시 정리 로직의 Closeable 누수

디스크 캐시 정리 배치 로직에서 여러 파일을 순회하며 `FileInputStream`으로 해시를 계산하는 코드가 예외 발생 시 `close()`를 건너뛰는 경로가 있어 VmPolicy 위반이 반복적으로 잡힌 사례. `use {}`로 감싸기만 해도 예외 발생 여부와 무관하게 항상 정리되므로, 이런 배치성 파일 순회 로직일수록 `use {}` 패턴을 기본으로 강제하는 것이 안전함.

<br>

## 15. 다른 도구와의 비교

| 도구 | 감지 대상 | 활성화 방식 | 특징 |
|---|---|---|---|
| StrictMode | 메인 스레드 I/O, 리소스 미해제, 정책 위반 | SDK 기본 내장, 코드 몇 줄 | 즉시성 있는 스레드 위반을 가볍게, 실시간으로 감지 |
| LeakCanary | 메모리 누수 (객체가 회수되지 않는 문제) | 라이브러리 추가 | 힙 덤프 기반 정밀 분석, 참조 체인 자동 추적 |
| Macrobenchmark | 콜드 스타트/스크롤 성능 수치 | 라이브러리 추가, 별도 벤치마크 모듈 | 정량적 수치 비교, Baseline Profile 생성과 연계 |
| Perfetto / Systrace | CPU, 프레임, 시스템 콜 전반 | 시스템 트레이스 도구 | 특정 원인을 넘어 시스템 전체 타임라인에서 병목 지점 탐색 |

네 도구는 서로 대체재가 아니라 겹치지 않는 영역을 보는 상호보완적 도구임. StrictMode로 "메인 스레드에서 하면 안 되는 일"을 상시 걸러내고, LeakCanary로 "회수되지 않는 객체"를 잡고, Macrobenchmark로 개선 효과를 수치화하고, 그래도 원인이 안 잡히는 복잡한 병목은 Perfetto로 타임라인을 직접 들여다보는 흐름이 실무에서 자연스러움.

<br>

## 16. 정리

- StrictMode는 SDK 기본 내장 도구로, 메인 스레드에서의 부적절한 I/O(ThreadPolicy)와 해제되지 않은 리소스/등록 객체(VmPolicy) 두 축으로 위반을 감지함
- `Application.onCreate()`에서 `BuildConfig.DEBUG` 조건으로 활성화하며, 초반에는 `penaltyLog()`로 현황을 파악하고 점진적으로 `penaltyDeath()`까지 강화하는 흐름이 무난함
- ThreadPolicy는 디스크 읽기/쓰기, 네트워크 호출, 커스텀 느린 호출(`noteSlowCall`)을 감지하고, VmPolicy는 Cursor/Closeable 미해제, 리스너/Receiver 등록 후 미해제, FileUriExposure 등을 감지함
- 리포트의 스택 트레이스에서 프레임워크 후킹 코드(`StrictMode$...`)를 제외한 첫 앱 코드 라인이 위반 발생 지점이며, VmPolicy 위반은 리소스를 "연" 시점의 스택을 보여줌
- `SharedPreferences.commit()` 대신 `apply()`, 파일/네트워크 I/O는 `Dispatchers.IO`로 이동, Closeable 객체는 `use {}`로 감싸는 것이 가장 흔한 위반들에 대한 기본 대응
- Compose 환경에서는 `remember` 블록 내부의 동기 I/O, 메인 스레드에서의 `runBlocking` 사용이 특히 주의할 지점
- `penaltyListener()`(API 28+)를 활용하면 릴리즈 빌드에서도 일부 샘플링된 사용자에게 위반을 수집해 사내 로깅 시스템으로 보낼 수 있음
- Instrumentation 테스트에 `penaltyDeath()`를 걸어 CI에서 새로운 위반의 유입을 자동으로 막을 수 있으며, 핵심 네비게이션 흐름을 폭넓게 도는 테스트와 함께 구성해야 커버리지가 확보됨
- LeakCanary, Macrobenchmark, Perfetto와는 겹치지 않는 영역을 다루는 상호보완적 도구로, 함께 조합해 사용하는 것이 일반적
