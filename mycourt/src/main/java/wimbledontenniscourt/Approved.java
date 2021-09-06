package wimbledontenniscourt;

public class Approved extends AbstractEvent {

    private Long id;
    private String court_name;
    private String player_name;
    private String time;
    private String status;
    private Long reservation_id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getCourtName() {
        return court_name;
    }

    public void setCourtName(String court_name) {
        this.court_name = court_name;
    }
    public String getPlayerName() {
        return player_name;
    }

    public void setPlayerName(String player_name) {
        this.player_name = player_name;
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
        return reservation_id;
    }

    public void setReservationId(Long reservation_id) {
        this.reservation_id = reservation_id;
    }
}