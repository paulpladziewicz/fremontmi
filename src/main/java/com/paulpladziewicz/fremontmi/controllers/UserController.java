package com.paulpladziewicz.fremontmi.controllers;

import com.paulpladziewicz.fremontmi.models.*;
import com.paulpladziewicz.fremontmi.services.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Optional;

@Controller
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        Model model) {

        if (error != null) {
            model.addAttribute("errorMessage", "Invalid username or password.");
        }

        if (logout != null) {
            model.addAttribute("logoutMessage", "You have been logged out successfully.");
        }

        return "auth/login";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("userRegistrationDto", new UserRegistrationDto());
        return "auth/register";
    }

    @PostMapping("/register")
    public String handleRegistration(@ModelAttribute("userRegistrationDto") @Valid UserRegistrationDto userRegistrationDto, BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("userRegistrationDto", userRegistrationDto);
            return "auth/register";
        }

        userRegistrationDto.setTermsAcceptedAt(LocalDateTime.now());

        ServiceResponse<Boolean> serviceResponse = userService.createUser(userRegistrationDto);

        if (serviceResponse.hasError()) {
            switch (serviceResponse.errorCode()) {
                case "username_exists":
                    result.rejectValue("username", "error.userRegistrationDto", "messageToShowUser");
                    break;
                case "email_exists":
                    result.rejectValue("email", "error.userRegistrationDto", "messageToShowUser");
                    break;
                case "password_mismatch":
                    result.rejectValue("matchingPassword", "error.userRegistrationDto", "messageToShowUser");
                    break;
                default:
                    model.addAttribute("unexpectedError", "An unexpected error occurred. Please try again later.");
                    break;
            }

            model.addAttribute("userRegistrationDto", userRegistrationDto);
            return "auth/register";
        }

        return "redirect:/login";
    }

    @GetMapping("/my/settings")
    public String settings(Model model) {
        Optional<UserProfile> userProfile = userService.getUserProfile();

        if (userProfile.isEmpty()) {
            model.addAttribute("error", true);
            return "settings/profile";
        }

        model.addAttribute("userProfile", userProfile.get());

        return "settings/profile";
    }

    @PostMapping("/my/settings")
    public String updateSettings(@ModelAttribute("userProfile") @Valid UserProfile userProfile, BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("userProfile", userProfile);
            return "settings/profile";
        }
        ServiceResponse<UserProfile> serviceResponse = userService.updateUserProfile(userProfile);

        if (serviceResponse.hasError()) {
            model.addAttribute("isSuccess", false);
            model.addAttribute("userProfile", userProfile);
            return "settings/profile";
        }

        model.addAttribute("isSuccess", true);
        model.addAttribute("userProfile", serviceResponse.value());

        return "settings/profile";
    }

    @GetMapping("/my/settings/billing")
    public String billingSettings(Model model) {

        return "settings/billing";
    }

    @GetMapping("/forgot-password")
    public String forgotPassword(Model model) {
        model.addAttribute("emailDto", new EmailDto());
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String handleForgotPassword(@ModelAttribute("emailDto") @Valid EmailDto emailDto, BindingResult result, Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("emailDto", emailDto);
            return "auth/forgot-password";
        }

        ServiceResponse<Boolean> serviceResponse = userService.forgotPassword(emailDto.getEmail());

        if (serviceResponse.hasError()) {
            model.addAttribute("isSuccess", false);
            model.addAttribute("emailDto", emailDto);
            return "auth/forgot-password";
        }

        redirectAttributes.addFlashAttribute("tokenSent", true);
        return "redirect:/login";
    }

    @GetMapping("/reset-password")
    public String resetPassword(Model model, @RequestParam("token") String token) {
        ResetPasswordDto resetPasswordDto = new ResetPasswordDto();
        resetPasswordDto.setToken(token);
        model.addAttribute("resetPasswordDto", resetPasswordDto);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPasswordConfirmation(@ModelAttribute("resetPasswordDto") ResetPasswordDto resetPasswordDto, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return "auth/reset-password";
        }

        ServiceResponse<Boolean> serviceResponse = userService.resetPassword(resetPasswordDto);

        if (serviceResponse.hasError()) {
            model.addAttribute("isSuccess", false);
            return "auth/reset-password";
        }

        model.addAttribute("message", "Password has been reset successfully");

        return "auth/login";
    }

    @GetMapping("/forgot-username")
    public String forgotUsername() {
        return "auth/forgot-username";
    }

    @PostMapping("/forgot-username")
    public String handleForgotUsername(@RequestParam("email") @Email String email, Model model, RedirectAttributes redirectAttributes) {
        if (email == null || email.isEmpty()) {
            model.addAttribute("emailError", "Please provide a valid email address.");
            return "auth/forgot-username";
        }

        ServiceResponse<Boolean> serviceResponse = userService.forgotUsername(email);

        if (serviceResponse.hasError()) {
            model.addAttribute("isSuccess", false);
            return "auth/login";
        }

        redirectAttributes.addFlashAttribute("message", "An email has been sent with your username.");
        return "auth/login";
    }
}

