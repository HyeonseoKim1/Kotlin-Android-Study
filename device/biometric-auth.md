# Android Biometric 인증

1. 개요
2. BiometricPrompt 아키텍처
3. 지원 인증 방식과 Authenticator 종류
4. 기본 사용법
5. BiometricManager로 사전 체크하기
6. CryptoObject를 이용한 암호화 연동
7. Keystore와 생체 인증 결합
8. 인증 콜백과 에러 처리
9. Negative Button과 Device Credential Fallback
10. Jetpack Compose에서의 사용
11. 다양한 인증 강도(Authenticator) 조합 전략
12. 보안 고려사항
13. 테스트
14. 기기별/제조사별 대응
15. UX 가이드라인
16. 자주 겪는 이슈
17. 정리

# 개요

Android의 생체 인증(Biometric Authentication)은 `androidx.biometric` 라이브러리를 통해 표준화된 API로 제공된다. 지문, 얼굴 인식, 홍채 인식 등 기기별로 다른 하드웨어를 추상화해서, 앱 개발자는 `BiometricPrompt` 하나로 대부분의 기기를 대응할 수 있다.

기존에는 `FingerprintManager`(API 23+)를 직접 다뤄야 했지만, 이 API는 지문 전용이었고 기기별 UI가 제각각이었다. `androidx.biometric`은 이를 대체하며 시스템이 제공하는 표준 UI(다이얼로그)를 사용하도록 강제해 UX 일관성을 확보한다.

<br>

Biometric 인증을 쓰는 대표적인 이유는 다음과 같다.

- 비밀번호 입력 없이 빠르게 사용자 신원을 확인
- 민감한 화면(결제, 개인정보 열람) 진입 전 재인증
- Keystore에 저장된 키를 생체 인증과 결합해, 인증 성공 시에만 복호화/서명 가능하도록 하드웨어 수준에서 보장

<br>

```gradle
implementation "androidx.biometric:biometric:1.2.0-alpha05"
// Compose 전용 헬퍼가 필요 없다면 기본 biometric 모듈만으로 충분하다
implementation "androidx.biometric:biometric-ktx:1.2.0-alpha05"
```

`biometric-ktx`는 코루틴 기반 `suspend fun authenticate(...)` 확장 함수를 제공해 콜백 지옥 없이 인증 결과를 받을 수 있게 해준다.

# BiometricPrompt 아키텍처

생체 인증 흐름은 크게 세 개의 컴포넌트로 구성된다.

- `BiometricManager`: 현재 기기/사용자 상태에서 특정 강도의 생체 인증이 가능한지 사전 확인
- `BiometricPrompt`: 실제 시스템 다이얼로그를 띄우고 인증을 수행하는 클래스
- `BiometricPrompt.PromptInfo`: 다이얼로그에 표시할 제목, 설명, 버튼 텍스트, 허용할 인증 방식 등을 정의하는 설정 객체

<br>

인증 요청의 전체 흐름은 다음과 같다.

```
BiometricManager.canAuthenticate(강도)
  → 가능하면 BiometricPrompt 생성
    → PromptInfo 구성
      → authenticate() 호출 → 시스템 다이얼로그 표시
        → 사용자 인증 성공/실패/취소
          → AuthenticationCallback으로 결과 전달
```

내부적으로 `BiometricPrompt`는 Android 프레임워크의 `android.hardware.biometrics.BiometricPrompt`(API 28+)를 감싸거나, 그보다 낮은 버전에서는 `FingerprintManager` 기반의 호환 다이얼로그(`BiometricFragment`)로 폴백하는 방식으로 동작한다. 이 폴백 처리가 라이브러리를 쓰는 핵심 이유 중 하나다.

# 지원 인증 방식과 Authenticator 종류

`BiometricManager.Authenticators`는 요청할 인증 강도를 비트 플래그로 지정한다.

- `BIOMETRIC_STRONG`: Class 3(구 Strong) 생체 인증. Keystore 키 바인딩이 가능한 최고 신뢰 등급
- `BIOMETRIC_WEAK`: Class 2(구 Weak) 생체 인증. 화면 잠금 해제 정도에는 쓰이지만 키 바인딩에는 부적합할 수 있음
- `DEVICE_CREDENTIAL`: PIN, 패턴, 비밀번호 등 지식 기반 인증. 생체 인증이 실패하거나 등록되지 않았을 때 대체 수단으로 사용

<br>

여러 값을 비트 OR로 조합해서 "생체 인증 또는 기기 비밀번호 둘 다 허용" 같은 정책을 만들 수 있다.

```kotlin
val allowedAuthenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
    BiometricManager.Authenticators.DEVICE_CREDENTIAL
```

단, `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` 조합은 API 30 미만에서는 지원되지 않으므로 버전 분기가 필요할 수 있다.

# 기본 사용법

```kotlin
val executor = ContextCompat.getMainExecutor(context)

val biometricPrompt = BiometricPrompt(
    activity, // FragmentActivity
    executor,
    object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            // 인증 성공, 다음 화면 진행
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            // 사용자 취소, 너무 많은 실패, 하드웨어 사용 불가 등
        }

        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            // 인증 시도가 실패했지만 다이얼로그는 유지됨 (재시도 가능)
        }
    }
)

val promptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle("본인 확인")
    .setSubtitle("녹음 파일을 열람하려면 인증이 필요합니다")
    .setNegativeButtonText("취소")
    .build()

biometricPrompt.authenticate(promptInfo)
```

`onAuthenticationFailed`는 에러가 아니라 "지문이 안 맞았음" 같은 단순 실패이며, 다이얼로그는 자동으로 재시도 상태를 유지한다. 반면 `onAuthenticationError`는 다이얼로그가 닫히는 종료 상태이므로, 여기서 화면 전환이나 재시도 버튼 노출 같은 후처리를 한다.

# BiometricManager로 사전 체크하기

`authenticate()`를 바로 호출하기 전에 반드시 `BiometricManager.canAuthenticate()`로 현재 상태를 확인해야 한다. 하드웨어가 없거나, 있어도 생체 정보가 등록되어 있지 않으면 다이얼로그를 띄워도 실패만 반복된다.

```kotlin
val biometricManager = BiometricManager.from(context)

when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
    BiometricManager.BIOMETRIC_SUCCESS -> {
        // 인증 가능, authenticate() 호출
    }
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
        // 이 기기에 생체 센서 자체가 없음
    }
    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
        // 센서는 있지만 일시적으로 사용 불가
    }
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
        // 센서는 있지만 등록된 지문/얼굴이 없음 → 설정으로 유도
        val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
            putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
        }
        activity.startActivity(enrollIntent)
    }
    BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
        // 보안 패치가 필요해 현재 사용 불가
    }
    BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
    BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
        // 판단 불가 / 미지원 조합
    }
}
```

`BIOMETRIC_ERROR_NONE_ENROLLED` 케이스에서 설정 화면으로 바로 유도하는 것이 UX상 중요하다. 사용자가 왜 인증이 안 되는지 이해하지 못한 채 앱만 탓하게 되는 경우를 막을 수 있다.

# CryptoObject를 이용한 암호화 연동

단순히 "인증 성공/실패"만 확인하는 것은 보안상 취약하다. 루팅된 기기에서는 시스템 다이얼로그 결과를 조작해 인증을 우회할 수 있기 때문이다. 실제로 민감한 데이터를 보호하려면 `CryptoObject`를 통해 Keystore의 암호화 키와 인증을 결합해야 한다.

```kotlin
val cipher = getCipherForDecryption() // 아래 Keystore 섹션 참고
val cryptoObject = BiometricPrompt.CryptoObject(cipher)

biometricPrompt.authenticate(promptInfo, cryptoObject)
```

인증이 성공하면 콜백에서 이 `CryptoObject`를 다시 꺼내 실제 복호화에 사용한다.

```kotlin
override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
    val cipher = result.cryptoObject?.cipher ?: return
    val decrypted = cipher.doFinal(encryptedBytes)
    // decrypted를 사용
}
```

이 방식에서는 생체 인증이 실제로 성공해야만 Keystore가 Cipher 사용을 허가하므로, 다이얼로그 결과를 소프트웨어적으로 위조해도 실제 복호화는 불가능하다.

# Keystore와 생체 인증 결합

생체 인증에 바인딩된 키는 `KeyGenParameterSpec`에서 `setUserAuthenticationRequired(true)`로 생성한다.

```kotlin
val keyGenerator = KeyGenerator.getInstance(
    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
)

val spec = KeyGenParameterSpec.Builder(
    "recording_key",
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
)
    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
    .setUserAuthenticationRequired(true)
    .setInvalidatedByBiometricEnrollment(true) // 새 지문 등록 시 키 무효화
    .build()

keyGenerator.init(spec)
val secretKey = keyGenerator.generateKey()
```

`setInvalidatedByBiometricEnrollment(true)`는 사용자가 새로운 지문/얼굴을 추가 등록하면 기존 키를 무효화한다. 제3자가 피해자 기기에 자신의 지문을 몰래 등록해 우회하는 공격을 막기 위한 설정이며, 민감도가 높은 데이터일수록 켜두는 것이 안전하다.

## 복호화용 Cipher 준비

```kotlin
fun getCipherForDecryption(): Cipher {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val secretKey = keyStore.getKey("recording_key", null) as SecretKey

    val cipher = Cipher.getInstance(
        "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}"
    )
    cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(savedIv))
    return cipher
}
```

암호화 시 사용했던 IV(초기화 벡터)를 별도로 저장해뒀다가 복호화 시 동일하게 넣어줘야 한다.

# 인증 콜백과 에러 처리

`onAuthenticationError`의 `errorCode`는 `BiometricPrompt.ERROR_*` 상수로 구분되며, 각 상황에 맞는 UX 처리가 필요하다.

```kotlin
override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
    when (errorCode) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
            // 사용자가 직접 취소함, 별도 에러 표시 불필요
        }
        BiometricPrompt.ERROR_LOCKOUT -> {
            // 너무 많이 실패해 30초간 잠김
        }
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
            // 반복 잠금으로 영구 잠김, 기기 비밀번호로만 해제 가능
        }
        BiometricPrompt.ERROR_NO_BIOMETRICS -> {
            // 등록된 생체 정보 없음
        }
        BiometricPrompt.ERROR_HW_NOT_PRESENT,
        BiometricPrompt.ERROR_HW_UNAVAILABLE -> {
            // 하드웨어 문제
        }
        BiometricPrompt.ERROR_TIMEOUT -> {
            // 응답 시간 초과
        }
        else -> {
            // 기타
        }
    }
}
```

`ERROR_LOCKOUT_PERMANENT`가 발생하면 생체 인증 자체를 당분간 제안하지 말고, 기기 비밀번호나 앱 자체 비밀번호 같은 대체 수단으로 유도해야 한다.

# Negative Button과 Device Credential Fallback

`PromptInfo`에는 두 가지 대체 수단 설정 방식이 있으며, 서로 배타적이다.

## Negative Button 방식

```kotlin
val promptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle("본인 확인")
    .setNegativeButtonText("비밀번호로 로그인")
    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    .build()
```

이 경우 "취소" 대신 원하는 문구의 버튼을 넣고, `onAuthenticationError(ERROR_NEGATIVE_BUTTON, ...)` 콜백에서 앱이 직접 커스텀 비밀번호 화면으로 전환한다.

## Device Credential 방식

```kotlin
val promptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle("본인 확인")
    .setAllowedAuthenticators(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    )
    .build()
```

이 방식은 시스템이 알아서 PIN/패턴/비밀번호 입력 화면까지 다이얼로그 안에서 처리해준다. 단, `DEVICE_CREDENTIAL`을 허용 목록에 넣으면 `setNegativeButtonText`를 함께 설정할 수 없다(시스템이 자동으로 "다른 방법" 버튼을 넣어주기 때문). 두 방식을 동시에 설정하면 `IllegalArgumentException`이 발생한다.

# Jetpack Compose에서의 사용

Compose는 `FragmentActivity` context가 필요하므로, `LocalContext.current`를 `FragmentActivity`로 캐스팅해서 사용한다. `biometric-ktx`의 코루틴 확장을 쓰면 콜백 대신 `suspend` 함수로 처리할 수 있다.

```kotlin
@Composable
fun BiometricAuthButton(onSuccess: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()

    Button(onClick = {
        scope.launch {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("본인 확인")
                .setNegativeButtonText("취소")
                .build()

            val biometricPrompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }
                }
            )
            biometricPrompt.authenticate(promptInfo)
        }
    }) {
        Text("생체 인증으로 열기")
    }
}
```

`BiometricPrompt`는 내부적으로 `FragmentManager`에 헤드리스 `Fragment`를 붙여서 동작하므로, `remember`로 인스턴스를 캐싱하기보다는 매 요청 시 새로 생성해도 큰 오버헤드가 없다. 다만 recomposition 중 중복 호출을 막기 위해 버튼 클릭 시점에만 생성하는 패턴이 일반적이다.

# 다양한 인증 강도(Authenticator) 조합 전략

앱의 보안 요구 수준에 따라 아래처럼 단계적으로 전략을 나눌 수 있다.

- 단순 화면 잠금(예: 앱 재진입 시 확인): `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`
- 민감 정보 열람(녹음 파일, 개인 메모): `BIOMETRIC_STRONG`, CryptoObject 미사용도 가능
- 결제/서명 등 최고 보안: `BIOMETRIC_STRONG` + `CryptoObject` + `setInvalidatedByBiometricEnrollment(true)`

<br>

보안 수준과 사용자 편의성은 트레이드오프 관계이므로, 매번 최고 강도를 강제하기보다는 기능의 민감도에 맞춰 차등 적용하는 것이 실무에서 흔한 패턴이다.

# 보안 고려사항

- `onAuthenticationSucceeded` 콜백만 믿고 민감 데이터를 그대로 노출하는 것은 지양한다. 가능하면 항상 `CryptoObject`를 결합해 하드웨어 수준의 보증을 받는다.
- `setInvalidatedByBiometricEnrollment(false)`로 설정하면 공격자가 새 지문을 등록해도 기존 키가 유효하게 남으므로, 보안이 중요한 키에는 권장하지 않는다.
- 화면 캡처/녹화를 통한 정보 유출을 막으려면 인증 후 진입하는 화면에 `FLAG_SECURE`를 함께 적용하는 것이 일반적이다.
- 생체 정보 자체(지문 이미지, 얼굴 임베딩)는 앱이 절대 접근할 수 없으며, TEE(Trusted Execution Environment) 또는 보안 엘리먼트 내부에서만 처리된다. `BiometricPrompt`는 성공/실패 신호와 (있다면) 복호화된 Cipher만 앱에 전달한다.
- `BIOMETRIC_WEAK`만으로 승인된 인증 결과를 결제처럼 법적/금전적 책임이 따르는 동작에 사용하지 않는다.

# 테스트

에뮬레이터에서는 확장 컨트롤(Extended Controls)의 Fingerprint 탭에서 가상 지문 이벤트를 보낼 수 있어 `onAuthenticationSucceeded` / `onAuthenticationFailed` 흐름을 수동으로 검증할 수 있다.

```bash
# 에뮬레이터에 가상 지문 인증 이벤트 전송 (지문 ID 1번 기준)
adb -e emu finger touch 1
```

단위 테스트 레벨에서는 `BiometricPrompt` 자체가 시스템 UI에 강하게 결합되어 있어 직접 mocking하기 어렵다. 실무에서는 인증 로직을 `BiometricAuthenticator` 같은 인터페이스로 감싸고, ViewModel은 그 인터페이스에만 의존하게 해서 단위 테스트에서는 fake 구현체를 주입하는 방식을 쓴다.

```kotlin
interface BiometricAuthenticator {
    suspend fun authenticate(): AuthResult
}

class FakeBiometricAuthenticator(
    private val result: AuthResult
) : BiometricAuthenticator {
    override suspend fun authenticate(): AuthResult = result
}
```

# 기기별/제조사별 대응

생체 인증 하드웨어와 소프트웨어 스택은 제조사마다 구현이 달라, `androidx.biometric`으로 추상화되어 있음에도 실제 동작에 미묘한 차이가 남는다.

- 삼성 기기는 초음파 지문 센서(갤럭시 S 시리즈 일부)에서 화면 보호 필름 종류에 따라 인식률이 크게 달라지는 경우가 보고된다. 앱 단에서 직접 해결할 수는 없지만, 인식 실패가 반복될 때 안내 문구로 "화면을 깨끗이 닦아보세요" 같은 힌트를 주는 것이 도움이 된다.
- 일부 중저가 기기는 얼굴 인식을 2D 카메라 기반으로만 구현해 `BIOMETRIC_WEAK`로 분류되며, `BIOMETRIC_STRONG`을 요구하는 앱에서는 얼굴 인식 옵션 자체가 노출되지 않는다.
- 폴더블 기기에서는 화면 전환(접힘/펼침) 중 다이얼로그가 표시되는 시점에 레이아웃이 깨지는 경우가 있어, 가능하면 화면 상태가 안정된 시점에 `authenticate()`를 호출하는 것이 안전하다.
- Android 버전별로 `BiometricManager.Authenticators` 조합 지원 범위가 다르므로, 최소 지원 API 레벨을 정할 때 `DEVICE_CREDENTIAL` 조합 사용 가능 여부(API 30+)를 함께 고려해야 한다.

```kotlin
val allowedAuthenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
} else {
    BiometricManager.Authenticators.BIOMETRIC_STRONG
}
```

# UX 가이드라인

- 인증 다이얼로그가 뜨기 전, 왜 인증이 필요한지 화면에 미리 문구로 설명해두면 사용자가 갑작스러운 다이얼로그에 당황하지 않는다.
- `setSubtitle`과 `setDescription`을 적절히 나눠서 사용한다. Subtitle은 짧은 맥락(예: 앱/계정명), Description은 조금 더 구체적인 안내(예: "이 작업은 본인 확인이 필요합니다")에 적합하다.
- 인증 실패가 반복되면(`onAuthenticationFailed`가 여러 번 호출되면) 다이얼로그 바깥의 화면에 별도로 "생체 인증에 문제가 있나요?" 같은 도움말 링크를 노출해 대체 수단으로 자연스럽게 유도한다.
- 인증이 필수가 아닌 기능(예: 단순 편의 기능)이라면 최초 진입 시 자동으로 다이얼로그를 띄우기보다 사용자가 명시적으로 버튼을 눌렀을 때 띄우는 편이 예상치 못한 다이얼로그로 인한 이탈을 줄인다.
- 다크 모드/라이트 모드에 따라 시스템 다이얼로그 스타일이 자동으로 맞춰지므로 앱이 별도로 대응할 필요는 없지만, 다이얼로그 진입 직전 화면의 배경과 톤이 급격히 바뀌지 않도록 전환 애니메이션을 신경 쓰면 체감 완성도가 올라간다.

# 자주 겪는 이슈

- `canAuthenticate()` 체크 없이 바로 `authenticate()`를 호출하면, 생체 정보가 없는 기기에서 다이얼로그가 뜨지 않거나 바로 에러 콜백만 호출되어 원인을 파악하기 어렵다.
- `DEVICE_CREDENTIAL`과 `setNegativeButtonText`를 동시에 설정하면 `IllegalArgumentException`이 발생한다. 둘 중 하나만 선택해야 한다.
- API 30 미만 기기에서 `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` 조합을 시도하면 `BIOMETRIC_ERROR_UNSUPPORTED`가 반환된다. 버전별 분기 처리가 필요하다.
- Keystore 키 생성 시 `setUserAuthenticationValidityDurationSeconds`를 잘못 설정하면(예: 음수가 아닌 특정 값) 매 요청마다 인증을 요구하지 않고 일정 시간 캐싱되어, 의도치 않게 보안이 느슨해질 수 있다. 민감한 동작에는 이 값을 설정하지 않거나 매우 짧게 유지한다.
- `Activity`가 아니라 `Application` context로 `BiometricPrompt`를 생성하려고 하면 `IllegalArgumentException`이 발생한다. 반드시 `FragmentActivity`가 필요하다.
- 화면 회전 등으로 Activity가 재생성되는 도중 인증 다이얼로그가 떠 있으면 콜백 유실이 발생할 수 있다. `BiometricPrompt`를 Activity/Fragment의 생명주기에 맞춰 다시 바인딩하거나, ViewModel에서 상태를 들고 있다가 재구성 후 다시 연결하는 패턴이 필요하다.
- 일부 저가형 기기의 얼굴 인식은 `BIOMETRIC_WEAK`로만 분류되어 있어, `BIOMETRIC_STRONG`을 요구하면 얼굴 인식은 목록에서 제외되고 지문만 허용되는 경우가 있다.

# 정리

Android의 생체 인증은 `BiometricManager`로 사전 상태를 확인하고, `BiometricPrompt` + `PromptInfo`로 표준 UI 인증을 수행하는 두 단계로 이뤄진다. 단순 신원 확인을 넘어 실제 데이터 보호가 목적이라면 `CryptoObject`와 Android Keystore를 결합해, 인증 성공 여부를 소프트웨어 콜백이 아니라 하드웨어 수준에서 보증받는 구조로 설계해야 한다.

인증 강도(`BIOMETRIC_STRONG` / `WEAK` / `DEVICE_CREDENTIAL`)는 기능의 민감도에 따라 차등 적용하고, 실패/잠금 상황별로 적절한 대체 수단(비밀번호 화면, 기기 크리덴셜)을 안내하는 것이 UX와 보안을 동시에 만족시키는 핵심이다. Compose 환경에서도 근본적으로 `FragmentActivity` 기반 API를 그대로 쓰며, 코루틴 확장을 활용하면 콜백 없이 순차적인 코드로 작성할 수 있다.
