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

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverReserved_UpdateCourt(@Payload Reserved reserved){

        if(!reserved.validate()) return;

        System.out.println("\n\n##### listener UpdateCourt : " + reserved.toJson() + "\n\n");



        // Sample Logic //

    }
    /*
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverApproved_UpdateCourt(@Payload Approved approved){

        if(!approved.validate()) return;

        System.out.println("\n\n##### listener UpdateCourt policy handler: " + approved.toJson() + "\n\n");



        // Sample Logic //

    }
    */
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverCancledReservation_UpdateCourt(@Payload CancledReservation cancledReservation){

        if(!cancledReservation.validate()) return;

        System.out.println("\n\n##### listener UpdateCourt : " + cancledReservation.toJson() + "\n\n");



        // Sample Logic //

    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverCancledApproval_UpdateCourt(@Payload CancledApproval cancledApproval){

        if(!cancledApproval.validate()) return;

        System.out.println("\n\n##### listener UpdateCourt : " + cancledApproval.toJson() + "\n\n");



        // Sample Logic //

    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString){}


}