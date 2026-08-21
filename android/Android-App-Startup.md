# Android App Startup

<br>

## 목차

1. 앱 시작 과정 개요
2. Application 클래스와 역할
3. 초기화 작업이 앱 시작 속도에 미치는 영향
4. App Startup 라이브러리란 무엇인가
5. Initializer 구현하기
6. Initializer 자동 등록과 발견 원리
7. 의존성이 있는 Initializer 체이닝
8. 특정 Initializer 비활성화하기
9. 지연 초기화(Lazy Initialization) 전략과 병행
10. App Startup와 다른 라이브러리 연동 (WorkManager 등)
11. 멀티프로세스 환경에서 App Startup
12. App Startup 성능 측정과 프로파일링
13. 테스트에서 App Startup 다루기
14. 주의사항과 자주 하는 실수
15. 정리

<br>

## 1. 앱 시작 과정 개요

안드로이드 앱을 아이콘으로 실행하면, 우리가 흔히 보는 "첫 화면"이 뜨기까지 시스템 내부에서는 여러 단계가 순차적으로 일어난다. 이 흐름을 이해해야 어느 지점에서 초기화 작업을 넣어야 앱 시작 속도에 영향을 최소화할 수 있는지 판단할 수 있다.

전체적인 순서는 다음과 같다.

1. 런처(홈 화면)에서 앱 아이콘 탭 → 시스템이 새 프로세스를 생성(Zygote에서 fork)
2. 프로세스가 만들어지면 시스템이 `ContentProvider`들의 `onCreate()`를 가장 먼저 호출
3. 그다음 `Application` 클래스의 `onCreate()` 호출
4. 이후 런처 Activity(`MainActivity` 등)의 `onCreate()` → `onStart()` → `onResume()` 순으로 진행
5. 첫 프레임이 그려지면서 사용자에게 화면이 보임

```kotlin
// 실행 순서를 로그로 직접 확인해보는 예시
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("Startup", "Application.onCreate 호출됨: ${SystemClock.elapsedRealtime()}")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Startup", "MainActivity.onCreate 호출됨: ${SystemClock.elapsedRealtime()}")
    }
}
```

| 단계 | 설명 |
|---|---|
| 프로세스 생성 | Zygote 프로세스를 fork해서 새 앱 프로세스 생성 |
| ContentProvider.onCreate() | 매니페스트에 등록된 모든 Provider가 Application보다 먼저 초기화됨 |
| Application.onCreate() | 앱 전역 초기화가 이루어지는 지점, 여기가 App Startup의 핵심 무대 |
| 첫 Activity 생명주기 | onCreate → onStart → onResume, 이 시점까지 걸리는 시간이 체감 시작 속도 |
| 첫 프레임 렌더링 | TTID(Time to Initial Display)로 측정되는 지점 |

**실전 팁**
- "앱이 느리게 켜진다"는 체감은 대부분 `Application.onCreate()`부터 첫 화면 렌더링까지의 구간에서 결정된다. 이 구간에 무엇이 들어가 있는지가 App Startup 최적화의 핵심 포인트다.
- Cold Start(완전 종료 후 최초 실행), Warm Start(백그라운드에서 재진입), Hot Start(단순 포그라운드 전환)에 따라 최적화해야 할 지점이 다르다는 것을 구분해서 접근하자.

<br>

## 2. Application 클래스와 역할

`Application`은 앱 프로세스 전체에서 단 하나만 존재하는 싱글톤 컴포넌트로, 앱의 모든 컴포넌트(Activity, Service 등)보다 먼저 생성되어 전역 상태와 초기화를 담당한다.

```kotlin
class MyApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        initLogging()
        initCrashReporting()
        initAnalytics()
    }

    private fun initLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun initCrashReporting() {
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
    }

    private fun initAnalytics() {
        AnalyticsSdk.initialize(this, apiKey = BuildConfig.ANALYTICS_KEY)
    }
}
```

```xml
<application
    android:name=".MyApplication"
    android:allowBackup="true"
    android:icon="@mipmap/ic_launcher">
    ...
</application>
```

Hilt를 사용하는 프로젝트라면 `@HiltAndroidApp` 어노테이션을 붙인 Application 클래스가 DI 그래프의 루트 역할도 겸한다.

```kotlin
@HiltAndroidApp
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Hilt가 생성한 컴포넌트가 이 시점 이후 사용 가능
    }
}
```

| 특징 | 설명 |
|---|---|
| 프로세스당 1개 | 앱 프로세스가 살아있는 동안 단 하나의 인스턴스만 존재 |
| 최초 생성 시점 | 모든 Activity/Service보다 먼저 onCreate() 호출 |
| Context 역할 | Application Context는 컴포넌트 생명주기와 무관하게 앱 전체 생명주기를 따름 |
| DI 루트 | Hilt, Koin 등 대부분의 DI 프레임워크가 Application을 진입점으로 사용 |

**실전 팁**
- `Application.onCreate()`는 앱이 실행될 때마다(멀티프로세스 앱이면 프로세스마다) 호출되므로, 여기서 무거운 작업을 하면 모든 화면 진입 경로의 시작 속도에 영향을 준다.
- Application Context를 Activity Context 대신 남용하면 메모리 누수는 피할 수 있지만, 테마나 화면 크기에 의존하는 UI 관련 작업(다이얼로그 생성 등)에는 사용할 수 없다는 제약을 기억하자.

<br>

## 3. 초기화 작업이 앱 시작 속도에 미치는 영향

`Application.onCreate()`에 라이브러리 초기화 코드를 무작정 나열하면, 각 초기화 코드가 순차적으로 실행되면서 그 총합만큼 첫 화면이 늦게 뜬다. 실제 서비스 앱에서는 로깅, 크래시 리포팅, 분석 SDK, 광고 SDK, 이미지 로더, DI 프레임워크 등 초기화해야 할 라이브러리가 열 개를 넘는 경우가 흔하다.

```kotlin
// 안티패턴: 모든 초기화가 Application.onCreate()에 순차적으로 쌓여있음
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initTimber()          // 5ms
        initCrashlytics()     // 40ms
        initAnalyticsSdk()    // 60ms
        initAdSdk()           // 80ms
        initImageLoader()     // 20ms
        initFeatureFlags()    // 100ms
        // 총 305ms가 첫 화면이 뜨기 전에 그대로 소요됨
    }
}
```

이런 구조의 문제는 두 가지다. 첫째, 초기화 순서가 뒤섞여 있어 어떤 라이브러리가 다른 라이브러리에 의존하는지 파악하기 어렵다. 둘째, 여러 모듈에서 각자 필요한 라이브러리를 매니페스트나 Application에 흩어놓으면 초기화 로직이 여러 곳에 중복되거나 충돌한다. `App Startup` 라이브러리는 바로 이 문제, 즉 **초기화 순서를 명시적인 의존성 그래프로 관리**하고 **여러 ContentProvider 초기화를 하나로 합쳐 오버헤드를 줄이는 것**을 목표로 만들어졌다.

| 문제 | 설명 |
|---|---|
| 순차 실행 누적 | 각 초기화 시간이 그대로 합산되어 시작 시간에 반영됨 |
| 순서 파악 어려움 | Application.onCreate() 안에 나열된 코드만으로는 의존 관계가 안 보임 |
| ContentProvider 남발 | 여러 라이브러리가 각자 ContentProvider로 자동 초기화를 구현하면 프로세스 생성 시 오버헤드 누적 |
| 모듈 간 중복 초기화 | 멀티 모듈 프로젝트에서 같은 초기화가 여러 곳에서 반복될 위험 |

**실전 팁**
- 초기화 작업을 추가하기 전에 "이게 정말 첫 화면이 뜨기 전에 완료되어야 하는가?"를 먼저 질문하자. 대부분의 분석/로깅 SDK는 약간의 지연 초기화를 허용해도 무방하다.
- 각 초기화 작업에 걸리는 시간을 실측(트레이싱)해보지 않고 "이 정도는 빠르겠지"라고 추측만으로 판단하지 말자. 의외로 SDK 초기화가 수십~수백 밀리초를 잡아먹는 경우가 많다.

<br>

## 4. App Startup 라이브러리란 무엇인가

`androidx.startup:startup-runtime`은 Jetpack에서 제공하는 라이브러리로, 여러 컴포넌트(라이브러리, 모듈)의 초기화 로직을 **단일 ContentProvider**를 통해 순서와 의존성을 관리하며 실행할 수 있게 해준다.

```kotlin
// build.gradle.kts
dependencies {
    implementation("androidx.startup:startup-runtime:1.2.0")
}
```

핵심 아이디어는 각 초기화 단위를 `Initializer<T>` 인터페이스로 구현하고, 시스템은 매니페스트에 등록된 단 하나의 `InitializationProvider`를 통해 이들을 일괄적으로 발견하고 실행한다는 것이다.

```kotlin
interface Initializer<T> {
    fun create(context: Context): T
    fun dependencies(): List<Class<out Initializer<*>>>
}
```

| 도입 전 | 도입 후 |
|---|---|
| 각 라이브러리가 저마다의 ContentProvider를 등록 | 모든 초기화가 하나의 InitializationProvider 아래로 통합 |
| 초기화 순서가 코드 나열 순서에 암묵적으로 의존 | dependencies()로 순서를 명시적인 그래프로 선언 |
| 프로세스 생성 시 ContentProvider 개수만큼 오버헤드 | ContentProvider 1개로 오버헤드 최소화 |
| WorkManager, Firebase 등 각자 다른 초기화 방식 | 여러 Jetpack 라이브러리가 App Startup 기반으로 표준화됨 |

**실전 팁**
- App Startup은 "초기화 코드를 더 빠르게 만들어주는" 라이브러리가 아니라 "초기화 순서와 의존성을 명시적으로, 그리고 더 적은 오버헤드로 관리해주는" 라이브러리라는 점을 정확히 이해하자. 코드 자체의 실행 속도는 여전히 개발자의 책임이다.
- WorkManager, Firebase, Room의 일부 확장 기능 등 여러 Jetpack 라이브러리가 이미 App Startup을 내부적으로 사용하고 있으므로, 프로젝트에 이미 App Startup 기반 초기화가 암묵적으로 존재할 가능성이 높다.

<br>

## 5. Initializer 구현하기

초기화하고 싶은 컴포넌트마다 `Initializer<T>` 인터페이스를 구현한 클래스를 만든다. 제네릭 타입 `T`는 이 초기화 작업이 만들어내는 결과물의 타입이다.

```kotlin
class LoggingInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        if (isDebugBuild(context)) {
            Timber.plant(Timber.DebugTree())
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    private fun isDebugBuild(context: Context): Boolean {
        return context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
}

class AnalyticsInitializer : Initializer<AnalyticsClient> {
    override fun create(context: Context): AnalyticsClient {
        return AnalyticsClient.builder(context)
            .apiKey(BuildConfig.ANALYTICS_KEY)
            .build()
            .also { it.trackAppOpen() }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
```

`create()`가 반환한 값은 이후 다른 코드에서 `AppInitializer`를 통해 다시 꺼내 쓸 수 있다.

```kotlin
val analyticsClient = AppInitializer.getInstance(context)
    .initializeComponent(AnalyticsInitializer::class.java)
```

| 요소 | 설명 |
|---|---|
| create(context) | 실제 초기화 로직, 초기화 결과 객체를 반환 |
| dependencies() | 이 Initializer가 실행되기 전에 먼저 실행되어야 할 Initializer 목록 |
| Initializer<Unit> | 반환값이 필요 없는 단순 초기화(로깅 설정 등)에 사용 |
| AppInitializer.getInstance() | 이미 초기화된 컴포넌트를 다시 꺼내 쓰거나 수동으로 트리거할 때 사용 |

**실전 팁**
- `create()`에서 반환하는 타입은 이후 다른 Initializer의 의존성으로 연결되거나, 코드 어딘가에서 재사용할 수 있으므로 되도록 의미 있는 객체(설정이 끝난 클라이언트 인스턴스 등)를 반환하도록 설계하자.
- 특별히 반환할 결과가 없는 단순 초기화라면 `Initializer<Unit>`을 사용하는 것이 관례다.

<br>

## 6. Initializer 자동 등록과 발견 원리

구현한 `Initializer`는 `AndroidManifest.xml`에 `InitializationProvider`의 `<meta-data>`로 등록해야 시스템이 앱 시작 시점에 자동으로 실행한다.

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">

    <meta-data
        android:name="com.example.app.startup.LoggingInitializer"
        android:value="androidx.startup" />

    <meta-data
        android:name="com.example.app.startup.AnalyticsInitializer"
        android:value="androidx.startup" />
</provider>
```

동작 원리는 다음과 같다. `InitializationProvider.onCreate()`가 호출되면, 매니페스트에서 자신에게 등록된 `<meta-data>` 목록을 리플렉션으로 읽어서 각 클래스명을 `Initializer` 인스턴스로 생성하고, `dependencies()`가 반환하는 그래프를 위상 정렬(topological sort)해서 순서대로 `create()`를 호출한다.

```kotlin
// AppInitializer 내부 동작을 요약하면 대략 이런 흐름이다
// 1) manifest의 <meta-data>를 리플렉션으로 스캔
// 2) 각 Initializer 클래스를 인스턴스화
// 3) dependencies()로 그래프를 구성, 순환 의존성이 있으면 예외 발생
// 4) 위상 정렬 순서대로 create() 호출
```

라이브러리 모듈(다른 gradle 모듈)에서도 자신의 `AndroidManifest.xml`에 동일한 방식으로 `<meta-data>`를 추가하면, 매니페스트 병합(manifest merge) 과정을 통해 앱 전체의 초기화 목록에 자동으로 합쳐진다.

| 등록 방식 | 특징 |
|---|---|
| meta-data 등록 | InitializationProvider가 앱 시작 시 자동으로 발견하고 실행 |
| tools:node="merge" | 여러 모듈의 InitializationProvider 선언을 하나로 병합 |
| 라이브러리 모듈에서 등록 | 라이브러리를 추가하기만 해도 초기화가 자동으로 딸려옴 |

**실전 팁**
- 여러 gradle 모듈이 각자 `InitializationProvider`를 선언하면 매니페스트 병합 충돌이 날 수 있으므로, `tools:node="merge"`를 명시하는 것이 안전하다.
- 라이브러리를 만들 때 App Startup 기반으로 초기화를 자동화해두면, 그 라이브러리를 쓰는 앱 개발자는 별도의 초기화 코드를 작성할 필요 없이 의존성만 추가하면 되는 편의성을 제공할 수 있다.

<br>

## 7. 의존성이 있는 Initializer 체이닝

여러 초기화 작업 사이에 순서가 있어야 한다면(예: 로깅 설정이 끝난 후에 크래시 리포팅을 초기화해야 하는 경우), `dependencies()`에 선행되어야 할 Initializer 클래스를 나열하면 된다.

```kotlin
class LoggingInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        Timber.plant(Timber.DebugTree())
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

class CrashReportingInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        // LoggingInitializer가 먼저 실행된 뒤에 호출되는 것이 보장됨
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        Timber.i("크래시 리포팅 초기화 완료")
    }
    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(LoggingInitializer::class.java)
    }
}

class AnalyticsInitializer : Initializer<AnalyticsClient> {
    override fun create(context: Context): AnalyticsClient {
        // CrashReportingInitializer의 결과물(Unit)이 필요 없어도, 실행 순서만 보장받을 수 있음
        return AnalyticsClient.initialize(context)
    }
    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(CrashReportingInitializer::class.java)
    }
}
```

이렇게 구성하면 실행 순서는 `LoggingInitializer → CrashReportingInitializer → AnalyticsInitializer`로 고정되며, 이 순서는 매니페스트에 `<meta-data>`가 나열된 순서와 무관하게 `dependencies()` 그래프에 의해서만 결정된다.

| 상황 | 처리 방식 |
|---|---|
| A가 B보다 먼저 실행되어야 함 | B의 dependencies()에 A::class.java 포함 |
| 순환 의존성 (A→B→A) | 런타임에 예외(StartupException) 발생 |
| 서로 독립적인 초기화 | dependencies()를 emptyList()로 유지 |

**실전 팁**
- 의존성 그래프가 복잡해질수록 전체 그림을 파악하기 어려워지므로, 초기화 순서가 실제로 중요한 경우에만 `dependencies()`를 사용하고, 독립적인 작업은 굳이 인위적으로 순서를 엮지 않는 것이 유지보수에 좋다.
- 순환 의존성은 컴파일 타임이 아니라 런타임에 예외로 발견되므로, 의존성 그래프를 변경한 뒤에는 반드시 앱을 실행해서 크래시 여부를 확인하자.

<br>

## 8. 특정 Initializer 비활성화하기

테스트 환경이나 특정 빌드 변형(build variant)에서 특정 Initializer를 실행하고 싶지 않을 때는, 매니페스트에서 자동 초기화를 비활성화(disable)하고 필요한 시점에 수동으로 트리거하는 방식을 사용한다.

```xml
<!-- 자동 초기화를 막고 싶은 Initializer는 value를 빈 문자열로 지정 -->
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">

    <meta-data
        android:name="com.example.app.startup.AnalyticsInitializer"
        tools:node="remove" />
</provider>
```

특정 라이브러리가 제공하는 Initializer(예: WorkManager의 기본 초기화)를 비활성화하고 커스텀 설정으로 직접 초기화하고 싶을 때도 같은 방식을 사용한다.

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">

    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

수동으로 초기화가 필요한 시점에는 `AppInitializer`를 직접 호출한다.

```kotlin
fun initAnalyticsManually(context: Context) {
    AppInitializer.getInstance(context)
        .initializeComponent(AnalyticsInitializer::class.java)
}
```

| 목적 | 방법 |
|---|---|
| 특정 Initializer를 항상 비활성화 | meta-data에 tools:node="remove" |
| 테스트에서만 비활성화 | test 소스셋의 매니페스트에서 remove 처리 |
| 자동 실행 대신 수동 트리거 | manifest에서 제거 후 AppInitializer.initializeComponent() 직접 호출 |

**실전 팁**
- 테스트 코드(Instrumented Test)에서는 실제 네트워크나 SDK 초기화가 불필요하거나 방해가 되는 경우가 많으므로, `androidTest` 소스셋에 별도 매니페스트를 두고 해당 Initializer들을 `remove` 처리하는 패턴이 유용하다.
- WorkManager처럼 커스텀 `Configuration`이 필요한 라이브러리는 기본 자동 초기화를 끄고 `Configuration.Provider`를 앱에서 직접 구현하는 경우가 많다는 점을 알아두자.

<br>

## 9. 지연 초기화(Lazy Initialization) 전략과 병행

App Startup은 초기화 "순서"를 관리해줄 뿐, 모든 초기화를 Application.onCreate() 시점에 즉시 실행해야 한다는 뜻은 아니다. 실제로 첫 화면 렌더링에 꼭 필요하지 않은 작업은 지연시키는 것이 최선의 최적화다.

```kotlin
class AnalyticsInitializer : Initializer<Lazy<AnalyticsClient>> {
    override fun create(context: Context): Lazy<AnalyticsClient> {
        // 실제 AnalyticsClient 생성은 최초 사용 시점까지 미룸
        return lazy { AnalyticsClient.initialize(context) }
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
```

첫 화면 렌더링 이후로 초기화를 미루고 싶다면, 메인 스레드의 `Looper`가 유휴 상태(idle)가 될 때 실행되도록 `IdlingHandler`나 `Handler.post`를 활용할 수 있다.

```kotlin
class DeferredAdSdkInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        Handler(Looper.getMainLooper()).post {
            // 첫 프레임이 그려진 이후, 메인 스레드가 한가할 때 실행
            AdSdk.initialize(context)
        }
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
```

코루틴 환경이라면 `Dispatchers.Default`나 별도 스코프에서 백그라운드로 초기화를 넘기는 방식도 흔하다.

```kotlin
class BackgroundCacheWarmupInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            CacheWarmupWorker.warmup(context) // 시작 화면과 무관한 캐시 준비 작업
        }
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
```

| 전략 | 적합한 경우 |
|---|---|
| 즉시 초기화 (Application.onCreate) | 첫 화면 렌더링 이전에 반드시 준비되어야 하는 것 (예: 테마, 필수 DI 그래프) |
| Lazy 위임 | 최초 사용 시점까지 생성 비용을 미뤄도 되는 것 |
| 메인 스레드 idle 이후 실행 | UI를 막지 않지만 메인 스레드에서 실행되어야 하는 것 |
| 백그라운드 스레드 실행 | UI와 무관하고 스레드 안전한 초기화 |

**실전 팁**
- "일단 Initializer로 만들었으니 즉시 실행되어야 한다"는 착각을 하기 쉬운데, `create()` 내부에서 실제 작업을 지연시키는 것은 얼마든지 가능하다는 것을 기억하자.
- 광고 SDK, 상세 분석 SDK처럼 사용자가 앱을 실제로 쓰기 시작한 뒤에 필요한 것들은 대부분 지연 초기화의 좋은 후보다.

<br>

## 10. App Startup와 다른 라이브러리 연동 (WorkManager 등)

여러 Jetpack 라이브러리가 이미 App Startup 기반으로 초기화를 구현하고 있다. 대표적으로 `WorkManager`는 기본적으로 `WorkManagerInitializer`를 통해 자동 초기화되며, 이를 커스터마이징하려면 App Startup의 비활성화 패턴과 `Configuration.Provider`를 함께 사용한다.

```kotlin
class MyApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
            .setWorkerFactory(MyCustomWorkerFactory())
            .build()
}
```

```xml
<!-- 기본 WorkManagerInitializer를 끄고, on-demand 초기화로 전환 -->
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

앱 내부의 여러 기능 모듈이 각자의 `Initializer`를 정의하고, 이들을 조합해서 전체 앱의 초기화 그래프를 구성하는 것도 흔한 멀티모듈 패턴이다.

```kotlin
// core-logging 모듈
class LoggingInitializer : Initializer<Unit> { /* ... */ }

// feature-auth 모듈, core-logging에 의존
class AuthSdkInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        AuthSdk.initialize(context)
    }
    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(LoggingInitializer::class.java)
    }
}
```

| 라이브러리 | App Startup 연동 방식 |
|---|---|
| WorkManager | WorkManagerInitializer로 기본 자동 초기화, Configuration.Provider로 커스터마이징 가능 |
| Firebase | 여러 Firebase 컴포넌트가 ComponentDiscoveryService/App Startup 유사 메커니즘 사용 |
| Room (일부 확장) | 특정 확장 라이브러리에서 App Startup 기반 초기화를 제공하는 경우 있음 |
| 사내/팀 커스텀 라이브러리 | 각 모듈이 자체 Initializer를 노출해서 앱이 의존성만 추가하면 되도록 설계 가능 |

**실전 팁**
- WorkManager의 자동 초기화를 끄고 커스텀 `Configuration`을 쓰는 것은 `WorkerFactory`(Hilt 연동 등)를 커스터마이징해야 하는 프로젝트에서 사실상 필수적인 패턴이다.
- 멀티모듈 프로젝트라면 "이 모듈을 추가하면 무엇이 자동으로 초기화되는가"를 각 모듈의 README나 문서에 명시해두는 것이 팀 협업에 큰 도움이 된다.

<br>

## 11. 멀티프로세스 환경에서 App Startup

`InitializationProvider`는 `ContentProvider`이므로, 매니페스트에 `android:process` 속성으로 별도 프로세스가 지정된 컴포넌트가 있는 앱(예: 별도 프로세스에서 동작하는 Service)에서는 **해당 프로세스가 생성될 때마다** 초기화 로직이 다시 실행된다는 점을 이해해야 한다.

```xml
<application android:name=".MyApplication">
    <provider
        android:name="androidx.startup.InitializationProvider"
        android:authorities="${applicationId}.androidx-startup"
        android:exported="false" />

    <service
        android:name=".sync.SyncService"
        android:process=":sync" /> <!-- 별도 프로세스에서 실행 -->
</application>
```

`:sync` 프로세스가 시작될 때도 동일하게 `Application.onCreate()`와 `InitializationProvider`가 실행되므로, 특정 초기화 작업(예: UI 관련 초기화)은 메인 프로세스에서만 실행되도록 분기 처리가 필요할 수 있다.

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (isMainProcess()) {
            // UI 스레드 관련, 광고 SDK 등 메인 프로세스에서만 필요한 초기화
        }
        // 로깅처럼 모든 프로세스에서 필요한 초기화는 분기 없이 실행
        initLogging()
    }

    private fun isMainProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val pid = Process.myPid()
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            manager.runningAppProcesses?.find { it.pid == pid }?.processName
        }
        return processName == packageName
    }
}
```

| 상황 | 결과 |
|---|---|
| 단일 프로세스 앱 | InitializationProvider가 1회만 실행 |
| android:process가 지정된 컴포넌트 존재 | 해당 프로세스 생성 시마다 InitializationProvider 재실행 |
| 프로세스 무관 초기화(로깅 등) | 분기 없이 그대로 실행해도 무방 |
| 프로세스 종속 초기화(광고, UI SDK 등) | 프로세스 이름을 확인해서 메인 프로세스에서만 실행하도록 분기 필요 |

**실전 팁**
- 멀티프로세스 앱에서 무거운 SDK를 모든 프로세스마다 초기화하면 메모리와 시작 속도 모두에 불필요한 부담을 준다. 정말 그 프로세스에서 필요한 초기화인지 항상 점검하자.
- `Application.getProcessName()`은 API 28 이상에서만 제공되므로, 하위 버전 호환이 필요하면 `ActivityManager.runningAppProcesses`를 활용한 폴백 로직이 필요하다.

<br>

## 12. App Startup 성능 측정과 프로파일링

초기화 작업을 추가하거나 재구성한 뒤에는 반드시 실측을 통해 실제로 시작 속도가 개선되었는지 확인해야 한다. 안드로이드는 이를 위한 여러 도구를 제공한다.

```kotlin
// 특정 Initializer의 소요 시간을 직접 측정하는 예시
class AnalyticsInitializer : Initializer<AnalyticsClient> {
    override fun create(context: Context): AnalyticsClient {
        return trace("AnalyticsInitializer") {
            AnalyticsClient.initialize(context)
        }
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    private inline fun <T> trace(label: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        return block().also {
            Log.d("StartupTrace", "$label: ${SystemClock.elapsedRealtime() - start}ms")
        }
    }
}
```

시스템 트레이스(Systrace) 구간을 명시적으로 표시하면 Android Studio Profiler에서 각 초기화 구간을 시각적으로 확인할 수 있다.

```kotlin
class CrashReportingInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        Trace.beginSection("CrashReportingInit")
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        } finally {
            Trace.endSection()
        }
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
```

앱 전체의 시작 시간은 `adb`로도 측정할 수 있다.

```bash
adb shell am start -W -n com.example.app/.MainActivity
```

이 명령어의 결과에서 `TotalTime`(전체 소요 시간)과 `WaitTime`을 확인할 수 있으며, `TotalTime`이 App Startup에서 관리하는 초기화 작업들의 영향을 가장 직접적으로 반영한다.

| 측정 도구 | 용도 |
|---|---|
| adb shell am start -W | TTID/TotalTime을 커맨드라인에서 빠르게 확인 |
| Trace.beginSection/endSection | Android Studio Profiler의 시스템 트레이스에서 구간 시각화 |
| Macrobenchmark (Jetpack) | Cold/Warm/Hot Start 시간을 자동화된 벤치마크로 측정 |
| Firebase Performance Monitoring | 실제 사용자 기기에서의 시작 시간 데이터 수집 |

**실전 팁**
- 로컬 개발 기기(고성능)에서 체감하는 시작 속도와 실제 사용자의 저사양 기기에서의 속도는 크게 다를 수 있으므로, 가능하면 저사양 기기나 Macrobenchmark로 실측하자.
- 초기화 작업 하나하나를 `Trace.beginSection`으로 감싸두면, 나중에 시작 속도 회귀(regression)가 발생했을 때 어떤 Initializer가 원인인지 프로파일러에서 빠르게 특정할 수 있다.

<br>

## 13. 테스트에서 App Startup 다루기

단위 테스트(JVM 테스트)에서는 `Initializer`의 `create()` 로직을 일반 클래스처럼 직접 호출해서 테스트할 수 있다.

```kotlin
class AnalyticsInitializerTest {

    @Test
    fun `AnalyticsClient가 올바른 apiKey로 초기화된다`() {
        val context = mockk<Context>(relaxed = true)
        val initializer = AnalyticsInitializer()

        val client = initializer.create(context)

        assertThat(client.apiKey).isEqualTo(BuildConfig.ANALYTICS_KEY)
    }

    @Test
    fun `dependencies는 비어있다`() {
        val initializer = AnalyticsInitializer()
        assertThat(initializer.dependencies()).isEmpty()
    }
}
```

계측 테스트(Instrumented Test)에서 `AppInitializer` 전체 흐름을 검증하고 싶다면 `androidx.startup:startup-runtime`이 제공하는 테스트 유틸리티를 사용할 수 있다.

```kotlin
@RunWith(AndroidJUnit4::class)
class StartupInitializationTest {

    @Test
    fun initializationProvider가_정상적으로_초기화된다() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val analyticsClient = AppInitializer.getInstance(context)
            .initializeComponent(AnalyticsInitializer::class.java)

        assertThat(analyticsClient).isNotNull()
    }
}
```

테스트 환경에서 실제 네트워크 SDK가 초기화되는 것을 막고 싶다면, `androidTest` 소스셋에 별도 매니페스트를 두고 해당 Initializer를 `remove` 처리하는 방법을 8장에서 다룬 방식 그대로 적용한다.

```xml
<!-- src/androidTest/AndroidManifest.xml -->
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="com.example.app.startup.AdSdkInitializer"
        tools:node="remove" />
</provider>
```

| 테스트 종류 | 검증 대상 |
|---|---|
| JVM 단위 테스트 | 개별 Initializer의 create()/dependencies() 로직 |
| 계측 테스트 | InitializationProvider를 통한 전체 초기화 흐름 |
| androidTest 매니페스트 오버라이드 | 테스트 환경에서 불필요한 SDK 초기화 제거 |

**실전 팁**
- `Initializer` 구현체는 결국 `create(context)`를 호출하는 평범한 함수이므로, 굳이 계측 테스트 없이도 JVM 단위 테스트로 대부분의 로직을 검증할 수 있다.
- CI 환경에서 네트워크 SDK가 실제로 초기화되어 테스트가 불안정(flaky)해지는 경우가 흔하므로, androidTest 매니페스트에서 네트워크 관련 Initializer를 걷어내는 것이 테스트 안정성에 도움이 된다.

<br>

## 14. 주의사항과 자주 하는 실수

1. App Startup을 도입하기만 하면 초기화 자체가 빨라진다고 착각하는 경우가 많다. App Startup은 순서 관리와 ContentProvider 오버헤드 절감 도구일 뿐, 개별 초기화 코드의 실행 속도는 그대로 개발자의 책임이다.
2. `dependencies()`를 습관적으로 모든 Initializer에 연결해서 불필요하게 복잡한 순차 실행 그래프를 만드는 경우가 있다. 정말 순서가 필요한 경우에만 의존성을 선언하자.
3. 순환 의존성을 만들어서 런타임에 `StartupException`을 만나는 경우가 있다. 의존성 그래프를 변경했다면 반드시 앱을 실행해서 확인해야 한다.
4. `Application.onCreate()`에 여전히 무거운 동기 초기화 코드를 그대로 두고 App Startup만 도입해서, 실질적인 시작 속도 개선 효과를 보지 못하는 경우가 흔하다.
5. 지연 초기화가 가능한 작업(광고 SDK, 상세 분석 등)까지 전부 즉시 초기화 그래프에 포함시켜서 최적화 기회를 놓치는 경우가 많다.
6. 멀티프로세스 앱에서 프로세스별 분기 없이 모든 초기화를 그대로 실행해서, 불필요한 프로세스에서까지 무거운 SDK가 초기화되는 문제를 만든다.
7. 여러 gradle 모듈에서 `InitializationProvider`를 각자 선언하면서 `tools:node="merge"`를 빠뜨려 매니페스트 병합 충돌이 발생하는 경우가 있다.
8. WorkManager 같은 라이브러리의 기본 Initializer를 비활성화한 뒤, 커스텀 `Configuration.Provider` 구현을 빠뜨려서 WorkManager가 아예 초기화되지 않는 실수를 한다.
9. 초기화 코드를 수정한 뒤 실제 기기나 Macrobenchmark로 실측하지 않고 "빨라졌을 것"이라고 추측만으로 결론짓는 경우가 많다.
10. 테스트 환경에서 실제 네트워크 SDK나 광고 SDK가 그대로 초기화되도록 방치해서, CI 테스트가 불안정해지거나 불필요한 외부 호출이 발생하는 문제를 겪는다.

<br>

## 15. 정리

앱 시작 과정은 프로세스 생성 → ContentProvider 초기화 → `Application.onCreate()` → 첫 Activity 생명주기 순으로 진행되며, 이 중 `Application.onCreate()`부터 첫 화면 렌더링까지의 구간이 사용자가 체감하는 시작 속도를 좌우한다. `App Startup` 라이브러리는 이 구간에서 이루어지는 여러 초기화 작업을, 각각을 `Initializer<T>`로 구현하고 `dependencies()`로 순서를 명시적인 그래프로 선언함으로써 관리하기 쉽게 만들어주며, 여러 라이브러리가 각자 ContentProvider를 등록하던 것을 하나의 `InitializationProvider`로 통합해 프로세스 생성 오버헤드도 줄여준다. 다만 App Startup 자체가 초기화 코드를 더 빠르게 만들어주지는 않으므로, 실제 성능 개선은 정말 즉시 필요한 초기화와 지연 가능한 초기화를 구분하고, 멀티프로세스 환경에서 불필요한 초기화를 걸러내며, `adb shell am start -W`나 Macrobenchmark 같은 도구로 꾸준히 실측하는 습관에서 나온다는 점을 기억해두자.
