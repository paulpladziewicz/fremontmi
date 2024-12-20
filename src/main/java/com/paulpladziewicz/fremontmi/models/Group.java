package com.paulpladziewicz.fremontmi.models;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class Group implements ContentDetail {

    private String title;

    private String description;

    private String externalUrl;

    private Map<String, Object> images;

    private List<Announcement> announcements = new ArrayList<>();

    @Override
    public void update(Content content, ContentDto contentDto) {
        if (!(contentDto instanceof GroupDto updatedGroup)) {
            throw new IllegalArgumentException("ContentDto is not a GroupDto");
        }

        setTitle(updatedGroup.getTitle());
        setDescription(updatedGroup.getDescription());
    }
}
