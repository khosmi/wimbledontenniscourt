package wimbledontenniscourt;

import wimbledontenniscourt.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
    @Autowired ApprovalRepository approvalRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverReserved_Receive(@Payload Reserved reserved){

        if(!reserved.validate()) return;

        System.out.println("\n\n##### listener Receive : " + reserved.toJson() + "\n\n");

        // Sample Logic //
        Approval approval = new Approval();
        approval.setCourtName(reserved.getCourtName());
        approval.setPlayerName(reserved.getPlayerName());
        approval.setReservationId(reserved.getId());
        approval.setTime(reserved.getTime());
        approval.setStatus(reserved.getStatus());
        //approvalRepository.save(approval);

    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString){}


}