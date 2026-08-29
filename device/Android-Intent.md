# Android Intent 완벽 가이드

Intent는 Android 컴포넌트(Activity, Service, BroadcastReceiver) 간의 통신을 담당하는 메시징 객체입니다. 처음 Android를 공부한다면 "화면을 전환하거나, 시스템 기능을 호출하거나, 컴포넌트끼리 데이터를 주고받을 때 쓰는 봉투"라고 생각하면 이해하기 쉽습니다. 이 문서는 개념 → Kotlin 코드 예시 → 비교표 → 실전 팁 순서로 Intent의 핵심 내용을 다룹니다.

## 목차

1. Intent란 무엇인가
2. Explicit Intent vs Implicit Intent
3. Intent Filter
4. startActivity와 Activity 실행
5. Intent에 데이터 전달하기 (Extra)
6. startActivityForResult vs Activity Result API
7. 암시적 Intent로 시스템 앱 실행하기
8. Intent Flags (FLAG_ACTIVITY_*)
9. PendingIntent
10. Deep Link와 Intent
11. Broadcast Intent (Broadcast Receiver)
12. Service와 Intent
13. Intent 데이터 전달 시 크기 제한과 Parcelable/Serializable
14. 주의사항과 자주 하는 실수
15. 정리

---

## 1. Intent란 무엇인가

Intent는 "무엇을 하고 싶다"는 의도를 시스템에 전달하는 객체입니다. 예를 들어 "다른 화면으로 이동하고 싶다", "문자를 보내고 싶다", "특정 서비스를 시작하고 싶다" 같은 요청을 Intent 객체에 담아서 시스템에 전달하면, 시스템이 이를 해석해서 알맞은 컴포넌트를 실행해줍니다.

Intent는 크게 두 가지 역할을 합니다.

- **컴포넌트 실행**: Activity를 새로 띄우거나, Service를 시작하거나, BroadcastReceiver에 메시지를 전달
- **데이터 전달**: 화면을 이동하면서 필요한 데이터를 함께 넘김

### 코드 예시

```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Button(onClick = {
                // DetailActivity를 실행하는 가장 단순한 Intent
                val intent = Intent(this@MainActivity, DetailActivity::class.java)
                startActivity(intent)
            }) {
                Text("상세 화면으로 이동")
            }
        }
    }
}
```

### Intent 구성요소

| 구성요소 | 설명 | 예시 |
|---|---|---|
| Component Name | 실행할 컴포넌트를 명시적으로 지정 | `DetailActivity::class.java` |
| Action | 수행할 동작 문자열 | `Intent.ACTION_VIEW` |
| Data (URI) | Action이 처리할 대상 데이터 | `Uri.parse("https://...")` |
| Category | Action의 부가 정보 | `Intent.CATEGORY_DEFAULT` |
| Extras | 추가로 전달할 데이터 번들 | `putExtra("key", value)` |
| Flags | 컴포넌트 실행 방식 제어 | `FLAG_ACTIVITY_NEW_TASK` |

### 실전 팁

- Intent는 "설계도"가 아니라 "요청서"입니다. 실제 실행은 시스템(Android Framework)이 담당합니다.
- Compose 프로젝트에서도 Activity 전환은 여전히 Intent 기반입니다. Compose Navigation은 같은 Activity 내부 화면 전환에만 쓰입니다.
- `LocalContext.current`를 통해 Compose에서 Context를 얻어 Intent를 생성하는 패턴에 익숙해지세요.

---

## 2. Explicit Intent vs Implicit Intent

Intent는 목적지를 지정하는 방식에 따라 두 가지로 나뉩니다.

- **Explicit Intent(명시적 Intent)**: 실행할 컴포넌트의 클래스를 직접 지정. 주로 같은 앱 내부의 Activity 이동에 사용.
- **Implicit Intent(암시적 Intent)**: 어떤 컴포넌트가 실행될지 지정하지 않고, "이런 동작을 할 수 있는 컴포넌트를 찾아달라"고 요청. 주로 다른 앱의 기능(공유, 브라우저 등)을 사용할 때 활용.

### 코드 예시

```kotlin
// Explicit Intent - 같은 앱 내 Activity 실행
val explicitIntent = Intent(context, SettingsActivity::class.java).apply {
    putExtra("user_id", 1234)
}
context.startActivity(explicitIntent)

// Implicit Intent - 웹 브라우저 실행 (어떤 앱이 처리할지 시스템이 결정)
val implicitIntent = Intent(Intent.ACTION_VIEW).apply {
    data = Uri.parse("https://developer.android.com")
}
context.startActivity(implicitIntent)
```

### 비교표

| 특징 | Explicit Intent | Implicit Intent |
|---|---|---|
| 대상 지정 | 클래스명 직접 지정 | Action/Data/Category로 간접 지정 |
| 주 사용처 | 같은 앱 내부 이동 | 다른 앱 기능 호출 |
| 필요 조건 | 없음 | 대상 앱의 Intent Filter 등록 필요 |
| 처리 앱 개수 | 항상 1개(지정한 컴포넌트) | 0개~여러 개 (선택 다이얼로그 표시 가능) |
| 대표 예시 | `Intent(context, XActivity::class.java)` | `Intent(Intent.ACTION_SEND)` |

### 실전 팁

- Implicit Intent를 보내기 전에는 반드시 `resolveActivity()` 또는 `PackageManager.queryIntentActivities()`로 처리 가능한 앱이 있는지 확인하세요. 없으면 크래시가 납니다.
- 같은 앱 내부 화면 전환에 Implicit Intent를 쓰는 것은 안티패턴입니다. 성능과 예측 가능성 모두에서 불리합니다.
- 여러 앱이 동일한 Implicit Intent를 처리할 수 있으면 시스템이 선택 다이얼로그(Chooser)를 띄웁니다.

---

## 3. Intent Filter

Intent Filter는 AndroidManifest.xml에 선언하는 설정으로, "이 컴포넌트는 이런 종류의 Implicit Intent를 처리할 수 있다"고 시스템에 알리는 역할을 합니다. Action, Category, Data를 조합해서 매칭 조건을 정의합니다.

### 코드 예시

```xml
<!-- AndroidManifest.xml -->
<activity
    android:name=".ShareReceiverActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
</activity>
```

```kotlin
// 위 Filter로 들어온 Intent를 처리하는 Activity
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        setContent {
            Text("받은 텍스트: ${sharedText ?: "없음"}")
        }
    }
}
```

### Intent Filter 구성요소

| 태그 | 역할 | 매칭 규칙 |
|---|---|---|
| `<action>` | 처리 가능한 동작 정의 | Intent의 Action과 정확히 일치해야 함 |
| `<category>` | 부가 조건 정의 | Intent의 모든 Category가 Filter에 포함돼야 함 |
| `<data>` | 처리 가능한 데이터 타입/스킴 정의 | mimeType, scheme, host 등으로 세부 매칭 |
| `android:exported` | 외부 앱 접근 허용 여부 | Android 12부터 명시 필수 |

### 실전 팁

- `CATEGORY_DEFAULT`를 빠뜨리면 `startActivity()`로 호출한 Implicit Intent가 매칭되지 않습니다. `startActivity()`는 내부적으로 `CATEGORY_DEFAULT`를 자동 추가하기 때문입니다.
- Android 12(API 31)부터 Intent Filter가 있는 컴포넌트는 `android:exported` 속성을 반드시 명시해야 빌드가 통과됩니다.
- 하나의 컴포넌트에 여러 `<intent-filter>`를 선언할 수 있으며, 각 Filter는 독립적으로 매칭됩니다.

---

## 4. startActivity와 Activity 실행

`startActivity()`는 새로운 Activity를 실행하는 가장 기본적인 방법입니다. 실행된 Activity는 Task(작업 스택)에 쌓이며, 뒤로 가기 버튼으로 이전 Activity로 돌아갈 수 있습니다.

### 코드 예시

```kotlin
class ListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            Button(onClick = {
                val intent = Intent(context, DetailActivity::class.java).apply {
                    putExtra("item_id", 42)
                }
                context.startActivity(intent)
            }) {
                Text("아이템 상세 보기")
            }
        }
    }
}
```

### startActivity 관련 API 변화

| Android 버전 | API Level | 관련 변경 사항 |
|---|---|---|
| Android 5.0 | 21 | `ActivityOptions`로 전환 애니메이션 커스터마이징 지원 강화 |
| Android 8.0 | 26 | 백그라운드에서 Activity 실행 제한 시작 |
| Android 10 | 29 | 백그라운드 Activity 실행 추가 제한 (포그라운드 서비스 예외) |
| Android 12 | 31 | Splash Screen API 도입으로 실행 시 전환 화면 표준화 |
| Android 13 | 33 | 알림 권한 등 런타임 권한과 연계된 Intent 처리 강화 |

### 실전 팁

- `startActivity()`는 반환값이 없습니다. 결과를 받아야 한다면 6번 섹션의 Activity Result API를 사용하세요.
- 화면 전환 애니메이션을 커스텀하려면 `ActivityOptionsCompat.makeCustomAnimation()` 또는 `overridePendingTransition()`(Deprecated, API 34부터 `overrideActivityTransition()` 사용)을 활용하세요.
- Compose 단일 Activity 구조라면 대부분의 화면 전환은 Compose Navigation으로 처리하고, `startActivity()`는 외부 모듈/다른 앱 연동에만 쓰는 경우가 많습니다.

---

## 5. Intent에 데이터 전달하기 (Extra)

Intent는 Key-Value 형태로 데이터를 담아 전달할 수 있습니다. 이를 Extra라고 부르며, 내부적으로 `Bundle`에 저장됩니다.

### 코드 예시

```kotlin
// 데이터 담아서 보내기
val intent = Intent(context, ProfileActivity::class.java).apply {
    putExtra("user_name", "현서")
    putExtra("user_age", 25)
    putExtra("is_premium", true)
    putExtra("scores", intArrayOf(90, 85, 100))
}
context.startActivity(intent)

// ProfileActivity에서 데이터 꺼내기
class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra("user_name") ?: "unknown"
        val age = intent.getIntExtra("user_age", 0)
        val isPremium = intent.getBooleanExtra("is_premium", false)
        val scores = intent.getIntArrayExtra("scores")

        setContent {
            Text("$name / $age / $isPremium / ${scores?.joinToString()}")
        }
    }
}
```

### putExtra 타입별 메서드

| 타입 | 저장 메서드 | 조회 메서드 |
|---|---|---|
| String | `putExtra(key, String)` | `getStringExtra(key)` |
| Int | `putExtra(key, Int)` | `getIntExtra(key, default)` |
| Boolean | `putExtra(key, Boolean)` | `getBooleanExtra(key, default)` |
| IntArray | `putExtra(key, IntArray)` | `getIntArrayExtra(key)` |
| Parcelable | `putExtra(key, Parcelable)` | `getParcelableExtra(key, Class)` (API 33+) |
| ArrayList<String> | `putStringArrayListExtra(key, list)` | `getStringArrayListExtra(key)` |

### 실전 팁

- 기본형(primitive) 타입은 default 값을 지정할 수 있으니, null 대신 명확한 기본값을 넣어 NPE를 예방하세요.
- API 33(Tiramisu)부터 `getParcelableExtra(String)`이 Deprecated 되었고, `getParcelableExtra(String, Class<T>)`를 사용해야 경고가 사라집니다.
- Extra 키 문자열은 오타에 취약하므로, `companion object`에 상수로 선언해서 재사용하는 습관을 들이세요.

---

## 6. startActivityForResult vs Activity Result API

과거에는 결과를 받아야 하는 화면 전환 시 `startActivityForResult()`와 `onActivityResult()`를 사용했지만, 콜백 지옥과 요청 코드 충돌 문제로 인해 Jetpack이 `Activity Result API`를 새로 도입했습니다.

### 코드 예시

```kotlin
// 구버전 방식 (Deprecated) - 참고용
class OldStyleActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    fun launchOld() {
        val intent = Intent(this, PickerActivity::class.java)
        startActivityForResult(intent, 1001)
    }

    @Deprecated("Use Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val picked = data?.getStringExtra("picked_value")
        }
    }
}

// 신버전 방식 - Activity Result API
class NewStyleActivity : ComponentActivity() {
    private val pickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val picked = result.data?.getStringExtra("picked_value")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Button(onClick = {
                pickerLauncher.launch(Intent(this@NewStyleActivity, PickerActivity::class.java))
            }) {
                Text("항목 선택하기")
            }
        }
    }
}
```

### 비교표

| 항목 | startActivityForResult | Activity Result API |
|---|---|---|
| 도입 시점 | 초기 Android부터 | AndroidX Activity 1.2.0 (2020) |
| 상태 | Deprecated | 권장 방식 |
| 요청 코드 관리 | 개발자가 직접 정수 코드 관리 | 자동 관리, 코드 충돌 없음 |
| 결과 처리 위치 | `onActivityResult()` 오버라이드 | 등록 시점의 람다 콜백 |
| 권한 요청 지원 | 별도 API 필요 | `RequestPermission` Contract로 통합 |
| Compose 친화성 | 낮음 | 상대적으로 높음(단, Composable 내부에는 rememberLauncherForActivityResult 사용) |

### 실전 팁

- Compose에서는 `rememberLauncherForActivityResult()`를 사용하면 Composable 함수 내에서 바로 런처를 만들 수 있습니다.
- `registerForActivityResult()`는 반드시 Activity/Fragment의 `onCreate` 시점 이전(멤버 프로퍼티 초기화 시점)에 호출해야 합니다. 클릭 리스너 안에서 호출하면 예외가 발생합니다.
- 사진 선택, 권한 요청 등 자주 쓰는 Contract는 `ActivityResultContracts`에 미리 정의되어 있으니 직접 구현하기 전에 확인하세요.

---

## 7. 암시적 Intent로 시스템 앱 실행하기

Implicit Intent의 가장 실용적인 활용은 시스템에 이미 설치된 다른 앱의 기능(공유, 전화, 지도, 카메라 등)을 호출하는 것입니다.

### 코드 예시

```kotlin
// 텍스트 공유
fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "공유하기"))
}

// 전화 걸기 화면 띄우기 (직접 통화는 CALL_PHONE 권한 필요)
fun dialPhone(context: Context, phoneNumber: String) {
    val intent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:$phoneNumber")
    }
    context.startActivity(intent)
}

// 지도에서 위치 보기
fun showOnMap(context: Context, lat: Double, lng: Double, label: String) {
    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(intent)
}
```

### 자주 쓰이는 Action 상수

| Action | 용도 | 필요 Extra/Data |
|---|---|---|
| `ACTION_VIEW` | URL, 지도, 콘텐츠 열람 | `data = Uri` |
| `ACTION_SEND` | 텍스트/파일 공유 | `type`, `EXTRA_TEXT` 또는 `EXTRA_STREAM` |
| `ACTION_DIAL` | 다이얼러 화면 열기 | `data = tel:번호` |
| `ACTION_CALL` | 바로 전화 걸기(권한 필요) | `data = tel:번호` |
| `ACTION_SENDTO` | 특정 앱으로 메시지 전송(SMS 등) | `data = smsto:번호` |
| `ACTION_GET_CONTENT` | 파일/이미지 선택 | `type` |
| `ACTION_IMAGE_CAPTURE` | 카메라 촬영 | `EXTRA_OUTPUT` (Uri) |

### 실전 팁

- `Intent.createChooser()`를 사용하면 사용자가 매번 처리 앱을 선택하는 다이얼로그가 뜨며, 기본 앱으로 고정되는 것을 방지할 수 있습니다.
- 파일을 공유할 때는 `file://` URI 대신 `FileProvider`로 생성한 `content://` URI를 사용해야 합니다. Android 7.0(API 24)부터 `file://` URI 노출은 `FileUriExposedException`을 발생시킵니다.
- Android 11(API 30)부터 패키지 가시성(Package Visibility) 정책이 도입되어, 특정 Implicit Intent를 처리할 앱이 있는지 확인(`queryIntentActivities`)하려면 Manifest에 `<queries>` 태그로 선언해야 할 수 있습니다.

---

## 8. Intent Flags (FLAG_ACTIVITY_*)

Flag는 Activity가 Task(작업 스택)에서 어떻게 동작할지를 제어합니다. 새 Task를 만들지, 기존 Activity를 재사용할지, 스택을 정리할지 등을 결정합니다.

### 코드 예시

```kotlin
fun goToLoginAndClearStack(context: Context) {
    val intent = Intent(context, LoginActivity::class.java).apply {
        // 새 Task를 만들고, 기존에 쌓여있던 Activity들을 모두 제거
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    context.startActivity(intent)
}

fun goToMainSingleTop(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        // 이미 스택 최상단에 MainActivity가 있으면 재사용
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    context.startActivity(intent)
}
```

### 주요 Flag 정리

| Flag | 동작 |
|---|---|
| `FLAG_ACTIVITY_NEW_TASK` | 새로운 Task에서 Activity 실행 (Application Context에서 startActivity 시 필수) |
| `FLAG_ACTIVITY_CLEAR_TASK` | 대상 Task의 기존 Activity를 모두 제거 후 새로 시작 (NEW_TASK와 함께 사용) |
| `FLAG_ACTIVITY_SINGLE_TOP` | 스택 최상단에 동일 Activity가 있으면 재사용(`onNewIntent` 호출) |
| `FLAG_ACTIVITY_CLEAR_TOP` | 스택에 대상 Activity가 있으면 그 위의 모든 Activity 제거 |
| `FLAG_ACTIVITY_NO_HISTORY` | 실행된 Activity를 백스택에 남기지 않음 |
| `FLAG_ACTIVITY_REORDER_TO_FRONT` | 스택 내 기존 Activity를 최상단으로 이동 |

### 실전 팁

- `Context`가 Activity가 아닌 경우(예: Application Context, Service 내부)에서 `startActivity()`를 호출하면 `FLAG_ACTIVITY_NEW_TASK`를 반드시 추가해야 하며, 안 그러면 `AndroidRuntimeException`이 발생합니다.
- 로그아웃 후 로그인 화면으로 돌아갈 때는 `NEW_TASK + CLEAR_TASK` 조합이 가장 흔하게 쓰입니다.
- Flag는 여러 개를 `or` 연산자로 조합할 수 있으며, 조합에 따라 동작이 크게 달라지므로 공식 문서의 Task 그림을 참고하는 것을 추천합니다.

---

## 9. PendingIntent

PendingIntent는 Intent 자체를 미래에 실행할 수 있도록 "포장"해서 다른 앱(주로 시스템)에 넘기는 객체입니다. 알림 클릭, 알람(AlarmManager), 위젯 클릭 등에서 사용됩니다. 실제 Intent를 실행하는 주체가 우리 앱이 아니라 시스템이기 때문에 필요합니다.

### 코드 예시

```kotlin
fun createNotificationWithAction(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, "channel_id")
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("새 메시지 도착")
        .setContentText("확인하려면 탭하세요")
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(1001, notification)
}
```

### PendingIntent Flag 버전별 변화

| Android 버전 | API Level | 관련 변경 사항 |
|---|---|---|
| ~Android 11 | ~30 | Flag 지정 없이 생성 가능 (Mutable이 기본값) |
| Android 12 | 31 | `FLAG_IMMUTABLE` 또는 `FLAG_MUTABLE` 명시 필수, 미지정 시 크래시 |
| Android 12 이상 권장 | 31+ | 대부분의 경우 `FLAG_IMMUTABLE` 사용 권장 (보안 강화) |
| 예외 케이스 | 31+ | 시스템이 Extra를 채워야 하는 경우(예: 위젯 RemoteViews)만 `FLAG_MUTABLE` 사용 |

### 실전 팁

- Android 12부터는 `FLAG_IMMUTABLE` 또는 `FLAG_MUTABLE`을 지정하지 않으면 앱이 크래시합니다. 특별한 이유가 없다면 `FLAG_IMMUTABLE`을 기본으로 사용하세요.
- `PendingIntent.getActivity()`의 두 번째 인자(requestCode)가 다르면 서로 다른 PendingIntent로 취급됩니다. 여러 알림을 구분해서 갱신하려면 requestCode를 다르게 주세요.
- `FLAG_UPDATE_CURRENT`는 기존 PendingIntent가 있으면 Extra 데이터만 갱신합니다. 매번 새 데이터를 반영해야 하는 알림에 유용합니다.

---

## 10. Deep Link와 Intent

Deep Link는 특정 URI를 통해 앱의 특정 화면으로 바로 진입하게 해주는 기능입니다. 커스텀 스킴(`myapp://`)과 표준 App Links(`https://`, 도메인 검증 포함) 두 가지 방식이 있습니다.

### 코드 예시

```xml
<!-- AndroidManifest.xml -->
<activity android:name=".ProductActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="myapp" android:host="product" />
    </intent-filter>
</activity>
```

```kotlin
class ProductActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // myapp://product/123 형태의 딥링크 처리
        val productId = intent.data?.lastPathSegment
        setContent {
            Text("상품 ID: $productId")
        }
    }
}

// Compose Navigation에서 딥링크 연동
NavHost(navController, startDestination = "home") {
    composable(
        route = "product/{id}",
        deepLinks = listOf(navDeepLink { uriPattern = "myapp://product/{id}" })
    ) { backStackEntry ->
        val id = backStackEntry.arguments?.getString("id")
        ProductScreen(productId = id)
    }
}
```

### 커스텀 스킴 vs App Links 비교

| 항목 | 커스텀 스킴 (`myapp://`) | App Links (`https://`) |
|---|---|---|
| 설정 난이도 | 낮음, Manifest만 수정 | 서버에 `assetlinks.json` 배포 필요 |
| 다른 앱 선점 가능성 | 있음 (동일 스킴 등록 가능) | 없음 (도메인 소유 검증) |
| 웹 폴백 | 기본 지원 안 됨 | 앱 미설치 시 자동으로 웹 페이지 이동 |
| 사용자 경험 | 선택 다이얼로그 뜰 수 있음 | 검증 성공 시 바로 앱 실행 |

### 실전 팁

- 커스텀 스킴은 다른 앱이 동일한 스킴을 선점할 수 있어 보안에 취약합니다. 결제/인증처럼 민감한 딥링크는 App Links를 사용하세요.
- `CATEGORY_BROWSABLE`을 빠뜨리면 브라우저나 다른 앱에서 클릭했을 때 딥링크가 열리지 않을 수 있습니다.
- App Links 검증은 `adb shell pm get-app-links 패키지명` 명령으로 확인할 수 있습니다.

---

## 11. Broadcast Intent (Broadcast Receiver)

Broadcast Intent는 앱 전체 또는 시스템 전체에 이벤트를 방송하는 방식입니다. 배터리 상태 변화, 네트워크 연결 변경, 커스텀 앱 내부 이벤트 등을 알릴 때 사용합니다.

### 코드 예시

```kotlin
// 커스텀 브로드캐스트 전송
fun sendCustomBroadcast(context: Context) {
    val intent = Intent("com.example.app.ACTION_DATA_UPDATED").apply {
        putExtra("updated_count", 5)
        setPackage(context.packageName) // 명시적으로 자신의 앱으로 범위 제한
    }
    context.sendBroadcast(intent)
}

// 동적 등록으로 수신하기
class DataUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val count = intent.getIntExtra("updated_count", 0)
        Log.d("Receiver", "업데이트된 개수: $count")
    }
}

// Activity에서 등록/해제
class MainActivity : ComponentActivity() {
    private val receiver = DataUpdateReceiver()

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("com.example.app.ACTION_DATA_UPDATED")
        ContextCompat.registerReceiver(
            this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(receiver)
    }
}
```

### 버전별 백그라운드 브로드캐스트 제한

| Android 버전 | API Level | 변경 사항 |
|---|---|---|
| Android 7.0 | 24 | 암시적 브로드캐스트 일부(CONNECTIVITY_ACTION 등) Manifest 등록 제한 시작 |
| Android 8.0 | 26 | 대부분의 암시적 브로드캐스트 Manifest 정적 등록 금지, 동적 등록만 허용 |
| Android 12 | 31 | Manifest에 정적 등록 시 `android:exported` 명시 필수 |
| Android 13 | 33 | `registerReceiver()` 호출 시 `RECEIVER_EXPORTED`/`RECEIVER_NOT_EXPORTED` 명시 필수 |

### 실전 팁

- 자신의 앱 내부에서만 쓰는 브로드캐스트라면 `sendBroadcast()`보다 `LocalBroadcastManager`(Deprecated) 대신 `Flow`/`SharedFlow`나 `LiveData`를 쓰는 것이 최신 권장 방식입니다.
- 시스템이 아닌 커스텀 브로드캐스트를 보낼 때는 `setPackage()`로 범위를 제한해 다른 앱이 가로채지 못하게 하세요.
- Android 8.0 이후로는 Manifest에 정적으로 등록해도 대부분 동작하지 않으니, `registerReceiver()`를 코드에서 동적으로 호출하는 방식에 익숙해지세요.

---

## 12. Service와 Intent

Service를 시작하거나 바인딩할 때도 Intent가 사용됩니다. `startService()`/`startForegroundService()`로 시작하는 방식과 `bindService()`로 연결하는 방식이 있습니다.

### 코드 예시

```kotlin
// Foreground Service 시작
class MusicService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            "ACTION_PLAY" -> playMusic()
            "ACTION_PAUSE" -> pauseMusic()
        }
        startForeground(1, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun playMusic() { /* 재생 로직 */ }
    private fun pauseMusic() { /* 일시정지 로직 */ }
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "music_channel")
            .setSmallIcon(R.drawable.ic_music)
            .setContentTitle("음악 재생 중")
            .build()
    }
}

// Service 시작 호출부
fun startMusicService(context: Context) {
    val intent = Intent(context, MusicService::class.java).apply {
        action = "ACTION_PLAY"
    }
    ContextCompat.startForegroundService(context, intent)
}
```

### Service 타입 비교

| 타입 | 시작 방법 | 생명주기 | 대표 사용처 |
|---|---|---|---|
| Foreground Service | `startForegroundService()` + `startForeground()` | 명시적 종료 전까지 유지, 알림 필수 | 음악 재생, 위치 추적 |
| Background Service | `startService()` | Android 8.0부터 백그라운드 실행 제약 큼 | 짧은 백그라운드 작업(권장 안 됨) |
| Bound Service | `bindService()` | 바인딩한 컴포넌트가 모두 해제되면 종료 | 컴포넌트 간 실시간 통신(예: 음악 컨트롤 UI) |

### 실전 팁

- Android 8.0(API 26)부터 백그라운드 상태의 앱은 `startService()`가 제한되므로, 장시간 작업이 필요하면 `startForegroundService()` + 즉시 `startForeground()` 호출 패턴을 지켜야 합니다.
- 짧고 지연 가능한 백그라운드 작업은 Service보다 `WorkManager`를 사용하는 것이 최신 권장 방식입니다.
- `Intent.action`을 활용해 하나의 Service로 여러 명령(재생/정지/다음곡 등)을 라우팅하는 패턴이 흔히 쓰입니다.

---

## 13. Intent 데이터 전달 시 크기 제한과 Parcelable/Serializable

Intent(정확히는 내부 Bundle)를 통해 전달할 수 있는 데이터 크기에는 제한이 있습니다. Binder Transaction Buffer 한도(약 1MB, 프로세스 전체 공유)를 초과하면 `TransactionTooLargeException`이 발생합니다. 또한 객체를 전달하려면 `Parcelable` 또는 `Serializable`을 구현해야 합니다.

### 코드 예시

```kotlin
// Parcelize를 이용한 간단한 Parcelable 구현
@Parcelize
data class UserProfile(
    val id: Int,
    val name: String,
    val email: String
) : Parcelable

// 전달하는 쪽
fun openProfile(context: Context, profile: UserProfile) {
    val intent = Intent(context, ProfileDetailActivity::class.java).apply {
        putExtra("profile", profile)
    }
    context.startActivity(intent)
}

// 받는 쪽 (API 33+ 방식)
class ProfileDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profile = intent.getParcelableExtra("profile", UserProfile::class.java)
        setContent {
            Text("이름: ${profile?.name}")
        }
    }
}
```

### Parcelable vs Serializable 비교

| 항목 | Parcelable | Serializable |
|---|---|---|
| 성능 | 빠름 (Android 전용 최적화) | 느림 (Reflection 사용) |
| 구현 방식 | `@Parcelize`로 자동 생성 (kotlin-parcelize 플러그인) | `Serializable` 인터페이스만 붙이면 됨 |
| 코드량 | 어노테이션 한 줄 | 인터페이스 상속만 필요, 가장 간단 |
| 권장 여부 | Android 앱 내부 전달 시 권장 | 간단한 프로토타입 외에는 비권장 |
| GC 부담 | 낮음 | 임시 객체 다수 생성으로 상대적으로 높음 |

### 실전 팁

- 큰 리스트나 이미지 데이터(Bitmap 등)는 Intent Extra로 직접 넘기지 말고, ID/URI만 전달한 뒤 목적지 화면에서 다시 조회하는 방식을 사용하세요.
- `kotlin-parcelize` 플러그인을 모듈의 `build.gradle.kts`에 `id("kotlin-parcelize")`로 추가해야 `@Parcelize`를 쓸 수 있습니다.
- 멀티모듈 프로젝트에서는 Parcelable 데이터 클래스를 어느 모듈에 둘지(공통 모듈 vs feature 모듈) 미리 설계하는 것이 좋습니다. 순환 의존성을 피하기 위함입니다.

---

## 14. 주의사항과 자주 하는 실수

1. Implicit Intent를 보내기 전에 처리 가능한 앱이 있는지 확인하지 않아 `ActivityNotFoundException`으로 크래시가 나는 경우가 많습니다.
2. Application Context에서 `startActivity()`를 호출하면서 `FLAG_ACTIVITY_NEW_TASK`를 빠뜨려 런타임 예외가 발생하는 실수가 흔합니다.
3. `CATEGORY_DEFAULT`를 Intent Filter에서 빠뜨려 정상적인 Implicit Intent가 매칭되지 않는 문제가 자주 발생합니다.
4. Android 12 이상에서 `PendingIntent` 생성 시 `FLAG_IMMUTABLE`/`FLAG_MUTABLE`을 지정하지 않아 앱이 크래시됩니다.
5. `file://` URI를 그대로 다른 앱에 공유해서 `FileUriExposedException`이 발생하는 경우가 많습니다. `FileProvider`를 사용해야 합니다.
6. Extra 키 문자열을 하드코딩하다가 보내는 쪽과 받는 쪽의 오타로 데이터가 null로 들어오는 실수가 잦습니다.
7. 큰 객체(Bitmap, 대용량 리스트)를 Intent Extra로 직접 전달하다가 `TransactionTooLargeException`을 만나는 경우가 있습니다.
8. Android 12 이상에서 Intent Filter가 있는 컴포넌트에 `android:exported` 속성을 명시하지 않아 빌드가 실패하는 경우가 있습니다.
9. `startActivityForResult()`처럼 Deprecated된 API를 신규 프로젝트에 그대로 사용해 향후 마이그레이션 비용을 키우는 경우가 있습니다.
10. Deep Link 처리 시 `intent.data`가 null일 수 있다는 점을 간과하고 NPE를 유발하는 코드를 작성하는 경우가 많습니다.

## 정리

Intent는 단순히 "화면 전환 도구"가 아니라, Android 컴포넌트 간 통신과 다른 앱과의 연동을 담당하는 핵심 메커니즘입니다. Explicit Intent로 같은 앱 내부를 안전하게 이동하고, Implicit Intent와 Intent Filter로 시스템 및 타 앱의 기능을 활용하며, Flag와 PendingIntent로 Task 스택과 지연 실행을 제어하는 흐름을 이해하면 대부분의 실무 시나리오를 커버할 수 있습니다. 여기에 Deep Link, Broadcast, Service까지 연결 지어 이해하면 Intent가 Android 4대 컴포넌트를 어떻게 하나로 엮는지 큰 그림이 보일 것입니다. 처음에는 각 API의 버전별 변경 사항(특히 Android 8, 12, 13에서의 제약 강화)이 많아 헷갈릴 수 있지만, "왜 이런 제약이 생겼는가(보안, 배터리, 백그라운드 남용 방지)"를 함께 이해하면 단순 암기보다 훨씬 오래 남습니다. 실무에서는 Deprecated API를 그대로 베끼기보다, 이 문서에 정리된 최신 권장 방식(Activity Result API, WorkManager, FileProvider 등)을 기본값으로 삼아 코드를 작성하는 습관을 들이는 것이 좋습니다.
