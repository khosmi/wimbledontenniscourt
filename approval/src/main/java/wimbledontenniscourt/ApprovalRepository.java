package wimbledontenniscourt;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(collectionResourceRel="approvals", path="approvals")
public interface ApprovalRepository extends PagingAndSortingRepository<Approval, Long>{


}
