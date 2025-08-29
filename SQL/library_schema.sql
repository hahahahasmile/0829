-- =========================================================
-- Library DB (Simple Model) - Fixed for MySQL 8.x
-- - ISBN = PK
-- - 저자 문자열, 장르 코드테이블
-- - 대여 2주, 연장 없음, 동시대여 3권
-- - utf8mb4 / InnoDB / Asia-Seoul
-- =========================================================

/*!50503 SET NAMES utf8mb4 */;
SET time_zone = '+09:00';

-- 깨끗한 재생성을 원할 때만 다음 줄 주석 해제
-- DROP DATABASE IF EXISTS library_db;

CREATE DATABASE IF NOT EXISTS library_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE library_db;

-- 뷰/트리거/테이블 정리(존재 시만)
DROP VIEW IF EXISTS v_current_loans;
DROP VIEW IF EXISTS v_available_books;

DELIMITER $$ 
DROP TRIGGER IF EXISTS trg_loan_bi $$
DROP TRIGGER IF EXISTS trg_loan_ai $$
DROP TRIGGER IF EXISTS trg_loan_bu $$
DROP TRIGGER IF EXISTS trg_loan_au $$
DELIMITER ;

DROP TABLE IF EXISTS loan;
DROP TABLE IF EXISTS book;
DROP TABLE IF EXISTS member;
DROP TABLE IF EXISTS genre;

-- ======================================
-- 1) 테이블
-- ======================================

CREATE TABLE genre (
  genre_code   VARCHAR(20) PRIMARY KEY,
  name_ko      VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE member (
  member_id    INT AUTO_INCREMENT PRIMARY KEY,
  name         VARCHAR(100) NOT NULL,
  birth_date   DATE         NOT NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
  -- CHECK 제약에서 CURDATE/CURRENT_DATE는 허용되지 않으므로 생략
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE book (
  isbn               VARCHAR(20) PRIMARY KEY,
  title              VARCHAR(255) NOT NULL,
  pub_date           DATE NULL,
  author             VARCHAR(200) NOT NULL,
  genre_code         VARCHAR(20) NOT NULL,
  loan_status        ENUM('AVAILABLE','LOANED') NOT NULL DEFAULT 'AVAILABLE',
  current_member_id  INT NULL,
  loaned_at          DATETIME NULL,
  due_at             DATETIME NULL,
  CONSTRAINT fk_book_genre  FOREIGN KEY (genre_code) REFERENCES genre(genre_code),
  CONSTRAINT fk_book_member FOREIGN KEY (current_member_id) REFERENCES member(member_id)
  -- 상태 일관성 CHECK는 환경에 따라 제한될 수 있어 트리거/애플리케이션에서 보강
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- loan: 생성열 + UNIQUE로 "동시 1대여/권" 보장
CREATE TABLE loan (
  loan_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  isbn        VARCHAR(20) NOT NULL,
  member_id   INT NOT NULL,
  loaned_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  due_at      DATETIME NOT NULL,
  returned_at DATETIME NULL,
  -- 활성 대여만 유니크 키 대상이 되도록 생성열 사용
  active_key  VARCHAR(20)
    GENERATED ALWAYS AS (IF(returned_at IS NULL, isbn, NULL)) STORED,
  CONSTRAINT fk_loan_book   FOREIGN KEY (isbn)      REFERENCES book(isbn),
  CONSTRAINT fk_loan_member FOREIGN KEY (member_id) REFERENCES member(member_id),
  CHECK (due_at >= loaned_at),
  CHECK (returned_at IS NULL OR returned_at >= loaned_at),
  UNIQUE KEY uq_active_loan_per_book (active_key),
  INDEX ix_loan_member_active (member_id, returned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_book_genre ON book (genre_code);
CREATE INDEX ix_book_title ON book (title);

-- ======================================
-- 2) 트리거 (대여/반납 규칙)
-- ======================================

DELIMITER $$

CREATE TRIGGER trg_loan_bi
BEFORE INSERT ON loan
FOR EACH ROW
BEGIN
  DECLARE active_cnt INT DEFAULT 0;
  DECLARE b_status VARCHAR(100);

  -- 기본 대여기간 14일 자동 설정
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
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '대여 불가: 존재하지 않는 ISBN';
  END IF;

  IF b_status <> 'AVAILABLE' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '대여 불가: 이미 대여중';
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

CREATE TRIGGER trg_loan_bu
BEFORE UPDATE ON loan
FOR EACH ROW
BEGIN
  -- 이미 반환된 건을 다시 활성화 금지
  IF OLD.returned_at IS NOT NULL AND NEW.returned_at IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '반환된 대여건을 재활성화할 수 없음';
  END IF;

  -- 반환 시점 보정
  IF NEW.returned_at IS NOT NULL AND OLD.returned_at IS NULL THEN
    IF NEW.returned_at < OLD.loaned_at THEN
      SET NEW.returned_at = NOW();
    END IF;
  END IF;
END $$

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
-- 3) 뷰(View)
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
-- 4) 시드 데이터
--    - CTE 대신 루프 프로시저를 사용(호환성↑)
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

-- 회원 100명 시드: 회원001~회원100, 1970~2010 범위에서 임의 생년월일
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_seed_members $$
CREATE PROCEDURE sp_seed_members()
BEGIN
  DECLARE i INT DEFAULT 1;
  WHILE i <= 100 DO
    INSERT INTO member (name, birth_date)
    VALUES (
      CONCAT('회원', LPAD(i,3,'0')),
      DATE(ADDDATE('1970-01-01', FLOOR(RAND(i) * (40*365))))
    );
    SET i = i + 1;
  END WHILE;
END $$
DELIMITER ;
CALL sp_seed_members();

-- 도서 100권 시드: ISBN(13자리 흉내), 도서 001~100, 작가001~100, 1995~2025 근사
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_seed_books $$
CREATE PROCEDURE sp_seed_books()
BEGIN
  DECLARE i INT DEFAULT 1;
  WHILE i <= 100 DO
    INSERT INTO book (isbn, title, pub_date, author, genre_code)
    VALUES (
      CONCAT('978000000', LPAD(i,4,'0')),
      CONCAT('도서 ', LPAD(i,3,'0')),
      DATE(ADDDATE('1995-01-01', FLOOR(RAND(i+100) * (30*365)))),
      CONCAT('작가', LPAD(i,3,'0')),
      CASE ((i-1) % 10)
        WHEN 0 THEN 'FICT'
        WHEN 1 THEN 'NFIC'
        WHEN 2 THEN 'SCI'
        WHEN 3 THEN 'HIST'
        WHEN 4 THEN 'TECH'
        WHEN 5 THEN 'ART'
        WHEN 6 THEN 'EDU'
        WHEN 7 THEN 'BIOG'
        WHEN 8 THEN 'KIDS'
        ELSE     'COMP'
      END
    );
    SET i = i + 1;
  END WHILE;
END $$
DELIMITER ;
CALL sp_seed_books();

-- 초기 대여중 10건: 회원1~10이 도서1~10을 대여
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_seed_loans $$
CREATE PROCEDURE sp_seed_loans()
BEGIN
  DECLARE i INT DEFAULT 1;
  WHILE i <= 10 DO
    INSERT INTO loan (isbn, member_id, loaned_at, due_at)
    VALUES (
      CONCAT('978000000', LPAD(i,4,'0')),
      i,
      NOW() - INTERVAL (i MOD 5) DAY,
      (NOW() - INTERVAL (i MOD 5) DAY) + INTERVAL 14 DAY
    );
    SET i = i + 1;
  END WHILE;
END $$
DELIMITER ;
CALL sp_seed_loans();

-- (원하면) 프로시저 정리
DROP PROCEDURE IF EXISTS sp_seed_members;
DROP PROCEDURE IF EXISTS sp_seed_books;
DROP PROCEDURE IF EXISTS sp_seed_loans;

-- 끝.
