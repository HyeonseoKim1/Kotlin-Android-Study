# Process Death

## 목차

1. Process란?
2. Android 프로세스 관리
3. Process Death란?
4. Process Death가 발생하는 이유
5. Activity 종료와 Process Death의 차이
6. Process Death 발생 시 생명주기
7. ViewModel은 왜 사라지는가?
8. SavedStateHandle이 필요한 이유
9. Process Death 대응 방법
10. 디버깅 및 테스트 방법
11. 헷갈리기 쉬운 개념 비교
12. 정리

# 1. Process란?

## Process란?

Process는 실행 중인 애플리케이션을 운영체제가 관리하기 위한 실행 단위이다.

Android에서 앱을 실행하면 Linux 기반의 새로운 프로세스가 생성되고, 해당 프로세스 안에서 Activity, Service, BroadcastReceiver 등이 함께 실행된다.

하나의 앱은 일반적으로 하나의 프로세스를 사용하며, 앱이 종료될 때까지 필요한 객체와 메모리를 유지한다.

예를 들어 다음과 같은 객체들은 모두 같은 프로세스 안에서 동작한다.

- Activity
- ViewModel
- Singleton 객체
- Coroutine
- Compose Runtime
- Repository

즉, 우리가 사용하는 대부분의 객체는 모두 프로세스 안에서 살아간다.

<br>

## 왜 Process를 알아야 할까?

많은 개발자는 화면이 종료되면 Activity만 사라진다고 생각한다.

하지만 Android에서는 시스템이 필요하면 앱의 **프로세스 자체를 종료**할 수 있다.

이 경우에는 Activity뿐 아니라 메모리에 존재하던 모든 객체가 함께 삭제된다.

따라서 다음과 같은 현상이 발생할 수 있다.

- ViewModel 데이터가 사라진다.
- remember 상태가 초기화된다.
- Singleton 객체가 다시 생성된다.
- Coroutine이 모두 종료된다.

이러한 이유 때문에 Process의 개념을 이해하는 것은 Android 상태 관리의 기본이 된다.

<br>

# 2. Android 프로세스 관리

Android는 메모리가 제한된 모바일 환경에서 동작하기 때문에 시스템이 직접 프로세스를 관리한다.

프로세스는 중요도(Priority)에 따라 관리되며, 메모리가 부족하면 중요도가 낮은 프로세스부터 종료된다.

일반적으로 우선순위는 다음과 같다.

1. Foreground Process
2. Visible Process
3. Service Process
4. Cached Process

현재 사용자가 보고 있는 화면은 가장 높은 우선순위를 가진다.

반면 백그라운드에서 오랫동안 사용하지 않은 앱은 Cached Process가 되며, 메모리가 부족하면 가장 먼저 종료될 수 있다.

즉, 사용자가 앱을 종료하지 않았더라도 Android 시스템이 언제든 프로세스를 종료할 수 있다.

<br>

# 3. Process Death란?

Process Death는 Android 시스템이 실행 중인 앱의 프로세스를 강제로 종료하는 것을 의미한다.

여기서 중요한 점은 **사용자가 앱을 종료한 것이 아니라 시스템이 메모리 확보를 위해 종료한다는 것**이다.

프로세스가 종료되면 다음과 같은 데이터가 모두 사라진다.

- ViewModel
- Singleton 객체
- Coroutine
- Compose 상태(remember)
- 메모리에 저장된 모든 객체

반면 DataStore, Room, 파일 등에 저장된 데이터는 그대로 유지된다.

Process Death 이후 사용자가 앱으로 다시 돌아오면 Android는 새로운 프로세스를 생성하고 앱을 처음부터 다시 실행한다.

<br>

# 4. Process Death가 발생하는 이유

Android가 Process Death를 발생시키는 대표적인 상황은 다음과 같다.

- 메모리가 부족한 경우
- 백그라운드에 오래 머문 경우
- 다른 앱이 많은 메모리를 사용하는 경우
- 시스템 자원 확보가 필요한 경우

이는 Android가 전체 시스템의 안정성을 유지하기 위해 수행하는 정상적인 동작이다.

따라서 Process Death는 예외적인 상황이 아니라 Android 앱이라면 항상 고려해야 하는 상황이다.

<br>

# 5. Activity 종료와 Process Death의 차이

Activity가 종료되는 것과 Process Death는 완전히 다른 개념이다.

| 구분 | Activity 종료 | Process Death |
|------|---------------|---------------|
| 종료 대상 | Activity | 프로세스 전체 |
| ViewModel | 유지될 수도 있음 | 삭제 |
| Singleton | 유지 | 삭제 |
| 메모리 | 대부분 유지 | 모두 삭제 |
| 앱 실행 | 계속 | 처음부터 다시 시작 |

Activity만 종료되었다고 해서 앱이 종료된 것은 아니다.

하지만 Process Death가 발생하면 앱 전체가 처음 실행되는 상태로 돌아간다.

<br>

# 6. Process Death 발생 시 생명주기

Process Death 이후 앱을 다시 실행하면 다음과 같은 순서로 동작한다.

1. 새로운 프로세스 생성
2. Application 생성
3. Activity 생성
4. SavedInstanceState 복원
5. ViewModel 생성
6. 화면 표시

즉, 기존 ViewModel이 살아나는 것이 아니라 새로운 ViewModel이 생성된다.

따라서 필요한 데이터는 SavedStateHandle이나 영구 저장소에서 다시 복원해야 한다.

<br>

# 7. ViewModel은 왜 사라지는가?

ViewModel은 Activity나 Fragment보다 오래 살아남을 수 있지만, 프로세스보다 오래 살아남지는 못한다.

ViewModel 역시 메모리에 존재하는 객체이기 때문이다.

따라서 Process Death가 발생하면 ViewModel도 함께 삭제된다.

많은 개발자가 ViewModel이 영구적으로 데이터를 보관한다고 오해하지만, ViewModel은 화면 상태를 관리하기 위한 메모리 객체일 뿐이다.

<br>

# 8. SavedStateHandle이 필요한 이유

SavedStateHandle은 Process Death 이후에도 필요한 최소한의 상태를 복원하기 위해 제공되는 API이다.

예를 들어 다음과 같은 데이터는 SavedStateHandle에 저장하기 적합하다.

- 검색어
- 선택된 탭
- 스크롤 위치
- 선택한 아이템 ID

반면 이미지나 대용량 리스트처럼 큰 데이터는 저장하지 않는 것이 좋다.

<br>

# 9. Process Death 대응 방법

Process Death를 고려한 앱을 만들기 위해서는 상태의 성격에 따라 저장 위치를 선택해야 한다.

- UI 상태 → remember
- 화면 회전 대응 → rememberSaveable
- 화면 상태 관리 → ViewModel
- 화면 복원 → SavedStateHandle
- 영구 저장 → DataStore 또는 Room

"이 데이터가 언제까지 유지되어야 하는가?"를 먼저 생각하면 적절한 저장 위치를 선택할 수 있다.

<br>

# 10. 디버깅 및 테스트 방법

Developer Options의 **Don't keep activities**를 활성화하면 Activity가 종료되는 상황을 쉽게 테스트할 수 있다.

또한 adb 명령어를 사용하면 Process Death를 직접 재현할 수 있다.

```bash
adb shell am kill <패키지명>
```

테스트 시에는 다음 사항을 확인하면 좋다.

- 입력값이 복원되는가?
- ViewModel이 새로 생성되는가?
- SavedStateHandle이 정상적으로 동작하는가?
- 로그인 상태가 유지되는가?

<br>

# 11. 헷갈리기 쉬운 개념 비교

## 1. Activity Destroy vs Process Death

| 구분 | Activity Destroy | Process Death |
|------|------------------|---------------|
| 종료 대상 | Activity 하나 | 애플리케이션 프로세스 전체 |
| ViewModel | 유지될 수도 있음 | 모두 삭제 |
| 메모리 | 대부분 유지 | 모두 초기화 |
| 발생 원인 | 뒤로가기, 화면 회전 등 | 시스템 메모리 부족, 장시간 백그라운드 등 |
| 복원 방식 | Activity 재생성 | 앱 프로세스부터 새로 생성 |

<br>

## 2. Process Death vs Configuration Change

| 구분 | Configuration Change | Process Death |
|------|----------------------|---------------|
| 원인 | 화면 회전, 다크모드 변경, 언어 변경 | 시스템이 프로세스 종료 |
| ViewModel | 유지 | 삭제 |
| 메모리 | 유지 | 삭제 |

<br>

## 3. remember vs rememberSaveable

| 구분 | remember | rememberSaveable |
|------|----------|------------------|
| Recomposition | 유지 | 유지 |
| 화면 회전 | 초기화 | 유지 |
| Process Death | 초기화 | 복원 가능 |

<br>

## 4. ViewModel vs SavedStateHandle

| 구분 | ViewModel | SavedStateHandle |
|------|-----------|------------------|
| 역할 | 화면 상태 관리 | 상태 복원 |
| 저장 위치 | 메모리 | SavedState |
| Process Death | 삭제 | 복원 가능 |

<br>

## 5. SavedStateHandle vs DataStore

| 구분 | SavedStateHandle | DataStore |
|------|------------------|-----------|
| 목적 | 화면 상태 복원 | 영구 데이터 저장 |
| 대표 데이터 | 검색어, 탭 | 로그인 정보, 설정 |

<br>

## 6. 언제 무엇을 사용해야 할까?

| 상황 | 추천 |
|------|------|
| Compose 상태 유지 | remember |
| 화면 회전 대응 | rememberSaveable |
| 화면 상태 관리 | ViewModel |
| Process Death 복원 | SavedStateHandle |
| 영구 저장 | DataStore 또는 Room |

<br>

# 7. ViewModel은 왜 사라지는가?

많은 개발자가 ViewModel은 화면이 회전해도 유지되기 때문에 항상 살아있는 객체라고 생각한다. 하지만 ViewModel이 유지되는 것은 **Configuration Change**까지이며, **Process Death**가 발생하면 함께 삭제된다.

ViewModel은 메모리에 존재하는 객체이기 때문에 프로세스가 종료되면 운영체제가 메모리를 회수하면서 ViewModel도 함께 제거된다.

즉, Process Death 이후에는 기존 ViewModel을 다시 사용하는 것이 아니라 새로운 ViewModel 인스턴스가 생성된다.

따라서 ViewModel에는 화면을 표시하기 위한 상태를 관리하되, Process Death 이후에도 반드시 복원되어야 하는 데이터는 별도로 저장해야 한다.

예를 들어 검색 화면이라면 다음과 같이 역할을 분리하는 것이 좋다.

- 검색 결과 목록 → ViewModel
- 현재 검색어 → SavedStateHandle
- 사용자 설정 → DataStore
- 서버 데이터 → Repository를 통해 다시 조회

ViewModel은 화면 상태를 관리하기 위한 객체이지 영구 저장소가 아니라는 점을 기억해야 한다.

<br>

# 8. SavedStateHandle이 필요한 이유

Process Death가 발생하면 ViewModel도 함께 삭제되기 때문에 화면을 이전 상태로 복원할 방법이 필요하다.

이를 위해 Android에서는 SavedStateHandle을 제공한다.

SavedStateHandle은 ViewModel 내부에서 사용할 수 있는 상태 저장소이며, Process Death 이후에도 필요한 최소한의 데이터를 자동으로 복원할 수 있다.

대표적으로 저장하기 좋은 데이터는 다음과 같다.

- 검색어
- 선택된 탭
- 현재 페이지 번호
- 선택한 아이템 ID
- 스크롤 위치

반대로 다음과 같은 데이터는 저장하지 않는 것이 좋다.

- 이미지
- 대용량 리스트
- Bitmap
- API 응답 전체
- Room에서 조회 가능한 데이터

SavedStateHandle은 화면을 이전 상태처럼 복원하기 위한 저장소이지, 모든 데이터를 저장하는 용도가 아니다.

따라서 필요한 최소한의 상태만 저장하고, 나머지 데이터는 Repository나 데이터베이스에서 다시 가져오는 것이 권장된다.

<br>

## 9. 헷갈리기 쉬운 개념 비교

Process Death는 Android에서 가장 많이 혼동하는 개념 중 하나이다.

비슷한 개념들의 차이를 이해하면 어떤 저장소를 사용해야 하는지 훨씬 쉽게 판단할 수 있다.

### Activity Destroy vs Process Death

| 구분 | Activity Destroy | Process Death |
|------|------------------|---------------|
| 종료 대상 | Activity 하나 | 애플리케이션 프로세스 전체 |
| 발생 원인 | 뒤로가기, 화면 회전 등 | 시스템 메모리 부족, 장시간 백그라운드 |
| ViewModel | 유지될 수도 있음 | 삭제 |
| Singleton | 유지 | 삭제 |
| 메모리 | 대부분 유지 | 모두 삭제 |
| 앱 실행 | 계속 | 처음부터 다시 시작 |

Activity가 종료되었다고 해서 앱이 종료된 것은 아니다.

반면 Process Death는 앱 전체가 처음 실행되는 상태와 거의 동일하게 다시 시작된다.

<br>

### Process Death vs Configuration Change

| 구분 | Configuration Change | Process Death |
|------|----------------------|---------------|
| 원인 | 화면 회전, 다크모드 변경, 언어 변경 | 시스템이 프로세스를 종료 |
| Activity | 재생성 | 재생성 |
| ViewModel | 유지 | 삭제 |
| 메모리 | 유지 | 삭제 |
| 프로세스 | 유지 | 새로 생성 |

둘 다 화면이 다시 생성되지만 내부적으로는 완전히 다른 동작이다.

Configuration Change에서는 ViewModel이 유지되므로 데이터를 다시 불러올 필요가 없는 경우가 많다.

하지만 Process Death에서는 ViewModel도 삭제되므로 상태를 다시 복원해야 한다.

<br>

### remember vs rememberSaveable

| 구분 | remember | rememberSaveable |
|------|----------|------------------|
| Recomposition | 유지 | 유지 |
| 화면 회전 | 초기화 | 유지 |
| Process Death | 초기화 | 복원 가능 |

remember는 메모리에만 저장되므로 Process Death가 발생하면 데이터가 모두 사라진다.

rememberSaveable은 Bundle을 이용하여 필요한 상태를 저장하므로 화면 회전이나 Process Death 이후에도 복원이 가능하다.

<br>

## ViewModel vs SavedStateHandle

| 구분 | ViewModel | SavedStateHandle |
|------|-----------|------------------|
| 역할 | 화면 상태 관리 | 화면 상태 복원 |
| 저장 위치 | 메모리 | SavedState |
| Process Death | 삭제 | 복원 가능 |
| 저장 대상 | UI 상태 | 최소한의 복원 정보 |

ViewModel은 상태를 관리하는 객체이고, SavedStateHandle은 그 상태를 다시 복원하기 위한 저장소이다.

두 기능은 서로 경쟁 관계가 아니라 함께 사용하는 것이 일반적이다.

<br>

## SavedStateHandle vs DataStore

| 구분 | SavedStateHandle | DataStore |
|------|------------------|-----------|
| 목적 | 화면 상태 복원 | 영구 데이터 저장 |
| 저장 기간 | 일시적 | 영구적 |
| 대표 데이터 | 검색어, 선택 탭 | 로그인 정보, 앱 설정 |

화면을 복원하기 위한 데이터라면 SavedStateHandle을 사용하고, 앱을 종료한 뒤에도 계속 유지해야 하는 데이터라면 DataStore를 사용하는 것이 적절하다.

<br>

## 10. Process Death 대응 방법

Process Death를 완전히 막을 수는 없다.

따라서 Android 앱은 Process Death가 발생하는 것을 전제로 설계해야 한다.

상태의 성격에 따라 저장 위치를 선택하는 것이 가장 중요하다.

| 데이터 | 권장 저장 위치 |
|---------|----------------|
| Compose UI 상태 | remember |
| 화면 회전까지 유지 | rememberSaveable |
| 화면 상태 관리 | ViewModel |
| Process Death 이후 복원 | SavedStateHandle |
| 영구 저장 | DataStore 또는 Room |

또한 다음과 같은 원칙을 지키면 Process Death에도 안정적인 앱을 만들 수 있다.

- ViewModel을 영구 저장소처럼 사용하지 않는다.
- 중요한 데이터는 DataStore 또는 Room에 저장한다.
- API 응답은 필요할 때 다시 조회할 수 있도록 설계한다.
- SavedStateHandle에는 최소한의 상태만 저장한다.

Process Death는 예외 상황이 아니라 Android에서 언제든 발생할 수 있는 정상적인 시스템 동작이라는 점을 항상 고려해야 한다.

<br>

## 11. 디버깅 및 테스트 방법

Process Death를 고려한 코드를 작성했다면 실제로 복원이 정상적으로 이루어지는지 반드시 테스트해야 한다.

가장 간단한 방법은 개발자 옵션을 사용하는 것이다.

### Don't keep activities

개발자 옵션의 **Don't keep activities**를 활성화하면 Activity가 화면에서 사라질 때마다 즉시 종료된다.

이를 통해 상태 복원 로직이 정상적으로 동작하는지 빠르게 확인할 수 있다.

단, 이 기능은 Activity 종료를 테스트하기 위한 옵션이며 실제 Process Death와는 완전히 동일하지는 않다.

<br>

### adb 명령어로 Process 종료

실제 Process Death에 가까운 상황은 adb 명령어를 이용해 재현할 수 있다.

```bash
adb shell am kill <패키지명>
```

프로세스를 종료한 뒤 최근 앱에서 다시 실행하면 새로운 프로세스가 생성되는 것을 확인할 수 있다.

<br>

### 확인해야 할 항목

Process Death를 테스트할 때는 다음 사항을 확인하는 것이 좋다.

- 입력한 내용이 복원되는가?
- 선택한 탭이 유지되는가?
- 스크롤 위치가 복원되는가?
- ViewModel이 새로 생성되는가?
- 필요한 데이터가 다시 조회되는가?
- 로그인 상태는 유지되는가?

이러한 항목을 점검하면 Process Death 이후에도 사용자 경험이 자연스럽게 이어지는지 확인할 수 있다.

<br>

## 12. 정리

- Process는 Android 앱이 실행되는 기본 단위이다.
- Process Death는 시스템이 프로세스를 종료하는 정상적인 동작이다.
- Process Death가 발생하면 메모리에 있던 모든 객체가 삭제된다.
- ViewModel은 Process Death 이후에는 유지되지 않는다.
- 필요한 UI 상태는 SavedStateHandle이나 rememberSaveable을 사용해 복원한다.
- 영구적으로 보관해야 하는 데이터는 DataStore나 Room에 저장한다.
- 상태의 생명주기를 기준으로 적절한 저장소를 선택하는 것이 가장 중요하다.
