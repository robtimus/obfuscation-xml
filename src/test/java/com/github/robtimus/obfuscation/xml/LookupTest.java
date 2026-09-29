/*
 * LookupTest.java
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
import javax.xml.namespace.QName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.support.CaseSensitivity;
import com.github.robtimus.obfuscation.xml.Lookup.MessageProvider;

@SuppressWarnings("nls")
class LookupTest {

    // Only test duplicate entries in this class, other functionality is tested through XMLObfuscatorTest

    @Nested
    @DisplayName("duplicate element")
    class DuplicateElement {

        @Test
        @DisplayName("case sensitive local name")
        void testCaseSensitiveLocalName() {
            Obfuscator obfuscator = Obfuscator.all();
            Lookup.Builder<Obfuscator> builder = Lookup.<Obfuscator>builder(MessageProvider.ELEMENT)
                    .add("test", obfuscator, CaseSensitivity.CASE_SENSITIVE)
                    .add("TEST", obfuscator, CaseSensitivity.CASE_SENSITIVE)
                    .add("test", obfuscator, CaseSensitivity.CASE_INSENSITIVE)
                    .add(new QName("test"), obfuscator);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> builder.add("test", obfuscator, CaseSensitivity.CASE_SENSITIVE));
            assertEquals(Messages.XMLObfuscator.duplicateElementWithCaseSensitivity(CaseSensitivity.CASE_SENSITIVE, "test"), exception.getMessage());
        }

        @Test
        @DisplayName("case insensitive local name")
        void testCaseInsensitiveLocalName() {
            Obfuscator obfuscator = Obfuscator.all();
            Lookup.Builder<Obfuscator> builder = Lookup.<Obfuscator>builder(MessageProvider.ELEMENT)
                    .add("test", obfuscator, CaseSensitivity.CASE_SENSITIVE)
                    .add("test", obfuscator, CaseSensitivity.CASE_INSENSITIVE)
                    .add(new QName("test"), obfuscator);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> builder.add("TEST", obfuscator, CaseSensitivity.CASE_INSENSITIVE));
            assertEquals(Messages.XMLObfuscator.duplicateElementWithCaseSensitivity(CaseSensitivity.CASE_INSENSITIVE, "TEST"),
                    exception.getMessage());
        }

        @Test
        @DisplayName("qualified name")
        void testQualifiedName() {
            Obfuscator obfuscator = Obfuscator.all();
            Lookup.Builder<Obfuscator> builder = Lookup.<Obfuscator>builder(MessageProvider.ELEMENT)
                    .add("test", obfuscator, CaseSensitivity.CASE_SENSITIVE)
                    .add("TEST", obfuscator, CaseSensitivity.CASE_SENSITIVE)
                    .add("test", obfuscator, CaseSensitivity.CASE_INSENSITIVE)
                    .add(new QName("test"), obfuscator);

            QName name = new QName("test");

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder.add(name, obfuscator));
            assertEquals(Messages.XMLObfuscator.duplicateElement(name), exception.getMessage());
        }
    }

    @Nested
    @DisplayName("duplicate attribute")
    class DuplicateAttribute {

        @Test
        @DisplayName("case sensitive local name")
        void testCaseSensitiveLocalName() {
            Obfuscator obfuscator = Obfuscator.all();
            Lookup.Builder<Obfuscator> builder = Lookup.<Obfuscator>builder(MessageProvider.ATTRIBUTE)
                    .add("test", obfuscator, CaseSensitivity.CASE_SENSITIVE)
                    .add("TEST", obfuscator, CaseSensitivity.CASE_SENSITIVE)
                    .add("test", obfuscator, CaseSensitivity.CASE_INSENSITIVE)
                    .add(new QName("test"), obfuscator);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> builder.add("test", obfuscator, CaseSensitivity.CASE_SENSITIVE));
            assertEquals(Messages.XMLObfuscator.duplicateAttributeWithCaseSensitivity(CaseSensitivity.CASE_SENSITIVE, "test"),
                    exception.getMessage());
        }

        @Test
        @DisplayName("case insensitive local name")
        void testCaseInsensitiveLocalName() {
            Obfuscator obfuscator = Obfuscator.all();
            Lookup.Builder<Obfuscator> builder = Lookup.<Obfuscator>builder(MessageProvider.ATTRIBUTE)
                    .add("test", obfuscator, CaseSensitivity.CASE_SENSITIVE)
                    .add("test", obfuscator, CaseSensitivity.CASE_INSENSITIVE)
                    .add(new QName("test"), obfuscator);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> builder.add("TEST", obfuscator, CaseSensitivity.CASE_INSENSITIVE));
            assertEquals(Messages.XMLObfuscator.duplicateAttributeWithCaseSensitivity(CaseSensitivity.CASE_INSENSITIVE, "TEST"),
                    exception.getMessage());
        }

        @Test
        @DisplayName("qualified name")
        void testQualifiedName() {
            Obfuscator obfuscator = Obfuscator.all();
            Lookup.Builder<Obfuscator> builder = Lookup.<Obfuscator>builder(MessageProvider.ATTRIBUTE)
                    .add("test", obfuscator, CaseSensitivity.CASE_SENSITIVE)
                    .add("TEST", obfuscator, CaseSensitivity.CASE_SENSITIVE)
                    .add("test", obfuscator, CaseSensitivity.CASE_INSENSITIVE)
                    .add(new QName("test"), obfuscator);

            QName name = new QName("test");

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder.add(name, obfuscator));
            assertEquals(Messages.XMLObfuscator.duplicateAttribute(name), exception.getMessage());
        }
    }
}
