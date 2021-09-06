package wimbledontenniscourt;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name="Mycourt_table")
public class Mycourt {

        @Id
        @GeneratedValue(strategy=GenerationType.AUTO)
        private Long id;
        private Long reservationId;
        private Long approvalId;
        private String courtName;
        private String playerName;
        private String time;
        private String status;


        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
        public Long getReservationId() {
            return reservationId;
        }

        public void setReservationId(Long reservationId) {
            this.reservationId = reservationId;
        }
        public Long getApprovalId() {
            return approvalId;
        }

        public void setApprovalId(Long approvalId) {
            this.approvalId = approvalId;
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