package feedlab.service.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationRunRepository extends JpaRepository<SimulationRun, UUID> {

    List<SimulationRun> findAllByOrderByCreatedAtDesc();

    List<SimulationRun> findByStatusIn(Collection<RunStatus> statuses);
}
