<<<<<<< HEAD
여러명이서 업무를 분담하여 도서관리 프로그램을 만들 것이다. 
우리가 쓸 언어는 JAVA이다. 편집기는 IntelliJ IDEA를 사용하고 있다.
MySQL DB를 연결하고자 한다.

내가 맡은 업무는 MySQL을 이용하여 SQL문으로 DB를 생성하고, 기본 데이터를 포함 시키는 것이다.


프로그램은 인텔리제를 기반으로 한 자바 기반으로 프로그램을 작성할거고, mysql을 연동해서 도서관리를 할 것이다 추가해야될 기능 

기본적으로는 다음의 기능을 가지게 하고 싶다.

1. 도서 관리기능: 책의 정보는 ISBN(PK), 책이름, 출판일, 저자, 장르, 대여상태 를 포함하면 좋겠다.

2. 회원 관리기능: 회원 정보는 회원번호, 이름, 생년월일을 포함하면 좋겠다.
 
3. 도서반납 기능을 만들고자 한다.

4. 도서대여 기능을 만들고자 한다.


자 이정도로 하여 DB를 생성하는 SQL코드를 작성해주면 좋겠다. 이 때, 도서와 회원의 임시 데이터를 각각 100건씩 생성해주면 좋겠다.

지금 바로 코드를 만들지 말고, 최선의 답을 위하여 내게 더 필요한 정보나 질문이 있으면 하길 바란다. 



------------------------------------------------------------------------------------------------------------------------------------

좋아요! 바로 SQL을 쓰기 전에, “현실적으로 잘 굴러가는 스키마( schema )”를 위해 몇 가지를 먼저 정리하면 이후 구현·유지보수가 훨씬 수월합니다. 아래 질문에 답해주시면, 그 기준에 맞춰 \*\*DB 생성 + 제약조건(Constraints) + 인덱스 + 샘플데이터(도서 100·회원 100)\*\*까지 한 번에 드리겠습니다.

---

# 1) 필수 결정사항(꼭 선택 필요)

**A. 도서 재고 모델링 방식**

* ⓐ **단순형(Simple)**: `book` 테이블만 있고, ISBN 당 **1권**으로 가정. 대여여부를 `loan_status`(ENUM)로 보관.

  * *장점*: 구현 빠름.
  * *단점*: 같은 ISBN의 **복수권** 관리 불가, 대여 이력(History) 관리에 제약.
* ⓑ **현실형(Recommended)**: `book_title`(서지정보) + `book_copy`(실물책 개체) 분리. 대여/반납은 `loan`(대여거래) 테이블에 기록.

  * *장점*: **복수권** 관리, **대여 이력**·연체 처리·통계에 강함.
  * *단점*: 테이블이 조금 늘어남.

> 어떤 방식을 원하시나요? (권장: ⓑ 현실형)

**B. 저자(Author)와 장르(Genre)**

* 저자는 **단일 문자열 컬럼**으로 둘까요, 아니면 **정규화**( `author` / `book_author_map`) 할까요?

  * *권장*: 다수 저자 지원을 위해 정규화.
* 장르는 **ENUM**으로 고정할까요, 아니면 **코드 테이블**(`genre`)로 관리할까요?

  * *권장*: 코드 테이블(확장·다국어 대응 유리).

**C. 대여 정책(Loan policy)**

* 기본 대여기간: **며칠**로 할까요? (예: 14일)
* **연장(renewal)** 허용 여부/횟수? (예: 1회, 7일)
* **동시대여 권수 제한**: 회원 1인당 최대 몇 권? (예: 3권)
* **연체료**를 DB 차원에서 관리할까요, 앱 로직에서만 계산할까요? (초기엔 미도입 가능)

**D. 회원 식별키**

* `member_id`는 **INT AUTO\_INCREMENT**로 생성할까요?
* 주민번호 등 민감정보는 **미수집** 전제 맞나요? (생년월일만 저장?)

**E. ISBN**

* ISBN은 **PK**로 쓸까요? (권장: `book_title`에서 `isbn` UNIQUE, PK는 별도 surrogate key)

  * 사유: 몇몇 서지데이터는 ISBN 누락/중복/오타가 존재할 수 있어 운영 중 유연성↑

---

# 2) 환경·운영 파라미터

1. **MySQL 버전**: 8.x 맞나요? (함수·제약·인덱스 옵션에 영향)
2. **문자셋/콜레이션**: `utf8mb4` / `utf8mb4_0900_ai_ci`로 할까요? (국문·이모지 대응)
3. **스토리지 엔진**: `InnoDB`(트랜잭션/외래키 지원)로 고정 OK?
4. **타임존 관리**: 서버/DB 타임존을 Asia/Seoul로 둘까요? (대여·반납 시간 기록 일관성)
5. **권한 스크립트**: DB 유저 생성(`CREATE USER …; GRANT …;`)도 같이 드릴까요?

---

# 3) 권장 스키마 제안(선택지가 확정되면 SQL로 제공)

아래는 **현실형(ⓑ)** 기준의 구조 개요입니다. *아직 SQL은 아닙니다(요청하신 대로 질문 후 작성).*

* `book_title` : 서지 단위(ISBN, 제목, 출판일, 기본 장르 등)
* `author` / `book_author_map` : 다대다 저자 매핑
* `genre` : 장르 코드
* `book_copy` : 실물 책(개별 바코드/인벤토리 ID, 상태)
* `member` : 회원(회원번호, 이름, 생년월일, 가입일 등)
* `loan` : 대여 트랜잭션(누가, 어느 실물책을, 언제 빌리고/반납했는지, 반납기한, 반납일)

  * “현재 대여중인 항목”은 `loan.returned_at IS NULL`로 조회
  * **동시대여 제한**, **중복대여 방지**는 제약/트리거/유니크 인덱스/애플리케이션 로직 조합으로 처리

> 만약 **단순형(ⓐ)** 을 원하시면 `book_copy`/`loan` 없이 `book(=title)`에 `loan_status`만 두고, 간단한 `rental_log` 정도만 두는 버전을 드릴 수 있습니다.

---

# 4) 샘플데이터(Seed) 생성 방침

* **도서 100권**:

  * ISBN: 합리적 포맷(임의·중복 방지)
  * 제목/저자/장르: 한국어 이름 섞어 현실감 부여(무작위)
  * 출판일: 최근 30년 범위 무작위
* **회원 100명**:

  * 한국형 이름(무작위), 생년월일(예: 1970–2010 범위)
  * 개인정보 최소화(연락처·주소는 초기 제외)
* **대여 상태**: 요청 3·4번 기능 검증 위해, 초기 대여 0건으로 둘까요, 아니면 몇 건은 대여중 상태로 더미 `loan` 데이터까지 넣어둘까요? (현실형 선택 시)

---

# 5) 인덱스·제약(Constraints) 초안

* **고유성(UNIQUE)**: `book_title.isbn`
* **외래키(FK)**:

  * `book_copy.book_title_id → book_title.id`
  * `book_author_map.(book_title_id, author_id)`
  * `loan.(book_copy_id → book_copy.id, member_id → member.id)`
* **무결성 보강**:

  * `loan`에 `(book_copy_id, returned_at IS NULL)` 유니크 인덱스(“같은 실물책은 동시에 두 사람에게 대여 불가”)
  * 날짜 컬럼들 `CHECK`(MySQL 8.0.16+), 또는 앱/트리거로 보강
* **조회 최적화**: `loan`의 `member_id`, `book_copy_id`, `due_at` 인덱스, `book_title.title` FULLTEXT(옵션)

---

# 6) 출력물(확정 후 제공 범위)

* `CREATE DATABASE …;` + `CREATE USER/GRANT`(선택 시)
* 테이블 생성 스크립트(DDL) + 외래키/인덱스
* 필수 제약(중복대여 방지 등)
* 시드 데이터(도서 100·회원 100 ·(선택) 대여 더미)
* 간단한 검증용 쿼리(예: “대여 가능 도서 목록”, “회원별 현재 대여 현황”, “연체 대상( today > due\_at AND returned\_at IS NULL )”)

---

# 7) 마지막 확인 질문(요약)

1. 스키마 방식: **ⓐ 단순형** vs **ⓑ 현실형(권장)** → ?
2. 저자/장르: **정규화(권장)** vs 단일 문자열/ENUM → ?
3. 대여정책: 기본 대여기간 **n일**, 연장 허용 여부/횟수, 동시대여 최대 권수 → ?
4. MySQL 8.x / `utf8mb4` / `InnoDB` / Asia/Seoul 타임존 → 괜찮나요?
5. DB 유저/권한 스크립트도 포함할까요?
6. 초기 더미 **대여건**도 몇 건 넣어둘까요, 아니면 0건으로 시작할까요?

---

필요 정보를 알려주시면, 곧바로 **완성된 SQL 스크립트**(DB 생성부터 시드 데이터까지)로 드리겠습니다.



------------------------------------------------------------------------------------------------------------------------------------
1)
A. 단순형
B. 단일 문자열 컬럼, 장르는 코드 테이블로 관리
C. 대여기간 2주, 연장 허용 안함. 동시대여 권수 3권
D. AUTO_INCREMENT, 민감정보 미수집. 생년월일만 저장
E. ISBN을 PK로


2) 
 1. 8.x 버전 맞음
 2. 추천하는 방식으로 적용
 3. InnoDB 적용
 4. 추천하는 방식으로 적용
 5. 권한 스크립트는 일단은 배제

3) 단순형

4) 추천하는 대로 하라. 몇 건은 대여중 상태로 만들어라.

5) 추천하는대로 해라.

6) 추천하는대로해라.

더 필요한 정보가 있으면 하고 없으면 터미널에서 실행가능한 SQL 파일을 생성해주면 좋겠다. 생성 후 어떻게 실행하여 DB를 추가해야 하는지도 알려주면 좋겠다.



------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
---------------------------------------------------------------------------------------
---------------------------------------------------------------------------------------


아주 좋습니다. 주신 선택사항(단순형·저자 문자열·장르 코드테이블·대여 2주·연장 없음·동시대여 3권·ISBN=PK·MySQL8/utf8mb4/InnoDB/Asia-Seoul) 기준으로 **터미널에서 곧바로 실행 가능한 단일 SQL 스크립트**를 준비했습니다.
아래에는 (1) 스키마/트리거/뷰 + (2) 시드데이터(도서 100·회원 100·대여중 10건)를 한 번에 만드는 코드가 포함되어 있습니다.

---

## 설계 개요(요약)

* **단순형(Simple model)**: 실물권(복본) 없이 ISBN당 1권 가정. 현재 대여상태는 `book` 테이블이 보유(`loan_status`, `current_member_id`, `loaned_at`, `due_at`).
* **대여 이력(Loan history)**: `loan` 테이블에 기록. “동시복수 대여 방지(같은 책 중복대여 금지)”는 **생성 열(Generated Column)** + **UNIQUE 인덱스**로 보강, “회원당 3권 제한”은 \*\*트리거(Trigger)\*\*로 보강.
* **장르(Genre)**: 코드 테이블(`genre`).
* **무결성(Constraints)**: FK/CHK/인덱스 및 트리거로 운영 규칙을 강제. *(MySQL 8.0.16+에서 CHECK 유효)* ([MySQL][1])
* **기술 근거**: 생성열과 인덱스, 트리거 사용은 MySQL 8.0 공식 문서 권고 방식입니다. ([MySQL][2])
=======
# 📚 도서관리 프로그램

## 프로젝트 개요
이 프로젝트는 **Java (IntelliJ IDEA)** 기반으로 작성되는 **도서 관리 프로그램**입니다.  
로컬 환경에서 **MySQL** 데이터베이스를 연동하여, 도서의 등록/대여/반납을 관리할 수 있도록 설계되었습니다.

> 4명이서 협업하며, 각자 고유한 DB를 로컬에 생성하여 독립적으로 실행할 수 있도록 GitHub로 공유합니다.

---

## 주요 기능
- 도서 관리
    - 도서 등록
    - 장르별 관리
- 도서 대여
- 도서 반납
- 도서 검색 (도서명 기준)

---

## 데이터베이스 설계

### 테이블 구조

#### 1. 장르 테이블 (`genres`)
| 컬럼명   | 타입          | 설명          |
|----------|---------------|---------------|
| genre_id | INT (PK, AI)  | 장르 고유 번호 |
| name     | VARCHAR(50)   | 장르 이름      |

#### 2. 도서 테이블 (`books`)
| 컬럼명        | 타입            | 설명               |
|---------------|-----------------|--------------------|
| book_id       | INT (PK, AI)    | 책 고유 번호        |
| title         | VARCHAR(100)    | 책 이름             |
| genre_id      | INT (FK)        | 장르 고유 번호      |
| registered_at | DATETIME        | 등록일 (기본값 NOW) |
| qty_total     | INT             | 총 보유 권수        |
| qty_available | INT             | 대여 가능 권수      |

---

## 개발 환경
- 언어: Java 17+
- IDE: IntelliJ IDEA
- DBMS: MySQL 8.x
- 빌드 툴: Maven 또는 Gradle
- 버전 관리: Git / GitHub

---

## 협업 규칙
- 각자 로컬 MySQL DB에 독립적으로 테이블을 생성하고 실행
- DB 접속 정보는 개인 환경에 맞게 설정 (`.properties` 파일 → `.gitignore`에 포함)
- GitHub에는 소스 코드와 DB 스키마 SQL만 업로드
- 브랜치 전략: `main`(안정 버전), `feature/*`(기능 개발용)
>>>>>>> a7d7bfbac6d9629ac2509e5594ad6034692726b6

---

## 실행 방법
<<<<<<< HEAD

1. 아래 전체 SQL을 \*\*`library_schema.sql`\*\*로 저장
2. 터미널/명령프롬프트에서 실행:

```bash
# Windows / macOS / Linux (MySQL 클라이언트 설치되어 있어야 함)
mysql -u <사용자명> -p < library_schema.sql
```

3. 완료 후 간단 검증:

```sql
USE library_db;
SELECT COUNT(*) AS books FROM book;
SELECT COUNT(*) AS members FROM member;
SELECT * FROM v_current_loans LIMIT 10;   -- 현재 대여중 10건 확인
SELECT * FROM v_available_books LIMIT 10; -- 대여 가능 도서 확인
```

---

## 전체 SQL 스크립트 (복사-붙여넣기 실행)

```sql
-- =========================================================
-- Library DB (Simple Model) - MySQL 8.x
-- 요구사항: ISBN=PK, 저자 문자열, 장르 코드테이블, 대여 2주, 연장 없음, 동시대여 3권
-- 문자셋/콜레이션/엔진/타임존: utf8mb4 / utf8mb4_0900_ai_ci / InnoDB / Asia-Seoul
-- =========================================================

-- 깨끗한 재생성을 원하면 다음 두 줄 주석 해제
-- DROP DATABASE IF EXISTS library_db;
-- SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS library_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE library_db;

-- 세션 타임존(서버에 tz 테이블 설치 여부와 무관하게 +09:00 적용)
SET time_zone = '+09:00';

-- ======================================
-- 1) 테이블
-- ======================================

-- 장르 코드 테이블
CREATE TABLE IF NOT EXISTS genre (
  genre_code   VARCHAR(20) PRIMARY KEY,
  name_ko      VARCHAR(100) NOT NULL
) ENGINE=InnoDB;

-- 회원
CREATE TABLE IF NOT EXISTS member (
  member_id    INT AUTO_INCREMENT PRIMARY KEY,
  name         VARCHAR(100) NOT NULL,
  birth_date   DATE         NOT NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (birth_date <= CURRENT_DATE)
) ENGINE=InnoDB;

-- 도서(ISBN=PK, 단순형: 복본 없음, 현재 대여상태 보관)
CREATE TABLE IF NOT EXISTS book (
  isbn               VARCHAR(20) PRIMARY KEY,          -- ISBN-13 등 문자열 사용
  title              VARCHAR(255) NOT NULL,
  pub_date           DATE NULL,
  author             VARCHAR(200) NOT NULL,            -- 저자(문자열, 복수 저자는 쉼표 등 활용)
  genre_code         VARCHAR(20) NOT NULL,
  loan_status        ENUM('AVAILABLE','LOANED') NOT NULL DEFAULT 'AVAILABLE',
  current_member_id  INT NULL,
  loaned_at          DATETIME NULL,
  due_at             DATETIME NULL,
  CONSTRAINT fk_book_genre  FOREIGN KEY (genre_code) REFERENCES genre(genre_code),
  CONSTRAINT fk_book_member FOREIGN KEY (current_member_id) REFERENCES member(member_id),
  -- 상태와 컬럼의 논리 일관성(8.0.16+에서 유효)
  CHECK (
    (loan_status = 'AVAILABLE' AND current_member_id IS NULL AND loaned_at IS NULL AND due_at IS NULL)
    OR
    (loan_status = 'LOANED' AND current_member_id IS NOT NULL AND loaned_at IS NOT NULL AND due_at IS NOT NULL)
  )
) ENGINE=InnoDB;

-- 대여 이력(History) + 활성 대여(Active) 제약 보강
CREATE TABLE IF NOT EXISTS loan (
  loan_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  isbn        VARCHAR(20) NOT NULL,
  member_id   INT NOT NULL,
  loaned_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  due_at      DATETIME NOT NULL,
  returned_at DATETIME NULL,
  CONSTRAINT fk_loan_book   FOREIGN KEY (isbn)      REFERENCES book(isbn),
  CONSTRAINT fk_loan_member FOREIGN KEY (member_id) REFERENCES member(member_id),
  CHECK (due_at >= loaned_at),
  CHECK (returned_at IS NULL OR returned_at >= loaned_at)
) ENGINE=InnoDB;

-- "같은 책은 동시에 1건만 대여 가능" 제약:
-- returned_at IS NULL인 행만 고유성 검사되도록 생성열 + UNIQUE 인덱스 사용
-- (MySQL 8.0의 생성열/인덱스 동작 참조)
ALTER TABLE loan
  ADD COLUMN active_key VARCHAR(20)
    GENERATED ALWAYS AS (IF(returned_at IS NULL, isbn, NULL)) STORED,
  ADD UNIQUE KEY uq_active_loan_per_book (active_key);

-- 조회 최적화 인덱스
CREATE INDEX ix_loan_member_active ON loan (member_id, returned_at);
CREATE INDEX ix_book_genre ON book (genre_code);
CREATE INDEX ix_book_title ON book (title);

-- ======================================
-- 2) 트리거 (대여/반납 규칙 보강)
--    - INSERT loan: 책 가용성 확인, 회원 동시대여 3권 제한, 기본 due_at=loaned_at+14일
--    - INSERT 후: book 상태를 LOANED로 반영
--    - UPDATE loan: 반환 처리(한 번 반환하면 되돌릴 수 없음), 이후 book 상태 AVAILABLE 반영
-- ======================================

DELIMITER $$

-- 중복생성 방지: 기존 트리거 제거
DROP TRIGGER IF EXISTS trg_loan_bi $$
DROP TRIGGER IF EXISTS trg_loan_ai $$
DROP TRIGGER IF EXISTS trg_loan_bu $$
DROP TRIGGER IF EXISTS trg_loan_au $$

-- BEFORE INSERT ON loan
CREATE TRIGGER trg_loan_bi
BEFORE INSERT ON loan
FOR EACH ROW
BEGIN
  DECLARE active_cnt INT DEFAULT 0;
  DECLARE b_status ENUM('AVAILABLE','LOANED');

  -- 기본 대여기간 14일
  IF NEW.loaned_at IS NULL THEN
    SET NEW.loaned_at = NOW();
  END IF;
  IF NEW.due_at IS NULL THEN
    SET NEW.due_at = NEW.loaned_at + INTERVAL 14 DAY;
  END IF;

  -- 책 가용성 확인(행 잠금)
  SELECT loan_status INTO b_status
    FROM book
    WHERE isbn = NEW.isbn
    FOR UPDATE;

  IF b_status IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '대여 불가: 존재하지 않는 ISBN입니다.';
  END IF;

  IF b_status <> 'AVAILABLE' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '대여 불가: 도서가 이미 대여중입니다.';
  END IF;

  -- 회원 동시대여 3권 제한
  SELECT COUNT(*) INTO active_cnt
    FROM loan
    WHERE member_id = NEW.member_id
      AND returned_at IS NULL;

  IF active_cnt >= 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '대여 불가: 회원 동시대여 한도(3권) 초과';
  END IF;
END $$
 
-- AFTER INSERT ON loan  → book 상태 업데이트
CREATE TRIGGER trg_loan_ai
AFTER INSERT ON loan
FOR EACH ROW
BEGIN
  IF NEW.returned_at IS NULL THEN
    UPDATE book
      SET loan_status       = 'LOANED',
          current_member_id = NEW.member_id,
          loaned_at         = NEW.loaned_at,
          due_at            = NEW.due_at
      WHERE isbn = NEW.isbn;
  END IF;
END $$

-- BEFORE UPDATE ON loan  → 반환 일시 유효성/재대여 방지
CREATE TRIGGER trg_loan_bu
BEFORE UPDATE ON loan
FOR EACH ROW
BEGIN
  -- 이미 반환된 건을 다시 활성화하려는 시도 금지
  IF OLD.returned_at IS NOT NULL AND NEW.returned_at IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '반환된 대여건을 다시 활성화할 수 없습니다.';
  END IF;

  -- 반환 처리 시점 보정
  IF NEW.returned_at IS NOT NULL AND OLD.returned_at IS NULL THEN
    IF NEW.returned_at < OLD.loaned_at THEN
      SET NEW.returned_at = NOW();
    END IF;
  END IF;
END $$

-- AFTER UPDATE ON loan  → book 상태 AVAILABLE 반영
CREATE TRIGGER trg_loan_au
AFTER UPDATE ON loan
FOR EACH ROW
BEGIN
  IF NEW.returned_at IS NOT NULL AND OLD.returned_at IS NULL THEN
    UPDATE book
      SET loan_status       = 'AVAILABLE',
          current_member_id = NULL,
          loaned_at         = NULL,
          due_at            = NULL
      WHERE isbn = NEW.isbn;
  END IF;
END $$

DELIMITER ;

-- ======================================
-- 3) 뷰(View) - 운영 조회 편의
-- ======================================

CREATE OR REPLACE VIEW v_current_loans AS
SELECT
  l.loan_id,
  l.isbn,
  b.title,
  l.member_id,
  m.name AS member_name,
  l.loaned_at,
  l.due_at
FROM loan l
JOIN book b   ON b.isbn = l.isbn
JOIN member m ON m.member_id = l.member_id
WHERE l.returned_at IS NULL
ORDER BY l.loaned_at DESC;

CREATE OR REPLACE VIEW v_available_books AS
SELECT
  b.isbn, b.title, b.author, b.pub_date, b.genre_code
FROM book b
WHERE b.loan_status = 'AVAILABLE';

-- ======================================
-- 4) 시드 데이터(장르/회원/도서 100건, 대여중 10건)
--     - WITH RECURSIVE 로 1..100 시퀀스 생성하여 삽입
-- ======================================

-- 장르 10종
INSERT INTO genre (genre_code, name_ko) VALUES
  ('FICT','소설'),
  ('NFIC','비소설'),
  ('SCI','과학'),
  ('HIST','역사'),
  ('TECH','기술'),
  ('ART','예술'),
  ('EDU','교육'),
  ('BIOG','전기'),
  ('KIDS','아동'),
  ('COMP','컴퓨터')
ON DUPLICATE KEY UPDATE name_ko = VALUES(name_ko);

-- 회원 100명 (이름: 회원001~회원100, 생년월일: 1970~2010 범위 내 의사난수)
WITH RECURSIVE seq(n) AS (
  SELECT 1
  UNION ALL
  SELECT n+1 FROM seq WHERE n < 100
)
INSERT INTO member (name, birth_date)
SELECT
  CONCAT('회원', LPAD(n,3,'0')) AS name,
  -- 1970-01-01 기준 0~(40년) 사이 가중 의사난수 → 1970~2010 근사 분포
  DATE(ADDDATE('1970-01-01', FLOOR(RAND(n)* (40*365)))) AS birth_date
FROM seq;

-- 도서 100권 (ISBN: 13자리 형식 흉내 / 제목/저자/출판일/장르)
--  ISBN: '978000000' + LPAD(n,4,'0') → 13자리 보장 (예: 9780000000001)
WITH RECURSIVE seq2(n) AS (
  SELECT 1
  UNION ALL
  SELECT n+1 FROM seq2 WHERE n < 100
)
INSERT INTO book (isbn, title, pub_date, author, genre_code)
SELECT
  CONCAT('978000000', LPAD(n,4,'0'))                                     AS isbn,
  CONCAT('도서 ', LPAD(n,3,'0'))                                         AS title,
  DATE(ADDDATE('1995-01-01', FLOOR(RAND(n)* (30*365))))                  AS pub_date, -- 1995~2025 근사
  CONCAT('작가', LPAD(n,3,'0'))                                          AS author,
  ELT(1 + (n-1) % 10, 'FICT','NFIC','SCI','HIST','TECH','ART','EDU','BIOG','KIDS','COMP') AS genre_code
FROM seq2
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  pub_date = VALUES(pub_date),
  author = VALUES(author),
  genre_code = VALUES(genre_code);

-- 초기 대여중 10건(회원1~10이 도서1~10을 대여) → 트리거가 book 상태 LOANED로 반영
WITH RECURSIVE seq3(n) AS (
  SELECT 1
  UNION ALL
  SELECT n+1 FROM seq3 WHERE n < 10
)
INSERT INTO loan (isbn, member_id, loaned_at, due_at)
SELECT
  CONCAT('978000000', LPAD(n,4,'0')) AS isbn,
  n                                   AS member_id,
  NOW() - INTERVAL (n MOD 5) DAY      AS loaned_at,       -- 일부는 며칠 전 대여로 설정
  (NOW() - INTERVAL (n MOD 5) DAY) + INTERVAL 14 DAY      AS due_at
FROM seq3;

-- SET FOREIGN_KEY_CHECKS = 1;
-- 끝.
```

---

## 사용 팁(대여·반납 쿼리 예시)

* **대여(Borrow)**: `loan`에 새 행을 INSERT → 트리거가 `book` 상태를 `LOANED`로 동기화합니다.

```sql
-- 예: 회원 12가 ISBN '9780000000025'를 대여
INSERT INTO loan (isbn, member_id) VALUES ('9780000000025', 12);
```

* **반납(Return)**: 해당 `loan`의 `returned_at`을 채워 UPDATE → 트리거가 `book` 상태를 `AVAILABLE`로 되돌립니다.

```sql
-- 예: loan_id=123을 지금 반납 처리
UPDATE loan SET returned_at = NOW() WHERE loan_id = 123;
```

* **현재 대여 현황 보기**:

```sql
SELECT * FROM v_current_loans ORDER BY due_at;
```

---

## 근거·참고(References)

* **생성 열(Generated Columns)**: MySQL 8.0의 가상/저장 생성 열과 인덱싱 가능성 설명. 본 스키마의 `loan.active_key`는 “부분적 고유성”을 흉내 내기 위해 **저장 생성 열 + UNIQUE 인덱스**를 사용합니다. ([MySQL][2])
* **트리거(Triggers)**: BEFORE/AFTER, NEW/OLD 사용 및 동작 순서. 본 스키마에서는 대여/반납 규칙 강제 및 `book` 상태 동기화를 트리거로 구현했습니다. ([MySQL][3])
* **CHECK 제약(Constraints)**: MySQL 8.0.16부터 핵심 기능 도입·검사 동작. 본 스키마의 상태 일관성 및 날짜 제약에서 활용했습니다. ([MySQL][1])

---

필요 시, **DB 접속 사용자/권한 생성 스크립트**(예: `CREATE USER ...; GRANT ...;`)도 추가해 드릴 수 있습니다.
추가 요구사항(예: 연체료 계산, 반납 알림, 검색 최적화, FULLTEXT 인덱스 등)이 생기면 알려주세요.

[1]: https://dev.mysql.com/doc/search/?d=201&p=1&q=constraint&utm_source=chatgpt.com "MySQL 8.0 Reference Manual :: Search Results"
[2]: https://dev.mysql.com/doc/en/create-table-generated-columns.html?utm_source=chatgpt.com "15.1.20.8 CREATE TABLE and Generated Columns"
[3]: https://dev.mysql.com/doc/en/trigger-syntax.html?utm_source=chatgpt.com "27.3.1 Trigger Syntax and Examples"


=======
1. MySQL에 데이터베이스 생성:
   ```sql
   CREATE DATABASE library_db;
>>>>>>> a7d7bfbac6d9629ac2509e5594ad6034692726b6
