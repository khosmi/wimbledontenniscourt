package wimbledontenniscourt;

import wimbledontenniscourt.config.kafka.KafkaProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class MycourtViewHandler {


    @Autowired
    private MycourtRepository mycourtRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whenReserved_then_CREATE_1 (@Payload Reserved reserved) {
        try {

            if (!reserved.validate()) return;

            // view 객체 생성
            Mycourt mycourt = new Mycourt();
            // view 객체에 이벤트의 Value 를 set 함
            mycourt.setReservationId(reserved.getId());
            mycourt.setCourtName(reserved.getCourtName());
            mycourt.setPlayerName(reserved.getPlayerName());
            mycourt.setTime(reserved.getTime());
            mycourt.setStatus(reserved.getStatus());
            // view 레파지 토리에 save
            mycourtRepository.save(mycourt);

        }catch (Exception e){
            e.printStackTrace();
        }
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whenApproved_then_UPDATE_1(@Payload Approved approved) {
        try {
            if (!approved.validate()) return;
                // view 객체 조회
                System.out.println("\n\n##### listener UpdateCourt view handler : " + approved.toJson() + "\n\n");

                    List<Mycourt> mycourtList = mycourtRepository.findByReservationId(approved.getReservationId());
                    for(Mycourt mycourt : mycourtList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    mycourt.setApprovalId(approved.getId());
                    mycourt.setStatus(approved.getStatus());
                // view 레파지 토리에 save
                mycourtRepository.save(mycourt);
                }

        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenCancledReservation_then_UPDATE_2(@Payload CancledReservation cancledReservation) {
        try {
            if (!cancledReservation.validate()) return;
                // view 객체 조회

                    List<Mycourt> mycourtList = mycourtRepository.findByReservationId(cancledReservation.getId());
                    for(Mycourt mycourt : mycourtList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    mycourt.setStatus(cancledReservation.getStatus());
                // view 레파지 토리에 save
                mycourtRepository.save(mycourt);
                }

        }catch (Exception e){
            e.printStackTrace();
        }
    }

}

