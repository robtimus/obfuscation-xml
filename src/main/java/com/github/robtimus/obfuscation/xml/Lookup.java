/*
 * Lookup.java
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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.xml.namespace.QName;
import com.github.robtimus.obfuscation.support.CaseSensitivity;
import com.github.robtimus.obfuscation.xml.XMLObfuscator.ElementPath;

abstract sealed class Lookup<T> {

    private final Map<String, T> caseSensitiveValues;
    private final Map<String, T> caseInsensitiveValues;
    private final Map<QName, T> qnameValues;

    private Lookup(Builder<T, ?, ?> builder) {
        caseSensitiveValues = Map.copyOf(builder.caseSensitiveValues);
        caseInsensitiveValues = caseInsensitiveCopy(builder.caseInsensitiveValues);
        qnameValues = Map.copyOf(builder.qnameValues);
    }

    private static <T> Map<String, T> caseInsensitiveCopy(Map<String, T> map) {
        Map<String, T> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        result.putAll(map);
        return Collections.unmodifiableMap(result);
    }

    private T find(String name) {
        T value = caseSensitiveValues.get(name);
        if (value != null) {
            return value;
        }
        return caseInsensitiveValues.get(name);
    }

    private T find(QName name) {
        T value = qnameValues.get(name);
        if (value != null) {
            return value;
        }
        return find(name.getLocalPart());
    }

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();

    private boolean hasEqualLookup(Lookup<?> other) {
        return caseSensitiveValues.equals(other.caseSensitiveValues)
                && caseInsensitiveValues.equals(other.caseInsensitiveValues)
                && qnameValues.equals(other.qnameValues);
    }

    private int calculateHashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + caseSensitiveValues.hashCode();
        result = prime * result + caseInsensitiveValues.hashCode();
        result = prime * result + qnameValues.hashCode();
        return result;
    }

    static <T> ForElements.Builder<T> forElements() {
        return new ForElements.Builder<>();
    }

    static <T> ForAttributes.Builder<T> forAttributes() {
        return new ForAttributes.Builder<>();
    }

    abstract static sealed class Builder<T, L extends Lookup<T>, B extends Builder<T, L, B>> {

        private final Map<String, T> caseSensitiveValues;
        private final Map<String, T> caseInsensitiveValues;
        private final Map<QName, T> qnameValues;

        private final BiFunction<CaseSensitivity, String, String> duplicateEntryWithCaseSensitivity;
        private final Function<QName, String> duplicateEntry;

        private Builder(BiFunction<CaseSensitivity, String, String> duplicateEntryWithCaseSensitivity, Function<QName, String> duplicateEntry) {
            caseSensitiveValues = new HashMap<>();
            caseInsensitiveValues = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            qnameValues = new HashMap<>();

            this.duplicateEntryWithCaseSensitivity = duplicateEntryWithCaseSensitivity;
            this.duplicateEntry = duplicateEntry;
        }

        B add(String key, T value, CaseSensitivity caseSensitivity) {
            Map<String, T> map = switch (caseSensitivity) {
                case CASE_SENSITIVE -> caseSensitiveValues;
                case CASE_INSENSITIVE -> caseInsensitiveValues;
            };
            map.merge(key, value, (a, b) -> {
                throw new IllegalArgumentException(duplicateEntryWithCaseSensitivity.apply(caseSensitivity, key));
            });
            return self();
        }

        B add(QName key, T value) {
            qnameValues.merge(key, value, (a, b) -> {
                throw new IllegalArgumentException(duplicateEntry.apply(key));
            });
            return self();
        }

        B clear() {
            caseSensitiveValues.clear();
            caseInsensitiveValues.clear();
            qnameValues.clear();
            return self();
        }

        @SuppressWarnings("unchecked")
        private B self() {
            return (B) this;
        }

        abstract L build();
    }

    static final class ForElements<T> extends Lookup<T> {

        private final List<ElementPathRegistration<T>> elementPaths;

        private ForElements(Builder<T> builder) {
            super(builder);
            elementPaths = builder.elementPaths
                    .entrySet()
                    .stream()
                    .map(e -> new ElementPathRegistration<>(e.getKey(), e.getValue()))
                    .toList();
        }

        T find(ElementPath path) {
            T result = findByElementPath(path);
            if (result != null) {
                return result;
            }
            return super.find(path.lastElement());
        }

        private T findByElementPath(ElementPath path) {
            for (ElementPathRegistration<T> registration : elementPaths) {
                if (registration.matcher.test(path)) {
                    return registration.value;
                }
            }
            return null;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ForElements<?> other
                    && super.hasEqualLookup(other)
                    && elementPaths.equals(other.elementPaths);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.calculateHashCode();
            result = prime * result + elementPaths.hashCode();
            return result;
        }

        static final class Builder<T> extends Lookup.Builder<T, ForElements<T>, Builder<T>> {

            private final Map<ElementPath.Matcher, T> elementPaths;

            private Builder() {
                super(Messages.XMLObfuscator::duplicateElementWithCaseSensitivity, Messages.XMLObfuscator::duplicateElement);
                elementPaths = new LinkedHashMap<>();
            }

            Builder<T> add(ElementPath.Matcher matcher, T value) {
                elementPaths.merge(matcher, value, (a, b) -> {
                    throw new IllegalArgumentException(Messages.XMLObfuscator.duplicateElementPathMatcher(matcher));
                });
                return this;
            }

            @Override
            ForElements<T> build() {
                return new ForElements<>(this);
            }
        }

        private record ElementPathRegistration<T>(ElementPath.Matcher matcher, T value) {
        }
    }

    static final class ForAttributes<T> extends Lookup<T> {

        private ForAttributes(Builder<T> builder) {
            super(builder);
        }

        @SuppressWarnings("squid:S2177") // this method exists to expose the private method
        T find(QName name) {
            return super.find(name);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ForAttributes<?> other
                    && super.hasEqualLookup(other);
        }

        @Override
        public int hashCode() {
            return super.calculateHashCode();
        }

        static final class Builder<T> extends Lookup.Builder<T, ForAttributes<T>, Builder<T>> {

            private Builder() {
                super(Messages.XMLObfuscator::duplicateAttributeWithCaseSensitivity, Messages.XMLObfuscator::duplicateAttribute);
            }

            @Override
            ForAttributes<T> build() {
                return new ForAttributes<>(this);
            }
        }
    }
}
