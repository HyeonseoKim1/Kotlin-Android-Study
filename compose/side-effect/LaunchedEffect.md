# LaunchedEffect

## LaunchedEffect란?

`LaunchedEffect`는 Compose에서 특정 시점에 코루틴(Coroutine)을 실행할 때 사용하는 Side Effect API이다.

주로 다음과 같을 때 사용한다.

- 화면 진입 시 작업 실행
- API 호출
- 딜레이 처리
- Snackbar 표시
- 상태 변화 감지

<br>

## 왜 필요할까?

Compose 함수는 상태(State)가 변경될 때마다 다시 실행(Recomposition)된다.

따라서 Composable 내부에서 바로 코드를 실행하면 의도하지 않게 여러 번 실행될 수 있다.

```kotlin
@Composable
fun MyScreen() {

    println("실행됨")

}
```

상태가 바뀔 때마다 계속 실행되는 문제를 해결하기 위해 `LaunchedEffect`를 사용한다.

<br>

## 기본 구조

```kotlin
LaunchedEffect(key1 = Unit) {

}
```

<br>

## 가장 기본적인 사용

```kotlin
@Composable
fun MyScreen() {

    LaunchedEffect(Unit) {
        println("한 번만 실행")
    }
}
```

<br>

### 특징

```text
LaunchedEffect(Unit)
→ 화면 진입 시 한 번 실행
```

<br>

## delay 사용 가능

`LaunchedEffect` 내부는 Coroutine Scope이기 때문에 `delay()` 사용 가능하다.

```kotlin
@Composable
fun MyScreen() {

    LaunchedEffect(Unit) {

        delay(3000)

        println("3초 후 실행")
    }
}
```

<br>

## 상태(State) 변화 감지

특정 상태가 변경될 때마다 다시 실행할 수 있다.

```kotlin
@Composable
fun CounterScreen() {

    var count by remember {
        mutableStateOf(0)
    }

    LaunchedEffect(count) {

        println("count 변경됨: $count")

    }
}
```

<br>

### 동작 방식

```text
count 변경
→ LaunchedEffect 재실행
```

<br>
<br>

## key란?

`LaunchedEffect`는 key 값이 변경될 때 다시 실행된다.

```kotlin
LaunchedEffect(key1 = count) {
}
```

<br>

### key가 Unit이면?

```kotlin
LaunchedEffect(Unit)
-> 처음 한 번만 실행
```

<br>

### key가 상태(State)면?

```kotlin
LaunchedEffect(count)
-> count 변경될 때마다 실행
```

<br>


## API 호출 예시

```kotlin
@Composable
fun UserScreen() {

    LaunchedEffect(Unit) {

        loadUsers()

    }

}
```


### 왜 많이 사용할까?

Compose에서는 화면이 다시 그려질 수 있기 때문에 API 호출을 일반 코드처럼 작성하면 여러 번 호출될 위험이 있다.

`LaunchedEffect`를 사용하면 원하는 시점에만 실행 가능하다.

<br>

## Snackbar 예시

```kotlin
@Composable
fun MyScreen() {

    val snackbarHostState = remember {
        SnackbarHostState()
    }

    LaunchedEffect(Unit) {

        snackbarHostState.showSnackbar(
            message = "저장 완료"
        )

    }

}
```

<br>

## 애니메이션 시작 예시

```kotlin
@Composable
fun MyScreen() {

    var visible by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {

        visible = true

    }

}
```

<br>


## CoroutineScope와 관계

`LaunchedEffect` 내부는 Coroutine Scope에서 실행된다.

따라서 delay(), launch(), async() 같은 Coroutine 기능 사용 가능하다.


<br>

## 주의할 점

### Composable 내부에서 직접 실행 금지

```kotlin
@Composable
fun MyScreen() {
    loadData()
}
```

이렇게 작성하면 Recomposition 때마다 실행될 수 있다.

<br>

### 올바른 방법

```kotlin
@Composable
fun MyScreen() {

    LaunchedEffect(Unit) {

        loadData()

    }

}
```

<br>

## rememberCoroutineScope와 차이점

| LaunchedEffect | rememberCoroutineScope |
|---|---|
| Composition 진입 시 자동 실행 | 이벤트 시 직접 실행 |
| key 기반 재실행 가능 | 버튼 클릭 등에 사용 |
| 화면 시작 작업에 적합 | 사용자 액션 처리에 적합 |

<br>

### LaunchedEffect 사용 예시

```text
화면 진입
→ API 호출
→ 초기 데이터 로딩
```

<br>

### rememberCoroutineScope 사용 예시

```text
버튼 클릭
→ Snackbar 표시
→ 애니메이션 실행
```


<br>

## LaunchedEffect 특징

- Coroutine 기반
- 화면 진입 시 실행 가능
- 상태(State) 변화 감지 가능
- Recomposition 안전
- API 호출에 자주 사용
- Side Effect 처리용 API

<br>

## 자주 같이 사용하는 것

```text
LaunchedEffect
→ rememberCoroutineScope
→ DisposableEffect
→ SideEffect
→ rememberUpdatedState
```

<br>

## 참고

- Compose에서 가장 많이 사용하는 Side Effect API 중 하나
- API 호출 및 초기 로딩에서 자주 사용
- Coroutine과 함께 사용하는 경우가 많음
