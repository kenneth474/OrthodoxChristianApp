package com.orthodox.orthodoxapp.admin;

import com.orthodox.orthodoxapp.account.AccountService;
import com.orthodox.orthodoxapp.account.AppUser;
import com.orthodox.orthodoxapp.account.AppUserRepository;
import com.orthodox.orthodoxapp.auth.AuthException;
import com.orthodox.orthodoxapp.auth.SignedInUser;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MembersController: the Members page, for the app's administrators only
 * (see Admins).
 *
 *   GET /api/admin/members -> how many accounts there are, and for each one
 *                             the name, email, phone number, how they sign in,
 *                             and when the account was made (newest first)
 *
 * Passwords are never included, because the app doesn't know them. It keeps
 * only a BCrypt hash, a one-way scramble that can check a password but can't
 * be turned back into it (see AppUser). Nobody can see a member's password,
 * not even an administrator; that's what keeps it safe.
 *
 * Signed-in people who aren't administrators get 403 ("Forbidden").
 */
@RestController
@RequestMapping("/api/admin")
public class MembersController {

    /** One account, as the Members page shows it. */
    public record Member(String name, String email, String phoneNumber,
                         boolean password, boolean google, Instant joined) {
    }

    public record Members(int count, List<Member> members) {
    }

    private final AccountService accountService;
    private final AppUserRepository users;
    private final Admins admins;

    public MembersController(AccountService accountService, AppUserRepository users, Admins admins) {
        this.accountService = accountService;
        this.users = users;
        this.admins = admins;
    }

    @GetMapping("/members")
    public Members members(@AuthenticationPrincipal SignedInUser me) {
        if (!admins.isAdmin(accountService.get(me.id()))) {
            throw new AuthException(HttpStatus.FORBIDDEN, "Only the app's administrators can see the members list.");
        }
        List<Member> members = users.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(MembersController::member)
                .toList();
        return new Members(members.size(), members);
    }

    private static Member member(AppUser user) {
        return new Member(user.getDisplayName(), user.getEmail(), user.getPhoneNumber(),
                user.hasPassword(), user.getGoogleId() != null, user.getCreatedAt());
    }
}
