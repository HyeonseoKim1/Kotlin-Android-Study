# Compose UI 테스트 (1) 기초

1. Compose UI 테스트 개요
2. 의존성 설정
3. Rule 종류와 첫 테스트
4. Semantics 트리
5. Finder와 Matcher
6. Assertion
7. Action
8. 상태가 있는 Composable 테스트
9. 상태 복원 테스트

<br>

# 1. Compose UI 테스트 개요

Compose UI 테스트는 View 계층이 아니라 **Semantics 트리**를 기준으로 UI를 찾고 검증한다. Espresso의 `onView(withId(...))` 대신 `onNodeWithText(...)`, `onNodeWithTag(...)` 같은 Finder를 쓴다.

## 1.1 테스트 계층에서의 위치

| 계층 | 대상 | 실행 환경 |
| --- | --- | --- |
| Unit 테스트 | ViewModel, UseCase, Reducer | JVM |
| Compose UI 테스트 | 화면 단위 Composable | 기기 또는 Robolectric |
| E2E 테스트 | 앱 전체 흐름 | 기기 |

## 1.2 MVI에서의 역할 분담

- ViewModel 테스트: Intent가 들어오면 State가 올바르게 바뀌는가
- Compose UI 테스트: State가 주어지면 화면이 올바르게 그려지는가, 클릭하면 Intent가 올바르게 나가는가

두 테스트가 `State`와 `Intent`라는 계약을 사이에 두고 서로 맞물린다. 그래서 화면 Composable을 State를 받는 stateless 형태로 분리해두면 UI 테스트가 매우 쉬워진다.

## 1.3 핵심 특징

- 테스트가 자동으로 앱의 Idle 상태를 기다린다. recomposition이 끝날 때까지 Finder와 Assertion이 동기화된다.
- `Thread.sleep`이 필요 없다.
- 테스트용 Clock을 제어해서 애니메이션 시간도 조절할 수 있다.
- `setContent`로 원하는 Composable만 따로 띄울 수 있어서 Activity 전체를 띄울 필요가 없다.

<br>

# 2. 의존성 설정

## 2.1 기본 의존성

```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    testOptions {
        // 애니메이션 때문에 테스트가 흔들리는 것을 방지
        animationsDisabled = true
    }
}

dependencies {
    // 프로젝트에서 쓰는 Compose BOM 버전을 그대로 사용
    val composeBom = platform(libs.androidx.compose.bom)
    androidTestImplementation(composeBom)

    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

## 2.2 ui-test-manifest가 필요한 이유

`createComposeRule()`은 내부적으로 빈 `ComponentActivity`를 띄운다. 이 Activity가 manifest에 등록되어 있어야 하는데, `ui-test-manifest`가 debug 빌드에 그 등록을 넣어준다. 빠뜨리면 아래와 같은 에러가 난다.

```text
java.lang.IllegalStateException: Unable to resolve activity for Intent
```

## 2.3 멀티모듈에서의 주의

UI 테스트를 작성하는 모듈마다 `androidTestImplementation`과 `debugImplementation`을 각각 선언해야 한다. 의존성은 모듈 간에 자동으로 전달되지 않는다.

<br>

# 3. Rule 종류와 첫 테스트

## 3.1 Rule 종류

| Rule | 용도 |
| --- | --- |
| `createComposeRule()` | 빈 Activity에 Composable만 띄울 때 (가장 많이 사용) |
| `createAndroidComposeRule<A>()` | 특정 Activity가 필요할 때, `activity`로 접근 가능 |
| `createEmptyComposeRule()` | Activity를 직접 `ActivityScenario`로 띄울 때 |

리소스 문자열을 테스트에서 쓰고 싶을 때는 `createAndroidComposeRule`을 쓴다.

```kotlin
@get:Rule
val composeRule = createAndroidComposeRule<ComponentActivity>()

val title = composeRule.activity.getString(R.string.login_title)
```

## 3.2 첫 테스트

```kotlin
@Composable
fun Greeting(name: String) {
    Text(text = "Hello, $name")
}
```

```kotlin
class GreetingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun greeting_이름이_표시된다() {
        composeRule.setContent {
            Greeting(name = "현서")
        }

        composeRule
            .onNodeWithText("Hello, 현서")
            .assertIsDisplayed()
    }
}
```

## 3.3 테스트의 기본 구조

1. `setContent`로 테스트할 Composable을 띄운다.
2. Finder로 노드를 찾는다.
3. Action으로 사용자 동작을 흉내 낸다.
4. Assertion으로 결과를 검증한다.

`setContent`는 테스트 하나에서 한 번만 호출할 수 있다. 두 번 호출하면 예외가 발생한다.

<br>

# 4. Semantics 트리

## 4.1 개념

Compose는 접근성 서비스와 테스트를 위해 UI 요소의 의미 정보를 담은 Semantics 트리를 만든다. 테스트 API는 이 트리를 탐색한다.

## 4.2 Merged 트리와 Unmerged 트리

`Button`, `Row(Modifier.clickable)` 같은 요소는 자식의 semantics를 하나로 **병합**한다. 기본 Finder는 병합된 트리를 기준으로 동작한다.

```kotlin
Button(onClick = onClick) {
    Icon(Icons.Default.Add, contentDescription = "추가")
    Text("추가하기")
}
```

위 코드에서 Button 안의 Text는 별도 노드가 아니라 Button 노드에 합쳐진다. 병합 전의 개별 노드를 찾고 싶으면 `useUnmergedTree = true`를 쓴다.

```kotlin
composeRule
    .onNodeWithText("추가하기", useUnmergedTree = true)
    .assertIsDisplayed()
```

## 4.3 testTag

텍스트나 contentDescription으로 찾기 어려운 요소에 붙인다.

```kotlin
Modifier.testTag("login_button")
```

태그 문자열은 상수로 모아서 관리하면 오타를 줄일 수 있다.

```kotlin
object LoginTestTags {
    const val EMAIL_FIELD = "login_email_field"
    const val PASSWORD_FIELD = "login_password_field"
    const val LOGIN_BUTTON = "login_button"
    const val ERROR_TEXT = "login_error_text"
}
```

## 4.4 semantics Modifier로 직접 지정

```kotlin
Modifier.semantics {
    contentDescription = "프로필 사진"
    stateDescription = if (selected) "선택됨" else "선택 안 됨"
    role = Role.Button
}
```

접근성을 위한 semantics를 잘 지정해두면 테스트와 TalkBack 지원을 같이 챙길 수 있다.

## 4.5 mergeDescendants와 clearAndSetSemantics

```kotlin
// 자식들을 하나의 노드로 합친다
Row(Modifier.semantics(mergeDescendants = true) { }) { ... }

// 자식 semantics를 모두 지우고 새로 지정한다
Box(Modifier.clearAndSetSemantics { contentDescription = "평점 4점" }) { ... }
```

<br>

# 5. Finder와 Matcher

## 5.1 자주 쓰는 Finder

| Finder | 설명 |
| --- | --- |
| `onNodeWithText` | 텍스트로 단일 노드 찾기 |
| `onNodeWithTag` | testTag로 단일 노드 찾기 |
| `onNodeWithContentDescription` | contentDescription으로 찾기 |
| `onAllNodesWithText` | 여러 노드 찾기 |
| `onAllNodesWithTag` | 여러 노드 찾기 |
| `onNode(matcher)` | 조건 조합으로 찾기 |
| `onRoot()` | 루트 노드 |

`onNode...` 계열은 노드가 정확히 하나여야 한다. 0개이거나 2개 이상이면 실패한다. 여러 개를 다룰 때는 `onAllNodes...`를 쓴다.

## 5.2 Matcher 조합

```kotlin
composeRule
    .onNode(hasText("로그인") and hasClickAction())
    .assertIsEnabled()

composeRule
    .onNode(hasTestTag("item") and !isSelected())
    .performClick()
```

| Matcher | 설명 |
| --- | --- |
| `hasText`, `hasTestTag`, `hasContentDescription` | 값 일치 |
| `hasClickAction`, `hasScrollAction`, `hasSetTextAction` | 가능한 동작 |
| `isEnabled`, `isSelected`, `isFocused`, `isToggleable` | 상태 |
| `hasParent`, `hasAnyChild`, `hasAnyDescendant`, `hasAnyAncestor` | 계층 관계 |
| `and`, `or`, `not` | 조합 |

## 5.3 계층 탐색

```kotlin
composeRule
    .onNodeWithTag("todo_item_1")
    .onChildren()
    .filterToOne(hasText("완료"))
    .assertIsDisplayed()

composeRule
    .onNodeWithText("삭제")
    .onParent()
    .assertHasClickAction()
```

그 밖에 `onChild()`, `onSibling()`, `onSiblings()`, `onAncestors()`가 있다.

## 5.4 컬렉션 다루기

```kotlin
val items = composeRule.onAllNodesWithTag("todo_item")

items.assertCountEquals(3)
items[0].assertTextContains("첫 번째")
items.onFirst().assertIsDisplayed()
items.onLast().performClick()
```

<br>

# 6. Assertion

## 6.1 존재 여부와 표시 여부

```kotlin
node.assertExists()
node.assertDoesNotExist()
node.assertIsDisplayed()
node.assertIsNotDisplayed()
```

`assertExists`는 Semantics 트리에 노드가 있는지만 본다. `assertIsDisplayed`는 화면에 실제로 보이는지까지 본다. LazyColumn에서 화면 밖에 있지만 컴포지션된 아이템은 존재는 하지만 표시되지 않을 수 있다.

## 6.2 상태 검증

```kotlin
node.assertIsEnabled()
node.assertIsNotEnabled()
node.assertIsSelected()
node.assertIsNotSelected()
node.assertIsOn()      // Switch, Checkbox 등
node.assertIsOff()
node.assertIsFocused()
```

## 6.3 텍스트와 접근성 검증

```kotlin
node.assertTextEquals("로그인")
node.assertTextContains("로그")
node.assertContentDescriptionEquals("뒤로 가기")
node.assertHasClickAction()
node.assertHasNoClickAction()
```

`assertTextEquals`는 여러 Text가 병합된 노드라면 모든 텍스트를 순서대로 넘겨야 한다.

## 6.4 커스텀 조건

```kotlin
node.assert(hasText("로그인") and isEnabled())

composeRule.onAllNodesWithTag("item").assertAll(hasClickAction())
composeRule.onAllNodesWithTag("item").assertAny(hasText("완료"))
```

<br>

# 7. Action

## 7.1 클릭과 입력

```kotlin
composeRule.onNodeWithTag("login_button").performClick()

composeRule.onNodeWithTag("email_field").performTextInput("test@test.com")
composeRule.onNodeWithTag("email_field").performTextReplacement("new@test.com")
composeRule.onNodeWithTag("email_field").performTextClearance()
composeRule.onNodeWithTag("email_field").performImeAction()
```

`performTextInput`은 기존 텍스트 뒤에 이어서 입력한다. 값을 통째로 바꾸려면 `performTextReplacement`를 쓴다.

## 7.2 스크롤

```kotlin
composeRule.onNodeWithTag("list").performScrollToIndex(20)
composeRule.onNodeWithTag("list").performScrollToKey("item_20")
composeRule.onNodeWithTag("list").performScrollToNode(hasText("할 일 20"))

// 스크롤 컨테이너 안의 요소를 화면으로
composeRule.onNodeWithText("하단 버튼").performScrollTo()
```

## 7.3 제스처

```kotlin
composeRule.onNodeWithTag("card").performTouchInput {
    swipeLeft()
}

composeRule.onNodeWithTag("box").performTouchInput {
    longClick()
    doubleClick()
    swipeUp(startY = bottom, endY = top)
}
```

`performTouchInput` 블록 안에서는 `center`, `top`, `bottom`, `width`, `height` 같은 노드 기준 좌표를 쓸 수 있다.

## 7.4 키보드와 semantics 액션

```kotlin
composeRule.onNodeWithTag("field").performKeyInput {
    pressKey(Key.Enter)
}

composeRule.onNodeWithTag("item").performSemanticsAction(SemanticsActions.OnClick)
```

<br>

# 8. 상태가 있는 Composable 테스트

## 8.1 대상 Composable

```kotlin
@Composable
fun Counter() {
    var count by rememberSaveable { mutableIntStateOf(0) }

    Column {
        Text(text = "count: $count", modifier = Modifier.testTag("count_text"))
        Button(onClick = { count++ }) {
            Text("증가")
        }
    }
}
```

## 8.2 테스트

```kotlin
class CounterTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun 증가_버튼을_누르면_count가_올라간다() {
        composeRule.setContent { Counter() }

        composeRule.onNodeWithTag("count_text").assertTextEquals("count: 0")

        composeRule.onNodeWithText("증가").performClick()
        composeRule.onNodeWithText("증가").performClick()

        composeRule.onNodeWithTag("count_text").assertTextEquals("count: 2")
    }
}
```

클릭 후 별도로 `waitForIdle()`을 호출하지 않아도 된다. Action 뒤의 Assertion은 recomposition이 끝난 뒤에 실행된다.

## 8.3 State를 테스트에서 직접 제어하기

외부에서 State를 바꿔가며 화면 반응을 확인하고 싶을 때는 `mutableStateOf`를 테스트에서 만들어 넘긴다.

```kotlin
@Test
fun 이름이_바뀌면_화면이_갱신된다() {
    var name by mutableStateOf("현서")

    composeRule.setContent { Greeting(name = name) }
    composeRule.onNodeWithText("Hello, 현서").assertIsDisplayed()

    name = "민수"

    composeRule.onNodeWithText("Hello, 민수").assertIsDisplayed()
}
```

<br>

# 9. 상태 복원 테스트

## 9.1 StateRestorationTester

화면 회전이나 프로세스 종료 후 `rememberSaveable` 값이 복원되는지 확인한다.

```kotlin
@Test
fun 회전_후에도_count가_유지된다() {
    val restorationTester = StateRestorationTester(composeRule)
    restorationTester.setContent { Counter() }

    composeRule.onNodeWithText("증가").performClick()
    composeRule.onNodeWithTag("count_text").assertTextEquals("count: 1")

    // Activity 재생성을 흉내 낸다
    restorationTester.emulateSavedInstanceStateRestore()

    composeRule.onNodeWithTag("count_text").assertTextEquals("count: 1")
}
```

## 9.2 확인 포인트

- `remember`만 쓴 상태는 복원 후 초기값으로 돌아간다. 같은 테스트로 `remember`와 `rememberSaveable`의 차이를 눈으로 확인할 수 있다.
- 이 테스트에서는 `setContent`를 `composeRule`이 아니라 `restorationTester`로 호출해야 한다.
- ViewModel의 `SavedStateHandle` 복원은 이 도구로 검증하기 어렵다. 그 부분은 ViewModel 테스트에서 `SavedStateHandle`을 직접 만들어 넘기는 방식으로 확인한다.

<br>

# 정리

1. Compose UI 테스트는 View가 아니라 **Semantics 트리**를 대상으로 하며, Idle 상태를 자동으로 기다린다.
2. 기본 흐름은 `setContent` → Finder → Action → Assertion이다.
3. `ui-test-junit4`는 `androidTestImplementation`, `ui-test-manifest`는 `debugImplementation`으로 추가한다.
4. 기본 Finder는 병합된 트리를 보고, 개별 노드가 필요하면 `useUnmergedTree = true`를 쓴다.
5. `testTag`는 텍스트나 접근성 정보로 찾기 어려운 경우에만 쓰고, 상수로 모아서 관리한다.
6. `onNode...`는 노드가 정확히 하나여야 하고, 여러 개는 `onAllNodes...`로 다룬다.
7. `assertExists`는 트리에 있는지, `assertIsDisplayed`는 화면에 보이는지까지 확인한다.
8. 상태 변화는 클릭 후 바로 검증해도 되고, `rememberSaveable` 복원은 `StateRestorationTester`로 확인한다.
9. 다음 노트(2)에서 MVI 화면, LazyColumn, Navigation, Hilt, 비동기, Robolectric을 다룬다.
