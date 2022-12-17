/*
 * SourceTest.java
 * Copyright 2022 Rob Spoor
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

import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.counting;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.repeatChar;
import static com.github.robtimus.obfuscation.xml.Source.OfReader.DEFAULT_PREFERRED_MAX_BUFFER_SIZE;
import static com.github.robtimus.obfuscation.xml.Source.OfReader.LOGGER_NAME;
import static com.github.robtimus.obfuscation.xml.Source.OfReader.PREFERRED_MAX_BUFFER_SIZE;
import static com.github.robtimus.obfuscation.xml.Source.OfReader.PREFERRED_MAX_BUFFER_SIZE_PROPERTY;
import static com.github.robtimus.obfuscation.xml.Source.OfReader.getPreferredMaxBufferSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import java.io.StringReader;
import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ClearSystemProperty;
import org.junitpioneer.jupiter.SetSystemProperty;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import com.github.robtimus.junit.support.extension.testlogger.Reload4jLoggerContext;
import com.github.robtimus.junit.support.extension.testlogger.TestLogger;
import com.github.robtimus.obfuscation.support.CountingReader;

@SuppressWarnings("nls")
final class SourceTest {

    private SourceTest() {
    }

    @BeforeAll
    static void ensurePreferredMaxBufferSizeIsInitialized() {
        // By calling this before any tests, PREFERRED_MAX_BUFFER_SIZE_PROPERTY will already have been initialized,
        // so that will not occur during any test
        Source.OfReader.getPreferredMaxBufferSize();
    }

    @Nested
    @DisplayName("OfCharSequence")
    class OfCharSequenceTest {

        @Test
        @DisplayName("needsTruncating and truncate")
        void testNeedsTruncatingAndTruncate() {
            Source.OfCharSequence source = new Source.OfCharSequence("foo");

            assertFalse(source.needsTruncating());
            assertThrows(UnsupportedOperationException.class, source::truncate);
        }
    }

    @Nested
    @DisplayName("OfReader")
    class OfReaderTest {

        @Test
        @DisplayName("append(char)")
        void testAppendChar() {
            Logger logger = mock(Logger.class);
            when(logger.isTraceEnabled()).thenReturn(true);

            @SuppressWarnings("resource")
            CountingReader reader = counting(new StringReader(""));

            Source.OfReader source = new Source.OfReader(reader, logger);

            for (int i = 0; i < PREFERRED_MAX_BUFFER_SIZE; i++) {
                source.append('*');

                assertFalse(source.needsTruncating());

                verifyNoInteractions(logger);
            }

            source.append('0');

            // not truncated, as that would lose information
            assertTrue(source.needsTruncating());

            // Make half the buffer available for truncating
            StringBuilder sb = new StringBuilder();
            assertDoesNotThrow(() -> source.appendTo(0, PREFERRED_MAX_BUFFER_SIZE / 2, sb));
            assertEquals(repeatChar('*', PREFERRED_MAX_BUFFER_SIZE / 2).toString(), sb.toString());

            source.append('1');

            // automatically truncated
            assertFalse(source.needsTruncating());

            verify(logger).trace(Messages.Source.truncating(PREFERRED_MAX_BUFFER_SIZE + 1));
            verify(logger).trace(Messages.Source.truncated(PREFERRED_MAX_BUFFER_SIZE - PREFERRED_MAX_BUFFER_SIZE / 2 + 1));
            verify(logger, times(2)).isTraceEnabled();
            verifyNoMoreInteractions(logger);

            sb.delete(0, sb.length());
            assertDoesNotThrow(() -> source.appendTo(PREFERRED_MAX_BUFFER_SIZE / 2, PREFERRED_MAX_BUFFER_SIZE + 2, sb));
            assertEquals(repeatChar('*', PREFERRED_MAX_BUFFER_SIZE / 2) + "01", sb.toString());
        }

        @Nested
        @DisplayName("append(CharSequence)")
        class AppendCharSequence {

            @Test
            @DisplayName("null CharSequence")
            void testNullCharSequence() {
                Logger logger = mock(Logger.class);
                when(logger.isTraceEnabled()).thenReturn(true);

                @SuppressWarnings("resource")
                CountingReader reader = counting(new StringReader(""));

                Source.OfReader source = new Source.OfReader(reader, logger);

                source.append(null);

                assertFalse(source.needsTruncating());

                StringBuilder sb = new StringBuilder();
                assertDoesNotThrow(() -> source.appendTo(0, 4, sb));
                assertEquals("null", sb.toString());
            }

            @Test
            @DisplayName("truncation")
            void testTruncation() {
                Logger logger = mock(Logger.class);
                when(logger.isTraceEnabled()).thenReturn(true);

                @SuppressWarnings("resource")
                CountingReader reader = counting(new StringReader(""));

                Source.OfReader source = new Source.OfReader(reader, logger);

                for (int i = 0; i < PREFERRED_MAX_BUFFER_SIZE; i += 64) {
                    source.append(repeatChar('*', 64));

                    assertFalse(source.needsTruncating());

                    verifyNoInteractions(logger);
                }

                source.append("foo");

                // not truncated, as that would lose information
                assertTrue(source.needsTruncating());

                // Make half the buffer available for truncating
                StringBuilder sb = new StringBuilder();
                assertDoesNotThrow(() -> source.appendTo(0, PREFERRED_MAX_BUFFER_SIZE / 2, sb));
                assertEquals(repeatChar('*', PREFERRED_MAX_BUFFER_SIZE / 2).toString(), sb.toString());

                source.append("bar");

                // automatically truncated
                assertFalse(source.needsTruncating());

                verify(logger).trace(Messages.Source.truncating(PREFERRED_MAX_BUFFER_SIZE + 3));
                verify(logger).trace(Messages.Source.truncated(PREFERRED_MAX_BUFFER_SIZE - PREFERRED_MAX_BUFFER_SIZE / 2 + 3));
                verify(logger, times(2)).isTraceEnabled();
                verifyNoMoreInteractions(logger);

                sb.delete(0, sb.length());
                assertDoesNotThrow(() -> source.appendTo(PREFERRED_MAX_BUFFER_SIZE / 2, PREFERRED_MAX_BUFFER_SIZE + 6, sb));
                assertEquals(repeatChar('*', PREFERRED_MAX_BUFFER_SIZE / 2) + "foobar", sb.toString());
            }
        }

        @Nested
        @DisplayName("append(CharSequence, int, int)")
        class AppendCharSequencePortion {

            @Test
            @DisplayName("null CharSequence")
            void testNullCharSequence() {
                Logger logger = mock(Logger.class);
                when(logger.isTraceEnabled()).thenReturn(true);

                @SuppressWarnings("resource")
                CountingReader reader = counting(new StringReader(""));

                Source.OfReader source = new Source.OfReader(reader, logger);

                source.append(null, 1, 3);

                assertFalse(source.needsTruncating());

                StringBuilder sb = new StringBuilder();
                assertDoesNotThrow(() -> source.appendTo(0, 2, sb));
                assertEquals("ul", sb.toString());
            }

            @Test
            @DisplayName("truncation")
            void testTruncation() {
                Logger logger = mock(Logger.class);
                when(logger.isTraceEnabled()).thenReturn(true);

                @SuppressWarnings("resource")
                CountingReader reader = counting(new StringReader(""));

                Source.OfReader source = new Source.OfReader(reader, logger);

                for (int i = 0; i < PREFERRED_MAX_BUFFER_SIZE; i += 64) {
                    source.append("x" + repeatChar('*', 64) + "x", 1, 65);

                    assertFalse(source.needsTruncating());

                    verifyNoInteractions(logger);
                }

                source.append("xfoox", 1, 4);

                // not truncated, as that would lose information
                assertTrue(source.needsTruncating());

                // Make half the buffer available for truncating
                StringBuilder sb = new StringBuilder();
                assertDoesNotThrow(() -> source.appendTo(0, PREFERRED_MAX_BUFFER_SIZE / 2, sb));
                assertEquals(repeatChar('*', PREFERRED_MAX_BUFFER_SIZE / 2).toString(), sb.toString());

                source.append("xbarx", 1, 4);

                // automatically truncated
                assertFalse(source.needsTruncating());

                verify(logger).trace(Messages.Source.truncating(PREFERRED_MAX_BUFFER_SIZE + 3));
                verify(logger).trace(Messages.Source.truncated(PREFERRED_MAX_BUFFER_SIZE - PREFERRED_MAX_BUFFER_SIZE / 2 + 3));
                verify(logger, times(2)).isTraceEnabled();
                verifyNoMoreInteractions(logger);

                sb.delete(0, sb.length());
                assertDoesNotThrow(() -> source.appendTo(PREFERRED_MAX_BUFFER_SIZE / 2, PREFERRED_MAX_BUFFER_SIZE + 6, sb));
                assertEquals(repeatChar('*', PREFERRED_MAX_BUFFER_SIZE / 2) + "foobar", sb.toString());
            }
        }

        @Test
        @DisplayName("LOGGER_NAME")
        void testLoggerName() {
            assertEquals(getClass().getPackage().getName(), LOGGER_NAME);
        }

        @Test
        @DisplayName("PREFERRED_MAX_BUFFER_SIZE_PROPERTY")
        void testPreferredMaxBufferSizeProperty() {
            assertEquals(getClass().getPackage().getName() + ".preferredMaxBufferSize", PREFERRED_MAX_BUFFER_SIZE_PROPERTY);
        }

        @Nested
        @DisplayName("getPreferredMaxBufferSize")
        class GetPreferredMaxBufferSize {

            @TestLogger(LOGGER_NAME)
            private Reload4jLoggerContext logger;

            private Appender appender;

            @BeforeEach
            void configureLogger() {
                appender = mock(Appender.class);
                logger.setLevel(Level.TRACE)
                        .setAppender(appender)
                        .useParentAppenders(false);
            }

            @Test
            @DisplayName("system property not set")
            @ClearSystemProperty(key = PREFERRED_MAX_BUFFER_SIZE_PROPERTY)
            void testSystemPropertyNotSet() {
                assertEquals(DEFAULT_PREFERRED_MAX_BUFFER_SIZE, getPreferredMaxBufferSize());

                verify(appender, never()).doAppend(any());
            }

            @Test
            @DisplayName("system property set to positive")
            @SetSystemProperty(key = PREFERRED_MAX_BUFFER_SIZE_PROPERTY, value = "1")
            void testSystemPropertySetToPositive() {
                assertEquals(1, getPreferredMaxBufferSize());

                verify(appender, never()).doAppend(any());
            }

            @Test
            @DisplayName("system property set to 0")
            @SetSystemProperty(key = PREFERRED_MAX_BUFFER_SIZE_PROPERTY, value = "0")
            void testSystemPropertySetToZero() {
                assertEquals(DEFAULT_PREFERRED_MAX_BUFFER_SIZE, getPreferredMaxBufferSize());

                ArgumentCaptor<LoggingEvent> eventCaptor = ArgumentCaptor.forClass(LoggingEvent.class);

                verify(appender).doAppend(eventCaptor.capture());

                LoggingEvent event = eventCaptor.getValue();
                assertEquals(Level.WARN, event.getLevel());
                assertEquals(Messages.Source.preferredMaxBufferSize.notPositive(0, DEFAULT_PREFERRED_MAX_BUFFER_SIZE), event.getRenderedMessage());
            }

            @Test
            @DisplayName("system property set to negative")
            @SetSystemProperty(key = PREFERRED_MAX_BUFFER_SIZE_PROPERTY, value = "-1")
            void testSystemPropertySetToNegative() {
                assertEquals(DEFAULT_PREFERRED_MAX_BUFFER_SIZE, getPreferredMaxBufferSize());

                ArgumentCaptor<LoggingEvent> eventCaptor = ArgumentCaptor.forClass(LoggingEvent.class);

                verify(appender, atLeastOnce()).doAppend(eventCaptor.capture());

                LoggingEvent event = eventCaptor.getValue();
                assertEquals(Level.WARN, event.getLevel());
                assertEquals(Messages.Source.preferredMaxBufferSize.notPositive(-1, DEFAULT_PREFERRED_MAX_BUFFER_SIZE), event.getRenderedMessage());
            }

            @Test
            @DisplayName("system property set to non-numeric")
            @SetSystemProperty(key = PREFERRED_MAX_BUFFER_SIZE_PROPERTY, value = "a12")
            void testSystemPropertySetToNonNumeric() {
                assertEquals(DEFAULT_PREFERRED_MAX_BUFFER_SIZE, getPreferredMaxBufferSize());

                ArgumentCaptor<LoggingEvent> eventCaptor = ArgumentCaptor.forClass(LoggingEvent.class);

                verify(appender).doAppend(eventCaptor.capture());

                LoggingEvent event = eventCaptor.getValue();
                assertEquals(Level.WARN, event.getLevel());
                assertEquals(Messages.Source.preferredMaxBufferSize.notNumeric("a12", DEFAULT_PREFERRED_MAX_BUFFER_SIZE), event.getRenderedMessage());
            }
        }
    }
}
