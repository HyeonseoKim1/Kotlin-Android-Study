# Android 보안

## 목차

1. Android 보안 모델
2. 권한(Permission)
3. 컴포넌트 보안
4. 로컬 데이터 저장 보안
5. Android Keystore
6. 생체인증(BiometricPrompt)
7. 네트워크 보안
8. WebView 보안
9. 화면·로그·클립보드 유출 방지
10. 코드 보호와 무결성 검증
11. 의존성과 빌드 보안
12. OWASP MASVS 체크리스트

<br>

# 1. Android 보안 모델

Android는 여러 계층이 겹쳐서 앱을 보호한다. 앱 하나가 뚫려도 다른 앱이나 시스템으로 번지지 않게 격리하는 것이 핵심 설계다.

## 1.1 계층 구조

| 계층 | 역할 |
|------|------|
| Linux 커널 | 프로세스 격리, UID 기반 접근 제어 |
| SELinux | 강제적 접근 통제(MAC), 앱 도메인 분리 |
| 앱 샌드박스 | 앱마다 고유 UID, 전용 데이터 디렉터리 |
| 권한 시스템 | 민감 자원 접근을 사용자 동의로 제어 |
| 앱 서명 | 앱 출처와 무결성 보증 |
| Verified Boot | 부팅 체인 무결성 검증 |
| 하드웨어 보안 | TEE, StrongBox, Keystore 키 보호 |

## 1.2 앱 샌드박스

- 설치 시 앱마다 고유한 Linux UID가 부여된다.
- `/data/data/<package>` (내부 저장소)는 해당 UID만 접근 가능하다.
- 다른 앱과 데이터를 공유하려면 ContentProvider, Intent, Bound Service 같은 명시적 통로를 거쳐야 한다.
- 루팅된 기기에서는 이 격리가 깨지므로 민감 데이터는 "기기가 안전하다"는 전제에 의존하면 안 된다.

## 1.3 앱 서명

- 모든 APK/AAB는 서명되어야 설치된다.
- 같은 서명 키로 서명된 앱만 업데이트가 가능하다. 즉 서명은 "이 앱의 정당한 업데이트인가"를 판별하는 기준이다.
- Play App Signing을 쓰면 실제 배포 키는 Google이 보관하고, 개발자는 업로드 키만 관리한다. 업로드 키를 분실해도 재설정이 가능해서 안전하다.
- v1(JAR)은 취약점 이력이 있어 v2 이상(APK Signature Scheme v2/v3/v4)을 사용한다.

```kotlin
// build.gradle.kts - 서명 정보는 코드에 넣지 말고 로컬/CI 환경에서 주입
android {
    signingConfigs {
        create("release") {
            storeFile = file(providers.gradleProperty("RELEASE_STORE_FILE").get())
            storePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

<br>

# 2. 권한(Permission)

## 2.1 원칙

- 최소 권한: 필요한 기능에 꼭 필요한 권한만 선언한다.
- 필요한 시점에 요청한다. 앱 시작 직후 일괄 요청은 거부율을 높인다.
- 거부되어도 앱이 최대한 동작해야 한다.

## 2.2 보호 수준

| 수준 | 설명 | 예시 |
|------|------|------|
| normal | 설치 시 자동 부여 | `INTERNET`, `VIBRATE` |
| dangerous | 런타임에 사용자 동의 필요 | `CAMERA`, `RECORD_AUDIO`, `ACCESS_FINE_LOCATION` |
| signature | 같은 서명의 앱끼리만 | 커스텀 권한에 자주 사용 |
| special | 설정 화면에서 별도 허용 | `SYSTEM_ALERT_WINDOW`, `MANAGE_EXTERNAL_STORAGE` |

## 2.3 Compose에서 런타임 권한 요청

```kotlin
@Composable
fun CameraPermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onGranted() else showRationale = true
    }

    LaunchedEffect(Unit) {
        val state = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (state == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("카메라 권한이 필요해요") },
            text = { Text("촬영 기능을 사용하려면 설정에서 권한을 허용해 주세요.") },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                }) { Text("설정으로 이동") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("취소") }
            }
        )
    }
}
```

## 2.4 권한을 줄이는 방법

- 사진/미디어: 전체 저장소 권한 대신 Photo Picker (`PickVisualMedia`) 사용. 권한 자체가 필요 없다.
- 위치: 정밀 위치가 꼭 필요한지 검토하고, 대략적 위치(`ACCESS_COARSE_LOCATION`)로 충분하면 그것만 요청한다.
- 파일: Storage Access Framework(`OpenDocument`, `CreateDocument`)로 사용자가 직접 고른 파일만 접근한다.

```kotlin
val pickMedia = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
) { uri: Uri? ->
    // 권한 없이 선택된 이미지 URI만 받는다
}

pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
```

<br>

# 3. 컴포넌트 보안

Activity, Service, BroadcastReceiver, ContentProvider는 외부에서 호출될 수 있는 공격면이다.

## 3.1 exported 관리

- `android:exported`는 intent-filter가 있는 컴포넌트에 Android 12(API 31)부터 명시가 필수다.
- 외부 앱이 호출할 필요가 없는 컴포넌트는 반드시 `exported="false"`.
- exported 컴포넌트는 들어오는 Intent의 모든 값을 신뢰하지 않고 검증한다.

```xml
<!-- 외부 진입이 필요한 런처 Activity만 true -->
<activity
    android:name=".MainActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>

<!-- 내부 전용 -->
<service
    android:name=".sync.SyncService"
    android:exported="false" />

<!-- 서명이 같은 앱에게만 공개 -->
<provider
    android:name=".data.SharedProvider"
    android:authorities="${applicationId}.shared"
    android:exported="true"
    android:permission="${applicationId}.permission.ACCESS_SHARED" />
```

## 3.2 Intent 검증

```kotlin
class DeepLinkActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val target = parseTarget(intent)
        if (target == null) {
            finish()
            return
        }
        // target을 기반으로 이동
    }

    private fun parseTarget(intent: Intent): Target? {
        val uri = intent.data ?: return null
        // 1) 스킴과 호스트를 화이트리스트로 검증
        if (uri.scheme != "https" || uri.host != "app.example.com") return null
        // 2) 경로를 알려진 값으로만 매핑
        return when (uri.pathSegments.firstOrNull()) {
            "product" -> uri.lastPathSegment?.toLongOrNull()?.let { Target.Product(it) }
            "event" -> uri.lastPathSegment?.toLongOrNull()?.let { Target.Event(it) }
            else -> null
        }
    }
}
```

- 딥링크 값으로 받은 URL을 그대로 WebView에 로드하지 않는다.
- 타입 변환(`toLongOrNull`)과 화이트리스트 매핑으로 입력을 좁힌다.
- 가능하면 Custom Scheme보다 App Links(검증된 `https`)를 사용한다. Custom Scheme은 다른 앱이 같은 스킴을 선점할 수 있다.

## 3.3 PendingIntent

- 기본은 `FLAG_IMMUTABLE`. Android 12부터는 가변/불변 중 하나를 반드시 명시한다.
- `FLAG_MUTABLE`은 인라인 답장처럼 꼭 필요한 경우에만, 그리고 반드시 명시적 Intent(컴포넌트 지정)와 함께 쓴다.
- 암시적 Intent를 담은 가변 PendingIntent는 다른 앱이 내용을 바꿔 호출할 수 있어 위험하다.

```kotlin
val intent = Intent(context, DetailActivity::class.java).apply {
    putExtra("id", id)
}
val pendingIntent = PendingIntent.getActivity(
    context,
    id.hashCode(),
    intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
```

## 3.4 BroadcastReceiver

- 동적 등록 시 Android 13+에서는 `RECEIVER_NOT_EXPORTED` / `RECEIVER_EXPORTED`를 명시한다.
- 앱 내부 통신은 Broadcast 대신 Flow/SharedFlow 같은 인메모리 방식이 안전하고 단순하다.

```kotlin
ContextCompat.registerReceiver(
    context,
    receiver,
    IntentFilter(ACTION_SYNC_DONE),
    ContextCompat.RECEIVER_NOT_EXPORTED
)
```

## 3.5 ContentProvider

- SQL을 직접 조합하지 말고 `selectionArgs`로 바인딩한다 (SQL Injection).
- `openFile`에서 경로를 받으면 `canonicalPath`로 정규화한 뒤 허용 디렉터리 안인지 확인한다 (Path Traversal).
- 파일 공유는 `FileProvider`로 임시 URI 권한(`FLAG_GRANT_READ_URI_PERMISSION`)만 부여한다.

<br>

# 4. 로컬 데이터 저장 보안

## 4.1 저장 위치별 특징

| 위치 | 접근 | 비고 |
|------|------|------|
| 내부 저장소(`filesDir`) | 앱만 | 기본 선택지. 루팅 시 노출 |
| 캐시(`cacheDir`) | 앱만 | 시스템이 삭제 가능. 민감 정보 금지 |
| 외부 저장소 앱 전용 디렉터리 | 앱 + (구버전은 타 앱) | 민감 정보 금지 |
| 공유 저장소(MediaStore) | 권한 있는 앱 | 사용자와 공유할 파일만 |

- 토큰, 비밀번호, 개인정보는 내부 저장소에 두되, 한 단계 더 나아가 Keystore 키로 암호화해서 저장한다.
- `MODE_WORLD_READABLE`, `MODE_WORLD_WRITEABLE`은 제거되었고 쓰면 안 된다.

## 4.2 SharedPreferences / DataStore에 민감 값 저장

`androidx.security:security-crypto`의 `EncryptedSharedPreferences`, `EncryptedFile`은 deprecated 되었다. 신규 코드에서는 아래 방식을 권장한다.

- Android Keystore로 직접 AES-GCM 암호화 후 DataStore에 저장
- 또는 Google Tink 라이브러리 사용 (Keystore 연동 지원)

```kotlin
// 암호문을 Preferences DataStore에 Base64 문자열로 저장하는 예시
class SecureTokenStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: KeystoreCipher
) {
    private val tokenKey = stringPreferencesKey("access_token_enc")

    suspend fun save(token: String) {
        val encrypted = cipher.encrypt(token.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        dataStore.edit { it[tokenKey] = encoded }
    }

    suspend fun read(): String? {
        val encoded = dataStore.data.first()[tokenKey] ?: return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            String(cipher.decrypt(bytes), Charsets.UTF_8)
        }.getOrNull()
    }

    suspend fun clear() {
        dataStore.edit { it.remove(tokenKey) }
    }
}
```

`KeystoreCipher` 구현은 5장에 있다.

## 4.3 Room DB 암호화

- 기본 Room(SQLite)은 평문 파일이다.
- 민감 데이터가 많다면 SQLCipher(`net.zetetic:sqlcipher-android`)를 Room의 `SupportSQLiteOpenHelper.Factory`로 연결한다.
- DB 암호화 키는 앱 코드에 하드코딩하지 말고, Keystore로 보호한 랜덤 키를 쓴다.
- 암호화 비용(성능, APK 크기)이 있으므로 "무엇이 민감한가"를 먼저 구분한다. 토큰 같은 소량 비밀은 DataStore + Keystore가 훨씬 가볍다.

## 4.4 백업

- `android:allowBackup="true"`이면 `adb backup`이나 클라우드 백업으로 앱 데이터가 빠져나갈 수 있다.
- 민감 데이터가 있으면 백업에서 제외한다.

```xml
<application
    android:allowBackup="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules">
</application>
```

```xml
<!-- res/xml/data_extraction_rules.xml (Android 12+) -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="." />
        <exclude domain="database" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="." />
        <exclude domain="database" path="." />
    </device-transfer>
</data-extraction-rules>
```

<br>

# 5. Android Keystore

## 5.1 개념

- 암호 키를 앱 프로세스 밖(TEE 또는 StrongBox)에 보관하고, 앱은 키 자체가 아니라 "키로 연산을 요청하는 핸들"만 가진다.
- 키 원본을 추출할 수 없다(non-exportable). 루팅된 기기에서도 키 자체를 꺼내기 어렵다. 다만 앱이 살아있는 동안 복호화 연산을 호출하는 것은 막지 못한다.
- 앱 삭제 시 키도 함께 삭제된다.

## 5.2 보안 수준

| 수준 | 설명 |
|------|------|
| Software | 키가 OS 소프트웨어에 있음 (가장 약함) |
| TEE | 신뢰 실행 환경에서 키 보호 (대부분의 기기) |
| StrongBox | 별도 보안 칩(SE)에서 보호 (지원 기기 한정) |

```kotlin
val builder = KeyGenParameterSpec.Builder(
    alias,
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256)

// StrongBox 지원 기기에서만 시도하고, 없으면 일반 TEE로 폴백
val spec = if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
    builder.setIsStrongBoxBacked(true).build()
} else {
    builder.build()
}
```

## 5.3 AES-GCM 암복호화 구현 (복붙용)

```kotlin
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreCipher(
    private val alias: String = "app_secret_key"
) {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    /** 결과 = IV(12바이트) + 암호문(+GCM 태그) */
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, IV_SIZE)
        val body = data.copyOfRange(IV_SIZE, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(TAG_BITS, iv)
        )
        return cipher.doFinal(body)
    }

    fun deleteKey() {
        keyStore.deleteEntry(alias)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
```

## 5.4 주의점

- GCM에서 IV는 암호화마다 새로 생성되어야 한다. Keystore가 자동 생성하는 IV를 그대로 쓰고(`cipher.iv`), 같은 IV를 재사용하지 않는다.
- 암호화 모드는 ECB를 쓰지 않는다. 인증이 포함된 GCM을 기본으로 한다.
- 복호화 실패(`AEADBadTagException`, `KeyPermanentlyInvalidatedException`)는 정상 시나리오다. 기기 잠금 해제 방식 변경, 생체 정보 추가 등으로 키가 무효화될 수 있으므로 재로그인 같은 복구 경로를 반드시 마련한다.
- 일부 제조사 기기에서 Keystore 오류가 보고된다. 예외를 잡아서 "저장된 값 삭제 후 재인증"으로 떨어지게 설계한다.

<br>

# 6. 생체인증(BiometricPrompt)

## 6.1 인증 강도

| 클래스 | 의미 |
|--------|------|
| `BIOMETRIC_STRONG` (Class 3) | 강한 생체인증. 결제, 키 해제 등에 사용 가능 |
| `BIOMETRIC_WEAK` (Class 2) | 약한 생체인증. CryptoObject와 함께 쓸 수 없음 |
| `DEVICE_CREDENTIAL` | PIN/패턴/비밀번호 |

## 6.2 단순 인증 vs CryptoObject 연동

- 단순 인증: 콜백이 성공이면 앱이 "통과"로 간주한다. 후킹(Frida 등)으로 콜백만 호출해 우회할 수 있다.
- CryptoObject 연동: 생체인증 성공이 있어야만 Keystore 키가 연산에 사용된다. 우회하려면 키 자체를 얻어야 하므로 훨씬 안전하다. 민감 작업에는 이 방식을 쓴다.

## 6.3 인증이 필요한 키 만들기

```kotlin
val spec = KeyGenParameterSpec.Builder(
    "biometric_key",
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256)
    .setUserAuthenticationRequired(true)
    // API 30+: 매번 인증(0)과 허용 인증 수단 지정
    .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
    // 새 지문 등록 시 키 무효화
    .setInvalidatedByBiometricEnrollment(true)
    .build()
```

## 6.4 BiometricPrompt + CryptoObject (복붙용)

```kotlin
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

class BiometricAuthenticator(private val activity: FragmentActivity) {

    fun canAuthenticate(): Boolean {
        val result = BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        cipher: Cipher,
        title: String,
        negativeText: String,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authedCipher = result.cryptoObject?.cipher
                    if (authedCipher != null) {
                        onSuccess(authedCipher)
                    } else {
                        onError("CryptoObject 없음")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
```

- `setNegativeButtonText`는 `DEVICE_CREDENTIAL`을 허용하지 않을 때만 필요하다.
- 인증 후 전달받은 `cipher`로 `doFinal`을 호출해야 실제로 암복호화가 된다.

<br>

# 7. 네트워크 보안

## 7.1 기본 원칙

- 모든 통신은 HTTPS(TLS). 평문 HTTP는 Android 9(API 28)부터 기본 차단이다.
- 개발 편의로 `usesCleartextTraffic="true"`를 켜두고 릴리즈에 그대로 나가는 실수를 주의한다. debug 빌드에만 허용한다.
- 서버 인증서 검증을 끄는 코드(모든 인증서를 신뢰하는 `TrustManager`, `HostnameVerifier { _, _ -> true }`)는 절대 금지. Play 정책 위반 사유가 되기도 한다.

## 7.2 Network Security Config

```xml
<!-- AndroidManifest.xml -->
<application
    android:networkSecurityConfig="@xml/network_security_config" />
```

```xml
<!-- res/xml/network_security_config.xml : 릴리즈 기본 -->
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <!-- 핀닝: 만료 시점을 반드시 설정 -->
    <domain-config>
        <domain includeSubdomains="true">api.example.com</domain>
        <pin-set expiration="2027-12-31">
            <pin digest="SHA-256">AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=</pin>
            <pin digest="SHA-256">BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

```xml
<!-- src/debug/res/xml/network_security_config.xml : 디버그에서만 로컬 서버 허용 -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">10.0.2.2</domain>
    </domain-config>
    <debug-overrides>
        <trust-anchors>
            <certificates src="user" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

- `debug-overrides`는 디버그 빌드에서만 적용된다. Charles/Proxyman 같은 프록시로 트래픽을 볼 때 편하다.
- 빌드 타입별로 같은 경로에 파일을 두면 해당 빌드 타입 쪽이 우선한다.

## 7.3 인증서 핀닝

- 핀닝은 "정당한 서버의 인증서(또는 공개키)만 신뢰"하도록 좁히는 것이다. 중간자 공격(MITM)과 악성 CA에 대한 방어가 된다.
- 단점: 서버가 인증서를 교체했는데 앱의 핀이 갱신되지 않으면 앱 전체 통신이 막힌다.
- 대응: 백업 핀(차기 키) 포함, 만료일 설정, 핀 갱신을 위한 강제 업데이트 경로 확보. 서버 인증서가 아닌 중간 CA의 공개키 핀을 쓰는 방법도 있다.

OkHttp로 설정하는 방식:

```kotlin
val pinner = CertificatePinner.Builder()
    .add("api.example.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    .add("api.example.com", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
    .build()

val client = OkHttpClient.Builder()
    .certificatePinner(pinner)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()
```

Hilt 모듈에서 제공하는 형태:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .certificatePinner(
                CertificatePinner.Builder()
                    .add(BuildConfig.API_HOST, BuildConfig.API_PIN_PRIMARY)
                    .add(BuildConfig.API_HOST, BuildConfig.API_PIN_BACKUP)
                    .build()
            )

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }
        return builder.build()
    }
}
```

- 로깅 인터셉터의 `BODY` 레벨은 토큰, 개인정보가 로그에 남으므로 릴리즈에서 제거한다. 디버그에서도 `Authorization` 헤더는 마스킹한다.

```kotlin
HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.HEADERS
    redactHeader("Authorization")
    redactHeader("Cookie")
}
```

## 7.4 토큰 관리

- Access Token은 짧은 수명, Refresh Token은 Keystore로 암호화해서 저장한다.
- 401 응답 시 `Authenticator`에서 재발급을 시도하고, 실패하면 로컬 토큰을 지우고 로그인 화면으로 보낸다.
- API 키를 앱에 넣어야 한다면 "앱에 들어간 값은 결국 추출된다"는 전제로, 서버 쪽에서 사용량 제한과 앱 검증(Play Integrity, App Check)을 걸어둔다.

<br>

# 8. WebView 보안

WebView는 앱 안에 웹 공격면을 들여오는 것이다.

## 8.1 설정 원칙

```kotlin
webView.settings.apply {
    javaScriptEnabled = true          // 필요한 경우에만
    allowFileAccess = false           // file:// 접근 차단
    allowContentAccess = false
    allowFileAccessFromFileURLs = false
    allowUniversalAccessFromFileURLs = false
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    setSupportMultipleWindows(false)
}
```

- `javaScriptEnabled`는 필요한 화면에서만 켠다.
- 신뢰할 수 없는 콘텐츠를 로드하는 WebView에는 `addJavascriptInterface`를 쓰지 않는다.

## 8.2 JavascriptInterface

- `@JavascriptInterface`가 붙은 메서드만 JS에서 호출 가능하다 (API 17+).
- 그래도 노출한 메서드는 "웹 페이지가 호출 가능한 API"다. 호출 출처가 신뢰 가능한 도메인인지 확인하고, 인자를 검증한다.
- 토큰, 개인정보를 JS로 직접 넘기지 않는다.

```kotlin
class WebBridge(private val onShare: (String) -> Unit) {

    @JavascriptInterface
    fun share(text: String) {
        // 입력 길이/형식 검증
        if (text.length > 500) return
        onShare(text)
    }
}
```

## 8.3 URL 화이트리스트

```kotlin
webView.webViewClient = object : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        val uri = request.url
        val allowed = uri.scheme == "https" && uri.host in ALLOWED_HOSTS
        if (!allowed) {
            // 외부 브라우저로 넘기거나 차단
            return true
        }
        return false
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError
    ) {
        handler.cancel()   // proceed() 호출 금지
    }
}
```

- `onReceivedSslError`에서 `handler.proceed()`를 호출하는 코드는 대표적인 취약 패턴이다. Play Console에서 경고가 뜬다.
- Google Play Protect와 Play Console은 이런 패턴을 정적 분석으로 잡아낸다.

## 8.4 대안

- 단순히 외부 페이지를 보여주려면 Custom Tabs(`androidx.browser`)가 안전하다. 브라우저의 보안 기능, 쿠키, 자동완성을 그대로 쓰고 앱이 페이지 내용에 접근하지 않는다.

<br>

# 9. 화면·로그·클립보드 유출 방지

## 9.1 화면 캡처/녹화 방지

민감한 화면(결제, 인증, 개인정보)에는 `FLAG_SECURE`를 설정한다. 스크린샷, 화면 녹화, 최근 앱 미리보기에서 내용이 가려진다.

```kotlin
// Activity 단위
window.setFlags(
    WindowManager.LayoutParams.FLAG_SECURE,
    WindowManager.LayoutParams.FLAG_SECURE
)
```

Compose에서 화면 단위로 적용:

```kotlin
@Composable
fun SecureScreen(content: @Composable () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    content()
}
```

## 9.2 로그

- `Log.d`, `Log.v`에 토큰, 비밀번호, 개인정보를 출력하지 않는다.
- 릴리즈에서는 R8로 로그 호출을 제거하거나, Timber에 릴리즈용 Tree를 심어서 민감 레벨을 걸러낸다.

```proguard
# proguard-rules.pro : 릴리즈에서 Log.d/v/i 제거
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
```

## 9.3 클립보드

- 비밀번호 입력 필드에서 복사/붙여넣기 메뉴를 제한한다.
- Android 13+에서는 민감 콘텐츠를 클립에 넣을 때 표시 마스킹 플래그를 지정한다.

```kotlin
val clip = ClipData.newPlainText("otp", code).apply {
    description.extras = PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
}
clipboardManager.setPrimaryClip(clip)
```

## 9.4 키보드 / 자동완성

- 비밀번호 필드는 `inputType`을 `textPassword`로 지정해 키보드 학습/제안에 남지 않도록 한다.
- Compose에서는 `PasswordVisualTransformation`과 `KeyboardType.Password`를 사용한다.

```kotlin
OutlinedTextField(
    value = password,
    onValueChange = { password = it },
    visualTransformation = PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Password,
        autoCorrectEnabled = false
    )
)
```

<br>

# 10. 코드 보호와 무결성 검증

## 10.1 R8 난독화와 축소

- 릴리즈에 `isMinifyEnabled = true`, `isShrinkResources = true`.
- 클래스/메서드 이름이 짧아져서 리버스 엔지니어링 난이도가 올라간다. 단, 난독화는 "읽기 어렵게" 만들 뿐 보호가 아니다. 비밀 값을 넣어도 된다는 뜻이 아니다.
- 리플렉션, 직렬화(Gson 등), JNI를 쓰는 클래스는 keep 규칙이 필요하다. kotlinx.serialization, Retrofit, Room은 라이브러리가 consumer rule을 제공한다.
- 릴리즈마다 생성되는 `mapping.txt`를 보관하고 Play Console / Crashlytics에 업로드해야 스택트레이스를 복원할 수 있다.

```kotlin
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

## 10.2 비밀 값 관리

| 방법 | 평가 |
|------|------|
| 소스에 하드코딩 | 금지. 저장소에 올라가고 APK에서 바로 추출됨 |
| `local.properties` + `BuildConfig` | 저장소 유출은 막지만 APK에서는 추출 가능 |
| NDK(C++)에 숨기기 | 난이도만 올라갈 뿐 추출 가능 |
| 서버가 보유하고 앱은 단기 토큰만 | 가장 안전. 가능하면 이쪽 |

- 서드파티 API 키(지도 등)는 패키지명 + 서명 SHA-1 제한을 콘솔에서 반드시 설정한다.
- `google-services.json`, 키스토어 파일, `.env`는 `.gitignore`에 넣는다. 이미 커밋했다면 이력까지 지우고 키를 폐기(rotate)한다.

## 10.3 루팅/변조 탐지와 Play Integrity API

- 로컬 루팅 탐지(su 바이너리 확인 등)는 우회가 쉽다. 신뢰의 근거로 쓰면 안 되고 보조 수단이다.
- 권장 방식: Play Integrity API로 서버에서 검증한다.
  - 기기 무결성: 정상 인증된 Android 기기인가
  - 앱 무결성: Play에서 배포된 변조되지 않은 앱인가
  - 계정 상태: Play에서 정상 라이선스를 가진 계정인가
- 앱은 서버가 발급한 nonce를 담아 토큰을 요청하고, 그 토큰을 서버로 보내 서버가 Google API로 판정을 해독한다. 판정 검증을 클라이언트에서 하면 의미가 없다.

흐름:

1. 서버가 요청별 nonce를 생성해 앱에 전달
2. 앱이 nonce로 Integrity 토큰 요청
3. 앱이 토큰을 서버로 전송
4. 서버가 Google 서버로 토큰 해독 요청 후 판정 확인
5. 판정에 따라 민감 API 허용 또는 거부

```kotlin
class IntegrityChecker(private val context: Context) {

    suspend fun requestToken(nonce: String): String {
        val manager = IntegrityManagerFactory.create(context)
        val response = manager.requestIntegrityToken(
            IntegrityTokenRequest.builder()
                .setNonce(nonce)
                .build()
        ).await()   // kotlinx-coroutines-play-services
        return response.token()
    }
}
```

- Standard 요청(사전 준비 방식)은 지연이 적어서 자주 호출되는 API에 적합하고, Classic 요청은 민감 작업 1회성에 적합하다. 호출량 한도가 있으므로 모든 요청마다 호출하지 않는다.

## 10.4 디버그/에뮬레이터 대응

- `android:debuggable`은 릴리즈에서 false가 기본이다. 수동으로 켜지 않는다.
- 릴리즈에서 `BuildConfig.DEBUG` 분기에 숨겨둔 테스트 계정, 우회 로직, 관리자 메뉴가 남아 있지 않은지 확인한다.

<br>

# 11. 의존성과 빌드 보안

## 11.1 공급망

- 라이브러리는 신뢰 가능한 저장소(Google, Maven Central)에서만 받는다. 출처 불명의 JitPack, 개인 저장소는 최소화한다.
- 버전을 `+`나 `latest`로 두지 않고 Version Catalog(`libs.versions.toml`)로 고정한다.
- Gradle Dependency Verification(`gradle/verification-metadata.xml`)으로 아티팩트 체크섬을 검증할 수 있다.
- 알려진 취약점은 Dependabot, Renovate, `dependencyCheck` 플러그인(OWASP)으로 주기적으로 점검한다.

```bash
# 의존성 검증 메타데이터 생성
./gradlew --write-verification-metadata sha256 help
```

## 11.2 CI/CD

- 서명 키, API 시크릿은 CI의 Secret 저장소(GitHub Actions Secrets 등)에 보관한다. 로그에 출력되지 않도록 마스킹 상태를 확인한다.
- PR에서는 시크릿에 접근하지 못하게 한다 (포크 PR 악용 방지).
- 릴리즈 빌드는 재현 가능하도록 환경(JDK, AGP 버전)을 고정한다.

## 11.3 targetSdk 관리

- 최신 `targetSdkVersion`으로 올릴수록 새 보안 정책(암시적 Intent 제한, 백그라운드 제한, 권한 모델 강화)이 적용된다.
- Google Play는 일정 기준 이하의 targetSdk 앱의 신규 등록/업데이트를 막는다. 매년 정책 변경 일정을 확인한다.

<br>

# 12. OWASP MASVS 체크리스트

OWASP MASVS(Mobile Application Security Verification Standard)는 모바일 앱 보안 검증 기준이고, MASTG는 테스트 가이드다. 자체 점검 용도로 쓰기 좋다.

| 영역 | 점검 질문 |
|------|-----------|
| STORAGE | 민감 데이터가 평문으로 저장되는가? 백업/로그/캐시로 유출되는가? |
| CRYPTO | 검증된 알고리즘(AES-GCM 등)을 쓰는가? 키를 하드코딩하지 않았는가? |
| AUTH | 인증 결과를 서버가 검증하는가? 생체인증이 CryptoObject에 연결되는가? |
| NETWORK | 모든 통신이 TLS인가? 인증서 검증을 끄지 않았는가? 핀닝 정책이 있는가? |
| PLATFORM | exported 컴포넌트, 딥링크, WebView, PendingIntent가 안전한가? |
| CODE | 릴리즈가 R8 처리되었는가? 디버그 코드와 비밀 값이 남지 않았는가? |
| RESILIENCE | 변조/루팅 상황을 서버와 함께 판단하는가? (선택) |
| PRIVACY | 수집하는 개인정보가 최소인가? 동의와 삭제 경로가 있는가? |

## 12.1 릴리즈 전 빠른 점검

1. `AndroidManifest.xml`에서 `exported="true"` 컴포넌트가 의도된 것뿐인지
2. `allowBackup`과 백업 제외 규칙
3. `usesCleartextTraffic`과 Network Security Config
4. WebView의 `proceed()`, `allowFileAccess`, `addJavascriptInterface`
5. 로그 출력과 HTTP 로깅 레벨
6. 하드코딩된 키, 토큰, 테스트 계정
7. R8 활성화와 mapping 파일 업로드
8. 민감 화면의 `FLAG_SECURE`
9. 의존성 취약점 스캔 결과

<br>

# 정리

1. Android 보안은 커널 격리, SELinux, 앱 샌드박스, 권한, 서명이 겹쳐진 구조이고, 루팅된 기기에서는 격리가 깨질 수 있어 "기기가 안전하다"는 가정에 기대지 않는다.
2. 권한은 최소로 요청하고, Photo Picker나 SAF처럼 권한이 필요 없는 대안을 먼저 검토한다.
3. 컴포넌트는 기본이 `exported="false"`이며, 외부 진입점은 Intent 입력을 화이트리스트와 타입 변환으로 검증하고, PendingIntent는 `FLAG_IMMUTABLE`을 기본으로 한다.
4. 민감 데이터는 Keystore 기반 AES-GCM으로 암호화해서 저장한다. `EncryptedSharedPreferences`는 deprecated이므로 신규 코드에서는 Keystore 직접 사용이나 Tink를 쓴다.
5. 생체인증은 콜백 성공 여부만 믿지 말고, `setUserAuthenticationRequired` 키와 `CryptoObject`로 연결해야 우회가 어렵다.
6. 네트워크는 TLS 고정, 릴리즈 평문 차단, 핀닝(백업 핀과 만료일 포함), 로깅 시 민감 헤더 마스킹이 기본이다.
7. WebView는 URL 화이트리스트, SSL 오류 `cancel()`, 불필요한 파일 접근 차단이 핵심이고, 단순 페이지 표시는 Custom Tabs가 안전하다.
8. 화면, 로그, 클립보드는 `FLAG_SECURE`, 릴리즈 로그 제거, 민감 클립 플래그로 유출 경로를 막는다.
9. R8 난독화는 비밀 보호가 아니라 분석 지연 수단이다. 비밀 값은 서버가 쥐고, 앱 변조/기기 상태는 Play Integrity 판정을 서버에서 검증한다.
10. 의존성 고정, 취약점 스캔, CI 시크릿 관리와 MASVS 체크리스트를 릴리즈 전 루틴으로 만든다.
