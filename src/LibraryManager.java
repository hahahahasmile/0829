import java.io.InputStream;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.Scanner;

public class LibraryManager {

    // ====== 설정 ======
    private static final String PROP_FILE = "db.properties"; // classpath에 두기 (예: src/main/resources)
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ====== 진입점 ======
    public static void main(String[] args) {
        try (Connection conn = getConnection();
             Scanner sc = new Scanner(System.in)) {

            System.out.println("===== [도서관리] =====");
            boolean running = true;
            while (running) {
                System.out.println("\n1) 도서등록  2) 도서삭제  3) 도서조회  4) 전체목록  0) 종료");
                System.out.print("선택> ");
                String sel = sc.nextLine().trim();

                switch (sel) {
                    case "1" -> menuAddBook(conn, sc);
                    case "2" -> menuDeleteBook(conn, sc);
                    case "3" -> menuQueryBook(conn, sc);
                    case "4" -> listAllBooks(conn);
                    case "0" -> running = false;
                    default -> System.out.println("올바른 메뉴를 선택하세요.");
                }
            }
            System.out.println("종료합니다.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ====== DB 연결 ======
    private static Connection getConnection() throws Exception {
        Properties props = new Properties();
        try (InputStream is = LibraryManager.class.getClassLoader().getResourceAsStream(PROP_FILE)) {
            if (is == null) {
                throw new IllegalStateException("환경파일("+PROP_FILE+")을 classpath에서 찾을 수 없습니다.");
            }
            props.load(is);
        }

        String url = props.getProperty("db.url");
        String user = props.getProperty("db.user");
        String password = props.getProperty("db.password");

        if (url == null || user == null) {
            throw new IllegalStateException("db.url / db.user / db.password 설정을 확인하세요.");
        }
        return DriverManager.getConnection(url, user, password);
    }

    // ====== 메뉴 동작 ======
    private static void menuAddBook(Connection conn, Scanner sc) {
        try {
            System.out.println("\n[도서등록]");
            System.out.print("ISBN(필수, 고유값): ");
            String isbn = sc.nextLine().trim();

            System.out.print("제목(필수): ");
            String title = sc.nextLine().trim();

            System.out.print("저자(필수): ");
            String author = sc.nextLine().trim();

            System.out.print("장르코드(예:FICT/NFIC/SCI/...): ");
            String genreCode = sc.nextLine().trim();

            System.out.print("출판일(YYYY-MM-DD, 생략 가능): ");
            String pub = sc.nextLine().trim();
            LocalDate pubDate = (pub.isEmpty() ? null : LocalDate.parse(pub, DF));

            // 유효성 & FK 확인
            if (isbn.isEmpty() || title.isEmpty() || author.isEmpty() || genreCode.isEmpty()) {
                System.out.println("필수 항목 누락입니다.");
                return;
            }
            if (!existsGenre(conn, genreCode)) {
                System.out.println("존재하지 않는 장르코드입니다. genre 테이블을 확인하세요.");
                return;
            }
            if (existsBook(conn, isbn)) {
                System.out.println("이미 존재하는 ISBN입니다.");
                return;
            }

            addBook(conn, isbn, title, pubDate, author, genreCode);
            System.out.println("등록 완료.");
        } catch (Exception e) {
            System.out.println("등록 실패: " + e.getMessage());
        }
    }

    private static void menuDeleteBook(Connection conn, Scanner sc) {
        try {
            System.out.println("\n[도서삭제]");
            System.out.print("삭제할 ISBN: ");
            String isbn = sc.nextLine().trim();

            if (!existsBook(conn, isbn)) {
                System.out.println("해당 ISBN의 도서가 없습니다.");
                return;
            }
            // 대여중이면 삭제 불가 (loan_status 확인)
            if (isBookLoaned(conn, isbn)) {
                System.out.println("현재 대여중(LOANED) 도서는 삭제할 수 없습니다.");
                return;
            }
            deleteBook(conn, isbn);
            System.out.println("삭제 완료.");
        } catch (Exception e) {
            System.out.println("삭제 실패: " + e.getMessage());
        }
    }

    private static void menuQueryBook(Connection conn, Scanner sc) {
        System.out.println("\n[도서조회]");
        System.out.println("1) ISBN으로 조회   2) 제목으로 조회(일부일치)");
        System.out.print("선택> ");
        String sel = sc.nextLine().trim();

        try {
            if ("1".equals(sel)) {
                System.out.print("ISBN: ");
                String isbn = sc.nextLine().trim();
                printBookByIsbn(conn, isbn);
            } else if ("2".equals(sel)) {
                System.out.print("제목 키워드: ");
                String keyword = sc.nextLine().trim();
                findBooksByTitle(conn, keyword);
            } else {
                System.out.println("올바른 메뉴를 선택하세요.");
            }
        } catch (Exception e) {
            System.out.println("조회 실패: " + e.getMessage());
        }
    }

    // ====== CRUD 구현 ======
    private static boolean existsGenre(Connection conn, String genreCode) throws SQLException {
        String sql = "SELECT 1 FROM genre WHERE genre_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, genreCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean existsBook(Connection conn, String isbn) throws SQLException {
        String sql = "SELECT 1 FROM book WHERE isbn = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean isBookLoaned(Connection conn, String isbn) throws SQLException {
        String sql = "SELECT loan_status FROM book WHERE isbn = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String status = rs.getString(1);
                    return "LOANED".equalsIgnoreCase(status);
                }
                return false;
            }
        }
    }

    private static void addBook(Connection conn, String isbn, String title,
                                LocalDate pubDate, String author, String genreCode) throws SQLException {
        String sql = """
            INSERT INTO book (isbn, title, pub_date, author, genre_code, loan_status, current_member_id, loaned_at, due_at)
            VALUES (?, ?, ?, ?, ?, 'AVAILABLE', NULL, NULL, NULL)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, isbn);
            ps.setString(2, title);
            if (pubDate == null) ps.setNull(3, Types.DATE);
            else ps.setDate(3, Date.valueOf(pubDate));
            ps.setString(4, author);
            ps.setString(5, genreCode);
            ps.executeUpdate();
        }
    }

    private static void deleteBook(Connection conn, String isbn) throws SQLException {
        // FK(loan, …) 제약에 막히지 않도록: 활성 대여가 없어야 함 (상단에서 이미 체크)
        String sql = "DELETE FROM book WHERE isbn = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, isbn);
            ps.executeUpdate();
        }
    }

    private static void printBookByIsbn(Connection conn, String isbn) throws SQLException {
        String sql = """
            SELECT b.isbn, b.title, b.author, b.pub_date, b.genre_code, g.name_ko AS genre_name,
                   b.loan_status, b.current_member_id, b.loaned_at, b.due_at
              FROM book b
              JOIN genre g ON g.genre_code = b.genre_code
             WHERE b.isbn = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("해당 ISBN의 도서가 없습니다.");
                    return;
                }
                printBookRow(rs);
            }
        }
    }

    private static void findBooksByTitle(Connection conn, String keyword) throws SQLException {
        String sql = """
            SELECT b.isbn, b.title, b.author, b.pub_date, b.genre_code, g.name_ko AS genre_name,
                   b.loan_status, b.current_member_id, b.loaned_at, b.due_at
              FROM book b
              JOIN genre g ON g.genre_code = b.genre_code
             WHERE b.title LIKE CONCAT('%', ?, '%')
             ORDER BY b.title
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, keyword);
            try (ResultSet rs = ps.executeQuery()) {
                int cnt = 0;
                while (rs.next()) {
                    printBookRow(rs);
                    cnt++;
                }
                if (cnt == 0) System.out.println("검색 결과가 없습니다.");
            }
        }
    }

    private static void listAllBooks(Connection conn) throws SQLException {
        String sql = """
            SELECT b.isbn, b.title, b.author, b.pub_date, b.genre_code, g.name_ko AS genre_name,
                   b.loan_status, b.current_member_id, b.loaned_at, b.due_at
              FROM book b
              JOIN genre g ON g.genre_code = b.genre_code
             ORDER BY b.title
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            int cnt = 0;
            while (rs.next()) {
                printBookRow(rs);
                cnt++;
            }
            System.out.println("(총 " + cnt + "권)");
        }
    }

    private static void printBookRow(ResultSet rs) throws SQLException {
        String isbn = rs.getString("isbn");
        String title = rs.getString("title");
        String author = rs.getString("author");
        Date pub = rs.getDate("pub_date");
        String genreCode = rs.getString("genre_code");
        String genreName = rs.getString("genre_name");
        String status = rs.getString("loan_status");
        Integer memberId = (Integer) rs.getObject("current_member_id"); // nullable
        Timestamp loanedAt = rs.getTimestamp("loaned_at");
        Timestamp dueAt = rs.getTimestamp("due_at");

        System.out.println("------------------------------------------------------------");
        System.out.println("ISBN        : " + isbn);
        System.out.println("제목        : " + title);
        System.out.println("저자        : " + author);
        System.out.println("출판일      : " + (pub == null ? "-" : pub.toLocalDate()));
        System.out.println("장르        : " + genreCode + " (" + genreName + ")");
        System.out.println("대여상태    : " + status);
        System.out.println("대여회원ID  : " + (memberId == null ? "-" : memberId));
        System.out.println("대여일시    : " + (loanedAt == null ? "-" : loanedAt.toLocalDateTime()));
        System.out.println("반납예정일  : " + (dueAt == null ? "-" : dueAt.toLocalDateTime()));
    }
}
