# Hilt

## 목차

1.  Hilt란?
2.  왜 Hilt를 사용하는가?
3.  DI(Dependency Injection)
4.  Dagger와 Hilt의 차이
5.  프로젝트 설정
6.  @HiltAndroidApp
7.  @AndroidEntryPoint
8.  @Inject
9.  @Module
10. @Provides
11. @Binds
12. @InstallIn
13. Component 구조
14. Scope
15. @HiltViewModel
16. Compose에서 Hilt
17. 자주 하는 실수
18. 디버깅 팁
19. 정리

> 이 파일은 분량 관계상 핵심 내용을 중심으로 정리한 버전이다. 각 항목은
> 실제 프로젝트에서 바로 활용할 수 있도록 작성하였다.

# 1. Hilt란?

Hilt는 Dagger를 Android에서 쉽게 사용할 수 있도록 만든 공식
DI(Dependency Injection) 라이브러리이다.

Hilt를 사용하면 객체 생성과 생명주기 관리를 프레임워크가 대신 수행하므로
개발자는 객체 사용에 집중할 수 있다.

# 2. 왜 Hilt를 사용하는가?

-   객체 생성 코드 감소
-   결합도 감소
-   테스트 용이
-   생명주기 기반 객체 관리
-   Google 공식 권장

# 3. DI

DI는 필요한 객체를 직접 생성하지 않고 외부에서 주입받는 방식이다.

``` kotlin
class UserViewModel(
    private val repository: UserRepository
)
```

# 4. Dagger와 Hilt

  항목           Dagger      Hilt
  -------------- ----------- -----------
  설정           복잡        간단
  Android 지원   직접 구현   자동 지원
  Boilerplate    많음        적음

# 5. 프로젝트 설정

``` kotlin
plugins {
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}
```

``` kotlin
dependencies {
    implementation("com.google.dagger:hilt-android:<version>")
    kapt("com.google.dagger:hilt-compiler:<version>")
}
```

# 6. @HiltAndroidApp

``` kotlin
@HiltAndroidApp
class MyApplication : Application()
```

Application 진입점이며 Dependency Graph를 생성한다.

# 7. @AndroidEntryPoint

``` kotlin
@AndroidEntryPoint
class MainActivity : AppCompatActivity()
```

Activity, Fragment, Service 등에서 Hilt를 사용할 수 있게 한다.

# 8. @Inject

생성자 주입을 가장 권장한다.

``` kotlin
class UserRepository @Inject constructor(
    private val api: ApiService
)
```

# 9. @Module

생성자를 수정할 수 없는 객체를 제공하기 위한 클래스이다.

# 10. @Provides

``` kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    fun provideApi(): ApiService = ApiService()
}
```

# 11. @Binds

인터페이스 구현체를 연결할 때 사용한다.

``` kotlin
@Binds
abstract fun bindRepository(
    impl: UserRepositoryImpl
): UserRepository
```

# 12. @InstallIn

객체를 어느 Component에 저장할지 지정한다.

대표적으로 SingletonComponent, ActivityComponent, ViewModelComponent가
있다.

# 13. Component 구조

Application → ActivityRetained → ViewModel

Application → Activity → Fragment → View

상위 Component 객체는 하위 Component에서 사용할 수 있다.

# 14. Scope

-   @Singleton
-   @ActivityRetainedScoped
-   @ActivityScoped
-   @FragmentScoped
-   @ViewModelScoped

Scope는 같은 Component 안에서 동일 객체를 재사용하기 위한 기능이다.

# 15. @HiltViewModel

``` kotlin
@HiltViewModel
class UserViewModel @Inject constructor(
    private val repository: UserRepository
): ViewModel()
```

# 16. Compose에서 Hilt

``` kotlin
@Composable
fun HomeScreen(
    viewModel: UserViewModel = hiltViewModel()
) {
}
```

# 17. 자주 하는 실수

-   @HiltAndroidApp 누락
-   Manifest 등록 누락
-   @AndroidEntryPoint 누락
-   Fragment만 @AndroidEntryPoint 적용
-   Scope 충돌
-   @Provides와 @Inject 중복

# 18. 디버깅 팁

-   Generated 코드 확인
-   kapt/ksp 오류 확인
-   Clean/Rebuild 수행
-   Component 범위 확인

# 정리

-   Hilt는 Android 공식 DI 라이브러리이다.
-   객체 생성보다 객체 사용에 집중하게 해준다.
-   생성자 주입을 우선 사용한다.
-   Module은 외부 객체를 제공할 때 사용한다.
-   Binds는 인터페이스 구현체 연결에 적합하다.
-   Scope는 객체 재사용 범위를 결정한다.
-   Compose에서는 hiltViewModel()을 사용한다.
