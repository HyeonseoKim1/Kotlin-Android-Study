# 로그인 / 인증 플로우: 토큰 저장, 자동 로그인, 토큰 갱신(Authenticator), 소셜 로그인

## 목차

1. 개요: 왜 "인증"이 복잡한가
2. 구성 요소
3. 가장 기본적인 구현
4. 상태 설계: 진실의 원천은 SessionManager
5. 토큰 저장: DataStore + Tink
6. 요청에 토큰 붙이기: Interceptor
7. 토큰 갱신: Authenticator
8. 동시 401 처리: 갱신은 한 번만
9. 자동 로그인
10. 소셜 로그인
11. 로그아웃과 세션 만료
12. MVI에서의 상태 관리
13. 네비게이션 연결
14. 테스트
15. 흔한 실수
16. 전체 예제 (복붙용)

<br>

# 1. 개요: 왜 "인증"이 복잡한가

로그인은 화면 하나로 끝나지 않고 앱 전체에 걸친 상태다. 토큰이 어디에 저장되는지, 만료되면 누가 갱신하는지, 갱신에 실패하면 어디로 보내는지가 모두 얽혀 있다.

실제로 문제가 생기는 지점은 대략 네 곳이다.

1. 토큰을 안전하게 저장해야 한다. (평문 SharedPreferences는 안 된다)
2. 앱을 다시 켜도 로그인이 유지되어야 한다. (자동 로그인)
3. 액세스 토큰은 짧게 만료되므로, 요청이 401을 받으면 사용자가 모르게 갱신하고 재시도해야 한다.
4. 소셜 로그인은 SDK마다 방식이 달라서 앱 코드가 SDK에 오염되기 쉽다.

이걸 화면마다, 요청마다 처리하기 시작하면 중복 코드와 경쟁 상태가 생긴다. 해결 원칙은 다음과 같다.

- 로그인 상태의 원천(source of truth)은 `SessionManager` 하나만 둔다.
- 토큰 첨부와 갱신은 화면이 아니라 네트워크 계층(Interceptor / Authenticator)이 맡는다.
- 소셜 SDK는 "우리 서버가 받을 토큰을 얻어오는 역할"까지만 하고, 앱이 쓰는 인증 토큰은 항상 우리 서비스가 발급한 것 하나로 통일한다.

이렇게 하면 데이터 흐름이 단방향이 된다.

```text
로그인 성공    -> 토큰 저장 -> SessionManager.state = Authenticated -> 화면 전환
401 -> 갱신 성공 -> 토큰 교체 -> 원래 요청 재시도 (화면은 모름)
401 -> 갱신 실패 -> 토큰 삭제 -> SessionManager.state = Unauthenticated -> 로그인 화면 전환
```

<br>

# 2. 구성 요소

| 구성 요소 | 역할 |
| --- | --- |
| `TokenStorage` | 토큰 저장/조회/삭제 인터페이스 |
| `DataStoreTokenStorage` | DataStore + Tink(AEAD)로 암호화해서 저장하는 구현체 |
| `SessionManager` | 로그인 상태(`AuthState`)를 들고 있는 싱글톤. 상태의 유일한 원천 |
| `AuthInterceptor` | 모든 요청에 `Authorization: Bearer ...` 첨부 |
| `TokenAuthenticator` | 401을 받으면 refresh token으로 갱신하고 요청 재시도 |
| `AuthApi` | 로그인, 소셜 로그인, 토큰 갱신, 로그아웃 API. 인증 클라이언트와 분리된 "plain" 클라이언트로 호출 |
| `SocialLoginClient` | 소셜 SDK를 감싸는 인터페이스. 결과로 `SocialCredential`을 돌려줌 |
| `AuthRepository` | 로그인/로그아웃 유스케이스. 성공 시 `SessionManager`에 세션 시작을 알림 |

OkHttp 클라이언트를 두 개로 나누는 것이 이 구조의 핵심이다.

| 클라이언트 | 구성 | 용도 |
| --- | --- | --- |
| `plain` | 로깅만 | 로그인, 소셜 로그인, 토큰 갱신, 로그아웃 |
| `auth` | `AuthInterceptor` + `TokenAuthenticator` | 인증이 필요한 모든 일반 API |

<br>

# 3. 가장 기본적인 구현

가장 단순한 형태는 토큰을 메모리에 들고 모든 요청에 붙이는 것이다.

```kotlin
object SimpleAuth {
    @Volatile var accessToken: String? = null
}

class SimpleAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = SimpleAuth.accessToken
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
```

로그인에 성공하면 `SimpleAuth.accessToken = response.accessToken`만 해주면 이후 요청에 토큰이 붙는다.

동작은 하지만 곧 문제가 보인다.

- 앱을 껐다 켜면 토큰이 사라져서 매번 다시 로그인해야 한다.
- 토큰이 만료되면 요청이 계속 401로 실패하고, 이를 화면마다 처리해야 한다.
- 토큰이 어디에도 안전하게 저장되지 않는다.
- "로그인되어 있는가"를 판단하는 기준이 코드 곳곳에 흩어진다.

이후 챕터는 이 기본형을 보완해 나간다.

<br>

# 4. 상태 설계: 진실의 원천은 SessionManager

## 4-1. 로그인 여부를 따로 두면 안 되는 이유

```kotlin
// 안티패턴
// SharedPreferences에 isLoggedIn = true 저장
// 토큰은 따로 저장
// 각 화면의 ViewModel이 자기만의 isLoggedIn 상태를 들고 있음
```

- `isLoggedIn`과 실제 토큰이 어긋난다. (토큰은 지워졌는데 플래그는 true)
- 세션이 만료되었을 때 모든 화면의 상태를 일일이 바꿔야 한다.
- 어느 값이 맞는지 알 수 없다.

## 4-2. 단일 상태로 표현

```kotlin
sealed interface AuthState {
    data object Unknown : AuthState          // 아직 저장소를 읽는 중 (스플래시)
    data object Authenticated : AuthState
    data object Unauthenticated : AuthState
}
```

`Unknown`이 있는 이유는 앱 시작 직후에는 저장된 토큰이 있는지 아직 모르기 때문이다. 이 상태를 구분하지 않으면 로그인 화면이 잠깐 깜빡인 뒤 메인으로 넘어가는 현상이 생긴다.

## 4-3. SessionManager

```kotlin
@Singleton
class SessionManager @Inject constructor(
    private val tokenStorage: TokenStorage,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Unknown)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        scope.launch {
            _state.value =
                if (tokenStorage.get() != null) AuthState.Authenticated
                else AuthState.Unauthenticated
        }
    }

    /** 로그인/소셜 로그인 성공 시 */
    suspend fun login(tokens: Tokens) {
        tokenStorage.save(tokens)
        _state.value = AuthState.Authenticated
    }

    /** 로그아웃, 또는 refresh token 거부로 세션이 끝났을 때 */
    suspend fun logout() {
        tokenStorage.clear()
        _state.value = AuthState.Unauthenticated
    }
}
```

규칙은 하나다. 상태를 바꾸는 곳은 `login()`과 `logout()` 두 곳뿐이고, 화면은 `state`를 읽기만 한다.

<br>

# 5. 토큰 저장: DataStore + Tink

## 5-1. 저장 방식 비교

| 방식 | 평가 |
| --- | --- |
| 평문 SharedPreferences / DataStore | 루팅 기기나 백업에서 그대로 노출. 사용하지 않는다 |
| `EncryptedSharedPreferences` | `androidx.security:security-crypto`가 deprecated 되어 신규 코드에서는 피하는 추세. 최신 상태는 공식 문서에서 확인 |
| DataStore + Tink AEAD | 값을 AES-256-GCM으로 암호화하고, 키는 Android Keystore가 보호. 현재 권장되는 조합 |

저장 대상은 `accessToken`과 `refreshToken`이다. 특히 refresh token은 수명이 길어서 탈취되면 피해가 크므로 반드시 암호화한다.

## 5-2. 모델과 인터페이스

```kotlin
data class Tokens(
    val accessToken: String,
    val refreshToken: String,
)

interface TokenStorage {
    suspend fun get(): Tokens?
    suspend fun save(tokens: Tokens)
    suspend fun clear()
}
```

인터페이스로 분리해 두면 Authenticator 테스트에서 메모리 Fake를 쓸 수 있다. (14장)

## 5-3. 암호화 도우미

```kotlin
@Singleton
class TokenCipher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREF_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://$MASTER_KEY_ALIAS")
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    fun encrypt(plain: String): String =
        Base64.encodeToString(aead.encrypt(plain.toByteArray(), ASSOCIATED_DATA), Base64.NO_WRAP)

    fun decrypt(encoded: String): String =
        String(aead.decrypt(Base64.decode(encoded, Base64.NO_WRAP), ASSOCIATED_DATA))

    private companion object {
        const val KEYSET_NAME = "token_keyset"
        const val KEYSET_PREF_FILE = "token_keyset_pref"
        const val MASTER_KEY_ALIAS = "token_master_key"
        val ASSOCIATED_DATA = "auth_token".toByteArray()
    }
}
```

Tink 버전에 따라 `getPrimitive(Aead::class.java)`가 deprecated 경고를 낼 수 있다. 그 경우 사용 중인 Tink 버전의 문서에 맞는 `RegistryConfiguration` 방식으로 바꾼다.

## 5-4. DataStore 저장소

```kotlin
private val Context.authDataStore by preferencesDataStore(name = "auth")

@Singleton
class DataStoreTokenStorage @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: TokenCipher,
) : TokenStorage {

    private val dataStore = context.authDataStore
    private val mutex = Mutex()

    // 요청마다 디스크를 읽지 않도록 복호화된 값을 메모리에 캐시
    private var cache: Tokens? = null
    private var loaded = false

    override suspend fun get(): Tokens? = mutex.withLock {
        if (!loaded) {
            cache = readFromDisk()
            loaded = true
        }
        cache
    }

    override suspend fun save(tokens: Tokens) = mutex.withLock {
        dataStore.edit {
            it[ACCESS] = cipher.encrypt(tokens.accessToken)
            it[REFRESH] = cipher.encrypt(tokens.refreshToken)
        }
        cache = tokens
        loaded = true
    }

    override suspend fun clear() = mutex.withLock {
        dataStore.edit { it.clear() }
        cache = null
        loaded = true
    }

    private suspend fun readFromDisk(): Tokens? {
        val prefs = dataStore.data.first()
        val access = prefs[ACCESS]
        val refresh = prefs[REFRESH]
        if (access == null || refresh == null) return null

        return try {
            Tokens(cipher.decrypt(access), cipher.decrypt(refresh))
        } catch (e: GeneralSecurityException) {
            // 키가 사라졌거나(복원/기기 변경 등) 데이터가 손상됨 -> 로그아웃 상태로 취급
            dataStore.edit { it.clear() }
            null
        }
    }

    private companion object {
        val ACCESS = stringPreferencesKey("access")
        val REFRESH = stringPreferencesKey("refresh")
    }
}
```

복호화 실패를 예외로 터뜨리지 않고 "토큰 없음"으로 처리하는 것이 중요하다. 이 경우 사용자는 로그인 화면으로 돌아갈 뿐이고, 앱이 크래시하지는 않는다.

## 5-5. 백업 제외

Android 자동 백업이 켜져 있으면 암호화된 토큰 파일이 새 기기로 복원되는데, Keystore 키는 복원되지 않으므로 복호화에 실패한다. 토큰과 키셋 파일은 백업에서 제외한다.

```xml
<!-- AndroidManifest.xml -->
<application
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules" ...>
```

```xml
<!-- res/xml/data_extraction_rules.xml (Android 12+) -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="datastore/auth.preferences_pb" />
        <exclude domain="sharedpref" path="token_keyset_pref.xml" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="datastore/auth.preferences_pb" />
        <exclude domain="sharedpref" path="token_keyset_pref.xml" />
    </device-transfer>
</data-extraction-rules>
```

```xml
<!-- res/xml/backup_rules.xml (Android 11 이하) -->
<full-backup-content>
    <exclude domain="file" path="datastore/auth.preferences_pb" />
    <exclude domain="sharedpref" path="token_keyset_pref.xml" />
</full-backup-content>
```

<br>

# 6. 요청에 토큰 붙이기: Interceptor

```kotlin
class AuthInterceptor @Inject constructor(
    private val tokenStorage: TokenStorage,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = runBlocking { tokenStorage.get() }?.accessToken
            ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        )
    }
}
```

`runBlocking`은 OkHttp의 백그라운드 스레드에서 실행되므로 괜찮다. (메인 스레드에서 호출되지 않는다.) 토큰은 메모리 캐시에서 바로 반환되어 비용이 거의 없다.

## 6-1. 로깅에서 토큰 가리기

```kotlin
HttpLoggingInterceptor().apply {
    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    redactHeader("Authorization")
}
```

로그인/갱신 응답 바디에는 토큰이 그대로 들어 있다. 디버그 빌드에서도 `BODY` 로그가 팀 로그나 크래시 리포트에 남을 수 있으니, 인증 관련 호출은 `HEADERS` 이하로 낮추는 것을 고려한다.

<br>

# 7. 토큰 갱신: Authenticator

## 7-1. Interceptor로 처리하지 않는 이유

응답 코드가 401인지 확인하고 갱신하는 로직을 Interceptor에 넣을 수도 있지만, OkHttp는 이 용도를 위해 `Authenticator`를 따로 제공한다. 서버가 401을 돌려주면 OkHttp가 `authenticate()`를 호출하고, 반환한 `Request`로 재시도한다. `null`을 반환하면 재시도를 포기하고 401 응답을 그대로 호출자에게 전달한다.

```text
요청 -> 401 -> Authenticator.authenticate() -> 새 Request 반환 -> 재전송 -> 200
                                            -> null 반환       -> 401 그대로 전달
```

## 7-2. 구조

```kotlin
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val authApi: AuthApi,               // plain 클라이언트 기반 (Authenticator가 없음)
    private val sessionManager: SessionManager,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // 1. Authorization 없이 나간 요청(로그인 등)은 갱신 대상이 아님
        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?: return null

        // 2. 재시도를 거듭해도 401이면 포기 (무한 루프 방지)
        if (response.responseCount() >= 2) return null

        return synchronized(lock) { refreshIfNeeded(failedToken, response.request) }
    }

    private fun refreshIfNeeded(failedToken: String, request: Request): Request? {
        val current = runBlocking { tokenStorage.get() } ?: return null

        // 3. 기다리는 사이 다른 요청이 이미 갱신했다면 새 토큰으로 재시도만
        if (current.accessToken != failedToken) {
            return request.withBearer(current.accessToken)
        }

        // 4. 실제 갱신
        val refreshed = try {
            runBlocking { authApi.refresh(RefreshRequest(current.refreshToken)) }
        } catch (e: HttpException) {
            when (e.code()) {
                400, 401, 403 -> {
                    // 서버가 refresh token 자체를 거부 = 세션 종료
                    runBlocking { sessionManager.logout() }
                    return null
                }
                else -> throw IOException("토큰 갱신 실패: ${e.code()}", e)   // 5xx 등은 일시적 오류
            }
        }
        // IOException(네트워크 오류)은 잡지 않는다: 호출자에게 그대로 전달되고 세션은 유지됨

        val newTokens = Tokens(refreshed.accessToken, refreshed.refreshToken)
        runBlocking { tokenStorage.save(newTokens) }
        return request.withBearer(newTokens.accessToken)
    }

    private fun Request.withBearer(token: String): Request =
        newBuilder().header("Authorization", "Bearer $token").build()

    private fun Response.responseCount(): Int {
        var count = 1
        var prior = priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
```

## 7-3. 갱신 API는 반드시 별도 클라이언트로

`AuthApi.refresh()`가 `auth` 클라이언트(= Authenticator가 붙은 클라이언트)를 쓰면 갱신 요청이 401을 받을 때 다시 Authenticator가 호출되어 재귀나 데드락이 생긴다. 갱신, 로그인, 로그아웃은 Interceptor/Authenticator가 없는 `plain` 클라이언트로 호출한다.

## 7-4. 실패를 구분해서 처리

| 갱신 결과 | 의미 | 처리 |
| --- | --- | --- |
| 성공 | 새 토큰 발급 | 저장 후 원래 요청 재시도 |
| 400 / 401 / 403 | refresh token이 만료/폐기/불일치 | 세션 종료(`logout`) 후 `null` |
| 5xx, 타임아웃, 네트워크 오류 | 일시적 문제 | `IOException`으로 전달. 로그아웃하지 않음 |

네트워크가 잠깐 끊겼다고 사용자를 로그아웃시키면 안 된다. 세션을 끝내는 것은 "서버가 명확히 거부했을 때"뿐이다.

<br>

# 8. 동시 401 처리: 갱신은 한 번만

화면 진입 시 API를 3개 동시에 호출하는데 액세스 토큰이 만료되어 있다면 세 요청이 거의 동시에 401을 받는다. 보호 장치가 없으면 refresh가 세 번 나간다.

특히 서버가 refresh token rotation(갱신할 때마다 refresh token도 새로 발급하고 이전 것은 폐기)을 쓰면 치명적이다.

```text
요청 A: refresh(r1) -> 성공, 새 refresh r2 발급, r1 폐기
요청 B: refresh(r1) -> 이미 폐기됨 -> 401 -> 세션 종료(로그아웃)   <- 사용자는 아무 잘못이 없는데 로그아웃됨
```

7장의 코드는 이를 두 가지로 막는다.

1. `synchronized(lock)`: 갱신 구간에는 한 번에 하나의 스레드만 들어간다.
2. 락을 얻은 뒤 `current.accessToken != failedToken`을 확인한다. 기다리는 사이 앞선 요청이 이미 갱신했다면 토큰이 달라져 있으므로, 갱신하지 않고 새 토큰으로 재시도만 한다.

```text
요청 A: 락 획득 -> 토큰 비교(같음) -> refresh -> 저장 -> 락 해제
요청 B: 락 대기 ...                  -> 락 획득 -> 토큰 비교(다름) -> 재시도만
요청 C: 락 대기 ...                                  -> 락 획득 -> 토큰 비교(다름) -> 재시도만
```

## 8-1. 만료 직전에 미리 갱신하기 (선택)

액세스 토큰의 `exp`를 알고 있다면, 만료 몇 초 전에 `AuthInterceptor`에서 먼저 갱신해서 401 자체를 줄일 수 있다. 하지만 서버 시간과 기기 시간이 어긋날 수 있으므로 Authenticator는 반드시 남겨둔다. 사전 갱신은 최적화이고, Authenticator가 안전망이다.

<br>

# 9. 자동 로그인

## 9-1. 방식 비교

| 방식 | 동작 | 평가 |
| --- | --- | --- |
| 토큰 존재 여부만 확인 | 저장된 토큰이 있으면 바로 메인 진입 | 가장 빠르고 오프라인에서도 동작. 만료는 첫 API에서 처리 |
| 시작할 때 `/me` 호출로 검증 | 응답을 받은 뒤 진입 | 네트워크가 느리면 스플래시에서 오래 대기 |
| 낙관적 진입 + 백그라운드 검증 | 먼저 진입하고 `/me`를 뒤에서 호출 | 체감 빠르고 계정 정지/탈퇴 같은 상태도 곧바로 반영 |

일반적으로는 토큰 존재 여부로 진입하는 방식이면 충분하다. 토큰이 만료되어 있어도 첫 API 호출이 401을 받으면 Authenticator가 갱신하고, 갱신마저 실패하면 `SessionManager`가 `Unauthenticated`로 바꿔서 로그인 화면으로 자동 전환된다. 자동 로그인을 위한 별도 로직이 필요 없다.

## 9-2. 스플래시에서 Unknown 동안 대기

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // 저장소를 다 읽기 전(Unknown)에는 스플래시 유지
        splash.setKeepOnScreenCondition { sessionManager.state.value is AuthState.Unknown }

        setContent { AppTheme { AppRoot() } }
    }
}
```

저장소 읽기는 메모리 캐시/DataStore 한 번이라 대부분 수십 ms 안에 끝난다. 네트워크 호출을 스플래시 대기 조건에 넣지 않는 것이 중요하다.

## 9-3. 자동 로그인 유지 기간

자동 로그인이 유지되는 기간은 refresh token의 수명으로 결정된다. 서버가 30일 refresh token을 발급한다면 30일간 앱을 안 써도 로그인이 유지된다. 사용할 때마다 refresh token이 연장되는 sliding 방식인지는 서버 정책을 확인한다.

<br>

# 10. 소셜 로그인

## 10-1. 구조: 소셜 토큰은 우리 서버로 넘기기만 한다

```text
[소셜 SDK] --(소셜 토큰)--> [앱] --(소셜 토큰)--> [우리 서버] --(검증)--> [소셜 서버]
                                                    |
앱 <---------(우리 서비스의 access/refresh token)---+
```

- 앱은 소셜 SDK에서 받은 토큰을 서버에 전달하고, 서버가 소셜 제공자와 검증한 뒤 우리 서비스의 토큰을 발급한다.
- 이후 모든 API 인증은 우리 서비스 토큰으로만 한다. 소셜 토큰은 앱에 저장하지 않는다.
- 이렇게 하면 일반 로그인과 소셜 로그인이 같은 `SessionManager.login(tokens)`으로 합쳐지고, 3~9장의 인프라(저장, Interceptor, Authenticator)를 소셜 제공자와 무관하게 재사용할 수 있다.

## 10-2. 공통 인터페이스

```kotlin
enum class SocialProvider { GOOGLE, KAKAO }

data class SocialCredential(
    val provider: SocialProvider,
    val token: String,
    val nonce: String? = null,
)

interface SocialLoginClient {
    /** UI(Activity) Context가 필요하므로 화면에서 호출한다. */
    suspend fun login(context: Context): SocialCredential
}

class SocialLoginCancelledException : Exception()
```

SDK 호출은 Activity Context가 필요해서 ViewModel이 직접 하지 않는다. UI가 Effect를 받아 호출하고 결과를 Intent로 돌려준다. (12장)

## 10-3. Google: Credential Manager

레거시 Google Sign-In 대신 Credential Manager의 "Sign in with Google"을 쓴다.

```kotlin
class GoogleLoginClient : SocialLoginClient {

    override suspend fun login(context: Context): SocialCredential {
        val nonce = UUID.randomUUID().toString()

        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        try {
            val result = CredentialManager.create(context).getCredential(context, request)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                return SocialCredential(SocialProvider.GOOGLE, idToken, nonce)
            }
            error("지원하지 않는 자격 증명 타입이에요")
        } catch (e: GetCredentialCancellationException) {
            throw SocialLoginCancelledException()
        }
    }
}
```

- `GOOGLE_WEB_CLIENT_ID`는 Android 클라이언트 ID가 아니라 서버(웹 애플리케이션) 클라이언트 ID다.
- `nonce`는 서버로 함께 보내서, ID 토큰 안의 nonce 클레임과 일치하는지 서버가 검증하도록 한다. 토큰 재사용(replay) 공격을 막는다.
- 서버는 ID 토큰의 서명, `aud`(우리 클라이언트 ID), 만료, nonce를 검증해야 한다.
- 구글 계정이 기기에 없으면 `NoCredentialException`이 발생한다. 이 경우 계정 추가를 안내한다.

## 10-4. Kakao: 카카오톡 앱 우선, 실패 시 계정 로그인

Kakao SDK는 콜백 기반이라 `suspendCancellableCoroutine`으로 감싼다.

```kotlin
class KakaoLoginClient : SocialLoginClient {

    override suspend fun login(context: Context): SocialCredential {
        val client = UserApiClient.instance

        val token = if (client.isKakaoTalkLoginAvailable(context)) {
            try {
                loginWithTalk(context)
            } catch (e: Throwable) {
                // 사용자가 직접 취소했다면 계정 로그인으로 넘어가지 않는다
                if (e is ClientError && e.reason == ClientErrorCause.Cancelled) {
                    throw SocialLoginCancelledException()
                }
                loginWithAccount(context)
            }
        } else {
            loginWithAccount(context)
        }

        return SocialCredential(SocialProvider.KAKAO, token.accessToken)
    }

    private suspend fun loginWithTalk(context: Context): OAuthToken =
        suspendCancellableCoroutine { cont ->
            UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
                when {
                    error != null -> cont.resumeWithException(error)
                    token != null -> cont.resume(token)
                    else -> cont.resumeWithException(IllegalStateException("토큰이 없어요"))
                }
            }
        }

    private suspend fun loginWithAccount(context: Context): OAuthToken =
        suspendCancellableCoroutine { cont ->
            UserApiClient.instance.loginWithKakaoAccount(context) { token, error ->
                when {
                    error != null -> cont.resumeWithException(
                        if (error is ClientError && error.reason == ClientErrorCause.Cancelled)
                            SocialLoginCancelledException() else error
                    )
                    token != null -> cont.resume(token)
                    else -> cont.resumeWithException(IllegalStateException("토큰이 없어요"))
                }
            }
        }
}
```

초기화와 매니페스트 설정도 필요하다.

```kotlin
// Application.onCreate()
KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
```

```xml
<!-- 카카오 로그인 리다이렉트 -->
<activity
    android:name="com.kakao.sdk.auth.AuthCodeHandlerActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:host="oauth" android:scheme="kakao${KAKAO_NATIVE_APP_KEY}" />
    </intent-filter>
</activity>
```

## 10-5. Naver 등 다른 제공자

Naver, Apple 같은 제공자도 같은 패턴이다. `SocialLoginClient`를 구현해서 "서버에 보낼 토큰(또는 인가 코드)"을 `SocialCredential`로 돌려주면 나머지 흐름은 바뀌지 않는다. 제공자마다 SDK 버전별 API가 달라지므로 구현 시 해당 SDK의 최신 문서를 확인한다.

## 10-6. 서버 호출

```kotlin
interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @POST("auth/social")
    suspend fun socialLogin(@Body body: SocialLoginRequest): TokenResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest)
}

@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class SocialLoginRequest(val provider: String, val token: String, val nonce: String? = null)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class LogoutRequest(val refreshToken: String)
@Serializable data class TokenResponse(val accessToken: String, val refreshToken: String)
```

<br>

# 11. 로그아웃과 세션 만료

## 11-1. 로그아웃 순서

```kotlin
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage,
    private val sessionManager: SessionManager,
) {
    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val res = authApi.login(LoginRequest(email, password))
        sessionManager.login(Tokens(res.accessToken, res.refreshToken))
    }.rethrowCancellation()

    suspend fun socialLogin(credential: SocialCredential): Result<Unit> = runCatching {
        val res = authApi.socialLogin(
            SocialLoginRequest(credential.provider.name, credential.token, credential.nonce)
        )
        sessionManager.login(Tokens(res.accessToken, res.refreshToken))
    }.rethrowCancellation()

    suspend fun logout() {
        // 서버에 refresh token 폐기를 알리되, 실패해도 로컬 로그아웃은 진행
        val refresh = tokenStorage.get()?.refreshToken
        if (refresh != null) {
            runCatching { authApi.logout(LogoutRequest(refresh)) }
        }
        sessionManager.logout()
    }
}

private fun <T> Result<T>.rethrowCancellation(): Result<T> =
    onFailure { if (it is CancellationException) throw it }
```

## 11-2. 로그아웃할 때 함께 정리할 것

| 대상 | 이유 |
| --- | --- |
| 토큰 | `SessionManager.logout()`이 처리 |
| 사용자 데이터(Room, 캐시, DataStore 설정) | 다음 사용자가 이전 사용자의 데이터를 보지 않도록 |
| 소셜 SDK 세션 | 예: `UserApiClient.instance.logout { }` |
| 푸시 토큰(FCM) 등록 | 로그아웃 후에도 알림이 오지 않도록 서버에서 해제 |

정리 코드를 로그아웃 버튼 핸들러에 넣으면 세션 만료로 인한 자동 로그아웃에서는 실행되지 않는다. 상태 변화에 반응하도록 한 곳에 모은다.

```kotlin
// Application 또는 초기화 모듈에서
scope.launch {
    sessionManager.state
        .filterIsInstance<AuthState.Unauthenticated>()
        .collect { userDataCleaner.clear() }
}
```

## 11-3. 세션 만료 안내

세션이 만료되어 로그인 화면으로 돌아왔다면 "다시 로그인해주세요" 같은 안내가 필요할 수 있다. 이때 `Unauthenticated`에 사유(`expired: Boolean`)를 담거나, 별도의 일회성 이벤트로 전달한다. 상태 변경 지점이 `SessionManager.logout()` 하나이므로 사유 인자만 추가하면 된다.

<br>

# 12. MVI에서의 상태 관리

## 12-1. 무엇을 어디에 두는가

| 종류 | 위치 | 이유 |
| --- | --- | --- |
| 로그인 여부(`AuthState`) | `SessionManager` (앱 전역) | 모든 화면이 공유하는 상태 |
| 로그인 버튼 로딩 상태 | 로그인 화면 `UiState` | 이 화면에서만 의미 있음 |
| 소셜 SDK 실행 요청 | `Effect` | Activity Context가 필요한 1회성 명령 |
| SDK 결과(토큰, 취소/실패) | `Intent` | 시스템이 발생시킨 입력 |
| 로그인 성공 후 화면 전환 | 별도 이벤트 없음 | `AuthState` 변화를 루트가 읽어 자동 전환 |

로그인 성공을 Effect로 "메인으로 이동해라"라고 보내지 않는 것이 포인트다. 성공하면 `SessionManager`의 상태가 바뀌고, 루트 화면이 그 상태를 읽어 전환한다. 로그인 화면은 자기가 어디로 이동할지 알 필요가 없다.

## 12-2. 계약

```kotlin
data class LoginState(
    val isLoading: Boolean = false,
)

sealed interface LoginIntent {
    data object ClickGoogle : LoginIntent
    data object ClickKakao : LoginIntent
    data class SocialCredentialReceived(val credential: SocialCredential) : LoginIntent
    data class SocialLoginFailed(val cancelled: Boolean) : LoginIntent
}

sealed interface LoginEffect {
    data class LaunchSocialLogin(val provider: SocialProvider) : LoginEffect
    data class ShowMessage(val message: String) : LoginEffect
}
```

## 12-3. ViewModel

```kotlin
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    private val _effect = Channel<LoginEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onIntent(intent: LoginIntent) {
        when (intent) {
            LoginIntent.ClickGoogle -> send(LoginEffect.LaunchSocialLogin(SocialProvider.GOOGLE))
            LoginIntent.ClickKakao -> send(LoginEffect.LaunchSocialLogin(SocialProvider.KAKAO))

            is LoginIntent.SocialCredentialReceived -> viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                authRepository.socialLogin(intent.credential)
                    .onFailure { send(LoginEffect.ShowMessage(it.toLoginMessage())) }
                _state.update { it.copy(isLoading = false) }
            }

            is LoginIntent.SocialLoginFailed ->
                if (!intent.cancelled) send(LoginEffect.ShowMessage("로그인에 실패했어요"))
        }
    }

    private fun send(effect: LoginEffect) {
        viewModelScope.launch { _effect.send(effect) }
    }
}

private fun Throwable.toLoginMessage(): String = when (this) {
    is IOException -> "네트워크 연결을 확인해주세요"
    is HttpException -> if (code() == 401) "인증에 실패했어요" else "서버 오류가 발생했어요 (${code()})"
    else -> "로그인에 실패했어요"
}
```

## 12-4. 화면

```kotlin
@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // SDK 클라이언트는 의존성이 없는 단순 클래스라 화면에서 직접 보관
    val clients = remember {
        mapOf(
            SocialProvider.GOOGLE to GoogleLoginClient(),
            SocialProvider.KAKAO to KakaoLoginClient(),
        )
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is LoginEffect.LaunchSocialLogin -> {
                    try {
                        val credential = clients.getValue(effect.provider).login(context)
                        viewModel.onIntent(LoginIntent.SocialCredentialReceived(credential))
                    } catch (e: SocialLoginCancelledException) {
                        viewModel.onIntent(LoginIntent.SocialLoginFailed(cancelled = true))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        viewModel.onIntent(LoginIntent.SocialLoginFailed(cancelled = false))
                    }
                }
                is LoginEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Button(
                onClick = { viewModel.onIntent(LoginIntent.ClickGoogle) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Google로 계속하기") }

            Button(
                onClick = { viewModel.onIntent(LoginIntent.ClickKakao) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("카카오로 계속하기") }

            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
```

`LocalContext.current`는 `Activity` Context다. Credential Manager와 Kakao SDK 모두 Activity Context를 요구하므로 `applicationContext`를 넘기면 안 된다.

<br>

# 13. 네비게이션 연결

루트 컴포저블이 `AuthState`에 따라 그래프를 통째로 바꾼다.

```kotlin
@HiltViewModel
class RootViewModel @Inject constructor(
    sessionManager: SessionManager,
) : ViewModel() {
    val authState: StateFlow<AuthState> = sessionManager.state
}

@Composable
fun AppRoot(viewModel: RootViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    when (authState) {
        AuthState.Unknown -> Unit                       // 스플래시가 가리는 구간
        AuthState.Unauthenticated -> LoginNavGraph()
        AuthState.Authenticated -> MainNavGraph()
    }
}
```

이 구조의 장점은 다음과 같다.

- 로그인 성공 시 별도의 `navigate()` 호출이 필요 없다.
- 세션 만료(401 + 갱신 실패)가 어느 화면에서 발생해도 자동으로 로그인 화면으로 전환된다.
- 그래프가 통째로 교체되므로 로그아웃 후 뒤로 가기로 이전 화면에 돌아가는 일이 없다.

`MainNavGraph` 안의 화면 상태(스크롤 위치 등)는 그래프가 교체되면 사라진다. 이는 로그아웃에서는 오히려 원하는 동작이다.

<br>

# 14. 테스트

## 14-1. Authenticator: MockWebServer

실제 `OkHttpClient`에 `TokenAuthenticator`를 붙이고 `MockWebServer`로 시나리오를 검증한다. `TokenStorage`는 메모리 Fake를 쓰고, `AuthApi`는 같은 서버를 가리키는 Retrofit으로 만든다.

```kotlin
class InMemoryTokenStorage(initial: Tokens?) : TokenStorage {
    @Volatile private var tokens = initial
    override suspend fun get() = tokens
    override suspend fun save(tokens: Tokens) { this.tokens = tokens }
    override suspend fun clear() { tokens = null }
}
```

```kotlin
private lateinit var server: MockWebServer
private lateinit var storage: InMemoryTokenStorage
private lateinit var sessionManager: SessionManager
private lateinit var client: OkHttpClient
private val refreshCount = AtomicInteger(0)

@Before
fun setUp() {
    server = MockWebServer().apply { start() }
    storage = InMemoryTokenStorage(Tokens(accessToken = "old", refreshToken = "r1"))
    sessionManager = SessionManager(storage, TestScope())

    val authApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(OkHttpClient())
        .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AuthApi::class.java)

    client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(storage))
        .authenticator(TokenAuthenticator(storage, authApi, sessionManager))
        .build()
}

@After
fun tearDown() = server.shutdown()
```

```kotlin
@Test
fun 만료된_토큰이면_갱신_후_재시도해서_성공한다() {
    server.dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = when {
            request.path == "/auth/refresh" ->
                MockResponse().setBody("""{"accessToken":"new","refreshToken":"r2"}""")
            request.getHeader("Authorization") == "Bearer new" -> MockResponse().setBody("ok")
            else -> MockResponse().setResponseCode(401)
        }
    }

    val response = client.newCall(Request.Builder().url(server.url("/me")).build()).execute()

    assertEquals(200, response.code)
    assertEquals("new", runBlocking { storage.get() }?.accessToken)
}

@Test
fun 동시에_401이_와도_갱신은_한_번만_한다() = runBlocking {
    server.dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = when {
            request.path == "/auth/refresh" -> {
                refreshCount.incrementAndGet()
                MockResponse().setBody("""{"accessToken":"new","refreshToken":"r2"}""")
            }
            request.getHeader("Authorization") == "Bearer new" -> MockResponse().setBody("ok")
            else -> MockResponse().setResponseCode(401)
        }
    }

    val codes = (1..3).map {
        async(Dispatchers.IO) {
            client.newCall(Request.Builder().url(server.url("/me")).build()).execute().use { it.code }
        }
    }.awaitAll()

    assertEquals(listOf(200, 200, 200), codes)
    assertEquals(1, refreshCount.get())
}

@Test
fun refresh_token이_거부되면_로그아웃된다() {
    server.dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
            "/auth/refresh" -> MockResponse().setResponseCode(401)
            else -> MockResponse().setResponseCode(401)
        }
    }

    val response = client.newCall(Request.Builder().url(server.url("/me")).build()).execute()

    assertEquals(401, response.code)
    assertNull(runBlocking { storage.get() })
}

@Test
fun 갱신_중_서버_오류면_세션을_유지한다() {
    server.dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
            "/auth/refresh" -> MockResponse().setResponseCode(503)
            else -> MockResponse().setResponseCode(401)
        }
    }

    assertThrows(IOException::class.java) {
        client.newCall(Request.Builder().url(server.url("/me")).build()).execute()
    }
    assertNotNull(runBlocking { storage.get() })
}
```

## 14-2. 그 밖에 테스트할 것

- `SessionManager`: 저장된 토큰이 있으면 `Authenticated`, 없으면 `Unauthenticated`, `logout()` 후 `Unauthenticated`
- `LoginViewModel`: Fake `AuthRepository`로 `SocialCredentialReceived` → 로딩 → 실패 시 `ShowMessage` Effect, 취소(`cancelled = true`)이면 메시지를 보내지 않음
- `DataStoreTokenStorage`: 저장 후 재생성해서 읽히는지, 복호화 실패 시 `null`을 반환하고 값이 지워지는지 (계측 테스트)
- 소셜 SDK는 단위 테스트에서 직접 호출하지 않고, `SocialLoginClient` 인터페이스 뒤에서 Fake로 대체한다.

<br>

# 15. 흔한 실수

## 15-1. 갱신 API를 Authenticator가 붙은 클라이언트로 호출

갱신 요청이 401을 받으면 다시 Authenticator가 호출되어 무한 재귀나 데드락이 생긴다. 로그인, 갱신, 로그아웃은 `plain` 클라이언트로 호출한다.

## 15-2. 동시 401에서 갱신이 여러 번 나감

refresh token rotation을 쓰는 서버에서는 두 번째 갱신이 폐기된 토큰으로 요청되어 세션이 끝난다. `synchronized`와 "락 획득 후 토큰 재비교"를 반드시 넣는다.

## 15-3. 네트워크 오류에도 로그아웃

갱신 중 타임아웃이나 5xx가 났을 때 로그아웃하면 사용자는 이유 없이 로그인 화면으로 쫓겨난다. 로그아웃은 서버가 refresh token을 명확히 거부했을 때(400/401/403)만 한다.

## 15-4. 재시도 상한이 없음

갱신 후에도 계속 401이 오는 경우(서버 설정 오류, 권한 문제)에 무한 재시도하지 않도록 `responseCount`로 상한을 둔다.

## 15-5. 토큰을 평문으로 저장

일반 SharedPreferences나 평문 DataStore에 토큰을 두면 루팅 기기, 백업, 디버깅 도구로 노출된다. Keystore로 보호되는 키로 암호화한다.

## 15-6. 암호화된 파일을 백업에 포함

새 기기로 복원되면 파일은 있는데 Keystore 키는 없어 복호화가 실패한다. 백업에서 제외하고, 복호화 실패는 예외가 아니라 "로그아웃 상태"로 처리한다.

## 15-7. 로그에 토큰이 남음

`HttpLoggingInterceptor`의 `BODY` 레벨은 로그인/갱신 응답의 토큰을 그대로 출력한다. `redactHeader("Authorization")`를 설정하고, 릴리즈 빌드에서는 로깅을 끈다.

## 15-8. 소셜 토큰으로 API를 직접 인증

앱이 소셜 토큰을 저장해서 API 호출에 쓰면 만료/갱신 정책이 제공자마다 갈라진다. 소셜 토큰은 서버로 한 번 넘기고, 이후에는 우리 서비스 토큰만 쓴다.

## 15-9. 소셜 로그인에 Application Context 사용

Credential Manager와 Kakao SDK는 UI를 띄우므로 Activity Context가 필요하다. ViewModel에서 `@ApplicationContext`로 호출하면 실패하거나 UI가 뜨지 않는다. UI가 호출하고 결과만 ViewModel에 전달한다.

## 15-10. Google 로그인에서 nonce를 생략

nonce 없이도 동작하지만 ID 토큰을 가로채 재사용하는 공격에 취약해진다. 앱에서 만든 nonce를 서버로 같이 보내 검증한다.

## 15-11. 스플래시에서 네트워크 응답을 기다림

자동 로그인 판단에 `/me` 같은 네트워크 호출을 끼우면 느린 네트워크에서 앱이 시작되지 않는 것처럼 보인다. 판단은 저장소만 읽어서 하고, 검증은 첫 API 호출의 401 처리나 백그라운드에 맡긴다.

## 15-12. 로그아웃 시 토큰만 삭제

Room 데이터, 캐시, 푸시 토큰, 소셜 SDK 세션이 남아 다음 사용자에게 노출되거나 알림이 계속 온다. 상태 변화(`Unauthenticated`)에 반응하는 한 곳에서 정리한다.

## 15-13. CancellationException을 삼킴

`runCatching`은 `CancellationException`도 `Result.failure`로 바꿔버려 코루틴 취소가 깨진다. 11장의 `rethrowCancellation()`처럼 다시 던진다.

<br>

# 16. 전체 예제 (복붙용)

## 16-1. 모델

```kotlin
data class Tokens(
    val accessToken: String,
    val refreshToken: String,
)

sealed interface AuthState {
    data object Unknown : AuthState
    data object Authenticated : AuthState
    data object Unauthenticated : AuthState
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
```

## 16-2. 저장소 / 세션 / 네트워크 / 소셜 / ViewModel

| 코드 | 위치 |
| --- | --- |
| `TokenStorage`, `TokenCipher`, `DataStoreTokenStorage` | 5장 |
| `SessionManager` | 4장 |
| `AuthInterceptor` | 6장 |
| `TokenAuthenticator` | 7장 (동시성 처리 포함 8장) |
| `AuthApi`와 요청/응답 모델 | 10-6 |
| `GoogleLoginClient`, `KakaoLoginClient` | 10장 |
| `AuthRepository` | 11장 |
| `LoginViewModel`, `LoginScreen` | 12장 |
| `RootViewModel`, `AppRoot`, `MainActivity` | 9-2, 13장 |

위 코드를 그대로 사용한다.

## 16-3. Hilt 모듈

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindsModule {
    @Binds
    abstract fun bindTokenStorage(impl: DataStoreTokenStorage): TokenStorage
}

@Module
@InstallIn(SingletonComponent::class)
object AuthProvidesModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
```

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://api.example.com/"

    private val json = Json { ignoreUnknownKeys = true }

    private fun loggingInterceptor() = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
                else HttpLoggingInterceptor.Level.NONE
        redactHeader("Authorization")
    }

    /** 로그인 / 소셜 로그인 / 토큰 갱신 / 로그아웃 전용. Interceptor, Authenticator 없음 */
    @Provides
    @Singleton
    @Named("plain")
    fun providePlainClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor())
        .build()

    /** 인증이 필요한 일반 API 전용 */
    @Provides
    @Singleton
    @Named("auth")
    fun provideAuthClient(
        authInterceptor: AuthInterceptor,
        authenticator: TokenAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor())
        .authenticator(authenticator)
        .build()

    @Provides
    @Singleton
    fun provideAuthApi(@Named("plain") client: OkHttpClient): AuthApi =
        retrofit(client).create(AuthApi::class.java)

    /** 예시: 일반 API 하나 */
    @Provides
    @Singleton
    fun provideUserApi(@Named("auth") client: OkHttpClient): UserApi =
        retrofit(client).create(UserApi::class.java)

    private fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
```

`TokenAuthenticator`는 `AuthApi`(plain 클라이언트 기반)를 주입받고, `auth` 클라이언트는 `TokenAuthenticator`를 주입받는다. `AuthApi`가 `auth` 클라이언트에 의존하지 않으므로 순환 의존이 생기지 않는다.

## 16-4. 의존성

```kotlin
// build.gradle.kts (app)
implementation("com.google.crypto.tink:tink-android:<최신 버전>")
implementation("androidx.datastore:datastore-preferences:<최신 버전>")

implementation("com.squareup.retrofit2:retrofit:<최신 버전>")
implementation("com.squareup.okhttp3:okhttp:<최신 버전>")
implementation("com.squareup.okhttp3:logging-interceptor:<최신 버전>")
implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:<최신 버전>")

// Google 로그인 (Credential Manager)
implementation("androidx.credentials:credentials:<최신 버전>")
implementation("androidx.credentials:credentials-play-services-auth:<최신 버전>")
implementation("com.google.android.libraries.identity.googleid:googleid:<최신 버전>")

// Kakao 로그인
implementation("com.kakao.sdk:v2-user:<최신 버전>")

// 스플래시
implementation("androidx.core:core-splashscreen:<최신 버전>")

// 테스트
testImplementation("com.squareup.okhttp3:mockwebserver:<최신 버전>")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:<최신 버전>")
```

<br>

# 정리

1. 인증이 복잡한 이유는 토큰 저장, 자동 로그인, 갱신, 소셜 로그인이 앱 전체에 얽혀 있기 때문이다. 해결은 로그인 상태의 원천을 `SessionManager` 하나로 두고, 토큰 첨부와 갱신을 화면이 아닌 네트워크 계층에 맡기는 것이다.
2. 로그인 여부는 `AuthState`(Unknown / Authenticated / Unauthenticated)로 표현한다. 상태를 바꾸는 곳은 `login()`과 `logout()`뿐이고, 화면은 읽기만 한다. `Unknown`이 있어야 스플래시에서 로그인 화면이 깜빡이지 않는다.
3. 토큰은 DataStore에 Tink(AES-256-GCM)로 암호화해서 저장하고, 키는 Android Keystore가 보호한다. 복호화 실패는 예외가 아니라 "로그아웃 상태"로 처리하고, 암호화 파일은 자동 백업에서 제외한다.
4. 요청에는 `AuthInterceptor`가 토큰을 붙이고, 401은 `Authenticator`가 처리한다. 로그인, 갱신, 로그아웃은 Authenticator가 없는 `plain` 클라이언트로 호출해 재귀와 데드락을 피한다.
5. 갱신은 `synchronized`와 "락 획득 후 토큰 재비교"로 한 번만 수행한다. 서버가 refresh token rotation을 쓸 때 중복 갱신은 세션 종료로 이어지므로 특히 중요하다.
6. 갱신 실패는 구분한다. 서버가 refresh token을 거부(400/401/403)하면 로그아웃, 네트워크 오류나 5xx는 세션을 유지한 채 오류로 전달한다. 재시도에는 `responseCount` 상한을 둔다.
7. 자동 로그인은 저장소에 토큰이 있는지만 읽어서 진입하고, 만료는 첫 API의 401 처리에 맡긴다. 스플래시에서 네트워크 응답을 기다리지 않는다.
8. 소셜 로그인은 SDK에서 얻은 토큰(Google ID 토큰 + nonce, Kakao access token)을 서버에 넘기고, 서버가 발급한 우리 서비스 토큰만 앱에서 사용한다. SDK 호출은 Activity Context가 필요하므로 UI가 실행하고 결과만 ViewModel에 전달한다.
9. 로그인 성공이나 세션 만료에 따른 화면 전환은 `AuthState`를 읽는 루트가 그래프를 교체해서 처리한다. 로그아웃 정리(DB, 캐시, 푸시 토큰, 소셜 세션)는 `Unauthenticated` 상태 변화에 반응하는 한 곳에 모은다.
10. 흔한 실수는 갱신 API에 Authenticator 클라이언트 사용, 동시 갱신, 네트워크 오류 시 로그아웃, 재시도 상한 없음, 평문 저장과 백업 포함, 로그에 토큰 노출, 소셜 토큰으로 직접 인증, Application Context로 소셜 로그인, nonce 생략, `CancellationException` 삼킴이다.
