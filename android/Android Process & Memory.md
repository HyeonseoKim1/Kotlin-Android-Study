# Android Process & Memory

<br>

## 목차

1. 프로세스란 무엇인가
2. 프로세스 생명주기와 우선순위
3. Application/Activity와 프로세스의 관계
4. 메모리 관리 기본 개념 (Heap, Native Memory)
5. Low Memory Killer와 oom_adj
6. onTrimMemory()로 메모리 회수 대응하기
7. 메모리 누수(Memory Leak) 원인과 패턴
8. LeakCanary로 메모리 누수 탐지하기
9. 멀티프로세스 앱 구성하기
10. 프로세스 간 통신(IPC)과 메모리 비용
11. 대용량 데이터/이미지 메모리 최적화
12. 메모리 프로파일링 도구 (Android Studio Profiler, adb)
13. 백그라운드 프로세스 제한 정책 (Doze, App Standby)
14. 주의사항과 자주 하는 실수
15. 정리

<br>

## 1. 프로세스란 무엇인가

안드로이드에서 앱은 기본적으로 각자 독립된 **리눅스 프로세스** 안에서 실행된다. 각 프로세스는 자신만의 가상 메모리 공간, 자신만의 Dalvik/ART 가상머신 인스턴스를 가지며, 다른 앱의 프로세스와는 메모리를 직접 공유하지 않는다. 이 격리 덕분에 한 앱이 크래시가 나도 다른 앱이나 시스템 전체에 영향을 주지 않는다.

기본적으로 하나의 앱(하나의 `AndroidManifest.xml`)은 하나의 프로세스에서 실행되며, 프로세스 이름은 기본적으로 앱의 패키지명을 따른다. 필요하다면 `android:process` 속성으로 특정 컴포넌트를 별도 프로세스에서 실행하도록 지정할 수 있다.

```xml
<application android:name=".MyApplication">
    <activity android:name=".MainActivity" /> <!-- 기본 프로세스(패키지명)에서 실행 -->

    <service
        android:name=".sync.SyncService"
        android:process=":sync" /> <!-- 별도의 :sync 프로세스에서 실행 -->
</application>
```

```kotlin
// 현재 실행 중인 프로세스 이름을 코드로 확인하는 방법 (API 28+)
val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    Application.getProcessName()
} else null
```

| 개념 | 설명 |
|---|---|
| 프로세스 | 리눅스 커널이 관리하는 독립적인 실행 단위, 앱마다 고유한 메모리 공간 부여 |
| Zygote | 모든 앱 프로세스의 부모가 되는 미리 초기화된 프로세스, fork로 새 프로세스를 빠르게 생성 |
| android:process | 특정 컴포넌트를 별도 프로세스에서 실행하도록 지정하는 매니페스트 속성 |
| PID | 프로세스 식별자, adb 명령어로 특정 프로세스를 대상 지정할 때 사용 |

**실전 팁**
- Zygote로부터 fork하는 방식 덕분에 새 앱 프로세스 생성이 완전히 처음부터 부팅하는 것보다 훨씬 빠르다. 이 구조를 이해하면 왜 안드로이드가 앱 실행 시마다 매번 JVM을 새로 띄우지 않는지 납득할 수 있다.
- 프로세스는 앱과 1:1이 아니다. 하나의 앱이 여러 프로세스를 가질 수도 있고(9장 참고), 여러 앱의 컴포넌트가 특정 조건에서 같은 프로세스를 공유하도록 구성하는 것도 이론적으로는 가능하다(공유 UID 등, 실무에서는 드묾).

<br>

## 2. 프로세스 생명주기와 우선순위

안드로이드는 시스템 메모리가 부족해지면, 프로세스의 **중요도(importance)** 를 기준으로 낮은 우선순위의 프로세스부터 강제 종료한다. 이 우선순위는 개발자가 직접 설정하는 것이 아니라, 현재 그 프로세스 안에서 어떤 컴포넌트가 어떤 상태로 실행 중인지에 따라 시스템이 동적으로 계산한다.

```kotlin
// 우선순위에 영향을 주는 대표적인 상태들
// - 포그라운드 Activity가 있는가 (Resumed 상태)
// - Foreground Service가 실행 중인가
// - 사용자에게 보이는 Service/BroadcastReceiver가 있는가
// - 백그라운드에 단순히 캐시된 상태인가
```

| 우선순위(높음→낮음) | 상태 | 종료 가능성 |
|---|---|---|
| Foreground Process | 사용자와 직접 상호작용 중인 Activity, Foreground Service 실행 중 | 거의 종료되지 않음 |
| Visible Process | 화면 일부에 보이지만 포커스는 없는 상태 (예: 다이얼로그 뒤에 가려짐) | 매우 드물게 종료 |
| Service Process | 포그라운드는 아니지만 실행 중인 일반 Service | 필요 시 종료 가능 |
| Background Process | 화면에 보이지 않는 Activity 스택만 존재 (Stopped 상태) | 메모리 부족 시 우선 종료 대상 |
| Empty Process | 활성 컴포넌트 없이 캐시 목적으로만 유지되는 프로세스 | 가장 먼저 종료 |

**실전 팁**
- "왜 내 앱이 백그라운드에서 자꾸 죽지?"에 대한 답은 대부분 이 우선순위 체계 때문이다. 일반 Service나 Background 상태의 Activity는 시스템이 자유롭게 종료할 수 있는 대상이라는 것을 전제로 앱을 설계해야 한다.
- 정말로 백그라운드에서 계속 실행되어야 하는 작업(음악 재생, 위치 추적 등)은 우선순위를 인위적으로 높이는 것이 아니라, Foreground Service처럼 시스템이 공식적으로 지원하는 메커니즘을 사용해야 한다.

<br>

## 3. Application/Activity와 프로세스의 관계

`Application` 객체는 프로세스당 정확히 하나만 생성되며, 그 프로세스 안에서 실행되는 모든 컴포넌트(Activity, Service, BroadcastReceiver, ContentProvider)가 이 `Application` 인스턴스를 공유한다.

```kotlin
class MyApplication : Application() {
    val appContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        Log.d("Process", "PID=${Process.myPid()}, Application 생성됨")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 같은 프로세스 안의 Application 인스턴스를 그대로 공유
        val container = (application as MyApplication).appContainer
    }
}
```

프로세스가 시스템에 의해 종료되었다가 사용자가 다시 그 앱으로 돌아오면, 시스템은 프로세스를 처음부터 재생성하며 `Application.onCreate()`도 다시 호출된다. 이때 `Application` 안에 저장해둔 상태(싱글톤 변수 등)는 전부 초기화된 상태로 다시 시작된다.

```kotlin
class MyApplication : Application() {
    // 프로세스가 재생성되면 이 값도 초기화된다 - 영속적인 저장소가 아니다
    var isFirstLaunchInSession = true
}
```

| 상황 | Application 인스턴스 |
|---|---|
| 같은 프로세스 내 여러 Activity | 모두 동일한 Application 인스턴스 공유 |
| 프로세스가 시스템에 의해 종료됨 | Application 인스턴스도 함께 사라짐 |
| 사용자가 다시 앱 진입 | 새 프로세스, 새 Application 인스턴스 생성 (onCreate 재호출) |
| android:process로 분리된 컴포넌트 | 별도 프로세스이므로 별도의 Application 인스턴스 생성 |

**실전 팁**
- `Application`을 전역 상태 저장소처럼 사용하되, 그 상태가 "프로세스가 살아있는 동안만 유효한 캐시"라는 것을 항상 인지하자. 앱을 다시 실행했을 때 반드시 남아있어야 하는 데이터는 `Application`이 아니라 `DataStore`나 `Room` 같은 영속적인 저장소에 두어야 한다.
- `android:process`로 컴포넌트를 분리하면 그 컴포넌트는 메인 프로세스의 `Application` 상태(싱글톤 등)에 접근할 수 없다. 서로 다른 프로세스는 메모리를 공유하지 않기 때문이며, 이 점을 간과하면 "분명 초기화했는데 null이다" 같은 버그를 만난다.

<br>

## 4. 메모리 관리 기본 개념 (Heap, Native Memory)

안드로이드 앱의 메모리는 크게 **Dalvik/ART Heap**(Java/Kotlin 객체가 할당되는 영역)과 **Native Heap**(Bitmap의 픽셀 데이터, NDK 코드가 직접 할당하는 메모리 등)으로 나뉜다. 안드로이드 5.0(API 21) 이상부터는 Bitmap의 픽셀 데이터도 기본적으로 Java Heap에 함께 포함되어 관리된다.

```kotlin
// 현재 앱이 사용 중인 메모리 정보를 코드로 확인하는 예시
fun logMemoryInfo(context: Context) {
    val runtime = Runtime.getRuntime()
    val usedMemory = runtime.totalMemory() - runtime.freeMemory()
    val maxMemory = runtime.maxMemory()

    Log.d("Memory", "사용 중: ${usedMemory / 1024 / 1024}MB / 최대: ${maxMemory / 1024 / 1024}MB")

    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    Log.d("Memory", "시스템 가용 메모리: ${memoryInfo.availMem / 1024 / 1024}MB, lowMemory: ${memoryInfo.lowMemory}")
}
```

앱마다 사용할 수 있는 최대 Heap 크기는 기기와 제조사에 따라 다르며, 매니페스트에서 `largeHeap`을 요청할 수는 있지만 남용은 권장되지 않는다.

```xml
<application android:largeHeap="true"> <!-- 정말 필요한 경우에만, 신중하게 -->
```

| 메모리 영역 | 설명 |
|---|---|
| Dalvik/ART Heap | Kotlin/Java 객체가 할당되는 영역, GC(가비지 컬렉션) 대상 |
| Native Heap | NDK 코드, 일부 시스템 라이브러리가 직접 관리하는 메모리 |
| Graphics/GL | 화면 렌더링에 사용되는 GPU 관련 메모리 |
| Code | APK의 실행 코드(dex, so 파일 등)가 매핑되는 영역 |

**실전 팁**
- `largeHeap="true"`는 앱이 사용할 수 있는 Heap 상한을 늘려주는 대신, 시스템이 이 앱을 "메모리를 많이 쓰는 앱"으로 인지하게 만들어 다른 백그라운드 프로세스들을 더 적극적으로 종료시키는 부작용이 있다. 근본적인 메모리 최적화 없이 이 옵션으로 문제를 덮으려 하지 말자.
- Heap 크기 자체보다 "메모리를 얼마나 빨리 회수 가능한 상태로 만드는가"(불필요한 참조를 얼마나 빨리 끊는가)가 실질적인 메모리 관리의 핵심이다.

<br>

## 5. Low Memory Killer와 oom_adj

시스템 전체의 메모리가 부족해지면 리눅스 커널 수준의 **Low Memory Killer(LMK)** 혹은 최신 기기의 **lmkd**가 개입해서, 우선순위가 낮은 프로세스부터 강제로 종료(kill)한다. 이때 사용되는 지표가 `oom_adj`(또는 `oom_score_adj`)로, 값이 낮을수록(음수에 가까울수록) 종료 우선순위가 낮고, 값이 높을수록 먼저 종료된다.

```bash
# 특정 프로세스의 oom_score_adj 값을 확인 (루트 권한 없이도 일부 확인 가능)
adb shell cat /proc/<pid>/oom_score_adj
```

각 프로세스 중요도(2장에서 다룬 Foreground/Visible/Service/Background/Empty)는 이 `oom_adj` 값과 대략적으로 대응되며, 시스템은 메모리가 부족할 때 `oom_adj`가 높은(우선순위가 낮은) 프로세스부터 순차적으로 종료한다.

```kotlin
// 앱에서 직접 oom_adj를 조작할 수는 없지만, 프로세스 상태를 간접 조회할 수는 있다
val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
val runningProcesses = activityManager.runningAppProcesses
runningProcesses?.forEach { info ->
    Log.d("Process", "${info.processName}: importance=${info.importance}")
}
```

| importance 상수 | 대략적인 의미 |
|---|---|
| IMPORTANCE_FOREGROUND | 사용자와 직접 상호작용 중 |
| IMPORTANCE_VISIBLE | 화면에 보이지만 포커스는 없음 |
| IMPORTANCE_SERVICE | 포그라운드는 아닌 일반 Service 실행 중 |
| IMPORTANCE_CACHED | 활성 컴포넌트 없이 캐시로만 유지 |

**실전 팁**
- `oom_adj`나 LMK의 세부 동작은 개발자가 직접 제어할 수 있는 영역이 아니다. 우리가 할 수 있는 것은 앱이 정말 필요한 만큼만 우선순위 높은 상태(Foreground Service 등)를 유지하도록 설계해서, 시스템의 종료 대상이 되기 쉬운 상태를 스스로 피하는 것이다.
- `ActivityManager.RunningAppProcessInfo.importance` 값은 대략적인 지표일 뿐, 정확한 `oom_adj` 수치와 1:1로 매핑되지는 않는다는 점을 참고하자.

<br>

## 6. onTrimMemory()로 메모리 회수 대응하기

시스템이 프로세스를 강제 종료하기 전에, 먼저 앱에게 "메모리를 자발적으로 줄여달라"는 신호를 보낼 기회를 준다. 이것이 `ComponentCallbacks2.onTrimMemory()`다. Activity, Application, Fragment 등에서 오버라이드할 수 있다.

```kotlin
class MyApplication : Application() {
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // 포그라운드 상태에서 시스템 메모리가 부족해짐
                imageLoader.memoryCache.trimToSize(imageLoader.memoryCache.maxSize / 2)
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                // 앱의 모든 UI가 화면에서 사라짐 (백그라운드로 전환)
                imageLoader.memoryCache.clear()
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // 백그라운드 상태에서 종료 위험도가 점점 높아짐
                releaseNonEssentialCaches()
            }
        }
    }
}
```

| level 상수 | 의미 |
|---|---|
| TRIM_MEMORY_RUNNING_MODERATE/LOW/CRITICAL | 포그라운드 상태이지만 시스템 메모리가 부족해지고 있음 |
| TRIM_MEMORY_UI_HIDDEN | 앱의 모든 화면이 가려짐 (백그라운드 전환 직후) |
| TRIM_MEMORY_BACKGROUND | 백그라운드 프로세스 목록에서 상대적으로 앞쪽(덜 위험) |
| TRIM_MEMORY_MODERATE | 백그라운드 프로세스 목록의 중간 정도 |
| TRIM_MEMORY_COMPLETE | 백그라운드 프로세스 목록의 끝, 곧 종료될 가능성이 높음 |

**실전 팁**
- 이미지 캐시(Coil, Glide 등)는 대부분 `onTrimMemory()`를 이미 내부적으로 처리하지만, 직접 만든 인메모리 캐시(맵, 리스트 등)가 있다면 이 콜백에서 명시적으로 정리해주는 것이 좋다.
- `onTrimMemory()`에서 너무 공격적으로 캐시를 비우면, 사용자가 다시 포그라운드로 돌아왔을 때 캐시 미스로 인해 오히려 성능이 나빠질 수 있다. `level`별로 회수 강도를 단계적으로 조절하는 것이 바람직하다.

<br>

## 7. 메모리 누수(Memory Leak) 원인과 패턴

메모리 누수는 더 이상 필요 없어진 객체가 어딘가에서 계속 참조되고 있어서 가비지 컬렉터(GC)가 회수하지 못하는 상황이다. 안드로이드에서 가장 흔한 패턴은 **Context, 특히 Activity Context가 그 생명주기보다 오래 살아남는 객체에 잡혀있는 경우**다.

```kotlin
// 안티패턴: 싱글톤이 Activity Context를 계속 들고 있음
object ImageCache {
    private var context: Context? = null // Activity가 전달되면 그 Activity 전체가 누수됨

    fun init(context: Context) {
        this.context = context // 위험: Activity Context를 그대로 저장
    }
}

// 개선: Application Context만 저장하거나, Context를 아예 저장하지 않음
object ImageCache {
    private var appContext: Context? = null

    fun init(context: Context) {
        this.appContext = context.applicationContext // Application 생명주기와 일치
    }
}
```

리스너/콜백 등록 후 해제하지 않는 것도 매우 흔한 누수 원인이다.

```kotlin
class MainActivity : ComponentActivity() {

    private val locationListener = LocationListener { location -> updateUi(location) }

    override fun onStart() {
        super.onStart()
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 1000L, 1f, locationListener
        )
    }

    override fun onStop() {
        super.onStop()
        locationManager.removeUpdates(locationListener) // 반드시 해제
    }
}
```

코루틴이나 Handler를 Activity/Fragment의 생명주기와 무관한 스코프에서 실행해서 누수가 생기는 경우도 흔하다.

```kotlin
// 안티패턴: GlobalScope는 Activity 생명주기와 무관하게 계속 실행됨
GlobalScope.launch {
    val result = repository.fetchData()
    updateUi(result) // Activity가 이미 파괴된 후에도 참조를 유지하며 실행 시도
}

// 개선: lifecycleScope는 Activity/Fragment 생명주기에 맞춰 자동 취소됨
lifecycleScope.launch {
    val result = repository.fetchData()
    updateUi(result)
}
```

| 누수 패턴 | 원인 | 해결 |
|---|---|---|
| 싱글톤이 Activity Context 보유 | 정적 필드/객체가 Context 참조를 오래 유지 | Application Context 사용 또는 WeakReference |
| 리스너/콜백 미해제 | 등록만 하고 해제 로직 누락 | 생명주기 콜백(onStop 등)에서 반드시 해제 |
| GlobalScope/무제한 스코프 사용 | 코루틴이 컴포넌트보다 오래 살아남음 | lifecycleScope, viewModelScope 사용 |
| 내부 클래스의 암묵적 외부 참조 | non-static inner class, 익명 클래스가 외부 Activity를 암묵적으로 참조 | static 중첩 클래스 + WeakReference, 혹은 Kotlin에서는 top-level 함수/클래스 활용 |

**실전 팁**
- "이 객체가 나보다 오래 살아남는 무언가에 저장되고 있는가?"라는 질문을 항상 던지는 습관이 메모리 누수를 예방하는 가장 실용적인 방법이다.
- Kotlin의 람다는 편리하지만 외부 스코프(Activity 등)를 암묵적으로 캡처하기 쉽다는 것을 인지하고, 장기간 유지되는 콜백에 Activity/Fragment의 `this`가 포함된 람다를 넘길 때는 특히 주의하자.

<br>

## 8. LeakCanary로 메모리 누수 탐지하기

`LeakCanary`는 Square에서 만든 오픈소스 라이브러리로, 앱 실행 중 발생하는 메모리 누수를 자동으로 탐지하고 힙 덤프 분석 결과를 알림으로 보여준다.

```kotlin
// build.gradle.kts (디버그 빌드에만 추가)
dependencies {
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
```

별도의 초기화 코드 없이 의존성만 추가하면, 내부적으로 App Startup(혹은 ContentProvider) 기반으로 자동 활성화되어 Activity/Fragment/ViewModel 등 주요 컴포넌트의 누수를 감지한다.

```kotlin
// 특정 객체를 수동으로 감시 대상에 추가하고 싶을 때
class MyCustomComponent {
    fun onDestroyed() {
        AppWatcher.objectWatcher.watch(
            watchedObject = this,
            description = "MyCustomComponent가 해제되어야 함"
        )
    }
}
```

누수가 감지되면 LeakCanary는 알림을 통해 힙 덤프 분석 결과(어떤 참조 경로로 인해 객체가 회수되지 못하고 있는지)를 트리 형태로 보여준다.

```
MainActivity leak trace:
┬───
│ GC Root: 정적 필드
├─ com.example.app.ImageCache
│    ↓ ImageCache.context
├─ com.example.app.MainActivity
│    누수 확인됨
```

| 항목 | 설명 |
|---|---|
| 적용 범위 | 디버그 빌드에서만 활성화 (릴리즈 빌드에는 영향 없음) |
| 자동 감지 대상 | Activity, Fragment, View, ViewModel 등 주요 안드로이드 컴포넌트 |
| 힙 덤프 분석 | 누수 경로를 GC Root부터 추적해서 시각적으로 표시 |
| CI 연동 | leakcanary-android-instrumentation 등으로 테스트 실패 조건에 포함 가능 |

**실전 팁**
- LeakCanary는 반드시 `debugImplementation`으로 추가해서 릴리즈 빌드에 포함되지 않도록 하자. 힙 덤프 분석 자체가 무거운 작업이라 프로덕션 성능에 영향을 줄 수 있다.
- 프로젝트 초기부터 도입해두면 누수가 작을 때 바로바로 잡을 수 있지만, 오래된 프로젝트에 뒤늦게 도입하면 한 번에 너무 많은 누수가 보고되어 압도당할 수 있다. 이런 경우 심각도가 높은 것부터 우선순위를 매겨 처리하자.

<br>

## 9. 멀티프로세스 앱 구성하기

특정 컴포넌트를 메인 프로세스와 분리하고 싶다면 `android:process` 속성을 사용한다. 대표적인 이유는 특정 기능(예: 오래 걸리는 동기화 작업)이 크래시를 일으켜도 메인 UI 프로세스에는 영향을 주지 않게 격리하거나, 특정 SDK가 요구하는 프로세스 격리를 지키기 위함이다.

```xml
<application android:name=".MyApplication">
    <activity android:name=".MainActivity" />

    <service
        android:name=".sync.SyncService"
        android:process=":sync" />

    <provider
        android:name=".provider.SharedDataProvider"
        android:authorities="${applicationId}.provider"
        android:process=":sync"
        android:exported="false" />
</application>
```

```kotlin
// :sync 프로세스는 메인 프로세스와 별도의 Application 인스턴스를 가진다
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val processName = getCurrentProcessName()
        when (processName) {
            packageName -> initMainProcessOnly()      // 메인 프로세스
            "$packageName:sync" -> initSyncProcessOnly() // 별도 프로세스
        }
    }
}
```

| 장점 | 단점 |
|---|---|
| 특정 기능의 크래시가 메인 프로세스에 영향 없음 | 프로세스 간 통신(IPC) 비용 발생 |
| 메모리 사용량을 컴포넌트 단위로 격리해서 관리 가능 | 별도 프로세스도 자체적인 메모리(Application 인스턴스 등) 소비 |
| 특정 프로세스만 선택적으로 종료 가능 | 디버깅과 로그 추적이 복잡해짐 (여러 PID 존재) |

**실전 팁**
- 멀티프로세스 구성은 명확한 이유(크래시 격리, SDK 요구사항 등)가 있을 때만 도입하자. 별도 프로세스는 그 자체로 추가적인 메모리(별도의 ART 인스턴스, 별도의 Application 초기화)를 소비하므로, 이유 없이 프로세스를 늘리면 오히려 전체 메모리 사용량이 늘어난다.
- 정적 변수(싱글톤)는 프로세스마다 독립적이라는 것을 항상 기억하자. 메인 프로세스에서 초기화한 싱글톤은 `:sync` 프로세스에서는 초기화되지 않은 상태 그대로다.

<br>

## 10. 프로세스 간 통신(IPC)과 메모리 비용

서로 다른 프로세스는 메모리를 공유하지 않으므로, 데이터를 주고받으려면 반드시 직렬화(serialization)를 거치는 IPC 메커니즘을 사용해야 한다. 안드로이드에서는 `Binder`가 이 IPC의 기반 기술이며, `Intent`의 Extra, `ContentProvider`, `AIDL`(Android Interface Definition Language) 등이 모두 내부적으로 Binder를 사용한다.

```kotlin
// Intent를 통한 프로세스 간 데이터 전달 - Parcelable로 직렬화되어 전달됨
@Parcelize
data class SyncResult(val itemCount: Int, val timestamp: Long) : Parcelable

val intent = Intent(context, SyncService::class.java).apply {
    putExtra("result", SyncResult(itemCount = 42, timestamp = System.currentTimeMillis()))
}
```

Binder를 통해 전달할 수 있는 데이터 크기에는 트랜잭션당 약 1MB 내외의 제한(`TransactionTooLargeException`)이 있으므로, 큰 데이터는 파일이나 `ContentProvider`의 스트림(`openFile()`)을 통해 전달하는 것이 일반적이다.

```kotlin
// 큰 데이터는 Extra로 직접 넘기지 않고 파일 Uri로 전달
val cacheFile = File(context.cacheDir, "large_payload.json")
cacheFile.writeText(largeJsonString)

val intent = Intent(context, SyncService::class.java).apply {
    putExtra("payloadPath", cacheFile.absolutePath)
}
```

| IPC 방식 | 특징 |
|---|---|
| Intent Extra | 작은 데이터(Parcelable/Serializable), Binder 트랜잭션 크기 제한 있음 |
| ContentProvider | 구조화된 데이터, 스트림(openFile)으로 대용량 파일도 전달 가능 |
| AIDL | 직접 정의한 인터페이스로 양방향 통신, Service 바인딩 기반 |
| Bundle/Messenger | Handler 기반의 비교적 가벼운 메시지 전달 |

**실전 팁**
- `TransactionTooLargeException`은 대부분 Bitmap이나 대용량 리스트를 Intent Extra로 직접 넘기려다 발생한다. 프로세스 경계를 넘는 데이터는 항상 "이게 1MB를 넘을 수 있는가"를 점검하자.
- IPC 자체가 직렬화/역직렬화와 컨텍스트 스위칭 비용을 수반하므로, 같은 프로세스 안에서 처리할 수 있는 작업을 굳이 멀티프로세스로 쪼개면 성능과 메모리 양쪽에서 손해를 볼 수 있다.

<br>

## 11. 대용량 데이터/이미지 메모리 최적화

메모리 문제의 상당 부분은 이미지(Bitmap)에서 발생한다. 원본 이미지를 화면에 표시할 크기보다 훨씬 크게 그대로 로드하면 불필요하게 많은 메모리를 소비한다.

```kotlin
// 안티패턴: 원본 크기 그대로 디코딩
val bitmap = BitmapFactory.decodeFile(imagePath) // 예: 4000x3000 원본을 그대로 메모리에 올림

// 개선: 실제로 필요한 크기로 다운샘플링해서 디코딩
fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)

    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
    options.inJustDecodeBounds = false
    return BitmapFactory.decodeFile(path, options)
}

fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height, width) = options.outHeight to options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
```

Coil, Glide 같은 이미지 로딩 라이브러리는 이런 다운샘플링, 메모리 캐시, `onTrimMemory()` 대응을 이미 내부적으로 처리해주므로 직접 구현하기보다는 라이브러리를 활용하는 것이 안전하다.

```kotlin
// Coil을 사용한 예시 - 대상 View 크기에 맞춰 자동으로 다운샘플링됨
imageView.load(imageUrl) {
    size(Size.ORIGINAL) // 또는 명시적 크기 지정
    memoryCachePolicy(CachePolicy.ENABLED)
}
```

대량의 리스트 데이터를 다룰 때는 전체를 한 번에 메모리에 올리기보다 `Paging` 라이브러리로 필요한 만큼만 로드하는 것이 메모리 효율에 유리하다.

```kotlin
val pager = Pager(PagingConfig(pageSize = 20)) {
    NotePagingSource(noteDao)
}
```

| 최적화 대상 | 기법 |
|---|---|
| 대용량 이미지 | inSampleSize 다운샘플링, 이미지 로딩 라이브러리 활용 |
| 대량 리스트 데이터 | Paging 라이브러리로 필요한 만큼만 로드 |
| 대용량 JSON/파일 파싱 | 스트리밍 파서 사용 (전체를 문자열로 메모리에 올리지 않기) |
| 반복적으로 생성되는 임시 객체 | 객체 재사용(Pool), 불필요한 박싱(Boxing) 회피 |

**실전 팁**
- 원본 이미지를 그대로 로드하는 실수는 신입 개발자가 가장 흔히 저지르는 메모리 문제 중 하나다. 화면에 실제로 보일 크기를 항상 염두에 두고 디코딩하자.
- 이미지 로딩 라이브러리를 쓴다고 해서 메모리 문제에서 완전히 자유로운 것은 아니다. `RecyclerView`의 아이템 뷰마다 서로 다른 원본 크기의 이미지를 무분별하게 로드하면 캐시 자체가 비대해질 수 있다.

<br>

## 12. 메모리 프로파일링 도구 (Android Studio Profiler, adb)

메모리 문제를 추측이 아니라 실측으로 확인하려면 프로파일링 도구가 필수다. Android Studio의 **Memory Profiler**는 실시간 메모리 사용량 그래프, 힙 덤프 캡처, 할당 추적(Allocation Tracking) 기능을 제공한다.

```kotlin
// 특정 구간의 메모리 할당을 추적하고 싶을 때, 코드에 구간 표시를 남겨두면
// Profiler의 타임라인에서 해당 구간을 쉽게 식별할 수 있다
Trace.beginSection("LoadLargeDataSet")
val data = repository.loadAllItems()
Trace.endSection()
```

`adb`로도 특정 프로세스의 메모리 사용 현황을 커맨드라인에서 확인할 수 있다.

```bash
# 특정 앱 프로세스의 상세 메모리 사용량(PSS 기준) 확인
adb shell dumpsys meminfo com.example.app

# 시스템 전체 메모리 요약
adb shell dumpsys meminfo
```

`dumpsys meminfo`의 결과에서 `TOTAL PSS`가 해당 프로세스가 실제로 차지하는 물리 메모리의 근사치이며, `Native Heap`, `Dalvik Heap`, `Graphics` 등 영역별 세부 수치도 함께 확인할 수 있다.

```bash
# 힙 덤프를 파일로 추출해서 정밀 분석하고 싶을 때
adb shell am dumpheap com.example.app /data/local/tmp/dump.hprof
adb pull /data/local/tmp/dump.hprof
```

| 도구 | 용도 |
|---|---|
| Android Studio Memory Profiler | 실시간 메모리 그래프, 힙 덤프, 할당 추적을 GUI로 확인 |
| adb shell dumpsys meminfo | 특정 프로세스 또는 시스템 전체 메모리 사용량 요약 |
| adb shell am dumpheap | 힙 덤프를 hprof 파일로 추출 (MAT, Android Studio 등으로 분석) |
| Macrobenchmark (Jetpack) | 메모리 사용량을 포함한 자동화된 성능 벤치마크 |

**실전 팁**
- "메모리를 얼마나 쓰는지"는 감으로 판단하지 말고 반드시 `dumpsys meminfo`나 Memory Profiler로 실측하자. 특히 저사양 기기에서의 수치가 실제 사용자 경험을 좌우한다.
- 힙 덤프는 캡처 시점의 스냅샷일 뿐이므로, 메모리 누수처럼 "시간이 지날수록 늘어나는" 문제를 진단하려면 여러 시점의 힙 덤프를 비교하거나 LeakCanary 같은 지속 감시 도구를 함께 사용하는 것이 효과적이다.

<br>

## 13. 백그라운드 프로세스 제한 정책 (Doze, App Standby)

안드로이드 6.0(API 23)부터 도입된 **Doze 모드**와 **App Standby**는 배터리 절약을 위해 화면이 꺼지고 기기가 일정 시간 정지 상태일 때, 백그라운드 앱의 네트워크 접근과 작업 실행을 제한하는 시스템 정책이다. 이는 메모리 관리와는 별개의 메커니즘이지만, 결과적으로 백그라운드 프로세스가 오래 유지되는 것을 막아 간접적으로 메모리 회수에도 영향을 준다.

```kotlin
// Doze 모드 중에도 실행이 필요한 정확한 작업은 setExactAndAllowWhileIdle 사용
val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
alarmManager.setExactAndAllowWhileIdle(
    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
)

// 배터리 최적화 예외가 필요한 경우, 사용자 동의를 받아 요청 (남용 금지)
val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}
```

| 상태 | 제한 내용 |
|---|---|
| Doze 모드 (화면 꺼짐 + 정지 상태 지속) | 네트워크 접근 차단, 대부분의 알람/작업 지연, 짧은 유지보수 윈도우에만 예외적으로 허용 |
| App Standby | 사용자가 오래 사용하지 않은 앱의 백그라운드 네트워크 접근 제한 |
| 배터리 최적화 무시 예외 | 사용자가 명시적으로 허용한 앱만 Doze의 영향을 덜 받음 |

**실전 팁**
- Doze/App Standby를 우회하려고 배터리 최적화 예외를 습관적으로 요청하는 것은 사용자 경험과 배터리 수명에 부정적이며, Play 스토어 정책상으로도 특별한 이유(내비게이션, 음악 재생 등 명확한 사용 사례) 없이는 권장되지 않는다.
- 정말 백그라운드에서 안정적으로 실행되어야 하는 작업은 `WorkManager`를 사용하는 것이 정석이다. `WorkManager`는 Doze/App Standby 제약을 시스템과 협조적으로 처리하도록 설계되어 있다.

<br>

## 14. 주의사항과 자주 하는 실수

1. 싱글톤 객체에 Activity Context를 그대로 저장해서 해당 Activity 전체가 메모리에서 회수되지 못하는 누수를 만드는 경우가 매우 흔하다. Application Context를 사용하거나 Context 참조 자체를 피하자.
2. `GlobalScope`로 코루틴을 실행해서 Activity/Fragment가 파괴된 후에도 작업이 계속 실행되며 참조를 유지하는 실수를 한다. `lifecycleScope`/`viewModelScope`를 사용하자.
3. 리스너나 콜백을 등록만 하고 생명주기 종료 시점에 해제하지 않아 메모리 누수와 예기치 않은 콜백 호출이 함께 발생하는 경우가 많다.
4. 원본 크기 그대로 이미지를 디코딩해서 화면에 표시할 크기보다 훨씬 큰 메모리를 소비하는 실수를 한다. 다운샘플링이나 이미지 로딩 라이브러리를 활용하자.
5. `largeHeap="true"`로 메모리 부족 문제를 근본적인 원인 분석 없이 임시방편으로 덮으려는 경우가 있다. 이는 시스템이 다른 백그라운드 프로세스를 더 적극적으로 종료시키는 부작용을 낳는다.
6. 멀티프로세스 앱에서 정적 변수(싱글톤)가 프로세스마다 독립적이라는 것을 모르고, 한 프로세스에서 초기화한 값을 다른 프로세스에서도 그대로 쓸 수 있다고 착각하는 경우가 있다.
7. Intent Extra로 대용량 데이터(원본 Bitmap, 큰 리스트 등)를 직접 넘기려다 `TransactionTooLargeException`을 만나는 경우가 흔하다. 큰 데이터는 파일이나 스트림으로 전달해야 한다.
8. `onTrimMemory()`를 전혀 구현하지 않아, 시스템이 메모리 회수를 요청하는 신호를 앱이 완전히 무시하는 상태로 방치되는 경우가 많다.
9. LeakCanary를 `implementation`(릴리즈 빌드에도 포함)으로 잘못 추가해서 프로덕션 성능에 불필요한 영향을 주는 실수를 한다. 반드시 `debugImplementation`을 사용해야 한다.
10. Doze/App Standby 제약을 우회하기 위해 배터리 최적화 예외를 습관적으로 요청해서, 사용자 경험 저하와 Play 스토어 정책 위반 위험을 동시에 만드는 경우가 있다.

<br>

## 15. 정리

안드로이드에서 프로세스는 앱 실행의 기본 단위이며, 시스템은 각 프로세스 안에서 어떤 컴포넌트가 어떤 상태로 실행 중인지를 기준으로 우선순위를 매겨, 메모리가 부족해지면 우선순위가 낮은 프로세스부터 강제 종료한다. 개발자가 이 종료 시점 자체를 직접 제어할 수는 없지만, `onTrimMemory()`로 시스템의 회수 요청에 협조하고, `SavedStateHandle` 등으로 프로세스 재생성에 대비하며, Foreground Service 같은 공식 메커니즘으로 정말 필요한 경우에만 우선순위를 유지하는 것으로 간접적으로 대응할 수 있다. 실무에서 가장 큰 메모리 문제는 대부분 두 가지로 귀결된다. 하나는 Activity Context나 리스너가 생명주기보다 오래 참조되어 발생하는 메모리 누수이고, 다른 하나는 원본 크기 그대로 로드되는 이미지나 대량 데이터다. 두 문제 모두 LeakCanary, Android Studio Memory Profiler, `adb shell dumpsys meminfo` 같은 도구로 감이 아니라 실측을 통해 확인하고 대응하는 습관이 가장 확실한 예방책이다.
