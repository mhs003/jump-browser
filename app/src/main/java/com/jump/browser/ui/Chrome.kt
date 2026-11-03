package com.jump.browser.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.jump.browser.ui.theme.Icon
import com.jump.browser.ui.theme.MaterialTheme
import com.jump.browser.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import com.jump.browser.browser.TabInfo
import com.jump.browser.ui.theme.JumpAccent
import com.jump.browser.ui.theme.Surface

enum class MenuAction {
    NIGHT, BOOKMARKS, HISTORY, DOWNLOADS, INCOGNITO, SHARE, ADD_BOOKMARK, DESKTOP, TOOLS, SETTINGS,
    FIND, SAVE_PAGE, SAVED_PAGES, TRANSLATE, VIEW_SOURCE, FULLSCREEN, SHOW_IMAGES,
    RESOURCE_SNIFFER, USER_AGENT, NETWORK_LOG, SCAN_QR, ADD_HOME, READ_ALOUD, AI,
    ORIENTATION, AD_BLOCK, MARK_AD, TEXT_SIZE, CLEAR_DATA, CUSTOMIZE_MENU, EXIT,
    NEW_TAB, NEW_INCOGNITO_TAB, READER, PRINT, PAGE_INFO, COPY_URL, RELOAD, HOME, HELP, ABOUT,
}

data class MenuEntry(val label: String, val icon: ImageVector, val action: MenuAction, val accent: Boolean = false)

/** Default menu order used by the menu customizer. */
val DEFAULT_MENU_ORDER: List<String> = listOf(
    "Night mode", "Bookmarks", "History", "Downloads", "Incognito\nmode",
    "Share", "Add\nbookmark", "Desktop site", "Tools", "Settings",
    "Find in\npage", "Save", "Saved\npages", "Translate", "View\nsource",
    "Full-screen", "Show\nimages", "Resource\nsniffer", "User-agent", "Network\nlog",
    "Scan QR\ncode", "Add to\nhome", "Read\naloud", "AI", "Orientation",
    "Ad\nblocking", "Mark as\nad", "Text size", "Clear data", "Customize\nmenu",
)

@Composable
fun TitleBar(
    tab: TabInfo?,
    bookmark: Boolean,
    urlFieldContent: String = "TITLE",
    onOpenAddress: () -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onTitleLongClick: () -> Unit,
    onSecureClick: () -> Unit = {},
) {
    val isHome = tab?.isHome != false
    val leftIcon = when {
        isHome -> ReferenceIcons.Search
        tab?.incognito == true && tab.secure -> ReferenceIcons.Incognito
        tab?.secure == true -> ReferenceIcons.Secure
        else -> ReferenceIcons.Search
    }
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isHome) {
            Box(
                Modifier.size(48.dp).jumpClickable { onLeftClick() },
                contentAlignment = Alignment.Center,
            ) { SearchGlyph() }
        } else {
            ChromeButton(
                icon = leftIcon,
                contentDescription = if (tab?.secure == true) "Secure connection" else "Not secure",
                size = 22,
                onClick = onSecureClick,
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenAddress,
                    onLongClick = onTitleLongClick,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            // "URL field content" decides whether the bar shows the page
            // title, the full address or just the domain, as in Via.
            BasicText(
                text = if (isHome) {
                    "Homepage"
                } else when (urlFieldContent) {
                    "URL" -> tab?.url ?: ""
                    "DOMAIN" -> tab?.url?.let { UrlField.domainOf(it) } ?: ""
                    else -> tab?.title?.ifBlank { tab.url } ?: ""
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                modifier = Modifier.padding(start = 8.dp, end = 4.dp),
            )
        }
        val rightIcon = when {
            isHome -> ReferenceIcons.Scanner
            tab?.loading == true -> ReferenceIcons.Close
            else -> ReferenceIcons.Refresh
        }
        ChromeButton(
            icon = rightIcon,
            contentDescription = when {
                isHome -> "Scan QR code"
                tab?.loading == true -> "Stop"
                else -> "Reload"
            },
            size = 22,
            onClick = onRightClick,
        )
    }
}

@Composable
fun TitleActionPopup(
    showCopy: Boolean,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onPasteAndGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(17.dp))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCopy) TitleAction("Copy", onCopy)
        TitleAction("Paste", onPaste)
        TitleAction("Paste & Go", onPasteAndGo)
    }
}

@Composable
private fun TitleAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(48.dp)
            .jumpClickable(androidx.compose.ui.graphics.RectangleShape, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** CSS-style homepage magnifier: 12dp circle with a 2dp stroke and a short handle. */
@Composable
fun SearchGlyph(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
    Canvas(modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        val radius = 7.dp.toPx()
        val center = Offset(size.width * 0.44f, size.height * 0.44f)
        drawCircle(
            color = tint,
            radius = radius,
            center = center,
            style = Stroke(width = stroke),
        )
        val start = Offset(
            center.x + radius * 0.72f,
            center.y + radius * 0.72f,
        )
        val end = Offset(
            center.x + radius + 3.6.dp.toPx(),
            center.y + radius + 3.6.dp.toPx(),
        )
        drawLine(color = tint, start = start, end = end, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun AddressBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    onExpand: () -> Unit,
    incognito: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (incognito) ChromeIcon(ReferenceIcons.Incognito, "Incognito", size = 22)
            else SearchGlyph()
        }
        Box(
            Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.text.isEmpty()) {
                            Text(
                                "Search",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
        }
        if (value.text.isNotEmpty()) {
            ChromeButton(
                icon = ReferenceIcons.Close,
                contentDescription = "Clear",
                size = 22,
                onClick = { onValueChange(TextFieldValue("", selection = TextRange(0))) },
            )
        }
        ChromeButton(
            icon = ReferenceIcons.Expand,
            contentDescription = "Expand address bar",
            size = 22,
            onClick = onExpand,
        )
        ChromeButton(
            icon = ReferenceIcons.Forward,
            contentDescription = "Go",
            size = 22,
            onClick = onSubmit,
        )
    }
}

@Composable
fun FullScreenAddressBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (value.text.isEmpty()) {
                            Text(
                                "Search",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        inner()
                    }
                },
            )
        }
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .jumpClickable { onCollapse() },
                contentAlignment = Alignment.Center,
            ) {
                ChromeIcon(
                    ReferenceIcons.Expand,
                    "Collapse address bar",
                    size = 22,
                    modifier = Modifier.rotate(180f),
                )
            }
            Spacer(Modifier.weight(1f))
            ChromeButton(
                icon = ReferenceIcons.Forward,
                contentDescription = "Go",
                size = 22,
                onClick = onSubmit,
            )
        }
    }
}

@Composable
fun TopChrome(
    editing: Boolean,
    tab: TabInfo?,
    addressValue: TextFieldValue,
    onAddressValueChange: (TextFieldValue) -> Unit,
    onOpenAddress: () -> Unit,
    onCloseAddress: () -> Unit,
    onSubmitAddress: () -> Unit,
    onExpandAddress: () -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onTitleLongClick: () -> Unit,
    onSecureClick: () -> Unit,
    urlFieldContent: String = "TITLE",
) {
    AnimatedContent(
        targetState = editing,
        transitionSpec = {
            fadeIn(tween(240)) togetherWith fadeOut(tween(240))
        },
        label = "address",
    ) { isEditing ->
        if (isEditing) {
            AddressBar(
                value = addressValue,
                onValueChange = onAddressValueChange,
                onSubmit = onSubmitAddress,
                onClose = onCloseAddress,
                onExpand = onExpandAddress,
                incognito = tab?.incognito == true,
            )
        } else {
            TitleBar(
                tab = tab,
                bookmark = false,
                urlFieldContent = urlFieldContent,
                onOpenAddress = onOpenAddress,
                onLeftClick = onLeftClick,
                onRightClick = onRightClick,
                onTitleLongClick = onTitleLongClick,
                onSecureClick = onSecureClick,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomBar(
    tab: TabInfo?,
    tabs: List<TabInfo>,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onNewTab: () -> Unit,
    onMenu: () -> Unit,
    onMenuLong: () -> Unit,
    onBackLong: () -> Unit,
    onHomeLong: () -> Unit,
    onForwardLong: () -> Unit,
    onSwitchTab: (Int) -> Unit = {},
    /** Toolbars settings → Enable search toolbar. */
    searchToolbar: Boolean = false,
    onSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val canBack = tab?.canGoBack == true
    val canForward = tab?.canGoForward == true
    val density = LocalDensity.current
    // How far the bar slides under the finger before it commits.
    val followLimitPx = with(density) { 56.dp.toPx() }
    val commitDistancePx = with(density) { 40.dp.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    val swipable = tabs.size > 1

    // Drag the bar with the finger, then snap it back and switch tab in the
    // same frame on release: no transition animation, as in Via.
    val dragState = rememberDraggableState { delta ->
        if (!swipable) return@rememberDraggableState
        offset = (offset + delta).coerceIn(-followLimitPx, followLimitPx)
    }
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(48.dp)
            .graphicsLayer { translationX = offset }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                startDragImmediately = false,
                onDragStopped = { velocity ->
                    if (!swipable) return@draggable
                    val projected = offset + velocity * 0.05f
                    offset = 0f
                    when {
                        projected < -commitDistancePx -> onSwitchTab(1)
                        projected > commitDistancePx -> onSwitchTab(-1)
                    }
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomSlot(ReferenceIcons.Back, "Go back", canBack, onBack, onBackLong)
        BottomSlot(ReferenceIcons.Forward, "Go forward", canForward, onForward, onForwardLong)
        if (searchToolbar) {
            // The search toolbar replaces the home button with an inline
            // search field, matching Via's compact bottom bar.
            val searchInteraction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .padding(horizontal = 2.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(
                        interactionSource = searchInteraction,
                        indication = com.jump.browser.ui.theme.JumpPressIndication,
                        onClick = onSearch,
                    )
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    ReferenceIcons.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Search",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            BottomSlot(ReferenceIcons.Home, "Home", true, onHome, onHomeLong)
        }
        val countInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(18.dp))
                .combinedClickable(
                    interactionSource = countInteraction,
                    indication = com.jump.browser.ui.theme.JumpPressIndication,
                    onClick = onTabs,
                    onLongClick = onNewTab,
                ),
            contentAlignment = Alignment.Center,
        ) {
            TabCountBadge(
                count = tabs.size,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        BottomSlot(ReferenceIcons.Menu, "Menu", true, onMenu, onMenuLong)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomSlot(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = com.jump.browser.ui.theme.JumpPressIndication,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun MenuOverlay(
    visible: Boolean,
    bottomInset: Int,
    customOrder: List<String> = emptyList(),
    tab: TabInfo?,
    bookmarked: Boolean,
    isDark: Boolean,
    imagesEnabled: Boolean,
    adBlocking: Boolean,
    textZoom: Int,
    onAction: (MenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    // A real window so the sheet floats above the chrome. It only fades: the
    // window animation is disabled because it follows window_animation_scale.
    SheetWindow(onDismiss = onDismiss) {
        val progress by rememberSheetProgress(visible)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.20f * progress))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .padding(horizontal = 1.dp)
                    .padding(bottom = with(LocalDensity.current) { bottomInset.toDp() })
                    .overlayFade(progress),
            ) {
                MenuSheetContent(
                    tab = tab,
                    bookmarked = bookmarked,
                    isDark = isDark,
                    imagesEnabled = imagesEnabled,
                    adBlocking = adBlocking,
                    textZoom = textZoom,
                    customOrder = customOrder,
                    onAction = onAction,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

private fun menuPages(
    tab: TabInfo?,
    bookmarked: Boolean,
    isDark: Boolean,
    imagesEnabled: Boolean,
    adBlocking: Boolean,
    textZoom: Int,
): List<List<MenuEntry>> {
    val incognitoNow = tab?.incognito == true
    val desktopNow = tab?.desktopMode == true
    val pageOne = listOf(
        MenuEntry("Night mode", ReferenceIcons.Moon, MenuAction.NIGHT, isDark),
        MenuEntry("Bookmarks", ReferenceIcons.Bookmark, MenuAction.BOOKMARKS),
        MenuEntry("History", ReferenceIcons.Clock, MenuAction.HISTORY),
        MenuEntry("Downloads", ReferenceIcons.Download, MenuAction.DOWNLOADS),
        MenuEntry("Incognito\nmode", ReferenceIcons.MenuIncognito, MenuAction.INCOGNITO, incognitoNow),
        MenuEntry("Share", ReferenceIcons.Share, MenuAction.SHARE),
        MenuEntry(if (bookmarked) "Edit\nbookmark" else "Add\nbookmark", ReferenceIcons.AddBookmark, MenuAction.ADD_BOOKMARK, bookmarked),
        MenuEntry("Desktop\nsite", ReferenceIcons.Desktop, MenuAction.DESKTOP, desktopNow),
        MenuEntry("Tools", ReferenceIcons.Tools, MenuAction.TOOLS),
        MenuEntry("Settings", ReferenceIcons.Settings, MenuAction.SETTINGS),
    )
    val pageTwo = listOf(
        MenuEntry("Find in\npage", ReferenceIcons.Find, MenuAction.FIND),
        MenuEntry("Save", ReferenceIcons.Save, MenuAction.SAVE_PAGE),
        MenuEntry("Saved\npages", ReferenceIcons.SavedPages, MenuAction.SAVED_PAGES),
        MenuEntry("Translate", ReferenceIcons.Translate, MenuAction.TRANSLATE),
        MenuEntry("View\nsource", ReferenceIcons.ViewSource, MenuAction.VIEW_SOURCE),
        MenuEntry("Full-screen", ReferenceIcons.Fullscreen, MenuAction.FULLSCREEN),
        MenuEntry("Show\nimages", ReferenceIcons.ShowImages, MenuAction.SHOW_IMAGES, imagesEnabled),
        MenuEntry("Resource\nsniffer", ReferenceIcons.Resource, MenuAction.RESOURCE_SNIFFER),
        MenuEntry("User-agent", ReferenceIcons.UserAgent, MenuAction.USER_AGENT, desktopNow),
        MenuEntry("Network\nlog", ReferenceIcons.Network, MenuAction.NETWORK_LOG),
    )
    val pageThree = listOf(
        MenuEntry("Scan QR\ncode", ReferenceIcons.QrScan, MenuAction.SCAN_QR),
        MenuEntry("Add to\nhome", ReferenceIcons.AddHome, MenuAction.ADD_HOME),
        MenuEntry("Read\naloud", ReferenceIcons.ReadAloud, MenuAction.READ_ALOUD),
        MenuEntry("AI", ReferenceIcons.Ai, MenuAction.AI),
        MenuEntry("Orientation", ReferenceIcons.Orientation, MenuAction.ORIENTATION),
        MenuEntry("Ad\nblocking", ReferenceIcons.AdBlock, MenuAction.AD_BLOCK, adBlocking),
        MenuEntry("Mark as\nad", ReferenceIcons.MarkAd, MenuAction.MARK_AD),
        MenuEntry("Text size", ReferenceIcons.TextSize, MenuAction.TEXT_SIZE, textZoom != 100),
        MenuEntry("Clear data", ReferenceIcons.ClearData, MenuAction.CLEAR_DATA),
        MenuEntry("Customize\nmenu", ReferenceIcons.CustomizeMenu, MenuAction.CUSTOMIZE_MENU),
    )
    return listOf(pageOne, pageTwo, pageThree)
}

@Composable
private fun MenuSheetContent(
    tab: TabInfo?,
    bookmarked: Boolean,
    isDark: Boolean,
    imagesEnabled: Boolean,
    adBlocking: Boolean,
    textZoom: Int,
    customOrder: List<String>,
    onAction: (MenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val pages = remember(tab, bookmarked, isDark, imagesEnabled, adBlocking, textZoom, customOrder) {
        val base = menuPages(tab, bookmarked, isDark, imagesEnabled, adBlocking, textZoom)
        if (customOrder.isEmpty()) base
        else {
            val all = base.flatten()
            val byLabel = all.associateBy { it.label.replace('\n', ' ') }
            val ordered = customOrder.mapNotNull { byLabel[it] }
            ordered.chunked(10).ifEmpty { base }
        }
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp)
            .clip(shape)
            .border(1.dp, com.jump.browser.ui.theme.JumpPressed, shape),
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                Column(Modifier.fillMaxSize()) {
                    pages[page].chunked(5).take(2).forEach { row ->
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            row.forEach { entry ->
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    MenuCell(
                                        icon = entry.icon,
                                        label = entry.label,
                                        tint = if (entry.accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                        onClick = { onAction(entry.action) },
                                    )
                                }
                            }
                            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.indices.forEach { index ->
                    Dot(selected = pagerState.currentPage == index) {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                }
            }
            // The reference footer overlaps the last grid row by 8dp.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .offset(y = (-12).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(48.dp).jumpClickable { onAction(MenuAction.EXIT) }, contentAlignment = Alignment.Center) {
                    ChromeIcon(ReferenceIcons.Power, "Exit", size = 22)
                }
                Spacer(Modifier.width(24.dp))
                Box(Modifier.size(48.dp).jumpClickable { onDismiss() }, contentAlignment = Alignment.Center) {
                    ChromeIcon(ReferenceIcons.ChevronDown, "Close menu", size = 22)
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
fun TabPopup(
    visible: Boolean,
    tabs: List<TabInfo>,
    activeId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit,
    bottomInset: Int,
    topInset: Int,
) {
    if (!visible) return
    // The window itself is sized to the page area, so the scrim fills exactly
    // that strip and the chrome above and below keeps receiving taps.
    SheetWindow(
        onDismiss = onDismiss,
        contentTopPx = topInset,
        contentBottomPx = bottomInset,
    ) {
        val progress by rememberSheetProgress(visible)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f * progress))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.overlayFade(progress)) {
                Surface(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    tonalElevation = 0.dp,
                ) {
                    Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                        tabs.forEach { tab ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .jumpClickable(androidx.compose.ui.graphics.RectangleShape) { onSelect(tab.id) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                    Favicon(tab, 20)
                                }
                                Text(
                                    text = tab.title.ifBlank { tab.url ?: "Homepage" },
                                    modifier = Modifier.weight(1f),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (tab.id == activeId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                )
                                Box(
                                    Modifier.size(48.dp).jumpClickable {
                                        onClose(tab.id)
                                    },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ChromeIcon(ReferenceIcons.Close, "Close tab", size = 20)
                                }
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .jumpClickable(androidx.compose.ui.graphics.RectangleShape) { onNewTab() },
                            contentAlignment = Alignment.Center,
                        ) {
                            ChromeIcon(ReferenceIcons.Plus, "New tab", size = 24)
                        }
                    }
                }
            }
        }
    }
}

/** Display helpers for the address bar's "URL field content" setting. */
internal object UrlField {
    /** "https://www.example.com/a/b?c" → "example.com". */
    fun domainOf(url: String): String {
        val host = runCatching { android.net.Uri.parse(url).host }.getOrNull() ?: return url
        return host.removePrefix("www.")
    }

    /** What to show in the title bar for a tab's address bar. */
    fun display(url: String?, title: String?, content: String): String {
        if (url.isNullOrBlank()) return title.orEmpty()
        return when (content) {
            "URL" -> url
            "DOMAIN" -> domainOf(url)
            else -> title?.ifBlank { url } ?: url
        }
    }
}

/**
 * The optional tab strip of the two-row toolbar.
 *
 * Shown only when Toolbars settings picks "Two-row toolbar" with the tab bar
 * enabled. Tabs scroll horizontally, and the highlighted one is the active tab.
 */
@Composable
fun TabStrip(
    tabs: List<TabInfo>,
    activeId: Long?,
    onSelect: (Long) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = tab.id == activeId
            Row(
                Modifier
                    .padding(horizontal = 2.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                    )
                    .jumpClickable { onSelect(tab.id) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Favicon(tab, 14)
                Spacer(Modifier.width(6.dp))
                Text(
                    tab.title.ifBlank { tab.url ?: "Homepage" },
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 110.dp),
                )
            }
        }
        Box(
            Modifier
                .size(36.dp)
                .jumpClickable(onClick = onNewTab),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ReferenceIcons.Plus,
                contentDescription = "New tab",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
