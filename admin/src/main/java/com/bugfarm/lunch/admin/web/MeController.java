package com.bugfarm.lunch.admin.web;

import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class MeController {

    public record Me(String username) {}

    @GetMapping("/me")
    public Me me(Principal principal) {
        return new Me(principal.getName());
    }
}
