/*
 * XMLObfuscator.java
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

import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.checkStartAndEnd;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.copyTo;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.discardAll;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.reader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ctc.wstx.stax.WstxInputFactory;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.support.CachingObfuscatingWriter;
import com.github.robtimus.obfuscation.support.CaseSensitivity;
import com.github.robtimus.obfuscation.support.MapBuilder;

/**
 * An obfuscator that obfuscates XML elements in {@link CharSequence CharSequences} or the contents of {@link Reader Readers}.
 * An {@code XMLObfuscator} will only obfuscate text, and ignore leading and trailing whitespace.
 * It will never obfuscate element tag names, comments, etc.
 * <p>
 * By default, if an obfuscator is configured for an element, it will be used to obfuscate the text of all nested elements as well. This can be turned
 * off using {@link Builder#excludeNestedElementsByDefault()} and/or {@link ElementConfigurer#excludeNestedElements()}. This will allow the nested
 * elements to use their own obfuscators.
 *
 * @author Rob Spoor
 */
public final class XMLObfuscator extends Obfuscator {

    private static final Logger LOGGER = LoggerFactory.getLogger(XMLObfuscator.class);

    private static final XMLInputFactory INPUT_FACTORY = createInputFactory();

    private final Map<String, ElementConfig> elements;

    private final String malformedXMLWarning;

    private XMLObfuscator(ObfuscatorBuilder builder) {
        elements = builder.elements();

        malformedXMLWarning = builder.malformedXMLWarning;
    }

    private static XMLInputFactory createInputFactory() {
        // Explicitly use Woodstox; any other implementation may not produce the correct locations
        XMLInputFactory inputFactory = new WstxInputFactory();
        trySetProperty(inputFactory, XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
        trySetProperty(inputFactory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); //$NON-NLS-1$
        trySetProperty(inputFactory, XMLConstants.ACCESS_EXTERNAL_STYLESHEET, ""); //$NON-NLS-1$
        trySetProperty(inputFactory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return inputFactory;
    }

    private static void trySetProperty(XMLInputFactory inputFactory, String name, Object value) {
        try {
            inputFactory.setProperty(name, value);
        } catch (@SuppressWarnings("unused") IllegalArgumentException e) {
            LOGGER.warn(Messages.XMLObfuscator.unsupportedProperty.get(name));
        }
    }

    @Override
    public CharSequence obfuscateText(CharSequence s, int start, int end) {
        checkStartAndEnd(s, start, end);
        StringBuilder sb = new StringBuilder(end - start);
        obfuscateText(s, start, end, sb);
        return sb.toString();
    }

    @Override
    public void obfuscateText(CharSequence s, int start, int end, Appendable destination) throws IOException {
        checkStartAndEnd(s, start, end);
        @SuppressWarnings("resource")
        Reader input = reader(s, start, end);
        obfuscateText(input, s, start, end, destination);
    }

    @Override
    public void obfuscateText(Reader input, Appendable destination) throws IOException {
        StringBuilder contents = new StringBuilder();
        @SuppressWarnings("resource")
        Reader reader = copyTo(input, contents);
        obfuscateText(reader, contents, 0, -1, destination);
    }

    private void obfuscateText(Reader input, CharSequence s, int start, int end, Appendable destination) throws IOException {
        try {
            XMLStreamReader xmlStreamReader = INPUT_FACTORY.createXMLStreamReader(input);
            ObfuscatingXMLParser parser = new ObfuscatingXMLParser(xmlStreamReader, s, start, end, destination, elements);
            while (parser.hasNext()) {
                parser.processNext();
            }

            // Read the remainder so the final append will include all text
            discardAll(input);
            parser.appendRemainder();
        } catch (XMLStreamException e) {
            LOGGER.warn(Messages.XMLObfuscator.malformedXML.warning.get(), e);
            if (malformedXMLWarning != null) {
                destination.append(malformedXMLWarning);
            }
        }
    }

    @Override
    public Writer streamTo(Appendable destination) {
        return new CachingObfuscatingWriter(this, destination);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        XMLObfuscator other = (XMLObfuscator) o;
        return elements.equals(other.elements)
                && Objects.equals(malformedXMLWarning, other.malformedXMLWarning);
    }

    @Override
    public int hashCode() {
        return elements.hashCode() ^ Objects.hashCode(malformedXMLWarning);
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return getClass().getName()
                + "[elements=" + elements
                + ",malformedXMLWarning=" + malformedXMLWarning
                + "]";
    }

    /**
     * Returns a builder that will create {@code XMLObfuscators}.
     *
     * @return A builder that will create {@code XMLObfuscators}.
     */
    public static Builder builder() {
        return new ObfuscatorBuilder();
    }

    /**
     * A builder for {@link XMLObfuscator XMLObfuscators}.
     *
     * @author Rob Spoor
     */
    public abstract static class Builder {

        private Builder() {
            super();
        }

        /**
         * Adds an element to obfuscate.
         * This method is an alias for {@link #withElement(String, Obfuscator, CaseSensitivity)} with the last specified default case sensitivity
         * using {@link #caseSensitiveByDefault()} or {@link #caseInsensitiveByDefault()}. The default is {@link CaseSensitivity#CASE_SENSITIVE}.
         *
         * @param element The local name of the element.
         * @param obfuscator The obfuscator to use for obfuscating the element.
         * @return An object that can be used to configure the element, or continue building {@link XMLObfuscator XMLObfuscators}.
         * @throws NullPointerException If the given element name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If an element with the same local name and the same case sensitivity was already added.
         */
        public abstract ElementConfigurer withElement(String element, Obfuscator obfuscator);

        /**
         * Adds an element to obfuscate.
         *
         * @param element The local name of the element.
         * @param obfuscator The obfuscator to use for obfuscating the element.
         * @param caseSensitivity The case sensitivity for the element.
         * @return An object that can be used to configure the element, or continue building {@link XMLObfuscator XMLObfuscators}.
         * @throws NullPointerException If the given element name, obfuscator or case sensitivity is {@code null}.
         * @throws IllegalArgumentException If an element with the same local name and the same case sensitivity was already added.
         */
        public abstract ElementConfigurer withElement(String element, Obfuscator obfuscator, CaseSensitivity caseSensitivity);

        /**
         * Sets the default case sensitivity for new elements to {@link CaseSensitivity#CASE_SENSITIVE}. This is the default setting.
         * <p>
         * Note that this will not change the case sensitivity of any element that was already added.
         *
         * @return This object.
         */
        public abstract Builder caseSensitiveByDefault();

        /**
         * Sets the default case sensitivity for new elements to {@link CaseSensitivity#CASE_INSENSITIVE}.
         * <p>
         * Note that this will not change the case sensitivity of any element that was already added.
         *
         * @return This object.
         */
        public abstract Builder caseInsensitiveByDefault();

        /**
         * Indicates that by default nested elements will not be obfuscated.
         * This method is shorthand for calling {@link #excludeNestedElementsByDefault()}.
         * <p>
         * Note that this will not change what will be obfuscated for any element that was already added.
         *
         * @return This object.
         */
        public Builder textOnlyByDefault() {
            return excludeNestedElementsByDefault();
        }

        /**
         * Indicates that by default nested elements will not be obfuscated.
         * This can be overridden per element using {@link ElementConfigurer#includeNestedElements()}
         * <p>
         * Note that this will not change what will be obfuscated for any element that was already added.
         *
         * @return This object.
         */
        public abstract Builder excludeNestedElementsByDefault();

        /**
         * Indicates that by default nested elements will be obfuscated (default).
         * This method is shorthand for calling {@link #includeNestedElementsByDefault()}.
         * <p>
         * Note that this will not change what will be obfuscated for any element that was already added.
         *
         * @return This object.
         */
        public Builder allByDefault() {
            return includeNestedElementsByDefault();
        }

        /**
         * Indicates that by default nested elements will be obfuscated (default).
         * This can be overridden per element using {@link ElementConfigurer#excludeNestedElements()}
         * <p>
         * Note that this will not change what will be obfuscated for any element that was already added.
         *
         * @return This object.
         */
        public abstract Builder includeNestedElementsByDefault();

        /**
         * Sets the warning to include if an {@link XMLStreamException} is thrown.
         * This can be used to override the default message. Use {@code null} to omit the warning.
         *
         * @param warning The warning to include.
         * @return This object.
         */
        public abstract Builder withMalformedXMLWarning(String warning);

        /**
         * This method allows the application of a function to this builder.
         * <p>
         * Any exception thrown by the function will be propagated to the caller.
         *
         * @param <R> The type of the result of the function.
         * @param f The function to apply.
         * @return The result of applying the function to this builder.
         */
        public <R> R transform(Function<? super Builder, ? extends R> f) {
            return f.apply(this);
        }

        /**
         * Creates a new {@code XMLObfuscator} with the elements and obfuscators added to this builder.
         *
         * @return The created {@code XMLObfuscator}.
         */
        public abstract XMLObfuscator build();
    }

    /**
     * An object that can be used to configure an element that should be obfuscated.
     *
     * @author Rob Spoor
     */
    public abstract static class ElementConfigurer extends Builder {

        private ElementConfigurer() {
            super();
        }

        /**
         * Indicates that elements nested in elements with the current name will not be obfuscated.
         * This method is shorthand for calling both {@link #excludeNestedElements()}.
         *
         * @return An object that can be used to configure the element, or continue building {@link XMLObfuscator XMLObfuscators}.
         */
        public ElementConfigurer textOnly() {
            return excludeNestedElements();
        }

        /**
         * Indicates that elements nested in elements with the current name will not be obfuscated.
         *
         * @return An object that can be used to configure the element, or continue building {@link XMLObfuscator XMLObfuscators}.
         */
        public abstract ElementConfigurer excludeNestedElements();

        /**
         * Indicates that elements nested in elements with the current name will be obfuscated.
         * This method is shorthand for calling {@link #includeNestedElements()}.
         *
         * @return An object that can be used to configure the element, or continue building {@link XMLObfuscator XMLObfuscators}.
         */
        public ElementConfigurer all() {
            return includeNestedElements();
        }

        /**
         * Indicates that elements nested in elements with the current name will be obfuscated.
         *
         * @return An object that can be used to configure the element, or continue building {@link XMLObfuscator XMLObfuscators}.
         */
        public abstract ElementConfigurer includeNestedElements();
    }

    private static final class ObfuscatorBuilder extends ElementConfigurer {

        private final MapBuilder<ElementConfig> elements;

        private String malformedXMLWarning;

        // default settings
        private boolean obfuscateNestedElementsByDefault;

        // per element settings
        private String element;
        private Obfuscator obfuscator;
        private CaseSensitivity caseSensitivity;
        private boolean obfuscateNestedElements;

        private ObfuscatorBuilder() {
            elements = new MapBuilder<>();
            malformedXMLWarning = Messages.XMLObfuscator.malformedXML.text.get();

            obfuscateNestedElementsByDefault = true;
        }

        @Override
        public ElementConfigurer withElement(String element, Obfuscator obfuscator) {
            addLastElement();

            elements.testEntry(element);

            this.element = element;
            this.obfuscator = obfuscator;
            this.caseSensitivity = null;
            this.obfuscateNestedElements = obfuscateNestedElementsByDefault;

            return this;
        }

        @Override
        public ElementConfigurer withElement(String element, Obfuscator obfuscator, CaseSensitivity caseSensitivity) {
            addLastElement();

            elements.testEntry(element, caseSensitivity);

            this.element = element;
            this.obfuscator = obfuscator;
            this.caseSensitivity = caseSensitivity;
            this.obfuscateNestedElements = obfuscateNestedElementsByDefault;

            return this;
        }

        @Override
        public Builder caseSensitiveByDefault() {
            elements.caseSensitiveByDefault();
            return this;
        }

        @Override
        public Builder caseInsensitiveByDefault() {
            elements.caseInsensitiveByDefault();
            return this;
        }

        @Override
        public Builder excludeNestedElementsByDefault() {
            obfuscateNestedElementsByDefault = false;
            return this;
        }

        @Override
        public Builder includeNestedElementsByDefault() {
            obfuscateNestedElementsByDefault = true;
            return this;
        }

        @Override
        public ElementConfigurer excludeNestedElements() {
            obfuscateNestedElements = false;
            return this;
        }

        @Override
        public ElementConfigurer includeNestedElements() {
            obfuscateNestedElements = true;
            return this;
        }

        @Override
        public Builder withMalformedXMLWarning(String warning) {
            malformedXMLWarning = warning;
            return this;
        }

        private Map<String, ElementConfig> elements() {
            return elements.build();
        }

        private void addLastElement() {
            if (element != null) {
                ElementConfig elementConfig = new ElementConfig(obfuscator, obfuscateNestedElements);
                if (caseSensitivity != null) {
                    elements.withEntry(element, elementConfig, caseSensitivity);
                } else {
                    elements.withEntry(element, elementConfig);
                }
            }

            element = null;
            obfuscator = null;
            caseSensitivity = null;
            obfuscateNestedElements = obfuscateNestedElementsByDefault;
        }

        @Override
        public XMLObfuscator build() {
            addLastElement();

            return new XMLObfuscator(this);
        }
    }
}
