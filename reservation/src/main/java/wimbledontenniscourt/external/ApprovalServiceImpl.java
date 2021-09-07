package wimbledontenniscourt.external;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ApprovalServiceImpl implements ApprovalService {
        public void cancelApproval(long id){
            System.out.println("\n\n ######  승인서비스 지연중입니다.    #######");
            System.out.println("######  잠시 뒤에 다시 시도해주세요.#######");
            System.out.println("######  승인서비스 지연중입니다.    #######\n\n");
        }

        public void createApproval(Approval approval){
            System.out.println("\n\n ######  승인서비스 지연중입니다.    #######");
            System.out.println("######  잠시 뒤에 다시 시도해주세요.#######");
            System.out.println("######  승인서비스 지연중입니다.    #######\n\n");
        }
}
