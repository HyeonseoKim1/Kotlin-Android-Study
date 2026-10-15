# 모바일 UX/UI와 안드로이드 디자인 시스템

1. UX와 UI의 차이
2. 모바일 UX 핵심 원칙
3. 모바일 UI 기본 요소
4. Material Design 3 개요
5. Color System
6. Typography
7. Shape와 Elevation
8. Spacing과 터치 타겟
9. Adaptive Layout
10. Compose에서 MaterialTheme 구성
11. 커스텀 디자인 시스템 설계
12. 멀티모듈에서 디자인 시스템 모듈 분리
13. 접근성
14. 다크 모드와 Dynamic Color
15. 정리

<br>

# 1. UX와 UI의 차이

| 구분 | UX (User Experience) | UI (User Interface) |
|------|----------------------|---------------------|
| 관점 | 사용자가 제품을 쓰며 느끼는 전체 경험 | 화면에서 눈에 보이고 만져지는 요소 |
| 대상 | 흐름, 구조, 맥락, 감정 | 색, 타이포, 버튼, 아이콘, 레이아웃 |
| 질문 | "목적을 쉽게 달성하는가" | "보기 좋고 일관적인가" |
| 산출물 | 유저 플로우, 와이어프레임, 정보 구조 | 비주얼 디자인, 컴포넌트, 스타일 가이드 |

- UX는 "무엇을 어떤 순서로", UI는 "어떻게 보이게"를 결정한다.
- UI가 아무리 예뻐도 흐름이 꼬이면 나쁜 UX이고, 흐름이 좋아도 UI가 불일치하면 신뢰가 떨어진다.
- 디자인 시스템은 이 둘을 개발 관점에서 일관되게 유지해 주는 장치다.

<br>

# 2. 모바일 UX 핵심 원칙

## 2.1 모바일 환경의 특성

- 작은 화면, 터치 입력, 한 손 사용
- 이동 중 사용, 짧은 세션, 잦은 인터럽트(알림, 전화)
- 네트워크 불안정, 배터리 제약
- 따라서 "적게 보여주고, 빨리 끝내게" 설계해야 한다.

## 2.2 주요 원칙

1. 단순함: 한 화면에 하나의 핵심 목적
2. 일관성: 같은 동작은 같은 모양과 위치
3. 피드백: 터치하면 즉시 반응(ripple, 상태 변화, 로딩 표시)
4. 가시성: 현재 위치와 가능한 행동이 보여야 함
5. 오류 예방과 복구: 되돌리기(Undo), 확인 다이얼로그, 친절한 에러 메시지
6. 인지 부하 최소화: 선택지를 줄이고 기본값 제공
7. 접근성: 모든 사용자가 사용할 수 있어야 함

## 2.3 법칙 정리

| 법칙 | 내용 | 모바일 적용 |
|------|------|-------------|
| Fitts의 법칙 | 목표가 크고 가까울수록 빨리 누른다 | 터치 타겟을 크게, 주요 버튼은 엄지 닿는 영역에 |
| Hick의 법칙 | 선택지가 많을수록 결정이 느려진다 | 메뉴/옵션 수 제한, 단계 분리 |
| Jakob의 법칙 | 사용자는 다른 앱의 방식에 익숙하다 | 플랫폼 관례(뒤로가기, 바텀 내비) 준수 |
| Miller의 법칙 | 단기 기억은 대략 7±2개 | 정보를 묶음(chunk)으로 제공 |
| 피크엔드 법칙 | 절정과 마지막 순간이 기억을 좌우 | 완료 화면, 성공 피드백에 신경 |

## 2.4 엄지 영역 (Thumb Zone)

- 한 손 사용 시 화면 하단이 가장 편하고, 상단 모서리는 닿기 어렵다.
- 그래서 Bottom Navigation, FAB, 하단 시트 같은 패턴이 모바일에서 선호된다.
- 위험한 동작(삭제 등)은 일부러 닿기 어려운 곳이나 확인 단계 뒤에 둔다.

<br>

# 3. 모바일 UI 기본 요소

## 3.1 시각 계층 (Visual Hierarchy)

- 크기, 굵기, 색 대비, 여백으로 중요도를 표현한다.
- 한 화면에서 가장 중요한 행동(Primary action)은 하나만 강조한다.

## 3.2 구성 요소

1. 레이아웃: 그리드, 여백, 정렬
2. 컬러: 브랜드 색, 의미 색(에러, 성공), 배경과 표면
3. 타이포그래피: 서체, 크기, 굵기, 행간
4. 아이콘과 이미지: 의미 전달, 일관된 스타일
5. 컴포넌트: 버튼, 카드, 리스트, 입력창, 다이얼로그, 내비게이션
6. 모션: 상태 전환, 맥락 유지(과하지 않게)

## 3.3 내비게이션 패턴

| 패턴 | 용도 | 비고 |
|------|------|------|
| Bottom Navigation / NavigationBar | 최상위 목적지 3~5개 | 가장 흔한 패턴 |
| Navigation Drawer | 목적지가 많을 때 | 태블릿에서는 상시 노출 |
| Navigation Rail | 중간/큰 화면 | 세로 방향 아이콘 바 |
| Top App Bar | 제목, 뒤로가기, 액션 | 스크롤 시 collapse 가능 |
| Tabs | 같은 레벨 콘텐츠 전환 | 스와이프와 함께 사용 |

<br>

# 4. Material Design 3 개요

- Material Design은 Google의 디자인 언어이며, 현재 안드로이드의 표준은 Material Design 3 (Material You)다.
- 핵심 키워드: 개인화(Dynamic Color), 접근성, 적응형 레이아웃, 표현력 있는 컴포넌트.
- 디자인 시스템은 크게 세 요소로 구성된다.

1. Design Tokens: 색, 타이포, 모양, 간격 같은 "값"
2. Components: 토큰을 사용해 만든 재사용 UI (Button, Card 등)
3. Guidelines: 언제 어떻게 쓰는지에 대한 규칙

## 4.1 Design Token이란

- 디자인 결정을 이름 붙인 변수로 추상화한 것이다.
- 값(`#6750A4`)이 아니라 역할(`primary`)로 참조하기 때문에 테마 교체가 쉽다.

```kotlin
// 나쁜 예: 값을 직접 사용
Text(text = "제목", color = Color(0xFF6750A4))

// 좋은 예: 역할(토큰)로 참조
Text(text = "제목", color = MaterialTheme.colorScheme.primary)
```

## 4.2 Compose Material3 의존성

```kotlin
// libs.versions.toml 사용 시 예시
dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
}
```

<br>

# 5. Color System

## 5.1 Color Roles

M3는 색을 "역할" 단위로 정의한다. 각 역할은 배경과 그 위의 콘텐츠 색이 쌍(`on`)으로 존재한다.

| 역할 | 설명 |
|------|------|
| primary / onPrimary | 주요 동작, 강조 요소(버튼, 활성 상태) |
| primaryContainer / onPrimaryContainer | primary보다 약한 강조 영역 |
| secondary / onSecondary | 보조 요소(필터 칩 등) |
| tertiary / onTertiary | 대비를 주는 포인트 색 |
| error / onError | 오류 상태 |
| background / onBackground | 화면 배경 |
| surface / onSurface | 카드, 시트, 메뉴 등 표면 |
| surfaceVariant / onSurfaceVariant | 덜 강조된 표면, 보조 텍스트 |
| outline / outlineVariant | 테두리, 구분선 |
| surfaceContainerLowest ~ Highest | 표면의 단계별 톤(elevation 대체) |

- `on` 색은 항상 짝이 되는 배경 위에서만 사용해야 대비가 보장된다.

## 5.2 ColorScheme 정의

```kotlin
private val LightColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
)
```

- 실무에서는 Material Theme Builder(디자이너 도구)로 시드 색에서 전체 팔레트를 생성해 가져오는 경우가 많다.

## 5.3 색 사용 규칙

1. 브랜드 색을 남발하지 않고 primary는 핵심 동작에만 사용
2. 의미 색(에러, 성공, 경고)은 색만으로 전달하지 않고 아이콘/텍스트를 병행
3. 텍스트 대비는 WCAG 기준 4.5:1 이상(큰 텍스트는 3:1)

<br>

# 6. Typography

## 6.1 Type Scale

M3는 5개 역할 x 3개 크기 = 15개 스타일을 제공한다.

| 역할 | 스타일 | 용도 |
|------|--------|------|
| Display | Large / Medium / Small | 매우 큰 숫자, 짧은 강조 |
| Headline | Large / Medium / Small | 화면의 주요 제목 |
| Title | Large / Medium / Small | 섹션 제목, 앱바 제목 |
| Body | Large / Medium / Small | 본문 |
| Label | Large / Medium / Small | 버튼, 캡션, 칩 |

```kotlin
Text(
    text = "오늘의 공연",
    style = MaterialTheme.typography.titleLarge,
)
Text(
    text = "공연 기록을 남겨보세요",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

## 6.2 커스텀 Typography

```kotlin
val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
)
```

## 6.3 주의점

1. 텍스트 크기는 `sp`로 지정해 사용자의 글자 크기 설정을 따른다.
2. 폰트 크기를 키워도 레이아웃이 깨지지 않도록 `maxLines`, `overflow`, 스크롤을 고려한다.
3. 본문 최소 크기는 14~16sp 권장.

<br>

# 7. Shape와 Elevation

## 7.1 Shape

- 모서리 둥글기도 토큰으로 관리한다 (ExtraSmall ~ ExtraLarge).

```kotlin
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
```

## 7.2 Elevation

- M3에서는 그림자 대신 표면 색의 톤 차이(`surfaceContainer*`)로 높이를 표현하는 것이 기본이다.
- 그림자(`shadowElevation`)는 보조적으로 사용한다.

```kotlin
Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = MaterialTheme.shapes.medium,
    tonalElevation = 2.dp,
) {
    // content
}
```

<br>

# 8. Spacing과 터치 타겟

## 8.1 8dp 그리드

- 모든 여백과 크기를 4dp, 8dp의 배수로 맞추면 시각적 리듬이 생긴다.
- 자주 쓰는 값: 4, 8, 12, 16, 24, 32, 48

```kotlin
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}
```

## 8.2 터치 타겟

- 최소 48dp x 48dp (Material 권장), 타겟 사이 간격 8dp 이상.
- 아이콘이 24dp여도 터치 영역은 48dp로 확보한다.
- Compose Material3의 `IconButton`, `Checkbox` 등은 기본적으로 최소 터치 영역을 보장한다.

```kotlin
// 직접 만든 클릭 요소에서 최소 터치 영역 보장
Box(
    modifier = Modifier
        .minimumInteractiveComponentSize()
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Icon(Icons.Default.Close, contentDescription = "닫기")
}
```

## 8.3 화면 여백

- 기본 좌우 패딩 16dp, 큰 화면에서는 24dp 이상.
- 시스템 바(상태바, 내비게이션 바)와 겹치지 않도록 WindowInsets를 처리한다 (Edge-to-Edge).

```kotlin
Scaffold(
    modifier = Modifier.fillMaxSize(),
) { innerPadding ->
    LazyColumn(
        contentPadding = innerPadding,
    ) { /* items */ }
}
```

<br>

# 9. Adaptive Layout

## 9.1 Window Size Class

폰, 폴더블, 태블릿 등 다양한 화면에 대응하기 위해 창 크기를 3단계로 분류한다.

| 구분 | Compact | Medium | Expanded |
|------|---------|--------|----------|
| 너비 | 600dp 미만 | 600~840dp | 840dp 이상 |
| 대표 기기 | 대부분의 폰(세로) | 폴더블, 소형 태블릿 | 태블릿, 데스크톱 |
| 내비게이션 | NavigationBar | NavigationRail | Drawer(Permanent) |

```kotlin
@Composable
fun AppRoot(windowSizeClass: WindowSizeClass) {
    when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> CompactLayout()
        WindowWidthSizeClass.Medium -> MediumLayout()
        WindowWidthSizeClass.Expanded -> ExpandedLayout()
    }
}
```

```kotlin
// Activity에서 계산
setContent {
    val windowSizeClass = calculateWindowSizeClass(this)
    AppTheme {
        AppRoot(windowSizeClass)
    }
}
```

## 9.2 Canonical Layouts

1. List-Detail: 목록과 상세를 나란히 (Expanded에서 2-pane)
2. Supporting Pane: 메인 콘텐츠 + 보조 패널
3. Feed: 카드 그리드 형태의 콘텐츠

- Compose에서는 `NavigationSuiteScaffold`, `ListDetailPaneScaffold` 같은 adaptive 라이브러리로 구현할 수 있다.

<br>

# 10. Compose에서 MaterialTheme 구성

## 10.1 기본 구조

```kotlin
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
```

## 10.2 Theme 값 접근

```kotlin
val color = MaterialTheme.colorScheme.primary
val style = MaterialTheme.typography.bodyLarge
val shape = MaterialTheme.shapes.medium
```

- `MaterialTheme`는 `CompositionLocal`로 값을 내려주기 때문에 하위 어디서든 접근 가능하다.
- 중첩 `MaterialTheme`으로 특정 영역만 테마를 덮어쓸 수도 있다.

## 10.3 CompositionLocal 개념

```kotlin
// MaterialTheme 내부 동작을 단순화한 형태
val LocalColorScheme = staticCompositionLocalOf { lightColorScheme() }

@Composable
fun MyTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalColorScheme provides myColorScheme) {
        content()
    }
}
```

- 값이 거의 바뀌지 않는 테마는 `staticCompositionLocalOf`, 자주 바뀌는 값은 `compositionLocalOf`를 쓴다.
- static은 값이 바뀌면 하위 전체가 recomposition되므로 테마처럼 드물게 바뀌는 값에 적합하다.

<br>

# 11. 커스텀 디자인 시스템 설계

Material3만으로 부족할 때(브랜드 고유 색, 추가 토큰) 자체 디자인 시스템을 얹는다.

## 11.1 설계 원칙

1. Material3를 버리지 않고 확장한다 (필요한 토큰만 추가)
2. 컴포넌트는 토큰만 참조하고 하드코딩 값을 쓰지 않는다
3. 화면(Feature)은 디자인 시스템 컴포넌트만 조합한다
4. 컴포넌트 API는 단순하게, 슬롯 기반으로 유연하게

## 11.2 커스텀 토큰 추가

```kotlin
@Immutable
data class AppColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
)

private val LightAppColors = AppColors(
    success = Color(0xFF2E7D32),
    onSuccess = Color.White,
    warning = Color(0xFFED6C02),
    onWarning = Color.White,
)

private val DarkAppColors = AppColors(
    success = Color(0xFF81C784),
    onSuccess = Color.Black,
    warning = Color(0xFFFFB74D),
    onWarning = Color.Black,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

// 접근용 확장 프로퍼티
val MaterialTheme.appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current
```

```kotlin
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

// 사용
Text("저장 완료", color = MaterialTheme.appColors.success)
```

## 11.3 재사용 컴포넌트 예시

```kotlin
enum class AppButtonType { Primary, Secondary, Text }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: AppButtonType = AppButtonType.Primary,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val content: @Composable RowScope.() -> Unit = {
        leadingIcon?.let {
            it()
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }

    when (type) {
        AppButtonType.Primary -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
        AppButtonType.Secondary -> OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
        AppButtonType.Text -> TextButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
    }
}
```

## 11.4 컴포넌트 API 설계 팁

1. `modifier`는 항상 첫 번째 선택 파라미터로 제공
2. 상태는 호이스팅하고 컴포넌트는 stateless로 유지
3. 내용 교체가 필요한 곳은 `@Composable` 슬롯으로 열어준다
4. 기본값은 테마 토큰에서 가져온다
5. 상태별 스타일(enabled, pressed, disabled)은 Material의 `Defaults` 객체 패턴을 참고

```kotlin
object AppCardDefaults {
    @Composable
    fun colors() = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
}
```

## 11.5 Preview 관리

```kotlin
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large Font", fontScale = 1.5f)
annotation class ThemePreviews

@ThemePreviews
@Composable
private fun AppButtonPreview() {
    AppTheme {
        Surface {
            AppButton(text = "저장", onClick = {})
        }
    }
}
```

- 라이트/다크/큰 글꼴 Preview를 어노테이션으로 묶어 모든 컴포넌트에 적용하면 시각 회귀를 빠르게 발견할 수 있다.

<br>

# 12. 멀티모듈에서 디자인 시스템 모듈 분리

## 12.1 모듈 구조

```text
:app
:feature:home
:feature:record
:core:designsystem     <- 테마, 토큰, 공통 컴포넌트
:core:ui               <- 도메인을 아는 공통 UI(옵션)
:core:model
```

- `:core:designsystem`은 비즈니스 로직과 도메인 모델을 모른다. 순수 UI 기반만 둔다.
- 모든 `:feature` 모듈이 `:core:designsystem`에만 의존해 일관된 UI를 얻는다.

## 12.2 패키지 구성 예시

```text
core/designsystem/
  theme/
    Color.kt
    Type.kt
    Shape.kt
    Spacing.kt
    Theme.kt
  component/
    AppButton.kt
    AppTextField.kt
    AppTopBar.kt
    AppDialog.kt
  icon/
    AppIcons.kt
```

## 12.3 아이콘 관리

```kotlin
object AppIcons {
    val Back = Icons.AutoMirrored.Filled.ArrowBack
    val Close = Icons.Default.Close
    val Add = Icons.Default.Add
}
```

- 아이콘을 한곳에 모으면 교체가 쉽고, RTL 대응용 `AutoMirrored` 사용 여부도 통일할 수 있다.

## 12.4 MVI와의 관계

- 디자인 시스템 컴포넌트는 stateless이며 MVI의 State를 받아 그리고 Intent는 콜백으로 올린다.
- 컴포넌트 내부에서 ViewModel을 직접 참조하지 않는다.

```kotlin
@Composable
fun RecordScreen(
    state: RecordUiState,
    onIntent: (RecordIntent) -> Unit,
) {
    AppButton(
        text = "기록 저장",
        enabled = state.canSave,
        onClick = { onIntent(RecordIntent.Save) },
    )
}
```

<br>

# 13. 접근성

## 13.1 핵심 체크리스트

1. 모든 의미 있는 이미지/아이콘에 `contentDescription` 제공, 장식용은 `null`
2. 터치 타겟 48dp 이상
3. 텍스트 대비 4.5:1 이상
4. 글꼴 크기 확대(fontScale)에도 레이아웃 유지
5. 색만으로 상태를 전달하지 않기
6. TalkBack으로 읽는 순서와 동작 확인

## 13.2 Compose Semantics

```kotlin
Icon(
    imageVector = Icons.Default.Favorite,
    contentDescription = "좋아요",
)

// 여러 요소를 하나로 묶어 읽기
Row(
    modifier = Modifier.semantics(mergeDescendants = true) {
        contentDescription = "공연 제목, 2026년 10월 15일"
    },
) {
    Text("공연 제목")
    Text("2026.10.15")
}

// 커스텀 역할과 상태
Box(
    modifier = Modifier
        .toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
)
```

## 13.3 테스트 도구

- Accessibility Scanner 앱: 터치 타겟, 대비 자동 점검
- Android Studio의 Compose Preview 접근성 모드
- Compose UI 테스트에서 `onNodeWithContentDescription`으로 라벨 검증

<br>

# 14. 다크 모드와 Dynamic Color

## 14.1 다크 모드 원칙

1. 순수 검정(`#000000`)보다 짙은 회색 표면 사용 권장 (눈의 피로, 번짐 감소)
2. 라이트 테마 색을 단순 반전하지 않고 별도로 정의
3. 이미지, 일러스트도 다크 대응 확인

## 14.2 Dynamic Color

- Android 12(API 31) 이상에서 사용자의 배경화면에서 색을 추출해 앱 테마에 반영하는 기능.
- 브랜드 색이 중요한 앱은 `dynamicColor = false`로 끄거나 사용자 설정으로 제공한다.
- 하위 버전은 정적 ColorScheme으로 대체한다 (10장 코드 참고).

## 14.3 테마 설정 저장 예시

```kotlin
enum class ThemeMode { System, Light, Dark }

@Composable
fun AppRoot(themeMode: ThemeMode) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    AppTheme(darkTheme = darkTheme) {
        // content
    }
}
```

- 사용자가 선택한 모드는 DataStore에 저장해 앱 시작 시 반영한다.

<br>

# 정리

1. UX는 경험 전체(흐름, 맥락), UI는 눈에 보이는 요소이며 디자인 시스템이 둘의 일관성을 유지해 준다.
2. 모바일 UX는 작은 화면, 한 손, 짧은 세션을 전제로 단순함, 일관성, 피드백, 접근성을 우선한다. Fitts, Hick, Jakob 법칙과 엄지 영역이 실무 판단의 기준이 된다.
3. 안드로이드의 표준 디자인 언어는 Material Design 3이며, 색(Color roles), 타이포(Type scale), 모양(Shape), 높이(Tonal elevation), 간격(8dp grid)을 토큰으로 관리한다.
4. 토큰은 값이 아니라 역할로 참조해야 테마 교체와 다크 모드, Dynamic Color 대응이 쉽다.
5. 터치 타겟은 48dp 이상, 텍스트 대비는 4.5:1 이상, 글꼴은 sp 단위를 지켜 접근성을 확보한다.
6. Compose에서는 `MaterialTheme`이 `CompositionLocal`로 토큰을 내려주며, 부족한 토큰은 `CompositionLocalProvider`와 확장 프로퍼티로 추가한다.
7. 화면 크기는 `WindowSizeClass`(Compact, Medium, Expanded)로 나누어 NavigationBar, Rail, Drawer 등을 전환한다.
8. 멀티모듈에서는 `:core:designsystem` 모듈에 테마와 공통 컴포넌트를 모으고, feature 모듈은 이 모듈만 통해 UI를 구성한다.
9. 디자인 시스템 컴포넌트는 stateless로 만들고 MVI의 State는 파라미터로, Intent는 콜백으로 연결한다.
10. Light, Dark, Large Font Preview를 어노테이션으로 묶어 모든 컴포넌트에서 한 번에 확인하는 습관이 시각 회귀 방지에 효과적이다.
