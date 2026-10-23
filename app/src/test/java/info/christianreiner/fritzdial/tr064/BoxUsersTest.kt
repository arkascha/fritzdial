package info.christianreiner.fritzdial.tr064

import org.junit.Assert.assertEquals
import org.junit.Test

class BoxUsersTest {

    @Test
    fun `reads the users and marks the last one`() {
        val xml = """<List><Username last_user="1">fritz1234</Username><Username>anna</Username></List>"""
        assertEquals(listOf(BoxUser("fritz1234", true), BoxUser("anna", false)), BoxUsers.parse(xml))
    }

    @Test
    fun `copes with an empty or odd list`() {
        assertEquals(emptyList<BoxUser>(), BoxUsers.parse(""))
        assertEquals(emptyList<BoxUser>(), BoxUsers.parse("<List></List>"))
        assertEquals(
            listOf(BoxUser("anna", false)),
            BoxUsers.parse("""<List><Username> </Username><Username>anna</Username><Username>anna</Username></List>"""),
        )
    }
}
