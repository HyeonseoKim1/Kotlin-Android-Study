# SavedStateHandle

## 21. SavedStateHandle 내부 구현

지금까지는 SavedStateHandle을 사용하는 방법을 살펴봤다.

이번에는 Android 내부에서 어떻게 동작하는지 알아보자.

많은 개발자는

```kotlin
savedStateHandle["query"] = "Compose"
```

한 줄만 사용하지만 실제 내부에서는 여러 객체가 함께 동작한다.

구조를 단순화하면 다음과 같다.

```
Activity / NavBackStackEntry

↓

SavedStateRegistryOwner

↓

SavedStateRegistry

↓

SavedStateHandleController

↓

SavedStateHandle

↓

ViewModel
```

각 객체의 역할은 다음과 같다.

| 객체 | 역할 |
|------|------|
| SavedStateRegistry | Bundle 저장/복원 |
| SavedStateHandleController | SavedStateHandle과 Registry 연결 |
| SavedStateHandle | Key-Value 저장소 |
| ViewModel | UI 상태 관리 |

SavedStateHandle은 혼자 동작하는 객체가 아니라 SavedStateRegistry와 함께 동작한다.

<br>

## 22. SavedStateRegistry란?

SavedStateRegistry는 AndroidX Lifecycle 라이브러리에서 제공하는 상태 저장 시스템이다.

예전에는

```kotlin
onSaveInstanceState(Bundle)
```

를 직접 사용했다.

```kotlin
override fun onSaveInstanceState(
    outState: Bundle
) {
    outState.putString("query", query)
}
```

복원도 직접 해야 했다.

```kotlin
query = savedInstanceState?.getString("query")
```

코드가 여러 곳에 흩어지고

ViewModel에서는 사용할 수도 없었다.

이를 해결하기 위해

SavedStateRegistry가 만들어졌다.

즉

```
Bundle

↓

SavedStateRegistry

↓

SavedStateHandle

↓

ViewModel
```

이라는 구조가 생긴 것이다.

<br>

## 23. SavedStateHandleController

SavedStateHandleController는 내부적으로 SavedStateHandle을 SavedStateRegistry에 등록하는 역할을 한다.

동작 순서는 다음과 같다.

```
ViewModel 생성

↓

SavedStateHandle 생성

↓

Controller 생성

↓

Registry 등록

↓

Lifecycle 관찰

↓

onSaveInstanceState()

↓

Bundle 저장
```

개발자는 존재조차 느끼지 못하지만

실제로는 이 객체가 자동으로 연결을 수행한다.

<br>

## 24. CreationExtras와 SavedStateHandle

최근 ViewModel은 CreationExtras를 이용해 생성된다.

예전에는

```kotlin
ViewModelProvider.Factory
```

가 대부분의 역할을 담당했다.

현재는

```
Activity

↓

CreationExtras

↓

SavedStateHandle 생성

↓

ViewModelFactory

↓

ViewModel
```

구조가 사용된다.

CreationExtras 안에는

- Application
- SavedStateRegistryOwner
- ViewModelStoreOwner
- Default Arguments

등이 들어있다.

ViewModelFactory는 이 정보를 이용하여 SavedStateHandle을 생성한다.

<br>

## 25. Hilt에서는 어떻게 주입될까?

많은 사람들이

```kotlin
@Inject
constructor(
    savedStateHandle: SavedStateHandle
)
```

를 보고

> 누가 SavedStateHandle을 생성하는 걸까?

라는 궁금증을 가진다.

실제로 생성하는 것은 Hilt가 아니다.

순서는 다음과 같다.

```
Activity

↓

ViewModelProvider

↓

SavedStateHandle 생성

↓

Hilt Factory

↓

ViewModel 생성
```

즉

Hilt는 이미 만들어진 SavedStateHandle을 생성자에 전달만 해준다.

그래서

```kotlin
@Provides

@Binds
```

등으로 SavedStateHandle을 등록할 필요가 없다.

<br>

## 26. getLiveData()

StateFlow가 나오기 전에는

LiveData를 많이 사용했다.

SavedStateHandle도 LiveData를 지원한다.

```kotlin
val query = savedStateHandle.getLiveData(
    "query",
    ""
)
```

업데이트는 동일하다.

```kotlin
savedStateHandle["query"] = "Compose"
```

그러면

LiveData도 자동으로 변경된다.

현재 Compose에서는

StateFlow 사용이 더 일반적이다.

<br>

## 27. saveable() API

Compose에서는 SavedStateHandle과 함께 saveable() API를 사용할 수도 있다.

예를 들어

```kotlin
var query by savedStateHandle.saveable {
    mutableStateOf("")
}
```

처럼 사용할 수 있다.

이 방식은

Compose State와

SavedStateHandle을 자동으로 연결한다.

값이 변경되면

```
mutableStateOf

↓

SavedStateHandle

↓

Bundle
```

까지 자동으로 저장된다.

Compose와 함께 사용할 때 매우 편리한 기능이다.

<br>

## 28. rememberSaveable과의 차이

Compose에는

```kotlin
rememberSaveable()
```

도 존재한다.

둘 다 상태를 저장한다는 점은 같지만 사용하는 위치가 다르다.

### rememberSaveable

- Composable 내부
- UI 상태 저장
- ViewModel과 무관

```kotlin
var query by rememberSaveable {
    mutableStateOf("")
}
```

<br>

### SavedStateHandle

- ViewModel 내부
- 화면 상태 관리
- Process Death 복원
- Navigation과 연동

```kotlin
savedStateHandle["query"] = query
```

정리하면

UI 내부에서만 사용하는 상태라면

rememberSaveable

여러 Composable이 함께 사용하는 상태라면

SavedStateHandle이 적합하다.

<br>

## 29. 자주 하는 실수

### 실수 1

API 응답 전체 저장

```kotlin
savedStateHandle["users"] = users
```

좋지 않다.

API 응답은 Repository에서 다시 가져오는 것이 좋다.

<br>

### 실수 2

Bitmap 저장

```kotlin
savedStateHandle["bitmap"] = bitmap
```

Bundle 크기가 커질 수 있다.

Bitmap은 저장하지 않는다.

<br>

### 실수 3

Repository 저장

```kotlin
savedStateHandle["repo"] = repository
```

UI 상태만 저장해야 한다.

Repository는 DI로 주입받는다.

<br>

### 실수 4

MutableStateFlow와 중복 관리

```kotlin
private val _query =
    MutableStateFlow("")

savedStateHandle["query"] = ""
```

두 곳에서 같은 상태를 관리하면

동기화 문제가 발생하기 쉽다.

복원이 필요한 상태라면

SavedStateHandle.getStateFlow() 하나만 사용하는 것이 좋다.

<br>

### 실수 5

모든 상태를 저장하려고 하기

SavedStateHandle은 캐시가 아니다.

필요한 UI 상태만 저장해야 한다.

<br>

## 30. Best Practice

실무에서는 다음과 같은 원칙을 많이 사용한다.

### UI 상태만 저장한다.

예를 들어

- 검색어
- 페이지 번호
- 체크 상태
- 선택 탭

등만 저장한다.

<br>

### 데이터는 Repository에서 다시 가져온다.

예를 들어

```kotlin
savedStateHandle["userId"] = 10
```

만 저장한다.

복원 후

```kotlin
repository.getUser(10)
```

을 호출하여 데이터를 다시 조회한다.

전체 User 객체를 저장하지 않는다.

<br>

### Key를 상수로 관리한다.

```kotlin
object SavedStateKey {

    const val QUERY = "query"

    const val TAB = "tab"

    const val FILTER = "filter"
}
```

실수를 줄일 수 있다.

<br>

### getStateFlow()를 적극 활용한다.

Compose에서는

```kotlin
savedStateHandle.getStateFlow()
```

를 사용하는 것이 가장 자연스럽다.

<br>

## 31. 면접에서 자주 나오는 질문

### Q. ViewModel이 있는데 왜 SavedStateHandle이 필요한가?

ViewModel은 Configuration Change는 해결하지만 Process Death는 해결하지 못한다.

SavedStateHandle은 Bundle을 이용해 필요한 UI 상태를 복원한다.

<br>

### Q. 모든 데이터를 SavedStateHandle에 저장해도 되나요?

아니다.

Bundle 크기에 제한이 있으며

UI 상태만 저장하는 것이 원칙이다.

<br>

### Q. Repository를 저장해도 되나요?

안 된다.

Repository는 다시 DI를 통해 주입받아야 한다.

<br>

### Q. rememberSaveable과 차이는?

rememberSaveable은 Composable 내부 상태를 저장한다.

SavedStateHandle은 ViewModel 상태를 저장한다.

<br>

### Q. Navigation Argument는 어디에 저장되나요?

Navigation은 Argument를 SavedStateHandle에 자동으로 저장한다.

ViewModel에서는 바로 읽어 사용할 수 있다.

<br>

## 32. 언제 사용하면 좋을까?

다음과 같은 화면이라면 SavedStateHandle 사용을 추천한다.

- 검색 화면
- 게시글 작성 화면
- 회원가입 화면
- 필터 화면
- 쇼핑 목록 화면
- 탭 화면
- 상세 화면(id 전달)
- 페이지네이션 화면

반대로 다음과 같은 경우에는 사용할 필요가 거의 없다.

- Room 데이터
- API 응답
- 이미지
- 캐시
- Repository
- UseCase

<br>

## 33. 정리

SavedStateHandle은 **ViewModel에서 Process Death 이후에도 필요한 UI 상태를 복원하기 위한 저장소**이다.

내부적으로는 SavedStateRegistry와 Bundle을 이용하여 값을 저장하고, ViewModel이 새로 생성될 때 이전 상태를 자동으로 복원한다.

Compose에서는 `getStateFlow()`와 함께 사용하는 것이 가장 일반적인 패턴이며, Navigation과도 자연스럽게 연동된다. 또한 Hilt를 사용할 경우 SavedStateHandle은 자동으로 주입되므로 별도의 설정이 필요하지 않다.

실무에서는 **사용자가 다시 입력하거나 선택하기 번거로운 UI 상태만 저장**하고, 실제 데이터는 Repository를 통해 다시 조회하는 것이 가장 권장되는 방식이다.
