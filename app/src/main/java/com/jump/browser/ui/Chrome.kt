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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.jump.browser.browser.TabInfo
import com.jump.browser.ui.theme.JumpAccent

enum class MenuAction {
    NIGHT, BOOKMARKS, HISTORY, DOWNLOADS, INCOGNITO, SHARE, ADD_BOOKMARK, DESKTOP, TOOLS, SETTINGS,
    FIND, SAVE_PAGE, SAVED_PAGES, TRANSLATE, VIEW_SOURCE, FULLSCREEN, SHOW_IMAGES,
    RESOURCE_SNIFFER, USER_AGENT, NETWORK_LOG, SCAN_QR, ADD_HOME, READ_ALOUD, AI,
    ORIENTATION, AD_BLOCK, MARK_AD, TEXT_SIZE, CLEAR_DATA, CUSTOMIZE_MENU, EXIT,
    NEW_TAB, NEW_INCOGNITO_TAB, READER, PRINT, PAGE_INFO, COPY_URL, RELOAD, HOME, HELP, ABOUT,
}

data class MenuEntry(val label: String, val icon: ImageVector, val action: MenuAction, val accent: Boolean = false)

@Composable
fun TitleBar(
    tab: TabInfo?,
    bookmark: Boolean,
    onOpenAddress: () -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onTitleLongClick: () -> Unit,
) {
    val isHome = tab?.isHome != false
    val leftIcon = when {
        isHome -> JumpIcons.Search
        tab?.incognito == true && tab.secure -> JumpIcons.Incognito
        tab?.secure == true -> JumpIcons.Secure
        else -> JumpIcons.Search
    }
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        ChromeButton(
            icon = leftIcon,
            contentDescription = if (isHome) "Search" else "Site info",
            size = 22,
            onClick = { if (isHome) onOpenAddress() else onLeftClick() },
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable { onOpenAddress() },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = if (isHome) "Homepage" else (tab?.title?.ifBlank { tab.url } ?: ""),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        val rightIcon = when {
            isHome -> JumpIcons.Scanner
            tab?.loading == true -> JumpIcons.Close
            else -> JumpIcons.Refresh
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
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            ChromeIcon(JumpIcons.Search, "Search", size = 22)
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
            )
        }
        if (value.text.isNotEmpty()) {
            ChromeButton(
                icon = JumpIcons.Close,
                contentDescription = "Clear",
                size = 22,
                onClick = { onValueChange(TextFieldValue("", selection = TextRange(0))) },
            )
        }
        ChromeButton(
            icon = JumpIcons.Expand,
            contentDescription = "Expand address bar",
            size = 22,
            onClick = onExpand,
        )
        ChromeButton(
            icon = JumpIcons.Forward,
            contentDescription = "Go",
            size = 22,
            onClick = onSubmit,
        )
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
                onOpenAddress = onOpenAddress,
                onLeftClick = onLeftClick,
                onRightClick = onRightClick,
                onTitleLongClick = onTitleLongClick,
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
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canBack = tab?.canGoBack == true
    val canForward = tab?.canGoForward == true
    val activeIndex = tabs.indexOfFirst { it.id == tab?.id }.coerceAtLeast(0)
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomSlot(JumpIcons.Back, "Go back", canBack, onBack)
        BottomSlot(JumpIcons.Forward, "Go forward", canForward, onForward)
        BottomSlot(JumpIcons.Home, "Home", true, onHome)
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .combinedClickable(
                    onClick = onTabs,
                    onLongClick = onTabs,
                ),
            contentAlignment = Alignment.Center,
        ) {
            TabCountBadge(
                count = tabs.size,
                index = activeIndex,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        BottomSlot(JumpIcons.Menu, "Menu", true, onMenu)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomSlot(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 0.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (pressed && enabled) com.jump.browser.ui.theme.JumpPressed else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun MenuOverlay(
    visible: Boolean,
    tab: TabInfo?,
    bookmarked: Boolean,
    isDark: Boolean,
    imagesEnabled: Boolean,
    adBlocking: Boolean,
    onAction: (MenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val slidePx = with(LocalDensity.current) { 60.dp.roundToPx() }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(160)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.20f))
                .clickable { onDismiss() },
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(160, easing = FastOutSlowInEasing)) { slidePx } + fadeIn(tween(160)),
                exit = slideOutVertically(tween(160, easing = FastOutSlowInEasing)) { slidePx } + fadeOut(tween(160)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 1.dp),
            ) {
                MenuSheetContent(
                    tab = tab,
                    bookmarked = bookmarked,
                    isDark = isDark,
                    imagesEnabled = imagesEnabled,
                    adBlocking = adBlocking,
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
): List<List<MenuEntry>> {
    val incognitoNow = tab?.incognito == true
    val desktopNow = tab?.desktopMode == true
    val pageOne = listOf(
        MenuEntry("Night mode", JumpIcons.Moon, MenuAction.NIGHT, isDark),
        MenuEntry("Bookmarks", JumpIcons.Star, MenuAction.BOOKMARKS),
        MenuEntry("History", JumpIcons.Clock, MenuAction.HISTORY),
        MenuEntry("Downloads", JumpIcons.Download, MenuAction.DOWNLOADS),
        MenuEntry("Incognito\nmode", JumpIcons.Incognito, MenuAction.INCOGNITO, incognitoNow),
        MenuEntry("Share", JumpIcons.Share, MenuAction.SHARE),
        MenuEntry(if (bookmarked) "Edit\nbookmark" else "Add\nbookmark", JumpIcons.BookmarkAdd, MenuAction.ADD_BOOKMARK, bookmarked),
        MenuEntry("Desktop site", JumpIcons.Desktop, MenuAction.DESKTOP, desktopNow),
        MenuEntry("Tools", JumpIcons.Tools, MenuAction.TOOLS),
        MenuEntry("Settings", JumpIcons.Settings, MenuAction.SETTINGS),
    )
    val pageTwo = listOf(
        MenuEntry("Find in\npage", JumpIcons.Find, MenuAction.FIND),
        MenuEntry("Save", JumpIcons.Save, MenuAction.SAVE_PAGE),
        MenuEntry("Saved\npages", JumpIcons.Offline, MenuAction.SAVED_PAGES),
        MenuEntry("Translate", JumpIcons.Translate, MenuAction.TRANSLATE),
        MenuEntry("View\nsource", JumpIcons.Code, MenuAction.VIEW_SOURCE),
        MenuEntry("Full-screen", JumpIcons.Fullscreen, MenuAction.FULLSCREEN),
        MenuEntry("Show\nimages", JumpIcons.Image, MenuAction.SHOW_IMAGES, imagesEnabled),
        MenuEntry("Resource\nsniffer", JumpIcons.Code, MenuAction.RESOURCE_SNIFFER),
        MenuEntry("User-agent", JumpIcons.Desktop, MenuAction.USER_AGENT, desktopNow),
        MenuEntry("Network\nlog", JumpIcons.Clock, MenuAction.NETWORK_LOG),
    )
    val pageThree = listOf(
        MenuEntry("Scan QR\ncode", JumpIcons.Scanner, MenuAction.SCAN_QR),
        MenuEntry("Add to\nhome", JumpIcons.AddHome, MenuAction.ADD_HOME),
        MenuEntry("Read\naloud", JumpIcons.Speaker, MenuAction.READ_ALOUD),
        MenuEntry("AI", JumpIcons.Sparkle, MenuAction.AI),
        MenuEntry("Orientation", JumpIcons.Rotate, MenuAction.ORIENTATION),
        MenuEntry("Ad\nblocking", JumpIcons.Block, MenuAction.AD_BLOCK, adBlocking),
        MenuEntry("Mark as\nad", JumpIcons.Flag, MenuAction.MARK_AD),
        MenuEntry("Text size", JumpIcons.TextSize, MenuAction.TEXT_SIZE),
        MenuEntry("Clear data", JumpIcons.Trash, MenuAction.CLEAR_DATA),
        MenuEntry("Customize\nmenu", JumpIcons.Grid, MenuAction.CUSTOMIZE_MENU),
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
    onAction: (MenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val pages = remember(tab, bookmarked, isDark, imagesEnabled, adBlocking) {
        menuPages(tab, bookmarked, isDark, imagesEnabled, adBlocking)
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(238.dp)
            .clip(shape)
            .border(1.dp, com.jump.browser.ui.theme.JumpPressed, shape),
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(164.dp),
            ) { page ->
                Column(Modifier.fillMaxSize()) {
                    pages[page].chunked(5).forEach { row ->
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
                    Box(Modifier.padding(horizontal = 4.dp)) {
                        Dot(selected = pagerState.currentPage == index) {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                ChromeButton(JumpIcons.Power, "Exit") { onAction(MenuAction.EXIT) }
                Spacer(Modifier.width(24.dp))
                ChromeButton(JumpIcons.ChevronDown, "Close menu", onClick = onDismiss)
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
) {
    val tabSlidePx = with(LocalDensity.current) { 60.dp.roundToPx() }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = with(LocalDensity.current) { bottomInset.toDp() })
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable { onDismiss() },
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(160, easing = FastOutLinearInEasing)) { tabSlidePx } + fadeIn(tween(160)),
                exit = slideOutVertically(tween(160, easing = FastOutLinearInEasing)) { tabSlidePx } + fadeOut(tween(160)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
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
                                    .height(48.dp)
                                    .clickable { onSelect(tab.id) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    Favicon(tab, 24)
                                }
                                Text(
                                    text = tab.title.ifBlank { tab.url ?: "Homepage" },
                                    modifier = Modifier.weight(1f),
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (tab.id == activeId) JumpAccent else MaterialTheme.colorScheme.onBackground,
                                )
                                Box(
                                    Modifier.size(48.dp).clickable { onClose(tab.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ChromeIcon(JumpIcons.Close, "Close tab", size = 20)
                                }
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clickable { onNewTab() },
                            contentAlignment = Alignment.Center,
                        ) {
                            ChromeIcon(JumpIcons.Plus, "New tab", size = 24)
                        }
                    }
                }
            }
        }
    }
}
