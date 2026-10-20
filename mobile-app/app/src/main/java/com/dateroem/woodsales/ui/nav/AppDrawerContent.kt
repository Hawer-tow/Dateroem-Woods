package com.dateroem.woodsales.ui.nav

import androidx.compose.runtime.remember
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.ui.common.AppIcons
import com.dateroem.woodsales.data.session.Session
import com.dateroem.woodsales.ui.common.defaultForest
import com.dateroem.woodsales.ui.common.drawFireflies
import com.dateroem.woodsales.ui.common.drawPineTree
import androidx.compose.ui.graphics.vector.ImageVector
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemDrawer
import com.dateroem.woodsales.ui.theme.DateroemLine
import com.dateroem.woodsales.ui.theme.DateroemWordmark
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodOn
import kotlin.math.sin

// One tint per category so a row reads as "this is an expense" / "this is
// products" at a glance, not just a generic list of equal-weight text.
private fun sectionTint(section: Section): Color = when (section) {
    Section.Dashboard -> Color(0xFF8A6D3B)
    Section.Products -> Color(0xFF4FA35C)
    Section.Marketing -> Color(0xFFC0559E)
    Section.Expenses -> Color(0xFFD9A63F)
    Section.Transactions -> Color(0xFF4A90D9)
    Section.Finance -> Color(0xFFD4AF37)
    Section.Ai -> Color(0xFF8E6FD9)
    Section.BulkOrders -> Color(0xFFE07B39)
    Section.Trips -> Color(0xFF3FA8A0)
    Section.Trees -> Color(0xFF2E8B57)
    Section.Orders -> Color(0xFFB5651D)
}

@Composable
fun AppDrawerContent(
    session: Session,
    visibleSections: List<Section>,
    currentRoute: String?,
    onSelectSection: (Section) -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    // A count shown beside a section (e.g. products out of stock); 0 hides it.
    badges: Map<Section, Int> = emptyMap()
) {
    // "a quarter of the screen and a little more" - noticeably narrower
    // than a standard drawer (which defaults toward ~90% width and reads
    // as covering the whole screen) without going so narrow the icon+label
    // rows stop being legible.
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val drawerWidth = (screenWidth * 0.42f).coerceIn(220.dp, 260.dp)

    val infinite = rememberInfiniteTransition(label = "drawer-ambience")
    val sway by infinite.animateFloat(
        initialValue = -0.025f,
        targetValue = 0.025f,
        animationSpec = infiniteRepeatable(tween(4600, easing = LinearEasing), RepeatMode.Reverse),
        label = "sway"
    )
    val glowPulse by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val driftClock by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift"
    )

    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight().width(drawerWidth),
        drawerContainerColor = DateroemDrawer
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Warm light filtering through the canopy - a soft pulsing
            // glow rather than a static wash, so the panel feels alive
            // even before anything is tapped.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(DateroemAccent.copy(alpha = 0.22f * glowPulse), Color.Transparent),
                            center = Offset(0.3f, 0.15f),
                            radius = 500f
                        )
                    )
            )

            // Forest-and-river backdrop. More present than a first pass -
            // lighter dark overlay below lets it read as an actual scene
            // rather than a barely-visible texture.
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRiver(size)
                val treeCount = defaultForest.size
                defaultForest.forEachIndexed { index, tree ->
                    val x = 0.08f + index * (0.9f / (treeCount - 1))
                    val treeSway = sway * sin(tree.swayPhase)
                    drawPineTree(x, tree.scale * 0.7f, growth = 1f, tone = tree.tone.copy(alpha = 0.16f), canvasSize = size, swayRadians = treeSway)
                }
                drawFireflies(size, driftClockMs = driftClock * 60000, color = DateroemAccent)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(colors = listOf(DateroemDrawer.copy(alpha = 0.3f), DateroemDrawer.copy(alpha = 0.55f))))
            )

            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DateroemWordmark(modifier = Modifier.height(30.dp).padding(bottom = 6.dp))
                    Text(session.name, color = DateroemText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(session.roleName, color = DateroemAccent, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider(color = DateroemLine.copy(alpha = 0.12f))

                // Sections in a fixed, meaningful order under small group labels:
                // the day-to-day "sell" work first, then trips & what goes into
                // them, then the money side.
                val groups = remember(visibleSections) {
                    DRAWER_GROUPS
                        .map { (title, sections) -> title to sections.filter { it in visibleSections } }
                        .filter { it.second.isNotEmpty() }
                }
                LazyColumn(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                    groups.forEach { (title, sections) ->
                        if (title != null) item(key = "group-$title") { GroupLabel(title) }
                        items(sections, key = { it.route }) { section ->
                            DrawerRow(
                                section = section,
                                selected = currentRoute == section.route,
                                badge = badges[section] ?: 0,
                                onClick = { onSelectSection(section) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = DateroemLine.copy(alpha = 0.12f))
                // "the menu seems full ... where to place the settings" (20
                // October 2026) - Settings shares the pinned bottom row with
                // Sign out: always in view, never scrolled away, and the
                // sections above keep all their room. "instead of writing
                // settings just place the bold icon" - the word didn't fit
                // beside Sign out, so Settings is a bold gear on its own.
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clickable(onClickLabel = "Open settings", onClick = onOpenSettings)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.SettingsBold, contentDescription = "Settings", tint = DateroemText, modifier = Modifier.size(24.dp))
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(DateroemLine.copy(alpha = 0.12f)))
                    DrawerFooterAction(AppIcons.Logout, "Sign out", onSignOut, Modifier.weight(1f))
                }
            }
        }
    }
}

private val DRAWER_GROUPS: List<Pair<String?, List<Section>>> = listOf(
    null to listOf(Section.Dashboard),
    "SELL" to listOf(Section.Orders, Section.Products, Section.BulkOrders, Section.Marketing),
    "OPERATIONS" to listOf(Section.Trips, Section.Trees, Section.Expenses),
    "MONEY" to listOf(Section.Transactions, Section.Finance)
)

@Composable
private fun DrawerFooterAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = DateroemTextMuted, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.size(10.dp))
        Text(label, color = DateroemTextMuted, maxLines = 1)
    }
}

@Composable
private fun GroupLabel(title: String) {
    Text(
        title,
        color = DateroemTextMuted.copy(alpha = 0.6f),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp)
    )
}

// One slim line per section: a thin marker on the left when it's the current
// one, a plain icon, the label. No coloured discs - the group labels and the
// spacing do the organising.
@Composable
private fun DrawerRow(section: Section, selected: Boolean, badge: Int = 0, onClick: () -> Unit) {
    val tint = sectionTint(section)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) tint.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) tint else Color.Transparent)
        )
        // Each section keeps its own colour, as the emoji used to.
        Icon(section.icon, contentDescription = null, tint = tint, modifier = Modifier.padding(start = 12.dp, end = 6.dp).size(20.dp))
        Text(
            section.label,
            color = if (selected) DateroemText else DateroemTextMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 6.dp, top = 11.dp, bottom = 11.dp)
        )
        if (badge > 0) {
            Spacer(modifier = Modifier.weight(1f))
            // Wood-coloured count pill, e.g. products that have run out.
            Text(
                if (badge > 99) "99+" else badge.toString(),
                color = WoodOn,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DateroemAccent)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    }
}

// A soft translucent river curving through the panel, alongside the
// forest trees, for the "trees, rivers" ambience asked for.
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRiver(size: androidx.compose.ui.geometry.Size) {
    val path = Path().apply {
        moveTo(-20f, size.height * 0.35f)
        cubicTo(
            size.width * 0.3f, size.height * 0.25f,
            size.width * 0.5f, size.height * 0.55f,
            size.width * 0.85f, size.height * 0.45f
        )
        cubicTo(
            size.width * 1.05f, size.height * 0.4f,
            size.width * 1.1f, size.height * 0.7f,
            size.width * 1.3f, size.height * 0.75f
        )
        lineTo(size.width * 1.3f, size.height * 0.85f)
        cubicTo(
            size.width * 1.05f, size.height * 0.8f,
            size.width * 1.0f, size.height * 0.5f,
            size.width * 0.8f, size.height * 0.55f
        )
        cubicTo(
            size.width * 0.5f, size.height * 0.65f,
            size.width * 0.3f, size.height * 0.35f,
            -20f, size.height * 0.45f
        )
        close()
    }
    drawPath(path, color = Color(0xFF4FA8C9).copy(alpha = 0.10f))
}
