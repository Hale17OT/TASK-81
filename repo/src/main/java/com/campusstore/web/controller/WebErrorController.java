package com.campusstore.web.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class WebErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);

        int statusCode = 500;
        if (status != null) {
            statusCode = Integer.parseInt(status.toString());
        }

        model.addAttribute("status", statusCode);
        model.addAttribute("error", HttpStatus.valueOf(statusCode).getReasonPhrase());
        // Do not expose internal error messages to the user — use generic messages only
        String safeMessage;
        if (statusCode == 404) {
            safeMessage = "The requested page was not found.";
        } else if (statusCode == 403) {
            safeMessage = "You do not have permission to access this resource.";
        } else {
            safeMessage = "An unexpected error occurred. Please try again later.";
        }
        model.addAttribute("message", safeMessage);
        model.addAttribute("currentPage", "error");

        if (statusCode == 404) {
            return "error/404";
        } else if (statusCode == 403) {
            return "error/403";
        } else if (statusCode == 500) {
            return "error/500";
        }

        return "error/error";
    }
}
