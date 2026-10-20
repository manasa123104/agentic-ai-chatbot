package com.agentic.chatbot.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The previous Website generated with code page is archived in
 * {@code archive/website-generated/}. Live visitors go to Home.
 */
@Controller
public class CodeViewerController {

    @GetMapping("/code")
    public String code() {
        return "redirect:/";
    }
}
