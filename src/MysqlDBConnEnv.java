import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.Properties;

public class MysqlDBConnEnv {
    public static void main(String[] args) {
        Properties props = new Properties();

        // classpath에서 db.properties 로드 (권장 위치: src/main/resources)
        try (InputStream is = MysqlDBConnEnv.class.getClassLoader()
                .getResourceAsStream("db.properties")) {

            if (is == null) {
                System.err.println("환경파일을 찾을 수 없습니다. (classpath: db.properties)");
                return;
            }
            props.load(is);

            String url = props.getProperty("db.url");
            String user = props.getProperty("db.user");
            String password = props.getProperty("db.password");

            String sql = """
                SELECT
                    AccountID,
                    CustomerSSN,
                    AccountType,
                    AccountBalance,
                    CardApplyStatus,
                    AccountOpenDate,
                    AccountHolderName,
                    AccountPhone,
                    AccountEmail
                FROM account
            """;

            try (Connection conn = DriverManager.getConnection(url, user, password);
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                System.out.println("MySQL 연결 성공!");

                while (rs.next()) {
                    int accountId = rs.getInt("AccountID");
                    String ssn = rs.getString("CustomerSSN");
                    String type = rs.getString("AccountType");
                    BigDecimal balance = rs.getBigDecimal("AccountBalance");
                    boolean cardApplied = rs.getBoolean("CardApplyStatus"); // tinyint(1) → boolean
                    LocalDate openDate = rs.getObject("AccountOpenDate", LocalDate.class);
                    String holder = rs.getString("AccountHolderName");
                    String phone = rs.getString("AccountPhone");
                    String email = rs.getString("AccountEmail");

                    System.out.printf(
                            "%d | %s | %s | %s | %b | %s | %s | %s | %s%n",
                            accountId, ssn, type, balance, cardApplied, openDate, holder, phone, email
                    );
                }

                System.out.println("MySQL 연결 종료.");
            }

        } catch (IOException e) {
            System.err.println("환경파일 로드 실패: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("MySQL 연결/쿼리 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
