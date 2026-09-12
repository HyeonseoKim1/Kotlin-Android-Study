# Media3 / ExoPlayer

1. 개요
2. Media3 아키텍처
3. 주요 컴포넌트
4. 기본 사용법
5. Player 상태와 이벤트 처리
6. MediaItem과 메타데이터
7. 재생 목록(Playlist) 관리
8. TrackSelector 커스터마이징
9. Renderer와 Decoder 커스터마이징
10. 커스텀 DataSource와 캐시
11. DRM 처리
12. MediaSessionService를 이용한 백그라운드 재생
13. MediaController와 알림(Notification) 커스터마이징
14. Cast 및 확장 모듈
15. Analytics와 로깅
16. 성능 최적화
17. 테스트
18. 자주 겪는 이슈
19. 정리

# 개요

Media3는 구글이 ExoPlayer, MediaSession, Cast 확장, DownloadManager 등 미디어 관련 라이브러리를 하나로 통합해 내놓은 Jetpack 라이브러리다. 기존 ExoPlayer는 2024년부터 Media3 안으로 편입되어 `androidx.media3` 네임스페이스로 이전되었고, 별도의 exoplayer 저장소는 유지보수 모드로 전환됐다.

기존에 `com.google.android.exoplayer2` 패키지를 쓰던 프로젝트는 마이그레이션 스크립트(`androidx.media3:media3-exoplayer` 도입 + 패키지명 치환)를 통해 이전할 수 있다. API 자체는 큰 틀에서 거의 동일하고, 클래스명과 패키지 경로가 바뀐 정도의 차이가 대부분이다.

<br>

Media3를 쓰는 이유는 다음과 같다.

- 오디오/비디오 재생을 위한 표준화된 Player 인터페이스 제공
- Adaptive streaming(DASH, HLS, SmoothStreaming) 지원
- MediaSession 기반 백그라운드 재생, 알림, 블루투스/차량 연동을 표준 방식으로 처리
- Renderer, TrackSelector, LoadControl 등 각 단계를 교체 가능한 구조로 설계되어 커스터마이징이 쉬움
- Google이 공식적으로 유지보수하는 라이브러리라 장기적으로 안정적

<br>

```gradle
implementation "androidx.media3:media3-exoplayer:1.4.1"
implementation "androidx.media3:media3-ui:1.4.1"
implementation "androidx.media3:media3-session:1.4.1"
implementation "androidx.media3:media3-exoplayer-dash:1.4.1"
implementation "androidx.media3:media3-exoplayer-hls:1.4.1"
implementation "androidx.media3:media3-datasource-okhttp:1.4.1"
implementation "androidx.media3:media3-exoplayer-rtsp:1.4.1"
implementation "androidx.media3:media3-cast:1.4.1"
implementation "androidx.media3:media3-transformer:1.4.1"
```

버전은 BOM 없이 개별 모듈마다 명시해야 하며, 모듈 간 버전이 어긋나면 런타임에 `NoSuchMethodError`류 예외가 발생할 수 있으므로 항상 동일 버전으로 맞춘다.

# Media3 아키텍처

Media3는 크게 다음 레이어로 구성된다.

- `media3-common`: Player 인터페이스, MediaItem, MediaMetadata 등 공통 API
- `media3-exoplayer`: ExoPlayer 구현체, Renderer, TrackSelector, LoadControl
- `media3-session`: MediaSession, MediaController, MediaSessionService, MediaLibraryService
- `media3-ui`: PlayerView, PlayerControlView 등 UI 컴포넌트
- `media3-extractor`: 컨테이너 포맷 파싱 (mp4, ts, mkv, ogg, wav 등)
- `media3-datasource`: 로컬/네트워크 데이터 소스 추상화 (HttpDataSource, FileDataSource, AssetDataSource 등)
- `media3-decoder`: 소프트웨어 디코더 확장 (FFmpeg, AV1 등)
- `media3-cast`: Google Cast 연동
- `media3-transformer`: 미디어 트랜스코딩/편집

<br>

재생 파이프라인은 개략적으로 다음 순서로 흐른다.

```
MediaItem
  → MediaSource (URI 해석, 매니페스트 파싱)
    → Extractor (컨테이너 디먹싱 → 샘플 큐)
      → Renderer (오디오/비디오/텍스트 각각 디코딩)
        → AudioSink / Surface (실제 출력)
```

각 단계는 인터페이스로 추상화되어 있어서, 예를 들어 `Extractor`만 커스텀으로 교체하거나 `Renderer`만 교체하는 식의 부분 커스터마이징이 가능하다. 이는 표준 포맷을 벗어나는 독자 포맷이나, 재생과 동시에 실시간 신호 처리를 해야 하는 경우에 특히 유용하다.

## ExoPlayer 내부 스레드 모델

ExoPlayer는 내부적으로 별도의 playback thread를 하나 띄우고, 앱의 메인 스레드에서 호출한 명령(재생, 시크, 트랙 변경 등)을 메시지 큐로 전달해 처리한다. `Player` 인터페이스의 메서드는 대부분 non-blocking이며, 실제 상태 변화는 `Player.Listener` 콜백을 통해 메인 스레드로 다시 전달된다. 따라서 플레이어 상태를 폴링하기보다는 리스너 기반으로 UI를 갱신하는 것이 원칙이다.

# 주요 컴포넌트

## ExoPlayer

`Player` 인터페이스의 구현체로, 실제 디코딩과 렌더링을 담당하는 핵심 클래스다. `ExoPlayer.Builder`로 생성하며, 다양한 하위 컴포넌트를 주입할 수 있다.

```kotlin
val player = ExoPlayer.Builder(context)
    .setTrackSelector(DefaultTrackSelector(context))
    .setLoadControl(DefaultLoadControl())
    .setBandwidthMeter(DefaultBandwidthMeter.getSingletonInstance(context))
    .setRenderersFactory(DefaultRenderersFactory(context))
    .setMediaSourceFactory(DefaultMediaSourceFactory(context))
    .setSeekParameters(SeekParameters.CLOSEST_SYNC)
    .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
    .setWakeMode(C.WAKE_MODE_NETWORK)
    .build()
```

`handleAudioFocus`를 true로 두면 ExoPlayer가 자동으로 AudioFocus 요청/포기를 처리해준다. 전화가 오거나 다른 앱이 오디오를 재생할 때 자동으로 일시정지/음소거되는 동작이 여기서 나온다.

## MediaSource

재생할 미디어의 소스를 정의하는 컴포넌트다. 포맷별로 다음과 같은 구현체가 있다.

- `ProgressiveMediaSource`: mp4, mp3 등 단일 파일 스트리밍
- `DashMediaSource`: MPEG-DASH
- `HlsMediaSource`: HTTP Live Streaming
- `SsMediaSource`: SmoothStreaming
- `RtspMediaSource`: RTSP
- `ConcatenatingMediaSource` (또는 `setMediaItems`로 대체): 여러 소스를 이어붙임
- `ClippingMediaSource`: 특정 구간만 잘라서 재생
- `MergingMediaSource`: 오디오/비디오 소스를 별도로 합침 (예: 비디오 파일 + 별도 자막 파일)

<br>

대부분은 `MediaItem`을 넘기면 `DefaultMediaSourceFactory`가 URI의 확장자/MIME 타입을 보고 알아서 적절한 타입을 선택하므로, 직접 MediaSource를 만들 일은 흔치 않다. 다만 로컬 파일 시스템이 아닌 커스텀 스토리지(예: 암호화된 내부 저장소)에서 읽어야 한다면 `MediaSource.Factory`를 직접 구현해야 한다.

```kotlin
val mediaSourceFactory = DefaultMediaSourceFactory(context)
    .setDataSourceFactory(customDataSourceFactory)

val player = ExoPlayer.Builder(context)
    .setMediaSourceFactory(mediaSourceFactory)
    .build()
```

## PlayerView

`media3-ui`에서 제공하는 XML 기반 뷰로, 비디오 서피스와 컨트롤러(재생/일시정지/시크바)를 한 번에 제공한다. Compose 환경에서는 `AndroidView`로 감싸서 사용한다.

```kotlin
AndroidView(
    modifier = Modifier.fillMaxWidth(),
    factory = { ctx ->
        PlayerView(ctx).apply {
            player = exoPlayer
            useController = true
            controllerAutoShow = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    },
    update = { view ->
        view.player = exoPlayer
    }
)
```

오디오 전용 앱이라 비디오 서피스가 필요 없다면 `PlayerView` 대신 `PlayerControlView`만 사용하거나, 아예 UI 모듈 없이 커스텀 Compose 컨트롤을 직접 그리는 경우도 많다.

## TrackSelector

여러 화질/음질 트랙 중 어떤 것을 재생할지 결정하는 컴포넌트. 기본 구현체는 `DefaultTrackSelector`이며, 대역폭, 화면 크기, 언어 선호도 등을 기준으로 자동 선택한다.

## LoadControl

버퍼링 정책을 담당한다. 최소/최대 버퍼 길이, 재생 시작에 필요한 최소 버퍼 등을 설정한다.

```kotlin
val loadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        /* minBufferMs = */ 15_000,
        /* maxBufferMs = */ 50_000,
        /* bufferForPlaybackMs = */ 2_500,
        /* bufferForPlaybackAfterRebufferMs = */ 5_000
    )
    .build()
```

## BandwidthMeter

현재 네트워크 대역폭을 추정해 TrackSelector가 적절한 화질을 고르도록 돕는다. 앱 전역에서 하나의 인스턴스를 공유(`DefaultBandwidthMeter.getSingletonInstance`)하면 여러 플레이어가 있어도 대역폭 추정치를 공유할 수 있다.

# 기본 사용법

```kotlin
val player = ExoPlayer.Builder(context).build()

val mediaItem = MediaItem.fromUri("https://example.com/video.mp4")
player.setMediaItem(mediaItem)
player.prepare()
player.playWhenReady = true

player.addListener(object : Player.Listener {
    override fun onPlaybackStateChanged(state: Int) {
        when (state) {
            Player.STATE_IDLE -> { /* 초기 상태, prepare 전 */ }
            Player.STATE_BUFFERING -> { /* 버퍼링 중 */ }
            Player.STATE_READY -> { /* 재생 준비 완료 */ }
            Player.STATE_ENDED -> { /* 재생 종료 */ }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        // 실제 재생 중 여부. playWhenReady && state == READY 를 합친 값
    }

    override fun onPlayerError(error: PlaybackException) {
        // 네트워크 오류, 디코더 초기화 실패 등
        Log.e("Player", "playback error", error)
    }
})

// 재생 제어
player.play()
player.pause()
player.seekTo(30_000L) // 30초 지점으로 이동
player.setPlaybackSpeed(1.5f)

// 해제
player.release()
```

Compose에서는 `DisposableEffect`로 라이프사이클에 맞춰 release를 호출해야 한다. `remember`로 감싸지 않으면 recomposition마다 플레이어가 재생성되어 리소스가 누수되므로 주의한다.

```kotlin
val player = remember {
    ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(uri))
        prepare()
    }
}

DisposableEffect(Unit) {
    onDispose {
        player.release()
    }
}
```

# Player 상태와 이벤트 처리

`Player.Listener`는 재생과 관련된 거의 모든 이벤트를 콜백으로 제공한다. 실무에서 자주 쓰는 콜백은 다음과 같다.

```kotlin
player.addListener(object : Player.Listener {
    override fun onEvents(player: Player, events: Player.Events) {
        // 여러 이벤트가 동시에 발생했을 때 한 번에 처리하고 싶다면 이 콜백을 사용
        if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
            updateUiState(player)
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        // 시크, 자동 재생목록 이동 등으로 위치가 불연속적으로 바뀔 때
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // 재생목록에서 다음 곡/영상으로 넘어갈 때
    }

    override fun onTracksChanged(tracks: Tracks) {
        // 사용 가능한 트랙 목록이 바뀔 때 (화질 옵션 UI 갱신 등에 사용)
    }

    override fun onVolumeChanged(volume: Float) {}

    override fun onRepeatModeChanged(repeatMode: Int) {}

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {}
})
```

`onEvents` 콜백을 활용하면 여러 개별 콜백에서 중복으로 UI 갱신 로직을 타는 것을 피할 수 있어서, 상태를 하나의 State 객체로 뽑아 매핑하는 패턴을 쓸 때 유용하다.

## 현재 재생 위치 폴링

`currentPosition`은 콜백이 아니라 값을 직접 조회해야 하므로, 진행 바(seek bar) 갱신에는 `Handler`나 코루틴으로 주기적으로 폴링하는 방식을 쓴다.

```kotlin
LaunchedEffect(player) {
    while (isActive) {
        currentPositionMs = player.currentPosition
        delay(200)
    }
}
```

# MediaItem과 메타데이터

`MediaItem.Builder`로 제목, 아트워크, 클리핑 구간, DRM 설정 등을 함께 지정할 수 있다.

```kotlin
val mediaItem = MediaItem.Builder()
    .setUri(uri)
    .setMediaId("recording_001")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle("녹음 파일")
            .setArtist("현서")
            .setArtworkUri(artworkUri)
            .build()
    )
    .setClippingConfiguration(
        MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(5_000)
            .setEndPositionMs(60_000)
            .build()
    )
    .build()
```

`setMediaId`는 MediaSession/MediaController 환경에서 특정 아이템을 식별할 때 사용되며, 알림의 다음/이전 곡 처리나 재생목록 동기화에 필요하다.

플레이리스트는 `setMediaItems(List<MediaItem>)`로 한 번에 지정하고, `seekToNext()` / `seekToPrevious()` / `seekToDefaultPosition(index)`로 이동한다.

# 재생 목록(Playlist) 관리

ExoPlayer는 `Player` 인터페이스 레벨에서 플레이리스트를 1급 개념으로 지원한다.

```kotlin
// 초기 설정
player.setMediaItems(listOf(item1, item2, item3))

// 동적으로 추가/삭제
player.addMediaItem(item4)                 // 맨 뒤에 추가
player.addMediaItem(1, item5)              // 특정 인덱스에 삽입
player.removeMediaItem(0)                  // 인덱스로 제거
player.moveMediaItem(0, 2)                 // 순서 이동
player.clearMediaItems()                   // 전체 삭제

// 반복/셔플 모드
player.repeatMode = Player.REPEAT_MODE_ALL
player.shuffleModeEnabled = true

// 현재 재생 목록 조회
val currentIndex = player.currentMediaItemIndex
val itemCount = player.mediaItemCount
val currentItem = player.currentMediaItem
```

여러 녹음 파일을 순차 재생하는 앱이라면, ViewModel에서 `List<MediaItem>`을 관리하고 `setMediaItems`로 통째로 갈아끼우는 방식이 개별 add/remove를 호출하는 것보다 상태 동기화가 단순하다.

# TrackSelector 커스터마이징

화질/음질 트랙을 제어하려면 `DefaultTrackSelector`의 파라미터를 조정한다.

```kotlin
val trackSelector = DefaultTrackSelector(context)
trackSelector.setParameters(
    trackSelector.buildUponParameters()
        .setMaxVideoSizeSd()
        .setPreferredAudioLanguage("ko")
        .setForceLowestBitrate(false)
        .setMaxVideoBitrate(2_000_000)
        .setTunnelingEnabled(true)
)
```

특정 트랙을 사용자가 직접 선택하게 하려면 `player.currentTracks`에서 그룹 목록을 뽑아 UI로 보여주고, 선택 시 `TrackSelectionOverride`를 적용한다.

```kotlin
val tracks = player.currentTracks
for (group in tracks.groups) {
    if (group.type == C.TRACK_TYPE_VIDEO) {
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            // format.height, format.bitrate 등으로 화질 옵션 UI 구성
        }
    }
}

// 사용자가 특정 트랙 그룹의 i번째를 선택했을 때
val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(i))
trackSelector.setParameters(
    trackSelector.buildUponParameters().addOverride(override)
)
```

# Renderer와 Decoder 커스터마이징

특수 코덱이나 커스텀 디코딩 로직이 필요하면 `RenderersFactory`를 구현해 `ExoPlayer.Builder`에 주입한다. 온디바이스 오디오 처리(예: 녹음 파일의 실시간 이펙트, 노이즈 게이트, 볼륨 정규화)처럼 표준 파이프라인을 벗어나는 경우에 사용한다.

```kotlin
class CustomRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        // 커스텀 AudioSink 또는 AudioProcessor 체인을 삽입할 수 있는 지점
        super.buildAudioRenderers(
            context, extensionRendererMode, mediaCodecSelector,
            enableDecoderFallback, audioSink, eventHandler, eventListener, out
        )
    }
}

val player = ExoPlayer.Builder(context)
    .setRenderersFactory(CustomRenderersFactory(context))
    .build()
```

## AudioProcessor 체인

오디오 신호에 실시간으로 이펙트를 적용하고 싶을 때는 `AudioSink`에 `AudioProcessor` 배열을 넣는 방식이 표준적이다. `DefaultAudioSink.Builder`에 `setAudioProcessors`로 커스텀 프로세서를 등록한다.

```kotlin
val customProcessor = object : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return inputAudioFormat // 포맷은 그대로 유지
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // 여기서 PCM 샘플을 직접 읽고 가공한 뒤 outputBuffer에 씀
    }
}

val audioSink = DefaultAudioSink.Builder(context)
    .setAudioProcessors(arrayOf(customProcessor))
    .build()
```

이 지점이 녹음/재생 앱에서 실시간 파형 시각화나 노이즈 제거를 붙이는 진입점이 된다.

# 커스텀 DataSource와 캐시

로컬 암호화 저장소나 커스텀 프로토콜에서 미디어를 읽어야 한다면 `DataSource.Factory`를 직접 구현한다.

```kotlin
class EncryptedFileDataSource : BaseDataSource(/* isNetwork = */ false) {
    private var inputStream: InputStream? = null

    override fun open(dataSpec: DataSpec): Long {
        val file = File(dataSpec.uri.path!!)
        inputStream = decryptingInputStream(file) // 복호화 스트림
        return file.length()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return inputStream?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
    }

    override fun getUri(): Uri? = null

    override fun close() {
        inputStream?.close()
    }
}
```

## SimpleCache를 이용한 다운로드/캐싱

네트워크 스트리밍 시 대역폭을 아끼고 재생 대기 시간을 줄이려면 `CacheDataSource`로 감싸는 것이 표준이다.

```kotlin
val cache = SimpleCache(
    File(context.cacheDir, "media_cache"),
    LeastRecentlyUsedCacheEvictor(200L * 1024 * 1024), // 200MB
    StandaloneDatabaseProvider(context)
)

val cacheDataSourceFactory = CacheDataSource.Factory()
    .setCache(cache)
    .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)
```

# DRM 처리

유료 콘텐츠 등 DRM이 걸린 스트림은 `MediaItem`에 `DrmConfiguration`을 추가한다. Widevine이 안드로이드에서 가장 널리 쓰인다.

```kotlin
val mediaItem = MediaItem.Builder()
    .setUri(uri)
    .setDrmConfiguration(
        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
            .setLicenseUri(licenseServerUrl)
            .setLicenseRequestHeaders(mapOf("Authorization" to token))
            .build()
    )
    .build()
```

오프라인 재생을 지원해야 한다면 `OfflineLicenseHelper`로 라이선스를 미리 다운로드해 저장해두고, 재생 시 로컬 라이선스를 사용하도록 구성한다.

# MediaSessionService를 이용한 백그라운드 재생

백그라운드/알림 재생을 지원하려면 `MediaSessionService`를 상속한 서비스에서 `MediaSession`을 생성해 관리한다. 이렇게 하면 시스템 알림, 블루투스 헤드셋 버튼, Android Auto/Wear 연동이 표준 방식으로 동작한다.

```kotlin
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 앱이 태스크에서 스와이프로 제거될 때 재생을 계속할지 여부 결정
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session.run {
            player.release()
            release()
        }
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // 커스텀 명령 등록, 연결 허용 여부 판단
            return super.onConnect(session, controller)
        }
    }
}
```

매니페스트에는 포그라운드 서비스 타입을 명시해야 한다.

```xml
<service
    android:name=".PlaybackService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
</service>
```

# MediaController와 알림(Notification) 커스터마이징

앱 UI(Activity/Compose)에서는 `MediaController.Builder`로 세션에 연결해 원격으로 제어한다. 프로세스가 분리되어 있어도(예: 위젯, 다른 액티비티) 동일한 인터페이스로 제어할 수 있다는 것이 MediaSession 구조의 장점이다.

```kotlin
val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

controllerFuture.addListener({
    val controller = controllerFuture.get()
    controller.play()
    controller.addListener(object : Player.Listener {
        // 서비스 쪽 플레이어 상태 변화가 여기로 그대로 전달됨
    })
}, MoreExecutors.directExecutor())
```

기본 알림 UI를 커스터마이징하려면 `MediaNotification.Provider`를 구현해 세션에 등록한다.

```kotlin
class CustomNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {
    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean
    ): ImmutableList<CommandButton> {
        // 재생/일시정지 외에 커스텀 버튼(예: 15초 뒤로 감기)을 추가할 수 있음
        return super.getMediaButtons(session, playerCommands, customLayout, showPauseButton)
    }
}
```

# Cast 및 확장 모듈

`media3-cast` 모듈을 추가하면 `CastPlayer`를 `ExoPlayer`와 동일한 `Player` 인터페이스로 다룰 수 있다. 로컬 재생과 캐스트 재생을 전환할 때 UI 레이어는 `Player` 인터페이스만 바라보게 설계하면 전환 로직이 단순해진다.

```kotlin
val castContext = CastContext.getSharedInstance(context)
val castPlayer = CastPlayer(castContext)

val currentPlayer: Player = if (castSessionAvailable) castPlayer else exoPlayer
playerView.player = currentPlayer
```

`media3-transformer`는 재생이 아니라 트랜스코딩/편집(트림, 포맷 변환, 워터마크 삽입 등)에 쓰이며, 별도의 `Transformer` API를 제공한다.

# Analytics와 로깅

`AnalyticsListener`는 `Player.Listener`보다 더 저수준의 정보(디코더 초기화 시간, 드롭 프레임 수, 로드 이벤트 등)를 제공한다. 디버깅이나 QoS 수집에 사용한다.

```kotlin
player.addAnalyticsListener(object : AnalyticsListener {
    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        // 프레임 드롭이 잦으면 디코더/렌더링 부하를 의심
    }

    override fun onLoadError(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: IOException,
        wasCanceled: Boolean
    ) {
        // 네트워크 로드 실패 상세 정보
    }
})
```

개발 중에는 `EventLogger`를 붙여두면 로그캣에 상태 전이, 트랙 변경, 에러를 사람이 읽기 쉬운 형태로 출력해준다.

```kotlin
player.addAnalyticsListener(EventLogger())
```

# 성능 최적화

- 앱 전역에서 `ExoPlayer` 인스턴스를 하나만 재사용하고, 화면 전환 시 `MediaItem`만 갈아끼우는 편이 매번 새로 생성하는 것보다 초기화 비용이 적다.
- `DefaultBandwidthMeter`를 싱글턴으로 공유하면 여러 플레이어가 있는 화면(예: 목록에서 여러 개 미리듣기)에서 대역폭 추정이 더 정확해진다.
- 이미지 썸네일이 붙은 오디오 전용 콘텐츠라면 비디오 렌더러를 비활성화(`setRendererDisabled`)해 불필요한 리소스 사용을 줄일 수 있다.
- 짧은 클립을 반복 재생하는 경우 `LoopingMediaSource`류 접근보다 `player.repeatMode = Player.REPEAT_MODE_ONE`이 오버헤드가 적다.
- 프리로드가 필요한 다음 곡은 `ExoPlayer.Builder.setPreloadConfiguration`으로 다음 아이템의 버퍼링을 미리 시작하게 할 수 있다(최근 버전 기준).

# 테스트

ExoPlayer는 `FakeExoPlayer`나 `TestPlayerRunHelper` 같은 테스트 유틸을 `media3-test-utils` 모듈에서 제공한다. UI 레이어를 테스트할 때는 실제 네트워크 재생 대신 `Player` 인터페이스를 mocking하거나 fake 구현체를 주입하는 방식을 쓴다.

```gradle
testImplementation "androidx.media3:media3-test-utils:1.4.1"
testImplementation "androidx.media3:media3-test-utils-robolectric:1.4.1"
```

```kotlin
@Test
fun `재생 시작 시 isPlaying이 true가 된다`() {
    val player = TestExoPlayerBuilder(context).build()
    player.setMediaItem(MediaItem.fromUri(testUri))
    player.prepare()
    player.play()

    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
    assertThat(player.isPlaying).isTrue()
}
```

# 자주 겪는 이슈

- `player.release()`를 누락하면 서비스 종료 후에도 리소스가 남아 메모리 누수로 이어진다.
- Compose에서 `PlayerView`를 여러 번 재생성하면 화면 깜빡임이 발생하므로 `remember`로 인스턴스를 유지해야 한다.
- HLS/DASH 재생 시 해당 확장 모듈(`media3-exoplayer-hls`, `media3-exoplayer-dash`)을 별도로 추가하지 않으면 `UnrecognizedInputFormatException`이 발생한다.
- 포그라운드 서비스 없이 백그라운드 재생을 시도하면 Android 12 이상에서 시스템에 의해 강제 종료될 수 있다.
- `setWakeMode(C.WAKE_MODE_NETWORK)`를 빼먹으면 화면이 꺼졌을 때 네트워크 스트리밍이 끊길 수 있다.
- 여러 모듈(`media3-exoplayer`, `media3-ui`, `media3-session` 등)의 버전이 서로 다르면 `NoSuchMethodError`나 `AbstractMethodError`가 런타임에 발생한다. 항상 동일 버전으로 맞춘다.
- `MediaItem`의 `mediaId`를 지정하지 않으면 MediaSession 환경에서 알림/외부 컨트롤러가 특정 트랙을 식별하지 못해 다음 곡 표시 등이 어긋날 수 있다.
- 커스텀 `AudioProcessor`에서 `queueInput`을 구현할 때 출력 버퍼 크기를 잘못 계산하면 지지직거리는 잡음이나 재생 끊김이 발생한다. 입력/출력 프레임 수 매핑을 정확히 맞춰야 한다.
- `onTaskRemoved`를 오버라이드하지 않으면 앱을 스와이프로 종료해도 백그라운드 재생이 계속되어, 사용자가 종료했다고 생각한 앱이 계속 소리를 내는 문제가 생길 수 있다.

# 정리

Media3는 ExoPlayer를 중심으로 세션, UI, 다운로드, Cast, Transformer 기능을 하나의 라이브러리군으로 통합한 것이다. 기본 재생은 `MediaItem + ExoPlayer` 조합으로 간단히 구현되고, 화질/음질 제어는 `TrackSelector`, 버퍼링 정책은 `LoadControl`, 캐싱은 `CacheDataSource`로 각각 분리되어 있어 필요한 부분만 골라 커스터마이징할 수 있다.

백그라운드/알림 재생이 필요하면 `MediaSessionService` + `MediaSession` + `MediaController` 조합으로 표준 방식을 따르는 것이 유지보수 측면에서 유리하다. 실시간 오디오 신호 처리가 필요한 경우에는 `RenderersFactory`와 `AudioProcessor` 체인이 진입점이 되며, 이는 표준 재생 파이프라인을 건드리지 않고 커스텀 로직을 끼워 넣을 수 있는 지점이다.

전체적으로 각 레이어(Source, Extractor, Renderer, Sink)가 인터페이스로 분리되어 있다는 점이 Media3의 핵심 설계이며, 이 구조를 이해하면 표준 API로 커버되지 않는 요구사항이 생겼을 때 어느 지점을 교체해야 하는지 판단하기 쉬워진다.
