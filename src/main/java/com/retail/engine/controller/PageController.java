package com.retail.engine.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {

    @GetMapping("/")
    public String home() {
        return "redirect:/products";
    }

    @GetMapping("/products")
    public String catalogPage() {
        return "products/catalog";
    }

    @GetMapping("/products/new")
    public String createProductPage() {
        return "products/form";
    }

    @GetMapping("/products/{id}/edit")
    public String editProductPage(@PathVariable Long id) {
        return "products/form";
    }
}
