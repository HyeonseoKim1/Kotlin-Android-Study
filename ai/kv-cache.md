# KV Cache

## 목차

1. KV Cache란?
2. Transformer는 왜 느려질까?
3. Attention 다시 이해하기
4. KV Cache가 필요한 이유
5. KV Cache 동작 과정
6. 예시로 이해하기
7. 코드로 이해하기
8. 장점과 단점
9. 실무에서의 활용
10. 정리

## KV Cache란?

KV Cache(Key-Value Cache)는 **이미 계산한 Key와 Value를 저장해 두었다가 다시 사용하는 기술**이다.

LLM은 문장을 한 글자(정확히는 토큰)씩 생성한다.

예를 들어

```
안
```

↓

```
안녕
```

↓

```
안녕하세요
```

처럼 한 토큰씩 생성한다.

문제를 하나 생각해 보자.

"안녕하세요"를 생성하는 동안

"안"

이라는 토큰은 계속 바뀔까?

아니다.

이미 생성된 토큰는 절대 바뀌지 않는다.

그런데 Transformer는 원래 새로운 토큰을 생성할 때마다 이전 토큰까지 모두 다시 계산한다.

이 과정이 매우 비효율적이다.

그래서 이전 계산 결과를 저장하는 것이 KV Cache이다.

<br>

## Transformer는 왜 느려질까?

Transformer는 다음 단어를 예측할 때 항상 **이전의 모든 단어를 참고**한다.

예를 들어

```
나는 오늘
```

까지 생성되었다고 하자.

다음 단어를 예측하려면

- 나는
- 오늘

두 단어를 모두 살펴본다.

여기까지는 문제가 없다.

그런데 다음 토큰을 생성하면

```
나는 오늘 학교에
```

이 된다.

이때도 모델은

- 나는
- 오늘
- 학교에

를 다시 모두 계산한다.

또 다음 토큰을 생성하면

```
나는 오늘 학교에 갔다
```

가 되고,

또 처음부터 다시 계산한다.

토큰이 많아질수록 계산량은 계속 증가한다.

<br>

## Attention 다시 이해하기

Attention에서는 입력으로부터

- Query(Q)
- Key(K)
- Value(V)

세 가지 벡터를 만든다.

```
입력

↓

Q 생성
K 생성
V 생성

↓

Attention 계산
```

여기서 중요한 점이 있다.

이미 생성된 토큰의

- Key
- Value

는 절대 변하지 않는다.

새로운 토큰이 추가될 뿐이다.

즉,

```
나는
```

의 Key와 Value는

10초 뒤에도

100개의 토큰이 생성된 뒤에도

동일하다.

굳이 다시 계산할 이유가 없다.

<br>

## KV Cache가 필요한 이유

예를 들어

```
I love Kotlin
```

까지 생성했다고 하자.

다음 단어를 생성하면

```
I love Kotlin because
```

가 된다.

KV Cache가 없다면

```
I
love
Kotlin
because
```

모든 토큰의 Key와 Value를 다시 만든다.

하지만

"I"

의 Key는 이미 계산했다.

"love"

도 계산했다.

"Kotlin"

도 계산했다.

다시 계산하는 것은 낭비다.

그래서

```
I

↓

Key 저장
Value 저장

love

↓

Key 저장
Value 저장

Kotlin

↓

Key 저장
Value 저장
```

해 두고,

새로운 토큰만 계산한다.

<br>

## KV Cache 동작 과정

### 1단계

첫 번째 토큰 생성

```
Hello
```

↓

```
Key 생성

Value 생성

Cache 저장
```

<br>

### 2단계

다음 토큰 생성

```
Hello world
```

↓

새 토큰의

- Key
- Value

만 계산한다.

기존 Hello의 Key와 Value는 그대로 사용한다.

<br>

### 3단계

또 다음 토큰 생성

```
Hello world !
```

↓

이번에도

"!"

의 Key와 Value만 계산한다.

Hello와 world는 캐시에서 가져온다.

<br>

## 예시로 이해하기

학생이 시험 문제를 푼다고 생각해 보자.

매 문제마다

```
1번 문제 다시 풀이

2번 문제 다시 풀이

3번 문제 다시 풀이
```

를 반복하면 시간이 오래 걸린다.

하지만

이미 푼 문제는 답을 저장해 두면 된다.

새 문제만 풀면 된다.

KV Cache도 똑같다.

이미 계산한 결과를 저장하고

새로운 토큰만 계산하는 것이다.

<br>

## 코드로 이해하기

```kotlin
val keyCache = mutableListOf<Key>()
val valueCache = mutableListOf<Value>()

fun addToken(token: Token) {

    // 새 토큰만 계산
    val key = makeKey(token)
    val value = makeValue(token)

    // 캐시에 저장
    keyCache.add(key)
    valueCache.add(value)

    // 이전 결과 + 새 결과 사용
    attention(
        query = makeQuery(token),
        keys = keyCache,
        values = valueCache
    )
}
```

실제 LLM도 거의 같은 개념으로 동작한다.

다만 각 Transformer Layer마다 별도의 KV Cache를 저장한다.

<br>

## 장점과 단점

### 장점

- 이전 토큰을 다시 계산하지 않는다.
- 추론 속도가 크게 빨라진다.
- GPU 연산량이 감소한다.
- 문장이 길어질수록 효과가 커진다.

### 단점

- Key와 Value를 계속 저장해야 하므로 메모리를 많이 사용한다.
- Context Window가 커질수록 KV Cache도 커진다.
- 긴 대화를 처리할수록 GPU 메모리 사용량이 증가한다.

<br>

## 실무에서의 활용

KV Cache는 거의 모든 최신 LLM에서 사용된다.

- ChatGPT
- Claude
- Gemini
- Llama
- Gemma
- Qwen

특히 챗봇처럼 답변을 한 토큰씩 생성하는 서비스에서는 필수적인 기술이다.

반대로 **학습(Training)** 단계에서는 모든 토큰을 한 번에 입력하기 때문에 일반적으로 KV Cache를 사용하지 않는다.

<br>

# 정리

- Transformer는 새로운 토큰을 생성할 때 이전 토큰도 함께 참고한다.
- 원래는 이전 토큰의 Key와 Value를 매번 다시 계산한다.
- 하지만 이전 토큰의 Key와 Value는 변하지 않는다.
- KV Cache는 이전에 계산한 Key와 Value를 저장해 두고 재사용하는 기술이다.
- 덕분에 추론 속도가 크게 향상된다.
- 대신 Key와 Value를 저장하기 위한 메모리가 추가로 필요하다.
