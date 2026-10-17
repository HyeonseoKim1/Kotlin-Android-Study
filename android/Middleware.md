# 미들웨어 (Middleware)

<br>

# 목차

1. 미들웨어란
2. Android OS 구조와 미들웨어 계층
3. Binder IPC와 System Server
4. OkHttp Interceptor
5. Interceptor 실전 구현
6. Authenticator와 토큰 갱신
7. Hilt로 OkHttp 구성하기
8. MVI 아키텍처의 미들웨어
9. MVI Store에 미들웨어 직접 구현하기
10. 그 외 안드로이드의 인터셉터 개념
11. 백엔드의 미들웨어 (FastAPI)
12. 디자인 패턴 관점
13. 흔한 실수와 주의점
14. 질문 정리
15. 정리

<br>

# 1. 미들웨어란

미들웨어는 양쪽 사이에 끼어서 중간 처리를 담당하는 계층이다. 요청이 목적지에 도달하기 전후에 공통 로직(인증, 로깅, 변환, 재시도, 캐싱 등)을 한곳에서 처리할 수 있게 해준다.

## 1.1 왜 필요한가

공통 로직이 없다면 모든 호출부에서 같은 코드를 반복해야 한다.

```kotlin
// 미들웨어가 없을 때: 모든 API 호출마다 토큰을 직접 붙인다
interface UserApi {
    @GET("users/me")
    suspend fun getMe(@Header("Authorization") token: String): UserDto

    @GET("users/{id}")
    suspend fun getUser(
        @Header("Authorization") token: String,
        @Path("id") id: Long
    ): UserDto
}
```

API가 50개라면 50군데에 같은 헤더 파라미터가 생기고, 토큰 갱신 로직까지 들어가면 관리가 불가능해진다. 미들웨어는 이 공통 관심사를 한곳으로 모은다.

```kotlin
// 미들웨어가 있을 때: 호출부는 비즈니스에만 집중한다
interface UserApi {
    @GET("users/me")
    suspend fun getMe(): UserDto

    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: Long): UserDto
}
```

## 1.2 안드로이드에서 쓰이는 세 가지 의미

| 구분 | 위치 | 대표 예시 |
|---|---|---|
| OS 구조 | 커널과 앱 사이 | Android Framework, ART, HAL |
| 네트워크 | 요청과 응답 사이 | OkHttp Interceptor |
| 상태 관리 | Intent와 Reducer 사이 | MVI/Redux Middleware |

같은 단어지만 공통점은 하나다. **A와 B 사이에 끼어서, A나 B가 몰라도 되는 일을 대신 처리한다.**

## 1.3 핵심 특징

- 호출하는 쪽과 호출받는 쪽 모두 미들웨어의 존재를 몰라도 된다.
- 여러 미들웨어를 체인으로 연결할 수 있다.
- 순서가 결과에 영향을 준다.
- 요청 방향(전)과 응답 방향(후) 양쪽에서 개입할 수 있다.

<br>

# 2. Android OS 구조와 미들웨어 계층

안드로이드 OS는 계층 구조로 되어 있고, 흔히 "미들웨어"라고 부르는 영역은 커널 위, 앱 아래에 있는 부분이다.

## 2.1 전체 계층도

```text
+-----------------------------------+
|  Applications (내 앱, 시스템 앱)     |
+-----------------------------------+
|  Android Framework (Java/Kotlin)  |
|  ActivityManager, WindowManager,  |
|  PackageManager, NotificationMgr  |
+-----------------------------------+
|  Native Libraries   |  ART        |
|  (libc, SQLite,     |  (Android   |
|   OpenGL, WebKit)   |   Runtime)  |
+-----------------------------------+
|  HAL (Hardware Abstraction Layer) |
|  Camera, Audio, Sensors, Radio    |
+-----------------------------------+
|  Linux Kernel                     |
|  Drivers, Binder, Power, Memory   |
+-----------------------------------+
```

미들웨어는 일반적으로 Native Libraries, ART, Framework를 묶어서 가리킨다. HAL까지 포함하기도 한다.

## 2.2 Linux Kernel

- 프로세스 관리, 메모리 관리, 파일 시스템, 네트워크 스택을 담당한다.
- 하드웨어 드라이버가 여기에 있다.
- 안드로이드 전용 확장으로 Binder, Low Memory Killer, Wakelock 등이 있다.

## 2.3 HAL

하드웨어 제조사마다 다른 드라이버를 Framework가 동일한 인터페이스로 쓸 수 있게 추상화한 계층이다.

- 카메라, 오디오, 센서, 블루투스, 통신 모뎀 등
- Framework는 HAL 인터페이스만 알면 되고, 실제 하드웨어는 몰라도 된다.
- Project Treble 이후 HAL은 vendor 파티션에서 별도로 업데이트할 수 있게 분리되었다.

## 2.4 Native Libraries

C/C++로 작성된 라이브러리 모음이다.

- libc (Bionic): 안드로이드 전용 C 라이브러리
- SQLite: Room이 내부적으로 사용하는 DB 엔진
- OpenGL ES, Vulkan: 그래픽 렌더링
- Skia: 2D 그래픽 엔진. Compose가 이 위에서 그려진다.
- WebKit/Chromium: WebView

## 2.5 ART (Android Runtime)

앱의 바이트코드를 실행하는 런타임이다.

- Kotlin/Java 코드는 `.class`로 컴파일되고, D8/R8을 거쳐 `.dex`로 변환된다.
- ART는 AOT(설치 시 컴파일), JIT(실행 중 컴파일), 프로파일 기반 컴파일을 조합한다.
- 가비지 컬렉션, 스레드 관리도 ART가 담당한다.
- Baseline Profile은 ART가 앱 시작 경로를 미리 컴파일하도록 알려주는 장치다.

## 2.6 Android Framework

앱 개발자가 직접 호출하는 API 계층이다. 가장 "미들웨어다운" 계층이다.

| 서비스 | 역할 |
|---|---|
| ActivityManager | Activity 생명주기, 프로세스 관리, Task/Back Stack |
| WindowManager | 윈도우 생성, 배치, 입력 이벤트 전달 |
| PackageManager | 앱 설치 정보, 권한 정보 |
| NotificationManager | 알림 관리 |
| LocationManager | 위치 서비스 |
| ContentResolver | Content Provider를 통한 데이터 공유 |
| ConnectivityManager | 네트워크 상태 |

앱이 `startActivity()`를 호출하면 앱은 ActivityManager의 존재만 알 뿐, 프로세스를 어떻게 띄우는지는 모른다. 이 간극을 Framework가 메꿔준다.

## 2.7 앱이 Framework를 쓰는 예

```kotlin
class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
```

`getSystemService()`로 받는 객체는 사실 System Server에 있는 실제 서비스의 프록시다. 이 프록시와 실제 서비스를 이어주는 것이 Binder다.

<br>

# 3. Binder IPC와 System Server

## 3.1 왜 IPC가 필요한가

안드로이드의 모든 앱은 독립된 프로세스에서 실행된다. 프로세스는 메모리가 격리되어 있어서 다른 프로세스의 객체를 직접 호출할 수 없다. 그래서 프로세스 간 통신(IPC)이 필요하고, 안드로이드는 이를 Binder로 해결한다.

## 3.2 Binder 흐름

```text
[내 앱 프로세스]                          [System Server 프로세스]
  ActivityManager (Proxy)  ----Binder---->  ActivityManagerService (실제 구현)
        |                    (커널 드라이버)            |
   startActivity() 호출                       프로세스 생성, Task 관리
```

- 앱 쪽에는 Proxy 객체가 있고, 호출은 Parcel로 직렬화되어 커널의 Binder 드라이버를 거쳐 상대 프로세스로 전달된다.
- 호출자 UID/PID가 커널에서 보장되기 때문에 권한 검사에 활용된다.
- 한 번의 복사로 데이터가 전달되어 소켓보다 효율적이다.

## 3.3 System Server

Zygote에서 fork되어 부팅 시 가장 먼저 뜨는 핵심 프로세스다. 대부분의 Framework 서비스가 여기서 동작한다.

- ActivityManagerService (AMS)
- WindowManagerService (WMS)
- PackageManagerService (PMS)
- PowerManagerService 등

## 3.4 AIDL로 직접 Binder 서비스 만들기

앱 간 통신이 필요하면 AIDL로 인터페이스를 정의할 수 있다.

```java
// IRecordingService.aidl
package com.example.recorder;

interface IRecordingService {
    void startRecording();
    void stopRecording();
    boolean isRecording();
}
```

```kotlin
class RecordingService : Service() {

    private val binder = object : IRecordingService.Stub() {
        override fun startRecording() { /* 녹음 시작 */ }
        override fun stopRecording() { /* 녹음 종료 */ }
        override fun isRecording(): Boolean = false
    }

    override fun onBind(intent: Intent): IBinder = binder
}
```

## 3.5 Binder 사용 시 주의점

- Binder 트랜잭션 버퍼는 프로세스당 약 1MB로 제한된다. 큰 데이터(Bitmap 등)를 Intent/Bundle에 담으면 `TransactionTooLargeException`이 발생한다.
- Binder 호출은 동기적이라 UI 스레드에서 오래 걸리는 호출을 하면 ANR이 날 수 있다.
- 서비스 프로세스가 죽으면 `DeadObjectException`이 발생할 수 있다.

<br>

# 4. OkHttp Interceptor

앱 개발자가 가장 자주 만나는 미들웨어다. Retrofit은 내부적으로 OkHttp를 쓰기 때문에, Retrofit을 쓴다면 이미 Interceptor 체인 위에서 동작하고 있다.

## 4.1 기본 구조

```kotlin
fun interface Interceptor {
    fun intercept(chain: Chain): Response

    interface Chain {
        fun request(): Request
        fun proceed(request: Request): Response
    }
}
```

- `chain.request()`로 현재 요청을 꺼낸다.
- `chain.proceed(request)`를 호출하면 다음 Interceptor(마지막이면 실제 네트워크 호출)로 넘어간다.
- `proceed()`가 반환하는 `Response`를 받아서 가공한 뒤 반환한다.
- `proceed()`를 호출하지 않으면 네트워크 호출 자체가 일어나지 않는다. 캐시 응답이나 Mock 응답을 바로 반환하는 데 쓸 수 있다.

## 4.2 동작 흐름

```text
 앱 코드
   |  request
   v
[Interceptor A] -- 요청 전 처리
   |
[Interceptor B] -- 요청 전 처리
   |
[실제 네트워크 호출]
   |
[Interceptor B] -- 응답 후 처리
   |
[Interceptor A] -- 응답 후 처리
   |  response
   v
 앱 코드
```

요청은 등록 순서대로 내려가고, 응답은 역순으로 올라온다. 양파 껍질 구조와 같다.

## 4.3 Application Interceptor vs Network Interceptor

OkHttp는 두 종류의 Interceptor를 구분한다.

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(appInterceptor)           // Application Interceptor
    .addNetworkInterceptor(networkInterceptor) // Network Interceptor
    .build()
```

| 구분 | Application Interceptor | Network Interceptor |
|---|---|---|
| 위치 | OkHttp 코어 바깥 | OkHttp 코어와 네트워크 사이 |
| 리다이렉트/재시도 | 한 번만 호출됨 | 리다이렉트/재시도마다 호출됨 |
| 캐시 응답 | 캐시 히트여도 호출됨 | 캐시 히트면 호출 안 됨 |
| `proceed()` 생략 | 가능 (Mock 응답 등) | 불가능 (정확히 한 번 호출해야 함) |
| 접근 가능한 정보 | 앱이 만든 원본 요청 | 실제 전송되는 요청(헤더 포함) |
| 대표 용도 | 토큰, 공통 헤더, 에러 변환 | 네트워크 레벨 로깅, 압축 확인 |

대부분의 경우 Application Interceptor로 충분하다. 실제 전송된 헤더나 리다이렉트 과정을 보고 싶을 때만 Network Interceptor를 쓴다.

## 4.4 등록 순서가 중요한 이유

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(HeaderInterceptor())   // 1. 공통 헤더 추가
    .addInterceptor(AuthInterceptor())     // 2. 토큰 추가
    .addInterceptor(LoggingInterceptor())  // 3. 마지막에 로깅
    .build()
```

로깅 Interceptor를 맨 앞에 두면 헤더가 붙기 전의 요청이 찍힌다. 맨 뒤에 두면 앞선 Interceptor들이 가공한 최종 요청이 찍힌다. **무엇을 보고 싶은지에 따라 위치를 정해야 한다.**

<br>

# 5. Interceptor 실전 구현

## 5.1 공통 헤더 Interceptor

```kotlin
class HeaderInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Accept", "application/json")
            .addHeader("X-Platform", "android")
            .addHeader("X-App-Version", BuildConfig.VERSION_NAME)
            .build()
        return chain.proceed(request)
    }
}
```

## 5.2 인증 토큰 Interceptor

```kotlin
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // 로그인, 회원가입 같은 인증 불필요 API는 건너뛴다
        if (original.header("No-Auth") != null) {
            val request = original.newBuilder()
                .removeHeader("No-Auth")
                .build()
            return chain.proceed(request)
        }

        val token = tokenStore.getAccessToken()
        val request = if (token != null) {
            original.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
```

Retrofit 인터페이스에서 `@Headers("No-Auth: true")`로 인증 제외 API를 표시한다.

```kotlin
interface AuthApi {
    @Headers("No-Auth: true")
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse
}
```

## 5.3 로깅 Interceptor

실무에서는 직접 만들기보다 공식 `HttpLoggingInterceptor`를 쓴다.

```kotlin
val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = if (BuildConfig.DEBUG) {
        HttpLoggingInterceptor.Level.BODY
    } else {
        HttpLoggingInterceptor.Level.NONE
    }
    redactHeader("Authorization") // 토큰이 로그에 남지 않게 가린다
}
```

릴리즈 빌드에서 `BODY` 레벨로 두면 개인정보와 토큰이 로그에 노출될 수 있으므로 반드시 `NONE`으로 둔다.

## 5.4 직접 만드는 로깅 Interceptor

동작 원리를 이해하기 위해 간단히 구현해본다.

```kotlin
class TimingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startNs = System.nanoTime()

        val response = chain.proceed(request)

        val tookMs = (System.nanoTime() - startNs) / 1_000_000
        Log.d("Network", "${request.method} ${request.url} -> ${response.code} (${tookMs}ms)")
        return response
    }
}
```

## 5.5 에러 응답 변환 Interceptor

서버가 HTTP 200에 에러 코드를 담아 보내는 경우, Interceptor에서 예외로 바꿀 수 있다.

```kotlin
class ApiErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (!response.isSuccessful) {
            val body = response.peekBody(1024 * 1024).string()
            throw ApiException(response.code, body)
        }
        return response
    }
}

class ApiException(val code: Int, val body: String) : IOException("HTTP $code: $body")
```

`response.body.string()`은 한 번만 읽을 수 있다. 중간에서 본문을 읽어야 한다면 `peekBody()`를 써서 원본이 소비되지 않게 해야 한다.

## 5.6 재시도 Interceptor

```kotlin
class RetryInterceptor(
    private val maxRetry: Int = 3
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var lastException: IOException? = null

        repeat(maxRetry) { attempt ->
            try {
                val response = chain.proceed(chain.request())
                if (response.code < 500) return response
                response.close() // 재시도 전에 이전 응답은 반드시 닫는다
            } catch (e: IOException) {
                lastException = e
            }
            Thread.sleep(500L * (attempt + 1)) // 단순 백오프
        }
        throw lastException ?: IOException("Retry failed")
    }
}
```

POST처럼 멱등하지 않은 요청은 재시도하면 중복 처리가 될 수 있으므로 GET에만 적용하는 편이 안전하다.

## 5.7 오프라인 캐시 Interceptor

```kotlin
class OfflineCacheInterceptor(
    private val networkMonitor: NetworkMonitor
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        if (!networkMonitor.isOnline()) {
            request = request.newBuilder()
                .cacheControl(
                    CacheControl.Builder()
                        .onlyIfCached()
                        .maxStale(7, TimeUnit.DAYS)
                        .build()
                )
                .build()
        }
        return chain.proceed(request)
    }
}
```

<br>

# 6. Authenticator와 토큰 갱신

## 6.1 Interceptor로 401을 처리하면 안 되는 이유

401 응답이 오면 토큰을 갱신하고 원래 요청을 다시 보내야 한다. 이걸 Interceptor에서 직접 구현할 수도 있지만, OkHttp는 이 용도로 `Authenticator`를 따로 제공한다.

- `Authenticator`는 서버가 401을 반환했을 때만 호출된다.
- 새 `Request`를 반환하면 OkHttp가 그 요청으로 재시도한다.
- `null`을 반환하면 재시도를 포기하고 401 응답을 그대로 전달한다.

## 6.2 기본 구현

```kotlin
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val authApiProvider: Provider<AuthApi> // 순환 의존 방지를 위해 Provider 사용
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // 이미 한 번 재시도한 요청이면 무한 루프를 막기 위해 포기한다
        if (response.priorResponse != null) return null

        synchronized(lock) {
            val currentToken = tokenStore.getAccessToken()
            val requestToken = response.request.header("Authorization")
                ?.removePrefix("Bearer ")

            // 다른 스레드가 이미 갱신했다면 새 토큰으로 바로 재시도
            if (currentToken != null && currentToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = tokenStore.getRefreshToken() ?: return null

            val newTokens = runBlocking {
                runCatching {
                    authApiProvider.get().refresh(RefreshRequest(refreshToken))
                }.getOrNull()
            } ?: run {
                tokenStore.clear() // 갱신 실패 시 로그아웃 처리
                return null
            }

            tokenStore.save(newTokens.accessToken, newTokens.refreshToken)

            return response.request.newBuilder()
                .header("Authorization", "Bearer ${newTokens.accessToken}")
                .build()
        }
    }
}
```

## 6.3 핵심 포인트

- **동시성**: 여러 API가 동시에 401을 받으면 갱신 요청이 여러 번 나갈 수 있다. `synchronized`로 한 번만 갱신하고, 나머지는 갱신된 토큰을 재사용한다.
- **무한 루프 방지**: `priorResponse`를 확인해서 이미 재시도한 요청이면 포기한다.
- **갱신 API 분리**: 갱신 호출은 `Authenticator`가 걸려 있지 않은 별도 OkHttpClient/Retrofit으로 보내야 한다. 같은 클라이언트를 쓰면 갱신 중 401이 나올 때 재귀가 발생한다.
- **runBlocking 사용**: `Authenticator`는 코루틴이 아닌 동기 컨텍스트에서 호출되므로 `runBlocking`이 불가피하다. OkHttp 디스패처 스레드에서 실행되므로 메인 스레드를 막지는 않는다.

<br>

# 7. Hilt로 OkHttp 구성하기

## 7.1 NetworkModule

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }

    @Provides
    @Singleton
    @AuthClient
    fun provideAuthOkHttpClient(
        headerInterceptor: HeaderInterceptor,
        authInterceptor: AuthInterceptor,
        authenticator: TokenAuthenticator,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(headerInterceptor)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(authenticator)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @PlainClient
    fun providePlainOkHttpClient(
        headerInterceptor: HeaderInterceptor,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(headerInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()
}
```

## 7.2 Qualifier 정의

```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlainClient
```

## 7.3 Retrofit 구성

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object RetrofitModule {

    @Provides
    @Singleton
    @AuthClient
    fun provideAuthRetrofit(
        @AuthClient client: OkHttpClient,
        json: Json
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    @PlainClient
    fun providePlainRetrofit(
        @PlainClient client: OkHttpClient,
        json: Json
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideUserApi(@AuthClient retrofit: Retrofit): UserApi =
        retrofit.create(UserApi::class.java)

    // 토큰 갱신용 API는 Authenticator가 없는 클라이언트를 사용한다
    @Provides
    @Singleton
    fun provideAuthApi(@PlainClient retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)
}
```

토큰 갱신 API만 `PlainClient`로 분리하는 것이 핵심이다. 멀티모듈에서는 이 모듈을 `core:network`에 두고 각 feature 모듈은 API 인터페이스만 주입받게 하면 된다.

<br>

# 8. MVI 아키텍처의 미들웨어

## 8.1 개념

Redux 계열 아키텍처에서 미들웨어는 **Intent(Action)가 Reducer에 도달하기 전후에 끼어드는 계층**이다.

```text
Intent --> [Middleware 1] --> [Middleware 2] --> Reducer --> State
              로깅              비동기 처리
```

Reducer는 순수 함수여야 하므로 네트워크 호출, 로깅, 분석 이벤트 전송 같은 부수효과를 넣을 수 없다. 이런 일을 미들웨어가 대신한다.

## 8.2 MVI와 Redux의 차이

| 구분 | Redux | 일반적인 안드로이드 MVI |
|---|---|---|
| 상태 저장소 | 전역 단일 Store | 화면(ViewModel)별 Store |
| 미들웨어 | 표준 개념(thunk, saga 등) | 보통 별도 계층 없이 ViewModel 안에서 처리 |
| 부수효과 | 미들웨어에서 처리 | `SideEffect` 채널(Channel/SharedFlow) |

안드로이드 MVI에서는 미들웨어라는 용어를 쓰지 않고 ViewModel의 `onIntent`가 그 역할을 겸하는 경우가 대부분이다.

## 8.3 일반적인 ViewModel 기반 MVI

```kotlin
data class RecordListState(
    val isLoading: Boolean = false,
    val records: List<Record> = emptyList(),
    val error: String? = null
)

sealed interface RecordListIntent {
    data object Load : RecordListIntent
    data class Delete(val id: Long) : RecordListIntent
}

sealed interface RecordListEffect {
    data class ShowToast(val message: String) : RecordListEffect
}

@HiltViewModel
class RecordListViewModel @Inject constructor(
    private val getRecords: GetRecordsUseCase,
    private val deleteRecord: DeleteRecordUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(RecordListState())
    val state: StateFlow<RecordListState> = _state.asStateFlow()

    private val _effect = Channel<RecordListEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onIntent(intent: RecordListIntent) {
        when (intent) {
            RecordListIntent.Load -> load()
            is RecordListIntent.Delete -> delete(intent.id)
        }
    }

    private fun load() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        getRecords()
            .onSuccess { list -> _state.update { it.copy(isLoading = false, records = list) } }
            .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
    }

    private fun delete(id: Long) = viewModelScope.launch {
        deleteRecord(id)
            .onSuccess { _effect.send(RecordListEffect.ShowToast("삭제되었습니다")) }
    }
}
```

이 구조에서는 로깅이나 분석 이벤트를 모든 ViewModel에 넣어야 한다는 문제가 있다. 이때 미들웨어 개념이 도움이 된다.

<br>

# 9. MVI Store에 미들웨어 직접 구현하기

## 9.1 Middleware 인터페이스

```kotlin
fun interface Middleware<I : UiIntent, S : UiState> {
    suspend fun process(intent: I, state: S, next: suspend (I) -> Unit)
}

interface UiIntent
interface UiState
```

OkHttp의 `Interceptor`와 같은 구조다. `next`를 호출해야 다음 단계로 넘어가고, 호출하지 않으면 해당 Intent는 거기서 멈춘다.

## 9.2 Store 구현

```kotlin
class Store<I : UiIntent, S : UiState>(
    initialState: S,
    private val reducer: (S, I) -> S,
    private val middlewares: List<Middleware<I, S>> = emptyList()
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    suspend fun dispatch(intent: I) {
        // 미들웨어 체인을 오른쪽부터 감싸서 하나의 함수로 만든다
        val chain = middlewares.foldRight<Middleware<I, S>, suspend (I) -> Unit>(
            { i -> _state.update { reducer(it, i) } }
        ) { middleware, next ->
            { i -> middleware.process(i, _state.value, next) }
        }
        chain(intent)
    }
}
```

## 9.3 로깅 미들웨어

```kotlin
class LoggingMiddleware<I : UiIntent, S : UiState>(
    private val tag: String
) : Middleware<I, S> {

    override suspend fun process(intent: I, state: S, next: suspend (I) -> Unit) {
        Log.d(tag, "Intent: $intent | State(before): $state")
        next(intent)
        Log.d(tag, "Intent handled: $intent")
    }
}
```

## 9.4 분석 이벤트 미들웨어

```kotlin
class AnalyticsMiddleware<I : UiIntent, S : UiState>(
    private val analytics: Analytics,
    private val eventMapper: (I) -> AnalyticsEvent?
) : Middleware<I, S> {

    override suspend fun process(intent: I, state: S, next: suspend (I) -> Unit) {
        eventMapper(intent)?.let { analytics.log(it) }
        next(intent)
    }
}
```

## 9.5 사용 예

```kotlin
val store = Store(
    initialState = RecordListState(),
    reducer = ::recordListReducer,
    middlewares = listOf(
        LoggingMiddleware("RecordList"),
        AnalyticsMiddleware(analytics, ::toAnalyticsEvent)
    )
)
```

## 9.6 언제 도입할 만한가

- 여러 화면에서 같은 로깅/분석/에러 처리 로직이 반복될 때
- 디버그 빌드에서 모든 Intent와 State 변화를 추적하고 싶을 때

작은 팀이나 화면 수가 적은 프로젝트에서는 `BaseViewModel`에서 공통 로직을 처리하는 편이 더 단순하다. 미들웨어는 구조가 복잡해지므로 필요가 생겼을 때 도입한다.

<br>

# 10. 그 외 안드로이드의 인터셉터 개념

| 라이브러리/기능 | 개념 | 용도 |
|---|---|---|
| Coil Interceptor | 이미지 요청 파이프라인에 끼어듦 | 요청 변환, 캐시 정책 |
| Room Callback | DB 생성/오픈 시점에 끼어듦 | 초기 데이터 삽입 |
| Navigation | `NavController.addOnDestinationChangedListener` | 화면 전환 로깅 |
| Compose Modifier 체인 | 요소에 순서대로 적용 | 레이아웃/그리기/입력 처리 |
| AccessibilityDelegate | 접근성 이벤트에 끼어듦 | 접근성 커스터마이징 |

## 10.1 Compose Modifier도 체인이다

Modifier는 순서에 따라 결과가 달라진다는 점에서 Interceptor와 닮아 있다.

```kotlin
// padding 후 background: 패딩 영역에는 배경이 칠해지지 않는다
Box(Modifier.padding(16.dp).background(Color.Blue))

// background 후 padding: 패딩 영역까지 배경이 칠해진다
Box(Modifier.background(Color.Blue).padding(16.dp))
```

<br>

# 11. 백엔드의 미들웨어 (FastAPI)

## 11.1 개념

웹 프레임워크에서 미들웨어는 요청이 라우터(엔드포인트)에 도달하기 전과 응답이 클라이언트에 나가기 전에 실행되는 함수다. 구조는 OkHttp Interceptor와 완전히 같다.

```text
Client --> [CORS] --> [Logging] --> [Auth] --> Router --> Endpoint
Client <-- [CORS] <-- [Logging] <-- [Auth] <-- Router <-- Endpoint
```

## 11.2 기본 미들웨어 작성

```python
import time
from fastapi import FastAPI, Request

app = FastAPI()


@app.middleware("http")
async def timing_middleware(request: Request, call_next):
    start = time.perf_counter()

    response = await call_next(request)  # 다음 단계로 넘김 (= chain.proceed)

    elapsed_ms = (time.perf_counter() - start) * 1000
    response.headers["X-Process-Time-Ms"] = f"{elapsed_ms:.1f}"
    return response
```

`call_next(request)`가 OkHttp의 `chain.proceed(request)`에 해당한다.

## 11.3 CORS 미들웨어

```python
from fastapi.middleware.cors import CORSMiddleware

app.add_middleware(
    CORSMiddleware,
    allow_origins=["https://example.com"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
```

모바일 앱은 CORS의 영향을 받지 않는다. 브라우저에서 호출하는 웹 프론트엔드가 있을 때만 필요하다.

## 11.4 등록 순서

```python
app.add_middleware(A)
app.add_middleware(B)
app.add_middleware(C)
```

FastAPI(Starlette)에서는 **나중에 등록한 미들웨어가 바깥쪽**이 된다. 요청은 C, B, A 순서로 지나간다. OkHttp는 먼저 등록한 것이 먼저 실행되므로 정반대다. 헷갈리기 쉬운 부분이다.

## 11.5 미들웨어 vs Depends

| 구분 | Middleware | Depends |
|---|---|---|
| 적용 범위 | 모든 요청 | 특정 엔드포인트/라우터 |
| 접근 가능한 정보 | Request/Response | 파싱된 파라미터, 다른 의존성 |
| 용도 | 로깅, CORS, 공통 헤더 | 인증 사용자 주입, DB 세션 |
| 비유 | OkHttp Interceptor | Hilt `@Inject` |

인증은 `Depends`로 처리하는 편이 일반적이다. 엔드포인트마다 인증 필요 여부가 다르고, 인증된 사용자 객체를 엔드포인트에 바로 넘겨줄 수 있기 때문이다.

```python
from fastapi import Depends, HTTPException, Header


async def get_current_user(authorization: str = Header(...)) -> User:
    token = authorization.removeprefix("Bearer ")
    user = verify_token(token)
    if user is None:
        raise HTTPException(status_code=401, detail="Invalid token")
    return user


@app.get("/users/me")
async def read_me(user: User = Depends(get_current_user)):
    return user
```

## 11.6 BaseHTTPMiddleware 주의점

`@app.middleware("http")`와 `BaseHTTPMiddleware`는 스트리밍 응답(SSE 등)에서 문제가 생길 수 있다. LLM 응답을 스트리밍하는 엔드포인트에서는 순수 ASGI 미들웨어로 작성하는 것이 안전하다.

```python
class PureAsgiTimingMiddleware:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        start = time.perf_counter()

        async def send_wrapper(message):
            if message["type"] == "http.response.start":
                elapsed = (time.perf_counter() - start) * 1000
                headers = list(message.get("headers", []))
                headers.append((b"x-process-time-ms", f"{elapsed:.1f}".encode()))
                message["headers"] = headers
            await send(message)

        await self.app(scope, receive, send_wrapper)
```

<br>

# 12. 디자인 패턴 관점

## 12.1 Chain of Responsibility

미들웨어 체인은 책임 연쇄 패턴이다. 요청을 처리할 수 있는 객체들을 사슬로 연결하고, 각자 처리하거나 다음으로 넘긴다.

## 12.2 Decorator

각 미들웨어가 다음 핸들러를 감싸서 기능을 덧붙이는 구조는 데코레이터 패턴이기도 하다.

```kotlin
// 개념적 표현: Interceptor는 결국 함수를 감싸는 함수다
typealias Handler = (Request) -> Response

fun loggingDecorator(next: Handler): Handler = { request ->
    println("before: $request")
    val response = next(request)
    println("after: $response")
    response
}
```

## 12.3 AOP (관점 지향 프로그래밍)

로깅, 인증, 트랜잭션 같은 횡단 관심사(Cross-cutting Concern)를 비즈니스 로직에서 분리한다는 점에서 AOP와 같은 목적을 가진다.

<br>

# 13. 흔한 실수와 주의점

## 13.1 OkHttp

- `response.body.string()`을 Interceptor에서 호출해 본문을 소비해버리고, 이후 Retrofit이 읽을 때 `IllegalStateException`이 나는 경우. `peekBody()`를 쓴다.
- 토큰 갱신 호출에 같은 클라이언트를 써서 무한 재귀가 발생하는 경우.
- 릴리즈 빌드에서 `Level.BODY` 로깅을 켜둔 채 배포하는 경우.
- 재시도 시 이전 `Response`를 닫지 않아 커넥션 누수가 생기는 경우.
- POST 같은 비멱등 요청을 무분별하게 재시도하는 경우.
- Interceptor 안에서 메인 스레드 전용 작업을 하는 경우. Interceptor는 백그라운드 스레드에서 실행된다.

## 13.2 MVI

- 미들웨어 안에서 State를 직접 수정하는 경우. State 변경은 Reducer만 담당해야 한다.
- 미들웨어 체인이 너무 길어져서 Intent의 흐름을 추적하기 어려워지는 경우.
- 단순한 화면에까지 미들웨어 구조를 도입해서 과설계가 되는 경우.

## 13.3 FastAPI

- 미들웨어 등록 순서를 OkHttp와 같다고 착각하는 경우.
- `BaseHTTPMiddleware`로 스트리밍 응답을 감싸서 응답이 버퍼링되는 경우.
- 인증처럼 엔드포인트별로 달라야 하는 로직을 전역 미들웨어에 넣어서 예외 처리가 복잡해지는 경우.

<br>

# 14. 질문 정리

## 14.1 OkHttp Interceptor와 Authenticator의 차이는?

Interceptor는 모든 요청/응답에 끼어들 수 있고, Authenticator는 서버가 401을 반환했을 때만 호출된다. Authenticator는 새 요청을 반환해서 재시도할 수 있고, `null`을 반환하면 포기한다. 토큰 갱신은 Authenticator가 적합하다.

## 14.2 Application Interceptor와 Network Interceptor는 언제 구분해서 쓰나?

기본은 Application Interceptor다. 리다이렉트 중간 요청, 실제 전송된 헤더, 네트워크 레벨 압축 확인이 필요할 때만 Network Interceptor를 쓴다. Network Interceptor는 캐시 히트 시 호출되지 않는다.

## 14.3 동시에 여러 요청이 401을 받으면 어떻게 처리하나?

Authenticator 안에서 락으로 갱신을 직렬화하고, 락을 얻은 후 현재 저장된 토큰과 요청에 쓰인 토큰을 비교한다. 이미 다른 스레드가 갱신했다면 새 토큰으로 바로 재시도해서 갱신 API 호출을 한 번으로 줄인다.

## 14.4 Interceptor의 등록 순서가 왜 중요한가?

요청은 등록 순서대로, 응답은 역순으로 지나간다. 로깅 Interceptor를 어디에 두느냐에 따라 로그에 찍히는 요청 내용이 달라지고, 인증과 재시도 순서에 따라 동작도 달라진다.

## 14.5 안드로이드에서 미들웨어라는 말은 어떤 의미로 쓰이나?

OS 구조에서는 커널과 앱 사이의 Framework/ART/Native Library 계층, 네트워크에서는 Interceptor, 상태 관리에서는 Intent와 Reducer 사이의 처리 계층을 의미한다. 공통점은 양쪽이 몰라도 되는 공통 처리를 중간에서 담당한다는 것이다.

## 14.6 Binder는 왜 필요하고 어떤 한계가 있나?

앱마다 프로세스가 격리되어 있어서 프로세스 간 호출에 IPC가 필요하다. Binder는 커널 드라이버를 통해 한 번의 복사로 효율적으로 데이터를 전달하고 호출자 UID를 보장한다. 트랜잭션 버퍼가 프로세스당 약 1MB로 제한되어 큰 데이터를 전달하면 `TransactionTooLargeException`이 발생한다.

## 14.7 FastAPI의 Middleware와 Depends는 어떻게 구분하나?

Middleware는 모든 요청에 공통으로 적용되는 로직(로깅, CORS, 공통 헤더), Depends는 특정 엔드포인트에 필요한 의존성(인증된 사용자, DB 세션)에 쓴다.

<br>

# 정리

- 미들웨어는 양쪽 사이에 끼어서 공통 처리를 담당하는 중간 계층이다.
- 안드로이드에서는 OS 계층(Framework/ART/HAL), 네트워크(OkHttp Interceptor), 상태 관리(MVI Middleware) 세 가지 의미로 쓰인다.
- OS 계층에서 앱은 Binder IPC를 통해 System Server의 서비스를 호출한다.
- OkHttp는 `proceed()`를 기준으로 요청은 등록 순서대로, 응답은 역순으로 흐른다.
- 토큰 갱신은 Interceptor보다 Authenticator가 적합하며, 갱신 API는 Authenticator가 없는 별도 클라이언트로 호출한다.
- Application Interceptor가 기본이고, 네트워크 레벨 정보가 필요할 때만 Network Interceptor를 쓴다.
- 안드로이드 MVI에서는 별도 미들웨어보다 ViewModel이나 BaseViewModel에서 처리하는 경우가 많고, 필요할 때만 Store 미들웨어를 도입한다.
- FastAPI 미들웨어는 구조가 OkHttp와 같지만 등록 순서는 반대(나중에 등록한 것이 바깥쪽)다.
- 인증처럼 엔드포인트별로 다른 로직은 미들웨어보다 `Depends`가 적합하다.
- 미들웨어 체인은 Chain of Responsibility, Decorator 패턴이며 AOP와 목적이 같다.
