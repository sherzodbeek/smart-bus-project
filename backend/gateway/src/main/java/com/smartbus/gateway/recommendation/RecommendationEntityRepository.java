package com.smartbus.gateway.recommendation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationEntityRepository extends JpaRepository<RecommendationEntity, Long> {

  List<RecommendationEntity> findTop10ByCustomerEmailOrderByCreatedAtDesc(String customerEmail);
}
