# Room Database

<br>

## 목차

1. Room Database란 무엇인가
2. Room의 3대 구성 요소
3. Entity 정의하기
4. DAO 작성하기
5. Database 클래스 구성과 싱글톤 패턴
6. 기본 CRUD 연산
7. Flow로 데이터 변화 관찰하기
8. Coroutines와 suspend 함수
9. 관계 매핑 (Relation)
10. Type Converter
11. 데이터베이스 마이그레이션
12. Room과 Hilt 연동
13. 쿼리 성능 최적화
14. 주의사항과 자주 하는 실수
15. 정리

<br>

## Room Database란 무엇인가

Room은 안드로이드에서 제공하는 SQLite 위의 추상화 라이브러리다. SQLite를 직접 다루면 SQL 문법 오류를 컴파일 타임에 잡을 수 없고, `Cursor`를 객체로 변환하는 보일러플레이트 코드를 매번 작성해야 한다. Room은 이런 문제를 해결하기 위해 어노테이션 기반으로 테이블과 쿼리를 정의하면, 컴파일 시점에 SQL을 검증하고 결과를 자동으로 객체(Kotlin data class)로 매핑해준다.

Room은 Jetpack의 Architecture Components 중 하나로, ViewModel, LiveData/Flow, Hilt 등과 자연스럽게 연동되도록 설계되었다. 특히 MVI/MVVM 아키텍처에서 로컬 데이터 소스 계층으로 자주 사용된다.

```kotlin
// build.gradle.kts (Module: app)
dependencies {
    val room_version = "2.6.1"

    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")
}
```

| 항목 | SQLite 직접 사용 | Room |
|---|---|---|
| SQL 문법 검증 | 런타임에만 확인 가능 | 컴파일 타임에 검증 |
| 객체 매핑 | Cursor를 수동 변환 | 어노테이션 기반 자동 매핑 |
| Coroutines/Flow 지원 | 직접 구현 필요 | 기본 지원 |
| 마이그레이션 관리 | 수동 버전 관리 | Migration API 제공 |

**실전 팁**: `ksp`는 `kapt`보다 컴파일 속도가 빠르므로, 신규 프로젝트라면 `room-compiler`를 반드시 `ksp`로 연결하는 것을 추천한다. 기존에 `kapt`로 되어 있다면 마이그레이션을 고려해볼 만하다.

<br>

## Room의 3대 구성 요소

Room은 세 가지 핵심 요소로 구성된다.

- **Entity**: 테이블 하나에 대응하는 데이터 클래스
- **DAO(Data Access Object)**: 테이블에 접근하는 쿼리 메서드의 모음
- **Database**: Entity와 DAO를 하나로 묶는 진입점 클래스

이 세 가지가 어떻게 연결되는지 미리 감을 잡아두면 이후 섹션을 이해하기 훨씬 수월하다.

```kotlin
// 1. Entity - 테이블 정의
@Entity(tableName = "user")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val age: Int
)

// 2. DAO - 쿼리 정의
@Dao
interface UserDao {
    @Insert
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM user")
    suspend fun getAllUsers(): List<UserEntity>
}

// 3. Database - 전체를 묶는 진입점
@Database(entities = [UserEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}
```

| 구성 요소 | 역할 | 주요 어노테이션 |
|---|---|---|
| Entity | 테이블 스키마 정의 | `@Entity`, `@PrimaryKey`, `@ColumnInfo` |
| DAO | 쿼리 메서드 정의 | `@Dao`, `@Query`, `@Insert`, `@Update`, `@Delete` |
| Database | Entity/DAO 결합, 인스턴스 생성 | `@Database`, `RoomDatabase` |

**실전 팁**: 셋을 처음 볼 때는 "Entity=테이블, DAO=쿼리 모음집, Database=이 둘을 연결하는 접착제"라고 단순하게 외워두면 헷갈리지 않는다.

<br>

## Entity 정의하기

Entity는 데이터베이스 테이블 하나를 코틀린 클래스로 표현한 것이다. `@Entity` 어노테이션을 붙인 data class가 곧 테이블이 되고, 각 프로퍼티는 컬럼이 된다. 기본적으로 프로퍼티 이름이 컬럼 이름이 되지만 `@ColumnInfo`로 커스터마이징할 수 있다.

기본 키는 `@PrimaryKey`로 지정하며, `autoGenerate = true`를 주면 SQLite가 자동으로 증가하는 정수 값을 부여한다.

```kotlin
@Entity(
    tableName = "note",
    indices = [Index(value = ["title"])]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "note_id")
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false
)
```

| 어노테이션 | 설명 |
|---|---|
| `@Entity` | 클래스를 테이블로 지정. `tableName`, `indices`로 옵션 지정 |
| `@PrimaryKey` | 기본 키 지정, `autoGenerate`로 자동 증가 여부 결정 |
| `@ColumnInfo` | 컬럼명, 기본값, NOT NULL 여부 등 세부 설정 |
| `@Ignore` | 특정 프로퍼티를 테이블 컬럼에서 제외 |

**실전 팁**: `id: Long = 0`처럼 기본값을 0으로 주면, 새 객체를 만들 때 id를 매번 넘기지 않아도 되고 Room이 insert 시 자동으로 값을 채워준다. `var`가 아니라 `val`을 쓰는 습관을 들이면 불변 데이터로 관리하기 쉬워진다.

<br>

## DAO 작성하기

DAO는 실제 SQL 쿼리를 정의하는 인터페이스다. Room이 컴파일 타임에 이 인터페이스의 구현체를 자동 생성해준다. 개발자는 SQL을 직접 작성하지만, Room이 문법 오류와 컬럼/테이블 존재 여부를 컴파일 시점에 검증해준다.

```kotlin
@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("SELECT * FROM note ORDER BY created_at DESC")
    suspend fun getAllNotes(): List<NoteEntity>

    @Query("SELECT * FROM note WHERE note_id = :noteId")
    suspend fun getNoteById(noteId: Long): NoteEntity?

    @Query("SELECT * FROM note WHERE title LIKE '%' || :keyword || '%'")
    suspend fun searchByTitle(keyword: String): List<NoteEntity>

    @Query("DELETE FROM note WHERE note_id = :noteId")
    suspend fun deleteById(noteId: Long)
}
```

| 어노테이션 | 용도 | 반환 가능 타입 |
|---|---|---|
| `@Insert` | 새 행 삽입 | `Unit`, `Long`, `List<Long>` |
| `@Update` | 기존 행 수정 (기본키 기준) | `Unit`, `Int`(수정된 행 수) |
| `@Delete` | 행 삭제 (기본키 기준) | `Unit`, `Int` |
| `@Query` | 임의의 SQL 실행 | 자유롭게 지정 |

**실전 팁**: `@Insert`에 `onConflict = OnConflictStrategy.REPLACE`를 지정해두면, 동일한 기본 키로 다시 insert할 때 update처럼 동작해서 "upsert" 패턴을 쉽게 구현할 수 있다.

<br>

## Database 클래스 구성과 싱글톤 패턴

`RoomDatabase`를 상속한 추상 클래스가 앱 전체에서 사용할 데이터베이스의 진입점이다. 이 클래스는 `@Database` 어노테이션에 Entity 목록과 버전을 명시해야 하며, DAO를 반환하는 추상 메서드를 선언한다.

데이터베이스 인스턴스는 앱 전체에서 단 하나만 존재해야 한다. 여러 인스턴스를 만들면 동시성 문제와 불필요한 리소스 낭비가 발생하므로 싱글톤 패턴으로 관리하는 것이 표준이다.

```kotlin
@Database(
    entities = [NoteEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
```

| 방식 | 설명 | 비고 |
|---|---|---|
| 수동 싱글톤 (`@Volatile` + `synchronized`) | 직접 더블 체크 락킹 구현 | Hilt 없는 소규모 프로젝트에 적합 |
| Hilt `@Singleton` | DI 컨테이너가 싱글톤 보장 | 실무에서 가장 흔한 방식 |

**실전 팁**: `exportSchema = true`로 설정하면 스키마 JSON 파일이 프로젝트에 생성된다. 마이그레이션 작성 시 이전 버전 스키마를 참조해야 하므로, 실무에서는 이 옵션을 켜고 `schemaLocation`을 build.gradle에 지정해두는 것이 좋다.

<br>

## 기본 CRUD 연산

CRUD는 Create, Read, Update, Delete의 약자로, DAO에서 정의한 메서드를 Repository 계층에서 호출하는 흐름을 실제 코드로 살펴본다.

```kotlin
class NoteRepository(private val noteDao: NoteDao) {

    suspend fun addNote(title: String, content: String) {
        val note = NoteEntity(title = title, content = content)
        noteDao.insertNote(note)
    }

    suspend fun getNotes(): List<NoteEntity> = noteDao.getAllNotes()

    suspend fun renameNote(note: NoteEntity, newTitle: String) {
        noteDao.updateNote(note.copy(title = newTitle))
    }

    suspend fun removeNote(note: NoteEntity) {
        noteDao.deleteNote(note)
    }
}

// 사용 예시 (ViewModel 내부, viewModelScope 안에서 호출)
class NoteViewModel(private val repository: NoteRepository) : ViewModel() {
    fun onAddClicked(title: String, content: String) {
        viewModelScope.launch {
            repository.addNote(title, content)
        }
    }
}
```

| 연산 | DAO 어노테이션 | 특징 |
|---|---|---|
| Create | `@Insert` | 기본키 충돌 전략 지정 가능 |
| Read | `@Query(SELECT)` | 자유로운 조건절 작성 |
| Update | `@Update` 또는 `@Query(UPDATE)` | 기본키 기준으로 전체 컬럼 갱신 |
| Delete | `@Delete` 또는 `@Query(DELETE)` | 객체 기준 또는 조건 기준 삭제 |

**실전 팁**: `@Update`는 전달한 객체의 기본 키를 기준으로 모든 컬럼을 덮어쓴다. 특정 컬럼만 바꾸고 싶다면 `@Query("UPDATE note SET title = :title WHERE note_id = :id")`처럼 명시적 쿼리를 쓰는 게 의도가 명확하다.

<br>

## Flow로 데이터 변화 관찰하기

Room DAO의 반환 타입을 `Flow<T>`로 지정하면, 테이블 데이터가 변경될 때마다 자동으로 새로운 값을 방출한다. `suspend fun`으로 한 번만 조회하는 방식과 달리, Flow는 데이터베이스 변화를 구독하는 스트림이 된다. Compose UI에서 실시간으로 목록을 갱신하고 싶을 때 필수적으로 쓰인다.

```kotlin
@Dao
interface NoteDao {
    @Query("SELECT * FROM note ORDER BY created_at DESC")
    fun observeAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM note WHERE note_id = :noteId")
    fun observeNoteById(noteId: Long): Flow<NoteEntity?>
}

// ViewModel에서 활용
class NoteViewModel(private val noteDao: NoteDao) : ViewModel() {
    val notes: StateFlow<List<NoteEntity>> = noteDao.observeAllNotes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}

// Compose에서 수집
@Composable
fun NoteListScreen(viewModel: NoteViewModel) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    LazyColumn {
        items(notes, key = { it.id }) { note ->
            Text(text = note.title)
        }
    }
}
```

| 반환 타입 | 특징 | 언제 쓰나 |
|---|---|---|
| `suspend fun ... : T` | 1회성 조회 | 단발성 요청 (버튼 클릭 시 조회 등) |
| `Flow<T>` | 테이블 변화 시 자동 재방출 | 화면에 실시간 반영이 필요할 때 |
| `LiveData<T>` | Flow의 구세대 대안 | 기존 LiveData 기반 프로젝트 유지보수 시 |

**실전 팁**: Room의 `Flow`는 내부적으로 `InvalidationTracker`를 이용해 테이블 변경을 감지한다. `DISTINCT`가 없는 동일 쿼리라도 값이 실제로 바뀌지 않았는데 재방출되는 경우가 있으니, UI에서 불필요한 리컴포지션이 걱정된다면 `.distinctUntilChanged()`를 붙이는 것도 방법이다.

<br>

## Coroutines와 suspend 함수

Room은 기본적으로 메인 스레드에서의 쿼리 실행을 금지한다(디버그 빌드에서는 예외를 던진다). DAO 메서드를 `suspend fun`으로 선언하면 Room이 내부적으로 백그라운드 디스패처(IO)에서 쿼리를 실행하도록 처리해준다.

```kotlin
@Dao
interface NoteDao {
    // suspend 함수 - Room이 자동으로 IO 스레드에서 실행
    @Insert
    suspend fun insertNote(note: NoteEntity)

    // 트랜잭션 - 여러 DAO 호출을 하나의 원자적 단위로 묶음
    @Transaction
    suspend fun replaceAllNotes(notes: List<NoteEntity>) {
        deleteAllNotes()
        insertNotes(notes)
    }

    @Query("DELETE FROM note")
    suspend fun deleteAllNotes()

    @Insert
    suspend fun insertNotes(notes: List<NoteEntity>)
}

// 호출부: 반드시 코루틴 스코프 안에서 호출
class NoteRepository(private val noteDao: NoteDao) {
    suspend fun replaceNotes(notes: List<NoteEntity>) {
        noteDao.replaceAllNotes(notes)
    }
}
```

| 방식 | 스레드 처리 | 비고 |
|---|---|---|
| `suspend fun` | Room이 자동으로 백그라운드 실행 | 권장 방식 (Room 2.1+) |
| `RxJava` (`Single`, `Flowable`) | Room이 스케줄러 제공 | RxJava 기반 레거시 프로젝트 |
| 콜백 방식 | 직접 스레드 관리 필요 | 구버전 Room, 비권장 |

**실전 팁**: `@Transaction`과 `suspend`를 함께 쓰면 여러 DAO 호출을 원자적으로 묶을 수 있다. 예를 들어 "전체 삭제 후 재삽입" 같은 작업이 중간에 실패해도 롤백되어 데이터 정합성이 깨지지 않는다.

<br>

## 관계 매핑 (Relation)

Room은 외래 키 기반의 관계형 데이터를 다루기 위해 `@Relation` 어노테이션을 제공한다. 1:N 관계에서 부모 엔티티와 자식 엔티티 목록을 하나의 객체로 묶어서 조회할 수 있다.

```kotlin
@Entity(tableName = "user")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "post",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    val content: String
)

// 부모 + 자식 리스트를 함께 담는 POJO
data class UserWithPosts(
    @Embedded val user: UserEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "user_id"
    )
    val posts: List<PostEntity>
)

@Dao
interface UserDao {
    @Transaction
    @Query("SELECT * FROM user WHERE id = :userId")
    suspend fun getUserWithPosts(userId: Long): UserWithPosts
}
```

| 관계 유형 | 구현 방법 |
|---|---|
| 1:N | `@Relation` + `parentColumn`/`entityColumn` |
| N:M | 중간 테이블(Junction Entity) + `@Relation(associateBy = Junction(...))` |
| 임베딩(포함 관계) | `@Embedded` — 하나의 객체를 다른 엔티티의 컬럼처럼 포함 |

**실전 팁**: `@Relation`을 사용하는 쿼리에는 반드시 `@Transaction`을 붙여야 한다. Room이 내부적으로 부모 쿼리와 자식 쿼리를 두 번 나눠서 실행하기 때문에, 트랜잭션 없이는 조회 도중 데이터 일관성이 깨질 수 있다.

<br>

## Type Converter

SQLite는 기본적으로 정수, 실수, 문자열, BLOB 타입만 지원한다. `Date`, `LocalDateTime`, 커스텀 enum, 리스트 같은 타입을 컬럼에 저장하려면 `TypeConverter`로 변환 로직을 정의해야 한다.

```kotlin
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.joinToString(separator = ",")
    }

    @TypeConverter
    fun toStringList(data: String?): List<String>? {
        return data?.split(",")?.map { it.trim() }
    }
}

@Database(entities = [NoteEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}
```

| 원본 타입 | 저장 방식 | 변환 방법 |
|---|---|---|
| `Date` | `Long` (timestamp) | `TypeConverter`로 상호 변환 |
| `enum class` | `String` (name) | `enum.name` ↔ `enumValueOf<T>()` |
| `List<String>` | `String` (구분자로 join) | 또는 Kotlinx Serialization으로 JSON 변환 |

**실전 팁**: 복잡한 객체를 저장해야 한다면 수동으로 join/split 하기보다 `kotlinx.serialization`으로 JSON 문자열 변환하는 편이 안전하다. 다만 JSON 컬럼은 SQL로 직접 조건 검색이 어려우므로, 검색이 필요한 데이터라면 애초에 별도 컬럼이나 테이블로 분리하는 게 낫다.

<br>

## 데이터베이스 마이그레이션

앱을 업데이트하면서 테이블 구조를 바꿔야 할 때, 기존 사용자의 로컬 데이터를 보존하면서 스키마를 변경하는 과정이 마이그레이션이다. `version`을 올리고 `Migration` 객체로 이전 버전과 새 버전 사이의 변경 SQL을 명시해야 한다.

```kotlin
// 버전 1 → 2: note 테이블에 isPinned 컬럼 추가
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE note ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0"
        )
    }
}

// 버전 2 → 3: 새로운 tag 테이블 추가
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tag (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}

val database = Room.databaseBuilder(context, AppDatabase::class.java, "app_database")
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
    .build()
```

| 방식 | 데이터 보존 | 적합한 상황 |
|---|---|---|
| `Migration` 객체 작성 | 보존됨 | 운영 중인 앱, 사용자 데이터가 중요한 경우 |
| `fallbackToDestructiveMigration()` | 삭제됨 (테이블 재생성) | 개발 초기 단계, 캐시성 데이터 |

**실전 팁**: 개발 초반에는 `fallbackToDestructiveMigration()`으로 빠르게 반복하다가, 앱을 배포한 이후부터는 반드시 정식 `Migration`을 작성해야 한다. 배포된 버전에서 마이그레이션 없이 스키마를 바꾸면 기존 사용자의 앱이 크래시나 데이터 손실을 겪는다.

<br>

## Room과 Hilt 연동

실무에서는 Room 인스턴스와 DAO를 Hilt를 통해 의존성 주입으로 관리하는 것이 표준적이다. 수동 싱글톤 코드를 직접 작성할 필요 없이, `@Module`에서 `@Provides`로 등록하면 Hilt가 앱 생명주기 동안 하나의 인스턴스만 유지해준다.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "app_database"
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    @Singleton
    fun provideNoteDao(database: AppDatabase): NoteDao {
        return database.noteDao()
    }
}

// Repository에 주입
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao
) {
    fun observeNotes(): Flow<List<NoteEntity>> = noteDao.observeAllNotes()
}

// ViewModel에 주입
@HiltViewModel
class NoteViewModel @Inject constructor(
    private val repository: NoteRepository
) : ViewModel()
```

| 방식 | 장점 | 단점 |
|---|---|---|
| 수동 싱글톤 (`@Volatile`) | 외부 라이브러리 의존 없음 | 테스트 시 Mock 주입이 번거로움 |
| Hilt DI | 테스트 용이, 생명주기 자동 관리 | 초기 설정(Module 등) 필요 |

**실전 팁**: DAO는 `@Singleton`으로 지정하지 않아도 무방하다. 실제 싱글톤이 필요한 건 `AppDatabase`뿐이며, DAO는 그 인스턴스의 메서드 호출로 간단히 얻어지는 가벼운 객체이기 때문이다. 다만 관례적으로 함께 `@Singleton` 스코프에 두는 경우가 많다.

<br>

## 쿼리 성능 최적화

데이터가 많아질수록 쿼리 성능이 앱 체감 속도에 직접적인 영향을 준다. Room에서 성능을 개선하는 대표적인 방법들을 정리한다.

```kotlin
// 1. 인덱스 추가 - 검색/정렬에 자주 쓰이는 컬럼에 지정
@Entity(
    tableName = "note",
    indices = [Index(value = ["created_at"]), Index(value = ["title"])]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

// 2. Paging3와 연동해 대량 데이터를 페이지 단위로 로드
@Dao
interface NoteDao {
    @Query("SELECT * FROM note ORDER BY created_at DESC")
    fun pagingSource(): PagingSource<Int, NoteEntity>
}

// 3. 필요한 컬럼만 조회 (SELECT * 지양)
data class NoteTitleOnly(
    @ColumnInfo(name = "note_id") val id: Long,
    val title: String
)

@Dao
interface NoteDaoOptimized {
    @Query("SELECT note_id, title FROM note")
    suspend fun getTitlesOnly(): List<NoteTitleOnly>
}
```

| 최적화 기법 | 효과 | 적용 시점 |
|---|---|---|
| `Index` 추가 | WHERE/ORDER BY 성능 향상 | 검색·정렬 자주 쓰는 컬럼 |
| Paging3 연동 | 메모리 사용량 감소, 스크롤 성능 개선 | 수백 건 이상 목록 |
| 필요한 컬럼만 SELECT | I/O 및 매핑 비용 감소 | 목록 화면처럼 일부 필드만 필요할 때 |
| `@Transaction`으로 배치 처리 | insert/update 반복 호출 대비 속도 향상 | 대량 데이터 삽입/수정 |

**실전 팁**: 인덱스는 조회 속도를 높여주지만 insert/update 성능은 소폭 낮추고 저장 공간도 더 쓴다. 모든 컬럼에 인덱스를 걸기보다, 실제로 `WHERE`나 `ORDER BY`에 자주 등장하는 컬럼에만 선택적으로 추가하는 것이 좋다.

<br>

## 주의사항과 자주 하는 실수

1. 메인 스레드에서 직접 쿼리를 실행하려다 `IllegalStateException`을 만나는 경우가 많다. DAO 메서드는 반드시 `suspend fun`이나 `Flow` 반환 타입으로 선언해야 한다.
2. `@Update`는 기본 키가 일치하는 행이 없으면 아무 일도 하지 않고 조용히 무시된다. 수정이 반영됐는지 반환값(`Int`)으로 확인하는 습관이 필요하다.
3. 스키마를 바꾸고 `version`을 올리지 않으면 앱이 크래시하거나 이전 스키마로 동작한다. Entity 변경 시 버전 번호를 함께 올리는 것을 잊지 말아야 한다.
4. 배포된 앱에서 `fallbackToDestructiveMigration()`을 그대로 쓰면 사용자의 기존 데이터가 통째로 삭제된다. 배포 이후에는 반드시 정식 `Migration`을 작성해야 한다.
5. `@Relation` 쿼리에 `@Transaction`을 빠뜨리면 부모/자식 쿼리 사이에 데이터 불일치가 생길 수 있다.
6. Room 인스턴스를 화면마다, 혹은 클래스마다 새로 생성하면 리소스 낭비와 락 경합이 발생한다. 반드시 앱 전체에서 하나의 인스턴스만 유지해야 한다.
7. `LIKE` 검색 시 사용자 입력에 `%`, `_` 같은 와일드카드 문자가 포함되면 의도치 않은 검색 결과가 나올 수 있다. 필요하다면 이스케이프 처리를 고려해야 한다.
8. `TypeConverter`를 등록해놓고 `@Database`에 `@TypeConverters`를 붙이는 것을 잊어서 컴파일 에러가 나는 경우가 흔하다.
9. `exportSchema = true`인데 `schemaLocation`을 설정하지 않으면 빌드 시 경고가 발생한다. 스키마 파일 경로를 build.gradle에 명시해야 한다.
10. 테스트에서 실제 파일 기반 DB 대신 `inMemoryDatabaseBuilder`를 쓰지 않으면 테스트마다 파일이 남아 다음 테스트에 영향을 줄 수 있다.

<br>

## 정리

Room은 Entity, DAO, Database라는 세 요소를 축으로, SQLite를 안전하고 생산적으로 다룰 수 있게 해주는 라이브러리다. Entity로 테이블 스키마를 정의하고, DAO로 쿼리를 선언하고, Database 클래스로 이 둘을 하나의 진입점에 묶은 뒤 싱글톤으로 관리하는 흐름이 기본 골격이다. 여기에 `suspend`와 `Flow`로 비동기·반응형 데이터 흐름을 붙이고, `@Relation`과 `TypeConverter`로 복잡한 데이터 구조를 다루며, `Migration`으로 스키마 변경 시에도 사용자 데이터를 안전하게 지킬 수 있다. 실무에서는 여기에 Hilt를 더해 인스턴스 관리를 자동화하고, 인덱스와 Paging3로 대량 데이터에서도 성능을 확보하는 것이 일반적인 패턴이다. 처음에는 Entity/DAO/Database 세 파일만으로 작은 앱을 직접 만들어보면서 감을 익히고, 이후 Flow 연동과 Migration, Hilt 통합 순으로 범위를 넓혀가는 것을 추천한다.
