# LLM Inference

## 목차

1. LLM Inference란?
2. Inference와 Training의 차이
3. LLM이 답변을 생성하는 과정
4. Token이 중요한 이유
5. Auto-Regressive 방식
6. 왜 속도가 느릴까?
7. Inference 최적화 기법
8. Android 개발자가 알아야 하는 이유
9. 언제 활용하면 좋을까?
10. 정리

<br>

## 1. LLM Inference란?

LLM Inference는 **학습이 완료된 AI 모델이 새로운 입력을 받아 답변을 생성하는 과정**을 의미한다.

예를 들어 사용자가 아래와 같이 질문했다고 가정해보자.

```
Android에서 Coroutine이 뭐야?
```

LLM은 이 문장을 입력받아 다음 Token을 하나씩 예측하면서 답변을 생성한다.

```
Coroutine
→ 는
→ 비동기
→ 작업을
→ ...
```

우리가 ChatGPT를 사용할 때 이루어지는 대부분의 과정은 Training이 아니라 Inference이다.

<br>

## 2. Inference와 Training의 차이

| 구분 | Training | Inference |
|------|----------|-----------|
| 목적 | 모델 학습 | 답변 생성 |
| 데이터 | 대량의 학습 데이터 | 사용자의 입력 |
| 결과 | 모델의 가중치 업데이트 | Token 생성 |
| 연산량 | 매우 큼 | 상대적으로 적음 |
| 일반 사용자 | 거의 사용하지 않음 | 항상 사용 |

예를 들어 ChatGPT를 사용할 때는 이미 학습이 끝난 모델을 사용하므로 Inference만 수행된다.

<br>

## 3. LLM이 답변을 생성하는 과정

사용자가 질문을 입력하면 다음과 같은 순서로 답변이 만들어진다.

```
사용자 입력
        ↓
Tokenization
        ↓
Embedding
        ↓
Transformer 계산
        ↓
다음 Token 예측
        ↓
Token 출력
        ↓
다음 Token 예측 반복
```

예를 들어

```
안녕하세요
```

라는 입력이 들어오면

```
["안녕", "하세요"]
```

와 같이 Token으로 분리된다.

이 Token들은 숫자로 변환되어 Transformer 모델에 입력되고, 모델은 가장 가능성이 높은 다음 Token을 하나씩 예측한다.

<br>

## 4. Token이 중요한 이유

LLM은 문장을 한 번에 생성하지 않는다.

항상 **Token 단위**로 생성한다.

예를 들어

```
Compose가 무엇인가요?
```

라는 질문에 대해

```
Compose
→ 는
→ Android
→ UI
→ Toolkit
→ 입니다.
```

처럼 하나씩 생성된다.

따라서 답변 길이가 길어질수록 생성해야 하는 Token 수도 증가한다.

Token이 많을수록

- 응답 시간이 길어진다.
- GPU 연산량이 증가한다.
- 메모리 사용량이 증가한다.

<br>

## 5. Auto-Regressive 방식

대부분의 LLM은 **Auto-Regressive** 방식을 사용한다.

이는 이전까지 생성한 Token을 기반으로 다음 Token을 예측하는 방식이다.

예를 들어

```
오늘
```

이 생성되면

다음 Token은

```
날씨가
```

일 수도 있고

```
점심은
```

일 수도 있다.

모델은 가장 확률이 높은 Token을 선택한다.

그다음에는

```
오늘 날씨가
```

를 입력으로 다시 계산하여 다음 Token을 예측한다.

즉,

```
입력
↓

Token 1 생성

↓

Token 2 생성

↓

Token 3 생성

↓

...
```

이 과정을 반복한다.

이러한 구조 때문에 앞의 Token이 생성되어야 뒤의 Token을 생성할 수 있다.

<br>

## 6. 왜 속도가 느릴까?

LLM은 답변을 한 글자씩 생성하는 것처럼 보이지만 실제로는 Token마다 Transformer 전체를 실행한다.

예를 들어 300개의 Token을 생성해야 한다면

```
Transformer 실행

↓

Token 1

↓

Transformer 실행

↓

Token 2

↓

Transformer 실행

↓

Token 3

...

↓

300번 반복
```

이 과정을 수행한다.

즉, 답변이 길어질수록 계산 횟수도 증가한다.

이 때문에 최신 LLM들은 Inference 속도를 높이기 위한 다양한 최적화 기법을 사용한다.

<br>

## 7. Inference 최적화 기법

대표적인 최적화 방법은 다음과 같다.

| 기술 | 목적 |
|------|------|
| KV Cache | 이전 계산 재사용 |
| Speculative Decoding | 여러 Token을 미리 생성 |
| MTP(Multi-Token Prediction) | 여러 Token 동시 예측 |
| Quantization | 모델 크기 감소 |
| Flash Attention | Attention 연산 최적화 |

예를 들어 KV Cache를 사용하면 이전 Token을 다시 계산하지 않아도 되므로 응답 속도가 크게 향상된다.

Speculative Decoding과 MTP는 여러 Token을 미리 생성하거나 동시에 예측하여 전체 생성 시간을 줄인다.

<br>

## 8. Android 개발자가 알아야 하는 이유

최근 Android 앱에서도 LLM을 직접 사용하는 사례가 증가하고 있다.

예를 들어

- Chat 기능
- 문서 요약
- 번역
- 음성 비서
- 코드 생성
- 이미지 설명 생성

과 같은 기능에서는 모두 Inference가 수행된다.

또한 On-device AI를 사용하는 경우에는 스마트폰 내부에서 Inference가 실행된다.

모델의 크기와 생성 속도는 사용자 경험에 직접적인 영향을 준다.

예를 들어 응답이 1초 안에 생성되는 앱과 10초가 걸리는 앱은 사용성이 크게 다르다.

따라서 Android 개발자도 Inference 과정과 성능 최적화 방법을 이해하는 것이 중요하다.

<br>

## 9. 언제 활용하면 좋을까?

다음과 같은 상황에서 Inference에 대한 이해가 도움이 된다.

- LLM 기반 앱 개발
- AI 챗봇 구현
- On-device AI 개발
- AI 응답 속도 개선
- AI 기능 성능 분석
- Speculative Decoding 이해
- KV Cache 이해
- Quantization 이해

특히 LLM 성능 최적화를 공부할 때 가장 먼저 이해해야 하는 핵심 개념이다.

<br>

## 10. 정리

- LLM Inference는 학습이 완료된 모델이 새로운 입력을 받아 답변을 생성하는 과정이다.
- 사용자가 AI 서비스를 사용할 때 대부분 수행되는 작업은 Inference이다.
- LLM은 문장을 한 번에 생성하지 않고 Token 단위로 생성한다.
- 대부분의 LLM은 Auto-Regressive 방식으로 이전 Token을 기반으로 다음 Token을 예측한다.
- 답변이 길어질수록 계산량이 증가하므로 다양한 Inference 최적화 기법이 사용된다.
- KV Cache, Speculative Decoding, MTP, Quantization 등은 모두 Inference 속도를 높이기 위한 기술이다.
- LLM 기반 서비스를 개발하거나 On-device AI를 구현하는 Android 개발자라면 반드시 이해해야 하는 핵심 개념이다.
