package com.paulpladziewicz.fremontmi.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home () {
        return "home";
    }

    @GetMapping("/things-to-do")
    public String thingsToDo () {
        return "things-to-do";
    }

    @GetMapping("/error")
    public String error () {
        return "error";
    }
}
