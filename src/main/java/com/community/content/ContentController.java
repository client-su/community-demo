package com.community.content;

import com.community.common.Result;
import com.community.content.dto.ContentVO;
import com.community.content.dto.CreateContentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contents")
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @PostMapping
    public ResponseEntity<Result<ContentVO>> publish(@Valid @RequestBody CreateContentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(contentService.publish(request)));
    }
}
