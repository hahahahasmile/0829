
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        LoanBookService loanService = new LoanBookService();
        ReturnBookService returnService = new ReturnBookService();

        while (true) {
            System.out.println("\n--- 도서 관리 시스템 ---");
            System.out.println("1. 도서 대출");
            System.out.println("2. 도서 반납");
            System.out.println("3. 도서 관리(등록/삭제/조회)");
            System.out.println("0. 종료");
            System.out.print("메뉴를 선택하세요: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    System.out.print("대출할 도서의 ISBN을 입력하세요: ");
                    String loanIsbn = scanner.nextLine();
                    System.out.print("회원 ID를 입력하세요: ");
                    try {
                        int loanMemberId = Integer.parseInt(scanner.nextLine());
                        loanService.loanBook(loanIsbn, loanMemberId);
                    } catch (NumberFormatException e) {
                        System.out.println("회원 ID는 숫자로 입력해주세요.");
                    }
                    break;

                case "2":
                    System.out.print("반납할 도서의 ISBN을 입력하세요: ");
                    String returnIsbn = scanner.nextLine();
                    System.out.print("회원 ID를 입력하세요: ");
                    try {
                        int returnMemberId = Integer.parseInt(scanner.nextLine());
                        returnService.returnBook(returnIsbn, returnMemberId);
                    } catch (NumberFormatException e) {
                        System.out.println("회원 ID는 숫자로 입력해주세요.");
                    }
                    break;

                case "3":
                    // ▶ 여기서 도서관리 서브메뉴로 진입
                    LibraryManager.runInteractive();
                    break;

                case "0":
                    System.out.println("시스템을 종료합니다.");
                    scanner.close();
                    return;

                default:
                    System.out.println("잘못된 입력입니다. 다시 시도해주세요.");
                    break;
            }
        }
    }
}
