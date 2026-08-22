# Task & Back Stack

<br>

## 목차

1. Task란 무엇인가
2. Back Stack의 동작 원리
3. launchMode 이해하기 (standard/singleTop/singleTask/singleInstance)
4. Intent Flags로 동작 제어하기
5. taskAffinity와 여러 Task 구성
6. allowTaskReparenting과 Task 재배치
7. 최근 앱 목록(Recents)과 Task 표시 방식
8. singleInstancePerTask (Android 12+)
9. 여러 Activity를 백스택으로 함께 실행하기 (TaskStackBuilder)
10. Deep Link/알림 진입 시 백스택 구성
11. 멀티 윈도우/폼팩터 환경에서의 Task
12. Task와 프로세스, 메모리 관계
13. Task/Back Stack 디버깅하기 (adb, Logcat)
14. 주의사항과 자주 하는 실수
15. 정리

<br>

## 1. Task란 무엇인가

Task는 사용자가 특정 목표를 수행하기 위해 이동하는 **Activity들의 묶음**이다. 안드로이드는 여러 앱의 Activity가 뒤섞여 실행되더라도, 사용자가 "하나의 작업 흐름"으로 인식할 수 있도록 이 Activity들을 Task라는 논리적 단위로 스택처럼 관리한다.

예를 들어 이메일 앱에서 받은 편지함 → 메일 상세 → 첨부 이미지 뷰어까지 이동했다면, 이 세 Activity는 하나의 Task 안에 순서대로 쌓여 있는 상태다. 뒤로가기를 누르면 이 스택에서 가장 위에 있는 Activity부터 하나씩 제거되며 이전 화면으로 돌아간다.

```kotlin
// InboxActivity -> MailDetailActivity -> ImageViewerActivity로 이동하면
// Task의 Back Stack은 아래에서 위로 이렇게 쌓인다
// [InboxActivity, MailDetailActivity, ImageViewerActivity]  <- ImageViewerActivity가 화면에 보임
```

| 개념 | 설명 |
|---|---|
| Task | 사용자 목표 수행을 위한 Activity들의 논리적 묶음 |
| Back Stack | Task 내부에서 Activity가 쌓이는 자료구조 (LIFO) |
| Root Activity | Task의 가장 아래에 있는, 그 Task를 시작한 Activity |
| Foreground Task | 현재 화면에 보이는 Task |

**실전 팁**
- Task는 하나의 앱과 1:1로 대응되지 않는다. 여러 앱의 Activity가 같은 Task 안에 섞여 들어갈 수도 있고(예: 공유 시트를 거쳐 다른 앱 화면으로 진입), 한 앱이 여러 Task를 가질 수도 있다.
- "화면 전환"을 설계할 때는 단순히 "이 Activity를 띄운다"가 아니라 "이 Activity가 어떤 Task의 Back Stack에 어떻게 쌓이는가"까지 함께 고려해야 예상치 못한 뒤로가기 동작을 방지할 수 있다.

Task는 언제 새로 만들어질까? 사용자가 런처에서 앱 아이콘을 탭했을 때, 해당 앱의 Task가 시스템에 이미 존재하지 않으면 새 Task가 생성되고 그 앱의 런처 Activity가 Root Activity로 들어간다. 이미 Task가 존재한다면(예: 홈 버튼으로 나갔다가 다시 아이콘을 탭한 경우) 새 Task를 만들지 않고 기존 Task를 그대로 포그라운드로 가져온다. 이때 Back Stack에 쌓여있던 화면들은 그대로 유지되므로, 사용자는 앱을 나가기 전 보고 있던 화면으로 돌아오게 된다.

```kotlin
// 홈 버튼(혹은 최근 앱에서 다른 앱 선택)으로 나가는 것은 Task를 없애지 않는다
// onPause() -> onStop() 순으로 호출되며 Activity는 Back Stack에 그대로 남아있음
override fun onStop() {
    super.onStop()
    Log.d("Task", "화면이 가려졌지만 Task와 Back Stack은 유지됨")
}
```

| 진입 방식 | Task 생성 여부 |
|---|---|
| 런처 아이콘 최초 탭 | 새 Task 생성, Root Activity로 런처 Activity 시작 |
| 홈 버튼 후 아이콘 재탭 | 기존 Task를 그대로 포그라운드로 전환 (Back Stack 유지) |
| 최근 앱 목록에서 카드 선택 | 해당 Task를 그대로 포그라운드로 전환 |
| 다른 앱에서 FLAG_ACTIVITY_NEW_TASK로 진입 | affinity가 일치하는 기존 Task가 있으면 재사용, 없으면 새로 생성 |

<br>

## 2. Back Stack의 동작 원리

Back Stack은 후입선출(LIFO, Last In First Out) 구조로 동작한다. `startActivity()`를 호출하면 새 Activity가 스택의 맨 위에 쌓이고(push), 뒤로가기 버튼을 누르거나 `finish()`를 호출하면 맨 위의 Activity가 스택에서 제거된다(pop).

```kotlin
class InboxActivity : ComponentActivity() {
    private fun openMailDetail(mailId: Long) {
        val intent = Intent(this, MailDetailActivity::class.java).apply {
            putExtra("mailId", mailId)
        }
        startActivity(intent) // Back Stack에 MailDetailActivity가 push됨
    }
}

class MailDetailActivity : ComponentActivity() {
    private fun close() {
        finish() // Back Stack에서 MailDetailActivity가 pop됨, InboxActivity가 다시 보임
    }
}
```

스택의 맨 위에 있는 Activity만 실행(Running) 상태이고, 그 아래에 가려진 Activity들은 모두 정지(Stopped) 상태로 메모리에 유지된다. 시스템 메모리가 부족해지면 스택 아래쪽의 정지된 Activity부터 시스템이 강제로 종료할 수 있으며, 이 경우 사용자가 다시 그 화면으로 돌아왔을 때 `onCreate()`가 `savedInstanceState`와 함께 재호출된다.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // 시스템에 의해 재생성된 경우 savedInstanceState가 null이 아님
    val restoredScrollPosition = savedInstanceState?.getInt("scrollY") ?: 0
}

override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putInt("scrollY", currentScrollY)
}
```

| 스택 위치 | 상태 | 설명 |
|---|---|---|
| 맨 위 (Top) | Running/Resumed | 사용자와 직접 상호작용하는 화면 |
| 중간 | Stopped | 화면에 보이지 않지만 메모리에는 유지됨 |
| 시스템 메모리 부족 시 | Destroyed | 스택 아래쪽부터 시스템이 강제 종료 가능, savedInstanceState로 복원 대비 필요 |

**실전 팁**
- 뒤로가기로 복귀하는 화면이 이전 상태를 잃어버리는 버그의 상당수는 `onSaveInstanceState()`를 제대로 구현하지 않았기 때문이다. ViewModel과 `SavedStateHandle`을 함께 쓰면 이 문제를 훨씬 안정적으로 다룰 수 있다.
- Back Stack에 Activity가 몇 개나 쌓여 있는지는 코드에서 직접 조회할 수 없다. 설계 시점부터 "이 흐름에서 뒤로가기를 몇 번 눌러야 하는가"를 스스로 그려보는 습관이 필요하다.

<br>

## 3. launchMode 이해하기 (standard/singleTop/singleTask/singleInstance)

`launchMode`는 매니페스트에서 Activity마다 지정하는 속성으로, 새 Intent가 들어왔을 때 기존 인스턴스를 재사용할지, 새 인스턴스를 만들지를 결정한다.

```xml
<activity
    android:name=".ui.DetailActivity"
    android:launchMode="singleTop" />
```

```kotlin
// standard(기본값): 항상 새 인스턴스를 만들어 스택 맨 위에 쌓음
// 같은 Activity를 3번 startActivity() 하면 스택에 3개의 인스턴스가 쌓인다

// singleTop: 스택의 맨 위가 이미 같은 Activity라면 재사용, onNewIntent() 호출
class SearchResultActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val query = intent.getStringExtra("query")
        updateSearchResults(query)
    }
}
```

| launchMode | 동작 | 대표 사용처 |
|---|---|---|
| standard | 항상 새 인스턴스 생성, 여러 개 중첩 가능 | 기본값, 대부분의 화면 |
| singleTop | 스택 맨 위에 이미 있으면 재사용(onNewIntent), 아니면 새로 생성 | 검색 결과 화면, 알림으로 반복 진입하는 화면 |
| singleTask | Task 전체에서 인스턴스가 하나만 존재, 이미 있으면 그 위의 Activity들을 모두 pop하고 재사용 | 앱의 메인/허브 화면 |
| singleInstance | singleTask와 유사하지만 해당 Activity 혼자 별도 Task를 독점, 다른 Activity가 같은 Task에 들어올 수 없음 | 전화 앱의 통화 화면처럼 완전히 독립적인 화면 |

**실전 팁**
- `singleTask`는 "이미 스택에 있으면 그 위의 모든 Activity를 제거하고 맨 위로 끌어올린다"는 동작이 있어, 잘못 사용하면 사용자가 방금 진행하던 화면들이 예고 없이 사라지는 경험을 줄 수 있다. 앱의 허브/메인 화면 정도로 신중하게 제한해서 사용하자.
- `singleInstance`는 거의 쓰이지 않는 특수한 모드다. "이 화면은 절대 다른 화면과 같은 Task에 섞이면 안 된다"는 요구사항이 명확할 때만 고려하자.

`launchMode`는 XML에서 정적으로 고정되는 값이라, 화면마다 상황에 따라 다르게 동작해야 한다면 한계가 있다. 예를 들어 "평소에는 여러 개 쌓여도 되지만, 특정 진입 경로에서만 기존 인스턴스를 재사용하고 싶다"는 요구사항은 `launchMode` 하나로는 표현할 수 없고, 다음 장에서 다루는 Intent Flag를 함께 조합해야 한다.

```kotlin
// Manifest의 launchMode="standard"인 Activity라도, 특정 상황에서만
// singleTop처럼 동작시키고 싶다면 Intent Flag로 그 상황에서만 지정할 수 있다
val intent = Intent(context, SearchResultActivity::class.java).apply {
    putExtra("query", query)
    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
}
context.startActivity(intent)
```

| 결정 방식 | 적용 범위 | 유연성 |
|---|---|---|
| launchMode (매니페스트) | 해당 Activity 전체에 항상 적용 | 상황별 예외를 두기 어려움 |
| Intent Flag (호출 시점) | 그 Intent를 발생시키는 지점에만 적용 | 진입 경로별로 다르게 제어 가능 |

<br>

## 4. Intent Flags로 동작 제어하기

`launchMode`가 매니페스트에서 고정적으로 선언하는 방식이라면, Intent Flag는 `startActivity()`를 호출하는 시점에 동적으로 Task/Back Stack 동작을 제어하는 방법이다.

```kotlin
val intent = Intent(context, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
}
context.startActivity(intent)
```

가장 흔하게 쓰이는 조합은 "로그아웃 후 로그인 화면으로 돌아가면서 기존 스택을 전부 비우는" 시나리오다.

```kotlin
fun navigateToLoginAndClearStack(context: Context) {
    val intent = Intent(context, LoginActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    context.startActivity(intent)
}
```

| Flag | 동작 |
|---|---|
| FLAG_ACTIVITY_NEW_TASK | 새 Task에서 Activity를 시작 (기존 Task가 있으면 그 Task를 앞으로 가져옴) |
| FLAG_ACTIVITY_CLEAR_TOP | 스택에 대상 Activity가 있으면 그 위의 모든 Activity를 pop, singleTask와 유사한 효과 |
| FLAG_ACTIVITY_CLEAR_TASK | Task 전체를 비우고 새로 시작 (NEW_TASK와 함께 사용해야 함) |
| FLAG_ACTIVITY_SINGLE_TOP | singleTop launchMode와 동일한 효과를 Intent 단위로 부여 |
| FLAG_ACTIVITY_NO_HISTORY | 이 Activity가 다른 화면으로 넘어가는 즉시 스택에서 제거됨 (스플래시 화면 등) |

**실전 팁**
- `FLAG_ACTIVITY_CLEAR_TASK`는 반드시 `FLAG_ACTIVITY_NEW_TASK`와 함께 사용해야 한다. 단독으로 사용하면 무시되거나 예상과 다르게 동작할 수 있다.
- Service나 BroadcastReceiver처럼 Activity Context가 아닌 곳에서 `startActivity()`를 호출할 때는 `FLAG_ACTIVITY_NEW_TASK`를 반드시 지정해야 하며, 빠뜨리면 `AndroidRuntimeException`이 발생한다.

<br>

## 5. taskAffinity와 여러 Task 구성

`taskAffinity`는 Activity가 "어떤 Task에 소속되고 싶어하는지"를 나타내는 속성이다. 기본값은 앱의 패키지명이며, 같은 값을 가진 Activity들은 원칙적으로 같은 Task에 모이려는 성향을 가진다.

```xml
<application android:taskAffinity="com.example.app">

    <activity android:name=".MainActivity" /> <!-- 기본 taskAffinity 사용 -->

    <activity
        android:name=".ui.PaymentActivity"
        android:taskAffinity="com.example.app.payment"
        android:launchMode="singleTask" /> <!-- 결제 화면을 별도 Task로 분리 -->
</application>
```

`taskAffinity`가 실제로 새로운 Task 생성으로 이어지려면 `FLAG_ACTIVITY_NEW_TASK` 플래그와 함께 사용되어야 한다. 단순히 `taskAffinity` 값만 다르게 설정한다고 자동으로 별도 Task가 생기지는 않는다.

```kotlin
fun openPaymentFlow(context: Context) {
    val intent = Intent(context, PaymentActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}
```

| 상황 | taskAffinity의 영향 |
|---|---|
| 같은 앱 내에서 일반적으로 화면 전환 | 기본 taskAffinity(패키지명)를 공유하므로 한 Task 안에 계속 쌓임 |
| NEW_TASK 플래그와 함께 다른 taskAffinity Activity 실행 | 해당 affinity를 가진 Task가 없으면 새로 생성, 있으면 그 Task로 전환 |
| allowTaskReparenting="true"인 Activity | 다른 앱에서 실행되었어도, 자신의 taskAffinity를 가진 Task가 포그라운드로 오면 그쪽으로 옮겨감 |

**실전 팁**
- `taskAffinity`를 남용해서 화면마다 제각각 다른 Task로 분리하면, 최근 앱 목록에 하나의 앱이 여러 개의 카드로 나타나는 등 사용자에게 혼란스러운 경험을 줄 수 있다. 정말로 독립된 흐름(예: 별도 프로세스의 위젯 설정 화면, 결제 팝업류)에만 제한적으로 사용하자.
- `taskAffinity=""` (빈 문자열)로 설정하면 그 Activity는 어떤 Task와도 선호도를 갖지 않게 되며, 이는 `singleTask`/`singleInstance`와 조합할 때 특별한 의미를 가지므로 문서를 참고해 신중히 사용해야 한다.

<br>

## 6. allowTaskReparenting과 Task 재배치

`allowTaskReparenting`은 Activity가 실행된 Task와 자신의 `taskAffinity`가 다를 때, 나중에 자신의 taskAffinity를 가진 Task가 포그라운드로 올라오면 그 Task로 옮겨갈 수 있게 허용하는 속성이다.

```xml
<activity
    android:name=".ui.WeatherDetailActivity"
    android:taskAffinity="com.example.weatherapp"
    android:allowTaskReparenting="true" />
```

예를 들어 지도 앱에서 어떤 위치를 탭해 날씨 앱의 `WeatherDetailActivity`가 지도 앱의 Task 안에서 실행되었다고 하자. 이후 사용자가 홈 화면에서 날씨 앱 아이콘을 직접 탭하면, `allowTaskReparenting="true"`가 설정된 `WeatherDetailActivity`는 지도 앱의 Task에서 빠져나와 날씨 앱 고유의 Task로 옮겨가서 표시된다.

```kotlin
// 다른 앱(지도 앱)에서 날씨 상세 화면을 실행하는 코드 예시
val intent = Intent(context, WeatherDetailActivity::class.java).apply {
    putExtra("locationId", locationId)
}
context.startActivity(intent) // 지도 앱의 Task에서 실행되지만, 나중에 재배치 가능
```

| 속성 조합 | 결과 |
|---|---|
| allowTaskReparenting="false" (기본값) | Activity는 처음 실행된 Task에 계속 남아있음 |
| allowTaskReparenting="true" + taskAffinity 지정 | 해당 affinity의 Task가 포그라운드로 오면 그쪽으로 이동 |

**실전 팁**
- 이 속성은 실무에서 자주 쓰이는 것은 아니지만, 여러 앱이 연동되는 SDK나 플러그인 형태의 화면을 제공할 때(다른 앱에 내장되는 위젯성 화면 등) 유용하게 쓰일 수 있다.
- 동작이 다소 직관적이지 않으므로, 도입하기 전에 실제 기기에서 최근 앱 목록과 함께 여러 시나리오를 직접 테스트해보는 것을 권장한다.

`taskAffinity`와 `allowTaskReparenting`을 함께 쓸 때 헷갈리기 쉬운 부분은, 이 재배치가 사용자가 명시적으로 어떤 동작을 하지 않아도 시스템이 알아서 수행한다는 점이다. 즉 개발자가 별도의 코드를 작성하지 않아도, 조건(자신의 taskAffinity를 가진 Task가 포그라운드로 온다)이 충족되면 시스템이 자동으로 Activity를 이동시킨다. 이는 명시적으로 화면 전환을 트리거하는 다른 Task 제어 방식들과 근본적으로 다른 지점이다.

```kotlin
// 재배치는 코드로 트리거하는 것이 아니라, 시스템이 조건 충족 시 자동으로 수행한다.
// 개발자는 매니페스트 선언만으로 이 동작을 활성화할 뿐이다.
```

| 제어 방식 | 트리거 주체 |
|---|---|
| launchMode, Intent Flag | 개발자가 startActivity() 호출 시점에 명시적으로 트리거 |
| allowTaskReparenting | 시스템이 조건 충족 시 자동으로 트리거, 개발자는 선언만 함 |

<br>

## 7. 최근 앱 목록(Recents)과 Task 표시 방식

사용자가 최근 앱 목록(Overview/Recents) 버튼을 누르면, 각 카드는 앱이 아니라 **Task** 단위로 표시된다. 하나의 앱이 여러 Task를 가지고 있다면 여러 카드로 나타날 수 있고, 반대로 여러 앱의 Activity가 하나의 Task에 섞여 있다면 그 Task를 대표하는 하나의 카드로만 보인다.

```xml
<activity
    android:name=".MainActivity"
    android:excludeFromRecents="false"
    android:documentLaunchMode="none" />
```

| 속성 | 설명 |
|---|---|
| excludeFromRecents | true로 설정하면 해당 Task를 최근 앱 목록에서 아예 숨김 |
| documentLaunchMode | "always"/"intoExisting" 등으로 문서 편집기처럼 여러 인스턴스를 개별 카드로 노출 가능 |
| android:label / android:icon (Activity 단위) | Task의 루트 Activity 설정이 최근 앱 카드의 제목/아이콘에 반영됨 |
| finishAndRemoveTask() | 현재 Task 전체를 종료하고 최근 앱 목록에서도 제거 |

```kotlin
// 결제나 인증처럼 민감한 화면을 최근 앱 목록에서 완전히 종료하고 싶을 때
fun finishPaymentTaskCompletely(activity: Activity) {
    activity.finishAndRemoveTask()
}
```

**실전 팁**
- 로그인/결제 등 민감한 정보를 다루는 Task는 로그아웃/결제 완료 시점에 `finishAndRemoveTask()`로 최근 앱 목록에서도 깔끔히 제거하는 것이 보안과 UX 측면에서 바람직하다.
- `excludeFromRecents="true"`는 스플래시 화면이나 매우 짧게 떴다 사라지는 중계용 Activity에 사용하면 최근 앱 목록이 불필요한 카드로 지저분해지는 것을 막을 수 있다.

<br>

## 8. singleInstancePerTask (Android 12+)

Android 12(API 31)부터 새롭게 추가된 `singleInstancePerTask` launchMode는, 기존 `singleTask`와 비슷하게 하나의 Task에는 해당 Activity 인스턴스가 하나만 존재하도록 보장하면서도, **여러 개의 서로 다른 Task**에서 각각 하나씩 존재하는 것은 허용한다.

```xml
<activity
    android:name=".ui.ChatRoomActivity"
    android:launchMode="singleInstancePerTask" />
```

```kotlin
// 채팅방 A를 새 Task로 열고, 채팅방 B도 새 Task로 열면
// 서로 다른 Task에 각각 ChatRoomActivity 인스턴스가 하나씩 존재할 수 있다
val intentA = Intent(context, ChatRoomActivity::class.java).apply {
    putExtra("roomId", "A")
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
}
context.startActivity(intentA)
```

| launchMode | Task당 인스턴스 | 여러 Task에 걸친 인스턴스 |
|---|---|---|
| singleTask | 앱 전체에서 1개만 존재 | 불가능, 하나의 Task로 통합됨 |
| singleInstancePerTask | Task마다 1개까지 허용 | 가능, Task별로 독립적인 인스턴스 유지 |

**실전 팁**
- `singleInstancePerTask`는 폴더블 기기나 대화면에서 같은 화면(예: 채팅방)을 여러 창으로 동시에 띄우는 멀티 인스턴스 시나리오에 적합하다.
- API 31 미만을 지원해야 한다면 이 launchMode는 사용할 수 없으므로, `minSdkVersion`을 확인하고 하위 버전에서는 대체 동작(예: singleTask)을 준비해야 한다.

<br>

## 9. 여러 Activity를 백스택으로 함께 실행하기 (TaskStackBuilder)

딥링크나 알림으로 특정 상세 화면에 바로 진입시키면서도, 뒤로가기를 눌렀을 때 자연스럽게 상위 화면(부모 화면)으로 돌아가도록 하고 싶을 때 `TaskStackBuilder`를 사용한다.

```kotlin
fun buildDetailPendingIntent(context: Context, noteId: Long): PendingIntent {
    val detailIntent = Intent(context, DetailActivity::class.java).apply {
        putExtra("noteId", noteId)
    }

    return TaskStackBuilder.create(context)
        .addNextIntentWithParentStack(detailIntent) // 매니페스트의 parentActivityName을 활용
        .getPendingIntent(
            noteId.toInt(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )!!
}
```

`addNextIntentWithParentStack()`이 부모 화면을 자동으로 찾으려면, 매니페스트에 `parentActivityName`(또는 `<meta-data>` 기반의 구버전 방식)이 선언되어 있어야 한다.

```xml
<activity
    android:name=".ui.DetailActivity"
    android:parentActivityName=".ui.MainActivity" />
```

여러 단계의 화면을 명시적으로 직접 쌓고 싶다면 `addNextIntent()`를 반복 호출할 수도 있다.

```kotlin
val pendingIntent = TaskStackBuilder.create(context)
    .addNextIntent(Intent(context, MainActivity::class.java))
    .addNextIntent(Intent(context, CategoryActivity::class.java))
    .addNextIntent(Intent(context, DetailActivity::class.java))
    .getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE)
```

| 메서드 | 설명 |
|---|---|
| addNextIntentWithParentStack() | parentActivityName 계층을 자동으로 따라가며 백스택 구성 |
| addNextIntent() | 명시적으로 지정한 순서대로 백스택에 Intent 추가 |
| getPendingIntent() | 구성된 백스택을 실행하는 PendingIntent 생성 |
| startActivities() | PendingIntent 없이 즉시 여러 Activity를 백스택으로 바로 실행 |

**실전 팁**
- `TaskStackBuilder`를 쓰지 않고 단순히 `getActivity()`로만 딥링크 PendingIntent를 만들면, 상세 화면에서 뒤로가기를 눌렀을 때 앱이 바로 종료되어 버리는 경험을 줄 수 있다. 딥링크 진입 화면일수록 부모 백스택 구성을 함께 고려하자.
- Jetpack Navigation Component를 사용한다면 `NavDeepLinkBuilder`가 내부적으로 유사한 역할을 대신 해주므로, 직접 `TaskStackBuilder`를 다룰 일은 줄어든다.

<br>

## 10. Deep Link/알림 진입 시 백스택 구성

Deep Link나 알림을 통한 진입은 일반적인 인앱 네비게이션과 달리, "사용자가 이 화면에 처음 도달했을 때 백스택이 비어있는 상태"라는 특수성이 있다. 이 상태를 그대로 두면 뒤로가기 시 앱이 종료되거나, 예상치 못한 화면으로 이동하게 된다.

```kotlin
class DetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpBackNavigation()
    }

    private fun setUpBackNavigation() {
        onBackPressedDispatcher.addCallback(this) {
            if (isTaskRoot) {
                // 이 Activity가 Task의 유일한 화면(딥링크로 바로 진입한 경우)
                val intent = Intent(this@DetailActivity, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                // 일반적인 인앱 네비게이션으로 도달한 경우, 원래 백스택으로 복귀
                finish()
            }
        }
    }
}
```

`isTaskRoot`는 현재 Activity가 자신이 속한 Task의 가장 아래 Activity인지(즉, 이 화면이 Task의 시작점인지)를 알려주는 프로퍼티로, 딥링크 진입 여부를 판단하는 데 자주 활용된다.

| 진입 경로 | isTaskRoot | 권장 처리 |
|---|---|---|
| 앱 내부에서 순차적으로 진입 | false | 그냥 finish()로 이전 화면 복귀 |
| 딥링크/알림으로 앱이 종료된 상태에서 바로 진입 | true | 인위적으로 부모 화면을 백스택에 추가하거나 TaskStackBuilder 사용 |

**실전 팁**
- `isTaskRoot`만으로 모든 케이스를 처리하려 하지 말고, 가능하면 8~9장에서 다룬 `TaskStackBuilder`나 Navigation Component의 딥링크 기능으로 애초에 올바른 백스택을 구성해서 진입시키는 것이 더 근본적인 해결책이다.
- Navigation Component를 사용한다면 `NavDeepLinkBuilder`나 `<deepLink>` 선언만으로 이 백스택 구성 문제가 대부분 자동으로 해결되므로, 수동 처리는 최소화하는 것이 유지보수에 유리하다.

<br>

## 11. 멀티 윈도우/폼팩터 환경에서의 Task

폴더블, 태블릿, 데스크톱 모드 등 대화면 기기에서는 여러 Task가 동시에 화면에 나뉘어 표시되는 멀티 윈도우(분할 화면, 자유 형식 창) 환경이 흔하다. 이런 환경에서는 Task의 개념이 "화면 전체를 차지하는 하나의 흐름"에서 "여러 창 중 하나"로 확장된다.

```xml
<activity
    android:name=".MainActivity"
    android:resizeableActivity="true"
    android:supportsPictureInPicture="false" />
```

```xml
<!-- 특정 Activity를 항상 새 창(Task)으로 열고 싶을 때 -->
<activity
    android:name=".ui.NoteEditorActivity"
    android:launchMode="singleInstancePerTask" />
```

여러 창을 프로그래밍적으로 다루고 싶을 때는 `ActivityOptions`의 `setLaunchBounds()` 등을 활용해 자유 형식 창의 위치와 크기를 지정할 수 있다.

```kotlin
fun openInNewWindow(activity: Activity, intent: Intent) {
    val options = ActivityOptions.makeBasic().apply {
        launchBounds = Rect(0, 0, 800, 600)
    }
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
    activity.startActivity(intent, options.toBundle())
}
```

| 환경 | Task 동작 특징 |
|---|---|
| 단일 창(폰) | 하나의 Task가 화면 전체를 차지, 전통적인 백스택 모델 그대로 적용 |
| 분할 화면(Split Screen) | 두 개의 Task가 각각 독립적인 화면 영역과 백스택을 가짐 |
| 자유 형식 창(Freeform, 데스크톱 모드) | 여러 Task가 겹치는 창으로 동시에 존재, singleInstancePerTask가 특히 유용 |

**실전 팁**
- `resizeableActivity`를 명시하지 않으면 targetSdkVersion에 따라 기본 동작이 달라질 수 있으므로, 대화면 지원이 목표라면 명시적으로 선언하고 실제 폴더블/태블릿 기기에서 테스트하자.
- 멀티 윈도우 환경을 고려하지 않고 설계된 `singleTask`/`taskAffinity` 로직은 분할 화면에서 두 창이 서로의 Task를 흡수하거나 예상과 다르게 전환되는 문제를 일으킬 수 있다.

<br>

## 12. Task와 프로세스, 메모리 관계

Task는 프로세스와 1:1로 대응되는 개념이 아니다. 하나의 Task 안에 서로 다른 앱(다른 프로세스)의 Activity가 섞여 있을 수 있고, 반대로 하나의 프로세스가 여러 Task에 걸쳐 Activity를 가지고 있을 수도 있다.

```kotlin
// 예: 우리 앱(Task 소속)에서 카메라 앱의 촬영 화면을 호출하면
// 카메라 앱의 Activity가 우리 앱의 Task 백스택에 잠시 포함될 수 있다
val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
    putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
}
startActivityForResult(intent, REQUEST_CAMERA) // 서로 다른 프로세스, 같은 Task
```

시스템 메모리가 부족해지면 안드로이드는 프로세스 우선순위(현재 보이는 Task의 프로세스 > 백그라운드 Task의 프로세스)를 기준으로 프로세스를 종료한다. 이때 Task 자체는 사라지지 않고 시스템에 "빈 껍데기" 상태로 기록이 남아있다가, 사용자가 다시 그 Task로 돌아오면 프로세스를 재생성하고 Activity들을 순서대로 다시 만든다.

```kotlin
// 프로세스가 강제 종료되었다가 재생성되는 경우를 대비한 상태 복원
class DetailActivity : ComponentActivity() {
    private val viewModel: DetailViewModel by viewModels() // SavedStateHandle로 상태 복원 지원

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // viewModel이 SavedStateHandle을 통해 이전 상태를 복원
    }
}
```

| 상황 | 결과 |
|---|---|
| 포그라운드 Task의 프로세스 | 시스템이 가장 마지막까지 유지하려는 우선순위 |
| 백그라운드 Task의 프로세스 | 메모리 부족 시 우선적으로 종료 대상이 됨 |
| 프로세스 종료 후 Task 재진입 | 시스템이 Back Stack 정보를 기반으로 Activity를 순서대로 재생성 |

**실전 팁**
- "왜 백그라운드에 오래 둔 화면이 다시 열면 처음부터 다시 로딩되지?"라는 질문의 답은 대부분 프로세스가 시스템에 의해 종료되었기 때문이다. `ViewModel` + `SavedStateHandle` 조합으로 복원 가능한 상태를 항상 준비해두는 것이 안드로이드 앱 개발의 기본기다.
- Task는 사라지지 않고 프로세스만 사라진다는 개념을 이해하면, "뒤로가기 스택 자체는 유지되는데 왜 화면 내용은 초기화되지"라는 혼란을 줄일 수 있다.

<br>

## 13. Task/Back Stack 디버깅하기 (adb, Logcat)

현재 시스템에 존재하는 Task와 각 Task의 Activity 스택 구조는 `adb`로 직접 확인할 수 있다.

```bash
# 현재 실행 중인 모든 Task와 그 안의 Activity 스택 정보 확인
adb shell dumpsys activity activities
```

출력 결과에서 `Task{...}` 단위로 Task ID와 그 안에 쌓인 Activity 목록, 그리고 `isTaskRoot` 여부 등을 확인할 수 있다. 특정 Task만 필터링해서 보고 싶다면 `grep`을 함께 활용한다.

```bash
adb shell dumpsys activity activities | grep -A 5 "com.example.app"
```

특정 Activity의 실행/종료를 실시간으로 추적하고 싶다면 Logcat에서 `ActivityTaskManager` 태그를 관찰하는 것도 유용하다.

```bash
adb logcat -s ActivityTaskManager:I
```

| 명령어 | 용도 |
|---|---|
| adb shell dumpsys activity activities | 전체 Task/Back Stack 구조 덤프 |
| adb shell dumpsys activity recents | 최근 앱 목록에 등록된 Task 정보 확인 |
| adb logcat -s ActivityTaskManager:I | Activity 시작/종료, Task 전환 로그 실시간 관찰 |
| adb shell am stack list (구버전) | 구형 API 레벨에서 스택 목록 확인 (최신 버전에서는 dumpsys 권장) |

**실전 팁**
- `launchMode`나 Intent Flag 조합을 바꾼 뒤에는 눈으로 화면 전환만 확인하지 말고, `dumpsys activity activities`로 실제 Task/Back Stack 구조가 의도대로 만들어졌는지 직접 확인하는 습관을 들이자.
- 복잡한 딥링크/알림 진입 시나리오를 디버깅할 때는 각 단계마다 `dumpsys` 출력을 캡처해서 비교하면, 어느 지점에서 예상과 다른 Task 구조가 만들어지는지 빠르게 특정할 수 있다.

개발자 옵션의 "액티비티를 유지하지 않음(Don't keep activities)"을 켜두면, 화면이 백그라운드로 전환되는 즉시 시스템이 해당 Activity를 강제로 종료하도록 흉내낼 수 있다. 12장에서 다룬 "프로세스 종료 후 재생성" 시나리오를 매번 기기 메모리가 부족해지길 기다리지 않고도 손쉽게 재현할 수 있어, 상태 복원 로직을 검증하는 데 유용하다.

```bash
# adb로 "액티비티를 유지하지 않음" 옵션을 직접 켜고 끌 수도 있다
adb shell settings put global always_finish_activities 1
adb shell settings put global always_finish_activities 0
```

| 개발자 옵션/설정 | 용도 |
|---|---|
| 액티비티를 유지하지 않음 | 백그라운드 전환 시 Activity를 즉시 종료시켜 상태 복원 로직 검증 |
| 백그라운드 프로세스 제한 | 허용되는 백그라운드 프로세스 개수를 줄여 메모리 부족 상황 재현 |
| adb shell am kill \<package\> | 특정 앱 프로세스를 즉시 종료 (Task는 유지된 채 프로세스만 사라짐) |

`adb shell am kill`은 실제 저사양 기기에서 벌어지는 "메모리 부족으로 인한 프로세스 강제 종료"를 인위적으로 재현하는 가장 간단한 방법이다. 종료 후 최근 앱 목록에서 해당 Task로 복귀했을 때 화면이 자연스럽게 복원되는지 확인하는 것을 QA 체크리스트에 포함시키는 것을 권장한다.

```bash
# Task를 유지한 채 프로세스만 강제 종료 -> 복귀 시 재생성 여부 확인
adb shell am kill com.example.app
```

<br>

## 14. 주의사항과 자주 하는 실수

1. `FLAG_ACTIVITY_CLEAR_TASK`를 `FLAG_ACTIVITY_NEW_TASK` 없이 단독으로 사용해서 의도한 대로 스택이 비워지지 않는 실수를 한다. 두 플래그는 항상 함께 사용해야 한다.
2. `singleTask`를 메인 화면이 아닌 여러 화면에 남용해서, 사용자가 진행 중이던 다른 화면들이 예고 없이 스택에서 사라지는 경험을 만든다.
3. Service나 BroadcastReceiver에서 `startActivity()`를 호출하면서 `FLAG_ACTIVITY_NEW_TASK`를 빠뜨려 `AndroidRuntimeException`을 만나는 경우가 흔하다.
4. `singleTop`으로 설정한 Activity에서 `onNewIntent()`를 구현하지 않아, 재진입 시 화면이 갱신되지 않고 예전 Intent 데이터로 남아있는 버그가 생긴다.
5. 딥링크나 알림으로 상세 화면에 바로 진입시키면서 백스택 구성을 고려하지 않아, 뒤로가기를 누르면 앱이 곧바로 종료되는 경험을 준다.
6. `taskAffinity`를 화면마다 무분별하게 다르게 설정해서, 최근 앱 목록에 같은 앱이 여러 카드로 중복 표시되는 문제를 만든다.
7. 백그라운드에서 프로세스가 종료되었다가 재생성될 수 있다는 것을 고려하지 않고 `onSaveInstanceState()`/`SavedStateHandle` 없이 화면 상태를 메모리 변수에만 저장해서, 복귀 시 상태가 초기화되는 버그를 만든다.
8. 결제, 로그인처럼 민감한 정보를 다루는 Task를 작업 완료 후에도 `finishAndRemoveTask()` 없이 방치해서 최근 앱 목록에 그대로 남아있게 하는 보안 실수를 한다.
9. 멀티 윈도우/폴더블 환경을 고려하지 않고 설계한 `launchMode`/`taskAffinity` 로직이 분할 화면이나 자유 형식 창에서 예상과 다르게 동작하는 것을 배포 이후에야 발견하는 경우가 많다.
10. 화면 전환 코드만 눈으로 검토하고 실제 Task/Back Stack 구조를 `adb shell dumpsys activity activities`로 확인하지 않아, 배포 후에야 예상치 못한 뒤로가기 버그를 발견하는 경우가 흔하다.

<br>

## 15. 정리

Task는 사용자가 인식하는 하나의 작업 흐름을 나타내는 Activity들의 논리적 묶음이며, 그 내부의 Back Stack은 후입선출 구조로 화면 전환과 뒤로가기 동작을 결정한다. `launchMode`(매니페스트에서 정적으로 선언)와 Intent Flag(호출 시점에 동적으로 지정)는 이 스택에 새 Activity를 어떻게 쌓거나 재사용할지를 제어하는 두 가지 축이며, `taskAffinity`와 `allowTaskReparenting`은 여러 Task 사이에서 Activity가 어디에 소속될지를 결정한다. 실무에서 가장 중요한 것은 딥링크나 알림처럼 백스택이 비어있는 상태로 화면에 진입하는 경우를 항상 별도로 고려하는 것과, 프로세스가 시스템에 의해 종료되었다가 재생성될 수 있다는 전제하에 `SavedStateHandle` 등으로 상태 복원을 준비해두는 것이다. `launchMode`나 Intent Flag 조합을 변경했다면 코드 리뷰나 눈으로 보는 것에 그치지 말고 `adb shell dumpsys activity activities`로 실제 Task 구조를 확인하는 습관을 들이면, 배포 후에 발견되는 뒤로가기 관련 버그를 크게 줄일 수 있다.
