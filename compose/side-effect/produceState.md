# produceState

## 목차

1. produceState란?
2. 왜 사용하는가?
3. 기본 문법
4. LaunchedEffect와 차이점
5. produceState 없이 구현하기
6. produceState 사용하기
7. 실전 예제 - API 데이터 불러오기
8. 실전 예제 - Flow 수집하기
9. produceState 사용 시 주의사항
10. 언제 사용하면 좋을까?
11. 정리

<br>

## 1. produceState란?

`produceState`는 비동기 작업의 결과를 Compose State로 변환해주는 Side Effect API이다.

Compose에서는 State가 변경되면 자동으로 화면이 다시 그려진다.

하지만 서버 통신, 데이터베이스 조회, Flow 수집 같은 비동기 작업은 바로 State로 사용할 수 없다.

이때 `produceState`를 사용하면 비동기 데이터를 Compose State로 변환하여 UI에서 바로 사용할 수 있다.

```kotlin
val userName by produceState(
    initialValue = ""
) {
    value = repository.getUserName()
}
```

위 코드에서 repository.getUserName() 의 결과가 State로 저장되고,

userName을 일반 State처럼 사용할 수 있다.

<br>

## 2. 왜 사용하는가?

Compose는 State 기반 UI이다.

Text(text = userName)처럼 화면은 State를 바라보고 있어야 한다.

하지만 실제 데이터는 대부분 비동기 작업으로 가져온다.

예를 들어 val user = repository.getUser()는

* API 호출
* Room 조회
* DataStore 조회

등의 작업일 수 있다.

이 데이터를 화면에서 사용하려면

1. State 생성
2. 비동기 작업 실행
3. 결과 저장
4. Recomposition 발생

과정이 필요하다.

보통은 remember + mutableStateOf 와 LaunchedEffect를 함께 사용해야 한다.

`produceState`는 이 과정을 하나의 API로 묶어준다.

<br>

## 3. 기본 문법

```kotlin
val text by produceState(
    initialValue = ""
) {
    value = "Hello Compose"
}
```

구조는 다음과 같다.

```kotlin
produceState(
    initialValue = 초기값
) {
    value = 변경할 값
}
```

### initialValue

화면에 처음 표시될 값이다.

```kotlin
initialValue = "Loading..."
```

<br>

### value

State 값이다.

```kotlin
value = "Complete"
```

위와 같이 변경하면 Recomposition이 발생한다.

<br>

## 4. LaunchedEffect와 차이점

### LaunchedEffect 방식

```kotlin
@Composable
fun SampleScreen() {

    var text by remember {
        mutableStateOf("")
    }

    LaunchedEffect(Unit) {
        text = "Hello Compose"
    }

    Text(text = text)
}
```

State를 직접 만들고 관리해야 한다.

<br>

### produceState 방식

```kotlin
@Composable
fun SampleScreen() {

    val text by produceState(
        initialValue = ""
    ) {
        value = "Hello Compose"
    }

    Text(text = text)
}
```

State 생성과 비동기 처리를 동시에 수행한다.

<br>

### 비교

| 항목       | LaunchedEffect | produceState |
| -------- | -------------- | ------------ |
| State 생성 | 직접 해야 함        | 자동 생성        |
| 비동기 처리   | 가능             | 가능           |
| 데이터 로딩   | 가능             | 특화           |
| Flow 수집  | 가능             | 매우 적합        |
| 코드 길이    | 김              | 짧음           |

<br>

## 5. produceState 없이 구현하기

사용자 정보를 불러온다고 가정해보자.

```kotlin
@Composable
fun UserScreen() {

    var userName by remember {
        mutableStateOf("Loading...")
    }

    LaunchedEffect(Unit) {
        delay(2000)
        userName = "Android"
    }

    Text(text = userName)
}
```

실행 결과

```text
Loading...
↓
Android
```

문제는 mutableStateOf(), LaunchedEffect() 두 개를 사용해야 한다.

<br>

## 6. produceState 사용하기

동일한 기능을 `produceState`로 구현하면 더 간결해진다.

```kotlin
@Composable
fun UserScreen() {

    val userName by produceState(
        initialValue = "Loading..."
    ) {

        delay(2000)

        value = "Android"
    }

    Text(text = userName)
}
```

<br>

## 7. 실전 예제 - API 데이터 불러오기

실무에서는 네트워크 요청을 자주 수행한다.

```kotlin
class UserRepository {

    suspend fun getUserName(): String {

        delay(2000)

        return "Android"
    }
}
```

<br>

화면에서 사용

```kotlin
@Composable
fun UserScreen(
    repository: UserRepository
) {

    val userName by produceState(
        initialValue = "Loading..."
    ) {

        value = repository.getUserName()
    }

    Text(text = userName)
}
```

실행 순서

1. Loading... 표시
2. API 호출
3. 데이터 수신
4. value 변경
5. Recomposition 발생
6. 결과 표시

<br>

## 8. 실전 예제 - Flow 수집하기

`produceState`가 가장 많이 사용되는 경우 중 하나이다.

```kotlin
val countFlow = flow {

    var count = 0

    while (true) {

        emit(count++)

        delay(1000)
    }
}
```

<br>

화면에서 수집

```kotlin
@Composable
fun CounterScreen() {

    val count by produceState(
        initialValue = 0
    ) {

        countFlow.collect {
            value = it
        }
    }

    Text(
        text = count.toString()
    )
}
```

실행 결과

```text
0
1
2
3
4
...
```

Flow 값이 변경될 때마다 value = it 이 실행되고 화면도 자동으로 갱신된다.

<br>

## 9. produceState 사용 시 주의사항

### 1) initialValue는 반드시 필요하다

```kotlin
produceState(
    initialValue = ""
)
```

초기 화면에 표시할 값이 필요하다.

보통 "Loading..." 또는 null을 사용한다.

<br>

### 2) 무거운 비즈니스 로직은 ViewModel에서 처리

좋지 않은 예

```kotlin
produceState(
    initialValue = emptyList()
) {

    value = heavyCalculation()
}
```

Compose는 UI 계층이다.

복잡한 로직은 ViewModel에서 처리하고 결과만 받아오는 것이 좋다.

<br>

### 3) key가 변경되면 다시 실행된다

```kotlin
val user by produceState(
    initialValue = null,
    key1 = userId
) {

    value = repository.getUser(userId)
}
```

`userId`가 변경되면 repository.getUser(userId)가 다시 실행된다.

<br>

### 4) StateFlow라면 collectAsState가 더 적합하다

```kotlin
viewModel.userState.collectAsState()
```

이미 StateFlow를 사용 중이라면 produceState 보다 collectAsState() 가 더 자연스럽다.

<br>

## 10. 언제 사용하면 좋을까?

사용하기 좋은 상황

### API 호출

```kotlin
repository.getUser()
```

<br>

### Room 조회

```kotlin
dao.getUser()
```

<br>

### DataStore 조회

```kotlin
dataStore.data.first()
```

<br>

### Flow 수집

```kotlin
flow.collect()
```

<br>

### 외부 SDK 데이터 수신

```kotlin
locationProvider.getLocation()
```

<br>

반대로 이미 StateFlow를 사용하고 있다면

collectAsState() 가 더 적절한 경우가 많다.

<br>

## 11. 정리

`produceState`는 비동기 데이터를 Compose State로 변환하기 위한 Side Effect API이다.

기존에는

```kotlin
remember
+
mutableStateOf
+
LaunchedEffect
```

를 함께 사용해야 했지만 produceState 를 사용하면 State 생성과 비동기 처리를 한 번에 수행할 수 있다.

특히 다음과 같은 작업에 매우 유용하다.

* API 호출
* Room 조회
* DataStore 조회
* Flow 수집

실무에서는 ViewModel에서 StateFlow를 관리하는 경우가 많지만, Compose 내부에서 간단한 비동기 데이터를 State로 변환해야 할 때 `produceState`를 사용하면 코드를 더 깔끔하게 작성할 수 있다.
