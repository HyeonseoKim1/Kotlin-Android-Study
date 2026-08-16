# Configuration Change

목차
1. Configuration Change란?
2. Configuration Change가 발생하는 상황
3. Configuration Change 발생 시 기본 동작
4. Activity 재생성 과정 상세
5. onSaveInstanceState()와 상태 저장
6. ViewModel과 Configuration Change
7. rememberSaveable과 Compose에서의 상태 보존
8. android:configChanges를 이용한 재생성 방지
9. Configuration 클래스와 리소스 재로딩
10. 화면 회전과 레이아웃 대응
11. 프로세스 종료와 Configuration Change의 차이
12. SavedStateHandle을 이용한 프로세스 종료 대응
13. Coroutine/Flow 작업과 Configuration Change
14. Fragment에서의 Configuration Change 처리
15. 멀티 윈도우와 폴더블 기기에서의 Configuration Change
16. 실전에서 겪는 Configuration Change 이슈
17. 주의사항과 자주 하는 실수
18. 정리

<br>

## 1. Configuration Change란?

Configuration Change는 기기의 화면 방향, 크기, 언어, 다크 모드, 폰트 크기 등 런타임 환경 설정이 변경되는 것을 의미한다. 가장 흔한 예시는 화면 회전(세로 ↔ 가로)이지만, 그 외에도 다양한 상황에서 발생한다.

Android는 기본적으로 Configuration Change가 발생하면 현재 실행 중인 Activity를 **완전히 소멸시키고 다시 생성**한다. 이는 버그가 아니라 의도된 설계다. 화면 방향이나 언어처럼 리소스 선택에 영향을 주는 값이 바뀌면, `res/layout-land`, `res/values-ko` 같은 대체 리소스를 다시 로드해서 올바른 UI를 그리기 위해서다.

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Lifecycle", "onCreate 호출됨")
        // 화면을 회전하면 이 로그가 다시 찍힌다
    }
}
```

즉 Configuration Change를 이해한다는 것은 "Activity가 왜, 언제 다시 만들어지는가"와 "그 과정에서 상태를 어떻게 잃지 않을 것인가"를 이해하는 것과 같다. Android 개발에서 가장 흔하게 겪는 버그 중 하나가 바로 회전 시 입력값이 사라지거나, 진행 중이던 네트워크 요청이 중복 실행되는 문제이며, 이는 대부분 Configuration Change에 대한 이해 부족에서 비롯된다.

<br>

## 2. Configuration Change가 발생하는 상황

Configuration Change를 유발하는 대표적인 상황들은 다음과 같다.

- 화면 회전 (세로 ↔ 가로)
- 시스템 언어 변경 (다국어 설정 변경)
- 다크 모드 ↔ 라이트 모드 전환
- 시스템 폰트 크기 변경 (접근성 설정)
- 디스플레이 크기 변경 (멀티 윈도우 진입/이탈, 폴더블 기기의 접힘/펼침)
- 키보드 표시 여부 변경 (물리 키보드 연결/해제)
- 화면 밀도(DPI) 변경 (외부 디스플레이 연결 등)

```xml
<!-- res/values/strings.xml -->
<string name="app_title">차곡</string>

<!-- res/values-en/strings.xml -->
<string name="app_title">ChaGok</string>
```

위와 같이 리소스가 언어별로 분리되어 있다면, 시스템 언어가 바뀌는 순간 시스템은 어떤 리소스 세트를 사용해야 할지 다시 판단해야 한다. 이 판단과 리소스 재적용 과정이 바로 Activity 재생성으로 이어진다.

모든 Configuration Change가 항상 Activity를 재생성시키는 것은 아니다. 매니페스트의 `android:configChanges` 속성에 특정 변경 유형이 명시되어 있다면, 해당 유형에 대해서는 재생성 대신 `onConfigurationChanged()` 콜백만 호출된다. 이 부분은 8장에서 자세히 다룬다.

<br>

## 3. Configuration Change 발생 시 기본 동작

Configuration Change가 발생하면 시스템은 다음과 같은 순서로 Activity를 재생성한다.

```
onPause()
    ↓
onStop()
    ↓
onSaveInstanceState()  ← Bundle에 상태 저장
    ↓
onDestroy()
    ↓
[새 Configuration 값으로 새 Activity 인스턴스 생성]
    ↓
onCreate(savedInstanceState)  ← 저장된 Bundle 전달받음
    ↓
onStart()
    ↓
onRestoreInstanceState()  ← onCreate 이후, onStart와 onResume 사이
    ↓
onResume()
```

핵심은 기존 Activity 인스턴스가 완전히 파괴되고, 새로운 인스턴스가 만들어진다는 점이다. 따라서 Activity의 멤버 변수에 저장해둔 값은 기본적으로 모두 사라진다.

```kotlin
class CounterActivity : ComponentActivity() {
    private var count = 0 // 회전하면 이 값은 초기화됨

    fun increment() {
        count++
        updateUi()
    }
}
```

이 문제를 해결하기 위한 대표적인 방법이 `onSaveInstanceState()`를 이용한 상태 저장, 그리고 더 현대적인 방식인 `ViewModel`을 이용한 상태 보존이다. 이 둘은 성격이 다르다. `onSaveInstanceState()`는 Bundle에 직렬화 가능한 작은 데이터를 저장하는 방식이고, `ViewModel`은 Activity와 별개의 생명주기를 가지는 객체 자체를 유지하는 방식이다.

<br>

## 4. Activity 재생성 과정 상세

Activity가 재생성될 때, 새로 생성되는 인스턴스는 이전 인스턴스와 완전히 다른 객체다. 메모리 주소도 다르고, 필드 값도 기본값으로 초기화된 상태에서 시작한다.

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Lifecycle", "새 인스턴스: ${this.hashCode()}")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("Lifecycle", "소멸되는 인스턴스: ${this.hashCode()}")
    }
}
```

화면을 회전하면 로그에 서로 다른 `hashCode()` 값이 찍히는 것을 확인할 수 있다. 이는 Configuration Change로 인한 재생성이 단순한 "새로고침"이 아니라 "완전히 새로운 객체의 생성"임을 보여준다.

재생성 과정에서 다음과 같은 리소스들도 함께 다시 로드된다.

- 레이아웃 XML (`res/layout`, `res/layout-land` 등)
- 문자열, 색상, 치수 등 값 리소스
- 드로어블 리소스 (해상도별 이미지 등)

이 때문에 `onCreate()`에서 무거운 초기화 작업(대용량 파일 파싱, 복잡한 객체 생성)을 매번 반복하면, 회전할 때마다 불필요한 연산이 다시 실행되어 성능 저하나 사용자 경험 저하로 이어질 수 있다. 이런 무거운 데이터는 Activity가 아니라 `ViewModel`에 두어야 회전과 무관하게 유지된다.

<br>

## 5. onSaveInstanceState()와 상태 저장

`onSaveInstanceState()`는 Activity가 소멸되기 전에 간단한 UI 상태를 `Bundle`에 저장할 수 있게 해주는 콜백이다. 저장된 값은 `onCreate()`나 `onRestoreInstanceState()`의 파라미터로 다시 전달된다.

```kotlin
class SearchActivity : ComponentActivity() {

    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        searchQuery = savedInstanceState?.getString(KEY_QUERY) ?: ""
        setupSearchView(searchQuery)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_QUERY, searchQuery)
    }

    companion object {
        private const val KEY_QUERY = "key_query"
    }
}
```

`onSaveInstanceState()`는 다음과 같은 특징과 제약을 가진다.

- `Bundle`에 담을 수 있는 데이터는 기본 타입, `Parcelable`, `Serializable` 등으로 제한된다.
- `Bundle`의 크기는 시스템 프로세스 간 통신(Binder)을 거치기 때문에 제한이 있다. 대용량 데이터(리스트 전체, 이미지 등)를 저장하면 `TransactionTooLargeException`이 발생할 수 있다.
- 회전 같은 Configuration Change뿐 아니라, 시스템이 메모리 부족으로 프로세스를 종료했다가 사용자가 뒤로가기로 복귀했을 때도 동일하게 호출된다. 즉 두 가지 상황(Config Change / 프로세스 종료) 모두를 커버하는 안전망 역할을 한다.

`EditText`처럼 View 자체에 상태가 있는 UI 컴포넌트는, `id`가 지정되어 있다면 시스템이 자동으로 `onSaveInstanceState()`/`onRestoreInstanceState()`를 통해 값을 보존해준다. 별도 코드 없이도 텍스트 입력값이나 스크롤 위치가 회전 후에도 유지되는 이유가 이것이다.

```xml
<EditText
    android:id="@+id/editSearch"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

반대로 `id`가 없는 View는 상태가 자동으로 보존되지 않으므로, 커스텀 View를 만들 때는 `id`를 부여하고 필요하다면 `onSaveInstanceState()`/`onRestoreInstanceState()`를 직접 오버라이드해야 한다.

<br>

## 6. ViewModel과 Configuration Change

`ViewModel`은 Configuration Change 문제를 근본적으로 해결하기 위해 Jetpack에서 제공하는 컴포넌트다. `ViewModel`의 생명주기는 Activity/Fragment의 생명주기와 분리되어 있어서, Configuration Change로 인한 재생성 시에도 동일한 `ViewModel` 인스턴스가 그대로 유지된다.

```kotlin
class RecordingViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    fun startRecording() {
        _uiState.update { it.copy(isRecording = true) }
    }
}

class RecordingActivity : ComponentActivity() {
    private val viewModel: RecordingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Lifecycle", "ViewModel: ${viewModel.hashCode()}")
        // 화면을 회전해도 이 hashCode는 동일하게 유지된다
    }
}
```

`ViewModel`이 이렇게 동작할 수 있는 이유는, 내부적으로 `ViewModelStore`라는 저장소가 Activity의 `NonConfigurationInstance`에 보관되어, Configuration Change로 인한 재생성 과정에서 시스템이 이를 새 Activity 인스턴스에 넘겨주기 때문이다.

```kotlin
class RecordingViewModel(
    private val voiceNoteRepository: VoiceNoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecordState>(RecordState.Idle)
    val uiState: StateFlow<RecordState> = _uiState.asStateFlow()

    fun loadVoiceNote(id: Long) {
        viewModelScope.launch {
            val note = voiceNoteRepository.getById(id)
            _uiState.value = RecordState.Loaded(note)
        }
    }
}
```

중요한 점은, `ViewModel`은 Configuration Change에는 살아남지만 다음 두 경우에는 함께 소멸된다는 것이다.

1. 사용자가 뒤로가기 등으로 Activity/Fragment를 완전히 종료(finish)했을 때 (`onCleared()` 호출)
2. 시스템이 메모리 부족으로 프로세스 자체를 강제 종료했을 때

즉 `ViewModel`은 "Configuration Change 동안의 상태 보존"은 완벽히 해결해주지만, "프로세스가 통째로 죽는 상황"에는 별도의 대응(`SavedStateHandle`)이 필요하다. 이 부분은 12장에서 다룬다.

<br>

## 7. rememberSaveable과 Compose에서의 상태 보존

Jetpack Compose에서는 `remember`로 선언한 상태가 Configuration Change 시 기본적으로 초기화된다. `remember`는 Composition이 유지되는 동안만 값을 기억하는데, Configuration Change로 Activity가 재생성되면 Composition 자체가 새로 만들어지기 때문이다.

```kotlin
@Composable
fun CounterScreen() {
    var count by remember { mutableStateOf(0) }
    // 화면을 회전하면 count가 0으로 초기화된다

    Button(onClick = { count++ }) {
        Text("Count: $count")
    }
}
```

이를 해결하려면 `rememberSaveable`을 사용해야 한다. `rememberSaveable`은 내부적으로 `onSaveInstanceState()`와 동일한 메커니즘(Bundle)을 사용해 값을 저장하고 복원한다.

```kotlin
@Composable
fun CounterScreen() {
    var count by rememberSaveable { mutableStateOf(0) }
    // 화면을 회전해도 count 값이 유지된다

    Button(onClick = { count++ }) {
        Text("Count: $count")
    }
}
```

기본 타입 외의 커스텀 객체를 저장하려면 `Parcelable`을 구현하거나 `Saver`를 직접 정의해야 한다.

```kotlin
data class SearchFilter(val keyword: String, val sortOrder: SortOrder) : Parcelable {
    // @Parcelize 어노테이션 사용 시 자동 구현
}

@Composable
fun SearchScreen() {
    var filter by rememberSaveable { mutableStateOf(SearchFilter("", SortOrder.LATEST)) }
    // ...
}
```

복잡한 객체나 리스트를 커스텀하게 저장/복원하고 싶다면 `mapSaver`나 `listSaver`를 사용할 수 있다.

```kotlin
val VoiceNoteFilterSaver = mapSaver(
    save = { mapOf("keyword" to it.keyword, "sortOrder" to it.sortOrder.name) },
    restore = {
        VoiceNoteFilter(
            keyword = it["keyword"] as String,
            sortOrder = SortOrder.valueOf(it["sortOrder"] as String)
        )
    }
)

@Composable
fun VoiceNoteListScreen() {
    var filter by rememberSaveable(stateSaver = VoiceNoteFilterSaver) {
        mutableStateOf(VoiceNoteFilter())
    }
}
```

실무에서는 화면의 UI 상태 전반을 `ViewModel`이 관리하고, `rememberSaveable`은 순수하게 UI 레벨의 임시 상태(다이얼로그 표시 여부, 스크롤 위치, 입력 중인 텍스트 등)에 한정해서 사용하는 조합이 흔하다. 비즈니스 로직과 관련된 데이터는 `ViewModel`의 `StateFlow`로, 화면 전환과 무관하게 유지되어야 할 필요는 없지만 회전 시에는 남아있어야 하는 값은 `rememberSaveable`로 나누는 것이 합리적이다.

<br>

## 8. android:configChanges를 이용한 재생성 방지

매니페스트의 `android:configChanges` 속성을 사용하면, 특정 Configuration 변경에 대해 Activity 재생성을 막고 대신 `onConfigurationChanged()` 콜백만 호출되도록 할 수 있다.

```xml
<activity
    android:name=".RecordingActivity"
    android:configChanges="orientation|screenSize|keyboardHidden" />
```

```kotlin
class RecordingActivity : ComponentActivity() {
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 가로 모드에 맞는 레이아웃 수동 조정
        } else {
            // 세로 모드에 맞는 레이아웃 수동 조정
        }
    }
}
```

이 방식은 Activity가 재생성되지 않으므로 상태 손실 문제 자체가 발생하지 않는다는 장점이 있지만, 다음과 같은 이유로 일반적으로는 권장되지 않는다.

- 대체 리소스(`layout-land`, `values-ko` 등)가 자동으로 다시 적용되지 않아, 개발자가 직접 리소스 전환 로직을 작성해야 한다.
- 실수로 처리하지 않은 Configuration 변경 유형이 있으면, 앱이 새 설정을 전혀 반영하지 못하는 버그로 이어질 수 있다.
- Android 공식 문서에서도 "최후의 수단(last resort)"으로만 사용하도록 권장하고 있다.

주로 다음과 같이 정말 예외적인 경우에만 제한적으로 사용된다.

- 카메라 프리뷰처럼 재생성 비용이 매우 크고, 회전에 따른 레이아웃 차이가 거의 없는 화면
- 게임처럼 자체적으로 렌더링과 상태 관리를 완전히 통제하는 화면

일반적인 화면에서는 `configChanges`로 재생성을 막기보다는, `ViewModel`과 `rememberSaveable`(또는 `onSaveInstanceState`)을 통해 재생성을 허용하면서 상태를 보존하는 방식이 Android가 권장하는 정석적인 접근이다.

<br>

## 9. Configuration 클래스와 리소스 재로딩

`Configuration` 클래스는 현재 기기의 환경 설정 정보를 담고 있는 객체로, `Resources.getConfiguration()`을 통해 언제든 조회할 수 있다.

```kotlin
val configuration = resources.configuration
val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
val isDarkMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
    Configuration.UI_MODE_NIGHT_YES
val screenWidthDp = configuration.screenWidthDp
```

시스템은 Configuration Change가 발생하면, 새로운 `Configuration` 값을 기준으로 어떤 리소스 디렉토리를 사용할지 다시 결정한다. Android의 리소스 시스템은 디렉토리 이름의 접미사(qualifier)를 기준으로 가장 적합한 리소스를 자동으로 선택한다.

```
res/
  layout/                  (기본)
  layout-land/             (가로 모드)
  layout-sw600dp/          (너비 600dp 이상, 태블릿)
  values/
  values-ko/                (한국어)
  values-night/              (다크 모드)
  drawable-xxhdpi/           (고밀도 화면)
```

이 자동 선택 메커니즘 덕분에, 대부분의 경우 개발자는 Configuration Change를 직접 감지하는 코드를 작성할 필요 없이 적절한 리소스 세트만 준비해두면 된다. 재생성 자체가 이 리소스 재선택 과정을 트리거하는 핵심 장치인 셈이다.

Compose에서는 `LocalConfiguration.current`를 통해 동일한 정보에 접근할 수 있다.

```kotlin
@Composable
fun AdaptiveLayout() {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        LandscapeContent()
    } else {
        PortraitContent()
    }
}
```

<br>

## 10. 화면 회전과 레이아웃 대응

화면 회전은 가장 흔하게 마주치는 Configuration Change이며, 레이아웃 설계 단계에서부터 고려하는 것이 좋다.

전통적인 View 시스템에서는 세로/가로 각각의 레이아웃 XML을 별도로 준비하는 방식이 흔했다.

```
res/layout/activity_recording.xml         (세로)
res/layout-land/activity_recording.xml    (가로)
```

두 레이아웃 모두 동일한 View `id`를 사용한다면, Activity/Fragment 코드는 수정 없이도 재생성 시 자동으로 알맞은 레이아웃을 사용하게 된다.

Compose에서는 별도 파일을 만들지 않고, 하나의 Composable 안에서 `LocalConfiguration`이나 `BoxWithConstraints`를 이용해 조건부로 레이아웃을 분기하는 방식이 일반적이다.

```kotlin
@Composable
fun RecordingScreen(uiState: RecordingUiState) {
    val configuration = LocalConfiguration.current

    if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        Row(modifier = Modifier.fillMaxSize()) {
            WaveformView(modifier = Modifier.weight(1f))
            ControlPanel(modifier = Modifier.weight(1f))
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            WaveformView(modifier = Modifier.weight(1f))
            ControlPanel(modifier = Modifier.fillMaxWidth())
        }
    }
}
```

회전 시 반드시 유지되어야 하는 상태(녹음 진행 시간, 재생 위치, 입력 중인 텍스트 등)는 `ViewModel`이나 `rememberSaveable`을 통해 관리해야 하며, 특히 미디어 재생/녹음처럼 상태를 잃으면 사용자에게 치명적인 기능은 회전 테스트를 QA 체크리스트에 반드시 포함시키는 것이 좋다.

<br>

## 11. 프로세스 종료와 Configuration Change의 차이

Configuration Change와 자주 혼동되지만 원인이 완전히 다른 상황이 바로 "프로세스 종료 후 재시작"이다. 두 상황 모두 Activity가 `onCreate()`부터 다시 시작된다는 점은 비슷하지만, 근본 원인과 대응 방법이 다르다.

| 구분 | Configuration Change | 프로세스 종료(백그라운드 kill) |
|---|---|---|
| 원인 | 회전, 언어 변경 등 환경 설정 변화 | 시스템 메모리 부족으로 인한 강제 종료 |
| ViewModel | 유지됨 | 소멸됨(새로 생성) |
| onSaveInstanceState Bundle | 저장/복원됨 | 저장/복원됨(단, 프로세스 재시작 후) |
| 정적(static) 변수, 싱글톤 | 유지됨 | 초기화됨 |
| 발생 시점 | 앱이 포그라운드에 있는 동안에도 발생 가능 | 앱이 백그라운드로 전환된 이후에만 발생 |

프로세스 종료는 사용자가 다른 앱을 사용하는 동안 시스템이 메모리 확보를 위해 백그라운드 앱의 프로세스를 완전히 종료시키는 상황이다. 이후 사용자가 다시 앱으로 돌아오면(뒤로가기로 복귀 등), 시스템은 마치 아무 일도 없었던 것처럼 Activity 스택을 복원하려고 시도하지만, 실제로는 프로세스가 새로 시작되고 `onCreate()`에 `onSaveInstanceState()`에서 저장했던 `Bundle`이 전달된다.

즉 `Bundle` 기반의 상태 저장(`onSaveInstanceState`, `rememberSaveable`)은 두 상황 모두에 대응하지만, `ViewModel`에만 의존한 상태는 프로세스 종료 시에는 손실된다. 이 문제를 보완하기 위한 도구가 다음 장에서 다룰 `SavedStateHandle`이다.

<br>

## 12. SavedStateHandle을 이용한 프로세스 종료 대응

`SavedStateHandle`은 `ViewModel`에 주입되는 키-값 저장소로, Configuration Change뿐 아니라 프로세스 종료 후 재시작 상황까지 아우르는 상태 보존을 제공한다. 내부적으로 `onSaveInstanceState()`의 `Bundle`과 유사한 메커니즘을 사용하지만, `ViewModel`과 통합되어 훨씬 사용하기 편하다.

```kotlin
class RecordingViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val voiceNoteRepository: VoiceNoteRepository
) : ViewModel() {

    val elapsedSeconds: StateFlow<Int> = savedStateHandle.getStateFlow(KEY_ELAPSED, 0)

    fun updateElapsed(seconds: Int) {
        savedStateHandle[KEY_ELAPSED] = seconds
    }

    companion object {
        private const val KEY_ELAPSED = "key_elapsed"
    }
}
```

Hilt를 사용하는 경우, `@HiltViewModel`과 함께 `SavedStateHandle`을 생성자에 그대로 주입받을 수 있다.

```kotlin
@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val voiceNoteRepository: VoiceNoteRepository
) : ViewModel() {

    private val voiceNoteId: Long = checkNotNull(savedStateHandle["voiceNoteId"])

    init {
        loadVoiceNote(voiceNoteId)
    }
}
```

`SavedStateHandle`을 사용할 데이터와 사용하지 않을 데이터를 구분하는 기준은 다음과 같다.

- 사용해야 하는 경우: 사용자가 입력 중인 값, 현재 진행 상태처럼 "프로세스가 죽었다가 복원되어도 반드시 유지되어야 하는" 작은 데이터
- 사용하지 않아도 되는 경우: 네트워크로 다시 가져올 수 있는 데이터(캐시성 데이터), 크기가 큰 리스트나 이미지 데이터(Bundle 크기 제약)

`SavedStateHandle`도 `Bundle`을 기반으로 하기 때문에 저장 가능한 데이터 크기와 타입에는 여전히 제약이 있다. 대용량 데이터는 Repository/DB 레이어에서 다시 조회하는 방식으로 설계하고, `SavedStateHandle`에는 "그 데이터를 다시 조회하기 위한 최소한의 키 값"만 저장하는 것이 일반적인 패턴이다.

```kotlin
class VoiceNoteDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: VoiceNoteRepository
) : ViewModel() {

    private val voiceNoteId: Long = checkNotNull(savedStateHandle["voiceNoteId"])

    val voiceNote: StateFlow<VoiceNote?> = repository.observeById(voiceNoteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
```

<br>

## 13. Coroutine/Flow 작업과 Configuration Change

`viewModelScope`에서 실행되는 Coroutine은 `ViewModel`의 생명주기를 따르기 때문에, Configuration Change 시에도 취소되지 않고 계속 실행된다. 이는 회전 시마다 네트워크 요청이 중복 실행되는 흔한 버그를 방지해준다.

```kotlin
class UploadViewModel : ViewModel() {

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    fun upload(file: File) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading(0)
            // 업로드 진행 중 회전이 발생해도 이 Coroutine은 계속 실행된다
            val result = uploadUseCase(file) { progress ->
                _uploadState.value = UploadState.Uploading(progress)
            }
            _uploadState.value = UploadState.Success(result)
        }
    }
}
```

반대로, Activity/Fragment의 `lifecycleScope`에서 직접 실행한 Coroutine은 Configuration Change 시 Activity가 소멸되면서 함께 취소된다.

```kotlin
class RecordingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            // 회전하면 이 Coroutine은 취소되고, 재생성된 Activity에서 다시 실행되지 않는다
            val result = fetchSomething()
        }
    }
}
```

따라서 회전에도 살아남아야 하는 비동기 작업(네트워크 요청, DB 쓰기, 파일 업로드 등)은 반드시 `ViewModel`의 `viewModelScope`에서 실행해야 한다. `lifecycleScope`는 화면에 종속적인, 회전과 무관하게 반복 실행되어도 무방한 짧은 작업에만 사용하는 것이 안전하다.

Compose에서 `LaunchedEffect`를 사용할 때도 유사한 함정이 있다. `LaunchedEffect`의 key가 재구성마다 달라지는 값이라면, 회전으로 인한 재구성 시 의도치 않게 작업이 재시작될 수 있다.

```kotlin
@Composable
fun VoiceNoteScreen(viewModel: VoiceNoteViewModel = hiltViewModel()) {
    // key를 Unit으로 고정하면, 이 Composable이 재구성되어도 한 번만 실행됨
    LaunchedEffect(Unit) {
        viewModel.loadInitialData()
    }
}
```

`ViewModel`이 Configuration Change에도 유지되기 때문에, 위 코드에서 `Unit`을 key로 사용해도 `viewModel.loadInitialData()`가 회전마다 중복 호출되지는 않도록 `ViewModel` 내부에서 이미 로드된 상태인지 체크하는 방어 로직을 함께 두는 것이 안전하다.

<br>

## 14. Fragment에서의 Configuration Change 처리

Fragment는 Activity 내부에 존재하는 컴포넌트이므로, Activity가 Configuration Change로 재생성될 때 Fragment도 함께 재생성된다. 다만 Fragment의 `View`와 Fragment 인스턴스 자체의 생명주기가 분리되어 있다는 점에서 미묘한 차이가 있다.

```kotlin
class RecordingFragment : Fragment() {

    private val viewModel: RecordingViewModel by viewModels()
    // Fragment 스코프의 ViewModel: 이 Fragment가 완전히 소멸되기 전까지 유지

    private val activityViewModel: SharedViewModel by activityViewModels()
    // Activity 스코프의 ViewModel: 같은 Activity 내 여러 Fragment가 공유
}
```

Fragment 간에 데이터를 공유해야 하거나, Configuration Change 시에도 여러 Fragment가 동일한 상태를 참조해야 한다면 `activityViewModels()`를 사용하는 것이 일반적이다.

```kotlin
class SharedViewModel : ViewModel() {
    private val _selectedVoiceNoteId = MutableStateFlow<Long?>(null)
    val selectedVoiceNoteId: StateFlow<Long?> = _selectedVoiceNoteId.asStateFlow()

    fun selectVoiceNote(id: Long) {
        _selectedVoiceNoteId.value = id
    }
}

class ListFragment : Fragment() {
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private fun onItemClick(id: Long) {
        sharedViewModel.selectVoiceNote(id)
    }
}

class DetailFragment : Fragment() {
    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.selectedVoiceNoteId.collect { id ->
                // 선택된 항목이 바뀔 때마다 상세 화면 갱신
            }
        }
    }
}
```

Fragment의 `View`는 `onDestroyView()`에서 소멸되고 `onCreateView()`에서 다시 생성되는데, 이 생명주기는 `Fragment` 객체 자체의 생명주기보다 짧다. 이 때문에 `View`와 관련된 Flow 구독은 `viewLifecycleOwner`를 기준으로 시작해야 하며, `Fragment` 자체의 `lifecycleScope`를 사용하면 `View`가 없는 상태에서도 구독이 유지되어 메모리 누수나 크래시로 이어질 수 있다.

<br>

## 15. 멀티 윈도우와 폴더블 기기에서의 Configuration Change

멀티 윈도우(분할 화면)나 폴더블 기기의 접힘/펼침도 화면 크기와 비율이 바뀌는 Configuration Change를 유발한다. 이런 환경은 일반적인 회전보다 훨씬 다양한 화면 크기 조합을 만들어내기 때문에, 고정된 세로/가로 레이아웃 두 가지만으로는 대응이 부족할 수 있다.

```kotlin
@Composable
fun AdaptiveVoiceNoteScreen() {
    val windowSizeClass = calculateWindowSizeClass(LocalContext.current as Activity)

    when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> CompactLayout()
        WindowWidthSizeClass.Medium -> MediumLayout()
        WindowWidthSizeClass.Expanded -> ExpandedLayout()
    }
}
```

`WindowSizeClass`는 단순히 세로/가로를 구분하는 것이 아니라, 사용 가능한 화면 너비/높이 구간(Compact, Medium, Expanded)을 기준으로 레이아웃을 적응시키는 방식이다. 멀티 윈도우 상태에서 사용자가 창 크기를 드래그해서 조절하면, 이 값이 실시간으로 바뀌면서 Configuration Change가 연속적으로 발생할 수 있다.

멀티 윈도우 환경에서 특히 주의할 점은 다음과 같다.

- 두 개 이상의 앱이 동시에 화면에 보이는 상태이므로, 어떤 앱도 완전한 포그라운드 우선순위를 독점하지 못할 수 있다.
- 창 크기가 매우 작아질 수 있으므로, 최소 지원 크기에 대한 레이아웃도 함께 고려해야 한다.
- 폴더블 기기에서는 접힘/펼침 시 Activity가 재시작되지 않고 크기만 부드럽게 전환되는 경우도 있어(제조사/기기별 상이), 레이아웃이 애니메이션처럼 매끄럽게 반응하도록 설계하는 것이 UX상 중요하다.

이런 환경에서는 특정 방향(세로/가로)을 고정하는 `android:screenOrientation` 설정을 남용하지 않는 것이 좋다. 폴더블/태블릿/멀티 윈도우 사용자 비중이 늘어나는 추세를 고려하면, 처음부터 유연한 반응형 레이아웃으로 설계하는 편이 장기적으로 유지보수 비용을 줄여준다.

<br>

## 16. 실전에서 겪는 Configuration Change 이슈

녹음/재생 앱
- 회전 시 진행 중인 녹음/재생이 끊기지 않아야 함 → 녹음/재생 로직 자체는 Foreground Service나 `ViewModel`의 `viewModelScope`에 두고, Activity는 상태를 구독만 하도록 설계
- 녹음 경과 시간 같은 UI 상태는 `ViewModel`의 `StateFlow`로 관리해 회전 후에도 정확히 표시

폼 입력 화면(회원가입, 설정 등)
- 여러 입력 필드가 있는 화면에서 회전 시 입력값이 사라지는 문제가 흔함
- `rememberSaveable` 또는 `ViewModel` + `SavedStateHandle` 조합으로 각 필드 값을 보존

이미지/카메라 관련 화면
- 카메라 프리뷰는 재생성 비용이 크고 매끄러운 회전 전환이 어려운 대표적 사례
- 프리뷰 자체는 유지하되, 주변 UI만 다시 그리는 절충안으로 제한적인 `configChanges` 사용을 고려하기도 함

리스트 화면(스크롤 위치)
- `RecyclerView`나 `LazyColumn`의 스크롤 위치는 대부분 시스템이 `id` 기반으로 자동 보존
- 다만 데이터 자체를 다시 네트워크에서 불러오는 로직이 `onCreate()`에 있다면, 회전마다 중복 요청이 발생하므로 `ViewModel`로 이동 필요

다이얼로그/바텀시트 표시 상태
- 다이얼로그가 떠 있는 상태에서 회전하면 다이얼로그가 사라지는 경우가 흔함
- `rememberSaveable`로 "다이얼로그가 열려 있었는지" 여부를 저장해두면 회전 후에도 동일한 UI 상태 복원 가능

<br>

## 17. 주의사항과 자주 하는 실수

1. Activity 멤버 변수에 중요한 상태를 저장
   회전 시 초기화되는 것을 모르고 Activity의 일반 프로퍼티에 진행 상태를 저장하면, 회전할 때마다 데이터가 사라지는 버그가 생긴다.

2. `onCreate()`에서 매번 네트워크 요청 실행
   회전마다 Activity가 재생성되므로, `onCreate()`에 직접 네트워크 요청 코드를 두면 회전할 때마다 중복 요청이 발생한다. `ViewModel`에서 이미 로드된 상태인지 확인하는 로직이 필요하다.

3. `android:configChanges`를 습관적으로 사용
   재생성이 번거롭다는 이유로 `configChanges`를 남용하면, 언어 변경이나 다크 모드 전환처럼 리소스가 반드시 다시 적용되어야 하는 경우까지 놓치는 버그로 이어질 수 있다.

4. `lifecycleScope`에서 회전에도 유지되어야 할 작업 실행
   Activity가 소멸되면 `lifecycleScope`의 Coroutine도 함께 취소된다. 오래 걸리거나 중요한 작업은 `viewModelScope`를 사용해야 한다.

5. `SavedStateHandle`에 대용량 데이터 저장 시도
   `Bundle` 기반이므로 크기 제약이 있다. 리스트 전체나 이미지 데이터를 저장하려 하면 `TransactionTooLargeException`이 발생할 수 있다.

6. Fragment의 `lifecycleScope`와 `viewLifecycleOwner.lifecycleScope`를 혼동
   View와 관련된 UI 갱신 작업은 `viewLifecycleOwner.lifecycleScope`에서 수행해야, `onDestroyView()` 이후에도 구독이 남아있는 문제를 방지할 수 있다.

7. 회전 테스트를 소홀히 함
   에뮬레이터나 실기기에서 회전 테스트를 하지 않으면, 상태 손실이나 중복 요청 버그를 배포 이후에야 발견하게 되는 경우가 많다.

8. Configuration Change와 프로세스 종료를 동일하게 취급
   `ViewModel`만으로 모든 상태가 보존된다고 가정하면, 실제 사용자가 오래 다른 앱을 쓰다가 돌아왔을 때(프로세스 종료 후 재시작) 상태가 사라지는 문제를 놓치게 된다. 중요한 상태는 `SavedStateHandle`까지 함께 고려해야 한다.

9. `rememberSaveable`에 저장 불가능한 타입을 그대로 사용
   `Parcelable`이나 기본 타입이 아닌 복잡한 객체를 `rememberSaveable`에 그대로 넣으면 런타임 예외가 발생할 수 있다. `Saver`를 직접 정의하거나 `@Parcelize`를 사용해야 한다.

10. 멀티 윈도우/폴더블 환경 테스트 누락
    일반적인 회전 테스트만으로는 멀티 윈도우의 다양한 창 크기 조합이나 폴더블의 접힘/펼침 전환을 검증할 수 없다. 에뮬레이터의 폴더블 프로필이나 실기기를 활용한 별도 테스트가 필요하다.

<br>

## 18. 정리

Configuration Change는 화면 회전을 비롯해 언어, 다크 모드, 화면 크기 변경 등 다양한 환경 설정 변화에 대해 Android가 적절한 리소스를 다시 적용하기 위해 Activity를 재생성시키는 메커니즘이다. 이 재생성 과정에서 상태를 잃지 않으려면, 생명주기를 넘어 유지되어야 하는 데이터는 `ViewModel`에, 프로세스 종료까지 대비해야 하는 작은 데이터는 `SavedStateHandle`에, 순수 UI 레벨의 임시 상태는 `rememberSaveable`(또는 `onSaveInstanceState`)에 위치시키는 것이 핵심 원칙이다.

`android:configChanges`로 재생성 자체를 막는 방법도 존재하지만, 리소스 재적용이 자동으로 이루어지지 않는다는 근본적인 단점 때문에 예외적인 경우로 제한하는 것이 바람직하다. 또한 Configuration Change와 프로세스 종료는 겉보기에 비슷해 보이지만 원인과 대응 방법이 다르므로, `ViewModel`만으로 모든 상황이 해결된다고 오해하지 않는 것이 중요하다.

결국 Configuration Change에 강건한 앱을 만든다는 것은, 어떤 데이터를 어떤 생명주기의 컴포넌트에 위치시킬지를 처음부터 명확히 설계하는 것과 같다. `ViewModel`과 `viewModelScope`로 비즈니스 로직과 비동기 작업을 회전으로부터 분리하고, `SavedStateHandle`과 `rememberSaveable`로 자잘한 UI 상태까지 촘촘히 보존한다면, 회전이나 멀티 윈도우 전환 같은 환경 변화에도 사용자가 전혀 불편함을 느끼지 않는 앱을 만들 수 있다.
