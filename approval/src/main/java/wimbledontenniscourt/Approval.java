package wimbledontenniscourt;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.Date;

@Entity
@Table(name="Approval_table")
public class Approval {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String courtName;
    private String playerName;
    private String time;
    private String status;
    private Long reservationId;

    @PostPersist
    public void onPostPersist(){
    }
    @PostUpdate
    public void onPostUpdate(){

        System.out.println("\n\n##### STATUS : "+this.getStatus()+"\n\n");
        if (this.getStatus().equals("approved")){
            Approved approved = new Approved();
            BeanUtils.copyProperties(this, approved);
            approved.publishAfterCommit();
            System.out.println("\n\n##### Approved Created : " + approved.toJson() + "\n\n");
        }else if (this.getStatus().equals("cancled reservation")){
            CancledApproval cancledApproval = new CancledApproval();
            BeanUtils.copyProperties(this, cancledApproval);
            cancledApproval.publishAfterCommit();
            System.out.println("\n\n##### Approval Cancled : " + cancledApproval.toJson() + "\n\n");
        }else{
            System.out.println("\n\n##### STATUS IS NOT ACCEPTABLE!! : " + this.getStatus() + "\n\n");
        }

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
    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }




}