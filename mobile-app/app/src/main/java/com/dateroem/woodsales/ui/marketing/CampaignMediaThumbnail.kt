package com.dateroem.woodsales.ui.marketing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dateroem.woodsales.data.model.Campaign
import com.dateroem.woodsales.ui.common.AppIcons
import com.dateroem.woodsales.ui.common.ProductThumbnail
import com.dateroem.woodsales.ui.common.VideoThumbnail
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground

// A campaign's own picture in the lists, for telling campaigns apart at a
// glance: its photo, or a still from the start of its video. The megaphone
// only stands in when the campaign has no media at all.
@Composable
fun CampaignMediaThumbnail(campaign: Campaign, modifier: Modifier = Modifier) {
    val media = campaign.media
    when {
        !media.isNullOrBlank() && campaign.mediaType == MEDIA_TYPE_VIDEO -> VideoThumbnail(media, modifier)
        !media.isNullOrBlank() -> ProductThumbnail(imageUrl = media, modifier = modifier)
        else -> Box(
            modifier = modifier.clip(RoundedCornerShape(8.dp)).background(DateroemBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.Megaphone, contentDescription = null, tint = DateroemAccent, modifier = Modifier.size(24.dp))
        }
    }
}
