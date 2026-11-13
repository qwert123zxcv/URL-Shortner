package com.urlshortener.repository;

import com.urlshortener.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    @Query(value = "select nextval('url_mapping_seq')", nativeQuery = true)
    long nextSequenceValue();

    Optional<UrlMapping> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    /**
     * Used for duplicate-URL dedup: only auto-generated mappings are eligible
     * to be reused. A custom alias is always an explicit new mapping.
     */
    Optional<UrlMapping> findFirstByLongUrlAndCustomAliasFalse(String longUrl);
}
