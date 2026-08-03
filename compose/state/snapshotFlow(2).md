# snapshotFlow

1. SnapshotFlow란?
2. 왜 필요한가?
3. StateFlow와의 차이
4. produceState와의 차이
5. 내부 구현 원리
6. 기본 사용법
7. remember와의 관계
8. LaunchedEffect와 함께 사용하는 이유
9. collectLatest와 함께 사용하기
10. LazyListState 스크롤 감지 예제
11. TextField 검색 예제
12. 여러 State 조합해서 관찰하기
13. 사용 시 주의사항
14. 자주 하는 실수
15. 성능 고려사항
16. 테스트 작성법
17. 실무 활용 사례
18. 정리

<br>

## 1. SnapshotFlow란?

`snapshotFlow`는 Compose의 `State<T>` 객체를 Kotlin `Flow`로 변환해주는 함수다. Compose 런타임이 관리하는 Snapshot 시스템을 관찰해서, `State`가 읽히고 그 값이 변경될 때마다 새 값을 flow로 emit한다.

```kotlin
val flow: Flow<Int> = snapshotFlow { someState.value }
```

Compose의 State는 원래 컴포저블 함수 안에서 recomposition을 트리거하는 용도로 설계되어 있는데, `snapshotFlow`는 이 State 변화를 Flow의 세계로 가져와서 `collect`, `map`, `debounce`, `filter` 같은 Flow 연산자를 그대로 쓸 수 있게 해준다.

패키지 위치는 `androidx.compose.runtime.snapshotFlow`이며, Compose Runtime 모듈에 포함되어 있어 별도의 의존성 추가 없이 Compose를 쓰는 프로젝트라면 바로 사용할 수 있다.

```kotlin
import androidx.compose.runtime.snapshotFlow
```

<br>

## 2. 왜 필요한가?

Compose에서는 State 변화에 반응해서 side effect를 실행해야 하는 경우가 많다. 예를 들어 스크롤 위치가 특정 지점을 넘으면 로그를 찍거나, 검색어가 바뀌면 API를 호출하는 식이다.

문제는 State 자체는 단순 값 보관/알림 역할만 하고, "값이 바뀔 때마다 무언가를 실행"하는 로직을 직접 구현하려면 번거롭다. `snapshotFlow`가 없다면 State 변화를 감지하기 위해 별도의 옵저버 패턴을 만들거나 매 recomposition마다 조건문을 검사해야 한다.

`snapshotFlow`가 없던 시절에는 이런 식으로 우회했다.

```kotlin
// snapshotFlow 없이 우회하던 방식 (권장하지 않음)
@Composable
fun WithoutSnapshotFlow(listState: LazyListState) {
    var lastIndex by remember { mutableStateOf(-1) }
    val currentIndex = listState.firstVisibleItemIndex

    // recomposition마다 직접 비교해야 함 — 지저분하고 side effect 타이밍 보장이 안 됨
    if (currentIndex != lastIndex) {
        lastIndex = currentIndex
        // 여기서 직접 부수효과를 실행하면 recomposition 중 금지된 작업(예: 네트워크 호출)이 섞일 위험
    }
}
```

`snapshotFlow`는 이 문제를 Flow 연산자 체인으로 깔끔하게 풀 수 있게 해준다. 특히 `debounce`, `distinctUntilChanged`, `collectLatest` 같은 연산자와 결합하면 검색 디바운싱, 스크롤 이벤트 필터링 같은 패턴을 손쉽게 구현할 수 있고, side effect 실행이 코루틴 스코프 안에서 안전하게 이루어진다는 장점도 있다.

<br>

## 3.StateFlow와의 차이

| 항목 | snapshotFlow | StateFlow |
|---|---|---|
| 데이터 소스 | Compose State (`mutableStateOf`) | 자체 상태 보관 (ViewModel 등에서 직접 생성) |
| 생성 위치 | Composable 또는 Compose 관련 스코프 | 어디서든 (ViewModel, Repository 등) |
| 용도 | Compose State → Flow 변환(브릿지) | 상태 자체를 Flow로 노출 |
| 초기값 | 없음(구독 시점의 현재 값부터 emit) | 항상 초기값 필요 |
| 생명주기 의존성 | Compose Snapshot 시스템에 의존 | Compose와 무관하게 독립적으로 동작 |
| Cold/Hot 여부 | Cold Flow (collect될 때마다 별도 관찰 시작) | Hot Flow (구독자 없어도 상태 유지) |
| 값 유지 | collect 종료 시 상태 사라짐 | collect 종료 후에도 마지막 값 유지 |

핵심 차이는 역할이다. `StateFlow`는 상태 자체를 표현하는 홀더고, `snapshotFlow`는 이미 존재하는 Compose State를 Flow로 "변환"하는 어댑터다. 보통 ViewModel의 상태는 `StateFlow`로 노출하고, UI 레이어(스크롤 위치, 텍스트 필드 값 등 Compose 자체 State)를 관찰할 때 `snapshotFlow`를 쓴다.

두 개를 같이 쓰는 경우도 흔하다. UI의 Compose State를 `snapshotFlow`로 관찰하다가, 그 값을 ViewModel의 `StateFlow`에 반영하는 패턴이다.

```kotlin
LaunchedEffect(Unit) {
    snapshotFlow { scrollState.firstVisibleItemIndex }
        .collect { index -> viewModel.updateScrollIndex(index) } // StateFlow로 전달
}
```

<br>

## 4.produceState와의 차이

`produceState`도 State를 생성하는 Compose API지만 목적이 반대다. `snapshotFlow`는 "State → Flow" 방향이고, `produceState`는 "Flow(또는 suspend 함수) → State" 방향이다.

```kotlin
// produceState: 외부 데이터(Flow, suspend 함수 등)를 State로 변환
@Composable
fun rememberUserState(userId: String): State<User?> {
    return produceState<User?>(initialValue = null, userId) {
        value = fetchUser(userId)
    }
}
```

즉 이미 Compose State로 가지고 있는 값을 Flow 연산자로 가공하고 싶다면 `snapshotFlow`, 반대로 Flow나 suspend 결과를 Compose State로 편입시키고 싶다면 `produceState`를 쓴다. 두 API는 정반대 방향이라 헷갈리지 않게 "S로 시작하면 State가 출발점, p로 시작하면 State가 도착점"으로 기억해두면 편하다.

<br>

## 5. 내부 구현 원리

`snapshotFlow`는 내부적으로 Compose의 Snapshot 시스템의 `Snapshot.observe` 메커니즘을 사용한다. 동작 흐름은 다음과 같다.

1. flow가 collect되면 block 람다를 실행하면서 그 안에서 읽힌 모든 State 객체를 추적한다.
2. Snapshot 시스템이 이 State들에 대한 읽기(read)를 기록해둔다.
3. 추적된 State 중 하나라도 값이 변경되면 Snapshot이 이를 감지하고 block을 다시 실행한다.
4. 새로 계산된 결과값이 이전 값과 `equals()` 기준으로 다르면 flow에 emit한다. 같으면 emit하지 않는다(내부적으로 `distinctUntilChanged`와 유사하게 동작).

즉 `snapshotFlow`는 폴링이 아니라 Compose의 변경 알림 시스템에 직접 훅을 거는 방식이라, State가 실제로 바뀔 때만 반응하고 불필요한 재계산이 없다.

```kotlin
// 개념적으로 이런 식으로 동작한다고 이해하면 됨
public fun <T> snapshotFlow(block: () -> T): Flow<T> = flow {
    val results = MutableSharedFlow<T>(replay = 1)
    val observer = SnapshotStateObserver { /* block 재실행 트리거 */ }
    // block 실행 → 읽힌 State 추적 → 변경 시 재실행 → emit
}
```

여기서 중요한 포인트는 "읽기 추적(read observation)"이 Compose Runtime의 핵심 메커니즘이라는 점이다. `@Composable` 함수가 recomposition을 트리거하는 방식과 원리가 동일하다. `Composable` 함수는 State를 읽으면 해당 함수가 recomposition scope에 등록되고, `snapshotFlow`는 block을 recomposition scope 대신 Flow emission 트리거로 등록한다는 차이만 있을 뿐, 근본적인 스냅샷 읽기 추적 메커니즘은 공유한다.

또한 `snapshotFlow`는 `Snapshot.current`에서 실행되기 때문에, 코루틴이 어떤 디스패처에서 실행되든 상관없이 Compose의 Global Snapshot 변경을 감지할 수 있다.

<br>

## 6.기본 사용법

```kotlin
@Composable
fun ScrollObserverExample(listState: LazyListState) {
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                println("첫 번째로 보이는 아이템 인덱스: $index")
            }
    }
}
```

기본 패턴은 항상 `LaunchedEffect` 내부에서 `snapshotFlow { ... }.collect { ... }` 형태로 사용하는 것이다. block 람다 안에서 관찰하고 싶은 State를 읽기만 하면 된다.

block은 람다이므로 단순히 State 하나를 반환할 수도 있고, 여러 값을 조합해서 하나의 결과로 반환할 수도 있다.

```kotlin
LaunchedEffect(Unit) {
    snapshotFlow { 
        // 여러 State를 조합해서 하나의 값으로 반환
        "${listState.firstVisibleItemIndex}-${listState.firstVisibleItemScrollOffset}"
    }.collect { combined ->
        println("스크롤 위치 조합: $combined")
    }
}
```

<br>

## 7.remember와의 관계

`snapshotFlow`가 관찰하는 대상은 대부분 `remember { mutableStateOf(...) }`로 만들어진 State다. `remember`는 recomposition 간에 값을 유지시켜주는 역할이고, `snapshotFlow`는 그 State의 변화를 Flow 스트림으로 노출하는 역할이라 서로 보완적이다.

```kotlin
val scrollState = rememberLazyListState() // 내부적으로 remember 사용
// scrollState.firstVisibleItemIndex는 State이며, snapshotFlow로 관찰 가능
```

주의할 점은 `snapshotFlow` 자체는 `remember`로 감싸지 않는다는 것이다. `snapshotFlow`는 보통 `LaunchedEffect` 블록 안에서 매번 새로 생성되는데, `LaunchedEffect`의 key가 바뀌지 않는 한 코루틴이 재시작되지 않으므로 문제가 없다.

만약 `snapshotFlow`로 만든 결과를 다시 Compose State로 되돌리고 싶다면 `collectAsState()`와 조합할 수도 있다.

```kotlin
val debouncedQuery by remember {
    snapshotFlow { query }
}.debounce(300).collectAsState(initial = "")
```

이 패턴은 Flow 연산자로 가공한 값을 다시 UI에서 State처럼 읽고 싶을 때 유용하다. 다만 `remember` 블록 안에서 `snapshotFlow`를 생성할 때는 key 없는 `remember`이므로 최초 1회만 생성되고 이후 recomposition에서는 재사용된다는 점을 이해하고 써야 한다.

<br>

## 8.LaunchedEffect와 함께 사용하는 이유

`snapshotFlow`는 suspend 컨텍스트, 즉 코루틴 스코프 안에서만 collect할 수 있다. Composable 함수는 기본적으로 suspend 함수가 아니므로 직접 호출할 수 없고, `LaunchedEffect`가 제공하는 코루틴 스코프 안에서 실행해야 한다.

또한 `LaunchedEffect`는 key가 바뀌지 않는 한 recomposition이 일어나도 코루틴을 재시작하지 않기 때문에, `snapshotFlow` collect가 매 recomposition마다 새로 시작되는 것을 방지해준다. 이게 없으면 collect가 계속 취소·재시작되면서 이벤트를 놓치거나 중복 실행될 수 있다.

`LaunchedEffect` 대신 `rememberCoroutineScope`로 얻은 스코프에서 `launch`로 collect를 시작하는 것도 가능은 하지만, 이 경우 컴포저블이 recomposition될 때마다 중복 launch가 발생하지 않도록 직접 관리해야 하므로 특별한 이유가 없다면 `LaunchedEffect`를 쓰는 게 안전하다.

```kotlin
// 권장: LaunchedEffect 사용
LaunchedEffect(Unit) {
    snapshotFlow { state.value }.collect { /* ... */ }
}

// 비권장: 버튼 클릭 등 이벤트 핸들러에서 직접 launch할 때만 예외적으로 사용
val scope = rememberCoroutineScope()
Button(onClick = {
    scope.launch {
        snapshotFlow { state.value }.first() // 단발성으로 첫 값만 필요할 때
    }
}) { Text("확인") }
```

<br>

## 9.collectLatest와 함께 사용하기

검색어 입력처럼 최신 값만 처리하고 이전 처리 중이던 작업은 취소하고 싶은 경우 `collectLatest`를 함께 쓴다.

```kotlin
@Composable
fun SearchExample(query: TextFieldValue) {
    LaunchedEffect(Unit) {
        snapshotFlow { query.text }
            .debounce(300)
            .distinctUntilChanged()
            .collectLatest { text ->
                val result = searchApi(text) // 새 입력 오면 이전 검색은 취소됨
                // UI 업데이트
            }
    }
}
```

`collect` 대신 `collectLatest`를 쓰면 새 emit이 들어올 때 진행 중이던 이전 블록 실행을 자동으로 취소하므로, 빠르게 연속 입력되는 검색어 처리에 적합하다.

`collect`와 `collectLatest`의 차이를 명확히 하면 다음과 같다.

- `collect`: 이전 처리가 끝나기 전에 새 값이 와도 큐에 쌓여 순서대로 처리된다(정확히는 suspend 함수이므로 이전 emit 처리가 끝나야 다음 emit이 전달됨).
- `collectLatest`: 새 값이 오면 이전 block 실행을 즉시 취소(`cancel`)하고 새 block을 실행한다.

네트워크 호출처럼 오래 걸리는 작업이 껴 있고 "최신 요청만 의미가 있는" 상황(검색, 자동완성)에는 `collectLatest`가 적합하고, 모든 이벤트를 순서대로 다 처리해야 하는 상황(로그 기록 등)에는 `collect`가 적합하다.

<br>

## 10.LazyListState 스크롤 감지 예제

```kotlin
@Composable
fun ScrollToTopButton(listState: LazyListState) {
    var showButton by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .map { it > 3 }
            .distinctUntilChanged()
            .collect { visible ->
                showButton = visible
            }
    }

    if (showButton) {
        FloatingActionButton(onClick = {
            // 스크롤 맨 위로 이동
        }) {
            Icon(Icons.Default.ArrowUpward, contentDescription = "위로")
        }
    }
}
```

`firstVisibleItemIndex`가 3을 넘는 시점에만 `showButton`이 true/false로 바뀌도록 `map` + `distinctUntilChanged`를 조합했다. 매 스크롤 픽셀 단위로 recomposition이 발생하지 않고, boolean 값이 실제로 바뀔 때만 UI가 갱신된다.

무한 스크롤(페이지네이션) 패턴도 비슷하게 구현할 수 있다.

```kotlin
@Composable
fun InfiniteScrollList(listState: LazyListState, onLoadMore: () -> Unit) {
    LaunchedEffect(listState) {
        snapshotFlow { 
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= totalItems - 3 // 끝에서 3개 남았을 때
        }
            .distinctUntilChanged()
            .filter { it } // true일 때만 통과
            .collect {
                onLoadMore()
            }
    }
}
```

<br>

## 11. TextField 검색 예제

```kotlin
@Composable
fun SearchScreen(viewModel: SearchViewModel) {
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(300)
            .filter { it.isNotBlank() }
            .distinctUntilChanged()
            .collectLatest { text ->
                viewModel.search(text)
            }
    }

    TextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("검색어 입력") }
    )
}
```

`debounce(300)`으로 타이핑이 멈추고 300ms 후에만 검색을 실행하고, `filter`로 빈 문자열은 건너뛴다. 타이핑할 때마다 API를 호출하는 비효율을 막아준다.

최소 글자 수 제한이나 검색 결과 로딩 상태까지 포함하면 실무에서는 이런 형태가 된다.

```kotlin
@Composable
fun SearchScreenWithLoading(viewModel: SearchViewModel) {
    var query by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(300)
            .distinctUntilChanged()
            .collectLatest { text ->
                if (text.length < 2) return@collectLatest
                isLoading = true
                try {
                    viewModel.search(text)
                } finally {
                    isLoading = false
                }
            }
    }

    Column {
        TextField(value = query, onValueChange = { query = it })
        if (isLoading) CircularProgressIndicator()
    }
}
```

<br>

## 12. 여러 State 조합해서 관찰하기

여러 State를 조합해서 하나의 조건으로 관찰하고 싶을 때는 block 안에서 `Pair`나 `data class`로 묶어서 반환하면 된다.

```kotlin
data class FormState(val email: String, val password: String)

@Composable
fun LoginForm() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isFormValid by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow { FormState(email, password) }
            .distinctUntilChanged()
            .collect { form ->
                isFormValid = form.email.contains("@") && form.password.length >= 8
            }
    }
}
```

이때 `FormState`가 `data class`라면 `equals()`가 필드 값 기준으로 자동 구현되므로 `distinctUntilChanged`가 제대로 동작한다. 만약 일반 클래스를 쓰면 참조 비교가 되어 매번 새로운 인스턴스로 인식되어 `distinctUntilChanged`가 무의미해질 수 있으니 주의해야 한다.

`combine`처럼 여러 개별 Flow를 합치는 대신, 애초에 block 안에서 여러 State를 한 번에 읽어 하나의 값으로 묶어 반환하는 게 `snapshotFlow`의 일반적인 관용구다.

<br>

## 13. 사용 시 주의사항

- block 람다 안에서 State를 실제로 **읽어야** 추적이 된다. 조건문에 의해 특정 State를 읽지 않는 분기가 있으면 그 State 변화는 감지되지 않는다.
- `snapshotFlow`는 Compose Snapshot 시스템에 의존하므로, Compose 스코프 밖(예: 일반 ViewModel의 init 블록)에서 State가 아닌 일반 변수를 넣으면 동작하지 않는다.
- 반드시 코루틴 스코프(`LaunchedEffect`, `rememberCoroutineScope` 등) 안에서 collect해야 한다.
- 초기값을 즉시 emit하지 않는다. 첫 emit은 block이 처음 실행되어 나온 값부터 시작되긴 하지만, 이는 구독 시점 기준이라 원하는 시점과 다를 수 있음을 유의해야 한다.
- 결과 값이 이전 값과 같으면 emit되지 않으므로(내부 distinct 동작), 값 자체가 아니라 "읽기 발생 여부"만 감지하고 싶다면 별도 처리가 필요하다.
- block 안에서 예외가 발생하면 flow 자체가 예외로 종료된다. try-catch로 감싸거나 `catch` 연산자를 붙여서 예외 처리를 고려해야 한다.

<br>

## 14. 자주 하는 실수

**1. State를 읽지 않고 미리 계산된 값을 넣는 실수**

```kotlin
// 잘못된 예: .value를 밖에서 미리 꺼내서 넣으면 추적이 안 됨
val currentValue = someState.value
snapshotFlow { currentValue } // 이미 스냅샷된 값이라 변화 감지 불가

// 올바른 예: block 안에서 State를 직접 읽어야 함
snapshotFlow { someState.value }
```

**2. LaunchedEffect 없이 Composable 함수 최상위에서 직접 호출하려는 실수**

`snapshotFlow`는 Flow를 "생성"만 하는 것은 suspend가 아니지만, `collect`는 suspend 함수라서 Composable 본문에서 직접 호출할 수 없다. 반드시 `LaunchedEffect` 등 코루틴 스코프 안에서 collect해야 한다.

**3. key를 잘못 지정해서 매번 재시작되는 실수**

```kotlin
// 잘못된 예: 매 recomposition마다 새로운 람다 객체가 key로 들어가 매번 재시작됨
LaunchedEffect(key1 = { listState.firstVisibleItemIndex }) { ... }

// 올바른 예: Unit이나 변하지 않는 값으로 key 고정
LaunchedEffect(Unit) {
    snapshotFlow { listState.firstVisibleItemIndex }.collect { ... }
}
```

**4. distinctUntilChanged 없이 무거운 객체를 비교하는 실수**

`data class`가 아닌 커스텀 객체에서 `equals()`를 오버라이드하지 않으면 참조 비교가 되어 매번 다른 값으로 인식되고, 결과적으로 `snapshotFlow`의 내부 distinct 로직이 무의미해져 매 recomposition마다 emit이 발생할 수 있다.

**5. side effect를 block 안에서 직접 실행하는 실수**

```kotlin
// 잘못된 예: block은 읽기 추적용이므로 여기서 side effect를 실행하면 예측 불가능하게 여러 번 실행될 수 있음
snapshotFlow { 
    println("호출됨") // block이 재실행 조건 확인을 위해 여러 번 불릴 수 있음
    someState.value 
}
```

block은 순수하게 값을 읽고 반환하는 용도로만 쓰고, 실제 side effect는 `collect { }` 안에서 실행해야 한다.

<br>

## 15. 성능 고려사항

- `snapshotFlow` block 안에서는 가볍고 빠른 연산만 하는 것이 좋다. block은 관찰 대상 State가 바뀔 때마다 동기적으로 재실행되므로 무거운 연산을 넣으면 UI 스레드 성능에 영향을 줄 수 있다.
- 여러 State를 하나의 block에서 읽으면 그 중 하나만 바뀌어도 전체 block이 재실행된다. 필요 이상으로 많은 State를 묶어서 읽지 않도록 주의한다.
- `map`, `filter`, `distinctUntilChanged` 등을 활용해 불필요한 emit을 줄이면 다운스트림에서 실행되는 recomposition이나 side effect 횟수를 최소화할 수 있다.
- 스크롤 이벤트처럼 매우 빈번하게 바뀌는 State를 관찰할 때는 `debounce`나 `sample` 같은 연산자로 emit 빈도를 조절하는 것이 성능에 유리하다.
- `LazyListState.layoutInfo`처럼 매 스크롤 프레임마다 갱신되는 State를 관찰할 때는 특히 연산자 체인을 신경 써야 한다. 아무 가공 없이 그대로 collect하면 스크롤 중 매우 빈번하게 collect 블록이 실행되어 성능 저하를 유발할 수 있다.
- `collectLatest`는 이전 작업을 취소하는 오버헤드가 있으므로, 매우 짧고 가벼운 작업이라면 오히려 `collect`가 더 효율적일 수 있다. 무조건 `collectLatest`가 좋은 것은 아니다.

<br>

## 16. 테스트 작성법

`snapshotFlow`를 사용하는 코드는 Compose 테스트 환경 또는 `Snapshot` API를 직접 다루는 단위 테스트로 검증할 수 있다.

```kotlin
@Test
fun snapshotFlow_emits_on_state_change() = runTest {
    val state = mutableStateOf(0)
    val results = mutableListOf<Int>()

    val job = launch {
        snapshotFlow { state.value }.toList(results)
    }

    // Compose 외부에서 State를 변경할 때는 Snapshot.withMutableSnapshot으로 감싸야 변경이 감지됨
    Snapshot.withMutableSnapshot {
        state.value = 1
    }
    Snapshot.withMutableSnapshot {
        state.value = 2
    }

    advanceUntilIdle()
    job.cancel()

    assertEquals(listOf(0, 1, 2), results)
}
```

Compose 테스트 룰(`createComposeRule`)을 사용하는 UI 테스트에서는 `composeTestRule.awaitIdle()`로 recomposition과 flow emission이 안정화될 때까지 기다린 후 검증하는 것이 일반적이다.

```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun scrollTriggersFlow() {
    composeTestRule.setContent {
        ScrollToTopButton(listState = rememberLazyListState())
    }
    composeTestRule.onNodeWithTag("list").performScrollToIndex(10)
    composeTestRule.awaitIdle()
    composeTestRule.onNodeWithContentDescription("위로").assertIsDisplayed()
}
```

단위 테스트 레벨에서 `Snapshot.withMutableSnapshot` 없이 그냥 `state.value = 1`처럼 직접 대입해도 대부분 동작은 하지만, 여러 스레드나 명시적인 스냅샷 경계가 필요한 상황에서는 `withMutableSnapshot`으로 감싸는 것이 더 안전하고 명시적이다.

<br>

## 17. 실무 활용 사례

- 무한 스크롤: `LazyListState`의 마지막 보이는 아이템 인덱스를 관찰해서 리스트 끝에 가까워지면 다음 페이지 로드
- 검색 디바운싱: `TextField` 입력값을 `debounce` + `collectLatest`로 관찰해서 API 호출 최적화
- 스크롤 방향에 따른 UI 표시/숨김: 위로 스크롤할 때만 상단 바를 보이게 하는 등의 처리
- 폼 유효성 검사: 여러 입력 필드의 State를 조합해서 전체 폼이 유효한지 실시간으로 판단
- 애니메이션 트리거: 특정 State 값이 임계치를 넘을 때 애니메이션을 시작
- 자동 저장(autosave): 텍스트 편집기에서 입력값이 일정 시간 멈추면 자동으로 임시 저장
- 스크롤 위치 기반 분석 이벤트 로깅: 사용자가 어떤 섹션을 얼마나 오래 봤는지 추적할 때 스크롤 인덱스 변화를 관찰
- Pager 상태 동기화: `PagerState.currentPage`를 관찰해서 외부 ViewModel의 현재 탭 상태와 동기화
- 키보드 표시 여부에 따른 레이아웃 조정: `WindowInsets` 관련 State를 관찰해서 키보드가 올라올 때 스크롤 위치 자동 조정

<br>

## 18. 정리

- `snapshotFlow`는 Compose의 State를 Flow로 변환해주는 브릿지 함수로, Snapshot 시스템의 읽기 추적을 이용해 State 변화가 있을 때만 재계산하고 emit한다. 
- `StateFlow`가 상태 자체를 담는 홀더라면 `snapshotFlow`는 이미 존재하는 Compose State를 관찰하는 어댑터이고, 반대 방향인 `produceState`와 헷갈리지 않아야 한다.

- 반드시 `LaunchedEffect` 같은 코루틴 스코프 안에서 collect해야 하며, `debounce`, `distinctUntilChanged`, `collectLatest` 같은 Flow 연산자와 결합하면 검색 디바운싱, 스크롤 감지, 무한 스크롤, 폼 유효성 검사 같은 UI 반응 로직을 효율적으로 구현할 수 있다.

- 핵심은 block 안에서 State를 실제로 읽어야 추적된다는 점, block은 가볍게 유지하고 side effect는 collect 블록 안에서 실행해야 한다는 점, 여러 State를 묶을 때는 `equals()`가 값 비교로 동작하는 타입(`data class` 등)을 써야 `distinctUntilChanged`가 제대로 동작한다는 점이다. 
이 원칙들을 지키면 실무에서 스크롤, 검색, 폼 검증 등 다양한 UI 반응 로직을 안정적으로 구현할 수 있다.
