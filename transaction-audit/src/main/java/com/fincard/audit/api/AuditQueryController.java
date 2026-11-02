package com.fincard.audit.api;

import com.fincard.audit.repository.AuditDao;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/api/v1/audit")
public class AuditQueryController {

    private final AuditDao dao;

    public AuditQueryController(AuditDao dao) {
        this.dao = dao;
    }


    @GetMapping
    public List<AuditEntry> query(
            @RequestParam(required = false) String cardNumber,
            @RequestParam(defaultValue = "20") int limit) {

        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit debe estar entre 1 y 100");
        }
        if (cardNumber != null && !cardNumber.matches("[0-9]{13,19}")) {
            throw new IllegalArgumentException("cardNumber debe ser 13-19 dígitos");
        }

        String masked = (cardNumber == null)
                ? null
                : cardNumber.substring(0, 4) + "****" + cardNumber.substring(cardNumber.length() - 4);

        return dao.list(masked, limit);
    }
}
