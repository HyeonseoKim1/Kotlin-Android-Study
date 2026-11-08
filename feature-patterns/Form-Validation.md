# 폼 입력 + 유효성 검사 (실시간 에러 표시, 키보드/포커스 이동, 제출 버튼 활성화)

<br>

# 목차

1. 개요
2. 에러는 언제 보여줄까 (표시 정책)
3. 상태 설계 - 에러는 저장하지 않고 파생한다
4. Validator
5. ViewModel (MVI)
6. 입력 필드 컴포넌트
7. 키보드와 포커스 이동
8. 비밀번호 필드 (표시 토글, 규칙 체크리스트, 확인 입력)
9. 제출 버튼과 제출 처리
10. 화면 조립
11. 비동기 검증 (이메일 중복 확인)
12. 입력 포맷팅 (전화번호)
13. 상태 복원
14. 테스트
15. 자주 하는 실수
16. 정리

<br>

# 1. 개요

회원가입, 로그인, 프로필 수정, 주소 입력처럼 입력 폼이 있는 화면은 거의 모든 앱에 있다. 이 노트는 회원가입 폼(이메일, 비밀번호, 비밀번호 확인, 닉네임)을 예제로 다음을 다룬다.

- 실시간 에러 표시 (언제, 어떻게 보여줄 것인가)
- 키보드 액션(Next/Done)과 포커스 이동
- 제출 버튼 활성화 조건
- 제출 시 첫 번째 에러 필드로 포커스 이동
- 비동기 검증 (이메일 중복 확인), 서버 에러를 필드 에러로 매핑
- 입력 포맷팅 (전화번호)

한 줄 요약: 값(value)과 touched만 저장하고, 에러와 버튼 활성화는 전부 값에서 계산한다.

<br>

# 2. 에러는 언제 보여줄까 (표시 정책)

입력을 시작하자마자 "이메일 형식이 올바르지 않습니다"가 뜨면 사용자는 아직 입력 중인데 혼나는 느낌을 받는다. 반대로 제출 때만 보여주면 피드백이 너무 늦다.

| 시점 | 동작 |
|---|---|
| 처음 입력하는 동안 | 에러를 숨긴다 |
| 포커스가 빠질 때 (blur) | 검증하고 에러를 보여준다 (touched 처리) |
| touched 이후 입력 | 입력할 때마다 실시간으로 다시 검증 (에러가 바로 사라지거나 바뀐다) |
| 제출 버튼 / 키보드 Done | 모든 필드를 touched 처리하고 첫 에러 필드로 포커스 이동 |

그리고 반드시 분리해야 하는 두 가지가 있다.

| 구분 | 기준 |
|---|---|
| 에러 표시 | `touched`일 때만 |
| 제출 가능 여부(`isValid`) | touched와 상관없이 값만 보고 계산 |

버튼 활성화는 "아직 안 건드린 필드도 비어 있으니 비활성"이어야 하므로, 에러 표시 여부와 무관하게 값으로 판단한다.

<br>

# 3. 상태 설계 - 에러는 저장하지 않고 파생한다

초보 때 흔히 `emailError: String?`, `passwordError: String?` 같은 필드를 상태에 저장하고 입력 이벤트마다 갱신한다. 그러면 비밀번호를 바꿨는데 비밀번호 확인의 에러가 갱신되지 않는 식의 불일치 버그가 생긴다.

값과 touched만 저장하고, 에러는 매번 계산하면 이런 문제가 없다.

```kotlin
enum class SignUpField { Email, Password, PasswordConfirm, Nickname }

enum class Availability { Unknown, Checking, Available, Duplicated }

enum class FormError(@StringRes val messageRes: Int) {
    EmailEmpty(R.string.error_email_empty),
    EmailInvalid(R.string.error_email_invalid),
    EmailDuplicated(R.string.error_email_duplicated),
    PasswordEmpty(R.string.error_password_empty),
    PasswordInvalid(R.string.error_password_invalid),
    PasswordConfirmEmpty(R.string.error_password_confirm_empty),
    PasswordMismatch(R.string.error_password_mismatch),
    NicknameEmpty(R.string.error_nickname_empty),
    NicknameInvalid(R.string.error_nickname_invalid),
}

data class SignUpState(
    val email: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val nickname: String = "",
    val touched: Set<SignUpField> = emptySet(),
    val emailAvailability: Availability = Availability.Unknown,
    val isSubmitting: Boolean = false,
) {
    // 값에서 계산한 에러 (touched와 무관)
    fun validate(field: SignUpField): FormError? = when (field) {
        SignUpField.Email ->
            Validators.email(email)
                ?: if (emailAvailability == Availability.Duplicated) FormError.EmailDuplicated else null

        SignUpField.Password -> Validators.password(password)
        SignUpField.PasswordConfirm -> Validators.passwordConfirm(password, passwordConfirm)
        SignUpField.Nickname -> Validators.nickname(nickname)
    }

    // 화면에 보여줄 에러 (touched일 때만)
    fun errorOf(field: SignUpField): FormError? =
        if (field in touched) validate(field) else null

    // 제출 가능 여부
    val isValid: Boolean
        get() = SignUpField.entries.all { validate(it) == null } &&
            emailAvailability != Availability.Checking
}
```

`strings.xml`

```xml
<string name="error_email_empty">이메일을 입력해주세요</string>
<string name="error_email_invalid">이메일 형식이 올바르지 않아요</string>
<string name="error_email_duplicated">이미 가입된 이메일이에요</string>
<string name="error_password_empty">비밀번호를 입력해주세요</string>
<string name="error_password_invalid">비밀번호 규칙을 확인해주세요</string>
<string name="error_password_confirm_empty">비밀번호를 한 번 더 입력해주세요</string>
<string name="error_password_mismatch">비밀번호가 일치하지 않아요</string>
<string name="error_nickname_empty">닉네임을 입력해주세요</string>
<string name="error_nickname_invalid">2~10자의 한글, 영문, 숫자만 사용할 수 있어요</string>

<string name="rule_length">8~20자</string>
<string name="rule_letter">영문 포함</string>
<string name="rule_digit">숫자 포함</string>
```

장점

- 에러를 따로 갱신하는 코드가 없어서 필드 간 의존(비밀번호 ↔ 비밀번호 확인)이 자동으로 맞는다
- `isValid`, `errorOf`가 순수 함수라서 ViewModel 없이 상태 객체만으로 테스트할 수 있다
- 에러 메시지는 `@StringRes`로 들고 있다가 UI에서 `stringResource`로 변환한다 (다국어, 문구 수정이 쉽다)

<br>

# 4. Validator

검증 로직은 Android 의존성이 없는 순수 함수로 둔다.

```kotlin
data class PasswordRule(
    @StringRes val labelRes: Int,
    val isSatisfied: (String) -> Boolean,
)

object Validators {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val NICKNAME_REGEX = Regex("^[가-힣a-zA-Z0-9]{2,10}$")

    val passwordRules = listOf(
        PasswordRule(R.string.rule_length) { it.length in 8..20 },
        PasswordRule(R.string.rule_letter) { pw -> pw.any { it in 'a'..'z' || it in 'A'..'Z' } },
        PasswordRule(R.string.rule_digit) { pw -> pw.any { it.isDigit() } },
    )

    fun email(value: String): FormError? = when {
        value.isBlank() -> FormError.EmailEmpty
        !EMAIL_REGEX.matches(value.trim()) -> FormError.EmailInvalid
        else -> null
    }

    fun password(value: String): FormError? = when {
        value.isEmpty() -> FormError.PasswordEmpty
        passwordRules.any { !it.isSatisfied(value) } -> FormError.PasswordInvalid
        else -> null
    }

    fun passwordConfirm(password: String, confirm: String): FormError? = when {
        confirm.isEmpty() -> FormError.PasswordConfirmEmpty
        password != confirm -> FormError.PasswordMismatch
        else -> null
    }

    fun nickname(value: String): FormError? = when {
        value.isBlank() -> FormError.NicknameEmpty
        !NICKNAME_REGEX.matches(value) -> FormError.NicknameInvalid
        else -> null
    }
}
```

- 이메일 검증은 `android.util.Patterns.EMAIL_ADDRESS`를 쓸 수도 있지만 JVM 단위 테스트에서 안 돌아가므로 Regex로 직접 둔다 (정규식은 서버 검증보다 느슨하게 두고, 최종 판단은 서버에 맡긴다)
- 비밀번호는 `trim()`하지 않는다 (공백도 비밀번호의 일부일 수 있다). 이메일은 입력 단계에서 공백을 제거한다
- 비밀번호 규칙은 `passwordRules` 리스트로 따로 노출해서, 검증과 체크리스트 UI가 같은 규칙을 공유하게 한다

<br>

# 5. ViewModel (MVI)

```kotlin
sealed interface SignUpIntent {
    data class ValueChanged(val field: SignUpField, val value: String) : SignUpIntent
    data class FocusLost(val field: SignUpField) : SignUpIntent
    data object Submit : SignUpIntent
}

sealed interface SignUpEffect {
    data class FocusField(val field: SignUpField) : SignUpEffect
    data class ShowMessage(val message: String) : SignUpEffect
    data object NavigateToHome : SignUpEffect
}

class EmailDuplicatedException : Exception()

interface AuthRepository {
    suspend fun isEmailAvailable(email: String): Boolean
    suspend fun signUp(email: String, password: String, nickname: String): Result<Unit>
}
```

```kotlin
@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {

    // TextField에 바인딩되므로 Compose State로 둔다 (동기 반영, 한글 조합 안전)
    var state by mutableStateOf(SignUpState())
        private set

    private val _effect = Channel<SignUpEffect>(Channel.BUFFERED)
    val effect: Flow<SignUpEffect> = _effect.receiveAsFlow()

    private val emailInput = MutableStateFlow("")

    init {
        observeEmailAvailability()
    }

    fun onIntent(intent: SignUpIntent) {
        when (intent) {
            is SignUpIntent.ValueChanged -> onValueChanged(intent.field, intent.value)
            is SignUpIntent.FocusLost ->
                state = state.copy(touched = state.touched + intent.field)
            SignUpIntent.Submit -> submit()
        }
    }

    private fun onValueChanged(field: SignUpField, value: String) {
        state = when (field) {
            SignUpField.Email -> state.copy(
                email = value.filterNot { it.isWhitespace() },
                emailAvailability = Availability.Unknown,   // 값이 바뀌면 이전 중복 확인 결과는 무효
            )
            SignUpField.Password -> state.copy(password = value)
            SignUpField.PasswordConfirm -> state.copy(passwordConfirm = value)
            SignUpField.Nickname -> state.copy(nickname = value.take(NICKNAME_MAX_LENGTH))
        }
        if (field == SignUpField.Email) emailInput.value = state.email
    }

    private fun submit() {
        if (state.isSubmitting) return          // 중복 제출 방지

        // 모든 필드를 touched 처리해서 에러를 전부 노출
        state = state.copy(touched = SignUpField.entries.toSet())

        // 첫 번째 에러 필드로 포커스 이동
        SignUpField.entries.firstOrNull { state.validate(it) != null }?.let { invalid ->
            viewModelScope.launch { _effect.send(SignUpEffect.FocusField(invalid)) }
            return
        }
        if (state.emailAvailability == Availability.Checking) return

        state = state.copy(isSubmitting = true)
        viewModelScope.launch {
            repository.signUp(state.email, state.password, state.nickname)
                .onSuccess { _effect.send(SignUpEffect.NavigateToHome) }
                .onFailure { handleFailure(it) }
        }
    }

    private suspend fun handleFailure(e: Throwable) {
        state = state.copy(isSubmitting = false)
        when (e) {
            is EmailDuplicatedException -> {
                // 서버 에러를 필드 에러로 매핑
                state = state.copy(
                    emailAvailability = Availability.Duplicated,
                    touched = state.touched + SignUpField.Email,
                )
                _effect.send(SignUpEffect.FocusField(SignUpField.Email))
            }
            else -> _effect.send(SignUpEffect.ShowMessage("가입에 실패했어요. 잠시 후 다시 시도해주세요"))
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeEmailAvailability() {
        emailInput
            .debounce(500)
            .distinctUntilChanged()
            .flatMapLatest { email ->
                if (Validators.email(email) != null) {
                    flowOf(email to Availability.Unknown)
                } else {
                    flow {
                        emit(email to Availability.Checking)
                        val available = repository.isEmailAvailable(email)
                        emit(email to if (available) Availability.Available else Availability.Duplicated)
                    }.catch { emit(email to Availability.Unknown) }
                }
            }
            .onEach { (email, availability) ->
                // 응답이 늦게 와도 현재 입력값과 같은 경우에만 반영
                if (email == state.email) {
                    state = state.copy(emailAvailability = availability)
                }
            }
            .launchIn(viewModelScope)
    }

    private companion object {
        const val NICKNAME_MAX_LENGTH = 10
    }
}
```

포인트

- `state`는 `mutableStateOf`로 들고 있는다. `StateFlow` + `collectAsState`로 TextField를 바인딩하면 한글 입력에서 조합이 깨질 수 있다 (검색 화면 노트와 같은 이유)
- 입력 필터링(이메일 공백 제거, 닉네임 길이 제한)은 `onValueChanged`에서 한다. 동기 State라서 필터링해도 커서가 튀지 않는다
- 제출은 같은 함수로 "에러 노출 + 첫 에러 필드 포커스 + 실제 제출"을 처리한다. 키보드 Done 액션과 버튼 클릭이 같은 경로를 쓴다
- 서버가 "이미 가입된 이메일"을 돌려주면 필드 에러로 바꿔서 해당 필드에 보여준다 (스낵바로 던지면 어느 필드 문제인지 알 수 없다)

<br>

# 6. 입력 필드 컴포넌트

폼 필드마다 같은 설정이 반복되므로 공통 컴포넌트로 뺀다.

```kotlin
@Composable
fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onBlur: () -> Unit,
    error: FormError?,
    modifier: Modifier = Modifier,
    helperText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    // 포커스를 한 번이라도 받은 적이 있어야 blur로 인정 (초기 구성 시 isFocused=false 이벤트 무시)
    var hadFocus by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    hadFocus = true
                } else if (hadFocus) {
                    hadFocus = false
                    onBlur()
                }
            },
        label = { Text(label) },
        isError = error != null,
        supportingText = {
            // 항상 한 줄을 차지하게 해서 에러가 나타날 때 레이아웃이 밀리지 않도록 한다
            Text(error?.let { stringResource(it.messageRes) } ?: helperText ?: " ")
        },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
    )
}
```

포인트

- 에러가 있을 때는 `isError = true`로 테두리와 라벨 색이 바뀌고, 메시지는 `supportingText`에 표시된다 (Material3 기본 동작)
- `onFocusChanged`는 컴포지션 직후 `isFocused = false`로 한 번 호출된다. 이걸 blur로 처리하면 화면에 들어오자마자 모든 필드가 touched가 되어 에러가 전부 뜬다. `hadFocus`로 "포커스를 받은 뒤 잃은 경우"만 blur로 인정한다
- `supportingText`가 있다가 없다가 하면 에러가 나타날 때마다 아래 필드들이 밀려 내려간다. `" "`로 한 줄을 항상 확보한다
- 에러가 있으면 에러 메시지가 우선, 없으면 보조 문구(`helperText`, 예: "사용 가능한 이메일이에요")를 보여준다

<br>

# 7. 키보드와 포커스 이동

## IME 액션

| 필드 | keyboardType | imeAction |
|---|---|---|
| 이메일 | `Email` | `Next` |
| 비밀번호 | `Password` | `Next` |
| 비밀번호 확인 | `Password` | `Next` |
| 닉네임 (마지막) | `Text` | `Done` |

- 마지막 필드만 `Done`으로 두고 제출을 연결한다
- `Next`는 별도 `keyboardActions` 없이도 다음 포커스로 이동한다 (기본 동작)
- 키보드 타입을 맞추면 이메일은 `@`, 숫자 입력은 숫자 키패드가 바로 나와서 입력 편의가 크게 좋아진다

## 이동 순서를 직접 제어

기본은 화면의 시각적 순서(위→아래)를 따른다. 순서를 바꾸거나 특정 필드로 보내려면 `FocusRequester`를 쓴다.

```kotlin
val requesters = remember {
    SignUpField.entries.associateWith { FocusRequester() }
}

FormTextField(
    // ...
    modifier = Modifier.focusRequester(requesters.getValue(SignUpField.Email)),
)

// 이동
requesters.getValue(SignUpField.Password).requestFocus()
```

`focusProperties`로 Next 이동 대상을 지정할 수도 있다.

```kotlin
Modifier.focusProperties { next = requesters.getValue(SignUpField.Nickname) }
```

## 키보드 내리기

```kotlin
val focusManager = LocalFocusManager.current

keyboardActions = KeyboardActions(
    onDone = {
        focusManager.clearFocus()    // 키보드 내리고 포커스 해제
        onIntent(SignUpIntent.Submit)
    },
)
```

- `clearFocus()`를 호출하면 blur가 발생해서 해당 필드가 touched 처리된다 (마지막 필드의 에러도 정상적으로 표시된다)
- 제출 결과 에러가 있으면 ViewModel이 `FocusField` Effect를 보내서 다시 해당 필드로 포커스를 옮긴다

## 키보드가 필드를 가릴 때

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .imePadding()                       // 키보드 높이만큼 아래 여백 확보
        .verticalScroll(rememberScrollState()),
)
```

- `imePadding()`은 반드시 `verticalScroll`보다 앞에 둔다. 그래야 키보드 높이만큼 스크롤 영역이 줄어들어 포커스된 필드가 보인다
- edge-to-edge를 쓰는 앱에서는 `Scaffold`의 `padding`과 함께 적용한다
- 포커스가 걸린 TextField는 보통 자동으로 화면에 스크롤되어 들어오지만, 안 되는 경우 `BringIntoViewRequester`를 추가한다

<br>

# 8. 비밀번호 필드 (표시 토글, 규칙 체크리스트, 확인 입력)

## 표시/숨김 토글

```kotlin
@Composable
fun PasswordTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onBlur: () -> Unit,
    error: FormError?,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var visible by rememberSaveable { mutableStateOf(false) }

    FormTextField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        onBlur = onBlur,
        error = error,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = keyboardActions,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "비밀번호 숨기기" else "비밀번호 보기",
                )
            }
        },
    )
}
```

- `Visibility`, `VisibilityOff` 아이콘은 `material-icons-extended` 의존성이 필요하다
- 표시 여부는 화면 회전에도 유지되도록 `rememberSaveable`로 둔다

## 규칙 체크리스트

에러 메시지 한 줄로는 "무엇이 부족한지" 알 수 없다. 규칙을 미리 보여주고 만족할 때마다 체크 표시를 바꿔주면 입력 중 피드백이 훨씬 친절하다. 이 영역은 touched와 상관없이 처음부터 보여준다.

```kotlin
@Composable
fun PasswordRuleList(
    password: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(start = 16.dp)) {
        Validators.passwordRules.forEach { rule ->
            val satisfied = rule.isSatisfied(password)
            val color = if (satisfied) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (satisfied) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(rule.labelRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            }
        }
    }
}
```

## 비밀번호 확인

- 확인 필드의 에러는 `Validators.passwordConfirm(password, confirm)`이 두 값을 같이 비교한다
- 에러를 파생해서 쓰고 있어서, 확인 필드를 입력한 뒤 비밀번호를 고치면 확인 필드의 에러가 자동으로 갱신된다 (별도 처리 불필요)

<br>

# 9. 제출 버튼과 제출 처리

## 버튼 활성화 정책

| 정책 | 장점 | 단점 |
|---|---|---|
| 유효할 때만 활성화 | 잘못된 제출 자체를 막는다 | 비활성 이유를 사용자가 모를 수 있다 |
| 항상 활성화 + 제출 시 에러 표시 | 접근성이 좋고 이유를 바로 알려준다 | 빈 폼 제출이 가능하다 |

이 노트의 구조는 두 방식 모두 지원한다. 버튼을 비활성화하더라도 키보드 Done으로는 `Submit`이 들어올 수 있으므로, `submit()`이 항상 검증하고 첫 에러 필드로 포커스를 옮기게 되어 있다.

비활성화 정책을 쓸 때는 반드시 "왜 비활성인지"가 화면에 보여야 한다 (규칙 체크리스트, 필드별 에러).

## 버튼

```kotlin
Button(
    onClick = { onIntent(SignUpIntent.Submit) },
    enabled = state.isValid && !state.isSubmitting,
    modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
) {
    if (state.isSubmitting) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = LocalContentColor.current,
        )
    } else {
        Text("가입하기")
    }
}
```

- 제출 중에는 `enabled = false`로 중복 탭을 막고, 버튼 안에 로딩 인디케이터를 보여준다
- ViewModel의 `submit()`에서도 `isSubmitting`을 확인해서 이중으로 막는다 (UI 방어만으로는 키보드 Done 연타를 못 막는다)

<br>

# 10. 화면 조립

```kotlin
@Composable
fun SignUpScreen(
    onSignedUp: () -> Unit,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val onIntent = viewModel::onIntent

    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val requesters = remember { SignUpField.entries.associateWith { FocusRequester() } }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SignUpEffect.FocusField -> requesters.getValue(effect.field).requestFocus()
                is SignUpEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                SignUpEffect.NavigateToHome -> onSignedUp()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("회원가입", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            FormTextField(
                label = "이메일",
                value = state.email,
                onValueChange = { onIntent(SignUpIntent.ValueChanged(SignUpField.Email, it)) },
                onBlur = { onIntent(SignUpIntent.FocusLost(SignUpField.Email)) },
                error = state.errorOf(SignUpField.Email),
                helperText = when (state.emailAvailability) {
                    Availability.Checking -> "확인 중이에요..."
                    Availability.Available -> "사용 가능한 이메일이에요"
                    else -> null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.focusRequester(requesters.getValue(SignUpField.Email)),
            )

            PasswordTextField(
                label = "비밀번호",
                value = state.password,
                onValueChange = { onIntent(SignUpIntent.ValueChanged(SignUpField.Password, it)) },
                onBlur = { onIntent(SignUpIntent.FocusLost(SignUpField.Password)) },
                error = state.errorOf(SignUpField.Password),
                imeAction = ImeAction.Next,
                modifier = Modifier.focusRequester(requesters.getValue(SignUpField.Password)),
            )
            PasswordRuleList(password = state.password)
            Spacer(Modifier.height(8.dp))

            PasswordTextField(
                label = "비밀번호 확인",
                value = state.passwordConfirm,
                onValueChange = { onIntent(SignUpIntent.ValueChanged(SignUpField.PasswordConfirm, it)) },
                onBlur = { onIntent(SignUpIntent.FocusLost(SignUpField.PasswordConfirm)) },
                error = state.errorOf(SignUpField.PasswordConfirm),
                imeAction = ImeAction.Next,
                modifier = Modifier.focusRequester(requesters.getValue(SignUpField.PasswordConfirm)),
            )

            FormTextField(
                label = "닉네임",
                value = state.nickname,
                onValueChange = { onIntent(SignUpIntent.ValueChanged(SignUpField.Nickname, it)) },
                onBlur = { onIntent(SignUpIntent.FocusLost(SignUpField.Nickname)) },
                error = state.errorOf(SignUpField.Nickname),
                helperText = "${state.nickname.length}/10",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onIntent(SignUpIntent.Submit)
                    },
                ),
                modifier = Modifier.focusRequester(requesters.getValue(SignUpField.Nickname)),
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    focusManager.clearFocus()
                    onIntent(SignUpIntent.Submit)
                },
                enabled = state.isValid && !state.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                } else {
                    Text("가입하기")
                }
            }
        }
    }
}
```

- 예제에서는 화면 문구를 문자열 리터럴로 썼지만, 실제 프로젝트에서는 `stringResource`로 뺀다
- 화면은 `state`와 `onIntent`만 알고, 검증/중복 확인/서버 호출은 모른다
- Effect 중 `FocusField`는 UI 동작(포커스)이라 Composable에서 처리하고, ViewModel은 "어느 필드로 가라"만 지정한다

<br>

# 11. 비동기 검증 (이메일 중복 확인)

서버에 물어봐야 하는 검증은 형식 검증과 구조가 다르다.

```
입력 변경 ──> availability = Unknown (즉시)
     |
     └──> emailInput(StateFlow) ──> debounce(500) ──> distinctUntilChanged
                                         |
                                         └──> flatMapLatest
                                               ├─ 형식 오류면 Unknown
                                               └─ 형식 통과면 Checking → API → Available / Duplicated
```

핵심 포인트

1. 입력이 바뀌면 이전 확인 결과는 즉시 무효로 만든다 (`Unknown`). 이 상태에서는 "사용 가능" 표시가 나가면 안 되고, 중복 에러도 지워야 한다
2. 형식이 틀린 입력은 서버에 묻지 않는다 (불필요한 요청 방지)
3. `debounce`로 타이핑 중 요청을 줄이고, `flatMapLatest`로 이전 요청을 취소한다
4. 취소되지 않은 지연 응답이 늦게 도착해서 현재 입력과 다른 이메일의 결과를 덮어쓰는 경우를 `if (email == state.email)` 비교로 막는다
5. 확인 중(`Checking`)에는 제출 버튼을 막고, 실패(네트워크 에러)는 `Unknown`으로 돌려서 사용자를 막지 않는다. 최종 중복 검사는 서버가 가입 요청에서 다시 하므로 `EmailDuplicatedException`으로 처리한다

| 상태 | 의미 | 화면 |
|---|---|---|
| Unknown | 아직 확인 안 함 / 무효화됨 | 표시 없음 |
| Checking | 확인 요청 중 | "확인 중이에요..." |
| Available | 사용 가능 | "사용 가능한 이메일이에요" |
| Duplicated | 이미 가입됨 | 에러 메시지 |

<br>

# 12. 입력 포맷팅 (전화번호)

사용자가 `01012345678`을 치면 화면에는 `010-1234-5678`로 보여주고 싶다. 값은 숫자만 저장하고 표시만 바꾸는 `VisualTransformation`을 쓴다.

```kotlin
class PhoneVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(11)

        val formatted = buildString {
            digits.forEachIndexed { index, char ->
                if (index == 3 || index == 7) append('-')
                append(char)
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val capped = offset.coerceAtMost(digits.length)
                return when {
                    capped <= 3 -> capped
                    capped <= 7 -> capped + 1
                    else -> capped + 2
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                val original = when {
                    offset <= 3 -> offset
                    offset <= 8 -> offset - 1
                    else -> offset - 2
                }
                return original.coerceIn(0, digits.length)
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}
```

사용

```kotlin
FormTextField(
    label = "휴대폰 번호",
    value = state.phone,                                   // 숫자만 저장
    onValueChange = {
        onIntent(SignUpIntent.ValueChanged(SignUpField.Phone, it.filter(Char::isDigit).take(11)))
    },
    onBlur = { onIntent(SignUpIntent.FocusLost(SignUpField.Phone)) },
    error = state.errorOf(SignUpField.Phone),
    visualTransformation = PhoneVisualTransformation(),
    keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Phone,
        imeAction = ImeAction.Next,
    ),
)
```

```kotlin
// Validators에 추가
private val PHONE_REGEX = Regex("^01[016789]\\d{7,8}$")

fun phone(value: String): FormError? = when {
    value.isBlank() -> FormError.PhoneEmpty
    !PHONE_REGEX.matches(value) -> FormError.PhoneInvalid
    else -> null
}
```

- 폼에 필드를 추가할 때는 `SignUpField`, `FormError`, `SignUpState`(값 필드, `validate`의 분기), `onValueChanged` 분기만 늘리면 된다. 나머지(touched, 에러 표시, isValid, 제출)는 그대로 동작한다
- `OffsetMapping`이 틀리면 커서가 엉뚱한 곳으로 튀거나 앱이 크래시(`OffsetMapping is not monotonic`)가 난다. 하이픈 위치를 기준으로 원본/변환 offset을 꼭 검증한다
- 같은 방식으로 금액 입력(천 단위 콤마), 카드 번호(4자리 공백), 생년월일(`YYYY.MM.DD`)도 만들 수 있다

<br>

# 13. 상태 복원

회전은 ViewModel이 유지해 주지만, 프로세스가 죽었다 살아나면(메모리 부족, 백그라운드 장시간) 입력값이 사라진다. 이메일, 닉네임처럼 민감하지 않은 값만 `SavedStateHandle`에 저장한다.

```kotlin
@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: AuthRepository,
) : ViewModel() {

    var state by mutableStateOf(
        SignUpState(
            email = savedStateHandle["email"] ?: "",
            nickname = savedStateHandle["nickname"] ?: "",
        )
    )
        private set

    // onValueChanged 안에서
    //   SignUpField.Email -> savedStateHandle["email"] = state.email
    //   SignUpField.Nickname -> savedStateHandle["nickname"] = state.nickname
}
```

- 비밀번호는 저장하지 않는다 (SavedStateHandle은 디스크에 직렬화될 수 있다)
- 복원된 값은 touched가 비어 있으므로 에러 없이 깨끗하게 보이고, 포커스를 잃을 때부터 검증된다
- 복원한 이메일은 `emailInput`에도 초기값으로 넣어야 중복 확인이 다시 돈다

<br>

# 14. 테스트

에러와 `isValid`가 값에서 파생되기 때문에 상태 객체만으로 대부분 테스트할 수 있다.

## Validator / State

```kotlin
class ValidatorsTest {

    @Test
    fun `이메일 형식을 검증한다`() {
        assertNull(Validators.email("test@test.com"))
        assertEquals(FormError.EmailEmpty, Validators.email("   "))
        assertEquals(FormError.EmailInvalid, Validators.email("test@"))
        assertEquals(FormError.EmailInvalid, Validators.email("test.com"))
    }

    @Test
    fun `비밀번호는 길이, 영문, 숫자를 모두 만족해야 한다`() {
        assertNull(Validators.password("abcd1234"))
        assertEquals(FormError.PasswordInvalid, Validators.password("abcd123"))      // 7자
        assertEquals(FormError.PasswordInvalid, Validators.password("abcdefgh"))     // 숫자 없음
        assertEquals(FormError.PasswordInvalid, Validators.password("12345678"))     // 영문 없음
    }

    @Test
    fun `비밀번호 확인은 비밀번호와 같아야 한다`() {
        assertNull(Validators.passwordConfirm("abcd1234", "abcd1234"))
        assertEquals(FormError.PasswordMismatch, Validators.passwordConfirm("abcd1234", "abcd12345"))
    }
}

class SignUpStateTest {

    private val validState = SignUpState(
        email = "test@test.com",
        password = "abcd1234",
        passwordConfirm = "abcd1234",
        nickname = "현서",
    )

    @Test
    fun `모든 필드가 유효하면 isValid가 true`() {
        assertTrue(validState.isValid)
    }

    @Test
    fun `touched 전에는 에러를 숨기고 touched 후에는 보여준다`() {
        val state = validState.copy(email = "abc")

        assertNull(state.errorOf(SignUpField.Email))
        assertEquals(
            FormError.EmailInvalid,
            state.copy(touched = setOf(SignUpField.Email)).errorOf(SignUpField.Email),
        )
    }

    @Test
    fun `중복 확인 중이면 제출할 수 없다`() {
        assertFalse(validState.copy(emailAvailability = Availability.Checking).isValid)
    }

    @Test
    fun `비밀번호를 바꾸면 확인 필드 에러가 자동으로 갱신된다`() {
        val state = validState.copy(
            password = "different1234",
            touched = setOf(SignUpField.PasswordConfirm),
        )
        assertEquals(FormError.PasswordMismatch, state.errorOf(SignUpField.PasswordConfirm))
    }
}
```

## ViewModel

```kotlin
class SignUpViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = mockk<AuthRepository>()

    private fun createViewModel(): SignUpViewModel {
        coEvery { repository.isEmailAvailable(any()) } returns true
        return SignUpViewModel(repository)
    }

    @Test
    fun `제출하면 모든 필드가 touched 되고 첫 에러 필드로 포커스 이동 효과를 보낸다`() = runTest {
        val viewModel = createViewModel()

        viewModel.onIntent(SignUpIntent.ValueChanged(SignUpField.Email, "test@test.com"))
        viewModel.onIntent(SignUpIntent.Submit)
        advanceUntilIdle()

        assertEquals(SignUpField.entries.toSet(), viewModel.state.touched)
        assertEquals(SignUpEffect.FocusField(SignUpField.Password), viewModel.effect.first())
    }

    @Test
    fun `서버가 이메일 중복을 응답하면 이메일 필드 에러로 매핑한다`() = runTest {
        coEvery { repository.signUp(any(), any(), any()) } returns Result.failure(EmailDuplicatedException())
        val viewModel = createViewModel()

        viewModel.onIntent(SignUpIntent.ValueChanged(SignUpField.Email, "test@test.com"))
        viewModel.onIntent(SignUpIntent.ValueChanged(SignUpField.Password, "abcd1234"))
        viewModel.onIntent(SignUpIntent.ValueChanged(SignUpField.PasswordConfirm, "abcd1234"))
        viewModel.onIntent(SignUpIntent.ValueChanged(SignUpField.Nickname, "현서"))
        advanceUntilIdle()
        viewModel.onIntent(SignUpIntent.Submit)
        advanceUntilIdle()

        assertEquals(FormError.EmailDuplicated, viewModel.state.errorOf(SignUpField.Email))
        assertFalse(viewModel.state.isSubmitting)
    }

    @Test
    fun `제출 중에는 중복 제출을 무시한다`() = runTest {
        coEvery { repository.signUp(any(), any(), any()) } coAnswers {
            delay(1000)
            Result.success(Unit)
        }
        val viewModel = createViewModel()
        // 유효한 값 입력 후 ...
        viewModel.onIntent(SignUpIntent.Submit)
        viewModel.onIntent(SignUpIntent.Submit)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.signUp(any(), any(), any()) }
    }
}
```

- `MainDispatcherRule`은 검색 화면 노트에서 만든 것과 같다 (`StandardTestDispatcher`를 Main으로 지정)
- 이메일 입력 후에는 debounce(500ms)가 지나야 중복 확인이 도는데, `advanceUntilIdle()`이 가상 시간을 앞당겨 준다
- UI 쪽은 Compose UI 테스트로 "blur 전에는 에러 없음, blur 후 에러 표시", "Done 누르면 제출"을 확인할 수 있다 (`performImeAction()`)

<br>

# 15. 자주 하는 실수

1. 에러 문자열을 상태에 저장하고 이벤트마다 갱신해서 필드 간 의존(비밀번호 ↔ 확인)이 어긋난다
2. `onFocusChanged`의 초기 `isFocused = false`를 blur로 처리해서 화면 진입 즉시 모든 에러가 뜬다 (`hadFocus` 필요)
3. 입력 시작과 동시에 에러를 보여줘서 사용자를 압박한다 (touched 이후부터)
4. 에러 표시 여부와 버튼 활성화를 같은 조건으로 묶어서, 아직 안 건드린 빈 필드가 있는데도 버튼이 활성화된다
5. `supportingText` 공간을 확보하지 않아 에러가 뜰 때마다 레이아웃이 밀린다
6. TextField 값을 `StateFlow` + `collectAsState`로 바인딩해서 한글 입력이 깨진다 (`mutableStateOf` 사용)
7. 중복 확인 응답이 늦게 도착해서 이미 바뀐 입력값에 결과를 덮어쓴다 (`email == state.email` 확인, `flatMapLatest`)
8. 입력이 바뀌었는데 이전 `Available` 결과가 남아서 다른 이메일이 "사용 가능"으로 표시된다
9. 제출 버튼만 막고 ViewModel에서는 `isSubmitting`을 확인하지 않아 키보드 Done 연타로 중복 가입 요청이 나간다
10. 서버 에러를 전부 스낵바로만 보여줘서 어느 필드 문제인지 알 수 없다 (필드 에러로 매핑)
11. `imePadding()`을 `verticalScroll` 뒤에 둬서 키보드가 올라와도 마지막 필드가 가려진다
12. 비밀번호를 `SavedStateHandle`/로그에 남긴다
13. 전화번호 `OffsetMapping`이 틀려서 커서가 튀거나 크래시가 난다
14. 비밀번호를 `trim()`해서 사용자가 입력한 값과 서버로 보낸 값이 달라진다

<br>

# 정리

1. 에러 표시는 처음 입력 중에는 숨기고, blur 이후(touched)부터 실시간으로 보여주며, 제출 시에는 모든 필드를 touched 처리한다
2. 상태에는 값과 touched만 저장하고 에러와 `isValid`는 값에서 파생한다. 필드 간 의존이 자동으로 맞고 테스트가 쉽다
3. 에러 표시(touched 기준)와 제출 가능 여부(값 기준)는 분리한다
4. Validator는 Android 의존성 없는 순수 함수로 두고, 에러는 `@StringRes`를 가진 enum으로 정의한다
5. 필드 컴포넌트는 `isError` + `supportingText`(항상 한 줄 확보)로 만들고, blur는 `hadFocus`로 초기 이벤트를 걸러서 감지한다
6. 키보드는 필드마다 `keyboardType`과 `imeAction`(Next, 마지막은 Done)을 맞추고, 순서 제어와 에러 필드 이동은 `FocusRequester`로 한다
7. 제출은 하나의 경로(`submit`)에서 검증, 첫 에러 필드 포커스, 중복 제출 방지, 서버 에러 → 필드 에러 매핑까지 처리한다
8. 이메일 중복 확인 같은 비동기 검증은 `debounce` + `flatMapLatest`에 입력 무효화와 "현재 값과 일치할 때만 반영" 규칙을 더한다
9. 입력 포맷팅은 값은 원본으로 저장하고 `VisualTransformation`으로 표시만 바꾼다. `OffsetMapping`을 꼭 검증한다
10. 한글 입력이 있는 TextField는 `mutableStateOf`(동기)로 바인딩한다
