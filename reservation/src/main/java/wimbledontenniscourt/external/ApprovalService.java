package wimbledontenniscourt.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="approval", url="${api.url.pay}", fallback=ApprovalServiceImpl.class)
//@FeignClient(name="approval", url="${api.url.pay}")
public interface ApprovalService {
    @RequestMapping(method= RequestMethod.DELETE, path="/approvals/{id}")
    public void cancelApproval(@PathVariable long id);

}

