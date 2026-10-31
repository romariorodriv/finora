package com.finora.app.admin;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AdminAuditRepository extends JpaRepository<AdminAudit,Long> {}
