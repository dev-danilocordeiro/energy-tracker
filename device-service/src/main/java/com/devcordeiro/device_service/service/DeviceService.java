package com.devcordeiro.device_service.service;

import com.devcordeiro.device_service.dto.DeviceDto;
import com.devcordeiro.device_service.entity.Device;
import com.devcordeiro.device_service.exception.DeviceNotFoundException;
import com.devcordeiro.device_service.repository.DeviceRepository;
import org.springframework.stereotype.Service;

@Service
public class DeviceService {

    private DeviceRepository deviceRepository;
    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public DeviceDto getDeviceById(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new DeviceNotFoundException("Device not found with id " + id));

        return mapToDto(device);
    }

    public DeviceDto createDevice(DeviceDto deviceDto) {
        Device device = new Device();
        device.setName(deviceDto.getName());
        device.setLocation(deviceDto.getLocation());
        device.setDeviceType(deviceDto.getType());
        device.setUserId(deviceDto.getUserId());

        final Device savedDevice = deviceRepository.save(device);
        return mapToDto(savedDevice);
    }

    public DeviceDto updateDevice(Long id, DeviceDto deviceDto) {
        Device existed = deviceRepository.findById(id)
                .orElseThrow(() -> new DeviceNotFoundException("Device not found with id " + id));
        existed.setName(deviceDto.getName());
        existed.setLocation(deviceDto.getLocation());
        existed.setDeviceType(deviceDto.getType());
        existed.setUserId(deviceDto.getUserId());
        final Device updatedDevice = deviceRepository.save(existed);
        return mapToDto(updatedDevice);
    }

    public void deleteDevice(Long id) {
        if (!deviceRepository.existsById(id)) {
            throw new DeviceNotFoundException("Device not found with id " + id);
        }
        deviceRepository.deleteById(id);
    }

    private DeviceDto mapToDto(Device device) {
        DeviceDto deviceDto = new DeviceDto();
        deviceDto.setId(device.getId());
        deviceDto.setName(device.getName());
        deviceDto.setType(device.getDeviceType());
        deviceDto.setLocation(device.getLocation());
        deviceDto.setUserId(device.getUserId());
        return deviceDto;
    }
}
