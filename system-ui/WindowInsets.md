# WindowInsets

## WindowInsets란?

WindowInsets는 상태바(Status Bar), 네비게이션 바(Navigation Bar), 키보드(IME) 등으로 인해 생기는 화면 여백 정보를 의미한다.

안드로이드는 시스템 UI 영역 크기를
WindowInsets를 통해 앱에게 전달한다.

<br>

## 왜 필요한가?

앱 화면이 시스템 바 영역까지 확장되면 **UI가 상태바와 겹칠 수 있다**.

문제 : 
- 텍스트가 상태바 뒤로 올라감
- 버튼이 네비게이션 바에 가려짐
- 키보드에 입력창이 가려짐

이를 해결하기 위해 Insets 정보를 사용한다.

<br>

## Insets 의미

Insets는 "안쪽으로 밀어야 하는 영역 크기" 라고 할 수 있다.

| 영역 | 의미 |
|---|---|
| top inset | 상태바 높이 |
| bottom inset | 네비게이션 바 높이 |
| ime inset | 키보드 높이 |

<br>

### 시스템 바와 관계

안드로이드 시스템 UI:

- 상태바
- 네비게이션 바
- 키보드

등은 앱 화면 위에 겹쳐질 수 있다.

WindowInsets는 **얼마나 겹치는지** 에 대한 정보를 제공한다.

<br>

### Compose에서 자주 사용하는 이유

Compose에서는 Edge-To-Edge UI를 많이 사용한다.

따라서 :

```kotlin
Modifier.systemBarsPadding()
```

같은 처리가 중요하다.

이 Modifier 내부에서도 WindowInsets 정보를 사용한다.

<br>

### systemBarsPadding()

```kotlin
Modifier.systemBarsPadding()
```

상태바 + 네비게이션 바 크기만큼
자동 padding 추가

<br>

### statusBarsPadding()

```kotlin
Modifier.statusBarsPadding()
```

상태바 높이만큼 padding 추가

<br>

### navigationBarsPadding()

```kotlin
Modifier.navigationBarsPadding()
```

네비게이션 바 높이만큼 padding 추가

<br>

### imePadding()

```kotlin
Modifier.imePadding()
```

키보드 높이만큼 padding 추가

주로 TextField 화면에서 사용한다.


<br>


### 예제

```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .systemBarsPadding()
)
```

시스템 바 영역과 겹치지 않도록 처리

<br>

### Edge-To-Edge와의 관계

```kotlin
WindowCompat.setDecorFitsSystemWindows(
    window,
    false
)
```

사용 시 앱이 직접 Insets 처리를 해야 한다.

그래서 WindowInsets가 매우 중요해진다.

<br>

### 기존 View 시스템과 차이

예전 Android View 시스템에서는 **fitsSystemWindows** 속성을 많이 사용했다.

Compose에서는 **WindowInsets + Modifier padding** 방식을 더 많이 사용한다.

<br>

## WindowInsets 핵심 정리

| 기능 | 설명 |
|---|---|
| 시스템 UI 여백 정보 | 제공 |
| 상태바 높이 확인 | 가능 |
| 네비게이션 바 높이 확인 | 가능 |
| 키보드 높이 확인 | 가능 |
| Edge-To-Edge 필수 개념 | O |

<br>


## 자주 사용하는 Modifier

### 상태바 처리

```kotlin
Modifier.statusBarsPadding()
```

### 네비게이션 바 처리

```kotlin
Modifier.navigationBarsPadding()
```

### 시스템 바 전체 처리

```kotlin
Modifier.systemBarsPadding()
```

### 키보드 처리

```kotlin
Modifier.imePadding()
```
