package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/")
    public Map<String, String> index() {
        return Map.of("status", "ok", "app", "demo");
    }

    @GetMapping("/hello")
    public Map<String, String> hello(@RequestParam(defaultValue = "world") String name) {
        return Map.of("message", "Hello, " + name + "!");
    }
}
