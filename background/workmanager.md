# WorkManager

## 목차

1. WorkManager란?
2. 왜 WorkManager를 사용할까?
3. Worker 만들기
4. OneTimeWorkRequest
5. PeriodicWorkRequest
6. Constraints
7. InputData와 OutputData
8. Unique Work
9. Work 상태 확인
10. CoroutineWorker
11. Hilt와 함께 사용하기
12. Service와 비교
13. 실무에서 자주 사용하는 패턴
14. 헷갈리는 부분
15. 면접 질문
16. 정리

# 1. WorkManager란?

WorkManager는 Android Jetpack에서 제공하는 백그라운드 작업 처리 라이브러리이다.

앱이 종료되거나 프로세스가 제거되더라도 언젠가는 반드시 실행되어야 하는 작업을 수행할 때 사용한다.

대표적인 예시

* 로그 업로드
* 서버 동기화
* 이미지 업로드
* 주기적인 데이터 갱신
* 백업 작업

<br>

# 2. 왜 WorkManager를 사용할까?

예를 들어 사용자가 게시글을 작성한 후 서버에 업로드해야 한다고 가정해보자.

```text
게시글 작성

↓

업로드 시작

↓

앱 종료
```

일반 Coroutine으로 처리하면 앱이 종료되는 순간 작업도 함께 종료된다.

하지만 WorkManager를 사용하면

```text
게시글 작성

↓

WorkManager 등록

↓

앱 종료

↓

시스템이 적절한 시점에 실행
```

작업이 보장된다.

그래서 Android에서는 "반드시 실행되어야 하는 작업"에 WorkManager를 사용한다.

<br>

# 3. Worker 만들기

WorkManager는 Worker를 통해 실제 작업을 수행한다.

```kotlin
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {

        Log.d("SyncWorker", "동기화 실행")

        return Result.success()
    }
}
```

doWork()가 실제 작업을 수행하는 함수이다.

<br>

## Result 종류

성공

```kotlin
Result.success()
```

실패

```kotlin
Result.failure()
```

재시도

```kotlin
Result.retry()
```

예시

```kotlin
override fun doWork(): Result {

    return try {
        uploadData()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
```

<br>

# 4. OneTimeWorkRequest

한 번만 실행되는 작업이다.

```kotlin
val request =
    OneTimeWorkRequestBuilder<SyncWorker>()
        .build()

WorkManager
    .getInstance(context)
    .enqueue(request)
```

실행 흐름

```text
Work 등록

↓

Worker 실행

↓

종료
```

<br>

## 실행 지연

10초 뒤 실행

```kotlin
val request =
    OneTimeWorkRequestBuilder<SyncWorker>()
        .setInitialDelay(
            10,
            TimeUnit.SECONDS
        )
        .build()
```

<br>

# 5. PeriodicWorkRequest

주기적으로 실행되는 작업이다.

```kotlin
val request =
    PeriodicWorkRequestBuilder<SyncWorker>(
        15,
        TimeUnit.MINUTES
    )
        .build()
```

예시

```text
15분마다 서버 동기화

1시간마다 로그 전송

하루마다 데이터 백업
```

<br>

## 최소 주기

WorkManager는 최소 15분부터 가능하다.

가능

```text
15분
30분
1시간
24시간
```

불가능

```text
1분
5분
10분
```

<br>

# 6. Constraints

특정 조건에서만 작업을 실행할 수 있다.

<br>

## 네트워크 연결 시만

```kotlin
val constraints =
    Constraints.Builder()
        .setRequiredNetworkType(
            NetworkType.CONNECTED
        )
        .build()
```

<br>

## Wi-Fi 연결 시만

```kotlin
Constraints.Builder()
    .setRequiredNetworkType(
        NetworkType.UNMETERED
    )
    .build()
```

<br>

## 충전 중일 때만

```kotlin
Constraints.Builder()
    .setRequiresCharging(true)
    .build()
```

<br>

## 배터리 부족 시 실행 금지

```kotlin
Constraints.Builder()
    .setRequiresBatteryNotLow(true)
    .build()
```

<br>

## 저장 공간 부족 시 실행 금지

```kotlin
Constraints.Builder()
    .setRequiresStorageNotLow(true)
    .build()
```

<br>

## 적용하기

```kotlin
val request =
    OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(constraints)
        .build()
```

<br>

# 7. InputData와 OutputData

Worker에 데이터를 전달할 수 있다.

<br>

## InputData

```kotlin
val request =
    OneTimeWorkRequestBuilder<SyncWorker>()
        .setInputData(
            workDataOf(
                "userId" to "1234"
            )
        )
        .build()
```

받기

```kotlin
override fun doWork(): Result {

    val userId =
        inputData.getString("userId")

    return Result.success()
}
```

<br>

## OutputData

```kotlin
return Result.success(
    workDataOf(
        "result" to "success"
    )
)
```

<br>

# 8. Unique Work

같은 작업이 여러 번 등록되는 것을 막는다.

<br>

예시

```kotlin
WorkManager
    .getInstance(context)
    .enqueueUniqueWork(
        "sync_work",
        ExistingWorkPolicy.KEEP,
        request
    )
```

<br>

## ExistingWorkPolicy

KEEP

기존 작업이 있으면 무시

```kotlin
ExistingWorkPolicy.KEEP
```

<br>

REPLACE

기존 작업 제거 후 실행

```kotlin
ExistingWorkPolicy.REPLACE
```

<br>

# 9. Work 상태 확인

```kotlin
WorkManager
    .getInstance(context)
    .getWorkInfoByIdLiveData(
        request.id
    )
```

<br>

상태 종류

```text
ENQUEUED
RUNNING
SUCCEEDED
FAILED
BLOCKED
CANCELLED
```

<br>

Compose에서는 State로 변환해서 UI를 갱신하는 경우가 많다.

<br>

# 10. CoroutineWorker

실무에서는 대부분 CoroutineWorker를 사용한다.

```kotlin
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(
    context,
    params
) {

    override suspend fun doWork(): Result {

        delay(1000)

        return Result.success()
    }
}
```

<br>

왜 사용할까?

* suspend 함수 사용 가능
* Retrofit 호출 가능
* Room 접근 가능
* Coroutine 취소 처리 가능

<br>

# 11. Hilt와 함께 사용하기

실무에서 가장 많이 사용하는 형태

```kotlin
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: SyncRepository
) : CoroutineWorker(
    context,
    params
) {

    override suspend fun doWork(): Result {

        repository.sync()

        return Result.success()
    }
}
```

<br>

Repository를 바로 주입할 수 있다.

```kotlin
private val repository: SyncRepository
```

<br>

# 12. Service와 비교

| 항목        | WorkManager | Service   |
| --------- | ----------- | --------- |
| 실행 보장     | O           | X         |
| 지연 실행     | O           | X         |
| 주기 실행     | O           | 직접 구현     |
| 앱 종료 후 실행 | O           | 상황에 따라 다름 |
| 음악 재생     | X           | O         |
| 위치 추적     | X           | O         |

<br>

## 선택 기준

WorkManager

```text
로그 업로드

데이터 동기화

백업

이미지 업로드
```

<br>

Service

```text
음악 재생

통화

실시간 위치 추적

화면 녹화
```

<br>

# 13. 실무에서 자주 사용하는 패턴

## 앱 실행 후 동기화

```text
앱 시작

↓

WorkManager 등록

↓

API 호출

↓

DB 저장
```

<br>

## 로그 업로드

```text
로그 저장

↓

Room 저장

↓

WorkManager 등록

↓

서버 업로드
```

<br>

## 이미지 업로드

```text
이미지 선택

↓

WorkManager 등록

↓

업로드

↓

성공 여부 저장
```

<br>

# 14. 헷갈리는 부분

## Coroutine과 WorkManager 차이

많이 헷갈린다.

Coroutine

```kotlin
viewModelScope.launch {

}
```

특징

* 현재 프로세스 안에서만 동작
* 앱 종료 시 작업 종료

<br>

WorkManager

```kotlin
WorkManager
```

특징

* 앱 종료 후에도 실행 가능
* 시스템이 작업 보장

<br>

## WorkManager가 즉시 실행될까?

아니다.

WorkManager는

```text
즉시 실행

아님
```

시스템이 적절한 시점에 실행한다.

그래서 수 초 정도 지연될 수 있다.

<br>

## PeriodicWorkRequest는 정확히 15분마다 실행될까?

아니다.

Android 시스템 상태에 따라 약간 늦어질 수 있다.

정확한 타이머가 아니다.

<br>

# 15. 다른 언어 경험자가 헷갈리는 부분

Python이나 JavaScript 경험자는

```python
upload()
```

실행하면 바로 수행된다고 생각하기 쉽다.

<br>

하지만 Android에서는

```text
배터리

절전 모드

Doze Mode

앱 상태
```

등을 고려해야 한다.

그래서 WorkManager가 시스템과 협력하여 실행 시점을 결정한다.

<br>

# 16. 면접 질문

### WorkManager를 사용하는 이유는?

앱이 종료되거나 프로세스가 제거되어도 실행이 보장되어야 하는 작업을 처리하기 위해 사용한다.

<br>

### Worker와 CoroutineWorker 차이는?

Worker는 일반 동기 방식이다.

CoroutineWorker는 suspend 함수 사용이 가능하다.

<br>

### PeriodicWorkRequest 최소 주기는?

15분이다.

<br>

### Unique Work를 사용하는 이유는?

동일한 작업의 중복 등록을 방지하기 위해 사용한다.

<br>

### Service 대신 WorkManager를 사용할 수 있을까?

음악 재생이나 실시간 위치 추적처럼 장시간 지속되는 작업은 Service가 적합하다.

로그 업로드나 서버 동기화는 WorkManager가 적합하다.

<br>

# 정리

* WorkManager는 Android의 백그라운드 작업 표준 라이브러리이다.
* 앱이 종료되어도 실행이 보장되는 작업에 사용한다.
* OneTimeWorkRequest는 한 번 실행한다.
* PeriodicWorkRequest는 주기적으로 실행한다.
* Constraints를 통해 실행 조건을 지정할 수 있다.
* 실무에서는 대부분 CoroutineWorker와 Hilt를 함께 사용한다.
* 로그 업로드, 서버 동기화, 이미지 업로드에 매우 자주 사용된다.
