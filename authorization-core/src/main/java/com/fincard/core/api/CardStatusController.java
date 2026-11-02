package com.fincard.core.api;

import com.fincard.core.dto.CardStatusResponse;
import com.fincard.core.CardStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cards")
public class CardStatusController {

    private final CardStatusService service;

    public CardStatusController(CardStatusService service) {
        this.service = service;
    }

    @GetMapping("/{cardNumber}")
    public CardStatusResponse getCardStatus(@PathVariable String cardNumber) {
        return service.getCardStatus(cardNumber);
    }
}