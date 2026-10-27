# FastAPI

<br>

# 목차

1. FastAPI란?
2. 프로젝트 설정
3. 첫 번째 API
4. Path / Query 파라미터
5. Pydantic 모델과 검증
6. Request Body와 Response Model
7. 의존성 주입 (Depends)
8. async / await와 동시성
9. DB 연동 (SQLAlchemy 2.0 async)
10. 인증 (JWT)
11. 미들웨어와 CORS
12. 예외 처리
13. Lifespan과 BackgroundTasks
14. 파일 업로드
15. 스트리밍 응답 (SSE, LLM 연동)
16. 프로젝트 구조
17. 테스트
18. 배포
19. Android(Retrofit) 연동 관점
20. 정리

<br>

# 1. FastAPI란?

## 1.1 개요

- Python 기반의 **비동기 웹 프레임워크**
- **Starlette**(웹 레이어)와 **Pydantic**(데이터 검증) 위에 만들어졌다.
- Python 타입 힌트를 기반으로 검증, 직렬화, 문서화를 자동으로 처리한다.

## 1.2 특징

| 특징 | 설명 |
| --- | --- |
| 타입 힌트 기반 | 함수 파라미터 타입으로 검증/변환 자동 처리 |
| 자동 문서화 | `/docs` (Swagger UI), `/redoc` 자동 생성 (OpenAPI) |
| 비동기 지원 | `async def` 핸들러, ASGI 서버(uvicorn) |
| 의존성 주입 | `Depends`로 DB 세션, 인증 등을 선언적으로 주입 |
| 높은 성능 | Node.js, Go에 근접한 수준의 처리량 (Python 프레임워크 중 상위권) |

## 1.3 Flask, Django와 비교

| 구분 | FastAPI | Flask | Django |
| --- | --- | --- | --- |
| 성격 | API 중심 마이크로 프레임워크 | 마이크로 프레임워크 | 풀스택 프레임워크 |
| 비동기 | 네이티브 지원 | 제한적 | 부분 지원 (3.x~) |
| 검증 | Pydantic 내장 | 직접 구성 | Form / DRF Serializer |
| 문서화 | 자동 | 별도 라이브러리 | DRF + 별도 설정 |
| ORM | 선택 (SQLAlchemy 등) | 선택 | 내장 ORM |
| 적합한 용도 | REST API, AI 서빙, 마이크로서비스 | 소규모 서비스 | 관리자 포함 풀스택 |

## 1.4 AI 백엔드에서 FastAPI를 많이 쓰는 이유

- ML/LLM 생태계가 Python이라 모델 코드와 API 코드를 한 언어로 통합할 수 있다.
- 스트리밍 응답(SSE)과 비동기 I/O가 LLM API 호출 패턴과 잘 맞는다.
- Pydantic 스키마로 LLM의 구조화 출력을 검증하기 쉽다.

<br>

# 2. 프로젝트 설정

## 2.1 설치

```bash
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate

pip install "fastapi[standard]"
```

- `fastapi[standard]`는 uvicorn, 테스트용 httpx, 파일 업로드용 python-multipart 등을 함께 설치한다.
- 의존성은 `requirements.txt` 또는 `pyproject.toml`로 관리한다. 요즘은 **uv** 도 많이 쓴다.

```bash
pip freeze > requirements.txt
```

## 2.2 실행

```bash
# 개발 모드 (자동 리로드)
fastapi dev main.py

# 또는 uvicorn 직접 실행
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

- `main:app` -> `main.py` 파일의 `app` 객체
- `--reload`는 개발 환경에서만 사용한다.

## 2.3 문서 확인

- `http://localhost:8000/docs` : Swagger UI (API 직접 호출 가능)
- `http://localhost:8000/redoc` : ReDoc
- `http://localhost:8000/openapi.json` : OpenAPI 스펙 (클라이언트 코드 생성에 활용)

<br>

# 3. 첫 번째 API

```python
from fastapi import FastAPI

app = FastAPI(title="Study API", version="0.1.0")


@app.get("/")
async def root():
    return {"message": "Hello FastAPI"}


@app.get("/health")
async def health():
    return {"status": "ok"}
```

## 3.1 HTTP 메서드 데코레이터

| 데코레이터 | 용도 |
| --- | --- |
| `@app.get` | 조회 |
| `@app.post` | 생성 |
| `@app.put` | 전체 수정 |
| `@app.patch` | 부분 수정 |
| `@app.delete` | 삭제 |

## 3.2 상태 코드 지정

```python
from fastapi import status

@app.post("/items", status_code=status.HTTP_201_CREATED)
async def create_item():
    ...
```

<br>

# 4. Path / Query 파라미터

## 4.1 Path 파라미터

```python
@app.get("/users/{user_id}")
async def get_user(user_id: int):
    return {"user_id": user_id}
```

- 타입 힌트(`int`)로 자동 변환된다. 변환에 실패하면 **422 Unprocessable Entity**가 반환된다.

## 4.2 Query 파라미터

```python
@app.get("/items")
async def list_items(skip: int = 0, limit: int = 10, keyword: str | None = None):
    return {"skip": skip, "limit": limit, "keyword": keyword}
```

- 함수 파라미터 중 Path에 없는 것은 Query로 취급된다.
- 기본값이 있으면 선택, 없으면 필수 파라미터다.

## 4.3 Query / Path로 검증 추가

```python
from typing import Annotated
from fastapi import Path, Query

@app.get("/products/{product_id}")
async def get_product(
    product_id: Annotated[int, Path(gt=0, description="상품 ID")],
    q: Annotated[str | None, Query(min_length=2, max_length=50)] = None,
    tags: Annotated[list[str], Query()] = [],
):
    return {"product_id": product_id, "q": q, "tags": tags}
```

- `gt`, `ge`, `lt`, `le`: 숫자 범위
- `min_length`, `max_length`, `pattern`: 문자열 검증
- 같은 키를 여러 번 보내면 리스트로 받는다. (`?tags=a&tags=b`)

## 4.4 Enum으로 허용 값 제한

```python
from enum import Enum

class SortOrder(str, Enum):
    asc = "asc"
    desc = "desc"

@app.get("/posts")
async def list_posts(order: SortOrder = SortOrder.desc):
    return {"order": order}
```

<br>

# 5. Pydantic 모델과 검증

## 5.1 BaseModel

```python
from datetime import datetime
from pydantic import BaseModel, Field, EmailStr


class UserCreate(BaseModel):
    email: EmailStr
    name: str = Field(min_length=2, max_length=20)
    age: int = Field(ge=0, le=150)
    nickname: str | None = None
```

- `EmailStr`을 쓰려면 `pip install email-validator`가 필요하다.
- Pydantic v2 기준이다. (v1과 `validator` -> `field_validator`, `orm_mode` -> `from_attributes` 등 문법이 다르다.)

## 5.2 커스텀 검증

```python
from pydantic import BaseModel, field_validator, model_validator


class SignUpRequest(BaseModel):
    password: str
    password_confirm: str

    @field_validator("password")
    @classmethod
    def validate_password(cls, v: str) -> str:
        if len(v) < 8:
            raise ValueError("비밀번호는 8자 이상이어야 합니다")
        return v

    @model_validator(mode="after")
    def check_passwords_match(self):
        if self.password != self.password_confirm:
            raise ValueError("비밀번호가 일치하지 않습니다")
        return self
```

## 5.3 모델 변환

```python
user = UserCreate(email="a@b.com", name="현서", age=25)

user.model_dump()                    # dict
user.model_dump(exclude_unset=True)  # 설정된 필드만 (PATCH에 유용)
user.model_dump_json()               # JSON 문자열

UserCreate.model_validate({"email": "a@b.com", "name": "현서", "age": 25})
```

## 5.4 ORM 객체 -> Pydantic

```python
from pydantic import BaseModel, ConfigDict


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    email: str
    name: str
```

- `from_attributes=True`가 있어야 SQLAlchemy 객체를 `UserOut.model_validate(orm_obj)`로 변환할 수 있다.

<br>

# 6. Request Body와 Response Model

## 6.1 Request Body

```python
@app.post("/users", status_code=201)
async def create_user(user: UserCreate):
    return user
```

- 파라미터 타입이 Pydantic 모델이면 **JSON Body**로 해석한다.

## 6.2 입력/출력 모델 분리

- 입력 모델과 출력 모델을 **반드시 분리**한다. (비밀번호 같은 필드가 응답에 노출되는 사고를 막는다.)

```python
class UserBase(BaseModel):
    email: EmailStr
    name: str


class UserCreate(UserBase):
    password: str


class UserOut(UserBase):
    model_config = ConfigDict(from_attributes=True)
    id: int


@app.post("/users", response_model=UserOut, status_code=201)
async def create_user(payload: UserCreate):
    # payload.password는 해싱해서 저장하고, 응답에는 포함하지 않는다
    return {"id": 1, "email": payload.email, "name": payload.name}
```

- `response_model`은 응답을 해당 모델 기준으로 **필터링/검증**한다.

## 6.3 응답 옵션

```python
@app.get(
    "/users/{user_id}",
    response_model=UserOut,
    response_model_exclude_none=True,
    summary="사용자 조회",
    tags=["users"],
)
async def get_user(user_id: int):
    ...
```

## 6.4 PATCH (부분 수정)

```python
class UserUpdate(BaseModel):
    name: str | None = None
    nickname: str | None = None


@app.patch("/users/{user_id}", response_model=UserOut)
async def update_user(user_id: int, payload: UserUpdate):
    data = payload.model_dump(exclude_unset=True)  # 보낸 필드만
    ...
```

- `exclude_unset=True`가 핵심이다. 보내지 않은 필드를 `None`으로 덮어쓰지 않는다.

<br>

# 7. 의존성 주입 (Depends)

## 7.1 개념

- 핸들러가 필요로 하는 객체(DB 세션, 현재 사용자, 설정 등)를 **함수로 선언하고 자동 주입**받는 방식이다.
- Android의 Hilt와 비슷한 목적이다. 단, 컴파일 타임 그래프가 아니라 **요청마다 함수가 호출**되는 방식이다.

## 7.2 기본 사용

```python
from typing import Annotated
from fastapi import Depends


async def common_params(skip: int = 0, limit: int = 10):
    return {"skip": skip, "limit": limit}


CommonParams = Annotated[dict, Depends(common_params)]


@app.get("/items")
async def list_items(params: CommonParams):
    return params
```

- `Annotated`로 타입 별칭을 만들어 재사용하는 방식이 권장된다.

## 7.3 yield 의존성 (리소스 정리)

```python
async def get_db():
    async with SessionLocal() as session:
        try:
            yield session
        finally:
            await session.close()
```

- `yield` 앞은 요청 시작 전, 뒤는 응답 이후에 실행된다.
- DB 세션, 파일 핸들 등 **열고 닫아야 하는 자원**에 사용한다.

## 7.4 클래스 의존성과 하위 의존성

```python
class Pagination:
    def __init__(self, skip: int = 0, limit: int = 20):
        self.skip = skip
        self.limit = min(limit, 100)


@app.get("/posts")
async def list_posts(page: Annotated[Pagination, Depends()]):
    return {"skip": page.skip, "limit": page.limit}
```

- 의존성 안에서 또 다른 `Depends`를 사용할 수 있다. (예: `get_current_user` 안에서 `get_db` 사용)
- 같은 요청 안에서 동일한 의존성은 **캐시**되어 한 번만 호출된다.

## 7.5 라우터 단위 적용

```python
from fastapi import APIRouter

router = APIRouter(
    prefix="/admin",
    tags=["admin"],
    dependencies=[Depends(require_admin)],
)
```

- 라우터 전체에 인증 같은 공통 의존성을 걸 수 있다.

## 7.6 테스트에서 의존성 교체

```python
app.dependency_overrides[get_db] = get_test_db
```

- 실제 DB 대신 테스트 DB나 Fake를 넣을 수 있다. (Hilt의 `@TestInstallIn`과 비슷한 역할)

<br>

# 8. async / await와 동시성

## 8.1 def vs async def

| 선언 | 실행 방식 |
| --- | --- |
| `async def` | 이벤트 루프에서 직접 실행 |
| `def` | **스레드풀**에서 실행 (이벤트 루프를 막지 않음) |

## 8.2 선택 기준

- `await`할 수 있는 비동기 라이브러리를 쓰면 -> `async def`
  - 예: httpx, asyncpg, SQLAlchemy async, redis.asyncio
- 블로킹 라이브러리를 쓰면 -> `def` (스레드풀로 처리)
  - 예: requests, 동기 SQLAlchemy, 일부 SDK
- **`async def` 안에서 블로킹 호출을 하면 서버 전체가 멈춘다.** 가장 흔한 실수다.

```python
import time
import asyncio


@app.get("/bad")
async def bad():
    time.sleep(5)          # 이벤트 루프를 5초간 막는다 (다른 요청도 대기)
    return {"ok": True}


@app.get("/good")
async def good():
    await asyncio.sleep(5)  # 양보하면서 대기
    return {"ok": True}
```

## 8.3 블로킹 코드를 async에서 써야 할 때

```python
from fastapi.concurrency import run_in_threadpool


@app.get("/legacy")
async def legacy():
    result = await run_in_threadpool(blocking_function, arg1, arg2)
    return {"result": result}
```

## 8.4 병렬 호출

```python
import asyncio
import httpx


@app.get("/aggregate")
async def aggregate():
    async with httpx.AsyncClient(timeout=10) as client:
        r1, r2 = await asyncio.gather(
            client.get("https://api.example.com/a"),
            client.get("https://api.example.com/b"),
        )
    return {"a": r1.json(), "b": r2.json()}
```

- 독립적인 외부 호출은 `asyncio.gather`로 동시에 실행해서 응답 시간을 줄인다.
- Kotlin의 `async { }.await()` 병렬 호출과 같은 개념이다.

## 8.5 CPU 바운드 작업

- 무거운 연산(이미지 처리, 모델 추론 등)은 이벤트 루프를 막는다.
- 해결 방법: 별도 워커 프로세스(Celery, ARQ, RQ), `ProcessPoolExecutor`, 또는 모델 서빙 서버 분리(vLLM, Triton 등)

<br>

# 9. DB 연동 (SQLAlchemy 2.0 async)

## 9.1 설치

```bash
pip install sqlalchemy asyncpg aiosqlite alembic
```

- PostgreSQL: `asyncpg`, SQLite(개발용): `aiosqlite`

## 9.2 엔진과 세션

```python
# app/db.py
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase

DATABASE_URL = "sqlite+aiosqlite:///./app.db"
# PostgreSQL: "postgresql+asyncpg://user:password@localhost:5432/dbname"

engine = create_async_engine(DATABASE_URL, echo=False)
SessionLocal = async_sessionmaker(engine, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


async def get_db():
    async with SessionLocal() as session:
        yield session
```

- `expire_on_commit=False`: commit 후 객체 속성 접근 시 lazy load로 인한 에러를 방지한다. (async에서 중요)

## 9.3 모델 정의

```python
# app/models.py
from datetime import datetime
from sqlalchemy import String, ForeignKey, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db import Base


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    name: Mapped[str] = mapped_column(String(50))
    hashed_password: Mapped[str] = mapped_column(String(255))
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())

    posts: Mapped[list["Post"]] = relationship(back_populates="author")


class Post(Base):
    __tablename__ = "posts"

    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    content: Mapped[str]
    author_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())

    author: Mapped[User] = relationship(back_populates="posts")
```

## 9.4 테이블 생성

```python
# 학습용: 앱 시작 시 테이블 생성 (운영에서는 Alembic 마이그레이션 사용)
from contextlib import asynccontextmanager


@asynccontextmanager
async def lifespan(app: FastAPI):
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield
    await engine.dispose()


app = FastAPI(lifespan=lifespan)
```

## 9.5 CRUD

```python
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi import HTTPException

DbSession = Annotated[AsyncSession, Depends(get_db)]


@app.post("/users", response_model=UserOut, status_code=201)
async def create_user(payload: UserCreate, db: DbSession):
    exists = await db.scalar(select(User).where(User.email == payload.email))
    if exists:
        raise HTTPException(status_code=409, detail="이미 가입된 이메일입니다")

    user = User(
        email=payload.email,
        name=payload.name,
        hashed_password=hash_password(payload.password),
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)
    return user


@app.get("/users/{user_id}", response_model=UserOut)
async def get_user(user_id: int, db: DbSession):
    user = await db.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다")
    return user


@app.get("/users", response_model=list[UserOut])
async def list_users(db: DbSession, skip: int = 0, limit: int = 20):
    result = await db.scalars(select(User).offset(skip).limit(limit))
    return result.all()
```

## 9.6 N+1 방지 (async에서는 필수)

- async에서는 lazy loading이 **에러**를 낸다. 관계는 미리 로드해야 한다.

```python
from sqlalchemy.orm import selectinload

stmt = select(User).options(selectinload(User.posts)).where(User.id == user_id)
user = await db.scalar(stmt)
```

## 9.7 Alembic (마이그레이션)

```bash
alembic init -t async migrations
alembic revision --autogenerate -m "create users and posts"
alembic upgrade head
```

- 모델을 바꿀 때마다 마이그레이션 파일을 생성해서 스키마 변경 이력을 관리한다.

<br>

# 10. 인증 (JWT)

## 10.1 설치

```bash
pip install pyjwt "pwdlib[argon2]"
```

- 비밀번호는 평문으로 저장하지 않고 **해시**로 저장한다. (argon2, bcrypt)
- JWT는 `pyjwt`를 사용한다.

## 10.2 비밀번호 해싱과 토큰 생성

```python
# app/security.py
from datetime import datetime, timedelta, timezone

import jwt
from pwdlib import PasswordHash

SECRET_KEY = "환경변수에서 읽어올 것"   # 코드에 하드코딩 금지
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 30

password_hash = PasswordHash.recommended()


def hash_password(plain: str) -> str:
    return password_hash.hash(plain)


def verify_password(plain: str, hashed: str) -> bool:
    return password_hash.verify(plain, hashed)


def create_access_token(subject: str) -> str:
    expire = datetime.now(timezone.utc) + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    payload = {"sub": subject, "exp": expire}
    return jwt.encode(payload, SECRET_KEY, algorithm=ALGORITHM)
```

## 10.3 로그인 엔드포인트

```python
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/login")


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"


@app.post("/auth/login", response_model=Token)
async def login(
    form: Annotated[OAuth2PasswordRequestForm, Depends()],
    db: DbSession,
):
    user = await db.scalar(select(User).where(User.email == form.username))
    if user is None or not verify_password(form.password, user.hashed_password):
        raise HTTPException(
            status_code=401,
            detail="이메일 또는 비밀번호가 올바르지 않습니다",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return Token(access_token=create_access_token(str(user.id)))
```

- `OAuth2PasswordRequestForm`은 `application/x-www-form-urlencoded` 형식(username, password)을 받는다. JSON이 아니다. (Swagger UI의 Authorize 버튼과 연동된다.)

## 10.4 현재 사용자 의존성

```python
from jwt import InvalidTokenError


async def get_current_user(
    token: Annotated[str, Depends(oauth2_scheme)],
    db: DbSession,
) -> User:
    credentials_error = HTTPException(
        status_code=401,
        detail="인증 정보가 유효하지 않습니다",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        user_id = payload.get("sub")
        if user_id is None:
            raise credentials_error
    except InvalidTokenError:
        raise credentials_error

    user = await db.get(User, int(user_id))
    if user is None:
        raise credentials_error
    return user


CurrentUser = Annotated[User, Depends(get_current_user)]


@app.get("/me", response_model=UserOut)
async def me(user: CurrentUser):
    return user
```

## 10.5 실무 체크리스트

- Access Token은 짧게(15~30분), Refresh Token을 별도로 발급해서 갱신한다.
- `SECRET_KEY`는 환경변수 / 시크릿 매니저로 관리한다.
- HTTPS를 필수로 사용한다.
- 로그인 실패 메시지는 이메일/비밀번호 중 무엇이 틀렸는지 구분하지 않는다. (계정 열거 공격 방지)
- 로그인 시도 횟수 제한(rate limit)을 둔다.

<br>

# 11. 미들웨어와 CORS

## 11.1 CORS

- 브라우저 클라이언트(웹 프론트)가 다른 출처의 API를 호출할 때 필요하다. 네이티브 Android 앱은 CORS의 영향을 받지 않는다.

```python
from fastapi.middleware.cors import CORSMiddleware

app.add_middleware(
    CORSMiddleware,
    allow_origins=["https://example.com"],   # 운영에서 "*" 지양
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
```

- `allow_credentials=True`일 때는 `allow_origins=["*"]`를 쓸 수 없다. 출처를 명시해야 한다.

## 11.2 커스텀 미들웨어

```python
import time
from fastapi import Request


@app.middleware("http")
async def add_process_time_header(request: Request, call_next):
    start = time.perf_counter()
    response = await call_next(request)
    elapsed = time.perf_counter() - start
    response.headers["X-Process-Time"] = f"{elapsed:.4f}"
    return response
```

- 요청 로깅, 요청 ID 부여, 처리 시간 측정 등에 사용한다.
- 미들웨어는 **등록 역순**으로 요청을 감싼다. (나중에 등록한 것이 가장 바깥)

## 11.3 기타 유용한 미들웨어

```python
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.middleware.trustedhost import TrustedHostMiddleware

app.add_middleware(GZipMiddleware, minimum_size=1000)
app.add_middleware(TrustedHostMiddleware, allowed_hosts=["example.com", "*.example.com"])
```

<br>

# 12. 예외 처리

## 12.1 HTTPException

```python
raise HTTPException(status_code=404, detail="Not Found")
```

## 12.2 커스텀 예외와 핸들러

```python
from fastapi import Request
from fastapi.responses import JSONResponse


class BusinessError(Exception):
    def __init__(self, code: str, message: str, status_code: int = 400):
        self.code = code
        self.message = message
        self.status_code = status_code


@app.exception_handler(BusinessError)
async def business_error_handler(request: Request, exc: BusinessError):
    return JSONResponse(
        status_code=exc.status_code,
        content={"code": exc.code, "message": exc.message},
    )
```

- 클라이언트(Android)가 파싱하기 쉽도록 **에러 응답 포맷을 통일**한다. (`code`, `message`)

## 12.3 검증 에러 (422) 커스터마이징

```python
from fastapi.exceptions import RequestValidationError


@app.exception_handler(RequestValidationError)
async def validation_error_handler(request: Request, exc: RequestValidationError):
    return JSONResponse(
        status_code=422,
        content={
            "code": "VALIDATION_ERROR",
            "message": "요청 값이 올바르지 않습니다",
            "errors": [
                {"field": ".".join(str(p) for p in e["loc"]), "message": e["msg"]}
                for e in exc.errors()
            ],
        },
    )
```

## 12.4 예상 못한 예외

```python
import logging

logger = logging.getLogger(__name__)


@app.exception_handler(Exception)
async def unhandled_error_handler(request: Request, exc: Exception):
    logger.exception("Unhandled error: %s %s", request.method, request.url)
    return JSONResponse(
        status_code=500,
        content={"code": "INTERNAL_ERROR", "message": "서버 오류가 발생했습니다"},
    )
```

- 내부 예외 메시지, 스택 트레이스를 클라이언트에 그대로 노출하지 않는다.

<br>

# 13. Lifespan과 BackgroundTasks

## 13.1 Lifespan

- 앱 시작/종료 시점에 한 번만 실행할 작업(리소스 초기화, 연결 정리)을 정의한다.
- 기존 `@app.on_event("startup")`은 deprecated이고, **lifespan**을 사용한다.

```python
from contextlib import asynccontextmanager
import httpx


@asynccontextmanager
async def lifespan(app: FastAPI):
    # startup
    app.state.http = httpx.AsyncClient(timeout=30)
    app.state.model = load_model()          # 무거운 모델은 시작 시 한 번만 로드
    yield
    # shutdown
    await app.state.http.aclose()


app = FastAPI(lifespan=lifespan)


@app.get("/predict")
async def predict(request: Request, text: str):
    model = request.app.state.model
    return {"result": model.predict(text)}
```

- 요청마다 모델을 로드하거나 HTTP 클라이언트를 새로 만들면 매우 느리다. **시작 시 한 번** 만들어 재사용한다.

## 13.2 BackgroundTasks

```python
from fastapi import BackgroundTasks


def send_welcome_email(email: str):
    ...


@app.post("/signup", status_code=201)
async def signup(payload: UserCreate, background: BackgroundTasks):
    # 회원 저장 ...
    background.add_task(send_welcome_email, payload.email)
    return {"message": "가입 완료"}
```

- 응답을 보낸 **직후** 같은 프로세스에서 실행된다.
- 가벼운 작업(이메일, 로그)에만 쓴다. 오래 걸리거나 **반드시 실행되어야 하는** 작업은 Celery, ARQ 같은 작업 큐를 사용한다. (프로세스가 죽으면 작업도 사라진다.)

<br>

# 14. 파일 업로드

```python
from fastapi import File, UploadFile
from pathlib import Path
import uuid

UPLOAD_DIR = Path("uploads")
UPLOAD_DIR.mkdir(exist_ok=True)
ALLOWED_TYPES = {"image/jpeg", "image/png", "image/webp"}
MAX_SIZE = 5 * 1024 * 1024  # 5MB


@app.post("/upload")
async def upload_image(file: UploadFile):
    if file.content_type not in ALLOWED_TYPES:
        raise HTTPException(status_code=415, detail="지원하지 않는 파일 형식입니다")

    data = await file.read()
    if len(data) > MAX_SIZE:
        raise HTTPException(status_code=413, detail="파일 크기는 5MB 이하여야 합니다")

    ext = Path(file.filename or "").suffix.lower()
    saved_name = f"{uuid.uuid4().hex}{ext}"          # 원본 파일명을 그대로 쓰지 않는다
    (UPLOAD_DIR / saved_name).write_bytes(data)
    return {"filename": saved_name, "size": len(data)}
```

- `python-multipart`가 필요하다. (`fastapi[standard]`에 포함)
- 파일명을 그대로 쓰면 경로 조작(path traversal) 위험이 있다. 서버에서 생성한 이름을 사용한다.
- `content_type`은 클라이언트가 보내는 값이라 신뢰할 수 없다. 중요한 서비스는 매직 넘버 등으로 실제 내용을 검사한다.
- 큰 파일은 S3 Presigned URL로 클라이언트가 직접 업로드하게 하는 구조가 낫다.

## 14.1 여러 파일

```python
@app.post("/upload/multi")
async def upload_many(files: list[UploadFile]):
    return {"names": [f.filename for f in files]}
```

<br>

# 15. 스트리밍 응답 (SSE, LLM 연동)

## 15.1 StreamingResponse

```python
import asyncio
from fastapi.responses import StreamingResponse


async def number_stream():
    for i in range(5):
        yield f"data: {i}\n\n"
        await asyncio.sleep(1)


@app.get("/stream")
async def stream():
    return StreamingResponse(number_stream(), media_type="text/event-stream")
```

- **SSE(Server-Sent Events)** 형식: `data: ...\n\n`
- 서버 -> 클라이언트 단방향 스트림이다. LLM 응답을 토큰 단위로 보내는 UI(타이핑 효과)에 많이 쓴다.

## 15.2 LLM 스트리밍 예시 (Anthropic SDK)

```bash
pip install anthropic
```

```python
import json
import anthropic
from pydantic import BaseModel

client = anthropic.AsyncAnthropic()   # ANTHROPIC_API_KEY 환경변수 사용


class ChatRequest(BaseModel):
    message: str


@app.post("/chat/stream")
async def chat_stream(req: ChatRequest):
    async def event_generator():
        try:
            async with client.messages.stream(
                model="claude-sonnet-4-5",   # 사용할 모델명으로 교체
                max_tokens=1024,
                messages=[{"role": "user", "content": req.message}],
            ) as stream:
                async for text in stream.text_stream:
                    yield f"data: {json.dumps({'delta': text}, ensure_ascii=False)}\n\n"
            yield "data: [DONE]\n\n"
        except anthropic.APIError as e:
            yield f"data: {json.dumps({'error': str(e)}, ensure_ascii=False)}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
```

- `X-Accel-Buffering: no`: Nginx가 응답을 버퍼링해서 스트림이 한꺼번에 도착하는 문제를 방지한다.
- `AsyncAnthropic`을 쓰는 이유: 동기 클라이언트를 쓰면 스트리밍 중 이벤트 루프가 막힌다.
- 모델명은 변경될 수 있으니 공식 문서에서 최신 값을 확인해서 쓴다.

## 15.3 구조화 출력 검증

```python
class Summary(BaseModel):
    title: str
    key_points: list[str]


raw = '{"title": "FastAPI", "key_points": ["비동기", "Pydantic"]}'
summary = Summary.model_validate_json(raw)   # LLM이 만든 JSON을 스키마로 검증
```

- LLM 출력은 형식이 어긋날 수 있다. Pydantic으로 검증하고, 실패하면 재시도하는 로직을 둔다.

## 15.4 WebSocket

```python
from fastapi import WebSocket, WebSocketDisconnect


@app.websocket("/ws")
async def ws_endpoint(ws: WebSocket):
    await ws.accept()
    try:
        while True:
            text = await ws.receive_text()
            await ws.send_text(f"echo: {text}")
    except WebSocketDisconnect:
        pass
```

- 양방향 실시간 통신이 필요할 때 사용한다. 단방향 스트리밍이면 SSE가 더 단순하다.

<br>

# 16. 프로젝트 구조

## 16.1 권장 구조

```text
app/
├── main.py                # FastAPI 인스턴스, 라우터 등록, lifespan
├── core/
│   ├── config.py          # 설정 (pydantic-settings)
│   ├── security.py        # 해싱, JWT
│   └── exceptions.py      # 커스텀 예외, 핸들러
├── db/
│   ├── base.py            # Base, engine, session
│   └── deps.py            # get_db
├── users/
│   ├── router.py          # 엔드포인트
│   ├── schemas.py         # Pydantic 모델
│   ├── models.py          # SQLAlchemy 모델
│   ├── service.py         # 비즈니스 로직
│   └── deps.py            # 도메인별 의존성
├── posts/
│   └── ...
└── tests/
```

- 계층(routers/, models/, schemas/)별 분리보다 **도메인(기능)별 분리**가 규모가 커져도 관리하기 쉽다.
- Android 멀티모듈에서 feature 단위로 모듈을 나누는 것과 같은 사고방식이다.

## 16.2 APIRouter

```python
# app/users/router.py
from fastapi import APIRouter

router = APIRouter(prefix="/users", tags=["users"])


@router.get("/{user_id}", response_model=UserOut)
async def get_user(user_id: int, db: DbSession):
    ...
```

```python
# app/main.py
from fastapi import FastAPI
from app.users.router import router as users_router
from app.posts.router import router as posts_router

app = FastAPI(lifespan=lifespan)
app.include_router(users_router)
app.include_router(posts_router, prefix="/api/v1")
```

- API 버전은 `/api/v1`처럼 경로 prefix로 관리한다. 모바일 앱은 구버전 사용자가 오래 남기 때문에 **하위 호환**이 중요하다.

## 16.3 설정 관리 (pydantic-settings)

```bash
pip install pydantic-settings
```

```python
# app/core/config.py
from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str
    secret_key: str
    access_token_expire_minutes: int = 30
    anthropic_api_key: str | None = None


@lru_cache
def get_settings() -> Settings:
    return Settings()
```

```python
SettingsDep = Annotated[Settings, Depends(get_settings)]
```

- `.env` 파일은 `.gitignore`에 반드시 추가한다. 시크릿을 저장소에 올리지 않는다.

<br>

# 17. 테스트

## 17.1 설치

```bash
pip install pytest pytest-asyncio httpx
```

## 17.2 TestClient (동기 테스트)

```python
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_health():
    res = client.get("/health")
    assert res.status_code == 200
    assert res.json() == {"status": "ok"}
```

## 17.3 비동기 테스트 + DB 교체

```python
# tests/conftest.py
import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from app.main import app
from app.db import Base, get_db

TEST_DB_URL = "sqlite+aiosqlite:///:memory:"


@pytest_asyncio.fixture
async def client():
    engine = create_async_engine(TEST_DB_URL)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    TestSession = async_sessionmaker(engine, expire_on_commit=False)

    async def override_get_db():
        async with TestSession() as session:
            yield session

    app.dependency_overrides[get_db] = override_get_db

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as c:
        yield c

    app.dependency_overrides.clear()
    await engine.dispose()
```

```python
# tests/test_users.py
import pytest


@pytest.mark.asyncio
async def test_create_user(client):
    res = await client.post(
        "/users",
        json={"email": "a@b.com", "name": "현서", "password": "password123"},
    )
    assert res.status_code == 201
    body = res.json()
    assert body["email"] == "a@b.com"
    assert "password" not in body


@pytest.mark.asyncio
async def test_duplicate_email(client):
    payload = {"email": "a@b.com", "name": "현서", "password": "password123"}
    await client.post("/users", json=payload)
    res = await client.post("/users", json=payload)
    assert res.status_code == 409
```

- `pytest.ini`에 `asyncio_mode = auto`를 설정하면 `@pytest.mark.asyncio`를 생략할 수 있다.
- 인메모리 SQLite(`:memory:`)는 연결마다 DB가 따로 생길 수 있다. 문제가 되면 `StaticPool`을 사용하거나 파일 DB, 혹은 Testcontainers의 실제 PostgreSQL을 쓴다.

## 17.4 외부 API 모킹

```python
from unittest.mock import AsyncMock


async def test_with_mock(client, monkeypatch):
    fake = AsyncMock(return_value={"answer": "mocked"})
    monkeypatch.setattr("app.services.llm.ask", fake)
    ...
```

- LLM, 결제 같은 외부 호출은 테스트에서 반드시 모킹한다. (비용, 비결정성, 속도 문제)

<br>

# 18. 배포

## 18.1 운영 실행

```bash
# uvicorn 단독 (프로세스 수 지정)
uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 4

# 또는 gunicorn + uvicorn worker
gunicorn app.main:app -k uvicorn.workers.UvicornWorker -w 4 -b 0.0.0.0:8000
```

- 워커 수는 보통 CPU 코어 수를 기준으로 시작해서 부하 테스트로 조정한다.
- 컨테이너 환경(Kubernetes 등)에서는 **컨테이너 1개 = 프로세스 1개**로 두고 컨테이너 수로 스케일하는 방식이 일반적이다.
- 워커마다 메모리에 모델을 올리면 메모리가 워커 수만큼 늘어난다. 주의한다.

## 18.2 Dockerfile

```dockerfile
FROM python:3.12-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

RUN useradd -m appuser
USER appuser

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

- 의존성 파일을 먼저 복사하면 Docker 레이어 캐시를 활용할 수 있다.
- 컨테이너는 root가 아닌 일반 사용자로 실행한다.

## 18.3 docker-compose (API + PostgreSQL)

```yaml
services:
  api:
    build: .
    ports:
      - "8000:8000"
    env_file: .env
    depends_on:
      db:
        condition: service_healthy

  db:
    image: postgres:16
    environment:
      POSTGRES_USER: app
      POSTGRES_PASSWORD: app
      POSTGRES_DB: app
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app"]
      interval: 5s
      timeout: 3s
      retries: 5

volumes:
  pgdata:
```

## 18.4 운영 체크리스트

- 리버스 프록시(Nginx, ALB)와 HTTPS 적용
- 운영에서는 `/docs` 비활성화 또는 접근 제한: `FastAPI(docs_url=None, redoc_url=None)`
- 헬스체크 엔드포인트(`/health`) 제공
- 구조화된 로그(JSON)와 요청 ID
- 시크릿은 환경변수/시크릿 매니저로 주입
- DB 마이그레이션(Alembic)을 배포 파이프라인에 포함

<br>

# 19. Android(Retrofit) 연동 관점

## 19.1 OpenAPI로 계약 공유

- FastAPI의 `/openapi.json`은 API 계약서 역할을 한다.
- **openapi-generator**로 Kotlin/Retrofit 클라이언트 코드를 자동 생성할 수 있다.
- 서버 응답 모델(Pydantic)과 Android DTO(`@Serializable data class`)의 필드명, nullable 여부를 일치시킨다.

## 19.2 Pydantic <-> Kotlin DTO 대응

```python
class PostOut(BaseModel):
    id: int
    title: str
    content: str
    thumbnail_url: str | None = None
    created_at: datetime
```

```kotlin
@Serializable
data class PostDto(
    val id: Int,
    val title: String,
    val content: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("created_at") val createdAt: String,
)
```

- Python은 snake_case, Kotlin은 camelCase라서 `@SerialName`으로 매핑한다.
- 서버에서 camelCase 응답을 쓰려면 Pydantic의 `alias_generator=to_camel`을 설정하는 방법도 있다.
- `datetime`은 ISO 8601 문자열로 직렬화된다. Android에서 `Instant` / `OffsetDateTime`으로 파싱한다.

## 19.3 에러 응답 처리

```kotlin
@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
)
```

- 12장에서 통일한 에러 포맷(`code`, `message`)을 Retrofit의 `HttpException` 처리에서 파싱해서 UI 메시지로 연결한다.
- 401 응답 시 OkHttp `Authenticator`에서 Refresh Token으로 갱신하는 흐름과 맞물린다.

## 19.4 Android에서 SSE 소비

- OkHttp의 `okhttp-sse`(`EventSources`)로 SSE를 받아 `callbackFlow`로 감싸서 UI에 토큰 단위로 표시한다.
- 서버는 15장의 `/chat/stream` 형식을 그대로 사용한다.

## 19.5 페이지네이션

- Paging3와 연동할 때는 서버가 `skip/limit`(offset) 또는 **cursor 기반** 응답을 제공하게 설계한다.
- 무한 스크롤에서는 offset보다 cursor 방식이 데이터 추가/삭제에 안정적이다.

```python
class Page(BaseModel, Generic[T]):
    items: list[T]
    next_cursor: str | None
```

<br>

# 20. 정리

## 20.1 핵심 요약

1. **FastAPI**는 Starlette + Pydantic 기반의 비동기 API 프레임워크로, 타입 힌트만으로 검증, 직렬화, 문서화가 자동 처리된다.
2. **Pydantic**으로 입력/출력 모델을 분리하고, `response_model`로 응답 노출 범위를 통제한다.
3. **Depends**로 DB 세션, 현재 사용자, 설정을 주입한다. `dependency_overrides`로 테스트에서 교체한다.
4. **async def 안에서 블로킹 호출을 하지 않는다.** 블로킹 코드는 `def`(스레드풀) 또는 `run_in_threadpool`로 처리한다.
5. **SQLAlchemy 2.0 async** 사용 시 `expire_on_commit=False`, 관계는 `selectinload`로 미리 로드한다. 스키마는 Alembic으로 관리한다.
6. **JWT 인증**은 `OAuth2PasswordBearer` + `get_current_user` 의존성으로 구성하고, 시크릿은 환경변수로 관리한다.
7. **lifespan**에서 모델, HTTP 클라이언트를 한 번만 초기화한다. 오래 걸리거나 중요한 작업은 `BackgroundTasks`가 아니라 작업 큐를 사용한다.
8. LLM 응답은 **SSE(StreamingResponse)** 로 스트리밍하고, 구조화 출력은 Pydantic으로 검증한다.
9. 프로젝트는 **도메인별**로 나누고, 설정은 `pydantic-settings`로 관리한다.
10. 테스트는 `httpx.AsyncClient` + `dependency_overrides`, 외부 API는 모킹한다. 배포는 Docker + uvicorn, 운영에서는 `/docs` 접근을 제한한다.

## 20.2 Android 개발자 관점 비교

| Android | FastAPI |
| --- | --- |
| Hilt / `@Inject` | `Depends` |
| `@HiltViewModel` + 상태 | 요청 단위 의존성 (상태 없음) |
| Retrofit 인터페이스 | 라우터 (엔드포인트 정의) |
| `@Serializable data class` (DTO) | Pydantic `BaseModel` |
| Room | SQLAlchemy |
| Coroutines / Flow | `async` / `await`, 스트리밍 응답 |
| OkHttp Interceptor | Middleware |
| 멀티모듈(feature) | 도메인별 패키지 + APIRouter |
| MockK | `unittest.mock`, `dependency_overrides` |

## 20.3 면접 대비 한 줄 정리

- FastAPI가 빠른 이유는? -> ASGI 기반 비동기 처리(Starlette, uvicorn)와 Pydantic v2(Rust 코어)의 빠른 검증.
- `def`와 `async def`의 차이는? -> `def`는 스레드풀, `async def`는 이벤트 루프에서 실행. 블로킹 호출을 `async def` 안에서 하면 서버 전체가 막힌다.
- 의존성 주입은 어떻게 동작하나? -> 함수 시그니처의 `Depends`를 요청마다 해석해서 호출하고, 같은 요청 안에서는 결과를 캐시한다. `yield`로 자원 정리도 가능하다.
- 입력 모델과 출력 모델을 왜 분리하나? -> 비밀번호 같은 민감 필드 노출 방지, 클라이언트에 공개할 계약을 명확히 하기 위해.
- BackgroundTasks와 Celery의 차이는? -> 전자는 같은 프로세스에서 응답 직후 실행(유실 가능), 후자는 별도 워커와 브로커로 신뢰성 있는 비동기 작업 처리.
- LLM 응답을 실시간으로 보여주려면? -> SSE로 토큰 단위 스트리밍, 프록시 버퍼링 비활성화, 클라이언트는 OkHttp SSE 등으로 수신.

## 20.4 실습 과제

- 회원가입 / 로그인 / 내 정보 조회 API 만들고 Swagger UI에서 Authorize까지 테스트하기
- SQLAlchemy async + Alembic으로 게시글 CRUD와 cursor 기반 페이지네이션 구현하기
- `/chat/stream` SSE 엔드포인트를 만들고 Android(OkHttp SSE)에서 토큰 단위로 표시하기
- pytest로 사용자 API 테스트 작성하고 `dependency_overrides`로 테스트 DB 교체하기
- Dockerfile + docker-compose로 API와 PostgreSQL 띄우기
