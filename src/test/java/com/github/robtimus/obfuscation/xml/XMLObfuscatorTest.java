/*
 * XMLObfuscatorTest.java
 * Copyright 2020 Rob Spoor
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

import static com.github.robtimus.obfuscation.Obfuscator.fixedLength;
import static com.github.robtimus.obfuscation.Obfuscator.fixedValue;
import static com.github.robtimus.obfuscation.Obfuscator.none;
import static com.github.robtimus.obfuscation.support.CaseSensitivity.CASE_SENSITIVE;
import static com.github.robtimus.obfuscation.xml.XMLObfuscator.builder;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.xml.XMLObfuscator.Builder;

@SuppressWarnings("nls")
@TestInstance(Lifecycle.PER_CLASS)
class XMLObfuscatorTest {

    @ParameterizedTest(name = "{1}")
    @MethodSource
    @DisplayName("equals(Object)")
    void testEquals(Obfuscator obfuscator, Object object, boolean expected) {
        assertEquals(expected, obfuscator.equals(object));
    }

    Arguments[] testEquals() {
        Obfuscator obfuscator = createObfuscator(builder().withElement("test", none()));
        return new Arguments[] {
                arguments(obfuscator, obfuscator, true),
                arguments(obfuscator, null, false),
                arguments(obfuscator, createObfuscator(builder().withElement("test", none())), true),
                arguments(obfuscator, createObfuscator(builder().withElement("test", none(), CASE_SENSITIVE)), true),
                arguments(obfuscator, createObfuscator(builder().withElement("test", fixedLength(3))), false),
                arguments(obfuscator, createObfuscator(builder().withElement("test", none()).excludeNestedElements()), false),
                arguments(obfuscator, builder().build(), false),
                arguments(obfuscator, createObfuscator(builder().withElement("test", none()).withMalformedXMLWarning(null)), false),
                arguments(obfuscator, createObfuscator(false), false),
                arguments(obfuscator, "foo", false),
        };
    }

    @Test
    @DisplayName("hashCode()")
    void testHashCode() {
        Obfuscator obfuscator = createObfuscator();
        assertEquals(obfuscator.hashCode(), obfuscator.hashCode());
        assertEquals(obfuscator.hashCode(), createObfuscator().hashCode());
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTest {

        @Test
        @DisplayName("withElement(QName, Obfuscator) with the same local name but different namespaces")
        void testQualifiedElementNameWithSameLocalName() {
            String localName = "test";
            Obfuscator obfuscator = fixedLength(3);

            Builder builder = builder();
            assertDoesNotThrow(() -> builder.withElement(new QName(localName), obfuscator));
            assertDoesNotThrow(() -> builder.withElement(new QName(XMLConstants.XML_NS_URI, localName), obfuscator));
        }

        @Test
        @DisplayName("withElement(QName, Obfuscator) with duplicate name")
        void testDuplicateQualifiedElementName() {
            QName element = new QName("test");
            Obfuscator obfuscator = fixedLength(3);

            Builder builder = builder();
            assertDoesNotThrow(() -> builder.withElement(element, obfuscator));
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder.withElement(element, obfuscator));
            assertEquals(Messages.XMLObfuscator.duplicateElement.get(element), exception.getMessage());
        }
    }

    @Nested
    @DisplayName("valid XML")
    @TestInstance(Lifecycle.PER_CLASS)
    class ValidXML {

        @Nested
        @DisplayName("caseSensitiveByDefault()")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingCaseSensitively extends ObfuscatorTest {

            ObfuscatingCaseSensitively() {
                super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.all",
                        () -> createObfuscator(builder().caseSensitiveByDefault()));
            }
        }

        @Nested
        @DisplayName("caseInsensitiveByDefault()")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingCaseInsensitively extends ObfuscatorTest {

            ObfuscatingCaseInsensitively() {
                super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.all",
                        () -> createObfuscatorCaseInsensitive(builder().caseInsensitiveByDefault()));
            }
        }

        @Nested
        @DisplayName("using qualified names")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingQualified extends ObfuscatorTest {

            ObfuscatingQualified() {
                super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.all",
                        () -> createObfuscatorQualifiedNames(builder()));
            }
        }

        @Nested
        @DisplayName("obfuscating all (default)")
        class ObfuscatingAll extends ObfuscatorTest {

            ObfuscatingAll() {
                super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.all",
                        () -> createObfuscator(builder()));
            }
        }

        @Nested
        @DisplayName("obfuscating all, overriding text only by default")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingAllOverridden extends ObfuscatorTest {

            ObfuscatingAllOverridden() {
                super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.all",
                        () -> createObfuscatorObfuscatingAll(builder().textOnlyByDefault()));
            }
        }

        @Nested
        @DisplayName("obfuscating text only by default")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingText extends ObfuscatorTest {

            ObfuscatingText() {
                super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.text",
                        () -> createObfuscator(builder().textOnlyByDefault()));
            }
        }

        @Nested
        @DisplayName("obfuscating text only, overriding all by default")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingTextOverridden extends ObfuscatorTest {

            ObfuscatingTextOverridden() {
                super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.text",
                        () -> createObfuscatorObfuscatingTextOnly(builder().allByDefault()));
            }
        }
    }

    @Nested
    @DisplayName("invalid XML")
    @TestInstance(Lifecycle.PER_CLASS)
    class InvalidXML extends ObfuscatorTest {

        InvalidXML() {
            super("XMLObfuscator.input.invalid", "XMLObfuscator.expected.invalid",
                    () -> createObfuscator());
        }
    }

    @Nested
    @DisplayName("truncated XML")
    @TestInstance(Lifecycle.PER_CLASS)
    class TruncatedXML {

        @Nested
        @DisplayName("with warning")
        class WithWarning extends TruncatedXMLTest {

            WithWarning() {
                super("XMLObfuscator.expected.truncated", true);
            }
        }

        @Nested
        @DisplayName("without warning")
        class WithoutWarning extends TruncatedXMLTest {

            WithoutWarning() {
                super("XMLObfuscator.expected.truncated.no-warning", false);
            }
        }

        private class TruncatedXMLTest extends ObfuscatorTest {

            TruncatedXMLTest(String expectedResource, boolean includeWarning) {
                super("XMLObfuscator.input.truncated", expectedResource, () -> createObfuscator(includeWarning));
            }
        }
    }

    abstract static class ObfuscatorTest {

        private final String input;
        private final String expected;
        private final Supplier<Obfuscator> obfuscatorSupplier;

        ObfuscatorTest(String inputResource, String expectedResource, Supplier<Obfuscator> obfuscatorSupplier) {
            this.input = readResource(inputResource);
            this.expected = readResource(expectedResource);
            this.obfuscatorSupplier = obfuscatorSupplier;
        }

        @Test
        @DisplayName("obfuscateText(CharSequence, int, int)")
        void testObfuscateTextCharSequence() {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            assertEquals(expected, obfuscator.obfuscateText("x" + input + "x", 1, 1 + input.length()).toString());
        }

        @Test
        @DisplayName("obfuscateText(CharSequence, int, int, Appendable)")
        void testObfuscateTextCharSequenceToAppendable() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringWriter destination = spy(new StringWriter());
            obfuscator.obfuscateText("x" + input + "x", 1, 1 + input.length(), destination);
            assertEquals(expected, destination.toString());
            verify(destination, never()).close();
        }

        @Test
        @DisplayName("obfuscateText(Reader, Appendable)")
        @SuppressWarnings("resource")
        void testObfuscateTextReaderToAppendable() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringWriter destination = spy(new StringWriter());
            Reader reader = spy(new StringReader(input));
            obfuscator.obfuscateText(reader, destination);
            assertEquals(expected, destination.toString());
            verify(reader, never()).close();
            verify(destination, never()).close();

            destination.getBuffer().delete(0, destination.getBuffer().length());
            reader = spy(new BufferedReader(new StringReader(input)));
            obfuscator.obfuscateText(reader, destination);
            assertEquals(expected, destination.toString());
            verify(reader, never()).close();
            verify(destination, never()).close();
        }

        @Test
        @DisplayName("streamTo(Appendable")
        void testStreamTo() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            Writer writer = spy(new StringWriter());
            try (Writer w = obfuscator.streamTo(writer)) {
                int index = 0;
                while (index < input.length()) {
                    int to = Math.min(index + 5, input.length());
                    w.write(input, index, to - index);
                    index = to;
                }
            }
            assertEquals(expected, writer.toString());
            verify(writer, never()).close();
        }
    }

    private static Obfuscator createObfuscator() {
        return builder()
                .transform(XMLObfuscatorTest::createObfuscator);
    }

    private static Obfuscator createObfuscator(boolean includeWarning) {
        Builder builder = builder();
        if (!includeWarning) {
            builder = builder.withMalformedXMLWarning(null);
        }
        return builder.transform(XMLObfuscatorTest::createObfuscator);
    }

    private static XMLObfuscator createObfuscator(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withElement("text", obfuscator)
                .withElement("cdata", obfuscator)
                .withElement("empty", obfuscator)
                .withElement("element", obfuscator)
                .withElement("notObfuscated", none())
                .build();
    }

    private static Obfuscator createObfuscatorCaseInsensitive(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withElement("TEXT", obfuscator)
                .withElement("CDATA", obfuscator)
                .withElement("EMPTY", obfuscator)
                .withElement("ELEMENT", obfuscator)
                .withElement("NOTOBFUSCATED", none())
                .build();
    }

    private static XMLObfuscator createObfuscatorQualifiedNames(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        Obfuscator unusedObfuscator = fixedValue("not used");
        return builder
                .withElement(new QName("text"), obfuscator)
                .withElement(new QName("cdata"), obfuscator)
                .withElement(new QName("empty"), obfuscator)
                .withElement(new QName("element"), obfuscator)
                .withElement(new QName("notObfuscated"), none())

                .withElement(new QName(XMLConstants.XML_NS_URI, "text"), unusedObfuscator)
                .withElement(new QName(XMLConstants.XML_NS_URI, "cdata"), unusedObfuscator)
                .withElement(new QName(XMLConstants.XML_NS_URI, "empty"), unusedObfuscator)
                .withElement(new QName(XMLConstants.XML_NS_URI, "element"), unusedObfuscator)
                .withElement(new QName(XMLConstants.XML_NS_URI, "notObfuscated"), unusedObfuscator)
                .build();
    }

    private static Obfuscator createObfuscatorObfuscatingAll(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withElement("text", obfuscator).all()
                .withElement("cdata", obfuscator).all()
                .withElement("empty", obfuscator).all()
                .withElement("element", obfuscator).all()
                .withElement("notObfuscated", none()).all()
                .build();
    }

    private static Obfuscator createObfuscatorObfuscatingTextOnly(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withElement("text", obfuscator).textOnly()
                .withElement("cdata", obfuscator).textOnly()
                .withElement("empty", obfuscator).textOnly()
                .withElement("element", obfuscator).textOnly()
                .withElement("notObfuscated", none()).textOnly()
                .build();
    }

    static String readResource(String name) {
        StringBuilder sb = new StringBuilder();
        try (Reader input = new InputStreamReader(XMLObfuscatorTest.class.getResourceAsStream(name), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int len;
            while ((len = input.read(buffer)) != -1) {
                sb.append(buffer, 0, len);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sb.toString().replace("\r", "");
    }
}
