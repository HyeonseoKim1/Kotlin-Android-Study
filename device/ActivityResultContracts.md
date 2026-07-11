# rememberLauncherForActivityResult

## 목차

1. rememberLauncherForActivityResult란?
2. 왜 사용하는가?
3. ActivityResultContracts와의 관계
4. 기본 사용 방법
5. 사진 선택 예제
6. 권한 요청 예제
7. 여러 Contract 종류
8. Compose에서 사용할 때 주의사항
9. 언제 사용하면 좋을까?
10. 정리

<br>

## 1. rememberLauncherForActivityResult란?

`rememberLauncherForActivityResult()`는 Activity나 다른 앱을 실행한 뒤 결과를 받아오기 위한 Compose 전용 API이다.

기존 Android에서는 `startActivityForResult()`를 사용했지만 현재는 Deprecated 되었으며, 대신 Activity Result API를 사용한다.

Compose에서는 Activity Result API를 더욱 쉽게 사용할 수 있도록 `rememberLauncherForActivityResult()`를 제공한다.

대표적으로 아래와 같은 기능에서 사용한다.

- 갤러리 사진 선택
- 카메라 실행
- 파일 선택
- 권한 요청
- 다른 Activity 실행 후 결과 받기

<br>

## 2. 왜 사용하는가?

기존 방식은 Activity나 Fragment에서 콜백을 직접 관리해야 했다.

```kotlin
startActivityForResult(intent, REQUEST_CODE)
```

결과는 아래 메서드에서 받아야 했다.

```kotlin
override fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
)
```

문제점

- RequestCode를 직접 관리해야 한다.
- 여러 요청이 있으면 코드가 복잡해진다.
- Compose와 잘 맞지 않는다.
- Lifecycle을 고려하기 어렵다.

Compose에서는 launcher 하나만 만들면 된다.

```kotlin
val launcher = rememberLauncherForActivityResult(...)
```

필요한 순간

```kotlin
launcher.launch(...)
```

를 호출하면 된다.

<br>

## 3. ActivityResultContracts와의 관계

`rememberLauncherForActivityResult()`는 실행을 담당한다.

`ActivityResultContracts`는 어떤 작업을 할 것인지를 정의한다.

예를 들어

```kotlin
ActivityResultContracts.GetContent()
```
파일 선택

```kotlin
ActivityResultContracts.RequestPermission()
```
권한 요청

```kotlin
ActivityResultContracts.TakePicture()
```
카메라 촬영

둘은 항상 함께 사용한다.

```kotlin
rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
) { uri ->
    // 결과 처리
}
```

<br>

## 4. 기본 사용 방법

```kotlin
@Composable
fun ImagePickerScreen() {

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->

            println(uri)

        }

    Button(
        onClick = {
            launcher.launch("image/*")
        }
    ) {
        Text("사진 선택")
    }

}
```

### 코드 설명

```kotlin
val launcher =
```

결과를 받을 Launcher 생성

```kotlin
rememberLauncherForActivityResult()
```

Compose에서 Launcher를 기억한다.

재구성(Recomposition)이 일어나도 새로 생성되지 않는다.

```kotlin
ActivityResultContracts.GetContent()
```
갤러리에서 파일 선택

```kotlin
launcher.launch("image/*")
```
갤러리를 실행한다.

```kotlin
uri ->
```
선택된 사진의 Uri가 전달된다.

<br>

## 5. 사진 선택 예제

```kotlin
@Composable
fun PhotoPicker() {

    var imageUri by remember {
        mutableStateOf<Uri?>(null)
    }

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            imageUri = uri

        }

    Column {

        Button(
            onClick = {
                launcher.launch("image/*")
            }
        ) {
            Text("사진 선택")
        }

        imageUri?.let {

            Image(
                painter = rememberAsyncImagePainter(it),
                contentDescription = null
            )

        }

    }

}
```

선택한 이미지를 바로 화면에 표시할 수 있다.

실무에서도 가장 많이 사용하는 형태이다.

<br>

## 6. 권한 요청 예제

```kotlin
@Composable
fun PermissionScreen() {

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                println("권한 허용")
            } else {
                println("권한 거부")
            }

        }

    Button(
        onClick = {
            launcher.launch(
                Manifest.permission.CAMERA
            )
        }
    ) {
        Text("권한 요청")
    }

}
```

결과는 Boolean으로 전달된다.

```kotlin
true
```

권한 허용

```kotlin
false
```

권한 거부

<br>

## 7. 여러 Contract 종류

| Contract | 설명 |
|-----------|------|
| GetContent | 파일 선택 |
| OpenDocument | 문서 선택 |
| CreateDocument | 파일 생성 |
| RequestPermission | 권한 요청 |
| RequestMultiplePermissions | 여러 권한 요청 |
| TakePicture | 사진 촬영 |
| TakePicturePreview | 미리보기 촬영 |
| PickContact | 연락처 선택 |
| StartActivityForResult | Activity 실행 |

상황에 맞는 Contract를 선택하면 된다.

<br>

## 8. Compose에서 사용할 때 주의사항

### 1. remember를 사용해야 한다.

Launcher는 반드시

```kotlin
rememberLauncherForActivityResult()
```

로 생성해야 한다.

직접 생성하면 재구성 시 문제가 발생할 수 있다.

### 2. launch는 이벤트에서 호출한다.

좋은 예

```kotlin
Button(
    onClick = {
        launcher.launch("image/*")
    }
)
```

나쁜 예

```kotlin
launcher.launch("image/*")
```

Composable이 그려질 때 바로 실행된다.

### 3. 결과는 Nullable일 수 있다.

사용자가 취소하면

```kotlin
uri == null
```

이 된다.

항상 Null 체크를 해주는 것이 좋다.

<br>

## 9. 언제 사용하면 좋을까?

아래 상황이라면 대부분 사용한다고 생각하면 된다.

- 갤러리 열기
- 카메라 실행
- 파일 선택
- PDF 선택
- 권한 요청
- 다른 Activity 실행
- 연락처 선택

Compose 프로젝트에서는 매우 자주 등장하는 API이다.

<br>

## 10. 정리

- `rememberLauncherForActivityResult()`는 Compose에서 Activity Result를 처리하는 API이다.
- 기존 `startActivityForResult()`를 대체한다.
- `ActivityResultContracts`와 함께 사용한다.
- Launcher는 `rememberLauncherForActivityResult()`로 생성한다.
- 실행은 `launch()`를 통해 한다.
- 사진 선택, 권한 요청, 파일 선택 등 다양한 기능에서 활용된다.
- Compose 프로젝트에서는 거의 표준적으로 사용하는 방식이다.
