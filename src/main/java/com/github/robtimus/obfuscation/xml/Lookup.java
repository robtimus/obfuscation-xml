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
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;
import com.github.robtimus.obfuscation.support.CaseSensitivity;

final class Lookup<T> {

    private final Map<String, T> caseSensitiveValues;
    private final Map<String, T> caseInsensitiveValues;
    private final Map<QName, T> qnameValues;

    private Lookup(Builder<T> builder) {
        caseSensitiveValues = Map.copyOf(builder.caseSensitiveValues);
        caseInsensitiveValues = caseInsensitiveCopy(builder.caseInsensitiveValues);
        qnameValues = Map.copyOf(builder.qnameValues);
    }

    private static <T> Map<String, T> caseInsensitiveCopy(Map<String, T> map) {
        Map<String, T> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        result.putAll(map);
        return Collections.unmodifiableMap(result);
    }

    T find(String name) {
        T value = caseSensitiveValues.get(name);
        if (value != null) {
            return value;
        }
        return caseInsensitiveValues.get(name);
    }

    T find(QName name) {
        T value = qnameValues.get(name);
        if (value != null) {
            return value;
        }
        return find(name.getLocalPart());
    }

    T find(XMLStreamReader xmlStreamReader) {
        return qnameValues.isEmpty()
                ? find(xmlStreamReader.getLocalName())
                : find(xmlStreamReader.getName());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Lookup<?> other
                && equals(other);
    }

    private boolean equals(Lookup<?> other) {
        return caseSensitiveValues.equals(other.caseSensitiveValues)
                && caseInsensitiveValues.equals(other.caseInsensitiveValues)
                && qnameValues.equals(other.qnameValues);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + caseSensitiveValues.hashCode();
        result = prime * result + caseInsensitiveValues.hashCode();
        result = prime * result + qnameValues.hashCode();
        return result;
    }

    static <T> Builder<T> builder(MessageProvider messageProvider) {
        return new Builder<>(messageProvider);
    }

    static final class Builder<T> {

        private final MessageProvider messageProvider;

        private final Map<String, T> caseSensitiveValues;
        private final Map<String, T> caseInsensitiveValues;
        private final Map<QName, T> qnameValues;

        private Builder(MessageProvider messageProvider) {
            this.messageProvider = messageProvider;

            caseSensitiveValues = new HashMap<>();
            caseInsensitiveValues = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            qnameValues = new HashMap<>();
        }

        Builder<T> add(String key, T value, CaseSensitivity caseSensitivity) {
            Map<String, T> map = switch (caseSensitivity) {
                case CASE_SENSITIVE -> caseSensitiveValues;
                case CASE_INSENSITIVE -> caseInsensitiveValues;
            };
            map.merge(key, value, (a, b) -> {
                throw new IllegalArgumentException(messageProvider.duplicateEntry(caseSensitivity, key));
            });
            return this;
        }

        Builder<T> add(QName key, T value) {
            qnameValues.merge(key, value, (a, b) -> {
                throw new IllegalArgumentException(messageProvider.duplicateEntry(key));
            });
            return this;
        }

        Builder<T> clear() {
            caseSensitiveValues.clear();
            caseInsensitiveValues.clear();
            qnameValues.clear();
            return this;
        }

        Lookup<T> build() {
            return new Lookup<>(this);
        }
    }

    enum MessageProvider {
        ELEMENT(Messages.XMLObfuscator::duplicateElementWithCaseSensitivity, Messages.XMLObfuscator::duplicateElement),
        ATTRIBUTE(Messages.XMLObfuscator::duplicateAttributeWithCaseSensitivity, Messages.XMLObfuscator::duplicateAttribute),
        ;

        private BiFunction<CaseSensitivity, String, String> duplicateEntryWithCaseSensitivity;
        private Function<QName, String> duplicateEntry;

        MessageProvider(BiFunction<CaseSensitivity, String, String> duplicateEntryWithCaseSensitivity, Function<QName, String> duplicateEntry) {
            this.duplicateEntryWithCaseSensitivity = duplicateEntryWithCaseSensitivity;
            this.duplicateEntry = duplicateEntry;
        }

        private String duplicateEntry(CaseSensitivity caseSensitivity, String key) {
            return duplicateEntryWithCaseSensitivity.apply(caseSensitivity, key);
        }

        private String duplicateEntry(QName key) {
            return duplicateEntry.apply(key);
        }
    }
}
