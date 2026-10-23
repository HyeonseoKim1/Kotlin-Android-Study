# Gradle 멀티모듈 / Version Catalog / Convention Plugin

<br>

# 목차

1. 멀티모듈을 쓰는 이유
2. 모듈 구조 설계
3. settings.gradle.kts
4. Version Catalog (libs.versions.toml)
5. api vs implementation
6. Convention Plugin (build-logic)
7. 기능 모듈 Convention 예시 (Compose + Hilt)
8. 의존성 규칙과 순환 참조
9. 빌드 속도 최적화
10. Hilt와 멀티모듈
11. 자주 겪는 문제
12. 정리

<br>

# 1. 멀티모듈을 쓰는 이유

| 이유 | 설명 |
|---|---|
| 빌드 시간 | 변경된 모듈과 그 의존 모듈만 다시 컴파일 (증분 빌드, 병렬 빌드) |
| 경계 강제 | 모듈 간 의존성을 Gradle이 강제하므로 레이어 침범을 컴파일 단계에서 차단 |
| 재사용 | core 모듈을 여러 앱, 여러 feature에서 공유 |
| 병렬 작업 | 팀원이 서로 다른 모듈에서 작업해 충돌 감소 |
| 테스트 | 모듈 단위로 테스트 범위를 좁게 유지 |

작은 프로젝트에서는 오히려 설정 비용이 더 클 수 있다. 화면이 늘어나고 팀이 커지는 시점에 나누는 것이 현실적이다.

<br>

# 2. 모듈 구조 설계

```text
project-root/
├─ app/                      # 진입점, DI 조립, Navigation 그래프
├─ feature/
│  ├─ home/                  # 화면 + ViewModel (MVI)
│  └─ settings/
├─ core/
│  ├─ model/                 # 순수 Kotlin 데이터 모델
│  ├─ domain/                # UseCase, Repository 인터페이스
│  ├─ data/                  # Repository 구현
│  ├─ network/               # Retrofit, OkHttp
│  ├─ database/              # Room
│  ├─ designsystem/          # 공통 Compose 컴포넌트, 테마
│  └─ common/                # 유틸, 확장 함수
├─ build-logic/
│  └─ convention/            # Convention Plugin
├─ gradle/
│  └─ libs.versions.toml
└─ settings.gradle.kts
```

## 레이어 방향

```text
app -> feature -> domain -> model
              \-> data -> network / database -> model
```

- feature는 다른 feature를 직접 참조하지 않는다 (화면 이동은 app 또는 navigation 계약 모듈을 통해서)
- domain은 Android 의존성이 없는 순수 Kotlin 모듈로 둘 수 있다 (`kotlin("jvm")`)
- data는 domain의 인터페이스를 구현한다 (의존성 역전)

<br>

# 3. settings.gradle.kts

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "MyApp"

include(":app")
include(":feature:home")
include(":feature:settings")
include(":core:model")
include(":core:domain")
include(":core:data")
include(":core:network")
include(":core:database")
include(":core:designsystem")
include(":core:common")
```

- `includeBuild("build-logic")`: Convention Plugin을 별도 빌드로 포함
- `TYPESAFE_PROJECT_ACCESSORS`: `project(":core:model")` 대신 `projects.core.model` 사용 가능 (오타 방지)
- `FAIL_ON_PROJECT_REPOS`: 모듈별 repositories 선언을 금지해 저장소를 한 곳에서 관리

<br>

# 4. Version Catalog (libs.versions.toml)

`gradle/libs.versions.toml`

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
composeBom = "2024.12.01"
hilt = "2.52"
hiltNavigationCompose = "1.2.0"
coroutines = "1.9.0"
retrofit = "2.11.0"
okhttp = "4.12.0"
room = "2.6.1"

[libraries]
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# build-logic에서 compileOnly로 사용
android-gradlePlugin = { group = "com.android.tools.build", name = "gradle", version.ref = "agp" }
kotlin-gradlePlugin = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlin" }
ksp-gradlePlugin = { group = "com.google.devtools.ksp", name = "com.google.devtools.ksp.gradle.plugin", version.ref = "ksp" }

[bundles]
retrofit = ["retrofit", "okhttp-logging"]
room = ["room-runtime", "room-ktx"]

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }

# 직접 만든 Convention Plugin
app-android-application = { id = "app.android.application", version = "unspecified" }
app-android-library = { id = "app.android.library", version = "unspecified" }
app-android-feature = { id = "app.android.feature", version = "unspecified" }
```

사용 예시

```kotlin
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.bundles.retrofit)
    ksp(libs.room.compiler)
}
```

- 이름의 `-`는 코드에서 `.`으로 변환된다 (`room-runtime` -> `libs.room.runtime`)
- `[bundles]`로 항상 같이 쓰는 라이브러리를 한 줄로 묶는다
- 버전은 `[versions]`에 한 번만 선언하고 `version.ref`로 참조

<br>

# 5. api vs implementation

| 구성 | 전이 노출 | 설명 |
|---|---|---|
| `implementation` | 노출 안 함 | 내 모듈 내부에서만 사용. 변경 시 의존하는 모듈의 재컴파일 범위가 작음 |
| `api` | 노출함 | 내 모듈의 공개 API에 타입이 등장할 때만 사용 |
| `compileOnly` | 컴파일 시에만 | 런타임에는 다른 곳에서 제공 |
| `runtimeOnly` | 런타임에만 | 컴파일 클래스패스에 불필요 |
| `testImplementation` | 단위 테스트 | |
| `androidTestImplementation` | 계측 테스트 | |

```kotlin
// core:data/build.gradle.kts
dependencies {
    api(projects.core.domain)           // Repository 인터페이스가 data의 공개 API에 노출됨
    implementation(projects.core.network) // 내부 구현 세부사항
    implementation(projects.core.database)
}
```

기본은 `implementation`, 공개 시그니처에 타입이 나올 때만 `api`를 쓴다. `api`를 남용하면 모듈을 나눈 이점(증분 빌드 범위 축소)이 사라진다.

<br>

# 6. Convention Plugin (build-logic)

모듈마다 반복되는 `compileSdk`, `minSdk`, Java 버전, Compose 설정 등을 플러그인 한 곳으로 모은다.

## 디렉터리

```text
build-logic/
├─ settings.gradle.kts
└─ convention/
   ├─ build.gradle.kts
   └─ src/main/kotlin/
      ├─ AndroidApplicationConventionPlugin.kt
      ├─ AndroidLibraryConventionPlugin.kt
      ├─ AndroidFeatureConventionPlugin.kt
      └─ ProjectExtensions.kt
```

## build-logic/settings.gradle.kts

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
```

## build-logic/convention/build.gradle.kts

```kotlin
plugins {
    `kotlin-dsl`
}

group = "com.example.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "app.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "app.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "app.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
    }
}
```

## ProjectExtensions.kt

```kotlin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
```

## AndroidLibraryConventionPlugin.kt

```kotlin
import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<LibraryExtension> {
                compileSdk = 35

                defaultConfig {
                    minSdk = 26
                    consumerProguardFiles("consumer-rules.pro")
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
        }
    }
}
```

AGP, Kotlin 버전에 따라 플러그인 id와 확장 설정 방식이 달라질 수 있다 (예: AGP 9의 built-in Kotlin). 사용하는 버전의 공식 마이그레이션 문서를 확인할 것.

## 모듈에서 사용

```kotlin
// core/network/build.gradle.kts
plugins {
    alias(libs.plugins.app.android.library)
}

android {
    namespace = "com.example.core.network"
}

dependencies {
    implementation(libs.bundles.retrofit)
}
```

모듈 build.gradle.kts가 `namespace`와 의존성만 남아 짧아진다.

<br>

# 7. 기능 모듈 Convention 예시 (Compose + Hilt)

```kotlin
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("app.android.library")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply("com.google.devtools.ksp")
                apply("com.google.dagger.hilt.android")
            }

            extensions.configure<LibraryExtension> {
                buildFeatures {
                    compose = true
                }
            }

            dependencies {
                val bom = libs.findLibrary("androidx-compose-bom").get()
                add("implementation", platform(bom))
                add("implementation", libs.findLibrary("androidx-compose-ui").get())
                add("implementation", libs.findLibrary("androidx-compose-material3").get())
                add("implementation", libs.findLibrary("hilt-android").get())
                add("implementation", libs.findLibrary("androidx-hilt-navigation-compose").get())
                add("ksp", libs.findLibrary("hilt-compiler").get())
            }
        }
    }
}
```

feature 모듈 사용

```kotlin
// feature/home/build.gradle.kts
plugins {
    alias(libs.plugins.app.android.feature)
}

android {
    namespace = "com.example.feature.home"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.designsystem)
}
```

<br>

# 8. 의존성 규칙과 순환 참조

- Gradle은 모듈 간 순환 의존을 허용하지 않는다 (`Circular dependency between the following tasks` 오류)
- 순환이 생기면 공통 부분을 새 모듈(`core:model`, `core:common`)로 분리하거나, 인터페이스를 하위 모듈로 내린다
- feature 간 이동은 다음 중 하나로 해결한다
  - app 모듈이 모든 feature를 알고 NavHost에서 조립
  - 각 feature가 `navigation` 하위 모듈(라우트 정의만)을 공개하고 다른 feature는 그 모듈만 참조

의존성 그래프 확인

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
./gradlew :feature:home:dependencies --configuration debugCompileClasspath
```

<br>

# 9. 빌드 속도 최적화

`gradle.properties`

```properties
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.configureondemand=false

android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.incremental=true
ksp.incremental=true
```

| 설정 | 효과 |
|---|---|
| `org.gradle.parallel` | 독립적인 모듈을 병렬로 빌드 |
| `org.gradle.caching` | 이전 빌드 결과를 캐시로 재사용 |
| `org.gradle.configuration-cache` | 설정 단계 결과를 저장해 재사용 |
| `android.nonTransitiveRClass` | 모듈마다 자기 R 클래스만 생성해 리소스 변경 파급 감소 |
| `ksp.incremental` | KSP 증분 처리 |

분석 도구

```bash
./gradlew assembleDebug --scan
./gradlew assembleDebug --profile
```

- Build Scan으로 병목 태스크와 캐시 미스 원인을 확인
- 모듈을 너무 잘게 나누면 설정 단계 비용이 늘어 오히려 느려질 수 있다

<br>

# 10. Hilt와 멀티모듈

- `@HiltAndroidApp`은 app 모듈에만 둔다
- `@Module`과 `@InstallIn`은 각 모듈에 두고, 모든 모듈은 app의 Hilt 그래프에 합쳐진다
- feature 모듈의 `@HiltViewModel`은 feature 모듈에서 정의 가능
- Repository 바인딩은 data 모듈에서 `@Binds`로 제공

```kotlin
// core/data
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
}
```

```kotlin
// core/network
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder().build()
}
```

주의

- app 모듈이 data, network 모듈을 (최소 `runtimeOnly`로) 의존해야 해당 Hilt 모듈이 그래프에 포함된다
- 순수 Kotlin 모듈(`kotlin("jvm")`)에서는 Hilt Android 플러그인을 쓸 수 없으므로 `@Inject constructor`만 사용하고 바인딩은 Android 모듈에서 처리

<br>

# 11. 자주 겪는 문제

| 증상 | 원인 | 해결 |
|---|---|---|
| `Unresolved reference: R` | `nonTransitiveRClass` 활성화 후 다른 모듈 리소스 참조 | 해당 모듈 R을 import 하거나 리소스를 해당 모듈로 이동 |
| `Cannot find a version of ... ` | catalog에 `version.ref` 누락 | `[versions]`에 선언했는지 확인 |
| Hilt `MissingBinding` | 구현 모듈이 app 의존성에 없음 | app에서 해당 모듈 의존 추가 |
| Compose 컴파일러 오류 | feature 모듈에 compose 플러그인/`buildFeatures` 누락 | Convention Plugin에서 일괄 적용 |
| Convention Plugin 변경이 반영 안 됨 | build-logic 캐시 | `./gradlew --stop` 후 재빌드 |
| 모듈 추가 후 sync 실패 | `settings.gradle.kts`에 include 누락 | include 추가 |
| 순환 의존 오류 | 모듈 간 양방향 참조 | 공통 모듈 분리 또는 인터페이스 이동 |

<br>

# 12. 정리

- 멀티모듈은 빌드 시간, 경계 강제, 재사용을 위한 구조이며 프로젝트 규모에 맞게 나눈다
- 구조는 `app` / `feature` / `core` / `build-logic`으로 나누고 의존 방향은 항상 위에서 아래로 한다
- Version Catalog(`libs.versions.toml`)로 버전과 플러그인을 한 곳에서 관리하고 `bundles`로 묶어 쓴다
- 의존성은 기본 `implementation`, 공개 API에 타입이 노출될 때만 `api`를 쓴다
- Convention Plugin으로 `compileSdk`, `minSdk`, Compose, Hilt 설정을 모듈마다 반복하지 않고 한 곳에서 관리한다
- feature끼리는 직접 의존하지 않고 app 또는 navigation 모듈을 통해 연결한다
- `parallel`, `caching`, `configuration-cache`, `nonTransitiveRClass`로 빌드 속도를 확보하고 Build Scan으로 병목을 확인한다
- Hilt는 `@HiltAndroidApp`을 app에 두고 각 모듈의 `@Module`이 하나의 그래프로 합쳐진다
