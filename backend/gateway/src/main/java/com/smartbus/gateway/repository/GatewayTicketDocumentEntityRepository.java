package com.smartbus.gateway.repository;

import com.smartbus.gateway.entity.GatewayTicketDocumentEntity;
import com.smartbus.gateway.entity.GatewayTicketDocumentId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayTicketDocumentEntityRepository extends JpaRepository<GatewayTicketDocumentEntity, GatewayTicketDocumentId> {

  List<GatewayTicketDocumentEntity> findByIdOwnerEmailIgnoreCaseOrderByCreatedAtDesc(String ownerEmail);

  Optional<GatewayTicketDocumentEntity> findByIdOwnerEmailIgnoreCaseAndIdBookingReference(String ownerEmail, String bookingReference);
}
