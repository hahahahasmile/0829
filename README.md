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

---

## 실행 방법
1. MySQL에 데이터베이스 생성:
   ```sql
   CREATE DATABASE library_db;
