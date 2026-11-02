package com.fincard.core.api;

import com.fincard.core.dto.CardAuthorizationRequest;
import com.fincard.core.dto.CardAuthorizationResponse;
import com.fincard.core.service.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cards")
public class CardAuthorizationController {

    private final AuthorizationService service;

    public CardAuthorizationController(AuthorizationService service) {
        this.service = service;
    }

    @PostMapping("/authorizations")
    public CardAuthorizationResponse authorize(@Valid @RequestBody CardAuthorizationRequest request) {
        return service.authorize(request);
    }
}
