# 패키지와 디렉터리 구조, import 디렉티브

## 목차
1. 패키지(Package)란?
2. 디렉터리와 패키지의 관계
3. package 선언 방법
4. import 디렉티브란?
5. import 사용 방법
6. import alias(as)
7. wildcard(*) import
8. Kotlin에서 자동으로 import되는 패키지
9. 같은 이름의 클래스가 있을 때
10. Java와의 차이
11. Android 프로젝트에서의 패키지 구조
12. 좋은 패키지 설계 방법
13. 자주 하는 실수
14. 정리

<br>

## 1. 패키지(Package)란?

패키지는 관련된 클래스, 함수, 인터페이스 등을 그룹으로 묶는 논리적인 공간이다.

패키지를 사용하는 이유는 다음과 같다.

- 이름 충돌 방지
- 코드 관리
- 기능별 분리
- 가독성 향상

예를 들어

```kotlin
package com.example.user
```

안에는

- `User`
- `UserRepository`
- `UserService`

등이 존재할 수 있다.

패키지가 없다면 프로젝트가 커질수록 같은 이름의 클래스가 계속 충돌하게 된다.

<br>

## 2. 디렉터리와 패키지의 관계

Kotlin에서는 패키지와 디렉터리 구조가 반드시 같을 필요는 없다.

예를 들어

```
src/
 └── sample/
      Test.kt
```

```kotlin
package com.example.user
```

파일 위치와 패키지 경로가 달라도 컴파일은 정상적으로 된다.

하지만 대부분의 프로젝트는 관리 편의성을 위해 동일하게 맞춘다.

예시

```
src/main/kotlin
└── com
    └── example
        └── user
            User.kt
```

```kotlin
package com.example.user
```

Android Studio도 이러한 구조를 기본으로 사용한다.

<br>

## 3. package 선언 방법

파일 가장 위에서 선언한다.

```kotlin
package com.example.user
```

그 아래부터 import가 시작된다.

```kotlin
package com.example.user

import kotlin.random.Random

class User
```

package는 파일당 하나만 선언 가능하다.

<br>

## 4. import 디렉티브란?

다른 패키지의 클래스를 현재 파일에서 사용할 수 있도록 가져오는 문법이다.

예를 들어

```kotlin
package com.example.main

import kotlin.random.Random

fun main() {
    println(Random.nextInt())
}
```

`Random` 클래스는 `kotlin.random` 패키지 안에 있기 때문에 import가 필요하다.

<br>

## 5. import 사용 방법

### 클래스 가져오기

```kotlin
import kotlin.random.Random
```

사용

```kotlin
val number = Random.nextInt()
```

<br>

### 함수 가져오기

최상위 함수도 import할 수 있다.

```kotlin
package util

fun printLog() {
    println("Log")
}
```

다른 파일

```kotlin
import util.printLog

fun main() {
    printLog()
}
```

<br>

### 프로퍼티 가져오기

```kotlin
package config

const val VERSION = "1.0"
```

```kotlin
import config.VERSION

println(VERSION)
```

클래스뿐 아니라 함수와 변수도 import 가능하다.

<br>

## 6. import alias(as)

같은 이름이 여러 개 있을 때 별칭을 붙일 수 있다.

예를 들어

```kotlin
import java.util.Date
import java.sql.Date
```

는 불가능하다.

별칭을 사용하면

```kotlin
import java.util.Date
import java.sql.Date as SqlDate
```

사용

```kotlin
val today = Date()
val birthday = SqlDate(0)
```

가독성도 좋아지고 충돌도 해결된다.

<br>

## 7. wildcard(*) import

패키지 전체를 import할 수도 있다.

```kotlin
import kotlin.math.*
```

이후

```kotlin
sqrt(16.0)
abs(-10)
max(1, 5)
```

처럼 사용할 수 있다.

하지만 일반적으로는 필요한 것만 import하는 것이 권장된다.

이유는

- 어떤 클래스를 사용하는지 명확함
- IDE 자동 import 활용 가능
- 가독성 향상

Android Studio 역시 대부분 개별 import를 자동 생성한다.

<br>

## 8. Kotlin에서 자동으로 import되는 패키지

다음 패키지는 import 없이 사용할 수 있다.

```
kotlin.*
```

예를 들어

```kotlin
String
Int
println()
Unit
Nothing
```

등은 자동 import된다.

또한

```
kotlin.collections.*
kotlin.io.*
kotlin.ranges.*
kotlin.text.*
```

등도 기본적으로 포함된다.

그래서

```kotlin
listOf()
mutableListOf()
println()
repeat()
```

등은 별도 import가 필요 없다.

<br>

## 9. 같은 이름의 클래스가 있을 때

예를 들어

```kotlin
com.example.user.User
com.example.admin.User
```

두 클래스가 존재한다고 가정하자.

둘 다 import하면

```kotlin
import com.example.user.User
import com.example.admin.User
```

컴파일 오류가 발생한다.

방법은 두 가지다.

- 별칭 사용

```kotlin
import com.example.user.User
import com.example.admin.User as AdminUser
```

- 전체 이름 사용

```kotlin
val admin = com.example.admin.User()
```

<br>

## 10. Java와의 차이

### 디렉터리 구조

Java는 일반적으로 패키지와 디렉터리를 동일하게 유지한다.

Kotlin은 반드시 같지 않아도 된다.

<br>

### 최상위 함수

Java는

```java
class Util {
    static void print() {}
}
```

처럼 static 메서드를 만들어야 한다.

Kotlin은

```kotlin
fun printLog() {}
```

처럼 파일에 바로 작성할 수 있다.

그리고

```kotlin
import util.printLog
```

로 가져온다.

<br>

### static import

Java

```java
import static java.lang.Math.max;
```

Kotlin

```kotlin
import kotlin.math.max
```

Kotlin은 static이라는 개념 대신 최상위 선언을 import한다.

<br>

## 11. Android 프로젝트에서의 패키지 구조

일반적으로 기능 단위로 패키지를 구성한다.

```
com.example.app
├── data
├── domain
├── ui
├── di
├── model
├── navigation
└── util
```

Compose 프로젝트라면

```
ui
├── home
├── login
├── setting
└── profile
```

처럼 화면 기준으로 나누는 경우가 많다.

이렇게 하면 관련 코드가 한곳에 모여 유지보수가 쉬워진다.

<br>

## 12. 좋은 패키지 설계 방법

기능 중심으로 구성하는 것이 좋다.

예를 들어

```
user
├── UserScreen
├── UserViewModel
└── UserRepository
```

를 함께 두는 방식이다.

반대로

```
activity
viewmodel
repository
model
```

처럼 역할만 기준으로 나누면 프로젝트가 커질수록 관련 파일을 찾기 어려워질 수 있다.

최근 Android 프로젝트는 Feature 기반 구조를 많이 사용한다.

<br>

## 13. 자주 하는 실수

### import를 직접 작성하려고 한다

Android Studio의 자동 import 기능을 활용하는 것이 좋다.

<br>

### wildcard import를 남발한다

필요한 클래스만 import하는 편이 가독성이 좋다.

<br>

### 패키지를 너무 세분화한다

```
user
user.model
user.data
user.data.local
user.data.local.entity
user.data.local.entity.temp
```

처럼 지나치게 깊은 구조는 오히려 관리가 어려워질 수 있다.

<br>

### 패키지를 역할 중심으로만 나눈다

프로젝트가 커질수록 Feature 중심 구조가 유지보수에 유리하다.

<br>

## 14. 정리

- 패키지는 관련 코드를 그룹화하는 논리적인 공간이다.
- Kotlin에서는 패키지와 디렉터리가 반드시 일치할 필요는 없다.
- package는 파일의 가장 위에 선언한다.
- import를 사용하면 다른 패키지의 클래스, 함수, 프로퍼티를 사용할 수 있다.
- as를 사용하면 이름 충돌을 해결할 수 있다.
- `*` import는 가능하지만 필요한 선언만 import하는 것이 권장된다.
- Kotlin은 여러 표준 라이브러리를 자동 import한다.
- Android 프로젝트에서는 기능(Feature) 중심 패키지 구성이 유지보수에 유리하다.
