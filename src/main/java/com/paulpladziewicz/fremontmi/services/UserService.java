package com.paulpladziewicz.fremontmi.services;

import com.paulpladziewicz.fremontmi.models.UserDetailsDto;
import com.paulpladziewicz.fremontmi.models.UserDto;
import com.paulpladziewicz.fremontmi.models.UserRegistrationDto;
import com.paulpladziewicz.fremontmi.repositories.UserDetailsRepository;
import com.paulpladziewicz.fremontmi.repositories.UserRepository;
import jakarta.validation.ValidationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    private final UserDetailsRepository userDetailsRepository;

    private final PasswordEncoder passwordEncoder;

    private final EmailService emailService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, EmailService emailService, UserDetailsRepository userDetailsRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.userDetailsRepository = userDetailsRepository;
    }

    public void createUser(UserRegistrationDto userRegistrationDto) {
        if (userRepository.findByUsername(userRegistrationDto.getEmail()) != null) {
            throw new RuntimeException("There is an account with that email address: " + userRegistrationDto.getEmail());
        }

        validatePasswords(userRegistrationDto.getPassword(), userRegistrationDto.getMatchingPassword());

        UserDto newUserDto = new UserDto();
        newUserDto.setUsername(userRegistrationDto.getEmail());
        newUserDto.setPassword(passwordEncoder.encode(userRegistrationDto.getPassword()));

        UserDto savedUser = userRepository.save(newUserDto);

        UserDetailsDto userDetails = new UserDetailsDto();
        userDetails.setUsername(savedUser.getUsername());
        userDetails.setEmail(userRegistrationDto.getEmail());
        userDetails.setFirstName(userRegistrationDto.getFirstName());
        userDetails.setLastName(userRegistrationDto.getLastName());

        userDetailsRepository.save(userDetails);
    }

    public void forgotPassword(String email) {
        UserDto userDto = userRepository.findByUsername(email);

        if (userDto != null) {
            String token = UUID.randomUUID().toString();
            userDto.setResetPasswordToken(token);
            userRepository.save(userDto);

            emailService.sendSimpleMessage(userDto.getUsername(), "Reset your password", "To reset your password, click here: " + "http://localhost:8080/reset-password?token=" + token);
        }
    }

    public String getSignedInUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return auth.getName();
        }

        return null;
    }

    public UserDetailsDto getUserDetails() {
        String username = getSignedInUser();
        Optional<UserDetailsDto> userDetailsOpt = userDetailsRepository.findById(username);
        if (userDetailsOpt.isEmpty()) {
            throw new IllegalStateException("User details not found for username: " + username);
        }
        return userDetailsOpt.get();
    }

    public void saveUserDetails(UserDetailsDto userDetails) {
        userDetailsRepository.save(userDetails);
    }

    private void validatePasswords(String password, String matchingPassword) throws ValidationException {
        if (password == null || !password.equals(matchingPassword)) {
            throw new ValidationException("Passwords must match.");
        }
    }

    public UserDetailsDto updateUserDetails(UserDetailsDto newDetails) {
        String username = getSignedInUser();
        Optional<UserDetailsDto> existingDetails = userDetailsRepository.findById(username);
        if (existingDetails.isPresent()) {
            UserDetailsDto updatedDetails = existingDetails.get();
            updatedDetails.setFirstName(newDetails.getFirstName());
            updatedDetails.setLastName(newDetails.getLastName());
            updatedDetails.setEmail(newDetails.getEmail());
            userDetailsRepository.save(updatedDetails);
            return updatedDetails;
        } else {
            throw new RuntimeException("User details not found.");
        }
    }
}
