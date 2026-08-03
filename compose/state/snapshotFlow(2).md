# snapshotFlow (심화)

1. StateFlow와의 차이
2. produceState와의 차이
3. 내부 구현 원리
4. remember와의 관계
5. collectLatest와 함께 사용하기
6. 여러 State 조합해서 관찰하기
7. 무한 스크롤 심화 예제
8. 자주 하는 실수
9. 성능 고려사항
10. 테스트 작성법
11. 실무 활용 사례
12. 정리

<br>

# StateFlow와의 차이

| 항목 | snapshotFlow | StateFlow |
|---|---|---|
| 데이터 소스 | Compose State (`mutableStateOf`) | 자체 상태 보관 (ViewModel 등에서 직접 생성) |
| 생성 위치 | Composable 또는 Compose 관련 스코프 | 어디서든 (ViewModel, Repository 등) |
| 용도 | Compose State → Flow 변환(브릿지) | 상태 자체를 Flow로 노출 |
| 초기값 | 없음(구독 시점의 현재 값부터 emit) | 항상 초기값 필요 |
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

# produceState와의 차이

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

# 내부 구현 원리

`snapshotFlow`는 내부적으로 Compose의 Snapshot 시스템의 읽기 추적(read observation) 메커니즘을 사용한다. 동작 흐름은 다음과 같다.

1. flow가 collect되면 block 람다를 실행하면서 그 안에서 읽힌 모든 State 객체를 추적한다.
2. Snapshot 시스템이 이 State들에 대한 읽기를 기록해둔다.
3. 추적된 State 중 하나라도 값이 변경되면 Snapshot이 이를 감지하고 block을 다시 실행한다.
4. 새로 계산된 결과값이 이전 값과 `equals()` 기준으로 다르면 flow에 emit한다. 같으면 emit하지 않는다.

즉 `snapshotFlow`는 폴링이 아니라 Compose의 변경 알림 시스템에 직접 훅을 거는 방식이라, State가 실제로 바뀔 때만 반응하고 불필요한 재계산이 없다.

여기서 중요한 포인트는 이 "읽기 추적"이 `@Composable` 함수가 recomposition을 트리거하는 방식과 원리가 완전히 동일하다는 점이다. `Composable` 함수는 State를 읽으면 해당 함수가 recomposition scope에 등록되고, `snapshotFlow`는 block을 recomposition scope 대신 Flow emission 트리거로 등록한다는 차이만 있을 뿐, 근본적인 스냅샷 읽기 추적 메커니즘은 공유한다.

또한 `snapshotFlow`는 `Snapshot.current`를 기준으로 동작하기 때문에, 코루틴이 어떤 디스패처에서 실행되든 상관없이 Compose의 전역 Snapshot 변경을 감지할 수 있다.

<br>

# remember와의 관계

`snapshotFlow`가 관찰하는 대상은 대부분 `remember { mutableStateOf(...) }`로 만들어진 State다. `remember`는 recomposition 간에 값을 유지시켜주는 역할이고, `snapshotFlow`는 그 State의 변화를 Flow 스트림으로 노출하는 역할이라 서로 보완적이다.

주의할 점은 `snapshotFlow` 자체는 `remember`로 감싸지 않는다는 것이다. `snapshotFlow`는 보통 `LaunchedEffect` 블록 안에서 매번 새로 생성되는데, `LaunchedEffect`의 key가 바뀌지 않는 한 코루틴이 재시작되지 않으므로 문제가 없다.

만약 `snapshotFlow`로 만든 결과를 다시 Compose State로 되돌리고 싶다면 `collectAsState()`와 조합할 수도 있다.

```kotlin
val debouncedQuery by remember {
    snapshotFlow { query }
}.debounce(300).collectAsState(initial = "")
```

이 패턴은 Flow 연산자로 가공한 값을 다시 UI에서 State처럼 읽고 싶을 때 유용하다. `remember` 블록 안에서 `snapshotFlow`를 생성할 때는 key 없는 `remember`이므로 최초 1회만 생성되고 이후 recomposition에서는 재사용된다는 점을 이해하고 써야 한다.

<br>

# collectLatest와 함께 사용하기

이전 처리 중이던 작업을 취소하고 최신 값만 처리하고 싶다면 `collect` 대신 `collectLatest`를 쓴다.

```kotlin
LaunchedEffect(Unit) {
    snapshotFlow { query }
        .debounce(300)
        .collectLatest { text ->
            val result = searchApi(text) // 새 입력이 오면 이전 검색 작업은 취소됨
        }
}
```

`collect`와 `collectLatest`의 차이는 다음과 같다.

- `collect`: 이전 처리가 끝나기 전에 새 값이 와도 순서대로 처리된다(suspend 함수이므로 이전 emit 처리가 끝나야 다음 emit이 전달됨).
- `collectLatest`: 새 값이 오면 이전 block 실행을 즉시 취소하고 새 block을 실행한다.

네트워크 호출처럼 오래 걸리는 작업이 껴 있고 "최신 요청만 의미가 있는" 상황(검색, 자동완성)에는 `collectLatest`가 적합하고, 모든 이벤트를 순서대로 다 처리해야 하는 상황(로그 기록 등)에는 `collect`가 적합하다.

다만 `collectLatest`는 이전 작업을 취소하는 오버헤드가 있으므로, 매우 짧고 가벼운 작업이라면 오히려 `collect`가 더 효율적일 수 있다. 무조건 `collectLatest`가 좋은 것은 아니다.

<br>

# 여러 State 조합해서 관찰하기

여러 State를 조합해서 하나의 조건으로 관찰하고 싶을 때는 block 안에서 `data class`로 묶어서 반환하면 된다.

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

이때 `FormState`가 `data class`라면 `equals()`가 필드 값 기준으로 자동 구현되므로 `distinctUntilChanged`가 제대로 동작한다. 일반 클래스를 쓰면 참조 비교가 되어 매번 새로운 인스턴스로 인식되어 `distinctUntilChanged`가 무의미해질 수 있으니 주의해야 한다.

`combine`처럼 여러 개별 Flow를 합치는 대신, 애초에 block 안에서 여러 State를 한 번에 읽어 하나의 값으로 묶어 반환하는 게 `snapshotFlow`의 일반적인 관용구다.

<br>

# 무한 스크롤 심화 예제

스크롤 위치 감지를 페이지네이션까지 연결한 실전 예제다.

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

단순히 인덱스 값만 관찰하는 것이 아니라 `layoutInfo`에서 전체 아이템 수와 마지막으로 보이는 아이템 인덱스를 계산해서 boolean으로 변환한 뒤 `distinctUntilChanged`로 중복 호출을 막는 패턴이다. 이렇게 하면 스크롤할 때마다가 아니라 "끝에 도달했다"는 조건이 true로 바뀌는 그 순간에만 `onLoadMore`가 호출된다.

<br>

# 자주 하는 실수

**1. State를 읽지 않고 미리 계산된 값을 넣는 실수**

```kotlin
// 잘못된 예: .value를 밖에서 미리 꺼내서 넣으면 추적이 안 됨
val currentValue = someState.value
snapshotFlow { currentValue } // 이미 스냅샷된 값이라 변화 감지 불가

// 올바른 예: block 안에서 State를 직접 읽어야 함
snapshotFlow { someState.value }
```

**2. key를 잘못 지정해서 매번 재시작되는 실수**

```kotlin
// 잘못된 예: 매 recomposition마다 새로운 람다 객체가 key로 들어가 매번 재시작됨
LaunchedEffect(key1 = { listState.firstVisibleItemIndex }) { ... }

// 올바른 예: Unit이나 변하지 않는 값으로 key 고정
LaunchedEffect(Unit) {
    snapshotFlow { listState.firstVisibleItemIndex }.collect { ... }
}
```

**3. distinctUntilChanged 없이 무거운 객체를 비교하는 실수**

`data class`가 아닌 커스텀 객체에서 `equals()`를 오버라이드하지 않으면 참조 비교가 되어 매번 다른 값으로 인식되고, 결과적으로 `snapshotFlow`의 내부 distinct 로직이 무의미해져 매 recomposition마다 emit이 발생할 수 있다.

**4. side effect를 block 안에서 직접 실행하는 실수**

```kotlin
// 잘못된 예: block은 읽기 추적용이므로 여기서 side effect를 실행하면 예측 불가능하게 여러 번 실행될 수 있음
snapshotFlow {
    println("호출됨") // block이 재실행 조건 확인을 위해 여러 번 불릴 수 있음
    someState.value
}
```

block은 순수하게 값을 읽고 반환하는 용도로만 쓰고, 실제 side effect는 `collect { }` 안에서 실행해야 한다.

**5. block 안에서 예외 처리를 하지 않는 실수**

block 안에서 예외가 발생하면 flow 자체가 예외로 종료되고 이후 emit이 전혀 일어나지 않는다. 필요하면 try-catch로 감싸거나 `catch` 연산자를 붙여야 한다.

<br>

# 성능 고려사항

- `LazyListState.layoutInfo`처럼 매 스크롤 프레임마다 갱신되는 State를 관찰할 때는 특히 연산자 체인을 신경 써야 한다. 아무 가공 없이 그대로 collect하면 스크롤 중 매우 빈번하게 collect 블록이 실행되어 성능 저하를 유발할 수 있다.
- 여러 State를 하나의 block에서 읽으면 그 중 하나만 바뀌어도 전체 block이 재실행된다. 필요 이상으로 많은 State를 묶어서 읽지 않도록 주의한다.
- 스크롤 이벤트처럼 매우 빈번하게 바뀌는 State를 관찰할 때는 `debounce`나 `sample` 같은 연산자로 emit 빈도를 조절하는 것이 성능에 유리하다.
- block 자체는 매번 동기적으로 실행되므로, block 안에 무거운 연산(정렬, 대량 리스트 순회 등)을 넣지 않는 것이 좋다. 무거운 연산은 `map` 연산자로 분리해서 collect 다운스트림에서 처리하는 게 낫다.

<br>

# 테스트 작성법

`snapshotFlow`를 사용하는 코드는 `Snapshot` API를 직접 다루는 단위 테스트로 검증할 수 있다.

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

# 실무 활용 사례

- 자동 저장(autosave): 텍스트 편집기에서 입력값이 일정 시간 멈추면 자동으로 임시 저장
- 스크롤 위치 기반 분석 이벤트 로깅: 사용자가 어떤 섹션을 얼마나 오래 봤는지 추적할 때 스크롤 인덱스 변화를 관찰
- Pager 상태 동기화: `PagerState.currentPage`를 관찰해서 외부 ViewModel의 현재 탭 상태와 동기화
- 키보드 표시 여부에 따른 레이아웃 조정: 키보드가 올라올 때 스크롤 위치를 자동으로 조정
- 폼 유효성 검사: 여러 입력 필드의 State를 조합해서 전체 폼이 유효한지 실시간으로 판단
- 스크롤 방향 기반 UI 표시/숨김: 위로 스크롤할 때만 상단 바가 보이도록 처리

<br>

# 정리

`snapshotFlow`는 Compose State를 Flow로 바꾸는 브릿지 함수로, `StateFlow`(상태를 담는 홀더)나 `produceState`(Flow → State, 반대 방향)와는 역할이 다르다. 내부적으로는 `@Composable` 함수의 recomposition 트리거와 동일한 Snapshot 읽기 추적 메커니즘을 사용하며, `remember`로 만든 State를 관찰 대상으로 삼는 경우가 대부분이다.

`collectLatest`로 최신 값만 처리하거나, 여러 State를 `data class`로 묶어 한 번에 관찰하는 패턴을 알아두면 검색, 폼 검증, 무한 스크롤 같은 실무 상황에 바로 적용할 수 있다. block 안에서 State를 직접 읽어야 추적이 된다는 점, block은 순수하게 값만 반환하고 side effect는 collect에서 처리해야 한다는 점, `equals()`가 값 비교로 동작하는 타입을 써야 `distinctUntilChanged`가 제대로 동작한다는 점이 실수를 줄이는 핵심 원칙이다.
