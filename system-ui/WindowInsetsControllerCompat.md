# WindowInsetsControllerCompat 정리

안드로이드에서 화면을 만들다 보면:

- 상태바(Status Bar)
- 네비게이션 바(Navigation Bar)

를 숨기거나 색을 바꾸고 싶을 때가 많다.

이때 WindowInsetsControllerCompat를 사용한다.

---

# 1. 상태바 / 네비게이션 바란?

## 🔹 상태바 (Status Bar)

화면 맨 위 바.

- 시간
- 배터리
- 와이파이
- 알림 아이콘

등이 표시된다.

---

## 🔹 네비게이션 바 (Navigation Bar)

화면 맨 아래 바.

- 뒤로가기
- 홈
- 최근 앱

버튼이 있는 영역이다.

---

# 2. WindowInsetsControllerCompat란?

> "시스템 바(상태바/네비게이션 바)를 조종하는 컨트롤러"

WindowInsetsControllerCompat를 통해 다음과 같은 설정이 가능하다.

- 상태바 숨기기
- 네비게이션 바 숨기기
- 상태바 아이콘 색 변경
- 풀스크린 모드 만들기



---

# 3. WindowInsetsControllerCompat의 뜻

이름을 나눠보면 이해가 쉽다.

## 🔹 Window + Insets + Controller + Compat

현재 앱 화면(Window) + 시스템 바 때문에 생기는 화면 여백 + Controller + 구버전 안드로이드 호환 지원

> 즉, "시스템 UI 여백(상태바/네비게이션 바)을 제어하는 호환 컨트롤러"


---

# 4. 컨트롤러 가져오기

보통 이렇게 만든다.

```kotlin
val controller =
    WindowCompat.getInsetsController(
        window,
        window.decorView
    )
```

---

# 5. WindowCompat.getInsetsController(...) 이해하기

## 전체 코드

```kotlin
WindowCompat.getInsetsController(
    window,
    window.decorView
)
```

- window : 현재 Activity의 화면 전체 창(Window)
- window.decorView : 현재 화면의 최상위 View(루트 뷰) -> 시스템 UI와 연결된 기준 View 역할

---

## 🔹 getInsetsController(...)

현재 Window에 연결된 시스템 UI 컨트롤러를 가져오는 함수.

즉:

```kotlin
val controller = ...
```

를 하면:

> 상태바/네비게이션 바를 조작할 수 있는 객체를 얻는 것

이다.

---

# 6. 상태바 아이콘 색 바꾸기

## 검정 아이콘으로 변경

```kotlin
controller.isAppearanceLightStatusBars = true
```

의 의미:

- `true`
    - 밝은 상태바 배경
    - 아이콘은 검정색

---

## 흰 아이콘으로 변경

```kotlin
controller.isAppearanceLightStatusBars = false
```

의 의미:

- `false`
    - 어두운 상태바 배경
    - 아이콘은 흰색

---

# 7. 상태바 숨기기

```kotlin
controller.hide(
    WindowInsetsCompat.Type.statusBars()
)
```

결과:

- 상태바가 사라짐
- 풀스크린 화면 가능

주로:

- 영상 앱
- 게임
- 사진 뷰어

에서 사용한다.

---

# 8. 네비게이션 바 숨기기

```kotlin
controller.hide(
    WindowInsetsCompat.Type.navigationBars()
)
```

결과:

- 아래 네비게이션 바 숨김

---

# 9. 상태바 + 네비게이션 바 둘 다 숨기기

```kotlin
controller.hide(
    WindowInsetsCompat.Type.systemBars()
)
```

의미:

```text
status bar + navigation bar
```

둘 다 숨긴다는 뜻이다.

---

# 10. 다시 보이게 하기

```kotlin
controller.show(
    WindowInsetsCompat.Type.systemBars()
)
```

숨겼던 시스템 바를 다시 표시한다.

---

# 11. 전체 예제

```kotlin
val controller =
    WindowCompat.getInsetsController(
        window,
        window.decorView
    )

// 상태바 아이콘 검정색
controller.isAppearanceLightStatusBars = true

// 상태바 숨기기
controller.hide(
    WindowInsetsCompat.Type.statusBars()
)
```

---

# 12. 실무에서 자주 같이 쓰는 것

보통 Edge-To-Edge UI와 함께 많이 사용한다.

예시:

```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)
```

이 코드는:

> 앱 화면이 상태바 영역까지 확장되도록 설정

하는 코드다.

요즘 Android 앱들은 대부분 이 방식을 사용한다.
