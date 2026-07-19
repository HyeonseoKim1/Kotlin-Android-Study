# SavedStateHandle

## 목차

1. SavedStateHandle이란?
2. 왜 필요한가?
3. ViewModel과의 관계
4. Configuration Change와 Process Death
5. SavedStateHandle의 동작 원리
6. 저장 가능한 데이터
7. 기본 사용법
8. Key 관리 방법
9. Process Death 실습
10. 정리

<br>

## 1. SavedStateHandle이란?

`SavedStateHandle`은 ViewModel에서 사용하는 **상태 저장(State Storage)** 객체이다.

ViewModel은 화면 회전(Configuration Change)에는 살아남지만, 앱 프로세스가 종료(Process Death)되면 함께 사라진다.

따라서 사용자가 입력 중이던 값이나 화면의 상태도 모두 잃어버리게 된다.

SavedStateHandle은 이러한 문제를 해결하기 위해 만들어졌다.

쉽게 말하면

> "ViewModel의 중요한 상태를 Bundle에 자동으로 저장해두는 객체"

라고 생각하면 된다.

예를 들어 검색 화면을 생각해보자.

사용자가

```
검색어 : Jetpack Compose
```

를 입력한 상태에서

- 홈 버튼 클릭
- 메모리 부족
- 앱 종료

가 발생했다.

다시 앱을 실행하면

검색어가 그대로 복원되어야 사용자 경험이 좋다.

이럴 때 SavedStateHandle을 사용한다.

<br>

## 2. 왜 필요한가?

많은 사람들이

> ViewModel이 있는데 왜 또 SavedStateHandle이 필요하지?

라는 의문을 가진다.

이유는 ViewModel이 해결하는 문제가 다르기 때문이다.

ViewModel은

- 화면 회전
- 다크모드 변경
- 언어 변경

같은 Configuration Change에서는 살아남는다.

예를 들어

```kotlin
class MainViewModel : ViewModel() {

    var count = 10
}
```

화면을 회전해도

```
count = 10
```

이 유지된다.

하지만 Process Death가 발생하면

새로운 ViewModel이 생성된다.

```
count = 0
```

이 되어버린다.

왜냐하면 ViewModel은 메모리에 존재하는 객체이기 때문이다.

메모리가 사라지면 ViewModel도 함께 사라진다.

SavedStateHandle은

필요한 값만 Bundle에 저장해 두었다가

새로운 ViewModel이 생성될 때 다시 복원해준다.

즉

```
ViewModel

↓

메모리 저장

↓

Process Death

↓

메모리 삭제

↓

새 ViewModel 생성

↓

SavedStateHandle이 Bundle에서 값 복원
```

이 과정을 수행한다.

<br>

## 3. ViewModel과의 관계

보통 ViewModel은 다음과 같이 작성한다.

```kotlin
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel()
```

여기서

```kotlin
SavedStateHandle
```

은 직접 생성하지 않는다.

Android가 자동으로 생성해서 전달한다.

즉

```
Activity 생성

↓

ViewModelProvider

↓

SavedStateHandle 생성

↓

ViewModel 생성
```

순서로 동작한다.

따라서 개발자는

```kotlin
savedStateHandle
```

을 그냥 사용하기만 하면 된다.

<br>

## 4. Configuration Change와 Process Death

이 둘을 헷갈리는 경우가 정말 많다.

### Configuration Change

예를 들어

- 화면 회전
- 폰트 변경
- 다크모드 변경

이 발생하면

Activity는 새로 만들어진다.

하지만 ViewModel은 살아있다.

```
Activity X

↓

Activity O

↓

ViewModel 유지
```

따라서

```kotlin
var count = 5
```

는 그대로 유지된다.

<br>

### Process Death

반면

메모리가 부족하면

Android는 앱 프로세스를 종료한다.

```
앱 실행

↓

메모리 부족

↓

프로세스 종료

↓

사용자 재실행
```

이 경우에는

Activity

ViewModel

Repository

Singleton

모두 새로 생성된다.

즉 메모리에 있던 모든 값이 사라진다.

이때 Bundle에 저장했던 값만 복원된다.

SavedStateHandle이 바로 이 Bundle을 이용한다.

<br>

## 5. SavedStateHandle의 동작 원리

SavedStateHandle은 사실 직접 저장하지 않는다.

실제로는

```
Activity

↓

SavedStateRegistry

↓

Bundle

↓

SavedStateHandle

↓

ViewModel
```

이 구조로 되어 있다.

Activity가

```kotlin
onSaveInstanceState()
```

를 호출하면

SavedStateHandle 안의 값도 Bundle에 저장된다.

다시 Activity가 생성되면

Bundle에서 값을 읽어

새로운 SavedStateHandle이 만들어진다.

즉

SavedStateHandle은 Bundle을 쉽게 사용하도록 만든 래퍼(wrapper)라고 생각하면 된다.

<br>

### 내부 흐름

```
savedStateHandle["keyword"] = "Compose"

↓

SavedStateRegistry

↓

Bundle 저장

↓

Process Death

↓

Bundle 복원

↓

savedStateHandle["keyword"]

↓

Compose
```

개발자는 Bundle을 직접 다룰 필요가 없다.

SavedStateHandle이 자동으로 처리해준다.

<br>

## 6. 저장 가능한 데이터

Bundle에 저장 가능한 타입만 저장할 수 있다.

대표적으로

- Int
- Long
- Float
- Double
- Boolean
- String
- Parcelable
- Serializable
- Enum
- ArrayList

예를 들어

```kotlin
savedStateHandle["count"] = 10

savedStateHandle["name"] = "Android"

savedStateHandle["login"] = true
```

처럼 사용할 수 있다.

읽을 때는

```kotlin
val count = savedStateHandle["count"]
```

또는

```kotlin
val count: Int? = savedStateHandle["count"]
```

를 사용한다.

<br>

### 저장하면 안 되는 것

다음과 같은 객체는 저장하면 안 된다.

- Repository
- Database
- Retrofit
- Bitmap
- Context
- Activity
- Fragment
- CoroutineScope

이들은 Bundle에 저장할 수 없거나

저장 비용이 매우 크다.

SavedStateHandle은

UI 상태만 저장하는 용도로 사용해야 한다.

좋은 예

- 현재 페이지 번호
- 검색어
- 선택된 탭
- 체크박스 상태
- 필터 조건

나쁜 예

- 사용자 목록 전체
- 이미지
- Room Database
- API Response 전체

<br>

## 7. 기본 사용법

### 값 저장

```kotlin
savedStateHandle["query"] = "Compose"
```

Map처럼 사용할 수 있다.

<br>

### 값 읽기

```kotlin
val query = savedStateHandle["query"]
```

또는 타입을 지정할 수도 있다.

```kotlin
val query: String? = savedStateHandle["query"]
```

<br>

### remove()

```kotlin
savedStateHandle.remove<String>("query")
```

저장된 값을 제거한다.

예를 들어

검색을 초기화할 때 사용할 수 있다.

<br>

### contains()

```kotlin
if (savedStateHandle.contains("query")) {

}
```

Key가 존재하는지 확인할 수 있다.

<br>

### keys()

```kotlin
savedStateHandle.keys()
```

현재 저장되어 있는 Key 목록을 가져온다.

디버깅 시 자주 사용된다.

<br>

## 8. Key 관리 방법

Key를 문자열로 직접 작성하면 오타가 발생하기 쉽다.

```kotlin
savedStateHandle["keyword"]
savedStateHandle["keywrod"]
```

이런 실수는 컴파일 에러가 발생하지 않는다.

따라서 object로 관리하는 것이 좋다.

```kotlin
object SavedStateKeys {

    const val QUERY = "query"

    const val PAGE = "page"

    const val FILTER = "filter"
}
```

사용은

```kotlin
savedStateHandle[SavedStateKeys.QUERY] = "Compose"
```

처럼 한다.

실무에서는 대부분 이런 방식을 사용한다.

<br>

## 9. Process Death 실습

예를 들어

```kotlin
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    var keyword = savedStateHandle["keyword"] ?: ""
        private set

    fun updateKeyword(text: String) {
        keyword = text
        savedStateHandle["keyword"] = text
    }
}
```

사용자가

```
Android Compose
```

를 입력했다고 가정하자.

```
keyword

↓

SavedStateHandle 저장

↓

Process Death

↓

새 ViewModel 생성

↓

keyword 복원
```

사용자는

앱이 종료되었는지조차 느끼지 못한다.

이것이 SavedStateHandle의 가장 큰 장점이다.

<br>

# 정리

SavedStateHandle은 ViewModel의 상태를 Process Death 이후에도 복원하기 위해 사용하는 객체이다.

ViewModel 자체를 저장하는 것이 아니라 필요한 UI 상태만 Bundle에 저장한다.

검색어, 페이지 번호, 선택된 탭, 입력값처럼 사용자가 다시 입력하기 번거로운 정보는 SavedStateHandle을 사용하는 것이 좋다.

반면 Repository나 API 응답처럼 크기가 큰 객체는 저장하지 않는 것이 원칙이다.

다음 Part에서는 Navigation과 함께 사용하는 방법, StateFlow 연동, Compose 활용, getStateFlow(), SavedStateRegistry 내부 구현까지 자세히 살펴본다.
