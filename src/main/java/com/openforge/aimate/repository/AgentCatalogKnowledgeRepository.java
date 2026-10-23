package com.openforge.aimate.repository;

import com.openforge.aimate.domain.AgentCatalogKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Collection;
import java.util.List;

public interface AgentCatalogKnowledgeRepository extends JpaRepository<AgentCatalogKnowledge, Long> {
    List<AgentCatalogKnowledge> findByCatalogIdOrderByIdAsc(Long catalogId);

    List<AgentCatalogKnowledge> findByCatalogIdIn(Collection<Long> catalogIds);

    @Modifying(clearAutomatically = true)
    void deleteByCatalogId(Long catalogId);

    long countByKnowledgeBaseId(Long knowledgeBaseId);
}
