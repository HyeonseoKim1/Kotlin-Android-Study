# SavedStateHandle

## 목차

1. SavedStateHandle이란?
2. 왜 사용하는가?
3. Bundle와의 차이점
4. 이전 화면으로 결과 전달하기
5. SavedStateHandle 사용하기
6. Compose에서 결과 받기
7. 실전 예제 - 주소 선택 화면
8. 실전 예제 - Dialog 결과 전달
9. 주의사항
10. 언제 사용하면 좋을까?
11. 정리

<br>

## 1. SavedStateHandle이란?

`SavedStateHandle`은 Navigation에서 화면 간 데이터를 저장하거나 이전 화면으로 결과를 전달할 때 사용하는 객체이다.

ViewModel에서도 많이 사용하지만, Compose Navigation에서는 화면 간 결과를 주고받을 때 특히 자주 사용된다.

예를 들어 다음과 같은 상황에서 많이 사용된다.

- 주소 선택 후 이전 화면으로 주소 전달
- 프로필 사진 선택 후 이전 화면 갱신
- Dialog에서 확인 버튼을 누른 결과 전달
- BottomSheet에서 선택한 값 전달


<br>

## 2. 왜 사용하는가?

보통 화면을 이동할 때는 `navigate()`와 route parameter를 사용한다.

```kotlin
navController.navigate("detail/10")
```

하지만 이미 열려 있는 이전 화면으로 값을 돌려줘야 하는 경우도 있다.

```
회원가입 화면 - 주소 검색 화면 - 주소 선택 - 회원가입 화면으로 주소 전달
```

이때는 route parameter를 사용할 수 없다.

이럴 때 사용하는 것이 `SavedStateHandle`이다.

이전 BackStackEntry에 값을 저장해두면 이전 화면이 다시 활성화되면서 값을 읽을 수 있다.

<br>

## 3. Bundle와의 차이점

|Bundle|SavedStateHandle|
|---|---|
|Activity, Fragment 중심|Navigation 중심|
|Intent Extras 사용|BackStackEntry 사용|
|결과 전달이 번거로움|결과 전달이 매우 쉬움|
|Compose에서 사용성이 떨어짐|Compose에서 가장 많이 사용|

Compose에서는 Bundle보다 SavedStateHandle을 사용하는 경우가 훨씬 많다.

<br>

## 4. 이전 화면으로 결과 전달하기

예를 들어 주소 선택 화면이 있다고 가정해보자.

```
회원가입 화면 - 주소 검색 화면 - "서울시 강남구" 선택 - 회원가입 화면으로 주소 전달
```

주소 검색 화면에서는 이전 화면의 SavedStateHandle에 값을 넣는다.

```kotlin
navController.previousBackStackEntry
    ?.savedStateHandle
    ?.set("address", "서울시 강남구")

navController.popBackStack()
```

동작 순서는 다음과 같다.

1. 이전 화면 가져오기
2. SavedStateHandle에 값 저장
3. 이전 화면으로 돌아가기

<br>

## 5. SavedStateHandle 사용하기

### 값 저장하기

```kotlin
navController.previousBackStackEntry
    ?.savedStateHandle
    ?.set("result", "선택 완료")
```

- previousBackStackEntry
    - 이전 화면
- savedStateHandle
    - 데이터를 저장하는 공간
- set()
    - 값 저장

<br>

### 값 가져오기

```kotlin
val result =
    navController.currentBackStackEntry
        ?.savedStateHandle
        ?.get<String>("result")
```

저장했던 key로 값을 가져오면 된다.

<br>

### 값 삭제하기

결과를 한 번 사용했다면 삭제하는 것이 좋다.

```kotlin
navController.currentBackStackEntry
    ?.savedStateHandle
    ?.remove<String>("result")
```

삭제하지 않으면 화면이 다시 생성될 때 이전 값이 그대로 남아 있을 수 있다.

<br>

## 6. Compose에서 결과 받기

Compose에서는 StateFlow로 관찰하는 경우가 많다.

```kotlin
val address =
    navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow(
            key = "address",
            initialValue = ""
        )
        ?.collectAsState()
```

이렇게 하면 값이 변경될 때 자동으로 UI가 다시 그려진다.

예시

```kotlin
Text(
    text = address?.value ?: ""
)
```

StateFlow를 사용하면 별도의 콜백 없이도 UI가 자동으로 갱신된다.

<br>

## 7. 실전 예제 - 주소 선택 화면

### 회원가입 화면

```kotlin
Button(
    onClick = {
        navController.navigate("address")
    }
) {
    Text("주소 선택")
}

val address =
    navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow(
            "address",
            ""
        )
        ?.collectAsState()

Text(
    text = address?.value ?: "주소 없음"
)
```

<br>

### 주소 선택 화면

```kotlin
Button(
    onClick = {

        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(
                "address",
                "서울시 강남구"
            )

        navController.popBackStack()
    }
) {
    Text("선택")
}
```

실행 결과

```
회원가입 

주소 없음 

↓

주소 선택 - 서울시 강남구 선택

↓

회원가입

서울시 강남구
```

<br>

## 8. 실전 예제 - Dialog 결과 전달

Dialog에서도 동일하게 사용할 수 있다.

```kotlin
Button(
    onClick = {

        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(
                "confirmed",
                true
            )

        navController.popBackStack()
    }
) {
    Text("확인")
}
```

이전 화면

```kotlin
val confirmed =
    navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow(
            "confirmed",
            false
        )
        ?.collectAsState()
```

Boolean뿐 아니라 String, Int, Boolean, Parcelable, Serializable 등도 저장할 수 있다.

<br>

## 9. 주의사항

### key 이름은 항상 동일해야 한다.

```kotlin
.set("address", value)
```

```kotlin
.get<String>("address")
```

key가 다르면 값을 가져올 수 없다.

<br>

### 사용 후에는 삭제하는 것이 좋다.

```kotlin
savedStateHandle.remove<String>("address")
```

이전 값이 계속 남아 있는 것을 방지할 수 있다.

<br>

### 이전 화면이 없는 경우 null일 수 있다.

```kotlin
navController.previousBackStackEntry
```

현재 첫 화면이라면 null이다.

따라서 안전 호출(`?.`)을 사용하는 것이 좋다.

<br>

## 10. 언제 사용하면 좋을까?

다음과 같은 상황에서 가장 많이 사용한다.

- 주소 선택
- 날짜 선택
- 사진 선택
- 프로필 수정 완료
- Dialog 결과 전달
- BottomSheet 결과 전달
- 필터 선택
- 카테고리 선택
- 로그인 완료 결과 전달

반대로 처음 화면으로 이동하면서 데이터를 전달하는 경우에는 route parameter나 argument를 사용하는 것이 더 적합하다.

<br>

## 11. 정리

- SavedStateHandle은 Navigation에서 화면 간 결과를 전달하기 위한 객체이다.
- 이전 화면으로 값을 전달할 때 가장 많이 사용된다.
- `previousBackStackEntry.savedStateHandle.set()`으로 값을 저장한다.
- `currentBackStackEntry.savedStateHandle.get()` 또는 `getStateFlow()`로 값을 가져올 수 있다.
- Compose에서는 `getStateFlow()`와 `collectAsState()`를 함께 사용하는 패턴이 자주 사용된다.
- 사용이 끝난 값은 `remove()`로 삭제하는 것이 좋다.
- 주소 선택, Dialog, BottomSheet, 사진 선택 등 실무에서 매우 자주 사용하는 기능이다.
