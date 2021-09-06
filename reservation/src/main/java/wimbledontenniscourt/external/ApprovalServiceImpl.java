package wimbledontenniscourt.external;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ApprovalServiceImpl implements ApprovalService {
        public void cancelApproval(long id){
        //public void cancelApproval(Approval approval){
        //public void cancelApproval(Long id, Approval approval){
            System.out.println("\n\n ######  승인서비스 지연중입니다.    #######");
            System.out.println("\n\n ######  잠심 뒤에 다시 시도해주세요.#######");
            System.out.println("\n\n ######  승인서비스 지연중입니다.    #######");
        }
}
