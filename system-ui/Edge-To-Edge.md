# Edge-To-Edge

## Edge-To-Edge란?

앱 화면을 상태바(Status Bar)와
네비게이션 바(Navigation Bar) 영역까지 확장해서
전체 화면을 사용하는 UI 방식이다.

최근 Android 앱에서는 기본처럼 사용된다.

(유튜브, 인스타그램, 카카오톡, 갤러리 앱 등 대부분의 앱이 Edge-To-Edge UI를 사용한다.)

<br>

## 왜 사용하는가?

기존 Android UI는:

```text
시스템 바 영역 제외
→ 앱 화면 표시
```

방식이었다.

그래서 다음과 같은 문제가 있었다.

- 위/아래 여백이 생김
- 화면 활용도가 줄어듦
- 몰입감 감소

Edge-To-Edge는 **시스템 바 영역까지 화면 확장**을 통해 더 넓은 화면을 사용한다.

<br>

### 핵심 코드

```kotlin
WindowCompat.setDecorFitsSystemWindows(
    window,
    false
)
```

<br>

### setDecorFitsSystemWindows 의미

| 코드 | 의미 |
|---|---|
| true | 시스템 바 영역 제외 |
| false | 시스템 바 영역까지 확장 |

false로 설정하면 앱이 직접 Insets 처리를 해야 한다.

<br>

## 왜 padding 문제가 생기는가?

Edge-To-Edge 활성화 후 시스템 바 영역까지 앱이 직접 그리게 되면서

text 상태바 위로 UI가 올라가는 현상이 생길 수 있다.

따라서 다음과 같은 처리가 필요하다.

```kotlin
Modifier.systemBarsPadding()
```

<br>

### Compose 예제

```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .systemBarsPadding()
)
```

<br>

## WindowInsets와의 관계

Edge-To-Edge에서는:

```text
시스템 바 크기만큼 padding 처리
```

가 중요하다.

이 정보를 제공하는 것이 **WindowInsets**이다.

<br>

## Edge-To-Edge 핵심 정리

| 기능 | 설명 |
|---|---|
| 화면 확장 | 상태바 영역까지 사용 |
| 몰입감 증가 | 가능 |
| 화면 활용 증가 | 가능 |
| Insets 처리 필요 | O |
| WindowInsets와 연결 | 매우 중요 |
