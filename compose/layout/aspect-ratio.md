# AspectRatio

## 목차

1. AspectRatio란?
2. 왜 사용하는가?
3. 기본 사용법
4. 다양한 비율 적용하기
5. Image와 함께 사용하기
6. 카드 UI 만들기
7. weight와 함께 사용하기
8. 사용 시 주의사항
9. XML의 layout_weight와 비교
10. 정리

<br>

## 1. AspectRatio란?

AspectRatio는 가로와 세로의 비율을 고정하는 Modifier이다.

- 1:1 → 정사각형
- 16:9 → 유튜브 썸네일
- 4:3 → 사진
- 9:16 → 쇼츠, 릴스

위와 같이 특정 비율을 유지하고 싶을 때 사용한다.

```kotlin
Modifier.aspectRatio(1f)
```

위 코드는 항상 정사각형을 만든다.

<br>

## 2. 왜 사용하는가?

일반적으로 화면 크기는 기기마다 다르다.

예를 들어 이미지 너비를 .fillMaxWidth() 로 지정하면 기기마다 가로 크기가 달라진다.

이때 높이를 고정값으로 지정하면 .height(200.dp) 화면에 따라 비율이 어색해질 수 있다.

AspectRatio를 사용하면 .aspectRatio(16f / 9f) 가로 크기가 바뀌어도 비율은 항상 유지된다.

따라서 이미지, 배너, 카드, 영상 썸네일 구현 시 자주 사용한다.

<br>

## 3. 기본 사용법

### 1:1 정사각형

```kotlin
Box(
    modifier = Modifier
        .size(200.dp)
        .aspectRatio(1f)
)
```

<br>

### 16:9 비율

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
)
```

결과 : 유튜브 썸네일과 비슷한 비율

```text
━━━━━━━━━━━━━━━━
━━━━━━━━━━━━━━━━
━━━━━━━━━━━━━━━━
```


<br>

## 4. 다양한 비율 적용하기

### 1:1

```kotlin
Modifier.aspectRatio(1f)
```

정사각형

<br>

### 4:3

```kotlin
Modifier.aspectRatio(4f / 3f)
```

일반 사진 비율

<br>

### 16:9

```kotlin
Modifier.aspectRatio(16f / 9f)
```

영상 썸네일

<br>

### 9:16

```kotlin
Modifier.aspectRatio(9f / 16f)
```

쇼츠, 릴스

<br>

### 2:1

```kotlin
Modifier.aspectRatio(2f)
```

배너 이미지

<br>

## 5. Image와 함께 사용하기

실무에서 가장 많이 사용하는 예제다.

```kotlin
Image(
    painter = painterResource(R.drawable.sample),
    contentDescription = null,
    modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f),
    contentScale = ContentScale.Crop
)
```

왜 ContentScale.Crop을 사용할까?

```kotlin
contentScale = ContentScale.Crop
```

이미지가 비율에 맞게 꽉 차도록 잘라준다.

만약 사용하지 않으면 여백이 생길 수 있다.

```kotlin
contentScale = ContentScale.Fit
```



<br>

## 6. 카드 UI 만들기

예를 들어 영화 포스터 카드가 있다고 가정해보자.

```kotlin
Card(
    modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(3f / 4f)
) {

    Image(
        painter = painterResource(R.drawable.poster),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}
```

결과

```text
┌───────────┐
│           │
│  Poster   │
│           │
└───────────┘
```

화면 크기가 달라도 카드 비율은 유지된다.

<br>

## 7. weight와 함께 사용하기

Compose를 공부하다 보면 의외로 많이 헷갈리는 부분이다.

예제

```kotlin
Row {

    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
    )
}
```

결과

```text
□ □
```

각 Box가 동일한 너비를 가진 뒤 AspectRatio가 높이를 계산한다.

1. weight가 너비 결정
2. aspectRatio가 높이 결정

순서로 생각하면 이해하기 쉽다.

많은 사람이 "aspectRatio가 크기를 결정한다"고 생각하지만

실제로는 부모 레이아웃의 제약 조건을 기준으로 계산된다.

<br>

## 8. 사용 시 주의사항

### 1) size와 같이 사용하면 헷갈릴 수 있다

```kotlin
Modifier
    .size(200.dp)
    .aspectRatio(16f / 9f)
```

이미 size에서 가로 세로를 모두 지정했기 때문에 AspectRatio의 의미가 줄어든다.

보통은 다음과 같이 사용한다.

```kotlin
.fillMaxWidth()
.aspectRatio(16f / 9f)
```

<br>

### 2) 비율만 정할 뿐 실제 크기를 정하지 않는다

잘못 이해하기 쉬운 부분이다.

```kotlin
Modifier.aspectRatio(1f)
```

이 코드만으로는 크기가 정해지지 않는다.

부모가 제공하는 공간이 있어야 한다.

예시

```kotlin
Modifier
    .fillMaxWidth()
    .aspectRatio(1f)
```

<br>

### 3) 지나치게 복잡한 비율은 피하기

```kotlin
.aspectRatio(1.732f)
```

보다는

```kotlin
.aspectRatio(16f / 9f)
```

처럼 의미가 명확한 비율이 유지보수에 좋다.

<br>


## 9. XML의 layout_weight와 비교

XML에서는 비율을 유지하려면

```xml
<ImageView
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_weight="1" />
```

처럼 여러 속성을 조합하거나

ConstraintLayout의 ratio 기능을 사용해야 했다.

```xml
app:layout_constraintDimensionRatio="16:9"
```

Compose에서는 다음과 같이 작성한다..

```kotlin
Modifier.aspectRatio(16f / 9f)
```

그래서 비율 유지 UI를 만들기가 훨씬 쉽다.

<br>

## 9. 정리

- AspectRatio는 가로 세로 비율을 유지하는 Modifier이다.
- 이미지, 카드, 배너, 영상 썸네일 구현 시 자주 사용한다.
- 1:1, 4:3, 16:9 같은 비율을 쉽게 적용할 수 있다.
- 실제 크기를 정하는 것이 아니라 비율만 결정한다.
- weight와 함께 사용하면 부모가 너비를 결정하고 AspectRatio가 높이를 계산한다.
- XML의 ConstraintLayout Ratio 기능과 비슷한 역할을 한다.
- 실무에서는 Image + ContentScale.Crop 조합으로 사용하는 경우가 가장 많다.
