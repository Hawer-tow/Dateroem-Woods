package com.dateroem.woodsales.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.dateroem.woodsales.ui.common.AppIcons

// business rules: "the transaction section also have the stk push section
// and this should also work for any user since it is mostly for bulk
// goods" (isAdmin=false still sees this entry) - "the assigning are" and
// Send Money are admin-only (paying a partner/vendor, and matching real
// incoming payments, are both admin actions).
//
// "it does not feel so organized ... stk push taking half the space send
// money the other and view all assign transaction and refunds like a
// small circular area for one to click ... those rows feel overwhelming"
// - the two most-used, most-explained actions (STK Push, Send Money) keep
// their full description side by side; the rest become a row of compact
// quick-action circles instead of five more identical full-width cards.
@Composable
fun TransactionsHomeScreen(
    isAdmin: Boolean,
    // "only one phone the company phone will have the ability to read
    // the offline SMS messages ... they will actually never see that
    // option" - was gated by isAdmin alone, so every admin saw it even
    // though only one account is ever actually authorized to use it.
    canSyncSms: Boolean,
    onStkPush: () -> Unit,
    onSendMoney: () -> Unit,
    onAssign: () -> Unit,
    onSmsSync: () -> Unit,
    onViewAllUnassigned: () -> Unit,
    onRefunds: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(DateroemBackground).padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeCard(
                icon = AppIcons.PhoneSend,
                title = "Send STK Push",
                subtitle = "Prompt a payment for bulk goods - anyone can send this.",
                onClick = onStkPush,
                modifier = Modifier.weight(1f)
            )
            if (isAdmin) {
                HomeCard(
                    icon = AppIcons.Send,
                    title = "Send Money",
                    subtitle = "Pay a pending expense entry - pick a trip and category, then Pay.",
                    onClick = onSendMoney,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (isAdmin || canSyncSms) {
            Text(
                "Quick actions",
                color = DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                if (isAdmin) {
                    // "in assign transaction all those are there new
                    // transactions but i dont think those are all" -
                    // Assign Transactions only ever shows a claimed batch
                    // by design (splits the load across staff); View All
                    // Unassigned is the same data with nothing held back,
                    // useful right after a big SMS backlog import to see
                    // the true count.
                    QuickAction(icon = AppIcons.CheckCircle, label = "Assign", onClick = onAssign)
                    QuickAction(icon = AppIcons.Clipboard, label = "View All", onClick = onViewAllUnassigned)
                    // "when one drops a product ... where exactly do i go
                    // ... still cannot see it" - dropping an order only
                    // records the refund figure on the order itself; it's
                    // never a candidate for Assign Transactions above
                    // (that's for matching unclaimed INCOMING payments),
                    // so it gets its own quick action with the same
                    // Pay-out mechanics as Send Money.
                    QuickAction(icon = AppIcons.Undo, label = "Refunds", onClick = onRefunds)
                }
                // "the phone has the message app which have the mpesa
                // messages coming in ... i want the system to check the
                // messages" - only meaningful on the phone holding the
                // company SIM. Gated on canSyncSms specifically (not
                // isAdmin) - the backend enforces the same restriction
                // independently either way.
                if (canSyncSms) {
                    QuickAction(icon = AppIcons.Message, label = "SMS Sync", onClick = onSmsSync)
                }
            }
        }
    }
}

@Composable
private fun HomeCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(150.dp).clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = DateroemAccent, modifier = Modifier.size(26.dp))
            Text(
                title,
                color = DateroemAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(subtitle, color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(DateroemSurface, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = DateroemAccent, modifier = Modifier.size(24.dp))
        }
        Text(
            label,
            color = DateroemText,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
