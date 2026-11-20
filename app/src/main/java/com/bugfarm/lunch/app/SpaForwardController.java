package com.bugfarm.lunch.app;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Lets the React app handle its own routes: a GET for a path that is not under
 * {@code /api} and has no dot in it (so not a file such as {@code /assets/app.js})
 * is forwarded to {@code /index.html}. Up to three path segments deep.
 */
@Controller
public class SpaForwardController {

    private static final String FIRST = "/{first:(?!api$)[^.]*}";
    private static final String SECOND = "/{second:[^.]*}";
    private static final String THIRD = "/{third:[^.]*}";

    @GetMapping({FIRST, FIRST + SECOND, FIRST + SECOND + THIRD})
    public String forward() {
        return "forward:/index.html";
    }
}
