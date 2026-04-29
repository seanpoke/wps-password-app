package com.wpspasswordmanager.utils

import org.junit.Test
import org.junit.Assert.*

class FileNameResolverTest {

    @Test
    fun testIsHashFileName_MD5Pattern() {
        assertTrue(FileNameResolver.isHashFileName("d41d8cd98f00b204e9800998ecf8427e.docx"))
        assertTrue(FileNameResolver.isHashFileName("D41D8CD98F00B204E9800998ECF8427E.docx"))
        assertFalse(FileNameResolver.isHashFileName("normal_file.docx"))
    }

    @Test
    fun testIsHashFileName_LongHashPattern() {
        assertTrue(FileNameResolver.isHashFileName("0eef0795bc4de2313c67642fa4f73fb8_6281104599594940343_m.docx"))
        assertTrue(FileNameResolver.isHashFileName("0eef0795bc4de2313c67642fa4f73fb8_6281104599594940343.docx"))
        assertFalse(FileNameResolver.isHashFileName("normal_file_123.docx"))
    }

    @Test
    fun testGetFileExtension() {
        assertEquals("docx", FileNameResolver.getFileExtension("test.docx"))
        assertEquals("pdf", FileNameResolver.getFileExtension("document.pdf"))
        assertEquals("", FileNameResolver.getFileExtension("no_extension"))
        assertEquals("txt", FileNameResolver.getFileExtension(".hidden.txt"))
    }

    @Test
    fun testIsHashFileName_EdgeCases() {
        assertFalse(FileNameResolver.isHashFileName(""))
        assertFalse(FileNameResolver.isHashFileName(".docx"))
        assertFalse(FileNameResolver.isHashFileName("a".repeat(32)))
        assertTrue(FileNameResolver.isHashFileName("a".repeat(32) + ".docx"))
    }
}
