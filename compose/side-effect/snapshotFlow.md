# snapshotFlow

### 목차

1. snapshotFlow란?
2. 왜 사용하는가?
3. snapshotFlow의 동작 방식
4. 기본 사용법
5. Scroll 상태 감지 예제
6. 검색어 변경 감지 예제
7. distinctUntilChanged()를 사용하는 이유
8. snapshotFlow 사용 시 주의사항
9. 언제 사용하면 좋을까?
10. 정리

<br>

## 1. snapshotFlow란?

`snapshotFlow`는 Compose의 State 값을 Kotlin Flow로 변환해주는 함수이다.

Compose의 State는 Compose 내부에서만 관찰되지만, Flow는 Coroutine에서 다양한 연산자를 사용할 수 있다.

snapshotFlow는 다음과 같은 특징이 있다.

- Compose State → Flow로 변환
- Flow API(map, filter, debounce 등) 활용 가능
- Coroutine에서 안전하게 처리 가능

대표적으로 사용하는 곳은 다음과 같다.

- 스크롤 위치 감지
- 검색어 변경 감시
- 화면 노출 이벤트
- Analytics 로그

<br>

## 2. 왜 사용하는가?

Compose에서는 State가 변경될 때마다 Recomposition이 발생한다.

하지만 "값이 변경될 때마다 Coroutine에서 무언가 실행하고 싶다."는 경우도 있다.

예를 들어

- 스크롤이 100px 이상 내려가면 로그 전송
- 검색어 입력이 끝나면 API 호출
- 특정 값이 변경되면 Repository 호출

이런 작업은 UI가 아니라 Coroutine이 담당하는 것이 좋다.

이때 사용하는 것이 snapshotFlow이다.

<br>

## 3. snapshotFlow의 동작 방식

예를 들어

```kotlin
val text by remember {
    mutableStateOf("")
}
```

State는 Compose가 관리한다.

이를

```kotlin
snapshotFlow {
    text
}
```

로 감싸면

```kotlin
Flow<String>
```

이 생성된다.

정리하면 다음과 같은 흐름으로 동작한다.

```
Compose State

↓

snapshotFlow

↓

Flow

↓

collect
```

<br>

## 4. 기본 사용법

```kotlin
@Composable
fun SnapshotFlowExample() {

    var text by remember {
        mutableStateOf("")
    }

    LaunchedEffect(Unit) {

        snapshotFlow { text }

            .collect {

                println(it)
            }
    }

    TextField(

        value = text,

        onValueChange = {

            text = it
        }
    )
}
```

#### 코드 설명

```kotlin
snapshotFlow { text }
```

현재 text 값을 Flow로 만든다.

<br>

```kotlin
.collect { }
```

값이 변경될 때마다 실행된다.

사용자가

```
a

ab

abc
```

를 입력하면 collect도

```
a

ab

abc
```

순서대로 호출된다.

<br>

## 5. Scroll 상태 감지 예제

Compose에서 가장 많이 사용하는 예제이다.

```kotlin
@Composable
fun ScrollExample() {

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {

        snapshotFlow {

            listState.firstVisibleItemIndex

        }.collect {

            println("현재 위치 : $it")
        }
    }

    LazyColumn(

        state = listState

    ) {

        items(100) {

            Text("Item $it")
        }
    }
}
```

스크롤이 움직일 때마다

```
0

1

2

3
```

처럼 값이 변경된다.

이 값을 이용하여 다음과 같은 것들을 구현할 수 있다.

- 버튼 표시
- Analytics
- 화면 전환

<br>

## 6. 검색어 변경 감지 예제

```kotlin
LaunchedEffect(Unit) {

    snapshotFlow {

        keyword

    }

        .debounce(500)

        .distinctUntilChanged()

        .collect {

            viewModel.search(it)
        }
}
```

사용자가

```
A

An

And

Andr

Android
```

를 입력해도

500ms 동안 입력이 없을 때만 search()가 호출된다.

검색 API에서 매우 자주 사용하는 패턴이다.

<br>

## 7. distinctUntilChanged()를 사용하는 이유

snapshotFlow는 값이 변경될 때마다 Flow를 방출한다.

같은 값이 여러 번 전달되는 상황에서는 .distinctUntilChanged()를 추가하는 것이 좋다.

```kotlin
snapshotFlow {

    state

}

.distinctUntilChanged()

.collect {

}
```

같은 값은 무시하고

실제로 변경된 값만 전달한다.

이를 통해 불필요한 작업을 줄일 수 있다.

<br>

## 8. snapshotFlow 사용 시 주의사항

### 1. 반드시 Coroutine에서 사용한다.

```kotlin
LaunchedEffect {

    snapshotFlow {

        value
    }
}
```

처럼 사용해야 한다.

<br>

### 2. UI를 그리는 용도로 사용하지 않는다.

snapshotFlow는

UI를 다시 그리기 위한 것이 아니라

State 변경을 Flow에서 처리하기 위한 도구이다.

<br>

### 3. 너무 많은 작업을 하지 않는다.

값이 자주 변경된다면

collect도 계속 실행된다.

필요하다면

- debounce()
- filter()
- distinctUntilChanged()

등을 활용하는 것이 좋다.

<br>

## 9. 언제 사용하면 좋을까?

사용하기 좋은 경우

- TextField 검색
- LazyList 스크롤 감지
- BottomSheet 상태 감시
- Pager 현재 페이지 감지
- Analytics 이벤트
- 화면 노출 이벤트
- Repository 호출
- API 요청

사용하지 않아도 되는 경우

- 단순히 Text를 화면에 출력
- Compose 내부에서만 사용하는 State
- Recomposition만으로 충분한 경우

이런 경우에는 일반 State만으로도 충분하다.

<br>

## 10. 정리

- snapshotFlow는 Compose State를 Flow로 변환하는 함수이다.
- Coroutine에서 State 변경을 관찰할 때 사용한다.
- 스크롤 위치, 검색어 변경, Analytics 등에 자주 활용된다.
- Flow 연산자(debounce, filter, map, distinctUntilChanged 등)를 함께 사용할 수 있다.
- UI를 그리기 위한 도구가 아니라 State 변경을 Coroutine에서 처리하기 위한 도구이다.
- Compose와 Kotlin Flow를 연결하는 대표적인 Side Effect API 중 하나이다.
