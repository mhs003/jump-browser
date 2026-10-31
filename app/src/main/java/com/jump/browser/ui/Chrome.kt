package com.jump.browser.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.jump.browser.browser.TabInfo
import com.jump.browser.ui.theme.JumpAccent

enum class MenuAction {
    NEW_TAB, NEW_INCOGNITO_TAB, BOOKMARKS, HISTORY, DOWNLOADS, SHARE, ADD_BOOKMARK,
    DESKTOP, TOOLS, SETTINGS, NIGHT, INCOGNITO, FIND, READER, SAVE_PAGE, ADD_HOME,
    TRANSLATE, VIEW_SOURCE, PRINT, PAGE_INFO, COPY_URL, RELOAD, HOME, HELP, EXIT,
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
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (isHome) {
                ChromeButton(JumpIcons.Search, "Search", onClick = onOpenAddress)
            } else {
                Box(
                    Modifier.size(48.dp).clickable { onLeftClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Favicon(tab!!, 24)
                }
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable { onOpenAddress() }
                .then(Modifier),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = if (isHome) "Homepage" else (tab?.title?.ifBlank { tab.url } ?: ""),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 2.dp, end = 8.dp),
            )
        }
        if (isHome) {
            ChromeButton(JumpIcons.QR, "Scan QR code", onClick = onRightClick)
        } else {
            ChromeButton(
                icon = if (tab?.loading == true) JumpIcons.Close else JumpIcons.Refresh,
                contentDescription = if (tab?.loading == true) "Stop" else "Reload",
                onClick = onRightClick,
            )
        }
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
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (incognito) {
                ChromeIcon(JumpIcons.Incognito, "Incognito", size = 22)
            } else {
                ChromeIcon(JumpIcons.Search, "Search", size = 21)
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(JumpAccent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .padding(vertical = 4.dp),
        )
        if (value.text.isNotEmpty()) {
            Box(
                Modifier.size(40.dp).clickable {
                    onValueChange(TextFieldValue("", selection = TextRange(0)))
                },
                contentAlignment = Alignment.Center,
            ) {
                ChromeIcon(JumpIcons.Close, "Clear", size = 20)
            }
        }
        ChromeButton(
            icon = JumpIcons.Expand,
            contentDescription = "Expand address bar",
            onClick = onExpand,
            size = 21,
        )
        ChromeButton(
            icon = JumpIcons.Forward,
            contentDescription = "Go",
            onClick = onSubmit,
            size = 22,
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
            (fadeIn(tween(130)) + slideInVertically(tween(150)) { -it / 12 }) togetherWith
                (fadeOut(tween(90)) + slideOutVertically(tween(120)) { -it / 16 })
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
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canBack = tab?.canGoBack == true
    val canForward = tab?.canGoForward == true
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomSlot(if (tab?.incognito == true) JumpIcons.Incognito else JumpIcons.Back, "Go back", canBack, onBack)
        BottomSlot(JumpIcons.Forward, "Go forward", canForward, onForward)
        BottomSlot(JumpIcons.Home, "Home", true, onHome)
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .combinedClickable(
                    onClick = onTabs,
                    onLongClick = {
                        onTabs()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            TabCountBadge(
                count = tabCount,
                tint = if (tab?.incognito == true) JumpAccent else MaterialTheme.colorScheme.onBackground,
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
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onBackground,
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
    onAction: (MenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(150)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable { onDismiss() },
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(120)),
                exit = slideOutVertically(tween(170, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(100)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                MenuSheetContent(
                    tab = tab,
                    bookmarked = bookmarked,
                    isDark = isDark,
                    onAction = onAction,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

private fun menuPages(tab: TabInfo?, bookmarked: Boolean, isDark: Boolean): List<List<MenuEntry>> {
    val incognitoNow = tab?.incognito == true
    val desktopNow = tab?.desktopMode == true
    val pageOne = listOf(
        MenuEntry("Night mode", JumpIcons.Moon, MenuAction.NIGHT, isDark),
        MenuEntry("Bookmarks", JumpIcons.Star, MenuAction.BOOKMARKS),
        MenuEntry("History", JumpIcons.Clock, MenuAction.HISTORY),
        MenuEntry("Downloads", JumpIcons.Download, MenuAction.DOWNLOADS),
        MenuEntry("Incognito\nmode", JumpIcons.Incognito, MenuAction.INCOGNITO, incognitoNow),
        MenuEntry("Share", JumpIcons.Share, MenuAction.SHARE),
        MenuEntry(if (bookmarked) "Edit\nbookmark" else "Add\nbookmark", JumpIcons.BookmarkAdd, MenuAction.ADD_BOOKMARK),
        MenuEntry("Desktop site", JumpIcons.Desktop, MenuAction.DESKTOP, desktopNow),
        MenuEntry("Tools", JumpIcons.Tools, MenuAction.TOOLS),
        MenuEntry("Settings", JumpIcons.Settings, MenuAction.SETTINGS),
    )
    val pageTwo = listOf(
        MenuEntry("New tab", JumpIcons.Plus, MenuAction.NEW_TAB),
        MenuEntry("New\nincognito", JumpIcons.Incognito, MenuAction.NEW_INCOGNITO_TAB),
        MenuEntry("Find in\npage", JumpIcons.Find, MenuAction.FIND),
        MenuEntry("Reader\nmode", JumpIcons.Reader, MenuAction.READER),
        MenuEntry("Save page", JumpIcons.Save, MenuAction.SAVE_PAGE),
        MenuEntry("Add to\nhome", JumpIcons.AddHome, MenuAction.ADD_HOME),
        MenuEntry("Translate", JumpIcons.Translate, MenuAction.TRANSLATE),
        MenuEntry("View\nsource", JumpIcons.Copy, MenuAction.VIEW_SOURCE),
        MenuEntry("Print", JumpIcons.Printer, MenuAction.PRINT),
        MenuEntry("Page info", JumpIcons.Info, MenuAction.PAGE_INFO),
    )
    val pageThree = listOf(
        MenuEntry("Home", JumpIcons.Home, MenuAction.HOME),
        MenuEntry("Reload", JumpIcons.Refresh, MenuAction.RELOAD),
        MenuEntry("Copy link", JumpIcons.Copy, MenuAction.COPY_URL),
        MenuEntry("Share", JumpIcons.Share, MenuAction.SHARE),
        MenuEntry("Bookmarks", JumpIcons.Star, MenuAction.BOOKMARKS),
        MenuEntry("History", JumpIcons.Clock, MenuAction.HISTORY),
        MenuEntry("Downloads", JumpIcons.Download, MenuAction.DOWNLOADS),
        MenuEntry("Settings", JumpIcons.Settings, MenuAction.SETTINGS),
        MenuEntry("Help", JumpIcons.Info, MenuAction.HELP),
        MenuEntry("About", JumpIcons.Info, MenuAction.SETTINGS),
    )
    return listOf(pageOne, pageTwo, pageThree)
}

@Composable
private fun MenuSheetContent(
    tab: TabInfo?,
    bookmarked: Boolean,
    isDark: Boolean,
    onAction: (MenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val pages = remember(tab, bookmarked, isDark) { menuPages(tab, bookmarked, isDark) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(304.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .clickable { },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 10.dp, bottom = 2.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(164.dp),
            ) { page ->
                Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    pages[page].chunked(5).forEach { row ->
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            row.forEach { entry ->
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    MenuCell(
                                        icon = entry.icon,
                                        label = entry.label,
                                        tint = if (entry.accent) JumpAccent else MaterialTheme.colorScheme.onBackground,
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
                Modifier.fillMaxWidth().height(22.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.indices.forEach { index ->
                    Box(Modifier.padding(horizontal = 4.dp)) {
                        Dot(selected = pagerState.currentPage == index) {
                            // Clicking is a nice desktop affordance; pager itself owns the animation.
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
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(170)), exit = fadeOut(tween(140))) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = with(LocalDensity.current) { bottomInset.toDp() })
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable { onDismiss() },
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(210, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(100)),
                exit = slideOutVertically(tween(160, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(80)),
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
                                    Favicon(tab, 22)
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
                            ChromeIcon(JumpIcons.Plus, "New tab", size = 26)
                        }
                    }
                }
            }
        }
    }
}
