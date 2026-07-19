# SavedStateHandle

## 10. Navigation과 함께 사용하기

SavedStateHandle은 Navigation과 함께 사용할 때 가장 많이 사용된다.

특히 Jetpack Navigation에서는 목적지(Destination)에 전달한 Argument를 SavedStateHandle에 자동으로 저장해준다.

예를 들어 상세 화면으로 이동한다고 가정해보자.

```kotlin
navController.navigate("detail/100")
```

Navigation Graph는 다음과 같이 정의되어 있다.

```kotlin
composable(
    route = "detail/{id}",
    arguments = listOf(
        navArgument("id") {
            type = NavType.IntType
        }
    )
)
```

ViewModel에서는 별도의 전달 코드 없이 바로 사용할 수 있다.

```kotlin
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val id: Int = checkNotNull(
        savedStateHandle["id"]
    )
}
```

여기서

```kotlin
savedStateHandle["id"]
```

는 Navigation이 자동으로 넣어준 값이다.

개발자가 Bundle을 직접 다룰 필요가 없다.

<br>

### Navigation 내부 흐름

```
navigate("detail/100")

↓

NavController

↓

BackStackEntry 생성

↓

Arguments 저장

↓

SavedStateHandle 생성

↓

ViewModel 생성

↓

savedStateHandle["id"]
```

따라서 화면에 필요한 Argument는 대부분 SavedStateHandle을 통해 가져오는 것이 일반적이다.

<br>

## 11. Navigation Compose Typed Destination과 함께 사용하기

Navigation Compose의 Typed Destination을 사용하는 경우에도 내부적으로 SavedStateHandle이 활용된다.

예를 들어

```kotlin
@Serializable
data class DetailRoute(
    val id: Long
)
```

화면 이동

```kotlin
navController.navigate(
    DetailRoute(100)
)
```

ViewModel에서는

```kotlin
savedStateHandle.toRoute<DetailRoute>()
```

처럼 사용할 수 있다.

```kotlin
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val route =
        savedStateHandle.toRoute<DetailRoute>()

    val id = route.id
}
```

Bundle을 직접 꺼내는 코드가 사라지고

Route 객체를 그대로 사용할 수 있다.

최근 Compose에서는 이 방식을 많이 사용한다.

<br>

## 12. getStateFlow()

SavedStateHandle의 가장 유용한 기능 중 하나이다.

단순히 값을 저장하는 것이 아니라

StateFlow를 만들어 준다.

```kotlin
val queryFlow =
    savedStateHandle.getStateFlow(
        key = "query",
        initialValue = ""
    )
```

반환 타입은

```kotlin
StateFlow<String>
```

이다.

따라서 Compose에서는 바로 collect하여 사용할 수 있다.

```kotlin
val query by
    viewModel.queryFlow.collectAsStateWithLifecycle()
```

별도의 MutableStateFlow를 만들 필요가 없다.

<br>

### 값 변경

StateFlow는 읽기 전용이다.

따라서 값을 변경할 때는

```kotlin
savedStateHandle["query"] = "Compose"
```

처럼 저장하면 된다.

그러면

```
savedStateHandle

↓

StateFlow 자동 갱신

↓

Compose Recomposition
```

이 일어난다.

즉

SavedStateHandle과 StateFlow가 자동으로 연결되어 있다.

<br>

## 13. MutableStateFlow와의 차이

많은 사람들이

```kotlin
MutableStateFlow
```

와

```kotlin
SavedStateHandle.getStateFlow()
```

를 헷갈린다.

둘은 목적이 다르다.

### MutableStateFlow

- 메모리에만 존재
- Process Death 복원 불가능
- 자유롭게 값 변경 가능

```kotlin
private val _query =
    MutableStateFlow("")
```

<br>

### SavedStateHandle.getStateFlow()

- Bundle에 저장
- Process Death 복원 가능
- SavedStateHandle을 통해 값 변경

```kotlin
val query =
    savedStateHandle.getStateFlow(
        "query",
        ""
    )
```

실무에서는

검색어

필터

선택 탭

현재 페이지

처럼 복원되어야 하는 상태는

SavedStateHandle을 사용하는 것이 좋다.

<br>

## 14. Compose에서 활용하기

Compose에서는 일반적으로 다음 구조를 사용한다.

```kotlin
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val query =
        savedStateHandle.getStateFlow(
            "query",
            ""
        )

    fun updateQuery(
        text: String
    ) {
        savedStateHandle["query"] = text
    }
}
```

화면에서는

```kotlin
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel()
) {

    val query by
        viewModel.query.collectAsStateWithLifecycle()

    TextField(
        value = query,
        onValueChange = viewModel::updateQuery
    )
}
```

사용자가 입력하면

```
TextField

↓

updateQuery()

↓

SavedStateHandle

↓

StateFlow

↓

Compose

↓

Recomposition
```

이 흐름으로 동작한다.

<br>

## 15. 왜 MutableState 대신 StateFlow를 사용할까?

Compose에는

```kotlin
mutableStateOf()
```

도 존재한다.

그런데 ViewModel에서는

StateFlow를 사용하는 경우가 더 많다.

이유는 다음과 같다.

### Lifecycle을 고려하기 쉽다.

```kotlin
collectAsStateWithLifecycle()
```

를 사용할 수 있다.

<br>

### Flow 연산자를 사용할 수 있다.

```kotlin
debounce()

map()

combine()

filter()
```

등을 사용할 수 있다.

<br>

### Repository와 연결하기 쉽다.

Repository도 Flow를 사용하는 경우가 많다.

```kotlin
Repository

↓

Flow

↓

ViewModel

↓

StateFlow

↓

Compose
```

구조가 자연스럽다.

<br>

## 16. 실무 예제 1 - 검색 화면

검색 화면에서는

입력한 검색어를 유지해야 한다.

```kotlin
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val keyword =
        savedStateHandle.getStateFlow(
            "keyword",
            ""
        )

    fun updateKeyword(
        text: String
    ) {
        savedStateHandle["keyword"] = text
    }
}
```

Process Death 이후에도

검색어가 그대로 유지된다.

<br>

## 17. 실무 예제 2 - 선택된 탭

```kotlin
savedStateHandle["tab"] = 2
```

복원

```kotlin
val currentTab =
    savedStateHandle.getStateFlow(
        "tab",
        0
    )
```

앱이 종료되었다가 다시 실행되어도

사용자가 보고 있던 탭으로 복원된다.

<br>

## 18. 실무 예제 3 - RecyclerView / LazyColumn 위치

스크롤 위치 역시 저장하기 좋은 상태이다.

```kotlin
savedStateHandle["scrollIndex"] = index

savedStateHandle["scrollOffset"] = offset
```

복원

```kotlin
val index =
    savedStateHandle["scrollIndex"] ?: 0

val offset =
    savedStateHandle["scrollOffset"] ?: 0
```

Compose에서는

```kotlin
rememberLazyListState(
    initialFirstVisibleItemIndex = index,
    initialFirstVisibleItemScrollOffset = offset
)
```

와 함께 사용할 수 있다.

다만 `LazyListState` 자체를 저장하는 것이 아니라 **인덱스와 오프셋 같은 최소한의 UI 상태만 저장**하는 것이 좋다.

<br>

## 19. 실무 예제 4 - 필터 조건 유지

쇼핑 앱을 생각해보자.

사용자가

- 가격순
- 최신순
- 브랜드
- 카테고리

를 선택했다고 가정한다.

```kotlin
savedStateHandle["sort"] = "price"

savedStateHandle["category"] = "phone"

savedStateHandle["brand"] = "google"
```

Process Death 이후에도

필터 상태를 그대로 복원할 수 있다.

사용자는 다시 조건을 선택할 필요가 없다.

<br>

## 20. SavedStateHandle을 사용하는 것이 좋은 상태

다음과 같은 UI 상태는 SavedStateHandle을 사용하는 것이 적합하다.

| 상태 | 저장 권장 |
|------|-----------|
| 검색어 | O |
| 선택된 탭 | O |
| 페이지 번호 | O |
| 필터 조건 | O |
| 체크박스 상태 | O |
| 입력 중인 텍스트 | O |
| 스크롤 위치 | O |
| 로그인 여부 | X |
| API 응답 전체 | X |
| Bitmap | X |
| Repository | X |

판단 기준은 간단하다.

> **사용자가 다시 입력하거나 선택하기 번거로운 UI 상태라면 SavedStateHandle을 고려한다.**

반대로, 데이터베이스나 네트워크에서 다시 가져올 수 있는 데이터는 저장하지 않는 것이 좋다.
