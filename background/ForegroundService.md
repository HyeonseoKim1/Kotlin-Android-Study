# Foreground Service

목차
1. Foreground Service란?
2. Foreground Service가 필요한 이유
3. 일반 Service와 Foreground Service의 차이
4. Android의 백그라운드 실행 제한
5. Foreground Service 실행 방법
6. startForegroundService()와 startForeground()
7. Foreground Service Notification
8. Foreground Service Type
9. Android 버전별 Foreground Service 제한
10. Foreground Service 생명주기
11. START_STICKY와 START_NOT_STICKY
12. Foreground Service와 WorkManager 비교
13. Foreground Service와 Coroutine 사용
14. Foreground Service 종료와 리소스 정리
15. 실전에서 사용하는 경우
16. 주의사항과 자주 하는 실수
17. 정리

<br>

## 1. Foreground Service란?

Foreground Service는 사용자가 능동적으로 인지할 수 있는 작업을 수행하는 Service다. 시스템에 "지금 사용자에게 중요한 작업을 하고 있다"는 것을 알리기 위해, 실행 중에는 반드시 상태 표시줄(Notification)을 통해 진행 중임을 표시해야 한다.

일반 Service는 백그라운드에서 조용히 동작하지만, Foreground Service는 다음과 같은 특징을 가진다.

- 항상 Notification을 표시해야 한다.
- 시스템이 메모리가 부족한 상황에서도 우선적으로 살려둔다.
- 사용자가 앱을 떠나거나 화면을 꺼도 계속 실행될 수 있다.
- Android 버전이 올라갈수록 실행 조건과 제약이 점점 강화되고 있다.

대표적인 예시로는 음악 재생 앱의 재생 컨트롤, 내비게이션 앱의 경로 안내, 녹음 앱의 녹음 진행 상태, 파일 다운로드/업로드 진행률 등이 있다.

```kotlin
class MyForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}
```

핵심은 "Service를 시작하는 것"과 "Foreground 상태로 전환하는 것"이 별개의 단계라는 점이다. `startForeground()`를 호출하기 전까지는 아직 일반 Service와 다르지 않다.

<br>

## 2. Foreground Service가 필요한 이유

Android는 배터리와 메모리를 보호하기 위해 백그라운드에서 실행되는 앱의 동작을 강하게 제한한다. Android 8.0(API 26) 이후부터는 앱이 백그라운드 상태로 전환되면 일정 시간 후 일반 Service가 시스템에 의해 강제로 종료될 수 있다.

하지만 다음과 같은 작업은 사용자가 화면을 보고 있지 않아도 반드시 계속되어야 한다.

- 음악/팟캐스트 재생
- 실시간 위치 추적(내비게이션, 배달 앱)
- 음성 녹음
- 대용량 파일 업로드/다운로드
- 화상 통화, VoIP

이런 작업을 일반 Service로 구현하면 시스템이 임의로 종료시켜버릴 위험이 있다. Foreground Service는 "이 작업은 사용자가 명시적으로 요청했고, 지금도 진행 중임을 알고 있다"는 신호를 시스템에 지속적으로 보내는 방식으로 이 문제를 해결한다.

즉 Foreground Service의 존재 이유는 다음 두 가지로 요약할 수 있다.

1. 시스템의 백그라운드 실행 제한을 우회해 작업을 계속 실행하기 위해
2. 사용자에게 현재 어떤 작업이 진행 중인지 투명하게 알리기 위해

두 번째 이유가 특히 중요하다. Foreground Service는 단순히 "백그라운드에서 오래 도는 기술"이 아니라, 사용자 신뢰를 전제로 한 API다. Notification 없이 몰래 오래 실행되는 작업을 막기 위한 장치이기도 하다.

<br>

## 3. 일반 Service와 Foreground Service의 차이

| 구분 | 일반 Service (Background Service) | Foreground Service |
|---|---|---|
| 알림 | 필요 없음 | 필수 (Notification 항상 표시) |
| 우선순위 | 낮음, 메모리 부족 시 종료 가능 | 높음, foreground 앱 수준으로 취급 |
| 실행 시간 제한 | Android 8.0+ 에서 백그라운드 상태일 때 제한적 | 명시적으로 종료하기 전까지 지속 가능 |
| 시작 방법 | `startService()` | `startForegroundService()` + `startForeground()` |
| 사용자 인지 | 사용자가 알 수 없음 | 상태 표시줄로 항상 인지 가능 |
| 대표 용도 | 짧은 백그라운드 작업, 브로드캐스트 처리 | 음악 재생, 내비게이션, 녹음, 다운로드 |

일반 Service는 Activity가 없어도 동작할 수 있지만, 오래 실행되는 작업에는 적합하지 않다. 반면 Foreground Service는 Notification이라는 대가를 치르는 대신 훨씬 안정적으로 오래 실행될 수 있다.

또한 일반 Service는 앱이 백그라운드로 전환되는 즉시 시스템 제약을 받기 시작하지만, Foreground Service는 `startForeground()` 호출 시점부터 foreground 상태의 프로세스와 유사한 우선순위를 부여받는다.

<br>

## 4. Android의 백그라운드 실행 제한

Android는 버전이 올라갈수록 백그라운드 실행을 점점 더 강하게 제한해왔다. 이 흐름을 이해해야 왜 Foreground Service가 필요한지 체감할 수 있다.

- Android 6.0 (Doze Mode): 기기가 유휴 상태일 때 네트워크 접근과 백그라운드 작업을 제한
- Android 7.0 (App Standby): 사용하지 않는 앱의 리소스 사용을 제한
- Android 8.0 (Background Execution Limits): 앱이 백그라운드로 전환되면 몇 분 내에 백그라운드 Service가 중지됨. 이때부터 `startForegroundService()`가 도입됨
- Android 9.0: 백그라운드 앱의 카메라, 마이크 접근 제한 강화
- Android 12: Foreground Service 시작에 대한 제약 강화. 백그라운드에서 Foreground Service를 시작하는 경우가 일부 예외를 제외하고 제한됨
- Android 13: 알림 권한(POST_NOTIFICATIONS)이 런타임 권한으로 변경되어, Foreground Service Notification을 표시하려면 사용자 동의가 필요해짐
- Android 14: `foregroundServiceType`을 매니페스트에 반드시 명시해야 하며, 타입별로 별도 권한이 요구됨

이런 흐름 때문에 예전에는 그냥 Service를 띄워두고 백그라운드 작업을 처리하는 패턴이 흔했지만, 지금은 다음 세 갈래로 명확히 나뉘게 되었다.

1. 즉시, 사용자가 인지해야 하는 지속 작업 → Foreground Service
2. 지연 가능하고 조건부로 실행되는 작업 → WorkManager
3. 앱이 foreground일 때만 필요한 짧은 작업 → ViewModelScope/Coroutine

<br>

## 5. Foreground Service 실행 방법

Foreground Service를 사용하려면 먼저 매니페스트에 권한과 Service 선언을 추가해야 한다.

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application>
        <service
            android:name=".service.RecordingService"
            android:foregroundServiceType="microphone"
            android:exported="false" />
    </application>
</manifest>
```

Service 클래스 구현은 다음과 같은 흐름을 따른다.

```kotlin
class RecordingService : Service() {

    override fun onCreate() {
        super.onCreate()
        // 초기화 로직
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        val notification = createNotification("녹음 중입니다")
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        // 실제 녹음 로직 시작
    }

    private fun stopRecording() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val NOTIFICATION_ID = 2001
    }
}
```

Activity나 ViewModel에서는 다음과 같이 Service를 시작한다.

```kotlin
fun startRecordingService(context: Context) {
    val intent = Intent(context, RecordingService::class.java).apply {
        action = RecordingService.ACTION_START
    }
    ContextCompat.startForegroundService(context, intent)
}
```

여기서 `ContextCompat.startForegroundService()`를 쓰는 이유는, API 26 미만에서는 `startService()`로 자동 위임되고 그 이상에서는 `startForegroundService()`로 처리되기 때문이다. 버전 분기를 직접 작성하지 않아도 되는 편의 함수다.

<br>

## 6. startForegroundService()와 startForeground()

이 두 API는 이름이 비슷해서 자주 혼동되지만 역할이 명확히 다르다.

`startForegroundService()`는 Context에서 호출하는 함수로, Service를 시작시키는 진입점이다. Android 8.0 이상에서 백그라운드 상태의 앱이 Service를 시작할 때 반드시 이 함수를 사용해야 한다.

`startForeground()`는 Service 내부에서 호출하는 함수로, 이미 시작된 Service를 Foreground 상태로 승격시키는 역할을 한다. Notification 객체와 ID를 인자로 받는다.

가장 중요한 규칙은 다음과 같다.

> `startForegroundService()`로 Service를 시작했다면, 그 Service는 반드시 5초 이내에 `startForeground()`를 호출해야 한다.

이 5초 규칙을 지키지 않으면 시스템이 `ANR (Application Not Responding)`을 발생시키고 Service를 강제 종료한다. 실무에서 가장 흔하게 겪는 실수 중 하나가, `onCreate()`나 `onStartCommand()`에서 무거운 초기화 작업(파일 IO, 네트워크 요청 등)을 먼저 처리한 뒤 `startForeground()`를 나중에 호출하는 패턴이다. 이렇게 하면 초기화가 5초를 넘기는 순간 앱이 크래시된다.

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // 잘못된 예: 무거운 초기화를 먼저 하고 나중에 startForeground 호출
    // val heavyResult = doHeavyInitialization()
    // startForeground(ID, buildNotification())

    // 올바른 예: startForeground를 최대한 빨리 호출한 뒤 무거운 작업 진행
    startForeground(NOTIFICATION_ID, buildNotification())
    doHeavyInitialization()
    return START_STICKY
}
```

또한 Android 12부터는 앱이 백그라운드 상태일 때 `startForegroundService()`를 호출하는 것 자체가 제한되는 케이스가 늘어났다. 예외 상황(사용자가 직접 알림을 탭해서 실행하는 경우, Exact Alarm 등)이 아니라면 `ForegroundServiceStartNotAllowedException`이 발생할 수 있다.

<br>

## 7. Foreground Service Notification

Foreground Service는 반드시 Notification Channel과 Notification 객체를 함께 구성해야 한다. Android 8.0부터 모든 Notification은 채널에 속해야 하므로, Service가 시작되기 전에 채널을 미리 생성해두는 것이 일반적이다.

```kotlin
private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "녹음 서비스",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "녹음 진행 상태를 표시합니다"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}

private fun buildNotification(): Notification {
    val stopIntent = Intent(this, RecordingService::class.java).apply {
        action = ACTION_STOP
    }
    val stopPendingIntent = PendingIntent.getService(
        this, 0, stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("녹음 중")
        .setContentText("탭하여 앱으로 이동")
        .setSmallIcon(R.drawable.ic_mic)
        .setOngoing(true)
        .addAction(R.drawable.ic_stop, "중지", stopPendingIntent)
        .build()
}
```

Notification 구성 시 주의할 점들이 있다.

- `IMPORTANCE_LOW` 이상을 사용해야 실제로 상태 표시줄에 노출된다. `IMPORTANCE_MIN`은 숨겨질 수 있다.
- `setOngoing(true)`를 설정하면 사용자가 스와이프로 지울 수 없다. 대부분의 Foreground Service Notification은 이 옵션을 사용한다.
- Android 13(API 33)부터는 `POST_NOTIFICATIONS` 런타임 권한이 없으면 Notification 자체가 표시되지 않는다. 단, Foreground Service는 권한이 없어도 계속 실행은 되지만 사용자에게 보이지 않게 되므로 정책 위반 소지가 있다.
- Notification을 실시간으로 갱신하려면 같은 `NOTIFICATION_ID`로 `NotificationManager.notify()`를 다시 호출하면 된다.

```kotlin
private fun updateProgressNotification(progress: Int) {
    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("업로드 중")
        .setProgress(100, progress, false)
        .setSmallIcon(R.drawable.ic_upload)
        .setOngoing(true)
        .build()

    val manager = getSystemService(NotificationManager::class.java)
    manager.notify(NOTIFICATION_ID, notification)
}
```

<br>

## 8. Foreground Service Type

Android 10(API 29)부터 `foregroundServiceType` 속성이 도입되었고, Android 14(API 34)부터는 이 속성 지정이 사실상 필수가 되었다. 타입을 지정하지 않고 Foreground Service를 시작하면 `MissingForegroundServiceTypeException`이 발생할 수 있다.

대표적인 타입은 다음과 같다.

| Type | 용도 | 필요 권한 예시 |
|---|---|---|
| `microphone` | 음성 녹음 | RECORD_AUDIO, FOREGROUND_SERVICE_MICROPHONE |
| `camera` | 카메라 촬영 | CAMERA, FOREGROUND_SERVICE_CAMERA |
| `location` | 위치 추적 | ACCESS_FINE_LOCATION, FOREGROUND_SERVICE_LOCATION |
| `mediaPlayback` | 음악/동영상 재생 | FOREGROUND_SERVICE_MEDIA_PLAYBACK |
| `dataSync` | 데이터 동기화, 업/다운로드 | FOREGROUND_SERVICE_DATA_SYNC |
| `phoneCall` | 통화 관련 | FOREGROUND_SERVICE_PHONE_CALL |
| `connectedDevice` | 블루투스 등 외부 기기 연결 | FOREGROUND_SERVICE_CONNECTED_DEVICE |
| `health` | 건강/피트니스 관련 | FOREGROUND_SERVICE_HEALTH |
| `remoteMessaging` | 메시지 송수신 | FOREGROUND_SERVICE_REMOTE_MESSAGING |
| `shortService` | 짧고 즉시 완료되는 작업 (Android 14+) | 없음 |

매니페스트와 코드 양쪽에서 타입을 일치시켜야 한다.

```xml
<service
    android:name=".service.RecordingService"
    android:foregroundServiceType="microphone" />
```

```kotlin
startForeground(
    NOTIFICATION_ID,
    notification,
    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
)
```

하나의 Service가 여러 타입을 동시에 가질 수도 있다. 예를 들어 화상 통화 앱이라면 `camera`와 `microphone`을 함께 선언한다.

```xml
android:foregroundServiceType="camera|microphone"
```

`dataSync` 타입은 Android 15부터 백그라운드에서 실행 가능한 시간이 제한(대략 6시간)되어 있어, 장시간 동기화 작업에는 별도 전략이 필요하다.

<br>

## 9. Android 버전별 Foreground Service 제한

버전별로 Foreground Service와 관련해 달라진 정책을 정리하면 다음과 같다.

Android 8.0 (API 26)
- 백그라운드 상태에서는 `startService()` 대신 `startForegroundService()` 사용 강제
- `startForeground()`를 5초 이내에 호출하지 않으면 ANR

Android 9.0 (API 28)
- `FOREGROUND_SERVICE` 권한 명시 필요

Android 10 (API 29)
- `foregroundServiceType` 도입 (선택적)
- 백그라운드 상태에서 Activity 실행 제한 강화

Android 11 (API 30)
- 카메라/마이크 접근에 대한 백그라운드 제한 강화

Android 12 (API 31)
- 앱이 백그라운드 상태일 때 Foreground Service 시작이 대부분 금지됨
- 예외: 알림 탭, Exact Alarm, 블루투스 이벤트 등 일부 트리거

Android 13 (API 33)
- `POST_NOTIFICATIONS` 런타임 권한 필요
- 권한이 없으면 Notification이 표시되지 않음 (Service 자체는 실행 가능)

Android 14 (API 34)
- `foregroundServiceType` 지정이 사실상 필수
- 타입별 세부 권한 요구 강화
- `shortService` 타입 추가로 짧은 작업에 대한 부담 완화

Android 15 (API 35)
- `dataSync`, `mediaProcessing` 등 일부 타입에 실행 시간 제한(timeout) 도입
- 제한 시간 초과 시 `Service.onTimeout()` 콜백 호출

이런 변화의 공통된 방향성은 명확하다. "정말 필요한 경우에만, 명확한 타입과 함께, 사용자에게 투명하게" Foreground Service를 사용하도록 유도하는 것이다.

<br>

## 10. Foreground Service 생명주기

Foreground Service의 생명주기는 일반 Service와 동일한 콜백을 따르지만, 중간에 Foreground 상태로의 전환/해제가 추가된다.

```
onCreate()
    ↓
onStartCommand()  ← startForeground() 호출 시점
    ↓
[Foreground 상태로 실행 중]
    ↓
stopForeground()  ← Foreground 상태 해제 (Service 자체는 살아있을 수 있음)
    ↓
onDestroy()  ← stopSelf() 또는 시스템에 의해 종료
```

각 콜백의 역할은 다음과 같다.

- `onCreate()`: Service가 처음 생성될 때 한 번 호출. 초기화에 사용하지만 무거운 작업은 피한다.
- `onStartCommand()`: `startService()` 또는 `startForegroundService()` 호출 시마다 실행. 여기서 실제 작업을 시작하고 `startForeground()`를 호출한다.
- `onBind()`: 바인딩이 필요 없다면 `null`을 반환한다. Foreground Service는 대부분 started 방식으로 쓰이지만, 필요하다면 바인딩도 가능하다.
- `onDestroy()`: Service가 종료될 때 호출. 리소스 해제는 반드시 여기서 처리한다.

바인딩과 병행하는 하이브리드 패턴도 가능하다. 예를 들어 Activity가 Service에 바인딩되어 실시간 진행률을 받아오면서, 동시에 Foreground Service로 백그라운드에서도 계속 동작하게 하는 방식이다.

```kotlin
class RecordingService : Service() {

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    val progressFlow = MutableStateFlow(0)
}
```

이 경우 Activity가 화면에 있을 때는 바인딩을 통해 직접 통신하고, 화면을 벗어나면 언바인드하되 Service 자체는 `startForeground` 상태를 유지하며 계속 실행되는 구조를 만들 수 있다.

<br>

## 11. START_STICKY와 START_NOT_STICKY

`onStartCommand()`의 반환값은 시스템이 메모리 부족 등의 이유로 Service를 강제 종료했을 때, 이후에 어떻게 재시작할지를 결정한다.

`START_STICKY`
- Service가 종료된 후 시스템이 다시 살릴 때 `intent`를 `null`로 전달하며 재시작한다.
- 마지막으로 전달받은 Intent 데이터는 유지되지 않는다.
- 음악 재생처럼 "이전 명령과 무관하게 계속 실행 상태를 유지해야 하는" 서비스에 적합하다.

`START_NOT_STICKY`
- Service가 종료되면 시스템이 자동으로 재시작하지 않는다.
- 명시적인 트리거(사용자 액션, 알람 등)가 있을 때만 다시 시작되길 원하는 경우에 적합하다.
- 재시작 비용이 크거나, 굳이 자동으로 재개될 필요가 없는 일회성 작업에 사용한다.

`START_REDELIVER_INTENT`
- Service가 종료된 후 재시작될 때, 마지막으로 전달받은 Intent를 그대로 다시 전달한다.
- 다운로드처럼 "어떤 작업을 하고 있었는지"의 정보가 반드시 필요한 경우에 적합하다.

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val downloadUrl = intent?.getStringExtra(EXTRA_URL)
    startForeground(NOTIFICATION_ID, buildNotification())
    downloadUrl?.let { startDownload(it) }
    return START_REDELIVER_INTENT
}
```

녹음 앱이라면 보통 `START_STICKY`를 선택하는 경우가 많다. 녹음이 진행되는 도중에 시스템이 프로세스를 죽였다가 되살릴 때, 사용자가 다시 녹음 버튼을 눌러야 하는 상황보다는 이어서 진행되는 편이 자연스럽기 때문이다. 다만 실제로는 녹음 세션 상태를 어떻게 복구할지 별도로 설계해야 한다.

<br>

## 12. Foreground Service와 WorkManager 비교

Foreground Service와 WorkManager는 둘 다 "오래 걸리는 작업"을 처리하지만 목적이 다르다.

| 구분 | Foreground Service | WorkManager |
|---|---|---|
| 실행 시점 | 즉시, 사용자가 인지하는 지금 | 지연 가능, 조건 충족 시 |
| 사용자 인지 | Notification 필수, 실시간 진행 표시 | 필수 아님 (선택적으로 Notification 연동) |
| 적합한 작업 | 음악 재생, 녹음, 내비게이션, 실시간 통화 | 로그 업로드, 캐시 정리, 주기적 동기화 |
| 실행 보장 | 앱 프로세스가 살아있는 동안 | 프로세스가 죽어도 시스템이 재시도 보장 |
| 제약 조건 | 없음 (즉시 실행) | 네트워크, 충전 상태, 배터리 등 조건 설정 가능 |
| 내부 구현 | Service 직접 관리 | 내부적으로 JobScheduler / AlarmManager / Service 활용 |

실무에서 흔한 오해 중 하나는 "오래 걸리는 작업이면 무조건 WorkManager"라고 생각하는 것이다. 하지만 WorkManager는 정확한 즉시 실행이나 실시간 진행률 표시에는 적합하지 않다. 반대로 몇 시간 뒤에 실행되어도 무방한 동기화 작업을 Foreground Service로 구현하면 불필요하게 Notification을 계속 띄워야 하는 부담이 생긴다.

WorkManager도 내부적으로 오래 걸리는 작업을 처리할 때는 `setForeground()`를 통해 Foreground Service와 결합할 수 있다.

```kotlin
class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo())
        // 업로드 로직
        return Result.success()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("업로드 중")
            .setSmallIcon(R.drawable.ic_upload)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
```

정리하면, 판단 기준은 "사용자가 지금 이 작업이 진행 중임을 실시간으로 알아야 하는가"이다. 그렇다면 Foreground Service, 아니라면 WorkManager를 우선 고려한다.

<br>

## 13. Foreground Service와 Coroutine 사용

Service 자체는 생명주기가 있는 컴포넌트이지만 기본적으로 `CoroutineScope`를 제공하지 않는다. 따라서 Service 안에서 Coroutine을 사용하려면 직접 Scope를 만들고, `onDestroy()`에서 반드시 취소해줘야 한다.

```kotlin
class RecordingService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch {
            recordAudioUseCase().collect { amplitude ->
                updateNotification(amplitude)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

Hilt를 사용하는 프로젝트라면 `@AndroidEntryPoint`를 붙여서 Service에도 의존성 주입을 적용할 수 있다.

```kotlin
@AndroidEntryPoint
class RecordingService : Service() {

    @Inject
    lateinit var recordAudioUseCase: RecordAudioUseCase

    @Inject
    lateinit var voiceNoteRepository: VoiceNoteRepository

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch {
            runCatching {
                recordAudioUseCase()
            }.onFailure { e ->
                Log.e(TAG, "recording failed", e)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RecordingService"
    }
}
```

`Dispatchers.Main.immediate`를 Notification 업데이트에 사용하고, 실제 파일 IO나 인코딩 작업은 `Dispatchers.IO`나 `Dispatchers.Default`로 분리하는 것이 일반적인 패턴이다. `StateFlow`를 이용해 Service 내부 상태를 외부(Activity, ViewModel)에 노출하면 바인딩을 통해 실시간으로 UI에 반영할 수 있다.

```kotlin
private val _recordState = MutableStateFlow<RecordState>(RecordState.Idle)
val recordState: StateFlow<RecordState> = _recordState.asStateFlow()
```

주의할 점은, `serviceScope`가 `onDestroy()`에서 취소되지 않으면 Service 객체가 GC되지 않고 메모리 누수로 이어질 수 있다는 것이다. `SupervisorJob`을 사용하면 하나의 자식 코루틴이 실패해도 다른 코루틴에 영향을 주지 않으면서, `onDestroy()` 시점에 전체를 한 번에 취소할 수 있어 관리가 편하다.

<br>

## 14. Foreground Service 종료와 리소스 정리

Foreground Service를 안전하게 종료하는 방법은 크게 두 단계로 나뉜다.

1. `stopForeground()`로 Foreground 상태를 해제하고 Notification을 제거한다.
2. `stopSelf()`로 Service 자체를 종료한다.

```kotlin
private fun stopRecording() {
    // 녹음 관련 리소스 정리
    audioRecorder.release()
    mediaCodec?.release()

    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
}
```

`stopForeground()`의 인자는 Android 12부터 상수 기반으로 명확해졌다.

- `STOP_FOREGROUND_REMOVE`: Notification을 완전히 제거
- `STOP_FOREGROUND_DETACH`: Notification은 유지하되 Foreground 상태만 해제 (더 이상 ongoing으로 취급하지 않음)

Boolean 파라미터를 받는 구버전 API(`stopForeground(true)`)는 Deprecated 되었으므로 새 코드에서는 상수를 사용하는 편이 좋다.

리소스 정리는 반드시 `onDestroy()`에서도 한 번 더 확인해야 한다. 외부 요인(시스템에 의한 강제 종료, 다른 컴포넌트의 `stopService()` 호출 등)으로 `stopRecording()`을 거치지 않고 바로 `onDestroy()`가 호출될 수 있기 때문이다.

```kotlin
override fun onDestroy() {
    serviceJob.cancel()
    audioRecorder.release()
    wakeLock?.let { if (it.isHeld) it.release() }
    super.onDestroy()
}
```

WakeLock을 사용하는 경우, 획득과 해제가 반드시 쌍으로 관리되어야 한다. WakeLock을 해제하지 않으면 Service가 종료된 후에도 화면이 꺼지지 않거나 배터리가 비정상적으로 소모되는 문제가 생긴다.

```kotlin
private var wakeLock: PowerManager.WakeLock? = null

private fun acquireWakeLock() {
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "RecordingService::WakeLock"
    ).apply {
        acquire(10 * 60 * 1000L /*10분 타임아웃*/)
    }
}

private fun releaseWakeLock() {
    wakeLock?.let {
        if (it.isHeld) it.release()
    }
    wakeLock = null
}
```

타임아웃 값을 명시하는 것도 중요하다. 혹시 해제 로직이 누락되더라도, 타임아웃이 있으면 시스템이 일정 시간 후 자동으로 WakeLock을 회수하기 때문에 최소한의 안전장치가 된다.

<br>

## 15. 실전에서 사용하는 경우

실제 프로젝트에서 Foreground Service를 적용하는 대표적인 시나리오들을 정리하면 다음과 같다.

음악/오디오 재생 앱
- `mediaPlayback` 타입 사용
- `MediaSessionCompat`과 연동해 잠금화면, 블루투스 이어폰 컨트롤 지원
- Notification에 재생/일시정지/다음곡 액션 버튼 포함

음성 녹음 앱
- `microphone` 타입 사용
- 녹음 중 실시간 파형/진행 시간을 Notification에 표시
- 화면이 꺼지거나 다른 앱으로 전환해도 녹음이 끊기지 않아야 함

내비게이션/위치 추적 앱
- `location` 타입 사용
- 지속적인 GPS 업데이트가 필요하므로 배터리 최적화 예외 처리도 함께 고려
- Notification에 다음 안내 정보(좌회전, 남은 거리 등) 표시

파일 업로드/다운로드
- `dataSync` 타입 사용
- 진행률(%)을 Notification progress bar로 표시
- 실패 시 재시도 로직과 사용자 알림 필요

화상 통화/VoIP
- `camera`, `microphone`, `phoneCall` 조합
- 통화 종료 시 모든 하드웨어 리소스(카메라, 마이크) 즉시 해제 필요

ChaGok(차곡)과 같은 온디바이스 AI 녹음 앱의 경우, 녹음 자체는 `microphone` 타입의 Foreground Service로 처리하고, 녹음이 끝난 뒤 Gemma 모델을 이용한 STT 교정이나 요약 같은 무거운 후처리 작업은 상황에 따라 별도의 `dataSync` Foreground Service나 WorkManager로 분리하는 설계도 고려할 수 있다. 이렇게 작업의 성격에 따라 Service를 나누면, 녹음 중 Notification과 후처리 중 Notification의 문구/액션을 서로 다르게 관리할 수 있어 UX가 명확해진다.

<br>

## 16. 주의사항과 자주 하는 실수

1. `startForeground()` 호출 지연
   `startForegroundService()`로 시작한 뒤 5초 안에 `startForeground()`를 호출하지 않으면 `ForegroundServiceDidNotStartInTimeException`이 발생하며 앱이 크래시된다. 무거운 초기화 작업은 `startForeground()` 호출 이후로 미뤄야 한다.

2. `foregroundServiceType` 누락
   Android 14 이상 타겟에서 타입을 지정하지 않고 `startForeground()`를 호출하면 예외가 발생한다. 매니페스트와 코드 양쪽에 동일한 타입을 명시해야 한다.

3. 권한 누락
   `FOREGROUND_SERVICE` 권한뿐 아니라, 타입별 세부 권한(`FOREGROUND_SERVICE_MICROPHONE` 등)도 함께 선언해야 한다. 실제 하드웨어 접근 권한(`RECORD_AUDIO`, `CAMERA` 등)은 별도로 런타임 권한 요청도 필요하다.

4. Notification 채널 미생성
   Android 8.0 이상에서 채널 없이 Notification을 빌드하면 아예 표시되지 않거나 크래시가 발생할 수 있다. Service가 시작되기 전에 Application 클래스나 초기화 시점에 채널을 미리 만들어두는 것이 안전하다.

5. `onDestroy()`에서 리소스 미정리
   Coroutine Scope, WakeLock, MediaRecorder, ExoPlayer 등은 명시적으로 해제하지 않으면 메모리 누수나 배터리 소모 문제로 이어진다.

6. 백그라운드에서 Foreground Service 시작 시도
   Android 12 이상에서는 앱이 백그라운드 상태일 때 `startForegroundService()`를 호출하면 예외가 발생할 수 있다. 사용자가 알림을 탭하거나 앱이 foreground일 때만 시작하도록 설계해야 한다.

7. 불필요하게 오래 실행
   작업이 끝났는데도 `stopForeground()`와 `stopSelf()`를 호출하지 않아 Notification이 계속 남아있는 경우가 있다. 사용자 입장에서는 "왜 이 앱이 아직도 실행 중이지?"라는 의문을 갖게 되고, 스토어 정책 위반으로 이어질 수도 있다.

8. `START_STICKY` 남용
   모든 Service에 무조건 `START_STICKY`를 반환하면, 사용자가 원치 않는데도 Service가 계속 재시작되는 상황이 생길 수 있다. 작업 성격에 맞게 반환값을 선택해야 한다.

9. Notification 클릭 시 이동 처리 누락
   `PendingIntent`를 설정하지 않으면 사용자가 Notification을 탭해도 아무 반응이 없다. 최소한 앱의 관련 화면으로 이동하는 `PendingIntent`는 설정해두는 것이 좋다.

10. 테스트를 실기기에서만 하고 다양한 OEM 대응 누락
    삼성, 샤오미 등 일부 제조사는 자체적인 배터리 최적화 정책으로 Foreground Service를 더 적극적으로 종료시키는 경우가 있다. 실제 배포 전에는 여러 제조사 기기에서 테스트하는 것이 바람직하다.

<br>

## 17. 정리

- Foreground Service는 사용자가 명시적으로 인지해야 하는 지속적인 작업을 안정적으로 실행하기 위한 Android의 핵심 컴포넌트다.
  일반 Service와 달리 Notification 표시를 대가로 시스템의 백그라운드 실행 제한을 우회할 수 있으며, 음악 재생, 녹음, 내비게이션, 파일 전송 등 다양한 실전 시나리오에서 사용된다.
- 버전이 올라갈수록 `foregroundServiceType` 명시, 런타임 알림 권한, 백그라운드 시작 제한 등 정책이 점점 엄격해지고 있으므로, 최신 타겟 SDK를 기준으로 매니페스트와 코드를 함께 관리해야 한다.
  `startForegroundService()`와 `startForeground()`의 역할 차이, 5초 이내 호출 규칙, `START_STICKY`/`START_NOT_STICKY`/`START_REDELIVER_INTENT`의 차이를 명확히 이해하는 것이 실무에서 크래시를 예방하는 데 직결된다.
- "지금 사용자가 인지해야 하는 작업인가"를 기준으로 Foreground Service와 WorkManager를 구분해서 사용하고, Coroutine과 결합할 때는 Scope를 직접 관리하며 `onDestroy()`에서 반드시 정리하는 습관을 들이는 것이 중요하다.
- WakeLock, MediaRecorder 등 하드웨어 리소스는 생명주기와 무관하게 누수될 수 있으므로, 정상 종료 경로와 비정상 종료 경로 양쪽 모두에서 해제 로직을 갖춰야 한다.
