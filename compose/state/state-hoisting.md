# State Hoisting

## State Hoisting란?

State Hoisting은 Composable 내부에서 관리하던 상태(State)를 외부(부모 Composable)로 끌어올려 관리하는 방식이다.

Compose에서는 상태를 한 곳에서 관리하고, 필요한 곳에 전달하는 구조를 권장한다.  


다음과 같은 장점을 가진다.

- 상태 관리가 쉬워짐
- 재사용성이 높아짐
- 테스트가 쉬워짐
- ViewModel 연결이 자연스러워짐

<br>

## 왜 필요한가?

Composable 내부에서 상태를 직접 관리하는 경우 다음과 같은 단점이 있다.

- 다른 화면에서 재사용하기 어려움
- 상태 추적이 어려움
- 여러 Composable 간 상태 공유가 힘듦

예시:

```kotlin
@Composable
fun NameField() {

    var name by remember {
        mutableStateOf("")
    }

    TextField(
        value = name,
        onValueChange = {
            name = it
        }
    )
}
```

위 방식은 간단하지만 다음과 같은 문제가 있다.

- 외부에서 값 접근 불가능
- 상태 제어 어려움
- ViewModel 연결 어려움


<br>

## 상태를 외부로 끌어올리기

**상태는 부모가 관리**하고, **자식 Composable은 값을 표시하고 이벤트만 전달**한다.

```kotlin
@Composable
fun ParentScreen() {

    var name by remember {
        mutableStateOf("")
    }

    NameField(
        name = name,
        onNameChange = {
            name = it
        }
    )
}
```

```kotlin
@Composable
fun NameField(
    name: String,
    onNameChange: (String) -> Unit
) {

    TextField(
        value = name,
        onValueChange = onNameChange
    )
}
```

<br>

## State Hoisting 구조

```text
부모
 ├─ 상태(State) 관리
 └─ 자식에게 값 전달

자식
 ├─ 값 표시
 └─ 이벤트 전달
```

Compose에서는 이 구조를 주로 사용한다.

<br>
<br>

## Before / After 비교

### Before

```kotlin
@Composable
fun Counter() {

    var count by remember {
        mutableIntStateOf(0)
    }

    Button(
        onClick = {
            count++
        }
    ) {
        Text("$count")
    }
}
```

- 상태를 내부에서 관리
- 재사용성 낮음
- 외부 제어 불가능

<br>

### After

```kotlin
@Composable
fun ParentScreen() {

    var count by remember {
        mutableIntStateOf(0)
    }

    Counter(
        count = count,
        onIncrease = {
            count++
        }
    )
}
```

```kotlin
@Composable
fun Counter(
    count: Int,
    onIncrease: () -> Unit
) {

    Button(
        onClick = onIncrease
    ) {
        Text("$count")
    }
}
```
- 상태 관리 분리
- 재사용 가능
- 외부 제어 가능

<br>

## onValueChange 패턴

Compose에서는 입력 이벤트를 함수 형태로 전달하는 패턴을 자주 사용한다.

```kotlin
onValueChange: (String) -> Unit
```

의미 : 문자열을 전달받고 아무 값도 반환하지 않는 함수


```kotlin
TextField(
    value = text,
    onValueChange = {
        text = it
    }
)
```
→ 입력 값이 변경될 때마다 호출된다.

<br>

## 단방향 데이터 흐름 (UDF)

Compose는 단방향 데이터 흐름(Unidirectional Data Flow)을 기반으로 동작한다.

```text
State ↓
UI 출력 ↓
사용자 입력 ↓
이벤트 발생 ↓
State 변경 ↓
Recomposition
```

- 데이터는 아래로 전달
- 이벤트는 위로 전달


<br>

## 재사용 가능한 Composable 만들기

State Hoisting을 사용하면 다양한 곳에서 재사용 가능하다.

```kotlin
@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit
) {

    TextField(
        value = value,
        onValueChange = onValueChange
    )
}
```


```kotlin
CustomTextField(
    value = email,
    onValueChange = {
        email = it
    }
)
```

```kotlin
CustomTextField(
    value = password,
    onValueChange = {
        password = it
    }
)
```

같은 Composable을 여러 곳에서 재사용할 수 있다.

<br>

## ViewModel과 연결되는 이유

실무에서는 상태를 ViewModel에서 관리한다.

```text
ViewModel
   ↓
Composable
   ↓
이벤트 전달
   ↓
ViewModel 상태 변경
```


State Hoisting 패턴을 이해하면 MVVM 구조를 훨씬 쉽게 이해할 수 있다.

<br>

## 정리

- State Hoisting은 상태를 부모로 끌어올리는 패턴이다.
- 부모는 상태를 관리한다.
- 자식은 UI 표시와 이벤트 전달만 담당한다.
- Compose에서는 매우 중요한 구조이다.
- 재사용성과 유지보수성이 높아진다.
- ViewModel과 자연스럽게 연결된다.
- 단방향 데이터 흐름(UDF)의 기반이 된다.
