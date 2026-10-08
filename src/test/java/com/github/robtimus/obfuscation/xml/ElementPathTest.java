/*
 * ElementPathTest.java
 * Copyright 2026 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robtimus.obfuscation.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Collection;
import java.util.List;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.junit.support.test.collections.IteratorTests;
import com.github.robtimus.junit.support.test.collections.UnmodifiableIteratorTests;
import com.github.robtimus.obfuscation.xml.XMLObfuscator.ElementPath;

@SuppressWarnings("nls")
class ElementPathTest {

    private static final String NAMESPACE_URI = "urn:test";

    @Test
    @DisplayName("length()")
    void testLength() {
        ElementPath path = newPath("a", "b", "c");

        assertEquals(3, path.length());
    }

    @Test
    @DisplayName("elementAt(int)")
    void testElementAt() {
        ElementPath path = newPath("a", "b", "c");

        assertThrows(IndexOutOfBoundsException.class, () -> path.elementAt(-1));
        assertEquals(new QName(NAMESPACE_URI, "a"), path.elementAt(0));
        assertEquals(new QName(NAMESPACE_URI, "b"), path.elementAt(1));
        assertEquals(new QName(NAMESPACE_URI, "c"), path.elementAt(2));
        assertThrows(IndexOutOfBoundsException.class, () -> path.elementAt(3));
    }

    @Test
    @DisplayName("lastElement()")
    void testLastElement() {
        ElementPath path = newPath("a", "b", "c");

        assertEquals(new QName(NAMESPACE_URI, "c"), path.lastElement());
    }

    @Nested
    @DisplayName("iterator()")
    class IteratorTest implements IteratorTests.IterationTests<QName>, UnmodifiableIteratorTests.RemoveTests<QName> {

        @Override
        public Iterable<QName> iterable() {
            return newPath("a", "b", "c");
        }

        @Override
        public Collection<QName> expectedElements() {
            return List.of(new QName(NAMESPACE_URI, "a"), new QName(NAMESPACE_URI, "b"), new QName(NAMESPACE_URI, "c"));
        }

        @Override
        public boolean fixedOrder() {
            return true;
        }
    }

    @Test
    void testToString() {
        ElementPath path = newPath("a", "b", "c");

        assertEquals("/{%1$s}a/{%1$s}b/{%1$s}c".formatted(NAMESPACE_URI), path.toString());
    }

    private static ElementPath newPath(String... elements) {
        ElementPath path = new ElementPath();
        for (String element : elements) {
            path.push(new QName(NAMESPACE_URI, element));
        }
        return path;
    }
}
