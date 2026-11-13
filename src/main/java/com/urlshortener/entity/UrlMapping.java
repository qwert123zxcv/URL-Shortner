package com.urlshortener.entity;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * A single long-URL <-> short-code mapping.
 *
 * The id is NOT JPA-generated. It is fetched explicitly from the
 * url_mapping_seq DB sequence (see UrlMappingRepository#nextSequenceValue)
 * before the entity is constructed, so the service layer can derive the
 * short code from it and hand JPA a fully-populated entity in one shot.
 *
 * This entity implements Persistable so Spring Data's save() calls
 * entityManager.persist() (a single INSERT) instead of merge() -- Spring
 * Data's default "is this new?" heuristic assumes a non-null @Id means an
 * existing row, which would be wrong here since we assign ids ourselves.
 *
 * (An earlier version let Hibernate's @GeneratedValue(SEQUENCE) assign the
 * id, planning to set shortCode afterward and rely on dirty-checking to
 * include it in the same INSERT. That doesn't work: Hibernate snapshots
 * entity state at persist() time for the queued insert action, so the
 * later mutation was silently dropped and short_code was inserted as NULL,
 * violating the NOT NULL constraint. Fetching the id up front avoids the
 * problem entirely.)
 */
@Entity
@Table(name = "url_mapping")
public class UrlMapping implements Persistable<Long> {

    @Id
    private Long id;

    @Column(name = "short_code", unique = true, nullable = false, length = 32)
    private String shortCode;

    @Column(name = "long_url", nullable = false, columnDefinition = "text")
    private String longUrl;

    @Column(name = "is_custom_alias", nullable = false)
    private boolean customAlias;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected UrlMapping() {
        // required by JPA
    }

    public UrlMapping(Long id, String longUrl, String shortCode, boolean customAlias) {
        this.id = id;
        this.longUrl = longUrl;
        this.shortCode = shortCode;
        this.customAlias = customAlias;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public boolean isCustomAlias() {
        return customAlias;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
