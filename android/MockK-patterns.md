# MockK 활용법
1. 개요
2. mockk vs relaxed mockk
3. every / coEvery로 동작 정의하기
4. verify / coVerify로 호출 검증하기
5. Flow를 반환하는 함수 모킹
6. slot과 capture로 인자 캡처하기
7. Answers로 동적 응답 만들기
8. spyk로 부분 모킹하기
9. object, companion object, static 함수 모킹
10. Android 프레임워크 클래스 모킹
11. Hilt/DI 환경에서의 MockK 활용
12. Coroutine과 MockK 조합
13. Relaxed Mock의 함정과 명시적 스텁 전략
14. Fake와 Mock을 언제 나눠 쓸지
15. 자주 겪는 이슈
16. 정리

# 개요

MockK는 Kotlin 언어 특성(코루틴, object, 확장 함수, sealed class 등)을 온전히 지원하는 모킹 라이브러리다. Mockito도 Kotlin에서 쓸 수 있지만 `final` 클래스 모킹, suspend 함수 모킹, object 모킹 등에서 별도 설정이나 우회가 필요한 반면, MockK는 이런 것들을 라이브러리 차원에서 기본 지원한다.

안드로이드 프로젝트에서는 주로 Repository, UseCase, DataSource 같은 인터페이스/클래스를 모킹해 ViewModel이나 UseCase 단위 테스트를 격리시키는 용도로 쓴다.

<br>

```gradle
testImplementation "io.mockk:mockk:1.13.13"
testImplementation "io.mockk:mockk-android:1.13.13" // 계측 테스트(androidTest)에서 필요할 때
```

# mockk vs relaxed mockk

`mockk<T>()`로 만든 목은 스텁하지 않은 함수를 호출하면 예외를 던진다. 반면 `mockk<T>(relaxed = true)`는 스텁하지 않은 함수도 타입에 맞는 기본값(0, false, 빈 리스트, null 등)을 알아서 반환한다.

```kotlin
val strictMock = mockk<RecordingRepository>()
strictMock.getRecordingCount() // 스텁 안 했으면 MockKException 발생

val relaxedMock = mockk<RecordingRepository>(relaxed = true)
relaxedMock.getRecordingCount() // 예외 없이 0 반환
```

일반적으로는 strict mock으로 시작해서 필요한 함수만 명시적으로 `every`로 스텁하는 것이 테스트 의도를 명확히 드러낸다. relaxed mock은 인터페이스에 함수가 아주 많고 그중 일부만 이번 테스트와 관련 있을 때, 나머지 함수 호출까지 일일이 스텁하는 수고를 줄이기 위해 쓴다.

# every / coEvery로 동작 정의하기

일반 함수는 `every`, suspend 함수는 `coEvery`로 스텁한다.

```kotlin
val repository = mockk<RecordingRepository>()

// 동기 함수
every { repository.getCachedTitle() } returns "임시 제목"

// suspend 함수
coEvery { repository.fetchRecordings() } returns listOf(
    Recording("rec_1", "녹음 1"),
    Recording("rec_2", "녹음 2")
)

// 예외를 던지도록 스텁
coEvery { repository.deleteRecording("rec_1") } throws IOException("삭제 실패")

// 인자에 따라 다른 값을 반환
every { repository.getTitleById(any()) } returns "기본 제목"
every { repository.getTitleById("rec_1") } returns "녹음 1" // 더 구체적인 스텁이 우선 적용
```

같은 함수에 여러 `every`를 등록하면 더 구체적인 매처(정확한 값)가 `any()` 같은 넓은 매처보다 우선 매칭된다는 점을 기억해두면 스텁 순서를 고민할 필요가 줄어든다.

## 여러 번 호출 시 다른 값 반환

```kotlin
every { repository.getNextId() } returnsMany listOf(1, 2, 3)
// 첫 호출 1, 두 번째 호출 2, 세 번째 이후는 마지막 값(3) 유지
```

# verify / coVerify로 호출 검증하기

특정 함수가 호출됐는지, 몇 번 호출됐는지, 어떤 인자로 호출됐는지 검증한다.

```kotlin
val repository = mockk<RecordingRepository>(relaxed = true)
val viewModel = RecordingViewModel(repository)

viewModel.onIntent(RecordingIntent.Delete("rec_1"))

coVerify(exactly = 1) { repository.deleteRecording("rec_1") }
coVerify(exactly = 0) { repository.deleteRecording("rec_2") }

// 호출 순서까지 검증
coVerifyOrder {
    repository.fetchRecordings()
    repository.deleteRecording("rec_1")
}

// 아예 호출되지 않았는지 확인
verify { repository wasNot Called }
```

`exactly`를 생략하면 기본값은 "최소 1번 이상 호출됨"이다. 정확히 몇 번 호출됐는지가 중요한 테스트라면 `exactly`를 명시하는 습관을 들이는 것이 좋다.

# Flow를 반환하는 함수 모킹

Repository가 `Flow<T>`를 반환하는 경우 `flowOf`나 `MutableStateFlow`로 감싸서 스텁한다.

```kotlin
every { repository.observeRecordings() } returns flowOf(
    listOf(Recording("rec_1", "녹음 1"))
)

// 여러 번 값이 바뀌는 시나리오를 테스트하고 싶다면 MutableStateFlow를 직접 노출
val recordingsFlow = MutableStateFlow<List<Recording>>(emptyList())
every { repository.observeRecordings() } returns recordingsFlow

// 테스트 코드에서 흐름 중간에 값을 바꿈
recordingsFlow.value = listOf(Recording("rec_1", "녹음 1"))
```

시간에 따라 여러 번 방출해야 하는 Flow는 목보다 Fake 클래스로 직접 `MutableStateFlow`를 관리하는 편이 코드가 더 읽기 쉬운 경우가 많다. 이 판단 기준은 문서 뒷부분의 "Fake와 Mock을 언제 나눠 쓸지" 섹션에서 다룬다.

# slot과 capture로 인자 캡처하기

함수에 전달된 인자를 나중에 꺼내 검증하고 싶을 때 `slot`을 사용한다. 콜백 인자나 복잡한 객체를 통째로 검증할 때 유용하다.

```kotlin
val callbackSlot = slot<(Result<Recording>) -> Unit>()

every { repository.saveRecording(any(), capture(callbackSlot)) } just Runs

viewModel.onSave(recording)

// 캡처된 콜백을 직접 실행시켜 성공 케이스를 트리거
callbackSlot.captured.invoke(Result.success(recording))

assertThat(viewModel.uiState.value).isEqualTo(RecordingUiState.Saved)
```

여러 번 호출되는 함수의 인자를 전부 모으고 싶다면 `mutableListOf()`와 `capture(slot, list)` 조합 대신 `every { ... } answers { ... }` 안에서 `it`을 직접 리스트에 append하는 방식도 흔히 쓰인다.

```kotlin
val capturedTitles = mutableListOf<String>()
every { repository.logTitle(capture(capturedTitles)) } just Runs

viewModel.onTitleChanged("a")
viewModel.onTitleChanged("b")

assertThat(capturedTitles).containsExactly("a", "b")
```

# Answers로 동적 응답 만들기

인자 값에 따라 계산된 응답을 돌려주고 싶을 때는 `answers { }` 블록에서 `it.invocation.args`로 인자에 접근한다.

```kotlin
every { repository.getTitleById(any()) } answers {
    val id = firstArg<String>()
    "제목_$id"
}

coEvery { repository.fetchRecordingsAfter(any()) } coAnswers {
    val timestamp = firstArg<Long>()
    fakeRecordings.filter { it.createdAt > timestamp }
}
```

suspend 함수 안에서 동적 로직을 실행해야 하면 `answers` 대신 `coAnswers`를 써야 한다는 점에 유의한다.

# spyk로 부분 모킹하기

실제 객체의 대부분 동작은 그대로 쓰면서 일부 함수만 오버라이드하고 싶을 때 `spyk`를 사용한다.

```kotlin
val realFormatter = DateFormatter()
val spiedFormatter = spyk(realFormatter)

every { spiedFormatter.now() } returns fixedTimestamp
// format() 같은 다른 함수는 실제 구현이 그대로 호출됨

val result = spiedFormatter.formatWithCurrentTime(recording)
```

시간, 랜덤 값처럼 테스트에서 고정해야 하는 값만 오버라이드하고 나머지 로직은 실제 구현을 검증하고 싶을 때 유용하다. 다만 남용하면 "실제로 뭐가 진짜 실행되는지" 파악이 어려워지므로, 전체를 목킹할 수 있는 상황에서는 일반 `mockk`를 우선 고려한다.

# object, companion object, static 함수 모킹

Kotlin의 `object` 싱글턴이나 `companion object`, Java의 static 함수는 `mockkObject` / `mockkStatic`으로 모킹한다.

```kotlin
object TimeProvider {
    fun now(): Long = System.currentTimeMillis()
}

@Test
fun `TimeProvider를 모킹해 고정 시간으로 테스트`() {
    mockkObject(TimeProvider)
    every { TimeProvider.now() } returns 1_700_000_000_000L

    val result = createRecordingEntity()
    assertThat(result.createdAt).isEqualTo(1_700_000_000_000L)

    unmockkObject(TimeProvider) // 다른 테스트에 영향 주지 않도록 반드시 해제
}
```

```kotlin
// Java static 함수 (예: android.util.Log)
mockkStatic(Log::class)
every { Log.d(any(), any()) } returns 0
```

`mockkObject`/`mockkStatic`은 전역 상태를 바꾸는 것이므로, 테스트가 끝나면 `unmockkObject`/`unmockkStatic` 또는 `unmockkAll()`로 반드시 해제해야 다른 테스트에 부작용이 번지지 않는다. `@After`에서 `unmockkAll()`을 습관적으로 호출해두는 것이 안전하다.

```kotlin
@After
fun tearDown() {
    unmockkAll()
}
```

# Android 프레임워크 클래스 모킹

`Context`, `Intent`, `Uri` 같은 안드로이드 프레임워크 클래스는 순수 JVM 단위 테스트(`test` 소스셋)에서는 기본적으로 스텁(stub) 구현이라 실제 로직이 없다. Robolectric 없이 순수 MockK로 이런 클래스를 다루려면 대부분의 함수를 직접 스텁해야 한다.

```kotlin
val mockContext = mockk<Context>(relaxed = true)
every { mockContext.getString(R.string.app_name) } returns "차곡"
every { mockContext.packageName } returns "com.example.chagok"
```

`Uri`처럼 정적 팩토리 함수(`Uri.parse`)를 쓰는 클래스는 `mockkStatic(Uri::class)`로 정적 함수 자체를 모킹해야 하는 경우가 많다.

```kotlin
mockkStatic(Uri::class)
val fakeUri = mockk<Uri>()
every { Uri.parse(any()) } returns fakeUri
every { fakeUri.toString() } returns "content://fake"
```

안드로이드 프레임워크 의존이 깊은 로직(파일 시스템 접근, ContentResolver 등)은 순수 MockK보다 Robolectric을 함께 쓰는 것이 실제 동작에 가까운 검증이 가능하다.

# Hilt/DI 환경에서의 MockK 활용

Hilt로 주입되는 의존성은 테스트에서 실제 모듈을 `@TestInstallIn`으로 교체하거나, ViewModel을 직접 생성자 호출로 만들어 순수 단위 테스트로 격리하는 두 가지 접근이 있다. 순수 ViewModel 단위 테스트에서는 Hilt 컨테이너 자체를 띄우지 않고, 생성자에 MockK로 만든 목을 직접 넣는 방식이 훨씬 빠르고 단순하다.

```kotlin
class RecordingViewModelTest {

    private val repository = mockk<RecordingRepository>()
    private val analytics = mockk<AnalyticsLogger>(relaxed = true)

    private lateinit var viewModel: RecordingViewModel

    @Before
    fun setUp() {
        coEvery { repository.observeRecordings() } returns flowOf(emptyList())
        viewModel = RecordingViewModel(repository, analytics)
    }
}
```

Hilt 컨테이너까지 띄워야 하는 계측 테스트(androidTest)에서 특정 모듈만 목으로 바꾸고 싶다면 `@TestInstallIn` + `@Module`로 실제 모듈을 대체하는 방식을 쓰지만, 이는 MockK 자체보다는 Hilt 테스트 설정의 영역이라 대부분의 ViewModel/UseCase 단위 테스트에서는 생성자 주입 방식만으로 충분하다.

# Coroutine과 MockK 조합

`coEvery`/`coVerify`는 그 자체로 suspend 함수를 다루지만, 실제 실행은 여전히 `runTest` 코루틴 컨텍스트 안에서 이뤄져야 한다.

```kotlin
@Test
fun `저장 실패 시 에러 상태로 전이한다`() = runTest {
    val repository = mockk<RecordingRepository>()
    coEvery { repository.saveRecording(any()) } throws IOException("network")

    val viewModel = RecordingViewModel(repository)
    viewModel.onIntent(RecordingIntent.Save(recording))

    assertThat(viewModel.uiState.value).isInstanceOf(RecordingUiState.Error::class.java)
}
```

Turbine과 조합할 때는 MockK로 Repository의 동작을 정의해두고, Turbine으로는 ViewModel의 `StateFlow`/`SharedFlow` 방출을 검증하는 역할 분담이 자연스럽다.

```kotlin
@Test
fun `저장 성공 시 Saved 상태와 토스트 이벤트가 발생한다`() = runTest {
    val repository = mockk<RecordingRepository>()
    coEvery { repository.saveRecording(any()) } returns Result.success(Unit)

    val viewModel = RecordingViewModel(repository)

    turbineScope {
        val stateTurbine = viewModel.uiState.testIn(this)
        val effectTurbine = viewModel.uiEffect.testIn(this)

        stateTurbine.skipItems(1) // 초기 상태
        viewModel.onIntent(RecordingIntent.Save(recording))

        assertThat(stateTurbine.awaitItem()).isEqualTo(RecordingUiState.Saved)
        assertThat(effectTurbine.awaitItem()).isEqualTo(RecordingUiEffect.ShowToast("저장 완료"))

        stateTurbine.cancelAndIgnoreRemainingEvents()
        effectTurbine.cancelAndIgnoreRemainingEvents()
    }

    coVerify(exactly = 1) { repository.saveRecording(recording) }
}
```

# Relaxed Mock의 함정과 명시적 스텁 전략

relaxed mock은 편리하지만, 스텁을 깜빡한 함수가 조용히 기본값(0, false, 빈 컬렉션)을 반환하면서 테스트가 "우연히" 통과하는 경우가 생긴다. 예를 들어 `getRecordingCount()`를 스텁하지 않았는데 relaxed mock이 0을 반환해서, 실제로는 개수를 세는 로직이 전혀 검증되지 않은 채로 테스트가 초록불이 되는 식이다.

이를 방지하는 실무 전략은 다음과 같다.

- 이번 테스트에서 실제로 검증하려는 함수는 relaxed 여부와 무관하게 항상 `every`/`coEvery`로 명시적으로 스텁한다.
- relaxed는 "이 테스트와 무관한, 호출은 되지만 반환값이 로직에 영향을 주지 않는 함수들"에 한해서만 의존한다.
- 의심스러우면 strict mock으로 시작해서, `MockKException: no answer found` 예외가 나는 함수를 하나씩 스텁해나가는 방식이 결과적으로 더 안전하다.

# Fake와 Mock을 언제 나눠 쓸지

- 함수 호출 여부/인자/횟수 자체가 검증 대상이라면 Mock(MockK)이 적합하다. "삭제 함수가 정확히 이 id로 한 번 호출됐는가"처럼 상호작용을 검증할 때다.
- 여러 번의 상태 변화, 시간에 따른 Flow 방출처럼 "행동의 결과로 상태가 어떻게 흘러가는가"를 보고 싶다면 Fake(직접 구현한 인메모리 클래스)가 코드도 간결하고 실제에 가깝다.
- 하나의 테스트 클래스 안에서 Mock과 Fake를 섞어 쓰는 것도 흔하다. 예를 들어 Repository는 Fake로, 외부 Analytics 로깅 같은 부수 효과는 Mock으로 처리해 호출 여부만 확인하는 식이다.

```kotlin
class FakeRecordingRepository : RecordingRepository {
    private val recordings = MutableStateFlow<List<Recording>>(emptyList())

    override fun observeRecordings(): Flow<List<Recording>> = recordings

    override suspend fun saveRecording(recording: Recording): Result<Unit> {
        recordings.value = recordings.value + recording
        return Result.success(Unit)
    }
}

@Test
fun `Fake Repository와 Mock Analytics를 함께 사용`() = runTest {
    val fakeRepository = FakeRecordingRepository()
    val analytics = mockk<AnalyticsLogger>(relaxed = true)
    val viewModel = RecordingViewModel(fakeRepository, analytics)

    viewModel.onIntent(RecordingIntent.Save(recording))

    verify { analytics.logEvent("recording_saved") }
}
```

# 자주 겪는 이슈

- `mockkStatic`/`mockkObject`로 전역 상태를 바꾼 뒤 `unmockkAll()`을 호출하지 않으면, 다른 테스트 클래스나 이후 테스트 메서드에서 원인 불명의 실패가 발생한다. `@After`에 항상 `unmockkAll()`을 넣어두는 습관이 필요하다.
- `every { }`로 스텁한 함수와 실제 호출 시 인자가 정확히 매칭되지 않으면(예: `any()` 대신 구체적인 인스턴스를 넣었는데 `equals`가 오버라이드되지 않은 data class가 아닌 일반 클래스인 경우) 스텁이 적용되지 않고 예외가 발생한다.
- suspend 함수에 `every`를 쓰거나, 일반 함수에 `coEvery`를 쓰는 실수가 흔하다. 컴파일은 되지만 런타임에 스텁이 적용되지 않아 원인 파악이 오래 걸릴 수 있다.
- `verify`는 기본적으로 "지금까지의 호출 중에" 조건을 만족하는지 확인하는데, 코루틴이 아직 완료되지 않은 시점에 호출하면 실제로는 나중에 호출될 함수가 아직 검증 시점에 반영되지 않아 실패로 보일 수 있다. `runTest` 안에서 순서를 잘 맞추거나 `advanceUntilIdle()` 후에 검증한다.
- final 클래스는 기본적으로 MockK가 모킹 가능하지만, Kotlin 프로젝트가 아닌 일부 Java 라이브러리 클래스는 `mock-maker-inline` 설정이 필요할 수 있다. `mockk-agent` 의존성이 자동으로 처리해주는 경우가 대부분이지만, 특이한 클래스에서 실패하면 이 설정을 확인한다.
- 같은 함수에 여러 `every`를 등록했는데 의도와 다른 스텁이 적용되는 경우, 매처의 구체성 순서(정확한 값 > 커스텀 매처 > `any()`)를 다시 확인해야 한다.

# 정리

MockK는 `every`/`coEvery`로 스텁, `verify`/`coVerify`로 호출 검증이라는 기본 축을 중심으로, `slot`(인자 캡처), `answers`(동적 응답), `spyk`(부분 모킹), `mockkObject`/`mockkStatic`(object/static 모킹)으로 대부분의 Kotlin/Android 테스트 시나리오를 커버한다.

relaxed mock은 편의성과 맞바꿔 "스텁 누락을 조용히 감추는" 위험이 있으므로, 실제로 검증하려는 함수는 항상 명시적으로 스텁하는 습관이 중요하다. 상호작용 자체(호출 여부/횟수/인자)가 검증 대상이면 Mock을, 시간에 따른 상태 변화를 보고 싶다면 Fake를 우선 고려하고, 필요하면 한 테스트 안에서 둘을 섞어 쓰는 것도 자연스럽다. Turbine과 조합할 때는 MockK로 Repository 동작을 정의하고 Turbine으로 ViewModel의 Flow 방출을 검증하는 역할 분담이 MVI 구조의 ViewModel 테스트에서 가장 실무적인 패턴이다.
