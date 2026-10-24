package com.orthodox.orthodoxapp.account;

import com.orthodox.orthodoxapp.auth.AuthException;
import com.orthodox.orthodoxapp.auth.SignedInUser;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * AccountController: endpoints for the signed-in person's own account.
 * SecurityConfig makes every address here require a signed-in user.
 *
 *   GET    /api/account             -> your details (AccountView)
 *   PUT    /api/account/profile     -> name, patron saint and feast day, home parish
 *   PUT    /api/account/appearance  -> text size, light/dark theme and style
 *   PUT    /api/account/phone       -> add, change or remove your phone number
 *   GET    /api/account/photo       -> your photo (a JPEG)
 *   POST   /api/account/photo       -> upload a new photo
 *   DELETE /api/account/photo       -> remove it
 *
 * @AuthenticationPrincipal gives us the SignedInUser from the session, so a
 * person can only ever see and change their OWN account.
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    // nameDay: the patron saint's feast day, as "MM-DD".
    public record ProfileRequest(String displayName, String patronSaint, String nameDay, String homeParish) {
    }

    public record AppearanceRequest(TextSize textSize, Theme theme, AppStyle style) {
    }

    public record PhoneRequest(String phone) {
    }

    private final AccountService accountService;
    private final ProfilePhotoService photoService;

    public AccountController(AccountService accountService, ProfilePhotoService photoService) {
        this.accountService = accountService;
        this.photoService = photoService;
    }

    @GetMapping
    public AccountView me(@AuthenticationPrincipal SignedInUser me) {
        return accountService.view(accountService.get(me.id()));
    }

    @PutMapping("/profile")
    public AccountView updateProfile(@AuthenticationPrincipal SignedInUser me, @RequestBody ProfileRequest body) {
        return accountService.view(accountService.updateProfile(
                me.id(), body.displayName(), body.patronSaint(), body.nameDay(), body.homeParish()));
    }

    @PutMapping("/appearance")
    public AccountView updateAppearance(@AuthenticationPrincipal SignedInUser me, @RequestBody AppearanceRequest body) {
        return accountService.view(accountService.updateAppearance(
                me.id(), body.textSize(), body.theme(), body.style()));
    }

    /** Blank removes the number. */
    @PutMapping("/phone")
    public AccountView updatePhone(@AuthenticationPrincipal SignedInUser me, @RequestBody PhoneRequest body) {
        return accountService.view(accountService.updatePhone(me.id(), body.phone()));
    }

    // ----- Photo -----

    /**
     * Sends the photo's bytes back as an image. ResponseEntity lets us set the
     * status and the Content-Type header ourselves; 404 = no photo yet.
     */
    @GetMapping("/photo")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal SignedInUser me) {
        return photoService.find(me.id())
                .map(jpeg -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(jpeg))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Uploads arrive as "multipart/form-data", the format browsers use for
     * files. @RequestParam("photo") picks out the file field named "photo".
     */
    @PostMapping("/photo")
    public AccountView uploadPhoto(@AuthenticationPrincipal SignedInUser me,
                                   @RequestParam("photo") MultipartFile photo) throws IOException {
        if (photo.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Please choose a photo.");
        }
        return accountService.view(photoService.save(me.id(), photo.getBytes()));
    }

    @DeleteMapping("/photo")
    public AccountView deletePhoto(@AuthenticationPrincipal SignedInUser me) {
        return accountService.view(photoService.delete(me.id()));
    }
}
