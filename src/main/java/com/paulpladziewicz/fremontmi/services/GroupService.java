package com.paulpladziewicz.fremontmi.services;

import com.paulpladziewicz.fremontmi.models.Announcement;
import com.paulpladziewicz.fremontmi.models.Group;
import com.paulpladziewicz.fremontmi.models.GroupDetailsDto;
import com.paulpladziewicz.fremontmi.models.UserDetailsDto;
import com.paulpladziewicz.fremontmi.repositories.GroupRepository;
import com.paulpladziewicz.fremontmi.repositories.UserDetailsRepository;
import com.paulpladziewicz.fremontmi.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private final GroupRepository groupRepository;

    private final UserService userService;

    private final UserDetailsRepository userDetailsRepository;

    public GroupService(GroupRepository groupRepository, UserService userService, UserDetailsRepository userDetailsRepository) {
        this.groupRepository = groupRepository;
        this.userService = userService;
        this.userDetailsRepository = userDetailsRepository;
    }

    public List<Group> findAll() {
        return groupRepository.findAll();
    }

    public Group findGroupById(String id) {
        return groupRepository.findById(id).orElse(null);
    }

    public List<GroupDetailsDto> findGroupsByUser() {
        UserDetailsDto userDetails = userService.getUserDetails();

        List<Group> groups = groupRepository.findAllById(userDetails.getGroupIds());

        return groups.stream()
                .map(group -> {
                    GroupDetailsDto dto = new GroupDetailsDto();
                    dto.setGroup(group);
                    if (userDetails.getGroupAdminIds().contains(group.getId())) {
                        dto.setUserRole("admin");
                    } else {
                        dto.setUserRole("member");
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public Group addGroup (Group group) {
        UserDetailsDto userDetails = userService.getUserDetails();

        List<String> administrators = new ArrayList<>(group.getAdministrators());
        List<String> members = new ArrayList<>(group.getMembers());
        administrators.add(userDetails.getUsername());
        members.add(userDetails.getUsername());
        group.setAdministrators(administrators);
        group.setMembers(members);
        Group savedGroup = groupRepository.save(group);

        List<String> groupIds = new ArrayList<>(userDetails.getGroupIds());
        List<String> adminGroupIds = new ArrayList<>(userDetails.getGroupAdminIds());
        groupIds.add(savedGroup.getId());
        adminGroupIds.add(savedGroup.getId());
        userDetails.setGroupIds(groupIds);
        userDetails.setGroupAdminIds(adminGroupIds);
        userDetailsRepository.save(userDetails);

        return savedGroup;
    }

    @Transactional
    public void joinGroup (String groupId) {
        UserDetailsDto userDetails = userService.getUserDetails();
        Group group = findGroupById(groupId);

        List<String> members = group.getMembers();
        if (!members.contains(userDetails.getUsername())) {
            members.add(userDetails.getUsername());
            group.setMembers(members);
            groupRepository.save(group);
        }

        List<String> groupIds = userDetails.getGroupIds();
        if (!groupIds.contains(groupId)) {
            groupIds.add(groupId);
            userDetails.setGroupIds(groupIds);
            userService.saveUserDetails(userDetails);
        }
    }

    @Transactional
    public void leaveGroup (String groupId) {
        UserDetailsDto userDetails = userService.getUserDetails();
        Group group = findGroupById(groupId);

        List<String> members = new ArrayList<>(group.getMembers());
        members.remove(userDetails.getUsername());
        group.setMembers(members);
        Group savedGroup = groupRepository.save(group);

        List<String> groupIds = new ArrayList<>(userDetails.getGroupIds());
        groupIds.remove(savedGroup.getId());
        userDetails.setGroupIds(groupIds);
        userService.saveUserDetails(userDetails);
    }

    public Group updateGroup (Group group) {
        Group groupDocument = findGroupById(group.getId());
        groupDocument.setName(group.getName());
        groupDocument.setDescription(group.getDescription());
        return groupRepository.save(groupDocument);
    }

    public void deleteGroup (String groupId) {
        groupRepository.deleteById(groupId);
    }

    public List<Announcement> addAnnouncement(String groupId, Announcement announcement) {
        Group group = findGroupById(groupId);
        announcement.setId(group.getAnnouncements().size() + 1);
        List<Announcement> announcements = new ArrayList<>(group.getAnnouncements());
        announcements.add(announcement);
        group.setAnnouncements(announcements);
        groupRepository.save(group);
        return announcements;
    }

    public List<Announcement> updateAnnouncement(String groupId, Announcement announcement) {
        Group group = findGroupById(groupId);
        List<Announcement> announcements = new ArrayList<>(group.getAnnouncements());

        boolean isUpdated = false;

        for (int i = 0; i < announcements.size(); i++) {
            Announcement currentAnnouncement = announcements.get(i);
            if (currentAnnouncement.getId() == i) {
                currentAnnouncement.setTitle(announcement.getTitle());
                currentAnnouncement.setContent(announcement.getContent());
                isUpdated = true;
                break;
            }
        }

        if (!isUpdated) {
            throw new IllegalArgumentException("Announcement not found with ID: " + announcement.getId());
        }

        groupRepository.save(group);

        return new ArrayList<>(group.getAnnouncements());
    }

    public List<Announcement> updateAllAnnouncements(String groupId, List<Announcement> announcements) {
        Group group = findGroupById(groupId);
        List<Announcement> newAnnouncementsArray = new ArrayList<>(announcements);
        group.setAnnouncements(newAnnouncementsArray);
        groupRepository.save(group);
        return announcements;
    }

    public boolean deleteAnnouncement(String groupId, int announcementId) {
        Group group = findGroupById(groupId);
        List<Announcement> announcements = new ArrayList<>(group.getAnnouncements());

        boolean isDeleted = false;

        for (int i = 0; i < announcements.size(); i++) {
            Announcement currentAnnouncement = announcements.get(i);
            if (currentAnnouncement.getId() == announcementId) {
                announcements.remove(i);
                isDeleted = true;
                break;
            }
        }

        if (!isDeleted) {
            return false;
        }

        group.setAnnouncements(announcements);

        groupRepository.save(group);

        return true;
    }
}
