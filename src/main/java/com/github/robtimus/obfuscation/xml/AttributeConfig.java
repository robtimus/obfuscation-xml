/*
 * AttributeConfig.java
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

import java.util.Map;
import java.util.Objects;
import javax.xml.namespace.QName;
import com.github.robtimus.obfuscation.Obfuscator;

final class AttributeConfig {

    private final Obfuscator obfuscator;
    private final Map<String, Obfuscator> elements;
    private final Map<QName, Obfuscator> qualifiedElements;

    AttributeConfig(Obfuscator obfuscator, Map<String, Obfuscator> elements, Map<QName, Obfuscator> qualifiedElements) {
        this.obfuscator = Objects.requireNonNull(obfuscator);
        this.elements = Objects.requireNonNull(elements);
        this.qualifiedElements = Objects.requireNonNull(qualifiedElements);
    }

    Obfuscator obfuscator(QName elementName) {
        Obfuscator result = qualifiedElements.get(elementName);
        if (result != null) {
            return result;
        }
        result = elements.get(elementName.getLocalPart());
        if (result != null) {
            return result;
        }
        return obfuscator;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        AttributeConfig other = (AttributeConfig) o;
        return obfuscator.equals(other.obfuscator)
                && elements.equals(other.elements)
                && qualifiedElements.equals(other.qualifiedElements);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + obfuscator.hashCode();
        result = prime * result + elements.hashCode();
        result = prime * result + qualifiedElements.hashCode();
        return result;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        String result = "[obfuscator=" + obfuscator;
        if (!elements.isEmpty() || !qualifiedElements.isEmpty()) {
            result += ",elements=" + elements
                    + ",qualifiedElements=" + qualifiedElements;
        }
        return result + "]";
    }
}
