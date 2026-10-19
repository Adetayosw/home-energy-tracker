package com.adetayo.device_service.service;

import com.adetayo.device_service.dto.DeviceDto;
import com.adetayo.device_service.entity.Device;
import com.adetayo.device_service.exception.DeviceNotFoundException;
import com.adetayo.device_service.repository.DeviceRepository;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class DeviceService {
    private DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public DeviceDto getDeviceById(Long id) {

        Device device = deviceRepository.findById(id)
                .orElseThrow(()-> new DeviceNotFoundException("Device not found with id " + id));
        return mapToDto(device);
    }

    public DeviceDto createDevice(DeviceDto input){

       Device newDevice = Device.builder()
                .id(input.getId())
                .name(input.getName())
                .type(input.getDeviceType())
                .location(input.getLocation())
                .userId(input.getUserId())
                .build();

        Device savedDevice = deviceRepository.save(newDevice);
        return mapToDto(savedDevice);
    }

    public DeviceDto updateDevice(Long id, DeviceDto input){
        Device existingDevice = deviceRepository.findById(id)
                .orElseThrow(()-> new DeviceNotFoundException("Device not found with id " + id));

        existingDevice.setName(input.getName());
        existingDevice.setType(input.getDeviceType());
        existingDevice.setLocation(input.getLocation());
        existingDevice.setUserId(input.getUserId());

        Device updatedDevice = deviceRepository.save(existingDevice);
        return mapToDto(updatedDevice);

    }

    public void deleteDevice(Long id){
       deviceRepository.findById(id)
                        .orElseThrow(()-> new DeviceNotFoundException("Device not found with id " + id));

        deviceRepository.deleteById(id);

    }

    public List<DeviceDto> getAllDevicesByUserId(Long userId){
        List<Device> devices = deviceRepository.findAllByUserId(userId);
        return devices.stream().map(this::mapToDto)
                .toList();
    }

    private DeviceDto mapToDto(Device device) {

        return DeviceDto.builder()
            .id(device.getId())
            .name(device.getName())
            .location(device.getLocation())
                .deviceType(device.getType())
            .userId(device.getUserId())
            .build();

    }
}
