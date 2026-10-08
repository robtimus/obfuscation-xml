/*
 * ElementPathsTest.java
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import com.github.robtimus.obfuscation.xml.XMLObfuscator.ElementPath;

@SuppressWarnings("nls")
class ElementPathsTest {

    private static final String NAMESPACE_URI = "urn:test";

    @Nested
    class Is {

        @Nested
        class MatchingQNames {

            @Test
            void testExpectedShorterThanPath() {
                ElementPath.Matcher matcher = ElementPath.is(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"));

                ElementPath path = newPath("a", "b", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testExpectedEqualsPath() {
                ElementPath.Matcher matcher = ElementPath.is(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                ElementPath path = newPath("a", "b", "c");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedLargerThanPath() {
                ElementPath.Matcher matcher = ElementPath.is(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                ElementPath path = newPath("a", "b");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtFirst() {
                ElementPath.Matcher matcher = ElementPath.is(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                ElementPath path = newPath(
                        new QName("a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtLast() {
                ElementPath.Matcher matcher = ElementPath.is(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                ElementPath path = newPath(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName("c"));

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                ElementPath.Matcher matcher = ElementPath.is(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                assertEquals("is(/{%1$s}a/{%1$s}b/{%1$s}c)".formatted(NAMESPACE_URI), matcher.toString());
            }
        }

        @Nested
        class MatchingStrings {

            @Test
            void testExpectedShorterThanPath() {
                ElementPath.Matcher matcher = ElementPath.is("a", "b");

                ElementPath path = newPath("a", "b", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testExpectedEqualsPath() {
                ElementPath.Matcher matcher = ElementPath.is("a", "b", "c");

                ElementPath path = newPath("a", "b", "c");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedLargerThanPath() {
                ElementPath.Matcher matcher = ElementPath.is("a", "b", "c");

                ElementPath path = newPath("a", "b");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtFirst() {
                ElementPath.Matcher matcher = ElementPath.is("a", "b", "c");

                ElementPath path = newPath("A", "b", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtLast() {
                ElementPath.Matcher matcher = ElementPath.is("a", "b", "c");

                ElementPath path = newPath("a", "b", "C");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                ElementPath.Matcher matcher = ElementPath.is("a", "b", "c");

                assertEquals("is(/a/b/c)", matcher.toString());
            }
        }
    }

    @Nested
    class IsIgnoreCase {

        @Test
        void testExpectedShorterThanPath() {
            ElementPath.Matcher matcher = ElementPath.isIgnoreCase("a", "b");

            ElementPath path = newPath("a", "b", "c");

            assertFalse(matcher.test(path));
        }

        @Test
        void testExpectedEqualsPath() {
            ElementPath.Matcher matcher = ElementPath.isIgnoreCase("a", "b", "c");

            ElementPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));
        }

        @Test
        void testExpectedLargerThanPath() {
            ElementPath.Matcher matcher = ElementPath.isIgnoreCase("a", "b", "c");

            ElementPath path = newPath("a", "b");

            assertFalse(matcher.test(path));
        }

        @Test
        void testCaseMismatchOnly() {
            ElementPath.Matcher matcher = ElementPath.isIgnoreCase("a", "b", "c");

            ElementPath path = newPath("A", "B", "C");

            assertTrue(matcher.test(path));
        }

        @Test
        void testMismatchAtFirst() {
            ElementPath.Matcher matcher = ElementPath.isIgnoreCase("a", "b", "c");

            ElementPath path = newPath("d", "b", "c");

            assertFalse(matcher.test(path));
        }

        @Test
        void testMismatchAtLast() {
            ElementPath.Matcher matcher = ElementPath.isIgnoreCase("a", "b", "c");

            ElementPath path = newPath("a", "b", "d");

            assertFalse(matcher.test(path));
        }

        @Test
        void testToString() {
            ElementPath.Matcher matcher = ElementPath.isIgnoreCase("a", "b", "c");

            assertEquals("isIgnoreCase(/a/b/c)", matcher.toString());
        }
    }

    @Nested
    class StartsWith {

        @Nested
        class MatchingQNames {

            @Test
            void testPrefixShorterThanPath() {
                ElementPath.Matcher matcher = ElementPath.startsWith(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"));

                ElementPath path = newPath(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                assertTrue(matcher.test(path));
            }

            @Test
            void testPrefixEqualsPath() {
                ElementPath.Matcher matcher = ElementPath.startsWith(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                ElementPath path = newPath(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                assertTrue(matcher.test(path));
            }

            @Test
            void testPrefixLargerThanPath() {
                ElementPath.Matcher matcher = ElementPath.startsWith(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                ElementPath path = newPath(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"));

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtFirst() {
                ElementPath.Matcher matcher = ElementPath.startsWith(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                ElementPath path = newPath(
                        new QName("a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtLast() {
                ElementPath.Matcher matcher = ElementPath.startsWith(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                ElementPath path = newPath(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName("c"));

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                ElementPath.Matcher matcher = ElementPath.startsWith(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                assertEquals("startsWith(/{%1$s}a/{%1$s}b/{%1$s}c)".formatted(NAMESPACE_URI), matcher.toString());
            }
        }

        @Nested
        class MatchingStrings {

            @Test
            void testPrefixShorterThanPath() {
                ElementPath.Matcher matcher = ElementPath.startsWith("a", "b");

                ElementPath path = newPath("a", "b", "c");

                assertTrue(matcher.test(path));
            }

            @Test
            void testPrefixEqualsPath() {
                ElementPath.Matcher matcher = ElementPath.startsWith("a", "b", "c");

                ElementPath path = newPath("a", "b", "c");

                assertTrue(matcher.test(path));
            }

            @Test
            void testPrefixLargerThanPath() {
                ElementPath.Matcher matcher = ElementPath.startsWith("a", "b", "c");

                ElementPath path = newPath("a", "b");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtFirst() {
                ElementPath.Matcher matcher = ElementPath.startsWith("a", "b", "c");

                ElementPath path = newPath("A", "b", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtLast() {
                ElementPath.Matcher matcher = ElementPath.startsWith("a", "b", "c");

                ElementPath path = newPath("a", "b", "C");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                ElementPath.Matcher matcher = ElementPath.startsWith("a", "b", "c");

                assertEquals("startsWith(/a/b/c)", matcher.toString());
            }
        }
    }

    @Nested
    class StartsWithIgnoreCase {

        @Test
        void testPrefixShorterThanPath() {
            ElementPath.Matcher matcher = ElementPath.startsWithIgnoreCase("a", "b");

            ElementPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));
        }

        @Test
        void testPrefixEqualsPath() {
            ElementPath.Matcher matcher = ElementPath.startsWithIgnoreCase("a", "b", "c");

            ElementPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));
        }

        @Test
        void testPrefixLargerThanPath() {
            ElementPath.Matcher matcher = ElementPath.startsWithIgnoreCase("a", "b", "c");

            ElementPath path = newPath("a", "b");

            assertFalse(matcher.test(path));
        }

        @Test
        void testCaseMismatchOnly() {
            ElementPath.Matcher matcher = ElementPath.startsWithIgnoreCase("a", "b", "c");

            ElementPath path = newPath("A", "B", "C");

            assertTrue(matcher.test(path));
        }

        @Test
        void testMismatchAtFirst() {
            ElementPath.Matcher matcher = ElementPath.startsWithIgnoreCase("a", "b", "c");

            ElementPath path = newPath("d", "b", "c");

            assertFalse(matcher.test(path));
        }

        @Test
        void testMismatchAtLast() {
            ElementPath.Matcher matcher = ElementPath.startsWithIgnoreCase("a", "b", "c");

            ElementPath path = newPath("a", "b", "d");

            assertFalse(matcher.test(path));
        }

        @Test
        void testToString() {
            ElementPath.Matcher matcher = ElementPath.startsWithIgnoreCase("a", "b", "c");

            assertEquals("startsWithIgnoreCase(/a/b/c)", matcher.toString());
        }
    }

    @Nested
    class EndsWith {

        @Nested
        class MatchingQNames {

            @Test
            void testPostfixShorterThanPath() {
                ElementPath.Matcher matcher = ElementPath.endsWith(
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                ElementPath path = newPath(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                assertTrue(matcher.test(path));
            }

            @Test
            void testPostfixEqualsPath() {
                ElementPath.Matcher matcher = ElementPath.endsWith(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                ElementPath path = newPath(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                assertTrue(matcher.test(path));
            }

            @Test
            void testPostfixLargerThanPath() {
                ElementPath.Matcher matcher = ElementPath.endsWith(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                ElementPath path = newPath(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"));

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtFirst() {
                ElementPath.Matcher matcher = ElementPath.endsWith(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                ElementPath path = newPath(
                        new QName("a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtLast() {
                ElementPath.Matcher matcher = ElementPath.endsWith(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                ElementPath path = newPath(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName("c"));

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                ElementPath.Matcher matcher = ElementPath.endsWith(
                        new QName(NAMESPACE_URI, "a"),
                        new QName(NAMESPACE_URI, "b"),
                        new QName(NAMESPACE_URI, "c"));

                assertEquals("endsWith(/{%1$s}a/{%1$s}b/{%1$s}c)".formatted(NAMESPACE_URI), matcher.toString());
            }
        }

        @Nested
        class MatchingStrings {

            @Test
            void testPostfixShorterThanPath() {
                ElementPath.Matcher matcher = ElementPath.endsWith("b", "c");

                ElementPath path = newPath("a", "b", "c");

                assertTrue(matcher.test(path));
            }

            @Test
            void testPostfixEqualsPath() {
                ElementPath.Matcher matcher = ElementPath.endsWith("a", "b", "c");

                ElementPath path = newPath("a", "b", "c");

                assertTrue(matcher.test(path));
            }

            @Test
            void testPostfixLargerThanPath() {
                ElementPath.Matcher matcher = ElementPath.endsWith("a", "b", "c");

                ElementPath path = newPath("a", "b");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtFirst() {
                ElementPath.Matcher matcher = ElementPath.endsWith("a", "b", "c");

                ElementPath path = newPath("A", "b", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtLast() {
                ElementPath.Matcher matcher = ElementPath.endsWith("a", "b", "c");

                ElementPath path = newPath("a", "b", "C");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                ElementPath.Matcher matcher = ElementPath.endsWith("a", "b", "c");

                assertEquals("endsWith(/a/b/c)", matcher.toString());
            }
        }
    }

    @Nested
    class EndsWithIgnoreCase {

        @Test
        void testPostfixShorterThanPath() {
            ElementPath.Matcher matcher = ElementPath.endsWithIgnoreCase("b", "c");

            ElementPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));
        }

        @Test
        void testPostfixEqualsPath() {
            ElementPath.Matcher matcher = ElementPath.endsWithIgnoreCase("a", "b", "c");

            ElementPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));
        }

        @Test
        void testPostfixLargerThanPath() {
            ElementPath.Matcher matcher = ElementPath.endsWithIgnoreCase("a", "b", "c");

            ElementPath path = newPath("a", "b");

            assertFalse(matcher.test(path));
        }

        @Test
        void testCaseMismatchOnly() {
            ElementPath.Matcher matcher = ElementPath.endsWithIgnoreCase("a", "b", "c");

            ElementPath path = newPath("A", "B", "C");

            assertTrue(matcher.test(path));
        }

        @Test
        void testMismatchAtFirst() {
            ElementPath.Matcher matcher = ElementPath.endsWithIgnoreCase("a", "b", "c");

            ElementPath path = newPath("d", "b", "c");

            assertFalse(matcher.test(path));
        }

        @Test
        void testMismatchAtLast() {
            ElementPath.Matcher matcher = ElementPath.endsWithIgnoreCase("a", "b", "c");

            ElementPath path = newPath("a", "b", "d");

            assertFalse(matcher.test(path));
        }

        @Test
        void testToString() {
            ElementPath.Matcher matcher = ElementPath.endsWithIgnoreCase("a", "b", "c");

            assertEquals("endsWithIgnoreCase(/a/b/c)", matcher.toString());
        }
    }

    @Nested
    class ContainsAt {

        @Nested
        class MatchingQNames {

            @Nested
            class ZeroIndex {

                @Test
                void testExpectedShorterThanPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(0,
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"));

                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    assertTrue(matcher.test(path));
                }

                @Test
                void testExpectedEqualsPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(0,
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"));

                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"));

                    assertTrue(matcher.test(path));
                }

                @Test
                void testExpectedLargerThanPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(0,
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"));

                    assertFalse(matcher.test(path));
                }

                @Test
                void testMismatchAtFirst() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(0,
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"));

                    ElementPath path = newPath(
                            new QName("a"),
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    assertFalse(matcher.test(path));
                }

                @Test
                void testMismatchAtLast() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(0,
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"));

                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName("B"),
                            new QName(NAMESPACE_URI, "c"));

                    assertFalse(matcher.test(path));
                }

                @Test
                void testToString() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(0,
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"));

                    assertEquals("containsAt(0, /{%1$s}a/{%1$s}b)".formatted(NAMESPACE_URI), matcher.toString());
                }
            }

            @Nested
            class PositiveIndex {

                @Test
                void testExpectedShorterThanPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(1,
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"),
                            new QName(NAMESPACE_URI, "d"));

                    assertTrue(matcher.test(path));
                }

                @Test
                void testExpectedEqualsPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(1,
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    assertTrue(matcher.test(path));
                }

                @Test
                void testExpectedLargerThanPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(1,
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"));

                    assertFalse(matcher.test(path));
                }

                @Test
                void testMismatchAtFirst() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(1,
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName("B"),
                            new QName(NAMESPACE_URI, "c"));

                    assertFalse(matcher.test(path));
                }

                @Test
                void testMismatchAtLast() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(1,
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"),
                            new QName("c"));

                    assertFalse(matcher.test(path));
                }

                @Test
                void testToString() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(1,
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    assertEquals("containsAt(1, /{%1$s}b/{%1$s}c)".formatted(NAMESPACE_URI), matcher.toString());
                }
            }

            @Nested
            class NegativeIndex {

                @Test
                void testExpectedShorterThanPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(-3,
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"),
                            new QName(NAMESPACE_URI, "d"));

                    assertTrue(matcher.test(path));
                }

                @Test
                void testExpectedEqualsPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(-2,
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    assertTrue(matcher.test(path));
                }

                @Test
                void testExpectedLargerThanPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(-3,
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    assertFalse(matcher.test(path));
                }

                @Test
                void testMismatchAtFirst() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(-2,
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName("b"),
                            new QName(NAMESPACE_URI, "c"));

                    assertFalse(matcher.test(path));
                }

                @Test
                void testMismatchAtLast() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(-2,
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"),
                            new QName("c"));

                    assertFalse(matcher.test(path));
                }

                @Test
                void testToString() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(-2,
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    assertEquals("containsAt(-2, /{%1$s}b/{%1$s}c)".formatted(NAMESPACE_URI), matcher.toString());
                }
            }

            @Nested
            class TooHighIndex {

                @Test
                void testExpectedShorterThanPath() {
                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"),
                            new QName(NAMESPACE_URI, "d"));

                    testMismatch(path);
                }

                @Test
                void testExpectedEqualsPath() {
                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    testMismatch(path);
                }

                @Test
                void testExpectedLargerThanPath() {
                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"));

                    testMismatch(path);
                }

                private void testMismatch(ElementPath path) {
                    ElementPath.Matcher matcher = ElementPath.containsAt(10,
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    assertFalse(matcher.test(path));
                }

                @Test
                void testToString() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(10,
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    assertEquals("containsAt(10, /{%1$s}b/{%1$s}c)".formatted(NAMESPACE_URI), matcher.toString());
                }
            }

            @Nested
            class TooLowIndex {

                @Test
                void testExpectedShorterThanPath() {
                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"),
                            new QName(NAMESPACE_URI, "d"));

                    testMismatch(path);
                }

                @Test
                void testExpectedEqualsPath() {
                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"),
                            new QName(NAMESPACE_URI, "c"));

                    testMismatch(path);
                }

                @Test
                void testExpectedLargerThanPath() {
                    ElementPath path = newPath(
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"));

                    testMismatch(path);
                }

                private void testMismatch(ElementPath path) {
                    ElementPath.Matcher matcher = ElementPath.containsAt(-10,
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"));

                    assertFalse(matcher.test(path));
                }

                @Test
                void testToString() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(-10,
                            new QName(NAMESPACE_URI, "a"),
                            new QName(NAMESPACE_URI, "b"));

                    assertEquals("containsAt(-10, /{%1$s}a/{%1$s}b)".formatted(NAMESPACE_URI), matcher.toString());
                }
            }
        }

        @Nested
        class MatchingStrings {

            @Nested
            class ZeroIndex {

                @Test
                void testExpectedShorterThanPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(0, "a", "b");

                    ElementPath path = newPath("a", "b", "c");

                    assertTrue(matcher.test(path));
                }

                @Test
                void testExpectedEqualsPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(0, "a", "b");

                    ElementPath path = newPath("a", "b");

                    assertTrue(matcher.test(path));
                }

                @Test
                void testExpectedLargerThanPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(0, "a", "b", "c");

                    ElementPath path = newPath("a", "b");

                    assertFalse(matcher.test(path));
                }

                @Test
                void testMismatchAtFirst() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(0, "a", "b");

                    ElementPath path = newPath("A", "b", "c");

                    assertFalse(matcher.test(path));
                }

                @Test
                void testMismatchAtLast() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(0, "a", "b");

                    ElementPath path = newPath("a", "B", "c");

                    assertFalse(matcher.test(path));
                }

                @Test
                void testToString() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(0, "a", "b");

                    assertEquals("containsAt(0, /a/b)", matcher.toString());
                }
            }

            @Nested
            class PositiveIndex {

                @Test
                void testExpectedShorterThanPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(1, "b", "c");

                    ElementPath path = newPath("a", "b", "c", "d");

                    assertTrue(matcher.test(path));
                }

                @Test
                void testExpectedEqualsPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(1, "b", "c");

                    ElementPath path = newPath("a", "b", "c");

                    assertTrue(matcher.test(path));
                }

                @Test
                void testExpectedLargerThanPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(1, "b", "c");

                    ElementPath path = newPath("a", "b");

                    assertFalse(matcher.test(path));
                }

                @Test
                void testMismatchAtFirst() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(1, "b", "c");

                    ElementPath path = newPath("a", "B", "c");

                    assertFalse(matcher.test(path));
                }

                @Test
                void testMismatchAtLast() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(1, "b", "c");

                    ElementPath path = newPath("a", "b", "C");

                    assertFalse(matcher.test(path));
                }

                @Test
                void testToString() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(1, "b", "c");

                    assertEquals("containsAt(1, /b/c)", matcher.toString());
                }
            }

            @Nested
            class NegativeIndex {

                @Test
                void testExpectedShorterThanPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(-3, "b", "c");

                    ElementPath path = newPath("a", "b", "c", "d");

                    assertTrue(matcher.test(path));
                }

                @Test
                void testExpectedEqualsPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(-2, "b", "c");

                    ElementPath path = newPath("a", "b", "c");

                    assertTrue(matcher.test(path));
                }

                @Test
                void testExpectedLargerThanPath() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(-3, "b", "c");

                    ElementPath path = newPath("a", "b", "c");

                    assertFalse(matcher.test(path));
                }

                @Test
                void testMismatchAtFirst() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(-2, "b", "c");

                    ElementPath path = newPath("a", "B", "c");

                    assertFalse(matcher.test(path));
                }

                @Test
                void testMismatchAtLast() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(-2, "b", "c");

                    ElementPath path = newPath("a", "b", "C");

                    assertFalse(matcher.test(path));
                }

                @Test
                void testToString() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(-2, "b", "c");

                    assertEquals("containsAt(-2, /b/c)", matcher.toString());
                }
            }

            @Nested
            class TooHighIndex {

                @Test
                void testExpectedShorterThanPath() {
                    ElementPath path = newPath("a", "b", "c", "d");

                    testMismatch(path);
                }

                @Test
                void testExpectedEqualsPath() {
                    ElementPath path = newPath("a", "b", "c");

                    testMismatch(path);
                }

                @Test
                void testExpectedLargerThanPath() {
                    ElementPath path = newPath("a", "b");

                    testMismatch(path);
                }

                private void testMismatch(ElementPath path) {
                    ElementPath.Matcher matcher = ElementPath.containsAt(10, "b", "c");

                    assertFalse(matcher.test(path));
                }

                @Test
                void testToString() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(10, "b", "c");

                    assertEquals("containsAt(10, /b/c)", matcher.toString());
                }
            }

            @Nested
            class TooLowIndex {

                @Test
                void testExpectedShorterThanPath() {
                    ElementPath path = newPath("a", "b", "c", "d");

                    testMismatch(path);
                }

                @Test
                void testExpectedEqualsPath() {
                    ElementPath path = newPath("a", "b", "c");

                    testMismatch(path);
                }

                @Test
                void testExpectedLargerThanPath() {
                    ElementPath path = newPath("a", "b");

                    testMismatch(path);
                }

                private void testMismatch(ElementPath path) {
                    ElementPath.Matcher matcher = ElementPath.containsAt(-10, "a", "b");

                    assertFalse(matcher.test(path));
                }

                @Test
                void testToString() {
                    ElementPath.Matcher matcher = ElementPath.containsAt(-10, "a", "b");

                    assertEquals("containsAt(-10, /a/b)", matcher.toString());
                }
            }
        }
    }

    @Nested
    class ContainsAtIgnoreCase {

        @Nested
        class ZeroIndex {

            @Test
            void testExpectedShorterThanPath() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(0, "a", "b");

                ElementPath path = newPath("a", "b", "c");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedEqualsPath() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(0, "a", "b");

                ElementPath path = newPath("a", "b");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedLargerThanPath() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(0, "a", "b", "c");

                ElementPath path = newPath("a", "b");

                assertFalse(matcher.test(path));
            }

            @Test
            void testCaseMismatchOnly() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(0, "a", "b", "c");

                ElementPath path = newPath("A", "B", "C");

                assertTrue(matcher.test(path));
            }

            @Test
            void testMismatchAtFirst() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(0, "a", "b");

                ElementPath path = newPath("d", "b", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtLast() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(0, "a", "b");

                ElementPath path = newPath("a", "d", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(0, "a", "b");

                assertEquals("containsAtIgnoreCase(0, /a/b)", matcher.toString());
            }
        }

        @Nested
        class PositiveIndex {

            @Test
            void testExpectedShorterThanPath() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(1, "b", "c");

                ElementPath path = newPath("a", "b", "c", "d");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedEqualsPath() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(1, "b", "c");

                ElementPath path = newPath("a", "b", "c");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedLargerThanPath() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(1, "b", "c");

                ElementPath path = newPath("a", "b");

                assertFalse(matcher.test(path));
            }

            @Test
            void testCaseMismatchOnly() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(1, "b", "c");

                ElementPath path = newPath("A", "B", "C");

                assertTrue(matcher.test(path));
            }

            @Test
            void testMismatchAtFirst() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(1, "b", "c");

                ElementPath path = newPath("a", "d", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtLast() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(1, "b", "c");

                ElementPath path = newPath("a", "b", "d");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(1, "b", "c");

                assertEquals("containsAtIgnoreCase(1, /b/c)", matcher.toString());
            }
        }

        @Nested
        class NegativeIndex {

            @Test
            void testExpectedShorterThanPath() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(-3, "b", "c");

                ElementPath path = newPath("a", "b", "c", "d");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedEqualsPath() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(-2, "b", "c");

                ElementPath path = newPath("a", "b", "c");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedLargerThanPath() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(-3, "b", "c");

                ElementPath path = newPath("a", "b", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testCaseMismatchOnly() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(-2, "b", "c");

                ElementPath path = newPath("A", "B", "C");

                assertTrue(matcher.test(path));
            }

            @Test
            void testMismatchAtFirst() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(-2, "b", "c");

                ElementPath path = newPath("a", "d", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtLast() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(-2, "b", "c");

                ElementPath path = newPath("a", "b", "d");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(-2, "b", "c");

                assertEquals("containsAtIgnoreCase(-2, /b/c)", matcher.toString());
            }
        }

        @Nested
        class TooHighIndex {

            @Test
            void testExpectedShorterThanPath() {
                ElementPath path = newPath("a", "b", "c", "d");

                testMismatch(path);
            }

            @Test
            void testExpectedEqualsPath() {
                ElementPath path = newPath("a", "b", "c");

                testMismatch(path);
            }

            @Test
            void testExpectedLargerThanPath() {
                ElementPath path = newPath("a", "b");

                testMismatch(path);
            }

            private void testMismatch(ElementPath path) {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(10, "b", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(10, "b", "c");

                assertEquals("containsAtIgnoreCase(10, /b/c)", matcher.toString());
            }
        }

        @Nested
        class TooLowIndex {

            @Test
            void testExpectedShorterThanPath() {
                ElementPath path = newPath("a", "b", "c", "d");

                testMismatch(path);
            }

            @Test
            void testExpectedEqualsPath() {
                ElementPath path = newPath("a", "b", "c");

                testMismatch(path);
            }

            @Test
            void testExpectedLargerThanPath() {
                ElementPath path = newPath("a", "b");

                testMismatch(path);
            }

            private void testMismatch(ElementPath path) {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(-10, "a", "b");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                ElementPath.Matcher matcher = ElementPath.containsAtIgnoreCase(-10, "a", "b");

                assertEquals("containsAtIgnoreCase(-10, /a/b)", matcher.toString());
            }
        }
    }

    @Nested
    class HasLength {

        @Test
        void testLengthLessThanValue() {
            ElementPath.Matcher matcher = ElementPath.hasLength(3);

            assertFalse(matcher.test(newPath("a", "b")));
        }

        @Test
        void testLengthEqualToValue() {
            ElementPath.Matcher matcher = ElementPath.hasLength(3);

            assertTrue(matcher.test(newPath("a", "b", "c")));
        }

        @Test
        void testLengthGreaterThanValue() {
            ElementPath.Matcher matcher = ElementPath.hasLength(3);

            assertFalse(matcher.test(newPath("a", "b", "c", "d")));
        }

        @ParameterizedTest
        @ValueSource(ints = { -1, 0 })
        void testInvalidLength(int length) {
            assertThrows(IllegalArgumentException.class, () -> ElementPath.hasLength(length));
        }

        @Test
        void testToString() {
            ElementPath.Matcher matcher = ElementPath.hasLength(3);

            assertEquals("hasLength(3)", matcher.toString());
        }
    }

    @Nested
    class HasLengthAtLeast {

        @Test
        void testLengthLessThanMin() {
            ElementPath.Matcher matcher = ElementPath.hasLengthAtLeast(3);

            assertFalse(matcher.test(newPath("a", "b")));
        }

        @Test
        void testLengthEqualToMin() {
            ElementPath.Matcher matcher = ElementPath.hasLengthAtLeast(3);

            assertTrue(matcher.test(newPath("a", "b", "c")));
        }

        @Test
        void testLengthGreaterThanMin() {
            ElementPath.Matcher matcher = ElementPath.hasLengthAtLeast(3);

            assertTrue(matcher.test(newPath("a", "b", "c", "d")));
        }

        @ParameterizedTest
        @ValueSource(ints = { -1, 0 })
        void testInvalidLength(int length) {
            assertThrows(IllegalArgumentException.class, () -> ElementPath.hasLengthAtLeast(length));
        }

        @Test
        void testToString() {
            ElementPath.Matcher matcher = ElementPath.hasLengthAtLeast(3);

            assertEquals("hasLengthAtLeast(3)", matcher.toString());
        }
    }

    @Nested
    class HasLengthGreaterThan {

        @Test
        void testLengthLessThanMin() {
            ElementPath.Matcher matcher = ElementPath.hasLengthGreaterThan(3);

            assertFalse(matcher.test(newPath("a", "b")));
        }

        @Test
        void testLengthEqualToMin() {
            ElementPath.Matcher matcher = ElementPath.hasLengthGreaterThan(3);

            assertFalse(matcher.test(newPath("a", "b", "c")));
        }

        @Test
        void testLengthGreaterThanMin() {
            ElementPath.Matcher matcher = ElementPath.hasLengthGreaterThan(3);

            assertTrue(matcher.test(newPath("a", "b", "c", "d")));
        }

        @ParameterizedTest
        @ValueSource(ints = { -1, 0 })
        void testInvalidLength(int length) {
            assertThrows(IllegalArgumentException.class, () -> ElementPath.hasLengthGreaterThan(length));
        }

        @Test
        void testToString() {
            ElementPath.Matcher matcher = ElementPath.hasLengthGreaterThan(3);

            assertEquals("hasLengthGreaterThan(3)", matcher.toString());
        }
    }

    @Nested
    class HasLengthAtMost {

        @Test
        void testLengthLessThanMax() {
            ElementPath.Matcher matcher = ElementPath.hasLengthAtMost(3);

            assertTrue(matcher.test(newPath("a", "b")));
        }

        @Test
        void testLengthEqualToMax() {
            ElementPath.Matcher matcher = ElementPath.hasLengthAtMost(3);

            assertTrue(matcher.test(newPath("a", "b", "c")));
        }

        @Test
        void testLengthGreaterThanMax() {
            ElementPath.Matcher matcher = ElementPath.hasLengthAtMost(3);

            assertFalse(matcher.test(newPath("a", "b", "c", "d")));
        }

        @ParameterizedTest
        @ValueSource(ints = { -1, 0 })
        void testInvalidLength(int length) {
            assertThrows(IllegalArgumentException.class, () -> ElementPath.hasLengthAtMost(length));
        }

        @Test
        void testToString() {
            ElementPath.Matcher matcher = ElementPath.hasLengthAtMost(3);

            assertEquals("hasLengthAtMost(3)", matcher.toString());
        }
    }

    @Nested
    class HasLengthLessThan {

        @Test
        void testLengthLessThanMax() {
            ElementPath.Matcher matcher = ElementPath.hasLengthLessThan(3);

            assertTrue(matcher.test(newPath("a", "b")));
        }

        @Test
        void testLengthEqualToMax() {
            ElementPath.Matcher matcher = ElementPath.hasLengthLessThan(3);

            assertFalse(matcher.test(newPath("a", "b", "c")));
        }

        @Test
        void testLengthGreaterThanMax() {
            ElementPath.Matcher matcher = ElementPath.hasLengthLessThan(3);

            assertFalse(matcher.test(newPath("a", "b", "c", "d")));
        }

        @ParameterizedTest
        @ValueSource(ints = { -1, 0 })
        void testInvalidLength(int length) {
            assertThrows(IllegalArgumentException.class, () -> ElementPath.hasLengthLessThan(length));
        }

        @Test
        void testToString() {
            ElementPath.Matcher matcher = ElementPath.hasLengthLessThan(3);

            assertEquals("hasLengthLessThan(3)", matcher.toString());
        }
    }

    @Nested
    class And {

        @Test
        void testNoMatch() {
            ElementPath.Matcher first = mock();
            when(first.test(any())).thenReturn(false);
            when(first.and(any())).thenAnswer(Answers.CALLS_REAL_METHODS);

            ElementPath.Matcher second = mock();

            ElementPath.Matcher matcher = first.and(second);

            ElementPath path = newPath("a", "b", "c");

            assertFalse(matcher.test(path));

            verify(first).test(path);
            verify(first).and(second);
            verifyNoMoreInteractions(first, second);
        }

        @Test
        void testFirstMatches() {
            ElementPath.Matcher first = mock();
            when(first.test(any())).thenReturn(true);
            when(first.and(any())).thenAnswer(Answers.CALLS_REAL_METHODS);

            ElementPath.Matcher second = mock();
            when(second.test(any())).thenReturn(false);

            ElementPath.Matcher matcher = first.and(second);

            ElementPath path = newPath("a", "b", "c");

            assertFalse(matcher.test(path));

            verify(first).test(path);
            verify(first).and(second);
            verify(second).test(path);
            verifyNoMoreInteractions(first, second);
        }

        @Test
        void testBothMatch() {
            ElementPath.Matcher first = mock();
            when(first.test(any())).thenReturn(true);
            when(first.and(any())).thenAnswer(Answers.CALLS_REAL_METHODS);

            ElementPath.Matcher second = mock();
            when(second.test(any())).thenReturn(true);

            ElementPath.Matcher matcher = first.and(second);

            ElementPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));

            verify(first).test(path);
            verify(first).and(second);
            verify(second).test(path);
            verifyNoMoreInteractions(first, second);
        }

        @Test
        void testToString() {
            ElementPath.Matcher matcher = ElementPath.startsWith("a").and(ElementPath.endsWith("c"));

            assertEquals("and(startsWith(/a), endsWith(/c))", matcher.toString());
        }
    }

    @Nested
    class Or {

        @Test
        void testNoMatch() {
            ElementPath.Matcher first = mock();
            when(first.test(any())).thenReturn(false);
            when(first.or(any())).thenAnswer(Answers.CALLS_REAL_METHODS);

            ElementPath.Matcher second = mock();
            when(second.test(any())).thenReturn(false);

            ElementPath.Matcher matcher = first.or(second);

            ElementPath path = newPath("a", "b", "c");

            assertFalse(matcher.test(path));

            verify(first).test(path);
            verify(first).or(second);
            verify(second).test(path);
            verifyNoMoreInteractions(first, second);
        }

        @Test
        void testFirstMatches() {
            ElementPath.Matcher first = mock();
            when(first.test(any())).thenReturn(true);
            when(first.or(any())).thenAnswer(Answers.CALLS_REAL_METHODS);

            ElementPath.Matcher second = mock();

            ElementPath.Matcher matcher = first.or(second);

            ElementPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));

            verify(first).test(path);
            verify(first).or(second);
            verifyNoMoreInteractions(first, second);
        }

        @Test
        void testSecondMatches() {
            ElementPath.Matcher first = mock();
            when(first.test(any())).thenReturn(false);
            when(first.or(any())).thenAnswer(Answers.CALLS_REAL_METHODS);

            ElementPath.Matcher second = mock();
            when(second.test(any())).thenReturn(true);

            ElementPath.Matcher matcher = first.or(second);

            ElementPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));

            verify(first).test(path);
            verify(first).or(second);
            verify(second).test(path);
            verifyNoMoreInteractions(first, second);
        }

        @Test
        void testToString() {
            ElementPath.Matcher matcher = ElementPath.startsWith("a").or(ElementPath.endsWith("c"));

            assertEquals("or(startsWith(/a), endsWith(/c))", matcher.toString());
        }
    }

    @Nested
    class Not {

        @Test
        void testNegatedDoesNotMatch() {
            ElementPath.Matcher negated = mock();
            when(negated.test(any())).thenReturn(false);
            when(negated.negate()).thenAnswer(Answers.CALLS_REAL_METHODS);

            ElementPath.Matcher matcher = negated.negate();

            ElementPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));

            verify(negated).test(path);
            verify(negated).negate();
            verifyNoMoreInteractions(negated);
        }

        @Test
        void testNegatedMatches() {
            ElementPath.Matcher negated = mock();
            when(negated.test(any())).thenReturn(true);
            when(negated.negate()).thenAnswer(Answers.CALLS_REAL_METHODS);

            ElementPath.Matcher matcher = negated.negate();

            ElementPath path = newPath("a", "b", "c");

            assertFalse(matcher.test(path));

            verify(negated).test(path);
            verify(negated).negate();
            verifyNoMoreInteractions(negated);
        }

        @ParameterizedTest
        @ValueSource(booleans = { true, false })
        void testNegativeTwiceIsIdentity(boolean match) {
            ElementPath.Matcher negated = mock();
            when(negated.test(any())).thenReturn(match);
            when(negated.negate()).thenAnswer(Answers.CALLS_REAL_METHODS);

            ElementPath.Matcher matcher = negated.negate().negate();

            ElementPath path = newPath("a", "b", "c");

            assertEquals(match, matcher.test(path));

            verify(negated).test(path);
            verify(negated).negate();
            verifyNoMoreInteractions(negated);
        }

        @Test
        void testToString() {
            ElementPath.Matcher matcher = ElementPath.startsWith("a").negate();

            assertEquals("not(startsWith(/a))", matcher.toString());
        }
    }

    private static ElementPath newPath(QName... elements) {
        ElementPath path = new ElementPath();
        for (QName element : elements) {
            path.push(element);
        }
        return path;
    }

    private static ElementPath newPath(String... elements) {
        ElementPath path = new ElementPath();
        for (String element : elements) {
            path.push(new QName(NAMESPACE_URI, element));
        }
        return path;
    }
}
