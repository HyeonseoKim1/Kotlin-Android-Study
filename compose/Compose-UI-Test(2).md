# Compose UI 테스트 (2) 실전

1. MVI 화면 테스트
2. LazyColumn 테스트
3. Navigation 테스트
4. Hilt와 함께 쓰기
5. 시간 제어와 비동기 처리
6. Robolectric으로 JVM에서 실행
7. 디버깅
8. 자주 하는 실수

<br>

# 1. MVI 화면 테스트

## 1.1 화면을 stateless로 분리

ViewModel을 직접 주입받는 Route와, State와 콜백만 받는 Screen으로 나눈다.

```kotlin
data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface LoginIntent {
    data class EmailChanged(val value: String) : LoginIntent
    data class PasswordChanged(val value: String) : LoginIntent
    data object ClickLogin : LoginIntent
}
```

```kotlin
@Composable
fun LoginRoute(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginScreen(state = state, onIntent = viewModel::onIntent)
}

@Composable
fun LoginScreen(
    state: LoginState,
    onIntent: (LoginIntent) -> Unit,
) {
    Column {
        TextField(
            value = state.email,
            onValueChange = { onIntent(LoginIntent.EmailChanged(it)) },
            modifier = Modifier.testTag(LoginTestTags.EMAIL_FIELD),
        )
        TextField(
            value = state.password,
            onValueChange = { onIntent(LoginIntent.PasswordChanged(it)) },
            modifier = Modifier.testTag(LoginTestTags.PASSWORD_FIELD),
        )
        Button(
            onClick = { onIntent(LoginIntent.ClickLogin) },
            enabled = !state.isLoading,
            modifier = Modifier.testTag(LoginTestTags.LOGIN_BUTTON),
        ) {
            Text("로그인")
        }
        if (state.isLoading) {
            CircularProgressIndicator(Modifier.testTag("loading"))
        }
        state.errorMessage?.let {
            Text(it, modifier = Modifier.testTag(LoginTestTags.ERROR_TEXT))
        }
    }
}
```

## 1.2 State에 따른 렌더링 테스트

```kotlin
class LoginScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun 로딩중이면_버튼이_비활성화되고_로딩이_보인다() {
        composeRule.setContent {
            LoginScreen(state = LoginState(isLoading = true), onIntent = {})
        }

        composeRule.onNodeWithTag(LoginTestTags.LOGIN_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag("loading").assertIsDisplayed()
    }

    @Test
    fun 에러메시지가_있으면_표시된다() {
        composeRule.setContent {
            LoginScreen(
                state = LoginState(errorMessage = "비밀번호가 틀렸습니다"),
                onIntent = {},
            )
        }

        composeRule
            .onNodeWithTag(LoginTestTags.ERROR_TEXT)
            .assertTextEquals("비밀번호가 틀렸습니다")
    }
}
```

## 1.3 Intent 발행 테스트

콜백으로 넘어온 Intent를 리스트에 모아서 검증한다.

```kotlin
@Test
fun 로그인_버튼을_누르면_ClickLogin_Intent가_발행된다() {
    val intents = mutableListOf<LoginIntent>()

    composeRule.setContent {
        LoginScreen(state = LoginState(), onIntent = { intents += it })
    }

    composeRule.onNodeWithTag(LoginTestTags.LOGIN_BUTTON).performClick()

    assertEquals(listOf<LoginIntent>(LoginIntent.ClickLogin), intents)
}

@Test
fun 이메일을_입력하면_EmailChanged_Intent가_발행된다() {
    val intents = mutableListOf<LoginIntent>()

    composeRule.setContent {
        LoginScreen(state = LoginState(), onIntent = { intents += it })
    }

    composeRule.onNodeWithTag(LoginTestTags.EMAIL_FIELD).performTextInput("a@b.com")

    assertEquals(listOf<LoginIntent>(LoginIntent.EmailChanged("a@b.com")), intents)
}
```

<br>

# 2. LazyColumn 테스트

## 2.1 대상 코드

```kotlin
@Composable
fun TodoList(items: List<String>) {
    LazyColumn(modifier = Modifier.testTag("todo_list")) {
        items(items, key = { it }) { item ->
            Text(text = item, modifier = Modifier.padding(16.dp))
        }
    }
}
```

## 2.2 스크롤 후 검증

LazyColumn은 화면에 보이는 아이템만 컴포지션한다. 화면 밖 아이템은 Semantics 트리에도 없으므로 먼저 스크롤해야 한다.

```kotlin
@Test
fun 스크롤하면_뒤쪽_아이템이_보인다() {
    val items = List(50) { "할 일 $it" }
    composeRule.setContent { TodoList(items) }

    composeRule.onNodeWithText("할 일 40").assertDoesNotExist()

    composeRule.onNodeWithTag("todo_list").performScrollToIndex(40)

    composeRule.onNodeWithText("할 일 40").assertIsDisplayed()
}
```

## 2.3 조건으로 스크롤

```kotlin
composeRule
    .onNodeWithTag("todo_list")
    .performScrollToNode(hasText("할 일 40"))
```

인덱스를 모를 때 유용하다. 대상이 끝까지 스크롤해도 없으면 실패한다.

<br>

# 3. Navigation 테스트

## 3.1 의존성

```kotlin
androidTestImplementation("androidx.navigation:navigation-testing:<navigation-version>")
```

## 3.2 TestNavHostController

```kotlin
class AppNavHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        composeRule.setContent {
            navController = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            AppNavHost(navController = navController)
        }
    }

    @Test
    fun 시작_화면은_홈이다() {
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun 상세_버튼을_누르면_상세_화면으로_이동한다() {
        composeRule.onNodeWithText("상세 보기").performClick()

        val route = navController.currentBackStackEntry?.destination?.route
        assertEquals("detail/{id}", route)
    }
}
```

## 3.3 검증 대상 정하기

- 현재 destination의 route를 검증하는 것이 가장 안정적이다.
- 화면 내용까지 검증하면 이동 로직이 아니라 화면 로직을 같이 테스트하게 되므로 범위를 나눈다.
- 인자 전달은 `backStackEntry.arguments`나 `toRoute<Detail>()`로 확인한다.

<br>

# 4. Hilt와 함께 쓰기

## 4.1 의존성과 Runner

```kotlin
androidTestImplementation("com.google.dagger:hilt-android-testing:<hilt-version>")
kspAndroidTest("com.google.dagger:hilt-android-compiler:<hilt-version>")
```

```kotlin
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
```

```kotlin
// build.gradle.kts
android {
    defaultConfig {
        testInstrumentationRunner = "com.example.app.HiltTestRunner"
    }
}
```

## 4.2 Hilt용 테스트 Activity

`hiltViewModel()`을 쓰려면 `@AndroidEntryPoint`가 붙은 Activity에서 Composable이 실행되어야 한다.

```kotlin
// debug 소스셋
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()
```

```xml
<!-- debug/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity
            android:name=".HiltTestActivity"
            android:exported="false" />
    </application>
</manifest>
```

## 4.3 테스트 작성

```kotlin
@HiltAndroidTest
class LoginRouteTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun 로그인_화면이_표시된다() {
        composeRule.setContent { LoginRoute() }

        composeRule.onNodeWithTag(LoginTestTags.LOGIN_BUTTON).assertIsDisplayed()
    }
}
```

Rule 순서가 중요하다. `HiltAndroidRule`이 먼저 실행되어야 Activity가 뜰 때 의존성 그래프가 준비되어 있다.

## 4.4 모듈 교체

실제 네트워크 대신 Fake를 주입하려면 프로덕션 모듈을 교체한다.

```kotlin
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [RepositoryModule::class],
)
object FakeRepositoryModule {

    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepository = FakeAuthRepository()
}
```

## 4.5 Route 테스트 vs Screen 테스트

- Screen(stateless) 테스트: Hilt가 필요 없고 빠르다. UI 테스트의 대부분은 여기서 해결한다.
- Route(Hilt 포함) 테스트: ViewModel까지 이어지는 연결을 확인할 때만 소수로 작성한다.

<br>

# 5. 시간 제어와 비동기 처리

## 5.1 자동 동기화 범위

Compose 테스트는 recomposition, 측정, 그리기, 그리고 Compose가 아는 애니메이션까지만 자동으로 기다린다. 아래는 직접 처리해야 한다.

- 네트워크 호출, 디스크 I/O 같은 외부 비동기 작업
- `delay`를 쓰는 코루틴 (테스트 Dispatcher가 아닌 경우)

## 5.2 waitUntil

```kotlin
composeRule.waitUntil(timeoutMillis = 5_000) {
    composeRule
        .onAllNodesWithText("로딩 완료")
        .fetchSemanticsNodes()
        .isNotEmpty()
}
```

조건이 참이 될 때까지 기다린다. 시간 초과 시 테스트가 실패한다. `Thread.sleep`보다 항상 이쪽을 쓴다.

## 5.3 mainClock으로 애니메이션 제어

```kotlin
@Test
fun 애니메이션_중간_상태를_확인한다() {
    composeRule.mainClock.autoAdvance = false

    composeRule.setContent { AnimatedBanner(visible = true) }

    composeRule.mainClock.advanceTimeBy(150)
    composeRule.onNodeWithTag("banner").assertIsDisplayed()

    composeRule.mainClock.advanceTimeBy(1_000)
}
```

`autoAdvance = false`로 두면 시간이 자동으로 흐르지 않고, `advanceTimeBy`로 직접 진행시킨다. 무한 반복 애니메이션이 있으면 Idle 상태가 오지 않아 테스트가 멈추므로, 이때도 `autoAdvance = false`가 필요하다.

<br>

# 6. Robolectric으로 JVM에서 실행

## 6.1 장점

기기나 에뮬레이터 없이 `src/test`에서 Compose UI 테스트를 돌릴 수 있다. CI에서 빠르고, 로컬에서도 부담이 적다.

## 6.2 설정

```kotlin
android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    testImplementation("org.robolectric:robolectric:<robolectric-version>")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

## 6.3 테스트

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LoginScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun 에러메시지가_표시된다() {
        composeRule.setContent {
            LoginScreen(state = LoginState(errorMessage = "오류"), onIntent = {})
        }

        composeRule.onNodeWithText("오류").assertIsDisplayed()
    }
}
```

<br>

# 7. 디버깅

## 7.1 Semantics 트리 출력

노드가 안 찾아질 때 가장 먼저 트리를 출력해서 확인한다.

```kotlin
composeRule.onRoot().printToLog("TAG")
composeRule.onRoot(useUnmergedTree = true).printToLog("TAG_UNMERGED")
composeRule.onNodeWithTag("login_button").printToLog("BUTTON")
```

Logcat에서 `TAG`로 필터링하면 노드별 Text, 태그, Actions, 상태를 확인할 수 있다.

## 7.2 실패 메시지 읽기

```text
Failed to assert the following: (Text + EditableText contains 'abc' (ignoring case: false))
Semantics of the node:
Node #5 at (l=0.0, t=0.0, r=200.0, b=48.0)px
Text = '[abc]'
```

실패 메시지에 현재 노드의 semantics가 같이 나온다. 기대값과 실제값을 비교하면 대부분 원인이 바로 보인다.

<br>

# 8. 자주 하는 실수

## 8.1 setContent 중복 호출

```kotlin
composeRule.setContent { A() }
composeRule.setContent { B() } // 예외 발생
```

한 테스트에서 화면을 바꾸고 싶으면 State로 분기하거나 테스트를 나눈다.

## 8.2 ui-test-manifest 누락

`Unable to resolve activity for Intent` 에러의 대표 원인이다. `debugImplementation`으로 추가했는지 확인한다.

## 8.3 Thread.sleep 사용

타이밍 문제를 가려줄 뿐 안정적이지 않다. `waitUntil`, `mainClock`, Fake 주입으로 해결한다.

## 8.4 Hilt Rule 순서

`HiltAndroidRule`은 `order = 0`으로 가장 먼저 실행한다. 순서가 틀리면 `hiltViewModel()` 생성 시점에 의존성이 없어서 크래시가 난다.

## 8.5 testTag 남용

텍스트나 contentDescription으로 찾을 수 있는 요소에는 굳이 태그를 붙이지 않는다. 사용자가 실제로 보는 기준으로 찾는 테스트가 UI 변경에 더 강하다. 태그는 아래 경우에 쓴다.

- 같은 텍스트가 여러 번 나오는 요소
- 텍스트가 없는 요소 (이미지, 컨테이너)
- 다국어로 텍스트가 바뀌는 요소

<br>

# 정리

1. MVI에서는 화면을 `State`와 `onIntent`만 받는 stateless Screen으로 분리하면 UI 테스트가 단순해진다.
2. State 렌더링 테스트와 Intent 발행 테스트를 나누면 ViewModel 테스트와 역할이 깔끔하게 분리된다.
3. LazyColumn은 화면 밖 아이템이 트리에 없으므로 `performScrollToIndex`, `performScrollToNode` 후에 검증한다.
4. Navigation은 `TestNavHostController`로 현재 destination만 검증하는 것이 안정적이다.
5. Hilt 테스트는 `HiltTestRunner`, `@AndroidEntryPoint` 테스트 Activity, Rule 순서(`order`)가 핵심이고, 모듈 교체는 `@TestInstallIn`을 쓴다.
6. 비동기는 `Thread.sleep` 대신 `waitUntil`, `mainClock`, Fake 주입으로 처리한다.
7. stateless Screen 테스트는 Robolectric으로 JVM에서 빠르게, 실제 기기 동작이 필요한 시나리오는 기기 테스트로 나눈다.
8. 노드가 안 찾아지면 `printToLog`로 Semantics 트리부터 확인한다.
