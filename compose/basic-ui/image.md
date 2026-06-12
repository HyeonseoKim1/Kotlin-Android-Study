# Image

## 목차

1. Image란?
2. Image 기본 사용
3. drawable 이미지 표시
4. ContentScale
5. Modifier 활용
6. URL 이미지 표시 (Coil)
7. contentDescription
8. 자주 헷갈리는 부분
9. 실무 예시
10. 정리

<br>

# 1. Image란?

Image는 Jetpack Compose에서 이미지를 화면에 표시하기 위한 컴포저블이다.

기존 Android XML에서는 ImageView를 사용했지만, Compose에서는 Image 컴포저블을 사용한다.

XML

```xml
<ImageView
    android:layout_width="100dp"
    android:layout_height="100dp" />
```

Compose

```kotlin
Image(
    painter = painterResource(R.drawable.sample),
    contentDescription = null
)
```

<br>

# 2. Image 기본 사용

가장 기본적인 사용 방법이다.

```kotlin
Image(
    painter = painterResource(R.drawable.sample),
    contentDescription = null
)
```

매개변수 설명

| 속성                 | 설명          |
| ------------------ | ----------- |
| painter            | 표시할 이미지     |
| contentDescription | 접근성 설명      |
| modifier           | 크기, 패딩 등 설정 |
| contentScale       | 이미지 표시 방식   |

<br>

# 3. drawable 이미지 표시

프로젝트의 drawable 폴더에 있는 이미지를 표시할 수 있다.

```kotlin
Image(
    painter = painterResource(R.drawable.profile),
    contentDescription = "프로필 이미지"
)
```

drawable

```text
res
 └ drawable
     └ profile.png
```

왜 painterResource를 사용할까?

Compose의 Image는 이미지 데이터를 직접 받는 것이 아니라 Painter 객체를 사용한다.

따라서 drawable 이미지를 표시하려면 painterResource()로 Painter를 생성해야 한다.

```kotlin
painterResource(R.drawable.profile)
```

<br>

# 4. ContentScale

이미지 크기와 영역 크기가 다를 때 이미지를 어떻게 표시할지 결정한다.

## ContentScale.Fit

이미지가 잘리지 않도록 전체를 표시한다.

```kotlin
Image(
    painter = painterResource(R.drawable.sample),
    contentDescription = null,
    contentScale = ContentScale.Fit
)
```

<br>

## ContentScale.Crop

영역을 꽉 채운다.

필요한 경우 이미지가 잘릴 수 있다.

```kotlin
Image(
    painter = painterResource(R.drawable.sample),
    contentDescription = null,
    contentScale = ContentScale.Crop
)
```

실무에서 가장 많이 사용한다.

프로필 이미지

썸네일 이미지

배너 이미지

<br>

## ContentScale.FillBounds

영역을 강제로 채운다.

비율이 깨질 수 있다.

```kotlin
Image(
    painter = painterResource(R.drawable.sample),
    contentDescription = null,
    contentScale = ContentScale.FillBounds
)
```

<br>

## ContentScale 종류 정리

| 종류         | 설명                  |
| ---------- | ------------------- |
| Fit        | 이미지 전체 표시           |
| Crop       | 영역을 채우며 필요한 부분 잘림   |
| FillBounds | 영역을 강제로 채움          |
| Inside     | 이미지가 영역 안에 들어가도록 표시 |

<br>

# 5. Modifier 활용

## 크기 지정

```kotlin
Image(
    painter = painterResource(R.drawable.sample),
    contentDescription = null,
    modifier = Modifier.size(120.dp)
)
```

<br>

## 가로 크기 채우기

```kotlin
Image(
    painter = painterResource(R.drawable.sample),
    contentDescription = null,
    modifier = Modifier.fillMaxWidth()
)
```

<br>

## 원형 이미지 만들기

```kotlin
Image(
    painter = painterResource(R.drawable.profile),
    contentDescription = null,
    modifier = Modifier
        .size(80.dp)
        .clip(CircleShape)
)
```

프로필 이미지에서 자주 사용한다.

<br>

## 모서리 둥글게 만들기

```kotlin
Image(
    painter = painterResource(R.drawable.sample),
    contentDescription = null,
    modifier = Modifier.clip(
        RoundedCornerShape(12.dp)
    )
)
```

<br>

# 6. URL 이미지 표시 (Coil)

실무에서는 서버에서 받은 이미지 URL을 표시하는 경우가 많다.

Compose에서는 보통 Coil 라이브러리를 사용한다.

build.gradle.kts

```kotlin
implementation("io.coil-kt.coil3:coil-compose:3.2.0")
```

<br>

## AsyncImage

```kotlin
AsyncImage(
    model = "https://sample.com/image.png",
    contentDescription = null
)
```

<br>

## 크기 지정

```kotlin
AsyncImage(
    model = "https://sample.com/image.png",
    contentDescription = null,
    modifier = Modifier.size(100.dp)
)
```

<br>

## ContentScale 적용

```kotlin
AsyncImage(
    model = "https://sample.com/image.png",
    contentDescription = null,
    contentScale = ContentScale.Crop
)
```

<br>

# 7. contentDescription

접근성을 위한 설명이다.

화면 낭독기(TalkBack)가 사용자에게 읽어주는 값이다.

```kotlin
Image(
    painter = painterResource(R.drawable.profile),
    contentDescription = "프로필 이미지"
)
```

<br>

## 장식용 이미지

단순 배경이나 꾸미기 용도라면 null 사용

```kotlin
Image(
    painter = painterResource(R.drawable.banner),
    contentDescription = null
)
```

<br>

## 의미가 있는 이미지

설명을 작성

```kotlin
Image(
    painter = painterResource(R.drawable.search),
    contentDescription = "검색"
)
```

<br>

# 8. 자주 헷갈리는 부분

## Q. Image와 AsyncImage 차이는?

Image

* drawable 리소스 표시
* Painter 필요

```kotlin
Image(
    painter = painterResource(R.drawable.sample),
    contentDescription = null
)
```

AsyncImage

* URL 이미지 표시
* Coil 사용

```kotlin
AsyncImage(
    model = imageUrl,
    contentDescription = null
)
```

<br>

## Q. 왜 painter를 사용하는가?

Compose는 이미지 종류(drawable, bitmap, vector 등)를 통일된 방식으로 처리하기 위해 Painter 객체를 사용한다.

```kotlin
painterResource(R.drawable.sample)
```

결과적으로 Image는 Painter만 알면 된다.

<br>

## Q. ContentScale.Crop을 많이 쓰는 이유는?

사용자가 지정한 영역을 빈 공간 없이 채울 수 있기 때문이다.

```kotlin
contentScale = ContentScale.Crop
```

실무에서 썸네일, 프로필, 카드 이미지 대부분이 Crop을 사용한다.

<br>

# 9. 실무 예시

프로필 이미지

```kotlin
AsyncImage(
    model = profileImageUrl,
    contentDescription = "프로필 이미지",
    modifier = Modifier
        .size(80.dp)
        .clip(CircleShape),
    contentScale = ContentScale.Crop
)
```

<br>

게시글 썸네일

```kotlin
AsyncImage(
    model = thumbnailUrl,
    contentDescription = "게시글 썸네일",
    modifier = Modifier
        .fillMaxWidth()
        .height(200.dp),
    contentScale = ContentScale.Crop
)
```

<br>

상품 이미지

```kotlin
AsyncImage(
    model = productImageUrl,
    contentDescription = "상품 이미지",
    modifier = Modifier.fillMaxWidth(),
    contentScale = ContentScale.Fit
)
```

<br>

# 10. 정리

* Image는 Compose에서 이미지를 표시하는 컴포저블이다.
* drawable 이미지는 painterResource()를 사용한다.
* URL 이미지는 Coil의 AsyncImage를 사용한다.
* ContentScale은 이미지 표시 방식을 결정한다.
* 실무에서는 ContentScale.Crop을 가장 많이 사용한다.
* 프로필 이미지는 CircleShape와 함께 자주 사용한다.
* 접근성을 위해 의미 있는 이미지는 contentDescription을 작성해야 한다.
* 단순 장식용 이미지는 contentDescription에 null을 사용한다.
