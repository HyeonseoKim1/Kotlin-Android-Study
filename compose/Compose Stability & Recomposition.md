# Compose Stability & Recomposition

1. 왜 이 주제를 알아야 하는가?
2. Recomposition이란?
3. Compose는 언제 다시 그리는가?
4. State 읽기(Read)와 Recomposition의 관계
5. Skipping이란?
6. Stable, Immutable, Unstable의 차이
7. @Stable
8. @Immutable
9. Strong Skipping Mode
10. remember와 Stability
11. derivedStateOf와 Recomposition 최소화
12. 불필요한 Recomposition이 발생하는 대표적인 사례
13. Stability Report 보는 방법
14. Compose 성능 최적화 팁
15. 실무 예제
16. 자주 하는 실수
17. 핵심 정리

<br>

## 1. 왜 이 주제를 알아야 하는가?

Compose는 선언형 UI 프레임워크라 "무엇을 그릴지"만 기술하면 되지만, 그 이면에서는 컴파일러와 런타임이 "언제 다시 그릴지"를 자동으로 판단한다. 이 판단이 틀어지면 화면은 정상으로 보여도 내부적으로는 필요 없는 Composable이 계속 재실행되어 프레임 드랍, 배터리 소모, 스크롤 버벅임으로 이어진다. 즉 Stability는 문법이 아니라 "Compose 컴파일러가 내 코드를 얼마나 똑똑하게 최적화할 수 있는가"를 결정하는 성능 이슈다. MVI처럼 State가 자주 흐르는 구조에서는 특히 영향이 크다.

## 2. Recomposition이란?

Recomposition은 State 변경에 반응해 해당 State를 읽고 있는 Composable 함수를 다시 실행하는 과정이다. 중요한 점은 "화면 전체를 다시 그리는 것"이 아니라 "변경된 State를 읽는 부분만" 다시 실행한다는 것이다. Compose는 함수 단위(정확히는 Composable의 group 단위)로 재실행 범위를 계산하기 때문에, 하나의 큰 Composable보다 작은 단위로 쪼갠 Composable이 Recomposition 범위를 좁히는 데 유리하다.

## 3. Compose는 언제 다시 그리는가?

Recomposition은 다음 조건이 만족될 때 트리거된다.

- `State<T>`(예: `mutableStateOf`, `MutableState`)의 값이 변경될 때
- 그 State를 실제로 **읽고 있는** Composable 스코프 내부에서만 발생
- State를 읽지 않는 상위/하위 Composable은 영향받지 않음

즉 State 변경 자체가 아니라 "그 State를 읽는 스코프"가 Recomposition의 단위다. 이 때문에 State를 어디서 읽느냐(호이스팅 위치)가 성능에 직접적인 영향을 준다.

## 4. State 읽기(Read)와 Recomposition의 관계

State 읽기는 `remember { mutableStateOf(...) }`의 `.value`에 접근하는 순간 발생한다. 이 읽기가 일어난 Composable 스코프(람다 블록)가 Recomposition의 최소 단위로 기록된다.

```kotlin
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }

    Column {
        Text("정적 텍스트") // count를 읽지 않으므로 재실행 안 됨
        Text("count: $count") // count를 읽으므로 이 줄만 재실행
        Button(onClick = { count++ }) { Text("증가") }
    }
}
```

`Text("count: $count")`만 재구성되고 `Text("정적 텍스트")`는 건너뛴다. 이것이 Compose 최적화의 핵심 원리이며, State 읽기 위치를 최대한 좁게(leaf에 가깝게) 만드는 것이 성능 최적화의 기본기다.

## 5. Skipping이란?

Skipping은 Recomposition이 필요한 상황이 와도, 파라미터 값이 이전과 동일하면 해당 Composable의 재실행 자체를 건너뛰는 최적화다. Compose 컴파일러는 각 Composable에 `Composer`를 통해 이전 파라미터 값을 저장해두고, 새 값과 비교(equals)해서 같으면 실행을 스킵한다.

단, Skipping이 가능하려면 파라미터 타입이 **Stable**로 판단되어야 한다. Unstable 타입이 하나라도 파라미터에 섞여 있으면 그 Composable은 Skip 대상에서 제외되고 매번 재실행된다.

## 6. Stable, Immutable, Unstable의 차이

- **Immutable**: 생성 이후 내부 상태가 절대 바뀌지 않음. `val`만으로 구성되고 모든 프로퍼티 타입도 Immutable이어야 함.
- **Stable**: 값이 바뀔 수는 있지만(`var` 허용), 값이 바뀌면 Compose에게 반드시 알림(`State` 통지)이 가고, `equals()` 비교로 동일 여부를 신뢰할 수 있음. Compose 관점에서는 "바뀌어도 되지만, 바뀌었는지 확실히 알 수 있다"가 핵심.
- **Unstable**: 위 조건을 만족하지 못함. 대표적으로 `List`, `Map`, `Set` 같은 Kotlin 표준 인터페이스 타입(구현체가 mutable할 수 있어 컴파일러가 안전을 보장 못함)이 여기 해당.

Compose 컴파일러는 클래스를 분석해 자동으로 이 세 등급 중 하나로 추론하며, 파라미터가 모두 Stable 이상일 때만 Skipping이 적용된다.

```kotlin
// Unstable로 추론됨 (List는 인터페이스, mutable 구현체 가능성 있음)
data class UiState(val items: List<String>)

// Stable로 추론됨 (kotlinx.collections.immutable 사용)
data class UiState(val items: ImmutableList<String>)
```

## 7. @Stable

`@Stable` 어노테이션은 컴파일러가 스스로 Stable 여부를 추론하지 못하는 타입(주로 인터페이스나 외부 라이브러리 클래스)에 대해 "이 타입은 Stable 계약을 지킨다"고 개발자가 직접 보증하는 수단이다.

```kotlin
@Stable
interface UiEvent {
    val timestamp: Long
}
```

`@Stable`을 선언하면 컴파일러는 이를 신뢰하고 Skipping 최적화를 적용한다. 다만 실제로 계약(값이 바뀌면 Compose에 알림)을 지키지 않으면 UI가 갱신되지 않는 버그로 이어지므로, 남발하지 않고 정말 보증 가능한 경우에만 사용해야 한다.

## 8. @Immutable

`@Immutable`은 `@Stable`보다 더 강한 보증이다. "이 타입의 모든 프로퍼티는 생성 후 절대 변경되지 않는다"고 선언하는 것으로, 컴파일러는 이 타입을 항상 동일하다고 간주해 최대한으로 최적화한다.

```kotlin
@Immutable
data class ChatMessage(
    val id: String,
    val text: String,
    val sender: String
)
```

`data class`가 전부 `val`이고 각 프로퍼티 타입도 Immutable/Stable이면 컴파일러가 자동으로 Immutable로 추론하지만, `List` 같은 Unstable 타입이 껴 있을 때 개발자가 "실제로는 안 바뀐다"는 걸 알고 있다면 `@Immutable`로 직접 강제할 수 있다.

## 9. Strong Skipping Mode

Strong Skipping Mode는 Compose Compiler 1.5.4 이상(Kotlin 2.0 이후 K2 컴파일러와 함께)부터 기본으로 활성화된 최신 최적화 모드다. 기존 방식과의 핵심 차이는 두 가지다.

- **Unstable 파라미터도 Skip 대상에 포함**: 기존에는 파라미터 중 하나라도 Unstable이면 해당 Composable 전체가 Skip 불가였지만, Strong Skipping Mode에서는 Unstable 파라미터를 `equals()`가 아닌 **참조 동일성(instance identity)**으로 비교해서 Skip 여부를 판단한다. 즉 매 Recomposition마다 새로 생성되는 람다나 객체가 아니라면 Skip이 가능해진다.
- **모든 람다를 자동으로 remember 처리**: Composable 내부에서 선언한 람다(`onClick = { ... }` 등)를 개발자가 `remember`로 감싸지 않아도 컴파일러가 자동으로 memoization한다. 이전에는 람다가 매번 새 인스턴스로 생성되어 하위 Composable의 Skip을 깨뜨리는 대표적 원인이었는데, 이 문제가 컴파일러 차원에서 해결됐다.

```kotlin
// Strong Skipping Mode 이전: onClick 람다가 매번 새로 생성 → 하위 Skip 실패 위험
// Strong Skipping Mode 이후: 컴파일러가 자동 remember 처리 → 안전
Button(onClick = { viewModel.onClick() }) { Text("클릭") }
```

결과적으로 실무에서 `remember { }`로 람다를 감싸는 코드를 직접 작성할 필요가 크게 줄었고, Unstable 타입(List 등)을 그대로 파라미터로 넘겨도 어느 정도 자동 방어가 된다. 다만 이는 "안전망"이지 Stability를 신경 쓰지 않아도 된다는 뜻은 아니며, 여전히 데이터 클래스 설계 자체는 Stable하게 유지하는 것이 근본적인 해결책이다.

## 10. remember와 Stability

`remember`는 Recomposition 간에 값을 유지시키는 역할이지 Stability를 부여하는 것이 아니다. 하지만 실무에서는 둘이 자주 엮인다.

```kotlin
// 매 Recomposition마다 새 리스트 생성 → Unstable 취급, 하위 Skip 실패
val items = someState.map { it.toUiModel() }

// remember + key로 값이 실제로 바뀔 때만 재계산 → 참조 동일성 유지
val items = remember(someState) { someState.map { it.toUiModel() } }
```

`remember`로 감싸면 입력 key가 동일한 한 같은 참조를 반환하므로, Strong Skipping Mode의 참조 동일성 비교와 시너지가 나서 Unstable 타입이라도 불필요한 재계산과 재구성을 줄일 수 있다.

## 11. derivedStateOf와 Recomposition 최소화

`derivedStateOf`는 여러 State로부터 파생된 값을 계산할 때, 파생값 자체가 바뀌지 않으면 그 값을 읽는 Composable의 Recomposition을 막아주는 도구다. `remember(key)`와의 차이는, `remember`는 key가 바뀌면 무조건 재계산하지만 `derivedStateOf`는 원본 State가 자주 바뀌어도 "계산 결과값"이 같으면 하위 Recomposition을 스킵시킨다는 점이다.

```kotlin
val listState = rememberLazyListState()

// listState.firstVisibleItemIndex는 스크롤마다 계속 바뀜
// 하지만 "0보다 큰가"라는 파생 결과는 자주 바뀌지 않음
val showScrollToTop by remember {
    derivedStateOf { listState.firstVisibleItemIndex > 0 }
}
```

스크롤 오프셋처럼 원본 값은 매우 자주 바뀌지만 UI 분기에 필요한 파생값은 드물게 바뀌는 상황에서 효과가 크다.

## 12. 불필요한 Recomposition이 발생하는 대표적인 사례

- **람다를 파라미터로 직접 인라인 생성**: Strong Skipping Mode 이전 환경(구버전 컴파일러)에서는 매번 새 인스턴스가 되어 Skip 실패
- **Unstable 컬렉션을 그대로 State로 사용**: `List<T>` 등 표준 컬렉션 인터페이스는 기본적으로 Unstable
- **ViewModel의 State 클래스에 `var`나 Unstable 필드 혼용**: 하나만 Unstable이어도 전체가 영향받음
- **최상위 Composable에서 State를 너무 넓게 읽는 경우**: Recomposition 스코프가 넓어져 관련 없는 UI까지 재실행
- **CompositionLocal을 통한 과도한 State 전파**: 값이 바뀌면 그 CompositionLocal을 읽는 모든 하위 트리가 영향받음

## 13. Stability Report 보는 방법

Compose 컴파일러는 각 클래스/함수의 Stability 추론 결과를 리포트로 출력할 수 있다. `build.gradle.kts`에 컴파일러 옵션을 추가하면 빌드 시 텍스트 파일로 생성된다.

```kotlin
// build.gradle.kts (module)
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("stability_config.conf")
    )
}
```

빌드 후 `build/compose_compiler/` 경로에 `*-classes.txt`, `*-composables.txt` 파일이 생성되며, 여기서 각 Composable 함수의 파라미터별 `stable`/`unstable` 여부와 Skip 가능 여부를 직접 확인할 수 있다. 외부 라이브러리 타입처럼 소스 수정이 불가능한 경우 `stability_config.conf`에 클래스명을 등록해 강제로 Stable 취급시킬 수도 있다.

## 14. Compose 성능 최적화 팁

- 화면 State는 가능한 한 `ImmutableList`/`ImmutableMap`(kotlinx.collections.immutable) 사용
- State 클래스는 `data class` + 모든 프로퍼티 `val`로 설계
- 큰 Composable을 작은 단위로 쪼개 Recomposition 범위 최소화(State 읽기 위치를 leaf로 내리기)
- 자주 바뀌는 값에서 파생된 조건은 `derivedStateOf`로 감싸기
- 리스트 렌더링 시 `LazyColumn`의 `key` 파라미터를 반드시 지정해 아이템 재사용 극대화
- Kotlin 2.0 / Compose Compiler 최신 버전으로 올려 Strong Skipping Mode 자동 적용받기
- Stability Report로 주기적으로 Unstable 타입을 점검

## 15. 실무 예제

MVI 구조에서 State가 Unstable해서 매번 전체 화면이 재구성되는 상황을 개선하는 흐름이다.

```kotlin
// Before: List → Unstable, 매번 재구성
data class ScreenState(
    val messages: List<ChatMessage>,
    val isLoading: Boolean
)

// After: ImmutableList + @Immutable 데이터 클래스로 Stable 확보
@Immutable
data class ScreenState(
    val messages: ImmutableList<ChatMessage>,
    val isLoading: Boolean
)

@Composable
fun ChatScreen(state: ScreenState, onSend: (String) -> Unit) {
    LazyColumn {
        items(state.messages, key = { it.id }) { message ->
            MessageItem(message) // message가 Stable → 스크롤 시 불필요한 재구성 없음
        }
    }
}
```

`items`에 `key`를 지정하고 `message` 타입을 `@Immutable`로 만들면, 새 메시지가 추가돼도 기존 아이템들은 Skip되고 신규 아이템만 Composition이 실행된다.

## 16. 자주 하는 실수

- `@Stable`/`@Immutable`을 실제 계약을 지키지 않으면서 붙이는 것 — UI가 갱신 안 되는 버그로 이어짐
- Strong Skipping Mode를 믿고 Unstable 컬렉션을 방치하는 것 — 참조 동일성 비교는 "새로 생성되지 않을 때"만 효과가 있음
- `remember` 없이 매번 새 리스트/객체를 만들어 State로 넘기는 것 — Strong Skipping Mode에서도 매번 새 참조면 Skip 실패
- Recomposition이 많다고 무조건 문제라 판단하는 것 — Skip되는 재실행은 비용이 거의 없으므로, Layout/Draw 단계까지 이어지는지가 진짜 성능 지표
- 최적화를 위해 Composable을 과도하게 쪼개 가독성을 해치는 것 — 실제 병목이 확인된 곳부터 적용하는 것이 우선

## 17. 핵심 정리

- Recomposition은 State를 "읽는" 스코프 단위로 발생하며, Skipping은 파라미터가 이전과 같으면 재실행 자체를 생략하는 최적화다.
- Skipping이 동작하려면 파라미터가 Stable(또는 Immutable)이어야 하며, Kotlin 표준 컬렉션 인터페이스는 기본적으로 Unstable로 추론된다.
- `@Stable`/`@Immutable`은 컴파일러가 추론 못하는 타입에 대해 개발자가 안정성을 보증하는 수단이다.
- Strong Skipping Mode(Compose Compiler 1.5.4+)는 Unstable 파라미터도 참조 동일성으로 Skip을 시도하고, 람다를 자동 remember 처리해 이전 세대의 대표적인 함정을 상당 부분 완화했다.
- 그럼에도 State 설계 자체를 Immutable/Stable하게 유지하는 것이 가장 근본적이고 확실한 성능 최적화다.
