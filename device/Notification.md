# Notification

목차
1. Notification이란?
2. Notification이 필요한 이유
3. Notification의 기본 구조
4. Notification Channel
5. NotificationChannel 생성과 설정
6. NotificationCompat.Builder 사용 방법
7. Notification 표시 과정
8. PendingIntent를 이용한 클릭 처리
9. Notification 권한과 Android 버전별 변경사항
10. Notification의 중요도와 우선순위
11. 알림의 종류와 표시 방식
12. Notification 업데이트와 취소
13. Foreground Service와 Notification
14. Notification과 Background 작업
15. 알림 그룹화와 Group Notification
16. 실전에서 사용하는 Notification
17. 주의사항과 자주 하는 실수
18. 정리

<br>

## 1. Notification이란?

Notification은 앱이 현재 화면에 떠 있지 않을 때도 사용자에게 정보를 전달할 수 있는 시스템 UI 요소다. 상태 표시줄(Status Bar)에 아이콘으로 표시되고, 사용자가 아래로 끌어내리면 상세 내용을 확인할 수 있는 알림 패널(Notification Drawer)에 나타난다.

```kotlin
val notification = NotificationCompat.Builder(context, CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_notification)
    .setContentTitle("새 메시지")
    .setContentText("현서님이 메시지를 보냈습니다")
    .build()

NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
```

Notification은 단순한 메시지 표시를 넘어, 액션 버튼, 진행률 표시줄, 이미지, 답장 입력창 등 다양한 형태로 확장될 수 있다. 또한 Foreground Service처럼 특정 기능이 정상적으로 동작하기 위한 필수 전제조건이 되기도 한다.

Notification을 다루는 핵심 클래스는 다음과 같다.

- `NotificationManager` / `NotificationManagerCompat`: 알림을 시스템에 게시(post)하거나 취소하는 역할
- `NotificationChannel`: Android 8.0 이상에서 알림을 분류하는 채널
- `NotificationCompat.Builder`: 알림의 내용과 형태를 구성하는 빌더

<br>

## 2. Notification이 필요한 이유

앱이 포그라운드에 있지 않을 때도 사용자와 소통해야 하는 상황은 매우 많다.

- 새 메시지, 새 댓글 같은 실시간 이벤트를 즉시 알려야 할 때
- 다운로드/업로드 진행 상황을 지속적으로 보여줘야 할 때
- 음악 재생, 녹음처럼 백그라운드에서 진행 중인 작업을 사용자에게 인지시켜야 할 때(Foreground Service 필수 요건)
- 예약된 시각에 리마인더를 전달해야 할 때(AlarmManager와 결합)
- 작업 완료, 오류 발생 등 결과를 사후에 전달해야 할 때

Notification이 없다면 사용자는 앱을 직접 열어보기 전까지 무슨 일이 일어나고 있는지 알 수 없다. 특히 Foreground Service는 시스템 정책상 Notification 없이는 아예 실행될 수 없기 때문에, 백그라운드 지속 작업을 구현하려면 Notification 설계가 항상 함께 따라온다.

또한 Notification은 단순히 "알려주는" 역할을 넘어, 앱을 다시 열지 않고도 빠르게 액션을 취할 수 있게 해주는 진입점이기도 하다. 메시지 알림에서 바로 답장을 입력하거나, 다운로드 알림에서 바로 취소 버튼을 누르는 식의 상호작용이 대표적이다.

<br>

## 3. Notification의 기본 구조

Notification은 크게 다음 요소들로 구성된다.

| 구성 요소 | 설명 |
|---|---|
| Small Icon | 상태 표시줄에 표시되는 작은 아이콘, 필수 항목 |
| Title / Text | 알림의 제목과 본문 내용 |
| Large Icon | 알림 패널에서 크게 표시되는 이미지(프로필 사진 등) |
| Content Intent | 알림을 탭했을 때 실행되는 PendingIntent |
| Actions | 알림에 추가되는 버튼(최대 3개 권장) |
| Style | BigText, BigPicture, Inbox, Messaging 등 확장 스타일 |
| Priority/Importance | 알림의 중요도, 소리·진동·표시 방식에 영향 |
| Channel | 알림이 속한 채널(Android 8.0 이상 필수) |

```kotlin
val notification = NotificationCompat.Builder(context, CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_notification)
    .setContentTitle("업로드 완료")
    .setContentText("녹음 파일이 성공적으로 업로드되었습니다")
    .setLargeIcon(bitmap)
    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    .setAutoCancel(true)
    .build()
```

`setAutoCancel(true)`를 설정하면 사용자가 알림을 탭했을 때 자동으로 알림이 사라진다. 이 옵션이 없으면 사용자가 직접 스와이프해서 지워야 한다.

Notification은 결국 하나의 View를 그리기 위한 데이터 명세라고 볼 수 있다. `NotificationCompat.Builder`는 이 명세를 조합해서 최종적으로 시스템이 렌더링할 `Notification` 객체를 만들어내는 역할을 한다.

<br>

## 4. Notification Channel

Android 8.0(API 26)부터 모든 Notification은 반드시 하나의 `NotificationChannel`에 속해야 한다. 채널은 알림을 종류별로 그룹화하여, 사용자가 앱 전체가 아니라 특정 종류의 알림만 선택적으로 끄거나 설정을 변경할 수 있게 해주는 장치다.

예를 들어 메신저 앱이라면 다음과 같이 채널을 나눌 수 있다.

- `messages`: 새 메시지 알림 (중요도 높음, 소리 O)
- `promotions`: 프로모션/광고성 알림 (중요도 낮음, 소리 X)
- `system`: 시스템 공지 알림 (중요도 보통)

채널이 도입되기 전(Android 7.1 이하)에는 앱 단위로만 알림을 켜거나 끌 수 있었지만, 채널 도입 이후에는 사용자가 훨씬 세밀하게 알림을 제어할 수 있게 되었다. 반대로 개발자 입장에서는, 한 번 생성되어 사용자가 설정을 변경한 채널의 중요도/소리 등은 코드로 다시 덮어쓸 수 없다는 제약도 함께 생겼다.

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    val channel = NotificationChannel(
        "messages",
        "새 메시지",
        NotificationManager.IMPORTANCE_HIGH
    )
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)
}
```

<br>

## 5. NotificationChannel 생성과 설정

채널은 앱이 처음 실행될 때(주로 `Application.onCreate()`) 미리 생성해두는 것이 일반적이다. 이미 존재하는 채널 ID로 다시 `createNotificationChannel()`을 호출해도 아무 문제가 없으며, 기존 설정을 덮어쓰지 않는다(사용자가 변경한 설정은 유지됨).

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)

        val recordingChannel = NotificationChannel(
            CHANNEL_RECORDING,
            "녹음 진행 상태",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "녹음이 진행 중일 때 표시되는 알림입니다"
            setShowBadge(false)
        }

        val summaryChannel = NotificationChannel(
            CHANNEL_SUMMARY,
            "요약 완료 알림",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "AI 요약이 완료되면 표시됩니다"
            enableVibration(true)
        }

        manager.createNotificationChannels(listOf(recordingChannel, summaryChannel))
    }

    companion object {
        const val CHANNEL_RECORDING = "channel_recording"
        const val CHANNEL_SUMMARY = "channel_summary"
    }
}
```

채널에는 다음과 같은 세부 옵션을 설정할 수 있다.

- `setShowBadge(boolean)`: 런처 아이콘에 배지(뱃지) 표시 여부
- `enableVibration(boolean)` / `setVibrationPattern(longArray)`: 진동 여부와 패턴
- `setSound(uri, audioAttributes)`: 알림 소리 지정
- `enableLights(boolean)` / `setLightColor(color)`: LED 알림등 색상(지원 기기 한정)
- `setLockscreenVisibility(int)`: 잠금화면에서의 노출 수준(공개/비공개/숨김)

채널을 여러 개로 세분화할수록 사용자 제어권은 커지지만, 너무 잘게 나누면 설정 화면이 복잡해져 오히려 혼란을 줄 수 있으므로 적절한 단위로 묶는 것이 좋다.

<br>

## 6. NotificationCompat.Builder 사용 방법

`NotificationCompat.Builder`는 하위 호환성을 보장하면서 Notification을 구성할 수 있게 해주는 클래스다. 플랫폼 버전에 관계없이 동일한 API로 작성할 수 있어 실무에서는 `Notification.Builder` 대신 거의 항상 이 클래스를 사용한다.

```kotlin
val notification = NotificationCompat.Builder(context, CHANNEL_RECORDING)
    .setSmallIcon(R.drawable.ic_mic)
    .setContentTitle("녹음 중")
    .setContentText("00:32")
    .setContentIntent(contentPendingIntent)
    .setOngoing(true)
    .setOnlyAlertOnce(true)
    .setCategory(NotificationCompat.CATEGORY_SERVICE)
    .addAction(R.drawable.ic_pause, "일시정지", pausePendingIntent)
    .addAction(R.drawable.ic_stop, "중지", stopPendingIntent)
    .build()
```

자주 사용하는 옵션들을 정리하면 다음과 같다.

| 메서드 | 설명 |
|---|---|
| `setSmallIcon()` | 필수. 상태 표시줄 아이콘 |
| `setContentTitle()` / `setContentText()` | 제목과 본문 |
| `setOngoing(true)` | 사용자가 스와이프로 지울 수 없게 설정 |
| `setOnlyAlertOnce(true)` | 알림을 갱신할 때 소리/진동을 반복하지 않음 |
| `setAutoCancel(true)` | 탭 시 자동으로 알림 제거 |
| `setCategory()` | 알림의 목적(서비스, 메시지, 알람 등)을 시스템에 힌트로 제공 |
| `setVisibility()` | 잠금화면 노출 수준 |
| `addAction()` | 액션 버튼 추가 |
| `setStyle()` | BigText, BigPicture 등 확장 스타일 적용 |

진행률 표시가 필요한 다운로드/업로드 알림에는 `setProgress()`를 사용한다.

```kotlin
val notification = NotificationCompat.Builder(context, CHANNEL_ID)
    .setContentTitle("업로드 중")
    .setSmallIcon(R.drawable.ic_upload)
    .setProgress(100, currentProgress, false)
    .setOngoing(true)
    .build()
```

진행률을 알 수 없는 불확정 상태라면 세 번째 인자를 `true`로 설정해 인디케이터를 무한 반복 애니메이션으로 표시할 수 있다.

<br>

## 7. Notification 표시 과정

Notification이 실제로 화면에 표시되기까지의 흐름은 다음과 같다.

1. `NotificationChannel` 생성 (최초 1회, 앱 초기화 시점)
2. `NotificationCompat.Builder`로 알림 내용 구성
3. `NotificationManagerCompat.from(context).notify(id, notification)` 호출
4. 시스템이 채널 설정(중요도, 소리 등)을 반영해 실제 알림을 게시
5. 사용자가 상태 표시줄 또는 알림 패널에서 알림을 확인

```kotlin
fun showSummaryCompleteNotification(context: Context, title: String) {
    val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
        .setSmallIcon(R.drawable.ic_check)
        .setContentTitle("요약 완료")
        .setContentText(title)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, notification)
}
```

여기서 `id` 값은 알림을 식별하는 고유 키 역할을 한다. 같은 `id`로 다시 `notify()`를 호출하면 새 알림이 추가되는 것이 아니라 기존 알림이 갱신된다. 서로 다른 알림을 동시에 여러 개 표시하려면 각기 다른 `id`를 사용해야 한다.

Android 13(API 33)부터는 `POST_NOTIFICATIONS` 런타임 권한이 없으면 이 `notify()` 호출 자체가 무시되고 알림이 표시되지 않는다는 점을 반드시 기억해야 한다.

<br>

## 8. PendingIntent를 이용한 클릭 처리

Notification을 탭했을 때 특정 화면으로 이동시키거나 특정 동작을 트리거하려면 `PendingIntent`를 `setContentIntent()`에 연결해야 한다.

```kotlin
val intent = Intent(context, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    putExtra(EXTRA_VOICE_NOTE_ID, voiceNoteId)
}

val contentPendingIntent = PendingIntent.getActivity(
    context,
    voiceNoteId,
    intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
    .setSmallIcon(R.drawable.ic_check)
    .setContentTitle("요약 완료")
    .setContentIntent(contentPendingIntent)
    .setAutoCancel(true)
    .build()
```

액션 버튼에도 각각 독립적인 `PendingIntent`를 연결할 수 있다. 예를 들어 녹음 알림에 "중지" 버튼을 추가하면, 해당 버튼은 Service로 향하는 `PendingIntent`를 통해 실제 중지 로직을 실행시킨다.

```kotlin
val stopIntent = Intent(context, RecordingService::class.java).apply {
    action = RecordingService.ACTION_STOP
}
val stopPendingIntent = PendingIntent.getService(
    context, 0, stopIntent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
```

여러 단계를 거쳐 특정 화면으로 이동해야 하는 경우(딥링크, 백스택 복원)에는 `TaskStackBuilder`를 사용하면 뒤로가기 버튼을 눌렀을 때 자연스럽게 이전 화면으로 이동하는 백스택을 구성할 수 있다.

```kotlin
val detailIntent = Intent(context, VoiceNoteDetailActivity::class.java).apply {
    putExtra(EXTRA_VOICE_NOTE_ID, voiceNoteId)
}

val pendingIntent = TaskStackBuilder.create(context).run {
    addNextIntentWithParentStack(detailIntent)
    getPendingIntent(voiceNoteId, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
```

<br>

## 9. Notification 권한과 Android 버전별 변경사항

Notification과 관련된 정책은 버전마다 크게 달라져 왔다.

Android 8.0 (API 26)
- `NotificationChannel` 도입, 채널 없이는 알림 게시 불가

Android 10 (API 29)
- 백그라운드 상태에서 알림을 탭했을 때의 Activity 실행 정책이 강화됨

Android 11 (API 30)
- 대화형 알림(`MessagingStyle`)에 대한 UI 개선, 알림 채널 그룹 기능 강화

Android 12 (API 31)
- Notification 디자인 가이드라인 변경(Material You), 커스텀 알림 레이아웃에 대한 제약 강화
- `PendingIntent`의 `FLAG_IMMUTABLE`/`FLAG_MUTABLE` 명시 필수화

Android 13 (API 33)
- `POST_NOTIFICATIONS`가 런타임 권한으로 전환됨. 사용자 동의 없이는 어떤 알림도 표시되지 않음
- 앱이 처음 알림을 게시하려는 시점 근처에 시스템이 권한 요청 다이얼로그를 유도하는 것이 권장됨

```kotlin
val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted ->
    if (!isGranted) {
        // 알림을 받을 수 없다는 안내 UI 표시
    }
}

fun requestNotificationPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
```

매니페스트에도 권한 선언이 필요하다.

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

권한이 거부된 상태에서도 앱은 정상적으로 동작해야 하며, 알림이 필요한 핵심 기능(녹음 진행 표시 등)이라면 왜 권한이 필요한지 설명하는 UX(Rationale)를 함께 설계하는 것이 바람직하다.

<br>

## 10. Notification의 중요도와 우선순위

중요도(Importance)는 채널 단위로 설정되며, 알림이 얼마나 눈에 띄게 표시될지를 결정한다.

| Importance | 동작 |
|---|---|
| `IMPORTANCE_NONE` | 알림이 전혀 표시되지 않음 |
| `IMPORTANCE_MIN` | 상태 표시줄에 아이콘 없이 조용히 표시, 알림 패널 하단에만 노출 |
| `IMPORTANCE_LOW` | 소리 없이 표시, 상태 표시줄 아이콘 노출 |
| `IMPORTANCE_DEFAULT` | 소리와 함께 표시 |
| `IMPORTANCE_HIGH` | 소리와 함께 화면 상단에 헤드업(Heads-up) 알림으로 표시 |

```kotlin
val channel = NotificationChannel(
    CHANNEL_MESSAGES,
    "메시지",
    NotificationManager.IMPORTANCE_HIGH
)
```

과거(Android 7.1 이하)에는 `NotificationCompat.Builder.setPriority()`로 알림 단위의 우선순위를 지정했지만, 채널이 도입된 이후에는 채널의 Importance가 우선 적용된다. 하위 호환을 위해 `setPriority()`도 함께 설정해두는 것이 일반적이다.

```kotlin
.setPriority(NotificationCompat.PRIORITY_HIGH) // API 26 미만 기기 대응용
```

Foreground Service의 Notification은 대부분 `IMPORTANCE_LOW`를 사용한다. 녹음이나 재생처럼 지속적으로 갱신되는 알림에 매번 소리가 울리면 사용자 경험이 나빠지기 때문이다.

<br>

## 11. 알림의 종류와 표시 방식

기본 알림 외에도 `NotificationCompat.Style`을 통해 다양한 형태의 알림을 구성할 수 있다.

BigTextStyle: 긴 텍스트를 확장해서 보여줄 때 사용한다.

```kotlin
.setStyle(
    NotificationCompat.BigTextStyle()
        .bigText("오늘 회의에서 논의된 주요 내용은 다음과 같습니다...")
)
```

BigPictureStyle: 이미지를 크게 보여줄 때 사용한다.

```kotlin
.setStyle(
    NotificationCompat.BigPictureStyle()
        .bigPicture(bitmap)
)
```

InboxStyle: 여러 줄의 짧은 항목을 나열할 때 사용한다(다수의 새 메시지 요약 등).

```kotlin
.setStyle(
    NotificationCompat.InboxStyle()
        .addLine("현서: 회의 자료 확인했어요")
        .addLine("민수: 네 확인했습니다")
        .setSummaryText("+3개의 새 메시지")
)
```

MessagingStyle: 채팅 형태의 대화형 알림으로, 답장 입력 액션과 자연스럽게 결합된다.

```kotlin
.setStyle(
    NotificationCompat.MessagingStyle(myself)
        .addMessage("회의 자료 보냈어요", timestamp, sender)
)
```

이 외에도 미디어 재생 컨트롤에 특화된 `MediaStyle`(`androidx.media` 패키지 제공), 진행률 바를 기본으로 포함하는 형태 등 목적에 맞는 스타일을 선택해서 사용할 수 있다. 스타일을 지정하지 않으면 기본적으로 한 줄 요약(`ContentText`)만 표시되는 축약형 알림이 된다.

<br>

## 12. Notification 업데이트와 취소

동일한 `id`로 `notify()`를 다시 호출하면 기존 알림이 새 내용으로 갱신된다. 진행률 알림에서 흔히 사용하는 패턴이다.

```kotlin
fun updateUploadProgress(context: Context, progress: Int) {
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle("업로드 중")
        .setSmallIcon(R.drawable.ic_upload)
        .setProgress(100, progress, false)
        .setOngoing(true)
        .build()

    NotificationManagerCompat.from(context).notify(UPLOAD_NOTIFICATION_ID, notification)
}

fun completeUploadNotification(context: Context) {
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle("업로드 완료")
        .setSmallIcon(R.drawable.ic_check)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(UPLOAD_NOTIFICATION_ID, notification)
}
```

알림을 취소하는 방법은 세 가지가 있다.

- `NotificationManagerCompat.cancel(id)`: 특정 알림 하나만 제거
- `NotificationManagerCompat.cancelAll()`: 앱이 게시한 모든 알림 제거
- 사용자가 직접 스와이프하여 제거(`setAutoCancel(true)`이거나 `setOngoing(false)`인 경우)

```kotlin
NotificationManagerCompat.from(context).cancel(UPLOAD_NOTIFICATION_ID)
```

`setOngoing(true)`로 설정된 알림(주로 Foreground Service의 알림)은 사용자가 스와이프로 지울 수 없으며, 코드에서 명시적으로 `cancel()`을 호출하거나 `stopForeground(STOP_FOREGROUND_REMOVE)`를 호출해야 사라진다.

<br>

## 13. Foreground Service와 Notification

Foreground Service는 실행되는 동안 반드시 Notification을 함께 표시해야 하는 대표적인 사례다. Service 내부에서 `startForeground()`를 호출할 때 Notification 객체를 함께 전달한다.

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val notification = NotificationCompat.Builder(this, CHANNEL_RECORDING)
        .setSmallIcon(R.drawable.ic_mic)
        .setContentTitle("녹음 중")
        .setOngoing(true)
        .build()

    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    return START_STICKY
}
```

이 Notification은 Service가 살아있는 한 상태 표시줄에서 계속 노출되며, 사용자가 스와이프로 지울 수 없다. Service 내부에서 진행 상황이 바뀔 때마다(녹음 시간 경과 등) 같은 `NOTIFICATION_ID`로 `NotificationManager.notify()`를 호출해 갱신한다.

```kotlin
private fun updateRecordingNotification(elapsedSeconds: Int) {
    val notification = NotificationCompat.Builder(this, CHANNEL_RECORDING)
        .setSmallIcon(R.drawable.ic_mic)
        .setContentTitle("녹음 중")
        .setContentText(formatElapsedTime(elapsedSeconds))
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .build()

    NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
}
```

여기서 `setOnlyAlertOnce(true)`를 빼먹으면, 갱신할 때마다 알림 소리나 진동이 반복될 수 있다. 1초마다 갱신되는 타이머 알림에서 매번 소리가 울리면 사용자 경험이 크게 나빠지므로 이 옵션은 실질적으로 필수에 가깝다.

Service 종료 시에는 `stopForeground(STOP_FOREGROUND_REMOVE)`로 Notification을 완전히 제거하거나, `STOP_FOREGROUND_DETACH`로 Notification만 남기고 Foreground 상태만 해제할 수 있다.

<br>

## 14. Notification과 Background 작업

Notification 자체는 UI 표시 수단일 뿐, 백그라운드 작업의 실행을 보장해주지는 않는다. 즉 "Notification을 띄웠다"는 사실이 "작업이 계속 실행된다"는 것을 의미하지 않는다. 실제 작업의 지속성은 Foreground Service, WorkManager, AlarmManager 같은 다른 컴포넌트가 담당하고, Notification은 그 상태를 사용자에게 보여주는 역할만 한다.

WorkManager로 실행되는 작업도 `setForeground()`를 통해 Notification과 결합할 수 있다.

```kotlin
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo("동기화 중"))
        // 동기화 로직
        return Result.success()
    }

    private fun createForegroundInfo(text: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(text)
            .setSmallIcon(R.drawable.ic_sync)
            .build()
        return ForegroundInfo(SYNC_NOTIFICATION_ID, notification)
    }
}
```

AlarmManager로 예약된 작업이 실행될 때도, `BroadcastReceiver`에서 즉시 Notification을 게시하는 패턴이 흔하다.

```kotlin
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(title)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(title.hashCode(), notification)
    }
}
```

즉 Notification, Foreground Service, WorkManager, AlarmManager는 각자 역할이 다르지만 실전에서는 서로 긴밀하게 조합되어 하나의 기능을 완성한다.

<br>

## 15. 알림 그룹화와 Group Notification

같은 종류의 알림이 여러 개 쌓이면, 상태 표시줄이 지저분해지고 사용자가 일일이 확인하기 번거로워진다. Android는 `setGroup()`을 통해 관련된 알림들을 하나의 그룹으로 묶어 보여주는 기능을 제공한다.

```kotlin
val GROUP_KEY_MESSAGES = "group_messages"

fun showMessageNotification(context: Context, sender: String, message: String, id: Int) {
    val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
        .setSmallIcon(R.drawable.ic_message)
        .setContentTitle(sender)
        .setContentText(message)
        .setGroup(GROUP_KEY_MESSAGES)
        .build()

    NotificationManagerCompat.from(context).notify(id, notification)
}

fun showSummaryNotification(context: Context, count: Int) {
    val summaryNotification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
        .setSmallIcon(R.drawable.ic_message)
        .setContentTitle("$count 개의 새 메시지")
        .setStyle(NotificationCompat.InboxStyle().setSummaryText("$count 개의 새 메시지"))
        .setGroup(GROUP_KEY_MESSAGES)
        .setGroupSummary(true)
        .build()

    NotificationManagerCompat.from(context).notify(SUMMARY_ID, summaryNotification)
}
```

`setGroupSummary(true)`로 지정한 알림은 그룹의 대표(요약) 알림이 되어, 개별 알림들이 접혀있을 때 대표로 표시된다. 같은 `GROUP_KEY`를 공유하는 알림들은 시스템이 자동으로 하나의 묶음처럼 표시해준다.

그룹화는 특히 채팅 앱, 이메일 앱처럼 같은 종류의 알림이 짧은 시간에 여러 번 발생하는 경우에 사용자 경험을 크게 개선한다.

<br>

## 16. 실전에서 사용하는 Notification

메신저/채팅 앱
- `MessagingStyle`로 대화형 알림 구성
- 답장 액션(`RemoteInput`)을 추가해 알림에서 바로 답장 가능
- 발신자별로 그룹화하여 알림 패널 정리

녹음/편집 앱
- Foreground Service와 결합된 `IMPORTANCE_LOW` 채널의 지속 알림
- 진행 시간, 파일 크기 등 실시간 정보 갱신
- 완료 시 별도의 `IMPORTANCE_DEFAULT` 채널로 결과 알림

파일 다운로드/업로드
- `setProgress()`로 진행률 표시
- 완료/실패 시 알림 내용과 액션 버튼을 다르게 구성(재시도, 열기 등)

캘린더/리마인더 앱
- AlarmManager와 결합해 정확한 시각에 알림 게시
- 잠금화면에서도 노출되도록 `setVisibility(VISIBILITY_PUBLIC)` 고려

음악 재생 앱
- `MediaStyle`을 사용해 재생/일시정지/다음곡 컨트롤을 알림에 직접 노출
- 잠금화면 컨트롤과 자연스럽게 연동

시스템/보안 알림
- 로그인 시도, 결제 승인 등 민감한 정보는 `setVisibility(VISIBILITY_PRIVATE)`로 잠금화면 노출 제한

<br>

## 17. 주의사항과 자주 하는 실수

1. 채널 미생성 또는 잘못된 채널 ID 사용
   존재하지 않는 채널 ID로 `Builder`를 생성하면 알림이 표시되지 않거나 예외가 발생할 수 있다. 앱 초기화 시점에 모든 채널을 확실히 생성해두어야 한다.

2. `POST_NOTIFICATIONS` 권한 미확인
   Android 13 이상에서 권한 요청 없이 알림을 게시하려 하면 조용히 무시된다. 크래시가 나지 않기 때문에 오히려 디버깅이 까다롭다.

3. `setOnlyAlertOnce()` 누락
   자주 갱신되는 알림(진행률, 타이머)에서 이 옵션을 빼먹으면 갱신될 때마다 소리/진동이 반복되어 사용자에게 불쾌감을 준다.

4. `PendingIntent`의 `FLAG_IMMUTABLE` 누락
   Android 12 이상 타겟에서 플래그를 지정하지 않으면 런타임 예외가 발생한다.

5. 알림 `id` 충돌
   서로 다른 의도의 알림에 같은 `id`를 사용하면 의도치 않게 알림이 덮어써진다. 알림 `id` 체계를 명확히 설계해야 한다(예: 알림 종류별 offset 사용).

6. 과도한 알림 남발
   중요하지 않은 알림을 너무 자주 보내면 사용자가 알림 권한 자체를 꺼버리는 결과로 이어질 수 있다. 채널별로 중요도를 신중하게 설계해야 한다.

7. `setOngoing()` 남용
   지울 수 없는 알림을 꼭 필요하지 않은 곳에도 사용하면 사용자가 답답함을 느낀다. Foreground Service처럼 정말 지속되어야 하는 작업에만 사용해야 한다.

8. 채널 설정을 코드에서 재변경하려는 시도
   한 번 생성된 채널의 중요도나 소리 설정은, 사용자가 직접 시스템 설정에서 변경하지 않는 한 코드에서 다시 덮어쓸 수 없다. 설정을 변경하고 싶다면 새로운 채널 ID로 마이그레이션해야 한다.

9. 잠금화면 노출 수준 미고려
   민감한 정보(결제 금액, 개인 메시지 내용 등)를 `VISIBILITY_PUBLIC`으로 노출하면 프라이버시 문제가 생길 수 있다. 내용에 따라 `VISIBILITY_PRIVATE`나 `VISIBILITY_SECRET`을 고려해야 한다.

10. 테스트 시 실제 기기 알림 설정 확인 누락
    일부 제조사는 자체 알림 정책(팝업 차단, 배터리 최적화)을 적용하기 때문에, 실제 기기에서 채널별 설정과 알림 동작을 함께 테스트해야 한다.

<br>

## 18. 정리

- Notification은 앱이 화면에 없을 때도 사용자와 소통할 수 있게 해주는 핵심 UI 요소이자, Foreground Service나 WorkManager 같은 백그라운드 작업이 사용자에게 투명하게 인지되도록 만드는 필수 장치다. Android 8.0 이후 도입된 채널 시스템은 알림을 세밀하게 분류하고 사용자 제어권을 강화했으며, Android 13부터는 런타임 권한이 추가되어 사용자 동의 없이는 어떤 알림도 표시되지 않는다.

- `NotificationCompat.Builder`를 통해 제목, 본문, 액션, 진행률, 스타일 등을 자유롭게 조합할 수 있고, `PendingIntent`를 통해 알림과 실제 동작(화면 이동, Service 제어)을 연결한다. 특히 Foreground Service의 지속 알림에서는 `setOnlyAlertOnce()`와 `setOngoing()`을 적절히 사용해 사용자 경험을 해치지 않도록 신경 써야 한다.

- 좋은 Notification 설계는 "얼마나 정보를 잘 전달하는가"뿐 아니라 "얼마나 사용자를 방해하지 않는가"의 균형에서 결정된다. 채널을 목적에 맞게 나누고, 중요도를 신중히 설정하며, 불필요한 알림을 최소화하는 것이 실전에서 가장 중요한 원칙이다.
