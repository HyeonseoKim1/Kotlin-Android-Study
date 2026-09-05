# Coil / Glide

<br>

## 목차

1. Coil과 Glide란 무엇인가
2. 이미지 로딩 파이프라인의 기본 원리
3. Coil 기본 사용법
4. Glide 기본 사용법
5. Compose에서 이미지 로딩하기
6. 플레이스홀더와 에러 처리
7. 이미지 변환 (Transformation)
8. 메모리 캐시와 디스크 캐시
9. GIF와 애니메이션 이미지 처리
10. 커스텀 OkHttpClient 연동
11. 이미지 크기와 메모리 최적화
12. 프리로드와 미리 캐싱
13. 테스트와 디버깅
14. 주의사항과 자주 하는 실수
15. 정리

<br>

## Coil과 Glide란 무엇인가

Coil과 Glide는 둘 다 안드로이드에서 URL이나 리소스로부터 이미지를 비동기로 불러와 화면에 표시해주는 이미지 로딩 라이브러리다. 두 라이브러리 모두 다운로드, 디코딩, 캐싱, 화면 표시라는 동일한 문제를 해결하지만 설계 철학이 다르다.

Glide는 오래전부터(2014년경) 널리 쓰여온 라이브러리로 View 시스템(`ImageView`)을 기본 대상으로 설계되었고, 내부적으로 자체 요청 큐와 캐시 엔진을 갖추고 있다. Coil은 비교적 최근에 나온 라이브러리로 Kotlin Coroutines를 기반으로 처음부터 설계되었고, Jetpack Compose를 네이티브로 지원한다는 점이 가장 큰 차이다.

```kotlin
// build.gradle.kts (Module: app)
dependencies {
    // Coil (Compose 프로젝트에 권장)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Glide (View 시스템, 또는 세밀한 캐시 제어가 필요한 경우)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    ksp("com.github.bumptech.glide:ksp:4.16.0")
}
```

| 항목 | Coil | Glide |
|---|---|---|
| 기반 기술 | Kotlin Coroutines | 자체 스레드 풀/요청 큐 |
| Compose 지원 | 네이티브 (`AsyncImage`) | 별도 확장 라이브러리 필요 |
| 생명주기 인식 | Lifecycle-aware (자동 취소) | `Glide.with(context/fragment)`로 수동 연결 |
| 설정 방식 | Kotlin DSL 스타일 | 빌더 패턴, 어노테이션 프로세서 기반 확장 |
| 커뮤니티/역사 | 비교적 최근, Compose 프로젝트에서 강세 | 오래되고 검증된 이력, View 기반 프로젝트에서 강세 |

**실전 팁**: 신규 Compose 프로젝트라면 Coil을 기본 선택지로 두는 것이 자연스럽다. 반대로 기존 View 기반 대형 프로젝트를 유지보수 중이거나, Glide의 세밀한 커스텀 디코더/트랜스코더 기능이 필요하다면 Glide를 유지하는 편이 낫다.

<br>

## 이미지 로딩 파이프라인의 기본 원리

두 라이브러리 모두 내부적으로 비슷한 흐름을 따른다: 요청이 들어오면 먼저 메모리 캐시를 확인하고, 없으면 디스크 캐시를 확인하고, 그마저 없으면 네트워크(또는 로컬 파일)에서 원본을 가져와 디코딩한 뒤 화면 크기에 맞게 리사이즈해서 표시한다. 이 과정에서 각 단계의 결과를 캐시에 저장해두어, 같은 이미지를 다시 요청할 때는 이전 단계를 건너뛸 수 있게 한다.

```kotlin
// 개념적으로 표현한 이미지 로딩 파이프라인
suspend fun loadImageConceptual(url: String): Bitmap {
    memoryCache.get(url)?.let { return it }          // 1. 메모리 캐시 확인
    diskCache.get(url)?.let {
        val bitmap = decode(it)
        memoryCache.put(url, bitmap)
        return bitmap
    }                                                  // 2. 디스크 캐시 확인
    val rawData = fetchFromNetwork(url)                // 3. 네트워크에서 원본 다운로드
    diskCache.put(url, rawData)
    val bitmap = decode(rawData)
    memoryCache.put(url, bitmap)
    return bitmap                                       // 4. 디코딩 후 메모리 캐시 저장
}
```

| 단계 | 설명 |
|---|---|
| 메모리 캐시 조회 | 가장 빠름, 앱 프로세스가 살아있는 동안만 유지 |
| 디스크 캐시 조회 | 앱을 재시작해도 유지, 메모리보다는 느림 |
| 네트워크/로컬 소스 fetch | 캐시에 없을 때만 발생하는 가장 느린 단계 |
| 디코딩 및 리사이즈 | 화면에 필요한 크기로 비트맵을 최적화 |

**실전 팁**: 이 파이프라인을 이해하고 있으면, "왜 같은 이미지를 여러 번 요청해도 두 번째부터는 빠른지", "왜 화면 크기와 다른 원본 이미지를 로드하면 느린지" 같은 질문에 스스로 답할 수 있게 된다.

<br>

## Coil 기본 사용법

Coil은 `ImageLoader`라는 싱글톤 객체를 중심으로 동작하며, 대부분의 경우 기본 `ImageLoader`만으로 충분하다. View 시스템에서는 `ImageView.load()` 확장 함수로, Compose에서는 `AsyncImage` 컴포저블로 바로 사용할 수 있다.

```kotlin
// View 시스템에서 사용
imageView.load("https://example.com/photo.jpg") {
    crossfade(true)
    placeholder(R.drawable.placeholder)
    error(R.drawable.error_image)
}

// 커스텀 ImageLoader 구성 (Application 클래스 등에서)
class MyApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
    }
}
```

| API | 설명 |
|---|---|
| `ImageLoader` | Coil의 핵심 엔진, 캐시/디코더/네트워크 설정을 담당 |
| `ImageView.load()` | View 시스템에서 이미지 로드를 시작하는 확장 함수 |
| `ImageLoaderFactory` | `Application`에서 커스텀 `ImageLoader`를 등록하는 인터페이스 |

**실전 팁**: `ImageLoaderFactory`로 커스텀 `ImageLoader`를 한 번 등록해두면 앱 전체에서 동일한 캐시 정책을 공유한다. 화면마다 새로운 `ImageLoader`를 만들면 캐시가 화면별로 분리되어 캐시 효율이 크게 떨어진다.

<br>

## Glide 기본 사용법

Glide는 `Glide.with(context)`로 요청 빌더를 시작하고, `load()`로 소스를 지정한 뒤 `into()`로 대상 `ImageView`에 연결하는 흐름으로 동작한다. Fragment나 Activity의 생명주기와 연결해서 사용하면 화면이 파괴될 때 자동으로 요청이 취소된다.

```kotlin
// Fragment 안에서 사용 (생명주기 자동 연결)
Glide.with(this)
    .load("https://example.com/photo.jpg")
    .placeholder(R.drawable.placeholder)
    .error(R.drawable.error_image)
    .into(imageView)

// Application 전역에서 캐시/디코딩 정책을 커스터마이징
@GlideModule
class MyAppGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setDiskCache(
            InternalCacheDiskCacheFactory(context, 50L * 1024 * 1024)
        )
        builder.setDefaultRequestOptions(
            RequestOptions().format(DecodeFormat.PREFER_RGB_565)
        )
    }
}
```

| API | 설명 |
|---|---|
| `Glide.with(context/fragment)` | 요청 빌더 시작, 생명주기 스코프 지정 |
| `.load()` | URL, 리소스, 파일 등 이미지 소스 지정 |
| `.into(imageView)` | 최종 대상 View에 이미지 바인딩 |
| `AppGlideModule` | 앱 전역 Glide 설정을 커스터마이징하는 확장 지점 |

**실전 팁**: `Glide.with(applicationContext)` 대신 가능하면 `Glide.with(fragment)`나 `Glide.with(activity)`로 시작하는 것이 좋다. Fragment/Activity 스코프로 시작하면 화면이 사라질 때 진행 중인 요청이 자동으로 취소되어 불필요한 네트워크 낭비와 메모리 누수를 막을 수 있다.

<br>

## Compose에서 이미지 로딩하기

Coil은 Compose를 위한 `AsyncImage` 컴포저블을 기본 제공하며, 로딩/에러 상태를 세밀하게 다루고 싶다면 `SubcomposeAsyncImage`를 사용한다. Glide도 별도 확장 라이브러리(`GlideImage`)를 통해 Compose를 지원한다.

```kotlin
// Coil - 기본 사용
@Composable
fun ProfileImage(url: String) {
    AsyncImage(
        model = url,
        contentDescription = "프로필 이미지",
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape),
        contentScale = ContentScale.Crop
    )
}

// Coil - 로딩/에러 상태를 세밀하게 제어
@Composable
fun ProfileImageWithState(url: String) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = "프로필 이미지",
        loading = { CircularProgressIndicator(modifier = Modifier.size(24.dp)) },
        error = { Icon(Icons.Default.BrokenImage, contentDescription = null) },
        modifier = Modifier.size(80.dp)
    )
}
```

| API | 라이브러리 | 특징 |
|---|---|---|
| `AsyncImage` | Coil | 로딩/에러를 `placeholder`/`error` 파라미터로 간단히 지정 |
| `SubcomposeAsyncImage` | Coil | 로딩/에러 상태에 임의의 컴포저블을 완전히 커스터마이징 가능 |
| `GlideImage` | Glide (compose 확장 모듈) | Glide 엔진을 Compose에서 그대로 활용 |

**실전 팁**: 단순히 이미지 하나만 보여준다면 `AsyncImage`로 충분하다. 로딩 중 스켈레톤 UI, 에러 시 재시도 버튼처럼 복잡한 상태별 UI가 필요할 때만 `SubcomposeAsyncImage`로 전환하는 것이 코드 복잡도 관리에 유리하다.

<br>

## 플레이스홀더와 에러 처리

이미지가 로드되기 전이나 실패했을 때 보여줄 대체 이미지를 지정하는 것은 두 라이브러리 모두 기본 기능으로 제공한다. 어떤 상태에서 어떤 이미지를 보여줄지 명확히 구분해서 설정하는 것이 사용자 경험에 중요하다.

```kotlin
// Coil
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(url)
        .crossfade(true)
        .placeholder(R.drawable.placeholder)
        .error(R.drawable.error_image)
        .fallback(R.drawable.no_image) // model 자체가 null일 때
        .build(),
    contentDescription = null
)

// Glide
Glide.with(imageView)
    .load(url)
    .placeholder(R.drawable.placeholder)  // 로딩 중
    .error(R.drawable.error_image)        // 로드 실패
    .fallback(R.drawable.no_image)        // url이 null인 경우
    .into(imageView)
```

| 상태 | 설정 파라미터 | 의미 |
|---|---|---|
| 로딩 중 | `placeholder` | 이미지가 아직 도착하지 않은 동안 표시 |
| 로드 실패 | `error` | 네트워크 오류, 디코딩 실패 등 |
| 소스가 null | `fallback` | `model`/`load()` 자체에 null이 전달된 경우 |

**실전 팁**: `error`와 `fallback`을 혼동하는 경우가 많다. `error`는 요청은 시도했지만 실패한 경우이고, `fallback`은 애초에 로드할 대상 URL 자체가 없는 경우다. 둘을 구분해서 설정하면 "이미지가 없는 상품"과 "이미지 로드에 실패한 상품"을 다르게 안내할 수 있다.

<br>

## 이미지 변환 (Transformation)

원형 크롭, 블러, 라운드 코너 같은 이미지 변형은 두 라이브러리 모두 `Transformation` API로 지원한다. 다만 Compose에서는 `Modifier.clip()` 같은 Compose 자체 기능으로 처리하는 경우도 많다.

```kotlin
// Coil - Transformation 적용
imageView.load(url) {
    transformations(
        RoundedCornersTransformation(radius = 16f),
        GrayscaleTransformation()
    )
}

// Compose에서는 Modifier로 원형/라운드 처리하는 것이 더 일반적
AsyncImage(
    model = url,
    contentDescription = null,
    modifier = Modifier
        .size(80.dp)
        .clip(RoundedCornerShape(12.dp))
)

// Glide - Transformation 적용
Glide.with(imageView)
    .load(url)
    .transform(CenterCrop(), RoundedCorners(24))
    .into(imageView)
```

| 방식 | 사용 상황 |
|---|---|
| 라이브러리의 `Transformation` API | 블러, 그레이스케일처럼 픽셀 자체를 가공해야 하는 경우 |
| Compose `Modifier.clip()` | 단순 원형/라운드 코너 정도는 Compose Modifier가 더 간단 |

**실전 팁**: 단순히 모양만 잘라내는 정도(원형, 라운드 코너)라면 라이브러리의 Transformation보다 Compose의 `Modifier.clip()`을 쓰는 게 더 가볍고 직관적이다. 실제 픽셀 데이터를 가공해야 하는 블러나 색상 필터 같은 효과에만 Transformation API를 사용하는 것이 합리적이다.

<br>

## 메모리 캐시와 디스크 캐시

두 라이브러리 모두 메모리 캐시(LRU 기반)와 디스크 캐시를 별도로 관리하며, 각각의 최대 크기를 설정할 수 있다. 메모리 캐시는 앱이 살아있는 동안의 빠른 재사용을, 디스크 캐시는 앱을 재시작해도 유지되는 영속성을 담당한다.

```kotlin
// Coil - 메모리/디스크 캐시 크기 설정
ImageLoader.Builder(context)
    .memoryCache {
        MemoryCache.Builder(context)
            .maxSizePercent(0.25) // 사용 가능 메모리의 25%
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(context.cacheDir.resolve("coil_cache"))
            .maxSizeBytes(100L * 1024 * 1024) // 100MB
            .build()
    }
    .build()

// Glide - 캐시 전략을 요청 단위로 지정
Glide.with(imageView)
    .load(url)
    .diskCacseStrategy(DiskCacheStrategy.ALL) // 원본+변형본 모두 캐시
    .into(imageView)
```

| 캐시 전략 (Glide `DiskCacheStrategy`) | 설명 |
|---|---|
| `ALL` | 원본과 변형된(리사이즈된) 이미지 모두 캐시 |
| `RESOURCE` | 변형된 이미지만 캐시 |
| `DATA` | 원본 데이터만 캐시 |
| `NONE` | 디스크 캐시 사용 안 함 |

**실전 팁**: 같은 원본 이미지를 여러 크기(썸네일, 상세 화면 등)로 자주 재사용한다면 `DiskCacheStrategy.ALL`이 유리하다. 반대로 한 번만 특정 크기로 표시하고 다시 쓸 일이 없다면 `RESOURCE`만으로도 충분하고 디스크 공간을 아낄 수 있다.

<br>

## GIF와 애니메이션 이미지 처리

두 라이브러리 모두 GIF나 애니메이션 WebP를 정지 이미지와 동일한 API로 자동 재생해준다. Coil은 `ImageDecoderDecoder`(API 28+) 또는 `GifDecoder`를 통해, Glide는 기본적으로 GIF를 자동 감지해서 처리한다.

```kotlin
// Coil - GIF 지원을 위한 ImageLoader 설정
ImageLoader.Builder(context)
    .components {
        if (Build.VERSION.SDK_INT >= 28) {
            add(ImageDecoderDecoder.Factory())
        } else {
            add(GifDecoder.Factory())
        }
    }
    .build()

// Compose에서는 특별한 처리 없이 AsyncImage로 GIF도 그대로 재생됨
AsyncImage(
    model = "https://example.com/animation.gif",
    contentDescription = null
)

// Glide - 별도 설정 없이 GIF 자동 재생
Glide.with(imageView)
    .load("https://example.com/animation.gif")
    .into(imageView) // GIF는 자동으로 감지되어 애니메이션으로 재생됨
```

| 라이브러리 | GIF 지원 방식 |
|---|---|
| Coil | `ImageDecoderDecoder`/`GifDecoder`를 `ImageLoader`에 명시적으로 추가해야 함 |
| Glide | 기본 설정으로 자동 지원, 별도 설정 불필요 |

**실전 팁**: Coil에서 GIF가 정지 이미지처럼 첫 프레임만 보이고 재생되지 않는다면, `ImageLoader`에 GIF 디코더 컴포넌트를 추가했는지부터 확인해야 한다. 기본 `ImageLoader`에는 포함되어 있지 않아 자주 놓치는 설정이다.

<br>

## 커스텀 OkHttpClient 연동

두 라이브러리 모두 네트워크 계층으로 OkHttp를 사용하도록 커스터마이징할 수 있다. 앱에서 Retrofit용으로 이미 구성해둔 `OkHttpClient`(인증 헤더, 로깅 등)를 이미지 로딩에도 재사용하면 일관된 네트워킹 정책을 유지할 수 있다.

```kotlin
// Coil - 커스텀 OkHttpClient 연결
ImageLoader.Builder(context)
    .okHttpClient {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .cache(Cache(context.cacheDir.resolve("coil_http_cache"), 20L * 1024 * 1024))
            .build()
    }
    .build()

// Glide - OkHttp를 네트워크 스택으로 등록 (GlideModule 필요)
@GlideModule
class MyAppGlideModule : AppGlideModule() {
    override fun registerComponents(
        context: Context, glide: Glide, registry: Registry
    ) {
        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(okHttpClient)
        )
    }
}
```

| 상황 | 커스텀 OkHttpClient가 필요한 이유 |
|---|---|
| 인증이 필요한 이미지 서버 | Authorization 헤더를 이미지 요청에도 동일하게 적용 |
| 사내 프록시/방화벽 환경 | Retrofit과 동일한 네트워크 정책 재사용 |
| 캐시 정책 통합 관리 | 네트워크 캐시를 API 요청과 이미지 요청에서 일관되게 관리 |

**실전 팁**: 이미지 서버가 인증 토큰을 요구한다면, Retrofit에 쓰던 `AuthInterceptor`가 포함된 `OkHttpClient`를 이미지 로더에도 그대로 연결하는 것이 가장 간단한 해결책이다. 별도의 인증 로직을 이미지 로딩 쪽에 중복 구현할 필요가 없다.

<br>

## 이미지 크기와 메모리 최적화

원본 이미지 해상도가 실제 화면에 표시되는 크기보다 훨씬 크면, 불필요하게 큰 비트맵이 메모리에 올라가 OOM(OutOfMemoryError)의 원인이 될 수 있다. 두 라이브러리 모두 대상 View/컴포저블의 크기에 맞춰 자동으로 리사이즈해주지만, 명시적으로 크기를 지정하면 더 안전하다.

```kotlin
// Coil - 명시적 크기 지정
imageView.load(url) {
    size(300, 300) // 최대 300x300으로 디코딩
}

// Compose - AsyncImage는 배치된 크기에 맞춰 자동으로 리사이즈됨
AsyncImage(
    model = url,
    contentDescription = null,
    modifier = Modifier.size(120.dp) // 이 크기에 맞춰 자동 최적화
)

// Glide - override로 명시적 디코딩 크기 지정
Glide.with(imageView)
    .load(url)
    .override(300, 300)
    .into(imageView)
```

| 최적화 방법 | 효과 |
|---|---|
| 명시적 크기 지정 (`size`, `override`) | 필요 이상으로 큰 비트맵을 메모리에 올리지 않음 |
| `RGB_565` 포맷 사용 | `ARGB_8888` 대비 픽셀당 메모리 사용량 절반 (투명도 미지원) |
| 리스트에서 재사용되는 ViewHolder | 스크롤 시 이전 요청을 정확히 취소해 메모리 낭비 방지 |

**실전 팁**: 리스트(RecyclerView, LazyColumn)에서 썸네일을 보여줄 때는 원본 크기가 아무리 커도 항상 썸네일 크기로 디코딩되도록 명시적으로 크기를 지정하는 습관이 중요하다. 이를 놓치면 수십 장의 큰 원본 이미지가 동시에 메모리에 올라가 저사양 기기에서 OOM이 발생하기 쉽다.

<br>

## 프리로드와 미리 캐싱

사용자가 다음 화면으로 이동하기 전에 필요한 이미지를 미리 다운로드해서 캐시에 채워두면, 실제 화면 전환 시 이미지가 훨씬 빠르게 표시된다. 두 라이브러리 모두 화면에 표시하지 않고 캐시만 채우는 프리로드 API를 제공한다.

```kotlin
// Coil - 프리로드 (표시 없이 캐시만 채움)
val request = ImageRequest.Builder(context)
    .data(url)
    .build()
imageLoader.enqueue(request)

// Glide - 프리로드
Glide.with(context)
    .load(url)
    .preload()

// 다음 화면에서 실제로 표시할 때는 이미 캐시에 있으므로 즉시 로드됨
AsyncImage(model = url, contentDescription = null)
```

| 활용 시나리오 | 설명 |
|---|---|
| 다음 페이지 이동 예상 시점 | 목록에서 상세 화면으로 넘어갈 이미지를 미리 로드 |
| 배너/캐러셀 다음 슬라이드 | 현재 보이는 슬라이드 로드 직후 다음 슬라이드 프리로드 |

**실전 팁**: 프리로드는 사용자가 실제로 그 화면에 갈지 확신할 수 없는 상황에서 남용하면 불필요한 네트워크/배터리 낭비로 이어질 수 있다. 다음 동작이 거의 확실한 지점(예: 페이지네이션의 다음 페이지, 캐러셀의 다음 슬라이드)에 한정해서 사용하는 것이 좋다.

<br>

## 테스트와 디버깅

이미지 로딩 자체를 단위 테스트하기보다는, 로깅을 활성화해서 어느 단계(캐시 히트/미스, 네트워크, 디코딩)에서 시간이 걸리는지 확인하는 디버깅 방식이 더 실용적이다. 두 라이브러리 모두 상세 로그를 켤 수 있는 옵션을 제공한다.

```kotlin
// Coil - 로그 레벨 설정
ImageLoader.Builder(context)
    .logger(DebugLogger()) // 요청/캐시 히트 여부를 Logcat에 출력
    .build()

// Glide - 로그 레벨 설정 (AppGlideModule)
class MyAppGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setLogLevel(Log.VERBOSE)
    }
}
```

| 확인 항목 | 로그에서 알 수 있는 것 |
|---|---|
| 캐시 히트/미스 | 메모리/디스크 캐시가 실제로 잘 활용되고 있는지 |
| 요청 URL/크기 | 의도한 크기로 리사이즈 요청이 나가고 있는지 |
| 실패 원인 | 네트워크 오류인지, 디코딩 오류인지 구분 |

**실전 팁**: `DebugLogger()`나 `Log.VERBOSE`는 개발 중에만 활성화하고 릴리즈 빌드에서는 반드시 꺼야 한다. 모든 이미지 요청마다 로그가 찍히면 Logcat이 매우 지저분해지고, 미세하지만 성능에도 영향을 줄 수 있다.

<br>

## 주의사항과 자주 하는 실수

1. 리스트(RecyclerView, LazyColumn)에서 원본 이미지 크기 그대로 로드하면, 스크롤 시 다수의 큰 비트맵이 동시에 메모리에 올라가 OOM이 발생하기 쉽다. 썸네일에는 반드시 명시적 크기를 지정해야 한다.
2. `error`와 `fallback`을 혼동해서, url이 애초에 없는 경우와 로드 자체가 실패한 경우를 같은 이미지로 안내하는 경우가 많다.
3. 화면마다 새로운 `ImageLoader`(Coil)나 새로운 Glide 설정을 만들면 캐시가 분산되어 캐시 히트율이 크게 떨어진다. 전역에서 하나의 인스턴스를 공유해야 한다.
4. GIF가 재생되지 않는다고 당황하는 경우가 있는데, Coil은 기본 `ImageLoader`에 GIF 디코더가 포함되어 있지 않으므로 명시적으로 추가해야 한다.
5. `Glide.with(applicationContext)`로 요청을 시작하면 화면이 사라져도 요청이 취소되지 않아 불필요한 네트워크/메모리 낭비로 이어질 수 있다. 가능하면 Fragment/Activity 스코프로 시작해야 한다.
6. 디버그 로그(`DebugLogger`, `Log.VERBOSE`)를 릴리즈 빌드에 그대로 남겨두어 불필요한 로그 출력과 미세한 성능 저하를 겪는 경우가 있다.
7. 인증이 필요한 이미지 서버인데 커스텀 `OkHttpClient`를 연결하지 않아, 이미지 요청마다 401 에러가 발생하는 것을 뒤늦게 알아채는 경우가 있다.
8. 프리로드를 사용자의 다음 행동이 불확실한 곳에 남용해서 불필요한 네트워크/배터리를 소모하는 경우가 있다.
9. `ARGB_8888`이 기본값인데 투명도가 필요 없는 이미지(사진 등)에도 이를 그대로 써서, `RGB_565`로 바꿨을 때 얻을 수 있는 메모리 절감 기회를 놓치는 경우가 있다.
10. Compose와 View 시스템을 함께 쓰는 프로젝트에서 Coil과 Glide를 동시에 의존성으로 추가해 두 캐시 엔진이 별도로 동작하는 바람에, 동일 이미지를 두 번 캐시하는 비효율이 생기는 경우가 있다. 한 프로젝트 안에서는 가능하면 하나의 라이브러리로 통일하는 것이 좋다.

<br>

## 정리

Coil과 Glide는 둘 다 다운로드-캐싱-디코딩-표시라는 동일한 문제를 해결하는 이미지 로딩 라이브러리이지만, 
Coil은 Coroutines와 Compose를 중심으로 설계되어 신규 프로젝트에 적합하고 Glide는 오랜 기간 검증된 View 기반 생태계와 세밀한 커스터마이징 기능에 강점이 있다. 
두 라이브러리 모두 메모리/디스크 캐시, 플레이스홀더·에러 처리, Transformation, GIF 지원, 커스텀 OkHttpClient 연동 같은 핵심 기능을 유사한 형태로 제공한다.
실무에서 가장 신경 써야 할 부분은 리스트 화면에서의 이미지 크기 최적화와 화면 생명주기에 맞춘 요청 취소로, 이 두 가지만 제대로 지켜도 대부분의 메모리 문제와 불필요한 네트워크 낭비를 예방할 수 있다. 
신규 Compose 프로젝트라면 Coil을 기본으로 시작하고, 필요에 따라 커스텀 `ImageLoader`와 OkHttpClient 연동으로 점차 세부 설정을 다듬어가는 순서를 추천한다.
