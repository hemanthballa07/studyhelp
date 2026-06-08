package com.platform.search.app;

import com.platform.shared.dedup.DedupPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SearchDedupAdapter implements DedupPort {

    private final SearchService searchService;

    public SearchDedupAdapter(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public Optional<UUID> checkDuplicate(UUID questionId, String subject, String title, String body) {
        return searchService.findDuplicates(questionId, subject, title, body).stream().findFirst();
    }
}
