package com.adetayo.device_service.repository;

import com.adetayo.device_service.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceRepository  extends JpaRepository<Device,Long> {

    List<Device> findAllByUserId(Long deviceId);
}
