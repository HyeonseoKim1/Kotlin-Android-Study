# rememberUpdatedState

`rememberUpdatedState`는 Side Effect 내부에서 **항상 최신 값을 참조**하기 위해 사용하는 API다.

Compose에서는 `LaunchedEffect`, `DisposableEffect`와 같은 Side Effect가 실행된 이후에도 Composable이 여러 번 Recomposition될 수 있다. 이때 Effect 내부에서 사용하는 값이 이전 값으로 고정되는(Stale) 문제가 발생할 수 있는데, 이를 해결하기 위해 `rememberUpdatedState`를 사용한다.

<br>

# 목차

1. rememberUpdatedState란?
2. 왜 필요한가?
3. Stale Value 문제
4. rememberUpdatedState 사용법
5. LaunchedEffect에서 활용
6. DisposableEffect에서 활용
7. remember와의 차이
8. rememberUpdatedState를 사용하지 않아도 되는 경우
9. 실무에서 자주 사용하는 예제
10. 주의사항
11. 정리

<br>

## 1. rememberUpdatedState란?

`rememberUpdatedState`는 Recomposition이 발생해도 Effect를 다시 시작하지 않고, **최신 값을 참조할 수 있도록 하는 State**를 만들어준다.

```kotlin
val currentOnClick by rememberUpdatedState(onClick)
```

이후 Effect에서는 항상 `currentOnClick()`을 호출하면 된다.

<br>

## 2. 왜 필요한가?

Compose에서는 다음과 같은 코드가 자주 등장한다.

```kotlin
LaunchedEffect(Unit) {
    delay(3000)
    onTimeout()
}
```

3초 뒤에 콜백을 실행하는 코드다.

하지만 3초 동안 Composable이 여러 번 Recomposition되면서 `onTimeout`이 변경될 수도 있다.

그럼 Effect는 처음 전달받은 `onTimeout`만 기억하고 있기 때문에 최신 콜백이 아닌 이전 콜백을 실행한다.

이것이 **Stale Value(오래된 값 참조)** 문제다.

<br>

## 3. Stale Value 문제

예를 들어 화면이 다음과 같다고 가정한다.

```kotlin
@Composable
fun TimerScreen(
    onFinish: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(5000)
        onFinish()
    }
}
```

처음에는

```text
onFinish = A
```

였다가,

2초 뒤 부모 Composable에서

```text
onFinish = B
```

로 변경되었다.

하지만 이미 실행 중인 `LaunchedEffect`는

```text
A
```

를 기억하고 있기 때문에

5초 뒤에도

```text
A
```

를 호출한다.

원하는 동작은 최신 값인

```text
B
```

를 호출하는 것이다.

<br>

## 4. rememberUpdatedState 사용법

이를 해결하려면 다음처럼 작성한다.

```kotlin
@Composable
fun TimerScreen(
    onFinish: () -> Unit
) {
    val currentOnFinish by rememberUpdatedState(onFinish)

    LaunchedEffect(Unit) {
        delay(5000)
        currentOnFinish()
    }
}
```

이제 Effect는 다시 시작하지 않지만,

항상 최신 `onFinish`를 호출한다.

<br>

## 5. LaunchedEffect에서 활용

가장 흔한 사용 예다.

```kotlin
@Composable
fun SplashScreen(
    onNavigate: () -> Unit
) {
    val currentNavigate by rememberUpdatedState(onNavigate)

    LaunchedEffect(Unit) {
        delay(2000)
        currentNavigate()
    }
}
```

만약 화면 회전이나 상태 변경으로 부모에서 새로운 람다가 전달되어도

2초 뒤에는 항상 최신 람다가 실행된다.

<br>

## 6. DisposableEffect에서 활용

리스너 등록 시에도 자주 사용한다.

```kotlin
@Composable
fun LifecycleObserver(
    onStart: () -> Unit
) {
    val currentOnStart by rememberUpdatedState(onStart)

    DisposableEffect(Unit) {

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                currentOnStart()
            }
        }

        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}
```

Observer는 계속 살아있지만,

항상 최신 콜백을 호출하게 된다.

<br>

## 7. remember와의 차이

|remember|rememberUpdatedState|
|---|---|
|객체를 유지|최신 값을 유지|
|Recomposition 시 같은 객체 반환|Recomposition 시 값만 업데이트|
|객체 캐싱 목적|최신 값 참조 목적|

예를 들어

```kotlin
val coroutineScope = rememberCoroutineScope()
```

는 객체를 유지하기 위해 사용한다.

반면

```kotlin
val currentCallback by rememberUpdatedState(callback)
```

는 최신 callback을 참조하기 위해 사용한다.

목적이 완전히 다르다.

<br>

## 8. rememberUpdatedState를 사용하지 않아도 되는 경우

Effect가 값이 바뀔 때마다 다시 실행되어도 된다면 필요 없다.

예를 들어

```kotlin
LaunchedEffect(userId) {
    repository.load(userId)
}
```

여기서는

`userId`가 바뀌면 Effect를 다시 실행하는 것이 올바른 동작이다.

이 경우에는

```kotlin
rememberUpdatedState(userId)
```

를 사용할 필요가 없다.

<br>

## 9. 실무에서 자주 사용하는 예제

### Splash 화면

```kotlin
LaunchedEffect(Unit) {
    delay(2000)
    currentNavigate()
}
```

<br>

### Snackbar

```kotlin
LaunchedEffect(Unit) {
    snackbarHostState.showSnackbar(message)
    currentOnDismiss()
}
```

<br>

### Timeout 처리

```kotlin
LaunchedEffect(Unit) {
    delay(timeout)
    currentOnTimeout()
}
```

<br>

### Lifecycle Observer

```kotlin
DisposableEffect(Unit) {
    ...
}
```

리스너는 유지하면서 최신 콜백만 사용하고 싶을 때 자주 사용한다.

<br>

## 10. 주의사항

### Effect를 다시 실행해야 하는 상황에서는 사용하지 않는다.

잘못된 예

```kotlin
val currentUserId by rememberUpdatedState(userId)

LaunchedEffect(Unit) {
    repository.load(currentUserId)
}
```

이 경우에는 userId가 변경되어도 다시 조회하지 않는다.

올바른 코드는

```kotlin
LaunchedEffect(userId) {
    repository.load(userId)
}
```

이다.

<br>

### 콜백이나 이벤트 처리에 사용하는 것이 일반적이다.

주로 사용하는 대상은 다음과 같다.

- callback
- lambda
- event
- listener

데이터 변경 자체를 감지하기 위한 용도가 아니다.

<br>

## 11. 정리

- `rememberUpdatedState`는 Side Effect에서 최신 값을 참조하기 위한 API다.
- Effect를 다시 시작하지 않고 최신 값을 사용할 수 있다.
- Stale Value 문제를 해결할 수 있다.
- `LaunchedEffect`, `DisposableEffect`와 함께 자주 사용된다.
- 객체를 캐싱하는 `remember`와는 목적이 다르다.
- Effect를 다시 실행해야 하는 상황에서는 사용하지 않는 것이 맞다.
