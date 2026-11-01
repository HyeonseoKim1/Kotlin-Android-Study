# Android 앱 프로세스 실행 구조

<br>

# 목차

1. Android 앱 실행 구조란 무엇인가
2. Android 운영체제의 전체 아키텍처
3. Android 부팅과 Zygote의 생성
4. Zygote의 역할과 fork()
5. system_server와 시스템 서비스
6. 앱 실행 요청부터 프로세스 생성까지
7. ActivityThread와 앱 프로세스 초기화
8. Application과 Activity의 생성 순서
9. Binder IPC와 프로세스 간 통신
10. 앱 프로세스와 스레드의 관계
11. 프로세스 종료와 우선순위
12. 실제 기기에서 실행 과정 확인하기
13. 주의사항과 자주 하는 오해
14. 참고 자료
15. 정리

<br>

# Android 앱 실행 구조란 무엇인가

Android에서 앱 아이콘을 누르면 화면이 나타나기까지는 여러 시스템 구성
요소가 협력한다. 앱 프로세스가 없다면 운영체제는 먼저 프로세스를
준비해야 하고, 프로세스가 준비된 뒤에는 앱의 런타임과 메인 스레드를
초기화하고 실행할 Activity를 생성해야 한다.

이 과정은 앱 코드만으로 처리되지 않는다. Android 프레임워크의 시스템
서비스, Zygote, Linux 커널, ART 런타임, Binder IPC가 함께 관여한다.

대표적인 흐름은 다음과 같다.

```text
사용자가 앱 아이콘 선택
        |
        v
Launcher가 앱 실행 요청
        |
        v
system_server
  - ActivityTaskManagerService
  - ActivityManagerService
        |
        v
Zygote에 새 앱 프로세스 생성 요청
        |
        v
Zygote가 fork() 수행
        |
        v
새 앱 프로세스
  - ActivityThread.main()
  - 메인 Looper 준비
  - Application 생성 및 초기화
        |
        v
Activity 생성 및 생명주기 콜백
        |
        v
화면 표시
```

여기서 중요한 점은 모든 앱 실행이 항상 완전히 동일한 경로를 거치는 것은
아니라는 것이다. 이미 앱 프로세스가 살아 있다면 새 프로세스를 만들지
않고 기존 프로세스에 컴포넌트 실행을 요청할 수 있다. 또한 앱 실행 방식과
내부 클래스의 세부 동작은 Android 버전 및 AOSP 구현에 따라 달라질 수
있다.

**핵심 개념**: Android 앱 실행은 `Activity.onCreate()`를 호출하는 단순한
과정이 아니다. 시스템 서비스가 앱 프로세스의 존재 여부를 판단하고,
필요하면 Zygote를 통해 프로세스를 생성한 다음 앱 런타임과 컴포넌트를
초기화하는 과정이다.

<br>

# Android 운영체제의 전체 아키텍처

Android는 Linux 커널 위에 하드웨어 추상화 계층(HAL), 네이티브
라이브러리와 런타임, Android 프레임워크, 앱 계층을 쌓은 구조다.

```text
+------------------------------------------+
| Applications                            |
| 사용자 앱 / 시스템 앱                    |
+------------------------------------------+
| Java API Framework                       |
| ActivityManager / WindowManager 등       |
+------------------------------------------+
| Android Runtime (ART)                    |
| Core Libraries                           |
+------------------------------------------+
| Native C/C++ Libraries                   |
| SurfaceFlinger / Media / SQLite 등       |
+------------------------------------------+
| Hardware Abstraction Layer (HAL)         |
+------------------------------------------+
| Linux Kernel                             |
| 프로세스 / 메모리 / 보안 / 드라이버      |
+------------------------------------------+
```

| 계층                                주요 역할 |
|---|
| Applications                        사용자가 실행하는 앱과 시스템 앱 |
| Java API Framework                  Activity, Window, Package 등 앱 개발 API 및 시스템 서비스 제공 |
| Android Runtime (ART)               DEX 코드 실행, 가비지 컬렉션, 런타임 관리 |
| Native Libraries                    그래픽, 미디어, 데이터베이스 등 네이티브 기능 제공 |
| HAL                                 Android 프레임워크와 하드웨어 구현 사이의 표준 인터페이스 |
| Linux Kernel                        프로세스, 스레드, 메모리, 스케줄링, 보안, 디바이스 드라이버 관리 |

Android 앱 실행 구조를 이해할 때는 특히 세 영역을 구분해야 한다.

- **Linux 커널**: 실제 프로세스와 스레드를 생성하고 CPU 스케줄링,
  메모리, 권한 등을 관리한다.
- **Android Framework**: 어떤 앱 컴포넌트를 실행할지 결정하고 시스템
  정책을 적용한다.
- **ART**: 앱의 DEX 코드를 실행하는 런타임 환경을 제공한다.

**실전 팁**: `ActivityManagerService`가 프로세스를 관리한다고 해서 Linux
커널의 프로세스 관리 기능을 대체하는 것은 아니다. Android Framework는 앱
실행 정책과 시스템 차원의 조정을 담당하고, 실제 프로세스 생성과
스케줄링은 Linux 커널 기능을 사용한다.

<br>

# Android 부팅과 Zygote의 생성

Zygote는 Android 앱 프로세스 생성의 기반이 되는 특수한 프로세스다.
Android가 부팅될 때 초기화 과정에서 실행되며, 앱 실행에 필요한 공통
클래스와 리소스 등을 미리 준비한다.

일반적인 부팅 흐름은 다음과 같다.

```text
Bootloader
    |
    v
Linux Kernel
    |
    v
init 프로세스
    |
    v
네이티브 서비스 및 런타임 초기화
    |
    v
Zygote 시작
    |
    v
system_server 시작
    |
    v
Android 시스템 서비스 초기화
    |
    v
System UI / Launcher 등 실행
```

| 구성 요소       역할 |  |
|---|---|
| Bootloader | 기기 부팅 및 커널 로딩 |
| Linux Kernel | 운영체제 핵심 기능 초기화 |
| init | 초기 사용자 공간 프로세스, 서비스 시작 및 설정 |
| Zygote | 앱 프로세스 생성의 기반이 되는 런타임 프로세스 |
| system_server | 주요 Android 시스템 서비스를 실행하는 프로세스 |
| Launcher | 홈 화면과 앱 아이콘 등 앱 실행 진입점 제공 |

Zygote는 앱마다 반복해서 수행할 초기화 작업을 줄이기 위해 공통 환경을
먼저 준비한다. 이후 새로운 앱 프로세스가 필요하면 Zygote가 자신을 복제해
자식 프로세스를 만든다.

Android 기기 구성에 따라 32비트와 64비트 Zygote가 함께 존재할 수 있으며,
실제 프로세스 구성은 기기와 빌드 설정에 따라 다르다.

**실전 팁**: Zygote는 사용자가 실행하는 일반 앱이 아니다. 앱 프로세스를
빠르게 만들기 위해 부팅 시점부터 준비되는 시스템 프로세스다.

<br>

# Zygote의 역할과 fork()

Zygote의 가장 중요한 역할은 앱 프로세스 생성이다. 새 앱 프로세스가
필요하면 system_server 등의 시스템 구성 요소가 Zygote에 요청하고,
Zygote는 Linux의 `fork()` 시스템 호출을 사용해 자식 프로세스를 생성한다.

`fork()`는 호출한 프로세스를 기반으로 새로운 프로세스를 만드는 Linux
시스템 호출이다. 부모 프로세스와 자식 프로세스는 생성 직후 메모리 공간의
내용을 논리적으로 공유하는 것처럼 보이지만, 서로 독립된 프로세스 주소
공간을 갖는다.

이때 중요한 최적화가 **Copy-on-Write(CoW)** 다.

## Copy-on-Write란

부모와 자식 프로세스가 처음부터 메모리 내용을 모두 복사하는 대신, 동일한
물리 메모리 페이지를 공유하다가 한쪽에서 해당 페이지를 수정할 때 필요한
페이지를 복사하는 방식이다.

```text
fork() 직후

Zygote              App Process
   |                     |
   +------ 공유 페이지 ---+
              |
       동일한 물리 페이지
              |
              v
      앱 프로세스가 수정
              |
              v
      수정 대상 페이지만 복사

Zygote              App Process
   |                     |
공유 페이지          복사된 페이지
```

| 구분                                설명 |
|---|
| fork()                              부모 프로세스를 기반으로 자식 프로세스 생성 |
| Copy-on-Write                       실제 수정이 발생할 때 페이지를 복사하는 최적화 |
| 장점                                프로세스 생성 시점의 불필요한 메모리 복사 감소 |
| 주의점                              모든 메모리가 계속 공유되는 것은 아니며 수정된 페이지는 분리됨 |

Zygote가 공통 클래스를 미리 로드해 두면 새 앱 프로세스가 이를 활용할 수
있다. Copy-on-Write는 이런 공통 메모리 페이지를 효율적으로 공유하는 데
도움을 준다.

다만 앱 프로세스가 생성된 뒤에는 앱별 초기화와 메모리 변경이 발생하므로
실제 메모리 사용량이 완전히 동일하게 유지되는 것은 아니다.

**실전 팁**: Zygote와 Copy-on-Write를 함께 이해하면 Android가 앱 실행
시간을 줄이고 메모리 사용을 최적화하는 이유를 설명할 수 있다. `fork()`
자체가 앱 실행을 모두 완료하는 것은 아니며, 자식 프로세스는 이후 앱 전용
초기화 과정을 거친다.

<br>

# system_server와 시스템 서비스

`system_server`는 Android Framework의 주요 시스템 서비스가 실행되는
프로세스다. 앱은 시스템 서비스와 직접 메모리를 공유하는 대신 Binder
IPC를 통해 요청을 전달한다.

대표적인 서비스는 다음과 같다.

| 서비스                              주요 역할 |
|---|
| ActivityManagerService (AMS)        앱 프로세스와 서비스 등 프로세스 관련 관리 |
| ActivityTaskManagerService (ATMS)   Activity 및 Task 실행·전환 관리 |
| PackageManagerService (PMS)         설치된 패키지, 컴포넌트, 권한 등 패키지 정보 관리 |
| WindowManagerService (WMS)          윈도우의 생성, 배치, 포커스 등 관리 |
| PowerManagerService                 전원 상태 및 WakeLock 등 전원 정책 관리 |
| UserManagerService                  Android 사용자 및 프로필 관리 |

AMS와 ATMS는 이름과 책임이 비슷해 보이지만 역할이 구분된다. Activity와
Task의 실행·전환 정책은 ATMS가 담당하고, 프로세스 관리 등은 AMS가
담당한다. 실제 구현에서는 서로 협력하며, 내부 구조는 Android 버전에 따라
변경될 수 있다.

앱 실행 시에는 시스템이 실행 대상 컴포넌트와 프로세스 상태를 확인한다.
프로세스가 없으면 프로세스 생성 절차를 진행하고, 이미 존재한다면 해당
프로세스에 컴포넌트 실행 요청을 전달할 수 있다.

**실전 팁**: 앱에서 `startActivity()`를 호출하면 해당 Activity가 즉시
현재 앱 프로세스 안에서 생성되는 것이 아니다. 요청은 Android 시스템에
전달되고, 시스템이 실행 대상과 Task 상태를 판단한 다음 적절한 프로세스에
실행을 요청한다.

<br>

# 앱 실행 요청부터 프로세스 생성까지

사용자가 Launcher에서 앱 아이콘을 누르면 Launcher는 실행할 앱의 진입
Activity를 실행하도록 요청한다. 시스템은 패키지 및 Activity 정보를
확인하고 실행할 프로세스가 존재하는지 판단한다.

프로세스가 없는 경우의 개념적인 흐름은 다음과 같다.

```text
Launcher
   |
   | startActivity 요청
   v
system_server
   |
   | 실행 대상 및 프로세스 상태 확인
   v
프로세스가 존재하는가?
   |
   +-- Yes --> 기존 앱 프로세스에 실행 요청
   |
   +-- No
         |
         v
      Zygote에 프로세스 생성 요청
         |
         v
      fork()
         |
         v
      새 앱 프로세스 생성
         |
         v
      ActivityThread 초기화
         |
         v
      Application 및 Activity 실행
```

실제 AOSP 구현에서는 Zygote와 system_server 사이의 통신 프로토콜,
프로세스 시작 인자, 런타임 초기화 단계 등이 더 복잡하다. 위 흐름은
개념을 이해하기 위한 단순화된 표현이다.

앱 프로세스가 이미 존재하는 경우에는 `fork()`를 다시 수행하지 않는다.
시스템은 기존 프로세스에 새로운 Activity 실행 또는 기존 Activity 재사용
등의 요청을 전달한다.

| 상황                                일반적인 동작 |
|---|
| 앱 프로세스가 없음                  새 프로세스를 생성한 뒤 컴포넌트 실행 |
| 앱 프로세스가 존재함                기존 프로세스에서 컴포넌트 실행 |
| 기존 Task의 Activity 재사용 가능    Launch Mode 및 Intent Flag 등에 따라 기존 인스턴스 재사용 가능 |
| 프로세스가 종료된 상태에서 복귀     필요하면 프로세스를 다시 생성하고 상태 복원 절차 수행 |

**실전 팁**: 앱 아이콘을 다시 눌렀을 때 항상 `Application.onCreate()`가
호출되는 것은 아니다. 프로세스가 살아 있고 기존 앱 상태를 재사용하는
상황이라면 Application을 새로 생성하지 않는다.

<br>

# ActivityThread와 앱 프로세스 초기화

새 앱 프로세스가 생성되면 앱 프로세스 내부에서 `ActivityThread`가
초기화된다. 클래스 이름에 Activity가 포함되어 있지만 Activity 인스턴스
하나를 의미하지 않는다. 앱 프로세스의 메인 스레드와 컴포넌트 실행을
연결하는 핵심 역할을 한다.

앱 프로세스의 진입 흐름은 개념적으로 다음과 같다.

```text
새 앱 프로세스
      |
      v
ActivityThread.main()
      |
      v
메인 Looper 준비
      |
      v
ActivityThread 인스턴스 및 시스템 연결 초기화
      |
      v
시스템으로부터 앱 바인딩 요청 처리
      |
      v
Application 생성 및 초기화
      |
      v
실행 대상 Activity 생성
```

`ActivityThread.main()`은 앱 프로세스의 메인 진입점으로 사용된다. 여기서
메인 스레드의 메시지 루프가 준비되고, 이후 시스템에서 전달하는 앱
컴포넌트 실행 요청을 처리할 수 있게 된다.

ActivityThread는 시스템 프로세스와 앱 프로세스 사이에서 Binder 기반
통신을 통해 실행 관련 요청을 주고받는다. 시스템의 요청은 앱 프로세스
내부에서 적절한 메시지 처리와 생명주기 작업으로 이어진다.

| 구성 요소                           역할 |
|---|
| ActivityThread                      앱 프로세스의 메인 실행 환경과 컴포넌트 실행 연결 |
| main()                              앱 프로세스 초기 진입점 |
| Looper                              메인 스레드의 메시지 이벤트 루프 |
| Handler                             메시지를 Looper에 전달하고 처리 |
| ApplicationThread                   시스템에 앱 프로세스의 상태 및 실행 관련 동작을 연결하는 Binder 측 구현 |

`ApplicationThread`는 `ActivityThread` 내부에 정의된 Binder 기반
구현으로, 시스템이 앱 프로세스에 실행 관련 요청을 전달하는 통로 역할을
한다. 이름 때문에 `Application` 클래스와 혼동하기 쉽지만 서로 다른
개념이다.

**실전 팁**: `ActivityThread`는 일반 앱 코드에서 직접 생성하거나
호출하는 클래스가 아니다. Android Framework 내부 구현을 이해하기 위한
개념으로 접근해야 하며, 앱 개발에서는 공식 API를 사용하는 것이
바람직하다.

<br>

# Application과 Activity의 생성 순서

앱 프로세스가 준비되면 시스템은 앱의 Application 객체를 생성하고
초기화한다. 이후 실행 요청을 받은 Activity가 생성되고 생명주기 콜백이
진행된다.

일반적인 최초 실행 흐름은 다음과 같다.

```text
ActivityThread 초기화
       |
       v
Application 객체 생성
       |
       v
Application.attachBaseContext()
       |
       v
Application.onCreate()
       |
       v
Activity 객체 생성
       |
       v
Activity.attach()
       |
       v
Activity.onCreate()
       |
       v
Activity.onStart()
       |
       v
Activity.onResume()
```

Application은 앱 프로세스당 일반적으로 하나 생성된다. 같은 프로세스에서
여러 Activity를 실행해도 Application 객체를 매번 새로 생성하지 않는다.

다만 앱이 여러 프로세스를 사용하도록 구성되어 있다면 각 프로세스에서
별도의 Application 인스턴스가 생성될 수 있다. `android:process` 속성을
사용하는 컴포넌트나 별도 프로세스 설정이 이에 해당한다.

| 콜백                                호출 시점 및 역할 |
|---|
| `attachBaseContext()`               Application에 기본 Context 연결 |
| `Application.onCreate()`            앱 프로세스의 Application 초기화 |
| `Activity.onCreate()`               Activity 최초 생성 및 초기 UI 구성 |
| `Activity.onStart()`                Activity가 사용자에게 보이기 시작 |
| `Activity.onResume()`               Activity가 사용자와 상호작용 가능한 상태로 진입 |

`Application.onCreate()`는 앱 프로세스가 생성될 때 호출되는 초기화
지점이지, 앱이 화면에 나타날 때마다 호출되는 지점은 아니다.

**실전 팁**: Application에 무거운 초기화 작업을 많이 넣으면 앱의 콜드
스타트(Cold Start)가 느려질 수 있다. 앱 시작에 반드시 필요한 작업과 실제
기능을 사용할 때 지연 초기화해도 되는 작업을 분리하는 것이 좋다.

<br>

# Binder IPC와 프로세스 간 통신

Android에서는 앱 프로세스와 system_server 등 서로 다른 프로세스가 자주
통신한다. 각 프로세스는 독립된 가상 주소 공간을 사용하므로 일반적인 함수
호출처럼 다른 프로세스의 메모리와 객체에 직접 접근할 수 없다.

Android는 이러한 프로세스 간 통신(IPC, Inter-Process Communication)을
위해 Binder를 핵심 메커니즘으로 사용한다.

```text
App Process                    system_server
     |                              |
     | Binder Proxy                 |
     |                              |
     v                              |
Binder Driver (Linux Kernel)        |
     |                              |
     +----------------------------->|
                                    |
                              Binder Stub
                                    |
                                    v
                              System Service
```

| 구성 요소                           역할 |
|---|
| Binder Proxy                        클라이언트 측에서 원격 객체처럼 호출할 수 있도록 하는 프록시 |
| Binder Stub                         서버 측에서 전달받은 요청을 실제 구현으로 연결 |
| Binder Driver                       커널에서 Binder 트랜잭션 및 IPC 전달을 중개 |
| AIDL                                Binder 인터페이스를 정의하는 인터페이스 정의 언어 |

Binder는 단순히 데이터를 전달하는 기능만 제공하는 것이 아니다. 호출 대상
식별, 트랜잭션 전달, 스레드 처리 등 Android의 IPC 구조를 구성한다.

앱에서 시스템 서비스를 사용하는 예시는 다음과 같다.

```kotlin
// 시스템 서비스 획득 예시
val notificationManager =
    getSystemService(NotificationManager::class.java)

// 앱은 공개 API를 호출하지만,
// 내부적으로는 시스템 서비스와 IPC가 수행될 수 있다.
```

모든 시스템 API 호출이 항상 동일한 방식으로 Binder IPC를 수행하는 것은
아니다. 일부 작업은 앱 프로세스 내부에서 처리되고, 시스템 서비스가
필요한 작업은 Binder 등을 통해 시스템 프로세스에 요청한다.

## Binder 사용 시 주의할 점

Binder 호출은 프로세스 간 통신이므로 일반적인 로컬 함수 호출과 비용 및
실패 조건이 다르다.

- IPC 과정에서 데이터 직렬화와 전달 비용이 발생할 수 있다.
- Binder 호출이 오래 걸리면 호출한 스레드가 대기할 수 있다.
- 메인 스레드에서 오래 걸리는 작업을 수행하면 UI 응답성이 떨어질 수
  있다.
- Binder 트랜잭션 데이터에는 크기 제한이 있으므로 대량 데이터를
  무리하게 전달하면 `TransactionTooLargeException`이 발생할 수 있다.

**실전 팁**: 시스템 API를 호출하는 코드가 한 줄이라고 해서 내부 작업도
항상 가벼운 것은 아니다. API가 Binder를 통해 다른 프로세스에 요청하는지,
호출이 동기적인지, 메인 스레드에서 실행해도 되는지 고려해야 한다.

<br>

# 앱 프로세스와 스레드의 관계

Android에서 프로세스와 스레드는 서로 다른 개념이다.

- **프로세스(Process)**: 실행 중인 프로그램의 독립된 실행 환경과 주소
  공간을 가진다.
- **스레드(Thread)**: 프로세스 내부에서 실제 명령을 실행하는 흐름이다.

일반적인 Android 앱은 프로세스가 생성되면 메인 스레드를 갖는다. 앱은
필요에 따라 추가 스레드를 생성할 수 있으며, 같은 프로세스 안의
스레드들은 메모리 공간을 공유한다.

```text
App Process
|
+-- Main Thread
|     +-- UI 이벤트 처리
|     +-- Activity 생명주기 콜백
|     +-- View 및 Compose UI 작업
|
+-- Worker Thread
|     +-- 파일 I/O
|     +-- 네트워크 작업
|     +-- 백그라운드 계산
|
+-- Other Threads
      +-- 라이브러리 및 런타임 내부 작업
```

| 구분                    프로세스                스레드 |
|---|
| 메모리                  독립된 가상 주소 공간   같은 프로세스의 메모리 공유 |
| 생성 비용               상대적으로 큼           일반적으로 프로세스보다 작음 |
| 통신                    IPC 필요                공유 메모리 등으로 통신 가능 |
| 종료 영향               프로세스 종료 시 내부   해당 스레드만 종료 가능 스레드도 종료 |

메인 스레드는 UI 이벤트와 생명주기 콜백 등을 처리한다. 메인 스레드에서
긴 작업을 수행하면 프레임 처리가 지연되고, 일정 시간 이상 응답하지
않으면 ANR(Application Not Responding)이 발생할 수 있다.

**실전 팁**: Coroutine을 사용한다고 해서 자동으로 별도 스레드에서
실행되는 것은 아니다. Coroutine은 실행을 중단하고 재개할 수 있는 작업
단위이며, 실제 실행 스레드는 Dispatcher와 실행 환경에 따라 결정된다.

<br>

# 프로세스 종료와 우선순위

Android는 메모리가 부족해지면 프로세스를 종료해 메모리를 확보할 수 있다.
앱이 백그라운드로 이동했다고 해서 즉시 프로세스가 종료되는 것은 아니다.

Android는 사용자가 현재 보고 있거나 상호작용하는 앱을 우선 보호하고,
중요도가 낮은 프로세스부터 종료 후보로 고려한다.

대표적인 프로세스 중요도 범주는 다음과 같다.

| 프로세스 상태                       설명 |
|---|
| Foreground Process                  현재 사용자와 상호작용 중이거나 중요한 포그라운드 작업을 수행하는 프로세스 |
| Visible Process                     사용자에게 보이지만 포그라운드에서 직접 상호작용하지 않는 프로세스 |
| Service Process                     서비스 실행 등으로 중요한 작업을 수행하는 프로세스 |
| Cached Process                      현재 사용자에게 보이지 않고 당장 실행할 컴포넌트가 없는 캐시된 프로세스 |

실제 Android의 프로세스 중요도는 단순히 위 네 가지 이름만으로 결정되지
않는다. 포그라운드 서비스, 바인딩된 서비스, 시스템과의 상호작용 등
다양한 조건이 반영된다.

## Low Memory Killer와 lmkd

Android는 메모리 압박 상황에서 중요도가 낮은 프로세스를 종료할 수 있다.
최신 Android에서는 사용자 공간 데몬인 `lmkd`(Low Memory Killer Daemon)가
메모리 압박을 감지하고 프로세스 종료를 조정하는 데 관여한다.

과거 커널 기반 Low Memory Killer 구현과 현재의 `lmkd` 기반 동작은
Android 버전과 기기 구현에 따라 차이가 있다.

```text
메모리 압박 발생
       |
       v
메모리 상태 및 프로세스 중요도 판단
       |
       v
종료 대상 프로세스 선택
       |
       v
프로세스 종료
       |
       v
메모리 확보
```

프로세스가 종료되면 프로세스 내부의 객체, 싱글톤, 메모리 캐시 등은 모두
사라진다. 앱이 다시 실행되면 새로운 프로세스가 생성되고 필요한 상태를
복원해야 한다.

따라서 앱은 프로세스가 언제든 종료될 수 있다는 전제로 상태를 설계해야
한다.

**실전 팁**: `onDestroy()`가 항상 호출된다고 가정하면 안 된다. 시스템이
프로세스를 강제로 종료하는 경우 Activity나 Application의 종료 콜백이
실행되지 않을 수 있다. 중요한 데이터는 종료 콜백에만 의존하지 말고
적절한 시점에 영속화해야 한다.

<br>

# 실제 기기에서 실행 과정 확인하기

Android Studio의 터미널 또는 별도 터미널에서 ADB(Android Debug Bridge)를
사용하면 실제 앱 프로세스를 확인할 수 있다.

아래 명령어에서 `com.example.app`은 확인하려는 앱의 패키지명으로 바꾼다.

## 1. 앱 프로세스 확인

```bash
adb shell ps -A | grep com.example.app
```

현재 실행 중인 프로세스 목록에서 앱 패키지명을 검색한다.

PID(Process ID)는 운영체제가 프로세스를 구분하기 위해 부여하는 식별자다.

## 2. PID 확인

```bash
adb shell pidof com.example.app
```

앱 프로세스의 PID를 확인할 수 있다. 앱이 실행 중이지 않다면 결과가 없을
수 있다.

## 3. 앱 강제 종료

```bash
adb shell am force-stop com.example.app
```

Android의 Activity Manager 명령을 통해 앱을 강제 종료한다. 이 명령은
단순히 Activity 화면을 닫는 것과 다르며 앱 프로세스를 종료하고 패키지를
강제 중지 상태로 만든다.

## 4. 앱 실행

```bash
adb shell monkey -p com.example.app 1
```

테스트 목적으로 앱 실행 이벤트를 발생시킨다. 앱의 런처 Activity가 실행
가능한 상태여야 한다.

## 5. PID 다시 확인

```bash
adb shell pidof com.example.app
```

종료 전후 PID를 비교한다. 일반적으로 새 프로세스가 생성되면 새로운 PID가
부여된다. 단, PID는 재사용될 수 있으므로 숫자가 달라졌다는 사실만으로
모든 실행 과정을 증명할 수는 없다.

## 6. 프로세스 및 시스템 로그 확인

```bash
# 앱 관련 로그 검색
adb logcat | grep com.example.app

# Activity 및 프로세스 관련 로그 검색
adb logcat | grep -E "ActivityTaskManager|ActivityManager|Zygote"
```

로그는 Android 버전과 제조사 빌드에 따라 형식과 태그가 다를 수 있다.
필요한 경우 Android Studio Logcat에서 패키지명과 프로세스 ID를 기준으로
필터링한다.

## 실습 시나리오

1. 앱을 실행하고 PID를 기록한다.
2. 홈 버튼을 눌러 앱을 백그라운드로 보낸다.
3. `pidof`로 프로세스가 유지되는지 확인한다.
4. 다시 앱으로 돌아와 기존 프로세스가 유지되는지 확인한다.
5. `am force-stop`으로 앱을 강제 종료한다.
6. 앱을 다시 실행하고 PID와 초기화 로그를 비교한다.

**관찰 포인트**: 백그라운드로 이동하는 것과 프로세스가 종료되는 것은
다르다. 프로세스가 살아 있다면 Application 초기화가 다시 수행되지 않을
수 있지만, 프로세스가 종료된 뒤 다시 실행되면 새로운 Application과
Activity 인스턴스가 생성된다.

<br>

# 주의사항과 자주 하는 오해

1. **앱을 종료하면 항상 `Application.onTerminate()`가 호출된다.**
   일반적인 실제 기기 환경에서는 프로세스 종료 시 이 콜백이 호출된다고
   보장할 수 없다.
2. **Activity가 종료되면 앱 프로세스도 종료된다.** Activity는 프로세스
   내부의 컴포넌트다. Activity가 없어도 서비스 등 다른 구성 요소 때문에
   프로세스가 유지될 수 있다.
3. **앱을 백그라운드로 보내면 프로세스가 바로 종료된다.** 백그라운드
   전환과 프로세스 종료는 별개다. 시스템은 메모리 상황과 프로세스
   중요도 등을 고려한다.
4. **Zygote가 앱의 모든 초기화를 완료한다.** Zygote는 공통 런타임
   환경을 준비하고 프로세스 생성을 지원한다. 앱별 Application 및
   Activity 초기화는 새 앱 프로세스에서 진행된다.
5. **`fork()`가 메모리를 전부 즉시 복사한다.** Copy-on-Write를 통해
   실제 수정이 발생할 때 페이지 복사가 이뤄질 수 있다.
6. **system_server가 Linux 커널의 프로세스 관리 기능을 대신한다.**
   system_server는 Android 시스템 정책과 서비스를 담당하고, 실제
   프로세스와 스케줄링은 Linux 커널이 담당한다.
7. **ActivityThread는 Activity 객체 하나를 의미한다.** ActivityThread는
   앱 프로세스의 메인 실행 환경과 컴포넌트 실행을 연결하는 Framework
   내부 클래스다.
8. **Coroutine은 항상 새로운 스레드를 만든다.** Coroutine은 스레드와
   다른 개념이며 Dispatcher에 따라 실행 스레드가 결정된다.
9. **앱 프로세스가 죽으면 모든 상태가 자동으로 복원된다.** 시스템이
   일부 UI 상태 복원을 지원하더라도 앱의 모든 메모리 상태가 보존되는
   것은 아니다. 중요한 데이터는 별도로 저장해야 한다.
10. **모든 Android 버전에서 내부 실행 순서와 클래스 구조가 동일하다.**
    AOSP 구현은 버전이 올라가면서 변경될 수 있으므로 내부 동작을 조사할
    때는 대상 Android 버전을 함께 확인해야 한다.

<br>

# 참고 자료

- [Android Open Source Project -
  Zygote](https://source.android.com/docs/core/runtime/zygote)
- [Android Open Source Project - Android Runtime
  (ART)](https://source.android.com/docs/core/runtime)
- [Android Developers - Processes and
  Threads](https://developer.android.com/guide/components/processes-and-threads)
- [Android Developers - Processes and App
  Lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle)
- [Android Open Source Project -
  Binder](https://source.android.com/docs/core/architecture/ipc/binder-overview)
- [Android Open Source Project - Low Memory Killer
  Daemon](https://source.android.com/docs/core/perf/lmkd)
- [Android Open Source Project -
  Architecture](https://source.android.com/docs/core/architecture)

<br>

# 정리

Android 앱 실행은 Launcher의 실행 요청에서 시작해 system_server의 실행
판단, Zygote의 프로세스 생성, ActivityThread의 초기화, Application 및
Activity 생성으로 이어지는 여러 계층의 협력 과정이다.

Zygote는 공통 런타임 환경을 미리 준비하고 `fork()`와 Copy-on-Write를
활용해 앱 프로세스를 효율적으로 생성한다. system_server는 Activity와
프로세스 등 Android 시스템 차원의 실행 정책을 관리하며, 앱 프로세스와
시스템 서비스는 Binder IPC를 통해 통신한다. 새 앱 프로세스에서는
ActivityThread가 메인 실행 환경을 준비하고 Application과 Activity의
초기화를 진행한다.

앱 프로세스는 백그라운드로 이동했다고 즉시 종료되는 것이 아니며,
Android는 메모리 압박과 프로세스 중요도를 고려해 종료 대상을 결정한다.
따라서 앱 개발자는 프로세스가 언제든 종료될 수 있다는 점을 고려해 상태
저장과 복원 구조를 설계해야 한다.

이 구조를 이해하면 앱 실행 속도, Cold Start, ANR, 프로세스 종료, 상태
복원, 시스템 서비스 호출과 같은 Android 실무 이슈를 더 깊이 있게 분석할
수 있다.
