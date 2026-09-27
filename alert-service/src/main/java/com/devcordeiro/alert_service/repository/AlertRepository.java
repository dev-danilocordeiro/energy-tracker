package com.devcordeiro.alert_service.repository;

import com.devcordeiro.alert_service.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
}
