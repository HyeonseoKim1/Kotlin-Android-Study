# ProtoDataStore

Proto DataStore는 Jetpack DataStore의 한 종류로, Protocol Buffers(Protobuf)를 이용하여 타입 안전(Type-safe)하게 데이터를 저장하는 방식이다.

Preferences DataStore는 Key-Value 형태로 데이터를 저장하지만, Proto DataStore는 미리 정의한 데이터 구조를 그대로 저장할 수 있다.

설정 정보가 많아질수록 Proto DataStore의 장점이 더욱 커진다.

<br>

## 목차

1. Proto DataStore란?
2. Preferences DataStore와 차이점
3. 언제 사용하면 좋을까?
4. 동작 방식
5. Proto DataStore 설정하기
6. proto 파일 작성
7. Serializer 구현
8. DataStore 생성
9. 데이터 읽기
10. 데이터 저장하기
11. updateData 사용 이유
12. Migration
13. 장점과 단점
14. 정리

<br>

## 1. Proto DataStore란?

Proto DataStore는 Google의 Protocol Buffers를 이용하여 객체 자체를 저장하는 DataStore이다.

SharedPreferences처럼 문자열 Key를 관리할 필요가 없으며, 컴파일 시점에 타입 검사가 이루어진다.

예를 들어 로그인 설정을 저장한다고 하면

- accessToken
- refreshToken
- autoLogin
- userId

같은 값을 하나의 UserPreferences 객체로 관리할 수 있다.

<br>

## 2. Preferences DataStore와 차이점

|항목|Preferences DataStore|Proto DataStore|
|---|---|---|
|저장 방식|Key-Value|객체|
|타입 안정성|낮음|높음|
|Key 관리|직접 해야 함|필요 없음|
|데이터 구조|단순|복잡한 객체 가능|
|직렬화|자동|Serializer 필요|
|추천 상황|간단한 설정|복잡한 설정|

<br>

## 3. 언제 사용하면 좋을까?

다음과 같은 경우 Proto DataStore가 적합하다.

- 로그인 정보
- 사용자 설정
- 앱 환경설정
- 알림 설정
- 다크모드 설정
- 언어 설정
- 여러 값이 하나의 객체로 묶이는 경우

반대로 Boolean 하나 정도만 저장한다면 Preferences DataStore가 더 간단하다.

<br>

## 4. 동작 방식

```
UserPreferences
        │
        ▼
 Serializer
        │
        ▼
 Protocol Buffers
        │
        ▼
 DataStore File
```

동작 과정은 다음과 같다.

1. 객체를 수정한다.
2. Serializer가 Protobuf 형태로 변환한다.
3. DataStore가 파일에 저장한다.
4. 다시 읽을 때는 Serializer가 객체로 복원한다.

<br>

## 5. Proto DataStore 설정하기

build.gradle

```kotlin
plugins {
    id("com.google.protobuf")
}

dependencies {
    implementation("androidx.datastore:datastore:1.1.1")
    implementation("com.google.protobuf:protobuf-javalite:4.30.2")
}
```

protobuf 플러그인도 추가해야 한다.

```kotlin
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.30.2"
    }

    generateProtoTasks {
        all().forEach {
            it.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}
```

<br>

## 6. proto 파일 작성

app/src/main/proto/user_preferences.proto

```proto
syntax = "proto3";

option java_package = "com.example.datastore";
option java_multiple_files = true;

message UserPreferences {

  bool dark_mode = 1;

  bool auto_login = 2;

  string access_token = 3;

  string refresh_token = 4;
}
```

Protocol Buffers는 번호를 기준으로 데이터를 저장한다.

```proto
bool dark_mode = 1;
```

여기서 중요한 것은

```
1
```

이라는 번호이다.

필드를 삭제하거나 수정할 때 이 번호는 함부로 변경하면 안 된다.

<br>

## 7. Serializer 구현

```kotlin
object UserPreferencesSerializer : Serializer<UserPreferences> {

    override val defaultValue: UserPreferences =
        UserPreferences.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): UserPreferences {
        return UserPreferences.parseFrom(input)
    }

    override suspend fun writeTo(
        t: UserPreferences,
        output: OutputStream
    ) {
        t.writeTo(output)
    }
}
```

Serializer는

- 읽기
- 쓰기
- 기본값

세 가지를 정의한다.

DataStore는 이 Serializer를 이용하여 객체를 저장하고 복원한다.

<br>

## 8. DataStore 생성

```kotlin
val Context.userDataStore by dataStore(
    fileName = "user.pb",
    serializer = UserPreferencesSerializer
)
```

Context 확장 프로퍼티로 만들어 두면 앱 어디서든 사용할 수 있다.

<br>

## 9. 데이터 읽기

```kotlin
val darkMode: Flow<Boolean> =
    context.userDataStore.data
        .map {
            it.darkMode
        }
```

data는 Flow를 반환한다.

설정이 변경되면 새로운 값이 자동으로 전달된다.

Compose에서는 collectAsStateWithLifecycle()와 함께 사용하는 경우가 많다.

<br>

## 10. 데이터 저장하기

```kotlin
suspend fun saveDarkMode(enable: Boolean) {

    context.userDataStore.updateData {

        it.toBuilder()
            .setDarkMode(enable)
            .build()
    }
}
```

builder를 이용하여 새로운 객체를 만들어 반환한다.

Proto 객체는 Immutable이기 때문에 직접 수정할 수 없다.

<br>

## 11. updateData 사용 이유

DataStore에서는 updateData만으로 데이터를 수정한다.

```kotlin
context.userDataStore.updateData {

    it.toBuilder()
        .setAccessToken(token)
        .setAutoLogin(true)
        .build()
}
```

updateData는

- Thread-safe
- Atomic
- Coroutine 지원

이라는 장점이 있다.

여러 스레드에서 동시에 수정하더라도 안전하게 처리된다.

<br>

## 12. Migration

SharedPreferences를 사용하던 앱도 쉽게 이전할 수 있다.

```kotlin
val Context.userDataStore by dataStore(
    fileName = "user.pb",
    serializer = UserPreferencesSerializer,
    produceMigrations = {
        listOf(
            SharedPreferencesMigration(it, "user_pref")
        )
    }
)
```

앱 최초 실행 시 자동으로 데이터를 옮긴다.

<br>

## 13. 장점과 단점

### 장점

- 타입 안정성이 높다.
- 객체 단위 관리가 가능하다.
- Key 문자열을 관리하지 않아도 된다.
- 컴파일 시 오류를 발견할 수 있다.
- 데이터 구조가 커질수록 관리하기 쉽다.

### 단점

- proto 파일을 작성해야 한다.
- Serializer를 구현해야 한다.
- 초기 설정이 Preferences DataStore보다 복잡하다.

<br>
<br>

## 14. 정리

- Proto DataStore는 Protocol Buffers 기반의 DataStore이다.
- 객체 단위로 데이터를 저장할 수 있다.
- 타입 안정성이 높고 컴파일 시 오류를 발견할 수 있다.
- updateData를 이용하여 안전하게 데이터를 수정한다.
- Serializer를 통해 객체를 저장하고 복원한다.
- 로그인 정보나 사용자 설정처럼 여러 값을 함께 관리해야 하는 경우 가장 적합하다.
- Compose에서는 Flow와 collectAsStateWithLifecycle()를 함께 사용하는 경우가 많다.
