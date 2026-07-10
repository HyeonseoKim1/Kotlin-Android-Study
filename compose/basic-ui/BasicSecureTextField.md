# BasicSecureTextField

## 목차

1. BasicSecureTextField란?
2. 왜 BasicTextField 대신 사용할까?
3. BasicSecureTextField의 특징
4. BasicSecureTextField 기본 사용법
5. 비밀번호 표시 여부 구현하기
6. TextObfuscationMode 알아보기
7. Material TextField와 차이점
8. 언제 사용하면 좋을까?
9. 사용 시 주의사항
10. 실전 예제
11. 정리

<br>

## 1. BasicSecureTextField란?

`BasicSecureTextField`는 Jetpack Compose Foundation에서 제공하는 비밀번호 입력 전용 TextField이다.

기존에는 비밀번호 입력을 만들기 위해 `BasicTextField`에 `PasswordVisualTransformation()`을 적용하는 방식이 일반적이었다.

하지만 이 방식은 화면에만 글자를 가려줄 뿐 내부적으로는 일반 TextField와 동일하게 동작한다.

Compose에서는 비밀번호 입력을 조금 더 안전하게 처리할 수 있도록 `BasicSecureTextField`를 제공한다.

```kotlin
BasicSecureTextField(
    state = rememberTextFieldState()
)
```

비밀번호 입력을 구현한다면 특별한 이유가 없는 이상 `BasicSecureTextField`를 사용하는 것이 권장된다.

<br>

## 2. 왜 BasicTextField 대신 사용할까?

예전에는 대부분 아래처럼 구현했다.

```kotlin
BasicTextField(
    value = password,
    onValueChange = {
        password = it
    },
    visualTransformation = PasswordVisualTransformation()
)
```

겉으로 보기에는 문제가 없어 보인다.

하지만 실제로는

- 일반 TextField처럼 동작
- 복사/붙여넣기 처리
- 보안 입력 처리
- 문자 표시 방식

등을 직접 신경 써야 한다.

반면 `BasicSecureTextField`는

- 비밀번호 입력에 맞는 동작 제공
- 문자 노출 최소화
- 최신 보안 입력 방식 지원
- Compose에서 권장하는 구현

등을 기본적으로 제공한다.

<br>

## 3. BasicSecureTextField의 특징

대표적인 특징은 다음과 같다.

### 비밀번호 입력 전용

일반 문자열 입력이 아니라 비밀번호 입력을 위한 컴포넌트이다.

### 문자 숨김 지원

입력된 문자를 자동으로 가려준다.

```text
password123

↓

•••••••••••
```

### TextFieldState 사용

`String` 대신 `TextFieldState`를 사용한다.

```kotlin
val state = rememberTextFieldState()
```

최근 Compose는 `value` 방식보다 `TextFieldState` 방식을 권장하고 있다.

### Material에 의존하지 않는다.

Foundation에 포함되어 있으므로 원하는 UI를 자유롭게 만들 수 있다.

<br>

## 4. BasicSecureTextField 기본 사용법

가장 기본 형태이다.

```kotlin
@Composable
fun PasswordField() {

    val passwordState = rememberTextFieldState()

    BasicSecureTextField(
        state = passwordState
    )
}
```

입력된 값은

```kotlin
passwordState.text
```

로 확인할 수 있다.

예를 들어

```text
abc123
```

을 입력하면

화면에는

```text
••••••
```

처럼 보이지만

```kotlin
passwordState.text
```

에는

```text
abc123
```

이 저장된다.

<br>

## 5. 비밀번호 표시 여부 구현하기

가장 많이 사용하는 기능이다.

```kotlin
@Composable
fun PasswordField() {

    val state = rememberTextFieldState()

    var visible by remember {
        mutableStateOf(false)
    }

    BasicSecureTextField(
        state = state,
        textObfuscationMode =
            if (visible)
                TextObfuscationMode.Visible
            else
                TextObfuscationMode.Hidden
    )
}
```

보통 Eye 아이콘을 눌렀을 때

```text
••••••

↓

password
```

처럼 변경된다.

<br>

## 6. TextObfuscationMode 알아보기

비밀번호를 어떻게 표시할지 결정하는 옵션이다.

대표적으로 다음과 같은 값이 있다.

### Hidden

항상 숨긴다.

```text
password

↓

••••••••
```

가장 많이 사용한다.

```kotlin
textObfuscationMode = TextObfuscationMode.Hidden
```

### Visible

항상 보여준다.

```text
password
```

```kotlin
textObfuscationMode = TextObfuscationMode.Visible
```

설정 화면에서 "비밀번호 보기" 기능을 만들 때 사용한다.

### RevealLastTyped

마지막 입력한 글자만 잠시 보여준다.

예를 들어

```text
a 입력

↓

a

잠시 후

↓

•
```

Compose 기본 동작도 이 방식을 사용한다.

```kotlin
textObfuscationMode =
    TextObfuscationMode.RevealLastTyped
```

사용자가 오타를 확인하기 쉽다는 장점이 있다.

<br>

## 7. Material TextField와 차이점

|항목|BasicSecureTextField|TextField|
|---|---|---|
|Material 디자인|X|O|
|비밀번호 전용|O|X|
|커스텀 자유도|높음|낮음|
|Decoration 직접 구현|O|불필요|
|Compose 권장 보안 입력|O|일반 입력|

로그인 화면처럼 디자인을 직접 만들고 싶다면

`BasicSecureTextField`

Material 디자인을 그대로 사용할 거라면

`TextField`

를 사용하는 것이 일반적이다.

<br>

## 8. 언제 사용하면 좋을까?

다음과 같은 경우 사용하면 좋다.

- 로그인
- 회원가입
- 비밀번호 변경
- PIN 번호 입력
- 인증번호 입력(UI 커스텀 시)
- 금융 서비스
- 보안이 중요한 앱

비밀번호 입력이라면 거의 항상 사용한다고 보면 된다.

<br>

## 9. 사용 시 주의사항

### String 상태를 같이 관리하지 않는다.

좋지 않은 예이다.

```kotlin
var password by remember {
    mutableStateOf("")
}

val state = rememberTextFieldState(password)
```

상태가 두 개가 되어 동기화 문제가 발생할 수 있다.

다음처럼 `TextFieldState` 하나만 사용하는 것이 좋다.

```kotlin
val state = rememberTextFieldState()
```

<br>

### BasicSecureTextField는 Decoration을 직접 만들어야 한다.

Material TextField처럼

- Label
- Placeholder
- Border
- Leading Icon
- Trailing Icon

등을 자동으로 제공하지 않는다.

필요하면 직접 구현해야 한다.

<br>

### Foundation 버전을 확인한다.

`BasicSecureTextField`는 비교적 최근 Compose Foundation에서 추가된 API이다.

프로젝트에서 사용하는 Compose 버전이 오래되었다면 사용할 수 없을 수 있다.

<br>

## 10. 실전 예제

```kotlin
@Composable
fun LoginPasswordField() {

    val passwordState = rememberTextFieldState()

    var visible by remember {
        mutableStateOf(false)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {

        BasicSecureTextField(
            state = passwordState,
            modifier = Modifier.weight(1f),
            textObfuscationMode =
                if (visible)
                    TextObfuscationMode.Visible
                else
                    TextObfuscationMode.Hidden
        )

        IconButton(
            onClick = {
                visible = !visible
            }
        ) {

            Icon(
                imageVector =
                    if (visible)
                        Icons.Default.VisibilityOff
                    else
                        Icons.Default.Visibility,
                contentDescription = null
            )
        }
    }
}
```

동작 과정은 다음과 같다.

1. 사용자가 비밀번호를 입력한다.
2. 화면에는 기본적으로 숨겨진 상태로 표시된다.
3. 눈 아이콘을 누르면 비밀번호가 보인다.
4. 다시 누르면 숨겨진다.

로그인 화면에서 가장 많이 사용하는 형태이다.

<br>

## 11. 정리

- `BasicSecureTextField`는 비밀번호 입력 전용 컴포넌트이다.
- `BasicTextField + PasswordVisualTransformation`보다 사용이 권장된다.
- `TextFieldState`를 사용하여 상태를 관리한다.
- `TextObfuscationMode`로 표시 방식을 제어할 수 있다.
- Material 디자인은 직접 구현해야 한다.
- 로그인, 회원가입, 비밀번호 변경 화면에서 가장 많이 사용된다.
- 보안 입력이 필요한 경우에는 `BasicSecureTextField`를 사용하는 것이 가장 적합하다.
