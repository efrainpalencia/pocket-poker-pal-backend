package com.pocket_poker_pal_app.PocketPokerPalApp.controller;

import com.pocket_poker_pal_app.PocketPokerPalApp.dto.AdminRegisterRequest;
import com.pocket_poker_pal_app.PocketPokerPalApp.dto.AuthResponse;
import com.pocket_poker_pal_app.PocketPokerPalApp.dto.ClientRegisterRequest;
import com.pocket_poker_pal_app.PocketPokerPalApp.dto.LoginRequest;
import com.pocket_poker_pal_app.PocketPokerPalApp.entity.AdminUser;
import com.pocket_poker_pal_app.PocketPokerPalApp.entity.ClientUser;
import com.pocket_poker_pal_app.PocketPokerPalApp.entity.User;
import com.pocket_poker_pal_app.PocketPokerPalApp.security.CustomUserDetailsService;
import com.pocket_poker_pal_app.PocketPokerPalApp.security.JwtService;
import com.pocket_poker_pal_app.PocketPokerPalApp.service.*;
import com.pocket_poker_pal_app.PocketPokerPalApp.serviceImpl.UserServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
//@PropertySource("classpath:env.properties")
public class AuthController {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final AdminUserService adminUserService;
    private final ClientUserService clientUserService;
    private final UserServiceImpl userServiceImpl; // ✅ Using shared logic here
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private static final int VERIFICATION_EXPIRY_HOURS = 24;
    private final String verificationToken = UUID.randomUUID().toString();

    @Value("${VERIFICATION_LINK}")
    private String getVerificationLink;


    ApplicationEventPublisher eventPublisher;


    // ✅ Register Admin User
    @PostMapping("/register/admin")
    public Object registerAdmin(@RequestBody @Valid AdminRegisterRequest request) {

        if (userServiceImpl.emailExists(request.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("An admin already exists with this email: " + request.getEmail());
        }

        try {
            AdminUser adminUser = new AdminUser();
            adminUser.setEmail(request.getEmail());
            adminUser.setFirstName(request.getFirstName());
            adminUser.setLastName(request.getLastName());
            adminUser.setPassword(passwordEncoder.encode(request.getPassword()));
            adminUser.setEnabled(true); // TODO: Update to false after creating email verification.
            adminUser.setRole(User.Role.ADMIN);
            adminUser.setVerificationToken(verificationToken);
            adminUser.setVerificationTokenExpiry(LocalDateTime.now().plusHours(VERIFICATION_EXPIRY_HOURS));

            AdminUser savedAdmin = adminUserService.createAdminUser(adminUser);
            UserDetails userDetails = userDetailsService.loadUserByUsername(savedAdmin.getEmail());

            String accessToken = jwtService.generateAccessToken(userDetails);
            String refreshToken = jwtService.generateRefreshToken(userDetails);


            String verificationLink = "https://yourdomain.com/verify-email?token=" + verificationToken;
            emailService.sendVerificationEmail(adminUser.getEmail(), verificationLink);


            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new AuthResponse(accessToken, refreshToken));

        } catch (Exception e) {
//            e.printStackTrace(); // Logs to console
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }

    // ✅ Register Client User
    @PostMapping("/register/client")
    public Object registerClient(@RequestBody @Valid ClientRegisterRequest request) {

        if (userServiceImpl.emailExists(request.getEmail())) {
            return ResponseEntity.badRequest().body("Email already exists");
        }

        if (userServiceImpl.usernameExists(request.getUsername())) {
            return ResponseEntity.badRequest().body("Username already exists");
        }


        try {
            ClientUser clientUser = new ClientUser();
            clientUser.setEmail(request.getEmail());
            clientUser.setUsername(request.getUsername());
            clientUser.setPassword(passwordEncoder.encode(request.getPassword()));
            clientUser.setRole(User.Role.CLIENT);
            clientUser.setEnabled(true); // TODO: Update to false after creating email verification.
            clientUser.setVerificationToken(verificationToken);
            clientUser.setVerificationTokenExpiry(LocalDateTime.now().plusHours(VERIFICATION_EXPIRY_HOURS));

            ClientUser savedClientUser = clientUserService.createClientUser(clientUser);
            UserDetails userDetails = userDetailsService.loadUserByUsername(savedClientUser.getEmail());

            String accessToken = jwtService.generateAccessToken(userDetails);
            String refreshToken = jwtService.generateRefreshToken(userDetails);

            String verificationLink = getVerificationLink + verificationToken;
            emailService.sendVerificationEmail(clientUser.getEmail(), verificationLink);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new AuthResponse(accessToken, refreshToken));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }


    // ✅ Admin Login
    @PostMapping("/login/admin")
    public ResponseEntity<?> loginAdmin(@RequestBody @Valid LoginRequest request) {
        Optional<AdminUser> userOpt = adminUserService.findByEmail(request.getEmail());


        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials.");
        }

        AdminUser user = userOpt.get();

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials.");
        }

        if (!Boolean.TRUE.equals(user.isEnabled())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Account not verified.");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken));
    }

    // ✅ Client Login
    @PostMapping("/login/client")
    public ResponseEntity<?> loginClient(@RequestBody @Valid LoginRequest request) {
        Optional<ClientUser> userOpt = clientUserService.findByEmail(request.getEmail());

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials.");
        }

        ClientUser user = userOpt.get();

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials.");
        }

        if (!Boolean.TRUE.equals(user.isEnabled())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Account not verified.");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken));
    }



    // ✅ Verify user by token
    @GetMapping("/verify")
    public ResponseEntity<String> verifyUser(@RequestParam("token") String token) {

        Optional<? extends User> userOpt = userServiceImpl.findUserByVerificationToken(token);

        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid verification token.");
        }

        User user = userOpt.get();

        if (user.isEnabled()) {
            return ResponseEntity.badRequest().body("Account is already verified.");
        }

        if (user.getVerificationTokenExpiry() == null || user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body("Verification token expired. Please request a new verification link.");
        }

        user.setEnabled(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);

        // Save using appropriate service
        if (user.getRole() == User.Role.ADMIN) {
            adminUserService.updateAdminUser(user.getId(), (AdminUser) user);
        } else {
            clientUserService.updateClientUser(user.getId(), (ClientUser) user);
        }

        return ResponseEntity.ok("Account verified successfully. You can now log in.");
    }

    // ✅ Resend verification link
    @PostMapping("/resend-verification")
    public ResponseEntity<String> resendVerification(@RequestParam("email") String email) {

        Optional<? extends User> userOpt = userServiceImpl.findUserByEmail(email);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found with email: " + email);
        }

        User user = userOpt.get();

        if (user.isEnabled()) {
            return ResponseEntity.badRequest().body("Account already verified.");
        }

        if (user.getVerificationTokenExpiry() != null &&
                user.getVerificationTokenExpiry().isAfter(LocalDateTime.now().minusMinutes(5))) {
            return ResponseEntity.badRequest().body("Please wait before requesting a new verification link.");
        }

        String newToken = UUID.randomUUID().toString();
        user.setVerificationToken(newToken);
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(VERIFICATION_EXPIRY_HOURS));

        if (user.getRole() == User.Role.ADMIN) {
            adminUserService.updateAdminUser(user.getId(), (AdminUser) user);
        } else {
            clientUserService.updateClientUser(user.getId(), (ClientUser) user);
        }

        String verificationLink = getVerificationLink + newToken;
        emailService.sendVerificationEmail(user.getEmail(), verificationLink);

        return ResponseEntity.ok("Verification email resent successfully. Please check your inbox.");
    }

    // ✅ Request password reset
    @PostMapping("/password-reset-request")
    public ResponseEntity<String> requestPasswordReset(@RequestParam("email") String email) {

        Optional<? extends User> userOpt = userServiceImpl.findUserByEmail(email);

        if (userOpt.isEmpty() || !userOpt.get().isEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found with email: " + email);
        }

        User user = userOpt.get();

        userServiceImpl.sendGeneratedPasswordResetToken(user);

        return ResponseEntity.ok("Password reset link sent. It will expire in 1 hour.");
    }


    // ✅ Resend password reset link
    @PostMapping("/resend-password-reset")
    public ResponseEntity<String> resendPasswordReset(@RequestParam("email") String email) {

        Optional<? extends User> userOpt = userServiceImpl.findUserByEmail(email);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found with email: " + email);
        }

        User user = userOpt.get();

        // Optional: add throttling logic here
        if (user.getResetTokenExpiry() != null &&
                user.getResetTokenExpiry().isAfter(LocalDateTime.now().minusMinutes(5))) {
            return ResponseEntity.badRequest().body("Please wait before requesting another reset link.");
        }

        userServiceImpl.sendGeneratedPasswordResetToken(user);

        return ResponseEntity.ok("Password reset link resent successfully.");
    }



    // ✅ Reset password with token
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(
            @RequestParam("token") String token,
            @RequestParam("newPassword") String newPassword) {

        Optional<? extends User> userOpt = userServiceImpl.findUserByResetToken(token);

        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid password reset token.");
        }

        User user = userOpt.get();

        if (user.getResetTokenExpiry() == null ||
                user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body("Password reset token expired. Please request a new one.");
        }

        userServiceImpl.updatePassword(user, newPassword);

        return ResponseEntity.ok("Password reset successful.");
    }

}
