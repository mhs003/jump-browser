package com.jump.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A floating surface anchored under the title bar, rendered in its own window
 * so the platform animates it.
 *
 * Compose transitions only run their exit while the composable stays in the
 * tree, and these surfaces are switched by a boolean, so the leave animation
 * never played. As windows they get the reference popup animation in both
 * directions, exactly like the reference's floating windows.
 *
 * [scrimAlpha] dims the page behind. The dim is a separate window that fades
 * rather than slides, so it does not drag the background with the popup.
 */
@Composable
fun TopAnchoredPopup(
    onDismiss: () -> Unit,
    scrimAlpha: Float = 0.32f,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        JumpDialogWindow(JumpWindowAnimation.SCRIM)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        )
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        JumpDialogWindow(JumpWindowAnimation.POPUP)
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        ) {
            Column {
                Box(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp),
                    contentAlignment = contentAlignment,
                ) {
                    content()
                }
            }
        }
    }
}
