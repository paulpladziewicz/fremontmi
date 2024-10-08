package com.paulpladziewicz.fremontmi.controllers;

import com.paulpladziewicz.fremontmi.exceptions.UserNotAuthenticatedException;
import com.paulpladziewicz.fremontmi.models.*;
import com.paulpladziewicz.fremontmi.services.BusinessService;
import com.paulpladziewicz.fremontmi.services.HtmlSanitizationService;
import com.paulpladziewicz.fremontmi.services.TagService;
import com.paulpladziewicz.fremontmi.services.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
public class BusinessController {

    @Value("${stripe.price.monthly.business}")
    private String monthlyPriceId;

    @Value("${stripe.price.annual.business}")
    private String annualPriceId;

    private final HtmlSanitizationService htmlSanitizationService;

    private final BusinessService businessService;

    private final TagService tagService;

    private final UserService userService;

    public BusinessController(HtmlSanitizationService htmlSanitizationService, BusinessService businessService, TagService tagService, UserService userService) {
        this.htmlSanitizationService = htmlSanitizationService;
        this.businessService = businessService;
        this.tagService = tagService;
        this.userService = userService;
    }

    @GetMapping("/create/business/overview")
    public String createBusinessListingOverview(Model model) {
        model.addAttribute("monthlyPriceId", monthlyPriceId);
        model.addAttribute("annualPriceId", annualPriceId);
        return "businesses/create-business-overview";
    }

    @PostMapping("/setup/create/business")
    public String setupCreateBusinessForm(@RequestParam("priceId") String priceId, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("priceId", priceId);
        return "redirect:/create/business";
    }

    @GetMapping("/create/business")
    public String createBusinessListingView(Model model) {
        if (model.containsAttribute("priceId")) {
            String priceId = (String) model.getAttribute("priceId");

            Business business = new Business();
            business.setPriceId(priceId);

            model.addAttribute("business", business);

            return "businesses/create-business";
        } else {
            return "redirect:/create/business/overview";
        }
    }

    @PostMapping("/create/business")
    public String createBusinessListing(@Valid @ModelAttribute("business") Business business, BindingResult result) {
        if (result.hasErrors()) {
            return "businesses/create-business";
        }

        Business savedBusiness = businessService.createBusiness(business);

        return "redirect:/pay/subscription?contentId=" + savedBusiness.getId();
    }

    @GetMapping("/businesses")
    public String displayActiveBusinesses(@RequestParam(value = "tag", required = false) String tag, Model model) {
        List<Business> businesses = businessService.findAllBusinesses(tag);

        List<Content> contentList = new ArrayList<>(businesses);
        List<TagUsage> popularTags = tagService.getTagUsageFromContent(contentList, 15);
        model.addAttribute("popularTags", popularTags);
        model.addAttribute("selectedTag", tag);

        model.addAttribute("businesses", businesses);

        return "businesses/businesses";
    }

    @GetMapping("/businesses/{slug}")
    public String viewBusiness(@PathVariable String slug, Model model) {
        Business business = businessService.findBusinessBySlug(slug);

        business.setDescription(htmlSanitizationService.sanitizeHtml(business.getDescription().replace("\n", "<br/>")));

        String userId;

        try {
           userId = userService.getUserId();
        } catch (UserNotAuthenticatedException e) {
            model.addAttribute("isAdmin", false);
            model.addAttribute("business", business);
            return "businesses/business-page";
        }

        boolean isAdmin = business.getAdministrators().contains(userId);

        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("business", business);

        return "businesses/business-page";
    }

    @GetMapping("/my/businesses")
    public String getMyBusinesses(Model model) {
        List<Business> businesses = businessService.findBusinessesByUser();

        model.addAttribute("businesses", businesses);

        return "businesses/my-businesses";
    }

    @GetMapping("/edit/business/{slug}")
    public String editBusiness(@PathVariable String slug, Model model) {
        Business business = businessService.findBusinessBySlug(slug);

        String tagsAsString = String.join(",", business.getTags());
        model.addAttribute("tagsAsString", tagsAsString);

        model.addAttribute("business", business);

        return "businesses/edit-business";
    }

    @PostMapping("/edit/business")
    public String updateBusiness(@Valid @ModelAttribute("business") Business business, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "businesses/edit-business";
        }

        Business updatedBusiness = businessService.updateBusiness(business);

        model.addAttribute("business", updatedBusiness);

        return "redirect:/businesses/" + updatedBusiness.getSlug();
    }

    @PostMapping("/delete/business")
    public String deleteBusiness(@RequestParam("businessId") String businessId) {
        businessService.deleteBusiness(businessId);

        return "redirect:/my/businesses";
    }

    @PostMapping("/contact/business")
    public ResponseEntity<Map<String, Object>> handleContactForm(
            @RequestBody ContactFormRequest contactFormRequest) {

        Boolean contactFormSuccess = businessService.handleContactFormSubmission(
                contactFormRequest.getSlug(),
                contactFormRequest.getName(),
                contactFormRequest.getEmail(),
                contactFormRequest.getMessage()
        );

        Map<String, Object> response = new HashMap<>();

        if (!contactFormSuccess) {
            response.put("success", false);
            response.put("message", "An error occurred while trying to send your message. Please try again later.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        response.put("success", true);
        response.put("message", "We've passed your message along! We hope you hear back soon.");
        return ResponseEntity.ok(response);
    }
}
