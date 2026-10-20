package com.dateroem.woodsales.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// "for the mobile application as well as desktop lets do away with emojis"
// - the same line icons as the website (24x24, rounded 1.9 strokes, the
// open Feather/Lucide style), drawn here so there's no icon library to add.
// Each is drawn in black and takes its colour from Icon(tint = ...).
// Generated together with the desktop app's app/ui/icons.py so the two
// always match - change a shape in both.
object AppIcons {
    val Cart: ImageVector by lazy { lineIcon("cart", "M7.6 20a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0z", "M16.6 20a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0z", "M2.5 3h2.2l2.4 11.2a2 2 0 0 0 2 1.6h8.6a2 2 0 0 0 1.9-1.5L21.5 7H6") }
    val User: ImageVector by lazy { lineIcon("user", "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", "M8 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z") }
    val Menu: ImageVector by lazy { lineIcon("menu", "M4 6h16M4 12h16M4 18h16") }
    val Close: ImageVector by lazy { lineIcon("close", "M18 6 6 18M6 6l12 12") }
    val Search: ImageVector by lazy { lineIcon("search", "M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0z", "m20 20-4.2-4.2") }
    val Truck: ImageVector by lazy { lineIcon("truck", "M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2", "M15 18H9", "M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.62l-3.48-4.35A1 1 0 0 0 17.52 8H14", "M15 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z", "M5 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z") }
    val Package: ImageVector by lazy { lineIcon("package", "M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z", "M12 22V12M3.3 7 12 12l8.7-5M7.5 4.27l9 5.15") }
    val CheckCircle: ImageVector by lazy { lineIcon("checkCircle", "M2.5 12a9.5 9.5 0 1 0 19.0 0a9.5 9.5 0 1 0 -19.0 0z", "m8 12.5 2.8 2.8L16.5 9.5") }
    val Check: ImageVector by lazy { lineIcon("check", "M20 6 9 17l-5-5") }
    val Star: ImageVector by lazy { lineIcon("star", "M12 2.8l2.84 5.76 6.36.92-4.6 4.49 1.09 6.33L12 17.3l-5.69 3 1.09-6.33-4.6-4.49 6.36-.92z") }
    val Pin: ImageVector by lazy { lineIcon("pin", "M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0z", "M9 10a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z") }
    val Smartphone: ImageVector by lazy { lineIcon("smartphone", "M8.5 2h7.0a2.5 2.5 0 0 1 2.5 2.5v15.0a2.5 2.5 0 0 1 -2.5 2.5h-7.0a2.5 2.5 0 0 1 -2.5 -2.5v-15.0a2.5 2.5 0 0 1 2.5 -2.5z", "M11 18h2") }
    val Warning: ImageVector by lazy { lineIcon("warning", "M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z", "M12 9v4M12 17h.01") }
    val Megaphone: ImageVector by lazy { lineIcon("megaphone", "m3 11 18-5v12L3 14v-3z", "M11.6 16.8a3 3 0 1 1-5.8-1.6") }
    val Home: ImageVector by lazy { lineIcon("home", "M3 10.5 12 3l9 7.5", "M5 9.2V20a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V9.2") }
    val TreeRings: ImageVector by lazy { lineIcon("treeRings", "M12.00 2.20C12.85 2.19 13.68 3.03 14.55 3.32C15.41 3.60 16.45 3.49 17.19 3.92C17.93 4.36 18.38 5.27 18.99 5.94C19.60 6.61 20.56 7.15 20.87 7.95C21.18 8.75 20.77 9.83 20.86 10.73C20.95 11.63 21.49 12.50 21.40 13.35C21.32 14.20 20.71 14.99 20.37 15.82C20.02 16.66 19.91 17.73 19.33 18.35C18.75 18.98 17.64 19.10 16.87 19.57C16.09 20.04 15.50 20.88 14.69 21.16C13.88 21.45 12.91 21.26 12.00 21.30C11.09 21.34 10.05 21.68 9.24 21.40C8.42 21.12 7.86 20.13 7.11 19.61C6.36 19.09 5.29 18.94 4.74 18.29C4.20 17.64 4.25 16.54 3.86 15.72C3.47 14.90 2.56 14.22 2.40 13.38C2.24 12.54 2.73 11.58 2.89 10.69C3.05 9.80 2.99 8.83 3.36 8.05C3.73 7.28 4.56 6.75 5.12 6.04C5.68 5.33 6.01 4.24 6.73 3.80C7.45 3.35 8.59 3.63 9.46 3.36C10.34 3.10 11.15 2.21 12.00 2.20z", "M10.25 17.74C9.76 17.52 8.00 17.15 7.31 16.43C6.62 15.71 6.38 14.47 6.12 13.44C5.86 12.40 5.48 11.18 5.75 10.22C6.02 9.25 6.96 8.38 7.73 7.65C8.50 6.91 9.40 6.05 10.36 5.81C11.33 5.58 12.50 5.98 13.52 6.25C14.55 6.51 15.79 6.72 16.52 7.40C17.25 8.07 17.64 9.30 17.91 10.31C18.19 11.33 18.43 12.54 18.17 13.51C17.91 14.47 17.08 15.37 16.36 16.12C15.64 16.87 14.26 17.71 13.84 18.02", "M12 21.2V9.2", "M12 16.4 8.9 13.1", "M12 14.6 15.2 11.4", "M12 12.3 10.2 10") }
    val Tree: ImageVector by lazy { lineIcon("tree", "M12 2.5 6.5 10h3L5 16.5h14L14.5 10h3z", "M12 16.5V21.5") }
    val Sprout: ImageVector by lazy { lineIcon("sprout", "M7 20h10", "M10 20c5.5-2.5.8-6.4 3-10", "M9.5 9.4c1.1.8 1.8 2.2 2.3 3.7-2 .4-3.5.4-4.8-.3-1.2-.6-2.3-1.9-3-4.2 2.8-.5 4.4 0 5.5.8z", "M14.1 6a7 7 0 0 0-1.1 4c1.9-.1 3.3-.6 4.3-1.4 1-1 1.6-2.3 1.7-4.6-2.7.1-4 1-4.9 2z") }
    val Receipt: ImageVector by lazy { lineIcon("receipt", "M5 2.5v19l2.33-1.4 2.34 1.4L12 20.1l2.33 1.4 2.34-1.4L19 21.5v-19l-2.33 1.4-2.34-1.4L12 3.9 9.67 2.5 7.33 3.9z", "M9 8.5h6M9 12h6M9 15.5h4") }
    val Exchange: ImageVector by lazy { lineIcon("exchange", "M8 3 4 7l4 4", "M4 7h16", "m16 21 4-4-4-4", "M20 17H4") }
    val Bank: ImageVector by lazy { lineIcon("bank", "M3 21.5h18", "M6 18v-7M10 18v-7M14 18v-7M18 18v-7", "M12 2.5 20.5 7.5h-17z") }
    val Sparkles: ImageVector by lazy { lineIcon("sparkles", "M10 3l1.6 5.4L17 10l-5.4 1.6L10 17l-1.6-5.4L3 10l5.4-1.6z", "M18.5 14.5l.75 2.25L21.5 17.5l-2.25.75L18.5 20.5l-.75-2.25L15.5 17.5l2.25-.75z") }
    val Map: ImageVector by lazy { lineIcon("map", "M3 6l6-3 6 3 6-3v15l-6 3-6-3-6 3z", "M9 3v15M15 6v15") }
    val Users: ImageVector by lazy { lineIcon("users", "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", "M5 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z", "M22 21v-2a4 4 0 0 0-3-3.87", "M16 3.13a4 4 0 0 1 0 7.75") }
    private val SETTINGS_PATHS = arrayOf("M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z")
    val Settings: ImageVector by lazy { lineIcon("settings", *SETTINGS_PATHS) }
    // The menu's bottom row shows Settings as this icon alone (20 October
    // 2026: "instead of writing settings just place the bold icon"), so it
    // gets heavier strokes to hold its own beside "Sign out".
    val SettingsBold: ImageVector by lazy { lineIcon("settingsBold", *SETTINGS_PATHS, stroke = 2.6f) }
    val Bed: ImageVector by lazy { lineIcon("bed", "M2 4v16", "M2 8h18a2 2 0 0 1 2 2v10", "M2 17h20", "M6 8v9") }
    val Document: ImageVector by lazy { lineIcon("document", "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7z", "M14 2v4a2 2 0 0 0 2 2h4", "M10 9H8M16 13H8M16 17H8") }
    val Wallet: ImageVector by lazy { lineIcon("wallet", "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1", "M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4") }
    val Banknote: ImageVector by lazy { lineIcon("banknote", "M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z", "M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z", "M6 12h.01M18 12h.01") }
    val Undo: ImageVector by lazy { lineIcon("undo", "M9 14 4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11") }
    val PhoneSend: ImageVector by lazy { lineIcon("phoneSend", "M6.5 2h6.0a2.5 2.5 0 0 1 2.5 2.5v15.0a2.5 2.5 0 0 1 -2.5 2.5h-6.0a2.5 2.5 0 0 1 -2.5 -2.5v-15.0a2.5 2.5 0 0 1 2.5 -2.5z", "M8.5 18h2", "M15 9h6.5M18.5 6l3 3-3 3") }
    val Send: ImageVector by lazy { lineIcon("send", "M22 2 11 13", "M22 2l-7 20-4-9-9-4z") }
    val Clipboard: ImageVector by lazy { lineIcon("clipboard", "M9 2h6a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1v-2a1 1 0 0 1 1 -1z", "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2", "M12 11h4M12 16h4M8 11h.01M8 16h.01") }
    val ClipboardCheck: ImageVector by lazy { lineIcon("clipboardCheck", "M9 2h6a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1v-2a1 1 0 0 1 1 -1z", "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2", "m9 14 2 2 4-4") }
    val Message: ImageVector by lazy { lineIcon("message", "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z", "M7 8h10M7 12h6") }
    val Fingerprint: ImageVector by lazy { lineIcon("fingerprint", "M12 10a2 2 0 0 0-2 2c0 1.02-.1 2.51-.26 4", "M14 13.12c0 2.38 0 6.38-1 8.88", "M17.29 21.02c.12-.6.43-2.3.5-3.02", "M2 12a10 10 0 0 1 18-6", "M2 16h.01", "M21.8 16c.2-2 .131-5.354 0-6", "M5 19.5C5.5 18 6 15 6 12a6 6 0 0 1 .34-2", "M8.65 22c.21-.66.45-1.32.57-2", "M9 6.8a6 6 0 0 1 9 5.2v2") }
    val Lock: ImageVector by lazy { lineIcon("lock", "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2z", "M7 11V7a5 5 0 0 1 10 0v4") }
    val Mic: ImageVector by lazy { lineIcon("mic", "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 19v3") }
    val Stop: ImageVector by lazy { lineIcon("stop", "M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z") }
    val Play: ImageVector by lazy { lineIcon("play", "M7 4.5v15l12-7.5z") }
    val Loader: ImageVector by lazy { lineIcon("loader", "M21 12a9 9 0 1 1-6.22-8.56") }
    val Logout: ImageVector by lazy { lineIcon("logout", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "m16 17 5-5-5-5", "M21 12H9") }
    val Video: ImageVector by lazy { lineIcon("video", "m16 13 5.2 3.5a.5.5 0 0 0 .8-.4V7.9a.5.5 0 0 0-.8-.4L16 11", "M4 6h10a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z") }
    val Image: ImageVector by lazy { lineIcon("image", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z", "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z", "m21 15-3.1-3.1a2 2 0 0 0-2.8 0L6 21") }
    val TrendingUp: ImageVector by lazy { lineIcon("trendingUp", "M22 7 13.5 15.5 8.5 10.5 2 17", "M16 7h6v6") }
    val Lightbulb: ImageVector by lazy { lineIcon("lightbulb", "M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5", "M9 18h6", "M10 22h4") }
    val Database: ImageVector by lazy { lineIcon("database", "M3 5a9 3 0 1 0 18 0a9 3 0 1 0 -18 0z", "M3 5v14a9 3 0 0 0 18 0V5", "M3 12a9 3 0 0 0 18 0") }
    val Server: ImageVector by lazy { lineIcon("server", "M4 2h16a2 2 0 0 1 2 2v4a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-4a2 2 0 0 1 2 -2z", "M4 14h16a2 2 0 0 1 2 2v4a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-4a2 2 0 0 1 2 -2z", "M6 6h.01M6 18h.01") }
    val Globe: ImageVector by lazy { lineIcon("globe", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z", "M2 12h20", "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20") }
    val Wrench: ImageVector by lazy { lineIcon("wrench", "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z") }
    val Link: ImageVector by lazy { lineIcon("link", "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71", "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71") }
    val Route: ImageVector by lazy { lineIcon("route", "M3 19a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z", "M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15", "M15 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z") }
    val Code: ImageVector by lazy { lineIcon("code", "m16 18 6-6-6-6", "m8 6-6 6 6 6") }
    val Layers: ImageVector by lazy { lineIcon("layers", "M12 2 2 7l10 5 10-5-10-5z", "m2 17 10 5 10-5", "m2 12 10 5 10-5") }
    val Camera: ImageVector by lazy { lineIcon("camera", "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z", "M9 13a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z") }
    val Refresh: ImageVector by lazy { lineIcon("refresh", "M21 12a9 9 0 0 1-15.4 6.36L3 16", "M3 21v-5h5", "M3 12a9 9 0 0 1 15.4-6.36L21 8", "M21 3v5h-5") }
}

private fun lineIcon(name: String, vararg paths: String, stroke: Float = 1.9f): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    for (path in paths) {
        builder.addPath(
            pathData = addPathNodes(path),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = stroke,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}
