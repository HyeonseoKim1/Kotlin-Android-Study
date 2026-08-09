# PowerManager & WakeLock

## 목차

1. PowerManager란?
2. Android 전원 관리 구조
3. Sleep, Doze, App Standby 차이
4. WakeLock이 필요한 이유 및 종류
5. PARTIAL_WAKE_LOCK 동작 원리
6. WakeLock 획득 및 해제
7. WakeLock 사용 시 주의사항
8. Battery Optimization과의 관계
9. Foreground Service와 WakeLock
10. WorkManager에서 WakeLock
11. AlarmManager와 WakeLock
12. 실제 사용 사례(BLE, 위치추적, 음악재생)
13. dumpsys power로 상태 확인
14. Android 버전별 변경 사항
15. 정리

<br>

## 1. PowerManager란?

`PowerManager`는 기기의 전원 상태를 제어하고 조회할 수 있는 시스템 서비스다. CPU, 화면, 키보드 백라이트 등의 절전 여부를 앱이 직접 개입해서 제어할 수 있게 해주는 API를 제공한다.

```kotlin
val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
```

주요 역할

- WakeLock 생성 및 관리
- 기기의 화면 On/Off, Doze 상태 등 조회
- 배터리 최적화 예외 여부 확인 (`isIgnoringBatteryOptimizations`)

기본적으로 Android는 배터리 수명을 최우선으로 설계되어 있고, `PowerManager`는 그 기본 정책에 "예외"를 요청하는 통로 역할을 한다. 따라서 남용하면 시스템 정책과 계속 충돌하게 된다.

<br>

## 2. Android 전원 관리 구조

Android의 전원 관리는 크게 세 레이어로 나뉜다.

1. 커널 레벨 - Linux Wakelock (커널 자체의 suspend/wake 메커니즘)
2. 프레임워크 레벨 - `PowerManager`, `PowerManagerService`
3. 앱 레벨 - `WakeLock`, `WorkManager`, `AlarmManager`, `JobScheduler`

앱이 `PowerManager.WakeLock`을 획득하면 프레임워크가 이를 커널 레벨의 wake lock으로 변환해서 시스템 suspend를 막는다. 즉 앱 개발자가 직접 다루는 건 프레임워크 레벨이지만 실제 효과는 커널까지 내려간다.

Android 6.0(Marshmallow) 이후부터는 여기에 Doze와 App Standby라는 추가 절전 레이어가 얹혀서, WakeLock을 잡고 있어도 시스템이 강제로 절전 모드에 들어가는 경우가 생겼다.

<br>

## 3. Sleep, Doze, App Standby 차이

세 개념은 비슷해 보이지만 트리거 조건과 적용 범위가 다르다.

Sleep (화면 꺼짐)

- 사용자가 화면을 끄거나 타임아웃되면 즉시 진입
- CPU는 WakeLock이 없으면 곧 suspend
- 앱 단위 구분 없이 기기 전체에 적용

Doze (Android 6.0+)

- 기기가 일정 시간 동안 화면 꺼짐 + 미충전 + 정지 상태(가속도계 무변화)일 때 단계적으로 진입
- 네트워크 접근, 동기화, JobScheduler, 알람 등이 시스템 차원에서 제한/지연됨
- 주기적으로 짧은 "maintenance window"를 열어 밀린 작업 처리

App Standby (Android 6.0+, 개별 앱 단위)

- 사용자가 일정 기간 사용하지 않은 "개별 앱"에 대해 네트워크/동기화 제한
- 기기 전체가 아니라 앱별로 독립적으로 적용
- 앱을 실행하거나 충전 중이면 해제

```
Sleep      → 기기 전체, 화면 꺼짐 기준
Doze       → 기기 전체, 정지+미충전 기준, 단계적 제한
App Standby → 앱별, 미사용 기간 기준
```

핵심은 WakeLock이 Sleep 진입은 막을 수 있지만, Doze나 App Standby가 걸어놓은 제한은 WakeLock만으로는 못 뚫는다는 점이다.

<br>

## 4. WakeLock이 필요한 이유 및 종류

화면이 꺼지고 Sleep 상태가 되면 CPU도 결국 suspend되어 백그라운드 작업이 멈춘다. 음악 재생, 위치 추적, BLE 통신처럼 화면이 꺼져도 계속 동작해야 하는 작업이 있다면 WakeLock으로 CPU를 깨워둬야 한다.

종류 (`PowerManager` 상수)

```kotlin
PowerManager.PARTIAL_WAKE_LOCK       // CPU만 유지, 화면/키보드는 꺼짐
PowerManager.SCREEN_DIM_WAKE_LOCK    // deprecated (API 17~)
PowerManager.SCREEN_BRIGHT_WAKE_LOCK // deprecated (API 17~)
PowerManager.FULL_WAKE_LOCK          // deprecated (API 17~)
```

`SCREEN_*`, `FULL_WAKE_LOCK`은 API 17부터 deprecated 되었고 현재는 화면을 켜둬야 하면 `Window` 플래그(`FLAG_KEEP_SCREEN_ON`)나 `setTurnScreenOn()`을 쓰는 게 권장 방식이다. 실무에서 쓰는 건 사실상 `PARTIAL_WAKE_LOCK` 하나라고 봐도 된다.

<br>

## 5. PARTIAL_WAKE_LOCK 동작 원리

`PARTIAL_WAKE_LOCK`은 화면과 키보드 백라이트는 꺼진 상태를 허용하면서 CPU만 깨어있게 유지한다.

- 화면 On/Off와 무관하게 CPU 클럭이 suspend되지 않도록 커널에 신호를 보냄
- 내부적으로는 참조 카운트(reference count) 방식으로 동작 - 같은 태그로 여러 번 `acquire()`해도 `release()` 횟수가 맞아야 완전히 해제됨
- 앱 프로세스가 죽어도 시스템이 자동으로 회수하긴 하지만, 그 사이에 배터리 소모나 ANR 감시 대상이 될 수 있어 명시적 해제가 원칙

```kotlin
val wakeLock = powerManager.newWakeLock(
    PowerManager.PARTIAL_WAKE_LOCK,
    "MyApp::MyWakeLockTag"
)
```

태그는 반드시 `"패키지/클래스명::용도"` 형식의 관례를 따르는 게 좋다. `dumpsys power`로 확인할 때 어떤 컴포넌트가 잡고 있는지 구분하기 위함이다.

<br>

## 6. WakeLock 획득 및 해제

기본 패턴은 `acquire()` → 작업 → `release()`다.

```kotlin
private val powerManager by lazy {
    getSystemService(Context.POWER_SERVICE) as PowerManager
}

private var wakeLock: PowerManager.WakeLock? = null

fun acquireWakeLock() {
    wakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "MyApp:BLEScanWakeLock"
    ).apply {
        setReferenceCounted(false)
        acquire(10 * 60 * 1000L /*10분 타임아웃*/)
    }
}

fun releaseWakeLock() {
    wakeLock?.let {
        if (it.isHeld) {
            it.release()
        }
    }
    wakeLock = null
}
```

핵심 포인트

- `acquire()`에 타임아웃을 반드시 넣는다. 무한정 잡아두면 릴리즈를 놓쳤을 때 배터리를 계속 소모함
- `isHeld`로 중복 release 방지
- `setReferenceCounted(false)`로 단순 on/off 방식으로 관리하면 실수로 인한 카운트 불일치를 줄일 수 있음 (팀 컨벤션에 따라 다름)

<br>

## 7. WakeLock 사용 시 주의사항

- release 누락 → 배터리 급격히 소모, 사용자에게 "배터리 잡아먹는 앱"으로 인식됨
- 예외 발생 시에도 release가 보장되도록 `try/finally` 사용 권장
- `AndroidManifest.xml`에 `android.permission.WAKE_LOCK` 권한 필수
- Android 9(Pie)부터는 WakeLock을 과도하게 오래 잡고 있으면 시스템이 강제로 release하고 로그를 남김
- UI 스레드에서 무거운 작업을 하면서 WakeLock만 잡아두는 건 근본 해결책이 아님 - 애초에 백그라운드 작업 구조 자체를 점검해야 함

```kotlin
fun doWorkWithWakeLock() {
    val wl = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK, "MyApp:SafeWakeLock"
    )
    try {
        wl.acquire(5 * 60 * 1000L)
        // 실제 작업
    } finally {
        if (wl.isHeld) wl.release()
    }
}
```

<br>

## 8. Battery Optimization과의 관계

Android 6.0부터 도입된 Battery Optimization(=Doze/App Standby 대상 여부)은 WakeLock과는 별개의 축이다.

- WakeLock: "CPU를 깨워둘지"에 대한 제어
- Battery Optimization 예외: "이 앱을 Doze/App Standby 제한에서 아예 빼줄지"에 대한 제어

앱이 Battery Optimization 대상에서 제외되지 않으면, WakeLock을 잡고 있어도 Doze 진입 시 네트워크 등 다른 리소스는 여전히 제한된다. 즉 WakeLock만으로는 Doze의 모든 제약을 우회할 수 없다.

```kotlin
val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
val isIgnoring = pm.isIgnoringBatteryOptimizations(packageName)

if (!isIgnoring) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:$packageName")
    }
    startActivity(intent)
}
```

이 예외 요청은 Google Play 정책상 실제로 백그라운드에서 지속적인 작업이 꼭 필요한 앱(예: VoIP, 음악 재생)에만 허용되는 사용 사례이고, 남용하면 스토어 심사에서 반려될 수 있다.

<br>

## 9. Foreground Service와 WakeLock

화면이 꺼진 상태에서 지속적으로 작업해야 한다면 대부분의 경우 WakeLock 단독보다 Foreground Service와 함께 쓰는 게 정석이다.

- Foreground Service는 알림(Notification)을 통해 사용자에게 백그라운드 작업 중임을 명시
- 시스템이 프로세스를 강제 종료할 가능성을 크게 낮춤
- 그 안에서 필요한 순간에만 `PARTIAL_WAKE_LOCK`을 짧게 잡는 조합이 일반적

```kotlin
class LocationForegroundService : Service() {

    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MyApp:LocationServiceWakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        wakeLock.acquire(10 * 60 * 1000L)
        return START_STICKY
    }

    override fun onDestroy() {
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }
}
```

Android 14(API 34)부터는 Foreground Service Type을 명시해야 하고(`location`, `mediaPlayback`, `connectedDevice` 등), 타입에 맞지 않는 작업은 시스템이 제한할 수 있다.

<br>

## 10. WorkManager에서 WakeLock

`WorkManager`는 내부적으로 필요한 시점에 자체적으로 WakeLock을 관리해준다. `Worker`의 `doWork()` 실행 구간 동안은 시스템이 알아서 CPU가 잠들지 않게 처리한다.

```kotlin
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 이 구간은 WorkManager가 내부적으로 wake 상태 유지
        syncData()
        return Result.success()
    }
}
```

단, 오래 걸리거나 화면 꺼짐 상태에서 계속 실행되어야 하는 작업이면 `setForeground()`로 Foreground Service와 연동하는 `ExpeditedWork` 또는 별도 Foreground Service 전환이 필요하다. `WorkManager`가 알아서 처리해주는 wake 유지는 어디까지나 "짧고 즉각적인 백그라운드 작업" 범위 안에서다.

<br>

## 11. AlarmManager와 WakeLock

`AlarmManager`의 `setExactAndAllowWhileIdle()` 등으로 예약한 알람이 발화되는 시점에는 시스템이 짧게 CPU를 깨운다. 하지만 그 알람을 받는 `BroadcastReceiver.onReceive()`는 실행 시간이 매우 짧게 제한된다(수 초 내).

- `onReceive()` 안에서 시간이 걸리는 작업을 하려면 직접 WakeLock을 잡고 별도 스레드/서비스로 넘겨야 함
- `androidx.legacy`의 `WakefulBroadcastReceiver`는 이 패턴을 도와주던 헬퍼였으나 현재는 deprecated - `JobIntentService`나 `WorkManager`로 대체 권장

```kotlin
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MyApp:AlarmWakeLock"
        )
        wl.acquire(60 * 1000L)

        // 실제 처리는 WorkManager 등에 위임하고 완료 콜백에서 release
        val work = OneTimeWorkRequestBuilder<AlarmWorker>().build()
        WorkManager.getInstance(context).enqueue(work)

        wl.release()
    }
}
```

<br>

## 12. 실제 사용 사례(BLE, 위치추적, 음악재생)

BLE 스캔/연결 유지

- 화면이 꺼진 상태에서 지속적으로 BLE 기기와 연결을 유지해야 하는 앱(예: 웨어러블 동기화)은 Foreground Service + `PARTIAL_WAKE_LOCK` 조합 사용
- 스캔 자체는 시스템이 배터리 보호 차원에서 주기를 강제로 늘리기도 해서, WakeLock만으로 스캔 주기 문제까지 해결되진 않음

위치 추적

- `FusedLocationProviderClient`로 백그라운드 위치를 받는 동안 Foreground Service Type을 `location`으로 지정
- 짧은 시간 동안 GPS 신호 처리할 때만 WakeLock을 잡고, 위치 콜백이 끝나면 바로 release

음악 재생

- `MediaSessionCompat` + Foreground Service(`mediaPlayback` 타입) 구조가 기본
- 실제로 `MediaPlayer`/`ExoPlayer` 자체가 재생 중에는 내부적으로 WakeLock을 관리해주는 옵션 제공 (`setWakeMode()` 등)

```kotlin
exoPlayer.setWakeMode(C.WAKE_MODE_LOCAL)
```

세 사례 공통점은 "WakeLock을 앱이 직접, 오래, 넓게" 잡기보다 "프레임워크가 제공하는 기능(Foreground Service Type, ExoPlayer WakeMode 등)에 위임하고, 꼭 필요한 짧은 구간만 직접 잡는다"는 방향으로 수렴한다.

<br>

## 13. dumpsys power로 상태 확인

adb로 현재 기기의 전원/WakeLock 상태를 직접 확인할 수 있다.

```
adb shell dumpsys power
```

주요 확인 포인트

- `Wake Locks: size=N` - 현재 활성화된 WakeLock 개수와 태그 목록
- `mWakefulness` - 현재 기기의 wakefulness 상태(Awake, Asleep, Dozing 등)
- `Display Power` - 화면 관련 전원 상태

특정 태그만 걸러보고 싶으면

```
adb shell dumpsys power | grep -A 3 "Wake Locks"
```

내가 release를 안 하고 있는 WakeLock이 있는지 확인할 때 가장 먼저 보는 명령어. 앱을 백그라운드로 보내고 몇 분 뒤에도 내 태그가 계속 떠 있으면 release 로직에 버그가 있다는 뜻.

<br>

## 14. Android 버전별 변경 사항

- Android 6.0 (API 23) - Doze, App Standby 최초 도입. WakeLock만으로는 백그라운드 제약을 완전히 우회할 수 없게 됨
- Android 7.0 (API 24) - Doze가 화면 꺼짐만으로도 더 빨리 진입하도록 강화 (기존엔 충전 안 하고 정지 상태까지 필요했음)
- Android 8.0 (API 26) - 백그라운드 서비스 제한 강화, Foreground Service 개념이 더 중요해짐
- Android 9.0 (API 28) - 오래 유지되는 WakeLock을 시스템이 강제로 정리하고 로그 남김
- Android 12 (API 31) - Foreground Service 시작 제약 추가(백그라운드에서 무분별하게 FGS 띄우는 것 제한)
- Android 14 (API 34) - Foreground Service Type 명시 필수화, 타입별 권한/제약 세분화

버전이 올라갈수록 방향성은 명확하다 - "앱이 직접 WakeLock으로 우회하는 여지"를 계속 줄이고, 대신 시스템이 관리하는 구조화된 API(WorkManager, Foreground Service Type 등)로 유도하는 흐름.

<br>

## 15. 정리

- `PowerManager`는 전원 상태 제어의 진입점이고, `WakeLock`은 그중 CPU sleep을 막는 수단
- 실무에서 쓰는 건 사실상 `PARTIAL_WAKE_LOCK` 하나, `SCREEN_*`/`FULL_WAKE_LOCK`은 deprecated
- WakeLock은 Sleep 진입만 막을 뿐, Doze/App Standby가 거는 다른 제약(네트워크 등)까지 풀어주진 않음
- 가능하면 WakeLock을 직접 다루기보다 WorkManager, Foreground Service, ExoPlayer WakeMode 같은 상위 API에 위임하는 게 최신 Android 방향성에 맞음
- 직접 다뤄야 한다면 반드시 타임아웃 설정 + try/finally release + `dumpsys power`로 검증하는 습관 필요
