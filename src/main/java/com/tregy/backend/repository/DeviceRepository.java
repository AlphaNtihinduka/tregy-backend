package com.tregy.backend.repository;
import com.tregy.backend.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface DeviceRepository extends JpaRepository<Device,String>{Optional<Device> findByUserIdAndInstallationId(String userId,String installationId);}
