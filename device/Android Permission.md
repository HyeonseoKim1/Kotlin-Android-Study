# Android Permission

목차
1. Permission이란?
2. Permission이 필요한 이유
3. Normal Permission과 Dangerous Permission
4. Signature Permission과 Special Permission
5. 매니페스트에서의 권한 선언
6. 런타임 권한 요청의 기본 흐름
7. ActivityResultContracts를 이용한 권한 요청
8. shouldShowRequestPermissionRationale()
9. 권한 그룹과 관련 권한 자동 승인
10. Android 버전별 권한 정책 변화
11. 위치 권한(Location Permission)의 세분화
12. 미디어 권한의 세분화 (Android 13+)
13. 알림 권한(POST_NOTIFICATIONS)
14. 정확한 알람과 특수 권한
15. 권한 거부와 영구 거부 처리
16. 다중 권한 요청과 Hilt/Compose에서의 처리
17. 실전에서 사용하는 권한 처리 패턴
18. 주의사항과 자주 하는 실수
19. 정리

<br>

## 1. Permission이란?

Permission은 앱이 사용자의 민감한 데이터(위치, 연락처, 카메라, 마이크 등)나 시스템 리소스에 접근하려 할 때, 사용자의 동의를 거치도록 강제하는 Android의 보안 모델이다. 앱은 매니페스트에 사용하려는 권한을 선언하고, 일부 권한은 실행 중에 사용자에게 직접 승인을 요청해야 한다.

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

Permission 시스템의 목적은 명확하다. 사용자가 자신의 정보와 기기 자원이 어떻게 사용되는지 인지하고 통제할 수 있게 하는 것이다. 개발자 입장에서는 매니페스트 선언만으로 끝나던 시절(Android 5.1 이하)과 달리, 지금은 권한 요청 흐름을 코드로 직접 설계해야 하는 경우가 대부분이다.

권한은 크게 "선언"과 "요청"이라는 두 단계로 나뉜다.

1. 선언: `AndroidManifest.xml`에 어떤 권한이 필요한지 명시
2. 요청: 위험 권한(Dangerous Permission)의 경우, 런타임에 사용자에게 직접 승인을 요청

이 두 단계 중 하나라도 빠지면 권한 관련 기능이 정상 동작하지 않으므로, Android Permission을 다룰 때는 항상 이 두 층위를 함께 고려해야 한다.

<br>

## 2. Permission이 필요한 이유

스마트폰에는 위치, 카메라, 마이크, 연락처, 파일 등 사용자의 매우 민감한 정보와 하드웨어가 집약되어 있다. 만약 앱이 아무 제약 없이 이 모든 것에 접근할 수 있다면, 악성 앱이 사용자 몰래 위치를 추적하거나 대화를 녹음하는 등의 심각한 문제가 발생할 수 있다.

Permission 시스템은 다음과 같은 목표를 달성하기 위해 존재한다.

- 사용자가 앱이 어떤 데이터에 접근하는지 명확히 인지하게 한다.
- 민감한 접근은 반드시 사용자의 명시적 동의를 거치게 한다.
- 사용자가 언제든 부여한 권한을 철회할 수 있게 한다.
- 앱이 필요 이상으로 과도한 권한을 요구하지 못하도록 정책적으로 제한한다.

Android 6.0(API 23) 이전에는 앱을 설치하는 시점에 필요한 모든 권한을 한꺼번에 보여주고 동의를 받는 방식(Install-time Permission)이었다. 이 방식은 사용자가 "설치하려면 다 동의해야 하는" 구조였기 때문에, 실질적으로 선택권이 크지 않았다. Android 6.0부터는 위험 권한을 실제로 사용하는 시점에 개별적으로 요청하는 런타임 권한(Runtime Permission) 모델로 전환되어, 사용자가 훨씬 세밀하게 통제할 수 있게 되었다.

<br>

## 3. Normal Permission과 Dangerous Permission

Android 권한은 보호 수준(Protection Level)에 따라 나뉜다. 그중 가장 기본이 되는 두 가지가 Normal과 Dangerous다.

Normal Permission
- 사용자의 프라이버시나 다른 앱의 동작에 위험이 낮은 권한
- 매니페스트에 선언만 하면 설치 시 자동으로 부여됨, 런타임 요청 불필요
- 예: `INTERNET`, `ACCESS_NETWORK_STATE`, `VIBRATE`, `SET_ALARM`

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Dangerous Permission
- 사용자의 개인정보나 기기 동작에 직접적인 영향을 줄 수 있는 권한
- 매니페스트 선언에 더해, 런타임에 사용자의 명시적 승인이 필요함
- 예: `CAMERA`, `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`, `READ_CONTACTS`

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

```kotlin
if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
    != PackageManager.PERMISSION_GRANTED
) {
    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
}
```

Dangerous Permission은 다시 여러 권한 그룹(Permission Group)으로 묶여 있다. 예를 들어 `READ_CONTACTS`와 `WRITE_CONTACTS`는 모두 `CONTACTS` 그룹에 속한다. Android 6.0~7.1까지는 같은 그룹 내 권한 하나가 승인되면 같은 그룹의 다른 권한도 자동 승인되는 방식이었지만, Android 8.0 이후부터는 그룹 내에서도 각 권한을 개별적으로 요청하고 확인해야 한다.

<br>

## 4. Signature Permission과 Special Permission

Dangerous/Normal 외에도 특수한 목적의 권한 카테고리가 존재한다.

Signature Permission
- 권한을 정의한 앱과 동일한 서명(Signing Key)으로 서명된 앱에만 자동으로 부여되는 권한
- 주로 같은 개발사에서 만든 여러 앱 간에 데이터를 공유하거나 협업할 때 사용
- 사용자에게 별도로 노출되지 않음

```xml
<permission
    android:name="com.impactus.chagok.permission.ACCESS_INTERNAL_API"
    android:protectionLevel="signature" />
```

Special Permission (특수 권한)
- 매우 민감하거나 시스템 전반에 큰 영향을 줄 수 있어, 일반적인 런타임 요청 다이얼로그가 아니라 별도의 설정 화면을 통해서만 부여할 수 있는 권한
- 대표적으로 다음과 같은 것들이 있다.

| 권한 | 설명 | 요청 방식 |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | 다른 앱 위에 그리기(오버레이) | `ACTION_MANAGE_OVERLAY_PERMISSION` 설정 화면 |
| `WRITE_SETTINGS` | 시스템 설정 변경 | `ACTION_MANAGE_WRITE_SETTINGS` 설정 화면 |
| `MANAGE_EXTERNAL_STORAGE` | 모든 파일에 대한 접근(Android 11+) | `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION` |
| `SCHEDULE_EXACT_ALARM` | 정확한 알람 예약(Android 12+) | `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` |

```kotlin
fun requestOverlayPermission(context: Context) {
    if (!Settings.canDrawOverlays(context)) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }
}
```

이런 특수 권한들은 일반 런타임 권한 다이얼로그(`requestPermissions()`)로는 요청할 수 없고, 반드시 명시적으로 설정 화면 Intent를 띄워 사용자가 직접 토글을 켜도록 유도해야 한다. 또한 스토어 정책상 특수 권한 사용 목적을 명확히 소명해야 하는 경우가 많다.

<br>

## 5. 매니페스트에서의 권한 선언

모든 권한(Normal이든 Dangerous든)은 `AndroidManifest.xml`에 `<uses-permission>` 태그로 선언되어야 한다. 이 선언이 없으면 런타임 요청 자체가 불가능하고, 접근 시도 시 `SecurityException`이 발생한다.

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application>
        ...
    </application>
</manifest>
```

특정 하드웨어 기능이 필수인지 선택인지도 함께 명시할 수 있다. `<uses-feature>`에서 `required="false"`로 설정하면, 해당 하드웨어가 없는 기기에서도 스토어에 노출되도록 할 수 있다.

```xml
<uses-feature
    android:name="android.hardware.camera"
    android:required="false" />
```

여러 모듈로 구성된 프로젝트(멀티 모듈)에서는 각 모듈의 매니페스트에 권한이 흩어져 선언될 수 있는데, 최종 빌드 시 매니페스트 병합(Manifest Merger) 과정을 통해 하나로 합쳐진다. 이 과정에서 중복 선언이나 충돌이 발생하면 빌드 시 경고나 에러로 확인할 수 있다.

targetSdkVersion에 따라 요구되는 권한이 달라지는 경우도 있으므로, 앱이 타겟하는 API 레벨과 실제 사용하는 기능이 요구하는 권한 목록을 항상 함께 점검해야 한다.

<br>

## 6. 런타임 권한 요청의 기본 흐름

Dangerous Permission은 매니페스트 선언만으로는 부족하고, 실제 기능을 사용하는 시점에 다음과 같은 흐름으로 런타임 요청을 거쳐야 한다.

1. 권한이 이미 부여되어 있는지 확인 (`checkSelfPermission()`)
2. 부여되어 있지 않다면 요청 (`requestPermissions()` 또는 Activity Result API)
3. 사용자의 응답(허용/거부) 결과를 콜백으로 수신
4. 결과에 따라 기능을 실행하거나, 거부 시 대체 UX 제공

```kotlin
fun checkAndRequestPermission(
    context: Context,
    permission: String,
    launcher: ActivityResultLauncher<String>
) {
    when {
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> {
            // 이미 권한이 있음 → 바로 기능 실행
        }
        else -> {
            launcher.launch(permission)
        }
    }
}
```

핵심은 "권한이 필요한 매 순간마다 확인해야 한다"는 점이다. 사용자는 시스템 설정에서 언제든 권한을 취소할 수 있으므로, 이전에 승인받았다고 해서 계속 유효하다고 가정하면 안 된다. 특히 녹음, 촬영처럼 반복적으로 실행되는 기능이라면 매 실행 시점마다 `checkSelfPermission()`으로 재확인하는 것이 안전하다.

<br>

## 7. ActivityResultContracts를 이용한 권한 요청

과거에는 `Activity.requestPermissions()`와 `onRequestPermissionsResult()` 콜백을 직접 오버라이드하는 방식이 표준이었지만, 현재는 `ActivityResultContracts`를 사용하는 것이 권장되는 방식이다. 코드가 더 명확하고, Fragment나 Compose에서도 일관되게 사용할 수 있다.

단일 권한 요청

```kotlin
class RecordingActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startRecording()
            } else {
                showPermissionDeniedMessage()
            }
        }

    private fun checkAndStartRecording() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startRecording()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}
```

다중 권한 요청

```kotlin
private val requestMultiplePermissionsLauncher =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            startVideoCall()
        } else {
            val deniedPermissions = results.filterValues { !it }.keys
            showDeniedPermissionsMessage(deniedPermissions)
        }
    }

private fun requestCallPermissions() {
    requestMultiplePermissionsLauncher.launch(
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    )
}
```

Jetpack Compose 환경에서는 `rememberLauncherForActivityResult()`를 사용해 동일한 패턴을 Composable 내부에서 선언적으로 작성할 수 있다.

```kotlin
@Composable
fun RecordingScreen() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    Button(onClick = {
        if (hasPermission) {
            // 녹음 시작
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }) {
        Text("녹음 시작")
    }
}
```

<br>

## 8. shouldShowRequestPermissionRationale()

사용자가 권한 요청을 한 번 거부하면, 다음에 다시 요청했을 때 시스템 다이얼로그에 "다시 묻지 않음" 체크박스가 함께 표시된다. `shouldShowRequestPermissionRationale()`은 현재 이 상태에 있는지, 즉 "사용자에게 왜 이 권한이 필요한지 설명하면 도움이 될 시점"인지를 판단하는 데 사용된다.

```kotlin
fun handlePermissionRequest(activity: Activity, permission: String, launcher: ActivityResultLauncher<String>) {
    when {
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED -> {
            // 이미 승인됨
        }
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) -> {
            // 이전에 한 번 거부한 적이 있음 → 이유를 설명하는 다이얼로그를 먼저 보여준 뒤 재요청
            showRationaleDialog {
                launcher.launch(permission)
            }
        }
        else -> {
            // 처음 요청하거나, "다시 묻지 않음"으로 완전히 거부된 상태
            launcher.launch(permission)
        }
    }
}
```

이 함수의 반환값이 `false`인 경우는 두 가지 상황을 모두 포함한다는 점에 유의해야 한다.

1. 아직 한 번도 권한을 요청한 적이 없는 경우
2. 사용자가 "다시 묻지 않음"과 함께 거부해서, 시스템이 더 이상 요청 다이얼로그 자체를 보여주지 않기로 한 경우

두 상황을 구분하려면 앱에서 직접 "이 권한을 몇 번 요청했는지"를 `SharedPreferences` 등에 기록해두는 방식이 흔히 쓰인다. 완전히 거부된 상태(2번)라면 시스템 다이얼로그가 아예 뜨지 않으므로, 이 경우에는 설정 화면으로 안내하는 UX가 필요하다.

```kotlin
fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}
```

<br>

## 9. 권한 그룹과 관련 권한 자동 승인

Android 8.0 이전에는 하나의 권한 그룹(Permission Group) 내에서 권한 하나가 승인되면, 같은 그룹의 다른 권한도 사용자 개입 없이 자동으로 부여되었다. 예를 들어 `READ_CONTACTS`가 승인되면 같은 `CONTACTS` 그룹의 `WRITE_CONTACTS`도 자동 승인되는 식이었다.

Android 8.0(API 26) 이후부터는 이 동작이 변경되어, 같은 그룹에 속한 권한이라도 각각 개별적으로 요청되고 승인 상태도 개별로 관리된다. 다만 UI 다이얼로그 자체는 여전히 그룹 단위의 문구를 보여줄 수 있다.

대표적인 권한 그룹은 다음과 같다.

| 그룹 | 포함 권한 예시 |
|---|---|
| `LOCATION` | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` |
| `CAMERA` | `CAMERA` |
| `MICROPHONE` | `RECORD_AUDIO` |
| `CONTACTS` | `READ_CONTACTS`, `WRITE_CONTACTS`, `GET_ACCOUNTS` |
| `STORAGE` | `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` (API 32 이하) |
| `CALENDAR` | `READ_CALENDAR`, `WRITE_CALENDAR` |
| `SMS` | `SEND_SMS`, `READ_SMS`, `RECEIVE_SMS` 등 |

`STORAGE` 그룹은 Android 13(API 33)부터 세분화된 미디어 권한(`READ_MEDIA_IMAGES` 등)으로 대체되었기 때문에, 최신 프로젝트에서는 타겟 SDK 버전에 따라 어떤 권한을 요청해야 하는지가 달라진다는 점을 항상 함께 고려해야 한다.

<br>

## 10. Android 버전별 권한 정책 변화

권한 정책은 Android 버전이 올라갈수록 점점 더 세분화되고 엄격해지는 방향으로 발전해왔다.

Android 6.0 (API 23)
- 런타임 권한 모델 도입. Dangerous Permission은 실행 중 개별 요청 필요

Android 8.0 (API 26)
- 같은 권한 그룹 내에서도 권한별 개별 승인 상태 관리

Android 10 (API 29)
- 위치 권한이 "앱 사용 중에만 허용" 옵션으로 세분화 시작
- 백그라운드에서의 화면/오디오 접근에 대한 제약 강화

Android 11 (API 30)
- 위치 권한에 "한 번만 허용(One-time permission)" 옵션 추가
- 오랫동안 사용하지 않은 앱의 권한을 시스템이 자동으로 회수(Auto-reset)

Android 12 (API 31)
- 대략적 위치(`ACCESS_COARSE_LOCATION`)와 정확한 위치(`ACCESS_FINE_LOCATION`)를 사용자가 선택적으로 부여 가능
- 마이크/카메라 접근 상태를 상태 표시줄 인디케이터로 실시간 표시

Android 13 (API 33)
- `READ_EXTERNAL_STORAGE`가 `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`로 세분화
- `POST_NOTIFICATIONS`가 런타임 권한으로 전환

Android 14 (API 34)
- 부분적 미디어 접근 권한(`READ_MEDIA_VISUAL_USER_SELECTED`) 도입: 사용자가 특정 사진/동영상만 선택적으로 공유 가능
- 정확한 알람 권한 정책 세분화

이 흐름의 공통된 방향성은 "필요한 최소한의 정보만, 필요한 시점에만, 사용자가 원하는 범위에서" 접근하도록 유도하는 것이다. 최신 targetSdkVersion을 사용할수록 이런 세분화된 정책의 적용을 받게 되므로, 앱을 업데이트할 때마다 권한 요청 로직도 함께 점검해야 한다.

<br>

## 11. 위치 권한(Location Permission)의 세분화

위치 권한은 Android 권한 중에서도 가장 복잡하게 세분화된 영역이다.

기본 위치 권한

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

Android 10부터는 "앱을 사용하는 동안만" 허용할지, "항상 허용"할지를 사용자가 선택할 수 있게 되었다. 백그라운드에서도 위치가 필요한 앱(내비게이션의 백그라운드 추적 등)이라면 별도의 권한을 추가로 요청해야 한다.

```xml
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
```

```kotlin
fun requestBackgroundLocationIfNeeded(activity: Activity, launcher: ActivityResultLauncher<String>) {
    // 반드시 foreground 위치 권한이 먼저 승인된 상태에서 요청해야 함
    if (ContextCompat.checkSelfPermission(
            activity, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        launcher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
}
```

백그라운드 위치 권한은 별도의 다이얼로그가 아니라, Android 11부터는 시스템 설정 화면으로 직접 연결되는 경우가 많다. 즉 "항상 허용" 옵션이 앱 내 다이얼로그가 아니라 설정 앱의 위치 권한 화면에서만 선택 가능하도록 강제된다.

Android 12부터는 정확한 위치와 대략적인 위치를 사용자가 선택할 수 있는 다이얼로그가 추가되었다. 두 권한을 함께 요청하면, 사용자는 "정확한 위치 허용", "대략적 위치만 허용", "거부" 중 하나를 고를 수 있다.

```kotlin
val locationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

requestMultiplePermissionsLauncher.launch(locationPermissions)
```

결과를 처리할 때는 두 권한의 승인 여부를 각각 확인해서, 앱의 요구 수준(정확한 위치가 꼭 필요한지, 대략적 위치로도 충분한지)에 맞게 분기 처리해야 한다.

<br>

## 12. 미디어 권한의 세분화 (Android 13+)

Android 13(API 33)부터 기존의 포괄적인 `READ_EXTERNAL_STORAGE` 권한이 미디어 유형별로 세분화되었다.

```xml
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
```

앱이 사진만 필요하다면 `READ_MEDIA_IMAGES`만 요청하면 되고, 굳이 오디오나 비디오 접근 권한까지 함께 요청할 필요가 없다. 이는 "필요한 최소 권한만 요청한다"는 원칙(Principle of Least Privilege)을 시스템 차원에서 강제하는 방향이다.

Android 14(API 34)부터는 한 걸음 더 나아가, 사용자가 사진첩 전체가 아니라 특정 사진/동영상만 선택적으로 공유할 수 있는 "부분 접근" 권한이 추가되었다.

```xml
<uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />
```

```kotlin
val mediaPermissions = if (Build.VERSION.SDK_INT >= 34) {
    arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
} else if (Build.VERSION.SDK_INT >= 33) {
    arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
} else {
    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}
```

이 부분 접근 권한이 부여된 경우, 앱은 사용자가 명시적으로 선택한 사진/동영상에만 접근할 수 있다. 사용자가 나중에 더 많은 파일을 공유하고 싶다면, 다시 선택 UI를 띄워야 한다. 이런 세분화 때문에, 사진/미디어 관련 기능을 만들 때는 API 레벨별 분기 처리가 사실상 필수가 되었다.

한편 앱 전용 디렉토리(`getExternalFilesDir()` 등)에 대한 접근은 별도의 권한 없이 항상 가능하므로, 다른 앱과 공유할 필요가 없는 파일이라면 굳이 미디어 권한을 요청하지 않고 앱 전용 저장소를 사용하는 것이 더 간단하고 안전한 선택이다.

<br>

## 13. 알림 권한(POST_NOTIFICATIONS)

Android 13(API 33) 이전까지는 알림 표시에 별도의 런타임 권한이 필요하지 않았다. 매니페스트 선언조차 필요 없었다. 하지만 Android 13부터는 `POST_NOTIFICATIONS`가 Dangerous Permission으로 분류되어, 다른 위험 권한과 동일한 런타임 요청 절차를 거쳐야 한다.

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

```kotlin
private val notificationPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            // 권한이 없어도 앱은 정상 동작해야 하며, 알림 관련 기능만 제한됨을 안내
        }
    }

fun requestNotificationPermissionIfNeeded(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    if (ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
```

이 권한은 Foreground Service를 사용하는 앱에서 특히 중요하다. 권한이 없어도 Foreground Service 자체는 시작될 수 있지만, Notification이 사용자에게 표시되지 않기 때문에 사용자는 앱이 왜 계속 실행 중인지 알 수 없는 상태가 된다. 따라서 Foreground Service를 사용하는 기능(녹음, 재생 등)을 시작하기 전에는 알림 권한도 함께 확인하는 것이 바람직하다.

권한을 요청하는 시점도 중요한 설계 요소다. 앱을 처음 켜자마자 다짜고짜 요청하기보다는, 실제로 알림이 필요한 기능(예: 녹음 시작 버튼)을 사용자가 누르는 시점에 맥락과 함께 요청하는 것이 승인율을 높이는 데 도움이 된다.

<br>

## 14. 정확한 알람과 특수 권한

AlarmManager의 정확한 알람 API(`setExact()`, `setExactAndAllowWhileIdle()`)를 사용하려면 Android 12(API 31)부터 `SCHEDULE_EXACT_ALARM` 권한이 필요하다. 이 권한은 일반적인 런타임 다이얼로그가 아니라 특수 권한(Special Permission)에 속하며, 별도의 설정 화면을 통해서만 부여할 수 있다.

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

```kotlin
fun requestExactAlarmPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (!alarmManager.canScheduleExactAlarms()) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }
}
```

이 외에도 카메라 위 오버레이를 그리는 `SYSTEM_ALERT_WINDOW`, 모든 파일에 접근하는 `MANAGE_EXTERNAL_STORAGE` 등 여러 특수 권한이 존재한다. 이런 권한들의 공통점은 다음과 같다.

- 일반적인 `requestPermissions()` 다이얼로그로는 요청할 수 없다.
- 사용자가 시스템 설정 화면에서 직접 토글을 켜야 한다.
- 앱 스토어 정책상 사용 목적을 명확히 소명해야 하는 경우가 많다.
- 승인 여부를 앱에서 직접 폴링하거나, `onResume()` 시점에 재확인해야 한다.

```kotlin
override fun onResume() {
    super.onResume()
    val alarmManager = getSystemService(AlarmManager::class.java)
    updateExactAlarmUiState(alarmManager.canScheduleExactAlarms())
}
```

특수 권한은 일반 위험 권한보다 훨씬 신중하게 사용해야 하며, 정말로 필요한 기능인지 먼저 검토하는 것이 좋다. 예를 들어 "정확한 알람"이 꼭 필요하지 않다면 `setWindow()`나 WorkManager로 대체해 특수 권한 요청 자체를 피하는 것도 좋은 전략이다.

<br>

## 15. 권한 거부와 영구 거부 처리

사용자의 권한 응답은 크게 세 가지 상태로 나눌 수 있다.

1. 승인(Granted): 정상적으로 기능 실행
2. 거부(Denied, 재요청 가능): 이유를 설명하고 다시 요청할 수 있는 상태
3. 영구 거부(Denied, "다시 묻지 않음"): 시스템 다이얼로그로는 더 이상 요청할 수 없는 상태

```kotlin
fun handlePermissionResult(
    activity: Activity,
    permission: String,
    isGranted: Boolean
) {
    when {
        isGranted -> {
            proceedWithFeature()
        }
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) -> {
            // 아직 재요청 가능한 상태
            showRationaleThenRetry(permission)
        }
        else -> {
            // 영구 거부 상태로 추정 → 설정 화면 안내
            showGoToSettingsDialog()
        }
    }
}
```

앞서 다룬 것처럼, `shouldShowRequestPermissionRationale()`이 `false`를 반환하는 상황은 "아직 한 번도 요청한 적 없음"과 "영구 거부됨"을 구분하지 못한다. 이를 명확히 구분하려면 앱에서 요청 횟수를 직접 추적하는 것이 일반적이다.

```kotlin
object PermissionTracker {
    private const val PREFS_NAME = "permission_tracker"

    fun recordRequest(context: Context, permission: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(permission, 0)
        prefs.edit().putInt(permission, count + 1).apply()
    }

    fun hasRequestedBefore(context: Context, permission: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(permission, 0) > 0
    }
}
```

영구 거부 상태로 판단되면, 시스템 다이얼로그 대신 앱 자체 UI로 "이 기능을 사용하려면 설정에서 권한을 허용해주세요"라는 안내와 함께 설정 화면으로 이동하는 버튼을 제공하는 것이 일반적인 패턴이다.

```kotlin
fun showGoToSettingsDialog(context: Context) {
    AlertDialog.Builder(context)
        .setTitle("권한이 필요합니다")
        .setMessage("녹음 기능을 사용하려면 설정에서 마이크 권한을 허용해주세요")
        .setPositiveButton("설정으로 이동") { _, _ -> openAppSettings(context) }
        .setNegativeButton("취소", null)
        .show()
}
```

Android 11부터는 사용자가 특정 권한을 오랫동안 사용하지 않으면 시스템이 자동으로 권한을 회수하는 기능(Permission Auto-reset)도 있으므로, 오랜만에 실행된 앱이라면 이전에 승인받았던 권한도 다시 확인하는 것이 안전하다.

<br>

## 16. 다중 권한 요청과 Hilt/Compose에서의 처리

여러 권한이 동시에 필요한 기능(예: 화상 통화에 카메라+마이크)에서는 `RequestMultiplePermissions()` 계약을 사용해 한 번에 요청하고, 결과를 개별적으로 처리하는 것이 일반적이다.

```kotlin
@Composable
fun VideoCallScreen(viewModel: VideoCallViewModel = hiltViewModel()) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val cameraGranted = results[Manifest.permission.CAMERA] == true
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true
        viewModel.onPermissionResult(cameraGranted, micGranted)
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (uiState) {
        is VideoCallUiState.PermissionDenied -> PermissionDeniedContent()
        is VideoCallUiState.Ready -> VideoCallContent(uiState)
        else -> LoadingContent()
    }
}
```

MVI 아키텍처를 사용하는 프로젝트라면, 권한 요청 결과도 하나의 `Intent`(사용자 액션)로 취급해 `ViewModel`에서 상태를 관리하는 것이 일관성 있는 설계다.

```kotlin
sealed interface VideoCallUiState {
    object Loading : VideoCallUiState
    object PermissionDenied : VideoCallUiState
    data class Ready(val isCameraOn: Boolean) : VideoCallUiState
}

class VideoCallViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow<VideoCallUiState>(VideoCallUiState.Loading)
    val uiState: StateFlow<VideoCallUiState> = _uiState.asStateFlow()

    fun onPermissionResult(cameraGranted: Boolean, micGranted: Boolean) {
        _uiState.value = if (cameraGranted && micGranted) {
            VideoCallUiState.Ready(isCameraOn = true)
        } else {
            VideoCallUiState.PermissionDenied
        }
    }
}
```

권한 확인 로직 자체를 재사용 가능한 형태로 추출해두면, 여러 화면에서 반복되는 보일러플레이트를 줄일 수 있다. Hilt를 사용한다면 `PermissionChecker`와 같은 클래스를 주입받아 사용하는 방식도 흔하다.

```kotlin
class PermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
```

<br>

## 17. 실전에서 사용하는 권한 처리 패턴

녹음 앱
- `RECORD_AUDIO` 필수, `POST_NOTIFICATIONS`는 Foreground Service 알림을 위해 함께 확인
- 녹음 버튼을 누르는 시점에 맥락과 함께 요청(선요청 지양)

카메라/영상 앱
- `CAMERA`, `RECORD_AUDIO`(영상에 오디오 포함 시) 동시 요청
- 미디어 저장을 위해 API 레벨에 따라 `READ_MEDIA_*` 또는 앱 전용 저장소 사용 분기

내비게이션/위치 기반 앱
- 먼저 foreground 위치 권한(`ACCESS_FINE_LOCATION`)을 요청하고, 실제로 백그라운드 추적이 필요할 때 별도로 `ACCESS_BACKGROUND_LOCATION` 요청
- "왜 백그라운드 위치가 필요한지"를 명확히 설명하는 화면을 먼저 보여주는 것이 스토어 심사에도 유리

메신저 앱
- `POST_NOTIFICATIONS`(메시지 알림), `READ_CONTACTS`(연락처 동기화), `CAMERA`/`RECORD_AUDIO`(음성/영상 메시지) 등 기능별로 필요한 시점에 개별 요청

파일 공유/편집 앱
- Android 13 이상에서는 필요한 미디어 타입만 선택적으로 요청(`READ_MEDIA_IMAGES` 등)
- 굳이 광범위한 저장소 권한이 필요 없다면 `Storage Access Framework`(SAF)를 활용해 권한 요청 자체를 회피하는 것도 좋은 대안

알람/리마인더 앱
- `SCHEDULE_EXACT_ALARM` 특수 권한이 거부된 경우를 대비해 `setWindow()` 기반의 폴백 로직을 함께 준비

<br>

## 18. 주의사항과 자주 하는 실수

1. 매니페스트 선언 누락
   런타임 요청 코드는 완벽한데 매니페스트에 `<uses-permission>` 선언이 빠져 있으면, 요청 자체가 실패하거나 항상 거부로 처리된다.

2. 앱 실행 시점에 모든 권한을 한꺼번에 요청
   맥락 없이 앱을 열자마자 여러 권한을 동시에 요청하면 사용자가 거부할 확률이 크게 높아진다. 기능을 실제로 사용하는 시점에 개별적으로 요청하는 것이 훨씬 효과적이다.

3. 영구 거부 상태를 재요청 가능 상태와 혼동
   `shouldShowRequestPermissionRationale()`의 반환값만으로 상태를 단정하면, 이미 영구 거부된 사용자에게 계속 시스템 다이얼로그를 띄우려다 실패하는 로직을 만들게 된다.

4. 권한 승인 여부를 캐싱하고 재확인하지 않음
   앱 진입 시 한 번 확인한 권한 상태를 계속 신뢰하면, 사용자가 설정에서 권한을 취소한 이후에도 잘못된 상태로 동작할 수 있다. 기능 실행 직전에 항상 재확인해야 한다.

5. 특수 권한을 일반 다이얼로그로 요청하려는 시도
   `SYSTEM_ALERT_WINDOW`, `SCHEDULE_EXACT_ALARM` 같은 특수 권한은 `requestPermissions()`로 요청할 수 없다. 반드시 전용 설정 화면 Intent를 사용해야 한다.

6. 과도한 권한 요구
   기능에 꼭 필요하지 않은 권한까지 요구하면 사용자의 신뢰를 잃고, 스토어 심사에서도 문제가 될 수 있다. 정말 필요한 최소한의 권한만 요청해야 한다.

7. API 레벨 분기 누락
   `READ_EXTERNAL_STORAGE`와 `READ_MEDIA_IMAGES`처럼 버전에 따라 요구되는 권한이 다른 경우, 분기 처리를 하지 않으면 특정 버전에서만 기능이 깨질 수 있다.

8. 권한 거부 시 앱이 크래시되거나 멈춤
   권한이 없는 상태에서 해당 API를 호출하면 `SecurityException`이 발생한다. 항상 승인 여부를 먼저 확인한 뒤 분기 처리해야 한다.

9. 백그라운드 위치 권한을 foreground 권한 없이 요청
   `ACCESS_BACKGROUND_LOCATION`은 반드시 `ACCESS_FINE_LOCATION` 또는 `ACCESS_COARSE_LOCATION`이 먼저 승인된 상태에서만 의미가 있다. 순서를 지키지 않으면 다이얼로그가 정상적으로 표시되지 않을 수 있다.

10. 테스트를 에뮬레이터에서만 진행
    일부 제조사는 권한 관련 UI나 정책을 커스터마이징하는 경우가 있으므로, 실제 기기에서 다양한 시나리오(최초 요청, 거부 후 재요청, 영구 거부, 설정에서 취소 후 재진입)를 테스트하는 것이 중요하다.

<br>

## 19. 정리

Android Permission 시스템은 사용자의 민감한 데이터와 기기 자원을 보호하기 위한 핵심 보안 장치이며, Normal/Dangerous/Signature/Special이라는 보호 수준에 따라 요청 방식이 크게 달라진다. Android 6.0의 런타임 권한 도입 이후, 버전이 올라갈수록 위치·미디어·알림 권한이 점점 더 세분화되는 방향으로 발전해왔고, 이는 "필요한 최소한의 정보만, 필요한 시점에" 접근하도록 유도하는 일관된 흐름이다.

`ActivityResultContracts`를 사용한 권한 요청, `shouldShowRequestPermissionRationale()`을 통한 거부 상태 판단, 영구 거부 시 설정 화면으로의 안내까지 이어지는 흐름을 정확히 구현하는 것이 안정적인 권한 처리의 핵심이다. 또한 특수 권한은 일반 런타임 다이얼로그로 요청할 수 없다는 점, API 레벨에 따라 요구되는 권한 자체가 달라질 수 있다는 점을 항상 함께 고려해야 한다.

결국 좋은 권한 처리는 기술적으로 정확한 것을 넘어, 사용자가 "왜 이 권한이 필요한지" 납득할 수 있는 맥락과 함께 요청하는 데서 완성된다. 기능을 실제로 사용하는 시점에, 명확한 이유와 함께, 필요한 최소한의 권한만 요청하는 것이 실전에서 가장 중요한 원칙이다.
