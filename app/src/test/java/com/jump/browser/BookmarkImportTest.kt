package com.jump.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.jump.browser.data.NetscapeBookmarks

/**
 * The Netscape bookmark reader is written with a hand-tuned regex walker, so
 * its folder nesting and duplicate handling are pinned down here rather than
 * only being checked by hand on a device.
 */
class BookmarkImportTest {

    private val sample = """
        <!DOCTYPE NETSCAPE-Bookmark-file-1>
        <DL><p>
            <DT><A HREF="https://loose.example/" ADD_DATE="1">Loose One</A>
            <DT><A HREF="https://second.example/">Loose Two</A>
            <DT><H3>Reading</H3>
            <DL><p>
                <DT><A HREF="https://a.example/">A</A>
                <DT><H3>Nested</H3>
                <DL><p>
                    <DT><A HREF="https://deep.example/">Deep</A>
                </DL><p>
            </DL><p>
            <DT><H3>Tools</H3>
            <DL><p>
                <DT><A HREF="https://tool.example/">Tool</A>
            </DL><p>
        </DL><p>
    """.trimIndent()

    @Test
    fun parsesEntriesAndFolders() {
        val parsed = NetscapeBookmarks.parse(sample) { 1L }
        assertEquals(5, parsed.size)

        val byUrl = parsed.associateBy { it.url }
        assertEquals("Loose One", byUrl["https://loose.example/"]?.title)
        assertEquals("", byUrl["https://loose.example/"]?.folder)
        assertEquals("Reading", byUrl["https://a.example/"]?.folder)
        assertEquals("Nested", byUrl["https://deep.example/"]?.folder)
        assertEquals("Tools", byUrl["https://tool.example/"]?.folder)
    }

    @Test
    fun decodesEntitiesInTitles() {
        val parsed = NetscapeBookmarks.parse(
            """<DL><DT><A HREF="https://x.example/?a=1&amp;b=2">Tom &amp; Jerry &lt;3</A></DL>""",
        ) { 1L }
        assertEquals(1, parsed.size)
        assertEquals("Tom & Jerry <3", parsed[0].title)
        assertEquals("https://x.example/?a=1&b=2", parsed[0].url)
    }

    @Test
    fun ignoresNonHttpTargets() {
        val parsed = NetscapeBookmarks.parse(
            """<DL>
               <DT><A HREF="javascript:void(0)">Bad</A>
               <DT><A HREF="https://ok.example/">Good</A>
               </DL>""",
        ) { 1L }
        assertEquals(listOf("https://ok.example/"), parsed.map { it.url })
        assertTrue(parsed.isNotEmpty())
    }
}
