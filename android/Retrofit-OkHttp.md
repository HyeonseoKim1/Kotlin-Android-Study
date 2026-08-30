# Retrofit + OkHttp

<br>

## 목차

1. Retrofit과 OkHttp란 무엇인가
2. OkHttpClient 기본 설정
3. Retrofit 인스턴스 생성하기
4. API 인터페이스 정의하기
5. Converter로 JSON 파싱하기
6. Coroutines로 API 호출하기
7. Interceptor 활용하기
8. 타임아웃과 재시도 설정
9. 에러 처리하기
10. Response<T>와 원시 반환 타입
11. OkHttp 캐시 활용
12. 파일 업로드 (Multipart)
13. Hilt와 Retrofit/OkHttp 연동
14. 주의사항과 자주 하는 실수
15. 정리

<br>

## Retrofit과 OkHttp란 무엇인가

OkHttp는 실제로 네트워크 소켓을 열고 HTTP 요청/응답을 주고받는 저수준 HTTP 클라이언트 라이브러리다. 커넥션 풀링, 요청 재시도, 캐싱, GZIP 압축 해제 같은 네트워킹의 핵심 기능을 담당한다. Retrofit은 이 OkHttp 위에 얹혀서, "인터페이스에 어노테이션을 붙이면 자동으로 HTTP 요청 코드를 생성해주는" 타입 세이프(type-safe) API 클라이언트 역할을 한다.

즉 관계를 정리하면: **OkHttp가 실제로 통신을 담당하고, Retrofit은 그 위에서 개발자가 SQL 대신 인터페이스를 작성하듯 API를 선언적으로 정의할 수 있게 해주는 계층**이다. Retrofit 내부에서는 결국 OkHttp의 `Call` 객체를 만들어서 실행한다.

```kotlin
// build.gradle.kts (Module: app)
dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
}
```

| 항목 | OkHttp | Retrofit |
|---|---|---|
| 역할 | 실제 HTTP 통신 (소켓, 커넥션 풀) | API 호출을 인터페이스로 추상화 |
| 사용 형태 | `Request`/`Response`를 직접 다룸 | 어노테이션 기반 인터페이스 |
| 의존 관계 | 독립적으로 사용 가능 | 내부적으로 OkHttp를 클라이언트로 사용 |
| JSON 파싱 | 직접 처리 필요 | Converter(Gson, Moshi 등)로 자동 처리 |

**실전 팁**: 두 라이브러리를 별개로 배우려 하지 말고 "OkHttp는 엔진, Retrofit은 그 엔진 위의 운전대"라는 비유로 이해하면 각 API가 왜 존재하는지 헷갈리지 않는다.

실제로 Retrofit만 단독으로 추가해도 OkHttp는 전이 의존성(transitive dependency)으로 함께 딸려온다. `Retrofit.Builder().client()`에 아무것도 지정하지 않으면 Retrofit이 기본 설정의 `OkHttpClient`를 내부적으로 생성해서 사용한다. 다만 타임아웃이나 인터셉터 같은 커스터마이징이 필요하다면 직접 `OkHttpClient`를 구성해서 넘겨줘야 한다.

```kotlin
// client()를 지정하지 않은 경우 - Retrofit이 기본 OkHttpClient를 자동 생성
val defaultRetrofit = Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()
```

<br>

## OkHttpClient 기본 설정

Retrofit을 만들기 전에 먼저 `OkHttpClient`를 구성한다. 타임아웃, 인터셉터, 캐시 등 네트워킹과 관련된 대부분의 설정은 이 `OkHttpClient`에서 이뤄진다.

```kotlin
val okHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()
```

```kotlin
// 로깅 인터셉터를 추가한 디버그용 클라이언트
val debugOkHttpClient: OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    )
    .connectTimeout(15, TimeUnit.SECONDS)
    .build()
```

| 설정 항목 | 메서드 | 설명 |
|---|---|---|
| 연결 타임아웃 | `connectTimeout()` | 서버와 커넥션을 맺는 데 걸리는 최대 시간 |
| 읽기 타임아웃 | `readTimeout()` | 응답 데이터를 읽는 데 걸리는 최대 시간 |
| 쓰기 타임아웃 | `writeTimeout()` | 요청 데이터를 보내는 데 걸리는 최대 시간 |
| 연결 실패 재시도 | `retryOnConnectionFailure()` | 일시적 네트워크 오류 시 자동 재시도 여부 |

**실전 팁**: `HttpLoggingInterceptor`의 `Level.BODY`는 요청/응답 본문까지 전부 로그로 찍기 때문에 디버깅에는 매우 유용하지만, 민감한 정보(토큰, 개인정보)가 로그캣에 그대로 노출될 수 있다. 릴리즈 빌드에서는 반드시 `BuildConfig.DEBUG` 조건으로 감싸서 제외해야 한다.

<br>

## Retrofit 인스턴스 생성하기

`Retrofit.Builder()`로 baseUrl, OkHttpClient, Converter를 조합해 인스턴스를 만든다. 이 인스턴스는 앱 전체에서 하나만 유지하는 것이 일반적이며, Room의 `Database`처럼 싱글톤으로 관리한다.

```kotlin
object RetrofitProvider {

    @Volatile
    private var INSTANCE: Retrofit? = null

    fun getInstance(okHttpClient: OkHttpClient): Retrofit {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Retrofit.Builder()
                .baseUrl("https://api.example.com/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .also { INSTANCE = it }
        }
    }
}

// API 인터페이스 구현체 생성
val apiService: ApiService = RetrofitProvider
    .getInstance(okHttpClient)
    .create(ApiService::class.java)
```

| 설정 항목 | 메서드 | 설명 |
|---|---|---|
| 기본 URL | `.baseUrl()` | 반드시 `/`로 끝나야 하며, 이후 `@GET` 등의 경로가 이어붙는다 |
| HTTP 클라이언트 | `.client()` | 앞서 구성한 `OkHttpClient` 지정 |
| 컨버터 | `.addConverterFactory()` | JSON ↔ Kotlin 객체 변환 담당 |
| 인터페이스 구현체 생성 | `.create()` | 어노테이션 기반 인터페이스를 실제 구현체로 변환 |

**실전 팁**: `baseUrl`은 반드시 슬래시(`/`)로 끝나야 한다. 빠뜨리면 `IllegalArgumentException: baseUrl must end in /`가 발생하는데, 처음 접하는 사람이 자주 겪는 실수 중 하나다.

<br>

## API 인터페이스 정의하기

Retrofit의 핵심은 인터페이스에 HTTP 메서드 어노테이션을 붙여 API를 선언하는 것이다. `@GET`, `@POST`, `@PUT`, `@DELETE` 등을 사용하며, 경로 변수는 `@Path`, 쿼리 파라미터는 `@Query`, 요청 본문은 `@Body`로 표현한다.

```kotlin
interface ApiService {

    @GET("users")
    suspend fun getUsers(
        @Query("page") page: Int,
        @Query("size") size: Int = 20
    ): List<UserDto>

    @GET("users/{id}")
    suspend fun getUser(@Path("id") userId: Long): UserDto

    @POST("users")
    suspend fun createUser(@Body request: CreateUserRequest): UserDto

    @PUT("users/{id}")
    suspend fun updateUser(
        @Path("id") userId: Long,
        @Body request: UpdateUserRequest
    ): UserDto

    @DELETE("users/{id}")
    suspend fun deleteUser(@Path("id") userId: Long)

    @Headers("Cache-Control: no-cache")
    @GET("users/{id}/profile")
    suspend fun getProfile(@Path("id") userId: Long): ProfileDto
}

data class UserDto(val id: Long, val name: String, val email: String)
data class CreateUserRequest(val name: String, val email: String)
data class UpdateUserRequest(val name: String)
```

| 어노테이션 | 용도 |
|---|---|
| `@GET`, `@POST`, `@PUT`, `@DELETE` | HTTP 메서드 지정 |
| `@Path` | URL 경로의 `{변수}` 부분 치환 |
| `@Query` | 쿼리 스트링(`?key=value`) 지정 |
| `@Body` | 요청 본문(JSON)으로 직렬화될 객체 지정 |
| `@Headers` | 정적 헤더 추가 |

**실전 팁**: 요청/응답 DTO 이름에 `Dto`나 `Request`/`Response` 접미사를 일관되게 붙여두면, 도메인 모델(Repository 계층에서 쓰는 순수 데이터 클래스)과 네트워크 계층 모델을 명확히 구분할 수 있어 나중에 API 스펙이 바뀌어도 영향 범위를 좁힐 수 있다.

<br>

## Converter로 JSON 파싱하기

서버 응답(JSON)을 Kotlin 객체로, 요청 객체를 JSON으로 자동 변환해주는 것이 Converter의 역할이다. Gson, Moshi, kotlinx.serialization 등 여러 선택지가 있으며, 최근에는 코틀린 네이티브인 kotlinx.serialization을 선호하는 추세다.

```kotlin
// Gson 방식
val gsonRetrofit = Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

// kotlinx.serialization 방식
val json = Json { ignoreUnknownKeys = true }

val kotlinxRetrofit = Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .build()

@Serializable
data class UserDto(
    val id: Long,
    val name: String,
    @SerialName("email_address") val email: String
)
```

| Converter | 특징 |
|---|---|
| Gson | 가장 널리 쓰였음, 리플렉션 기반, 설정이 간단 |
| Moshi | Gson보다 가볍고 빠름, Kotlin 지원이 더 자연스러움 |
| kotlinx.serialization | 코틀린 공식 직렬화 라이브러리, 컴파일 타임 코드 생성이라 리플렉션 오버헤드 없음 |

**실전 팁**: 신규 프로젝트라면 kotlinx.serialization을 우선 고려하는 것을 추천한다. `@Serializable` 어노테이션만 붙이면 되고, 리플렉션을 쓰지 않아 성능도 좋으며 코틀린 멀티플랫폼(KMP)으로 확장할 때도 유리하다.

<br>

## Coroutines로 API 호출하기

Retrofit은 인터페이스 메서드를 `suspend fun`으로 선언하는 것만으로 Coroutines를 지원한다. 별도의 `Call.enqueue()` 콜백 코드를 작성할 필요 없이, 동기 코드처럼 순차적으로 작성할 수 있다.

```kotlin
class UserRepository(private val apiService: ApiService) {

    suspend fun fetchUser(userId: Long): Result<UserDto> {
        return try {
            val user = apiService.getUser(userId)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchUsers(page: Int): List<UserDto> {
        return apiService.getUsers(page = page, size = 20)
    }
}

// ViewModel에서 호출
class UserViewModel(private val repository: UserRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadUser(userId: Long) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.fetchUser(userId)
                .onSuccess { _uiState.value = UiState.Success(it) }
                .onFailure { _uiState.value = UiState.Error(it.message.orEmpty()) }
        }
    }
}
```

| 방식 | 스레드 처리 | 비고 |
|---|---|---|
| `suspend fun` | Retrofit이 자동으로 백그라운드 실행 후 결과 반환 | 현재 표준 방식 |
| `Call<T>` + `enqueue()` | 콜백 기반, 직접 콜백 스레드 처리 | 구버전 코드, 콜백 지옥 유발 |
| RxJava (`Single`, `Observable`) | RxJava 스케줄러로 처리 | RxJava 기반 레거시 프로젝트 |

**실전 팁**: `suspend fun`으로 선언된 Retrofit 메서드는 내부적으로 OkHttp의 디스패처 스레드 풀에서 실행되므로, 호출부에서 별도로 `Dispatchers.IO`를 지정할 필요가 없다. `viewModelScope.launch { }` 안에서 바로 호출해도 메인 스레드가 블로킹되지 않는다.

<br>

## Interceptor 활용하기

Interceptor는 모든 요청/응답이 지나가는 지점에 공통 로직을 끼워 넣을 수 있는 OkHttp의 핵심 기능이다. 인증 토큰 헤더 추가, 로깅, 공통 에러 처리 등에 자주 사용된다.

```kotlin
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val original = chain.request()

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

class RetryInterceptor(private val maxRetry: Int = 2) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var response: Response = chain.proceed(chain.request())

        while (!response.isSuccessful && attempt < maxRetry) {
            attempt++
            response.close()
            response = chain.proceed(chain.request())
        }
        return response
    }
}

val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(AuthInterceptor { tokenStorage.getAccessToken() })
    .addInterceptor(RetryInterceptor())
    .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
    .build()
```

| Interceptor 종류 | 등록 메서드 | 실행 시점 |
|---|---|---|
| Application Interceptor | `addInterceptor()` | 캐시/리다이렉트 이전, 요청당 1회 |
| Network Interceptor | `addNetworkInterceptor()` | 실제 네트워크 계층 직전, 리다이렉트 시 여러 번 호출될 수 있음 |

**실전 팁**: 여러 Interceptor를 등록하는 순서가 실행 순서에 영향을 준다. 인증 헤더 추가 → 로깅 순서로 등록해야 로그에 실제로 전송된 헤더가 찍힌다. 반대로 등록하면 헤더가 추가되기 전 상태가 로그에 남아 디버깅할 때 혼란스러울 수 있다.

<br>

## 타임아웃과 재시도 설정

네트워크 상태가 불안정한 모바일 환경에서는 타임아웃과 재시도 전략을 명확히 세워두는 것이 중요하다. 무작정 긴 타임아웃은 사용자를 오래 기다리게 하고, 너무 짧은 타임아웃은 정상 요청도 실패로 처리해버린다.

```kotlin
val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)   // 응답이 느린 API는 조금 더 길게
    .writeTimeout(15, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)   // 전체 요청 흐름의 총 제한 시간
    .retryOnConnectionFailure(true)
    .build()

// 특정 API 호출에만 다른 타임아웃을 적용하고 싶을 때
suspend fun fetchLargeFile(apiService: ApiService, url: String) {
    val customClient = okHttpClient.newBuilder()
        .readTimeout(2, TimeUnit.MINUTES)
        .build()
    // customClient로 별도의 Retrofit 인스턴스를 구성해 사용
}
```

| 타임아웃 종류 | 기본값 | 의미 |
|---|---|---|
| `connectTimeout` | 10초 | TCP 커넥션 수립까지의 제한 시간 |
| `readTimeout` | 10초 | 응답 데이터를 읽는 동안의 제한 시간 |
| `writeTimeout` | 10초 | 요청 데이터를 전송하는 동안의 제한 시간 |
| `callTimeout` | 0(무제한) | 요청 시작부터 응답 완료까지 전체 제한 시간 |

**실전 팁**: `newBuilder()`를 활용하면 기존 `OkHttpClient`의 설정(인터셉터 등)은 그대로 유지하면서 특정 값만 오버라이드한 새 클라이언트를 만들 수 있다. 파일 업로드/다운로드처럼 유독 오래 걸리는 API에만 별도로 긴 타임아웃을 적용할 때 유용하다.

<br>

## 에러 처리하기

Retrofit + Coroutines 조합에서는 크게 두 종류의 예외를 구분해서 처리해야 한다: 네트워크 자체가 실패한 경우(`IOException`)와, 서버가 응답은 했지만 실패 상태 코드를 준 경우(`HttpException`)다.

```kotlin
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val code: Int?, val message: String) : NetworkResult<Nothing>()
}

suspend fun <T> safeApiCall(apiCall: suspend () -> T): NetworkResult<T> {
    return try {
        NetworkResult.Success(apiCall())
    } catch (e: HttpException) {
        // 서버가 4xx, 5xx 등 에러 상태 코드를 응답한 경우
        NetworkResult.Error(code = e.code(), message = e.message())
    } catch (e: IOException) {
        // 네트워크 연결 자체가 실패한 경우 (타임아웃, 연결 끊김 등)
        NetworkResult.Error(code = null, message = "네트워크 연결을 확인해주세요")
    }
}

class UserRepository(private val apiService: ApiService) {
    suspend fun fetchUser(userId: Long): NetworkResult<UserDto> =
        safeApiCall { apiService.getUser(userId) }
}
```

| 예외 타입 | 발생 상황 | 처리 방향 |
|---|---|---|
| `HttpException` | 서버가 응답했지만 상태 코드가 실패(4xx/5xx) | 응답 코드/본문을 기반으로 구체적 에러 메시지 노출 |
| `IOException` (`SocketTimeoutException` 등 포함) | 연결 실패, 타임아웃 | "네트워크 확인" 안내, 재시도 유도 |
| `SerializationException`/`JsonSyntaxException` | 응답 본문 파싱 실패 | 서버 스펙 불일치 가능성, 로깅 후 별도 처리 |

**실전 팁**: `HttpException`의 `e.response()?.errorBody()?.string()`으로 서버가 내려준 에러 응답 본문을 직접 파싱할 수 있다. 서버가 에러 메시지를 JSON으로 내려주는 경우, 이 값을 파싱해 사용자에게 좀 더 구체적인 안내를 보여줄 수 있다.

에러 상황을 더 세분화해서 사용자에게 다른 메시지를 보여주고 싶다면, HTTP 상태 코드 범위별로 분기하는 것도 흔한 패턴이다.

```kotlin
fun mapErrorMessage(code: Int?): String = when (code) {
    401 -> "로그인이 만료되었습니다. 다시 로그인해주세요"
    403 -> "접근 권한이 없습니다"
    404 -> "요청한 정보를 찾을 수 없습니다"
    in 500..599 -> "서버에 일시적인 문제가 발생했습니다"
    else -> "알 수 없는 오류가 발생했습니다"
}
```

<br>

## Response<T>와 원시 반환 타입

Retrofit API 메서드는 반환 타입을 두 가지 방식으로 선언할 수 있다: 도메인 객체를 바로 반환하는 방식과, `Response<T>`로 감싸서 HTTP 상태 코드와 헤더까지 함께 받는 방식이다.

```kotlin
interface ApiService {
    // 방식 1: 성공 시 바로 객체 반환, 실패 시 예외 발생
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: Long): UserDto

    // 방식 2: Response로 감싸서 상태 코드, 헤더까지 직접 확인
    @GET("users/{id}")
    suspend fun getUserWithResponse(@Path("id") id: Long): Response<UserDto>
}

suspend fun fetchUserManually(apiService: ApiService, id: Long) {
    val response = apiService.getUserWithResponse(id)
    if (response.isSuccessful) {
        val user = response.body()
        val rateLimit = response.headers()["X-RateLimit-Remaining"]
    } else {
        val errorBody = response.errorBody()?.string()
    }
}
```

| 반환 방식 | 장점 | 단점 |
|---|---|---|
| 도메인 객체 직접 반환 | 코드가 간결함 | 상태 코드/헤더 확인 불가, 예외 처리로만 실패를 감지 |
| `Response<T>` | 상태 코드, 헤더까지 접근 가능 | `isSuccessful` 체크를 매번 직접 해야 함 |

**실전 팁**: 대부분의 화면 로직에서는 도메인 객체를 직접 반환받고 `try-catch`로 예외를 처리하는 방식이 더 간결하다. `Response<T>`는 응답 헤더(페이지네이션 토큰, Rate Limit 정보 등)가 실제로 필요한 특정 API에서만 선택적으로 사용하는 것을 추천한다.

<br>

## OkHttp 캐시 활용

OkHttp는 HTTP 표준 캐시 정책을 그대로 지원한다. 서버가 `Cache-Control` 헤더를 내려주면 OkHttp가 이를 해석해서 동일 요청에 대해 네트워크를 다시 타지 않고 캐시된 응답을 반환할 수 있다.

```kotlin
val cacheSize = 10L * 1024 * 1024 // 10MB
val cache = Cache(File(context.cacheDir, "http_cache"), cacheSize)

val okHttpClient = OkHttpClient.Builder()
    .cache(cache)
    .addInterceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Cache-Control", "public, max-age=60") // 서버 헤더가 없을 때 강제 지정
            .build()
        chain.proceed(request)
    }
    .build()

// 오프라인일 때는 캐시라도 사용하도록 강제하는 인터셉터
class OfflineCacheInterceptor(private val isOnline: () -> Boolean) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if (!isOnline()) {
            request = request.newBuilder()
                .header("Cache-Control", "public, only-if-cached, max-stale=86400")
                .build()
        }
        return chain.proceed(request)
    }
}
```

| 캐시 관련 헤더 | 의미 |
|---|---|
| `Cache-Control: max-age=60` | 60초 동안은 네트워크 재요청 없이 캐시 사용 |
| `Cache-Control: no-cache` | 캐시를 저장은 하되 매번 서버에 유효성 재검증 |
| `Cache-Control: only-if-cached` | 캐시가 없으면 요청 자체를 실패시킴(오프라인 대응) |

**실전 팁**: OkHttp의 HTTP 캐시는 GET 요청에만 적용되며, 서버가 적절한 `Cache-Control` 헤더를 내려주지 않으면 사실상 동작하지 않는다. 서버 스펙을 바꿀 수 없는 상황이라면, 위 예시처럼 Interceptor에서 헤더를 강제로 덮어써서 캐시 정책을 클라이언트 쪽에서 정의할 수 있다.

<br>

## 파일 업로드 (Multipart)

이미지나 파일을 서버로 전송할 때는 `multipart/form-data` 형식을 사용하며, Retrofit에서는 `@Multipart`와 `@Part` 어노테이션으로 이를 표현한다.

```kotlin
interface ApiService {
    @Multipart
    @POST("upload")
    suspend fun uploadProfileImage(
        @Part image: MultipartBody.Part,
        @Part("description") description: RequestBody
    ): UploadResponse
}

fun createImagePart(file: File): MultipartBody.Part {
    val requestBody = file.asRequestBody("image/jpeg".toMediaType())
    return MultipartBody.Part.createFormData(
        name = "image",
        filename = file.name,
        body = requestBody
    )
}

class UploadRepository(private val apiService: ApiService) {
    suspend fun uploadImage(file: File, description: String): UploadResponse {
        val imagePart = createImagePart(file)
        val descriptionBody = description.toRequestBody("text/plain".toMediaType())
        return apiService.uploadProfileImage(imagePart, descriptionBody)
    }
}
```

| 요소 | 어노테이션 | 설명 |
|---|---|---|
| Multipart 요청 표시 | `@Multipart` | 요청 전체가 multipart/form-data임을 선언 |
| 파일 파트 | `@Part` + `MultipartBody.Part` | 실제 파일 데이터 |
| 텍스트 파트 | `@Part("key")` + `RequestBody` | 파일과 함께 전송할 추가 텍스트 필드 |

**실전 팁**: 업로드 진행률을 UI에 보여줘야 한다면, `RequestBody`를 직접 커스텀 구현해서 `writeTo()`에서 바이트를 쓸 때마다 진행률 콜백을 호출하는 방식으로 구현할 수 있다. Retrofit 자체는 업로드 진행률 API를 기본 제공하지 않는다.

<br>

## Hilt와 Retrofit/OkHttp 연동

실무에서는 `OkHttpClient`, `Retrofit`, API 인터페이스 구현체를 모두 Hilt `@Module`에 등록해서 관리한다. Room과 동일한 패턴으로, `@Provides`와 `@Singleton`을 조합해 앱 전체에서 하나의 인스턴스를 공유한다.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.example.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }
}

// Repository에 주입
class UserRepository @Inject constructor(
    private val apiService: ApiService
)
```

| 등록 대상 | 스코프 | 이유 |
|---|---|---|
| `OkHttpClient` | `@Singleton` | 커넥션 풀 재사용, 매번 새로 만들면 리소스 낭비 |
| `Retrofit` | `@Singleton` | baseUrl, Converter 등 설정이 앱 전체에서 동일 |
| API 인터페이스 구현체 | `@Singleton` | 상태를 갖지 않는 가벼운 객체, 재사용에 문제 없음 |

**실전 팁**: 서로 다른 baseUrl을 가진 API를 여러 개 써야 한다면(예: 자체 서버 API와 외부 결제 서비스 API), `@Qualifier` 어노테이션으로 커스텀 태그를 만들어 여러 `Retrofit`/`OkHttpClient` 인스턴스를 구분해서 주입해야 한다.

<br>

## 주의사항과 자주 하는 실수

1. `baseUrl`이 `/`로 끝나지 않아 `IllegalArgumentException`을 만나는 경우가 흔하다. 항상 마지막에 슬래시를 붙여야 한다.
2. `OkHttpClient`를 API 호출마다, 혹은 화면마다 새로 생성하면 커넥션 풀이 재사용되지 않아 성능이 크게 저하된다. 앱 전체에서 하나의 인스턴스를 공유해야 한다.
3. `HttpLoggingInterceptor`의 `Level.BODY`를 릴리즈 빌드에 그대로 남겨두면 토큰, 개인정보가 로그캣에 노출될 수 있다. 반드시 디버그 빌드에서만 활성화해야 한다.
4. 네트워크 예외(`IOException`)와 서버 에러 응답(`HttpException`)을 구분하지 않고 하나의 `catch (e: Exception)`으로만 처리하면, 사용자에게 "네트워크 연결을 확인하세요"와 "서버 오류입니다"를 구분해서 안내할 수 없다.
5. `suspend fun` API 메서드 안에서 굳이 `withContext(Dispatchers.IO)`로 한 번 더 감싸는 실수를 한다. Retrofit이 이미 백그라운드 스레드에서 실행하므로 중복이다.
6. Interceptor 등록 순서를 고려하지 않아, 인증 헤더가 붙기 전 상태가 로그에 찍히거나 재시도 로직이 의도한 순서로 동작하지 않는 경우가 있다.
7. `Cache-Control` 헤더 없이 GET 요청에 캐시가 자동으로 동작할 거라 오해하는 경우가 많다. 서버가 헤더를 내려주지 않으면 OkHttp 캐시는 사실상 작동하지 않는다.
8. Multipart 업로드 시 `RequestBody`의 MIME 타입을 잘못 지정하면(`text/plain`을 이미지에 사용하는 등) 서버가 파일을 정상적으로 처리하지 못할 수 있다.
9. 타임아웃 값을 지나치게 짧게 잡아서, 정상적인 느린 응답까지 실패로 처리되는 경우가 있다. API 특성에 맞게 타임아웃을 조정해야 한다.
10. 여러 API를 호출해야 하는데 baseUrl이 다른 경우, `@Qualifier` 없이 하나의 `Retrofit` 인스턴스로 모두 처리하려다 경로 조합이 꼬이는 실수를 한다.

<br>

## 정리

OkHttp가 실제 네트워크 통신을 담당하는 엔진이라면, Retrofit은 그 위에서 어노테이션 기반 인터페이스로 API를 선언적으로 정의할 수 있게 해주는 계층이다. `OkHttpClient`에서 타임아웃, 인터셉터, 캐시 같은 공통 네트워킹 정책을 설정하고, 이를 바탕으로 만든 `Retrofit` 인스턴스에 Converter를 연결해 JSON과 Kotlin 객체를 자동 매핑한다. `suspend fun`으로 API 메서드를 선언하면 별도 콜백 코드 없이 Coroutines와 자연스럽게 연동되고, `HttpException`과 `IOException`을 구분해서 처리하면 사용자에게 더 정확한 에러 안내를 줄 수 있다. 실무에서는 Hilt로 `OkHttpClient`, `Retrofit`, API 인터페이스를 싱글톤으로 등록해 관리하는 것이 표준적인 패턴이며, 여기에 Interceptor로 인증/로깅/재시도를, 캐시 설정으로 오프라인 대응을 더해가는 식으로 점진적으로 네트워킹 계층을 완성해나가는 것을 추천한다.
