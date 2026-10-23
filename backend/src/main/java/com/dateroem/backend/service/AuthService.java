package com.dateroem.backend.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.AuthResponse;
import com.dateroem.backend.dto.GoogleSignInRequest;
import com.dateroem.backend.dto.LoginRequest;
import com.dateroem.backend.dto.RegisterRequest;
import com.dateroem.backend.security.GoogleIdTokens;
import com.dateroem.backend.security.GoogleIdTokens.GoogleUser;
import com.dateroem.backend.security.JwtService;
import com.dateroem.backend.util.EmailAddresses;

@Service
public class AuthService {

    private static final String ACCOUNT_COLUMNS =
        "u.User_ID, u.User_Name, u.Name, u.Password, u.Status, u.Role_ID, r.Name AS Role_Name, u.Can_Sync_Sms, u.Theme, " +
        "u.Must_Change_Password";
    // Also checked by UserService when the SuperAdmin corrects a username.
    static final Pattern USER_NAME = Pattern.compile("^[^\\s@]{3,30}$");
    // The colour themes an account can choose (USERS.Theme, migration 037):
    // the Living Forest greens (the default), dark or light.
    public static final String DEFAULT_THEME = "forest";
    private static final Set<String> THEMES = Set.of(DEFAULT_THEME, "dark", "light");

    private final JdbcTemplate jdbcTemplate;
    private final JwtService jwtService;
    private final MpesaClient mpesaClient;
    private final GoogleIdTokens googleIdTokens;
    private final DeviceService deviceService;
    private final ComputerService computerService;
    private final RoleService roleService;
    private final PhoneVerificationService phoneVerification;
    private final AppSettings appSettings;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(JdbcTemplate jdbcTemplate, JwtService jwtService, MpesaClient mpesaClient, GoogleIdTokens googleIdTokens,
                       DeviceService deviceService, ComputerService computerService, RoleService roleService,
                       PhoneVerificationService phoneVerification, AppSettings appSettings) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtService = jwtService;
        this.mpesaClient = mpesaClient;
        this.googleIdTokens = googleIdTokens;
        this.deviceService = deviceService;
        this.computerService = computerService;
        this.roleService = roleService;
        this.phoneVerification = phoneVerification;
        this.appSettings = appSettings;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String phoneNo = requiredPhone(request.phoneNo());
        String userName = chosenUserName(request.userName());

        // "verify if an email is correct and a valid email" - optional, but
        // when given it must be a real address (see EmailAddresses), and not
        // one another account already signs in with.
        String email = EmailAddresses.normalize(request.email());
        if (email != null) {
            String problem = EmailAddresses.problem(email);
            if (problem != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
            }
            Integer sameEmail = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM USERS WHERE LOWER(Email) = LOWER(?) AND Is_Deleted = FALSE",
                Integer.class, email
            );
            if (sameEmail != null && sameEmail > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An account already uses this email - sign in instead, or use another address.");
            }
        }

        // "push a code to the phone number they placed so that its verified"
        // - the SMS code step comes before the account exists.
        boolean phoneConfirmed = phoneVerification.useSignUpProof(phone254(phoneNo), request.phoneProof());

        int customerRoleId = customerRoleId();
        String hashed = passwordEncoder.encode(request.password());

        jdbcTemplate.update(
            "INSERT INTO USERS (User_Name, Name, Password, Email, Phone_NO, Phone_Verified_At, Status, Role_ID) " +
            "VALUES (?, ?, ?, ?, ?, " + (phoneConfirmed ? "NOW()" : "NULL") + ", 1, ?)",
            userName, request.name(), hashed, email, phoneNo, customerRoleId
        );
        int userId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        if (phoneConfirmed) {
            phoneVerification.linkProofToUser(phone254(phoneNo), request.phoneProof(), userId);
        }

        String token = jwtService.issueToken(userId);
        return new AuthResponse(token, userId, userName, request.name(), customerRoleId, "Customer", false, DEFAULT_THEME,
            false, appSettings.idleLockMinutes());
    }

    public AuthResponse login(LoginRequest request) {
        Map<String, Object> row = findAccount(request.userName());

        String storedHash = (String) row.get("Password");
        if (storedHash == null || !passwordEncoder.matches(request.password(), storedHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        if (request.device() == null) {
            return signedIn(row);
        }
        // Mobile app: the token is tied to this phone, and only an approved
        // phone gets one ("allows only one user per account per mobile phone").
        assertEnabled(row);
        int userId = (Integer) row.get("User_ID");
        // The phone app is the staff app; customers use the website. A
        // customer account is turned away here, before a phone is recorded
        // for it, so it can't reach staff screens or data on the phone app.
        if (!roleService.isStaff(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "This app is for staff. Please use the website to shop and track your orders.");
        }
        int deviceId = deviceService.approvedDeviceForLogin(userId, request.device());
        return signedIn(row, deviceId);
    }

    /**
     * The desktop app ("higher privilege ... even if the backend is not in
     * the same machine"): Admin and SuperAdmin accounts only, and only on an
     * approved computer (ComputerService). The token is tied to that
     * computer and lasts a working day (JwtService.issueComputerToken).
     */
    public AuthResponse desktopLogin(LoginRequest request) {
        Map<String, Object> row = findAccount(request.userName());

        String storedHash = (String) row.get("Password");
        if (storedHash == null || !passwordEncoder.matches(request.password(), storedHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        assertEnabled(row);
        int userId = (Integer) row.get("User_ID");
        // Checked before the computer is recorded, so other accounts can't
        // fill the admins' approval list.
        if (!roleService.isAdmin(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "The '" + row.get("Role_Name") + "' role can't use the desktop app - Admin or SuperAdmin only.");
        }
        int computerId = computerService.approvedComputerForLogin(userId, request.computer());
        return response(row, jwtService.issueComputerToken(userId, computerId));
    }

    // "Username or email": the username first, exactly as before; an email
    // address (a "+tag" one included) only when no username matches and
    // exactly one account uses that address.
    private Map<String, Object> findAccount(String userNameOrEmail) {
        String select = "SELECT " + ACCOUNT_COLUMNS + " FROM USERS u JOIN ROLE r ON r.Role_ID = u.Role_ID ";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            select + "WHERE u.User_Name = ? AND u.Is_Deleted = FALSE", userNameOrEmail
        );
        String email = EmailAddresses.normalize(userNameOrEmail);
        if (rows.isEmpty() && email != null && email.contains("@")) {
            rows = jdbcTemplate.queryForList(select + "WHERE LOWER(u.Email) = LOWER(?) AND u.Is_Deleted = FALSE", email);
            if (rows.size() > 1) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "More than one account uses this email - please sign in with your username.");
            }
        }
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return rows.get(0);
    }

    /**
     * "can my website have both the manual logging in and the google
     * assisted one" - the website's "Continue with Google". Returns either
     * a signed-in AuthResponse or, for someone new, {needsPhone,
     * needsUserName, name, email}: every account needs a phone number and a
     * username of the person's own choosing, and Google gives neither, so
     * the website asks for both and sends the same Google token back with
     * them.
     */
    @Transactional
    public Object googleSignIn(GoogleSignInRequest request) {
        GoogleUser google = googleIdTokens.verify(request.credential());

        // Signed up with this Google account before.
        List<Map<String, Object>> known = jdbcTemplate.queryForList(
            "SELECT " + ACCOUNT_COLUMNS + " FROM USERS u JOIN ROLE r ON r.Role_ID = u.Role_ID " +
            "WHERE u.Google_Sub = ? AND u.Is_Deleted = FALSE",
            google.subject()
        );
        if (!known.isEmpty()) {
            return signedIn(known.get(0));
        }

        // An existing username/password account with the same email is
        // never joined to the Google login automatically: emails typed at
        // sign-up aren't checked, so whoever made that account may not own
        // the address - joining them would hand one person's account to
        // another.
        String email = google.emailVerified() && google.email() != null ? google.email().trim() : null;
        if (email != null) {
            Integer sameEmail = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM USERS WHERE LOWER(Email) = LOWER(?) AND Is_Deleted = FALSE",
                Integer.class, email
            );
            if (sameEmail != null && sameEmail > 0) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account here already uses " + email + ". Please sign in with its username and password."
                );
            }
        }

        String name = google.name() != null && !google.name().isBlank()
            ? google.name().trim()
            : email != null ? email.substring(0, email.indexOf('@')) : "Customer";

        // Someone new: the site asks for their phone number and a username
        // of their own choosing, then calls again with both.
        if (request.phoneNo() == null || request.phoneNo().isBlank()
            || request.userName() == null || request.userName().isBlank()) {
            Map<String, Object> ask = new LinkedHashMap<>();
            ask.put("needsPhone", true);
            ask.put("needsUserName", true);
            ask.put("name", name);
            ask.put("email", email);
            ask.put("needsPhoneCode", phoneVerification.isRequired());
            return ask;
        }
        String phoneNo = requiredPhone(request.phoneNo());
        String userName = chosenUserName(request.userName());
        boolean phoneConfirmed = phoneVerification.useSignUpProof(phone254(phoneNo), request.phoneProof());

        // No password: this account signs in through Google.
        int customerRoleId = customerRoleId();
        jdbcTemplate.update(
            "INSERT INTO USERS (User_Name, Name, Password, Google_Sub, Email, Phone_NO, Phone_Verified_At, Status, Role_ID) " +
            "VALUES (?, ?, NULL, ?, ?, ?, " + (phoneConfirmed ? "NOW()" : "NULL") + ", 1, ?)",
            userName, name, google.subject(), email, phoneNo, customerRoleId
        );
        int userId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        if (phoneConfirmed) {
            phoneVerification.linkProofToUser(phone254(phoneNo), request.phoneProof(), userId);
        }
        return new AuthResponse(jwtService.issueToken(userId), userId, userName, name, customerRoleId, "Customer", false, DEFAULT_THEME,
            false, appSettings.idleLockMinutes());
    }

    // "make the phone number a must since a user must at least have a
    // number we can use for identification" - every public sign-up needs a
    // valid Kenyan number, checked by the same rule as M-Pesa payments.
    // Saved as 07XXXXXXXX, the way existing customers' numbers are written
    // (lookups match on the last 9 digits either way).
    private String requiredPhone(String raw) {
        String phone = raw == null ? "" : raw.trim();
        if (phone.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A phone number is required to create an account");
        }
        return "0" + mpesaClient.validateAndNormalizePhone(phone).substring(3);
    }

    // 07XXXXXXXX (as saved) -> 2547XXXXXXXX (as PHONE_VERIFICATIONS keeps it).
    private static String phone254(String savedPhone) {
        return "254" + savedPhone.substring(1);
    }

    // "one can even disable a certain phone user" - USERS.Status existed in
    // the schema already but was never actually checked anywhere, so
    // disabling an account had no real effect until now.
    private static void assertEnabled(Map<String, Object> row) {
        Boolean active = (Boolean) row.get("Status");
        if (active != null && !active) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has been disabled");
        }
    }

    private int customerRoleId() {
        return jdbcTemplate.queryForObject("SELECT Role_ID FROM ROLE WHERE Name = 'Customer'", Integer.class);
    }

    // A username is the person's own choice (website sign-up and Google
    // sign-up alike): 3 to 30 characters, no spaces, and no "@" - so it can
    // never be mistaken for someone's email address now that either can be
    // used to sign in.
    private String chosenUserName(String userName) {
        String chosen = userName == null ? "" : userName.strip();
        if (!USER_NAME.matcher(chosen).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Choose a username of 3 to 30 characters, with no spaces and no @.");
        }
        Integer taken = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM USERS WHERE User_Name = ? AND Is_Deleted = FALSE", Integer.class, chosen
        );
        if (taken != null && taken > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken");
        }
        return chosen;
    }

    private AuthResponse signedIn(Map<String, Object> row) {
        return signedIn(row, null);
    }

    private AuthResponse signedIn(Map<String, Object> row, Integer deviceId) {
        assertEnabled(row);
        int userId = (Integer) row.get("User_ID");
        return response(row, jwtService.issueToken(userId, deviceId));
    }

    private AuthResponse response(Map<String, Object> row, String token) {
        Boolean canSyncSms = (Boolean) row.get("Can_Sync_Sms");
        Object mustChange = row.get("Must_Change_Password");
        return new AuthResponse(
            token, (Integer) row.get("User_ID"), (String) row.get("User_Name"), (String) row.get("Name"),
            (Integer) row.get("Role_ID"), (String) row.get("Role_Name"), canSyncSms != null && canSyncSms,
            themeOf(row.get("Theme")),
            Boolean.TRUE.equals(mustChange) || (mustChange instanceof Number n && n.intValue() != 0),
            appSettings.idleLockMinutes()
        );
    }

    /** A stored theme, or the default for anything unexpected. */
    public static String themeOf(Object stored) {
        return stored instanceof String theme && THEMES.contains(theme) ? theme : DEFAULT_THEME;
    }

    /**
     * "a user can change the theme and it will be remembered" - saves the
     * account's theme so the website, the phone app and the desktop app all
     * open in it next time.
     */
    public Map<String, Object> saveTheme(int userId, String theme) {
        String chosen = theme == null ? "" : theme.strip().toLowerCase(Locale.ROOT);
        if (!THEMES.contains(chosen)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose forest, dark or light.");
        }
        jdbcTemplate.update("UPDATE USERS SET Theme = ? WHERE User_ID = ?", chosen, userId);
        return Map.of("theme", chosen);
    }
}
