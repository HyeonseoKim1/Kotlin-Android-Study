# DataStore

## 목차

1. DataStore란?
2. SharedPreferences와 차이점
3. DataStore 종류
4. 의존성 추가
5. DataStore 생성하기
6. 데이터 저장하기
7. 데이터 읽기
8. ViewModel에서 사용하기
9. Compose에서 사용하기
10. 실전에서 많이 사용하는 패턴
11. 주의사항
12. 언제 사용하면 좋을까?
13. 정리

<br>

## 1. DataStore란?

DataStore는 안드로이드에서 **간단한 데이터를 저장하기 위한 Jetpack 라이브러리**이다.

기존에는 대부분 SharedPreferences를 사용했지만 여러 문제점이 있어 현재는 DataStore 사용이 권장된다.

대표적으로 저장하는 데이터는 다음과 같다.

- 자동 로그인 여부
- 다크모드 설정
- 언어 설정
- 알림 ON/OFF
- 온보딩 완료 여부
- 마지막 로그인 정보

앱을 종료했다가 다시 실행해도 값이 그대로 유지된다.

<br>

## 2. SharedPreferences와 차이점

| SharedPreferences | DataStore |
|-------------------|-----------|
| 동기 방식 | 비동기(Coroutine) |
| 메인 스레드에서 실행 가능 | 백그라운드에서 안전하게 실행 |
| 타입 안정성 낮음 | Flow 기반으로 안전하게 사용 |
| 예외 처리 어려움 | IOException 처리 가능 |
| 최신 권장 아님 | Jetpack 권장 방식 |

SharedPreferences는 메인 스레드에서 접근하면 ANR 위험이 있다.

DataStore는 Coroutine 기반이라 안전하게 사용할 수 있다.

<br>

## 3. DataStore 종류

### Preferences DataStore

가장 많이 사용하는 방식이다.

Key-Value 형태로 저장한다.

```kotlin
"isLogin" -> true
"userName" -> "Android"
"theme" -> "Dark"
```

설정값 저장에 적합하다.

<br>

### Proto DataStore

객체 자체를 저장할 수 있다.

```kotlin
User(
    name = "Android",
    age = 20
)
```

Protocol Buffers를 사용하기 때문에 설정이 조금 복잡하다.

일반적인 앱에서는 Preferences DataStore만으로 충분한 경우가 많다.

<br>

## 4. 의존성 추가

```kotlin
implementation("androidx.datastore:datastore-preferences:1.1.1")
```

<br>

## 5. DataStore 생성하기

보통 Context의 Extension Property로 만든다.

```kotlin
private val Context.dataStore by preferencesDataStore(
    name = "settings"
)
```

### 왜 Extension Property를 사용할까?

앱 전체에서 하나의 DataStore만 사용하기 위해서이다.

DataStore는 Singleton처럼 사용하는 것이 권장된다.

<br>

Preference Key를 만든다.

```kotlin
private object PreferenceKeys {
    val IsLogin = booleanPreferencesKey("is_login")
    val UserName = stringPreferencesKey("user_name")
}
```

Key를 통해 값을 저장하고 읽는다.

<br>

## 6. 데이터 저장하기

```kotlin
class UserPreference(
    private val context: Context
) {

    suspend fun saveLogin(isLogin: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.IsLogin] = isLogin
        }
    }
}
```

### 코드 설명

```kotlin
context.dataStore.edit
```

데이터를 수정하는 함수이다.

<br>

```kotlin
preferences[PreferenceKeys.IsLogin]
```

Key를 이용해 값을 저장한다.

<br>

```kotlin
suspend
```

DataStore는 Coroutine 기반이므로 suspend 함수에서 호출한다.

<br>

## 7. 데이터 읽기

```kotlin
class UserPreference(
    private val context: Context
) {

    val isLogin: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferenceKeys.IsLogin] ?: false
        }
}
```

### 왜 Flow를 사용할까?

DataStore는 값이 변경될 때마다 자동으로 새로운 값을 전달한다.

예를 들어

```
false

↓

true

↓

false
```

Flow가 계속 변경사항을 전달해준다.

따라서 Compose와 매우 잘 어울린다.

<br>

예외 처리도 함께 해주는 것이 좋다.

```kotlin
val isLogin: Flow<Boolean> =
    context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferenceKeys.IsLogin] ?: false
        }
```

<br>

## 8. ViewModel에서 사용하기

```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreference: UserPreference
) : ViewModel() {

    val isLogin = userPreference.isLogin.asLiveData()

}
```

Flow 그대로 사용해도 되고,

```kotlin
val isLogin = userPreference.isLogin
```

StateFlow로 변환해서 사용할 수도 있다.

```kotlin
val isLogin = userPreference.isLogin
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )
```

Compose에서는 StateFlow를 많이 사용한다.

<br>

## 9. Compose에서 사용하기

```kotlin
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {

    val isLogin by viewModel.isLogin.collectAsState()

    Text(
        text = if (isLogin) "로그인 상태" else "로그아웃 상태"
    )
}
```

DataStore 값이 변경되면 UI도 자동으로 변경된다.

<br>

## 10. 실전에서 많이 사용하는 패턴

### 온보딩 완료 여부

```kotlin
val Onboarding = booleanPreferencesKey("onboarding")
```

```kotlin
suspend fun completeOnboarding() {
    context.dataStore.edit {
        it[PreferenceKeys.Onboarding] = true
    }
}
```

앱 실행 시

```kotlin
if (onboardingCompleted) {
    HomeScreen()
} else {
    OnboardingScreen()
}
```

를 결정하는 데 많이 사용한다.

<br>

### 다크모드 저장

```kotlin
val Theme = stringPreferencesKey("theme")
```

```kotlin
"Light"

"Dark"

"System"
```

사용자가 선택한 테마를 저장할 수 있다.

<br>

### 자동 로그인

```kotlin
val AutoLogin = booleanPreferencesKey("auto_login")
```

앱 실행 시

```kotlin
if (autoLogin) {
    HomeScreen()
} else {
    LoginScreen()
}
```

처럼 사용할 수 있다.

<br>

## 11. 주의사항

### DataStore를 여러 개 생성하지 않는다.

좋지 않은 예

```kotlin
val dataStore1 = ...
val dataStore2 = ...
val dataStore3 = ...
```

하나만 생성해서 공유하는 것이 좋다.

<br>

### 큰 데이터를 저장하지 않는다.

DataStore는 설정값 저장용이다.

다음과 같은 데이터는 적합하지 않다.

- 이미지
- 영상
- 대용량 JSON
- 수천 개의 객체

이런 데이터는 Room이나 파일 저장소를 사용하는 것이 좋다.

<br>

### Main Thread를 막지 않는다.

DataStore는 Coroutine 기반이다.

반드시 Coroutine 안에서 사용한다.

```kotlin
viewModelScope.launch {
    userPreference.saveLogin(true)
}
```

<br>

## 12. 언제 사용하면 좋을까?

사용하기 좋은 경우

- 로그인 여부
- 사용자 설정
- 앱 테마
- 언어 설정
- 토큰 저장
- 온보딩 완료 여부
- 알림 설정

사용하지 않는 것이 좋은 경우

- 게시글 목록
- 채팅 기록
- 이미지
- 파일
- 대용량 데이터

<br>

## 13. 정리

- DataStore는 SharedPreferences를 대체하는 Jetpack 저장소이다.
- Coroutine과 Flow 기반으로 동작한다.
- 설정값 저장에 가장 적합하다.
- 값이 변경되면 Flow를 통해 자동으로 전달된다.
- Compose에서는 collectAsState()와 함께 사용하기 좋다.
- 하나의 DataStore를 앱 전체에서 공유하는 것이 권장된다.
- 대용량 데이터 저장에는 Room이나 파일 저장소를 사용하는 것이 적합하다.
