package wimbledontenniscourt;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.Date;

@Entity
@Table(name="Reservation_table")
public class Reservation {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String courtName;
    private String playerName;
    private String time;
    private String status;

    @PostPersist
    public void onPostPersist(){
        Reserved reserved = new Reserved();
        BeanUtils.copyProperties(this, reserved);
        reserved.publishAfterCommit();

        wimbledontenniscourt.external.Approval approval = new wimbledontenniscourt.external.Approval();
        ReservationApplication.applicationContext.getBean(wimbledontenniscourt.external.ApprovalService.class)
            .createApproval(approval);

    }
    @PreUpdate
    public void onPreUpdate(){
        CancledReservation cancledReservation = new CancledReservation();
        BeanUtils.copyProperties(this, cancledReservation);
        cancledReservation.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        wimbledontenniscourt.external.Approval approval = new wimbledontenniscourt.external.Approval();
        // mappings goes here
        ReservationApplication.applicationContext.getBean(wimbledontenniscourt.external.ApprovalService.class)
            //.cancelApproval(approval);
            //.cancelApproval(this.getId(), approval);
            .cancelApproval(this.getId());

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getCourtName() {
        return courtName;
    }

    public void setCourtName(String courtName) {
        this.courtName = courtName;
    }
    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }




}