# Class

## 목차

1. 클래스(Class)
2. 프로퍼티(Property)
3. 생성자(Constructor)
4. init 블록
5. 멤버 함수(Member Function)
6. 접근 제한자(Access Modifier)
7. 중첩 클래스(Nested Class)와 내부 클래스(Inner Class)

<br>

# 1. 클래스(Class)

클래스는 객체(Object)를 생성하기 위한 설계도이다.

코틀린에서는 `class` 키워드를 사용하여 클래스를 선언한다.

```kotlin
class User
```

객체 생성

```kotlin
val user = User()
```

Java와 달리 `new` 키워드를 사용하지 않는다.

```java
User user = new User();
```

```kotlin
val user = User()
```

<br>

# 2. 프로퍼티(Property)

프로퍼티는 클래스가 가지는 데이터를 의미한다.

코틀린은 필드(Field)와 Getter/Setter를 하나의 개념인 프로퍼티로 제공한다.

```kotlin
class User {
    val name = "철수"
    var age = 20
}
```

사용 예시

```kotlin
val user = User()

println(user.name)
println(user.age)
```

결과

```text
철수
20
```

<br>

## val 프로퍼티

읽기 전용 프로퍼티이다.

```kotlin
class User {
    val name = "철수"
}
```

```kotlin
val user = User()

user.name = "철수"
```

결과

```text
Val cannot be reassigned
```

<br>

## var 프로퍼티

값 변경이 가능한 프로퍼티이다.

```kotlin
class User {
    var age = 20
}
```

```kotlin
val user = User()

user.age = 25

println(user.age)
```

결과

```text
25
```

<br>

# 3. 생성자(Constructor)

객체가 생성될 때 초기값을 전달하기 위해 사용한다.

코틀린에서는 주 생성자(Primary Constructor)를 가장 많이 사용한다.

```kotlin
class User(
    val name: String,
    var age: Int
)
```

객체 생성

```kotlin
val user = User(
    name = "철수",
    age = 20
)
```

사용

```kotlin
println(user.name)
println(user.age)
```

결과

```text
철수
20
```

<br>

## 보조 생성자(Secondary Constructor)

추가 생성 로직이 필요한 경우 사용할 수 있다.

```kotlin
class User {

    var name: String

    constructor(name: String) {
        this.name = name
    }
}
```

사용 예시

```kotlin
val user = User("철수")

println(user.name)
```

<br>

## 주 생성자와 보조 생성자 함께 사용

```kotlin
class User(
    val name: String
) {

    constructor(
        name: String,
        age: Int
    ) : this(name) {

        println(age)
    }
}
```

보조 생성자는 반드시 주 생성자를 호출해야 한다.

```kotlin
: this(...)
```

<br>

# 4. init 블록

객체 생성 시 실행되는 초기화 블록이다.

생성자에서 전달받은 값을 검증하거나 초기화 작업을 수행할 때 사용한다.

```kotlin
class User(
    val name: String
) {

    init {
        println("객체 생성")
    }
}
```

객체 생성

```kotlin
val user = User("철수")
```

결과

```text
객체 생성
```

<br>

## 생성자 값 사용

```kotlin
class User(
    val name: String
) {

    init {
        println("이름: $name")
    }
}
```

결과

```text
이름: 철수
```

<br>

## 유효성 검사

```kotlin
class User(
    val age: Int
) {

    init {
        require(age >= 0) {
            "나이는 0 이상이어야 합니다."
        }
    }
}
```

```kotlin
val user = User(-1)
```

결과

```text
IllegalArgumentException
```

<br>

# 5. 멤버 함수(Member Function)

클래스 내부에 정의된 함수를 멤버 함수라고 한다.

```kotlin
class User(
    val name: String
) {

    fun introduce() {
        println("안녕하세요. 저는 $name 입니다.")
    }
}
```

사용

```kotlin
val user = User("철수")

user.introduce()
```

결과

```text
안녕하세요. 저는 철수 입니다.
```

<br>

## 프로퍼티 사용

```kotlin
class User(
    val name: String,
    val age: Int
) {

    fun printInfo() {
        println("$name / $age")
    }
}
```

```kotlin
val user = User(
    name = "철수",
    age = 20
)

user.printInfo()
```

결과

```text
철수 / 20
```

<br>

# 6. 접근 제한자(Access Modifier)

접근 가능한 범위를 제한하기 위한 키워드이다.

| 키워드       | 접근 범위           |
| --------- | --------------- |
| public    | 어디서나 접근 가능      |
| internal  | 같은 모듈 내 접근 가능   |
| protected | 상속 관계에서 접근 가능   |
| private   | 클래스 내부에서만 접근 가능 |

<br>

## public

기본 접근 제한자이다.

```kotlin
class User {
    val name = "철수"
}
```

```kotlin
val user = User()

println(user.name)
```

<br>

## private

클래스 내부에서만 접근 가능하다.

```kotlin
class User {

    private val password = "1234"
}
```

```kotlin
val user = User()

println(user.password)
```

결과

```text
Cannot access 'password'
```

<br>

## internal

같은 모듈 내에서만 접근 가능하다.

```kotlin
internal class User
```

주로 멀티 모듈 프로젝트에서 사용한다.

<br>

## protected

상속받은 클래스에서만 접근 가능하다.

```kotlin
open class Animal {

    protected fun move() {
        println("이동")
    }
}
```

```kotlin
class Dog : Animal() {

    fun run() {
        move()
    }
}
```

<br>

# 7. 중첩 클래스(Nested Class)와 내부 클래스(Inner Class)

클래스 안에 또 다른 클래스를 정의할 수 있다.

<br>

## 중첩 클래스(Nested Class)

기본적으로 외부 클래스 참조를 가지지 않는다.

```kotlin
class Outer {

    class Nested
}
```

객체 생성

```kotlin
val nested = Outer.Nested()
```

<br>

## 내부 클래스(Inner Class)

외부 클래스의 멤버에 접근할 수 있다.

```kotlin
class Outer {

    private val name = "Outer"

    inner class Inner {

        fun printName() {
            println(name)
        }
    }
}
```

사용

```kotlin
val outer = Outer()
val inner = outer.Inner()

inner.printName()
```

결과

```text
Outer
```

<br>

# 정리

* 클래스는 객체를 생성하기 위한 설계도이다.
* 프로퍼티는 클래스가 보유한 데이터이다.
* 생성자를 통해 객체 생성 시 값을 전달할 수 있다.
* `init` 블록은 객체 생성 시 실행된다.
* 멤버 함수는 객체의 동작을 정의한다.
* 접근 제한자를 통해 접근 범위를 제어할 수 있다.
* 중첩 클래스는 외부 참조가 없고, 내부 클래스는 외부 클래스에 접근할 수 있다.

