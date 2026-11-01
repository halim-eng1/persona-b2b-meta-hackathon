package com.example.persona.features.onboarding.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.R
import com.example.persona.ui.components.personaScreenBackground
import com.example.persona.ui.theme.spacing
import kotlinx.coroutines.launch

private data class OnboardingPageData(
    val eyebrow: String,
    val title: String,
    val description: String,
    val featureTags: List<String>,
    val animation: @Composable () -> Unit
)

/**
 * 3-Page Animated Feature Onboarding Screen.
 *
 * Implements a delightful, high-craft introduction to Persona B2B with:
 * - Fluid vector canvas animations for each core value proposition.
 * - Dynamic page indicators with smooth width expansion.
 * - Skip / Next / Get Started controls with responsive Cairo typography.
 * - Native RTL layout support for Egyptian & Arab merchants.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeatureOnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val spacing = MaterialTheme.spacing
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    BackHandler(enabled = pagerState.currentPage > 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }

    val pages = listOf(
        OnboardingPageData(
            eyebrow = stringResource(R.string.feature_onboarding_page1_eyebrow),
            title = stringResource(R.string.feature_onboarding_page1_title),
            description = stringResource(R.string.feature_onboarding_page1_desc),
            featureTags = listOf(
                stringResource(R.string.feature_onboarding_page1_tag1),
                stringResource(R.string.feature_onboarding_page1_tag2),
                stringResource(R.string.feature_onboarding_page1_tag3)
            ),
            animation = { NeuralChatWavesAnimation(modifier = Modifier.fillMaxSize()) }
        ),
        OnboardingPageData(
            eyebrow = stringResource(R.string.feature_onboarding_page2_eyebrow),
            title = stringResource(R.string.feature_onboarding_page2_title),
            description = stringResource(R.string.feature_onboarding_page2_desc),
            featureTags = listOf(
                stringResource(R.string.feature_onboarding_page2_tag1),
                stringResource(R.string.feature_onboarding_page2_tag2),
                stringResource(R.string.feature_onboarding_page2_tag3)
            ),
            animation = { KnowledgeSyncBrainAnimation(modifier = Modifier.fillMaxSize()) }
        ),
        OnboardingPageData(
            eyebrow = stringResource(R.string.feature_onboarding_page3_eyebrow),
            title = stringResource(R.string.feature_onboarding_page3_title),
            description = stringResource(R.string.feature_onboarding_page3_desc),
            featureTags = listOf(
                stringResource(R.string.feature_onboarding_page3_tag1),
                stringResource(R.string.feature_onboarding_page3_tag2),
                stringResource(R.string.feature_onboarding_page3_tag3)
            ),
            animation = { AutomatedCrmOrdersAnimation(modifier = Modifier.fillMaxSize()) }
        )
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .personaScreenBackground()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = spacing.xl, vertical = spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Top Navigation Bar: Skip & Brand Eyebrow ───────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back arrow when not on first page
                    if (pagerState.currentPage > 0) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier.size(spacing.touchTargetMin)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.feature_onboarding_back),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Decorative placeholder to balance layout
                        Spacer(modifier = Modifier.size(spacing.touchTargetMin))
                    }

                    // Top Skip Button
                    AnimatedVisibility(
                        visible = pagerState.currentPage < 2,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        TextButton(
                            onClick = onComplete,
                            modifier = Modifier.heightIn(min = 40.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.feature_onboarding_skip),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.s))

                // ── Middle: Horizontal Pager Content ──────────────────────────────
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { pageIndex ->
                    val page = pages[pageIndex]

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Animated Canvas Graphic Surface
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            shape = RoundedCornerShape(28.dp),
                            color = if (isDark) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0)
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                page.animation()
                            }
                        }

                        Spacer(modifier = Modifier.height(spacing.xl))

                        // Category Eyebrow Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ) {
                            Text(
                                text = page.eyebrow,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.md))

                        // Title
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeight = 34.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(spacing.s))

                        // Description
                        Text(
                            text = page.description,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 24.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = spacing.md)
                        )

                        Spacer(modifier = Modifier.height(spacing.lg))

                        // Feature Tag Pills
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.s, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(spacing.xs)
                        ) {
                            page.featureTags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                    border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1))
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.md))

                // ── Bottom Action Area: Indicators & Primary Button ───────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.lg)
                ) {
                    // Smooth Animated Dot Indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 3) {
                            val isSelected = pagerState.currentPage == i
                            val dotWidth by animateDpAsState(
                                targetValue = if (isSelected) 30.dp else 9.dp,
                                animationSpec = tween(durationMillis = 300),
                                label = "DotWidth_$i"
                            )
                            val dotColor by animateColorAsState(
                                targetValue = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                                },
                                animationSpec = tween(durationMillis = 300),
                                label = "DotColor_$i"
                            )

                            Box(
                                modifier = Modifier
                                    .height(9.dp)
                                    .width(dotWidth)
                                    .clip(CircleShape)
                                    .background(dotColor)
                                    .clickable {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(i)
                                        }
                                    }
                            )
                        }
                    }

                    // Main Action CTA Button (Next -> Get Started)
                    Button(
                        onClick = {
                            if (pagerState.currentPage < 2) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                onComplete()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        AnimatedContent(
                            targetState = pagerState.currentPage == 2,
                            transitionSpec = {
                                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> -width } + fadeOut()
                                )
                            },
                            label = "ButtonContentTransition"
                        ) { isLastPage ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isLastPage) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.feature_onboarding_get_started),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.feature_onboarding_next),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
