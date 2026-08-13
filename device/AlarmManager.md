# AlarmManager

목차
1. AlarmManager란?
2. AlarmManager가 필요한 이유
3. AlarmManager의 동작 구조
4. Alarm과 일반적인 지연 작업의 차이
5. set(), setExact(), setWindow() 차이
6. setRepeating()과 반복 알람
7. setInexactRepeating()의 특징
8. PendingIntent와 AlarmManager
9. BroadcastReceiver와 AlarmManager
10. Doze Mode와 AlarmManager
11. setExactAndAllowWhileIdle()의 동작
12. 정확한 알람 권한과 Android 버전별 제한
13. AlarmManager와 WorkManager 비교
14. AlarmManager와 Foreground Service 비교
15. 알람 취소와 재등록
16. 실전에서 사용하는 경우
17. 주의사항과 자주 하는 실수
18. 정리

<br>

## 1. AlarmManager란?

AlarmManager는 지정된 시각 또는 지정된 시간 간격마다 특정 작업을 트리거하도록 시스템에 예약하는 Android 시스템 서비스다. 앱의 프로세스가 살아있지 않아도, 심지어 종료된 상태에서도 시스템이 정해진 시각에 앱을 깨워 등록된 작업을 실행시킬 수 있다는 점이 가장 큰 특징이다.

내부적으로는 `Context.getSystemService(Context.ALARM_SERVICE)`를 통해 획득하며, 알람이 울릴 때 실행할 대상은 `PendingIntent` 형태로 전달한다.

```kotlin
val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
```

AlarmManager는 Handler의 `postDelayed()`나 Coroutine의 `delay()`와 근본적으로 다르다. 이들은 앱 프로세스가 메모리에 살아있는 동안에만 유효하지만, AlarmManager는 시스템 레벨에서 관리되기 때문에 앱이 종료되어도, 심지어 기기가 재부팅되어도(재부팅 이후 재등록 로직만 있다면) 예약된 시각에 정확히 동작을 트리거할 수 있다.

```kotlin
val intent = Intent(context, AlarmReceiver::class.java)
val pendingIntent = PendingIntent.getBroadcast(
    context, REQUEST_CODE, intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

alarmManager.setExact(
    AlarmManager.RTC_WAKEUP,
    triggerAtMillis,
    pendingIntent
)
```

<br>

## 2. AlarmManager가 필요한 이유

Android 앱은 기본적으로 사용자가 실행하고 있을 때만 코드가 동작한다. 하지만 다음과 같은 요구사항들은 앱이 실행 중이지 않아도 특정 시각에 정확히 처리되어야 한다.

- 매일 아침 정해진 시간에 울리는 알람 앱
- 특정 시각에 리마인더를 보내는 캘린더/할 일 앱
- 정기 결제 알림, 구독 만료 알림
- 특정 시간 간격으로 데이터를 갱신해야 하는 위젯
- 예약된 시각에 자동으로 시작되는 녹음/작업

이런 요구사항을 Handler나 Coroutine의 `delay()`로 구현하면, 앱 프로세스가 종료되는 순간 예약이 모두 사라진다. 사용자가 알람 앱을 설정해두고 앱을 완전히 종료했는데 알람이 울리지 않는다면 치명적인 결함이 된다.

AlarmManager는 이런 문제를 해결하기 위해 시스템 자체가 예약을 기억하고, 지정된 시각이 되면 앱이 죽어있더라도 프로세스를 새로 띄워서(또는 BroadcastReceiver만 잠깐 실행해서) 작업을 처리하게 해준다.

다만 정확한 시각 실행이 배터리에 미치는 영향이 크기 때문에, Android는 버전이 올라갈수록 AlarmManager의 정확도와 사용 조건을 점점 더 엄격하게 제한하고 있다. 이 균형을 이해하는 것이 AlarmManager를 다루는 핵심이다.

<br>

## 3. AlarmManager의 동작 구조

AlarmManager의 동작은 크게 세 요소로 이루어진다.

1. 트리거 시각/간격: 언제 알람을 울릴지 지정
2. 알람 타입: 기기의 절전 상태를 깨울지, 시계 기준인지 부팅 후 경과 시간 기준인지
3. PendingIntent: 알람이 울렸을 때 실제로 실행할 작업(대부분 BroadcastReceiver)

알람 타입은 다음 네 가지가 있다.

| 타입 | 기준 시각 | 절전 상태(Sleep) 깨움 여부 |
|---|---|---|
| `RTC` | 실제 시계 시각(wall clock time) | 깨우지 않음 |
| `RTC_WAKEUP` | 실제 시계 시각 | 깨움 |
| `ELAPSED_REALTIME` | 부팅 후 경과 시간 | 깨우지 않음 |
| `ELAPSED_REALTIME_WAKEUP` | 부팅 후 경과 시간 | 깨움 |

`RTC` 계열은 "오후 9시에 알람"처럼 실제 날짜/시각 기준으로 예약할 때 사용하고, `ELAPSED_REALTIME` 계열은 "지금부터 30분 후"처럼 상대적인 시간 기준으로 예약할 때 사용한다.

`WAKEUP`이 붙은 타입은 기기가 절전 모드(화면 꺼짐, CPU 슬립)에 있어도 시스템을 깨워서 알람을 실행시킨다. 반대로 `WAKEUP`이 없는 타입은 기기가 어차피 다른 이유로 깨어날 때까지 알람 실행이 지연될 수 있다.

```kotlin
// 정확히 지금부터 10초 뒤, 절전 상태여도 깨워서 실행
alarmManager.setExact(
    AlarmManager.ELAPSED_REALTIME_WAKEUP,
    SystemClock.elapsedRealtime() + 10_000,
    pendingIntent
)
```

<br>

## 4. Alarm과 일반적인 지연 작업의 차이

`Handler.postDelayed()`, `Coroutine.delay()`, `Timer` 같은 지연 작업들은 모두 앱 프로세스의 생명주기 안에서만 유효하다.

| 구분 | Handler/Coroutine delay | AlarmManager |
|---|---|---|
| 실행 주체 | 앱 프로세스 내부 | 시스템 (프로세스 밖) |
| 앱 종료 시 | 예약이 소멸됨 | 예약이 유지됨 |
| 정확도 | 프로세스가 살아있으면 정확 | 시스템 최적화 정책에 따라 달라질 수 있음 |
| 용도 | 화면이 떠 있는 동안의 짧은 지연 | 장시간 후, 또는 앱이 죽어도 실행되어야 하는 작업 |
| 배터리 영향 | 거의 없음 | 부정확한 사용 시 배터리 소모 큼 |

예를 들어 "3초 뒤에 애니메이션을 재생"하는 작업은 `delay(3000)`으로 충분하다. 하지만 "내일 아침 7시에 사용자를 깨우는 알람"은 앱이 백그라운드에 있든 완전히 종료되어 있든 반드시 동작해야 하므로 AlarmManager가 필요하다.

반대로, AlarmManager를 짧은 지연 작업에 남용하는 것도 좋지 않다. 시스템 리소스를 사용하는 만큼, 화면이 떠 있는 동안만 유효하면 되는 작업에는 Coroutine이나 Handler가 훨씬 가볍고 적절하다.

<br>

## 5. set(), setExact(), setWindow() 차이

AlarmManager는 정확도에 따라 여러 등록 함수를 제공한다.

`set(type, triggerAtMillis, operation)`
- Android 4.4(API 19) 이후부터는 정확한 시각이 보장되지 않는다.
- 시스템이 배터리 최적화를 위해 여러 알람을 묶어서(batching) 처리할 수 있다.
- 대략적인 시각에만 실행되어도 되는 경우에 사용한다.

`setExact(type, triggerAtMillis, operation)`
- 지정한 시각에 정확히 알람을 실행하려고 시도한다.
- 단, Doze Mode 중에는 지연될 수 있다(뒤에서 다룸).
- 정확도가 중요한 알람 앱, 리마인더 등에 사용한다.

```kotlin
alarmManager.setExact(
    AlarmManager.RTC_WAKEUP,
    triggerAtMillis,
    pendingIntent
)
```

`setWindow(type, windowStartMillis, windowLengthMillis, operation)`
- 정확한 한 시점이 아니라 "이 구간 안의 어느 시점"에 실행되도록 허용한다.
- 시스템이 배터리 최적화를 위해 구간 내에서 유연하게 타이밍을 조절할 수 있다.
- 정확도는 약간 포기하되 시스템 배터리 최적화에 협조하고 싶을 때 사용한다.

```kotlin
alarmManager.setWindow(
    AlarmManager.RTC_WAKEUP,
    windowStartMillis,
    10 * 60 * 1000L, // 10분 구간
    pendingIntent
)
```

정리하면, 정확도가 중요할수록 시스템 자원을 더 많이 사용하게 된다. `set()` < `setWindow()` < `setExact()` 순으로 정확도가 높아지고, 그만큼 배터리 관점에서는 더 신중하게 사용해야 한다.

<br>

## 6. setRepeating()과 반복 알람

특정 간격으로 반복 실행되는 알람은 `setRepeating()`으로 등록한다.

```kotlin
alarmManager.setRepeating(
    AlarmManager.RTC_WAKEUP,
    firstTriggerMillis,
    AlarmManager.INTERVAL_DAY, // 하루 간격
    pendingIntent
)
```

`AlarmManager`는 자주 쓰이는 간격 상수를 미리 제공한다.

- `INTERVAL_FIFTEEN_MINUTES`
- `INTERVAL_HALF_HOUR`
- `INTERVAL_HOUR`
- `INTERVAL_HALF_DAY`
- `INTERVAL_DAY`

`setRepeating()`으로 등록한 반복 알람은 Android 4.4부터 정확한 시각이 보장되지 않는다. 즉 "정확히 매일 오전 9시"가 아니라 "대략 오전 9시 전후"로 실행될 수 있다. 완전히 정확한 반복이 필요하다면 알람이 울릴 때마다 콜백 내부에서 `setExact()`로 다음 알람을 다시 등록하는 방식(self-scheduling)을 사용해야 한다.

```kotlin
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 실제 알람 처리 로직

        // 다음 알람을 정확히 다시 예약
        val nextTrigger = calculateNextTriggerTime()
        AlarmScheduler.scheduleExactAlarm(context, nextTrigger)
    }
}
```

이 방식은 반복 간격이 매번 정확히 지켜져야 하는 알람 앱(기상 알람 등)에서 널리 쓰인다.

<br>

## 7. setInexactRepeating()의 특징

`setInexactRepeating()`은 이름 그대로 "부정확한 반복"을 명시적으로 허용하는 API다. 사실 Android 4.4 이후에는 `setRepeating()`도 내부적으로 부정확하게 동작하므로 둘의 실질적인 차이는 크지 않아졌지만, 의도를 명확히 드러낸다는 점에서 여전히 의미가 있다.

```kotlin
alarmManager.setInexactRepeating(
    AlarmManager.RTC_WAKEUP,
    firstTriggerMillis,
    AlarmManager.INTERVAL_HOUR,
    pendingIntent
)
```

시스템은 여러 앱이 등록한 부정확한 반복 알람들을 가능한 한 같은 시점으로 묶어서(batch) 한꺼번에 깨움으로써, 기기가 불필요하게 여러 번 깨어나는 것을 방지하고 배터리를 절약한다. 예를 들어 여러 앱이 각자 "1시간마다"를 요청했다면, 시스템은 이들을 비슷한 시점에 몰아서 실행시킨다.

따라서 정확한 시각이 중요하지 않은 주기적 작업(예: 배경 데이터 캐시 정리, 사용 통계 집계)이라면 `setInexactRepeating()`이나 `set()` 계열을 사용하는 것이 배터리 친화적인 선택이다. 다만 요즘은 이런 용도에는 AlarmManager보다 WorkManager의 `PeriodicWorkRequest`가 더 권장된다.

<br>

## 8. PendingIntent와 AlarmManager

AlarmManager는 알람이 울렸을 때 무엇을 실행할지를 직접 알지 못한다. 대신 `PendingIntent`라는 "미래에 실행될 Intent에 대한 위임 권한"을 전달받아, 지정된 시각에 시스템이 그 `PendingIntent`를 대신 실행해준다.

```kotlin
val intent = Intent(context, AlarmReceiver::class.java).apply {
    putExtra(EXTRA_ALARM_ID, alarmId)
}

val pendingIntent = PendingIntent.getBroadcast(
    context,
    alarmId, // requestCode: 알람마다 고유해야 서로 다른 PendingIntent로 취급됨
    intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
```

여기서 주의할 점이 여러 가지 있다.

`requestCode`의 역할
- `requestCode`가 같고 Intent의 액션/데이터/컴포넌트가 같으면 동일한 `PendingIntent`로 취급된다.
- 여러 개의 서로 다른 알람을 등록하려면 각 알람마다 고유한 `requestCode`(보통 알람의 고유 ID)를 사용해야 한다.
- 같은 `requestCode`로 새로 등록하면 기존 알람 설정이 덮어써진다(취소 목적으로 활용 가능).

`FLAG_IMMUTABLE`과 `FLAG_MUTABLE`
- Android 12부터 `PendingIntent` 생성 시 `FLAG_IMMUTABLE` 또는 `FLAG_MUTABLE`을 명시하지 않으면 예외가 발생한다.
- 시스템이 Intent 내용을 채워 넣어야 하는 특수한 경우(예: 알림의 Direct Reply)가 아니라면 대부분 `FLAG_IMMUTABLE`을 사용한다.

`getBroadcast()` vs `getActivity()` vs `getForegroundService()`
- 알람이 울렸을 때 BroadcastReceiver를 실행하고 싶다면 `getBroadcast()`
- 바로 화면(Activity)을 띄우고 싶다면 `getActivity()`
- Foreground Service를 직접 시작하고 싶다면 `getForegroundService()`를 사용한다.

```kotlin
val pendingIntent = PendingIntent.getForegroundService(
    context, alarmId, serviceIntent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
```

<br>

## 9. BroadcastReceiver와 AlarmManager

가장 일반적인 패턴은 알람이 울렸을 때 `BroadcastReceiver`가 이를 받아 처리하는 방식이다. `BroadcastReceiver`는 실행 시간이 매우 짧기 때문에(대략 10초 내외), 무거운 작업은 직접 하지 않고 WorkManager나 Foreground Service에 위임하는 경우가 많다.

```kotlin
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)

        // 짧은 처리는 여기서 바로
        showAlarmNotification(context, alarmId)

        // 무거운 작업이 필요하다면 WorkManager나 Foreground Service로 위임
        val workRequest = OneTimeWorkRequestBuilder<AlarmSyncWorker>()
            .setInputData(workDataOf(EXTRA_ALARM_ID to alarmId))
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
```

매니페스트에 리시버를 등록해야 한다.

```xml
<receiver
    android:name=".alarm.AlarmReceiver"
    android:exported="false" />
```

`BroadcastReceiver` 안에서 `goAsync()`를 사용하면 실행 시간을 약간 연장할 수 있지만, 여전히 무거운 작업(파일 IO, 네트워크 요청)을 직접 처리하기에는 부적합하다.

```kotlin
override fun onReceive(context: Context, intent: Intent) {
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            doSomeWork()
        } finally {
            pendingResult.finish()
        }
    }
}
```

기기가 재부팅되면 등록해둔 알람이 모두 사라지므로, `BOOT_COMPLETED` 브로드캐스트를 수신해 알람을 다시 등록하는 리시버도 함께 구현해야 한다.

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<receiver android:name=".alarm.BootReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmRepository.rescheduleAllAlarms(context)
        }
    }
}
```

<br>

## 10. Doze Mode와 AlarmManager

Android 6.0(API 23)부터 도입된 Doze Mode는, 기기가 일정 시간 동안 움직임 없이 화면이 꺼진 채로 방치되면 시스템이 네트워크 접근과 백그라운드 작업을 대폭 제한하는 절전 모드다.

Doze Mode가 활성화되면 일반적인 `set()`, `setWindow()`, `setRepeating()`으로 등록한 알람은 Doze의 유지보수 윈도우(maintenance window)가 열릴 때까지 지연된다. 즉 정해진 시각이 되어도 기기가 깊은 절전 상태라면 알람이 바로 실행되지 않을 수 있다.

Doze Mode는 두 단계로 나뉜다.

- 얕은 Doze(화면 꺼짐 + 정지 상태 일정 시간): 네트워크와 일부 작업 제한 시작
- 깊은 Doze(장시간 미사용, 주머니 속 등): 대부분의 백그라운드 작업, 알람, 네트워크, GPS가 유예됨

이 상태에서도 정확히 실행되어야 하는 알람(기상 알람, 캘린더 리마인더 등)을 위해 Android는 `setExactAndAllowWhileIdle()`과 `setAlarmClock()`이라는 예외 API를 제공한다.

<br>

## 11. setExactAndAllowWhileIdle()의 동작

`setExactAndAllowWhileIdle()`은 Doze Mode 중에도 알람이 정확한 시각에 실행되도록 허용하는 API다.

```kotlin
alarmManager.setExactAndAllowWhileIdle(
    AlarmManager.RTC_WAKEUP,
    triggerAtMillis,
    pendingIntent
)
```

다만 이 API에도 제약이 있다.

- 같은 앱이 이 API로 등록한 알람은 시스템이 Doze 최적화를 위해 최소 실행 간격을 강제할 수 있다(너무 잦은 정확한 알람 남용 방지).
- 실행은 보장되지만, 정확히 그 밀리초 단위까지 보장되는 것은 아니고 아주 근접한 시점에 실행된다.

가장 강력한 정확도가 필요한 경우(사용자가 명시적으로 설정한 알람 시계 등)에는 `setAlarmClock()`을 사용한다.

```kotlin
val alarmClockInfo = AlarmManager.AlarmClockInfo(
    triggerAtMillis,
    showIntent // 알람 UI로 이동하는 PendingIntent, 상태바 아이콘 클릭 시 사용됨
)
alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
```

`setAlarmClock()`으로 등록한 알람은 Doze Mode의 영향을 거의 받지 않으며, 상태 표시줄에 알람 아이콘이 표시되어 사용자가 "이 앱이 알람을 예약해두었다"는 것을 명확히 인지할 수 있다. 다만 이 API는 진짜 "시계형 알람" 용도로 설계된 것이라, 일반적인 리마인더나 백그라운드 동기화에 남용하면 시스템 정책상 부적절할 수 있다.

<br>

## 12. 정확한 알람 권한과 Android 버전별 제한

Android 12(API 31)부터 정확한 알람(`setExact()`, `setExactAndAllowWhileIdle()`, `setAlarmClock()`)을 사용하려면 `SCHEDULE_EXACT_ALARM` 권한이 필요해졌다.

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

Android 13(API 33)부터는 이 권한이 기본적으로 거부된 상태로 시작하며, 사용자가 설정 화면에서 직접 허용해야 한다. 앱은 `AlarmManager.canScheduleExactAlarms()`로 현재 권한 상태를 확인할 수 있다.

```kotlin
fun canScheduleExactAlarm(context: Context): Boolean {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager.canScheduleExactAlarms()
    } else {
        true
    }
}
```

권한이 없다면 사용자를 설정 화면으로 안내해야 한다.

```kotlin
fun requestExactAlarmPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}
```

Android 14(API 34)부터는 정책이 한층 더 세분화되어, 알람/캘린더 카테고리의 앱이 아니라면 `SCHEDULE_EXACT_ALARM` 권한이 기본적으로 승인되지 않는 경우가 늘었고, 대신 대안으로 `USE_EXACT_ALARM` 권한(특정 카테고리 앱 전용, 설치 시 자동 부여)이 도입되었다.

권한이 없는 상태에서 `setExact()` 계열 API를 호출하면 `SecurityException`이 발생하므로, 실무에서는 반드시 다음과 같은 흐름으로 방어 코드를 작성해야 한다.

```kotlin
fun scheduleExactAlarmSafely(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    if (canScheduleExactAlarm(context)) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
        )
    } else {
        // 권한이 없다면 부정확한 알람으로 폴백하거나 사용자에게 권한 요청 안내
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP, triggerAtMillis, 10 * 60 * 1000L, pendingIntent
        )
    }
}
```

<br>

## 13. AlarmManager와 WorkManager 비교

| 구분 | AlarmManager | WorkManager |
|---|---|---|
| 목적 | 정해진 시각에 정확히 트리거 | 지연 가능한 백그라운드 작업 실행 보장 |
| 정확도 | 매우 높음(설정 시) | 낮음, 시스템이 최적 시점에 실행 |
| Doze 대응 | 예외 API 필요(`setExactAndAllowWhileIdle`) | 시스템이 내부적으로 알아서 최적화 |
| 재부팅 대응 | `BOOT_COMPLETED` 수신 후 수동 재등록 필요 | 자동으로 유지됨(DB 기반 영속성) |
| 적합한 작업 | 알람 시계, 정확한 리마인더 | 동기화, 업로드, 정기 정리 작업 |
| API 복잡도 | 상대적으로 단순 | Constraint, Chain 등 다양한 기능 제공 |

일반적인 가이드라인은 다음과 같다.

- "정확히 이 시각에 반드시 실행되어야 한다" → AlarmManager
- "언젠가, 조건이 맞을 때 실행되면 된다" → WorkManager

캘린더 리마인더나 알람 시계 앱은 시각 자체가 핵심 가치이기 때문에 AlarmManager가 적합하다. 반면 "와이파이가 연결되면 로그를 업로드"하는 작업은 정확한 시각이 중요하지 않으므로 WorkManager의 `Constraints`를 활용하는 것이 훨씬 안정적이고 배터리 효율적이다.

```kotlin
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.UNMETERED)
    .build()

val syncRequest = PeriodicWorkRequestBuilder<LogSyncWorker>(1, TimeUnit.HOURS)
    .setConstraints(constraints)
    .build()
```

<br>

## 14. AlarmManager와 Foreground Service 비교

둘은 종종 함께 쓰이지만 역할이 다르다. AlarmManager는 "언제(when)" 실행할지를 결정하는 예약 도구이고, Foreground Service는 "실행되는 동안 어떻게(how) 지속시킬지"를 담당하는 실행 도구다.

전형적인 조합은 다음과 같다.

1. AlarmManager로 특정 시각에 BroadcastReceiver를 트리거
2. BroadcastReceiver에서 Foreground Service를 시작
3. Foreground Service가 실제 작업(녹음 시작, 위치 추적 시작 등)을 지속적으로 수행

```kotlin
class ScheduledRecordingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
```

즉 "예약된 시각에 녹음을 자동으로 시작하는 기능"을 만든다면, AlarmManager로 시각을 예약하고 실제 녹음 지속은 Foreground Service가 담당하는 구조가 자연스럽다. AlarmManager 단독으로는 오래 지속되는 작업을 표현할 수 없고, Foreground Service 단독으로는 "미래의 정확한 시각"을 예약할 수 없기 때문에 두 API는 상호 보완적이다.

<br>

## 15. 알람 취소와 재등록

등록한 알람을 취소하려면 알람 등록 때 사용한 것과 동일한 `PendingIntent`(같은 `requestCode`, 같은 Intent 구성)를 다시 만들어 `cancel()`에 전달해야 한다.

```kotlin
fun cancelAlarm(context: Context, alarmId: Int) {
    val intent = Intent(context, AlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, alarmId, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(pendingIntent)
    pendingIntent.cancel()
}
```

`requestCode`가 일치하지 않으면 시스템은 완전히 다른 알람으로 인식하기 때문에 취소가 되지 않는다. 이런 이유로 알람마다 고유 ID를 DB(Room 등)에 저장해두고, 취소/수정 시 항상 그 ID를 재사용하는 패턴이 일반적이다.

```kotlin
@Entity
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val triggerAtMillis: Long,
    val isRepeating: Boolean,
    val isEnabled: Boolean
)
```

알람 시각을 수정하는 경우, 대부분은 "취소 후 재등록"의 형태로 구현한다.

```kotlin
fun updateAlarm(context: Context, alarm: AlarmEntity) {
    cancelAlarm(context, alarm.id)
    if (alarm.isEnabled) {
        scheduleExactAlarmSafely(context, alarm.triggerAtMillis, buildPendingIntent(context, alarm.id))
    }
}
```

재부팅 이후에는 시스템이 모든 알람을 잃어버리므로, 활성화된 알람 목록을 DB에서 조회해 일괄적으로 재등록하는 로직이 반드시 필요하다.

```kotlin
object AlarmRepository {
    fun rescheduleAllAlarms(context: Context) {
        val alarms = alarmDao.getAllEnabledAlarms()
        alarms.forEach { alarm ->
            scheduleExactAlarmSafely(context, alarm.triggerAtMillis, buildPendingIntent(context, alarm.id))
        }
    }
}
```

<br>

## 16. 실전에서 사용하는 경우

알람 시계 앱
- `setAlarmClock()` 사용으로 Doze Mode 영향을 최소화
- 상태바 알람 아이콘으로 사용자에게 예약 상태를 명확히 전달
- 알람이 울리면 Full-screen Intent로 잠금화면 위에 알람 UI 표시

캘린더/할 일 리마인더
- `setExactAndAllowWhileIdle()`로 정확한 시각에 알림 표시
- 여러 일정이 있다면 각 일정 ID를 `requestCode`로 사용해 개별 관리

정기 결제/구독 만료 알림
- 정확도가 아주 중요하지는 않으므로 `setWindow()`나 WorkManager 병행 고려
- 하루~며칠 단위의 리드타임을 두고 미리 알림

예약 녹음/예약 촬영
- AlarmManager로 시작 시각을 예약하고, 실제 녹음/촬영은 Foreground Service로 위임
- 사용자가 예약을 취소하면 등록된 알람도 함께 취소되어야 함

위젯 주기 갱신
- 예전에는 `setInexactRepeating()`을 많이 사용했으나, 최근에는 `WorkManager`의 `PeriodicWorkRequest`나 `GlanceAppWidget`의 자체 업데이트 메커니즘을 사용하는 추세

수면 추적, 명상 앱의 예약 알림
- 특정 시각에 세션 시작을 유도하는 알림을 AlarmManager로 예약
- 사용자가 세션을 미루거나 반복 설정을 바꾸면 알람도 함께 갱신

<br>

## 17. 주의사항과 자주 하는 실수

1. `requestCode` 관리 소홀
   여러 알람에 동일한 `requestCode`를 사용하면 서로 덮어써지거나, 다른 `requestCode`를 사용하면 취소가 되지 않는 문제가 생긴다. 알람의 고유 ID를 일관되게 `requestCode`로 사용해야 한다.

2. `FLAG_IMMUTABLE` 누락
   Android 12 이상 타겟에서 `PendingIntent` 생성 시 `FLAG_IMMUTABLE`이나 `FLAG_MUTABLE`을 지정하지 않으면 빌드 자체는 되지만 런타임에 예외가 발생한다.

3. 정확한 알람 권한 미확인
   `SCHEDULE_EXACT_ALARM` 권한 없이 `setExact()` 계열을 호출하면 `SecurityException`이 발생한다. 항상 `canScheduleExactAlarms()`로 사전 확인 후 호출해야 한다.

4. 재부팅 후 알람 소실
   `BOOT_COMPLETED` 리시버를 등록하지 않으면, 기기가 재부팅되는 순간 모든 예약이 사라진다. 알람 앱에서 가장 치명적인 버그 중 하나다.

5. Doze Mode 미고려
   일반 `set()`이나 `setRepeating()`으로 등록한 알람이 Doze 상태에서 지연되는 것을 모르고 "가끔 알람이 안 울린다"는 버그 리포트를 받는 경우가 많다. 정확도가 필요하다면 처음부터 `setExactAndAllowWhileIdle()`이나 `setAlarmClock()`을 사용해야 한다.

6. BroadcastReceiver에서 무거운 작업 직접 처리
   `onReceive()`는 몇 초 안에 반환되어야 한다. 시간이 오래 걸리는 작업을 그 안에서 동기적으로 처리하면 ANR이 발생할 수 있다. WorkManager나 Foreground Service로 위임해야 한다.

7. 정확한 알람 남용
   모든 알림을 `setExactAndAllowWhileIdle()`로 등록하면 배터리 소모가 커지고, 시스템이 해당 앱의 정확 알람 요청 빈도를 제한할 수도 있다. 정말 정확도가 필요한 경우로 한정해야 한다.

8. 알람 취소 시 DB 상태와 불일치
   사용자가 앱에서 알람을 껐는데 실제 시스템에는 여전히 등록되어 있는 경우가 생길 수 있다. UI 상태(DB)와 실제 AlarmManager 등록 상태를 항상 동기화해야 한다.

9. 테스트 시 시간 조작 미흡
   알람 로직을 테스트할 때 실제로 몇 시간~며칠을 기다릴 수 없으므로, 트리거 시각 계산 로직을 별도 함수로 분리해 단위 테스트하는 것이 바람직하다.

10. 제조사별 배터리 최적화 차이
    일부 제조사는 자체적인 절전 정책으로 정확한 알람도 지연시키는 경우가 있다. 배터리 최적화 제외 요청(`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)을 안내하는 것이 도움이 될 수 있으나, 남용하면 스토어 정책과 충돌할 수 있으므로 신중히 사용해야 한다.

<br>

## 18. 정리

- AlarmManager는 앱 프로세스의 생명주기와 무관하게 정해진 시각에 정확히 작업을 트리거해야 하는 상황을 위한 시스템 서비스다. `set()`, `setExact()`, `setWindow()`, `setRepeating()` 등 정확도와 배터리 효율 사이에서 선택할 수 있는 다양한 API를 제공하며, Doze Mode 같은 시스템 절전 정책 속에서도 정확한 실행이 필요하다면 `setExactAndAllowWhileIdle()`이나 `setAlarmClock()`을 사용해야 한다.

- Android 12 이상에서는 `SCHEDULE_EXACT_ALARM` 권한 확인이 필수가 되었고, `PendingIntent`의 `FLAG_IMMUTABLE` 지정 역시 빠뜨리면 런타임 예외로 이어진다. 재부팅 시 알람이 소실되는 특성 때문에 `BOOT_COMPLETED` 리시버를 통한 재등록 로직도 반드시 갖춰야 한다.

- 정확한 시각 실행이 핵심 가치라면 AlarmManager를, 지연 가능한 조건부 백그라운드 작업이라면 WorkManager를 선택하는 것이 기본 원칙이다. 또한 AlarmManager는 "언제 실행할지"를 예약할 뿐이므로, 실행 이후 오래 지속되어야 하는 작업이 있다면 Foreground Service와 조합해서 설계하는 것이 실전에서 가장 흔하고 안정적인 패턴이다.
