# SplashScreen

## Splash Screen이란

Splash Screen은 앱이 실행될 때 가장 먼저 표시되는 화면이다. 사용자가 앱 아이콘을 터치하면 시스템은 앱을 실행하는 동시에 Splash Screen을 표시하며, 앱은 이 시간 동안 실행에 필요한 최소한의 초기 작업을 수행한다.

Splash Screen은 단순히 로고를 보여주는 화면이 아니다. 앱이 정상적으로 실행되고 있다는 피드백을 제공하고, 초기화가 완료될 때까지 자연스러운 대기 화면을 제공하는 역할을 한다.

또한 로그인 여부, 온보딩 여부, 사용자 설정 등을 확인하여 사용자가 처음 보게 될 화면(Home, Login, Onboarding)을 결정하는 역할도 수행한다.

Android 12(API 31)부터는 시스템에서 기본 Splash Screen을 제공하며, 대부분의 앱은 동일한 방식으로 시작 화면을 표시한다.

<br>

## Splash Screen이 필요한 이유

앱은 실행 직후 다양한 초기화 작업을 수행한다.

대표적으로 다음과 같은 작업이 있다.

- 로그인 상태 확인
- Access Token 확인 및 갱신
- DataStore 조회
- 사용자 설정 로드
- Firebase 초기화
- Remote Config 조회
- Feature Flag 조회
- 사용자 정보 조회

이러한 작업이 완료되기 전에 화면을 먼저 표시하면 사용자는 여러 화면이 연속으로 전환되는 모습을 보게 된다.

예를 들어 로그인 여부를 확인하지 않고 Home 화면을 먼저 표시한다면 다음과 같은 흐름이 발생할 수 있다.

Home → Login

또는 온보딩 여부를 확인하지 않은 경우에는 다음과 같은 흐름이 발생할 수 있다.

Home → Onboarding

이처럼 잠깐 동안 잘못된 화면이 나타났다가 다른 화면으로 이동하는 현상을 **화면 깜빡임(Flickering)**이라고 한다.

Splash Screen에서 초기화가 끝난 뒤 목적 화면을 결정하면 이러한 문제를 방지할 수 있다.

---

## Splash Screen의 역할

Splash Screen은 다음과 같은 역할을 수행한다.

- 앱이 실행 중임을 사용자에게 알린다.
- 앱 실행에 필요한 최소한의 초기화를 수행한다.
- 첫 화면을 결정한다.
- 불필요한 화면 전환을 방지한다.
- 앱의 브랜드 이미지를 전달한다.

Splash Screen은 데이터를 많이 불러오는 화면이 아니라 앱 실행에 반드시 필요한 최소한의 작업만 수행하는 것이 중요하다.

<br>

## Android 12 이전과 이후

### Android 12 이전

Android 11 이하에서는 개발자가 Splash Activity를 직접 구현하는 방식이 일반적이었다.

앱 실행 과정은 다음과 같다.

```
Launcher
    ↓
SplashActivity
    ↓
초기화
    ↓
MainActivity
```

앱마다 구현 방식이 달랐으며 애니메이션도 일관되지 않았다.

또한 흰 화면이 잠시 나타나거나 Activity 전환이 부자연스러운 문제가 자주 발생하였다.

<br>

### Android 12 이후

Android 12부터는 시스템에서 Splash Screen을 기본 제공한다.

앱 실행 과정은 다음과 같다.

```
앱 실행
    ↓
System Splash Screen
    ↓
초기화
    ↓
첫 화면
```

개발자는 Splash 화면 자체를 구현하는 것이 아니라 초기화 로직에만 집중하면 된다.

덕분에 모든 앱이 동일한 방식으로 시작되며 사용자 경험도 향상되었다.

<br>

## SplashScreen API

AndroidX에서는 Splash Screen을 쉽게 사용할 수 있도록 `core-splashscreen` 라이브러리를 제공한다.

주요 API는 다음과 같다.

- `installSplashScreen()`
- `setKeepOnScreenCondition()`
- `setOnExitAnimationListener()`

각 API는 Splash Screen의 생성, 유지, 종료 시점을 제어하는 역할을 한다.

<br>

## 의존성 추가

Splash Screen API를 사용하려면 다음 라이브러리를 추가해야 한다.

```kotlin
implementation("androidx.core:core-splashscreen:1.0.1")
```

<br>

## installSplashScreen()

Splash Screen을 활성화하는 함수이다.

```kotlin
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen()

        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}
```

반드시 `super.onCreate()`보다 먼저 호출해야 한다.

시스템은 Activity가 생성되기 전에 Splash Screen을 준비한다. 따라서 `super.onCreate()` 이후에 호출하면 Splash Screen이 정상적으로 적용되지 않을 수 있다.

<br>

## Theme 설정

Splash Screen의 배경색과 아이콘은 Theme에서 설정한다.

```xml
<style
    name="Theme.App.Starting"
    parent="Theme.SplashScreen">

    <item name="windowSplashScreenBackground">
        @color/white
    </item>

    <item name="windowSplashScreenAnimatedIcon">
        @drawable/ic_launcher_foreground
    </item>

    <item name="postSplashScreenTheme">
        @style/Theme.App
    </item>

</style>
```

### 주요 속성

|속성|설명|
|---|---|
|windowSplashScreenBackground|Splash Screen의 배경색|
|windowSplashScreenAnimatedIcon|표시할 아이콘|
|windowSplashScreenAnimationDuration|아이콘 애니메이션 시간|
|postSplashScreenTheme|Splash 종료 후 적용할 Theme|

<br>

## setKeepOnScreenCondition()

초기화가 완료될 때까지 Splash Screen을 유지하는 API이다.

```kotlin
val splashScreen = installSplashScreen()

splashScreen.setKeepOnScreenCondition {
    !viewModel.isReady.value
}
```

람다식이 `true`를 반환하는 동안 Splash Screen은 계속 유지된다.

초기화가 완료되어 `false`를 반환하면 시스템이 자동으로 Splash Screen을 종료한다.

이 기능은 DataStore 조회, 로그인 확인, 토큰 갱신처럼 짧은 비동기 작업을 기다릴 때 자주 사용한다.

<br>

## setOnExitAnimationListener()

Splash Screen이 종료되는 시점을 직접 제어하는 API이다.

```kotlin
splashScreen.setOnExitAnimationListener { provider ->

    provider.remove()

}
```

기본적으로는 시스템 애니메이션이 적용된다.

브랜드에 맞는 Fade, Scale, Slide 등의 애니메이션을 적용하고 싶다면 이 API를 사용할 수 있다.

애니메이션이 끝난 뒤에는 반드시 `remove()`를 호출하여 Splash Screen을 제거해야 한다.

<br>

## SplashViewModel을 사용하는 이유

Splash Screen에서는 대부분 비동기 작업이 수행된다.

대표적인 작업은 다음과 같다.

- DataStore 조회
- 로그인 상태 확인
- Access Token 확인
- Firebase 초기화
- Remote Config 조회
- 사용자 정보 조회

이러한 작업을 Activity에서 직접 수행하면 생명주기 관리가 어려워지고 화면 회전과 같은 구성 변경 시 초기화가 다시 수행될 가능성이 있다.

ViewModel에서 초기화를 수행하면 상태를 안전하게 관리할 수 있으며 UI와 비즈니스 로직도 자연스럽게 분리할 수 있다.

```kotlin
class SplashViewModel : ViewModel() {

    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {

            // 초기화 작업

            _isReady.value = true
        }
    }
}
```

`isReady`가 `true`가 되는 시점은 앱 실행에 필요한 최소한의 초기화가 완료되었음을 의미한다.

Activity는 이 상태를 기반으로 Splash Screen을 종료하고 다음 화면으로 이동한다.


## Compose에서 사용하는 방법

Compose에서는 Splash Screen을 직접 구현하기보다 Activity에서 SplashScreen API를 사용하고, Compose는 초기화 상태를 관찰하여 화면을 전환하는 구조를 많이 사용한다.

초기화 작업은 ViewModel에서 수행하고 Compose에서는 상태만 관찰하는 것이 일반적이다.

```kotlin
@Composable
fun SplashRoute(
    viewModel: SplashViewModel = hiltViewModel(),
    onNavigateHome: () -> Unit
) {
    val isReady by viewModel.isReady.collectAsStateWithLifecycle()

    LaunchedEffect(isReady) {
        if (isReady) {
            onNavigateHome()
        }
    }
}
```

이처럼 UI는 상태를 관찰하고, 실제 초기화 로직은 ViewModel이 담당하도록 역할을 분리하는 것이 좋다.

<br>

## DataStore와 함께 사용하는 방법

Splash Screen에서는 DataStore를 이용하여 앱 실행에 필요한 설정을 불러오는 경우가 많다.

대표적인 예시는 다음과 같다.

- 온보딩 완료 여부
- 자동 로그인 설정
- 다크 모드 설정
- 언어 설정

예를 들어 온보딩 완료 여부를 확인하는 코드는 다음과 같다.

```kotlin
val onboardingCompleted =
    dataStore.onboardingCompletedFlow.first()

if (onboardingCompleted) {
    navigateToHome()
} else {
    navigateToOnboarding()
}
```

DataStore는 비동기로 동작하기 때문에 Splash Screen에서 함께 사용하는 경우가 많다.

<br>

## 로그인 상태 확인

Splash Screen에서 가장 많이 수행하는 작업 중 하나는 로그인 여부 확인이다.

로그인 상태에 따라 첫 화면을 결정한다.

```
Splash
    ↓
로그인 확인
    ↓
Home
```

또는

```
Splash
    ↓
로그인 안됨
    ↓
Login
```

Access Token이 만료되었다면 Refresh Token으로 새로운 토큰을 발급받은 뒤 Home 화면으로 이동하는 구조를 사용할 수도 있다.

<br>

## Onboarding과 함께 사용하는 방법

앱을 처음 실행한 사용자인지 확인하는 작업도 Splash Screen에서 자주 수행한다.

```
Splash
    ↓
Onboarding 완료
    ↓
Home
```

```
Splash
    ↓
Onboarding 미완료
    ↓
Onboarding
```

Onboarding 완료 여부는 일반적으로 DataStore에 저장해 두고 앱 실행 시 조회한다.

이러한 방식은 Home 화면이 잠시 나타났다가 Onboarding으로 이동하는 현상을 방지할 수 있다.

<br>

## Navigation과 연결

Splash Screen에서는 화면을 구성하기보다 목적 화면을 결정하는 역할을 수행한다.

```
Splash
    ↓
초기화 완료
    ↓
Navigation
        ├── Home
        ├── Login
        └── Onboarding
```

Splash Screen은 가능한 한 빠르게 종료하는 것이 좋다.

실제 화면 구성은 Navigation Graph에서 담당하도록 구성하는 것이 일반적이다.

<br>

## Splash Screen에서 수행하면 좋은 작업

Splash Screen에서는 앱 실행에 반드시 필요한 작업만 수행해야 한다.

대표적인 작업은 다음과 같다.

- 로그인 상태 확인
- Access Token 확인
- Refresh Token 갱신
- DataStore 조회
- 사용자 설정 로드
- Firebase 초기화
- Remote Config 조회
- Feature Flag 조회
- 사용자 권한 확인

이러한 작업은 앱이 정상적으로 실행되기 위해 필요한 최소한의 작업이다.

<br>

## Splash Screen에서 수행하지 않는 것이 좋은 작업

Splash Screen은 오래 유지될수록 사용자 경험이 나빠진다.

따라서 시간이 오래 걸리는 작업은 Splash Screen에서 수행하지 않는 것이 좋다.

예를 들면 다음과 같다.

- 대용량 이미지 다운로드
- 모든 API 호출
- 대량의 데이터 동기화
- 파일 다운로드
- 복잡한 데이터 가공
- 사용자의 입력이 필요한 작업

이러한 작업은 Home 화면으로 이동한 뒤 백그라운드에서 수행하는 것이 좋다.

<br>

## 앱 실행 흐름

일반적인 앱 실행 흐름은 다음과 같다.

```
앱 실행
    ↓
System Splash Screen
    ↓
installSplashScreen()
    ↓
SplashViewModel 생성
    ↓
초기화 시작
        ├── DataStore 조회
        ├── 로그인 확인
        ├── Firebase 초기화
        ├── Remote Config 조회
        └── 사용자 설정 조회
    ↓
초기화 완료
    ↓
목적 화면 결정
    ↓
Home / Login / Onboarding
```

Splash Screen에서는 첫 화면을 결정하는 것까지만 담당하고 이후의 화면 구성은 Navigation이 담당한다.

<br>

## Best Practice

### 앱 실행에 필요한 작업만 수행한다.

Splash Screen은 앱 실행에 반드시 필요한 작업만 수행하는 것이 좋다.

필요 이상의 작업을 수행하면 앱 시작 속도가 느려진다.

<br>

### ViewModel에서 초기화를 수행한다.

초기화 작업을 Activity에 작성하기보다 ViewModel에서 수행하면 생명주기 관리가 쉬워지고 UI와 비즈니스 로직도 자연스럽게 분리된다.

<br>

### 초기화 완료 여부로 Splash를 종료한다.

일정 시간을 기다리는 방식보다 실제 초기화 완료 여부를 기준으로 종료하는 것이 좋다.

좋지 않은 예

```kotlin
delay(2000)
```

좋은 예

```kotlin
setKeepOnScreenCondition {
    !viewModel.isReady.value
}
```

<br>

### 하나의 목적 화면만 결정한다.

Splash Screen에서는 여러 번 화면을 이동하지 않는 것이 좋다.

좋지 않은 예

```
Splash
    ↓
Home
    ↓
Login
```

좋은 예

```
Splash
    ↓
Login
```

첫 화면은 Splash에서 한 번만 결정하는 것이 사용자 경험 측면에서 유리하다.

<br>

### 초기화 시간을 최소화한다.

Splash Screen은 가능한 한 짧게 유지하는 것이 좋다.

사용자가 오래 기다려야 하는 구조는 피해야 한다.

<br>

## 자주 하는 실수

### delay()로 Splash를 유지하는 경우

Splash Screen은 시간을 기준으로 유지하는 것이 아니라 초기화 완료 여부를 기준으로 유지해야 한다.

잘못된 예

```kotlin
delay(3000)
```

이 방식은 초기화가 이미 끝났더라도 사용자를 불필요하게 기다리게 만든다.

<br>

### 모든 데이터를 Splash에서 불러오는 경우

앱 시작에 필요하지 않은 데이터까지 모두 가져오면 Splash Screen이 길어진다.

필수 데이터만 가져오고 나머지는 Home 화면에서 불러오는 것이 좋다.

<br>

### Activity에서 모든 초기화를 수행하는 경우

Activity에 모든 로직을 작성하면 코드가 비대해지고 유지보수가 어려워진다.

초기화 로직은 ViewModel이나 UseCase로 분리하는 것이 좋다.

<br>

### Navigation을 여러 번 호출하는 경우

상태 변화에 따라 Navigation이 여러 번 실행될 수 있다.

이러한 문제를 방지하기 위해 일회성 이벤트(Event) 또는 적절한 상태 관리 방식을 사용하는 것이 좋다.

<br>

## 정리

- Splash Screen은 앱 실행 시 가장 먼저 표시되는 화면이다.
- Android 12부터는 시스템에서 기본 Splash Screen을 제공한다.
- `installSplashScreen()`으로 Splash Screen을 활성화한다.
- `setKeepOnScreenCondition()`을 사용하면 초기화 완료 시점까지 Splash Screen을 유지할 수 있다.
- Splash Screen에서는 로그인 여부, 온보딩 여부, 사용자 설정 등 앱 실행에 필요한 최소한의 초기화만 수행하는 것이 좋다.
- Compose에서는 ViewModel이 초기화를 담당하고 UI는 상태만 관찰하는 구조를 많이 사용한다.
- Splash Screen에서는 하나의 목적 화면만 결정하고 이후 화면 구성은 Navigation이 담당하도록 설계하는 것이 일반적이다.
