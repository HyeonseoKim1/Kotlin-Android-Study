Predictive Back
목차
Predictive Back이란?
기존 BackHandler와 차이
Android 14 Predictive Back 동작 방식
Compose에서 BackHandler 사용법
Navigation Compose와 함께 사용하는 방법
뒤로가기 스택 처리
특정 조건에서 뒤로가기 막기
Confirm Dialog와 함께 사용하기
실무에서 자주 발생하는 문제
정리
<br>
1. Predictive Back이란?

Predictive Back은 Android 14(API 34)에서 정식 지원되는 새로운 뒤로가기 UX이다.

기존에는 사용자가 뒤로가기 버튼이나 제스처를 수행하면 즉시 화면이 닫혔다.

반면 Predictive Back은 뒤로가기 제스처를 수행하는 동안 사용자가 어떤 화면으로 이동하는지 미리 애니메이션으로 보여준다.

즉,

"뒤로가기를 누르면 어디로 이동하는지 예측(Predictive)해서 보여주는 기능"

이라고 이해하면 된다.

기존에는

뒤로가기 →
현재 화면 종료 →
이전 화면 표시

였다면,

Predictive Back은

뒤로가기 제스처 시작
        ↓
이전 화면 미리 보여줌
        ↓
손을 떼면 완료
또는
취소하면 원래 화면 유지

가 된다.

사용자는 뒤로가기 결과를 확인한 후 손을 떼거나 취소할 수 있기 때문에 UX가 훨씬 자연스럽다.

<br>
왜 추가되었을까?

기존 제스처에서는

어디로 이동하는지 알 수 없었다.
실수로 뒤로가기를 하는 경우가 있었다.
앱마다 뒤로가기 경험이 달랐다.

Android는 Predictive Back을 통해

일관된 UX 제공
사용자 실수 감소
자연스러운 화면 전환

을 목표로 하고 있다.

<br>
2. 기존 BackHandler와 차이

Compose에서는 뒤로가기를 처리할 때 대부분 BackHandler를 사용한다.

BackHandler {
    navController.popBackStack()
}

이 코드는 뒤로가기를 누르면 즉시 실행된다.

하지만 Predictive Back에서는

제스처 시작
↓
애니메이션 진행
↓
완료 시점
↓
BackHandler 실행

이라는 흐름을 가진다.

즉 BackHandler 자체는 그대로 사용할 수 있지만,

Predictive Back은 시스템이 애니메이션을 먼저 처리한 뒤 콜백을 호출한다.

<br>
비교
기존 Back	Predictive Back
즉시 종료	애니메이션 후 종료
현재 화면만 보임	이전 화면 미리 보임
취소 불가능	제스처 취소 가능
UX 단순	UX 향상
<br>
3. Android 14 Predictive Back 동작 방식

Predictive Back은 시스템이 직접 제어한다.

대략적인 흐름은 다음과 같다.

사용자 스와이프

↓

Back Gesture 시작

↓

이전 화면 렌더링

↓

애니메이션

↓

손을 놓으면 완료

↓

Back 이벤트 전달

↓

현재 화면 종료

따라서 개발자가 애니메이션을 직접 구현하는 것이 아니다.

시스템이

화면 캡처
이전 화면 표시
진행도 계산
애니메이션

을 자동으로 수행한다.

개발자는 뒤로가기 이벤트만 적절히 처리하면 된다.

<br>
지원 버전
Android 버전	지원 여부
Android 13	Developer Option으로 테스트 가능
Android 14 이상	기본 지원
<br>
4. Compose에서 BackHandler 사용법

Compose에서는 BackHandler가 가장 많이 사용된다.

@Composable
fun DetailScreen(
    navController: NavController
) {
    BackHandler {
        navController.popBackStack()
    }

    Text("Detail")
}

동작 순서는

뒤로가기

↓

BackHandler 호출

↓

popBackStack()

↓

이전 화면

이다.

<br>
특정 조건에서만 실행
BackHandler(enabled = isEditMode) {
    showExitDialog = true
}

enabled를 이용하면

수정 중일 때만
로그인 중일 때만
입력 중일 때만

뒤로가기를 가로챌 수 있다.

<br>
여러 개 존재하면?

Compose는 가장 안쪽 Composable의 BackHandler가 우선 실행된다.

Activity

↓

Screen

↓

Dialog

↓

BottomSheet

BottomSheet의 BackHandler가 먼저 호출된다.

<br>
5. Navigation Compose와 함께 사용하는 방법

가장 일반적인 예제는 다음과 같다.

BackHandler {
    navController.popBackStack()
}

하지만 Navigation Compose에서는 대부분 BackHandler 없이도 동작한다.

왜냐하면

NavHost가 이미 시스템 Back을 처리하기 때문이다.

즉

NavHost(...)

만 있어도

뒤로가기

↓

BackStack 제거

↓

이전 Destination 이동

이 자동으로 수행된다.

<br>

BackHandler는 다음과 같은 경우에 사용하는 것이 좋다.

Dialog 닫기
BottomSheet 닫기
종료 확인
데이터 저장 여부 확인

즉 Navigation 자체를 위해 사용하는 것이 아니라

화면의 상태를 제어하기 위해 사용한다.

<br>
6. 뒤로가기 스택 처리

Navigation은 내부적으로 Back Stack을 관리한다.

예를 들어

Home

↓

List

↓

Detail

↓

Setting

이라면 Stack은

Home

List

Detail

Setting

이다.

뒤로가기를 하면

Setting 제거

↓

Detail

이 된다.

Compose에서는

navController.popBackStack()

한 줄로 처리한다.

<br>
특정 화면까지 이동
navController.popBackStack(
    route = "home",
    inclusive = false
)

결과

Home

List

Detail

↓

Home

중간 Stack이 모두 제거된다.

<br>
inclusive
inclusive = true

이면

Home도 제거

된다.

반대로

inclusive = false

이면

Home은 유지된다.

실무에서는 inclusive 여부 때문에 버그가 많이 발생하므로 항상 의도를 명확히 해야 한다.

<br>
7. 특정 조건에서 뒤로가기 막기

회원가입 화면처럼 반드시 입력을 완료해야 하는 경우가 있다.

BackHandler(enabled = true) {
    showDialog = true
}

Dialog를 띄운다.

AlertDialog(
    onDismissRequest = {},
    confirmButton = {
        Button(
            onClick = {
                navController.popBackStack()
            }
        ) {
            Text("나가기")
        }
    },
    dismissButton = {
        Button(
            onClick = {
                showDialog = false
            }
        ) {
            Text("취소")
        }
    },
    title = {
        Text("작성 중인 내용이 있습니다.")
    },
    text = {
        Text("정말 나가시겠습니까?")
    }
)

이런 방식은

게시글 작성
프로필 수정
회원가입
결제

등에서 자주 사용된다.

<br>
8. Confirm Dialog와 함께 사용하기

실무에서는 뒤로가기와 함께 가장 많이 사용하는 패턴이다.

var showDialog by remember {
    mutableStateOf(false)
}

BackHandler {
    showDialog = true
}

Dialog에서

Button(
    onClick = {
        navController.popBackStack()
    }
)

을 호출한다.

흐름은

뒤로가기

↓

Dialog 표시

↓

취소
    ↓
현재 화면 유지

또는

확인
    ↓
popBackStack()

이다.

사용자가 실수로 입력 내용을 잃는 것을 방지할 수 있다.

<br>
9. 실무에서 자주 발생하는 문제
1. BackHandler를 여러 개 등록
BackHandler { }

BackHandler { }

가장 안쪽 것만 실행된다.

불필요한 중복 등록은 피해야 한다.

<br>
2. Navigation도 처리하고 BackHandler도 처리
BackHandler {
    navController.popBackStack()
}

NavHost도 Back을 처리하는데 또 popBackStack()을 호출하면

화면이 두 번 이동
Stack 꼬임

등이 발생할 수 있다.

<br>
3. Dialog를 닫지 않고 popBackStack()
BackHandler {
    navController.popBackStack()
}

Dialog가 떠있는 상태라면

먼저 Dialog를 닫고

그 다음 화면을 종료하는 것이 자연스럽다.

<br>
4. enabled를 사용하지 않음

항상 BackHandler를 활성화하면

원래 Navigation의 뒤로가기를 모두 가로채게 된다.

필요한 순간에만

enabled = isEditing

처럼 사용하는 것이 좋다.

<br>
5. Android 버전 차이를 고려하지 않음

Predictive Back은 Android 14에서 가장 자연스럽게 동작한다.

Android 13 이하에서는 기존 뒤로가기 방식으로 동작할 수 있으므로, 특정 애니메이션이나 UX에 의존하지 않도록 설계하는 것이 좋다.

<br>
정리
Predictive Back은 Android 14의 새로운 뒤로가기 UX이다.
뒤로가기 결과를 애니메이션으로 미리 보여준다.
Compose에서는 기존 BackHandler를 그대로 사용할 수 있다.
NavHost는 기본적으로 시스템 뒤로가기를 처리하므로 불필요한 BackHandler 사용은 피한다.
화면 종료보다 Dialog 종료를 우선 처리하는 것이 자연스럽다.
enabled를 활용해 필요한 상황에서만 뒤로가기를 가로채는 것이 좋다.
popBackStack()과 Navigation의 기본 Back 처리 방식이 중복되지 않도록 주의해야 한다.
