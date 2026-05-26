# 안드로이드 앱의 기기 제한(Device Restriction)

## 왜 기기 제한이 필요한가?

안드로이드 앱은 수많은 제조사와 기기에서 실행된다.

삼성, 샤오미, Pixel, OPPO, 태블릿, 폴더블 등의 각 기기는

CPU, GPU, RAM,  Android 버전, AI 가속기(NPU) 등이 모두 다르다.

그래서 특정 기능은:

```text
"이 기기에서 안정적으로 실행 가능한가?"
```

를 앱에서 직접 판단해야 한다.

특히 온디바이스 AI(Gemma, Gemini Nano 등)는 기기 제한이 매우 중요하다.

<br>

# 기기 제한 방식

안드로이드에서는 보통 다음과 같은 방식으로 처리한다.

- 코드로 직접 검사
- Manifest로 설치 제한
- 둘 다 함께 사용

<br>
<br>

## 1. Android 버전 체크

특정 API는 특정 Android 버전 이상에서만 동작한다.

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    // Android 14 이상
}
```

### 주로 확인하는 것

- Android API 지원 여부
- 최신 시스템 기능 사용 가능 여부
- 보안 정책 지원 여부

<br>
<br>

## 2. CPU / ABI 체크

AI 모델은 대부분 ARM64 환경을 요구한다.

```kotlin
val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()

if (is64Bit) {
    // ARM64 지원
}
```

### ABI란?

ABI(Application Binary Interface)는 앱이 실행되는 CPU 구조를 의미한다.

예시:

- armeabi-v7a
- arm64-v8a
- x86
- x86_64

Gemma 같은 로컬 AI 모델은 대부분:

```text
arm64-v8a
```

환경을 요구한다.

<br>
<br>

## 3. 제조사 / 기기 모델 체크

특정 제조사 또는 특정 모델만 허용하는 경우도 있다.

```kotlin
val manufacturer = Build.MANUFACTURER
val model = Build.MODEL

if (manufacturer == "Samsung") {
    // 삼성 기기 전용 기능
}
```

실제 사례:

- Pixel 전용 AI 기능
- Galaxy S 시리즈 전용 기능
- 특정 태블릿만 지원

<br>

## 4. 시스템 Feature 체크

안드로이드에서 가장 많이 사용하는 방식 중 하나이다.

```kotlin
val pm = packageManager

if (pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
    // BLE 지원
}
```

### 확인 가능한 기능 예시

- BLE
- 카메라
- NFC
- GPS
- 센서
- AR 기능

<br>
<br>

## 5. RAM / 성능 체크

온디바이스 AI는 메모리를 많이 사용한다.

RAM이 부족하면:

- 앱 강제 종료
- 심한 발열
- 매우 느린 응답

등이 발생할 수 있다.

그래서 런타임에서 메모리 크기를 검사하기도 한다.

```kotlin
val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
val memoryInfo = ActivityManager.MemoryInfo()

activityManager.getMemoryInfo(memoryInfo)

val totalRam = memoryInfo.totalMem
```

<br>
<br>

## 6. GPU / AI 가속 지원 체크

고성능 AI 기능은:

- GPU
- NNAPI
- Vulkan
- NPU

지원 여부를 확인하는 경우가 많다.

예시:

```text
- NNAPI 지원 여부
- Vulkan 지원 여부
- GPU 연산 가능 여부
```

특히 AI 추론 속도에 큰 영향을 준다.

<br>
<br>

## 7. Manifest로 설치 자체 제한

앱 설치 가능 여부를 Play Store에서 제한할 수도 있다.

```xml
<uses-feature
    android:name="android.hardware.camera.ar"
    android:required="true"/>
```

이렇게 설정하면:

- 지원 기기 → 설치 가능
- 미지원 기기 → Play Store에서 앱이 보이지 않음

<br>
<br>

# 실제 AI 기능 흐름 예시

```text
앱 실행
→ 기기 스펙 검사
→ 지원 여부 판단
→ 모델 다운로드 가능 여부 확인
→ 가능하면 AI 기능 활성화
→ 불가능하면 기능 비활성화
```

<br>

# 실무에서 자주 사용하는 구조

보통은 기기 검사 로직을 클래스로 분리해서 관리한다.

```text
DeviceCapabilityChecker
 ├─ Android 버전 체크
 ├─ RAM 체크
 ├─ ABI 체크
 ├─ GPU 체크
 ├─ 제조사 체크
 └─ Feature 지원 여부 체크
```

<br>

### 간단한 예시 코드

```kotlin
fun isSupportedDevice(): Boolean {
    return Build.VERSION.SDK_INT >= 34 &&
           Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
}
```

실무에서는 여기에 다음과 같은 것들이 추가된다.

- RAM 조건
- 제조사 whitelist
- GPU 지원
- AI 가속 지원

<br>

# 정리

안드로이드의 기기 제한은 대부분 다음을 통해 처리한다.

- 코드로 기기 성능 검사
- 시스템 기능 검사
- Manifest 설치 제한


Gemma와 같은 온디바이스 AI는 RAM, CPU 구조, GPU/NPU 성능, Android 버전

등을 반드시 확인해야 안정적으로 동작할 수 있다.
