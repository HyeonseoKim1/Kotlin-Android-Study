# rememberUpdatedState

## 목차

1. rememberUpdatedState란?
2. 왜 필요한가?
3. 사용하지 않았을 때 문제점
4. rememberUpdatedState 사용하기
5. LaunchedEffect와 함께 사용하는 이유
6. 실전 예제 - 최신 콜백 실행
7. 실전 예제 - 최신 상태 참조
8. remember와의 차이
9. 사용 시 주의사항
10. 언제 사용하면 좋을까?
11. 정리

<br>

## 1. rememberUpdatedState란?

`rememberUpdatedState`는 **Composable이 재구성(Recomposition)되더라도 항상 최신 값을 참조할 수 있도록 만들어주는 State**이다.

주로 `LaunchedEffect`, `DisposableEffect`, `SideEffect`처럼 **한 번 시작하면 계속 실행되는 Effect 내부에서 최신 값을 사용하기 위해** 사용한다.

```kotlin
val currentValue = rememberUpdatedState(value)
```

처음 보면 `remember`와 비슷해 보이지만 목적은 완전히 다르다.

- remember : 객체를 재사용하기 위해 사용
- rememberUpdatedState : 최신 값을 항상 참조하기 위해 사용

<br>

## 2. 왜 필요한가?

Compose에서는 `LaunchedEffect`가 시작될 때의 값을 캡처한다.

예를 들어 아래처럼 작성했다고 가정해보자.

```kotlin
LaunchedEffect(Unit) {
    delay(3000)
    onTimeout()
}
```

3초를 기다리는 동안 화면이 재구성되어 `onTimeout`이 변경되더라도,

`LaunchedEffect`는 **처음 전달받은 onTimeout만 기억하고 있다.**

즉, 최신 콜백이 아니라 오래된 콜백을 실행하게 된다.

이 문제를 해결하는 것이 `rememberUpdatedState`이다.

<br>

## 3. 사용하지 않았을 때 문제점

버튼을 눌러 콜백을 변경한다고 가정해보자.

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

처음에는 A라는 콜백이 전달되었다.

5초를 기다리는 동안 화면이 재구성되어 B라는 콜백으로 변경되었다.

하지만 실행되는 것은 여전히 A이다.

왜냐하면 `LaunchedEffect`는 처음 실행될 때의 값을 캡처했기 때문이다.

<br>

## 4. rememberUpdatedState 사용하기

최신 콜백을 저장한다.

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

이제 5초 동안 콜백이 변경되더라도

항상 가장 최신의 `onFinish`가 실행된다.

### 동작 과정

1. LaunchedEffect는 한 번만 실행된다.
2. 화면이 재구성된다.
3. onFinish가 새로운 값으로 변경된다.
4. rememberUpdatedState가 최신 값을 저장한다.
5. delay가 끝난 뒤 최신 값을 실행한다.

-> Effect는 다시 시작하지 않으면서도 최신 값을 사용할 수 있다.

<br>

## 5. LaunchedEffect와 함께 사용하는 이유

`LaunchedEffect`의 Key를 변경하면 Effect가 다시 시작된다.

예를 들어

```kotlin
LaunchedEffect(onFinish) {
    delay(5000)
    onFinish()
}
```

콜백이 바뀔 때마다 delay가 다시 시작되고, 코루틴도 새로 실행된다.

원하는 것이 최신 콜백만 사용하는 것이라면 이 방법은 적절하지 않다.



```kotlin
val currentOnFinish by rememberUpdatedState(onFinish)

LaunchedEffect(Unit) {
    delay(5000)
    currentOnFinish()
}
```

반대로 이 경우, 

- 코루틴은 유지되고
- delay도 유지되고
- 최신 콜백만 변경된다.

실무에서는 이 방법을 더 많이 사용한다.

<br>

## 6. 실전 예제 - 최신 콜백 실행

Snackbar를 자동으로 닫는 상황을 생각해보자.

```kotlin
@Composable
fun AutoDismissSnackbar(
    onDismiss: () -> Unit
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(Unit) {
        delay(3000)
        currentOnDismiss()
    }
}
```

만약 사용자가 화면 상태를 변경하면서 `onDismiss`가 바뀌더라도

항상 최신 콜백이 호출된다.

<br>

## 7. 실전 예제 - 최신 상태 참조

카운트를 출력하는 예제이다.

```kotlin
@Composable
fun CounterScreen() {

    var count by remember {
        mutableStateOf(0)
    }

    val currentCount by rememberUpdatedState(count)

    Button(
        onClick = {
            count++
        }
    ) {
        Text("증가")
    }

    LaunchedEffect(Unit) {

        while (true) {
            delay(2000)
            println(currentCount)
        }
    }
}
```

버튼을 누를 때마다 count는 변경된다.

`LaunchedEffect`는 한 번만 실행되지만 출력되는 값은 항상 최신 count이다.

출력 결과

```
0
1
2
3
4
```

만약 `rememberUpdatedState`를 사용하지 않았다면 처음 값인 0만 계속 출력된다.

<br>

## 8. remember와의 차이

| remember | rememberUpdatedState |
|-----------|----------------------|
| 객체를 기억한다 | 최신 값을 참조한다 |
| 재구성 시 객체를 재사용한다 | 재구성 시 값만 업데이트한다 |
| 객체 생성 비용을 줄인다 | 오래 실행되는 Effect에서 최신 값을 사용한다 |
| 객체 자체를 저장 | State 내부 값만 변경 |

remember는 객체를 저장하는 것이 목적이다.

rememberUpdatedState는 최신 값을 가져오는 것이 목적이다.

<br>

## 9. 사용 시 주의사항

### Effect가 다시 시작되는 것을 막기 위한 용도이다.

무조건 사용하는 것이 아니다.

Effect를 다시 시작해야 하는 상황이라면

Key를 변경하는 것이 맞다.

예를 들어

```kotlin
LaunchedEffect(userId) {
    loadUser(userId)
}
```

사용자가 변경되면

새로운 데이터를 다시 불러와야 한다.

이 경우에는 `rememberUpdatedState`를 사용하면 안 된다.

<br>

### 최신 값만 필요한 경우에 사용한다.

대표적인 예

- 이벤트 콜백
- Listener
- Timer
- Delay
- Snackbar
- Animation
- Coroutine

<br>

## 10. 언제 사용하면 좋을까?

다음과 같은 상황에서 자주 사용된다.

- delay 이후 최신 콜백 실행
- Timer
- Splash 화면
- Snackbar 자동 닫기
- Animation 종료 콜백
- 오래 실행되는 Coroutine
- Listener 내부에서 최신 상태 참조

공통점은 **Effect는 다시 시작하고 싶지 않지만, 사용하는 값은 최신이어야 하는 경우**이다.

<br>

## 11. 정리

- rememberUpdatedState는 최신 값을 참조하기 위한 State이다.
- LaunchedEffect는 처음 실행될 때의 값을 캡처한다.
- 최신 콜백이 필요하면 rememberUpdatedState를 사용한다.
- Effect를 다시 시작하지 않고 최신 값을 사용할 수 있다.
- 주로 LaunchedEffect, DisposableEffect, Timer, Listener에서 사용된다.
- 객체를 저장하는 것은 remember, 최신 값을 참조하는 것은 rememberUpdatedState이다.
