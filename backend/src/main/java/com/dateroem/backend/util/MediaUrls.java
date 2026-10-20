package com.dateroem.backend.util;

// Shared by ProductService (its own product listings) and OrderService
// ("place them like cards ... a copy of the image of the product" - the
// My Orders page's items never carried one before) - PRODUCTS.Image holds
// only a relative key; this resolves it against app.media.base-url so
// switching to a CDN in production is a one-line config change, not a
// database migration.
public final class MediaUrls {

    private MediaUrls() {
    }

    public static String resolve(String imageKey, String mediaBaseUrl) {
        if (imageKey == null || imageKey.isBlank()) {
            return null;
        }
        if (imageKey.startsWith("http://") || imageKey.startsWith("https://")) {
            return imageKey;
        }
        return mediaBaseUrl + imageKey;
    }

    // The reverse, for anything a client sends back: "i edited a product
    // all of a sudden the image is lost ... i didnt tamper with the image".
    // Clients only ever see resolved forms - the website "/media/x.jpg",
    // the mobile server "media/x.jpg", the desktop the full
    // "http://127.0.0.1:8000/media/x.jpg", a phone over the tunnel
    // ".../mobile-api/media/x.jpg" - and an edit form that echoed one back
    // stored it as the key (a loopback URL phones can't load), or failed to
    // parse it and sent nothing. Every form comes back to the bare key.
    public static String toKey(String image) {
        if (image == null) {
            return null;
        }
        String key = image.strip();
        int media = key.indexOf("/media/");
        if (media >= 0) {
            key = key.substring(media + "/media/".length());
        } else if (key.startsWith("media/")) {
            key = key.substring("media/".length());
        }
        int query = key.indexOf('?');
        return query >= 0 ? key.substring(0, query) : key;
    }
}
