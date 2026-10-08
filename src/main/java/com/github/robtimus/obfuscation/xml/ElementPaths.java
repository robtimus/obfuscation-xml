/*
 * ElementPaths.java
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

import java.util.Iterator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import javax.xml.namespace.QName;
import com.github.robtimus.obfuscation.xml.XMLObfuscator.ElementPath;

final class ElementPaths {

    private ElementPaths() {
    }

    static String join(List<?> elements, String prefix, String postfix) {
        StringBuilder sb = new StringBuilder();
        sb.append(prefix);
        join(elements, sb);
        sb.append(postfix);
        return sb.toString();
    }

    private static void join(List<?> elements, StringBuilder target) {
        Iterator<?> iterator = elements.iterator();
        while (iterator.hasNext()) {
            target.append('/').append(iterator.next());
        }
    }

    private static boolean localNameEquals(QName element, String expected) {
        return element.getLocalPart().equals(expected);
    }

    private static boolean localNameEqualsIgnoreCase(QName element, String expected) {
        return element.getLocalPart().equalsIgnoreCase(expected);
    }

    private static <T> boolean equalsStartingAt(List<QName> elements, List<T> expected, int start, BiPredicate<QName, T> equals) {
        int elementsSize = elements.size();
        int expectedSize = expected.size();
        int startIndex = start >= 0 ? start : elementsSize + start;

        if (elementsSize < expectedSize || startIndex < 0 || startIndex + expectedSize > elementsSize) {
            return false;
        }
        Iterator<QName> i = elements.listIterator(startIndex);
        Iterator<T> j = expected.iterator();
        while (j.hasNext()) {
            if (!equals.test(i.next(), j.next())) {
                return false;
            }
        }
        return true;
    }

    record QualifiedNameIsMatcher(List<QName> elements) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return elements.equals(path.elements());
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return join(elements, "is(", ")");
        }
    }

    record LocalNameIsMatcher(List<String> elements) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return elements.size() == path.length()
                    && hasEqualElements(path);
        }

        private boolean hasEqualElements(ElementPath path) {
            Iterator<QName> i = path.iterator();
            Iterator<String> j = elements.iterator();
            while (j.hasNext()) {
                if (!i.next().getLocalPart().equals(j.next())) {
                    return false;
                }
            }
            return true;
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return join(elements, "is(", ")");
        }
    }

    record LocalNameIsIgnoreCaseMatcher(List<String> elements) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return elements.size() == path.length()
                    && hasEqualElements(path);
        }

        private boolean hasEqualElements(ElementPath path) {
            Iterator<QName> i = path.iterator();
            Iterator<String> j = elements.iterator();
            while (j.hasNext()) {
                if (!i.next().getLocalPart().equalsIgnoreCase(j.next())) {
                    return false;
                }
            }
            return true;
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return join(elements, "isIgnoreCase(", ")");
        }
    }

    record QualifiedNameStartsWithMatcher(List<QName> prefix) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return equalsStartingAt(path.elements(), prefix, 0, QName::equals);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return join(prefix, "startsWith(", ")");
        }
    }

    record LocalNameStartsWithMatcher(List<String> prefix) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return equalsStartingAt(path.elements(), prefix, 0, ElementPaths::localNameEquals);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return join(prefix, "startsWith(", ")");
        }
    }

    record LocalNameStartsWithIgnoreCaseMatcher(List<String> prefix) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return equalsStartingAt(path.elements(), prefix, 0, ElementPaths::localNameEqualsIgnoreCase);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return join(prefix, "startsWithIgnoreCase(", ")");
        }
    }

    record QualifiedNameEndsWithMatcher(List<QName> postfix) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return equalsStartingAt(path.elements(), postfix, -postfix.size(), QName::equals);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return join(postfix, "endsWith(", ")");
        }
    }

    record LocalNameEndsWithMatcher(List<String> postfix) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return equalsStartingAt(path.elements(), postfix, -postfix.size(), ElementPaths::localNameEquals);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return join(postfix, "endsWith(", ")");
        }
    }

    record LocalNameEndsWithIgnoreCaseMatcher(List<String> postfix) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return equalsStartingAt(path.elements(), postfix, -postfix.size(), ElementPaths::localNameEqualsIgnoreCase);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return join(postfix, "endsWithIgnoreCase(", ")");
        }
    }

    record QualifiedNameContainsAtMatcher(int index, List<QName> elements) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return equalsStartingAt(path.elements(), elements, index, QName::equals);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("containsAt(").append(index).append(", ");
            join(elements, sb);
            sb.append(')');
            return sb.toString();
        }
    }

    record LocalNameContainsAtMatcher(int index, List<String> elements) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return equalsStartingAt(path.elements(), elements, index, ElementPaths::localNameEquals);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("containsAt(").append(index).append(", ");
            join(elements, sb);
            sb.append(')');
            return sb.toString();
        }
    }

    record LocalNameContainsAtIgnoreCaseMatcher(int index, List<String> elements) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return equalsStartingAt(path.elements(), elements, index, ElementPaths::localNameEqualsIgnoreCase);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("containsAtIgnoreCase(").append(index).append(", ");
            join(elements, sb);
            sb.append(')');
            return sb.toString();
        }
    }

    record HasLengthMatcher(int length) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return path.length() == length;
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "hasLength(" + length + ")";
        }
    }

    record HasLengthAtLeastMatcher(int min) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return path.length() >= min;
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "hasLengthAtLeast(" + min + ")";
        }
    }

    record HasLengthGreaterThanMatcher(int min) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return path.length() > min;
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "hasLengthGreaterThan(" + min + ")";
        }
    }

    record HasLengthAtMostMatcher(int max) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return path.length() <= max;
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "hasLengthAtMost(" + max + ")";
        }
    }

    record HasLengthLessThanMatcher(int max) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return path.length() < max;
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "hasLengthLessThan(" + max + ")";
        }
    }

    record AndMatcher(Predicate<? super ElementPath> first, Predicate<? super ElementPath> second) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return first.test(path) && second.test(path);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "and(" + first + ", " + second + ")";
        }
    }

    record OrMatcher(Predicate<? super ElementPath> first, Predicate<? super ElementPath> second) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return first.test(path) || second.test(path);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "or(" + first + ", " + second + ")";
        }
    }

    record NotMatcher(ElementPath.Matcher negated) implements ElementPath.Matcher {

        @Override
        public boolean test(ElementPath path) {
            return !negated.test(path);
        }

        @Override
        public ElementPath.Matcher negate() {
            return negated;
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "not(" + negated + ")";
        }
    }
}
