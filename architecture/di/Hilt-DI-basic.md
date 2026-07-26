# Hilt DI 기초

<br>

## 목차

1. DI(Dependency Injection)란
2. 왜 DI가 필요한가 (비유로 이해하기)
3. Hilt란
4. Hilt 컴포넌트 계층 구조
5. 기본 설정
6. 주요 어노테이션
7. 실전 예제
8. Qualifier로 같은 타입 구분하기
9. 테스트에서 Hilt 사용하기
10. 자주 만나는 에러와 해결법
11. 헷갈리기 쉬운 부분

<br>

# 1. DI(Dependency Injection)란

객체가 필요로 하는 의존성(다른 객체)을 직접 생성하지 않고, 외부에서 주입받는 설계 패턴이다.

```kotlin
// DI를 적용하지 않은 경우
class UserRepository {
    private val api = RetrofitClient.apiService // 직접 생성 -> 강한 결합
}

// DI를 적용한 경우
class UserRepository(private val api: ApiService) // 외부에서 주입
```

DI를 쓰면 얻는 이점

- 테스트 시 mock 객체로 쉽게 교체 가능
- 클래스 간 결합도 감소
- 객체 생성 책임을 한 곳(컨테이너)으로 위임

<br>

# 2. 왜 DI가 필요한가 (비유로 이해하기)

커피를 만드는 상황으로 생각해보면 이해하기 쉽다.

- DI가 없는 경우: 바리스타(클래스)가 직접 원두를 재배하고, 로스팅하고, 그라인딩까지 다 한다. 바리스타를 바꾸려면 원두 농장 관리법부터 다시 알아야 한다.
- DI가 있는 경우: 바리스타는 "원두"라는 재료만 받아서 커피를 만든다. 원두가 어디서 왔는지는 신경 쓰지 않는다. 원두 공급처(구현체)를 바꿔도 바리스타 코드는 그대로다.

여기서 "원두 공급처를 결정해서 갖다주는 사람"이 바로 Hilt다. 클래스는 무엇이 필요한지(원두)만 선언하고, Hilt가 그것을 어떻게 만들지, 언제 만들지, 몇 개나 만들지를 대신 관리해준다.

<br>

# 3. Hilt란

Google이 만든 Android 전용 DI 라이브러리로, Dagger를 기반으로 하되 보일러플레이트를 크게 줄인 것이다.

- Dagger: 범용 DI 프레임워크. 강력하지만 Component, SubComponent를 손으로 다 설계해야 해서 설정이 복잡하다.
- Hilt: Dagger 위에 있는 얇은 레이어. Activity, Fragment, ViewModel처럼 Android가 이미 정해놓은 생명주기에 맞춰 표준 Component 구조를 자동으로 만들어준다. 즉 "Android용으로 미리 세팅된 Dagger"라고 보면 된다.

쉽게 말해: Dagger는 레고를 부품 하나하나부터 설계해야 하는 것이고, Hilt는 Android 앱에 맞는 레고 세트(설명서 포함)를 이미 만들어준 것이다.

<br>

# 4. Hilt 컴포넌트 계층 구조

Hilt는 Android의 생명주기에 맞춰 컴포넌트를 계층 구조로 미리 정의해뒀다. 위에서 아래로 갈수록 생명주기가 짧아진다.

```
SingletonComponent          (Application 생명주기 - 앱 전체)
    └─ ActivityRetainedComponent   (ViewModel 생명주기)
          └─ ViewModelComponent    (ViewModel 생명주기)
    └─ ActivityComponent           (Activity 생명주기)
          └─ FragmentComponent     (Fragment 생명주기)
                └─ ViewComponent   (View 생명주기)
    └─ ServiceComponent            (Service 생명주기)
```

이게 왜 중요하냐면, `@InstallIn`으로 어떤 컴포넌트에 모듈을 설치하느냐에 따라 그 의존성이 "얼마나 오래 살아있는지"가 결정되기 때문이다.

- `SingletonComponent`에 설치 + `@Singleton` -> 앱이 켜져 있는 동안 인스턴스 1개만 유지
- `ActivityComponent`에 설치 + `@ActivityScoped` -> 해당 Activity가 살아있는 동안만 인스턴스 1개 유지, Activity 끝나면 사라짐

쉽게 비유하면, SingletonComponent는 "회사 공용 물건"(퇴사할 때까지 계속 있음)이고, ActivityComponent는 "회의실에서만 쓰는 비품"(회의 끝나면 치워짐)이라고 생각하면 된다.

<br>

# 5. 기본 설정

```kotlin
// build.gradle.kts (project)
plugins {
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}

// build.gradle.kts (module)
plugins {
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt") // 또는 ksp 사용 가능
}

dependencies {
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")
}
```

Application 클래스에 `@HiltAndroidApp`을 붙여야 Hilt가 코드 생성을 시작한다. 이걸 빼먹으면 다른 어노테이션이 다 있어도 아무것도 동작하지 않는다.

```kotlin
@HiltAndroidApp
class MyApplication : Application()
```

<br>

# 6. 주요 어노테이션

처음 보면 어노테이션이 너무 많아서 헷갈리는데, 역할별로 묶어서 외우면 쉽다.

**그룹 1: "여기서 Hilt를 켜줘" (진입점 표시)**

| 어노테이션 | 위치 | 역할 |
|---|---|---|
| `@HiltAndroidApp` | Application 클래스 | 앱 전체 Hilt 컨테이너 생성 시작점 |
| `@AndroidEntryPoint` | Activity, Fragment, Service 등 | 이 클래스에 주입 기능을 켠다 |
| `@HiltViewModel` | ViewModel | ViewModel에 주입 기능을 켠다 |

**그룹 2: "이거 필요해요" (주입 요청)**

| 어노테이션 | 위치 | 역할 |
|---|---|---|
| `@Inject` | 생성자 or 필드 | 이 자리를 Hilt가 채워달라는 표시 |

**그룹 3: "이렇게 만들어줘" (제공 방법 정의)**

| 어노테이션 | 위치 | 역할 |
|---|---|---|
| `@Module` | object/class | "이 안에 만드는 방법들을 모아뒀다"는 표시 |
| `@InstallIn` | Module | 이 모듈을 어느 생명주기 컴포넌트에 설치할지 |
| `@Provides` | Module 내 함수 | 직접 코드를 못 고치는 타입(외부 라이브러리, 빌더 패턴)을 만드는 법 |
| `@Binds` | Module 내 추상 함수 | "인터페이스 A가 필요하면 구현체 B를 써라"라고 연결만 해주는 것 (더 가볍고 빠름) |

**그룹 4: "인스턴스 하나만 써" (수명 관리)**

| 어노테이션 | 위치 | 역할 |
|---|---|---|
| `@Singleton` | 클래스 or Provides 함수 | 앱 전역 인스턴스 1개만 유지 (SingletonComponent 전용) |
| `@ActivityScoped` | 클래스 or Provides 함수 | 해당 Activity 안에서만 인스턴스 1개 유지 |

<br>

# 7. 실전 예제

```kotlin
// 1) 생성자 주입 (가장 기본, 내가 만든 클래스일 때)
class UserRepository @Inject constructor(
    private val api: ApiService,
    private val dao: UserDao
) {
    suspend fun getUser(id: String) = api.fetchUser(id)
}

// 2) 인터페이스 -> 구현체 바인딩
// "AuthManager가 필요하면 AuthManagerImpl을 줘"라는 뜻
interface AuthManager {
    fun isLoggedIn(): Boolean
}

class AuthManagerImpl @Inject constructor() : AuthManager {
    override fun isLoggedIn(): Boolean = true
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    abstract fun bindAuthManager(impl: AuthManagerImpl): AuthManager
}

// 3) Retrofit처럼 내가 직접 생성자를 못 고치는 외부 객체는 @Provides
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.example.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }
}

// 4) ViewModel 주입
@HiltViewModel
class UserViewModel @Inject constructor(
    private val repository: UserRepository
) : ViewModel() {
    // ...
}

// 5) Activity/Fragment에서 사용
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: UserViewModel by viewModels()
}
```

전체 흐름을 그림으로 그리면 이렇다.

```
@Module (NetworkModule) --- "Retrofit은 이렇게 만든다" 정의
        │
        ▼
@Inject constructor(api: ApiService) --- UserRepository가 ApiService를 요청
        │
        ▼
@HiltViewModel UserViewModel(repository) --- ViewModel이 Repository를 요청
        │
        ▼
@AndroidEntryPoint MainActivity --- by viewModels()로 최종 사용
```

즉 필요한 재료(ApiService)를 누군가(NetworkModule)가 미리 만들어두면, 그 재료를 필요로 하는 곳(Repository, ViewModel, Activity)까지 Hilt가 자동으로 연결해주는 구조다.

<br>

# 8. Qualifier로 같은 타입 구분하기

같은 타입(예: `OkHttpClient`, `String`)인데 용도가 다른 경우가 있다. 예를 들어 base URL이 두 개 필요할 때, Hilt는 어떤 걸 줘야 할지 구분할 수 없어서 에러가 난다. 이럴 때 `@Qualifier`로 이름표를 붙여준다.

```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthBaseUrl

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PaymentBaseUrl

@Module
@InstallIn(SingletonComponent::class)
object UrlModule {
    @AuthBaseUrl
    @Provides
    fun provideAuthUrl(): String = "https://auth.example.com/"

    @PaymentBaseUrl
    @Provides
    fun providePaymentUrl(): String = "https://payment.example.com/"
}

// 사용하는 쪽
class AuthRepository @Inject constructor(
    @AuthBaseUrl private val baseUrl: String
)
```

이름표(Qualifier)를 안 쓰면 Hilt 입장에서는 "String이 두 개 있는데 뭘 줘야 하지?"라며 컴파일 에러를 낸다.

<br>

# 9. 테스트에서 Hilt 사용하기

Hilt는 테스트용 어노테이션도 따로 제공해서, 실제 의존성을 mock으로 쉽게 바꿔치기할 수 있다.

```kotlin
@HiltAndroidTest
class UserRepositoryTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: UserRepository

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun testGetUser() = runTest {
        val user = repository.getUser("123")
        assertNotNull(user)
    }
}
```

실제 네트워크 대신 가짜 서버 응답을 쓰고 싶다면, 테스트 전용 모듈을 만들어서 기존 모듈을 `@TestInstallIn`으로 교체하면 된다. 원래 코드는 건드리지 않고 테스트에서만 다른 구현체를 넣을 수 있다는 게 핵심이다.

<br>

# 10. 자주 만나는 에러와 해결법

Hilt를 처음 쓸 때 거의 다 겪는 에러들이다.

- **"Cannot be provided without an @Provides-annotated method"**
  뜻: Hilt가 이 타입을 어떻게 만들어야 할지 모른다.
  원인: 인터페이스인데 `@Binds` 연결을 안 했거나, 외부 클래스인데 `@Provides`를 안 만든 경우.
  해결: 인터페이스면 `@Binds`, 외부 라이브러리 객체면 `@Module` + `@Provides` 추가.

- **"@AndroidEntryPoint를 안 붙였는데 주입이 안 돼요"**
  뜻: 주입받고 싶은 Activity/Fragment에 진입점 표시가 없다.
  해결: 클래스 선언 위에 `@AndroidEntryPoint`를 붙인다. 부모 Activity가 있다면 부모도 붙여야 하는 경우가 있다.

- **"Application 클래스를 못 찾는다"**
  뜻: `@HiltAndroidApp`을 깜빡했거나, AndroidManifest에 Application 클래스 등록을 안 함.
  해결: Application 클래스에 `@HiltAndroidApp` 붙이고, manifest의 `android:name`에 등록 확인.

- **"스코프가 안 맞다 (component scope mismatch)"**
  뜻: 예를 들어 `ActivityComponent`짜리 의존성을 `SingletonComponent`에서 쓰려고 할 때 발생. 짧게 사는 애를 오래 사는 애 안에 넣으려는 것.
  해결: 필요한 컴포넌트 레벨을 다시 확인하고 `@InstallIn` 위치를 맞춘다.

<br>

# 11. 헷갈리기 쉬운 부분

- `@Provides` vs `@Binds`: 내가 직접 만든 클래스면 애초에 `@Inject constructor`로 충분해서 모듈 자체가 필요 없다. 인터페이스 바인딩이면 `@Binds` (더 가볍다), Retrofit/Room처럼 빌더 패턴이 필요한 외부 객체면 `@Provides`를 쓴다.
- `@InstallIn(SingletonComponent::class)`는 앱 전역 생명주기, `ActivityComponent::class`는 Activity 생명주기에 종속. 스코프를 잘못 지정하면 컴파일 에러 또는 예상치 못한 재생성이 발생한다.
- `@Singleton`은 `SingletonComponent`에 설치된 것에만 의미가 있다. 다른 컴포넌트 스코프에 `@Singleton`을 붙이면 무시되거나 에러가 난다.
- Hilt는 컴파일 타임에 코드를 생성하므로, 어노테이션 처리 후 빌드가 안 되면 대부분 모듈 설정이나 스코프 문제다. 런타임 에러가 아니라 대부분 "빌드가 안 되는" 형태로 먼저 나타난다는 걸 기억하면 디버깅이 쉬워진다.

<br>

# 정리

- Hilt는 Dagger 기반으로 Android 컴포넌트에 맞춰 DI 보일러플레이트를 줄여주는 라이브러리다.
- `@HiltAndroidApp`으로 앱 전체 컨테이너를 만들고, `@AndroidEntryPoint`로 각 컴포넌트에 주입을 활성화하며, 자체 클래스는 `@Inject constructor`로, 인터페이스나 외부 라이브러리 객체는 `@Module` + `@Provides`/`@Binds`로 제공한다.
- 같은 타입이 여러 개 필요하면 `@Qualifier`로 구분하고, 테스트에서는 `@TestInstallIn`으로 실제 구현을 mock으로 바꿔치기할 수 있다.
- 핵심은 "누가 이 컴포넌트 계층 안에서 얼마나 오래 살아야 하는가(스코프)"를 정확히 이해하는 것이다.
