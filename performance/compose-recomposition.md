# Compose 리컴포지션 추적

<br>

## 목차

1. 개요
2. Composition과 Recomposition 기본 개념
3. Stability(안정성) 개념
4. Compose Compiler의 안정성 추론
5. Compiler Metrics 리포트 읽는 법
6. Layout Inspector로 리컴포지션 확인하기
7. Recomposition Counts 오버레이 활용
8. 자주 발생하는 불필요한 리컴포지션 패턴
9. remember와 key를 이용한 최적화
10. derivedStateOf로 리컴포지션 범위 줄이기
11. Modifier.Node API 이해
12. Modifier.Node로 최적화하기
13. SnapshotStateObserver와 RecomposeScope
14. Macrobenchmark와의 연계
15. 실전 최적화 사례
16. 트러블슈팅
17. 정리

<br>

## 1. 개요

Jetpack Compose는 상태(State)가 변경될 때마다 해당 상태를 읽는 Composable 함수만 다시 실행(리컴포지션)해서 UI를 갱신하는 선언적 UI 프레임워크임. 이 모델은 기존 View 시스템의 명령형 업데이트(`findViewById` + `setText` 등)보다 코드가 간결하지만, "어떤 상태가 바뀌었을 때 어떤 Composable이 다시 실행되는지"를 개발자가 명확히 통제하지 못하면 불필요한 리컴포지션이 화면 전반에 퍼져 스크롤 jank나 배터리 소모 증가로 이어질 수 있음.

리컴포지션 자체는 나쁜 것이 아니라 Compose의 정상적인 동작 방식이며, 문제는 "꼭 필요하지 않은 리컴포지션"이 반복적으로, 특히 리스트처럼 개수가 많은 곳에서 발생하는 경우임. 이 문서는 리컴포지션이 왜 발생하는지의 근본 원리(Stability, Compiler Metrics)부터, 실제로 리컴포지션을 관찰하는 도구(Layout Inspector, Recomposition Counts), 그리고 최신 Modifier.Node API를 활용한 최적화까지 폭넓게 정리함.

<br>

## 2. Composition과 Recomposition 기본 개념

### Composition

Compose 런타임이 `@Composable` 함수를 실행해서 만들어내는 UI 트리(정확히는 Slot Table이라는 자료구조에 저장된 UI 구조와 상태의 스냅샷)를 Composition이라고 함. 최초 실행을 Initial Composition, 이후 상태 변경에 따라 트리 일부를 다시 그리는 것을 Recomposition이라고 구분함.

### Recomposition의 트리거

Compose는 `State<T>`(예: `mutableStateOf`, `remember { mutableStateOf(...) }`로 만든 값)를 읽는 Composable 함수를 자동으로 추적함. 이 상태 값이 변경되면, 런타임은 "이 상태를 실제로 읽은 Composable"만 다시 실행 대상으로 큐에 등록함. 이 단위를 Recompose Scope라고 부르며, 기본적으로 하나의 Composable 함수 호출 범위가 하나의 Recompose Scope가 됨.

중요한 점은 리컴포지션이 부모에서 자식으로 전체 트리를 따라 내려가는 것이 아니라, 상태를 읽은 지점부터 국소적으로 발생한다는 것임. 예를 들어 `Column { Text(count.toString()); Button(...) }` 구조에서 `count`가 바뀌면 `Text`만 리컴포지션 대상이 되고, `Column`이나 `Button`은 건드리지 않음 — 단, 이는 Compose 컴파일러가 각 호출부를 별도 Recompose Scope로 잘 분리할 수 있을 때의 이야기이며, 파라미터의 Stability에 따라 이 분리가 실패할 수 있음(3장).

### Skipping

Compose 컴파일러는 각 Composable 함수 호출 시 이전 호출과 파라미터가 동일한지(`equals()` 비교, 또는 안정적인 타입이면 참조 비교)를 확인해서, 파라미터가 전혀 바뀌지 않았다면 함수 본문 실행 자체를 건너뛰는 최적화를 자동으로 적용함. 이를 Skipping이라고 하며, 리컴포지션 최적화의 핵심 메커니즘임. 이 Skipping이 제대로 동작하려면 함수의 모든 파라미터가 "안정적(Stable)"이어야 함.

<br>

## 3. Stability(안정성) 개념

Compose 컴파일러는 각 타입을 다음 세 범주로 분류함.

- **Stable**: 값이 바뀌면 Compose가 이를 확실히 감지할 수 있고(즉 `equals()`가 값의 실질적 변경을 정확히 반영), 같으면 리컴포지션을 건너뛸 수 있다고 확신할 수 있는 타입. `Int`, `String`, `Boolean` 같은 원시 타입, `data class`로 정의된 모든 필드가 `val`이고 각 필드 타입도 Stable인 경우, `@Immutable`/`@Stable`이 명시된 타입, Compose 런타임이 관리하는 `MutableState<T>` 등이 해당됨.
- **Immutable**: Stable의 부분집합으로, 생성된 이후 절대 값이 바뀌지 않음을 보장하는 타입. `@Immutable` 애너테이션으로 명시.
- **Unstable**: 컴파일러가 값의 변경 여부를 확신할 수 없는 타입. 대표적으로 `var` 필드를 가진 클래스, `List`/`Map`/`Set` 같은 표준 컬렉션 인터페이스(구현체가 언제든 내부적으로 변경될 수 있다고 가정), 외부 라이브러리에서 온 클래스(안정성 정보가 없는 경우) 등이 해당됨.

파라미터 중 하나라도 Unstable로 분류되면, 컴파일러는 "이 값이 실제로 바뀌었는지 확신할 수 없으니 매번 리컴포지션을 수행하라"고 보수적으로 판단함. 즉 Unstable 파라미터가 하나라도 있으면 해당 Composable은 Skipping 대상에서 제외되어, 부모가 리컴포지션될 때마다 무조건 함께 실행됨.

### 표준 컬렉션이 Unstable로 취급되는 이유

`List<T>`는 인터페이스이고, `MutableList` 등 가변 구현체가 이를 구현할 수 있기 때문에 컴파일러 입장에서는 "이 List 인스턴스가 나중에 내용이 바뀔 수도 있다"는 가능성을 배제할 수 없음. 그래서 코틀린 표준 컬렉션 타입은 기본적으로 Unstable로 분류됨. `kotlinx.collections.immutable`의 `ImmutableList`/`persistentListOf()` 같은 진짜 불변 컬렉션을 쓰면 Stable로 인식되어 이 문제를 해결할 수 있음.

```kotlin
// Unstable로 취급됨 — List는 인터페이스, 가변 구현 가능성 때문
data class HomeUiState(
    val items: List<Item>
)

// Stable로 취급됨 — ImmutableList는 진짜 불변을 보장
data class HomeUiState(
    val items: ImmutableList<Item>
)
```

### @Stable과 @Immutable 직접 지정

컴파일러가 스스로 안정성을 추론하지 못하는 상황(예: 외부 모듈에서 가져온 클래스, 인터페이스)에는 개발자가 직접 애너테이션으로 계약을 명시할 수 있음.

```kotlin
@Immutable
data class UserProfile(
    val id: String,
    val name: String,
    val avatarUrl: String
)
```

```kotlin
@Stable
interface HomeUiEvents {
    fun onItemClick(id: String)
    fun onRefresh()
}
```

`@Stable`/`@Immutable`을 붙이는 것은 "나는 이 타입이 실제로 이 계약을 지킨다"는 개발자의 약속이므로, 내부적으로 `var` 필드를 몰래 변경하면서 애너테이션만 붙이면 Compose가 변경을 감지하지 못해 화면이 갱신되지 않는 버그로 이어짐. 애너테이션은 실제 불변성이 보장된 타입에만 붙여야 함.

<br>

## 4. Compose Compiler의 안정성 추론

Compose 컴파일러는 클래스를 분석할 때 다음 규칙을 순서대로 적용함.

1. 원시 타입(Int, Long, Float, Double, Boolean, Char), String, 함수 타입(람다) 등은 기본적으로 Stable
2. 모든 프로퍼티가 `val`이고, 각 프로퍼티의 타입도 Stable인 `data class`/`class`는 Stable로 추론
3. `var` 프로퍼티가 하나라도 있으면 Unstable
4. 인터페이스나 추상 클래스는 구현체를 알 수 없으므로 기본적으로 Unstable (단, `@Stable`로 계약을 명시하면 Stable로 간주)
5. 제네릭 타입 파라미터가 있는 클래스는 타입 파라미터의 안정성에 따라 조건부로 결정됨 (예: `Box<T>`는 `T`가 Stable일 때만 Stable)
6. 외부 모듈(다른 Gradle 모듈, 외부 라이브러리)의 클래스는 해당 모듈이 Compose 컴파일러의 안정성 설정 파일을 함께 배포하지 않는 한 기본적으로 Unstable로 간주 (멀티모듈 프로젝트에서 흔히 겪는 함정)

### 멀티모듈에서의 안정성 문제

`:core:model` 모듈에 정의된 `data class`를 `:feature:home` 모듈의 Composable에서 파라미터로 받는 경우, 두 모듈이 별도로 컴파일되기 때문에 `:feature:home`을 컴파일하는 시점에는 `:core:model`의 클래스가 정말 안정적인지 확신할 근거가 부족해 보수적으로 Unstable 취급될 수 있음. 이를 해결하려면 Compose 컴파일러의 `stabilityConfigurationFile` 옵션으로 특정 클래스를 명시적으로 Stable이라고 선언하는 설정 파일을 프로젝트 전역에 적용해야 함.

```kotlin
// app/build.gradle.kts
composeCompiler {
    stabilityConfigurationFile = rootProject.file("stability_config.conf")
}
```

```text
# stability_config.conf
com.example.core.model.User
com.example.core.model.HomeUiState
```

<br>

## 5. Compiler Metrics 리포트 읽는 법

Compose 컴파일러는 빌드 시 각 Composable 함수와 클래스의 안정성 분석 결과를 리포트 파일로 출력하는 옵션을 제공함.

```kotlin
// app/build.gradle.kts
composeCompiler {
    metricsDestination = layout.buildDirectory.dir("compose_metrics")
    reportsDestination = layout.buildDirectory.dir("compose_metrics")
}
```

빌드 후 `build/compose_metrics/` 경로에 다음과 같은 파일들이 생성됨.

- `*-classes.txt`: 각 클래스가 Stable인지 Unstable인지, 어떤 필드 때문에 그렇게 판정되었는지 상세 근거
- `*-composables.txt`: 각 Composable 함수의 Skippable 여부, Restartable 여부
- `*-module.json`: 모듈 전체의 안정성 통계 요약

`*-classes.txt` 예시:

```text
stable class HomeUiState {
    stable val items: ImmutableList<Item>
    stable val isLoading: Boolean
}

unstable class LegacyConfig {
    unstable var timeout: Int
    <runtime stability> = Unstable
}
```

`*-composables.txt` 예시:

```text
restartable skippable fun HomeScreen(
    stable state: HomeUiState
    stable onItemClick: Function1<String, Unit>
)

restartable scheme("[androidx.compose.ui.UiComposable]") fun HomeItemRow(
    unstable item: LegacyItem
)
```

두 번째 예시의 `HomeItemRow`는 `unstable item: LegacyItem` 때문에 `skippable`이 붙지 않은 것을 볼 수 있는데, 이는 이 함수가 파라미터 값이 동일해도 매번 리컴포지션된다는 뜻임. 리스트의 각 행(row)에 이런 함수가 쓰이고 있다면 스크롤 시 불필요한 리컴포지션이 대량으로 발생하는 원인이 됨.

### CI에서 안정성 리그레션 감지

이 리포트를 CI에 통합해서, PR마다 새로 Unstable로 분류된 클래스가 늘어나지 않았는지 스크립트로 diff 비교하는 방식도 실무에서 사용됨. `*-module.json`의 요약 수치(Stable/Unstable 클래스 개수)를 이전 빌드와 비교하는 간단한 스크립트만으로도 회귀를 조기에 잡을 수 있음.

<br>

## 6. Layout Inspector로 리컴포지션 확인하기

Android Studio의 Layout Inspector는 실행 중인 앱의 Compose 트리를 시각적으로 보여주며, 각 Composable이 몇 번 리컴포지션되었는지(Recomposition count), 몇 번 Skip되었는지(Skip count)를 실시간으로 확인할 수 있음.

### 사용 순서

1. 앱을 디버그 빌드로 기기/에뮬레이터에 실행
2. Android Studio에서 View → Tool Windows → Layout Inspector 열기
3. 대상 프로세스 선택 후 연결
4. 우측 패널에서 "Recomposition Counts" 토글 활성화
5. 화면과 상호작용하면서 각 Composable 노드 옆에 표시되는 숫자(리컴포지션 횟수/스킵 횟수) 관찰

트리에서 특정 노드를 클릭하면 해당 Composable의 소스 코드 위치로 바로 이동할 수 있어, "이 컴포저블이 왜 이렇게 자주 리컴포지션되는지" 코드를 열어 바로 확인하는 흐름이 매끄러움.

### 확인해야 할 신호

- 스크롤이나 단순 클릭 한 번에 리컴포지션 횟수가 화면 전체 노드에서 급격히 증가하는 경우 — 상태가 지나치게 상위(예: 화면 최상단)에 위치해서 하위 트리 전체가 함께 리컴포지션되는 구조적 문제 의심
- 리스트의 각 아이템 행이 스크롤할 때마다 매번 리컴포지션 카운트가 오르는 경우 — 아이템 데이터 클래스의 안정성 문제(5장) 또는 람다 재생성 문제(8장) 의심
- Skip count가 거의 0에 가깝고 Recomposition count만 계속 오르는 Composable — Skipping이 아예 동작하지 않고 있다는 뜻이므로 해당 함수의 파라미터 안정성을 최우선으로 점검

<br>

## 7. Recomposition Counts 오버레이 활용

Layout Inspector 없이도, 디버그 빌드에서 화면 위에 직접 리컴포지션 횟수를 오버레이로 표시하는 방법도 있음. `Modifier`를 확장해서 각 Composable에 리컴포지션 카운터를 붙이는 방식이 대표적임.

```kotlin
@Composable
inline fun Modifier.recompositionHighlighter(): Modifier {
    if (!BuildConfig.DEBUG) return this

    val count = remember { mutableIntStateOf(0) }
    SideEffect { count.intValue++ }

    return this.drawWithCache {
        onDrawWithContent {
            drawContent()
            val text = count.intValue.toString()
            // 실제 구현에서는 Canvas.nativeCanvas로 텍스트를 그려 화면에 오버레이
        }
    }
}
```

```kotlin
Text(
    text = item.title,
    modifier = Modifier.recompositionHighlighter()
)
```

이 방식은 별도 도구 설치 없이 코드 몇 줄로 즉시 시각적 피드백을 받을 수 있다는 장점이 있지만, Layout Inspector만큼 정교한 트리 탐색이나 Skip count 구분은 제공하지 않으므로 빠른 육안 확인용으로 쓰고, 정밀 분석은 Layout Inspector나 Compiler Metrics로 넘어가는 것이 합리적.

<br>

## 8. 자주 발생하는 불필요한 리컴포지션 패턴

### 매 리컴포지션마다 새로 생성되는 람다

```kotlin
// 문제 코드: onClick 람다가 HomeScreen이 리컴포지션될 때마다 새 인스턴스로 생성됨
@Composable
fun HomeScreen(items: List<Item>) {
    LazyColumn {
        items(items) { item ->
            ItemRow(
                item = item,
                onClick = { viewModel.onItemClick(item.id) } // 매번 새 람다
            )
        }
    }
}
```

Compose 컴파일러는 캡처하는 값이 모두 Stable이고 람다 자체가 캡처 변수 없이 동일한 형태라면 람다를 자동으로 memoization하지만, `item.id`처럼 리스트 순회 중 매번 바뀌는 값을 캡처하는 람다는 매번 새로 생성됨. 대부분의 경우 이는 실제로는 문제가 되지 않는데(캡처값이 바뀌었으니 새 람다가 맞음), 문제는 이 람다를 받는 `ItemRow`가 람다 타입 파라미터를 Unstable로 취급하는 경우 Skipping이 아예 안 되는 상황이 겹칠 때임.

### 리스트를 매번 새로 생성해서 전달

```kotlin
// 문제 코드: 부모가 리컴포지션될 때마다 filter가 다시 실행되어 새 List 인스턴스 생성
@Composable
fun HomeScreen(allItems: List<Item>, query: String) {
    val filtered = allItems.filter { it.title.contains(query) } // 매 리컴포지션마다 재계산 + 새 리스트
    ItemList(items = filtered)
}
```

```kotlin
// 수정: remember + derivedStateOf로 실제 의존값이 바뀔 때만 재계산
@Composable
fun HomeScreen(allItems: List<Item>, query: String) {
    val filtered by remember(allItems, query) {
        derivedStateOf { allItems.filter { it.title.contains(query) } }
    }
    ItemList(items = filtered)
}
```

### 상태를 지나치게 상위에서 hoisting

상태 호이스팅(state hoisting) 자체는 Compose의 권장 패턴이지만, 화면 전체를 관장하는 최상위 Composable에 모든 상태를 몰아두면 그 중 하나만 바뀌어도 최상위 함수 전체가 리컴포지션 대상이 되어, Skipping이 걸리지 않는 자식들까지 줄줄이 영향을 받을 수 있음. 상태의 스코프를 실제로 그 상태를 사용하는 범위로 최대한 좁혀서 hoisting하는 것이 리컴포지션 범위를 최소화하는 핵심 원칙임.

```kotlin
// 문제: 검색어 입력 상태가 최상위에 있어, 타이핑할 때마다 화면 전체가 리컴포지션 후보가 됨
@Composable
fun HomeScreen() {
    var query by remember { mutableStateOf("") }
    Column {
        SearchBar(query, onQueryChange = { query = it })
        ExpensiveHeader()
        ItemList(items)
    }
}
```

```kotlin
// 수정: 검색 상태와 관련 UI를 별도 Composable로 분리해서 스코프를 좁힘
@Composable
fun HomeScreen() {
    Column {
        SearchSection() // 내부에서 query 상태를 자체적으로 소유
        ExpensiveHeader() // query 변경과 무관하게 Skip됨
        ItemList(items)
    }
}

@Composable
private fun SearchSection() {
    var query by remember { mutableStateOf("") }
    SearchBar(query, onQueryChange = { query = it })
}
```

### Context/ViewModel을 함수 파라미터로 직접 전달

Composable 함수 시그니처에 `Context`나 `ViewModel` 자체를 통째로 파라미터로 넘기는 패턴은, 이 타입들이 Unstable로 취급되기 때문에 해당 함수의 Skipping을 막는 원인이 됨. 필요한 값/콜백만 개별 파라미터로 추출해서 전달하는 것이 Stability와 재사용성 양쪽에서 유리함.

<br>

## 9. remember와 key를 이용한 최적화

### remember의 기본 원리

`remember`는 Composition에 값을 저장해두고, 리컴포지션이 일어나도 같은 Slot에서 값을 재사용하게 해줌. `key`를 지정하지 않으면 Composable이 Composition에서 사라졌다가 다시 생성되지 않는 한 최초 계산값을 계속 재사용함.

```kotlin
val processedList = remember(rawList) {
    rawList.map { heavyTransform(it) } // rawList가 바뀔 때만 재계산
}
```

### key를 잘못 지정해서 매번 재계산되는 경우

```kotlin
// 문제: 매번 새 리스트 인스턴스가 파라미터로 들어오면 List.equals()는 내용이 같아도
// remember의 key 비교는 참조가 아닌 구조적 동등성(equals)을 사용하므로 실제로는 안전하지만,
// rawList 자체가 상위에서 매번 새로 filter()된 결과라면 결국 매번 재계산이 일어남
```

이 경우 근본 원인은 `remember`가 아니라 상위에서 `rawList`를 만드는 로직 자체가 매 리컴포지션마다 새 인스턴스를 만든다는 데 있으므로, 상위 로직도 함께 `remember`/`derivedStateOf`로 감싸야 함(8장 참고).

### LazyColumn에서 key() 지정

```kotlin
LazyColumn {
    items(
        items = uiState.items,
        key = { item -> item.id } // 명시적 key로 아이템 재정렬/삽입 시 불필요한 리컴포지션 방지
    ) { item ->
        ItemRow(item)
    }
}
```

`key`를 지정하지 않으면 리스트 위치(index) 기준으로 Composable을 재사용하기 때문에, 리스트 중간에 아이템이 삽입/삭제될 때 실제로는 바뀌지 않은 아이템들까지 모두 다른 데이터로 오해되어 불필요하게 리컴포지션됨. 안정적인 고유 id를 `key`로 지정하면 Compose가 각 아이템의 동일성을 정확히 추적해서 실제로 바뀐 아이템만 리컴포지션하고, 나머지는 위치가 바뀌어도 재사용함.

<br>

## 10. derivedStateOf로 리컴포지션 범위 줄이기

`derivedStateOf`는 하나 이상의 `State`를 읽어 계산한 파생 값을 새로운 `State`로 만들어주는데, 핵심은 "입력 상태가 바뀌어도 계산 결과가 실제로 달라지지 않으면 이 파생 State를 읽는 쪽은 리컴포지션되지 않는다"는 점임.

```kotlin
// 문제: 스크롤 오프셋이 1px만 바뀌어도 매번 "맨 위로 가기" 버튼의 표시 여부를 다시 계산하고,
// 그 결과를 읽는 Composable도 매 스크롤 프레임마다 리컴포지션됨
val showButton = listState.firstVisibleItemIndex > 0
```

```kotlin
// 수정: firstVisibleItemIndex는 매 프레임 바뀌지만, "0보다 큰가"라는 파생 결과(Boolean)는
// 실제로 0↔1 경계를 넘나들 때만 바뀌므로, derivedStateOf로 감싸면 그 경계를 넘을 때만 리컴포지션됨
val showButton by remember {
    derivedStateOf { listState.firstVisibleItemIndex > 0 }
}
```

이 패턴은 스크롤 위치처럼 "매우 자주 바뀌는 원본 상태"에서 "가끔만 바뀌는 파생 결과"를 뽑아낼 때 특히 효과적이며, 리스트 스크롤 중 발생하는 jank를 줄이는 대표적인 기법으로 꼽힘.

<br>

## 11. Modifier.Node API 이해

Compose 1.4부터 도입된 `Modifier.Node`는 기존 `Modifier.composed { }` 방식보다 낮은 오버헤드로 Modifier의 상태와 동작을 구현할 수 있게 해주는 API임. 기존 `composed { }`는 내부적으로 별도의 Composable 함수처럼 동작해서 자체적인 Composition 오버헤드(리컴포지션 스코프 생성 등)를 가졌지만, `Modifier.Node`는 Composition과 분리된 별도의 객체 트리(`Modifier.Node` 트리)에서 관리되어 이 오버헤드를 상당 부분 제거함.

### 기존 composed {} 방식의 한계

```kotlin
// 기존 방식: composed{}는 내부적으로 Composable 컨텍스트를 새로 열기 때문에 오버헤드가 있음
fun Modifier.fade(visible: Boolean): Modifier = composed {
    val alpha by animateFloatAsState(if (visible) 1f else 0f)
    this.graphicsLayer { this.alpha = alpha }
}
```

`composed { }`로 만든 Modifier는 이 Modifier가 적용된 Composable마다 매번 별도의 Composition 스코프를 새로 만들기 때문에, 리스트처럼 동일 Modifier가 대량으로 적용되는 상황에서 누적 오버헤드가 커짐.

### Modifier.Node 기본 구조

```kotlin
class FadeNode(var visible: Boolean) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        val alpha = if (visible) 1f else 0f
        drawContent()
    }
}

data class FadeElement(val visible: Boolean) : ModifierNodeElement<FadeNode>() {
    override fun create() = FadeNode(visible)
    override fun update(node: FadeNode) {
        node.visible = visible
    }
}

fun Modifier.fade(visible: Boolean): Modifier = this then FadeElement(visible)
```

`ModifierNodeElement`는 `equals()`/`hashCode()`가 자동 생성되는 `data class`로 정의하는 것이 일반적인데, 이렇게 하면 Compose가 이전 프레임과 현재 프레임의 Element를 비교해서 값이 같으면 기존 Node 인스턴스를 그대로 재사용(`update()`만 호출)하고, 다르면 새로 생성(`create()`)하는 최적화를 자동으로 적용함. 즉 Modifier 자체도 Skipping과 유사한 최적화 대상이 됨.

### 여러 인터페이스 조합

`Modifier.Node`는 필요한 동작에 따라 여러 인터페이스를 조합해서 구현함.

- `DrawModifierNode`: 커스텀 드로잉
- `LayoutModifierNode`: 측정/배치 커스터마이징
- `PointerInputModifierNode`: 터치/제스처 처리
- `SemanticsModifierNode`: 접근성 트리 기여
- `GlobalPositionAwareModifierNode`: 전역 좌표 변경 감지

```kotlin
class ClickableNode(var onClick: () -> Unit) : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize
    ) {
        if (pass == PointerEventPass.Main && pointerEvent.type == PointerEventType.Release) {
            onClick()
        }
    }

    override fun onCancelPointerInput() {}
}
```

<br>

## 12. Modifier.Node로 최적화하기

### LazyColumn 아이템에 반복 적용되는 Modifier 최적화

리스트 아이템마다 반복적으로 적용되는 커스텀 Modifier(예: 커스텀 리플, 커스텀 드래그 핸들)를 `composed { }`로 구현했다면, 아이템 개수만큼 Composition 오버헤드가 곱해짐. 이런 Modifier를 `Modifier.Node`로 전환하면 리스트 스크롤 성능이 체감될 정도로 개선되는 경우가 많음. 실제로 Compose 표준 라이브러리 자체도 1.4 이후 `clickable`, `draggable`, `scrollable` 등 자주 쓰이는 Modifier들을 내부적으로 `Modifier.Node` 기반으로 재구현했음.

### update()에서의 변경 감지 최소화

`update()`는 이전 Element와 새 Element의 값이 다를 때만 호출되므로, `update()` 내부에서 무거운 연산을 하더라도 "실제로 값이 바뀐 경우"에만 실행됨이 보장됨. 다만 여러 프로퍼티를 가진 Node라면, `update()` 내부에서 각 프로퍼티가 실제로 바뀌었는지 다시 한 번 세분화해서 확인하고 그에 따라 다른 후속 작업(예: 다시 그리기 요청 vs 다시 레이아웃 요청)을 구분해서 트리거하는 것이 더 세밀한 최적화로 이어짐.

```kotlin
class HighlightNode(var color: Color, var cornerRadius: Dp) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        drawContent()
        drawRoundRect(color = color, cornerRadius = CornerRadius(cornerRadius.toPx()))
    }
}

data class HighlightElement(val color: Color, val cornerRadius: Dp) : ModifierNodeElement<HighlightNode>() {
    override fun create() = HighlightNode(color, cornerRadius)

    override fun update(node: HighlightNode) {
        if (node.color != color) {
            node.color = color
            node.invalidateDraw() // 색상만 바뀌었으면 다시 그리기만 요청
        }
        if (node.cornerRadius != cornerRadius) {
            node.cornerRadius = cornerRadius
            node.invalidateDraw()
        }
    }
}
```

<br>

## 13. SnapshotStateObserver와 RecomposeScope

Compose의 상태 관리는 Snapshot 시스템 위에 구축되어 있음. `mutableStateOf`로 만든 값을 읽으면 현재 실행 중인 Composable의 `RecomposeScope`가 해당 State에 대한 구독자로 `SnapshotStateObserver`에 등록됨. 이후 해당 State가 쓰기(write)되면, Snapshot 시스템이 이 State를 구독 중인 모든 `RecomposeScope`를 무효화(invalidate) 표시하고, 다음 프레임에서 Compose 런타임이 무효화된 스코프만 골라 리컴포지션을 수행함.

이 구조를 이해하면 다음이 명확해짐.

- 상태를 "읽지 않은" Composable은 애초에 구독자로 등록되지 않으므로, 그 상태가 아무리 자주 바뀌어도 영향받지 않음. 따라서 상태를 실제로 사용하는 지점까지 최대한 낮게(deep) 전달해서 그 지점에서만 읽도록 하는 것이 리컴포지션 범위를 좁히는 근본 원리임.
- 하나의 Composable 함수 안에서 여러 상태를 읽으면, 그 함수 전체가 하나의 `RecomposeScope`이므로 그 중 아무 상태 하나만 바뀌어도 함수 전체가 다시 실행됨. 따라서 서로 무관하게 자주 바뀌는 상태들은 별도의 작은 Composable로 분리해서 각각 독립된 `RecomposeScope`를 갖게 하는 것이 유리함.

<br>

## 14. Macrobenchmark와의 연계

Baseline Profile/Macrobenchmark 문서에서 다룬 `FrameTimingMetric`은 리컴포지션 최적화의 효과를 정량적으로 검증하는 데 그대로 활용할 수 있음. 리컴포지션 최적화 전/후로 동일한 스크롤 시나리오를 벤치마크해서 `frameDurationCpuMs`, Jank 발생 프레임 비율이 실제로 개선되었는지 비교하는 흐름이 이상적임.

```kotlin
@Test
fun scrollHomeList_afterRecompositionOptimization() {
    benchmarkRule.measureRepeated(
        packageName = "com.example.app",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        compilationMode = CompilationMode.Partial()
    ) {
        // 최적화 전/후 동일 시나리오로 각각 측정해서 비교
    }
}
```

Compiler Metrics 리포트에서 Unstable로 분류된 클래스를 Stable로 바꾸는 작업, 리스트 아이템의 `key` 지정, `derivedStateOf` 적용 등을 각각 개별 커밋으로 나누어 벤치마크를 반복하면, 어떤 최적화가 실제로 유의미한 효과를 냈는지 구분해서 확인할 수 있음.

<br>

## 15. 실전 최적화 사례

### 사례 1: 검색 리스트에서 타이핑마다 전체 리스트가 리컴포지션

검색어를 한 글자 입력할 때마다 `LazyColumn`의 모든 아이템이 리컴포지션되던 문제를 Layout Inspector로 확인한 결과, 검색어 `State`를 필터링된 리스트를 만드는 최상위 Composable에서 직접 `remember` 없이 매번 `filter()`로 재계산하고 있었고, 그 결과 리스트(매번 새 인스턴스)를 `LazyColumn`에 그대로 전달하고 있었음. `derivedStateOf`로 필터링 로직을 감싸고, 아이템에 `key`를 지정한 뒤로는 실제로 검색 결과에 포함/제외되는 아이템만 리컴포지션되도록 개선됨.

### 사례 2: 리스트 아이템 데이터 클래스에 콜백 객체가 포함되어 매번 Unstable 판정

아이템 데이터 클래스에 `val onClick: () -> Unit` 형태로 콜백을 직접 필드로 포함시켜뒀는데, 이 데이터가 ViewModel에서 매번 새로 매핑되어 내려오다 보니 콜백 필드가 매번 새 람다로 교체되어 전체 데이터 클래스가 사실상 매번 다른 값으로 취급됨. 콜백을 데이터 클래스에서 분리해서 `ItemRow(item: Item, onClick: (String) -> Unit)`처럼 상위에서 한 번만 만든 안정적인 람다를 별도 파라미터로 전달하는 구조로 바꾸면서 해결됨.

### 사례 3: 커스텀 Ripple Modifier를 composed{}로 구현해서 리스트 스크롤이 버벅임

수백 개 아이템이 있는 리스트에서 아이템마다 커스텀 클릭 이펙트를 `Modifier.composed { }`로 구현했더니, 각 아이템마다 별도 Composition 스코프가 생겨 초기 컴포지션 시간과 스크롤 중 재구성 비용이 누적되어 체감 버벅임으로 이어졌음. 이를 `Modifier.Node` 기반(`PointerInputModifierNode` + `DrawModifierNode`)으로 재작성한 뒤 Macrobenchmark의 `FrameTimingMetric`으로 측정한 결과 프레임 드랍 비율이 유의미하게 감소함.

<br>

## 16. 트러블슈팅

### Compiler Metrics 리포트가 생성되지 않는 경우

- `composeCompiler { metricsDestination = ... }` 설정이 적용된 모듈에서 `./gradlew assembleRelease` 등으로 실제 컴파일이 수행되었는지 확인 (Instant Run/캐시된 빌드에서는 리포트가 갱신되지 않을 수 있음)
- Compose Compiler Gradle 플러그인 버전과 Kotlin 버전 호환성 확인

### Layout Inspector에서 Recomposition Counts가 표시되지 않는 경우

- 릴리즈 빌드나 `R8` 축소가 적용된 빌드에서는 Composable 관련 메타데이터가 제거되어 카운트가 정상적으로 표시되지 않을 수 있음. 반드시 디버그 빌드에서 확인
- Layout Inspector의 "Recomposition Counts" 토글이 활성화되어 있는지, 그리고 대상 앱이 실제로 인터랙션 중인지 확인 (정적 상태에서는 카운트가 갱신되지 않음)

### @Stable을 붙였는데도 계속 리컴포지션되는 경우

- `@Stable`/`@Immutable`은 컴파일러에게 "이 타입은 안정적"이라고 알려주는 것일 뿐, 실제로 내부 구현이 `var`로 값이 바뀌고 있다면 계약 위반으로 Compose가 변경 자체를 감지하지 못해 화면이 아예 갱신되지 않는(리컴포지션이 안 일어나는) 반대 방향의 버그가 될 수 있음. 두 증상(과도한 리컴포지션 vs 리컴포지션 누락)을 헷갈리지 않고 Compiler Metrics로 실제 안정성 판정 근거를 다시 확인해야 함
- 멀티모듈 환경이라면 `stabilityConfigurationFile` 설정 없이는 외부 모듈 클래스가 항상 Unstable로 취급된다는 점(4장) 재확인

<br>

## 17. 정리

- 리컴포지션은 Compose의 정상 동작 방식이며, 문제는 "불필요하게 반복되는" 리컴포지션이 스크롤/애니메이션 성능에 영향을 줄 때임
- Compose 컴파일러는 각 타입을 Stable/Immutable/Unstable로 분류하며, 파라미터 중 하나라도 Unstable이면 해당 Composable은 Skipping 대상에서 제외되어 부모가 리컴포지션될 때마다 무조건 함께 실행됨
- 표준 컬렉션(`List`, `Map` 등)은 기본적으로 Unstable이므로 `kotlinx.collections.immutable`의 `ImmutableList` 등으로 대체하거나, 멀티모듈 환경에서는 `stabilityConfigurationFile`로 안정성을 명시해야 함
- Compiler Metrics 리포트(`*-classes.txt`, `*-composables.txt`)로 어떤 클래스/함수가 Unstable/Non-skippable인지 정확히 확인할 수 있으며, CI에서 회귀 감지에도 활용 가능
- Layout Inspector의 Recomposition Counts로 실제 실행 중인 앱에서 어떤 노드가 얼마나 리컴포지션/스킵되는지 실시간 확인 가능
- 상태를 실제로 읽는 지점까지 최대한 낮게(deep) hoisting하고, 서로 무관한 상태는 별도 작은 Composable로 분리해서 각각 독립된 RecomposeScope를 갖게 하는 것이 리컴포지션 범위를 좁히는 근본 원리
- `derivedStateOf`는 자주 바뀌는 원본 상태에서 가끔만 바뀌는 파생 결과를 뽑아내 리컴포지션 빈도를 줄이는 대표적 기법이며, `LazyColumn`의 `key` 지정은 리스트 아이템의 불필요한 재사용/재계산을 방지함
- `Modifier.Node`는 `composed { }` 대비 Composition 오버헤드 없이 Modifier 상태/동작을 구현할 수 있는 API로, 리스트 아이템마다 반복 적용되는 커스텀 Modifier일수록 전환 효과가 큼
- 리컴포지션 최적화의 실제 효과는 Macrobenchmark의 `FrameTimingMetric`으로 최적화 전/후를 비교 측정해서 정량적으로 검증하는 것이 이상적
