package com.openforge.aimate.office;

import com.openforge.aimate.domain.RentPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/offices")
@RequiredArgsConstructor
public class OfficeController {

    private final OfficeRentalService officeRentalService;

    @GetMapping("/plans")
    public List<Map<String, Object>> plans() {
        return officeRentalService.listPlans();
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return officeRentalService.myOffice(requireUserId());
    }

    @GetMapping("/quote")
    public Map<String, Object> quote(@RequestParam Long planId, @RequestParam String period) {
        try {
            return officeRentalService.quote(planId, RentPeriod.valueOf(period.trim().toUpperCase()));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/rent")
    public Map<String, Object> rent(@RequestBody RentBody body) {
        try {
            return officeRentalService.rent(
                    requireUserId(),
                    body.planId(),
                    RentPeriod.valueOf(body.period().trim().toUpperCase())
            );
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/renew")
    public Map<String, Object> renew(@RequestBody RenewBody body) {
        try {
            return officeRentalService.renew(
                    requireUserId(),
                    RentPeriod.valueOf(body.period().trim().toUpperCase())
            );
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/release")
    public Map<String, Object> release() {
        try {
            return officeRentalService.release(requireUserId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private Long requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) return id;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
    }

    public record RentBody(Long planId, String period) {}
    public record RenewBody(String period) {}
}
