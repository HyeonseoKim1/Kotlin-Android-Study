# Push Notification

## Push Notification이란?

Push Notification은 서버가 사용자 스마트폰으로 직접 알림을 보내는 기술이다.

즉:

> 사용자가 앱을 열지 않아도
> 서버가 먼저 사용자에게 정보를 전달할 수 있다.

대표적인 예시는 다음과 같다.

- “탑승구가 변경되었습니다.”
- “비행기가 30분 지연되었습니다.”
- “새 메시지가 도착했습니다.”
- “주문이 완료되었습니다.”

Push Notification은 모바일 앱의 가장 대표적인 실시간 알림 기술이다.

<br>

## Push Notification의 핵심 특징

### 앱이 꺼져 있어도 동작한다

```text
서버 → 운영체제 → 사용자 스마트폰
```

Push Notification은 앱 프로세스가 종료되어 있어도 동작한다.

이는 운영체제(Android/iOS)가 시스템 수준에서 직접 알림 시스템을 관리하기 때문이다. 

따라서 절전 상태, 백그라운드 상태, 앱 종료 상태에서도 알림 전달이 가능하다.


<br>


## Push Notification 동작 구조

```text
앱 서버
   ↓
Push 서버 (FCM / APNs)
   ↓
사용자 디바이스
```

앱 서버가 직접 스마트폰으로 보내는 것이 아니라, 중간 Push 서버를 거쳐 전달되는 구조로 동작한다.

<br>

## Push Notification 특징

| 항목 | 설명 |
|---|---|
| 앱 종료 상태 수신 | 가능 |
| 실시간성 | 빠름 |
| 연결 유지 | 없음 |
| 배터리 사용량 | 적음 |
| 사용 목적 | 알림 전달 |

<br>

## Push Notification 사용 사례

### 메신저

- 카카오톡
- Discord
- Slack  
→ 새 메시지 알림 전달

### 항공 앱

- 지연 알림
- 탑승구 변경
- 체크인 시작  
→ 정보 전달

### 배달 앱

- 주문 완료
- 배달 시작
- 배달 도착  
→ 알림 전달

<br>

## Push Notification의 장점

### 앱 종료 상태에서도 동작
가장 큰 장점이다.
사용자가 앱을 실행하지 않아도 중요 정보를 전달할 수 있다.

### 배터리 효율이 좋음
항상 서버와 연결을 유지하지 않기 때문에 배터리 사용량이 비교적 적다.

### 운영체제가 관리

Google / Apple이 전달 시스템을 관리하므로 안정성이 높다.

<br>

## Push Notification의 한계

### 완전한 실시간 기술은 아님
네트워크 상황이나 절전 정책에 따라 약간 지연될 수 있다.

### 양방향 통신 불가능
Push는 기본적으로 '서버 → 사용자' 방향의 단방향 전달 기술이다.
따라서 실시간 채팅 이나 실시간 데이터 스트리밍 같은 기능에는 적합하지 않다.

<br>

## Android와 iOS의 Push 시스템
| 플랫폼 | 사용 기술 |
|---|---|
| Android | FCM |
| iOS | APNs |

<br>

## 실제 서비스 구조

대부분의 모바일 서비스는:
- Push Notification
- WebSocket
을 함께 사용한다.

예:
- 앱 종료 상태 → Push 알림
- 앱 사용 중 → 실시간 연결(WebSocket)

---
<br>

참고

Firebase Cloud Messaging 공식 문서  
Apple Developer Documentation  
Android Developers
