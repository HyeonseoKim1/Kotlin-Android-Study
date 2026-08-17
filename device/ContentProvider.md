# ContentProvider

<br>

## 목차

1. ContentProvider란 무엇인가
2. ContentProvider가 필요한 이유
3. 기본 구조와 생명주기
4. Uri와 UriMatcher
5. query() 구현하기
6. insert(), update(), delete() 구현하기
7. ContentResolver로 데이터 접근하기
8. AndroidManifest 등록과 권한(Permission) 설정
9. FileProvider로 파일 공유하기
10. ContentProvider와 Room 연동
11. ContentProviderOperation과 Batch 처리
12. ContentObserver로 데이터 변경 감지하기
13. 주의사항과 자주 하는 실수
14. 정리

<br>

## 1. ContentProvider란 무엇인가

ContentProvider는 안드로이드 4대 컴포넌트(Activity, Service, BroadcastReceiver, ContentProvider) 중 하나로, **앱 간에 데이터를 공유하기 위한 표준 인터페이스**다. 안드로이드는 기본적으로 각 앱이 자신만의 프로세스와 저장 공간(sandbox)을 가지고 있어서 다른 앱의 데이터베이스나 파일에 직접 접근할 수 없다. ContentProvider는 이 샌드박스 경계를 안전하게 넘나들 수 있게 해주는 유일한 표준 통로다.

가장 대표적인 예시가 연락처(Contacts), 미디어(MediaStore), 캘린더(Calendar)다. 예를 들어 갤러리 앱이 아니어도 사진 목록을 가져올 수 있는 이유는 미디어 스토어가 ContentProvider로 노출되어 있기 때문이다.

핵심 특징은 다음과 같다.

- 데이터를 테이블 형태(행과 열)로 추상화해서 노출한다. 실제 저장소가 SQLite든, 파일이든, 네트워크든 상관없다.
- `Uri`라는 고유 주소로 데이터를 식별한다.
- `ContentResolver`를 통해서만 접근 가능하며, 클라이언트는 ContentProvider의 내부 구현을 몰라도 된다.
- 프로세스 간 통신(IPC)을 내부적으로 처리해준다.

즉 ContentProvider는 "데이터베이스"가 아니라 **데이터 접근을 위한 추상화 레이어**라고 이해하는 게 정확하다.

```kotlin
// ContentProvider는 이런 식으로 다른 앱에서 데이터를 조회하게 해준다
val cursor = context.contentResolver.query(
    ContactsContract.Contacts.CONTENT_URI,
    null, null, null, null
)
```

| 구성 요소 | 역할 |
|---|---|
| ContentProvider | 데이터를 소유하고 CRUD 메서드를 구현하는 서버 역할 |
| ContentResolver | 클라이언트가 사용하는 창구, Provider를 몰라도 Uri만 알면 접근 가능 |
| Uri | 데이터의 위치를 나타내는 고유 식별자 |
| Cursor | query() 결과를 담는 테이블 형태의 데이터 셋 |

**실전 팁**
- 앱 내부에서만 쓰는 데이터라면 ContentProvider 대신 Room DAO를 직접 쓰는 게 훨씬 간단하다. ContentProvider는 "앱 간 공유"가 필요할 때만 고려한다.
- Jetpack DocumentsProvider, FileProvider도 ContentProvider를 상속한 특수 목적 구현체다.

<br>

## 2. ContentProvider가 필요한 이유

"굳이 ContentProvider를 안 써도 SharedPreferences나 파일로 데이터를 주고받으면 되지 않나?"라는 의문이 들 수 있다. 하지만 안드로이드의 프로세스 격리 정책 때문에 다른 앱은 우리 앱의 내부 저장소(internal storage)나 SharedPreferences에 직접 접근할 수 없다. 루트 권한 없이는 원천적으로 불가능하다.

ContentProvider가 필요한 대표적인 상황은 다음과 같다.

1. 다른 앱과 데이터를 공유해야 할 때 (예: 메모 앱이 위젯 앱에 메모 목록을 제공)
2. 시스템이 제공하는 표준 데이터(연락처, 캘린더, 미디어)에 접근해야 할 때
3. `FileProvider`를 이용해 다른 앱에 안전하게 파일 Uri를 공유해야 할 때 (카메라 촬영 결과 전달 등)
4. `Intent`로 파일을 주고받을 때 `file://` 대신 `content://` Uri를 써야 하는 안드로이드 7.0(API 24) 이상의 보안 정책을 지켜야 할 때

```kotlin
// 안드로이드 7.0 이상에서는 file:// Uri를 그대로 Intent에 담으면 FileUriExposedException 발생
val photoUri: Uri = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    photoFile
)

val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
    putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
}
```

| 방식 | 앱 간 공유 가능 여부 | 비고 |
|---|---|---|
| SharedPreferences | 불가능 | private 모드가 기본, MODE_WORLD_READABLE은 API 17부터 제거됨 |
| 내부 저장소 파일 | 불가능 | 앱 전용 샌드박스 |
| 외부 저장소(External Storage) | 제한적 가능 | Scoped Storage 정책 이후 접근이 까다로움 |
| ContentProvider | 가능 | 권한 기반으로 세밀하게 통제 가능한 유일한 표준 방법 |

**실전 팁**
- 단순히 파일 하나를 다른 앱에 공유하는 목적이라면 직접 ContentProvider를 구현하지 말고 Jetpack의 `FileProvider`를 매니페스트에 선언만 해서 쓰는 게 정석이다.
- "앱 간 공유"가 요구사항에 없다면 ContentProvider 도입 자체를 재고하자. 오버엔지니어링이 되기 쉽다.

<br>

## 3. 기본 구조와 생명주기

ContentProvider를 직접 구현하려면 `ContentProvider` 추상 클래스를 상속하고 6개의 메서드를 반드시 오버라이드해야 한다.

```kotlin
class NoteProvider : ContentProvider() {

    private lateinit var dbHelper: NoteDbHelper

    override fun onCreate(): Boolean {
        dbHelper = NoteDbHelper(context!!)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val db = dbHelper.readableDatabase
        return db.query("notes", projection, selection, selectionArgs, null, null, sortOrder)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val db = dbHelper.writableDatabase
        val id = db.insert("notes", null, values)
        return ContentUris.withAppendedId(CONTENT_URI, id)
    }

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?
    ): Int {
        val db = dbHelper.writableDatabase
        return db.update("notes", values, selection, selectionArgs)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        val db = dbHelper.writableDatabase
        return db.delete("notes", selection, selectionArgs)
    }

    override fun getType(uri: Uri): String? = "vnd.android.cursor.dir/notes"

    companion object {
        val CONTENT_URI: Uri = Uri.parse("content://com.example.app.provider/notes")
    }
}
```

`onCreate()`는 앱 프로세스가 시작될 때, 다른 4대 컴포넌트(Activity 등)보다 **먼저** 호출된다는 점이 중요하다. 즉 `Application.onCreate()`보다도 먼저 실행될 수 있으므로, 여기서 무거운 초기화 작업을 하면 앱 시작 속도에 큰 영향을 준다.

| 생명주기 시점 | 설명 |
|---|---|
| 프로세스 시작 | 시스템이 ContentProvider를 가장 먼저 인스턴스화 |
| onCreate() 호출 | DB 연결 등 초기화, false 반환 시 Provider 비활성화 |
| 요청 처리 | query/insert/update/delete가 여러 스레드에서 동시에 호출될 수 있음 |
| 프로세스 종료 | 별도의 onDestroy() 콜백은 없음 (프로세스와 생명주기 같이함) |

**실전 팁**
- `onCreate()`에서는 절대 디스크 I/O나 네트워크 작업을 하지 말자. 지연 초기화(lazy initialization) 패턴을 써서 실제 쿼리가 들어올 때 DB를 연다.
- ContentProvider의 각 메서드는 여러 스레드에서 동시에 호출될 수 있으므로 내부 상태를 다룰 때는 스레드 안전성을 고려해야 한다.

<br>

## 4. Uri와 UriMatcher

ContentProvider의 모든 데이터는 `Uri`로 식별된다. 형식은 `content://authority/path/id`다.

- `content://` : ContentProvider임을 나타내는 스킴
- `authority` : 어떤 Provider인지 구분하는 고유 이름 (보통 패키지명 기반)
- `path` : 테이블(리소스 종류)을 나타냄
- `id` : 특정 행(row)을 가리키는 선택적 값

여러 종류의 Uri 패턴을 처리해야 하므로 `UriMatcher`로 어떤 Uri가 들어왔는지 분기 처리한다.

```kotlin
private const val AUTHORITY = "com.example.app.provider"
private const val NOTES = 1
private const val NOTE_ID = 2

private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
    addURI(AUTHORITY, "notes", NOTES)
    addURI(AUTHORITY, "notes/#", NOTE_ID) // '#'는 숫자 와일드카드
}

override fun query(
    uri: Uri,
    projection: Array<String>?,
    selection: String?,
    selectionArgs: Array<String>?,
    sortOrder: String?
): Cursor? {
    val db = dbHelper.readableDatabase
    return when (uriMatcher.match(uri)) {
        NOTES -> db.query("notes", projection, selection, selectionArgs, null, null, sortOrder)
        NOTE_ID -> {
            val id = ContentUris.parseId(uri)
            db.query("notes", projection, "_id=?", arrayOf(id.toString()), null, null, sortOrder)
        }
        else -> throw IllegalArgumentException("알 수 없는 Uri: $uri")
    }
}
```

| 와일드카드 | 의미 |
|---|---|
| `#` | 숫자(정수) 하나를 매칭 |
| `*` | 임의의 문자열 하나를 매칭 |
| 없음 | 정확히 일치하는 경로만 매칭 |

**실전 팁**
- `UriMatcher`의 매칭 코드(NOTES, NOTE_ID 등)는 `object`나 companion object의 `const val`로 빼서 매직 넘버를 피하자.
- Uri 패턴이 늘어날수록 `when` 분기가 길어지므로, 테이블이 많은 Provider는 Uri 처리 로직을 별도 클래스로 분리하는 것이 유지보수에 좋다.

<br>

## 5. query() 구현하기

`query()`는 ContentProvider에서 가장 자주 호출되는 메서드로, SQL의 SELECT와 유사한 역할을 한다. 반환 타입은 `Cursor`이며, 클라이언트는 이 Cursor를 순회하며 데이터를 읽는다.

```kotlin
override fun query(
    uri: Uri,
    projection: Array<String>?,
    selection: String?,
    selectionArgs: Array<String>?,
    sortOrder: String?
): Cursor? {
    val db = dbHelper.readableDatabase
    val cursor = when (uriMatcher.match(uri)) {
        NOTES -> db.query(
            "notes",           // 테이블
            projection,        // 가져올 컬럼 (null이면 전체)
            selection,         // WHERE 절 (예: "title=?")
            selectionArgs,     // WHERE 절의 ? 에 바인딩할 값
            null,              // GROUP BY
            null,              // HAVING
            sortOrder          // ORDER BY
        )
        else -> throw IllegalArgumentException("지원하지 않는 Uri: $uri")
    }
    // 데이터 변경을 관찰하는 쪽에 알리기 위해 NotificationUri 설정
    cursor.setNotificationUri(context?.contentResolver, uri)
    return cursor
}
```

클라이언트 측에서는 이렇게 읽는다.

```kotlin
val cursor = contentResolver.query(
    NoteProvider.CONTENT_URI,
    arrayOf("_id", "title", "content"),
    "title LIKE ?",
    arrayOf("%kotlin%"),
    "created_at DESC"
)

cursor?.use {
    while (it.moveToNext()) {
        val id = it.getLong(it.getColumnIndexOrThrow("_id"))
        val title = it.getString(it.getColumnIndexOrThrow("title"))
        Log.d("NoteProvider", "id=$id, title=$title")
    }
}
```

| 파라미터 | SQL 대응 | 설명 |
|---|---|---|
| projection | SELECT 컬럼 목록 | null이면 전체 컬럼 |
| selection | WHERE 절 (조건식) | `?` 플레이스홀더 사용 권장 |
| selectionArgs | WHERE 절 바인딩 값 | selection의 `?` 개수와 일치해야 함 |
| sortOrder | ORDER BY | null이면 정렬 안 함 |

**실전 팁**
- `Cursor`는 반드시 `use {}` 블록이나 `close()`로 닫아야 한다. 닫지 않으면 리소스 누수(cursor leak)로 크래시나 경고 로그가 발생한다.
- selection에 문자열을 직접 이어붙이지 말고 항상 `?` + selectionArgs 조합을 쓰자. SQL Injection을 막는 가장 기본적인 방어다.
- 대용량 쿼리는 반드시 백그라운드 스레드에서 호출해야 한다. ContentResolver.query() 자체는 스레드를 자동으로 바꿔주지 않는다.

<br>

## 6. insert(), update(), delete() 구현하기

나머지 CUD(Create, Update, Delete) 메서드도 내부적으로는 SQLiteDatabase의 대응 메서드를 감싸는 형태가 일반적이다.

```kotlin
override fun insert(uri: Uri, values: ContentValues?): Uri {
    val db = dbHelper.writableDatabase
    val id = when (uriMatcher.match(uri)) {
        NOTES -> db.insert("notes", null, values)
        else -> throw IllegalArgumentException("insert를 지원하지 않는 Uri: $uri")
    }
    if (id <= 0) throw SQLException("삽입 실패: $uri")

    val resultUri = ContentUris.withAppendedId(CONTENT_URI, id)
    context?.contentResolver?.notifyChange(resultUri, null) // 변경 알림
    return resultUri
}

override fun update(
    uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?
): Int {
    val db = dbHelper.writableDatabase
    val count = when (uriMatcher.match(uri)) {
        NOTES -> db.update("notes", values, selection, selectionArgs)
        NOTE_ID -> {
            val id = ContentUris.parseId(uri)
            db.update("notes", values, "_id=?", arrayOf(id.toString()))
        }
        else -> throw IllegalArgumentException("update를 지원하지 않는 Uri: $uri")
    }
    if (count > 0) context?.contentResolver?.notifyChange(uri, null)
    return count
}

override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
    val db = dbHelper.writableDatabase
    val count = when (uriMatcher.match(uri)) {
        NOTES -> db.delete("notes", selection, selectionArgs)
        NOTE_ID -> {
            val id = ContentUris.parseId(uri)
            db.delete("notes", "_id=?", arrayOf(id.toString()))
        }
        else -> throw IllegalArgumentException("delete를 지원하지 않는 Uri: $uri")
    }
    if (count > 0) context?.contentResolver?.notifyChange(uri, null)
    return count
}
```

| 메서드 | 반환 타입 | 실패 시 |
|---|---|---|
| insert() | 새로 생성된 행의 Uri | SQLException 던지는 것이 관례 |
| update() | 변경된 행 개수(Int) | 0 반환 (예외 던지지 않음) |
| delete() | 삭제된 행 개수(Int) | 0 반환 (예외 던지지 않음) |

**실전 팁**
- `notifyChange()`를 빼먹으면 `ContentObserver`가 등록되어 있어도 변경 사항을 감지하지 못한다. CUD 메서드 끝에서 항상 호출하는 습관을 들이자.
- `insert()`는 실패 시 예외를 던지는 관례가 있지만, `update()`/`delete()`는 0을 반환하는 것이 관례다. 클라이언트 코드에서 이 차이를 헷갈리지 않도록 주의한다.

<br>

## 7. ContentResolver로 데이터 접근하기

`ContentResolver`는 클라이언트(다른 컴포넌트, 다른 앱)가 ContentProvider에 접근하는 유일한 창구다. `context.contentResolver`로 얻을 수 있으며, 내부적으로 Binder IPC를 통해 실제 Provider가 존재하는 프로세스로 요청을 전달한다.

```kotlin
class NoteRepository(private val context: Context) {

    fun getAllNotes(): List<Note> {
        val notes = mutableListOf<Note>()
        val cursor = context.contentResolver.query(
            NoteProvider.CONTENT_URI, null, null, null, "created_at DESC"
        )
        cursor?.use {
            val idIdx = it.getColumnIndexOrThrow("_id")
            val titleIdx = it.getColumnIndexOrThrow("title")
            while (it.moveToNext()) {
                notes.add(Note(id = it.getLong(idIdx), title = it.getString(titleIdx)))
            }
        }
        return notes
    }

    fun addNote(title: String, content: String): Uri? {
        val values = ContentValues().apply {
            put("title", title)
            put("content", content)
        }
        return context.contentResolver.insert(NoteProvider.CONTENT_URI, values)
    }

    fun deleteNote(id: Long): Int {
        val uri = ContentUris.withAppendedId(NoteProvider.CONTENT_URI, id)
        return context.contentResolver.delete(uri, null, null)
    }
}
```

Compose/코루틴 환경에서는 블로킹 호출을 IO 디스패처로 감싸는 것이 필수다.

```kotlin
suspend fun getAllNotesAsync(): List<Note> = withContext(Dispatchers.IO) {
    repository.getAllNotes()
}
```

| ContentResolver 메서드 | 대응하는 Provider 메서드 |
|---|---|
| query() | query() |
| insert() | insert() |
| update() | update() |
| delete() | delete() |
| getType() | getType() |
| openInputStream()/openOutputStream() | openFile() |

**실전 팁**
- `ContentResolver`의 모든 메서드는 동기(blocking) 호출이다. 메인 스레드에서 직접 호출하면 ANR 위험이 있으므로 반드시 `Dispatchers.IO` 등 백그라운드에서 실행하자.
- 같은 앱 내부 데이터라면 ContentResolver를 거치지 않고 Room DAO를 직접 주입받는 게 오버헤드가 적다. ContentResolver는 "경계를 넘어야 할 때"만 쓰는 도구다.

<br>

## 8. AndroidManifest 등록과 권한(Permission) 설정

구현한 ContentProvider는 `AndroidManifest.xml`에 `<provider>` 태그로 등록해야 시스템이 인식한다.

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <provider
            android:name=".provider.NoteProvider"
            android:authorities="com.example.app.provider"
            android:exported="false"
            android:readPermission="com.example.app.permission.READ_NOTES"
            android:writePermission="com.example.app.permission.WRITE_NOTES"
            android:grantUriPermissions="true" />
    </application>

    <permission
        android:name="com.example.app.permission.READ_NOTES"
        android:protectionLevel="signature" />
    <permission
        android:name="com.example.app.permission.WRITE_NOTES"
        android:protectionLevel="signature" />
</manifest>
```

`android:exported` 속성은 Android 12(API 31)부터 명시적으로 선언하지 않으면 빌드 오류가 난다. 다른 앱과 공유할 필요가 없다면 반드시 `false`로 설정해야 한다.

| protectionLevel | 의미 |
|---|---|
| normal | 낮은 위험, 시스템이 자동 승인 |
| dangerous | 사용자에게 런타임 권한 요청 필요 |
| signature | 같은 서명(같은 회사 앱)일 때만 자동 허용 |
| signatureOrSystem | signature 조건 + 시스템 앱도 허용 (레거시) |

**실전 팁**
- `exported="true"`이면서 권한 설정이 없으면 어떤 앱이든 데이터에 접근할 수 있으므로 심각한 보안 취약점이 된다. 공유가 필요 없다면 무조건 `exported="false"`.
- 특정 상대 앱에만 임시로 권한을 주고 싶다면 `grantUriPermissions="true"`와 `Intent.FLAG_GRANT_READ_URI_PERMISSION`을 조합해서 사용한다.

<br>

## 9. FileProvider로 파일 공유하기

`FileProvider`는 안드로이드 지원 라이브러리가 제공하는 ContentProvider 구현체로, 파일 시스템 경로를 노출하지 않고도 다른 앱에 파일을 안전하게 공유할 수 있게 해준다. 카메라 촬영, PDF 공유, 다른 앱에 파일 첨부 등에 널리 쓰인다.

```xml
<!-- AndroidManifest.xml -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

```xml
<!-- res/xml/file_paths.xml -->
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="shared_images" path="images/" />
    <external-files-path name="my_docs" path="documents/" />
</paths>
```

```kotlin
fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "PDF 공유하기"))
}
```

| path 태그 | 대응 디렉토리 |
|---|---|
| `files-path` | `Context.getFilesDir()` |
| `cache-path` | `Context.getCacheDir()` |
| `external-path` | `Environment.getExternalStorageDirectory()` |
| `external-files-path` | `Context.getExternalFilesDir(null)` |
| `external-cache-path` | `Context.getExternalCacheDir()` |

**실전 팁**
- `FLAG_GRANT_READ_URI_PERMISSION`을 빼먹으면 상대 앱이 `SecurityException`을 만난다. 파일을 주고받는 Intent에는 항상 플래그를 명시하자.
- `authorities`는 앱마다 고유해야 하므로 `${applicationId}.fileprovider`처럼 패키지명을 활용하는 것이 충돌을 피하는 관례다.

<br>

## 10. ContentProvider와 Room 연동

Room은 자체적으로 ContentProvider를 제공하지 않지만, Room의 `SupportSQLiteOpenHelper`를 ContentProvider 내부에서 활용해 두 가지 장점(타입 세이프한 로컬 접근 + 외부 공유 가능한 인터페이스)을 모두 취할 수 있다.

```kotlin
@Database(entities = [NoteEntity::class], version = 1)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao()
}

class RoomBackedProvider : ContentProvider() {

    private lateinit var db: NoteDatabase

    override fun onCreate(): Boolean {
        db = Room.databaseBuilder(context!!, NoteDatabase::class.java, "notes.db").build()
        return true
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor {
        // Room의 SupportSQLiteDatabase는 openHelper를 통해 raw Cursor를 얻을 수 있다
        val supportDb = db.openHelper.readableDatabase
        return supportDb.query(
            SimpleSQLiteQuery("SELECT * FROM notes ORDER BY created_at DESC")
        )
    }

    // insert/update/delete는 db.noteDao()를 직접 호출하는 방식으로 구현 가능
    // ...
}
```

| 접근 방식 | 장점 | 단점 |
|---|---|---|
| Room DAO 직접 사용 | 타입 세이프, Flow/코루틴 지원, 컴파일 타임 쿼리 검증 | 앱 내부에서만 사용 가능 |
| Room + ContentProvider 래핑 | 외부 앱 공유 가능 | Cursor 기반이라 타입 세이프티 상실, 보일러플레이트 증가 |

**실전 팁**
- 외부 공유가 필요 없는 대다수의 화면에서는 Room DAO(Flow 반환)를 ViewModel에서 직접 구독하는 것이 정석이다. ContentProvider 래핑은 "정말 다른 앱과 공유해야 하는 최소한의 테이블"에만 적용하자.
- Room의 `openHelper.readableDatabase`는 Room의 캐싱/무효화(invalidation) 트래킹을 우회하므로, `Flow` 기반 관찰이 필요한 화면에는 이 방식 대신 DAO를 그대로 쓰는 게 낫다.

<br>

## 11. ContentProviderOperation과 Batch 처리

여러 건의 insert/update/delete를 하나의 트랜잭션으로 묶어서 처리하고 싶을 때 `ContentProviderOperation`과 `applyBatch()`를 사용한다. 매번 IPC를 왕복하는 대신 한 번의 호출로 묶을 수 있어 성능상 이점이 크다.

```kotlin
fun insertNotesInBatch(context: Context, titles: List<String>) {
    val operations = ArrayList<ContentProviderOperation>()

    titles.forEach { title ->
        val values = ContentValues().apply { put("title", title) }
        operations.add(
            ContentProviderOperation.newInsert(NoteProvider.CONTENT_URI)
                .withValues(values)
                .build()
        )
    }

    try {
        val results: Array<ContentProviderResult> = context.contentResolver.applyBatch(
            "com.example.app.provider", operations
        )
        Log.d("Batch", "삽입된 행 개수: ${results.size}")
    } catch (e: OperationApplicationException) {
        Log.e("Batch", "배치 처리 실패", e)
    }
}
```

Provider 쪽에서는 기본 구현이 각 operation을 순서대로 적용해주지만, 트랜잭션 보장을 위해 `applyBatch()`를 오버라이드해서 명시적으로 트랜잭션을 걸어주는 것이 안전하다.

```kotlin
override fun applyBatch(
    operations: ArrayList<ContentProviderOperation>
): Array<ContentProviderResult> {
    val db = dbHelper.writableDatabase
    db.beginTransaction()
    try {
        val results = super.applyBatch(operations)
        db.setTransactionSuccessful()
        return results
    } finally {
        db.endTransaction()
    }
}
```

| 방식 | IPC 호출 횟수 | 트랜잭션 보장 |
|---|---|---|
| insert()를 N번 반복 호출 | N번 | 기본적으로 없음 (각각 독립) |
| applyBatch()로 묶기 | 1번 | Provider가 트랜잭션 처리하면 보장됨 |

**실전 팁**
- `applyBatch()`를 오버라이드하지 않으면 기본 구현은 트랜잭션 없이 순차 실행되므로, 중간에 실패해도 이전 작업들이 롤백되지 않는다. 데이터 정합성이 중요하면 반드시 트랜잭션을 직접 감싸자.
- `withValueBackReference()`를 사용하면 이전 operation의 결과(예: 방금 insert된 id)를 다음 operation에서 참조할 수 있어, 부모-자식 관계 데이터를 한 배치로 넣을 때 유용하다.

<br>

## 12. ContentObserver로 데이터 변경 감지하기

`ContentObserver`는 특정 Uri의 데이터가 변경되었을 때(Provider가 `notifyChange()`를 호출했을 때) 콜백을 받을 수 있게 해주는 옵저버 패턴 구현체다.

```kotlin
class NoteChangeObserver(
    handler: Handler,
    private val onChanged: () -> Unit
) : ContentObserver(handler) {
    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        onChanged()
    }
}

class NoteViewModel(app: Application) : AndroidViewModel(app) {

    private val observer = NoteChangeObserver(Handler(Looper.getMainLooper())) {
        loadNotes()
    }

    init {
        app.contentResolver.registerContentObserver(
            NoteProvider.CONTENT_URI, true, observer
        )
        loadNotes()
    }

    private fun loadNotes() {
        // ContentResolver.query()로 최신 데이터 다시 조회
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }
}
```

Compose에서는 `callbackFlow`로 감싸서 상태 스트림처럼 다루는 패턴이 흔하다.

```kotlin
fun observeNotes(context: Context, uri: Uri): Flow<Unit> = callbackFlow {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            trySend(Unit)
        }
    }
    context.contentResolver.registerContentObserver(uri, true, observer)
    trySend(Unit) // 최초 1회 즉시 발행
    awaitClose { context.contentResolver.unregisterContentObserver(observer) }
}
```

| registerContentObserver 파라미터 | 설명 |
|---|---|
| uri | 관찰할 대상 Uri |
| notifyForDescendants | true면 하위 경로(`notes/1` 등)의 변경도 감지 |
| observer | 콜백을 받을 ContentObserver 인스턴스 |

**실전 팁**
- `unregisterContentObserver()`를 호출하지 않으면 메모리 누수와 불필요한 콜백 호출이 계속 발생한다. `ViewModel.onCleared()`나 `DisposableEffect`에서 반드시 해제하자.
- `notifyForDescendants`를 `true`로 설정하지 않으면 `notes/1` 같은 하위 Uri 변경 알림을 상위 `notes` Uri 옵저버가 받지 못할 수 있다.

<br>

## 13. 주의사항과 자주 하는 실수

1. `android:exported` 속성을 명시하지 않아 Android 12 이상에서 빌드가 실패하는 경우가 많다. 공유 목적이 없다면 반드시 `false`로 지정하자.
2. `onCreate()`에서 데이터베이스 연결이나 파일 I/O 같은 무거운 작업을 수행해서 앱 시작 속도를 느리게 만드는 실수가 흔하다. 지연 초기화로 미루자.
3. `notifyChange()` 호출을 빼먹어서 `ContentObserver`가 변경 사항을 감지하지 못하는 버그가 자주 발생한다. insert/update/delete 끝에 항상 넣는 습관을 들이자.
4. `Cursor`를 닫지 않아 리소스 누수가 발생한다. 항상 `use {}` 블록을 사용하자.
5. selection 절에 사용자 입력값을 문자열 연결로 바로 붙여서 SQL Injection 취약점을 만드는 경우가 있다. 항상 `?` 플레이스홀더와 selectionArgs를 사용해야 한다.
6. `ContentResolver` 호출을 메인 스레드에서 그대로 실행해 ANR을 유발하는 실수가 많다. 반드시 `Dispatchers.IO` 등 백그라운드에서 호출하자.
7. 앱 내부에서만 쓰는 데이터인데도 굳이 ContentProvider로 감싸서 불필요한 IPC 오버헤드와 보일러플레이트를 늘리는 경우가 있다. 공유 요구사항이 없다면 Room DAO를 직접 쓰자.
8. `FileProvider` 설정 시 `file_paths.xml`의 경로와 실제 파일이 저장되는 경로가 일치하지 않아 `IllegalArgumentException`이 발생하는 경우가 많다.
9. 다른 앱에 Uri 권한을 부여할 때 `FLAG_GRANT_READ_URI_PERMISSION`/`FLAG_GRANT_WRITE_URI_PERMISSION`을 빠뜨려 상대 앱에서 `SecurityException`이 발생한다.
10. `applyBatch()`를 트랜잭션으로 감싸지 않아, 배치 작업 중간에 실패했을 때 데이터 일부만 반영되는 정합성 문제가 생긴다.

<br>

## 14. 정리

ContentProvider는 안드로이드 프로세스 샌드박스 경계를 안전하게 넘어 데이터를 공유하기 위한 표준 컴포넌트다. 핵심은 `Uri`로 데이터를 식별하고, `ContentResolver`라는 창구를 통해서만 클라이언트가 접근하며, `query/insert/update/delete/getType/onCreate` 여섯 개의 메서드로 CRUD를 노출한다는 점이다. 다른 앱과의 실제 데이터 공유(연락처, 미디어, 캘린더 같은 시스템 데이터, 혹은 자사 앱들 간의 데이터 공유)나 안드로이드 7.0 이상의 `content://` Uri 정책을 지켜야 하는 파일 공유(FileProvider) 상황이 아니라면, 앱 내부 데이터 접근에는 Room DAO를 직접 쓰는 편이 훨씬 간단하고 성능도 좋다. 실무에서는 권한(exported, readPermission/writePermission) 설정을 빠뜨려 보안 취약점을 만들거나, `notifyChange()`/`Cursor` 해제/스레드 처리를 놓쳐 버그를 만드는 경우가 많으므로, 이 문서의 "주의사항과 자주 하는 실수" 항목을 구현 체크리스트처럼 활용하는 것이 좋다.
