# CompositionLocal

## 목차

1. CompositionLocal이란?
2. 왜 사용하는가?
3. CompositionLocalProvider
4. LocalContext
5. LocalDensity
6. LocalConfiguration
7. 직접 CompositionLocal 만들기
8. compositionLocalOf와 staticCompositionLocalOf의 차이
9. 언제 사용하면 좋을까?
10. 사용 시 주의사항
11. 정리

<br>

## 1. CompositionLocal이란?

`CompositionLocal`은 **부모 Composable이 값을 제공하고, 자식 Composable 어디에서든 해당 값을 전달받을 수 있는 기능**이다.

매개변수(Parameter)를 계속 전달하지 않아도 필요한 값을 사용할 수 있도록 도와준다.

예를 들어 화면 깊숙한 곳에서 `Context`가 필요하다면

```kotlin
val context = LocalContext.current
```

이처럼 바로 가져올 수 있다.

이때 `LocalContext`도 `CompositionLocal`로 만들어져 있다.

<br>

## 2. 왜 사용하는가?

일반적으로 부모의 데이터를 자식에게 전달하려면 매개변수를 사용한다.

```kotlin
@Composable
fun Screen() {
    Parent()
}

@Composable
fun Parent() {
    Child("Android")
}

@Composable
fun Child(name: String) {
    Text(name)
}
```

구조가 깊어질수록 사용하지 않는 Composable도 값을 전달만 해야 한다.

```text
Screen
 └── Parent
      └── Container
           └── Content
                └── Item
                     └── Text
```

만약 `Item`에서 Context가 필요하다면

```kotlin
Screen(context)
    ↓
Parent(context)
    ↓
Container(context)
    ↓
Content(context)
    ↓
Item(context)
```

이처럼 계속 전달해야 한다.

이를 **Prop Drilling**이라고 한다.

CompositionLocal을 사용하면

```kotlin
val context = LocalContext.current
```

처럼 필요한 위치에서 바로 가져올 수 있다.

<br>

## 3. CompositionLocalProvider

CompositionLocal은 값을 제공하는 곳이 필요하다.

바로 `CompositionLocalProvider`이다.

```kotlin
val LocalUserName = compositionLocalOf { "Guest" }

@Composable
fun MyScreen() {

    CompositionLocalProvider(
        LocalUserName provides "Compose"
    ) {

        Greeting()
    }
}

@Composable
fun Greeting() {

    Text(LocalUserName.current)
}
```

실행 결과

```
Compose
```

### 동작 과정

1. `CompositionLocalProvider`가 값을 제공한다.
2. Provider 아래의 모든 Composable이 접근할 수 있다.
3. `.current`를 통해 값을 가져온다.

부모가 값을 바꾸면 자식도 자동으로 새로운 값을 사용한다.

<br>

## 4. LocalContext

가장 많이 사용하는 CompositionLocal이다.

```kotlin
@Composable
fun Example() {

    val context = LocalContext.current

    Button(
        onClick = {
            Toast.makeText(
                context,
                "Hello",
                Toast.LENGTH_SHORT
            ).show()
        }
    ) {
        Text("Toast")
    }
}
```

왜 사용할까?

Composable에는 Activity나 Context가 없다.

그래서 Compose가 Context를 CompositionLocal로 제공한다.

<br>

## 5. LocalDensity

픽셀(px)과 dp를 변환할 때 사용한다.

```kotlin
@Composable
fun Example() {

    val density = LocalDensity.current

    val width = with(density) {
        100.dp.toPx()
    }
}
```

반대로

```kotlin
with(density) {
    px.toDp()
}
```

도 가능하다.

커스텀 레이아웃을 만들 때 자주 사용한다.

<br>

## 6. LocalConfiguration

현재 기기 정보를 가져온다.

```kotlin
@Composable
fun Example() {

    val configuration = LocalConfiguration.current

    Text(
        text = "${configuration.screenWidthDp}dp"
    )
}
```

가져올 수 있는 정보

- 화면 너비
- 화면 높이
- 글자 크기
- 화면 방향
- Locale

태블릿 대응이나 반응형 UI에서 많이 사용한다.

<br>

## 7. 직접 CompositionLocal 만들기

직접 값을 공유할 수도 있다.

```kotlin
val LocalUser = compositionLocalOf {
    "Unknown"
}
```

Provider에서 값을 넣는다.

```kotlin
@Composable
fun App() {

    CompositionLocalProvider(
        LocalUser provides "Android"
    ) {

        HomeScreen()
    }
}
```

아래에서는 바로 사용할 수 있다.

```kotlin
@Composable
fun HomeScreen() {

    Text(LocalUser.current)
}
```

실행 결과

```
Android
```

Theme, 디자인 시스템, 사용자 정보 등을 전달할 때 많이 사용한다.

<br>

## 8. compositionLocalOf와 staticCompositionLocalOf의 차이

### compositionLocalOf

```kotlin
val LocalUser = compositionLocalOf {
    "Guest"
}
```

값이 변경되면

**해당 값을 사용하는 Composable만 다시 그린다.**

재구성 범위가 작아 성능상 유리한 경우가 많다.

<br>

### staticCompositionLocalOf

```kotlin
val LocalTheme = staticCompositionLocalOf {
    DefaultTheme
}
```

값이 변경되면

**Provider 아래의 모든 Composable이 다시 그려진다.**

대신 Compose가 어떤 Composable이 값을 사용하는지 추적하지 않기 때문에
읽기 비용이 조금 더 적다.

주로 거의 변경되지 않는 값을 전달할 때 사용한다.

예를 들어

- App Theme
- Design System
- Typography
- Shapes
- Colors

같은 값들이다.

### 어떤 것을 사용해야 할까?

| 상황 | 추천 |
|-------|------|
| 자주 변경되는 값 | compositionLocalOf |
| 거의 변경되지 않는 값 | staticCompositionLocalOf |

대부분의 경우 `compositionLocalOf`를 사용하면 된다.

<br>

## 9. 언제 사용하면 좋을까?

다음과 같은 경우에 적합하다.

- Context
- Density
- Configuration
- Theme
- Typography
- Colors
- Spacing
- 디자인 시스템
- 권한 정보
- Locale

공통적으로 **앱 여러 곳에서 사용하는 공통 데이터**를 전달할 때 사용한다.

<br>

## 10. 사용 시 주의사항

### 모든 데이터를 CompositionLocal로 전달하면 안 된다.

좋지 않은 예

```kotlin
val LocalUserName = compositionLocalOf { "" }
val LocalEmail = compositionLocalOf { "" }
val LocalAge = compositionLocalOf { 0 }
val LocalAddress = compositionLocalOf { "" }
```

화면 상태나 비즈니스 데이터까지 CompositionLocal로 관리하면
데이터의 흐름을 파악하기 어려워진다.

일반적인 UI 상태는 **State Hoisting**으로 전달하는 것이 더 적합하다.

<br>

### 화면 상태를 대신하는 용도가 아니다.

CompositionLocal은 **공통으로 사용하는 환경 정보나 의존성**을 전달하기 위한 기능이다.

예를 들어

- Theme
- Context
- Density

같은 값은 적합하지만

- TextField 입력값
- 리스트 데이터
- ViewModel의 State

같은 화면 상태는 매개변수나 StateFlow 등을 사용하는 것이 좋다.

<br>

## 11. 정리

- CompositionLocal은 부모에서 자식으로 공통 값을 전달하는 기능이다.
- 매개변수를 계속 전달하는 Prop Drilling을 줄일 수 있다.
- 값을 제공할 때는 `CompositionLocalProvider`를 사용한다.
- 값을 사용할 때는 `.current`로 가져온다.
- `LocalContext`, `LocalDensity`, `LocalConfiguration`도 CompositionLocal이다.
- 자주 변경되는 값은 `compositionLocalOf`, 거의 변경되지 않는 값은 `staticCompositionLocalOf`를 사용한다.
- 화면 상태를 전달하기 위한 용도가 아니라 공통 환경 정보나 의존성을 공유하기 위한 기능이다.
