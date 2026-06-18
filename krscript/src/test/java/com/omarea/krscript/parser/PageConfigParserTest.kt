package com.omarea.krscript.parser

import com.omarea.krscript.model.ActionNode
import com.omarea.krscript.model.GroupNode
import com.omarea.krscript.model.PageNode
import com.omarea.krscript.model.PickerNode
import com.omarea.krscript.model.SwitchNode
import com.omarea.krscript.model.TextNode
import com.omarea.krscript.validator.PageConfigValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream

/**
 * RU: Модульные тесты для чистого парсера [PageConfigParser].
 *
 * Тесты работают без Robolectric: мы внедряем собственную реализацию
 * [XmlPullParser] через `XmlPullParserFactory` и используем
 * [NoopDynamicValueResolver], чтобы shell-значения возвращались предсказуемо.
 *
 * EN: Unit tests for the pure [PageConfigParser].
 *
 * The tests run without Robolectric: we set up an [XmlPullParser] via
 * `XmlPullParserFactory` and use [NoopDynamicValueResolver] so shell values
 * are deterministic.
 */
class PageConfigParserTest {

    private fun newParser(resolver: DynamicValueResolver = NoopDynamicValueResolver()): PageConfigParser {
        return PageConfigParser(resolver)
    }

    private fun parseXml(xml: String, resolver: DynamicValueResolver = NoopDynamicValueResolver()): List<com.omarea.krscript.model.NodeInfoBase> {
        val parser = newParser(resolver)
        val xpp = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }.newPullParser()
        xpp.setInput(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "utf-8")
        return parser.parseWith(xpp, "/test/page.xml")
    }

    @Test
    fun `empty items returns empty list`() {
        val result = parseXml("<items></items>")
        assertTrue("Empty items should produce no nodes", result.isEmpty())
    }

    @Test
    fun `simple action inside group is parsed`() {
        val xml = """
            <items>
              <group title="Tools">
                <action key="act1">
                  <title>Run tool</title>
                  <set>echo hi</set>
                </action>
              </group>
            </items>
        """.trimIndent()
        val result = parseXml(xml)
        assertEquals(1, result.size)
        val group = result[0] as GroupNode
        assertEquals("Tools", group.title)
        assertEquals(1, group.children.size)
        val action = group.children[0] as ActionNode
        assertEquals("act1", action.key)
        assertEquals("Run tool", action.title)
        assertEquals("echo hi", action.setState)
    }

    @Test
    fun `switch node parses getstate and setstate`() {
        val xml = """
            <items>
              <switch key="sw1">
                <title>Toggle</title>
                <getstate>echo 1</getstate>
                <set>echo set</set>
              </switch>
            </items>
        """.trimIndent()
        val result = parseXml(xml)
        assertEquals(1, result.size)
        val sw = result[0] as SwitchNode
        assertEquals("sw1", sw.key)
        assertEquals("echo 1", sw.getState)
        assertEquals("echo set", sw.setState)
    }

    @Test
    fun `picker node parses static options`() {
        val xml = """
            <items>
              <picker key="pk1">
                <title>Pick one</title>
                <option val="a">A</option>
                <option val="b">B</option>
              </picker>
            </items>
        """.trimIndent()
        val result = parseXml(xml)
        assertEquals(1, result.size)
        val picker = result[0] as PickerNode
        assertEquals(2, picker.options!!.size)
        assertEquals("a", picker.options!![0].value)
        assertEquals("A", picker.options!![0].title)
    }

    @Test
    fun `unsupported group is skipped with its children`() {
        // With NoopDynamicValueResolver, support scripts always return "supported".
        // Use a custom resolver to emulate "not supported".
        val resolver = object : DynamicValueResolver {
            override fun resolveShellValue(shellScript: String): String = ""
            override fun isSupported(supportScript: String): Boolean = false
            override fun resolveText(value: String): String = value
        }
        val xml = """
            <items>
              <group title="Hidden" support="check-hidden">
                <action key="a"><title>Never</title></action>
              </group>
              <group title="Visible">
                <action key="b"><title>OK</title></action>
              </group>
            </items>
        """.trimIndent()
        val result = parseXml(xml, resolver)
        assertEquals(1, result.size)
        val visible = result[0] as GroupNode
        assertEquals("Visible", visible.title)
        assertEquals(1, visible.children.size)
    }

    @Test
    fun `dynamic value resolver is invoked for desc-sh`() {
        val resolver = object : DynamicValueResolver {
            override fun resolveShellValue(shellScript: String): String =
                if (shellScript == "describe") "DESC FROM SHELL" else ""
            override fun isSupported(supportScript: String): Boolean = true
            override fun resolveText(value: String): String = value
        }
        val xml = """
            <items>
              <action key="a" desc-sh="describe">
                <title>A</title>
              </action>
            </items>
        """.trimIndent()
        val result = parseXml(xml, resolver)
        val action = result[0] as ActionNode
        assertEquals("DESC FROM SHELL", action.desc)
        assertEquals("describe", action.descSh)
    }

    @Test
    fun `page node parses config and handler`() {
        val xml = """
            <items>
              <page key="p1" config="sub/page.xml" title="Sub page">
                <set>cd sub</set>
              </page>
            </items>
        """.trimIndent()
        val result = parseXml(xml)
        val page = result[0] as PageNode
        assertEquals("p1", page.key)
        assertEquals("sub/page.xml", page.pageConfigPath)
        assertEquals("cd sub", page.pageHandlerSh)
    }

    @Test
    fun `text node parses rows including bold and italic`() {
        val xml = """
            <items>
              <text key="t1">
                <title>Notice</title>
                <slice bold="true" italic="true">Hello</slice>
              </text>
            </items>
        """.trimIndent()
        val result = parseXml(xml)
        val text = result[0] as TextNode
        assertEquals("Notice", text.title)
        assertEquals(1, text.rows.size)
        assertTrue(text.rows[0].bold)
        assertTrue(text.rows[0].italic)
        assertEquals("Hello", text.rows[0].text)
    }

    @Test
    fun `validator reports missing picker options as error`() {
        val xml = """
            <items>
              <picker key="bad">
                <title>No options</title>
              </picker>
            </items>
        """.trimIndent()
        val nodes = parseXml(xml)
        val report = PageConfigValidator().validate(nodes)
        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.code == "picker-no-options" })
    }

    @Test
    fun `validator warns about action without setstate`() {
        val xml = """
            <items>
              <action key="empty">
                <title>Does nothing</title>
              </action>
            </items>
        """.trimIndent()
        val nodes = parseXml(xml)
        val report = PageConfigValidator().validate(nodes)
        // Empty setState is allowed (warnings only), so the report is still valid.
        assertTrue(report.isValid)
        assertTrue(report.warnings.any { it.code == "action-no-setstate" })
    }

    @Test
    fun `validator catches duplicate param names`() {
        val xml = """
            <items>
              <action key="dup">
                <title>Dup</title>
                <param name="x" type="text"/>
                <param name="x" type="text"/>
                <set>echo hi</set>
              </action>
            </items>
        """.trimIndent()
        val nodes = parseXml(xml)
        val report = PageConfigValidator().validate(nodes)
        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.code == "param-duplicate-name" })
    }

    @Test
    fun `parser does not invoke shell for plain text`() {
        var shellCalls = 0
        val resolver = object : DynamicValueResolver {
            override fun resolveShellValue(shellScript: String): String {
                shellCalls++
                return ""
            }
            override fun isSupported(supportScript: String): Boolean = true
            override fun resolveText(value: String): String = value
        }
        val xml = """
            <items>
              <action key="plain">
                <title>Plain</title>
                <desc>Just text</desc>
                <set>echo hi</set>
              </action>
            </items>
        """.trimIndent()
        parseXml(xml, resolver)
        assertEquals(0, shellCalls)
    }
}
