# Annotation

## 목차

1. Annotation이란?
2. Annotation을 사용하는 이유
3. 기본 Annotation 문법
4. Annotation을 붙일 수 있는 대상
5. 여러 Annotation 함께 사용하기
6. Annotation에 매개변수 전달하기
7. 메타 Annotation
8. Retention
9. Target
10. Repeatable Annotation
11. Annotation 클래스 만들기
12. Reflection으로 Annotation 읽기
13. Android에서 자주 사용하는 Annotation
14. Compose에서 사용하는 Annotation
15. Java Annotation과의 차이
16. 주의사항
17. 주요 질문
18. 정리

<br>

# 1. Annotation이란?

Annotation(애너테이션)은 코드에 **추가적인 정보를 붙이는 문법**이다.

컴파일러나 라이브러리, 프레임워크가 이 정보를 읽어서 특별한 동작을 수행한다.

쉽게 말하면 "이 코드는 이런 용도로 사용된다."라는 메모를 코드에 붙여주는 것이다.

예를 들어

```kotlin
@Deprecated("Use newFunction() instead")
fun oldFunction() {

}
```

`@Deprecated`는 이 함수는 더 이상 사용하지 않는다는 의미를 컴파일러에게 알려준다.

IDE는 이를 읽고 경고를 보여준다.

<br>

# 2. Annotation을 사용하는 이유

Annotation은 사람이 보기 위한 주석(Comment)이 아니다.

프로그램이 읽는 정보이다.

대표적인 목적

- 컴파일러에게 정보 전달
- 코드 생성
- 런타임 처리
- 라이브러리 설정
- 경고 표시

예를 들어 Room은

```kotlin
@Entity
data class User(
    @PrimaryKey
    val id: Int
)
```

이 Annotation을 보고 SQLite 테이블을 자동 생성한다.

즉, Annotation이 실제 기능을 만드는 것이다.

<br>

# 3. 기본 Annotation 문법

가장 기본 형태는

```kotlin
@Target(AnnotationTarget.CLASS)
annotation class MyAnnotation
```

사용할 때는

```kotlin
@MyAnnotation
class User
```

앞에 `@`를 붙인다.

<br>

# 4. Annotation을 붙일 수 있는 대상

거의 모든 곳에 붙일 수 있다.

- 클래스
- 함수
- 프로퍼티
- 생성자
- 파라미터
- 파일
- Getter
- Setter
- Expression

예를 들면

```kotlin
@MyAnnotation
class User

@MyAnnotation
fun login() {}

@MyAnnotation
val age = 20

class Repository(
    @MyAnnotation
    val api: Api
)
```

<br>

# 5. 여러 Annotation 함께 사용하기

여러 개를 동시에 사용할 수 있다.

```kotlin
@Deprecated("Old API")
@Suppress("UNUSED")
fun test() {

}
```

위에서부터 순서대로 작성한다.

<br>

# 6. Annotation에 매개변수 전달하기

Annotation도 생성자처럼 값을 받을 수 있다.

```kotlin
annotation class Author(
    val name: String
)
```

사용

```kotlin
@Author("Kim")
class User
```

여러 개도 가능하다.

```kotlin
annotation class Version(
    val major: Int,
    val minor: Int
)

@Version(
    major = 1,
    minor = 0
)
class App
```

<br>

# 7. 메타 Annotation

Annotation에도 Annotation을 붙일 수 있다. 이를 메타 Annotation이라고 한다.

대표적으로

- Target
- Retention
- Repeatable
- MustBeDocumented

등이 있다.

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MyAnnotation
```

<br>

# 8. Retention

Annotation이 언제까지 유지되는지 결정한다.

**SOURCE**

소스 코드에만 존재. 컴파일 후 사라진다.

```kotlin
@Retention(AnnotationRetention.SOURCE)
```

대표: `@Suppress`

**BINARY**

class 파일까지 존재. 실행 중에는 없다.

```kotlin
@Retention(AnnotationRetention.BINARY)
```

**RUNTIME**

실행 중에도 존재. Reflection으로 읽을 수 있다.

```kotlin
@Retention(AnnotationRetention.RUNTIME)
```

Room, Hilt 등의 많은 라이브러리에서 사용한다.

<br>

# 9. Target

어디에 붙일 수 있는지 제한한다.

```kotlin
@Target(AnnotationTarget.CLASS)
```

클래스에만 가능

```kotlin
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION
)
```

여러 곳 가능

대표 Target

- CLASS
- FUNCTION
- PROPERTY
- FIELD
- VALUE_PARAMETER
- CONSTRUCTOR
- FILE

<br>

# 10. Repeatable Annotation

같은 Annotation을 여러 번 붙일 수 있다.

```kotlin
@Repeatable
annotation class Tag(
    val value: String
)
```

사용

```kotlin
@Tag("Android")
@Tag("Kotlin")
class Study
```

<br>

# 11. Annotation 클래스 만들기

직접 만드는 것도 매우 쉽다.

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Developer(
    val name: String,
    val level: Int
)
```

사용

```kotlin
@Developer(
    name = "Kim",
    level = 3
)
class LoginViewModel
```

<br>

# 12. Reflection으로 Annotation 읽기

RUNTIME이어야 읽을 수 있다.

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Info(
    val author: String
)

@Info("Kim")
class User
```

읽기

```kotlin
fun main() {

    val annotation =
        User::class.annotations
            .filterIsInstance<Info>()
            .first()

    println(annotation.author)
}
```

출력

```
Kim
```

Reflection이 Annotation 정보를 읽어온 것이다.

<br>

# 13. Android에서 자주 사용하는 Annotation

**Deprecated**

```kotlin
@Deprecated("Use newApi()")
```

**Suppress**

```kotlin
@Suppress("UNCHECKED_CAST")
```

경고 제거

**Keep**

```kotlin
@Keep
```

Proguard가 제거하지 않는다.

**Parcelize**

```kotlin
@Parcelize
```

Parcelable 구현 자동 생성

**Serializable**

```kotlin
@Serializable
```

kotlinx.serialization 지원

**Hilt**

```kotlin
@HiltAndroidApp
@AndroidEntryPoint
@Inject
```

DI 코드 생성

**Room**

```kotlin
@Entity
@Dao
@Insert
@Query
```

데이터베이스 코드 생성

**Retrofit**

```kotlin
@GET
@POST
@Path
@Body
```

HTTP 요청 정의

<br>

# 14. Compose에서 사용하는 Annotation

Compose는 Annotation을 매우 많이 사용한다.

```kotlin
@Composable
fun Greeting() {

}
```

이 Annotation 하나로 컴파일러가 Composable 함수로 변환한다.

또한

- `@Preview` — 미리보기 제공
- `@Stable` — 안정성 정보 제공
- `@Immutable` — 불변 객체임을 알림
- `@ReadOnlyComposable` — 읽기 전용 Composable 표시

Compose Compiler는 이러한 Annotation을 읽어 최적화를 수행한다.

<br>

# 15. Java Annotation과의 차이

Java

```java
@interface Test {

}
```

Kotlin

```kotlin
annotation class Test
```

Kotlin이 훨씬 간결하다.

또한 Target과 Retention을 enum 형태로 제공해 사용하기 쉽다.

<br>

# 16. 주의사항

**Annotation은 실행 코드가 아니다.**

Annotation만 붙인다고 동작하는 것이 아니라 이를 읽는 컴파일러나 라이브러리가 있어야 한다.

**Reflection은 비용이 있다.**

RUNTIME Annotation을 Reflection으로 많이 읽으면 성능이 떨어질 수 있다.

**Retention을 잘 선택해야 한다.**

Reflection으로 읽으려면 반드시 `AnnotationRetention.RUNTIME`이어야 한다.

<br>

# 17. 주요 질문

**Q. Annotation이 무엇인가요?**

코드에 메타데이터를 추가하여 컴파일러나 프레임워크가 이를 기반으로 동작하도록 하는 기능이다.

**Q. Annotation과 주석(Comment)의 차이는?**

주석은 사람이 읽기 위한 설명이고, Annotation은 프로그램이 읽는 메타데이터이다.

**Q. Retention 종류는?**

SOURCE, BINARY, RUNTIME

**Q. Compose에서 가장 많이 사용하는 Annotation은?**

`@Composable`

**Q. Room에서 Annotation을 사용하는 이유는?**

Entity, DAO, Query 등의 정보를 바탕으로 데이터베이스 관련 코드를 자동 생성하기 위해 사용한다.

**Q. Annotation과 Reflection의 관계는?**

RUNTIME Retention으로 유지된 Annotation은 Reflection을 통해 런타임에 조회하고 활용할 수 있다.

<br>

# 18. 정리

- Annotation은 코드에 메타데이터를 추가하는 기능이다.
- `@` 기호를 사용하여 선언한다.
- 컴파일러와 라이브러리가 Annotation을 읽어 특별한 동작을 수행한다.
- Target은 적용 가능한 위치를, Retention은 유지 범위를 결정한다.
- RUNTIME Annotation은 Reflection으로 읽을 수 있다.
- Android에서는 Room, Hilt, Retrofit, Compose 등 대부분의 주요 라이브러리가 Annotation을 기반으로 동작한다.
- `@Composable`, `@Entity`, `@Inject`, `@GET` 등은 대표적인 Annotation 활용 사례이다.
