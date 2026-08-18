# PendingIntent

<br>

## 목차

1. PendingIntent란 무엇인가
2. PendingIntent가 필요한 이유
3. 생성 방법 (getActivity/getService/getBroadcast/getForegroundService)
4. Flags 이해하기 (IMMUTABLE, MUTABLE, UPDATE_CURRENT 등)
5. requestCode와 Intent 매칭 규칙
6. Notification에서 PendingIntent 사용하기
7. AlarmManager와 PendingIntent
8. AppWidget에서 PendingIntent 사용하기
9. BroadcastReceiver 트리거용 PendingIntent
10. Android 12 이상 Mutability 정책 대응
11. PendingIntent 취소와 생명주기 관리
12. 보안 이슈: Intent Redirection
13. 주의사항과 자주 하는 실수
14. 정리

<br>

## 1. PendingIntent란 무엇인가

`PendingIntent`는 "나중에, 다른 프로세스(주로 시스템)가 대신 실행해줄 Intent"를 감싼 토큰이다. 이름 그대로 **보류된(pending) 상태의 Intent**로, 우리 앱이 직접 `startActivity()`를 호출하는 대신 시스템(알림 매니저, 알람 매니저, 런처 등)에 "이 작업을 나중에 이 권한으로 실행해줘"라고 위임할 때 사용한다.

핵심은 **권한 위임**이다. 일반 `Intent`는 그 자체로는 실행 권한이 없고, 실행하는 주체(예: 우리 앱)의 권한을 사용한다. 하지만 알림을 탭했을 때 실제로 Activity를 띄우는 주체는 우리 앱이 아니라 System UI(알림 패널) 프로세스다. System UI는 우리 앱의 컴포넌트를 실행할 권한이 없다. `PendingIntent`는 이 문제를 해결하기 위해, "이 Intent를 실행할 때는 원래 앱(우리 앱)의 권한과 신원으로 실행하라"는 위임 토큰을 시스템에 전달한다.

```kotlin
val intent = Intent(context, MainActivity::class.java)
val pendingIntent = PendingIntent.getActivity(
    context,
    0,
    intent,
    PendingIntent.FLAG_IMMUTABLE
)
```

| 개념 | 설명 |
|---|---|
| Intent | 지금 당장 우리 앱이 직접 실행하는 작업 요청 |
| PendingIntent | 나중에, 다른 프로세스가 우리 앱 권한으로 대신 실행하는 작업 위임 토큰 |
| 실행 시점 | PendingIntent를 만든 시점이 아니라, `send()`가 호출되는 시점 (예: 사용자가 알림을 탭한 순간) |

**실전 팁**
- PendingIntent를 만드는 시점과 실제로 실행되는 시점이 다르다는 것이 헷갈리기 쉬운 부분이다. 알림을 만들 때 PendingIntent에 담은 Intent의 extra 값들은 "알림이 눌리는 시점"에 그대로 전달된다.
- PendingIntent 자체는 Intent를 포함하지만, 실행 권한은 PendingIntent를 만든 앱(원본 앱)의 것을 그대로 유지한다는 점을 기억하자.

<br>

## 2. PendingIntent가 필요한 이유

PendingIntent가 없다면 시스템 컴포넌트가 우리 앱의 화면을 열거나 서비스를 실행할 방법이 없다. 대표적으로 다음 상황들에서 반드시 필요하다.

1. **Notification** - 알림을 탭했을 때 특정 Activity를 열거나, 알림의 액션 버튼(예: "답장", "완료 처리")을 눌렀을 때 특정 작업을 수행할 때
2. **AlarmManager** - 지정한 시각에 시스템이 우리 앱의 BroadcastReceiver나 Activity를 깨워야 할 때
3. **AppWidget** - 홈 화면 위젯의 버튼을 눌렀을 때 앱의 특정 동작을 실행할 때
4. **Geofencing, Location 콜백** - 특정 위치 이벤트가 발생했을 때 시스템이 우리 앱에 콜백을 전달할 때

```kotlin
// 알림 탭 시 실행될 PendingIntent
val contentIntent = PendingIntent.getActivity(
    context, 0,
    Intent(context, DetailActivity::class.java).apply {
        putExtra("noteId", noteId)
    },
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
)

val notification = NotificationCompat.Builder(context, CHANNEL_ID)
    .setContentTitle("새 메모")
    .setContentIntent(contentIntent)
    .setSmallIcon(R.drawable.ic_note)
    .build()
```

| 사용처 | 왜 PendingIntent가 필요한가 |
|---|---|
| Notification | System UI 프로세스가 우리 앱을 대신 실행 |
| AlarmManager | 시스템 알람 서비스가 지정 시각에 우리 앱을 깨움 |
| AppWidget | 홈 런처 프로세스가 우리 앱 동작을 트리거 |
| Geofence/Location API | Google Play Services가 이벤트 발생 시 콜백 전달 |

**실전 팁**
- "다른 프로세스가 내 앱의 코드를 실행해야 하는가?"라는 질문이 PendingIntent 필요 여부를 판단하는 가장 빠른 기준이다.
- 앱 안에서 직접 startActivity()가 가능한 상황(같은 프로세스, 같은 화면 흐름)이라면 PendingIntent는 필요 없다.

<br>

## 3. 생성 방법 (getActivity/getService/getBroadcast/getForegroundService)

PendingIntent는 실행하고자 하는 컴포넌트 종류에 따라 4가지 팩토리 메서드로 생성한다.

```kotlin
// Activity 실행
val activityPendingIntent = PendingIntent.getActivity(
    context, requestCode, intent, flags
)

// Service 시작
val servicePendingIntent = PendingIntent.getService(
    context, requestCode, intent, flags
)

// BroadcastReceiver에 브로드캐스트 전달
val broadcastPendingIntent = PendingIntent.getBroadcast(
    context, requestCode, intent, flags
)

// Foreground Service 시작 (API 26+)
val foregroundServicePendingIntent = PendingIntent.getForegroundService(
    context, requestCode, intent, flags
)
```

공통 파라미터 구조는 동일하다.

```kotlin
PendingIntent.getActivity(
    context,      // Context
    requestCode,  // Int, PendingIntent를 구분하는 식별자
    intent,       // 실행할 Intent
    flags         // 동작 방식을 결정하는 플래그 (비트 OR로 조합 가능)
)
```

| 팩토리 메서드 | 실행 대상 | 대표 사용처 |
|---|---|---|
| getActivity() | Activity | 알림 탭 시 화면 이동 |
| getService() | 일반 Service | 백그라운드 작업 트리거 (API 26 미만 권장) |
| getForegroundService() | Foreground Service | 포그라운드 서비스 시작 (API 26+ 필수) |
| getBroadcast() | BroadcastReceiver | 알람, 위젯 버튼 클릭 처리 |

한 가지 덜 알려진 메서드로 `getActivities()`가 있는데, 여러 개의 Intent를 배열로 전달해서 **백스택(back stack)을 함께 구성**할 수 있다. 예를 들어 알림을 눌렀을 때 상세 화면과 함께 그 위에 홈 화면(부모 화면)을 백스택으로 깔아두고 싶을 때 유용하다.

```kotlin
val parentIntent = Intent(context, MainActivity::class.java)
val detailIntent = Intent(context, DetailActivity::class.java).apply {
    putExtra("noteId", noteId)
}

val pendingIntent = PendingIntent.getActivities(
    context, requestCode,
    arrayOf(parentIntent, detailIntent), // 배열 순서 = 백스택 순서
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
```

이렇게 하면 상세 화면에서 뒤로가기를 눌렀을 때 앱이 그냥 종료되지 않고 `MainActivity`로 자연스럽게 돌아간다. `TaskStackBuilder`를 사용하면 이 백스택 구성을 더 명시적으로 표현할 수 있다.

```kotlin
val pendingIntent = TaskStackBuilder.create(context).run {
    addNextIntentWithParentStack(detailIntent)
    getPendingIntent(requestCode, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
```

**실전 팁**
- API 26(Android 8.0) 이상에서 백그라운드 상태의 앱이 일반 `startService()`를 호출하면 `IllegalStateException`이 발생할 수 있으므로, 포그라운드 서비스를 시작해야 한다면 반드시 `getForegroundService()`를 사용하자.
- 실행 대상 컴포넌트 종류와 팩토리 메서드가 일치하지 않으면(`getActivity()`로 만든 PendingIntent에 Service Intent를 넣는 등) 런타임에 조용히 실패하거나 예상과 다르게 동작할 수 있다.
- 딥링크로 열리는 상세 화면에서 뒤로가기 시 자연스러운 백스택 흐름이 필요하다면 `getActivities()`나 `TaskStackBuilder`를 적극 활용하자. 단순히 `getActivity()`만 쓰면 뒤로가기 시 앱이 바로 종료되는 경험을 줄 수 있다.

<br>

## 4. Flags 이해하기 (IMMUTABLE, MUTABLE, UPDATE_CURRENT 등)

PendingIntent의 동작 방식은 플래그 조합으로 결정된다. 여러 플래그를 비트 OR(`or`)로 함께 사용할 수 있다.

```kotlin
val pendingIntent = PendingIntent.getActivity(
    context,
    requestCode,
    intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
```

| 플래그 | 동작 |
|---|---|
| FLAG_IMMUTABLE | 시스템이 이 PendingIntent 내부의 Intent를 수정할 수 없게 함 (Android 12+ 사실상 필수) |
| FLAG_MUTABLE | 시스템(예: Wear OS, 알림 답장)이 Intent extra를 채워 넣을 수 있게 허용 |
| FLAG_UPDATE_CURRENT | 동일 requestCode의 기존 PendingIntent가 있으면 extra 데이터만 최신으로 교체 |
| FLAG_CANCEL_CURRENT | 기존 PendingIntent를 취소하고 새로 생성 |
| FLAG_ONE_SHOT | 한 번 실행되면 이후 재사용 불가 (한 번 쓰고 버리는 알림 액션 등에 사용) |
| FLAG_NO_CREATE | 이미 존재하는 PendingIntent가 없으면 null 반환 (존재 여부 확인용) |

**실전 팁**
- Android 12(API 31)부터 `FLAG_IMMUTABLE` 또는 `FLAG_MUTABLE`을 반드시 명시해야 하며, 생략하면 컴파일 시점에 경고가 뜨고 런타임에도 문제가 생길 수 있다.
- 알림 콘텐츠(예: extra의 id 값)가 자주 바뀌는 알림이라면 `FLAG_UPDATE_CURRENT`를 꼭 넣어야 새 알림을 탭했을 때 최신 extra가 반영된다. 빠뜨리면 예전 알림의 데이터로 열리는 버그가 생긴다.

<br>

## 5. requestCode와 Intent 매칭 규칙

시스템은 PendingIntent를 "같은 것"으로 취급할지 "다른 것"으로 취급할지를 **requestCode + Intent의 ComponentName/Action/Data/Category/Type**의 조합으로 판단한다. 중요한 점은 **Intent의 extra는 이 비교에 포함되지 않는다**는 것이다.

```kotlin
// 이 두 PendingIntent는 extra만 다르고 requestCode, Intent 나머지 요소가 같으므로
// 시스템 입장에서는 "같은" PendingIntent로 취급된다.
val pi1 = PendingIntent.getActivity(
    context, 100,
    Intent(context, DetailActivity::class.java).putExtra("id", 1),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

val pi2 = PendingIntent.getActivity(
    context, 100,
    Intent(context, DetailActivity::class.java).putExtra("id", 2),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
// pi2를 만드는 순간, 기존 pi1의 extra가 id=2로 덮어써진다.
```

각 알림/알람마다 서로 다른 데이터를 담아야 한다면 `requestCode`를 고유하게 부여하거나, Intent의 `data`(Uri)를 다르게 설정해서 구분해야 한다.

```kotlin
val uniqueIntent = Intent(context, DetailActivity::class.java).apply {
    data = Uri.parse("app://note/$noteId") // Uri를 다르게 해서 구분
    putExtra("id", noteId)
}
val pendingIntent = PendingIntent.getActivity(
    context, noteId.toInt(), uniqueIntent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
```

| 매칭에 사용되는 요소 | 매칭에서 제외되는 요소 |
|---|---|
| requestCode | Intent의 extra (Bundle) |
| Intent의 ComponentName | |
| Intent의 Action | |
| Intent의 Data(Uri) | |
| Intent의 Category | |

**실전 팁**
- 노트 여러 개에 대해 각각 알림을 보내면서 같은 `requestCode`(예: 항상 0)를 쓰면, 나중에 만든 알림이 먼저 만든 알림의 PendingIntent를 덮어쓰는 버그가 흔하게 발생한다. `noteId.hashCode()`처럼 고유값을 requestCode로 사용하자.
- extra 값이 달라도 매칭에 영향을 주지 않는다는 사실을 모르면 "분명 다른 데이터를 넣었는데 항상 같은 화면이 열린다"는 디버깅하기 까다로운 버그를 만나게 된다.

<br>

## 6. Notification에서 PendingIntent 사용하기

알림은 PendingIntent가 가장 흔하게 쓰이는 곳이다. 알림 자체를 탭했을 때(`setContentIntent`)와 알림의 액션 버튼(`addAction`)을 눌렀을 때 각각 PendingIntent가 필요하다.

```kotlin
fun showNoteNotification(context: Context, noteId: Int, title: String) {
    val contentIntent = PendingIntent.getActivity(
        context, noteId,
        Intent(context, DetailActivity::class.java).apply {
            putExtra("noteId", noteId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val deleteIntent = PendingIntent.getBroadcast(
        context, noteId,
        Intent(context, NoteDeleteReceiver::class.java).apply {
            putExtra("noteId", noteId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    val notification = NotificationCompat.Builder(context, "note_channel")
        .setSmallIcon(R.drawable.ic_note)
        .setContentTitle(title)
        .setContentIntent(contentIntent)                  // 알림 탭
        .addAction(R.drawable.ic_delete, "삭제", deleteIntent) // 액션 버튼
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(noteId, notification)
}
```

알림에 답장(RemoteInput) 같은 텍스트 입력을 받으려면 시스템이 Intent에 사용자의 입력 값을 채워 넣어야 하므로 `FLAG_MUTABLE`이 필수다.

```kotlin
val remoteInput = RemoteInput.Builder("reply_key")
    .setLabel("답장 입력")
    .build()

val replyIntent = PendingIntent.getBroadcast(
    context, noteId,
    Intent(context, ReplyReceiver::class.java),
    PendingIntent.FLAG_MUTABLE // RemoteInput은 MUTABLE 필수
)

val replyAction = NotificationCompat.Action.Builder(
    R.drawable.ic_reply, "답장", replyIntent
).addRemoteInput(remoteInput).build()
```

| 상황 | 필요한 Flag |
|---|---|
| 단순 화면 이동 (알림 탭) | FLAG_IMMUTABLE 권장 |
| RemoteInput(답장 등) | FLAG_MUTABLE 필수 |
| 데이터가 계속 바뀌는 알림 | FLAG_UPDATE_CURRENT 추가 |

**실전 팁**
- `setAutoCancel(true)`를 설정하지 않으면 알림을 탭해도 알림 패널에서 사라지지 않아 사용자 경험이 나빠진다.
- 액션 버튼마다 서로 다른 동작을 연결하려면 각 PendingIntent의 requestCode 또는 Intent action을 서로 다르게 지정해야 한다.

<br>

## 7. AlarmManager와 PendingIntent

`AlarmManager`는 지정한 시각에 시스템이 대신 우리 앱을 깨워주는 시스템 서비스로, 반드시 PendingIntent와 함께 사용한다.

```kotlin
fun scheduleAlarm(context: Context, triggerAtMillis: Long, requestCode: Int) {
    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra("message", "알람 시간입니다")
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context, requestCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        // 정확한 알람 권한이 없으면 사용자를 설정 화면으로 유도
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        return
    }

    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
    )
}

fun cancelAlarm(context: Context, requestCode: Int) {
    val intent = Intent(context, AlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, requestCode, intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )
    pendingIntent?.let {
        context.getSystemService(Context.ALARM_SERVICE).let { am ->
            (am as AlarmManager).cancel(it)
        }
        it.cancel()
    }
}
```

| AlarmManager 메서드 | 특징 |
|---|---|
| set() | 부정확할 수 있음, 시스템이 배터리 최적화를 위해 시간을 조정 |
| setExact() | 정확한 시각에 실행하지만 Doze 모드에서는 지연될 수 있음 |
| setExactAndAllowWhileIdle() | Doze 모드에서도 정확하게 실행 (남용 시 시스템이 제한) |
| setRepeating() | 반복 알람이지만 API 19+ 부터는 부정확 모드로 동작 |

**실전 팁**
- 알람을 취소하려면 알람을 걸었을 때와 **완전히 동일한 requestCode + Intent**로 새 PendingIntent를 만들어 `cancel()`을 호출해야 한다. `FLAG_NO_CREATE`를 사용하면 실제로 등록된 PendingIntent가 있는지 확인할 수 있다.
- Android 12부터는 정확한 알람(`setExact` 계열)을 사용하려면 `SCHEDULE_EXACT_ALARM` 권한 또는 사용자의 명시적 허용이 필요하다.

<br>

## 8. AppWidget에서 PendingIntent 사용하기

홈 화면 위젯은 별도 프로세스(런처)에서 그려지기 때문에, 위젯의 버튼 클릭 같은 상호작용은 전부 PendingIntent를 통해 우리 앱으로 전달되어야 한다.

```kotlin
class NoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_note)

            val clickIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, widgetId, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

            val refreshIntent = Intent(context, NoteWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, widgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.refreshButton, refreshPendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.app.widget.ACTION_REFRESH"
    }
}
```

| RemoteViews 메서드 | 설명 |
|---|---|
| setOnClickPendingIntent() | 뷰 클릭 시 실행할 PendingIntent 지정 |
| setPendingIntentTemplate() | ListView/GridView 등 컬렉션 위젯에서 아이템별 클릭 처리 |
| setOnClickFillInIntent() | 템플릿 PendingIntent에 아이템별 데이터를 채워 넣음 |

**실전 팁**
- 위젯 안에 리스트(ListView)가 있고 각 아이템마다 다른 동작이 필요하다면 `setOnClickPendingIntent()` 대신 `setPendingIntentTemplate()` + `setOnClickFillInIntent()` 조합을 써야 한다. 아이템마다 PendingIntent를 개별 생성하는 방식은 성능 문제가 있다.
- 위젯 ID(`appWidgetId`)를 requestCode로 사용하면 위젯 인스턴스별로 서로 다른 PendingIntent를 안전하게 구분할 수 있다.

<br>

## 9. BroadcastReceiver 트리거용 PendingIntent

`getBroadcast()`로 만든 PendingIntent는 알람, 위젯 버튼, 알림 액션 등에서 가벼운 백그라운드 처리를 트리거할 때 널리 쓰인다.

```kotlin
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: return

        val pendingResult = goAsync() // 짧은 비동기 작업을 위한 시간 연장
        CoroutineScope(Dispatchers.IO).launch {
            try {
                showNoteNotification(context, 1, message)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

```xml
<receiver
    android:name=".receiver.AlarmReceiver"
    android:exported="false" />
```

| API 레벨 | BroadcastReceiver 백그라운드 실행 제약 |
|---|---|
| API 25 이하 | 비교적 자유로움 |
| API 26+ | Implicit Broadcast(암시적 브로드캐스트) 대부분 제한, Manifest 등록만으로 못 받는 것들이 늘어남 |
| API 26+ | 백그라운드에서 Foreground Service 시작 시 `startForegroundService()` 필요 |

**실전 팁**
- `BroadcastReceiver.onReceive()`는 기본적으로 몇 초 안에 끝나야 하는 짧은 콜백이다. 오래 걸리는 작업은 `goAsync()`로 연장하거나, WorkManager/Foreground Service로 위임하자.
- API 26 이상을 타겟팅한다면 많은 암시적 브로드캐스트가 매니페스트 등록으로는 더 이상 전달되지 않으므로, 동적 등록(`registerReceiver`)이나 다른 대안(WorkManager 등)을 고려해야 한다.

<br>

## 10. Android 12 이상 Mutability 정책 대응

Android 12(API 31)부터는 PendingIntent를 생성할 때 `FLAG_IMMUTABLE` 또는 `FLAG_MUTABLE`을 반드시 명시해야 한다. 이는 악성 앱이 PendingIntent 내부의 Intent를 변조해 의도치 않은 컴포넌트를 실행시키는 보안 취약점을 막기 위한 정책이다.

```kotlin
val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
} else {
    PendingIntent.FLAG_UPDATE_CURRENT
}

val pendingIntent = PendingIntent.getActivity(context, requestCode, intent, flags)
```

`FLAG_IMMUTABLE`은 API 23(Android 6.0)부터 존재했지만, API 30 이하에서는 필수가 아니었다. 하위 호환을 지키려면 SDK 버전 분기가 필요하다.

```kotlin
object PendingIntentCompat {
    fun getFlags(mutable: Boolean, vararg extraFlags: Int): Int {
        var flags = extraFlags.fold(0) { acc, f -> acc or f }
        flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        } else {
            flags
        }
        return flags
    }
}

// 사용 예
val flags = PendingIntentCompat.getFlags(
    mutable = false,
    PendingIntent.FLAG_UPDATE_CURRENT
)
```

| Android 버전 | Mutability 플래그 요구사항 |
|---|---|
| API 22 이하 | FLAG_IMMUTABLE 자체가 존재하지 않음 |
| API 23~30 | FLAG_IMMUTABLE 사용 가능하지만 선택 사항 |
| API 31(Android 12) 이상 | FLAG_IMMUTABLE 또는 FLAG_MUTABLE 명시 필수, 생략 시 예외 발생 가능 |

**실전 팁**
- 특별한 이유(RemoteInput, 위치 콜백 등 시스템이 Intent를 채워야 하는 경우)가 없다면 기본값은 항상 `FLAG_IMMUTABLE`로 두는 것이 안전하다.
- `FLAG_MUTABLE`이 필요한 대표 사례는 알림 답장(RemoteInput), Geofencing, ActivityRecognition API 콜백 정도로 한정된다. "잘 몰라서 일단 MUTABLE"은 보안 사고의 원인이 된다.

targetSdkVersion을 올릴 때 이 부분에서 빌드 경고가 대량으로 발생하는 경우가 많다. 프로젝트 전반에 흩어진 PendingIntent 생성 코드를 한 번에 점검하려면, 아래처럼 공통 유틸 함수를 두고 전 프로젝트에서 재사용하는 방식을 권장한다.

```kotlin
object PendingIntentFlags {
    const val DEFAULT = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    const val MUTABLE_FOR_REMOTE_INPUT = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
}

val pendingIntent = PendingIntent.getActivity(
    context, requestCode, intent, PendingIntentFlags.DEFAULT
)
```

이렇게 상수를 한 곳에 모아두면, 이후 새로운 안드로이드 버전에서 플래그 정책이 또 바뀌더라도 수정 지점이 하나로 좁혀진다.

<br>

## 11. PendingIntent 취소와 생명주기 관리

PendingIntent는 명시적으로 취소하지 않는 한 시스템 어딘가에 계속 남아있을 수 있다. 특히 알람이나 위젯처럼 장기간 유지되는 PendingIntent는 앱 삭제 전까지 시스템 메모리에 남는 경우가 있으므로 필요 없어지면 정리해주는 것이 좋다.

```kotlin
fun cancelPendingIntent(context: Context, requestCode: Int) {
    val intent = Intent(context, AlarmReceiver::class.java)
    val existing = PendingIntent.getBroadcast(
        context, requestCode, intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )
    existing?.let {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(it) // AlarmManager에 등록된 예약 자체도 취소
        it.cancel()             // PendingIntent 자체도 취소
    }
}
```

| 메서드 | 대상 | 설명 |
|---|---|---|
| PendingIntent.cancel() | PendingIntent 자체 | 이후 send() 호출 시 CanceledException 발생 |
| AlarmManager.cancel(pendingIntent) | 알람 예약 | 예약된 알람 트리거를 취소 |
| FLAG_NO_CREATE | 조회 용도 | 기존에 등록된 PendingIntent가 없으면 null 반환, 존재 여부 확인에 유용 |

**실전 팁**
- 사용자가 특정 알람이나 반복 알림을 껐다면, `AlarmManager.cancel()`뿐 아니라 `PendingIntent.cancel()`도 함께 호출해서 완전히 정리하는 습관을 들이자.
- `FLAG_NO_CREATE`로 조회했을 때 `null`이 반환된다면 해당 requestCode+Intent 조합으로 등록된 PendingIntent가 시스템에 없다는 뜻이므로, "이미 취소됐는지" 확인하는 용도로 활용할 수 있다.

<br>

## 12. 보안 이슈: Intent Redirection

`FLAG_MUTABLE`로 생성된 PendingIntent는 이를 전달받은 제3자(다른 앱이나 시스템 프로세스)가 내부 Intent의 일부(주로 extra)를 채우거나 변경할 수 있다. 이 특성이 악용되면 **Intent Redirection** 취약점으로 이어질 수 있다. 예를 들어 원래는 안전한 컴포넌트를 실행하도록 설계된 PendingIntent인데, 악성 앱이 이를 가로채서 내부 Intent의 대상을 자신이 원하는 컴포넌트로 바꿔치기하는 시나리오다.

```kotlin
// 위험한 패턴: 빈 Intent에 MUTABLE을 주고, 대상 컴포넌트를 지정하지 않음
val riskyIntent = Intent() // action, component가 비어있음
val riskyPendingIntent = PendingIntent.getActivity(
    context, 0, riskyIntent, PendingIntent.FLAG_MUTABLE
)
// 이 PendingIntent를 받은 쪽이 setClass()로 컴포넌트를 임의로 채워 넣을 수 있다

// 안전한 패턴: 명시적 컴포넌트 지정 + 불필요하면 IMMUTABLE 사용
val safeIntent = Intent(context, DetailActivity::class.java) // 명시적 컴포넌트
val safePendingIntent = PendingIntent.getActivity(
    context, 0, safeIntent, PendingIntent.FLAG_IMMUTABLE
)
```

| 위험 패턴 | 권장 대응 |
|---|---|
| 대상 컴포넌트가 없는 암시적(implicit) Intent를 MUTABLE로 노출 | 항상 명시적(explicit) Intent 사용 |
| 꼭 필요하지 않은데 MUTABLE을 습관적으로 사용 | 기본값은 IMMUTABLE, MUTABLE은 필요한 경우에만 |
| PendingIntent를 검증 없이 외부 앱에 그대로 전달 | 전달 대상과 목적을 명확히 하고, 최소 권한 원칙 적용 |

**실전 팁**
- 외부에 PendingIntent를 노출해야 한다면(다른 앱과 연동하는 SDK 등) 반드시 명시적 Intent(대상 컴포넌트가 지정된 Intent)로 감싸고, 가능한 한 `FLAG_IMMUTABLE`을 사용하자.
- Google Play의 보안 정책도 이 문제를 중요하게 다루고 있으므로, PendingIntent 관련 린트(lint) 경고는 무시하지 말고 하나씩 검토하는 것이 좋다.
- 정적 분석 도구(Android Lint, detekt 커스텀 룰 등)에 "PendingIntent 생성 시 flag 누락/암시적 Intent 사용"을 감지하는 규칙을 추가해두면, 코드 리뷰 단계에서 사람이 놓치기 쉬운 이 문제를 기계적으로 걸러낼 수 있다.

```kotlin
// 팀 컨벤션 예시: 항상 명시적 컴포넌트 + 최소 권한 플래그를 강제하는 헬퍼
fun Context.explicitPendingIntent(
    target: Class<*>,
    requestCode: Int,
    mutable: Boolean = false,
    configure: Intent.() -> Unit = {}
): PendingIntent {
    val intent = Intent(this, target).apply(configure)
    val flag = if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
    return PendingIntent.getActivity(
        this, requestCode, intent, flag or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
```

이런 헬퍼를 팀 컨벤션으로 강제하면, 신규 합류한 팀원이 실수로 암시적 Intent를 MUTABLE로 노출하는 상황 자체를 원천적으로 줄일 수 있다.

<br>

## 13. 주의사항과 자주 하는 실수

1. Android 12 이상을 타겟팅하면서 `FLAG_IMMUTABLE`/`FLAG_MUTABLE`을 명시하지 않아 크래시나 경고가 발생하는 경우가 많다. 모든 PendingIntent 생성 코드에 둘 중 하나를 반드시 포함하자.
2. 여러 알림/알람에 동일한 `requestCode`(흔히 0)를 재사용해서, 나중에 생성한 PendingIntent가 이전 것을 덮어써버리는 실수가 매우 흔하다. 고유한 requestCode(예: id 값)를 사용하자.
3. Intent의 extra 값만 다르면 다른 PendingIntent라고 착각하는 경우가 많다. requestCode와 Intent의 핵심 필드가 같으면 extra가 달라도 같은 PendingIntent로 취급된다는 점을 기억하자.
4. `FLAG_UPDATE_CURRENT`를 빠뜨려서, 데이터가 바뀐 새 알림을 눌러도 예전 extra 값으로 화면이 열리는 버그가 발생한다.
5. RemoteInput(답장) 기능에 `FLAG_IMMUTABLE`을 사용해서 시스템이 사용자의 입력값을 Intent에 채워 넣지 못하는 실수를 한다. 이 경우는 반드시 `FLAG_MUTABLE`이 필요하다.
6. 불필요하게 습관적으로 `FLAG_MUTABLE`을 사용해서 Intent Redirection 취약점 가능성을 열어두는 경우가 있다. 필요한 경우가 아니면 기본값은 `FLAG_IMMUTABLE`이다.
7. AlarmManager로 예약한 알람을 취소할 때, 알람을 걸었을 때와 다른 requestCode나 Intent로 PendingIntent를 만들어서 취소가 되지 않는 문제가 흔하다. 완전히 동일한 조합을 사용해야 한다.
8. API 26 이상에서 백그라운드 상태의 앱이 `getService()`로 일반 서비스를 시작하려다 `IllegalStateException`을 만나는 경우가 있다. 포그라운드 서비스가 필요하면 `getForegroundService()`를 사용해야 한다.
9. 위젯의 리스트(컬렉션) 뷰마다 개별 PendingIntent를 생성해서 성능 저하와 메모리 낭비를 초래하는 실수를 한다. `setPendingIntentTemplate()` + `setOnClickFillInIntent()` 조합을 사용해야 한다.
10. 대상 컴포넌트가 없는 암시적 Intent를 MUTABLE 상태로 외부에 노출해서 Intent Redirection 보안 취약점을 만드는 경우가 있다. 항상 명시적 Intent를 사용하고 최소 권한 원칙을 지키자.

<br>

## 14. 정리

PendingIntent는 우리 앱이 아닌 다른 프로세스(System UI, AlarmManager, 홈 런처 등)가 우리 앱의 권한으로 특정 작업을 대신 실행할 수 있게 해주는 위임 토큰이다. `getActivity/getService/getForegroundService/getBroadcast` 네 가지 팩토리 메서드로 생성하며, 실제 동작 방식은 `FLAG_IMMUTABLE`, `FLAG_MUTABLE`, `FLAG_UPDATE_CURRENT` 같은 플래그 조합으로 결정된다. 가장 중요한 함정은 두 가지다. 첫째, requestCode와 Intent의 핵심 필드(컴포넌트, 액션, 데이터)가 같으면 extra가 달라도 시스템은 동일한 PendingIntent로 취급하므로, 각 알림·알람마다 구분이 필요하면 고유한 requestCode를 반드시 부여해야 한다. 둘째, Android 12부터는 Mutability 플래그가 필수이며 보안상 기본값은 항상 `FLAG_IMMUTABLE`이어야 하고, `FLAG_MUTABLE`은 RemoteInput처럼 시스템이 Intent를 채워야 하는 예외적인 경우로 한정해야 한다. 알림, 알람, 위젯 등 PendingIntent가 쓰이는 모든 곳에서 이 두 가지 원칙만 지켜도 실무에서 발생하는 대부분의 버그와 보안 이슈를 예방할 수 있다.
