package com.smartbus.booking.repository;

import com.smartbus.booking.entity.BookingProcessEntity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingProcessEntityRepository extends JpaRepository<BookingProcessEntity, String> {

  List<BookingProcessEntity> findByCustomerEmailIgnoreCaseOrderByUpdatedAtDesc(String customerEmail);

  List<BookingProcessEntity> findAllByOrderByUpdatedAtDesc();

  Page<BookingProcessEntity> findAll(Pageable pageable);
}
