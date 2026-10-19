# Android UI와 One UI

<br>

# 목차

1. Android UI 개요
2. 시스템 UI 영역과 Edge-to-Edge
3. WindowInsets 다루기
4. Material Design 3 핵심
5. One UI란 무엇인가
6. One UI 버전 흐름
7. One UI 9.0 변경점
8. One UI 디자인 특징
9. 삼성 기기에서 자주 겪는 UI 이슈
10. 화면 크기 대응과 Android 16/17 변화
11. Compose 적응형 레이아웃
12. 폴더블과 Jetpack WindowManager
13. 폰트 스케일과 디스플레이 크기
14. 다크 모드
15. 제스처 내비게이션과 Back
16. One UI 9와 Android 17 보안 기능
17. 개발자가 지켜야 할 보안 관련 원칙
18. 테스트 전략
19. 체크리스트

<br>

# 1. Android UI 개요

Android UI는 크게 세 층으로 나눠서 생각하면 정리가 쉽다.

1. 플랫폼 층: AOSP가 제공하는 View 시스템, Window 관리, SystemUI, Settings
2. 디자인 시스템 층: Google이 정의한 Material Design (현재 Material 3)
3. 제조사 스킨 층: 삼성 One UI, Xiaomi HyperOS, OPPO ColorOS 등

앱 개발자가 직접 만드는 것은 2번 층 위에 올라가는 앱 화면이다. 하지만 실제 사용자는 3번 층까지 포함된 결과물을 본다.
그래서 같은 앱이 Pixel과 Galaxy에서 다르게 보이거나 다르게 동작하는 일이 생긴다.

## 1.1 앱 화면을 구성하는 요소

- Window: 앱 하나가 그려지는 화면 단위
- Activity: Window를 소유하는 진입점 (Compose에서는 보통 ComponentActivity 하나에 setContent)
- 시스템 바: 상태바(Status bar), 내비게이션 바(Navigation bar), 캡션 바(Caption bar, 데스크톱 모드)
- 디스플레이 컷아웃: 펀치홀, 노치
- IME: 소프트 키보드
- 앱 콘텐츠 영역

앱 콘텐츠가 이 요소들과 어떻게 겹치는지 다루는 것이 Android UI 대응의 핵심이다.

## 1.2 단위 정리

| 단위 | 의미 | 사용처 |
| --- | --- | --- |
| dp | 밀도 독립 픽셀 | 크기, 여백 |
| sp | dp와 같지만 사용자 폰트 스케일 반영 | 텍스트 크기 |
| px | 실제 픽셀 | 거의 직접 쓰지 않음 |

- 텍스트 크기는 반드시 sp, 그 외 레이아웃은 dp를 쓴다.
- 삼성 기기는 "화면 확대/축소"와 "글꼴 크기" 설정이 따로 있어서 dp와 sp가 각각 다르게 변할 수 있다.

## 1.3 Configuration

화면 크기, 방향, 폰트 스케일, 다크 모드 등은 모두 `Configuration`에 담겨 있다.
설정이 바뀌면 기본적으로 Activity가 재생성된다.

```kotlin
@Composable
fun ConfigurationInfo() {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    Column(modifier = Modifier.padding(16.dp)) {
        Text("screenWidthDp: ${configuration.screenWidthDp}")
        Text("screenHeightDp: ${configuration.screenHeightDp}")
        Text("smallestScreenWidthDp: ${configuration.smallestScreenWidthDp}")
        Text("fontScale: ${configuration.fontScale}")
        Text("densityDpi: ${configuration.densityDpi}")
        Text("density: ${density.density}")
    }
}
```

- `LocalConfiguration`은 Configuration이 바뀔 때마다 recomposition을 일으킨다.
- 멀티윈도우나 폴더블에서는 `screenWidthDp`가 기기 전체 폭이 아니라 앱 창의 폭이다.

<br>

# 2. 시스템 UI 영역과 Edge-to-Edge

## 2.1 Edge-to-Edge란

앱 콘텐츠를 시스템 바 뒤까지 그려서 화면 전체를 쓰는 방식이다.
Android 15(API 35)부터 API 35를 타깃으로 하는 앱은 edge-to-edge가 기본으로 강제된다.
즉 앱이 아무 처리를 안 하면 콘텐츠가 상태바와 내비게이션 바 아래로 들어간다.

## 2.2 기본 설정

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // setContent보다 먼저 호출한다
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                AppRoot()
            }
        }
    }
}
```

- `enableEdgeToEdge()`는 `androidx.activity` 라이브러리가 제공한다.
- 시스템 바 아이콘 색은 테마(밝음/어두움)에 맞춰 자동 조정된다.
- 상태바와 내비게이션 바 스타일을 직접 지정하려면 `SystemBarStyle`을 넘긴다.

```kotlin
enableEdgeToEdge(
    statusBarStyle = SystemBarStyle.auto(
        lightScrim = android.graphics.Color.TRANSPARENT,
        darkScrim = android.graphics.Color.TRANSPARENT
    ),
    navigationBarStyle = SystemBarStyle.auto(
        lightScrim = android.graphics.Color.argb(0xE6, 0xFF, 0xFF, 0xFF),
        darkScrim = android.graphics.Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    )
)
```

## 2.3 내비게이션 모드별 차이

- 3버튼 내비게이션: 내비게이션 바가 차지하는 영역이 있고 scrim이 기본 적용된다.
- 제스처 내비게이션: 하단에 얇은 제스처 핸들만 남고 완전 투명하게 그릴 수 있다.
- 삼성은 One UI에서 제스처 방식을 "스와이프 제스처" 등으로 부르며 옵션이 더 있다.
- 사용자가 모드를 바꾸면 insets 값이 달라지므로 하드코딩하면 안 된다.

## 2.4 디스플레이 컷아웃

- 가로 모드에서는 컷아웃이 좌우 측면에 오는 경우가 있다.
- `WindowInsets.displayCutout`을 반영해야 콘텐츠가 가려지지 않는다.
- 상단 바 높이만 고려하고 좌우 insets를 무시하면 가로 모드에서 UI가 잘린다.

<br>

# 3. WindowInsets 다루기

## 3.1 주요 Insets 종류

| 종류 | 설명 |
| --- | --- |
| statusBars | 상태바 영역 |
| navigationBars | 내비게이션 바 영역 |
| systemBars | 위 둘의 합 |
| displayCutout | 컷아웃 영역 |
| ime | 키보드 영역 |
| safeDrawing | 가려지면 안 되는 모든 영역 (systemBars + ime + cutout) |
| safeGestures | 시스템 제스처와 겹치면 안 되는 영역 |

- 대부분의 화면에서는 `safeDrawing`만 잘 처리해도 큰 문제가 없다.
- 스크롤 가능한 목록은 contentPadding으로 처리해야 목록이 바 아래까지 스크롤되면서도 마지막 항목이 가려지지 않는다.

## 3.2 Scaffold와 innerPadding

Material 3 `Scaffold`는 insets를 TopAppBar, BottomBar에 자동 반영한다.
콘텐츠에는 `innerPadding`을 반드시 적용해야 한다.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(items: List<String>) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("홈") })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding
        ) {
            items(items) { item ->
                ListItem(headlineContent = { Text(item) })
            }
        }
    }
}
```

- `innerPadding`을 무시하면 첫 항목이 TopAppBar 아래에 가려진다.
- `Modifier.padding(innerPadding)`보다 `contentPadding = innerPadding`이 스크롤 연출에 유리하다.

## 3.3 직접 처리

Scaffold를 쓰지 않는 화면에서는 modifier로 직접 처리한다.

```kotlin
@Composable
fun FullScreenContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Text("안전 영역 안의 콘텐츠")
    }
}
```

- `Modifier.safeDrawingPadding()`은 위와 같은 의미의 축약형이다.
- `Modifier.systemBarsPadding()`, `Modifier.statusBarsPadding()`, `Modifier.navigationBarsPadding()`도 있다.

## 3.4 키보드(IME) 처리

```kotlin
@Composable
fun LoginForm() {
    var text by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("이메일") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
            Text("로그인")
        }
    }
}
```

- Manifest의 `android:windowSoftInputMode`는 `adjustResize`가 기본이고 edge-to-edge와 함께 쓸 때 `imePadding()`으로 직접 처리하는 것이 안정적이다.
- 삼성 키보드는 상단에 툴바(클립보드, 이모지 등)가 있어 높이가 구글 키보드와 다를 수 있다. 높이를 고정값으로 가정하지 않는다.

## 3.5 Insets 소비(consume)

- 부모가 insets를 처리했다면 자식이 중복으로 처리하지 않도록 소비한다.
- `Scaffold`는 `innerPadding` 안쪽 콘텐츠에 대해 이미 insets를 소비한 것으로 취급한다.
- 중복 처리하면 위아래 여백이 이중으로 생긴다.

```kotlin
@Composable
fun Parent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // 자식에서 statusBarsPadding을 다시 쓰면 여백이 두 번 적용된다
        Child()
    }
}
```

## 3.6 둥근 모서리

- 최신 Galaxy와 Pixel은 화면 모서리가 둥글다.
- 가장자리에 붙는 UI(전체 폭 버튼, 바텀 시트 핸들)는 모서리 영역을 고려한다.
- 플랫폼에는 `RoundedCorner` API가 있지만 대부분의 경우 `safeDrawing` 여백과 최소한의 horizontal padding(16dp)으로 충분하다.

<br>

# 4. Material Design 3 핵심

## 4.1 구성 요소

- Color: 동적 색상(Dynamic Color), 역할 기반 색상 (primary, secondary, tertiary, surface 등)
- Typography: Display, Headline, Title, Body, Label 5계열
- Shape: 모서리 둥글기 토큰 (extraSmall ~ extraLarge)
- Motion: 전환과 애니메이션 가이드
- Components: Button, Card, TopAppBar, NavigationBar 등

## 4.2 Dynamic Color

Android 12(API 31) 이상에서 사용자의 배경화면에서 색을 추출해 앱 테마에 적용한다.

```kotlin
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
```

- 삼성 One UI도 Android 12 이상에서 배경화면 기반 색상 팔레트를 지원한다. 다만 삼성은 "Color Palette"라는 자체 설정 이름을 쓴다.
- 브랜드 색이 중요한 앱은 `dynamicColor = false`로 두는 선택지도 있다.

## 4.3 Color Role

| 역할 | 용도 |
| --- | --- |
| primary / onPrimary | 주요 액션, 그 위의 텍스트 |
| secondaryContainer | 선택된 상태의 배경 (NavigationBar indicator 등) |
| surface / onSurface | 일반 배경과 텍스트 |
| surfaceVariant | 구분되는 배경 (Card 등) |
| error / onError | 오류 표시 |
| outline | 테두리, 구분선 |

- 색을 직접 하드코딩하지 말고 `MaterialTheme.colorScheme.xxx`를 쓴다.
- 다크 모드와 Dynamic Color가 자동으로 대응된다.

## 4.4 Typography

```kotlin
Text(
    text = "제목",
    style = MaterialTheme.typography.titleLarge
)
Text(
    text = "본문 내용",
    style = MaterialTheme.typography.bodyMedium
)
```

- 스타일을 거치지 않고 `fontSize = 14.sp`를 직접 쓰면 일관성이 깨진다.
- 폰트 스케일 대응을 위해 sp 단위를 유지한다.

## 4.5 Material 3 Expressive

- Google은 Android 16 시기에 Material 3 Expressive를 발표했다. 더 풍부한 색, 모양, 모션을 강조하는 방향이다.
- One UI 8.5의 디자인 변화도 이런 흐름과 맞물려 있다고 볼 수 있다. (정확한 영향 관계는 공식 자료로 확인)
- 라이브러리 버전에 따라 Expressive 컴포넌트가 alpha/beta일 수 있으므로 프로덕션 적용 전에 상태를 확인한다.

<br>

# 5. One UI란 무엇인가

## 5.1 정의

One UI는 삼성이 AOSP(Android Open Source Project) 위에 얹은 사용자 인터페이스 스킨이다.
삼성 Galaxy 스마트폰, 태블릿, 폴더블, 워치에 적용된다.

## 5.2 One UI가 바꾸는 것

- SystemUI: 알림 패널, Quick Panel, 상태바, 잠금화면
- 런처: One UI Home
- 설정 앱 구조
- 기본 앱: 갤러리, 노트, 연락처, 캘린더, 삼성 키보드, 삼성 인터넷
- 부가 기능: Edge Panel, Samsung DeX, Good Lock, Secure Folder, Galaxy AI
- 보안 계층: Samsung Knox

## 5.3 One UI가 바꾸지 않는 것

- 앱이 사용하는 Android SDK API
- 앱의 Activity, Fragment, Compose 동작 규칙
- Android 권한 모델의 기본 틀

그래서 일반적인 앱은 One UI 때문에 코드를 따로 짜지 않는다.
대신 삼성 기기에서만 두드러지는 화면 비율, 폴더블, 멀티윈도우, 배터리 정책, 키보드 차이를 확인한다.

## 5.4 One UI Design 철학

삼성이 강조해 온 방향은 다음과 같다.

- 한 손 사용성: 큰 화면에서도 주요 조작 요소를 하단에 배치
- 시청 영역과 상호작용 영역 분리: 화면 상단은 보기, 하단은 조작
- 일관된 아이콘과 둥근 모서리
- 몰입을 방해하지 않는 시각 요소

개발자 입장에서 의미 있는 점은 화면이 클수록 상단 영역을 터치하기 어렵다는 가정이 기본이라는 것이다.
주요 버튼을 상단 우측에만 두는 설계는 큰 화면에서 불편하다.

<br>

# 6. One UI 버전 흐름

## 6.1 Android 버전과 One UI 버전

| Android | One UI |
| --- | --- |
| Android 14 | One UI 6 |
| Android 15 | One UI 7 |
| Android 16 | One UI 8 |
| Android 16 (중간 업데이트) | One UI 8.5 |
| Android 17 | One UI 9 |

- 삼성은 메이저 버전(x.0)과 중간 버전(x.5)을 나눠서 낸다.
- 일반적으로 x.5에서 기능이 크게 늘고, x.0은 Android 플랫폼 업데이트를 반영하는 성격이 강하다.

## 6.2 One UI 9의 위치

- One UI 9.0은 Android 17 기반이다.
- 베타는 2026년 5월에 Galaxy S26 시리즈부터 시작됐다.
- 안정판은 신형 플래그십(폴더블)에서 먼저 출시되고 이후 기존 기종으로 순차 배포되는 흐름이다.
- 업데이트 대상은 S23 이후 플래그십과 폴더블, Galaxy A 일부 모델, 태블릿 등으로 알려져 있으나 실제 대상과 시기는 삼성 공식 공지를 확인해야 한다.
- One UI 9.5는 2027년 초 Galaxy S27 시기에 나올 것이라는 전망이 있다.

## 6.3 개발자가 버전을 보는 방법

- 앱 코드는 One UI 버전이 아니라 `Build.VERSION.SDK_INT`로 분기한다.
- One UI 버전 자체를 코드에서 분기하는 것은 가급적 피한다.
- 삼성 전용 분기가 꼭 필요하다면 `Build.MANUFACTURER`를 확인한다.

```kotlin
object DeviceInfo {
    val isSamsung: Boolean
        get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    val isAtLeastAndroid15: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
}
```

- 제조사 분기는 최후의 수단이다. 대부분은 기능 존재 여부 확인(feature detection)이나 표준 API로 해결된다.

<br>

# 7. One UI 9.0 변경점

## 7.1 전체 성격

- One UI 8.5에서 디자인이 크게 바뀐 뒤 나온 업데이트라서 9.0은 다듬기 성격이 강하다.
- 대부분의 앱 화면은 8.5와 비슷하게 보이고, 일부 영역에 유리 같은 시각 효과가 늘어날 수 있다는 전망이 있다.
- 핵심은 Android 17 기반 전환, Galaxy AI 기능 강화, 보안 강화다.

## 7.2 사용자 눈에 보이는 변화

- Quick Panel: 레이아웃 커스텀 범위 확대, 밝기/볼륨/미디어 컨트롤 영역을 따로 조절, 크기 옵션 추가
- 삼성 노트: 장식용 테이프, 다양한 펜 선 스타일 등 창작 도구 추가
- 연락처: Creative Studio 직접 연결로 프로필 카드 제작
- 접근성: 마우스 키 속도 조절, 구글과 삼성의 TalkBack 기능 통합
- Galaxy AI: 화면 활동 기반 제안, 음성으로 사진 편집 등이 소개됐다. 세부 기능은 기기별로 다를 수 있다.

## 7.3 앱 개발자에게 영향 있는 변화

1. Android 17 플랫폼 변화가 그대로 들어온다. (대화면 정책, 권한 변화 등)
2. 보안 정책 강화로 외부 경로 설치 앱이 더 눈에 띄고 차단될 수 있다.
3. 접근성 TalkBack 통합으로 접근성 테스트 시 동작이 달라질 수 있다.
4. Quick Panel, 알림 영역 변화는 앱 알림 표시 방식과 Live Updates 등에 간접 영향을 줄 수 있다.

## 7.4 알림 UI

- Android 16에서 Live Updates(진행형 알림)가 도입됐고, Android 17에서 더 많은 앱 유형으로 확장될 것이라는 전망이 있다.
- 배달, 택시, 타이머 같은 진행 상황 알림은 시스템 UI에서 눈에 띄게 노출된다.
- 앱이 해당 유형에 맞는지 문서 기준으로 확인한 뒤 적용한다.

```kotlin
// 진행형 알림의 기본 형태 (카테고리와 ongoing 설정)
val notification = NotificationCompat.Builder(context, CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_stat_delivery)
    .setContentTitle("배달 진행 중")
    .setContentText("도착까지 10분")
    .setOngoing(true)
    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
    .build()
```

- Live Updates 전용 API와 요구 사항은 버전별로 다르므로 공식 문서를 확인한다.

<br>

# 8. One UI 디자인 특징

## 8.1 하단 중심 레이아웃

- 큰 화면에서 엄지 손가락이 닿는 하단에 주요 조작 요소를 배치하는 것이 One UI의 오래된 원칙이다.
- 앱에서도 하단 내비게이션, 하단 액션 버튼, 바텀 시트가 자연스럽게 어울린다.
- 상단 우측에 중요한 버튼만 있는 화면은 삼성 대화면 기기에서 불편해진다.

## 8.2 둥근 모서리와 곡률

- 카드, 다이얼로그, 시트의 모서리가 크게 둥글다.
- Material 3의 `Shape` 토큰으로 맞추면 자연스럽다.

```kotlin
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

MaterialTheme(
    colorScheme = colorScheme,
    shapes = AppShapes,
    content = content
)
```

## 8.3 One UI와 Material 3의 차이

- Material 3는 크로스 제조사 공통 가이드이고, One UI는 삼성 기기에서 시스템 UI와 삼성 앱에 적용되는 스타일이다.
- 서드파티 앱이 One UI 스타일을 그대로 따라 그릴 필요는 없다.
- 앱은 Material 3를 기준으로 만들고, One UI에서 어색하지 않은지만 확인하면 된다.

## 8.4 사용자 커스터마이징

삼성 사용자는 다음을 자주 바꾼다.

- 글꼴 종류와 크기, 굵기
- 화면 확대/축소
- 색상 팔레트
- 내비게이션 방식
- 앱 아이콘 모양 (Good Lock, 테마)
- 다크 모드 예약

앱은 이런 설정 변화에 대응할 수 있어야 한다.
고정 높이, 고정 크기에 의존하는 UI는 삼성 사용자에게서 먼저 깨진다.

## 8.5 Good Lock

- 삼성 Galaxy Store의 모듈형 커스터마이징 앱이다.
- QuickStar(상태바/Quick Panel), NavStar(내비게이션 바), MultiStar(멀티윈도우) 등 모듈이 있다.
- 파워 유저는 시스템 UI를 크게 바꿔 놓기도 한다.
- 앱이 시스템 바 높이나 모양에 의존하면 이런 사용자에게서 문제가 생긴다. insets API만 쓰면 대부분 안전하다.

<br>

# 9. 삼성 기기에서 자주 겪는 UI 이슈

## 9.1 큰 폰트와 화면 확대

- 삼성 기기는 폰트 스케일과 화면 밀도를 같이 올리는 사용자가 많다.
- 고정 높이 컨테이너 안의 텍스트가 잘리는 문제가 가장 흔하다.
- `Modifier.height(48.dp)`로 고정한 버튼에 텍스트가 길어지면 잘린다. 대신 `heightIn(min = 48.dp)`를 쓴다.

```kotlin
// 나쁜 예
Button(
    onClick = { },
    modifier = Modifier.height(40.dp)
) { Text("긴 텍스트가 들어가는 버튼") }

// 좋은 예
Button(
    onClick = { },
    modifier = Modifier.heightIn(min = 40.dp)
) { Text("긴 텍스트가 들어가는 버튼") }
```

## 9.2 키보드와 IME

- 삼성 키보드는 상단 툴바와 제안 줄 때문에 높이가 커지는 경우가 있다.
- `imePadding()`으로 실제 IME 높이에 맞춰야 한다.
- 로그인, 검색, 채팅 화면에서 입력창이 가려지는 문제는 대부분 insets 처리 누락이다.

## 9.3 멀티윈도우와 팝업 뷰

- 삼성은 분할 화면과 팝업 뷰(플로팅 창)를 적극 지원한다.
- 앱 창 크기가 수시로 바뀌므로 `LocalConfiguration`이나 `WindowSizeClass`로 현재 창 크기를 기준으로 레이아웃을 정한다.
- 기기 전체 화면 크기를 기준으로 판단하면 팝업 뷰에서 UI가 깨진다.
- Manifest에 `android:resizeableActivity="true"`를 명시해 두는 것이 안전하다. 최근 타깃 SDK에서는 기본값이 true다.

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:resizeableActivity="true" />
```

## 9.4 DeX와 데스크톱 모드

- Samsung DeX는 폰이나 태블릿을 외부 모니터에 연결해 데스크톱처럼 쓰는 기능이다.
- 창 크기 조절, 마우스 호버, 우클릭, 키보드 단축키를 쓰게 된다.
- Android 17에서 데스크톱 모드가 개선될 것이라는 전망이 있어 표준 데스크톱 윈도잉도 앞으로 중요해진다.
- 앱 입장에서는 마우스 호버 상태, 포커스 이동, 키보드 단축키, 창 크기 변경에 대응하면 된다.

```kotlin
@Composable
fun HoverableCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isHovered) 6.dp else 1.dp
        )
    ) {
        content()
    }
}
```

## 9.5 엣지 패널과 제스처 충돌

- 삼성 엣지 패널 손잡이가 화면 측면에 있다.
- 화면 가장자리에서 시작하는 스와이프 제스처(사이드 메뉴 열기 등)가 시스템 뒤로 가기 제스처와 충돌할 수 있다.
- Navigation Drawer를 가장자리 스와이프로 여는 UI는 제스처 내비게이션과 충돌하므로 버튼 중심으로 설계하거나 `safeGestures` insets를 고려한다.

## 9.6 배터리 정책과 백그라운드 제한

- 삼성은 "절전 중인 앱", "깊은 절전" 같은 자체 배터리 관리가 있다.
- UI 이슈는 아니지만 알림 지연, WorkManager 지연으로 사용자 눈에는 "앱이 이상하다"로 보인다.
- 알림이 중요한 앱은 사용자에게 배터리 최적화 제외를 안내할지 검토한다. (과도한 요청은 Play 정책에서 제한된다.)

## 9.7 Secure Folder와 듀얼 메신저

- Secure Folder는 별도의 사용자 공간에서 앱을 복제 실행한다. 앱의 데이터 저장소가 분리된다.
- 듀얼 메신저는 같은 앱을 두 계정으로 쓰게 해 준다.
- 앱이 단일 인스턴스를 가정하거나 기기 단위 고유 ID에 로그인 상태를 묶으면 문제가 생길 수 있다.

## 9.8 Auto Blocker와 개발 환경

- 삼성 Auto Blocker는 승인되지 않은 출처의 앱 설치와 USB 케이블을 통한 명령을 막을 수 있다.
- 개발용 기기에서 Auto Blocker가 켜져 있으면 adb 연결이나 APK 설치가 막혀 디버깅이 안 되는 경우가 있다.
- 삼성 기기로 테스트할 때 연결이 안 되면 Auto Blocker 설정부터 확인한다.

<br>

# 10. 화면 크기 대응과 Android 16/17 변화

## 10.1 배경

폴더블, 태블릿, 데스크톱 모드 사용이 늘면서 Google은 앱이 다양한 창 크기에 적응하도록 정책을 단계적으로 강화하고 있다.

## 10.2 Android 16 (API 36)

- 대화면 기기(최소 너비 sw 600dp 이상)에서 앱이 방향 고정이나 크기 조절 불가를 선언해도 시스템이 무시하는 쪽으로 바뀌었다.
- 대상 SDK를 36으로 올리는 앱에는 적용되되, 임시로 opt-out 할 수 있는 수단이 제공됐다.
- 게임으로 분류된 앱(`android:appCategory`)은 제외된다.

## 10.3 Android 17 (API 37)

- API 37을 타깃으로 하는 앱은 대화면에서 방향과 크기 조절 제한 opt-out이 제거된다.
- 즉 앱이 다양한 창 크기를 지원할 수 있어야 한다.
- Android 개발자 문서에는 2027년 8월까지 API 37 타깃팅이 언급돼 있다. 정확한 Play 요구 일정은 Play Console 공지를 확인한다.
- 이 변경은 sw 600dp 미만 화면에는 적용되지 않는다.

## 10.4 앱이 해야 할 것

1. Manifest에서 `screenOrientation` 고정과 `resizeableActivity="false"` 선언을 점검한다.
2. 버튼, 텍스트 필드가 큰 화면에서 비정상적으로 늘어나지 않게 최대 폭을 제한한다.
3. 가로 모드와 분할 화면에서 레이아웃이 깨지지 않는지 확인한다.
4. 창 크기 클래스에 따라 레이아웃을 바꾼다.
5. 설정 변경 시 상태가 유지되도록 `rememberSaveable`, ViewModel을 쓴다.

## 10.5 콘텐츠 최대 폭 제한

```kotlin
@Composable
fun ConstrainedContent(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            content = content
        )
    }
}
```

- 폼, 설정, 상세 텍스트 화면은 최대 폭을 제한하는 것만으로도 큰 화면 문제가 많이 해결된다.

## 10.6 Window Size Class 기준

| 구분 | 폭 기준 | 대표 기기 |
| --- | --- | --- |
| Compact | 600dp 미만 | 일반 폰 세로 |
| Medium | 600dp 이상 840dp 미만 | 폴더블 펼침, 작은 태블릿 |
| Expanded | 840dp 이상 | 태블릿, 데스크톱 창 |

- 높이 기준 분류도 별도로 있다.
- 최신 라이브러리 버전에서는 Large, Extra-large 폭 구간이 추가되는 방향이므로 사용 버전의 문서를 확인한다.

<br>

# 11. Compose 적응형 레이아웃

## 11.1 의존성

```kotlin
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:<최신 BOM 버전>")
    implementation(composeBom)

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    implementation("androidx.window:window:<최신 버전>")
}
```

- 버전은 프로젝트의 BOM과 공식 릴리스 노트에 맞춰 넣는다.

## 11.2 WindowSizeClass 계산

```kotlin
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            AppTheme {
                AppRoot(windowSizeClass = windowSizeClass)
            }
        }
    }
}
```

- `calculateWindowSizeClass`는 현재 창 크기를 기준으로 계산하므로 멀티윈도우와 폴더블에서도 정확하다.
- 일부 버전에서는 대안으로 `currentWindowAdaptiveInfo()`를 쓸 수 있다.

## 11.3 크기별 레이아웃 분기

```kotlin
@Composable
fun AppRoot(windowSizeClass: WindowSizeClass) {
    when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> CompactLayout()
        WindowWidthSizeClass.Medium -> MediumLayout()
        WindowWidthSizeClass.Expanded -> ExpandedLayout()
    }
}

@Composable
fun CompactLayout() {
    // 단일 패널: 목록 -> 상세로 화면 전환
    Text("Compact: 한 화면에 한 패널")
}

@Composable
fun MediumLayout() {
    Text("Medium: 내비게이션 레일 + 한 패널")
}

@Composable
fun ExpandedLayout() {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) { Text("목록 패널") }
        Box(modifier = Modifier.weight(2f)) { Text("상세 패널") }
    }
}
```

- 분기 기준은 기기 종류가 아니라 창 크기다.
- 폴더블 접힘 상태는 Compact, 펼침 상태는 Medium 또는 Expanded가 된다.
- 창 크기가 바뀌어도 상태가 유지되도록 상태는 ViewModel에서 관리한다.

## 11.4 NavigationSuiteScaffold

창 크기에 따라 하단 내비게이션 바, 내비게이션 레일, 드로어를 자동으로 바꿔 주는 컴포넌트다.

```kotlin
enum class AppDestination(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Default.Home),
    FAVORITES("즐겨찾기", Icons.Default.Favorite),
    SETTINGS("설정", Icons.Default.Settings)
}

@Composable
fun AdaptiveNavigationApp() {
    var current by rememberSaveable { mutableStateOf(AppDestination.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestination.entries.forEach { destination ->
                item(
                    selected = destination == current,
                    onClick = { current = destination },
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label) }
                )
            }
        }
    ) {
        when (current) {
            AppDestination.HOME -> Text("홈 화면")
            AppDestination.FAVORITES -> Text("즐겨찾기 화면")
            AppDestination.SETTINGS -> Text("설정 화면")
        }
    }
}
```

- 폰 세로에서는 하단 바, 중간 크기에서는 레일, 큰 화면에서는 드로어형으로 자동 전환된다.
- One UI 사용자가 폴더블을 펼쳤을 때 자연스러운 경험을 주려면 이런 컴포넌트가 효율적이다.

## 11.5 목록-상세 패턴

- 큰 화면에서는 목록과 상세를 한 화면에 나란히 보여 주는 것이 표준 패턴이다.
- Material 3 Adaptive 라이브러리의 `ListDetailPaneScaffold`가 이 패턴을 제공한다.
- API가 버전에 따라 바뀌는 편이라 사용하는 버전의 공식 샘플을 기준으로 구현한다.
- 직접 구현하는 경우에는 11.3의 `ExpandedLayout`처럼 Row로 나누고 선택 상태를 ViewModel에 둔다.

## 11.6 멀티 모듈에서의 위치

- MVI 구조에서는 레이아웃 분기를 Route/Screen 수준에서 처리하고, 개별 컴포넌트는 크기와 무관하게 동작하도록 만드는 것이 깔끔하다.
- `WindowSizeClass`를 ViewModel로 넘기지 않는다. UI 상태 판단은 Composable에서 하고 ViewModel은 데이터와 의도(Intent)만 다룬다.
- 디자인 시스템 모듈에 공통 컴포넌트와 최대 폭 제한 컨테이너를 모아 두면 화면 모듈에서 중복이 줄어든다.

<br>

# 12. 폴더블과 Jetpack WindowManager

## 12.1 폴더블 상태

- 접힘(Closed), 반 접힘(Half-opened), 펼침(Flat) 상태가 있다.
- 반 접힘에서 가로로 접힌 자세를 Tabletop, 세로로 접힌 자세를 Book이라 부른다.
- 삼성은 Flex Mode라는 이름으로 반 접힘 UI를 지원한다. 표준 구현은 Jetpack WindowManager가 담당한다.

## 12.2 의존성과 구독

```kotlin
class FoldViewModel(application: Application) : AndroidViewModel(application)
```

- 위처럼 ViewModel에서 직접 Activity에 의존하지 않도록 주의한다. WindowManager는 Activity 컨텍스트가 필요하다.
- 일반적으로 Activity에서 구독해 UI 상태로 변환한다.

```kotlin
@Composable
fun rememberFoldingState(activity: Activity): State<FoldingFeature?> {
    val flow = remember(activity) {
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { layoutInfo ->
                layoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()
            }
    }
    return flow.collectAsState(initial = null)
}
```

## 12.3 Tabletop 자세 대응

```kotlin
@Composable
fun VideoPlayerScreen(activity: Activity) {
    val folding by rememberFoldingState(activity)
    val feature = folding

    val isTabletop = feature != null &&
        feature.state == FoldingFeature.State.HALF_OPENED &&
        feature.orientation == FoldingFeature.Orientation.HORIZONTAL

    if (isTabletop) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) { Text("영상 영역 (상단)") }
            Box(modifier = Modifier.weight(1f)) { Text("컨트롤 영역 (하단)") }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) { Text("일반 레이아웃") }
    }
}
```

- 힌지(접히는 선) 위치에 중요한 콘텐츠를 걸치지 않도록 한다.
- `FoldingFeature.occlusionType`이 FULL이면 힌지가 화면을 가리는 형태다.

## 12.4 커버 화면

- Galaxy Z Flip 계열은 바깥쪽 커버 화면이 따로 있고 앱 실행은 기본적으로 제한적이다.
- 커버 화면 지원은 삼성의 허용 정책과 사용자 설정에 달려 있어 일반 앱이 직접 다룰 부분은 작다.
- 접힘 상태에서 앱이 어떻게 보이는지는 실제 기기나 에뮬레이터로 확인하는 것이 확실하다.

## 12.5 화면 연속성

- 접었다 폈을 때 앱이 Activity 재생성 없이 이어서 동작해야 한다.
- 상태는 `rememberSaveable`과 ViewModel에 둔다.
- `android:configChanges`로 재생성을 막는 방법도 있지만, Compose 앱에서는 상태 보존을 제대로 하는 쪽이 낫다.
- 재생성을 막으면 대부분의 편의는 얻지만 리소스 갱신을 직접 처리해야 하는 부담이 생긴다.

<br>

# 13. 폰트 스케일과 디스플레이 크기

## 13.1 두 가지 설정

- 글꼴 크기(font scale): sp 단위 텍스트만 커진다.
- 화면 확대/축소(display size, density): 모든 dp 요소가 같은 비율로 변한다.
- 삼성은 두 설정을 모두 쉽게 바꾸게 해 두었고 접근성 사용자나 고령 사용자에서 많이 쓴다.

## 13.2 Android 14의 비선형 폰트 스케일

- Android 14부터 폰트 스케일을 최대 200%까지 지원하고 큰 글자에서는 비선형으로 확대한다.
- 이미 큰 글자는 덜 커지고 작은 글자가 상대적으로 더 커져 가독성을 유지한다.
- sp를 정상적으로 쓰면 앱은 자동으로 이 혜택을 받는다.

## 13.3 레이아웃이 깨지는 패턴

1. 고정 height, 고정 width 컨테이너
2. `maxLines = 1`과 `overflow = Ellipsis`로 중요한 정보가 잘리는 경우
3. Row 안에 여러 요소를 weight 없이 나열해 폭이 모자라는 경우
4. 아이콘과 텍스트를 같은 줄에 고정 배치하는 경우
5. 텍스트 크기를 `dp` 기반으로 변환해 쓰는 경우

## 13.4 Row가 좁아질 때 대응

```kotlin
@Composable
fun SettingRow(title: String, description: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        trailing()
    }
}
```

- 텍스트 영역에 `weight(1f)`를 주어 trailing 요소가 밀려나지 않게 한다.
- 최소 높이는 `heightIn(min)`으로 두어 큰 글자에서 늘어나게 한다.

## 13.5 FlowRow 활용

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagList(tags: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.forEach { tag ->
            AssistChip(onClick = { }, label = { Text(tag) })
        }
    }
}
```

- 큰 글자나 좁은 창에서 칩이 한 줄에 못 들어가면 자동으로 줄바꿈된다.

## 13.6 Density 변화 대응

- 화면 확대/축소가 바뀌면 `densityDpi`가 변하고 Activity가 재생성된다.
- px 단위로 계산해서 저장하면 설정 변경 후 값이 어긋난다. 항상 dp 기준으로 저장한다.
- 이미지 리소스는 밀도별로 준비하거나 벡터를 쓴다.

## 13.7 테스트용 adb 명령

```bash
# 폰트 스케일 변경 (1.0이 기본)
adb shell settings put system font_scale 1.3
adb shell settings put system font_scale 2.0

# 화면 밀도 변경과 복원
adb shell wm density 480
adb shell wm density reset

# 화면 크기 변경과 복원
adb shell wm size 1080x2400
adb shell wm size reset
```

- 삼성 실기기에서도 같은 명령이 통하지만 삼성 설정 UI의 값과 동기화되지 않을 수 있다.
- 테스트 후에는 꼭 원래 값으로 복원한다.

<br>

# 14. 다크 모드

## 14.1 기본 대응

```kotlin
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

- `isSystemInDarkTheme()`은 시스템 설정을 따른다.
- 삼성 One UI는 다크 모드 예약(일몰 후 자동)과 앱별 설정이 있어 앱 실행 중에도 전환이 일어날 수 있다.
- 전환 시 Configuration 변경으로 Activity가 재생성되거나 recomposition이 일어난다.

## 14.2 흔한 실수

1. 색을 `Color(0xFFFFFFFF)`로 하드코딩하고 `onSurface`와 맞추지 않는 경우
2. 이미지나 로고가 다크 배경에서 안 보이는 경우
3. 상태바 아이콘이 배경과 같은 색이 되는 경우
4. 그림자(elevation)를 다크 모드에서 그대로 쓰는 경우. Material 3는 다크에서 tonal elevation으로 구분한다.

## 14.3 강제 다크 모드

- 다크 모드를 지원하지 않는 앱도 시스템이 자동으로 어둡게 변환하는 기능이 일부 기기와 버전에서 제공된다.
- Android 17에서 확장된 다크 테마가 논의되고 있다는 보도가 있다. (확정 기능과 동작 방식은 공식 문서 확인)
- 자동 변환은 색이 어색하게 반전되기 쉽다. 앱이 직접 다크 테마를 지원하는 것이 가장 안전하다.
- 변환을 막으려는 목적의 `android:forceDarkAllowed`는 View 시스템 앱에 해당하는 속성이다.

## 14.4 앱별 테마 선택

- 시스템 설정과 별개로 앱 안에서 라이트/다크/시스템 설정을 고르게 하는 경우가 많다.

```kotlin
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun AppTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

- 앱 내 테마 선택값은 DataStore에 저장하고 Application 단계에서 먼저 읽어 첫 프레임 깜빡임을 줄인다.
- `enableEdgeToEdge`의 SystemBarStyle도 선택된 테마에 맞춰 다시 설정해야 상태바 아이콘 색이 맞는다.

<br>

# 15. 제스처 내비게이션과 Back

## 15.1 Predictive Back

- Android 13에서 도입됐고 사용자가 뒤로 가기 제스처를 하는 도중 이전 화면을 미리 보여 주는 애니메이션이다.
- 앱이 `OnBackInvokedCallback` 기반 처리를 지원해야 한다.
- 최근 타깃 SDK에서는 시스템 애니메이션이 기본 활성화되고 기존 `onBackPressed()` 방식은 호출되지 않는 쪽으로 가고 있다. 사용 중인 타깃 SDK 기준 동작은 공식 문서에서 확인한다.

```xml
<application
    android:enableOnBackInvokedCallback="true">
    ...
</application>
```

## 15.2 Compose에서의 Back 처리

```kotlin
@Composable
fun EditScreen(onExit: () -> Unit) {
    var hasUnsavedChanges by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }

    // 저장하지 않은 변경이 있을 때만 가로챈다
    BackHandler(enabled = hasUnsavedChanges) {
        showDialog = true
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("변경 사항을 버릴까요?") },
            confirmButton = {
                TextButton(onClick = onExit) { Text("나가기") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("계속 편집") }
            }
        )
    }
}
```

- `BackHandler`의 `enabled`를 조건에 맞게 꺼야 불필요하게 Back을 가로채지 않는다.
- 항상 true로 두고 내부에서 분기하면 Predictive Back 미리 보기가 동작하지 않는다.

## 15.3 삼성의 내비게이션 옵션

- 삼성은 3버튼, 스와이프 제스처, 제스처 힌트 표시 여부, 버튼 순서(뒤로 가기가 좌/우) 등을 제공한다.
- 사용자 선택에 따라 하단 영역 insets가 달라진다.
- 하단 고정 버튼은 `navigationBarsPadding()`이나 Scaffold의 insets 처리를 반드시 거친다.

## 15.4 시스템 제스처 영역

- 화면 좌우 가장자리의 뒤로 가기 제스처 영역과 하단의 홈 제스처 영역은 앱이 가로챌 수 없다.
- 가장자리에 슬라이더, 스와이프 항목을 두면 시스템 제스처와 충돌한다.
- `WindowInsets.safeGestures`로 해당 영역을 확인해 중요한 조작 요소를 안쪽으로 둔다.

```kotlin
@Composable
fun GestureSafeSlider(value: Float, onValueChange: (Float) -> Unit) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeGestures.only(WindowInsetsSides.Horizontal))
    )
}
```

<br>

# 16. One UI 9와 Android 17 보안 기능

## 16.1 연관성 정리

- 보안은 One UI의 UI 변경과 직접 겹치기보다 OS와 사용자 보호 기능 쪽에 가깝다.
- 다만 설정 메뉴, 경고 화면, 권한 선택 UI가 모두 사용자에게 보이는 UI이므로 앱이 이 흐름과 어떻게 맞물리는지는 알아 둘 만하다.
- 보안 기능은 Android 17 자체 기능과 삼성이 얹은 기능으로 나뉜다.

## 16.2 One UI 9의 악성 앱 보호 강화

- 고위험 앱이 새로 감지되면 사용자에게 경고하고, 실행과 설치를 차단하고, 삭제를 권고한다.
- 보안 정책 업데이트를 통해 대응하는 방식이다.
- 보도에 따르면 통화 중 설치처럼 의심스러운 상황에서 설치된 앱을 앱 스토어 정보와 대조해 탐지하는 기능도 포함된다. (세부 동작은 기기와 지역에 따라 다를 수 있다.)

## 16.3 알 수 없는 앱 관리

- One UI 9 베타에서 설정 > 보안 및 개인정보 보호 > 추가 보안 설정에 "알 수 없는 앱 관리" 항목이 생겼다.
- 승인된 스토어(Play 스토어, Galaxy Store) 밖에서 설치된 앱을 모아서 보여 준다.
- 사용자는 의심스럽거나 쓰지 않는 앱을 쉽게 찾아 삭제할 수 있다.
- 개발 중인 테스트 APK를 adb로 설치하면 이 목록에 나타날 가능성이 있다. 목록 표시 자체는 문제가 아니지만 QA 기기에서 헷갈릴 수 있다.
- 사내 배포나 테스트 배포는 가능하면 Firebase App Distribution이나 Play 내부 테스트 트랙처럼 정식 경로를 쓴다.

## 16.4 Advanced Protection Mode

- Google이 제공하는 고위험 사용자 대상 강화 보안 모드이며 Android 17 기반으로 한 단계 확장된 것으로 소개된다.
- One UI 8부터 삼성도 이 모드를 지원했다. 당시에는 Android 레벨 설정을 관리하고 Knox나 Secure Folder 같은 삼성 자체 기능은 건드리지 않는 형태였다.
- 이 모드가 켜지면 사이드로딩 제한, USB 관련 보호 등 일부 기능이 제한될 수 있다. 정확한 항목은 공식 문서로 확인한다.
- 앱이 사이드로딩 설치나 특정 권한에 의존한다면 이 모드의 사용자에게서 동작이 제한될 수 있다.

## 16.5 시스템 연락처 선택기

- Android 17은 시스템 수준 연락처 선택기를 도입해 앱이 사용자가 명시적으로 허용한 연락처만 접근하게 한다.
- 사진 선택기(Photo Picker)와 같은 철학이다. 앱이 전체 연락처 권한을 요구하지 않고 필요한 항목만 받는다.
- 정확한 API 이름과 사용법은 공식 문서에서 확인해야 한다. 여기서는 기존에도 권한 없이 쓸 수 있던 방식을 정리한다.

```kotlin
@Composable
fun ContactPickerButton(onPicked: (Uri) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let(onPicked)
    }

    Button(onClick = {
        val intent = Intent(
            Intent.ACTION_PICK,
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        )
        launcher.launch(intent)
    }) {
        Text("연락처 선택")
    }
}
```

- `ACTION_PICK`으로 선택한 항목은 `READ_CONTACTS` 권한 없이도 그 항목에 대해서만 임시 읽기 권한이 부여된다.
- 전체 연락처 목록이 꼭 필요한 기능이 아니라면 이 방식이 사용자 신뢰와 심사에 유리하다.

## 16.6 보안 업데이트 구조

- 삼성은 월별 보안 패치에서 Android 공통 취약점(CVE)과 One UI 자체 취약점(SVE)을 따로 공개한다.
- 예를 들어 2026년 7월 패치는 Android CVE 41건과 One UI SVE 16건을 다뤘다고 알려져 있다.
- 앱 개발자는 이 항목을 직접 다루지는 않지만, 사용자 기기의 보안 패치 수준은 앱의 위험 평가에 영향을 줄 수 있다.

<br>

# 17. 개발자가 지켜야 할 보안 관련 원칙

## 17.1 악성 앱으로 오인되지 않으려면

One UI 9의 보호 기능은 위험한 앱을 차단하는 것이 목적이다. 정상 앱이 오탐을 겪지 않도록 다음을 지킨다.

1. 꼭 필요한 권한만 요청한다. 특히 접근성 서비스, 다른 앱 위에 표시, SMS, 설치 패키지 요청은 사용 사유가 명확해야 한다.
2. 코드 난독화는 R8 기본 수준으로 쓰고, 동작을 숨기기 위한 과도한 우회 기법은 쓰지 않는다.
3. Play 스토어나 Galaxy Store 등 정식 경로로 배포한다.
4. 앱 서명 키를 일관되게 유지한다.
5. 외부 코드를 동적으로 내려받아 실행하는 구조는 피한다.

## 17.2 권한 요청 UI

- 권한은 필요한 시점에 하나씩 요청한다. 앱 시작 직후 한꺼번에 요청하지 않는다.
- 거부했을 때의 대체 흐름을 준비한다.
- 설정으로 이동해야 하는 경우에는 이유를 먼저 설명한다.

```kotlin
@Composable
fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> granted = isGranted }

    if (granted) {
        content()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("사진 촬영을 위해 카메라 권한이 필요해요.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("권한 허용")
            }
        }
    }
}
```

## 17.3 Secure Folder와 Knox 고려

- Secure Folder 안에서 실행되면 별도 사용자 공간이므로 파일 경로, 공유 저장소, 다른 앱과의 연동이 달라질 수 있다.
- 기기 고유 ID(예: ANDROID_ID)는 사용자 공간마다 다를 수 있다. 로그인 식별은 서버 계정 기반으로 한다.
- Knox 관련 기능이 필요한 기업용 앱은 Knox SDK 문서를 따로 확인한다. 일반 앱은 Knox를 직접 다룰 일이 거의 없다.

## 17.4 민감 화면 보호

- 결제, 인증, 개인정보 화면에서는 화면 캡처와 최근 앱 미리보기 노출을 제한할 수 있다.

```kotlin
// 민감한 화면에서만 설정하고 벗어나면 해제한다
@Composable
fun SecureScreenEffect() {
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        activity?.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
```

- `FLAG_SECURE`는 스크린샷과 화면 녹화를 막고 최근 앱 화면에서 내용을 가린다.
- 삼성 기기에서는 스마트 캡처 등 추가 캡처 경로가 있지만 `FLAG_SECURE`는 시스템 수준에서 적용된다.

## 17.5 사용자에게 보이는 보안 UI 가이드

- 보안 경고, 권한 설명 문구는 짧고 구체적으로 쓴다.
- 시스템 경고와 비슷한 모양의 자체 UI를 만들어 사용자를 오도하지 않는다.
- 오버레이(다른 앱 위에 표시)는 꼭 필요한 경우가 아니면 쓰지 않는다. 최근 OS는 오버레이를 점점 제한한다.

<br>

# 18. 테스트 전략

## 18.1 어떤 기기에서 확인할지

- 일반 폰 세로
- 폰 가로
- 폴더블 접힘, 펼침, 반 접힘
- 태블릿
- 분할 화면과 팝업 뷰
- 큰 폰트(1.3, 2.0)와 화면 확대
- 다크 모드
- 제스처 내비게이션, 3버튼

## 18.2 에뮬레이터와 원격 기기

- Android Studio의 Device Manager에서 Resizable, Foldable, Tablet 에뮬레이터를 만들 수 있다.
- Resizable 에뮬레이터는 폰, 폴더블, 태블릿 크기를 한 에뮬레이터에서 전환해 볼 수 있어 효율적이다.
- 삼성은 Remote Test Lab을 통해 실제 Galaxy 기기를 원격으로 빌려 테스트하는 서비스를 제공해 왔다. 현재 제공 범위는 삼성 개발자 사이트에서 확인한다.
- One UI 베타 기기가 없다면 에뮬레이터의 Android 17 이미지로 플랫폼 변화를 먼저 확인한다.

## 18.3 Compose UI 테스트에서 환경 흉내내기

```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun largeFontDoesNotClipButtonText() {
    composeTestRule.setContent {
        DeviceConfigurationOverride(
            DeviceConfigurationOverride.FontScale(2f)
        ) {
            AppTheme {
                SettingRow(
                    title = "알림 설정",
                    description = "긴 설명이 들어가는 설정 항목입니다",
                    trailing = { Switch(checked = true, onCheckedChange = null) }
                )
            }
        }
    }

    composeTestRule.onNodeWithText("알림 설정").assertIsDisplayed()
}
```

- `DeviceConfigurationOverride`로 폰트 스케일, 다크 모드, 강제 크기 등을 테스트에서 바꿀 수 있다.
- 다크 모드와 크기는 각각 `DarkMode(true)`, `ForcedSize(DpSize(...))`로 지정한다.

## 18.4 스크린샷 테스트

- Paparazzi, Roborazzi, Compose Preview Screenshot Testing 등으로 화면 조합을 자동 비교할 수 있다.
- 다음 조합을 Preview 어노테이션으로 한 번에 만들어 두면 효율적이다.

```kotlin
@Preview(name = "기본", showBackground = true)
@Preview(name = "큰 글자", fontScale = 2f, showBackground = true)
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "폴더블", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "태블릿", device = Devices.TABLET, showBackground = true)
annotation class AppPreviews

@AppPreviews
@Composable
fun SettingRowPreview() {
    AppTheme {
        SettingRow(
            title = "알림 설정",
            description = "푸시 알림을 켜거나 끕니다",
            trailing = { Switch(checked = true, onCheckedChange = null) }
        )
    }
}
```

- 멀티 Preview 어노테이션을 디자인 시스템 모듈에 정의해 두고 모든 화면 모듈이 공유한다.

## 18.5 접근성 점검

- TalkBack을 켜서 화면 읽기 순서와 설명을 확인한다.
- 삼성 기기에서는 삼성과 구글의 TalkBack이 통합되는 방향이므로 최신 기기에서 한 번 더 확인한다.
- 터치 영역은 최소 48dp를 지킨다.
- 색 대비는 텍스트 기준 4.5:1 이상을 목표로 한다.

```kotlin
IconButton(
    onClick = onClose,
    modifier = Modifier.semantics { contentDescription = "닫기" }
) {
    Icon(Icons.Default.Close, contentDescription = null)
}
```

## 18.6 삼성 기기 점검 순서

1. 개발자 옵션과 USB 디버깅 활성화. 연결이 안 되면 Auto Blocker 확인
2. 설정에서 글꼴 크기와 화면 확대를 최대로 올려 주요 화면 확인
3. 팝업 뷰와 분할 화면으로 열어 보기
4. 폴더블이면 접기, 펴기, 반 접기 반복
5. 다크 모드 전환 중 앱이 정상 유지되는지 확인
6. 내비게이션을 3버튼과 제스처로 번갈아 확인
7. 배터리 최적화 상태에서 알림과 백그라운드 작업 확인

<br>

# 19. 체크리스트

## 19.1 Edge-to-Edge

- [ ] `enableEdgeToEdge()`를 `setContent` 전에 호출했다
- [ ] Scaffold의 `innerPadding`을 콘텐츠에 적용했다
- [ ] 스크롤 목록에 `contentPadding`으로 insets를 반영했다
- [ ] 가로 모드에서 컷아웃과 좌우 insets를 확인했다
- [ ] 키보드가 올라올 때 입력창이 가려지지 않는다

## 19.2 화면 크기

- [ ] 방향 고정과 `resizeableActivity=false` 선언을 점검했다
- [ ] 창 크기 클래스 기준으로 레이아웃을 분기한다
- [ ] 큰 화면에서 콘텐츠 최대 폭을 제한했다
- [ ] 분할 화면과 팝업 뷰에서 UI가 깨지지 않는다
- [ ] 접었다 펴도 상태가 유지된다

## 19.3 텍스트와 테마

- [ ] 텍스트 크기는 sp, 레이아웃은 dp를 쓴다
- [ ] 고정 height 대신 `heightIn(min)`을 쓴다
- [ ] 폰트 스케일 2.0에서 주요 화면을 확인했다
- [ ] 다크 모드에서 색, 이미지, 상태바 아이콘을 확인했다
- [ ] 색은 `MaterialTheme.colorScheme`으로 참조한다

## 19.4 내비게이션과 Back

- [ ] `BackHandler`의 `enabled`를 조건에 맞게 제어한다
- [ ] Predictive Back 설정을 확인했다
- [ ] 가장자리 제스처와 충돌하는 UI가 없다
- [ ] 하단 고정 요소가 내비게이션 바에 가려지지 않는다

## 19.5 보안과 배포

- [ ] 요청 권한이 모두 기능과 연결된다
- [ ] 연락처, 사진 등은 선택기 방식으로 대체 가능한지 검토했다
- [ ] 민감 화면에 `FLAG_SECURE` 적용 여부를 결정했다
- [ ] 테스트 빌드는 정식 배포 경로로 전달한다
- [ ] 기기 고유 ID 대신 서버 계정 기반 식별을 쓴다
- [ ] Secure Folder, 듀얼 메신저에서 동작을 확인했다

<br>

# 정리

1. Android UI는 플랫폼, Material 디자인 시스템, 제조사 스킨의 세 층으로 이해한다. 앱은 Material 3를 기준으로 만들고 One UI에서 어색한지만 확인한다.
2. One UI는 AOSP 위에 삼성이 얹은 스킨이고, 앱이 쓰는 Android API 자체는 바꾸지 않는다. 그래서 대응의 핵심은 코드 분기가 아니라 표준 API를 제대로 쓰는 것이다.
3. One UI 9.0은 Android 17 기반이며 8.5의 큰 디자인 변화 뒤에 나온 다듬기 성격의 업데이트다. 더 큰 변화는 9.5로 예상된다.
4. Android 15부터 edge-to-edge가 기본이므로 `enableEdgeToEdge()`, Scaffold의 `innerPadding`, `WindowInsets`를 정확히 다뤄야 한다.
5. Android 16/17에서 대화면의 방향과 크기 조절 제한이 단계적으로 사라진다. API 37 타깃팅 전에 `WindowSizeClass` 기반 적응형 레이아웃을 준비한다.
6. 폴더블은 Jetpack WindowManager의 `FoldingFeature`로 대응하고, 상태는 ViewModel과 `rememberSaveable`에 둬서 접고 펴도 유지한다.
7. 삼성 사용자는 큰 폰트, 화면 확대, 팝업 뷰, DeX, 엣지 패널을 많이 쓴다. 고정 크기 UI를 피하고 `heightIn(min)`, `weight`, `FlowRow`를 활용한다.
8. 보안은 One UI 9의 악성 앱 차단, 알 수 없는 앱 관리, Android 17의 시스템 연락처 선택기와 Advanced Protection Mode가 핵심이다. 앱 개발자는 최소 권한, 선택기 방식 활용, 정식 배포 경로, 민감 화면 보호로 대응한다.
9. 개발 중 삼성 기기 연결이 안 되면 Auto Blocker를 먼저 확인한다.
10. 테스트는 Resizable 에뮬레이터, `DeviceConfigurationOverride`, 멀티 Preview 어노테이션으로 폰트, 다크, 크기 조합을 자동화하고 최종은 실기기로 확인한다.
