package com.dateroem.backend.util;

import java.util.regex.Pattern;

/**
 * Which mobile network a Kenyan number belongs to, from its prefix - "let
 * it verify for airtel only then we can switch to the safaricom if funds
 * are enough" (17 October 2026): Africa's Talking's shared sender only
 * reaches Airtel until the business pays for its own sender ID, so SMS
 * codes go only to the networks in app.sms.networks.
 *
 * Only prefixes the published lists agree on are named; anything else is
 * "other". A number moved to another network keeps its old prefix, so this
 * is a best guess - which is why a number that isn't recognised is never
 * asked for a code, rather than asked for one it may never receive.
 */
public final class KenyanNetworks {

    // The 3 digits after 254.
    private static final Pattern AIRTEL = Pattern.compile("^(73\\d|75[0-6]|78[5-9]|10[0-2])");
    private static final Pattern SAFARICOM = Pattern.compile("^(7[0-2]\\d|74[0-3]|74[568]|75[7-9]|76[89]|79\\d|11[0-5])");
    private static final Pattern TELKOM = Pattern.compile("^(77[0-6])");

    private KenyanNetworks() {}

    /** "airtel", "safaricom", "telkom" or "other" for a 2547XXXXXXXX / 2541XXXXXXXX number. */
    public static String of(String phone254) {
        if (phone254 == null || phone254.length() != 12 || !phone254.startsWith("254")) {
            return "other";
        }
        String rest = phone254.substring(3);
        if (AIRTEL.matcher(rest).find()) return "airtel";
        if (SAFARICOM.matcher(rest).find()) return "safaricom";
        if (TELKOM.matcher(rest).find()) return "telkom";
        return "other";
    }

    /** Any saved form (07..., 7..., 254..., +254 ...) as 254XXXXXXXXX, or null. */
    public static String to254(String anyForm) {
        if (anyForm == null) return null;
        String digits = anyForm.replaceAll("[^0-9]", "");
        return digits.length() < 9 ? null : "254" + digits.substring(digits.length() - 9);
    }
}
