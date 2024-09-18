package com.paulpladziewicz.fremontmi.repositories;

import com.paulpladziewicz.fremontmi.models.Content;
import com.paulpladziewicz.fremontmi.models.NeighborServicesProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ContentRepository extends MongoRepository<Content, String> {
    Optional<Content> findBySlug(String slug);

    List<Content> findBySlugRegex(String slugPattern);

    @Query("{ 'type': ?0, 'visibility': ContentVISIBILITY.PUBLIC.getVisibility() }")
    List<Content> findAllByType(String contentType);

    @Query("{ 'tags': ?0, 'type':  ?1, 'visibility': ContentVISIBILITY.PUBLIC.getVisibility() }")
    List<Content> findByTagAndType(String tag, String contentType);

    @Query("{ 'createdBy': ?0 }")
    Optional<Content> findByCreatedBy(String createdBy);

    @Query(value = "{}", fields = "{ 'tags': 1 }")
    List<String> findDistinctTags();

    @Query("{ 'reviewed': false }")
    List<Content> contentNotReviewed();
}
