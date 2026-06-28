package com.linkgrab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    height: androidx.compose.ui.unit.Dp = 16.dp,
    radius: androidx.compose.ui.unit.Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(radius))
            .background(MiuixTheme.colorScheme.surfaceVariant),
    )
}

@Composable
fun ParseLoadingSkeleton() {
    val shimmer = rememberShimmer(shimmerBounds = ShimmerBounds.View)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer(shimmer)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Input field skeleton
        ShimmerBox(modifier = Modifier.fillMaxWidth(), height = 56.dp, radius = 12.dp)

        Spacer(modifier = Modifier.height(16.dp))

        // Button skeleton
        ShimmerBox(modifier = Modifier.fillMaxWidth(), height = 48.dp, radius = 24.dp)

        Spacer(modifier = Modifier.height(12.dp))

        ShimmerBox(modifier = Modifier.fillMaxWidth(), height = 48.dp, radius = 24.dp)

        Spacer(modifier = Modifier.height(32.dp))

        // Image grid skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShimmerBox(modifier = Modifier.weight(1f), height = 160.dp, radius = 12.dp)
            ShimmerBox(modifier = Modifier.weight(1f), height = 160.dp, radius = 12.dp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShimmerBox(modifier = Modifier.weight(1f), height = 160.dp, radius = 12.dp)
            ShimmerBox(modifier = Modifier.weight(1f), height = 160.dp, radius = 12.dp)
        }
    }
}

@Composable
fun ResultLoadingSkeleton() {
    val shimmer = rememberShimmer(shimmerBounds = ShimmerBounds.View)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer(shimmer)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Title skeleton
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f), height = 24.dp, radius = 8.dp)

        Spacer(modifier = Modifier.height(12.dp))

        // Image grid skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShimmerBox(modifier = Modifier.weight(1f), height = 180.dp, radius = 12.dp)
            ShimmerBox(modifier = Modifier.weight(1f), height = 180.dp, radius = 12.dp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShimmerBox(modifier = Modifier.weight(1f), height = 180.dp, radius = 12.dp)
            ShimmerBox(modifier = Modifier.weight(1f), height = 180.dp, radius = 12.dp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Button skeleton
        ShimmerBox(modifier = Modifier.fillMaxWidth(), height = 48.dp, radius = 24.dp)
    }
}

@Composable
fun HistoryLoadingSkeleton() {
    val shimmer = rememberShimmer(shimmerBounds = ShimmerBounds.View)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer(shimmer)
            .padding(horizontal = 16.dp),
    ) {
        repeat(5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShimmerBox(modifier = Modifier.size(56.dp), radius = 8.dp)
                Spacer(modifier = Modifier.size(12.dp, 0.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f), height = 16.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f), height = 12.dp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
