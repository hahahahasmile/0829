
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class ReturnBookService {

    /**
     * 도서 반납 로직
     * @param isbn 반납할 도서의 ISBN
     * @param memberId 대출했던 회원의 ID
     */
    public void returnBook(String isbn, int memberId) {
        String updateReturnSql = "UPDATE loan SET returned_at = ? WHERE isbn = ? AND member_id = ? AND returned_at IS NULL LIMIT 1";

        String currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        Connection conn = null;

        try {
            conn = DatabaseManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(updateReturnSql)) {
                pstmt.setString(1, currentDate);
                pstmt.setString(2, isbn);
                pstmt.setInt(3, memberId);

                int affectedRows = pstmt.executeUpdate();

                if (affectedRows > 0) {
                    System.out.println("도서가 성공적으로 반납되었습니다.");
                } else {
                    System.out.println("유효한 대출 기록을 찾을 수 없습니다. (ISBN 또는 회원 ID 확인 필요)");
                }
            }
        } catch (SQLException e) {
            System.err.println("도서 반납 실패: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("연결 종료 실패: " + e.getMessage());
                }
            }
        }
    }
}
