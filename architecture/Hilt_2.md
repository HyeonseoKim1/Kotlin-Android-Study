# Hilt DI 심화

<br>

## 목차

1. 왜 심화가 필요한가
2. 멀티모듈에서 Hilt 설정하기
3. 모듈 간 의존성 방향과 Hilt 모듈 배치
4. 커스텀 컴포넌트와 커스텀 스코프
5. @EntryPoint - Hilt 관리 밖 클래스에서 주입받기
6. Assisted Injection - 런타임 파라미터가 필요한 경우
7. 멀티바인딩 (Multibinding)
8. 멀티모듈 테스트 설정
9. 실무에서 자주 겪는 문제

<br>

# 1. 왜 심화가 필요한가

기초에서 다룬 `@Inject`, `@Module`, `@Provides`, `@Binds`는 싱글 모듈 앱에서는 충분하다. 하지만 실무처럼 `:app`, `:core-network`, `:feature-login`, `:feature-home`처럼 모듈이 여러 개로 쪼개진 멀티모듈 프로젝트에서는 몇 가지 문제가 추가로 생긴다.

- 어느 모듈에 `@Module`을 둬야 하는가
- feature 모듈끼리 서로 의존하지 않게 하면서 공통 의존성(Retrofit, DB 등)을 어떻게 공유하는가
- Hilt가 자동으로 못 다루는 클래스(예: WorkManager Worker, ContentProvider)는 어떻게 주입받는가
- 런타임에만 결정되는 값(예: itemId)을 가진 클래스는 어떻게 주입하는가

이런 것들이 심화 영역이다.

<br>

# 2. 멀티모듈에서 Hilt 설정하기

일반적인 멀티모듈 Android 프로젝트 구조는 이렇다.

```
:app                (Application, @HiltAndroidApp)
:core:network        (Retrofit, OkHttp 관련 모듈)
:core:database        (Room 관련 모듈)
:feature:login        (로그인 화면, ViewModel)
:feature:home        (홈 화면, ViewModel)
```

각 모듈에 Hilt 의존성을 추가해야 한다.

```kotlin
// core/network/build.gradle.kts
plugins {
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
}

dependencies {
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")
}
```

주의할 점: `@HiltAndroidApp`은 `:app` 모듈에 딱 하나만 있어야 한다. 다른 모듈에는 필요 없다. 대신 각 모듈은 자기 영역에 필요한 `@Module`만 정의하면 된다.

<br>

# 3. 모듈 간 의존성 방향과 Hilt 모듈 배치

핵심 원칙: **Hilt 모듈은 그 의존성을 실제로 제공할 수 있는 곳(구현체를 아는 곳)에 둔다.**

```
:core:network 모듈 안에 NetworkModule 정의
    -> Retrofit, OkHttpClient를 만드는 방법을 안다

:core:database 모듈 안에 DatabaseModule 정의
    -> Room Database, DAO를 만드는 방법을 안다

:feature:login 모듈은 NetworkModule, DatabaseModule을 직접 만들지 않고
    -> core 모듈에 이미 정의된 @Provides 결과물(ApiService, UserDao)을 그냥 주입받아서 쓴다
```

feature 모듈끼리는 서로 의존하면 안 되는 게 원칙이다(예: `:feature:login`이 `:feature:home`을 참조하면 안 됨). 대신 두 feature가 공통으로 필요한 것(로그인 상태 등)은 `:core` 계층에 인터페이스로 뽑아두고, 구현체는 `:app` 또는 별도 `:core:impl` 모듈에서 `@Binds`로 연결하는 식으로 처리한다.

```kotlin
// :core:domain 모듈 (인터페이스만 존재, 구현체는 모른다)
interface SessionManager {
    fun isLoggedIn(): Boolean
}

// :core:data 모듈 (구현체 + Hilt 모듈)
class SessionManagerImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SessionManager {
    override fun isLoggedIn(): Boolean { /* ... */ return true }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
    @Binds
    abstract fun bindSessionManager(impl: SessionManagerImpl): SessionManager
}
```

이렇게 하면 `:feature:login`, `:feature:home` 둘 다 `:core:domain`의 `SessionManager` 인터페이스만 알면 되고, 실제 구현이 무엇인지는 몰라도 된다.

<br>

# 4. 커스텀 컴포넌트와 커스텀 스코프

Hilt가 기본 제공하는 컴포넌트(SingletonComponent, ActivityComponent 등)로 부족한 경우, 커스텀 컴포넌트를 직접 정의할 수 있다. 예를 들어 "로그인된 사용자 세션 동안만 유지되는" 스코프가 필요한 경우다.

```kotlin
// 커스텀 스코프 정의
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class LoginScoped

// 커스텀 컴포넌트 정의
@LoginScoped
@DefineComponent(parent = SingletonComponent::class)
interface LoginSessionComponent

@DefineComponent.Builder
interface LoginSessionComponentBuilder {
    fun build(): LoginSessionComponent
}
```

다만 커스텀 컴포넌트는 관리가 복잡해서, 실무에서는 "로그인 시점에 새 인스턴스를 만들고 로그아웃 시점에 정리"해야 하는 게 명확할 때만 쓰고, 대부분의 경우는 SingletonComponent + 내부에서 로그인 상태를 체크하는 방식으로 충분히 해결된다. 커스텀 컴포넌트는 최후의 수단으로 생각하는 게 좋다.

<br>

# 5. @EntryPoint - Hilt 관리 밖 클래스에서 주입받기

`@AndroidEntryPoint`를 붙일 수 없는 클래스(Hilt가 생명주기를 모르는 클래스)에서 Hilt 의존성이 필요할 때 쓴다. 대표적으로 ContentProvider, 또는 서드파티 라이브러리가 만드는 클래스가 해당된다.

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MyEntryPoint {
    fun apiService(): ApiService
}

class MyContentProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            MyEntryPoint::class.java
        )
        val apiService = entryPoint.apiService()
        return true
    }
}
```

이건 Hilt가 관리하지 않는 지점에서 강제로 컨테이너에 접근하는 "탈출구" 같은 개념이라, 정말 필요한 경우에만 쓰는 게 맞다.

<br>

# 6. Assisted Injection - 런타임 파라미터가 필요한 경우

ViewModel이 "실행 시점에만 알 수 있는 값"(예: 상세 화면의 itemId)을 필요로 할 때가 있다. 이럴 때 예전에는 Factory 패턴을 직접 만들어야 했는데, Hilt는 `@AssistedInject`로 이를 지원한다.

```kotlin
class DetailViewModel @AssistedInject constructor(
    @Assisted private val itemId: String,      // 런타임에 결정되는 값
    private val repository: ItemRepository     // Hilt가 자동으로 주입하는 값
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(itemId: String): DetailViewModel
    }
}
```

사실 Compose Navigation에서는 `SavedStateHandle`을 통해 navArgs를 받는 방식(`@HiltViewModel` + `savedStateHandle.get<String>("itemId")`)이 더 흔하게 쓰이지만, Assisted Injection은 ViewModel이 아닌 일반 클래스에서 런타임 값이 필요할 때 특히 유용하다.

<br>

# 7. 멀티바인딩 (Multibinding)

같은 인터페이스를 구현하는 여러 클래스를 한 번에 모아서 리스트나 맵으로 주입받고 싶을 때 쓴다. 예를 들어 여러 개의 Analytics Tracker를 한 곳에서 순회하며 이벤트를 보내야 하는 경우다.

```kotlin
interface AnalyticsTracker {
    fun track(event: String)
}

class FirebaseTracker @Inject constructor() : AnalyticsTracker {
    override fun track(event: String) { /* ... */ }
}

class AmplitudeTracker @Inject constructor() : AnalyticsTracker {
    override fun track(event: String) { /* ... */ }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TrackerModule {
    @Binds
    @IntoSet
    abstract fun bindFirebaseTracker(tracker: FirebaseTracker): AnalyticsTracker

    @Binds
    @IntoSet
    abstract fun bindAmplitudeTracker(tracker: AmplitudeTracker): AnalyticsTracker
}

// 사용하는 쪽
class AnalyticsManager @Inject constructor(
    private val trackers: Set<@JvmSuppressWildcards AnalyticsTracker>
) {
    fun trackAll(event: String) {
        trackers.forEach { it.track(event) }
    }
}
```

새 Tracker를 추가할 때 `AnalyticsManager` 코드는 전혀 건드릴 필요 없이, `@IntoSet` 바인딩만 추가하면 자동으로 Set에 포함된다는 게 장점이다.

<br>

# 8. 멀티모듈 테스트 설정

멀티모듈에서는 테스트 모듈 자체도 별도 소스셋(`androidTest`)에 Hilt 테스트 의존성을 넣어야 한다. 또한 여러 모듈에 걸쳐 `@TestInstallIn`을 써서 특정 모듈의 실제 구현을 테스트용으로 바꿔치기할 수 있다.

```kotlin
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NetworkModule::class]
)
object FakeNetworkModule {
    @Provides
    @Singleton
    fun provideFakeApiService(): ApiService = FakeApiService()
}
```

이렇게 하면 테스트 실행 시에는 `NetworkModule` 대신 `FakeNetworkModule`이 자동으로 사용되고, 실제 코드는 전혀 수정할 필요가 없다.

<br>

# 9. 실무에서 자주 겪는 문제

- **"다른 모듈의 @Module이 인식이 안 돼요"**
  원인: 해당 모듈이 gradle 의존성 그래프에 아예 포함이 안 됐거나(`implementation(project(":core:network"))` 누락), kapt/ksp 설정이 그 모듈에는 빠져 있는 경우.
  해결: 모듈 간 gradle 의존성과 Hilt 플러그인 적용 여부를 먼저 확인한다.

- **"feature 모듈 간 순환 참조가 생겼어요"**
  원인: `:feature:login`과 `:feature:home`이 서로의 State나 클래스를 직접 참조하려고 함.
  해결: 공통 인터페이스를 `:core` 계층으로 뽑아내고, feature 모듈은 인터페이스만 참조하게 리팩터링한다. 이건 Hilt 문제가 아니라 모듈 설계 문제라서, DI로 해결하려 하지 말고 의존성 방향 자체를 재설계해야 한다.

- **"빌드 시간이 너무 오래 걸려요"**
  원인: kapt는 어노테이션 처리 시 전체 소스를 스캔해서 느리다.
  해결: 가능하면 `kapt`를 `ksp`로 전환한다(`com.google.dagger.hilt.android` + KSP 조합은 최근 버전에서 공식 지원). 빌드 시간이 체감될 정도로 줄어든다.

<br>

# 정리

- Hilt 심화는 결국 "멀티모듈 환경에서 의존성을 어느 모듈에 두고, 어떻게 경계를 넘나들게 할 것인가"의 문제로 귀결된다.
- Hilt 모듈은 실제 구현체를 아는 모듈에 두고, feature 모듈끼리는 직접 참조 대신 core 계층의 인터페이스를 통해 연결한다.
- 기본 컴포넌트로 부족하면 커스텀 컴포넌트/스코프를, Hilt가 관리 못 하는 클래스에서는 `@EntryPoint`를, 런타임 값이 필요하면 Assisted Injection을, 같은 타입 여러 개를 묶어야 하면 멀티바인딩을 쓴다. 
- 대부분의 실무 이슈는 Hilt 자체보다 모듈 의존성 방향 설계에서 비롯되므로, 에러가 나면 먼저 "이 의존성이 어느 모듈에 있어야 맞는가"부터 점검하는 게 순서다.
