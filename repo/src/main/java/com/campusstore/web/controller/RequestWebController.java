package com.campusstore.web.controller;

import com.campusstore.api.dto.PagedResponse;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.persistence.entity.ItemRequestEntity;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import com.campusstore.web.client.InternalApiClient;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RequestWebController {

    private final InternalApiClient apiClient;

    public RequestWebController(InternalApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @GetMapping("/requests/mine")
    public String myRequests(@AuthenticationPrincipal CampusUserPrincipal principal,
                             @RequestParam(value = "page", defaultValue = "0") int page,
                             @RequestParam(value = "size", defaultValue = "10") int size,
                             Model model) {
        model.addAttribute("currentPage", "requests");

        PagedResponse<ItemRequestEntity> requests =
                apiClient.listMyRequests(principal.getUserId(), PageRequest.of(page, size));
        model.addAttribute("requests", requests);

        return "request/my-requests";
    }

    @GetMapping("/requests/pending")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public String pendingApprovals(@AuthenticationPrincipal CampusUserPrincipal principal,
                                    @RequestParam(value = "page", defaultValue = "0") int page,
                                    @RequestParam(value = "size", defaultValue = "10") int size,
                                    Model model) {
        model.addAttribute("currentPage", "approvals");

        PagedResponse<ItemRequestEntity> requests =
                apiClient.listPendingApprovals(principal.getUserId(), PageRequest.of(page, size));
        model.addAttribute("requests", requests);

        return "request/pending-approvals";
    }

    @GetMapping("/requests/{id}")
    public String requestDetail(@PathVariable Long id,
                                @AuthenticationPrincipal CampusUserPrincipal principal,
                                Model model) {
        model.addAttribute("currentPage", "requests");

        ItemRequestEntity request =
                apiClient.getRequest(id, principal.getUserId());
        model.addAttribute("request", request);
        model.addAttribute("isOwner", request.getRequesterId().equals(principal.getUserId()));
        model.addAttribute("isAdmin", principal.isAdmin());
        model.addAttribute("isTeacher", principal.isTeacher());

        return "request/detail";
    }

    @GetMapping("/requests/new/{itemId}")
    public String newRequestForm(@PathVariable Long itemId,
                                  @AuthenticationPrincipal CampusUserPrincipal principal,
                                  Model model) {
        model.addAttribute("currentPage", "requests");

        InventoryItemEntity item = apiClient.getItem(itemId);
        model.addAttribute("item", item);
        model.addAttribute("userId", principal.getUserId());

        return "request/new";
    }

    @PostMapping("/requests/new")
    public String createRequest(@AuthenticationPrincipal CampusUserPrincipal principal,
                                @RequestParam Long itemId, @RequestParam(defaultValue = "1") int quantity,
                                @RequestParam(required = false) String justification,
                                RedirectAttributes redirectAttributes) {
        apiClient.createRequest(principal.getUserId(), itemId, quantity, justification);
        redirectAttributes.addFlashAttribute("successMessage", "Request submitted successfully");
        return "redirect:/requests/mine";
    }

    @PostMapping("/requests/{id}/approve")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public String approveRequest(@AuthenticationPrincipal CampusUserPrincipal principal,
                                 @PathVariable Long id, RedirectAttributes redirectAttributes) {
        apiClient.approveRequest(id, principal.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "Request approved");
        return "redirect:/requests/pending";
    }

    @PostMapping("/requests/{id}/reject")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public String rejectRequest(@AuthenticationPrincipal CampusUserPrincipal principal,
                                @PathVariable Long id, @RequestParam String reason,
                                RedirectAttributes redirectAttributes) {
        apiClient.rejectRequest(id, principal.getUserId(), reason);
        redirectAttributes.addFlashAttribute("successMessage", "Request rejected");
        return "redirect:/requests/pending";
    }

    @PostMapping("/requests/{id}/cancel")
    public String cancelRequest(@AuthenticationPrincipal CampusUserPrincipal principal,
                                @PathVariable Long id, RedirectAttributes redirectAttributes) {
        apiClient.cancelRequest(id, principal.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "Request cancelled");
        return "redirect:/requests/mine";
    }

    @PostMapping("/requests/{id}/picked-up")
    @PreAuthorize("hasRole('ADMIN')")
    public String markPickedUp(@AuthenticationPrincipal CampusUserPrincipal principal,
                               @PathVariable Long id, RedirectAttributes redirectAttributes) {
        apiClient.markPickedUp(id, principal.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "Marked as picked up");
        return "redirect:/requests/" + id;
    }
}
