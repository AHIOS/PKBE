package com.uci.pkbe.web;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Hidden
@Controller
public class WebviewPageController {

    @GetMapping({"/webview", "/webview/"})
    public String webview() {
        return "forward:/webview/index.html";
    }
}
