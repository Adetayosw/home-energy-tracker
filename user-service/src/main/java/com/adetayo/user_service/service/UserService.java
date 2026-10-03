package com.adetayo.user_service.service;


import com.adetayo.user_service.dto.UserDto;
import com.adetayo.user_service.entity.User;
import com.adetayo.user_service.exception.DuplicateEmailException;
import com.adetayo.user_service.exception.ResourceNotFoundException;
import com.adetayo.user_service.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserDto createUser(UserDto input){

        if (userRepository.existsByEmail(input.getEmail())) {
            throw new DuplicateEmailException(input.getEmail());
        }

        final User createdUser = User.builder()
                .name(input.getName())
                .surname(input.getSurname())
                .email(input.getEmail())
                .address(input.getAddress())
                .alerting(input.isAlerting())
                .energyAlertingThreshold(input.getEnergyAlertingThreshold())
                .build();

        User savedUser = userRepository.save(createdUser);
        return toDto(savedUser);
    }

    public UserDto getUserById(Long id){

        return userRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));
    }

    public UserDto updateUser(Long id, UserDto user){
        User existingUser = userRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("User", id.toString()));

        existingUser.setName(user.getName());
        existingUser.setSurname(user.getSurname());
        existingUser.setEmail(user.getEmail());
        existingUser.setAddress(user.getAddress());
        existingUser.setAlerting(user.isAlerting());
        existingUser.setEnergyAlertingThreshold(user.getEnergyAlertingThreshold());

        User savedUser = userRepository.save(existingUser);
        return toDto(savedUser);

    }

    public void deleteUser(Long id){
      userRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("User", id.toString()));

        userRepository.deleteById(id);
    }


    private UserDto toDto(User user){
        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .surname(user.getSurname())
                .email(user.getEmail())
                .address(user.getAddress())
                .alerting(user.isAlerting())
                .energyAlertingThreshold(user.getEnergyAlertingThreshold())
                .build();
    }



}
