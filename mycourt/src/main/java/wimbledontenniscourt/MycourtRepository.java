package wimbledontenniscourt;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MycourtRepository extends CrudRepository<Mycourt, Long> {

    List<Mycourt> findByReservationId(Long reservationId);
    //List<Mycourt> findByReservationId(String reservationId);

}