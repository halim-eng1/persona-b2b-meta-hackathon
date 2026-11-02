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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val animation: @Composable (isActive: Boolean) -> Unit
)

/**
 * 3-Page Animated Feature Onboarding Screen.
 *
 * Implements a delightful, high-craft introduction to Persona B2B with:
 * - Fluid, zero-allocation vector canvas animations honoring Reduced Motion.
 * - Dynamic page indicators with accessible 48dp touch targets and TalkBack semantics.
 * - ResponsiveCairo typography and scroll resilience under 200% font scaling.
 * - Natural locale alignment (RTL for Arabic, LTR for English).
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

    val p1Eyebrow = stringResource(R.string.feature_onboarding_page1_eyebrow)
    val p1Title = stringResource(R.string.feature_onboarding_page1_title)
    val p1Desc = stringResource(R.string.feature_onboarding_page1_desc)
    val p1Tag1 = stringResource(R.string.feature_onboarding_page1_tag1)
    val p1Tag2 = stringResource(R.string.feature_onboarding_page1_tag2)
    val p1Tag3 = stringResource(R.string.feature_onboarding_page1_tag3)

    val p2Eyebrow = stringResource(R.string.feature_onboarding_page2_eyebrow)
    val p2Title = stringResource(R.string.feature_onboarding_page2_title)
    val p2Desc = stringResource(R.string.feature_onboarding_page2_desc)
    val p2Tag1 = stringResource(R.string.feature_onboarding_page2_tag1)
    val p2Tag2 = stringResource(R.string.feature_onboarding_page2_tag2)
    val p2Tag3 = stringResource(R.string.feature_onboarding_page2_tag3)

    val p3Eyebrow = stringResource(R.string.feature_onboarding_page3_eyebrow)
    val p3Title = stringResource(R.string.feature_onboarding_page3_title)
    val p3Desc = stringResource(R.string.feature_onboarding_page3_desc)
    val p3Tag1 = stringResource(R.string.feature_onboarding_page3_tag1)
    val p3Tag2 = stringResource(R.string.feature_onboarding_page3_tag2)
    val p3Tag3 = stringResource(R.string.feature_onboarding_page3_tag3)

    // Memoize the pages bundle to prevent redundant resource querying on recomposition
    val pages = remember(p1Title, p2Title, p3Title) {
        listOf(
            OnboardingPageData(
                eyebrow = p1Eyebrow,
                title = p1Title,
                description = p1Desc,
                featureTags = listOf(p1Tag1, p1Tag2, p1Tag3),
                animation = { isActive -> NeuralChatWavesAnimation(isActive = isActive, modifier = Modifier.fillMaxSize()) }
            ),
            OnboardingPageData(
                eyebrow = p2Eyebrow,
                title = p2Title,
                description = p2Desc,
                featureTags = listOf(p2Tag1, p2Tag2, p2Tag3),
                animation = { isActive -> KnowledgeSyncBrainAnimation(isActive = isActive, modifier = Modifier.fillMaxSize()) }
            ),
            OnboardingPageData(
                eyebrow = p3Eyebrow,
                title = p3Title,
                description = p3Desc,
                featureTags = listOf(p3Tag1, p3Tag2, p3Tag3),
                animation = { isActive -> AutomatedCrmOrdersAnimation(isActive = isActive, modifier = Modifier.fillMaxSize()) }
            )
        )
    }

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
                    .height(spacing.touchTargetMin),
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
                    Spacer(modifier = Modifier.size(spacing.touchTargetMin))
                }

                // Top Skip Button with full 48dp minimum touch target
                AnimatedVisibility(
                    visible = pagerState.currentPage < 2,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TextButton(
                        onClick = onComplete,
                        modifier = Modifier.heightIn(min = spacing.touchTargetMin),
                        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.s)
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

            Spacer(modifier = Modifier.height(spacing.xs))

            // ── Middle: Horizontal Pager Content ──────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                val page = pages[pageIndex]
                val isPageActive = pagerState.currentPage == pageIndex
                val pageScrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(pageScrollState)
                        .padding(horizontal = spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Animated Canvas Graphic Surface with responsive aspect ratio & Tonal Calm
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 240.dp)
                            .aspectRatio(1.35f, matchHeightConstraintsFirst = false)
                            .semantics { contentDescription = page.title },
                        shape = RoundedCornerShape(24.dp),
                        color = if (isDark) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                        },
                        border = if (isDark) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        } else {
                            null // Tonal calm: borderless on light background per Persona specs
                        }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            page.animation(isPageActive)
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.lg))

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

                    Spacer(modifier = Modifier.height(spacing.md))

                    // Feature Tag Pills (Tonal Calm)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.s, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        page.featureTags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
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

                    Spacer(modifier = Modifier.height(spacing.s))
                }
            }

            Spacer(modifier = Modifier.height(spacing.s))

            // ── Bottom Action Area: Indicators & Primary Button ───────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.lg)
            ) {
                // Smooth Animated Dot Indicators with 48dp minimum touch target & TalkBack semantics
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 3) {
                        val isSelected = pagerState.currentPage == i
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) 28.dp else 8.dp,
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
                        val pageNumber = i + 1
                        val indicatorDescription = stringResource(R.string.feature_onboarding_page_indicator, pageNumber, 3)

                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = spacing.touchTargetMin)
                                .clickable(
                                    role = Role.Tab,
                                    onClickLabel = indicatorDescription
                                ) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(i)
                                    }
                                }
                                .semantics {
                                    role = Role.Tab
                                    selected = isSelected
                                    contentDescription = indicatorDescription
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(8.dp)
                                    .width(dotWidth)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
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
