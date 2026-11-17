package feedlab.service.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimelineSecondRepository extends JpaRepository<TimelineSecond, Long> {

    List<TimelineSecond> findByRunIdOrderBySecond(UUID runId);
}
