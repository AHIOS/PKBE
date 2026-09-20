package com.uci.pkbe.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebviewPageController {

    @GetMapping({"/webview", "/webview/"})
    public String webview() {
        return "forward:/webview/index.html";
    }
}
