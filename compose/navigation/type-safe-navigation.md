# Type-safe Navigation

## 목차

1. Type-safe Navigation이란?
2. 기존 String Route 방식의 문제점
3. Destination 정의하기
4. NavHost 구성하기
5. 화면 이동과 인자 전달
6. 객체 전달하기
7. Bottom Navigation / Nested Navigation에서 활용
8. 실무에서 사용하는 패턴
9. 정리

## 1. Type-safe Navigation이란?

Jetpack Compose Navigation은 기존에는 문자열(Route)을 이용하여 화면을 이동했다.

```kotlin
navController.navigate("detail/10")
```

이 방식은 간단하지만 문자열을 직접 작성하기 때문에 여러 문제가 발생할 수 있다.

Type-safe Navigation은 **문자열 대신 Kotlin 타입을 이용하여 화면을 이동하는 방식**이다.

예를 들어 화면을 객체로 정의하면 다음과 같이 사용할 수 있다.

```kotlin
@Serializable
data class Detail(
    val id: Long
)
```

그리고 화면 이동은 문자열이 아니라 객체를 전달한다.

```kotlin
navController.navigate(Detail(id = 10))
```

즉,

- 문자열이 아닌 객체를 이용한다.
- 컴파일 시점에 타입을 검사한다.
- 잘못된 Route를 작성할 가능성이 줄어든다.

Compose Navigation 2.8부터 공식적으로 Type-safe Navigation을 지원하면서 앞으로 권장되는 방식이 되었다.

## 2. 기존 String Route 방식의 문제점

기존 Navigation은 Route를 문자열로 관리했다.

```kotlin
const val DETAIL = "detail/{id}"
```

이후 이동할 때도 문자열을 직접 작성한다.

```kotlin
navController.navigate("detail/10")
```

처음에는 단순하지만 프로젝트가 커질수록 문제가 발생한다.

### 오타를 컴파일러가 발견하지 못한다.

```kotlin
navController.navigate("detial/10")
```

컴파일은 성공하지만 실행 중 오류가 발생한다.

### 인자의 개수를 검사하지 않는다.

```kotlin
navController.navigate("detail")
```

필수 인자가 빠져도 컴파일 오류가 발생하지 않는다.

### 타입이 보장되지 않는다.

```kotlin
navController.navigate("detail/abc")
```

id가 숫자여야 하지만 문자열도 전달할 수 있다.

### 리팩터링이 어렵다.

Route 이름을 변경하면 프로젝트 전체 문자열을 찾아 수정해야 한다.

이러한 문제를 해결하기 위해 Type-safe Navigation이 등장하였다.

## 3. Destination 정의하기

Type-safe Navigation에서는 화면을 객체로 정의한다.

먼저 Kotlin Serialization을 사용한다.

```kotlin
plugins {
    kotlin("plugin.serialization")
}
```

Destination은 @Serializable을 붙여 선언한다.

```kotlin
@Serializable
data object Home
```

인자가 있는 화면도 동일하다.

```kotlin
@Serializable
data class Detail(
    val id: Long
)
```

여러 개의 인자도 자연스럽게 표현할 수 있다.

```kotlin
@Serializable
data class Profile(
    val userId: Long,
    val nickname: String
)
```

객체 자체가 Route 역할을 하기 때문에 문자열을 직접 관리할 필요가 없다.

## 4. NavHost 구성하기

NavHost에서도 문자열 대신 타입을 사용한다.

```kotlin
NavHost(
    navController = navController,
    startDestination = Home
) {

    composable<Home> {
        HomeScreen()
    }

    composable<Detail> { backStackEntry ->

        val detail = backStackEntry.toRoute<Detail>()

        DetailScreen(
            id = detail.id
        )
    }
}
```

기존에는 Route 문자열을 비교했지만 이제는 타입을 기준으로 화면을 구성한다.

필요한 인자는 `toRoute()`를 통해 객체 형태로 가져올 수 있다.

## 5. 화면 이동과 인자 전달

### 화면 이동

```kotlin
navController.navigate(Home)
```

인자가 있는 경우

```kotlin
navController.navigate(
    Detail(id = 15)
)
```

### 뒤로가기

```kotlin
navController.popBackStack()
```

### 특정 화면까지 제거

```kotlin
navController.navigate(Home) {
    popUpTo<Home>()
}
```

### 화면 중복 생성 방지

```kotlin
navController.navigate(Home) {
    launchSingleTop = true
}
```

### 상태 복원

```kotlin
navController.navigate(Home) {
    restoreState = true
}
```

Bottom Navigation에서는 보통 다음과 같이 함께 사용한다.

```kotlin
navController.navigate(Home) {

    launchSingleTop = true

    restoreState = true

    popUpTo<Home> {
        saveState = true
    }
}
```

## 6. 객체 전달하기

기본 자료형뿐 아니라 객체도 전달할 수 있다.

```kotlin
@Serializable
data class User(
    val id: Long,
    val name: String
)
```

Destination에서 그대로 사용할 수 있다.

```kotlin
@Serializable
data class UserDetail(
    val user: User
)
```

이동도 객체 하나만 전달하면 된다.

```kotlin
navController.navigate(
    UserDetail(user)
)
```

단, 전달하는 객체는 반드시 `@Serializable`이어야 한다.

또한 화면 간에는 필요한 최소한의 데이터만 전달하는 것이 좋다.

실무에서는 전체 객체보다 ID만 전달하고 다음 화면에서 다시 조회하는 경우가 많다.

## 7. Bottom Navigation / Nested Navigation에서 활용

Type-safe Navigation은 Bottom Navigation과 Nested Navigation에서도 동일하게 사용할 수 있다.

Bottom Navigation에서는 각 탭을 Destination으로 정의한다.

```kotlin
@Serializable
data object Home

@Serializable
data object Search

@Serializable
data object MyPage
```

탭 이동도 객체를 사용한다.

```kotlin
navController.navigate(Home)
```

Nested Navigation 역시 타입 기반으로 정의할 수 있다.

```kotlin
navigation<Auth>(
    startDestination = Login
) {

    composable<Login> {
        LoginScreen()
    }

    composable<Signup> {
        SignupScreen()
    }
}
```

문자열을 직접 관리하지 않아도 되므로 Navigation 구조가 훨씬 명확해진다.

## 8. 실무에서 사용하는 패턴

실무에서는 다음과 같은 원칙을 많이 사용한다.

- Destination은 `sealed interface` 또는 별도 파일로 관리한다.
- 화면 간에는 가능한 ID만 전달한다.
- 필요한 데이터는 ViewModel에서 다시 조회한다.
- Bottom Navigation에서는 `launchSingleTop`, `restoreState`, `saveState`를 함께 사용한다.
- Route 문자열 대신 타입 기반 API를 사용한다.

이러한 구조를 사용하면 리팩터링이 쉬워지고 컴파일 시점에 대부분의 오류를 확인할 수 있다.

## 정리

- Type-safe Navigation은 문자열 대신 Kotlin 타입으로 화면을 이동하는 방식이다.
- Route 오타와 인자 누락을 컴파일 시점에 확인할 수 있다.
- Destination은 `@Serializable` 객체로 정의한다.
- `navigate()`에는 문자열 대신 객체를 전달한다.
- `toRoute()`를 통해 전달된 객체를 가져올 수 있다.
- Bottom Navigation과 Nested Navigation에서도 동일한 방식으로 사용할 수 있다.
- 실무에서는 필요한 최소한의 데이터만 전달하는 것을 권장한다.
