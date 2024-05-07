package com.paulpladziewicz.fremontmi.controllers;

import com.paulpladziewicz.fremontmi.models.Group;
import com.paulpladziewicz.fremontmi.models.GroupDetailsDto;
import com.paulpladziewicz.fremontmi.services.GroupService;
import jakarta.validation.Valid;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/groups")
    public String displayGroups(Model model) {
        model.addAttribute("groups", groupService.findAll());
        return "groups";
    }

    @GetMapping("/groups/{id}")
    public String displayGroup(@PathVariable String id, Model model) {
        model.addAttribute("group", groupService.findGroupById(id));
        return "group-page";
    }

    @PostMapping("/groups/join")
    public String joinGroup(@RequestParam("groupId") String groupId, RedirectAttributes redirectAttributes) {
        groupService.joinGroup(groupId);
        redirectAttributes.addFlashAttribute("successMessage", "You have successfully joined the group!");


        return "redirect:/groups/" + groupId;
    }

    @GetMapping("/my/groups")
    public String groups(Model model) {
        try {
            List<GroupDetailsDto> groupDetails = groupService.findGroupsForUser();
            model.addAttribute("groups", groupDetails);
            return "dashboard-groups";
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "home";
        }
    }


    @GetMapping("/my/groups/create")
    public String createGroup(Model model) {
        model.addAttribute("group", new Group());
        return "dashboard/create-group";
    }

    @PostMapping("/my/groups/create")
    public String submitCreateGroup(@ModelAttribute("group") @Valid Group group, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "dashboard/create-group";
        }

        Group savedGroup = groupService.addGroup(group);

        model.addAttribute("successMessage", "Group created successfully!");
        return "redirect:/my/groups/" + savedGroup.getId();
    }


    @GetMapping("/my/groups/{id}")
    public String memberGroupView(@PathVariable String id, Model model) {
        model.addAttribute("groups", groupService.findGroupById(id));
        return "dashboard-group-member";
    }

    @GetMapping("/my/groups/admin/{id}")
    public String adminGroupView(@PathVariable String id) {
        return "dashboard-angular";
    }
}
