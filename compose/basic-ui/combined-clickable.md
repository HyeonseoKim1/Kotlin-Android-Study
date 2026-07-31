# combined-clickable

## 목차
1. combinedClickable이란?
2. clickable과의 차이
3. 언제 사용하는가?
4. 기본 사용법
5. 클릭, 더블 클릭, 롱 클릭
6. 내부 동작 원리
7. InteractionSource와 함께 사용하기
8. indication(Ripple)와의 관계
9. 접근성(Semantics)
10. 실무 예제 1 - 채팅 메시지
11. 실무 예제 2 - 이미지 뷰어
12. LazyColumn에서 사용하기
13. 자주 하는 실수
14. 성능 관점
15. clickable과 combinedClickable 선택 기준
16. 내부 구현 살펴보기
17. 정리

<br>

## 1. combinedClickable이란?

combinedClickable은 하나의 Composable에서 여러 종류의 터치 이벤트를 처리할 수 있도록 만든 Modifier이다.

대표적으로 다음 이벤트를 동시에 처리할 수 있다.

- Click
- Double Click
- Long Click

예를 들어 카카오톡 메시지를 생각해보자.

- 한 번 클릭 → 링크 열기
- 길게 클릭 → 메뉴 표시
- 두 번 클릭 → 좋아요 표시

이러한 동작은 clickable 하나로는 구현할 수 없다.

그래서 combinedClickable을 사용한다.

<br>

## 2. clickable과의 차이

### clickable

```kotlin
Modifier.clickable {
    println("Click")
}
```

가능한 이벤트
- Click

불가능
- Double Click
- Long Click

### combinedClickable

```kotlin
Modifier.combinedClickable(
    onClick = {},
    onLongClick = {},
    onDoubleClick = {}
)
```

가능한 이벤트
- Click
- Double Click
- Long Click

모두 하나의 Modifier에서 처리한다.

<br>

## 3. 언제 사용하는가?

실무에서 정말 많이 사용된다.

### 메신저
- 클릭 → 메시지 열기
- 길게 클릭 → 삭제/복사 메뉴

### 사진 앱
- 클릭 → 전체 화면
- 더블 클릭 → 확대
- 길게 클릭 → 공유

### 이메일
- 클릭 → 메일 보기
- 길게 클릭 → 선택 모드

### 음악 앱
- 클릭 → 재생
- 길게 클릭 → 플레이리스트 추가

<br>

## 4. 기본 사용법

```kotlin
Box(
    modifier = Modifier
        .size(120.dp)
        .background(Color.Gray)
        .combinedClickable(
            onClick = {
                println("Click")
            },
            onLongClick = {
                println("Long Click")
            },
            onDoubleClick = {
                println("Double Click")
            }
        )
)
```

실행 결과

- 한 번 클릭
- 두 번 클릭
- 길게 클릭

모두 정상 동작한다.

<br>

## 5. 클릭, 더블 클릭, 롱 클릭

### Click

```kotlin
onClick = {
    println("Click")
}
```

가장 일반적인 터치이다.

### Long Click

```kotlin
onLongClick = {
    println("Long Click")
}
```

일정 시간 이상 누르면 실행된다.

Android 기본 LongPress Gesture를 사용한다.

### Double Click

```kotlin
onDoubleClick = {
    println("Double Click")
}
```

짧은 시간 안에 두 번 터치하면 호출된다.

Compose 내부에서 두 클릭의 시간을 계산한다.

<br>

## 6. 내부 동작 원리

사용자가 화면을 터치하면 다음과 같이 동작한다.

```
Touch Down
↓
Pointer Event
↓
Gesture Detector
↓
Click?
↓
Long Press?
↓
Double Click?
↓
Callback 실행
```

Compose는 Pointer Input을 직접 처리하지 않고 Gesture Detector를 통해 이벤트를 해석한다.

즉,

```
Touch
↓
PointerInput
↓
TapGestureDetector
↓
combinedClickable
↓
onClick()
```

이러한 구조이다.

<br>

## 7. InteractionSource와 함께 사용하기

Ripple이나 Press 상태를 추적할 때 사용한다.

```kotlin
val interactionSource = remember {
    MutableInteractionSource()
}

Box(
    modifier = Modifier.combinedClickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        onClick = {},
        onLongClick = {}
    )
)
```

왜 사용할까?

예를 들어

- 눌림 상태
- Hover 상태
- Focus 상태

등을 직접 관찰할 수 있다.

### Press 상태 수집

```kotlin
val pressed by interactionSource.collectIsPressedAsState()

Text(
    text = if (pressed) "Pressed" else "Idle"
)
```

버튼을 누르는 동안 상태가 변경된다.

<br>

## 8. indication(Ripple)와의 관계

기본적으로 Ripple 효과가 자동 적용된다.

```kotlin
Modifier.combinedClickable(
    onClick = {}
)
```

Ripple이 자동 표시된다.

Ripple을 제거하고 싶다면

```kotlin
Modifier.combinedClickable(
    interactionSource = remember {
        MutableInteractionSource()
    },
    indication = null,
    onClick = {}
)
```

Ripple만 제거되고 클릭은 그대로 동작한다.

<br>

## 9. 접근성(Semantics)

combinedClickable은 접근성 정보를 자동으로 추가한다.

TalkBack은

- 클릭 가능
- 길게 누르기 가능

등을 자동으로 읽어준다.

설명을 추가하려면

```kotlin
Modifier.combinedClickable(
    onClickLabel = "사진 열기",
    onClick = {}
)
```

또는

```kotlin
Modifier.semantics {
    contentDescription = "프로필 이미지"
}
```

접근성을 높일 수 있다.

<br>

## 10. 실무 예제 1 - 채팅 메시지

```kotlin
Card(
    modifier = Modifier.combinedClickable(
        onClick = {
            openMessage()
        },
        onLongClick = {
            showMenu()
        }
    )
) {
    Text("안녕하세요.")
}
```

동작

- 클릭 → 메시지 보기
- 길게 클릭 → 복사/삭제 메뉴

카카오톡이나 Slack과 동일한 방식이다.

<br>

## 11. 실무 예제 2 - 이미지 뷰어

```kotlin
Image(
    painter = painterResource(R.drawable.sample),
    contentDescription = null,
    modifier = Modifier.combinedClickable(
        onClick = {
            openViewer()
        },
        onDoubleClick = {
            zoom()
        }
    )
)
```

동작

- 클릭 → 이미지 보기
- 더블 클릭 → 확대

갤러리 앱에서 자주 볼 수 있는 패턴이다.

<br>

## 12. LazyColumn에서 사용하기

```kotlin
LazyColumn {
    items(messages) { message ->

        Text(
            text = message.text,
            modifier = Modifier.combinedClickable(
                onClick = {
                    openMessage(message)
                },
                onLongClick = {
                    showMenu(message)
                }
            )
        )

    }
}
```

메시지 하나마다 서로 다른 동작을 줄 수 있다.

실무에서 매우 흔한 패턴이다.

<br>

## 13. 자주 하는 실수

### 1) clickable과 combinedClickable 동시 사용

```kotlin
Modifier.clickable { }
    .combinedClickable { }
```

이렇게 두 Modifier를 동시에 사용하면 이벤트 충돌이 발생할 수 있다.

하나만 사용하는 것이 좋다.

### 2) onClick 생략

```kotlin
combinedClickable(
    onLongClick = {}
)
```

onClick을 생략할 수는 있지만, 사용자 입장에서는 클릭이 되지 않는 것처럼 느껴질 수 있다.

필요한 경우가 아니라면 기본 클릭도 함께 제공하는 것이 좋다.

### 3) onDoubleClick만 사용

```kotlin
combinedClickable(
    onDoubleClick = {}
)
```

더블 클릭은 일반 클릭보다 우선순위 판단이 필요하므로 클릭 이벤트가 약간 지연되어 처리될 수 있다.

이는 Compose의 제스처 판별 과정에서 의도된 동작이다.

<br>

## 14. 성능 관점

combinedClickable은 내부적으로 Pointer Input과 Gesture Detector를 사용하지만 일반적인 앱에서는 성능 부담이 거의 없다.

다만 매우 많은 아이템(예: 수천 개)을 표시하는 리스트에서는 필요한 경우에만 사용하는 것이 좋다.

불필요하게 모든 아이템에 더블 클릭과 롱 클릭을 등록하면 제스처 판별 비용이 조금 증가할 수 있다.

<br>

## 15. clickable과 combinedClickable 선택 기준

| 상황 | 추천 |
|---|---|
| 클릭만 필요 | clickable |
| 클릭 + 롱 클릭 | combinedClickable |
| 클릭 + 더블 클릭 | combinedClickable |
| 세 가지 모두 필요 | combinedClickable |
| 접근성 포함 클릭 처리 | 둘 다 가능 |

원칙은 필요한 기능만 사용하는 것이다. 단순 클릭만 필요하다면 clickable이 더 적합하다.

<br>

## 16. 내부 구현 살펴보기

combinedClickable은 내부적으로 detectTapGestures()를 사용하여 다양한 탭 제스처를 처리한다.

개념적으로는 다음과 같은 흐름이다.

```kotlin
pointerInput(Unit) {
    detectTapGestures(
        onTap = { },
        onDoubleTap = { },
        onLongPress = { }
    )
}
```

실제 구현은 접근성, Focus, Hover, Press Interaction, Ripple 등을 함께 처리하기 때문에 훨씬 복잡하지만, 핵심은 detectTapGestures()를 기반으로 한다.

즉, combinedClickable은 단순히 이벤트를 연결하는 Modifier가 아니라 Compose의 제스처 처리와 접근성 시스템을 함께 제공하는 고수준 API이다.

<br>

## 정리

- combinedClickable은 클릭, 더블 클릭, 롱 클릭을 하나의 Modifier에서 처리한다.
- 단순 클릭만 필요하다면 clickable을 사용하는 것이 좋다.
- InteractionSource와 함께 사용하면 Press 상태를 관찰할 수 있다.
- 기본적으로 Ripple 효과와 접근성 정보가 자동으로 제공된다.
- 채팅, 갤러리, 이메일, 리스트 선택 등 실무에서 자주 사용되는 Modifier이다.
- 내부적으로 detectTapGestures()를 기반으로 동작하며 Pointer Input을 직접 다루지 않고 제스처를 해석해 콜백을 호출한다.
- clickable과 combinedClickable을 동시에 사용하는 것은 이벤트 충돌을 일으킬 수 있으므로 피하는 것이 좋다.
