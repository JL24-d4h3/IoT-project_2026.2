package com.taxi.gestion.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Portada y pantalla de acceso del Superadmin. */
@Controller
public class PaginasController {

    @GetMapping("/")
    public String inicio() {
        return "redirect:/registro";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
