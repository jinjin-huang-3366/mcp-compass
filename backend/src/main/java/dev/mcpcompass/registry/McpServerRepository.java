package dev.mcpcompass.registry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface McpServerRepository extends JpaRepository<McpServerEntity, UUID>, JpaSpecificationExecutor<McpServerEntity> {
    Optional<McpServerEntity> findByRegistryName(String registryName);

    @Query("select s from McpServerEntity s where s.remoteCount > 0 and s.status <> 'deleted' "
            + "and (s.remoteConnectionCheckedAt is null or s.remoteConnectionCheckedAt < :before) "
            + "order by s.remoteConnectionCheckedAt asc, s.registryName asc")
    List<McpServerEntity> findRemoteChecksDue(Instant before, Pageable pageable);
}
