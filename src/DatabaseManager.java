
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseManager {
    private static final String DRIVER_NAME = "com.mysql.cj.jdbc.Driver";
    private static Properties props = new Properties();

    static {
        try {
            Class.forName(DRIVER_NAME);
            System.out.println("JDBC 드라이버 로드 성공");

            try (InputStream input = DatabaseManager.class.getClassLoader().getResourceAsStream("db.properties")) {
                if (input == null) {
                    // db.properties 파일을 찾을 수 없으면 RuntimeException을 던져서 프로그램 종료
                    throw new RuntimeException("db.properties 파일을 찾을 수 없습니다.");
                }
                props.load(input);
            }
        } catch (ClassNotFoundException e) {
            System.err.println("JDBC 드라이버를 찾을 수 없습니다: " + e.getMessage());
            throw new RuntimeException("JDBC 드라이버 로드 실패", e);
        } catch (IOException e) {
            System.err.println("db.properties 파일 로드 실패: " + e.getMessage());
            throw new RuntimeException("db.properties 파일 로드 실패", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        String url = props.getProperty("db.url");
        String user = props.getProperty("db.user");
        String password = props.getProperty("db.password");

        if (url == null || user == null || password == null) {
            throw new SQLException("데이터베이스 연결 정보(URL, 사용자, 비밀번호)가 누락되었습니다.");
        }

        return DriverManager.getConnection(url, user, password);
    }
}