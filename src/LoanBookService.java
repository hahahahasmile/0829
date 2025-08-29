
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class LoanBookService {

    /**
     * 도서 대출 로직
     * @param isbn 대출할 도서의 ISBN
     * @param memberId 대출하는 회원의 ID
     */
    public void loanBook(String isbn, int memberId) {
        String insertLoanSql = "INSERT INTO loan (isbn, member_id) VALUES (?, ?)";
        Connection conn = null;

        try {
            conn = DatabaseManager.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(insertLoanSql)) {
                pstmt.setString(1, isbn);
                pstmt.setInt(2, memberId);

                pstmt.executeUpdate();
                System.out.println("도서 대출이 성공적으로 처리되었습니다.");
            }
        } catch (SQLException e) {
            System.err.println("도서 대출 실패: " + e.getMessage());
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
