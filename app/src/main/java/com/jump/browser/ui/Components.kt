package com.jump.browser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jump.browser.browser.TabInfo
import com.jump.browser.ui.theme.JumpAccent
import com.jump.browser.ui.theme.JumpPressed
import com.jump.browser.ui.theme.JumpRipple
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun ChromeIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Int = 24,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size.dp),
    )
}

@Composable
fun Modifier.jumpClickable(
    shape: Shape = RoundedCornerShape(18.dp),
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .clip(shape)
        .clickable(
            interactionSource = interaction,
            indication = ripple(color = JumpRipple),
            enabled = enabled,
            onClick = onClick,
        )
}

@Composable
fun ChromeButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Int = 22,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .jumpClickable(RoundedCornerShape(18.dp), enabled, onClick),
        contentAlignment = Alignment.Center,
    ) {
        ChromeIcon(icon, contentDescription, tint = tint, size = size)
    }
}

@Composable
fun PressableScale(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, tween(110), label = "press")
    Box(
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null) { },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun TabCountBadge(
    count: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = ReferenceIcons.Tabs,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = if (count > 99) ":)" else count.toString(),
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
fun Modifier.pressable(
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .clip(shape)
        .clickable(
            interactionSource = interaction,
            indication = ripple(color = JumpRipple),
            enabled = enabled,
            onClick = onClick,
        )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )
    }
}

@Composable
fun Favicon(
    tab: TabInfo,
    size: Int = 24,
    modifier: Modifier = Modifier,
) {
    val bitmap = tab.favicon
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null && !bitmap.isRecycled) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(
                imageVector = ReferenceIcons.Globe,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size((size - 4).dp),
            )
        }
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
fun Dot(selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .size(4.dp)
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 0.42f else 0.18f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
    )
}

@Composable
fun MenuCell(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .jumpClickable(RoundedCornerShape(18.dp), enabled, onClick)
            .padding(start = 4.dp, top = 10.dp, end = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(7.75.dp))
        BasicText(
            text = label,
            style = TextStyle(
                color = tint,
                fontSize = 12.sp,
                lineHeight = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            ),
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
@Composable
fun ListRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}


@Composable
fun JumpDialogButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
            .jumpClickable(RoundedCornerShape(18.dp), enabled, onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(
                color = if (enabled) JumpAccent else JumpAccent.copy(alpha = 0.38f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
            )
        ) {
            content()
        }
    }
}

@Composable
fun JumpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    autoFocus: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) runCatching { focus.requestFocus() }
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = TextStyle(
            fontSize = 14.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onBackground,
        ),
        cursorBrush = SolidColor(JumpAccent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onDone = { onImeAction?.invoke() },
            onGo = { onImeAction?.invoke() },
            onSearch = { onImeAction?.invoke() },
            onNext = { onImeAction?.invoke() },
        ),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focus),
        decorationBox = { inner ->
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            placeholder,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(JumpPressed))
            }
        },
    )
}

@Composable
fun JumpCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    label: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = onCheckedChange != null,
            ) { onCheckedChange?.invoke(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) JumpAccent else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (checked) JumpAccent else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.width(21.dp))
        BasicText(
            text = label,
            style = TextStyle(
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
            ),
        )
    }
}

@Composable
fun JumpAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(0.90f) }
    val contentAlpha = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }
    var cardWidth by remember { mutableStateOf(0) }
    var closing by remember { mutableStateOf(false) }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            contentAlpha.animateTo(0f, tween(120))
            onDismissRequest()
        }
    }

    LaunchedEffect(Unit) {
        launch { contentAlpha.animateTo(1f, tween(120)) }
        scale.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 520f))
    }

    val cardHeight = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    // The reference dialog window hugs its content; allow long dialogs to scroll.
    val maxDialogHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 48.dp
    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        // Real window animation, as the reference does it, instead of a
        // suppressed platform animation plus a Compose imitation.
        JumpDialogWindow(JumpWindowAnimation.POPUP)
        Box(
            Modifier
                .fillMaxSize()
                .imePadding()
                .background(Color.Black.copy(alpha = 0.40f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { close() },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Surface(
                modifier = modifier
                    .fillMaxWidth(0.8f)
                    .widthIn(max = 288.dp)
                    .heightIn(max = maxDialogHeight)
                    .onSizeChanged {
                        cardWidth = it.width
                        cardHeight.value = it.height.toFloat()
                    }
                    .offset { IntOffset(drag.value.roundToInt(), 0) }
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        alpha = contentAlpha.value
                        translationY = (1f - scale.value) * cardHeight.value * 0.10f
                    }
                    .border(0.dp, Color.Transparent)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (drag.value > cardWidth * 0.46f) {
                                        drag.animateTo(cardWidth.toFloat(), tween(180))
                                        close()
                                    } else {
                                        drag.animateTo(0f, tween(
                                            300,
                                            easing = FastOutSlowInEasing,
                                        ))
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { drag.animateTo(0f, tween(300)) }
                            },
                        ) { change, amount ->
                            change.consume()
                            scope.launch {
                                drag.snapTo((drag.value + amount).coerceIn(0f, cardWidth.toFloat()))
                            }
                        }
                    },
                shape = shape,
                color = containerColor,
                tonalElevation = tonalElevation,
                shadowElevation = 0.dp,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    ) {
                    icon?.let {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) { it() }
                    }
                    title?.let {
                        CompositionLocalProvider(
                            LocalTextStyle provides TextStyle(
                                fontSize = 16.sp,
                                lineHeight = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 6.dp),
                            ) { it() }
                        }
                    }
                    text?.let {
                        CompositionLocalProvider(
                            LocalTextStyle provides TextStyle(
                                fontSize = 14.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(top = if (title == null) 14.dp else 0.dp),
                            ) { it() }
                        }
                    }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}
