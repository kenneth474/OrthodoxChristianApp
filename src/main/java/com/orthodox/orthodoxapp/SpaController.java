package com.orthodox.orthodoxapp;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SpaController: lets every screen of the React app be opened directly.
 *
 * THE PROBLEM
 * The React app (the frontend/ folder) is a "single-page app" (SPA): one
 * HTML page, index.html, that draws every screen itself. When you tap the
 * Bible tab, React changes the address to /bible/PSA/50 WITHOUT asking the
 * server. But if you then refresh, or open a bookmark, the browser asks the
 * server for /bible/PSA/50, a page that doesn't exist as a file, and you'd
 * get "404 Not Found".
 *
 * THE FIX
 * For the React app's addresses, send back index.html instead. React then
 * starts, reads the address, and shows the right screen.
 *
 * "forward:" hands the request to /index.html inside the server; the address
 * in the browser stays /bible/PSA/50. @Controller (not @RestController)
 * means the returned text names a page, rather than being the reply itself.
 * Keep this list in step with the routes in frontend/src/components/TabsHost.tsx.
 */
@Controller
public class SpaController {

    @GetMapping({"/bible", "/bible/**", "/prayers", "/prayers/**", "/church", "/church/**",
            "/profile", "/profile/**", "/sign-in"})
    public String app() {
        return "forward:/index.html";
    }

    /**
     * The privacy policy: a plain page of its own (frontend/public/privacy.html),
     * readable without the app, at the short address Google's sign-in screen
     * links to.
     */
    @GetMapping("/privacy")
    public String privacy() {
        return "forward:/privacy.html";
    }
}
