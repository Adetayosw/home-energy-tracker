package com.adetayo.device_service;

import com.adetayo.device_service.Model.DeviceType;
import com.adetayo.device_service.entity.Device;
import com.adetayo.device_service.repository.DeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Slf4j
@SpringBootTest
//@ActiveProfiles("test")
class DeviceServiceApplicationTests {

	public static final int Number_OF_DEVICES = 200;
	public static final int USERS = 10;

	@Autowired
	DeviceRepository deviceRepository;

	@Test
	void contextLoads() {
	}

	@Disabled
	@Test
	void createDevice() {
		for(int i = 0; i < Number_OF_DEVICES; i++) {
			var device = Device.builder()
					.name("Device" + i)
					.type(DeviceType.values()[i %  DeviceType.values().length])
					.location("Location" + ((i % 3) * 1))
					.userId((long) ((i % USERS) + 1))
					.build();

			deviceRepository.save(device);
		}

		log.info("Device table populated");
	}

}
