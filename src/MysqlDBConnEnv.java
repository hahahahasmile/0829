import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.time.LocalDate;
import java.util.Properties;
import java.util.Scanner;

public class MysqlDBConnEnv {

    // ----- main: 콘솔에서 입력 받아 도서 등록 -----
    public static void main(String[] args) {
        Properties props = new Properties();

        try (InputStream is = MysqlDBConnEnv.class.getClassLoader()
                .getResourceAsStream("db.properties")) {

            if (is == null) {
                System.err.println("환경파일을 찾을 수 없습니다. (classpath: db.properties)");
                return;
            }
            props.load(is);
        } catch (IOException e) {
            System.err.println("환경파일 로드 실패: " + e.getMessage());
            return;
        }

        try (Connection conn = getConnection(props);
             Scanner sc = new Scanner(System.in)) {

            System.out.println("MySQL 연결 성공! 도서 등록을 시작합니다.\n");

            // --- 콘솔 입력 ---
            System.out.print("책번호(PK, 정수): ");
            int bookId = Integer.parseInt(sc.nextLine().trim());

            System.out.print("책이름: ");
            String title = sc.nextLine().trim();

            System.out.print("등록일(YYYY-MM-DD, 비우면 오늘): ");
            String dateStr = sc.nextLine().trim();
            LocalDate regDate = dateStr.isEmpty() ? LocalDate.now() : LocalDate.parse(dateStr);

            System.out.print("개수(정수): ");
            int quantity = Integer.parseInt(sc.nextLine().trim());

            System.out.print("장르명(예: 소설, 역사, IT): ");
            String genreName = sc.nextLine().trim();

            // --- 트랜잭션 시작 ---
            conn.setAutoCommit(false);
            try {
                int genreId = ensureGenre(conn, genreName);
                registerBook(conn, bookId, title, regDate, quantity, genreId);
                conn.commit();
                System.out.println("\n✅ 도서 등록이 완료되었습니다.");

                // 확인용 단건 조회
                System.out.println("\n[등록 결과 확인]");
                printBookById(conn, bookId);

            } catch (SQLIntegrityConstraintViolationException dup) {
                conn.rollback();
                System.err.println("❌ 등록 실패: 이미 존재하는 책번호(PK)입니다. (book_id=" + dup.getMessage() + ")");
            } catch (Exception ex) {
                conn.rollback();
                System.err.println("❌ 등록 실패: " + ex.getMessage());
                ex.printStackTrace();
            } finally {
                conn.setAutoCommit(true);
            }

            System.out.println("\nMySQL 연결 종료.");

        } catch (SQLException e) {
            System.err.println("MySQL 연결/쿼리 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ----- DB 연결 -----
    private static Connection getConnection(Properties props) throws SQLException {
        String url = props.getProperty("db.url");
        String user = props.getProperty("db.user");
        String password = props.getProperty("db.password");
        return DriverManager.getConnection(url, user, password);
    }

    // ----- 장르 보장: 없으면 생성 후 genre_id 반환 -----
    private static int ensureGenre(Connection conn, String genreName) throws SQLException {
        // 1) 기존 장르 찾기
        String findSql = "SELECT genre_id FROM genre WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setString(1, genreName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("genre_id");
                }
            }
        }

        // 2) 없으면 추가
        String insertSql = "INSERT INTO genre(name) VALUES(?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, genreName);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }

        // 혹시 AUTO_INCREMENT가 없거나 반환이 안 될 때 재조회
        try (PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setString(1, genreName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("genre_id");
            }
        }
        throw new SQLException("장르 생성 실패: " + genreName);
    }

    // ----- 도서 등록 -----
    private static void registerBook(Connection conn, int bookId, String title,
                                     LocalDate regDate, int quantity, int genreId) throws SQLException {
        String sql =
                "INSERT INTO book (book_id, title, reg_date, quantity, genre_id) " +
                        "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            ps.setString(2, title);
            ps.setObject(3, regDate);      // LocalDate → DATE
            ps.setInt(4, quantity);
            ps.setInt(5, genreId);
            ps.executeUpdate();
        }
    }

    // ----- 단건 조회(확인용) -----
    private static void printBookById(Connection conn, int bookId) throws SQLException {
        String sql =
                "SELECT b.book_id, b.title, b.reg_date, b.quantity, g.name AS genre_name " +
                        "FROM book b JOIN genre g ON b.genre_id = g.genre_id " +
                        "WHERE b.book_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.printf("book_id=%d | 제목=%s | 등록일=%s | 개수=%d | 장르=%s%n",
                            rs.getInt("book_id"),
                            rs.getString("title"),
                            rs.getDate("reg_date").toLocalDate(),
                            rs.getInt("quantity"),
                            rs.getString("genre_name"));
                } else {
                    System.out.println("해당 book_id가 없습니다: " + bookId);
                }
            }
        }
    }
}
