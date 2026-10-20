package com.dateroem.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// EnableScheduling powers SmsQueueService's periodic reconciliation sweep.
@SpringBootApplication
@EnableScheduling
public class BackendApplication {

    public static void main(String[] args) {
        // Safaricom (and the AI providers) are reached over IPv4 only on this
        // network: with dual-stack the JVM tries IPv6 first, which has no
        // route, and every payment waited out a 15s connect timeout before
        // falling back. Must be set before anything opens a socket.
        System.setProperty("java.net.preferIPv4Stack", "true");
        // Re-resolve hostnames on every call: Safaricom's sandbox returns a
        // different address each time and the JVM would otherwise keep
        // using a dead one for 30 seconds (see MpesaClient).
        java.security.Security.setProperty("networkaddress.cache.ttl", "0");
        java.security.Security.setProperty("networkaddress.cache.negative.ttl", "0");
        SpringApplication.run(BackendApplication.class, args);
    }

}
