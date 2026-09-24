# LeakCanary로 메모리 누수 탐지

<br>

## 목차

1. 개요
2. 메모리 누수가 발생하는 이유
3. GC Root의 종류
4. LeakCanary 동작 원리
5. 설치 및 설정
6. 세부 설정 옵션
7. 누수 탐지 리포트 읽는 법
8. Android View 기반에서 자주 발생하는 누수 패턴
9. Compose에서 흔한 누수 패턴
10. ViewModel과 관련된 누수
11. 커스텀 객체 감시
12. 힙 덤프 직접 분석하기
13. False Positive 다루기
14. CI 통합
15. 실전 사례 분석
16. 다른 도구와의 비교
17. 정리

<br>

## 1. 개요

메모리 누수는 더 이상 필요 없는 객체(예: 종료된 Activity, 소멸된 Fragment)가 어딘가에서 참조를 계속 들고 있어 가비지 컬렉터(GC)가 회수하지 못하는 상태를 말함. 개별 누수 하나는 눈에 띄지 않지만, 화면 전환이 반복되는 앱에서 누수가 누적되면 결국 `OutOfMemoryError`로 이어지거나, 힙이 커지면서 GC 빈도가 늘어 전반적인 성능(프레임 드랍, ANR)에 영향을 줌.

LeakCanary는 Square에서 만든 오픈소스 라이브러리로, 디버그 빌드에서 자동으로 Activity/Fragment/ViewModel 등의 생명주기를 감시하다가 소멸되어야 할 객체가 메모리에 남아있으면 힙 덤프를 뜬 뒤 참조 체인(reference chain)을 분석해 어디서 누수가 발생했는지 알려주는 도구임. 개발 중 수동으로 Android Studio Profiler를 열어 힙을 분석하는 과정을 자동화해준다고 이해하면 됨.

이 문서는 원리부터 실제 코드 패턴, 힙 덤프를 직접 읽는 법, CI 통합까지 실무에서 필요한 범위를 폭넓게 다룸.

<br>

## 2. 메모리 누수가 발생하는 이유

Android에서 메모리 누수가 발생하는 근본 원인은 대부분 "생명주기가 짧은 객체를 생명주기가 긴 객체가 참조하고 있는" 구조임.

- 정적(static) 필드나 싱글톤이 Activity/View의 Context를 직접 들고 있는 경우
- 익명 클래스/내부 클래스(Handler, Runnable, Listener 등)가 바깥 클래스(Activity)에 대한 암묵적 참조를 갖고 있는데, 이 익명 객체의 생명주기가 Activity보다 긴 경우 (예: 지연 실행되는 Handler.postDelayed)
- 등록한 리스너(BroadcastReceiver, LocationListener 등)를 해제하지 않은 경우
- 코루틴/RxJava 구독을 화면 소멸 시점에 취소하지 않은 경우
- Bitmap 등 큰 리소스를 캐시에 넣고 정리하지 않은 경우
- 서드파티 SDK가 내부적으로 Context를 static으로 보관하는 경우 (개발자가 직접 고칠 수 없어 무시 규칙으로 대응해야 함)
- ThreadLocal에 Context나 View를 저장한 뒤 해당 스레드가 스레드 풀에 반환되어 재사용되는 경우

이 중 Kotlin에서 특히 자주 나오는 패턴은 "내부 클래스의 암묵적 외부 참조"임. Java/Kotlin 모두 non-static inner class(또는 Kotlin의 일반 클래스 내부에 정의된 람다/객체)는 컴파일 시 바깥 클래스에 대한 참조를 필드로 자동 추가하기 때문에, 개발자가 의도하지 않아도 Activity 전체가 참조 체인에 걸려있는 경우가 많음.

<br>

## 3. GC Root의 종류

LeakCanary의 리포트를 제대로 읽으려면 GC Root 개념을 먼저 이해해야 함. GC Root는 GC가 "이 객체부터는 도달 가능성을 추적해야 한다"고 판단하는 시작점으로, 대표적으로 다음이 있음.

- Global variable: JNI 전역 참조, 정적 필드 등
- Thread: 실행 중인 스레드 스택 프레임에 있는 로컬 변수
- Stack local: 현재 실행 중인 메서드의 지역 변수
- JNI local/global: 네이티브 코드에서 유지하는 참조
- Monitor: synchronized 블록에서 사용 중인 락 객체
- Finalizer: 아직 finalize()가 호출되지 않은 객체

LeakCanary 리포트의 맨 첫 줄(`GC Root: ...`)은 이 중 하나를 가리키며, 여기서부터 leaking object까지 강한 참조(strong reference)로 연결된 최단 경로를 보여주는 것이 리포트의 본질임. 즉 "이 GC Root가 살아있는 한 저 객체도 절대 회수되지 않는다"는 것을 증명하는 경로임.

<br>

## 4. LeakCanary 동작 원리

LeakCanary의 동작은 크게 다음 단계로 이루어짐.

1. `ActivityLifecycleCallbacks`, `FragmentLifecycleCallbacks` 등을 통해 Activity/Fragment/View/ViewModel의 소멸 시점을 감시
2. 객체가 소멸되어야 하는 시점에 `WeakReference`로 감싸서 `ReferenceQueue`에 등록
3. GC를 유도한 뒤에도 해당 객체가 `ReferenceQueue`에서 회수되지 않으면(즉 아직 참조되고 있으면) "retained object"로 판정
4. 일정 개수 이상의 retained object가 쌓이면(기본 5개) 힙 덤프(`.hprof`)를 생성
5. `Shark`(LeakCanary의 힙 분석 엔진)가 힙 덤프를 파싱해서 GC Root부터 leaking object까지의 최단 강한 참조 경로(shortest strong reference path)를 계산
6. 이 경로를 사람이 읽을 수 있는 형태로 알림 및 인앱 화면에 표시

이 전체 과정은 디버그 빌드에서 백그라운드 스레드로 수행되며, 릴리즈 빌드에는 포함되지 않음.

### Shark 엔진의 내부 동작

Shark는 힙 덤프(.hprof) 파일을 파싱해 객체 그래프를 메모리 효율적으로 순회하는 라이브러리임. 힙 덤프 자체가 수백 MB에 달할 수 있기 때문에, Shark는 다음과 같은 최적화를 적용함.

- 힙 덤프 파일을 전부 메모리에 올리지 않고, 필요한 부분만 랜덤 액세스로 읽는 인덱싱 방식 사용
- BFS(너비 우선 탐색)로 GC Root부터 leaking object까지의 최단 경로를 탐색해 "가장 직접적인 원인"을 우선 보여줌
- 경로 탐색 중 `WeakReference`, `SoftReference`, `PhantomReference` 등 약한 참조는 경로에서 제외 (약한 참조는 GC를 막지 않으므로 누수의 원인이 될 수 없음)
- 알려진 라이브러리 누수 패턴(`AndroidReferenceMatchers`)을 활용해 "이 경로는 실제 앱 코드 문제가 아니라 OS/SDK의 알려진 이슈"라고 구분해 표시

### Retained 객체 판정 지연

객체가 소멸 시점 직후 바로 GC되지 않았다고 해서 곧바로 누수로 판정하지 않음. LeakCanary는 GC 유도 후 일정 대기 시간을 두고 재확인하는 과정을 거치는데, 이는 GC가 즉시 실행되지 않을 수 있고, 정상적인 캐싱/애니메이션 종료 지연 등으로 인해 일시적으로 참조가 남아있을 수 있기 때문임. 이 임계값은 `retainedVisibleThreshold`, `retainedNullableCount` 등의 설정으로 조정 가능함(6장 참고).

<br>

## 5. 설치 및 설정

### 의존성 추가

```kotlin
// app/build.gradle.kts
dependencies {
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
```

`debugImplementation`으로 추가하면 별도 초기화 코드 없이 디버그 빌드에 앱을 실행하는 것만으로 자동 활성화됨. `ContentProvider` 기반으로 앱 시작 시 자동 등록되는 구조라 `Application.onCreate()`에 아무 코드도 넣을 필요가 없음. 이 `ContentProvider`는 `AndroidManifest.xml`에 라이브러리 자체적으로 병합되어 있어 앱의 매니페스트를 수정할 필요도 없음.

### 멀티모듈 프로젝트에서의 설정

멀티모듈 구조에서는 `app` 모듈뿐 아니라 각 feature 모듈에서도 디버그 빌드 시 LeakCanary가 활성화되도록, 공통 `build-logic` 컨벤션 플러그인에 `debugImplementation` 의존성을 일괄 추가하는 방식이 일반적임.

```kotlin
// build-logic/convention/AndroidApplicationConventionPlugin.kt
dependencies {
    "debugImplementation"(libs.findLibrary("leakcanary-android").get())
}
```

### Compose 전용 확장

Compose를 사용하는 프로젝트라면 `LeakCanary.Config`를 통해 Compose 관련 감시를 세밀하게 조정할 수 있음. 기본 설정만으로도 Activity/Fragment/ViewModel은 자동 감시되지만, Compose의 `remember`로 생성된 객체 등은 명시적으로 watch 등록이 필요한 경우가 있음(9장 참고).

### 감시 대상 커스터마이징

```kotlin
// MyApplication.kt
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        LeakCanary.config = LeakCanary.config.copy(
            dumpHeap = true,
            retainedVisibleThreshold = 3, // 화면에 보이는 상태에서 허용할 retained 객체 수
        )
    }
}
```

특정 클래스를 감시 대상에서 제외하고 싶다면(예: 서드파티 라이브러리의 알려진 누수) `AndroidReferenceMatchers`에 예외 규칙을 추가할 수 있음(13장 참고).

<br>

## 6. 세부 설정 옵션

`LeakCanary.Config`가 제공하는 주요 옵션을 정리하면 다음과 같음.

```kotlin
LeakCanary.config = LeakCanary.config.copy(
    // 힙 덤프 생성 여부. false로 두면 감시만 하고 덤프는 뜨지 않음 (CI 등에서 유용)
    dumpHeap = true,

    // 앱이 화면에 보이는 상태(foreground)일 때 허용할 retained 객체 수.
    // 이 수를 넘으면 힙 덤프를 트리거함. 기본값 5.
    retainedVisibleThreshold = 5,

    // 앱이 백그라운드 상태일 때 더 보수적으로 덤프하고 싶다면 별도 조정 가능
    // (2.x 버전대에서는 AppWatcher.retainedDelayMillis로 대기 시간 조정)

    // 힙 덤프 파일 저장 위치 커스터마이징
    heapDumper = AndroidHeapDumper.SdCardHeapDumper,

    // 특정 조건에서 힙 덤프를 건너뛰고 싶을 때 (예: 저장공간 부족)
    heapDumpOnDismissSnackbar = true,

    // 알려진 라이브러리 누수 무시 규칙 (13장 참고)
    referenceMatchers = AndroidReferenceMatchers.appDefaults,

    // 커스텀 이벤트 리스너 등록 (예: 누수 발생 시 사내 로깅 시스템으로 전송)
    eventListeners = LeakCanary.config.eventListeners + MyCustomEventListener(),

    // 알림을 띄울지 여부. CI 환경 등에서는 false로 끄는 경우도 있음
    showNotifications = true,

    // 힙 덤프 및 분석을 특정 조건(예: 테스트 중)에서만 수행하고 싶을 때
    dumpHeapWhenDebugging = false,
)
```

### 커스텀 이벤트 리스너로 사내 로깅 연동

`OnHeapAnalyzedListener`를 구현하면 LeakCanary가 분석을 마칠 때마다 콜백을 받아, 사내 크래시 리포팅 시스템(Firebase Crashlytics의 커스텀 로그, Sentry 등)에 누수 정보를 함께 전송하는 것도 가능함.

```kotlin
class CrashlyticsLeakListener : OnHeapAnalyzedListener {
    override fun onHeapAnalyzed(heapAnalysis: HeapAnalysis) {
        if (heapAnalysis is HeapAnalysisSuccess) {
            heapAnalysis.applicationLeaks.forEach { leak ->
                FirebaseCrashlytics.getInstance().log(
                    "LeakCanary: ${leak.shortDescription}"
                )
            }
        }
    }
}
```

이렇게 하면 개발자가 로컬에서 직접 앱을 실행하지 않아도, QA나 사내 테스터가 사용하는 도중 발생한 누수를 자동으로 추적할 수 있음.

<br>

## 7. 누수 탐지 리포트 읽는 법

LeakCanary가 누수를 감지하면 알림이 뜨고, 탭하면 인앱 화면에서 참조 체인을 확인할 수 있음. 예시 형태는 다음과 같음.

```text
┬───
│ GC Root: Global variable in native code
│
├─ android.os.Handler instance
│    Leaking: NO (Handler↓ is not leaking)
│    ↓ Handler.callback
├─ com.example.app.home.HomeFragment$loadData$1 instance
│    Leaking: UNKNOWN
│    ↓ HomeFragment$loadData$1.this$0
╰→ com.example.app.home.HomeFragment instance
     Leaking: YES (ObjectWatcher was watching this because
     com.example.app.home.HomeFragment received Fragment#onDestroy()
     callback and Fragment.mFragmentManager is now null)
     key = a1b2c3d4-...
     watchDurationMillis = 5601
     retainedDurationMillis = 5000
```

읽는 순서는 위에서 아래로, GC Root에서 시작해 최종적으로 누수된 객체(`Leaking: YES`)까지의 참조 체인임. 위 예시는 `HomeFragment` 내부에서 만든 익명 `Runnable`(`loadData$1`)이 `Handler`에 콜백으로 등록된 채 남아있어, `HomeFragment`가 `onDestroy()`된 이후에도 `Handler`가 암묵적으로 Fragment를 계속 참조하는 상황을 보여줌.

리포트 해석 시 체크포인트:

- `Leaking: YES`로 표시된 마지막 줄이 실제 누수된 객체
- 화살표(`↓`) 옆의 필드명이 어떤 참조 경로로 누수가 이어졌는지 보여줌
- `Leaking: UNKNOWN`은 아직 확정되지 않은 중간 노드
- `Leaking: NO`는 LeakCanary가 "이 객체 자체는 정상"이라고 판단한 노드 (보통 싱글톤, 시스템 객체)
- `watchDurationMillis`/`retainedDurationMillis`로 얼마나 오래 메모리에 남아있었는지 확인 가능
- 괄호 안의 설명 문구("received Fragment#onDestroy() callback and...")는 LeakCanary가 왜 이 객체가 소멸되었어야 하는지를 설명하는 부분으로, 원인 파악에 중요한 힌트가 됨

### Retained size와 Leak trace 개수

리포트 상단에는 해당 누수로 인해 회수되지 못하고 있는 전체 메모리 크기(Retained size)와, 동일한 누수 패턴이 몇 번 반복되었는지(Leak trace count)가 함께 표시됨. Retained size가 크거나 반복 횟수가 많을수록 우선순위를 높여 처리하는 것이 합리적임. 예를 들어 Bitmap을 들고 있는 누수는 개별 인스턴스당 크기가 커서 Retained size가 수 MB에 달하는 경우가 흔함.

<br>

## 8. Android View 기반에서 자주 발생하는 누수 패턴

### Handler + 지연 실행

```kotlin
// 누수 발생 코드
class HomeFragment : Fragment() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        handler.postDelayed({
            updateUi() // 암묵적으로 this(Fragment)를 참조
        }, 10_000)
    }
}
```

```kotlin
// 수정: onDestroyView에서 콜백 제거
class HomeFragment : Fragment() {
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = Runnable { updateUi() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        handler.postDelayed(updateRunnable, 10_000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateRunnable)
    }
}
```

### 정적 필드에 Context 보관

```kotlin
// 누수 발생 코드
object ImageCache {
    var context: Context? = null // Activity Context를 저장하면 Activity 전체가 누수됨
}
```

```kotlin
// 수정: applicationContext만 저장하거나 WeakReference 사용
object ImageCache {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
```

### 리스너 미해제

```kotlin
// 누수 발생 코드
class LocationFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        locationManager.requestLocationUpdates(provider, 0, 0f, listener)
        // onDestroyView에서 해제하지 않음
    }
}
```

```kotlin
// 수정
override fun onDestroyView() {
    super.onDestroyView()
    locationManager.removeUpdates(listener)
}
```

### BroadcastReceiver 미등록 해제

```kotlin
// 누수 발생 코드
class MainActivity : AppCompatActivity() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { /* ... */ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }
    // onDestroy에서 unregisterReceiver 누락
}
```

```kotlin
// 수정
override fun onDestroy() {
    super.onDestroy()
    unregisterReceiver(receiver)
}
```

### 싱글톤 EventBus에 등록한 구독 해제 누락

`EventBus`, `LiveData`의 전역 버스 패턴, 직접 구현한 pub/sub 구조에서 구독 해제를 잊으면 Activity/Fragment가 계속 참조됨.

```kotlin
// 누수 발생 코드
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    EventBus.getDefault().register(this)
}
// onDestroy에서 unregister 누락
```

```kotlin
// 수정
override fun onDestroy() {
    super.onDestroy()
    EventBus.getDefault().unregister(this)
}
```

### RecyclerView Adapter의 콜백 참조

`Adapter` 내부에 Activity/Fragment의 콜백을 직접 필드로 들고 있으면서, Adapter 인스턴스가 `ViewPager`나 캐시에 의해 Fragment보다 오래 살아있는 경우 누수로 이어질 수 있음. 콜백은 인터페이스로 분리하고, Fragment 소멸 시점에 Adapter의 콜백 참조를 null로 해제하거나, 애초에 Adapter가 Fragment를 직접 참조하지 않고 상위 ViewModel의 데이터만 관찰하도록 구조를 분리하는 것이 안전함.

### RxJava Disposable 미해제

```kotlin
// 누수 발생 코드
class HomeFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repository.fetchData()
            .subscribe { updateUi(it) } // Disposable을 들고 있지 않음
    }
}
```

```kotlin
// 수정: CompositeDisposable로 묶어서 일괄 해제
class HomeFragment : Fragment() {
    private val disposables = CompositeDisposable()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repository.fetchData()
            .subscribe { updateUi(it) }
            .addTo(disposables)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposables.clear()
    }
}
```

### Timer/TimerTask 미취소

`Timer`와 `TimerTask`는 백그라운드 스레드에서 반복 실행되며, `cancel()`을 호출하지 않으면 내부 익명 클래스가 Activity를 계속 참조한 채로 무한 반복됨. `Handler.postDelayed` + `removeCallbacks` 조합이나 코루틴의 `delay` 루프로 대체하고 생명주기에 맞춰 취소하는 것이 안전함.

<br>

## 9. Compose에서 흔한 누수 패턴

### remember에 Context/Activity 직접 캡처

```kotlin
// 누수 위험 코드
@Composable
fun MyScreen() {
    val context = LocalContext.current
    val activity = context as Activity

    val listener = remember {
        SomeListener { activity.doSomething() } // Composition을 벗어나도 Activity 참조 유지 가능
    }
}
```

`remember`로 생성된 람다/객체가 Composition이 사라진 뒤에도 다른 곳(전역 리스너 등)에 등록된 채 남아있으면 누수로 이어질 수 있음. `DisposableEffect`로 등록/해제 쌍을 맞추는 것이 안전함.

```kotlin
// 수정
@Composable
fun MyScreen() {
    DisposableEffect(Unit) {
        val listener = SomeListener { /* ... */ }
        registerListener(listener)
        onDispose {
            unregisterListener(listener)
        }
    }
}
```

### rememberCoroutineScope 밖에서 launch한 Job 미취소

`rememberCoroutineScope()`로 얻은 스코프는 Composition 소멸 시 자동 취소되지만, 별도로 만든 `CoroutineScope(Dispatchers.Main)`를 필드로 들고 있으면서 `Composable` 밖(예: ViewModel이 아닌 싱글톤 객체)에서 관리하면 취소 시점을 놓치기 쉬움. 가능한 한 `viewModelScope` 또는 `rememberCoroutineScope`를 사용하는 것이 안전함.

### AndroidView의 리소스 해제 누락

`AndroidView`로 전통적인 View(예: MapView, WebView)를 Compose 트리에 포함시키는 경우, 해당 View가 내부적으로 갖는 리소스(WebView의 경우 특히 유명)를 `onRelease` 콜백에서 명시적으로 정리해야 함.

```kotlin
AndroidView(
    factory = { context -> WebView(context) },
    update = { /* ... */ },
    onRelease = { webView ->
        webView.destroy()
    }
)
```

### LaunchedEffect의 key 오용으로 인한 중복 실행/재구독

`LaunchedEffect(Unit)`처럼 key를 고정값으로 주면 최초 1회만 실행되지만, 매 리컴포지션마다 바뀌는 값(예: 새로 생성되는 람다, 새로 생성되는 객체 인스턴스)을 key로 주면 의도치 않게 반복적으로 코루틴이 새로 시작되고 이전 코루틴이 정리되지 않은 채 쌓일 수 있음.

```kotlin
// 문제 코드: 매 리컴포지션마다 새 리스너 객체가 생성되어 key가 계속 바뀜
@Composable
fun MyScreen(onEvent: () -> Unit) {
    LaunchedEffect(onEvent) { // onEvent 람다가 매번 새 인스턴스면 매번 재실행됨
        subscribeToEvents(onEvent)
    }
}
```

```kotlin
// 수정: 안정적인 key 사용, 혹은 rememberUpdatedState로 최신 람다만 참조
@Composable
fun MyScreen(onEvent: () -> Unit) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(Unit) {
        subscribeToEvents { currentOnEvent() }
    }
}
```

### produceState/snapshotFlow에서 외부 리소스 캡처

`produceState`나 `snapshotFlow`로 외부 리소스(DB 커넥션, 네트워크 스트림)를 구독할 때, 내부에서 사용하는 콜백이 Composable 바깥의 매니저 객체에 등록되는 구조라면 `awaitDispose`(구버전 API) 또는 `DisposableEffect`의 `onDispose`에서 반드시 해제해야 함. Composition이 사라졌다고 자동으로 외부 리소스 구독까지 정리되는 것은 아니라는 점에 주의.

### CompositionLocal에 Activity/NavController를 과도하게 넓은 스코프로 제공

`CompositionLocalProvider`로 Activity 참조나 무거운 객체를 하위 트리 전체에 제공하는 패턴 자체는 문제가 아니지만, 이를 Composable 트리 밖의 전역 싱글톤(예: 커스텀 캐시 매니저)에 다시 저장해버리면 Composition 생명주기를 벗어난 곳에서 Activity가 계속 참조됨. CompositionLocal로 받은 값을 Composable 스코프 밖으로 유출시키지 않는 것이 원칙.

<br>

## 10. ViewModel과 관련된 누수

ViewModel은 `viewModelScope`가 `onCleared()` 시점에 자동으로 취소되기 때문에 기본적으로 안전하지만, 다음과 같은 경우는 여전히 누수로 이어질 수 있음.

### ViewModel이 View/Context를 직접 참조

```kotlin
// 누수 발생 코드
class HomeViewModel : ViewModel() {
    private var view: View? = null // ViewModel이 View를 들고 있으면 Activity 회전 시 누수

    fun bindView(view: View) {
        this.view = view
    }
}
```

ViewModel은 Activity/Fragment보다 생명주기가 길게 유지될 수 있으므로(화면 회전 시에도 유지) View나 Context 참조를 절대 필드로 보관하면 안 됨. 필요한 경우 콜백 인터페이스나 `StateFlow`/`SharedFlow`로 데이터만 흘려보내고, View 쪽에서 관찰하도록 구조를 반대로 설계해야 함.

### 전역 싱글톤 Repository가 ViewModel을 참조

Repository가 옵저버 패턴으로 ViewModel의 콜백을 등록받는 구조라면, ViewModel이 `onCleared()`될 때 해당 콜백도 함께 해제해야 함. `onCleared()`를 오버라이드해서 명시적으로 해제 로직을 넣는 것이 안전함.

```kotlin
class HomeViewModel(private val repository: HomeRepository) : ViewModel() {
    private val listener = HomeRepository.Listener { /* ... */ }

    init {
        repository.addListener(listener)
    }

    override fun onCleared() {
        super.onCleared()
        repository.removeListener(listener)
    }
}
```

<br>

## 11. 커스텀 객체 감시

Activity/Fragment/ViewModel 외에 직접 만든 클래스의 인스턴스가 제대로 소멸되는지 확인하고 싶다면 `AppWatcher.objectWatcher`에 수동으로 등록할 수 있음.

```kotlin
class VoiceRecorder {
    fun release() {
        // 정리 로직
        AppWatcher.objectWatcher.expectWeaklyReachable(
            this,
            "VoiceRecorder should be garbage collected after release()"
        )
    }
}
```

이렇게 등록해두면 `release()` 호출 이후 해당 인스턴스가 GC되지 않고 남아있을 때 LeakCanary가 동일한 방식으로 리포트를 생성함. ViewModel이 아닌 커스텀 매니저/컨트롤러 클래스의 누수를 조기에 발견하는 데 유용함.

### Presenter/Controller 패턴에서의 활용

MVP 아키텍처를 쓰는 레거시 모듈이 섞여 있는 경우, Presenter가 View 인터페이스를 참조하는 구조에서 `detachView()` 호출 이후 Presenter 자체가 잘 소멸되는지도 이 방식으로 검증할 수 있음.

```kotlin
class HomePresenter {
    fun detachView() {
        view = null
        AppWatcher.objectWatcher.expectWeaklyReachable(
            this,
            "HomePresenter should be GC'd after detachView()"
        )
    }
}
```

<br>

## 12. 힙 덤프 직접 분석하기

LeakCanary가 자동으로 원인을 찾아주지 못하는 복잡한 케이스(순환 참조, 여러 경로가 얽힌 경우)에서는 힙 덤프 파일을 직접 열어 분석해야 할 때가 있음.

### Android Studio Profiler 사용

1. Android Studio의 Profiler 탭에서 Memory 프로파일러를 열고 "Dump Java heap" 버튼으로 즉시 덤프 생성
2. LeakCanary가 생성한 `.hprof` 파일(기본 경로: `/sdcard/Download/leakcanary-{패키지명}/`)을 `adb pull`로 로컬에 내려받은 뒤 Android Studio에서 열기
3. Class List에서 의심되는 클래스(예: `HomeFragment`)를 검색해 인스턴스 개수 확인 — 화면을 여러 번 들어갔다 나왔는데 인스턴스 수가 계속 늘어난다면 누수 확정
4. 특정 인스턴스를 선택하고 "Show reference chain"으로 어떤 경로로 참조되고 있는지 트리 형태로 확인

```bash
adb pull /sdcard/Download/leakcanary-com.example.app/2026-09-24_14-30-00_001.hprof ./
```

### MAT(Memory Analyzer Tool)로 심화 분석

Eclipse MAT는 Android Studio Profiler보다 더 정교한 쿼리(OQL, Object Query Language)를 지원해 대규모 힙에서 특정 조건의 객체를 찾을 때 유용함. LeakCanary의 `.hprof`는 Android 전용 포맷이라 `hprof-conv` 도구로 표준 Java 힙 덤프 포맷으로 변환한 뒤 MAT에서 열어야 함.

```bash
hprof-conv leakcanary-dump.hprof standard-dump.hprof
```

MAT의 "Dominator Tree" 뷰는 어떤 객체가 가장 많은 메모리를 "독점적으로" 붙잡고 있는지 크기순으로 보여줘서, 자동 분석으로 못 잡아내는 대형 누수(예: 캐시에 쌓인 Bitmap 수백 개)를 찾는 데 효과적임.

### Leak trace가 여러 개 겹칠 때의 우선순위

동시에 여러 종류의 누수 리포트가 쌓이는 경우, Retained size가 큰 것부터, 그리고 앱의 핵심 흐름(자주 방문하는 화면)에서 발생하는 것부터 우선 처리하는 것이 효율적임. 드물게 방문하는 설정 화면의 작은 누수보다 홈 화면 진입마다 반복되는 누수가 훨씬 치명적임.

<br>

## 13. False Positive 다루기

일부 리포트는 앱 코드의 문제가 아니라 OS 버전별 알려진 버그나 서드파티 SDK의 구조적 문제인 경우가 있음. 이런 경우 매번 알림이 뜨는 것을 막기 위해 무시 규칙을 등록할 수 있음.

```kotlin
LeakCanary.config = LeakCanary.config.copy(
    referenceMatchers = AndroidReferenceMatchers.appDefaults +
        IgnoredReferenceMatcher(
            pattern = ReferencePattern.InstanceFieldPattern(
                className = "com.thirdpartysdk.InternalCache",
                fieldName = "context"
            )
        )
)
```

`AndroidReferenceMatchers.appDefaults`에는 이미 알려진 OS 레벨 누수(예: 특정 Samsung 기기의 InputMethodManager 관련 이슈 등)에 대한 무시 규칙이 기본 포함되어 있어, 개발자가 직접 마주치는 리포트는 대부분 실제 앱 코드 문제인 경우가 많음. 다만 사내에서 통제할 수 없는 서드파티 SDK의 리포트가 반복적으로 노이즈를 일으킨다면 위처럼 선택적으로 추가하는 것이 실용적.

### 무시 규칙 남용 주의

무시 규칙을 너무 광범위하게(예: 특정 패키지 전체) 설정하면 실제 앱 코드에서 발생한 새로운 누수까지 걸러질 위험이 있으므로, 가능한 한 구체적인 클래스/필드 단위로 좁혀서 등록하는 것이 안전함.

<br>

## 14. CI 통합

LeakCanary는 기본적으로 사람이 인앱 화면에서 확인하는 도구이지만, Instrumentation 테스트와 결합해 CI에서 자동으로 누수를 검증할 수도 있음.

```kotlin
// androidTest
@RunWith(AndroidJUnit4::class)
class HomeScreenLeakTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreen_doesNotLeak() {
        composeRule.onNodeWithTag("home_list").performScrollToIndex(20)
        composeRule.activityRule.scenario.recreate()

        // DetectLeaksAfterTestSuccess 규칙을 함께 적용하면
        // 테스트 종료 시점에 자동으로 누수 여부를 검증함
    }
}
```

`leakcanary-android-instrumentation` 아티팩트를 추가하고 `DetectLeaksAfterTestSuccess` JUnit 규칙을 적용하면, 테스트가 끝난 시점에 감시 중인 객체가 남아있을 경우 테스트 자체를 실패시켜 CI에서 누수를 조기에 잡아낼 수 있음.

```kotlin
dependencies {
    androidTestImplementation("com.squareup.leakcanary:leakcanary-android-instrumentation:2.14")
}
```

```kotlin
@get:Rule
val leakRule = DetectLeaksAfterTestSuccess()
```

### 화면 전환 반복 테스트로 누수 커버리지 확보

단순히 한 화면만 열었다 닫는 테스트보다, 앱의 핵심 네비게이션 경로를 반복적으로 왕복하는 테스트(홈 → 상세 → 뒤로가기를 N회 반복)를 CI에 추가하면 드물게만 발생하는 누수(예: 리스너가 매 진입마다 중복 등록되는 케이스)를 더 잘 잡아낼 수 있음.

```kotlin
@Test
fun repeatedNavigation_doesNotAccumulateLeaks() {
    repeat(5) {
        composeRule.onNodeWithTag("list_item_0").performClick()
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }
}
```

### GitHub Actions 파이프라인 예시

```yaml
# .github/workflows/leak-test.yml
name: Leak Detection Test

on:
  pull_request:
    branches: [ main ]

jobs:
  leak-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - name: Run instrumentation leak tests
        run: ./gradlew connectedDebugAndroidTest --tests "*LeakTest"
      - name: Upload heap dumps on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: heap-dumps
          path: app/build/outputs/connected_android_test_additional_output/**/*.hprof
```

<br>

## 15. 실전 사례 분석

### 사례 1: 녹음 화면에서 반복 진입 시 누적되는 누수

녹음 화면을 여러 번 들어갔다 나올 때마다 `AudioRecord` 관련 콜백 클래스가 계속 리포트되는 경우, 대부분 `onDestroyView`에서 콜백을 해제하지 않고 새 화면 진입 시마다 새 콜백을 등록만 하는 패턴임. 리포트의 참조 체인에서 `MediaRecorder$OnErrorListener` 같은 익명 클래스가 반복적으로 나타난다면, 등록(`setOnErrorListener`)과 해제(`setOnErrorListener(null)`)를 항상 쌍으로 관리하고 있는지부터 확인.

### 사례 2: 이미지 로딩 라이브러리와 Activity Context

이미지 로딩 시 `Glide.with(activity)` 대신 애플리케이션 단위로 등록해야 하는 캐시/타겟 객체를 Activity Context로 초기화하면, 로딩이 끝나기 전에 화면이 닫혀도 콜백이 Activity를 붙잡고 있을 수 있음. 대부분의 이미지 로딩 라이브러리는 Fragment/Activity의 생명주기를 자동으로 인식하는 API(`Glide.with(fragment)`)를 제공하므로, 이를 사용하면 별도 해제 코드 없이도 안전함.

### 사례 3: WebView를 포함한 화면의 반복 누수

`WebView`는 내부적으로 자바스크립트 엔진과 렌더링 리소스를 물고 있어서, Activity/Fragment가 소멸될 때 `destroy()`를 명시적으로 호출하지 않으면 거의 항상 누수로 리포트됨. Compose의 `AndroidView`로 감싼 경우 `onRelease` 콜백에서, 전통적인 View 기반이면 `onDestroyView`에서 `webView.destroy()`를 반드시 호출해야 함. 또한 WebView를 부모 ViewGroup에서 `removeView()`한 뒤 destroy하는 순서를 지켜야 일부 기기에서 발생하는 추가 크래시도 방지됨.

<br>

## 16. 다른 도구와의 비교

| 도구 | 특징 | 적합한 상황 |
|---|---|---|
| LeakCanary | 자동 감시, 참조 체인 자동 분석, 알림으로 즉시 확인 | 개발 중 상시 활성화, 회귀 조기 발견 |
| Android Studio Memory Profiler | 실시간 힙 사용량 그래프, 수동 덤프 및 인스턴스 탐색 | 특정 시나리오를 재현하며 실시간으로 메모리 추이 관찰 |
| MAT (Eclipse Memory Analyzer) | OQL 쿼리, Dominator Tree, 대형 힙 분석에 강함 | LeakCanary가 자동으로 원인을 못 찾는 복잡한 케이스 심화 분석 |
| Perfetto / Systrace | 메모리뿐 아니라 CPU, 프레임, 시스템 전반의 트레이스 통합 분석 | 메모리 누수보다는 성능 병목(jank) 전반을 함께 봐야 할 때 |

실무에서는 LeakCanary를 디버그 빌드에 상시 켜두고 개발 중 회귀를 조기에 잡아내는 1차 방어선으로 쓰고, 복잡하거나 재현이 어려운 케이스만 Android Studio Profiler나 MAT로 넘어가 직접 힙을 들여다보는 조합이 일반적임.

<br>

## 17. 정리

- 메모리 누수는 생명주기가 짧은 객체를 생명주기가 긴 객체(정적 필드, Handler, 리스너, 코루틴 스코프 등)가 계속 참조할 때 발생
- LeakCanary는 `WeakReference` + `ReferenceQueue`로 객체 소멸 여부를 감시하다가, 회수되지 않는 retained object가 쌓이면 힙 덤프를 떠서 GC Root부터의 최단 강한 참조 경로를 Shark 엔진으로 분석해 보여줌
- `debugImplementation`으로만 추가하면 별도 초기화 없이 디버그 빌드에서 자동 동작하며, 릴리즈 빌드에는 포함되지 않음
- 리포트를 읽을 때는 `Leaking: YES`로 표시된 최종 객체와, 그 직전까지의 참조 경로(필드명), Retained size, 반복 횟수를 함께 확인하는 것이 핵심
- Handler 지연 실행, 정적 Context 보관, 리스너/BroadcastReceiver/RxJava Disposable 미해제, Timer 미취소가 전통적인 View 기반 코드에서 흔한 원인
- Compose에서는 `remember`로 캡처된 람다가 Composition 밖 리스너에 등록된 채 남거나, `LaunchedEffect`의 key를 잘못 줘서 반복 재구독되거나, `AndroidView`의 리소스(WebView 등)를 해제하지 않는 경우가 흔한 원인이며 `DisposableEffect`의 `onDispose`로 정리하는 것이 기본 대응
- ViewModel은 `viewModelScope` 덕분에 기본적으로 안전하지만, View/Context를 직접 필드로 들고 있거나 Repository와의 콜백 등록을 `onCleared()`에서 해제하지 않으면 여전히 누수 가능
- `AppWatcher.objectWatcher.expectWeaklyReachable()`로 커스텀 클래스도 수동 감시 등록 가능
- 자동 분석으로 원인을 못 찾는 복잡한 케이스는 `.hprof` 파일을 Android Studio Profiler나 MAT로 직접 열어 Dominator Tree, 참조 체인을 수동 분석
- OS/SDK 레벨의 알려진 누수는 `AndroidReferenceMatchers`로 무시 규칙을 등록하되, 범위를 너무 넓게 잡지 않도록 주의
- `leakcanary-android-instrumentation` + `DetectLeaksAfterTestSuccess` 규칙과 반복 네비게이션 테스트를 CI에 추가하면 누수를 배포 전에 자동으로 잡아낼 수 있음
