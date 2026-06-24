# BoxWithConstraints

## 목차

1. BoxWithConstraints란?
2. 왜 사용하는가?
3. 기본 사용법
4. maxWidth와 maxHeight
5. 화면 크기에 따라 UI 변경하기
6. 실전 예제 - 태블릿 대응
7. Box와의 차이점
8. 사용 시 주의사항
9. 언제 사용하면 좋을까?
10. 정리

<br>

## 1. BoxWithConstraints란?

BoxWithConstraints는 현재 Composable이 사용할 수 있는 최대 크기와 최소 크기를 확인할 수 있는 레이아웃이다.

일반 Box는 단순히 자식 Composable을 배치하는 역할만 하지만, BoxWithConstraints는 부모로부터 전달받은 크기 정보를 사용할 수 있다.

즉, "현재 화면이 얼마나 넓은지", "현재 화면이 얼마나 높은지"를 코드에서 확인할 수 있다.

```kotlin
BoxWithConstraints {
    Text(text = "현재 너비: $maxWidth")
}
```

<br>

## 2. 왜 사용하는가?

안드로이드 기기는 화면 크기가 다양하다.

- 작은 스마트폰
- 큰 스마트폰
- 태블릿
- 폴더블
- 가로 모드

같은 UI라도 화면 크기에 따라 다르게 보여줘야 하는 경우가 많다.

예를 들어 다음과 같이 구성할 수 있다.

- 작은 화면 → 세로 배치
- 큰 화면 → 가로 배치

```kotlin
if (maxWidth < 600.dp) {
    // Phone UI
} else {
    // Tablet UI
}
```

<br>

## 3. 기본 사용법

```kotlin
@Composable
fun SizeExample() {
    BoxWithConstraints {
        Text(
            text = "너비: $maxWidth\n높이: $maxHeight"
        )
    }
}
```

실행하면 현재 사용 가능한 크기가 출력된다.

<br>

## 4. maxWidth와 maxHeight

BoxWithConstraints 안에서는 다음 값을 사용할 수 있다.

| 값 | 설명 |
|------|------|
| maxWidth | 사용 가능한 최대 너비 |
| maxHeight | 사용 가능한 최대 높이 |
| minWidth | 사용 가능한 최소 너비 |
| minHeight | 사용 가능한 최소 높이 |

<br>

**예제**

```kotlin
BoxWithConstraints {

    Text(
        text =
        """
        maxWidth = $maxWidth
        maxHeight = $maxHeight
        """.trimIndent()
    )
}
```

<br>

## 5. 화면 크기에 따라 UI 변경하기

가장 많이 사용하는 패턴이다.

```kotlin
@Composable
fun ResponsiveScreen() {

    BoxWithConstraints {

        if (maxWidth < 600.dp) {

            Column {
                Text("프로필")
                Text("설정")
            }

        } else {

            Row {
                Text("프로필")
                Spacer(modifier = Modifier.width(16.dp))
                Text("설정")
            }
        }
    }
}
```

작은 화면에서는 세로 배치, 큰 화면에서는 가로 배치가 된다.

<br>

## 6. 실전 예제 - 태블릿 대응

Compose 앱에서 흔히 사용하는 방식이다.

휴대폰

```text
[목록]
```

태블릿

```text
[목록] [상세]
```

예제

```kotlin
@Composable
fun MailScreen() {

    BoxWithConstraints {

        if (maxWidth < 840.dp) {

            MailListScreen()

        } else {

            Row {

                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    MailListScreen()
                }

                Box(
                    modifier = Modifier.weight(2f)
                ) {
                    MailDetailScreen()
                }
            }
        }
    }
}
```

이런 방식은 Gmail 같은 앱에서도 많이 사용된다.

<br>

## 7. Box와의 차이점

### Box

```kotlin
Box {
    Text("Hello")
}
```

자식을 겹쳐서 배치하는 역할

크기 정보는 알 수 없음

### BoxWithConstraints

```kotlin
BoxWithConstraints {
    Text("$maxWidth")
}
```

자식 배치 가능

크기 정보 사용 가능

반응형 UI 구현 가능

<br>

## 8. 사용 시 주의사항

### 1) 모든 화면에 사용할 필요는 없다

단순 화면이라면 일반 Box가 더 적절하다.

```kotlin
Box {
    Content()
}
```

굳이 BoxWithConstraints를 사용할 필요가 없다.

### 2) 너무 세밀하게 분기하지 말기

```kotlin
if (maxWidth < 400.dp)
```

```kotlin
if (maxWidth < 401.dp)
```

```kotlin
if (maxWidth < 402.dp)
```

이런 식으로 작성하면 유지보수가 어려워진다.

보통 다음과 같으 기준을 사용한다.

```kotlin
600.dp
840.dp
```


### 3) 상태 저장 용도가 아니다

```kotlin
val width = maxWidth
```

이 값은 현재 레이아웃 정보를 제공하는 것이지 상태를 저장하는 용도가 아니다.

<br>

## 9. 언제 사용하면 좋을까?

다음 상황이라면 사용을 고려할 수 있다.

- 태블릿 대응
- 폴더블 대응
- 가로 모드 대응
- 화면 크기에 따라 레이아웃 변경
- 반응형 UI
- 목록 + 상세 화면 구성

반대로 단순 UI에서는 사용하지 않아도 된다.


<br>

## 10. 정리

- BoxWithConstraints는 부모가 제공한 크기 정보를 사용할 수 있는 레이아웃이다.
- maxWidth, maxHeight를 통해 현재 사용 가능한 공간을 확인할 수 있다.
- 화면 크기에 따라 다른 UI를 보여줄 수 있다.
- 태블릿, 폴더블, 가로 모드 대응에 매우 유용하다.
- 일반적인 레이아웃 용도라면 Box를 사용하고, 반응형 UI가 필요할 때 BoxWithConstraints를 사용한다.
- 실무에서는 목록 + 상세 화면, 태블릿 대응 화면에서 자주 활용된다.
