package com.dateroem.backend.util;

import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

// "allow it to accept the +...@gmai... and also verify if an email is
// correct and a valid email". An address must be well formed - a "+tag"
// is fine (name+support@gmail.com reaches name@gmail.com) - and its domain
// must exist and be able to receive mail (it has MX records, or at least
// an address record). When DNS can't be asked at all (no internet), the
// address is accepted rather than turning a customer away for our sake.
public final class EmailAddresses {

    private static final Pattern FORMAT = Pattern.compile(
        "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*" +
        "@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$"
    );
    private static final long REMEMBER_MS = 60 * 60 * 1000L;
    private static final Map<String, Checked> DOMAINS = new ConcurrentHashMap<>();

    private record Checked(boolean receivesMail, long at) {
    }

    private EmailAddresses() {
    }

    /** The address with spaces trimmed and the domain in lower case, or null when blank. */
    public static String normalize(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String trimmed = email.strip();
        int at = trimmed.lastIndexOf('@');
        return at < 0 ? trimmed : trimmed.substring(0, at + 1) + trimmed.substring(at + 1).toLowerCase(Locale.ROOT);
    }

    /** Why the (normalized) address can't be used, or null when it can. */
    public static String problem(String email) {
        if (email.length() > 254 || !FORMAT.matcher(email).matches()) {
            return "That email address doesn't look right - check it and try again (for example name@gmail.com).";
        }
        String domain = email.substring(email.lastIndexOf('@') + 1);
        if (Boolean.FALSE.equals(receivesMail(domain))) {
            return "The email domain \"" + domain + "\" doesn't exist or can't receive email - check the spelling.";
        }
        return null;
    }

    // true or false, or null when DNS couldn't be asked.
    static Boolean receivesMail(String domain) {
        Checked known = DOMAINS.get(domain);
        if (known != null && System.currentTimeMillis() - known.at() < REMEMBER_MS) {
            return known.receivesMail();
        }
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        try {
            DirContext dns = new InitialDirContext(env);
            try {
                Attributes records = dns.getAttributes(domain, new String[] {"MX", "A", "AAAA"});
                boolean receives = acceptsMail(records.get("MX"))
                    || (records.get("MX") == null && (hasValues(records.get("A")) || hasValues(records.get("AAAA"))));
                DOMAINS.put(domain, new Checked(receives, System.currentTimeMillis()));
                return receives;
            } finally {
                dns.close();
            }
        } catch (NameNotFoundException e) {
            DOMAINS.put(domain, new Checked(false, System.currentTimeMillis()));
            return false;
        } catch (NamingException e) {
            return null;
        }
    }

    // A domain can publish the "null MX" (0 .) to say it never takes mail.
    private static boolean acceptsMail(Attribute mx) throws NamingException {
        if (mx == null) {
            return false;
        }
        NamingEnumeration<?> values = mx.getAll();
        while (values.hasMore()) {
            String record = String.valueOf(values.next()).trim();
            if (!record.endsWith(" .") && !record.equals(".")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasValues(Attribute attribute) {
        return attribute != null && attribute.size() > 0;
    }
}
