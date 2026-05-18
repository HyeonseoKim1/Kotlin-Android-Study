# WindowInsetsControllerCompat

## WindowInsetsControllerCompat란?

WindowInsetsControllerCompat는 상태바(Status Bar)와 네비게이션 바(Navigation Bar)를 제어하는 클래스이다.

안드로이드에서는 이를 통해 다음과 같은 설정을 처리할 수 있다.

- 상태바 숨기기
- 네비게이션 바 숨기기
- 상태바 아이콘 색 변경
- 풀스크린 모드 설정

<br>

### 상태바(Status Bar) / 네비게이션 바(Navigation Bar)

#### 상태바 (Status Bar)

화면 맨 위 영역. 시간, 배터리, 와이파이, 알림, 아이콘 등이 표시된다.

#### 네비게이션 바 (Navigation Bar)

화면 맨 아래 영역. 뒤로가기, 홈, 최근 앱 버튼이 표시된다.

<br>

## WindowInsetsControllerCompat 이름 의미

| 단어 | 의미 |
|---|---|
| Window | 현재 앱 화면(Window) |
| Insets | 시스템 바로 인해 생기는 화면 여백 |
| Controller | 제어 객체 |
| Compat | 구버전 안드로이드 호환 지원 |

> 즉, 시스템 UI(상태바/네비게이션 바)를 제어하는 호환 컨트롤러

<br>

## 컨트롤러 가져오기

보통 다음과 같이 생성한다.

```kotlin
val controller =
    WindowCompat.getInsetsController(
        window,
        window.decorView
    )
```

<br>

## WindowCompat.getInsetsController(...) 이해하기

전체 코드:

```kotlin
WindowCompat.getInsetsController(
    window,
    window.decorView
)
```

### window

현재 Activity의 전체 화면(Window)

### window.decorView

현재 화면의 최상위 View(루트 View)

시스템 UI와 연결되는 기준 View 역할을 한다.

<br>

## 상태바 아이콘 색 변경

### 검정 아이콘 사용

```kotlin
controller.isAppearanceLightStatusBars = true
```

- 밝은 상태바 배경
- 검정색 아이콘 사용


### 흰 아이콘 사용

```kotlin
controller.isAppearanceLightStatusBars = false
```


- 어두운 상태바 배경
- 흰색 아이콘 사용

<br>

## 상태바 숨기기

```kotlin
controller.hide(
    WindowInsetsCompat.Type.statusBars()
)
```

- 상태바 숨김
- 풀스크린 화면 가능

주로 영상 앱, 게임, 사진 뷰어 등에서 사용한다.

<br>

## 네비게이션 바 숨기기

```kotlin
controller.hide(
    WindowInsetsCompat.Type.navigationBars()
)
```

<br>

## 시스템 바 전체 숨기기

```kotlin
controller.hide(
    WindowInsetsCompat.Type.systemBars()
)
```

status bar + navigation bar 둘 다 숨기는 것이다.

<br>

## 시스템 바 다시 표시하기

```kotlin
controller.show(
    WindowInsetsCompat.Type.systemBars()
)
```

숨겼던 시스템 바를 다시 표시한다.

<br>

## 전체 예제

```kotlin
val controller =
    WindowCompat.getInsetsController(
        window,
        window.decorView
    )

// 상태바 아이콘 검정색 설정
controller.isAppearanceLightStatusBars = true

// 상태바 숨기기
controller.hide(
    WindowInsetsCompat.Type.statusBars()
)
```

<br>

## Edge-To-Edge와 함께 사용

실무에서는 보통 Edge-To-Edge UI와 함께 사용한다.

```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)
```

앱 화면을 상태바 영역까지 확장

최근 Android 앱에서는 자주 사용되는 방식이다.

<br>

## WindowInsetsControllerCompat 핵심 정리

| 기능 | 설명 |
|---|---|
| 상태바 숨기기 | 가능 |
| 네비게이션 바 숨기기 | 가능 |
| 상태바 아이콘 색 변경 | 가능 |
| 풀스크린 UI | 가능 |
| Edge-To-Edge UI | 함께 사용 가능 |

<br>

## 자주 사용하는 코드

### 컨트롤러 생성

```kotlin
val controller =
    WindowCompat.getInsetsController(
        window,
        window.decorView
    )
```



### 상태바 아이콘 색 변경

```kotlin
controller.isAppearanceLightStatusBars = true
```



### 상태바 숨기기

```kotlin
controller.hide(
    WindowInsetsCompat.Type.statusBars()
)
```



### 네비게이션 바 숨기기

```kotlin
controller.hide(
    WindowInsetsCompat.Type.navigationBars()
)
```



### 시스템 바 전체 숨기기

```kotlin
controller.hide(
    WindowInsetsCompat.Type.systemBars()
)
```
