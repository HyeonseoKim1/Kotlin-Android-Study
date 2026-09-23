# Baseline Profile & Macrobenchmark

<br>

## 목차

1. 개요
2. ART 컴파일 모드와 Baseline Profile 동작 원리
3. Macrobenchmark 모듈 설정
4. Macrobenchmark 측정 지표
5. Startup 벤치마크 작성
6. 스크롤/애니메이션 벤치마크 작성
7. Baseline Profile 생성
8. Baseline Profile 파일 구조 이해
9. Startup Profile
10. 적용 및 빌드 통합
11. R8/ProGuard와의 상호작용
12. 측정 결과 비교 및 통계 처리
13. Google Play Console Cloud Profile
14. CI/CD 통합
15. 트러블슈팅
16. 정리

<br>

## 1. 개요

앱의 콜드 스타트, 스크롤 시 프레임 드랍 등 초기/런타임 성능은 안드로이드 런타임(ART)의 코드 컴파일 방식에 크게 좌우됨. 앱을 처음 설치했을 때는 대부분의 클래스와 메서드가 컴파일되지 않은 상태이며, 실행 중 인터프리터가 바이트코드를 한 줄씩 해석하거나 JIT(Just-In-Time) 컴파일러가 즉석에서 기계어로 변환하는 과정을 거침. 이 과정 자체가 오버헤드이기 때문에 앱을 설치한 직후의 첫 실행, 또는 OS 업데이트 직후의 실행은 이후 실행보다 느림.

Baseline Profile은 앱이 자주 실행하는 코드 경로(클래스 로딩 순서, 자주 호출되는 메서드)를 미리 명시해서, 설치 시점에 ART가 이 경로를 AOT(Ahead-Of-Time)로 미리 컴파일하도록 힌트를 제공하는 메커니즘. 이렇게 하면 사용자가 처음 앱을 실행하는 순간부터 이미 컴파일된 코드를 사용하게 되어 콜드 스타트, 화면 전환, 초기 스크롤 등에서 체감 성능이 개선됨.

Macrobenchmark는 이 성능을 실제 기기/에뮬레이터에서 측정하는 Jetpack 테스트 라이브러리로, `androidx.benchmark:benchmark-macro-junit4` 의존성을 통해 사용함. Microbenchmark가 단일 함수/알고리즘의 실행 시간을 측정하는 반면, Macrobenchmark는 앱 전체를 프로세스 단위로 실행하며 실제 사용자 시나리오(콜드 스타트, 화면 전환, 스크롤)에서의 성능을 측정하는 데 특화되어 있음.

전체 작업 흐름은 다음과 같음.

1. Macrobenchmark로 현재 앱 성능(Baseline, 프로파일 미적용 상태) 측정
2. `BaselineProfileRule`로 사용자 핵심 흐름을 시뮬레이션하며 프로파일 생성
3. 생성된 프로파일을 앱 모듈의 `baseline-prof.txt`로 반영
4. 릴리즈 빌드로 APK/AAB 생성 시 프로파일이 자동 포함됨
5. Macrobenchmark로 재측정 후 개선폭 비교

이 문서는 위 흐름을 실제 구현 코드와 함께 정리함.

<br>

## 2. ART 컴파일 모드와 Baseline Profile 동작 원리

ART에는 크게 세 가지 컴파일 상태가 존재함.

- Interpreted: 바이트코드를 한 줄씩 해석. 가장 느림.
- JIT (Just-In-Time): 실행 중 자주 호출되는 코드(hot code)를 감지해 기계어로 컴파일하고 캐시. 반복 실행할수록 빨라지지만 초반에는 인터프리터 단계를 거쳐야 함.
- AOT (Ahead-Of-Time): 설치/유휴 시간에 미리 컴파일. 실행 시점에는 이미 네이티브 코드가 준비되어 있어 가장 빠름.

기본적으로 Play Store를 통해 배포된 앱은 설치 직후 대부분의 코드가 인터프리터/JIT 상태이고, 기기가 유휴(충전 중, 화면 꺼짐 등) 상태일 때 백그라운드에서 점진적으로 AOT 컴파일이 진행됨(`dex2oat` 데몬). 문제는 이 백그라운드 컴파일이 언제 완료될지 예측할 수 없고, 사용자가 자주 쓰는 화면이 우선적으로 컴파일된다는 보장도 없다는 점.

Baseline Profile은 이 문제를 해결하기 위해 앱 개발자가 "이 클래스들과 메서드들은 자주 쓰이니 설치 시점에 바로 AOT 컴파일하라"는 힌트를 `baseline-prof.txt` 파일 형태로 APK에 포함시키는 방식임. 이 파일은 `ProfileInstaller` 라이브러리에 의해 앱 최초 실행 시(정확히는 앱이 유휴 상태가 되는 첫 시점) 시스템에 등록되고, ART는 이를 기반으로 AOT 컴파일을 수행함.

Baseline Profile과 함께 언급되는 개념으로 Cloud Profile이 있는데, 이는 Google Play가 실제 사용자들의 사용 패턴을 집계해 자동으로 생성하는 프로파일로, 개발자가 직접 만든 Baseline Profile과 병합되어 적용됨. Baseline Profile은 앱의 "초기" 경험을, Cloud Profile은 이후 실사용 데이터를 기반으로 한 "장기" 최적화를 담당한다고 이해하면 됨.

<br>

## 3. Macrobenchmark 모듈 설정

### 모듈 생성

Macrobenchmark 테스트는 앱 모듈과 분리된 별도의 Android Test 모듈(`com.android.test` 플러그인)에서 작성함. Android Studio의 New Module 마법사에서 "Baseline Profile Generator" 또는 "Benchmark" 템플릿을 선택하면 자동 생성되지만, 수동으로 구성할 경우 다음과 같이 작성.

```kotlin
// settings.gradle.kts
include(":app")
include(":benchmark")
```

```kotlin
// benchmark/build.gradle.kts
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.example.benchmark"
    compileSdk = 34

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        minSdk = 24
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }
}

dependencies {
    implementation("androidx.test.ext:junit:1.1.5")
    implementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("androidx.test.uiautomator:uiautomator:2.2.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.2.3")
}

baselineProfile {
    useConnectedDevices = true
}
```

### 앱 모듈 설정

앱 모듈에는 프로파일링을 허용하는 설정과 `profileinstaller` 의존성이 필요함.

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("androidx.baselineprofile")
}

android {
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("benchmark") {
            initWith(release)
            matchingFallbacks += listOf("release")
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    baselineProfile(project(":benchmark"))
}
```

```xml
<!-- app/src/main/AndroidManifest.xml -->
<manifest>
    <application>
        <profileable android:shell="true" />
    </application>
</manifest>
```

`<profileable android:shell="true" />` 설정이 없으면 `benchmark` 빌드 타입이더라도 shell 명령을 통한 프로파일링 도구(simpleperf 등)에 접근할 수 없어 일부 측정이 실패함.

### root project 플러그인 등록

```kotlin
// build.gradle.kts (project root)
plugins {
    id("androidx.baselineprofile") version "1.2.3" apply false
}
```

<br>

## 4. Macrobenchmark 측정 지표

Macrobenchmark가 기본 제공하는 메트릭은 다음과 같음.

- `StartupTimingMetric`: 콜드/웜/핫 스타트 시간 측정. `timeToInitialDisplayMs`, `timeToFullDisplayMs` 등을 제공.
- `FrameTimingMetric`: 프레임 렌더링 시간, jank(프레임 드랍) 비율 측정. 스크롤/애니메이션 벤치마크에 사용.
- `TraceSectionMetric`: `Trace.beginSection`으로 감싼 커스텀 코드 구간의 실행 시간 측정.
- `PowerMetric`: 배터리 소모량 측정 (API 29 이상, 특정 기기 필요).
- `MemoryUsageMetric`: 메모리 사용량 측정.

각 메트릭은 여러 회 반복 실행한 결과의 median, min, max 값을 제공하며, 기기 환경 편차를 줄이기 위해 기본적으로 5회 이상 반복을 권장함.

<br>

## 5. Startup 벤치마크 작성

### CompilationMode 종류

- `CompilationMode.None()`: AOT 컴파일 없음. 순수 인터프리터/JIT 상태. Baseline Profile 미적용 시나리오의 기준값으로 사용.
- `CompilationMode.Partial(baselineProfileMode = ...)`: Baseline Profile만 적용한 상태. 실제 배포 앱과 가장 유사한 조건.
- `CompilationMode.Full()`: 앱 전체를 AOT 컴파일. 이론상 최고 성능이지만 실제 배포 환경과는 다름 (참고용).

### StartupMode 종류

- `StartupMode.COLD`: 프로세스가 완전히 종료된 상태에서 시작. 가장 일반적인 벤치마크 대상.
- `StartupMode.WARM`: 프로세스는 살아있지만 Activity가 재생성되는 경우.
- `StartupMode.HOT`: Activity가 포그라운드로 복귀하는 경우.

### 구현

```kotlin
// benchmark/src/main/java/com/example/benchmark/StartupBenchmark.kt
package com.example.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCompilationModeNone() = startup(CompilationMode.None())

    @Test
    fun startupCompilationModeBaselineProfile() =
        startup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    @Test
    fun startupCompilationModeFull() = startup(CompilationMode.Full())

    private fun startup(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = "com.example.app",
            metrics = listOf(StartupTimingMetric()),
            iterations = 10,
            startupMode = StartupMode.COLD,
            compilationMode = compilationMode,
            setupBlock = {
                pressHome()
            }
        ) {
            startActivityAndWait()
            device.wait(Until.hasObject(By.res(packageName, "home_root")), 5_000)
        }
    }
}
```

`iterations`를 10 이상으로 설정하면 초반/후반 측정값의 편차를 median으로 상쇄시킬 수 있어 신뢰도가 높아짐. 단, 실기기 기준 1회 실행에 수 초가 소요되므로 CI에서는 시간 예산을 고려해야 함.

<br>

## 6. 스크롤/애니메이션 벤치마크 작성

콜드 스타트뿐 아니라 리스트 스크롤 시 jank(프레임 드랍)도 자주 벤치마크 대상이 됨. `FrameTimingMetric`을 사용.

```kotlin
// benchmark/src/main/java/com/example/benchmark/ScrollBenchmark.kt
package com.example.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollHomeListCompilationNone() =
        scrollHomeList(CompilationMode.None())

    @Test
    fun scrollHomeListBaselineProfile() =
        scrollHomeList(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun scrollHomeList(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = "com.example.app",
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.WARM,
            compilationMode = compilationMode,
            setupBlock = {
                startActivityAndWait()
                device.wait(Until.hasObject(By.res(packageName, "home_list")), 5_000)
            }
        ) {
            val list = device.findObject(By.res(packageName, "home_list"))
            list.setGestureMargin(device.displayWidth / 5)
            repeat(3) {
                list.fling(Direction.DOWN)
                device.waitForIdle()
            }
        }
    }
}
```

`setupBlock`은 측정 대상에서 제외되는 준비 단계이고, 뒤따르는 람다 블록만 실제 측정 구간에 포함됨. 스크롤 벤치마크에서는 `setGestureMargin`을 반드시 설정해야 하는데, 그렇지 않으면 UiAutomator의 fling 동작이 시스템 제스처 영역(뒤로가기 등)과 충돌할 수 있음.

### 커스텀 구간 측정

특정 함수나 초기화 로직의 실행 시간만 별도로 측정하고 싶은 경우 `TraceSectionMetric`을 사용.

```kotlin
// app 코드 내 커스텀 트레이스 삽입
import androidx.tracing.trace

fun loadInitialData() {
    trace("loadInitialData") {
        // 실제 로직
    }
}
```

```kotlin
// benchmark
benchmarkRule.measureRepeated(
    packageName = "com.example.app",
    metrics = listOf(TraceSectionMetric("loadInitialData")),
    iterations = 5,
    compilationMode = CompilationMode.Partial()
) {
    startActivityAndWait()
}
```

<br>

## 7. Baseline Profile 생성

### BaselineProfileRule

Baseline Profile은 `BaselineProfileRule`을 이용한 별도 테스트로 생성함. 이 테스트는 앱을 계측 실행하면서 호출되는 클래스/메서드를 프로파일러가 기록하고, 이를 사람이 읽을 수 있는 `.txt` 형식으로 출력함.

```kotlin
// benchmark/src/main/java/com/example/benchmark/BaselineProfileGenerator.kt
package com.example.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.example.app",
        includeInStartupProfile = true,
        maxIterations = 15
    ) {
        pressHome()
        startActivityAndWait()

        // 1. 홈 화면 진입 및 초기 로딩 대기
        device.wait(Until.hasObject(By.res(packageName, "home_root")), 5_000)

        // 2. 핵심 리스트 스크롤 (Compose LazyColumn 등)
        val list = device.findObject(By.res(packageName, "home_list"))
        list.setGestureMargin(device.displayWidth / 5)
        list.fling(Direction.DOWN)
        device.waitForIdle()

        // 3. 상세 화면 진입
        device.findObject(By.res(packageName, "list_item_0")).click()
        device.wait(Until.hasObject(By.res(packageName, "detail_root")), 5_000)

        // 4. 뒤로가기
        pressBack()
        device.wait(Until.hasObject(By.res(packageName, "home_root")), 5_000)

        // 5. 로그인 흐름 (별도 진입점인 경우)
        device.findObject(By.res(packageName, "profile_tab")).click()
        device.wait(Until.hasObject(By.res(packageName, "profile_root")), 5_000)
    }
}
```

여기서 중요한 것은 실제 사용자가 앱을 처음 켰을 때 거칠 가능성이 높은 "핵심 경로"만 시뮬레이션하는 것임. 앱의 모든 화면을 다 훑는 게 아니라, 온보딩/홈/가장 많이 쓰이는 상세 화면 정도로 좁혀야 프로파일이 비대해지지 않고 실제 자주 쓰이는 코드에 집중됨.

### 여러 흐름을 나눠서 생성

사용자 시나리오가 여러 개인 경우(로그인 전/후, 다양한 진입점) 별도 `@Test` 메서드로 분리하면 각각의 프로파일이 병합되어 최종 `baseline-prof.txt`에 반영됨.

```kotlin
@Test
fun generateOnboardingFlow() = rule.collect(packageName = PACKAGE) {
    startActivityAndWait()
    // 온보딩 흐름
}

@Test
fun generateLoggedInHomeFlow() = rule.collect(packageName = PACKAGE) {
    startActivityAndWait()
    // 로그인 후 홈 흐름
}

@Test
fun generateCheckoutFlow() = rule.collect(packageName = PACKAGE) {
    startActivityAndWait()
    // 결제 흐름
}
```

<br>

## 8. Baseline Profile 파일 구조 이해

생성된 `baseline-prof.txt`는 다음과 같은 형태의 텍스트임 (일부 발췌).

```text
Lcom/example/app/MainActivity;
HSPLcom/example/app/MainActivity;-><init>()V
HSPLcom/example/app/MainActivity;->onCreate(Landroid/os/Bundle;)V
Lcom/example/app/home/HomeViewModel;
HSPLcom/example/app/home/HomeViewModel;->loadData()V
```

각 줄 앞의 접두사는 해당 클래스/메서드가 프로파일링 중 어떤 방식으로 실행되었는지를 나타냄.

- `H` (Hot): 자주 실행된 메서드.
- `S` (Startup): 앱 시작 시점에 실행된 메서드.
- `P` (Post-startup): 시작 이후 실행된 메서드.
- `L`: 단순히 로드(클래스 로딩)된 클래스.

이 플래그들의 조합(`HSPL`, `SPL`, `PL` 등)에 따라 ART가 컴파일 우선순위와 방식을 결정함. 개발자가 이 파일을 직접 수정하는 일은 거의 없고, `BaselineProfileRule`이 자동 생성한 결과를 그대로 사용하는 것이 일반적임.

<br>

## 9. Startup Profile

`includeInStartupProfile = true` 옵션을 주면 `baseline-prof.txt`와 별개로 `startup-prof.txt`도 함께 생성됨. 이는 R8이 코드 축소/난독화를 수행할 때, 시작 시점에 필요한 클래스들이 같은 DEX 파일에 묶이도록(class re-ordering) 힌트를 주는 용도임. Baseline Profile이 "어떤 코드를 미리 컴파일할지"를 다룬다면, Startup Profile은 "어떤 코드를 같은 페이지에 배치해 I/O를 줄일지"를 다루는 개념으로 이해하면 됨. AGP 8.1 이상에서 지원됨.

<br>

## 10. 적용 및 빌드 통합

### 자동 생성 태스크

`androidx.baselineprofile` 플러그인을 앱 모듈에 적용하면 다음 Gradle 태스크가 자동 등록됨.

```bash
./gradlew :app:generateReleaseBaselineProfile
```

이 태스크는 내부적으로 `:benchmark` 모듈의 `BaselineProfileGenerator` 테스트를 실행하고, 결과를 `app/src/main/baseline-prof.txt`에 자동 복사함. `useConnectedDevices = true` 옵션이 설정되어 있으면 연결된 실기기/에뮬레이터에서 바로 실행됨.

### 프로파일 확인

빌드된 릴리즈 APK에 프로파일이 포함되었는지는 다음처럼 확인 가능.

```bash
unzip -l app-release.apk | grep baseline
```

`assets/dexopt/baseline.prof` 경로로 포함되어 있으면 정상.

### Compose 전용 프로파일 규칙

Jetpack Compose를 사용하는 프로젝트는 Compose 컴파일러가 자체적으로 안정성 관련 클래스를 생성하기 때문에, `androidx.compose.runtime` 관련 클래스들도 프로파일에 포함되도록 미리 정의된 규칙 세트(`androidx.compose.compiler` 라이브러리에서 제공)를 함께 사용하는 것이 권장됨. 별도 설정 없이도 최신 Compose 버전은 자체 Baseline Profile을 라이브러리 AAR에 포함해서 배포하므로, 앱의 프로파일과 자동 병합됨.

<br>

## 11. R8/ProGuard와의 상호작용

Baseline Profile은 클래스/메서드의 완전한 시그니처(패키지 경로 포함)를 참조하기 때문에, R8이 난독화를 수행하면 프로파일에 적힌 이름과 실제 컴파일된 클래스 이름이 불일치하는 문제가 발생할 수 있음. 이를 방지하기 위해 AGP의 Baseline Profile 플러그인은 릴리즈 빌드 시 R8의 매핑 파일(`mapping.txt`)을 참조해 프로파일 내 클래스/메서드 이름을 자동으로 리네이밍(rewrite)해줌. 따라서 개발자가 별도로 ProGuard 규칙을 추가할 필요는 없지만, 다음 사항은 유의해야 함.

- 프로파일 생성용 `benchmark` 빌드 타입은 `release`를 `initWith`로 상속하되 `isDebuggable = false`, 디버그 서명을 사용하도록 구성해야 실제 릴리즈와 유사한 조건에서 프로파일을 뽑을 수 있음.
- R8 축소(shrinking) 단계에서 실제로 제거되는 클래스는 프로파일에서도 자동 제외되므로, 프로파일 생성을 릴리즈에 가까운 빌드 타입에서 수행하는 것이 중요함.

<br>

## 12. 측정 결과 비교 및 통계 처리

`CompilationMode.None()`과 `CompilationMode.Partial(baselineProfileMode = Require)` 두 조건으로 각 10회 이상 반복 측정 후, median 값 기준으로 비교.

| 지표 | 미적용 (None) | 적용 (Baseline Profile) | 개선율 |
|---|---|---|---|
| timeToInitialDisplayMs (median) | 측정값 A | 측정값 B | (A-B)/A × 100% |
| timeToFullDisplayMs (median) | 측정값 C | 측정값 D | (C-D)/C × 100% |
| frameDurationCpuMs P50 (스크롤) | 측정값 E | 측정값 F | - |
| 실제 Jank 발생 프레임 비율 | 측정값 G% | 측정값 H% | - |

측정값은 기기 사양, 배터리 상태, thermal throttling 여부에 따라 편차가 크므로 다음 원칙을 지켜야 신뢰도 있는 비교가 가능함.

- 동일한 실기기에서, 동일한 배터리/온도 조건으로 측정 (에뮬레이터는 참고용, 실기기 측정 권장)
- 측정 직전 다른 앱을 모두 종료하고 화면을 켠 상태로 고정
- `iterations`를 최소 10회 이상으로 설정해 median 기준 비교
- Macrobenchmark 결과는 Android Studio의 "Benchmarks" 창 또는 `build/outputs/androidTest-results` 경로의 JSON에서 확인 가능하며, CI에서는 이 JSON을 파싱해 회귀(regression) 여부를 자동 판정하는 스크립트를 추가할 수 있음

<br>

## 13. Google Play Console Cloud Profile

Play Store를 통해 앱을 배포하면, Google Play는 실제 사용자 기기에서 수집된 사용 패턴을 집계해 Cloud Profile을 생성하고 이를 앱 설치 시 자동으로 함께 배포함. 개발자가 직접 만든 Baseline Profile과 Cloud Profile은 서로 대체 관계가 아니라 보완 관계이며, ART는 두 프로파일을 병합해 사용함.

- Baseline Profile: 개발자가 지정한 핵심 흐름, 앱 최초 배포 시점부터 즉시 적용
- Cloud Profile: 실사용자 데이터 기반, 배포 후 일정 기간 데이터가 누적되어야 정확도가 올라감

따라서 신규 기능 배포 초반에는 Baseline Profile의 역할이 크고, 앱이 충분히 성숙한 이후에는 Cloud Profile이 보완적으로 작동한다고 이해하면 됨.

<br>

## 14. CI/CD 통합

### GitHub Actions 예시

```yaml
# .github/workflows/macrobenchmark.yml
name: Macrobenchmark

on:
  pull_request:
    branches: [ main ]

jobs:
  benchmark:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm

      - name: Run Macrobenchmark on managed device
        run: ./gradlew :benchmark:pixel6Api33BenchmarkAndroidTest

      - name: Upload benchmark results
        uses: actions/upload-artifact@v4
        with:
          name: benchmark-results
          path: benchmark/build/outputs/androidTest-results
```

### Gradle Managed Devices 설정

CI 환경에서는 실기기 대신 Gradle Managed Devices(GMD)를 사용하는 것이 일반적임.

```kotlin
// benchmark/build.gradle.kts
android {
    testOptions {
        managedDevices {
            devices {
                create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api33") {
                    device = "Pixel 6"
                    apiLevel = 33
                    systemImageSource = "google"
                }
            }
        }
    }
}
```

GMD는 에뮬레이터 기반이라 실기기 대비 절대값의 정확도는 떨어지지만, PR마다 동일한 조건에서 상대 비교(회귀 감지)를 자동화하는 용도로는 충분함.

<br>

## 15. 트러블슈팅

### 프로파일이 적용되지 않는 경우

- `<profileable android:shell="true" />` 매니페스트 설정 누락 여부 확인
- `benchmark` 빌드 타입이 `release`를 올바르게 `initWith`하고 있는지 확인 (디버그 빌드는 애초에 AOT 컴파일 대상이 아님)
- `CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require)`로 명시했는데도 프로파일이 없으면 예외가 발생하므로, 먼저 `generateReleaseBaselineProfile` 태스크가 성공했는지 확인

### UiAutomator가 View를 찾지 못하는 경우

- `By.res(packageName, "id")`의 리소스 ID가 실제 컴포넌트에 부여되어 있는지 확인 (Compose는 `Modifier.testTag()` + `semantics { testTagsAsResourceId = true }` 필요)
- `device.wait(Until.hasObject(...), timeoutMs)`의 타임아웃을 늘려서 로딩 지연 여부 확인

### 측정값 편차가 너무 큰 경우

- thermal throttling 여부 확인 (연속 측정 시 기기 발열로 후반 반복이 느려짐)
- 백그라운드 앱/알림 종료 후 재측정
- 에뮬레이터 사용 시 호스트 머신의 다른 프로세스 부하 확인

### Compose 관련 프로파일 누락

- Compose Compiler 버전과 Compose Runtime 버전 간 호환성 확인 (버전 불일치 시 컴파일러가 생성하는 stability 관련 클래스가 프로파일과 어긋날 수 있음)
- `testTagsAsResourceId`를 `MainActivity`의 최상위 Composable에 설정했는지 확인

```kotlin
CompositionLocalProvider(
    LocalTestTagsAsResourceId provides true
) {
    // 앱 컨텐츠
}
```

<br>

## 16. 정리

- Baseline Profile은 콜드 스타트 시 JIT 대신 AOT 컴파일 경로를 미리 지정해 초기 실행 속도를 개선하는 수단이며, `ProfileInstaller`를 통해 앱 최초 유휴 시점에 등록됨
- Macrobenchmark는 이 효과를 정량적으로 측정하는 도구이며, 프로파일 생성 이전에 반드시 `CompilationMode.None()` 기준값을 먼저 확보해야 비교 가능
- `BaselineProfileRule`로 수집한 프로파일은 앱의 핵심 사용자 흐름(온보딩, 로그인, 홈 진입, 주요 리스트 스크롤, 상세 화면 등)을 포함해야 실효성이 있으며, 불필요하게 모든 화면을 다 포함시키면 프로파일이 비대해짐
- `baseline-prof.txt`의 `HSPL` 접두사는 각각 Hot/Startup/Post-startup/Loaded를 의미하며 ART의 컴파일 우선순위를 결정
- R8 난독화 환경에서는 AGP 플러그인이 매핑 파일을 참조해 프로파일 내 이름을 자동 리네이밍하므로 별도 대응 불필요
- Baseline Profile은 릴리즈 빌드에만 적용되므로 디버그 빌드에서는 개선 효과를 체감할 수 없음
- Google Play의 Cloud Profile과는 대체가 아닌 보완 관계이며, 신규 배포 초반에는 Baseline Profile의 비중이 큼
- CI에서는 Gradle Managed Devices를 활용해 PR마다 상대적 회귀 여부를 자동 감지하는 방식이 일반적
