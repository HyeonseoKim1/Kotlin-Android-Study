# Activity/Fragment 생명주기

## 1. Android 생명주기란?

Android 화면은 사용자의 동작이나 시스템 상태에 따라 생성되고, 표시되고, 백그라운드로 이동하거나 제거된다.

이러한 상태 변화를 **Lifecycle**이라고 한다.

생명주기를 이해하면 다음과 같은 문제를 예방할 수 있다.

- 화면이 사라졌는데 작업이 계속 실행되는 문제
- 화면 회전 시 데이터가 초기화되는 문제
- 메모리 누수
- 중복 API 호출
- 백그라운드에서 UI를 변경하려는 문제

Android에서는 주로 `Activity`와 `Fragment`의 생명주기를 이해해야 한다.

## 2. Activity 생명주기

Activity의 대표적인 생명주기 메서드는 다음과 같다.

```text
onCreate()
    ↓
onStart()
    ↓
onResume()
    ↓
[Running]
    ↓
onPause()
    ↓
onStop()
    ↓
onDestroy()
```

다시 화면으로 돌아오는 경우:

```text
onStop()
    ↓
onRestart()
    ↓
onStart()
    ↓
onResume()
```

## 3. onCreate()

Activity가 처음 생성될 때 호출된다.

주로 초기화 작업을 수행한다.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_main)
}
```

주요 작업:

- UI 초기화
- ViewModel 연결
- 필요한 데이터 초기화
- `savedInstanceState` 복원

`onCreate()`가 호출되었다고 해서 사용자에게 화면이 보이는 상태는 아니다.

## 4. onStart()

Activity가 사용자에게 표시되기 시작할 때 호출된다.

```kotlin
override fun onStart() {
    super.onStart()
}
```

화면이 다시 표시될 때도 호출된다.

```text
onStop()
 ↓
onRestart()
 ↓
onStart()
```

화면에 보이는 동안 필요한 작업을 시작하는 용도로 사용할 수 있다.

## 5. onResume()

Activity가 사용자와 상호작용할 수 있는 상태가 되면 호출된다.

```kotlin
override fun onResume() {
    super.onResume()
}
```

예를 들어 다음과 같은 작업을 수행할 수 있다.

- 카메라 사용 시작
- 센서 리스너 등록
- 일시 중지했던 UI 작업 재개

## 6. onPause()

Activity가 포커스를 잃었을 때 호출된다.

```kotlin
override fun onPause() {
    super.onPause()
}
```

너무 무거운 작업을 수행하면 안 된다.

주로 다음과 같은 작업에 사용한다.

- 애니메이션 일시정지
- 센서 사용 중단
- 일시적인 리소스 정리

## 7. onStop()

Activity가 사용자에게 더 이상 보이지 않을 때 호출된다.

```kotlin
override fun onStop() {
    super.onStop()
}
```

화면이 완전히 가려진 상태이므로 화면과 관련된 리소스를 정리하기 적절하다.

```text
Activity 화면
    ↓
다른 Activity 실행
    ↓
onPause()
    ↓
onStop()
```

## 8. onRestart()

`onStop()` 이후 Activity가 다시 표시될 때 호출된다.

```kotlin
override fun onRestart() {
    super.onRestart()
}
```

일반적인 흐름은 다음과 같다.

```text
onStop()
   ↓
onRestart()
   ↓
onStart()
   ↓
onResume()
```

처음 Activity가 생성될 때는 호출되지 않는다.

## 9. onDestroy()

Activity가 제거되기 직전에 호출된다.

```kotlin
override fun onDestroy() {
    super.onDestroy()
}
```

대표적으로 다음 상황에서 호출될 수 있다.

- Activity가 `finish()`된 경우
- 화면 회전으로 Activity가 재생성되는 경우
- 시스템이 Activity를 제거하는 경우

따라서 `onDestroy()`가 호출되었다고 해서 반드시 앱이 완전히 종료된 것은 아니다.

## 10. Configuration Change

대표적인 Configuration Change는 화면 회전이다.

화면 방향이 변경되면 기존 Activity가 제거되고 새로운 Activity가 생성될 수 있다.

```text
기존 Activity
    ↓
onPause()
    ↓
onStop()
    ↓
onDestroy()

새 Activity
    ↓
onCreate()
    ↓
onStart()
    ↓
onResume()
```

Activity에 데이터를 직접 저장하면 데이터가 사라질 수 있다.

### ViewModel 사용

```kotlin
class MainViewModel : ViewModel() {

    var count = 0
}
```

ViewModel은 Configuration Change에서 Activity보다 오래 유지되므로 UI 상태를 유지하는 데 활용할 수 있다.

## 11. Fragment 생명주기

Fragment는 Activity 안에 존재하기 때문에 Activity와 별도의 생명주기를 가진다.

대표적인 흐름:

```text
onAttach()
    ↓
onCreate()
    ↓
onCreateView()
    ↓
onViewCreated()
    ↓
onStart()
    ↓
onResume()
    ↓
[Running]
    ↓
onPause()
    ↓
onStop()
    ↓
onDestroyView()
    ↓
onDestroy()
    ↓
onDetach()
```

Fragment에서 중요한 점은 **Fragment 자체의 생명주기와 View의 생명주기가 다르다는 것**이다.

## 12. onAttach()

Fragment가 Activity에 연결될 때 호출된다.

```kotlin
override fun onAttach(context: Context) {
    super.onAttach(context)
}
```

Context나 Activity에 대한 참조가 필요한 초기 작업에 사용할 수 있다.

## 13. onCreate()

Fragment 자체가 생성될 때 호출된다.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
}
```

Fragment의 초기 데이터를 설정하는 작업 등에 사용할 수 있다.

아직 Fragment의 View는 생성되지 않았다.

## 14. onCreateView()

Fragment의 View를 생성한다.

```kotlin
override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
): View {
    return inflater.inflate(
        R.layout.fragment_home,
        container,
        false
    )
}
```

XML 기반 Fragment에서 레이아웃을 inflate하는 데 사용한다.

## 15. onViewCreated()

Fragment의 View가 생성된 직후 호출된다.

```kotlin
override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
) {
    super.onViewCreated(view, savedInstanceState)

    // UI 초기화
}
```

주로 다음 작업을 수행한다.

- View 초기화
- 클릭 이벤트 설정
- RecyclerView 설정
- UI에 데이터 연결

Fragment에서는 `onCreateView()`보다 **`onViewCreated()`에서 UI 초기화 작업을 수행하는 경우가 많다.**

## 16. onDestroyView()

Fragment의 View가 제거될 때 호출된다.

```kotlin
override fun onDestroyView() {
    super.onDestroyView()
}
```

Fragment 자체는 살아 있지만 View만 제거될 수 있기 때문에 중요하다.

```text
Fragment
 ├── Fragment 생명주기
 │
 └── View 생명주기
       ↓
   onDestroyView()
```

View에 대한 참조를 계속 가지고 있으면 메모리 누수가 발생할 수 있다.

## 17. Fragment View Binding

View Binding을 사용할 경우 `onDestroyView()`에서 참조를 제거하는 패턴이 사용된다.

```kotlin
private var _binding: FragmentHomeBinding? = null
private val binding
    get() = _binding!!

override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
): View {
    _binding = FragmentHomeBinding.inflate(
        inflater,
        container,
        false
    )

    return binding.root
}

override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
}
```

Fragment의 View가 제거되었는데 binding이 View를 계속 참조하는 상황을 방지할 수 있다.

## 18. LifecycleOwner

Android에서는 생명주기를 직접 메서드 호출만으로 관리하지 않고 `Lifecycle` 객체를 통해 관찰할 수도 있다.

`Activity`와 `Fragment`는 `LifecycleOwner`를 구현한다.

```kotlin
lifecycleScope.launch {
    lifecycle.repeatOnLifecycle(
        Lifecycle.State.STARTED
    ) {
        viewModel.uiState.collect {
            // UI 업데이트
        }
    }
}
```

`STARTED` 이상의 상태에서만 Flow를 수집하고, 화면이 `STOPPED` 상태가 되면 수집을 중단한다.

## 19. repeatOnLifecycle

Coroutine과 Lifecycle을 함께 사용할 때 자주 사용한다.

```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect { state ->
            // UI 처리
        }
    }
}
```

화면이 보이지 않는 동안 불필요하게 UI 관련 작업을 실행하는 것을 방지할 수 있다.

Fragment에서는 View의 Lifecycle을 사용하는 것도 중요하다.

```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(
        Lifecycle.State.STARTED
    ) {
        viewModel.uiState.collect {
            // UI 업데이트
        }
    }
}
```

Fragment 자체가 아니라 **Fragment의 View가 살아있는 동안** UI 작업을 수행할 수 있다.

## 20. Activity와 Fragment 생명주기 관계

Fragment는 Activity에 종속되어 있기 때문에 두 생명주기가 서로 영향을 준다.

```text
Activity
┌──────────────────────────────┐
│ onCreate                     │
│   ┌──────────────────────┐   │
│   │ Fragment             │   │
│   │ onCreate             │   │
│   │ onCreateView         │   │
│   │ onViewCreated        │   │
│   │ onStart              │   │
│   │ onResume             │   │
│   └──────────────────────┘   │
│ onStart                      │
│ onResume                     │
└──────────────────────────────┘
```

Activity가 종료되면 Fragment도 함께 영향을 받는다.

하지만 Fragment의 View는 Fragment 자체보다 먼저 제거될 수 있다는 차이가 있다.

## 21. 생명주기별 역할 정리

| 생명주기 | 주요 역할 |
|---|---|
| `onCreate()` | 초기 설정 |
| `onStart()` | 화면 표시 시작 |
| `onResume()` | 사용자 상호작용 시작 |
| `onPause()` | 일시적인 작업 중단 |
| `onStop()` | 화면 관련 작업 중단 |
| `onDestroy()` | 객체 제거 전 정리 |
| `onCreateView()` | Fragment View 생성 |
| `onViewCreated()` | Fragment UI 초기화 |
| `onDestroyView()` | Fragment View 정리 |

## 22. 실무에서 기억할 것

### Activity

```text
onCreate
→ 초기화

onStart
→ 화면 표시 시작

onResume
→ 사용자와 상호작용

onPause
→ 일시 중단

onStop
→ 화면에서 사라짐

onDestroy
→ Activity 제거
```

### Fragment

```text
Fragment 자체
onCreate
        ↓
View 생성
onCreateView
        ↓
UI 초기화
onViewCreated
        ↓
View 제거
onDestroyView
        ↓
Fragment 제거
onDestroy
```

가장 중요한 차이는 **Fragment는 객체의 생명주기와 View의 생명주기가 분리되어 있다는 점**이다.

특히 View Binding, Flow 수집, UI 이벤트 처리에서는 `viewLifecycleOwner`와 `onDestroyView()`를 함께 이해해야 한다.
