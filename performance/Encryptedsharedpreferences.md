# EncryptedSharedPreferences / Android Keystore

<br>

# 목차

1. 개요
2. 평문 SharedPreferences의 문제점
3. Android Keystore System 개념
4. Keystore의 하드웨어 기반 보안 계층
5. StrongBox Keymaster
6. 대칭키(AES)와 비대칭키(RSA/EC) 사용 구분
7. KeyGenParameterSpec 상세
8. Keystore에 AES 키 생성하기
9. Cipher를 이용한 암복호화 실전 예제
10. Keystore에 RSA 키 생성하기
11. 생체 인증과 연계한 키 사용 제한
12. Keystore 사용 시 주의사항과 기기별 편차
13. EncryptedSharedPreferences 개념 (Jetpack Security Crypto)
14. 의존성 설정
15. MasterKey 생성
16. EncryptedSharedPreferences 생성 및 사용
17. PrefKeyEncryptionScheme / PrefValueEncryptionScheme
18. EncryptedFile 사용 예제
19. Deprecated 이슈와 배경
20. 대안 아키텍처: DataStore + Tink
21. Tink 의존성 및 기본 사용법
22. Proto DataStore + Tink 암호화 구현
23. 기존 EncryptedSharedPreferences 데이터 마이그레이션 전략
24. 실무 적용 가이드
25. 정리

<br>

# 개요

Android 앱에서 로그인 토큰, 리프레시 토큰, API Key, 사용자 식별자 같은 민감한 값을 로컬에 저장해야 하는 경우가 자주 발생한다. 이런 값을 일반 `SharedPreferences`에 평문으로 저장하면 루팅된 기기나 디버깅 가능한 빌드에서 파일을 직접 열어 값을 확인할 수 있다.

이 문제를 해결하기 위해 Android는 두 가지 축의 보안 저장 메커니즘을 제공한다.

첫 번째는 `Android Keystore System`이다. 이는 암호화 키 자체를 하드웨어 또는 격리된 보안 영역에 저장하고, 키 값이 앱 프로세스 메모리로 절대 노출되지 않도록 하는 시스템 레벨 서비스다.

두 번째는 `Jetpack Security Crypto` 라이브러리가 제공하던 `EncryptedSharedPreferences`다. 이는 Keystore를 내부적으로 활용해 `SharedPreferences`와 동일한 API로 값을 암호화 저장할 수 있게 해주는 래퍼였다.

다만 `androidx.security:security-crypto`는 2025년 4월 1.1.0-alpha07 버전에서 공식적으로 deprecated 되었고, Google은 후속 릴리즈 계획이 없다고 명시했다. 따라서 이 노트는 Keystore의 원리를 먼저 깊이 정리하고, EncryptedSharedPreferences의 사용법과 한계, 그리고 현재 권장되는 대안 아키텍처까지 함께 다룬다.

<br>

# 평문 SharedPreferences의 문제점

`SharedPreferences`는 앱 전용 디렉터리인 `/data/data/<package>/shared_prefs/` 아래 XML 파일로 저장된다.

```
/data/data/com.impactus.app/shared_prefs/user_prefs.xml
```

Android 10 이상에서는 파일 기반 암호화(FBE)가 기본 적용되어 있어 기기 자체가 잠겨 있을 때는 디스크 이미지를 직접 덤프해도 내용을 읽을 수 없다. 하지만 다음과 같은 상황에서는 여전히 값이 그대로 노출될 수 있다.

첫째, 루팅된 기기에서는 앱 프로세스가 실행 중이거나 기기 잠금이 해제된 상태라면 `run-as` 또는 root shell로 해당 XML 파일을 직접 열람할 수 있다.

둘째, `adb backup` 또는 클라우드 백업 설정이 잘못되어 있으면 SharedPreferences 파일이 백업 데이터에 포함되어 다른 기기나 PC로 유출될 수 있다.

셋째, 디버깅 가능한(`debuggable=true`) 빌드나 개발자 옵션이 활성화된 상태에서는 `Device File Explorer` 등으로 손쉽게 값을 확인할 수 있다.

넷째, 앱 자체에 임의 코드 실행 취약점(예: WebView JavaScript Injection)이 있다면 같은 프로세스 내에서 SharedPreferences 값을 그대로 읽어갈 수 있다.

이런 이유로 액세스 토큰, 리프레시 토큰, 결제 관련 식별자, 개인 식별 정보 등은 평문 저장을 지양하고 암호화 계층을 거치는 것이 안전하다.

<br>

# Android Keystore System 개념

`Android Keystore System`은 API 18(Jelly Bean MR2)부터 도입된 시스템 서비스로, 암호화 키를 생성하고 저장하는 별도의 보안 컨테이너를 제공한다.

가장 중요한 특징은 다음과 같다.

```
키 자체는 절대 애플리케이션 프로세스 메모리로 반환되지 않는다.
암복호화 연산은 Keystore 프로세스(또는 하드웨어) 내부에서 수행된다.
앱은 키에 대한 "핸들(별칭, alias)"만 가지고 연산을 요청할 뿐이다.
```

즉 일반적인 자바 암호화 API처럼 `SecretKey` 객체의 바이트 배열을 직접 다루는 것이 아니라, Keystore가 발급한 참조를 통해 `Cipher`, `Signature`, `Mac` 객체에 연산을 위임하는 구조다.

Keystore가 제공하는 핵심 보장은 다음 네 가지로 요약할 수 있다.

```
Extraction Prevention: 키 자체를 앱이나 OS로 추출할 수 없다.
Key Use Authorization: 특정 조건(생체 인증, 잠금화면 해제 등)에서만 키 사용을 허용할 수 있다.
Key Attestation: 키가 실제로 하드웨어에 안전하게 저장되었는지 원격 서버에서 검증 가능하다.
Cryptographic operation isolation: 암복호화 연산이 앱 프로세스 밖에서 수행된다.
```

<br>

# Keystore의 하드웨어 기반 보안 계층

Keystore의 실제 구현은 기기의 하드웨어 지원 수준에 따라 세 단계로 나뉜다.

```
TEE(Trusted Execution Environment) 기반: 대부분의 최신 기기가 채택. AP 내 격리된 보안 실행 환경(ARM TrustZone 등)에서 연산.
StrongBox: 별도의 보안 칩(Secure Element)에서 연산. 물리적으로 분리된 하드웨어라 더 강력.
소프트웨어 기반 폴백: TEE를 지원하지 않는 구형/저가 기기에서 사용되는 소프트웨어 구현. 보안 수준이 상대적으로 낮음.
```

앱은 `KeyInfo`를 조회해 현재 키가 실제로 어느 계층에 저장되었는지 확인할 수 있다.

```kotlin
fun checkKeySecurityLevel(alias: String) {
    val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
    val key = keyStore.getKey(alias, null) as javax.crypto.SecretKey
    val factory = javax.crypto.SecretKeyFactory.getInstance(
        key.algorithm,
        "AndroidKeyStore"
    )
    val keyInfo = factory.getKeySpec(
        key,
        android.security.keystore.KeyInfo::class.java
    ) as android.security.keystore.KeyInfo

    val isInsideSecureHardware = keyInfo.isInsideSecureHardware
    println("Secure hardware 저장 여부: $isInsideSecureHardware")
}
```

`isInsideSecureHardware`가 `true`라면 TEE 또는 StrongBox에 저장된 것이고, `false`라면 소프트웨어 폴백이 사용된 것이다.

<br>

# StrongBox Keymaster

StrongBox는 Android 9(API 28)부터 지원되는 별도의 보안 칩 기반 구현이다. Pixel 3 이상 및 일부 플래그십 기기에서 지원한다.

StrongBox를 명시적으로 요구하려면 `KeyGenParameterSpec.Builder`에서 `setIsStrongBoxBacked(true)`를 호출한다.

```kotlin
val spec = android.security.keystore.KeyGenParameterSpec.Builder(
    "strongbox_key_alias",
    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
        android.security.keystore.KeyProperties.PURPOSE_DECRYPT
)
    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
    .setIsStrongBoxBacked(true)
    .build()
```

다만 모든 기기가 StrongBox를 지원하지는 않으므로, 지원하지 않는 기기에서는 `StrongBoxUnavailableException`이 발생한다. 따라서 다음과 같이 폴백 처리를 해주는 것이 안전하다.

```kotlin
fun createKeyWithStrongBoxFallback(alias: String) {
    try {
        createKey(alias, useStrongBox = true)
    } catch (e: android.security.keystore.StrongBoxUnavailableException) {
        createKey(alias, useStrongBox = false)
    }
}
```

<br>

# 대칭키(AES)와 비대칭키(RSA/EC) 사용 구분

Keystore는 대칭키와 비대칭키를 모두 지원하지만 용도가 다르다.

대칭키(AES)는 로컬에서 데이터를 암호화하고 같은 앱이 다시 복호화해야 하는 경우에 적합하다. 키가 기기 밖으로 나갈 필요가 없는 시나리오, 즉 로컬 저장소 암호화에 주로 사용한다.

비대칭키(RSA, EC)는 서명(Signature) 용도나, 공개키만 서버에 등록해두고 개인키는 기기에 남겨 인증서 기반 인증을 수행하는 시나리오에 적합하다. 예를 들어 FIDO2/WebAuthn 방식의 생체 인증 로그인 구현에 사용된다.

이 노트에서 다루는 로컬 데이터 암호화 목적으로는 AES-GCM 대칭키가 표준이다.

<br>

# KeyGenParameterSpec 상세

`KeyGenParameterSpec`은 Keystore에 생성할 키의 속성을 정의하는 빌더 클래스다. 주요 옵션은 다음과 같다.

```
setKeySize: 키 길이. AES는 보통 256비트.
setBlockModes: 블록 암호 모드. GCM을 권장(인증 태그 포함, 무결성 보장).
setEncryptionPaddings: 패딩 방식. GCM 모드는 NoPadding을 사용.
setRandomizedEncryptionRequired: true면 매번 다른 IV를 강제해 동일 평문도 다른 암호문이 나오게 함.
setUserAuthenticationRequired: true면 키 사용 시 잠금화면 해제 또는 생체 인증을 요구.
setUserAuthenticationValidityDurationSeconds: 인증 후 키 사용 가능 유효 시간(초).
setInvalidatedByBiometricEnrollment: 새로운 지문/얼굴 등록 시 키를 무효화할지 여부.
```

전체 예제는 다음과 같다.

```kotlin
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "secure_local_data_key"

fun generateAesKey() {
    val keyGenerator = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        KEYSTORE_PROVIDER
    )

    val spec = KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
        .setKeySize(256)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build()

    keyGenerator.init(spec)
    keyGenerator.generateKey()
}
```

<br>

# Keystore에 AES 키 생성하기

앞서 생성한 키가 이미 Keystore에 존재하는지 확인하고, 없을 때만 새로 생성하는 헬퍼를 함께 구성하는 것이 일반적이다.

```kotlin
import java.security.KeyStore

object KeystoreHelper {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "secure_local_data_key"

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    }

    fun getOrCreateSecretKey(): javax.crypto.SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null)
            as? KeyStore.SecretKeyEntry

        if (existingKey != null) {
            return existingKey.secretKey
        }

        val keyGenerator = javax.crypto.KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )

        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(
                android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE
            )
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
```

이렇게 앱 최초 실행 시 한 번만 키가 생성되고, 이후에는 동일한 alias로 계속 재사용된다.

<br>

# Cipher를 이용한 암복호화 실전 예제

AES-GCM 모드는 암호화할 때마다 IV(초기화 벡터)가 랜덤으로 생성되므로, 복호화 시점에 동일한 IV를 함께 보관해두었다가 사용해야 한다. 보통 암호문 앞에 IV를 붙여 하나의 바이트 배열로 저장한다.

```kotlin
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

object CryptoManager {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    fun encrypt(plainText: String): String {
        val secretKey = KeystoreHelper.getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // IV(12바이트) + 암호문을 이어 붙여서 하나의 배열로 관리
        val combined = iv + encryptedBytes
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encryptedBase64: String): String {
        val secretKey = KeystoreHelper.getOrCreateSecretKey()
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)

        val ivSize = 12
        val iv = combined.copyOfRange(0, ivSize)
        val encryptedBytes = combined.copyOfRange(ivSize, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
```

사용 예시는 다음과 같다.

```kotlin
val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
val encrypted = CryptoManager.encrypt(token)

// SharedPreferences에는 암호화된 문자열만 저장
sharedPreferences.edit().putString("access_token", encrypted).apply()

// 복호화
val stored = sharedPreferences.getString("access_token", null)
val original = stored?.let { CryptoManager.decrypt(it) }
```

이 방식이 사실상 EncryptedSharedPreferences가 내부적으로 하던 일을 직접 구현한 것과 같다. Deprecated 이후 신규 프로젝트에서는 이런 형태로 Keystore를 직접 다루거나, 후술할 Tink 라이브러리를 사용하는 것이 권장된다.

<br>

# Keystore에 RSA 키 생성하기

서명이나 공개키 교환이 필요한 경우 RSA 키 쌍을 생성한다.

```kotlin
import java.security.KeyPairGenerator
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

fun generateRsaKeyPair(alias: String) {
    val keyPairGenerator = KeyPairGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_RSA,
        "AndroidKeyStore"
    )

    val spec = KeyGenParameterSpec.Builder(
        alias,
        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
    )
        .setDigests(KeyProperties.DIGEST_SHA256)
        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
        .setKeySize(2048)
        .build()

    keyPairGenerator.initialize(spec)
    keyPairGenerator.generateKeyPair()
}
```

생성된 개인키로 서명하고, 공개키는 서버로 전송해 검증하는 흐름이 일반적이다.

```kotlin
fun signData(alias: String, data: ByteArray): ByteArray {
    val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
    val privateKey = keyStore.getKey(alias, null) as java.security.PrivateKey

    val signature = java.security.Signature.getInstance("SHA256withRSA")
    signature.initSign(privateKey)
    signature.update(data)
    return signature.sign()
}
```

<br>

# 생체 인증과 연계한 키 사용 제한

`setUserAuthenticationRequired(true)`를 지정하면 키를 사용할 때마다(또는 지정한 유효 시간 동안) 잠금화면 해제나 생체 인증을 거쳐야만 `Cipher` 연산이 허용된다.

```kotlin
val spec = KeyGenParameterSpec.Builder(
    "biometric_protected_key",
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setUserAuthenticationRequired(true)
    .setUserAuthenticationValidityDurationSeconds(30)
    .setInvalidatedByBiometricEnrollment(true)
    .build()
```

`AndroidX Biometric` 라이브러리와 결합하면 `BiometricPrompt.CryptoObject`를 통해 인증에 성공한 뒤에만 `Cipher`가 실제로 동작하도록 강제할 수 있다.

```kotlin
val cryptoObject = androidx.biometric.BiometricPrompt.CryptoObject(cipher)

val biometricPrompt = androidx.biometric.BiometricPrompt(
    activity,
    executor,
    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(
            result: androidx.biometric.BiometricPrompt.AuthenticationResult
        ) {
            val authenticatedCipher = result.cryptoObject?.cipher
            // authenticatedCipher로 doFinal 호출
        }
    }
)

val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
    .setTitle("본인 확인")
    .setSubtitle("저장된 정보를 확인하려면 지문을 인증하세요")
    .setNegativeButtonText("취소")
    .build()

biometricPrompt.authenticate(promptInfo, cryptoObject)
```

이 방식은 결제 정보나 개인 식별 정보처럼 앱 실행 중에도 매번 재확인이 필요한 값에 적합하다.

<br>

# Keystore 사용 시 주의사항과 기기별 편차

Keystore는 시스템 서비스이기 때문에 다음과 같은 예외 상황을 항상 고려해야 한다.

```
KeyPermanentlyInvalidatedException: 기기 잠금 해제 방식(지문/PIN)이 변경되면 기존 키가 영구 무효화된다. 이 예외를 잡아 키를 재생성하고 사용자에게 재로그인을 요구해야 한다.
UserNotAuthenticatedException: setUserAuthenticationRequired가 걸린 키를 인증 없이 사용하려 할 때 발생.
일부 저가형/구형 OEM 기기에서 Keystore 구현 버그로 인해 특정 알고리즘 조합에서 예외가 발생하는 사례가 보고되어 있다.
```

실무에서는 아래처럼 무효화 예외를 잡아 안전하게 키를 재생성하는 방어 코드를 반드시 넣어야 한다.

```kotlin
fun encryptSafely(plainText: String): String {
    return try {
        CryptoManager.encrypt(plainText)
    } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
        // 기존 키 삭제 후 재생성
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
        keyStore.deleteEntry("secure_local_data_key")
        CryptoManager.encrypt(plainText)
    }
}
```

<br>

# EncryptedSharedPreferences 개념 (Jetpack Security Crypto)

`EncryptedSharedPreferences`는 위에서 직접 구현한 Keystore + Cipher 로직을 `SharedPreferences` 인터페이스 뒤로 감춰준 Jetpack 라이브러리다. 내부적으로 Google의 `Tink` 암호화 라이브러리를 사용해 키 관리와 봉투 암호화(envelope encryption)를 수행한다.

동작 원리를 요약하면 다음과 같다.

```
1. MasterKey를 Android Keystore에 생성/저장한다.
2. MasterKey로 데이터 암호화 키(DEK)를 감싸서(wrap) 별도 키셋 파일에 저장한다.
3. 실제 SharedPreferences의 각 key와 value는 DEK로 암호화되어 XML에 저장된다.
4. 조회 시 MasterKey로 DEK를 풀고, DEK로 실제 값을 복호화해 반환한다.
```

즉 앱 입장에서는 일반 `SharedPreferences`와 동일한 `getString`, `putString` API를 그대로 사용하면서 내부적으로만 암호화가 적용되는 구조였다.

<br>

# 의존성 설정

Deprecated 되었지만 기존 코드베이스 이해나 레거시 유지보수를 위해 문법은 알아둘 필요가 있다.

```gradle
dependencies {
    implementation "androidx.security:security-crypto:1.1.0-alpha06"
}
```

alpha07부터는 deprecated 표기가 붙었으므로, 신규로 참고할 때는 alpha06 문법 기준으로 이해해도 무방하다.

<br>

# MasterKey 생성

```kotlin
import androidx.security.crypto.MasterKey

fun createMasterKey(context: android.content.Context): MasterKey {
    return MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
}
```

`MasterKey`는 내부적으로 앞서 다룬 `KeyGenParameterSpec` 기반 AES-256-GCM 키를 Keystore에 생성한다. 즉 `EncryptedSharedPreferences`도 결국 Keystore를 그대로 사용하고 있었다.

<br>

# EncryptedSharedPreferences 생성 및 사용

```kotlin
import androidx.security.crypto.EncryptedSharedPreferences

fun createEncryptedPrefs(
    context: android.content.Context,
    masterKey: MasterKey
): android.content.SharedPreferences {
    return EncryptedSharedPreferences.create(
        context,
        "secret_shared_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
```

사용법은 일반 SharedPreferences와 완전히 동일하다.

```kotlin
val encryptedPrefs = createEncryptedPrefs(context, masterKey)

encryptedPrefs.edit()
    .putString("access_token", token)
    .putString("refresh_token", refreshToken)
    .apply()

val savedToken = encryptedPrefs.getString("access_token", null)
```

주의할 점은 이 파일을 Auto Backup 대상에서 반드시 제외해야 한다는 것이다. 복원 시 새 기기에는 원래 MasterKey가 없기 때문에 파일 자체가 무용지물이 되거나 예외가 발생한다.

```xml
<full-backup-content>
    <exclude domain="sharedpref" path="secret_shared_prefs.xml" />
</full-backup-content>
```

<br>

# PrefKeyEncryptionScheme / PrefValueEncryptionScheme

`EncryptedSharedPreferences`는 키(key)와 값(value)에 서로 다른 암호화 스킴을 적용한다.

```
PrefKeyEncryptionScheme.AES256_SIV: 결정적(deterministic) 암호화. 같은 평문 키는 항상 같은 암호문 키로 매핑되어 조회(get)가 가능해야 하므로 이 방식을 사용한다.
PrefValueEncryptionScheme.AES256_GCM: 비결정적(non-deterministic) 암호화. 매번 다른 암호문이 나오도록 해 값의 기밀성과 무결성을 강화한다.
```

키에는 SIV를, 값에는 GCM을 쓰는 이유는 명확하다. `SharedPreferences.getString("access_token", ...)`처럼 평문 키로 조회해야 하므로, 저장된 암호문 키도 같은 평문에 대해 항상 동일해야 검색이 가능하다. 반면 값은 조회 대상이 아니라 단순히 복호화만 하면 되므로 매번 다른 암호문이어도 상관없고, 오히려 그게 더 안전하다.

<br>

# EncryptedFile 사용 예제

`Jetpack Security Crypto`는 SharedPreferences뿐 아니라 파일 단위 암호화도 지원했다.

```kotlin
import androidx.security.crypto.EncryptedFile

fun writeEncryptedFile(
    context: android.content.Context,
    masterKey: MasterKey,
    fileName: String,
    content: ByteArray
) {
    val file = java.io.File(context.filesDir, fileName)
    if (file.exists()) file.delete()

    val encryptedFile = EncryptedFile.Builder(
        context,
        file,
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    encryptedFile.openFileOutput().use { outputStream ->
        outputStream.write(content)
    }
}

fun readEncryptedFile(
    context: android.content.Context,
    masterKey: MasterKey,
    fileName: String
): ByteArray {
    val file = java.io.File(context.filesDir, fileName)

    val encryptedFile = EncryptedFile.Builder(
        context,
        file,
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    return encryptedFile.openFileInput().use { inputStream ->
        inputStream.readBytes()
    }
}
```

이미지, 캐시된 민감 문서, 오프라인 저장이 필요한 개인정보 파일 등에 활용할 수 있다.

<br>

# Deprecated 이슈와 배경

2025년 4월 `androidx.security:security-crypto` 1.1.0-alpha07 릴리즈에서 `EncryptedSharedPreferences`를 포함한 라이브러리 전체가 deprecated 되었고, Google은 이후 추가 릴리즈 계획이 없다고 공식 문서에 명시했다.

Deprecated의 배경으로 커뮤니티에서 분석하는 이유는 다음과 같다.

```
성능 문제: SharedPreferences 자체가 호출 스레드에서 동기 I/O를 수행하는데, 암복호화 연산까지 더해지며 메인 스레드 블로킹이 심해졌다.
안정성 문제: 제조사별 Keystore 구현 편차로 인해 특정 OEM 기기에서 keyset 손상(keyset corruption) 예외가 Crashlytics에 다수 보고되었다.
설계 철학 문제: SharedPreferences 자체가 다수의 개별 파일 I/O를 유발하는 구조라 암호화와 결합하면 구조적으로 비효율적이다.
```

또한 커뮤니티 일부에서는 애초에 Android 10 이상 기기는 파일 기반 암호화가 기본 적용되어 있어, EncryptedSharedPreferences가 제공하는 추가 보안 이득이 생각보다 크지 않다는 지적도 있다. 오히려 이 API의 존재 자체가 "SharedPreferences는 불안전하다"는 오해를 강화해, 정작 더 중요한 원칙인 "민감한 데이터는 애초에 기기에 저장하지 않는다"는 원칙에서 개발자들의 주의를 분산시켰다는 비판도 있다.

<br>

# 대안 아키텍처: DataStore + Tink

현재 권장되는 방향은 저장(storage)과 암호화(encryption)의 책임을 분리하는 것이다.

```
저장 계층: Jetpack DataStore(Proto DataStore 권장). 코루틴/Flow 기반으로 항상 IO 디스패처에서 비동기 수행.
암호화 계층: Google Tink. 파일 전체를 스트림 단위로 암호화하며, 키 관리도 Tink의 Keyset 개념으로 별도 처리.
```

이 구조의 장점은 다음과 같다.

```
DataStore는 애초에 메인 스레드 위반(StrictMode violation) 문제가 없다.
Tink는 Keystore와 결합해 키 자체는 여전히 하드�90웨어에 안전하게 보관하면서, 암호화 로직은 Google이 계속 유지보수한다.
Proto DataStore를 쓰면 타입 세이프하게 구조화된 데이터를 다룰 수 있어 개별 key-value 파싱 오류가 줄어든다.
개별 값이 아니라 파일 전체를 암호화하므로 메타데이터(어떤 키들이 존재하는지)까지 감춰진다.
```

<br>

# Tink 의존성 및 기본 사용법

```gradle
dependencies {
    implementation "com.google.crypto.tink:tink-android:1.13.0"
}
```

Tink에서 Android Keystore와 연동된 Keyset을 다루는 기본 흐름은 다음과 같다.

```kotlin
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.aead.AeadKeyTemplates

object TinkCryptoManager {

    private const val MASTER_KEY_URI = "android-keystore://tink_master_key"
    private const val PREF_FILE_NAME = "tink_keyset_prefs"
    private const val PREF_KEY_NAME = "tink_keyset"

    fun init(context: android.content.Context) {
        AeadConfig.register()
    }

    fun getAead(context: android.content.Context): com.google.crypto.tink.Aead {
        val keysetManager = AndroidKeysetManager.Builder()
            .withSharedPref(context, PREF_KEY_NAME, PREF_FILE_NAME)
            .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()

        return keysetManager.keysetHandle.getPrimitive(com.google.crypto.tink.Aead::class.java)
    }
}
```

`Aead`(Authenticated Encryption with Associated Data) 프리미티브를 얻으면 바이트 배열 단위로 간단히 암복호화할 수 있다.

```kotlin
fun encryptBytes(aead: com.google.crypto.tink.Aead, plain: ByteArray): ByteArray {
    return aead.encrypt(plain, null)
}

fun decryptBytes(aead: com.google.crypto.tink.Aead, cipherBytes: ByteArray): ByteArray {
    return aead.decrypt(cipherBytes, null)
}
```

두 번째 인자(`associatedData`)에는 컨텍스트 바인딩용 데이터를 넣을 수 있는데, 예를 들어 사용자 ID를 넣으면 다른 사용자의 데이터로 바꿔치기하는 공격을 방지할 수 있다.

<br>

# Proto DataStore + Tink 암호화 구현

Proto 스키마를 먼저 정의한다.

```protobuf
syntax = "proto3";

option java_package = "com.impactus.app.data";
option java_multiple_files = true;

message SecureUserPrefs {
    string access_token = 1;
    string refresh_token = 2;
    int64 token_expiry = 3;
}
```

`Serializer`를 구현하면서 쓰기 전에는 암호화, 읽을 때는 복호화를 수행하도록 감싼다.

```kotlin
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream

class EncryptedSecureUserPrefsSerializer(
    private val aead: com.google.crypto.tink.Aead
) : Serializer<SecureUserPrefs> {

    override val defaultValue: SecureUserPrefs = SecureUserPrefs.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): SecureUserPrefs {
        return try {
            val encryptedBytes = input.readBytes()
            if (encryptedBytes.isEmpty()) {
                return defaultValue
            }
            val decryptedBytes = aead.decrypt(encryptedBytes, null)
            SecureUserPrefs.parseFrom(decryptedBytes)
        } catch (exception: Exception) {
            throw CorruptionException("암호화된 프로토 파일을 읽는 데 실패했습니다", exception)
        }
    }

    override suspend fun writeTo(t: SecureUserPrefs, output: OutputStream) {
        val plainBytes = t.toByteArray()
        val encryptedBytes = aead.encrypt(plainBytes, null)
        output.write(encryptedBytes)
    }
}
```

DataStore 인스턴스를 생성한다.

```kotlin
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile

val Context.secureUserPrefsStore: DataStore<SecureUserPrefs> by lazy {
    androidx.datastore.core.DataStoreFactory.create(
        serializer = EncryptedSecureUserPrefsSerializer(
            TinkCryptoManager.getAead(applicationContext)
        ),
        produceFile = { applicationContext.dataStoreFile("secure_user_prefs.pb") }
    )
}
```

읽기와 쓰기는 다음과 같이 코루틴 기반으로 처리한다.

```kotlin
suspend fun saveTokens(context: android.content.Context, access: String, refresh: String) {
    context.secureUserPrefsStore.updateData { current ->
        current.toBuilder()
            .setAccessToken(access)
            .setRefreshToken(refresh)
            .setTokenExpiry(System.currentTimeMillis())
            .build()
    }
}

fun observeAccessToken(context: android.content.Context) =
    context.secureUserPrefsStore.data
        .map { it.accessToken }
```

이 구조는 `EncryptedSharedPreferences`보다 다음 지점에서 명확히 우위에 있다.

```
모든 IO가 코루틴 Dispatcher에서 처리되어 메인 스레드 블로킹이 없다.
파일 전체가 통째로 암호화되어 개별 key 이름조차 노출되지 않는다.
Proto 스키마 덕분에 필드 오탈자나 타입 불일치로 인한 런타임 파싱 오류가 컴파일 타임에 방지된다.
Tink는 현재도 Google이 활발히 유지보수 중인 라이브러리다.
```

<br>

# 기존 EncryptedSharedPreferences 데이터 마이그레이션 전략

이미 서비스 중인 앱이라면 한 번에 갈아엎을 수 없으므로 점진적 마이그레이션이 필요하다.

```
1. 앱 시작 시점에 기존 EncryptedSharedPreferences 인스턴스를 그대로 열어 값을 읽는다.
2. 읽은 값을 새로운 Proto DataStore(Tink 기반)에 쓴다.
3. 마이그레이션 완료 플래그를 별도로 남긴다.
4. 이후 실행부터는 새 DataStore만 사용하고, 기존 EncryptedSharedPreferences 파일은 삭제한다.
```

코드로 표현하면 다음과 같다.

```kotlin
suspend fun migrateIfNeeded(context: android.content.Context) {
    val migrationDone = context.secureUserPrefsStore.data
        .map { it.tokenExpiry > 0 }
        .first()

    if (migrationDone) return

    val masterKey = createMasterKey(context)
    val legacyPrefs = createEncryptedPrefs(context, masterKey)

    val legacyAccessToken = legacyPrefs.getString("access_token", null)
    val legacyRefreshToken = legacyPrefs.getString("refresh_token", null)

    if (legacyAccessToken != null && legacyRefreshToken != null) {
        saveTokens(context, legacyAccessToken, legacyRefreshToken)
        legacyPrefs.edit().clear().apply()
    }
}
```

기존 라이브러리가 완전히 릴리즈 중단된 상태이므로, 신규 의존성 추가 시 alpha07 이상 버전은 deprecated 경고가 뜨는 것을 인지하고 있어야 하며, 신규 프로젝트라면 처음부터 DataStore + Tink 조합으로 시작하는 편이 장기적으로 유지보수 비용이 적다.

<br>

# 실무 적용 가이드

임팩터스처럼 소규모 팀에서 Android 앱에 이 내용을 적용할 때 고려할 우선순위를 정리하면 다음과 같다.

첫째, 정말 암호화 저장이 필요한 값인지부터 판단한다. Android 10 이상을 최소 지원 버전으로 잡고 있다면, 단순 사용자 설정값이나 UI 상태 같은 값은 암호화 없이 일반 DataStore/SharedPreferences로 충분한 경우가 많다.

둘째, 액세스 토큰이나 리프레시 토큰처럼 유출 시 실질적 피해가 큰 값만 선별적으로 암호화 계층을 적용한다. 모든 값을 무조건 암호화하면 성능 비용만 늘어난다.

셋째, 신규 기능이라면 EncryptedSharedPreferences 대신 처음부터 Proto DataStore + Tink 조합으로 설계한다. 기존에 EncryptedSharedPreferences를 쓰고 있는 코드가 있다면 당장 급하게 교체할 필요는 없지만, 신규 의존성 버전을 올릴 때 deprecated 경고가 발생할 수 있다는 점을 팀에 공유해둔다.

넷째, Keystore 키가 `KeyPermanentlyInvalidatedException`으로 무효화되는 상황(생체 정보 변경 등)에 대한 방어 코드를 반드시 넣어, 앱이 크래시 없이 재로그인 플로우로 자연스럽게 유도되도록 한다.

<br>

# 정리

Android Keystore는 키 자체를 하드웨어 또는 격리된 보안 영역에 보관하고, 앱은 그 키에 대한 연산만 위임받아 사용하는 시스템이다. TEE와 StrongBox라는 두 단계의 하드웨어 보안 계층이 있으며, `KeyGenParameterSpec`을 통해 알고리즘, 블록 모드, 사용자 인증 요구 여부 등을 세밀하게 제어할 수 있다.

`EncryptedSharedPreferences`는 이 Keystore 기반 암복호화를 `SharedPreferences` API 뒤에 감춘 편의 라이브러리였으나, 성능 문제와 기기별 안정성 편차로 인해 2025년 4월 공식 deprecated 되었다. 키에는 결정적 암호화(AES256_SIV), 값에는 비결정적 암호화(AES256_GCM)를 적용하던 설계 자체는 여전히 참고할 가치가 있는 패턴이다.

현재 권장되는 대안은 저장 계층(Proto DataStore)과 암호화 계층(Google Tink)을 분리하는 아키텍처다. 이 구조는 코루틴 기반 비동기 IO, 파일 전체 단위 암호화, 타입 세이프한 스키마라는 세 가지 이점을 동시에 제공한다. 기존 EncryptedSharedPreferences 코드가 있는 프로젝트는 점진적 마이그레이션 경로를 통해 안전하게 전환할 수 있으며, 신규 프로젝트는 처음부터 DataStore + Tink 조합으로 설계하는 것이 장기적으로 유리하다.
