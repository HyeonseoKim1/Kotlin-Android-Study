# file-provider

<br>

## 목차

1. FileProvider란?
2. 왜 FileProvider를 사용하는가?
3. File URI와 Content URI의 차이
4. FileProvider 동작 원리
5. FileProvider 설정하기
6. AndroidManifest.xml 설정
7. file_paths.xml 작성
8. 파일 공유하기
9. 카메라 촬영에서 사용하는 이유
10. 자주 발생하는 오류
11. 실무에서 사용하는 예제
12. 언제 사용하면 좋을까?
13. 정리

<br>

# # 1. FileProvider란?

FileProvider는 **앱 내부 파일을 다른 앱과 안전하게 공유하기 위한 컴포넌트**이다.

Android 7.0(API 24)부터는 앱이 자신의 파일 경로(`file://`)를 다른 앱에게 직접 전달하는 것이 금지되었다.

대신 Android는 **Content URI(content://)** 를 이용하여 파일을 공유하도록 변경하였다.

이 Content URI를 만들어주는 역할이 바로 FileProvider이다.

주로 다음과 같은 상황에서 모두 FileProvider를 사용한다.

* 카메라 앱에게 사진 저장 위치 전달
* 갤러리에 이미지 공유
* PDF 공유
* 이메일 첨부파일 전달


<br>

## 2. 왜 FileProvider를 사용하는가?

예전(Android 6 이하)

```text
App A
 ↓
file:///storage/emulated/0/image.jpg
 ↓
App B
```

다른 앱이 파일의 실제 경로를 모두 알 수 있었다.

이는 보안 문제, 권한 문제, 앱 내부 파일 노출 문제 등이 발생할 수 있었다.

Android 7부터는 다음과 같이 변경되었다.

```text
App A
 ↓
content://com.example.app.fileprovider/image.jpg
 ↓
App B
```

실제 파일 위치는 숨기고, Android가 권한을 관리하도록 변경하였다.

<br>

## 3. File URI와 Content URI의 차이

| File URI           | Content URI   |
| ------------------ | ------------- |
| file://            | content://    |
| 실제 경로 노출           | 실제 경로 숨김      |
| Android 7 이상 사용 불가 | Android 공식 방식 |
| 보안 취약              | 안전            |
| 권한 직접 관리           | Android가 관리   |

예시

기존 방식

```text
file:///storage/emulated/0/Pictures/image.jpg
```

FileProvider 방식

```text
content://com.example.app.fileprovider/my_images/image.jpg
```

<br>

## 4. FileProvider 동작 원리

```text
내 앱
 │
 │ 실제 파일
 ▼
storage/emulated/0/Pictures/image.jpg
 │
 │
 ▼
FileProvider
 │
 │ Content URI 생성
 ▼
content://com.example.app.fileprovider/...
 │
 ▼
카메라 앱
갤러리
카카오톡
메일 앱
```

다른 앱은 실제 파일 위치를 모른다.

오직 Content URI만 사용한다.

<br>

## 5. FileProvider 설정하기

먼저 의존성은 추가하지 않아도 된다.

AndroidX Core에 포함되어 있다.

필요한 것은 다음과 같다.

* Manifest 등록
* file_paths.xml 생성

<br>

## 6. AndroidManifest.xml 설정

```xml
<application>

    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.fileprovider"
        android:exported="false"
        android:grantUriPermissions="true">

        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths" />

    </provider>

</application>
```

각 속성 설명

| 속성                  | 설명                  |
| ------------------- | ------------------- |
| authorities         | FileProvider의 고유 이름 |
| exported            | 외부 앱이 직접 접근 불가      |
| grantUriPermissions | URI 접근 권한 허용        |
| meta-data           | 어떤 경로를 공유할지 지정      |

<br>

## 7. file_paths.xml 작성

res/xml/file_paths.xml

```xml
<?xml version="1.0" encoding="utf-8"?>

<paths>

    <cache-path
        name="cache"
        path="."/>

    <files-path
        name="files"
        path="."/>

    <external-files-path
        name="images"
        path="Pictures/"/>

</paths>
```

각 태그 의미

| 태그                  | 의미              |
| ------------------- | --------------- |
| cache-path          | cache 디렉터리      |
| files-path          | files 디렉터리      |
| external-files-path | 앱 전용 외부 저장소     |
| external-cache-path | 외부 cache        |
| external-path       | 외부 저장소(권장하지 않음) |

<br>

## 8. 파일 공유하기

파일을 Uri로 변환한다.

```kotlin
val file = File(filesDir, "sample.pdf")

val uri = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    file
)
```

이 Uri를 Intent에 넣는다.

```kotlin
val intent = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri, "application/pdf")
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

startActivity(intent)
```

`FLAG_GRANT_READ_URI_PERMISSION`을 반드시 추가해야 상대 앱이 파일을 읽을 수 있다.

<br>

## 9. 카메라 촬영에서 사용하는 이유

카메라 앱은 촬영한 사진을 저장할 위치가 필요하다.

예전에는 file:// 를 전달했지만, 

현재는 content:// 를 전달해야 한다.

예제

```kotlin
val imageUri = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    imageFile
)

val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
    putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
}

startActivity(intent)
```

촬영 후 사진이 imageFile에 저장된다.

<br>

## 10. 자주 발생하는 오류

### 1) FileUriExposedException

```text
android.os.FileUriExposedException
```

원인

```kotlin
Uri.fromFile(file)
```

Android 7 이상에서 금지되었다.

해결

```kotlin
FileProvider.getUriForFile(...)
```

사용한다.

<br>

### 2) Failed to find configured root

```text
Failed to find configured root
```

원인

file_paths.xml에 해당 경로가 등록되지 않았다.

해결

공유하려는 경로를 file_paths.xml에 추가한다.

<br>

### 3) Permission Denial

원인

읽기 권한을 전달하지 않았다.

해결

```kotlin
intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
```

또는

```kotlin
intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
```

추가한다.

<br>

## 11. 실무에서 사용하는 예제

이미지 공유

```kotlin
val intent = Intent(Intent.ACTION_SEND).apply {
    type = "image/*"
    putExtra(Intent.EXTRA_STREAM, imageUri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

startActivity(Intent.createChooser(intent, "이미지 공유"))
```

PDF 열기

```kotlin
val intent = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri, "application/pdf")
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

startActivity(intent)
```

카메라 촬영

```kotlin
Intent(MediaStore.ACTION_IMAGE_CAPTURE)
```

파일 첨부

```kotlin
Intent.ACTION_SEND
```

이 네 가지가 가장 많이 사용된다.

<br>

## 12. 언제 사용하면 좋을까?

다음과 같은 경우에는 거의 항상 FileProvider를 사용한다.

* 카메라 촬영
* 이미지 공유
* PDF 공유
* 문서 공유
* 이메일 첨부
* 카카오톡 파일 전송
* 다른 앱에서 파일 열기

반대로 앱 내부에서만 파일을 사용하는 경우에는 FileProvider가 필요하지 않다.

<br>

## 정리

* FileProvider는 파일을 안전하게 공유하기 위한 Android 공식 방법이다.
* Android 7부터는 file:// 대신 content://를 사용해야 한다.
* 실제 파일 경로를 숨겨 보안을 강화한다.
* FileProvider는 Content URI를 생성하는 역할을 한다.
* AndroidManifest.xml과 file_paths.xml을 반드시 설정해야 한다.
* 다른 앱으로 파일을 전달할 때는 `FLAG_GRANT_READ_URI_PERMISSION` 또는 `FLAG_GRANT_WRITE_URI_PERMISSION`을 함께 전달해야 한다.
* 카메라, 갤러리, PDF, 이메일, 카카오톡 공유 등 실무에서 매우 자주 사용되는 기능이다.
