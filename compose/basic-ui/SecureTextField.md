# SecureTextField

## 목차

1. SecureTextField란?
2. 왜 사용하는가?
3. SecureTextField와 TextField 차이
4. 기본 사용법
5. TextObfuscationMode 이해하기
6. 비밀번호 표시/숨기기 구현
7. PasswordVisualTransformation과 차이
8. 언제 사용하면 좋을까?
9. 사용 시 주의사항
10. 정리

<br>

## 1. SecureTextField란?

`SecureTextField`는 Material3에서 제공하는 **비밀번호 입력 전용 TextField**이다.

기존에는 일반 `TextField`에 `PasswordVisualTransformation`을 적용하여 비밀번호 입력을 구현했다. Material3에서는 이러한 작업을 더 쉽게 구현할 수 있도록 `SecureTextField`를 제공한다.

비밀번호 입력에 필요한 기능을 기본적으로 제공하므로 일반 `TextField`보다 구현이 간단하다.

```kotlin
SecureTextField(
    state = passwordState
)
```

<br>

## 2. 왜 사용하는가?

비밀번호 입력은 일반 텍스트 입력과 요구사항이 다르다.

대표적으로 다음과 같은 기능이 필요하다.

- 입력 내용 숨기기
- 마지막 입력 문자만 잠시 표시하기
- 보안에 적합한 입력 처리
- 접근성 지원

기존에는 이러한 기능을 개발자가 직접 구현해야 했다.

`SecureTextField`는 비밀번호 입력에 필요한 기능을 기본적으로 제공하므로 구현이 훨씬 단순해진다.

즉, 일반 텍스트 입력이 아니라 **비밀번호 입력을 위한 전용 TextField**라고 생각하면 된다.

<br>

## 3. SecureTextField와 TextField 차이

|항목|TextField|SecureTextField|
|---|---|---|
|용도|일반 텍스트 입력|비밀번호 입력|
|입력 숨김|직접 구현|기본 제공|
|TextObfuscationMode|지원하지 않음|지원|
|PasswordVisualTransformation|직접 사용|필요 없음|
|권장 사용|일반 입력|비밀번호 입력|

일반적인 문자열을 입력받는다면 `TextField`를 사용하면 된다.

비밀번호를 입력받는 화면이라면 `SecureTextField`를 사용하는 것이 적절하다.

<br>

## 4. 기본 사용법

`SecureTextField`는 상태 기반(State-based) TextField와 함께 사용한다.

```kotlin
@Composable
fun PasswordScreen() {
    val passwordState = rememberTextFieldState()

    SecureTextField(
        state = passwordState
    )
}
```

### 코드 설명

```kotlin
val passwordState = rememberTextFieldState()
```

입력된 문자열을 저장하는 상태를 생성한다.

```kotlin
SecureTextField(
    state = passwordState
)
```

비밀번호 입력창을 생성한다.

사용자가 입력한 문자열은 `passwordState.text`에 저장된다.

<br>

## 5. TextObfuscationMode 이해하기

`SecureTextField`는 `TextObfuscationMode`를 이용해 입력 내용을 어떻게 표시할지 설정할 수 있다.

|모드|설명|
|---|---|
|Visible|입력 내용을 그대로 표시한다.|
|RevealLastTyped|마지막 입력 문자만 잠시 보여준다.|
|Hidden|모든 입력을 항상 숨긴다.|

### Visible

```kotlin
SecureTextField(
    state = passwordState,
    textObfuscationMode = TextObfuscationMode.Visible
)
```

화면

```
password123
```

입력한 내용이 그대로 보인다.

비밀번호 입력에서는 거의 사용하지 않는다.

<br>

### RevealLastTyped

```kotlin
SecureTextField(
    state = passwordState,
    textObfuscationMode = TextObfuscationMode.RevealLastTyped
)
```

화면

```
••••••••2
```

마지막으로 입력한 문자만 잠시 표시한 뒤 자동으로 숨긴다.

안드로이드에서 가장 많이 사용하는 비밀번호 표시 방식이다.

<br>

### Hidden

```kotlin
SecureTextField(
    state = passwordState,
    textObfuscationMode = TextObfuscationMode.Hidden
)
```

화면

```
•••••••••
```

입력하는 즉시 모든 문자를 숨긴다.

가장 보안성이 높은 방식이다.

<br>

## 6. 비밀번호 표시/숨기기 구현

로그인 화면에서는 아이콘을 눌러 비밀번호를 표시하거나 다시 숨기는 기능을 자주 사용한다.

```kotlin
@Composable
fun PasswordField() {

    val passwordState = rememberTextFieldState()

    var visible by remember {
        mutableStateOf(false)
    }

    SecureTextField(
        state = passwordState,
        textObfuscationMode =
            if (visible)
                TextObfuscationMode.Visible
            else
                TextObfuscationMode.Hidden,
        trailingIcon = {
            IconButton(
                onClick = {
                    visible = !visible
                }
            ) {
                Icon(
                    imageVector =
                        if (visible)
                            Icons.Default.Visibility
                        else
                            Icons.Default.VisibilityOff,
                    contentDescription = null
                )
            }
        }
    )
}
```

동작 결과

초기 상태

```
•••••••••
```

아이콘 클릭

```
password123
```

다시 클릭

```
•••••••••
```

이처럼 `TextObfuscationMode`를 변경하면 비밀번호 표시 여부를 쉽게 구현할 수 있다.

<br>

## 7. PasswordVisualTransformation과 차이

기존에는 일반 `TextField`에 `PasswordVisualTransformation`을 적용했다.

```kotlin
TextField(
    value = password,
    onValueChange = {
        password = it
    },
    visualTransformation = PasswordVisualTransformation()
)
```

Material3에서는 `SecureTextField`를 사용하는 것이 권장된다.

```kotlin
SecureTextField(
    state = passwordState
)
```

차이점은 다음과 같다.

|PasswordVisualTransformation|SecureTextField|
|---|---|
|일반 TextField 사용|비밀번호 전용 컴포넌트|
|직접 설정해야 함|기본 기능 제공|
|구현 코드가 많음|간단하게 구현 가능|
|기존 방식|Material3 권장 방식|

새로운 Material3 프로젝트라면 `SecureTextField`를 사용하는 것이 좋다.

<br>

## 8. 언제 사용하면 좋을까?

다음과 같은 화면에서 사용하기 적합하다.

- 로그인
- 회원가입
- 비밀번호 변경
- PIN 입력
- 보안이 필요한 문자열 입력

반대로 다음과 같은 입력은 일반 `TextField`를 사용하는 것이 적절하다.

- 이름
- 이메일
- 전화번호
- 주소

<br>

## 9. 사용 시 주의사항

### 1. Material3 Experimental API일 수 있다.

Material3 버전에 따라 Experimental API인 경우가 있다.

이 경우에는 다음 애노테이션을 추가해야 한다.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
```

<br>

### 2. 일반 문자열 입력에는 사용하지 않는다.

`SecureTextField`는 비밀번호 입력을 위한 컴포넌트이다.

일반 문자열 입력에는 `TextField`를 사용하는 것이 적절하다.

<br>

### 3. 상태 기반 TextField와 함께 사용한다.

`SecureTextField`는 다음과 같이 `rememberTextFieldState()`와 함께 사용하는 것을 권장한다.

```kotlin
val passwordState = rememberTextFieldState()
```

기존의

```kotlin
value
onValueChange
```

방식과는 사용 방법이 다르므로 차이를 이해하는 것이 중요하다.

<br>

## 10. 정리

- `SecureTextField`는 Material3에서 제공하는 비밀번호 입력 전용 TextField이다.
- 입력 내용을 안전하게 숨기는 기능을 기본 제공한다.
- `TextObfuscationMode`를 이용해 입력 표시 방식을 설정할 수 있다.
- 아이콘과 함께 사용하면 비밀번호 표시/숨기기를 쉽게 구현할 수 있다.
- 기존 `PasswordVisualTransformation`보다 간단하게 구현할 수 있다.
- Material3 프로젝트에서는 비밀번호 입력 시 `SecureTextField`를 사용하는 것이 권장된다.
