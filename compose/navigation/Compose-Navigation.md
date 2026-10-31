# Compose Navigation

<br>

# 목차

1. Compose Navigation이란?
2. 프로젝트 설정
3. 핵심 구성 요소
4. 기본 사용법 (Type-safe Navigation)
5. 인자 전달
6. 이동 옵션 (popUpTo, launchSingleTop)
7. Back Stack 동작 원리
8. Bottom Navigation
9. Nested Navigation Graph
10. ViewModel과 Hilt 연동
11. 화면 간 결과 전달
12. Deep Link
13. 화면 전환 애니메이션
14. MVI에서 Navigation 처리
15. 멀티모듈에서의 Navigation
16. 테스트
17. 자주 하는 실수
18. Navigation 3
19. 정리

<br>

# 1. Compose Navigation이란?

## 1.1 개요

- Jetpack Compose에서 **화면 간 이동**을 처리하는 라이브러리 (`navigation-compose`)
- 기존 Fragment 기반 Navigation Component를 Compose용으로 만든 것이다.
- 단일 Activity에 여러 Composable 화면을 두는 **Single Activity** 구조를 전제로 한다.

## 1.2 Fragment Navigation과 비교

| 구분 | Fragment Navigation | Compose Navigation |
| --- | --- | --- |
| 화면 단위 | Fragment | Composable |
| 그래프 정의 | XML (nav_graph.xml) | Kotlin 코드 (DSL) |
| 인자 전달 | Safe Args 플러그인 | Type-safe Route (kotlinx.serialization) |
| Back Stack | FragmentManager | NavController (NavBackStackEntry) |
| 전환 애니메이션 | XML anim / Transition | `enterTransition` 등 Compose 애니메이션 |

## 1.3 Back Stack 관련 노트와의 관계

- Activity 단위의 Task / Back Stack은 `Task & Back Stack` 노트 참고
- 여기서 다루는 Back Stack은 **앱 내부(NavController)가 관리하는 Back Stack**이다.
- Compose Navigation은 한 Activity 안에서 Composable 목적지를 push / pop 하는 구조다.

<br>

# 2. 프로젝트 설정

## 2.1 의존성

```kotlin
// libs.versions.toml
[versions]
navigation = "2.8.9"          // 최신 안정 버전 확인 후 사용
kotlinSerialization = "1.7.3"
hiltNavigationCompose = "1.2.0"

[libraries]
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinSerialization" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

```kotlin
// build.gradle.kts (module)
plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.hilt.navigation.compose)
}
```

- **Type-safe Navigation은 navigation-compose 2.8.0 이상**부터 지원한다.
- 버전은 변경될 수 있으니 공식 릴리스 노트에서 최신 안정 버전을 확인한다.

<br>

# 3. 핵심 구성 요소

| 구성 요소 | 역할 |
| --- | --- |
| `NavController` | 이동(navigate), 뒤로가기(popBackStack), Back Stack 상태 관리 |
| `NavHost` | 현재 목적지를 화면에 그리는 컨테이너 |
| `NavGraph` | 목적지(Destination)들의 집합과 시작 목적지 |
| `NavDestination` | 이동할 수 있는 하나의 화면 |
| `NavBackStackEntry` | Back Stack의 한 항목. 인자, Lifecycle, ViewModelStore, SavedStateHandle 보유 |
| Route | 목적지를 식별하는 값. 이제 **타입(클래스/객체)** 으로 정의한다. |

## 3.1 NavBackStackEntry

- Back Stack의 각 항목은 자신만의 **Lifecycle**, **ViewModelStore**, **SavedStateHandle**을 가진다.
- 그래서 화면(항목)이 Back Stack에서 제거될 때 해당 항목의 ViewModel도 함께 정리된다.
- 화면이 Back Stack에 남아 있는 동안(다른 화면 위에 쌓인 상태)에는 ViewModel이 유지된다.

<br>

# 4. 기본 사용법 (Type-safe Navigation)

## 4.1 Route 정의

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data object Home            // 인자 없는 화면

@Serializable
data class Detail(val id: Long)   // 인자 있는 화면

@Serializable
data class Search(val keyword: String? = null)  // 선택 인자
```

- 인자 없는 화면은 `data object`, 인자 있는 화면은 `data class`로 정의한다.
- 클래스의 프로퍼티가 곧 **인자**다. 기본값이 있으면 선택 인자가 된다.
- 예전의 문자열 route(`"detail/{id}"`)는 오타, 타입 오류 위험이 있어 더 이상 권장하지 않는다.

## 4.2 NavHost 구성

```kotlin
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = modifier,
    ) {
        composable<Home> {
            HomeScreen(
                onItemClick = { id -> navController.navigate(Detail(id)) },
                onSearchClick = { navController.navigate(Search()) },
            )
        }

        composable<Detail> {
            DetailScreen(onBackClick = { navController.popBackStack() })
        }

        composable<Search> {
            SearchScreen(onBackClick = { navController.popBackStack() })
        }
    }
}
```

## 4.3 Activity에서 호스팅

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppNavHost()
            }
        }
    }
}
```

## 4.4 이동과 뒤로가기

```kotlin
// 이동
navController.navigate(Detail(id = 1L))

// 뒤로가기 (현재 화면 pop)
navController.popBackStack()

// 특정 목적지까지 pop
navController.popBackStack<Home>(inclusive = false)

// 위로 이동 (Up)
navController.navigateUp()
```

- `popBackStack()`은 pop 성공 여부를 Boolean으로 반환한다. Back Stack이 비어 있으면 false다.
- `navigateUp()`은 앱 바의 Up 버튼 동작에 사용한다. 현재 앱 안에서 위로 갈 곳이 없으면 false를 반환한다.

<br>

# 5. 인자 전달

## 5.1 Composable에서 받기

```kotlin
composable<Detail> { backStackEntry ->
    val detail: Detail = backStackEntry.toRoute()
    DetailScreen(id = detail.id)
}
```

- `toRoute<T>()`로 Route 객체를 그대로 복원한다.

## 5.2 ViewModel에서 받기 (권장)

```kotlin
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DetailRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Detail>()

    val uiState: StateFlow<DetailUiState> = repository.observe(route.id)
        .map { DetailUiState.Success(it) as DetailUiState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DetailUiState.Loading,
        )
}
```

- `SavedStateHandle.toRoute<T>()`로 ViewModel이 직접 인자를 읽는다.
- 화면(Composable)이 인자를 꺼내서 ViewModel에 넘기는 코드가 사라지고, 프로세스 종료 후 복원 시에도 동일하게 동작한다.

## 5.3 지원 타입

- 기본 지원: `Int`, `Long`, `Float`, `Boolean`, `String`, `Enum`, 그리고 이것들의 배열/리스트
- `@Serializable` 객체를 인자 안에 중첩하려면 **custom NavType**이 필요하다.

```kotlin
@Serializable
data class Filter(val category: String, val minPrice: Int)

@Serializable
data class ProductList(val filter: Filter)

val FilterNavType = object : NavType<Filter>(isNullableAllowed = false) {
    override fun get(bundle: Bundle, key: String): Filter? =
        bundle.getString(key)?.let { Json.decodeFromString(it) }

    override fun parseValue(value: String): Filter =
        Json.decodeFromString(Uri.decode(value))

    override fun serializeAsValue(value: Filter): String =
        Uri.encode(Json.encodeToString(value))

    override fun put(bundle: Bundle, key: String, value: Filter) {
        bundle.putString(key, Json.encodeToString(value))
    }
}

composable<ProductList>(
    typeMap = mapOf(typeOf<Filter>() to FilterNavType)
) { /* ... */ }
```

## 5.4 인자 전달 원칙

- 인자에는 **식별자(ID)나 가벼운 값**만 넘기고, 실제 데이터는 목적지에서 Repository로 조회한다.
- 큰 객체를 인자로 넘기면 Bundle 크기 제한(약 1MB, 트랜잭션 공유) 때문에 `TransactionTooLargeException` 위험이 있다.
- 화면 간 데이터 불일치도 막을 수 있다. (이동 중에 원본이 바뀌어도 목적지는 최신 데이터를 조회한다.)

<br>

# 6. 이동 옵션 (popUpTo, launchSingleTop)

## 6.1 navigate 옵션

```kotlin
navController.navigate(Home) {
    // Back Stack에서 특정 목적지까지 제거
    popUpTo<Login> { inclusive = true }

    // 같은 목적지가 최상단이면 중복 생성하지 않음
    launchSingleTop = true

    // 이전에 saveState로 저장된 상태 복원
    restoreState = true
}
```

| 옵션 | 설명 |
| --- | --- |
| `popUpTo<T>` | 이동 전에 T 목적지까지 Back Stack을 제거 |
| `inclusive = true` | popUpTo 대상 자신도 제거 |
| `saveState = true` | popUpTo로 제거되는 항목들의 상태를 저장 |
| `restoreState = true` | 이동할 목적지에 저장된 상태가 있으면 복원 |
| `launchSingleTop` | 최상단이 같은 목적지면 새로 쌓지 않음 |

## 6.2 대표 시나리오

### 로그인 후 홈으로 이동 (로그인 화면 제거)

```kotlin
navController.navigate(Home) {
    popUpTo<Login> { inclusive = true }
}
```

- 로그인 성공 후 뒤로가기를 눌렀을 때 로그인 화면으로 돌아가지 않게 한다.

### 로그아웃 (전체 스택 초기화)

```kotlin
navController.navigate(Login) {
    popUpTo(navController.graph.id) { inclusive = true }
}
```

### 같은 화면 중복 이동 방지

```kotlin
navController.navigate(Search()) {
    launchSingleTop = true
}
```

- 빠르게 버튼을 두 번 누르면 같은 화면이 두 번 쌓일 수 있다. `launchSingleTop`으로 막는다.

<br>

# 7. Back Stack 동작 원리

## 7.1 구조

```text
Back Stack (아래 -> 위)

[ NavGraph 시작점 ] -> [ Home ] -> [ Detail(1) ] -> [ Detail(2) ]
                                                        ^ 현재 화면 (RESUMED)
```

- 각 항목(`NavBackStackEntry`)의 Lifecycle 상태
  - 최상단: `RESUMED`
  - 그 아래 항목: `CREATED` (화면은 없지만 항목과 ViewModel은 유지)
  - pop 된 항목: `DESTROYED`

## 7.2 Back Stack 확인

```kotlin
val backStack by navController.currentBackStack.collectAsState()
backStack.forEach { Log.d("NAV", it.destination.route.toString()) }
```

- 디버깅용으로 사용한다. 운영 로직에 의존하지 않는다.

## 7.3 시스템 뒤로가기

- `NavHost`가 `BackHandler`를 내부적으로 등록해서, 시스템 뒤로가기 시 자동으로 `popBackStack()`을 호출한다.
- Back Stack이 시작 목적지 하나만 남으면 이후 뒤로가기는 Activity로 위임되어 앱이 종료(Task에서 제거)된다.
- 화면에서 뒤로가기를 가로채려면 `BackHandler`를 사용한다.

```kotlin
BackHandler(enabled = hasUnsavedChanges) {
    showDiscardDialog = true
}
```

## 7.4 상태 저장과 복원

- 구성 변경(화면 회전)과 프로세스 종료 시 Back Stack 구조는 `SavedStateHandle` / `rememberSaveable`과 함께 복원된다.
- Route 인자는 직렬화되어 저장되므로 **인자는 반드시 직렬화 가능한 값**이어야 한다.

<br>

# 8. Bottom Navigation

## 8.1 탭 정의

```kotlin
@Serializable data object HomeTab
@Serializable data object SearchTab
@Serializable data object MyPageTab

enum class TopLevelDestination(
    val route: Any,
    val label: String,
    val icon: ImageVector,
) {
    HOME(HomeTab, "홈", Icons.Default.Home),
    SEARCH(SearchTab, "검색", Icons.Default.Search),
    MY_PAGE(MyPageTab, "마이", Icons.Default.Person),
}
```

## 8.2 NavigationBar와 NavHost

```kotlin
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { tab ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.hasRoute(tab.route::class) } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeTab,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<HomeTab> { HomeScreen() }
            composable<SearchTab> { SearchScreen() }
            composable<MyPageTab> { MyPageScreen() }
        }
    }
}
```

## 8.3 세 가지 옵션이 중요한 이유

- `popUpTo(startDestination) { saveState = true }`: 탭을 옮길 때 Back Stack이 계속 쌓이지 않게 하고, 떠나는 탭의 상태(스크롤 위치 등)를 저장한다.
- `launchSingleTop = true`: 같은 탭을 다시 눌러도 중복 생성하지 않는다.
- `restoreState = true`: 다시 돌아온 탭의 저장된 상태를 복원한다.

## 8.4 `hierarchy`와 `hasRoute`

- `destination.hierarchy`는 현재 목적지부터 부모 그래프까지의 계층을 순회한다.
- 탭 안에 Nested Graph가 있어도 탭이 올바르게 선택된 상태로 표시된다.
- `hasRoute(KClass)`로 타입 기반 비교를 한다. (문자열 비교 불필요)

## 8.5 특정 화면에서 Bottom Bar 숨기기

```kotlin
val showBottomBar = TopLevelDestination.entries.any { tab ->
    currentDestination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
}

Scaffold(
    bottomBar = { if (showBottomBar) AppBottomBar(...) }
) { ... }
```

<br>

# 9. Nested Navigation Graph

## 9.1 개념

- 관련 화면들을 **하나의 그래프로 묶는** 방식이다. (예: 로그인 -> 회원가입 -> 비밀번호 찾기)
- 그래프 단위로 이동, 종료, ViewModel 공유를 처리할 수 있다.

## 9.2 정의

```kotlin
@Serializable data object AuthGraph       // 그래프 자체의 Route
@Serializable data object Login
@Serializable data object SignUp
@Serializable data object ForgotPassword

NavHost(navController = navController, startDestination = AuthGraph) {

    navigation<AuthGraph>(startDestination = Login) {
        composable<Login> {
            LoginScreen(
                onSignUpClick = { navController.navigate(SignUp) },
                onLoginSuccess = {
                    navController.navigate(MainGraph) {
                        popUpTo<AuthGraph> { inclusive = true }
                    }
                },
            )
        }
        composable<SignUp> { SignUpScreen() }
        composable<ForgotPassword> { ForgotPasswordScreen() }
    }

    navigation<MainGraph>(startDestination = Home) {
        composable<Home> { HomeScreen() }
    }
}
```

- `navigation<GraphRoute>(startDestination = ...)`으로 그래프를 만든다.
- 그래프 전체를 한 번에 `popUpTo<AuthGraph> { inclusive = true }`로 제거할 수 있어서 **인증 플로우 종료 처리**가 깔끔하다.

## 9.3 그래프 단위 ViewModel 공유

```kotlin
composable<SignUp> { backStackEntry ->
    val parentEntry = remember(backStackEntry) {
        navController.getBackStackEntry<AuthGraph>()
    }
    val sharedViewModel: SignUpViewModel = hiltViewModel(parentEntry)
    SignUpScreen(viewModel = sharedViewModel)
}

composable<ForgotPassword> { backStackEntry ->
    val parentEntry = remember(backStackEntry) {
        navController.getBackStackEntry<AuthGraph>()
    }
    val sharedViewModel: SignUpViewModel = hiltViewModel(parentEntry)
    ...
}
```

- 같은 그래프 안의 여러 화면이 **ViewModel 하나를 공유**한다. (다단계 가입, 주문 플로우 등)
- ViewModel의 수명은 그래프가 Back Stack에서 제거될 때까지다.
- `remember(backStackEntry)`로 감싸지 않으면 Recomposition마다 `getBackStackEntry`가 호출된다.

<br>

# 10. ViewModel과 Hilt 연동

## 10.1 hiltViewModel

```kotlin
composable<Detail> {
    // 현재 NavBackStackEntry에 스코프된 ViewModel
    val viewModel: DetailViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DetailScreen(uiState = uiState)
}
```

- `hiltViewModel()`은 **현재 NavBackStackEntry**를 ViewModelStoreOwner로 사용한다.
- 즉 ViewModel의 수명은 해당 목적지가 Back Stack에 있는 동안이다. (Activity 스코프가 아니다.)
- Hilt 사용 시 Activity에 `@AndroidEntryPoint`, Application에 `@HiltAndroidApp`이 필요하다. (`Hilt` 노트 참고)

## 10.2 ViewModel 스코프 정리

| 스코프 | 생성 방법 | 수명 |
| --- | --- | --- |
| 화면(목적지) | `hiltViewModel()` | 해당 NavBackStackEntry가 제거될 때까지 |
| 그래프 | `hiltViewModel(parentEntry)` | 해당 그래프가 제거될 때까지 |
| Activity | `hiltViewModel(LocalActivity.current as ViewModelStoreOwner)` 등 | Activity 종료까지 |

## 10.3 화면 인자를 ViewModel에서 받는 흐름

```text
navigate(Detail(1))
   -> NavBackStackEntry 생성 (arguments에 Route 직렬화)
   -> hiltViewModel() 호출 시 SavedStateHandle 주입
   -> ViewModel에서 savedStateHandle.toRoute<Detail>()
```

<br>

# 11. 화면 간 결과 전달

## 11.1 SavedStateHandle로 결과 돌려주기

- 목적지 B에서 작업 후 이전 화면 A로 **결과를 돌려주는** 패턴이다.

```kotlin
// B 화면: 결과를 이전 화면의 SavedStateHandle에 저장하고 닫기
navController.previousBackStackEntry
    ?.savedStateHandle
    ?.set("selected_category", category)
navController.popBackStack()
```

```kotlin
// A 화면: 결과 관찰
composable<Form> { backStackEntry ->
    val result by backStackEntry.savedStateHandle
        .getStateFlow<String?>("selected_category", null)
        .collectAsStateWithLifecycle()

    FormScreen(selectedCategory = result)
}
```

- 키가 문자열이라 오타 위험이 있다. 상수로 관리한다.
- 결과를 소비한 뒤에는 `remove`로 지워서 재사용(재전달)을 막는다.

## 11.2 더 단순한 대안

- 결과가 **영속 데이터**(DB, DataStore)에 반영되는 것이라면, 화면 간 결과 전달 대신 **Repository를 통해 Flow로 관찰**하는 편이 더 단순하고 견고하다.
- 선택 같은 임시 UI 결과에는 SavedStateHandle 방식을, 데이터 변경에는 Repository 방식을 쓴다.

<br>

# 12. Deep Link

## 12.1 Route에 Deep Link 등록

```kotlin
composable<Detail>(
    deepLinks = listOf(
        navDeepLink<Detail>(basePath = "https://example.com/detail")
    )
) { /* ... */ }
```

- URL 형태: `https://example.com/detail/{id}` (인자가 path로 자동 매핑)
- 선택 인자는 query 파라미터로 매핑된다. (`?keyword=abc`)
- 커스텀 스킴(`myapp://detail/1`)도 `basePath`로 지정할 수 있다.

## 12.2 Manifest 설정

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTop">

    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https" android:host="example.com" android:pathPrefix="/detail" />
    </intent-filter>
</activity>
```

- `autoVerify="true"` + `assetlinks.json`을 서버에 올리면 **App Links**(앱 선택창 없이 바로 실행)가 된다.
- `launchMode="singleTop"`: 이미 떠 있는 Activity에서 새 Intent를 `onNewIntent`로 받아 Navigation이 처리한다.

## 12.3 Deep Link 진입 시 Back Stack

- Deep Link로 진입하면 NavController가 **그래프 구조에 따라 부모 목적지들을 Back Stack에 합성**한다. (기본적으로 시작 목적지 -> 대상)
- 외부 앱에서 Deep Link로 들어온 경우, 뒤로가기 시 앱의 홈으로 가도록 구조를 설계한다.

## 12.4 PendingIntent로 알림에서 열기

```kotlin
val intent = Intent(
    Intent.ACTION_VIEW,
    "https://example.com/detail/1".toUri(),
    context,
    MainActivity::class.java,
)

val pendingIntent = TaskStackBuilder.create(context).run {
    addNextIntentWithParentStack(intent)
    getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
```

## 12.5 Deep Link 보안

- Deep Link로 들어온 인자는 **외부 입력**이다. 반드시 검증한다. (ID 범위, 권한, 로그인 여부)
- 로그인이 필요한 화면은 진입 시 인증 상태를 확인하고, 미인증이면 로그인 후 원래 목적지로 돌아오게 설계한다.
- `exported="true"`인 Activity는 외부에서 호출 가능하다는 점을 항상 의식한다. (`Android-Security` 노트 참고)

<br>

# 13. 화면 전환 애니메이션

## 13.1 NavHost 전체 기본값

```kotlin
NavHost(
    navController = navController,
    startDestination = Home,
    enterTransition = {
        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300))
    },
    exitTransition = {
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300))
    },
    popEnterTransition = {
        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300))
    },
    popExitTransition = {
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300))
    },
) { ... }
```

| 파라미터 | 시점 |
| --- | --- |
| `enterTransition` | 새 화면이 들어올 때 |
| `exitTransition` | 현재 화면이 다른 화면에 가려질 때 |
| `popEnterTransition` | 뒤로가기로 이전 화면이 다시 나타날 때 |
| `popExitTransition` | 뒤로가기로 현재 화면이 사라질 때 |

## 13.2 화면별 개별 설정

```kotlin
composable<Detail>(
    enterTransition = { fadeIn(tween(200)) },
    exitTransition = { fadeOut(tween(200)) },
) { ... }
```

## 13.3 애니메이션 비활성화

```kotlin
NavHost(
    enterTransition = { EnterTransition.None },
    exitTransition = { ExitTransition.None },
    popEnterTransition = { EnterTransition.None },
    popExitTransition = { ExitTransition.None },
    ...
)
```

- 탭 전환(Bottom Navigation)에는 슬라이드 대신 페이드나 None이 자연스럽다.

## 13.4 Shared Element Transition

- Compose 1.7+의 `SharedTransitionLayout`과 `sharedElement` / `sharedBounds`를 NavHost와 함께 사용해서 목록 -> 상세 간 공유 요소 전환을 만들 수 있다.
- 각 `composable` 안에서 `AnimatedVisibilityScope`(`this`)를 `sharedElement`에 전달한다.

<br>

# 14. MVI에서 Navigation 처리

## 14.1 원칙

- **화면(Screen Composable)이 NavController를 직접 알지 못하게** 한다.
- 대신 이동 의도를 **람다(콜백)** 로 상위(NavHost 정의부)에 노출한다.
- ViewModel이 이동 자체를 수행하지 않고, **Effect(단발성 이벤트)** 로 이동 요청을 방출한다.

## 14.2 MVI 구조 예시

```kotlin
// Contract
data class DetailUiState(val title: String = "", val isLoading: Boolean = true)

sealed interface DetailIntent {
    data object BackClicked : DetailIntent
    data class RelatedClicked(val id: Long) : DetailIntent
}

sealed interface DetailEffect {
    data object NavigateBack : DetailEffect
    data class NavigateToDetail(val id: Long) : DetailEffect
}
```

```kotlin
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _effect = Channel<DetailEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onIntent(intent: DetailIntent) {
        when (intent) {
            DetailIntent.BackClicked -> send(DetailEffect.NavigateBack)
            is DetailIntent.RelatedClicked -> send(DetailEffect.NavigateToDetail(intent.id))
        }
    }

    private fun send(effect: DetailEffect) {
        viewModelScope.launch { _effect.send(effect) }
    }
}
```

- Effect는 `Channel` + `receiveAsFlow()`로 **정확히 한 번** 전달한다. (`SharedFlow`는 구독자가 없을 때 유실될 수 있다.)

## 14.3 Route 화면에서 Effect 수집

```kotlin
@Composable
fun DetailRoute(
    onBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                DetailEffect.NavigateBack -> onBack()
                is DetailEffect.NavigateToDetail -> onNavigateToDetail(effect.id)
            }
        }
    }

    DetailScreen(uiState = uiState, onIntent = viewModel::onIntent)
}
```

- `DetailRoute`(상태, Effect 연결) 와 `DetailScreen`(순수 UI)을 분리한다. Preview, 테스트가 쉬워진다.
- 이동 람다만 받으므로 `DetailRoute`는 NavController에 의존하지 않는다.

## 14.4 NavHost 쪽 연결

```kotlin
composable<Detail> {
    DetailRoute(
        onBack = navController::popBackStack,
        onNavigateToDetail = { id -> navController.navigate(Detail(id)) },
    )
}
```

## 14.5 연타 방지

- 버튼을 빠르게 두 번 누르면 `navigate`가 두 번 호출되어 같은 화면이 중복되거나, `popBackStack`이 두 번 실행되어 두 화면이 사라진다.

```kotlin
fun NavController.navigateSafely(block: NavController.() -> Unit) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        block()
    }
}

// 사용
navController.navigateSafely { navigate(Detail(id)) }
```

- 화면 전환 애니메이션 중에는 이전 화면 항목이 `RESUMED`가 아니므로, 상태 확인으로 중복 호출을 걸러낼 수 있다.
- 간단한 경우 `launchSingleTop = true`와 `popBackStack`의 반환값 확인으로도 대응한다.

<br>

# 15. 멀티모듈에서의 Navigation

## 15.1 문제

- Feature 모듈끼리 서로 의존하면 안 된다. (`feature:home` -> `feature:detail` 직접 의존 금지)
- 그런데 `home`에서 `detail`로 이동하려면 Route 타입을 알아야 한다.

## 15.2 구조

```text
:app                      NavHost 구성, 모든 feature 연결
:feature:home:api         HomeRoute 등 Route 정의만 (가벼운 모듈)
:feature:home:impl        HomeScreen, ViewModel, 그래프 확장 함수
:feature:detail:api       Detail Route 정의
:feature:detail:impl      DetailScreen, ViewModel
```

- **api 모듈**: Route(`@Serializable`) 정의와 이동 확장 함수만 둔다.
- **impl 모듈**: 화면, ViewModel, 로직. 다른 feature의 `api`에만 의존한다.
- 규모가 작으면 feature당 모듈 하나에 Route와 화면을 같이 두고, 다른 feature에는 **콜백 람다**로만 연결하는 방식도 충분하다.

## 15.3 Feature 모듈의 그래프 확장 함수

```kotlin
// :feature:detail:api
@Serializable
data class Detail(val id: Long)

fun NavController.navigateToDetail(id: Long, navOptions: NavOptions? = null) {
    navigate(Detail(id), navOptions)
}
```

```kotlin
// :feature:detail:impl
fun NavGraphBuilder.detailScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
) {
    composable<Detail> {
        DetailRoute(
            onBack = onBack,
            onNavigateToDetail = onNavigateToDetail,
        )
    }
}
```

## 15.4 :app 에서 조립

```kotlin
@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Home) {
        homeScreen(
            onItemClick = navController::navigateToDetail,
        )
        detailScreen(
            onBack = navController::popBackStack,
            onNavigateToDetail = navController::navigateToDetail,
        )
    }
}
```

- 각 feature는 **자신이 이동할 곳을 모른 채 콜백만 노출**하고, 실제 연결은 `:app`이 한다.
- 이 구조는 `Gradle-MultiModule` 노트의 모듈 의존성 방향 원칙(feature끼리 의존 금지)과 맞는다.

<br>

# 16. 테스트

## 16.1 의존성

```kotlin
androidTestImplementation(libs.androidx.navigation.testing)
androidTestImplementation(libs.androidx.compose.ui.test.junit4)
debugImplementation(libs.androidx.compose.ui.test.manifest)
```

## 16.2 TestNavHostController

```kotlin
class AppNavHostTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var navController: TestNavHostController

    @Before
    fun setup() {
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            AppNavHost(navController = navController)
        }
    }

    @Test
    fun startDestination_isHome() {
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<Home>() == true)
    }

    @Test
    fun clickItem_navigatesToDetail() {
        composeTestRule.onNodeWithText("첫 번째 아이템").performClick()

        val route = navController.currentBackStackEntry?.toRoute<Detail>()
        assertEquals(1L, route?.id)
    }
}
```

## 16.3 단위 테스트 전략

- 이동 로직은 **ViewModel의 Effect 방출**을 테스트한다. (`ViewModel-MVI-Test` 노트의 방식과 동일)
- NavController를 쓰는 UI 테스트는 최소한의 핵심 플로우만 둔다. (느리고 깨지기 쉽다.)

```kotlin
@Test
fun backClicked_emitsNavigateBack() = runTest {
    val viewModel = DetailViewModel(SavedStateHandle(mapOf("id" to 1L)))

    viewModel.effect.test {
        viewModel.onIntent(DetailIntent.BackClicked)
        assertEquals(DetailEffect.NavigateBack, awaitItem())
    }
}
```

<br>

# 17. 자주 하는 실수

| 실수 | 문제 | 해결 |
| --- | --- | --- |
| `NavController`를 모든 하위 Composable에 전달 | 결합도 증가, Preview/테스트 어려움 | 이동 콜백 람다만 전달 |
| 큰 객체를 인자로 전달 | Bundle 크기 초과, 데이터 불일치 | ID만 전달하고 목적지에서 조회 |
| 연타로 `navigate` 중복 호출 | 같은 화면 중복, 의도치 않은 pop | `launchSingleTop`, Lifecycle 상태 확인 |
| Composable 본문에서 직접 `navigate` 호출 | Recomposition마다 호출됨 | `LaunchedEffect` 또는 이벤트 핸들러 안에서 호출 |
| Bottom Nav에서 `saveState` / `restoreState` 누락 | 탭 전환 시 상태 초기화 | 3가지 옵션 세트로 사용 |
| `hiltViewModel()`을 NavHost 바깥에서 사용 | Activity 스코프가 되어 화면 이동 후에도 ViewModel 유지 | 각 `composable` 안에서 호출 |
| 단발성 이벤트를 `StateFlow`로 처리 | 회전, 재구독 시 이동 재실행 | `Channel` 기반 Effect |
| 로그인 후 `popUpTo` 누락 | 뒤로가기로 로그인 화면 복귀 | `popUpTo<Login> { inclusive = true }` |
| Deep Link 인자 무검증 | 잘못된 값, 권한 우회 | 진입 시 검증과 인증 확인 |
| 문자열 route 직접 사용 | 오타, 타입 불일치 | Type-safe Route 사용 |

<br>

# 18. Navigation 3

## 18.1 개요

- Navigation 3는 Compose를 위해 처음부터 다시 설계된 **새 Navigation 라이브러리**다. (기존 Navigation 2와 별도 아티팩트)
- 핵심 차이: **Back Stack을 개발자가 직접 소유**한다. 상태로 관리되는 리스트를 직접 조작해서 이동한다.

```kotlin
// 개념 예시 (API 이름은 버전에 따라 달라질 수 있음)
val backStack = rememberNavBackStack(Home)

NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider {
        entry<Home> { HomeScreen(onItemClick = { backStack.add(Detail(it)) }) }
        entry<Detail> { key -> DetailScreen(id = key.id) }
    },
)
```

- 이동 = 리스트에 add, 뒤로가기 = 리스트에서 remove. 상태 기반이라 Compose 사고방식과 더 잘 맞는다.
- 적응형 레이아웃(리스트-상세 동시 표시) 같은 Scene 개념을 지원한다.

## 18.2 현업 적용 시 판단

- 기존 프로젝트는 대부분 Navigation 2(`navigation-compose`)를 쓰고 있어서, 당장은 이 노트의 내용이 실무 기준이다.
- 새 프로젝트나 마이그레이션을 검토할 때는 공식 문서의 최신 안정 버전과 마이그레이션 가이드를 확인한다.
- Route를 `@Serializable` 타입으로 정의하는 방식은 두 라이브러리에서 비슷해서 학습한 개념이 이어진다.

<br>

# 19. 정리

## 19.1 핵심 요약

1. Compose Navigation은 **NavController(이동) + NavHost(표시) + NavGraph(구조)** 로 구성된다.
2. Route는 **`@Serializable` 타입**으로 정의한다. 인자 없으면 `data object`, 있으면 `data class`. 문자열 route는 쓰지 않는다.
3. 인자는 **ID 같은 가벼운 값**만 넘기고, ViewModel에서 `savedStateHandle.toRoute<T>()`로 받아 Repository로 조회한다.
4. `popUpTo`, `launchSingleTop`, `saveState`, `restoreState`로 Back Stack을 제어한다. **로그인 후 이동**, **Bottom Navigation**이 대표 사례다.
5. 각 `NavBackStackEntry`는 자신의 Lifecycle, ViewModelStore, SavedStateHandle을 가지며, `hiltViewModel()`은 **목적지 단위 스코프**다.
6. **Nested Graph**로 플로우(인증 등)를 묶고, 그래프 단위로 ViewModel을 공유할 수 있다.
7. MVI에서는 화면이 NavController를 모르게 하고, **Effect(Channel) -> 콜백 람다 -> NavHost에서 navigate** 순서로 처리한다.
8. 멀티모듈에서는 feature끼리 직접 의존하지 않고, **api 모듈의 Route + 콜백 + `:app` 조립**으로 연결한다.
9. 연타 방지, Deep Link 검증, 큰 객체 인자 금지는 실무에서 반드시 챙긴다.
10. Navigation 3는 Back Stack을 직접 소유하는 새 방식이다. 기본기는 동일하게 이어진다.

## 19.2 한 줄 정리

- Compose Navigation에서 인자는 어떻게 전달하나? -> `@Serializable` Route 클래스에 담아 `navigate(Detail(id))`, 받는 쪽은 `toRoute()` 또는 `SavedStateHandle.toRoute()`. 큰 객체 대신 ID만 넘긴다.
- Bottom Navigation에서 탭 상태를 유지하려면? -> `popUpTo(start) { saveState = true }`, `launchSingleTop = true`, `restoreState = true`를 함께 사용.
- `hiltViewModel()`의 스코프는? -> 현재 `NavBackStackEntry`. 그래프 단위로 공유하려면 부모 그래프의 BackStackEntry를 인자로 넘긴다.
- 화면 간 ViewModel을 공유하려면? -> Nested Graph를 만들고 `navController.getBackStackEntry<GraphRoute>()`를 `hiltViewModel()`에 전달.
- MVI에서 화면 이동은 어떻게 처리하나? -> ViewModel이 Effect(Channel)로 이동 요청을 방출하고, Route Composable이 수집해서 상위에서 받은 이동 콜백을 호출. 화면은 NavController에 의존하지 않는다.
- 멀티모듈에서 feature 간 이동은? -> feature는 이동 콜백만 노출하고 `:app`이 연결. 필요하면 Route만 담은 api 모듈로 분리.
- 로그인 후 뒤로가기로 로그인 화면이 다시 보이지 않게 하려면? -> `navigate(Home) { popUpTo<Login> { inclusive = true } }`.
