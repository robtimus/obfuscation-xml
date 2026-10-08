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
import static com.github.robtimus.obfuscation.xml.XMLObfuscator.builder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
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
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import com.ctc.wstx.exc.WstxLazyException;
import com.github.robtimus.junit.support.extension.testlogger.Reload4jLoggerContext;
import com.github.robtimus.junit.support.extension.testlogger.TestLogger;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.xml.XMLObfuscator.Builder;
import com.github.robtimus.obfuscation.xml.XMLObfuscator.ElementConfigurer.ContentType;
import com.github.robtimus.obfuscation.xml.XMLObfuscator.ElementConfigurer.ObfuscationMode;
import com.github.robtimus.obfuscation.xml.XMLObfuscator.ElementPath;
import com.github.robtimus.obfuscation.xml.XMLObfuscator.LocalNameAttributeConfigurer;
import com.github.robtimus.obfuscation.xml.XMLObfuscator.LocalNameAttributeElementConfigurer;
import com.github.robtimus.obfuscation.xml.XMLObfuscator.LocalNameElementConfigurer;
import com.github.robtimus.obfuscation.xml.XMLObfuscatorTest.ObfuscatorTest.UseSourceTruncation;

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
        Obfuscator obfuscatorWithAttributes = createObfuscator(builder().withElement("test", none()).withAttribute("a", none()));
        QName testName = new QName("test");
        return new Arguments[] {
                arguments(obfuscator, obfuscator, true),
                arguments(obfuscator, null, false),
                arguments(obfuscator, createObfuscator(builder().withElement("test", none())), true),
                arguments(obfuscator, createObfuscator(builder().withElement("test", none(), e -> e.caseSensitive())), true),
                arguments(obfuscator, createObfuscator(builder().withElement(testName, none())), false),
                arguments(obfuscator, createObfuscator(builder().withElement("test", fixedLength(3))), false),
                arguments(obfuscator,
                        createObfuscator(builder().withElement("test", none(), e -> e.withContentTypes(ContentType.TEXT))),
                        false),
                arguments(obfuscator,
                        createObfuscator(builder().withElement("test", none(), e -> e.forNestedElements(ObfuscationMode.INHERIT_OVERRIDABLE))),
                        false),
                arguments(obfuscator, createObfuscator(builder().withElement("test", none()).withElement(new QName("text"), none())), false),
                arguments(obfuscator, createObfuscator(builder().withElement("test", none()).withAttribute("test", none())), false),
                arguments(obfuscator, createObfuscator(builder().withElement("test", none()).withAttribute(testName, none())), false),
                arguments(obfuscator, obfuscatorWithAttributes, false),
                arguments(obfuscator, createObfuscator(builder().withElement("test", none()).limitTo(Long.MAX_VALUE)), true),
                arguments(obfuscator, createObfuscator(builder().withElement("test", none()).limitTo(1024)), false),
                arguments(obfuscator,
                        createObfuscator(builder().withElement("test", none()).limitTo(Long.MAX_VALUE, l -> l.withTruncatedIndicator(null))),
                        false),
                arguments(obfuscator, builder().build(), false),
                arguments(obfuscator, createObfuscator(builder().withElement("test", none()).withMalformedXMLWarning(null)), false),
                arguments(obfuscator, createObfuscator(builder().withElement("test", none()).generateXML()), false),
                arguments(obfuscator, createObfuscator(false), false),
                arguments(obfuscator, "foo", false),

                arguments(obfuscatorWithAttributes, obfuscatorWithAttributes, true),
                arguments(obfuscatorWithAttributes, obfuscator, false),
                arguments(obfuscatorWithAttributes, createObfuscator(builder().withElement("test", none()).withAttribute("a", none())), true),
                arguments(obfuscatorWithAttributes,
                        createObfuscator(builder().withElement("test", none()).withAttribute("a", none(), a -> a.caseSensitive())), true),
                arguments(obfuscatorWithAttributes, createObfuscator(builder().withElement("test", none()).withAttribute(new QName("a"), none())),
                        false),
                arguments(obfuscatorWithAttributes, createObfuscator(builder().withElement("test", none()).withAttribute("a", fixedLength(3))),
                        false),
                arguments(obfuscatorWithAttributes,
                        createObfuscator(builder().withElement("test", none()).withAttribute("a", none(), a -> a.forElement("test", none()))),
                        false),
                arguments(obfuscatorWithAttributes,
                        createObfuscator(builder().withElement("test", none()).withAttribute("a", none(), a -> a.forElement(testName, none()))),
                        false),
        };
    }

    @Nested
    @DisplayName("hashCode()")
    class HashCode {

        @Test
        @DisplayName("without attributes")
        void testWithoutAttributes() {
            Obfuscator obfuscator = createObfuscator();
            assertEquals(obfuscator.hashCode(), obfuscator.hashCode());
            assertEquals(obfuscator.hashCode(), createObfuscator().hashCode());
        }

        @Test
        @DisplayName("with attributes")
        void testWithAttributes() {
            Obfuscator obfuscator = createObfuscatorWithAttributes();
            assertEquals(obfuscator.hashCode(), obfuscator.hashCode());
            assertEquals(obfuscator.hashCode(), createObfuscatorWithAttributes().hashCode());
        }
    }

    @Nested
    @DisplayName("external access")
    @TestInstance(Lifecycle.PER_CLASS)
    class ExternalAccess {

        @TestLogger.ForClass(XMLObfuscator.class)
        private Reload4jLoggerContext logger;

        private Appender appender;

        @BeforeEach
        void configureLogger() {
            appender = mock(Appender.class);
            logger.setLevel(Level.INFO)
                    .setAppender(appender)
                    .useParentAppenders(false);
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("obfuscators")
        @DisplayName("external DTD not allowed")
        void testExternalDTDNotAllowed(Obfuscator obfuscator, @SuppressWarnings("unused") String displayName) {
            String xml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
                    "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    </html>""";

            String obfuscated = obfuscator.obfuscateText(xml).toString();
            assertThat(obfuscated, endsWith(Messages.XMLObfuscator.malformedXML.text()));

            ArgumentCaptor<LoggingEvent> loggingEvents = ArgumentCaptor.forClass(LoggingEvent.class);

            verify(appender, atLeast(1)).doAppend(loggingEvents.capture());

            List<LoggingEvent> warningEvents = loggingEvents.getAllValues().stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .toList();

            assertFalse(warningEvents.isEmpty());

            LoggingEvent warningEvent = warningEvents.get(warningEvents.size() - 1);

            assertEquals(Messages.XMLObfuscator.malformedXML.warning(), warningEvent.getRenderedMessage());

            ThrowableInformation throwableInformation = warningEvent.getThrowableInformation();
            assertNotNull(throwableInformation);

            Throwable throwable = throwableInformation.getThrowable();
            assertInstanceOf(XMLStreamException.class, throwable);
            assertEquals(Messages.XMLObfuscator.externalDTDsNotSupported("http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd"),
                    throwable.getMessage());
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("obfuscators")
        @DisplayName("external entity not allowed")
        void testExternalEntityNotAllowed(Obfuscator obfuscator, @SuppressWarnings("unused") String displayName) {
            String xml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!DOCTYPE html [
                      <!ENTITY name SYSTEM "irrelevant">
                    ]>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    &name;
                    </html>""";

            String obfuscated = obfuscator.obfuscateText(xml).toString();
            assertThat(obfuscated, endsWith(Messages.XMLObfuscator.malformedXML.text()));

            ArgumentCaptor<LoggingEvent> loggingEvents = ArgumentCaptor.forClass(LoggingEvent.class);

            verify(appender, atLeast(1)).doAppend(loggingEvents.capture());

            List<LoggingEvent> warningEvents = loggingEvents.getAllValues().stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .toList();

            assertFalse(warningEvents.isEmpty());

            LoggingEvent warningEvent = warningEvents.get(warningEvents.size() - 1);

            assertEquals(Messages.XMLObfuscator.malformedXML.warning(), warningEvent.getRenderedMessage());

            ThrowableInformation throwableInformation = warningEvent.getThrowableInformation();
            assertNotNull(throwableInformation);

            Throwable throwable = throwableInformation.getThrowable();
            assertThat(throwable, anyOf(instanceOf(XMLStreamException.class), instanceOf(WstxLazyException.class)));

            String expectedPrefix = String.format("Encountered a reference to external entity \"%s\", but stream reader has feature \"%s\" disabled",
                    "name", XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES);
            assertThat(throwable.getMessage(), containsString(expectedPrefix));
        }

        private Arguments[] obfuscators() {
            return new Arguments[] {
                    arguments(createObfuscator(), "default"),
                    arguments(createObfuscatorWithAttributes(), "with attributes"),
            };
        }
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
            assertEquals(Messages.XMLObfuscator.duplicateElement(element), exception.getMessage());
        }

        @Test
        @DisplayName("withAttribute(QName, Obfuscator) with the same local name but different namespaces")
        void testQualifiedAttributeNameWithSameLocalName() {
            String localName = "test";
            Obfuscator obfuscator = fixedLength(3);

            Builder builder = builder();
            assertDoesNotThrow(() -> builder.withAttribute(new QName(localName), obfuscator));
            assertDoesNotThrow(() -> builder.withAttribute(new QName(XMLConstants.XML_NS_URI, localName), obfuscator));
        }

        @Test
        @DisplayName("withAttribute(QName, Obfuscator) with duplicate name")
        void testDuplicateQualifiedAttributeName() {
            QName attribute = new QName("test");
            Obfuscator obfuscator = fixedLength(3);

            Builder builder = builder();
            assertDoesNotThrow(() -> builder.withAttribute(attribute, obfuscator));
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder.withAttribute(attribute, obfuscator));
            assertEquals(Messages.XMLObfuscator.duplicateAttribute(attribute), exception.getMessage());
        }

        @Test
        @DisplayName("forElement(QName, Obfuscator) with the same local name but different namespaces")
        void testQualifiedAttributeElementNameWithSameLocalName() {
            String localName = "test";
            Obfuscator obfuscator = fixedLength(3);

            builder().withAttribute("a", obfuscator, a -> {
                assertDoesNotThrow(() -> a.forElement(new QName(localName), obfuscator));
                assertDoesNotThrow(() -> a.forElement(new QName(XMLConstants.XML_NS_URI, localName), obfuscator));
            });
        }

        @Test
        @DisplayName("forElement(QName, Obfuscator) with duplicate name")
        void testDuplicateQualifiedAttributeElementName() {
            QName element = new QName("test");
            Obfuscator obfuscator = fixedLength(3);

            Builder builder = builder();

            builder.withAttribute("a", obfuscator, a -> {
                assertDoesNotThrow(() -> a.forElement(element, obfuscator));
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> a.forElement(element, obfuscator));
                assertEquals(Messages.XMLObfuscator.duplicateElement(element), exception.getMessage());
            });

            // test for another attribute the same name can be reused
            builder.withAttribute("a2", obfuscator, a -> {
                assertDoesNotThrow(() -> a.forElement(element, obfuscator));
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> a.forElement(element, obfuscator));
                assertEquals(Messages.XMLObfuscator.duplicateElement(element), exception.getMessage());
            });
        }

        @Nested
        @DisplayName("limitTo")
        class LimitTo {

            @Test
            @DisplayName("negative limit")
            void testNegativeLimit() {
                Builder builder = builder();
                assertThrows(IllegalArgumentException.class, () -> builder.limitTo(-1));
            }
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
        @DisplayName("caseInsensitive(), overriding caseSensitiveByDefault()")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingCaseSensitivelyOverridden extends ObfuscatorTest {

            ObfuscatingCaseSensitivelyOverridden() {
                super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.all",
                        () -> createObfuscatorCaseInsensitive(builder().caseSensitiveByDefault(), LocalNameElementConfigurer::caseInsensitive));
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
                        () -> createObfuscatorObfuscatingAll(builder().withContentTypesByDefault(ContentType.TEXT)));
            }
        }

        @Nested
        @DisplayName("obfuscating text only by default")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingText extends ObfuscatorTest {

            ObfuscatingText() {
                super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.text",
                        () -> createObfuscator(builder().withContentTypesByDefault(ContentType.TEXT)));
            }
        }

        @Nested
        @DisplayName("obfuscating text only, overriding all by default")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingTextOverridden extends ObfuscatorTest {

            ObfuscatingTextOverridden() {
                super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.text",
                        () -> createObfuscatorObfuscatingTextOnly(builder().withContentTypesByDefault(ContentType.ALL)));
            }
        }

        @Nested
        @DisplayName("obfuscating nested elements only by default")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingNestedElements extends ObfuscatorTest {

            ObfuscatingNestedElements() {
                super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.nested-elements",
                        () -> createObfuscator(builder().withContentTypesByDefault(ContentType.NESTED_ELEMENT)));
            }
        }

        @Nested
        @DisplayName("obfuscating nested elements only, overriding all by default")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingNestedElementsOverridden extends ObfuscatorTest {

            ObfuscatingNestedElementsOverridden() {
                super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.nested-elements",
                        () -> createObfuscatorObfuscatingNestedElementsOnly(builder().withContentTypesByDefault(ContentType.ALL)));
            }
        }

        @Nested
        @DisplayName("obfuscating with INHERITED_OVERRIDABLE mode")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingInheritedOverridable extends ObfuscatorTest {

            ObfuscatingInheritedOverridable() {
                super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.inherited-overridable",
                        () -> createObfuscatorWithObfuscatorMode(builder(), ObfuscationMode.INHERIT_OVERRIDABLE));
            }
        }

        @Nested
        @DisplayName("limited")
        @TestInstance(Lifecycle.PER_CLASS)
        class Limited {

            @Nested
            @DisplayName("with truncated indicator")
            @TestInstance(Lifecycle.PER_CLASS)
            class WithTruncatedIndicator extends ObfuscatorTest {

                WithTruncatedIndicator() {
                    super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.limited.with-indicator",
                            () -> createObfuscator(builder().limitTo(289)));
                }
            }

            @Nested
            @DisplayName("without truncated indicator")
            @TestInstance(Lifecycle.PER_CLASS)
            class WithoutTruncatedIndicator extends ObfuscatorTest {

                WithoutTruncatedIndicator() {
                    super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.limited.without-indicator",
                            () -> createObfuscator(builder().limitTo(289, l -> l.withTruncatedIndicator(null))));
                }
            }
        }

        @Nested
        @DisplayName("paths and name matching")
        @TestInstance(Lifecycle.PER_CLASS)
        class PathAndNameMatching extends ObfuscatorTest {

            PathAndNameMatching() {
                super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.path-and-name-matching",
                        () -> createObfuscatorWithPathsAndNames(builder()));
            }
        }

        @Nested
        @DisplayName("generating XML")
        class GenerateXML extends ObfuscatorTest {

            GenerateXML() {
                super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.generate-xml",
                        () -> createObfuscator(builder()));
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

        private abstract static class TruncatedXMLTest extends ObfuscatorTest {

            TruncatedXMLTest(String expectedResource, boolean includeWarning) {
                super("XMLObfuscator.input.truncated", expectedResource, () -> createObfuscator(includeWarning));
            }
        }
    }

    @Nested
    @DisplayName("with attributes")
    @UseSourceTruncation(false)
    class WithAttributes {

        @Nested
        @DisplayName("valid XML")
        @TestInstance(Lifecycle.PER_CLASS)
        class ValidXML {

            @Nested
            @DisplayName("caseSensitiveByDefault()")
            @TestInstance(Lifecycle.PER_CLASS)
            class ObfuscatingCaseSensitively extends ObfuscatorTest {

                ObfuscatingCaseSensitively() {
                    super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.with-attributes.all",
                            () -> createObfuscatorWithAttributes(builder().caseSensitiveByDefault()));
                }
            }

            @Nested
            @DisplayName("caseInsensitiveByDefault()")
            @TestInstance(Lifecycle.PER_CLASS)
            class ObfuscatingCaseInsensitively extends ObfuscatorTest {

                ObfuscatingCaseInsensitively() {
                    super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.with-attributes.all",
                            () -> createObfuscatorCaseInsensitiveWithAttributes(builder().caseInsensitiveByDefault()));
                }
            }

            @Nested
            @DisplayName("caseInsensitive(), overriding caseSensitiveByDefault()")
            @TestInstance(Lifecycle.PER_CLASS)
            class ObfuscatingCaseSensitivelyOverridden extends ObfuscatorTest {

                ObfuscatingCaseSensitivelyOverridden() {
                    super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.with-attributes.all",
                            () -> createObfuscatorCaseInsensitiveWithAttributes(builder().caseSensitiveByDefault(),
                                    LocalNameElementConfigurer::caseInsensitive, LocalNameAttributeConfigurer::caseInsensitive,
                                    LocalNameAttributeElementConfigurer::caseInsensitive));
                }
            }

            @Nested
            @DisplayName("using qualified names")
            @TestInstance(Lifecycle.PER_CLASS)
            class ObfuscatingQualified extends ObfuscatorTest {

                ObfuscatingQualified() {
                    super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.with-attributes.qualified.all",
                            () -> createObfuscatorQualifiedNamesWithAttributes(builder()));
                }
            }

            @Nested
            @DisplayName("obfuscating all (default)")
            class ObfuscatingAll extends ObfuscatorTest {

                ObfuscatingAll() {
                    super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.with-attributes.all",
                            () -> createObfuscatorWithAttributes(builder()));
                }
            }

            @Nested
            @DisplayName("obfuscating all, overriding text only by default")
            @TestInstance(Lifecycle.PER_CLASS)
            class ObfuscatingAllOverridden extends ObfuscatorTest {

                ObfuscatingAllOverridden() {
                    super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.with-attributes.all",
                            () -> createObfuscatorObfuscatingAllWithAttributes(builder().withContentTypesByDefault(ContentType.TEXT)));
                }
            }

            @Nested
            @DisplayName("obfuscating text only by default")
            @TestInstance(Lifecycle.PER_CLASS)
            class ObfuscatingText extends ObfuscatorTest {

                ObfuscatingText() {
                    super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.with-attributes.text",
                            () -> createObfuscatorWithAttributes(builder().withContentTypesByDefault(ContentType.TEXT)));
                }
            }

            @Nested
            @DisplayName("obfuscating text only, overriding all by default")
            @TestInstance(Lifecycle.PER_CLASS)
            class ObfuscatingTextOverridden extends ObfuscatorTest {

                ObfuscatingTextOverridden() {
                    super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.with-attributes.text",
                            () -> createObfuscatorObfuscatingTextOnlyWithAttributes(builder().withContentTypesByDefault(ContentType.ALL)));
                }
            }

            @Nested
            @DisplayName("obfuscating nested elements only by default")
            @TestInstance(Lifecycle.PER_CLASS)
            class ObfuscatingNestedElements extends ObfuscatorTest {

                ObfuscatingNestedElements() {
                    super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.with-attributes.nested-elements",
                            () -> createObfuscatorWithAttributes(builder().withContentTypesByDefault(ContentType.NESTED_ELEMENT)));
                }
            }

            @Nested
            @DisplayName("obfuscating nested elements, overriding all by default")
            @TestInstance(Lifecycle.PER_CLASS)
            class ObfuscatingNestedElementsOverridden extends ObfuscatorTest {

                ObfuscatingNestedElementsOverridden() {
                    super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.with-attributes.nested-elements",
                            () -> createObfuscatorObfuscatingNestedElementsOnlyWithAttributes(builder().withContentTypesByDefault(ContentType.ALL)));
                }
            }

            @Nested
            @DisplayName("obfuscating with INHERITED_OVERRIDABLE mode")
            @TestInstance(Lifecycle.PER_CLASS)
            class ObfuscatingInheritedOverridable extends ObfuscatorTest {

                ObfuscatingInheritedOverridable() {
                    super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.with-attributes.inherited-overridable",
                            () -> createObfuscatorWithObfuscatorModeWithAttributes(builder(), ObfuscationMode.INHERIT_OVERRIDABLE));
                }
            }

            @Nested
            @DisplayName("limited")
            @TestInstance(Lifecycle.PER_CLASS)
            class Limited {

                @Nested
                @DisplayName("with truncated indicator")
                @TestInstance(Lifecycle.PER_CLASS)
                class WithTruncatedIndicator extends ObfuscatorTest {

                    WithTruncatedIndicator() {
                        super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.with-attributes.limited.with-indicator",
                                () -> createObfuscatorWithAttributes(builder().limitTo(289)));
                    }
                }

                @Nested
                @DisplayName("without truncated indicator")
                @TestInstance(Lifecycle.PER_CLASS)
                class WithoutTruncatedIndicator extends ObfuscatorTest {

                    WithoutTruncatedIndicator() {
                        super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.with-attributes.limited.without-indicator",
                                () -> createObfuscatorWithAttributes(builder().limitTo(289, l -> l.withTruncatedIndicator(null))));
                    }
                }
            }

            @Nested
            @DisplayName("paths and name matching")
            @TestInstance(Lifecycle.PER_CLASS)
            class PathAndNameMatching extends ObfuscatorTest {

                PathAndNameMatching() {
                    super("XMLObfuscator.input.valid.xml", "XMLObfuscator.expected.valid.with-attributes.path-and-name-matching",
                            () -> createObfuscatorWithPathsAndNamesWithAttributes(builder()));
                }
            }
        }

        @Nested
        @DisplayName("invalid XML")
        @TestInstance(Lifecycle.PER_CLASS)
        class InvalidXML extends ObfuscatorTest {

            InvalidXML() {
                super("XMLObfuscator.input.invalid", "XMLObfuscator.expected.invalid.with-attributes", () -> createObfuscatorWithAttributes());
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
                    super("XMLObfuscator.expected.truncated.with-attributes", true);
                }
            }

            @Nested
            @DisplayName("without warning")
            class WithoutWarning extends TruncatedXMLTest {

                WithoutWarning() {
                    super("XMLObfuscator.expected.truncated.with-attributes.no-warning", false);
                }
            }

            private abstract static class TruncatedXMLTest extends ObfuscatorTest {

                TruncatedXMLTest(String expectedResource, boolean includeWarning) {
                    super("XMLObfuscator.input.truncated", expectedResource, () -> createObfuscatorWithAttributes(includeWarning));
                }
            }
        }
    }

    @Nested
    @DisplayName("all events")
    @TestInstance(Lifecycle.PER_CLASS)
    class AllEvents extends ObfuscatorTest {

        AllEvents() {
            super("XMLObfuscator.input.valid.all-events.xml", "XMLObfuscator.expected.valid.all-events.all", () -> createObfuscator());
        }

        @Nested
        @DisplayName("with attributes")
        @TestInstance(Lifecycle.PER_CLASS)
        @UseSourceTruncation(false)
        class WithAttributes extends ObfuscatorTest {

            WithAttributes() {
                super("XMLObfuscator.input.valid.all-events.xml", "XMLObfuscator.expected.valid.all-events.with-attributes.all",
                        () -> createObfuscatorWithAttributes());
            }

            @Nested
            @DisplayName("limited")
            @TestInstance(Lifecycle.PER_CLASS)
            class Limited extends ObfuscatorTest {

                Limited() {
                    super("XMLObfuscator.input.valid.all-events.xml", "XMLObfuscator.expected.valid.all-events.with-attributes.limited",
                            () -> createObfuscatorWithAttributes(builder().limitTo(30, l -> l.withTruncatedIndicator(null))));
                }
            }
        }
    }

    abstract static class ObfuscatorTest {

        private final String input;
        private final String expected;
        private final String inputWithLargeValues;
        private final String expectedWithLargeValues;
        private final Supplier<Obfuscator> obfuscatorSupplier;
        private final boolean usesSourceTruncation;

        @TestLogger.ForClass(XMLObfuscator.class)
        private Reload4jLoggerContext logger;

        private Appender appender;

        ObfuscatorTest(String inputResource, String expectedResource, Supplier<Obfuscator> obfuscatorSupplier) {
            this.input = readResource(inputResource);
            this.expected = readResource(expectedResource);
            this.obfuscatorSupplier = obfuscatorSupplier;

            String largeValue = createLargeValue();
            inputWithLargeValues = input
                    .replace("text&quot;", largeValue)
                    .replace("text\"", largeValue)
                    .replace("Longer data with embedded <xml>", largeValue + "<xml>")
                    ;
            expectedWithLargeValues = expected
                    .replace("text&quot;", largeValue)
                    .replace("text\"", largeValue)
                    .replace("Longer data with embedded <xml>", largeValue + "<xml>")
                    .replace("(total: " + input.length(), "(total: " + inputWithLargeValues.length());

            this.usesSourceTruncation = usesSourceTruncation();
        }

        private boolean usesSourceTruncation() {
            Class<?> iterator = getClass();
            while (iterator != null) {
                UseSourceTruncation annotation = iterator.getAnnotation(UseSourceTruncation.class);
                if (annotation != null) {
                    return annotation.value();
                }
                iterator = iterator.getEnclosingClass();
            }
            return true;
        }

        @BeforeEach
        void configureLogger() {
            appender = mock(Appender.class);
            logger.setLevel(Level.TRACE)
                    .setAppender(appender)
                    .useParentAppenders(false);
        }

        @Test
        @DisplayName("obfuscateText(CharSequence, int, int)")
        void testObfuscateTextCharSequence() {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            assertEquals(expected, obfuscator.obfuscateText("x" + input + "x", 1, 1 + input.length()).toString());

            assertNoTruncationLogging(appender);
        }

        @Test
        @DisplayName("obfuscateText(CharSequence, int, int) with large values")
        void testObfuscateTextCharSequenceWithLargeValues() {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            assertEquals(expectedWithLargeValues,
                    obfuscator.obfuscateText("x" + inputWithLargeValues + "x", 1, 1 + inputWithLargeValues.length()).toString());

            assertNoTruncationLogging(appender);
        }

        @Test
        @DisplayName("obfuscateText(CharSequence, int, int, Appendable)")
        void testObfuscateTextCharSequenceToAppendable() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringWriter destination = spy(new StringWriter());
            obfuscator.obfuscateText("x" + input + "x", 1, 1 + input.length(), destination);
            assertEquals(expected, destination.toString());
            verify(destination, never()).close();

            assertNoTruncationLogging(appender);
        }

        @Test
        @DisplayName("obfuscateText(CharSequence, int, int, Appendable) with large values")
        void testObfuscateTextCharSequenceToAppendableWithLargeValues() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringWriter destination = spy(new StringWriter());
            obfuscator.obfuscateText("x" + inputWithLargeValues + "x", 1, 1 + inputWithLargeValues.length(), destination);
            assertEquals(expectedWithLargeValues, destination.toString());
            verify(destination, never()).close();

            assertNoTruncationLogging(appender);
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

            assertNoTruncationLogging(appender);
        }

        @Test
        @DisplayName("obfuscateText(Reader, Appendable) with large values")
        @SuppressWarnings("resource")
        void testObfuscateTextReaderToAppendableWithLargeValues() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringWriter destination = spy(new StringWriter());
            Reader reader = spy(new StringReader(inputWithLargeValues));
            obfuscator.obfuscateText(reader, destination);
            assertEquals(expectedWithLargeValues, destination.toString());
            verify(reader, never()).close();
            verify(destination, never()).close();

            destination.getBuffer().delete(0, destination.getBuffer().length());
            reader = spy(new BufferedReader(new StringReader(inputWithLargeValues)));
            obfuscator.obfuscateText(reader, destination);
            assertEquals(expectedWithLargeValues, destination.toString());
            verify(reader, never()).close();
            verify(destination, never()).close();

            if (usesSourceTruncation) {
                assertTruncationLogging(appender);
            } else {
                assertNoTruncationLogging(appender);
            }
        }

        @Test
        @DisplayName("obfuscateText(Reader, Appendable) with large values - logging disabled")
        @SuppressWarnings("resource")
        void testObfuscateTextReaderToAppendableWithLargeValuesLoggingDisabled() throws IOException {
            logger.setLevel(Level.DEBUG);

            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringWriter destination = spy(new StringWriter());
            Reader reader = spy(new StringReader(inputWithLargeValues));
            obfuscator.obfuscateText(reader, destination);
            assertEquals(expectedWithLargeValues, destination.toString());
            verify(reader, never()).close();
            verify(destination, never()).close();

            destination.getBuffer().delete(0, destination.getBuffer().length());
            reader = spy(new BufferedReader(new StringReader(inputWithLargeValues)));
            obfuscator.obfuscateText(reader, destination);
            assertEquals(expectedWithLargeValues, destination.toString());
            verify(reader, never()).close();
            verify(destination, never()).close();

            assertNoTruncationLogging(appender);
        }

        @Test
        @DisplayName("streamTo(Appendable)")
        void testStreamTo() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringWriter writer = spy(new StringWriter());
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

            assertNoTruncationLogging(appender);
        }

        @Test
        @DisplayName("streamTo(Appendable) with large values")
        void testStreamToWithLargeValues() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringWriter writer = spy(new StringWriter());
            try (Writer w = obfuscator.streamTo(writer)) {
                int index = 0;
                while (index < inputWithLargeValues.length()) {
                    int to = Math.min(index + 5, inputWithLargeValues.length());
                    w.write(inputWithLargeValues, index, to - index);
                    index = to;
                }
            }
            assertEquals(expectedWithLargeValues, writer.toString());
            verify(writer, never()).close();

            // streamTo caches the entire results, then obfuscate the cached contents as a CharSequence
            assertNoTruncationLogging(appender);
        }

        private String createLargeValue() {
            char[] chars = new char[Source.OfReader.PREFERRED_MAX_BUFFER_SIZE];
            for (int i = 0; i < chars.length; i += 10) {
                for (int j = 0; j < 10 && i + j < chars.length; j++) {
                    chars[i + j] = (char) ('0' + j);
                }
            }
            return new String(chars);
        }

        private void assertNoTruncationLogging(Appender appender) {
            ArgumentCaptor<LoggingEvent> loggingEvents = ArgumentCaptor.forClass(LoggingEvent.class);

            verify(appender, atLeast(0)).doAppend(loggingEvents.capture());

            List<String> traceMessages = loggingEvents.getAllValues().stream()
                    .filter(event -> event.getLevel() == Level.TRACE)
                    .map(LoggingEvent::getRenderedMessage)
                    .toList();

            assertThat(traceMessages, hasSize(0));
        }

        private void assertTruncationLogging(Appender appender) {
            ArgumentCaptor<LoggingEvent> loggingEvents = ArgumentCaptor.forClass(LoggingEvent.class);

            verify(appender, atLeast(1)).doAppend(loggingEvents.capture());

            List<String> traceMessages = loggingEvents.getAllValues().stream()
                    .filter(event -> event.getLevel() == Level.TRACE)
                    .map(LoggingEvent::getRenderedMessage)
                    .toList();

            assertThat(traceMessages, hasSize(greaterThanOrEqualTo(1)));

            Pattern pattern = Pattern.compile(".*: (\\d+)");
            int expectedMax = (int) (Source.OfReader.PREFERRED_MAX_BUFFER_SIZE * 1.05D);
            List<Integer> sizes = traceMessages.stream()
                    .map(message -> extractSize(message, pattern))
                    .toList();
            assertThat(sizes, everyItem(lessThanOrEqualTo(expectedMax)));
        }

        private int extractSize(String message, Pattern pattern) {
            Matcher matcher = pattern.matcher(message);
            assertTrue(matcher.find());
            return Integer.parseInt(matcher.group(1));
        }

        @Inherited
        @Target(ElementType.TYPE)
        @Retention(RetentionPolicy.RUNTIME)
        @interface UseSourceTruncation {

            boolean value();
        }
    }

    @Nested
    @DisplayName("ElementPath structure")
    class ElementPathStructure {

        @Test
        @DisplayName("indexed obfuscation")
        void testIndexedObfuscation() {
            testElementPathStructure(builder());
        }

        @Test
        @DisplayName("writing obfuscation")
        void testWritingObfuscation() {
            testElementPathStructure(builder().generateXML());
        }

        private void testElementPathStructure(XMLObfuscator.Builder builder) {
            String input = readResource("XMLObfuscator.input.valid.xml");

            List<List<QName>> capturedPaths = new ArrayList<>();
            ElementPath.Matcher capturingMatcher = p -> {
                // Need to create a copy
                capturedPaths.add(List.copyOf(p.elements()));
                return false;
            };

            builder.withElementPath(capturingMatcher, Obfuscator.none())
                    .build()
                    .obfuscateText(input);

            List<List<QName>> expected = List.of(
                    List.of(new QName("root")),
                    List.of(new QName("root"), new QName("urn:test", "text")),
                    List.of(new QName("root"), new QName("urn:test", "cdata")),
                    List.of(new QName("root"), new QName("empty")),
                    List.of(new QName("root"), new QName("element")),
                    List.of(new QName("root"), new QName("element"), new QName("urn:test", "text")),
                    List.of(new QName("root"), new QName("element"), new QName("urn:test", "cdata")),
                    List.of(new QName("root"), new QName("element"), new QName("empty")),
                    List.of(new QName("root"), new QName("element"), new QName("nested")),
                    List.of(new QName("root"), new QName("element"), new QName("nested"), new QName("elem1")),
                    List.of(new QName("root"), new QName("element"), new QName("nested"), new QName("elem2")),
                    List.of(new QName("root"), new QName("notMatchedText")),
                    List.of(new QName("root"), new QName("notMatchedCdata")),
                    List.of(new QName("root"), new QName("notMatchedEmpty")),
                    List.of(new QName("root"), new QName("notMatchedElement")),
                    List.of(new QName("root"), new QName("notMatchedElement"), new QName("notMatchedText")),
                    List.of(new QName("root"), new QName("notMatchedElement"), new QName("notMatchedCdata")),
                    List.of(new QName("root"), new QName("notMatchedElement"), new QName("notMatchedEmpty")),
                    List.of(new QName("root"), new QName("nested")),
                    List.of(new QName("root"), new QName("nested"), new QName("urn:test", "text")),
                    List.of(new QName("root"), new QName("nested"), new QName("urn:test", "cdata")),
                    List.of(new QName("root"), new QName("nested"), new QName("empty")),
                    List.of(new QName("root"), new QName("nested"), new QName("element")),
                    List.of(new QName("root"), new QName("nested"), new QName("element"), new QName("urn:test", "text")),
                    List.of(new QName("root"), new QName("nested"), new QName("element"), new QName("urn:test", "cdata")),
                    List.of(new QName("root"), new QName("nested"), new QName("element"), new QName("empty")),
                    List.of(new QName("root"), new QName("nested"), new QName("element"), new QName("nested")),
                    List.of(new QName("root"), new QName("nested"), new QName("element"), new QName("nested"), new QName("elem1")),
                    List.of(new QName("root"), new QName("nested"), new QName("element"), new QName("nested"), new QName("elem2")),
                    List.of(new QName("root"), new QName("nested"), new QName("notMatchedText")),
                    List.of(new QName("root"), new QName("nested"), new QName("notMatchedCdata")),
                    List.of(new QName("root"), new QName("nested"), new QName("notMatchedEmpty")),
                    List.of(new QName("root"), new QName("nested"), new QName("notMatchedElement")),
                    List.of(new QName("root"), new QName("nested"), new QName("notMatchedElement"), new QName("notMatchedText")),
                    List.of(new QName("root"), new QName("nested"), new QName("notMatchedElement"), new QName("notMatchedCdata")),
                    List.of(new QName("root"), new QName("nested"), new QName("notMatchedElement"), new QName("notMatchedEmpty")),
                    List.of(new QName("root"), new QName("notObfuscated")),
                    List.of(new QName("root"), new QName("notObfuscated"), new QName("urn:test", "text")),
                    List.of(new QName("root"), new QName("notObfuscated"), new QName("urn:test", "cdata")),
                    List.of(new QName("root"), new QName("notObfuscated"), new QName("empty")),
                    List.of(new QName("root"), new QName("notObfuscated"), new QName("element")),
                    List.of(new QName("root"), new QName("notObfuscated"), new QName("element"), new QName("urn:test", "text")),
                    List.of(new QName("root"), new QName("notObfuscated"), new QName("element"), new QName("urn:test", "cdata")),
                    List.of(new QName("root"), new QName("notObfuscated"), new QName("element"), new QName("empty")),
                    List.of(new QName("root"), new QName("notObfuscated"), new QName("element"), new QName("nested")),
                    List.of(new QName("root"), new QName("notObfuscated"), new QName("element"), new QName("nested"), new QName("elem1")),
                    List.of(new QName("root"), new QName("notObfuscated"), new QName("element"), new QName("nested"), new QName("elem2")),
                    List.of(new QName("root"), new QName("notObfuscated"), new QName("notMatchedText")),
                    List.of(new QName("root"), new QName("notObfuscated"), new QName("notMatchedCdata")),
                    List.of(new QName("root"), new QName("notObfuscated"), new QName("notMatchedEmpty")),
                    List.of(new QName("root"), new QName("notObfuscated"), new QName("notMatchedElement")),
                    List.of(new QName("root"), new QName("notObfuscated"), new QName("notMatchedElement"), new QName("notMatchedText")),
                    List.of(new QName("root"), new QName("notObfuscated"), new QName("notMatchedElement"), new QName("notMatchedCdata")),
                    List.of(new QName("root"), new QName("notObfuscated"), new QName("notMatchedElement"), new QName("notMatchedEmpty"))
            );

            assertEquals(expected, capturedPaths);
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
                .withElement("element", fixedLength(3, 'e'))
                .withElement("notObfuscated", none())
                .build();
    }

    private static Obfuscator createObfuscatorCaseInsensitive(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withElement("TEXT", obfuscator)
                .withElement("CDATA", obfuscator)
                .withElement("EMPTY", obfuscator)
                .withElement("ELEMENT", fixedLength(3, 'e'))
                .withElement("NOTOBFUSCATED", none())
                .build();
    }

    private static Obfuscator createObfuscatorCaseInsensitive(Builder builder, Consumer<LocalNameElementConfigurer> configurer) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withElement("TEXT", obfuscator, configurer)
                .withElement("CDATA", obfuscator, configurer)
                .withElement("EMPTY", obfuscator, configurer)
                .withElement("ELEMENT", fixedLength(3, 'e'), configurer)
                .withElement("NOTOBFUSCATED", none(), configurer)
                .build();
    }

    private static XMLObfuscator createObfuscatorQualifiedNames(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        Obfuscator unusedObfuscator = fixedValue("not used");
        return builder
                .withElement(new QName("urn:test", "text"), obfuscator)
                .withElement(new QName("urn:test", "cdata"), obfuscator)
                .withElement(new QName("empty"), obfuscator)
                .withElement(new QName("element"), fixedLength(3, 'e'))
                .withElement(new QName("notObfuscated"), none())

                .withElement(new QName(XMLConstants.XML_NS_URI, "text"), unusedObfuscator)
                .withElement(new QName(XMLConstants.XML_NS_URI, "cdata"), unusedObfuscator)
                .withElement(new QName(XMLConstants.XML_NS_URI, "empty"), unusedObfuscator)
                .withElement(new QName(XMLConstants.XML_NS_URI, "element"), unusedObfuscator)
                .withElement(new QName(XMLConstants.XML_NS_URI, "notObfuscated"), unusedObfuscator)

                .withElement("text", unusedObfuscator)
                .withElement("cdata", unusedObfuscator)
                .withElement("empty", unusedObfuscator)
                .withElement("element", unusedObfuscator)
                .withElement("notObfuscated", unusedObfuscator)
                .build();
    }

    private static Obfuscator createObfuscatorObfuscatingAll(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withElement("text", obfuscator, p -> p.withContentTypes(ContentType.ALL).forNestedElements(ObfuscationMode.INHERIT))
                .withElement("cdata", obfuscator, p -> p.withContentTypes(ContentType.ALL).forNestedElements(ObfuscationMode.INHERIT))
                .withElement("empty", obfuscator, p -> p.withContentTypes(ContentType.ALL).forNestedElements(ObfuscationMode.INHERIT))
                .withElement("element", fixedLength(3, 'e'), p -> p.withContentTypes(ContentType.ALL).forNestedElements(ObfuscationMode.INHERIT))
                .withElement("notObfuscated", none(), p -> p.withContentTypes(ContentType.ALL).forNestedElements(ObfuscationMode.INHERIT))
                .build();
    }

    private static Obfuscator createObfuscatorObfuscatingTextOnly(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withElement("text", obfuscator, p -> p.withContentTypes(ContentType.TEXT))
                .withElement("cdata", obfuscator, p -> p.withContentTypes(ContentType.TEXT))
                .withElement("empty", obfuscator, p -> p.withContentTypes(ContentType.TEXT))
                .withElement("element", fixedLength(3, 'e'), p -> p.withContentTypes(ContentType.TEXT))
                .withElement("notObfuscated", none(), p -> p.withContentTypes(ContentType.TEXT))
                .build();
    }

    private static Obfuscator createObfuscatorObfuscatingNestedElementsOnly(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withElement("text", obfuscator, p -> p.withContentTypes(ContentType.NESTED_ELEMENT))
                .withElement("cdata", obfuscator, p -> p.withContentTypes(ContentType.NESTED_ELEMENT))
                .withElement("empty", obfuscator, p -> p.withContentTypes(ContentType.NESTED_ELEMENT))
                .withElement("element", fixedLength(3, 'e'), p -> p.withContentTypes(ContentType.NESTED_ELEMENT))
                .withElement("notObfuscated", none(), p -> p.withContentTypes(ContentType.NESTED_ELEMENT))
                .build();
    }

    private static Obfuscator createObfuscatorWithObfuscatorMode(Builder builder, ObfuscationMode obfuscationMode) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .forNestedElementsByDefault(obfuscationMode)
                .withElement("text", obfuscator)
                .withElement("cdata", obfuscator)
                .withElement("empty", obfuscator)
                .withElement("element", fixedLength(3, 'e'))
                .withElement("notObfuscated", none())
                .build();
    }

    private static Obfuscator createObfuscatorWithAttributes() {
        return builder()
                .transform(XMLObfuscatorTest::createObfuscatorWithAttributes);
    }

    private static Obfuscator createObfuscatorWithAttributes(boolean includeWarning) {
        Builder builder = builder();
        if (!includeWarning) {
            builder = builder.withMalformedXMLWarning(null);
        }
        return builder.transform(XMLObfuscatorTest::createObfuscatorWithAttributes);
    }

    private static XMLObfuscator createObfuscatorWithAttributes(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return createObfuscator(builder
                .withAttribute("a", obfuscator, a -> a
                        .forElement("cdata", fixedLength(5))));
    }

    private static Obfuscator createObfuscatorCaseInsensitiveWithAttributes(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return createObfuscatorCaseInsensitive(builder
                .withAttribute("A", obfuscator, a -> a
                        .forElement("CDATA", fixedLength(5))));
    }

    private static Obfuscator createObfuscatorCaseInsensitiveWithAttributes(Builder builder,
                                                                            Consumer<LocalNameElementConfigurer> elementConfigurer,
                                                                            Consumer<LocalNameAttributeConfigurer> attributeConfigurer,
                                                                            Consumer<LocalNameAttributeElementConfigurer> attrElementConfigurer) {
        Obfuscator obfuscator = fixedLength(3);
        return createObfuscatorCaseInsensitive(builder
                .withAttribute("A", obfuscator, attributeConfigurer.andThen(a -> a
                        .forElement("CDATA", fixedLength(5), attrElementConfigurer))), elementConfigurer);
    }

    private static XMLObfuscator createObfuscatorQualifiedNamesWithAttributes(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        Obfuscator unusedObfuscator = fixedValue("not used");
        return createObfuscatorQualifiedNames(builder
                .withAttribute(new QName("urn:test", "a"), obfuscator, a -> a
                        .forElement(new QName("urn:test", "cdata"), fixedLength(5)))
                .withAttribute(new QName(XMLConstants.XML_NS_URI, "a"), unusedObfuscator, a -> a
                        .forElement("cdata", unusedObfuscator))
                .withAttribute("a", unusedObfuscator, a -> a
                        .forElement("cdata", unusedObfuscator)));
    }

    private static Obfuscator createObfuscatorObfuscatingAllWithAttributes(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return createObfuscatorObfuscatingAll(builder
                .withAttribute("a", obfuscator, a -> a
                        .forElement("cdata", fixedLength(5))));
    }

    private static Obfuscator createObfuscatorObfuscatingTextOnlyWithAttributes(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return createObfuscatorObfuscatingTextOnly(builder
                .withAttribute("a", obfuscator, a -> a
                        .forElement("cdata", fixedLength(5))));
    }

    private static Obfuscator createObfuscatorObfuscatingNestedElementsOnlyWithAttributes(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return createObfuscatorObfuscatingNestedElementsOnly(builder
                .withAttribute("a", obfuscator, a -> a
                        .forElement("cdata", fixedLength(5))));
    }

    private static Obfuscator createObfuscatorWithObfuscatorModeWithAttributes(Builder builder, ObfuscationMode obfuscationMode) {
        Obfuscator obfuscator = fixedLength(3);
        return createObfuscatorWithObfuscatorMode(builder
                .withAttribute("a", obfuscator, a -> a
                        .forElement("cdata", fixedLength(5))),
                obfuscationMode);
    }

    private static Obfuscator createObfuscatorWithPathsAndNames(Builder builder) {
        return builder
                .withElement("text", Obfuscator.fixedValue("<TEXT>"))
                .withElementPath(ElementPath.startsWith("root", "nested"), Obfuscator.fixedValue("<nested>"), element -> element
                        .forNestedElements(ObfuscationMode.INHERIT))
                .withElementPath(ElementPath.containsAt(-2, new QName("nested")), Obfuscator.fixedValue("<in-nested>"))
                .build();
    }

    private static Obfuscator createObfuscatorWithPathsAndNamesWithAttributes(Builder builder) {
        return createObfuscatorWithPathsAndNames(builder
                .withAttribute("a", Obfuscator.fixedLength(3), a -> a
                        .forElementPath(ElementPath.startsWith("root", "nested"), Obfuscator.fixedValue("<nested>"))
                        .forElementPath(ElementPath.containsAt(-2, new QName("nested")), Obfuscator.fixedValue("<in-nested>"))));
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
