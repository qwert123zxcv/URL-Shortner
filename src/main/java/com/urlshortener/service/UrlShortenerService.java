package com.urlshortener.service;

import com.urlshortener.entity.UrlMapping;
import com.urlshortener.exception.AliasConflictException;
import com.urlshortener.exception.ShortCodeNotFoundException;
import com.urlshortener.repository.UrlMappingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UrlShortenerService {

    private final UrlMappingRepository repository;

    public UrlShortenerService(UrlMappingRepository repository) {
        this.repository = repository;
    }

    /**
     * Result of a shorten request: {@code created} distinguishes a brand new
     * mapping (custom alias, or a URL never seen before) from an existing
     * mapping returned via dedup, so the controller can reply 201 vs 200.
     */
    public record ShortenResult(UrlMapping mapping, boolean created) {
    }

    @Transactional
    public ShortenResult shorten(String rawUrl, String rawAlias) {
        String longUrl = UrlValidator.validate(rawUrl);

        if (rawAlias != null && !rawAlias.isBlank()) {
            return new ShortenResult(createWithCustomAlias(longUrl, rawAlias), true);
        }

        return repository.findFirstByLongUrlAndCustomAliasFalse(longUrl)
                .map(existing -> new ShortenResult(existing, false))
                .orElseGet(() -> new ShortenResult(createWithGeneratedCode(longUrl), true));
    }

    private UrlMapping createWithCustomAlias(String longUrl, String rawAlias) {
        String alias = AliasValidator.validate(rawAlias);
        if (repository.existsByShortCode(alias)) {
            throw new AliasConflictException("alias '" + alias + "' is already taken");
        }

        UrlMapping mapping = new UrlMapping(repository.nextSequenceValue(), longUrl, alias, true);
        try {
            return repository.save(mapping);
        } catch (DataIntegrityViolationException e) {
            // Defense-in-depth against a concurrent request winning the race
            // between the existsByShortCode check above and this insert.
            throw new AliasConflictException("alias '" + alias + "' is already taken");
        }
    }

    private UrlMapping createWithGeneratedCode(String longUrl) {
        long id = repository.nextSequenceValue();
        UrlMapping mapping = new UrlMapping(id, longUrl, ShortCodeGenerator.encode(id), false);
        return repository.save(mapping);
    }

    @Transactional(readOnly = true)
    public String resolve(String shortCode) {
        return repository.findByShortCode(shortCode)
                .map(UrlMapping::getLongUrl)
                .orElseThrow(() -> new ShortCodeNotFoundException("no mapping found for code: " + shortCode));
    }
}
