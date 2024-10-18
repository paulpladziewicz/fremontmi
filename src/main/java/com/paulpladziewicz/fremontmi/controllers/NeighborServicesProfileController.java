package com.paulpladziewicz.fremontmi.controllers;

import com.paulpladziewicz.fremontmi.exceptions.ContentNotFoundException;
import com.paulpladziewicz.fremontmi.models.*;
import com.paulpladziewicz.fremontmi.services.HtmlSanitizationService;
import com.paulpladziewicz.fremontmi.services.NeighborServicesProfileService;
import com.paulpladziewicz.fremontmi.services.TagService;
import com.paulpladziewicz.fremontmi.services.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
public class NeighborServicesProfileController {

    private final HtmlSanitizationService htmlSanitizationService;

    private final NeighborServicesProfileService neighborServicesProfileService;

    private final TagService tagService;
    private final UserService userService;

    public NeighborServicesProfileController(NeighborServicesProfileService neighborServicesProfileService, TagService tagService, HtmlSanitizationService htmlSanitizationService, UserService userService) {
        this.neighborServicesProfileService = neighborServicesProfileService;
        this.tagService = tagService;
        this.htmlSanitizationService = htmlSanitizationService;
        this.userService = userService;
    }

    @GetMapping("/overview/neighbor-services")
    public String getOverview(Model model) {
        return "neighborservices/overview";
    }

    @GetMapping("/create/neighbor-services-profile")
    public String createNeighborServicesProfileForm(Model model, RedirectAttributes redirectAttributes) {
        try {
            neighborServicesProfileService.findNeighborServiceProfileByUserId();
            redirectAttributes.addFlashAttribute("errorMessage", "You already have a NeighborServices™ Profile. You can only have one profile at a time.");
            return "redirect:/my/neighbor-services/profile";

        } catch (ContentNotFoundException e) {
            NeighborServicesProfile neighborServicesProfile = new NeighborServicesProfile();
            neighborServicesProfile.getNeighborServices().add(new NeighborService());

            model.addAttribute("neighborServicesProfile", neighborServicesProfile);

            return "neighborservices/create-neighbor-services-profile";
        }
    }

    @PostMapping("/create/neighbor-services-profile")
    public String createNeighborServiceSubscription(@Valid @ModelAttribute("neighborService") NeighborServicesProfile neighborServicesProfile, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("tagsAsString", String.join(",", neighborServicesProfile.getTags()));
            return "neighborservices/create-neighbor-services-profile";
        }

        neighborServicesProfileService.createNeighborServiceProfile(neighborServicesProfile);

        return "redirect:/my/neighbor-services/profile";
    }

    @GetMapping("/neighbor-services")
    public String displayActiveNeighborServices(@RequestParam(value = "tag", required = false) String tag, Model model) {
        List<NeighborServicesProfile> profiles = neighborServicesProfileService.findAllActiveNeighborServices(tag);

        // TODO still displaying profiles that do not have any neighbor services

        List<Content> contentList = new ArrayList<>(profiles);
        List<TagUsage> popularTags = tagService.getTagUsageFromContent(contentList, 15);
        model.addAttribute("popularTags", popularTags);
        model.addAttribute("selectedTag", tag);

        model.addAttribute("profiles", profiles);

        return "neighborservices/neighbor-services";
    }

    @GetMapping("/neighbor-services/{slug}")
    public String viewNeighborService(@PathVariable String slug, Model model) {
        NeighborServicesProfile neighborServicesProfile = neighborServicesProfileService.findNeighborServiceProfileBySlug(slug);
        Boolean createdByUser;
        try {
            String userId = userService.getUserId();
            createdByUser = Objects.equals(userId, neighborServicesProfile.getCreatedBy());
        } catch (Exception e) {
            createdByUser = false;
        }

        if (Objects.equals(neighborServicesProfile.getVisibility(), ContentVisibility.RESTRICTED.getVisibility())) {
            return "restricted-visibility";
        }

        neighborServicesProfile.setDescription(htmlSanitizationService.sanitizeHtml(neighborServicesProfile.getDescription().replace("\n", "<br/>")));
        model.addAttribute("neighborServicesProfile", neighborServicesProfile);
        model.addAttribute("myProfile", createdByUser);

        return "neighborservices/neighbor-services-profile-page";
    }

    @GetMapping("/my/neighbor-services/profile")
    public String viewMyNeighborServiceProfile(Model model) {
        try {
            NeighborServicesProfile neighborServicesProfile = neighborServicesProfileService.findNeighborServiceProfileByUserId();
            neighborServicesProfile.setDescription(htmlSanitizationService.sanitizeHtml(neighborServicesProfile.getDescription().replace("\n", "<br/>")));
            model.addAttribute("neighborServicesProfile", neighborServicesProfile);
            model.addAttribute("myProfile", true);
            return "neighborservices/neighbor-services-profile-page";
        } catch (ContentNotFoundException e) {
            return "redirect:/create/neighbor-services-profile";
        }
    }

    @GetMapping("/edit/neighbor-service/profile/{slug}")
    public String editNeighborServiceProfilePage(@PathVariable String slug, Model model) {
        NeighborServicesProfile neighborServicesProfile = neighborServicesProfileService.findNeighborServiceProfileBySlug(slug);

        String tagsAsString = String.join(",", neighborServicesProfile.getTags());
        model.addAttribute("tagsAsString", tagsAsString);

        model.addAttribute("neighborServicesProfile", neighborServicesProfile);

        return "neighborservices/edit-neighbor-services-profile";
    }


    @PostMapping("/edit/neighbor-service/profile")
    public String editNeighborService(@Valid @ModelAttribute("neighborService") NeighborServicesProfile neighborServicesProfile, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("tagsAsString", String.join(",", neighborServicesProfile.getTags()));
            return "neighborservices/edit-neighbor-services-profile";
        }

        NeighborServicesProfile updatedNeighborServicesProfile = neighborServicesProfileService.updateNeighborServiceProfile(neighborServicesProfile);

        model.addAttribute("neighborService", updatedNeighborServicesProfile);

        return "redirect:/my/neighbor-services/profile";
    }

    // TODO delete neighbor service profile

    @PostMapping("/contact/neighbor-services-profile")
    public ResponseEntity<String> handleContactForm(@RequestBody @Valid ContactFormRequest contactFormRequest) {

        try {
            neighborServicesProfileService.handleContactFormSubmission(
                    contactFormRequest.getId(),
                    contactFormRequest.getName(),
                    contactFormRequest.getEmail(),
                    contactFormRequest.getMessage()
            );

            return ResponseEntity.ok("Form submitted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to submit the form");
        }
    }
}
