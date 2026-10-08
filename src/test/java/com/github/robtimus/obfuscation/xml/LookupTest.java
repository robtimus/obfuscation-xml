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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.support.CaseSensitivity;
import com.github.robtimus.obfuscation.xml.XMLObfuscator.ElementPath;

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
            Lookup.ForElements.Builder<Obfuscator> builder = Lookup.<Obfuscator>forElements()
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
            Lookup.ForElements.Builder<Obfuscator> builder = Lookup.<Obfuscator>forElements()
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
            Lookup.ForElements.Builder<Obfuscator> builder = Lookup.<Obfuscator>forElements()
                    .add("test", obfuscator, CaseSensitivity.CASE_SENSITIVE)
                    .add("TEST", obfuscator, CaseSensitivity.CASE_SENSITIVE)
                    .add("test", obfuscator, CaseSensitivity.CASE_INSENSITIVE)
                    .add(new QName("test"), obfuscator);

            QName name = new QName("test");

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder.add(name, obfuscator));
            assertEquals(Messages.XMLObfuscator.duplicateElement(name), exception.getMessage());
        }

        @Nested
        @DisplayName("ElementPath")
        class ElementPathTest {

            @Test
            @DisplayName("duplicate matcher")
            void testDuplicateMatcherWithExactMatch() {
                Obfuscator obfuscator = Obfuscator.all();
                Lookup.ForElements.Builder<Obfuscator> builder = Lookup.<Obfuscator>forElements()
                        .add(ElementPath.startsWith("foo"), obfuscator);
                ElementPath.Matcher matcher = ElementPath.startsWith("foo");
                assertThrows(IllegalArgumentException.class, () -> builder.add(matcher, obfuscator));
            }

            @Test
            @DisplayName("no duplicate with lambdas")
            void testNoDuplicateWithLambdas() {
                Obfuscator obfuscator = Obfuscator.all();
                Lookup.ForElements.Builder<Obfuscator> builder = Lookup.<Obfuscator>forElements()
                        .add(p -> p.lastElement().getLocalPart().equals("foo"), obfuscator);
                ElementPath.Matcher matcher = p -> p.lastElement().getLocalPart().equals("foo");
                assertDoesNotThrow(() -> builder.add(matcher, obfuscator));
            }

            @Test
            @DisplayName("duplicate matcher with shared lambda")
            void testDuplicateMatcherWithSharedLambda() {
                Obfuscator obfuscator = Obfuscator.all();
                Lookup.ForElements.Builder<Obfuscator> builder = Lookup.<Obfuscator>forElements()
                        .add(matches(), obfuscator);
                ElementPath.Matcher matcher = matches();
                assertThrows(IllegalArgumentException.class, () -> builder.add(matcher, obfuscator));
            }

            private ElementPath.Matcher matches() {
                return p -> p.lastElement().getLocalPart().equals("foo");
            }
        }
    }

    @Nested
    @DisplayName("duplicate attribute")
    class DuplicateAttribute {

        @Test
        @DisplayName("case sensitive local name")
        void testCaseSensitiveLocalName() {
            Obfuscator obfuscator = Obfuscator.all();
            Lookup.ForAttributes.Builder<Obfuscator> builder = Lookup.<Obfuscator>forAttributes()
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
            Lookup.ForAttributes.Builder<Obfuscator> builder = Lookup.<Obfuscator>forAttributes()
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
            Lookup.ForAttributes.Builder<Obfuscator> builder = Lookup.<Obfuscator>forAttributes()
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
