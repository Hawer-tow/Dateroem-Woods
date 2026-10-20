package com.dateroem.backend.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.AuthResponse;
import com.dateroem.backend.dto.GoogleSignInRequest;
import com.dateroem.backend.dto.LoginRequest;
import com.dateroem.backend.dto.PhoneCodeRequest;
import com.dateroem.backend.dto.RegisterRequest;
import com.dateroem.backend.security.DesktopClientFilter;
import com.dateroem.backend.security.GoogleIdTokens;
import com.dateroem.backend.security.JwtService;
import com.dateroem.backend.security.MobileDeviceFilter;
import com.dateroem.backend.service.AuthService;
import com.dateroem.backend.service.PhoneVerificationService;
import com.dateroem.backend.util.JsonKeys;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final GoogleIdTokens googleIdTokens;
    private final JdbcTemplate jdbcTemplate;
    private final PhoneVerificationService phoneVerification;

    public AuthController(AuthService authService, JwtService jwtService, GoogleIdTokens googleIdTokens, JdbcTemplate jdbcTemplate,
                          PhoneVerificationService phoneVerification) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.googleIdTokens = googleIdTokens;
        this.jdbcTemplate = jdbcTemplate;
        this.phoneVerification = phoneVerification;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    // The desktop app's server marks its calls X-Client: desktop - those
    // sign-ins are Admin/SuperAdmin only and need an approved computer.
    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request,
                              @RequestHeader(value = MobileDeviceFilter.CLIENT_HEADER, required = false) String client) {
        if (DesktopClientFilter.CLIENT.equalsIgnoreCase(client)) {
            return authService.desktopLogin(request);
        }
        return authService.login(request);
    }

    // Which sign-in options the website shows: the Google button appears
    // only once a Google client ID is configured. phoneCode: whether sign-up
    // asks for an SMS code to confirm the phone number (SMS set up), and
    // phoneCodeFor which networks' numbers get one (["Airtel"]; null = all).
    @GetMapping("/providers")
    public Map<String, Object> providers() {
        Map<String, Object> providers = new HashMap<>();
        providers.put("googleClientId", googleIdTokens.clientId());
        providers.put("phoneCode", phoneVerification.isRequired());
        providers.put("phoneCodeFor", phoneVerification.isRequired() ? phoneVerification.networkNames() : null);
        return providers;
    }

    // Sign-up's SMS code step ("push a code to the phone number they placed
    // so that its verified"): send a code, then check it - a correct code
    // returns the {phoneProof} that /register and /google then carry.
    @PostMapping("/phone/send-code")
    public Map<String, Object> sendSignUpCode(@RequestBody PhoneCodeRequest request) {
        return phoneVerification.sendCode(request.phoneNo(), null);
    }

    @PostMapping("/phone/verify")
    public Map<String, Object> verifySignUpCode(@RequestBody PhoneCodeRequest request) {
        return phoneVerification.verifyForSignUp(request.phoneNo(), request.code());
    }

    // The same for a signed-in customer whose number isn't confirmed yet
    // (asked at checkout): a correct code makes it the account's number.
    @PostMapping("/me/phone/send-code")
    public Map<String, Object> sendAccountCode(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                               @RequestBody PhoneCodeRequest request) {
        return phoneVerification.sendCode(request.phoneNo(), activeUser(authHeader));
    }

    @PostMapping("/me/phone/verify")
    public Map<String, Object> verifyAccountCode(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                 @RequestBody PhoneCodeRequest request) {
        return phoneVerification.verifyForAccount(activeUser(authHeader), request.phoneNo(), request.code());
    }

    // The signed-in person's colour theme ({"theme": "forest" | "dark" |
    // "light"}), saved from any of the apps; answers {"theme": ...}.
    @PutMapping("/me/theme")
    public Map<String, Object> saveTheme(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                         @RequestBody Map<String, String> body) {
        return authService.saveTheme(activeUser(authHeader), body == null ? null : body.get("theme"));
    }

    private int activeUser(String authHeader) {
        int userId = jwtService.validate(authHeader)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
        Integer active = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM USERS WHERE User_ID = ? AND Status = 1 AND Is_Deleted = FALSE", Integer.class, userId
        );
        if (active == null || active == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has been disabled");
        }
        return userId;
    }

    // An AuthResponse, or {needsPhone, name, email} for someone new.
    @PostMapping("/google")
    public Object google(@RequestBody GoogleSignInRequest request) {
        return authService.googleSignIn(request);
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        int userId = jwtService.validate(authHeader)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
        // roleName drives what a mobile-app user can see/do (business rules:
        // "if the role name associated with that is employee then the user
        // can only do staff job... admin/superAdmin etc") - the frontend
        // needs the name, not just Role_ID, to gate UI without a lookup.
        Map<String, Object> me = JsonKeys.camelCaseKeys(jdbcTemplate.queryForMap(
            // Phone_NO: the signed-in person's own number, so the website can
            // fill in the M-Pesa number at checkout and the email window's phone
            // - once Phone_Verified_At shows it was confirmed by an SMS code.
            "SELECT u.User_ID, u.User_Name, u.Name, u.Email, u.Phone_NO, u.Phone_Verified_At, u.Role_ID, " +
            "r.Name AS Role_Name, u.Can_Sync_Sms, u.Theme " +
            "FROM USERS u JOIN ROLE r ON r.Role_ID = u.Role_ID " +
            "WHERE u.User_ID = ? AND u.Is_Deleted = FALSE",
            userId
        ));
        me.put("theme", AuthService.themeOf(me.get("theme")));
        // Customers confirm their number before their first order (see
        // OrderService.createOrder) - the website asks at checkout.
        me.put("phoneCodeRequired", "Customer".equals(me.get("roleName")) && me.get("phoneVerifiedAt") == null
            && phoneVerification.requiredFor((String) me.get("phoneNo")));
        return me;
    }
}
