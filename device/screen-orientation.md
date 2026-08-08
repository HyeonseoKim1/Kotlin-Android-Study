# Screen Orientation

## 목차

1. 화면 방향 전환이란?
2. android:screenOrientation
3. setRequestedOrientation()
4. 화면 회전 시 Activity가 재생성되는 이유
5. configChanges의 역할
6. 회전과 Configuration 변경
7. Compose에서 화면 방향 감지하기
8. LocalConfiguration 활용
9. 세로·가로 화면에 따른 UI 대응
10. 태블릿·대형 화면에서의 방향 전환 고려사항

<br>

## 1. 화면 방향 전환이란?

Android 기기는 세로(Portrait)와 가로(Landscape) 방향으로 화면을 표시할 수 있다.

화면 방향이 변경되면 Android에서는 단순히 화면만 회전시키지 않고 Configuration 변경으로 처리한다.

```
세로 화면
  ↓
사용자가 기기 회전
  ↓
Configuration 변경
  ↓
Activity 재생성
  ↓
새로운 화면 구성
```

화면 방향 변경을 이해하려면 Configuration 변경과 Activity Lifecycle의 관계를 함께 알아야 한다.

<br>

## 2. android:screenOrientation

Manifest에서 Activity의 화면 방향을 지정할 수 있다.

```xml
<activity
    android:name=".MainActivity"
    android:screenOrientation="portrait" />
```

주요 값은 다음과 같다.

| 값 | 설명 |
|---|---|
| `portrait` | 세로 고정 |
| `landscape` | 가로 고정 |
| `unspecified` | 시스템이 방향 결정 |
| `fullSensor` | 센서에 따라 방향 변경 |

다만 화면 방향을 무조건 고정하는 것이 항상 좋은 것은 아니다. 태블릿이나 대형 화면에서는 가로 화면을 활용하는 것이 더 적절할 수 있다.

<br>

## 3. setRequestedOrientation()

코드에서 화면 방향을 변경할 수도 있다.

```kotlin
requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
```

세로로 변경하려면 다음과 같다.

```kotlin
requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
```

Activity의 `requestedOrientation`을 변경하면 Android가 화면 방향 변경을 처리한다. 예를 들어 특정 화면에서만 가로 화면을 사용해야 한다면 해당 Activity에서 설정할 수 있다.

```kotlin
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
}
```

<br>

## 4. 화면 회전 시 Activity가 재생성되는 이유

화면이 회전하면 화면 크기와 방향이 변경된다. Android에서는 이런 환경 변화를 Configuration 변경으로 처리한다.

대표적인 Configuration 변경에는 다음이 있다.

- 화면 방향
- 화면 크기
- 언어
- 글꼴 크기
- 다크 모드

화면 방향이 변경되면 기본적으로 Activity가 다시 생성된다.

```
Activity
  ↓
onPause()
  ↓
onStop()
  ↓
onDestroy()
  ↓
새 Activity 생성
  ↓
onCreate()
  ↓
onStart()
  ↓
onResume()
```

이렇게 하는 이유는 변경된 Configuration에 맞춰 새로운 리소스와 UI 환경을 다시 구성하기 위해서다.

예를 들어 다음과 같이 리소스를 분리할 수 있다.

```
res/
├── layout/
│   └── activity_main.xml
│
└── layout-land/
    └── activity_main.xml
```

세로에서는 `layout/activity_main.xml`을 사용하고, 가로에서는 `layout-land/activity_main.xml`을 사용할 수 있다.

<br>

## 5. configChanges의 역할

Activity에 다음과 같이 설정할 수도 있다.

```xml
<activity
    android:name=".MainActivity"
    android:configChanges="orientation|screenSize" />
```

이 경우 해당 Configuration 변경을 Activity가 직접 처리한다. Configuration이 변경되면 Activity를 재생성하는 대신 `onConfigurationChanged()`가 호출된다.

```kotlin
override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
}
```

흐름은 다음과 같이 달라진다.

```
기본 동작
화면 회전 → Configuration 변경 → Activity 재생성

configChanges 사용
화면 회전 → Configuration 변경 → onConfigurationChanged()
```

따라서 `configChanges`는 Activity 재생성을 막는 설정이라고 이해할 수 있다. 하지만 무조건 사용하는 것은 좋지 않다. Activity 재생성 과정에서 Android가 제공하는 상태 복원 메커니즘을 직접 처리해야 할 가능성이 생기기 때문이다.

<br>

## 6. 회전과 Configuration 변경

화면 방향은 `Configuration.orientation`으로 확인할 수 있다.

```kotlin
when (resources.configuration.orientation) {
    Configuration.ORIENTATION_PORTRAIT -> {
        // 세로
    }

    Configuration.ORIENTATION_LANDSCAPE -> {
        // 가로
    }
}
```

현재 방향만 확인하는 것이 목적이라면 다음과 같이 사용할 수 있다.

```kotlin
val orientation = resources.configuration.orientation
```

중요한 점은 화면 회전 자체를 상태 변화로만 생각하면 안 된다는 것이다. Android 입장에서는 현재 실행 환경이 변경된 것이며, 이를 Configuration 변경으로 관리한다.

<br>

## 7. Compose에서 화면 방향 감지하기

Jetpack Compose에서는 `LocalConfiguration`을 사용할 수 있다.

```kotlin
@Composable
fun Screen() {
    val configuration = LocalConfiguration.current

    // configuration 사용
}
```

`LocalConfiguration`은 현재 Android Configuration을 Compose에서 사용할 수 있도록 제공한다. 화면 방향도 확인할 수 있다.

```kotlin
@Composable
fun Screen() {
    val configuration = LocalConfiguration.current

    when (configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> {
            PortraitContent()
        }

        Configuration.ORIENTATION_LANDSCAPE -> {
            LandscapeContent()
        }
    }
}
```

<br>

## 8. LocalConfiguration 활용

화면 방향뿐 아니라 화면 크기와 관련된 Configuration도 확인할 수 있다.

```kotlin
@Composable
fun Screen() {
    val configuration = LocalConfiguration.current

    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
}
```

예를 들어 가로 화면에서는 UI 배치를 변경할 수 있다.

```kotlin
@Composable
fun Screen() {
    val configuration = LocalConfiguration.current

    if (configuration.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    ) {
        LandscapeContent()
    } else {
        PortraitContent()
    }
}
```

Compose에서는 이런 방식으로 Configuration에 따라 UI를 다시 구성할 수 있다.

<br>

## 9. 세로·가로 화면에 따른 UI 대응

화면 방향에 따라 UI를 단순히 두 개로 나누는 것보다 화면 공간에 맞춰 UI가 유연하게 변하도록 만드는 것이 중요하다.

예를 들어 세로에서는 `Column`을 사용하고 가로에서는 `Row`를 사용할 수 있다.

```kotlin
@Composable
fun Content() {
    val configuration = LocalConfiguration.current

    if (configuration.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    ) {
        Row {
            Menu()
            Content()
        }
    } else {
        Column {
            Menu()
            Content()
        }
    }
}
```

하지만 실제 앱에서는 orientation만으로 UI를 판단하기보다 사용 가능한 화면 크기와 Window Size Class 등을 함께 고려하는 것이 더 적절하다.

<br>

## 10. 태블릿·대형 화면에서의 방향 전환 고려사항

스마트폰에서는 세로 화면이 기본적인 경우가 많다. 하지만 태블릿이나 폴더블처럼 화면이 큰 기기에서는 가로와 세로 모두 고려해야 한다.

예를 들어 다음과 같은 차이가 있을 수 있다.

```
세로
┌──────────┐
│  Header  │
├──────────┤
│          │
│ Content  │
│          │
└──────────┘

가로
┌──────────┬──────────┐
│          │          │
│  Menu    │ Content  │
│          │          │
└──────────┴──────────┘
```

따라서 단순히

```kotlin
if (landscape) {
    ...
}
```

로 모든 레이아웃을 결정하기보다는 화면 크기와 현재 사용할 수 있는 공간을 기준으로 반응형 UI를 설계하는 것이 중요하다. 특히 Compose에서는 Window Size Class와 같은 Window 기반 적응형 UI 개념으로 확장해서 학습할 수 있다.

## 정리

- 화면 회전은 Android에서 Configuration 변경이다.
- Configuration이 변경되면 기본적으로 Activity가 재생성되며, 이를 통해 새로운 환경에 맞는 리소스와 UI를 다시 구성한다.
- 'android:screenOrientation`으로 Activity의 방향을 지정할 수 있고, `requestedOrientation`으로 코드에서 방향을 변경할 수 있다.
- `configChanges`를 사용하면 특정 Configuration 변경을 Activity가 직접 처리할 수 있다.
- Compose에서는 `LocalConfiguration.current`로 현재 Configuration을 확인할 수 있으며, 화면 방향뿐 아니라 화면 크기와 사용 가능한 공간을 고려해 UI를 설계하는 것이 중요하다.
