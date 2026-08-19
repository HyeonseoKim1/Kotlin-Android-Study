# Deep Link / App Link

<br>

## 목차

1. Deep Link란 무엇인가
2. Deep Link가 필요한 이유
3. Custom Scheme Deep Link 구현하기
4. App Link란 무엇인가 (Deep Link와의 차이)
5. Digital Asset Links로 App Link 검증하기
6. Navigation Component와 Deep Link 연동
7. Intent Filter 상세 옵션
8. 여러 앱에서 같은 링크를 처리할 때 (Disambiguation)
9. Deep Link로 전달된 데이터 처리하기
10. Deep Link 테스트하기 (adb, App Links Assistant)
11. Compose Navigation에서 Deep Link 처리
12. 웹에서 앱으로 전환 UX (Smart App Banner, 조건부 리다이렉트)
13. 주의사항과 자주 하는 실수
14. 정리

<br>

## 1. Deep Link란 무엇인가

Deep Link는 앱의 **특정 화면**으로 바로 진입할 수 있게 해주는 URI 기반의 링크다. 일반적으로 앱을 실행하면 항상 첫 화면(런처 액티비티)이 뜨지만, Deep Link를 사용하면 "상품 상세 화면", "특정 게시글", "결제 완료 화면"처럼 앱 내부의 임의의 지점으로 곧바로 이동시킬 수 있다.

가장 단순한 형태는 커스텀 스킴(custom scheme) 방식이다.

```
myapp://note/detail?id=42
```

이 URI를 시스템이 인식하면 해당 스킴(`myapp://`)을 처리하도록 등록된 앱의 Activity가 실행되고, Activity는 Intent에 담긴 URI를 파싱해서 42번 노트의 상세 화면을 보여준다.

```kotlin
class DetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = intent?.data
        val noteId = uri?.getQueryParameter("id")?.toLongOrNull()
        // noteId를 사용해 상세 화면 렌더링
    }
}
```

| 용어 | 설명 |
|---|---|
| Deep Link | 앱 내부의 특정 화면으로 이동시키는 URI (커스텀 스킴 포함) |
| App Link | `http/https` 스킴을 쓰면서 도메인 소유권을 검증한 안드로이드 공식 Deep Link |
| URI | 링크의 전체 형식, scheme + host + path + query로 구성 |
| Intent Filter | 어떤 URI 패턴을 어떤 Activity가 처리할지 매니페스트에 선언하는 것 |

**실전 팁**
- Deep Link는 "링크를 눌렀을 때 앱의 어느 화면이 열려야 하는가"를 설계하는 문제이지, 단순히 매니페스트에 태그 하나 추가하는 문제가 아니다. 화면 설계 단계에서부터 URI 구조를 함께 고민하는 것이 좋다.
- 커스텀 스킴은 구현이 간단하지만, 뒤에서 다룰 App Link(https)에 비해 보안성과 신뢰도가 떨어진다는 점을 알고 선택해야 한다.

<br>

## 2. Deep Link가 필요한 이유

Deep Link가 해결하는 문제는 명확하다. 사용자가 웹 검색, 카카오톡 공유, 이메일, 푸시 알림 등 다양한 경로로 특정 콘텐츠에 접근할 때, 매번 앱을 열고 처음부터 원하는 화면까지 직접 찾아가게 만드는 것은 심각한 UX 손실이다.

대표적인 활용 사례는 다음과 같다.

1. **마케팅/공유** - 친구가 공유한 상품 링크를 눌렀을 때 바로 해당 상품 상세 화면으로 이동
2. **푸시 알림** - 알림을 탭했을 때 관련된 화면(주문 상세, 채팅방)으로 바로 이동
3. **이메일/문자 인증** - 비밀번호 재설정 링크를 탭하면 앱의 재설정 화면으로 바로 진입
4. **웹-앱 연동** - 같은 서비스의 웹사이트와 앱이 URL 구조를 공유해서 일관된 딥링크 경험 제공
5. **App Indexing/검색 노출** - Google 검색 결과에서 앱 콘텐츠가 직접 노출되고, 탭하면 앱으로 연결

```kotlin
// 공유 버튼을 눌렀을 때 딥링크를 생성해서 공유하는 예시
fun shareNoteLink(context: Context, noteId: Long) {
    val deepLinkUri = "https://example.com/note/$noteId".toUri()
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "이 메모를 확인해보세요: $deepLinkUri")
    }
    context.startActivity(Intent.createChooser(intent, "공유하기"))
}
```

| 링크 경로 | Deep Link 없을 때 | Deep Link 있을 때 |
|---|---|---|
| 알림 탭 | 항상 앱 첫 화면만 열림 | 관련 상세 화면으로 바로 이동 |
| 친구가 공유한 링크 | 브라우저에서 웹페이지만 보임 | 앱이 설치돼 있으면 앱에서 바로 열림 |
| 이메일 인증 링크 | 웹으로만 처리 가능 | 앱 내 인증 화면으로 매끄럽게 연결 |

**실전 팁**
- Deep Link 설계는 백엔드/웹 팀과 URL 구조(path 규칙)를 맞추는 협업이 필수다. 앱만 따로 설계하면 나중에 웹과 URL이 어긋나는 문제가 생긴다.
- "어떤 화면까지 딥링크로 진입 가능해야 하는가"는 기획 단계에서 목록화해두면 Navigation 그래프 설계가 훨씬 수월해진다.

<br>

## 3. Custom Scheme Deep Link 구현하기

가장 기본적인 형태의 Deep Link로, 매니페스트에 원하는 스킴(scheme)을 선언한 Intent Filter를 추가하는 것으로 시작한다.

```xml
<activity
    android:name=".ui.DetailActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="myapp"
            android:host="note"
            android:pathPrefix="/detail" />
    </intent-filter>
</activity>
```

이 설정으로 `myapp://note/detail?id=42` 같은 URI를 시스템이 인식하면 `DetailActivity`가 실행된다.

```kotlin
class DetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent) // singleTop 등으로 재사용될 때도 처리
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data ?: return
        val id = uri.getQueryParameter("id")?.toLongOrNull() ?: return
        loadNoteDetail(id)
    }
}
```

| 스킴 방식 | 장점 | 단점 |
|---|---|---|
| Custom Scheme (`myapp://`) | 설정이 간단하고 도메인 검증이 필요 없음 | 다른 앱이 동일 스킴을 선점할 수 있음, 신뢰성 낮음, 웹에서 앱 미설치 시 처리 불가 |
| App Link (`https://`) | 도메인 소유권 검증으로 보안성 높음, 미설치 시 자연스럽게 웹으로 폴백 | 서버에 검증 파일 배포 필요, 설정이 상대적으로 복잡 |

**실전 팁**
- 커스텀 스킴은 여러 앱이 같은 이름을 사용할 수 있다는 근본적 한계가 있다. 악성 앱이 동일한 스킴을 등록해 사용자를 가로챌 위험(스킴 하이재킹)이 있으므로, 로그인 토큰이나 민감한 정보를 커스텀 스킴 Deep Link에 담는 것은 피해야 한다.
- `onNewIntent()` 처리를 빼먹으면, 이미 앱이 실행 중인 상태에서 딥링크를 다시 탭했을 때 새 데이터가 반영되지 않는 문제가 생긴다.

<br>

## 4. App Link란 무엇인가 (Deep Link와의 차이)

App Link는 `http`/`https` 스킴을 사용하면서, 안드로이드가 **웹사이트 소유권을 검증**하도록 만든 특별한 형태의 Deep Link다. 검증에 성공하면 시스템은 해당 URL을 열 때 "어떤 앱으로 열까요?" 선택창을 띄우지 않고 곧바로 우리 앱을 실행한다.

```xml
<activity
    android:name=".ui.DetailActivity"
    android:exported="true">
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="https"
            android:host="example.com"
            android:pathPrefix="/note" />
    </intent-filter>
</activity>
```

`android:autoVerify="true"`가 핵심으로, 앱이 설치될 때 시스템이 자동으로 `https://example.com/.well-known/assetlinks.json` 파일을 조회해서 이 앱이 해당 도메인의 소유자가 맞는지 검증한다.

| 구분 | Custom Scheme Deep Link | App Link |
|---|---|---|
| 스킴 | 임의(`myapp://` 등) | `http`/`https`만 가능 |
| 도메인 소유권 검증 | 없음 | `assetlinks.json`으로 검증 |
| 앱 미설치 시 | URI를 처리할 앱이 없어 오류 또는 아무 반응 없음 | 자동으로 브라우저에서 웹페이지가 열림 |
| 여러 앱 설치 시 동작 | 선택 다이얼로그가 뜨거나 스킴을 선점한 앱이 임의로 실행됨 | 검증된 앱이 있으면 선택 없이 바로 실행 |
| 사용자 신뢰도 | 낮음(피싱 위험) | 높음(도메인 소유 증명됨) |

**실전 팁**
- 신규 프로젝트라면 커스텀 스킴보다 App Link를 우선 고려하는 것이 좋다. 웹사이트가 있다면 검증 비용이 크지 않고, 사용자 경험과 보안 모두에서 이점이 크다.
- App Link 검증이 실패하면 시스템은 조용히 일반 Deep Link(선택 다이얼로그가 뜨는 방식)로 폴백한다. 검증 성공 여부를 반드시 실기기에서 확인해야 한다.

두 방식을 하나의 프로젝트에서 함께 쓰는 것도 흔한 패턴이다. 웹과 연동되는 화면은 `https` App Link로, 앱 내부에서만 쓰이는 특수한 딥링크(예: 위젯이나 알림 전용 네비게이션)는 커스텀 스킴으로 분리해서 관리하면 각 방식의 장점을 살릴 수 있다.

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https" android:host="example.com" android:pathPrefix="/note" />
</intent-filter>

<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:scheme="myapp" android:host="internal" />
</intent-filter>
```

이렇게 분리하면 외부에 노출되는 공식 링크는 App Link로 신뢰도를 확보하고, 내부 전용 네비게이션은 굳이 도메인 검증 부담 없이 가볍게 유지할 수 있다.

<br>

## 5. Digital Asset Links로 App Link 검증하기

App Link가 동작하려면 웹 서버의 `.well-known/assetlinks.json` 경로에 앱의 패키지명과 서명 인증서 지문(SHA-256 fingerprint)을 명시한 파일을 배포해야 한다.

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.example.app",
      "sha256_cert_fingerprints": [
        "14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5"
      ]
    }
  }
]
```

SHA-256 지문은 릴리즈 서명 키스토어를 기준으로 아래 명령어로 확인할 수 있다.

```bash
keytool -list -v -keystore release.keystore -alias my_alias
```

이 파일을 웹 서버의 다음 경로에 그대로 배포해야 한다.

```
https://example.com/.well-known/assetlinks.json
```

| 검증 조건 | 설명 |
|---|---|
| HTTPS 필수 | assetlinks.json은 반드시 https로 서빙되어야 함 |
| 리다이렉트 불가 | assetlinks.json 요청이 리다이렉트되면 검증 실패 |
| 패키지명 일치 | 매니페스트의 applicationId와 정확히 일치해야 함 |
| SHA-256 지문 일치 | 디버그/릴리즈 키가 다르면 각각 등록 필요 |

**실전 팁**
- 디버그 빌드와 릴리즈 빌드는 서명 키가 다르므로, 개발 중 테스트를 위해서는 디버그 키의 지문도 `assetlinks.json`에 함께 등록해두는 것이 편하다.
- Play 스토어를 통해 앱을 배포하면 Play 앱 서명(App Signing by Google Play)이 적용되어 실제 서명 키가 개발 시 사용하는 키와 다를 수 있다. Play Console의 "앱 무결성" 메뉴에서 실제 배포 서명 지문을 확인해서 등록해야 한다.

<br>

## 6. Navigation Component와 Deep Link 연동

Jetpack Navigation을 사용하면 Deep Link 처리를 각 destination(화면)에 선언적으로 붙일 수 있어, Activity에서 직접 URI를 파싱하는 보일러플레이트를 줄일 수 있다.

```xml
<!-- nav_graph.xml -->
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    app:startDestination="@id/homeFragment">

    <fragment
        android:id="@+id/detailFragment"
        android:name=".ui.DetailFragment">
        <argument
            android:name="noteId"
            app:argType="long" />
        <deepLink
            app:uri="https://example.com/note/{noteId}" />
        <deepLink
            app:uri="myapp://note/detail/{noteId}" />
    </fragment>
</navigation>
```

매니페스트에는 NavHost를 호스팅하는 Activity에 딱 한 번만 Intent Filter를 등록하면 되고, 세부 라우팅은 Navigation Component가 처리한다.

```xml
<activity
    android:name=".MainActivity"
    android:exported="true">
    <nav-graph android:value="@navigation/nav_graph" />
</activity>
```

Fragment 쪽에서는 딥링크로 전달된 인자를 일반 Navigation 인자처럼 그대로 받아 쓸 수 있다.

```kotlin
class DetailFragment : Fragment() {
    private val args: DetailFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadNoteDetail(args.noteId)
    }
}
```

| 방식 | 딥링크 파싱 위치 | 보일러플레이트 |
|---|---|---|
| 수동 처리 | 각 Activity의 onCreate/onNewIntent | 화면마다 반복됨 |
| Navigation Component | nav_graph.xml에 선언 | Safe Args로 타입 세이프하게 자동 처리 |

**실전 팁**
- `<nav-graph>` 태그를 매니페스트에 등록하면 그래프 안의 모든 `<deepLink>` 선언이 자동으로 Intent Filter로 병합되므로, 화면이 늘어나도 매니페스트를 매번 수정할 필요가 없다.
- `{noteId}` 같은 플레이스홀더는 destination의 `<argument>`와 이름이 정확히 일치해야 자동으로 바인딩된다는 점을 기억하자.

<br>

## 7. Intent Filter 상세 옵션

Intent Filter의 `<data>` 태그는 여러 속성을 조합해서 매우 세밀하게 URL 패턴을 매칭할 수 있다.

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data
        android:scheme="https"
        android:host="example.com"
        android:pathPattern="/note/.*"
        android:port="443" />
</intent-filter>
```

경로 매칭에는 세 가지 방식이 있으며 필요에 따라 선택한다.

```xml
<!-- 정확히 일치 -->
<data android:path="/note/detail" />

<!-- 접두사 일치 -->
<data android:pathPrefix="/note" />

<!-- 정규식 패턴 일치 (* 는 0개 이상의 임의 문자) -->
<data android:pathPattern="/note/.*" />
```

| 속성 | 설명 |
|---|---|
| android:scheme | http, https, myapp 등 URI 스킴 |
| android:host | 도메인 (example.com) |
| android:port | 포트 번호 (생략 시 기본 포트) |
| android:path | 정확히 일치하는 경로 |
| android:pathPrefix | 특정 접두사로 시작하는 경로 |
| android:pathPattern | 정규식 기반 경로 매칭 |
| category BROWSABLE | 브라우저/외부 앱에서 열 수 있게 함, App Link에 필수 |

**실전 팁**
- `category.BROWSABLE`을 빠뜨리면 브라우저나 다른 앱에서 링크를 눌러도 우리 앱이 후보로 뜨지 않는다. Deep Link/App Link Intent Filter에는 반드시 포함하자.
- `pathPattern`의 `.*`는 정규식이 아니라 안드로이드가 정의한 제한적인 와일드카드 문법(`.`과 `*`만 특수문자로 취급)이므로, 복잡한 정규식이 필요하면 앱 내부에서 URI를 받은 뒤 직접 파싱하는 편이 안전하다.

<br>

## 8. 여러 앱에서 같은 링크를 처리할 때 (Disambiguation)

동일한 URL 패턴을 여러 앱이 처리할 수 있도록 등록해두면(예: 유튜브 링크를 유튜브 앱과 크롬 둘 다 열 수 있는 경우), App Link 검증이 되지 않은 상태에서는 시스템이 "어떤 앱으로 열까요?" 선택 다이얼로그(Disambiguation Dialog)를 띄운다.

```kotlin
// 사용자가 링크를 탭했을 때 여러 앱이 후보로 뜨는 상황을 코드로 흉내내면
val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/note/42"))
startActivity(intent) // 매칭되는 앱이 여러 개면 시스템이 선택창을 띄움
```

App Link가 정상적으로 검증(`autoVerify` 성공)되면 이 선택 과정 없이 검증된 앱이 바로 실행된다. 사용자가 설정에서 수동으로 "항상 이 앱으로 열기"를 지정한 경우도 마찬가지로 선택창 없이 바로 실행된다.

| 상황 | 동작 |
|---|---|
| App Link 검증 성공, 후보 앱 1개 | 선택창 없이 바로 실행 |
| App Link 검증 실패 또는 커스텀 스킴, 후보 앱 여러 개 | 선택 다이얼로그 표시 |
| 사용자가 설정에서 기본 앱 지정 | 선택창 없이 지정된 앱 실행 |
| 설정 > 앱 > 기본적으로 열기에서 링크 열기 비활성화 | 브라우저로 폴백 |

**실전 팁**
- 사용자가 설정에서 "이 앱으로 링크 열기"를 껐다면, 이는 사용자의 명시적 선택이므로 앱에서 강제로 되돌리려 하지 말고 설정 화면으로 안내하는 것이 바람직하다 (`Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS`).
- 여러 화면(destination)에 동일한 host를 재사용하는 Intent Filter가 여러 개 있으면 개발자 스스로도 어떤 Activity가 우선되는지 헷갈릴 수 있으므로, path 단위로 명확히 분리해서 설계하자.

<br>

## 9. Deep Link로 전달된 데이터 처리하기

딥링크로 전달되는 데이터는 신뢰할 수 없는 외부 입력이라는 점을 항상 염두에 두고 방어적으로 파싱해야 한다.

```kotlin
fun parseNoteDeepLink(uri: Uri): NoteDeepLinkParams? {
    if (uri.host != "example.com") return null
    val segments = uri.pathSegments // ["note", "42"]
    if (segments.size < 2 || segments[0] != "note") return null

    val id = segments[1].toLongOrNull() ?: return null
    val highlight = uri.getQueryParameter("highlight")?.toBoolean() ?: false

    return NoteDeepLinkParams(id = id, highlight = highlight)
}

data class NoteDeepLinkParams(val id: Long, val highlight: Boolean)
```

앱이 완전히 종료된 상태(cold start), 백그라운드 상태, 이미 실행 중인 상태 각각에서 딥링크 진입 지점이 다르므로 세 가지 경우를 모두 처리해야 한다.

```kotlin
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cold start: 앱이 완전히 종료된 상태에서 딥링크로 시작
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 이미 실행 중인 상태에서 새 딥링크가 들어옴 (launchMode에 따라 다름)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        val params = parseNoteDeepLink(uri) ?: return
        navigateToNoteDetail(params)
    }
}
```

| 앱 상태 | 진입 지점 | 주의사항 |
|---|---|---|
| Cold start (완전 종료) | onCreate()의 intent | savedInstanceState 복원과 순서 주의 |
| Background (일시정지) | onNewIntent() | launchMode/taskAffinity 설정에 따라 호출 여부 다름 |
| Foreground (실행 중) | onNewIntent() | 현재 화면 스택과의 UX 충돌 고려 필요 |

**실전 팁**
- 딥링크 파라미터는 사용자가 URL을 직접 조작해서 임의의 값을 넣을 수 있는 입력이므로, 서버 API 호출 전에 반드시 유효성 검증(타입, 범위, 존재 여부)을 거치자.
- `onNewIntent()`가 호출되려면 Activity의 `launchMode`가 `singleTop`, `singleTask` 등으로 설정되어 있어야 한다. 기본값(`standard`)이면 매번 새 인스턴스가 생성되어 `onCreate()`로만 진입한다.

<br>

## 10. Deep Link 테스트하기 (adb, App Links Assistant)

딥링크는 실제로 링크를 눌러보지 않아도 `adb` 명령어로 손쉽게 테스트할 수 있다.

```bash
# 커스텀 스킴 딥링크 테스트
adb shell am start -W -a android.intent.action.VIEW \
    -d "myapp://note/detail?id=42" com.example.app

# App Link(https) 테스트
adb shell am start -W -a android.intent.action.VIEW \
    -d "https://example.com/note/42" com.example.app
```

App Link 검증 상태는 다음 명령어로 확인할 수 있다.

```bash
adb shell pm get-app-links com.example.app
```

정상적으로 검증되면 아래와 같이 `verified` 상태가 출력된다.

```
com.example.app:
  ID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
  Signatures: [xx:xx:xx:...]
  Domain verification state:
    example.com: verified
```

Android Studio의 **App Links Assistant**(Tools > App Links Assistant)를 사용하면 GUI에서 Intent Filter 추가, `assetlinks.json` 생성, 기기에서 실제 테스트까지 한 번에 진행할 수 있다.

| 도구 | 용도 |
|---|---|
| adb am start -d | 특정 URI로 앱 실행 트리거 |
| adb shell pm get-app-links | 도메인 검증 상태 확인 |
| adb shell pm verify-app-links --re-verify | 검증을 강제로 재시도 |
| App Links Assistant (Android Studio) | Intent Filter/assetlinks.json 생성 및 기기 테스트 GUI |

**실전 팁**
- `verified` 대신 `legacy_failure`나 `none`이 뜬다면 `assetlinks.json`의 패키지명·지문 불일치, HTTPS 리다이렉트, 혹은 파일이 아예 배포되지 않은 경우를 의심하자.
- 검증 상태는 앱 설치 시점에 캐싱되므로, `assetlinks.json`을 수정한 뒤에는 `adb shell pm verify-app-links --re-verify com.example.app`로 강제 재검증하거나 앱을 재설치해야 반영된다.

QA 단계에서는 실제 링크를 문자메시지나 메모 앱에 붙여넣고 탭해보는 방식으로도 검증할 수 있다. 이 방법은 `adb` 명령어로는 재현되지 않는, "브라우저를 거쳐 진입하는 실제 사용자 흐름"까지 확인할 수 있다는 장점이 있다.

```bash
# 여러 빌드 변형(flavor)이 있다면 패키지명을 명확히 지정해서 테스트
adb shell am start -W -a android.intent.action.VIEW \
    -d "https://example.com/note/42" com.example.app.debug
```

| 검증 단계 | 확인 방법 |
|---|---|
| 매니페스트 설정 | Intent Filter의 scheme/host/autoVerify 검토 |
| assetlinks.json 배포 | 브라우저로 직접 접속해 200 응답과 JSON 내용 확인 |
| 도메인 검증 상태 | adb shell pm get-app-links |
| 실제 사용자 흐름 | 메모/메시지 앱에서 링크 탭 → 선택창 없이 바로 앱 진입 확인 |

<br>

## 11. Compose Navigation에서 Deep Link 처리

Compose Navigation에서도 XML 기반 Navigation과 동일한 개념으로 각 composable destination에 `navDeepLink`를 선언할 수 있다.

```kotlin
NavHost(navController = navController, startDestination = "home") {
    composable("home") { HomeScreen() }

    composable(
        route = "note/{noteId}",
        arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
        deepLinks = listOf(
            navDeepLink { uriPattern = "https://example.com/note/{noteId}" },
            navDeepLink { uriPattern = "myapp://note/detail/{noteId}" }
        )
    ) { backStackEntry ->
        val noteId = backStackEntry.arguments?.getLong("noteId") ?: return@composable
        NoteDetailScreen(noteId = noteId)
    }
}
```

매니페스트 설정은 XML 방식과 동일하게, NavHost를 호스팅하는 단일 Activity에 Intent Filter를 등록한다.

```xml
<activity
    android:name=".MainActivity"
    android:exported="true">
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https" android:host="example.com" />
        <data android:scheme="myapp" android:host="note" />
    </intent-filter>
</activity>
```

Activity에서는 `intent`를 `NavController`에 명시적으로 전달해줘야 `onNewIntent()`로 들어온 딥링크도 처리된다.

```kotlin
class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navController.handleDeepLink(intent)
    }
}
```

| Compose Navigation 요소 | 역할 |
|---|---|
| navDeepLink { uriPattern } | 해당 composable이 처리할 URI 패턴 선언 |
| NavType | 경로 인자의 타입(Long, String, Boolean 등) 지정 |
| navController.handleDeepLink() | onNewIntent로 들어온 Intent를 현재 NavController에 반영 |

**실전 팁**
- Compose Navigation에서는 `deepLinks` 리스트에 여러 `navDeepLink`를 등록해서 커스텀 스킴과 App Link를 동시에 지원하는 패턴이 흔하다.
- `navController.handleDeepLink()` 호출을 빼먹으면, 앱이 이미 실행 중인 상태에서 새 딥링크를 탭했을 때 화면이 전혀 바뀌지 않는 문제가 생긴다.

<br>

## 12. 웹에서 앱으로 전환 UX (Smart App Banner, 조건부 리다이렉트)

App Link는 앱이 설치되어 있으면 자동으로 앱을 열지만, **미설치 상태**에서는 자연스럽게 웹페이지로 폴백된다는 것이 큰 장점이다. 이를 활용해 웹-앱 전환 경험을 매끄럽게 만들 수 있다.

```html
<!-- 웹페이지에서 iOS Smart App Banner처럼 안드로이드용 배너를 직접 구현하는 예시 -->
<div id="app-banner" style="display:none">
  <span>앱에서 더 편하게 보기</span>
  <a href="https://example.com/note/42">앱으로 열기</a>
  <a href="https://play.google.com/store/apps/details?id=com.example.app">설치하기</a>
</div>
<script>
  if (/Android/i.test(navigator.userAgent)) {
    document.getElementById('app-banner').style.display = 'block';
  }
</script>
```

Play 스토어로 유도하면서 설치 후 원래 보려던 콘텐츠로 자연스럽게 이어주고 싶다면 **Play 설치 참조(install referrer)** 를 활용해 딥링크 정보를 설치 후에도 전달할 수 있다.

```kotlin
// Play Install Referrer API로 설치 시점에 전달된 딥링크 정보를 읽는 예시
val referrerClient = InstallReferrerClient.newBuilder(context).build()
referrerClient.startConnection(object : InstallReferrerStateListener {
    override fun onInstallReferrerSetupFinished(responseCode: Int) {
        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
            val response = referrerClient.installReferrer
            val referrerUrl = response.installReferrer // 원래 딥링크 정보가 인코딩되어 있음
            handleDeferredDeepLink(referrerUrl)
        }
        referrerClient.endConnection()
    }
    override fun onInstallReferrerServiceDisconnected() {}
})
```

| 시나리오 | 처리 방식 |
|---|---|
| 앱 설치됨 + App Link 검증됨 | 클릭 즉시 앱의 해당 화면으로 이동 |
| 앱 미설치 | 브라우저로 웹페이지가 열림, 배너로 설치 유도 가능 |
| 미설치 → 설치 → 최초 실행 | Deferred Deep Link (Install Referrer 등)로 원래 의도했던 화면까지 이어줌 |

**실전 팁**
- "미설치 상태에서 링크를 눌렀는데 설치 후 원래 보려던 화면으로 이어지는" 경험(Deferred Deep Link)까지 구현하려면 Install Referrer API나 Firebase Dynamic Links 같은 별도 솔루션이 필요하다는 점을 미리 인지하자.
- Firebase Dynamic Links는 2025년 8월 서비스 종료가 공지되었으므로, 신규로 딥링크 솔루션을 도입한다면 Play Install Referrer API를 직접 활용하거나 다른 대체 서비스를 검토해야 한다.

<br>

## 13. 주의사항과 자주 하는 실수

1. `category.BROWSABLE`을 빠뜨려서 브라우저나 다른 앱에서 링크를 눌러도 우리 앱이 실행되지 않는 경우가 많다. Deep Link/App Link Intent Filter에는 반드시 포함해야 한다.
2. `android:autoVerify="true"`만 설정하고 `assetlinks.json`을 배포하지 않아 App Link 검증이 항상 실패하는 경우가 흔하다. 검증 상태는 반드시 `adb shell pm get-app-links`로 확인하자.
3. 커스텀 스킴 딥링크에 로그인 토큰이나 민감한 개인정보를 그대로 담아 전달해서 스킴 하이재킹에 취약한 구조를 만드는 실수를 한다.
4. `onNewIntent()` 처리를 빠뜨려서, 앱이 이미 실행 중인 상태에서 딥링크를 다시 탭했을 때 아무 반응이 없거나 이전 데이터가 그대로 남아있는 버그가 생긴다.
5. `launchMode`를 고려하지 않고 `onNewIntent()`가 항상 호출될 것이라 가정하는 경우가 많다. `standard` launchMode에서는 새 인스턴스가 생성되어 `onCreate()`로만 진입한다.
6. 딥링크 파라미터를 신뢰할 수 있는 입력으로 착각해 유효성 검증 없이 바로 API를 호출하거나 화면을 렌더링해서, 잘못된 값이나 악의적인 값에 그대로 노출되는 경우가 있다.
7. 디버그 키로 빌드한 앱을 테스트하면서 `assetlinks.json`에는 릴리즈 키 지문만 등록해두고 왜 검증이 안 되는지 헤매는 경우가 많다. 필요하면 디버그 지문도 함께 등록하자.
8. Play 앱 서명(App Signing by Google Play)을 사용하는 경우, 실제 배포 서명과 로컬에서 사용하는 서명이 다르다는 것을 몰라서 배포 후 App Link가 깨지는 경우가 있다.
9. `assetlinks.json` 요청이 HTTPS 리다이렉트(예: http→https, www 유무 리다이렉트)를 거치도록 서버를 구성해서 검증이 실패하는 경우가 흔하다. 리다이렉트 없이 직접 200 응답이 와야 한다.
10. Navigation Component의 `<deepLink>` 플레이스홀더 이름(`{noteId}`)과 destination의 `<argument>` 이름이 일치하지 않아 인자가 바인딩되지 않는 실수를 한다.

<br>

## 14. 정리

Deep Link는 URI를 통해 앱의 특정 화면으로 바로 진입시키는 메커니즘이며, App Link는 그중에서도 `https` 스킴과 `assetlinks.json` 기반 도메인 소유권 검증을 결합해 보안성과 사용자 경험을 모두 확보한 안드로이드의 공식 방식이다. 신규 프로젝트라면 커스텀 스킴보다 App Link를 우선 고려하는 것이 좋으며, 이는 앱 미설치 시 자연스러운 웹 폴백과 선택 다이얼로그 없는 즉시 실행이라는 두 가지 이점을 준다. 구현 시에는 `category.BROWSABLE` 포함 여부, `assetlinks.json` 배포와 검증 상태, `onNewIntent()`와 `launchMode`의 상호작용, 그리고 딥링크로 전달된 데이터를 신뢰할 수 없는 외부 입력으로 취급해 방어적으로 파싱하는 것, 이 네 가지가 실무에서 가장 많이 놓치는 지점이다. Navigation Component(XML/Compose 모두)를 사용하면 Intent Filter 관리와 인자 바인딩을 선언적으로 처리할 수 있어 화면이 늘어나도 유지보수 부담이 크게 줄어든다.
