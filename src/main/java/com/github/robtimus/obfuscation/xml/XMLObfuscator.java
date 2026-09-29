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

import static com.github.robtimus.obfuscation.support.CaseSensitivity.CASE_SENSITIVE;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.appendAtMost;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.checkStartAndEnd;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.copyTo;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.counting;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.discardAll;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.reader;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.writer;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.codehaus.stax2.XMLOutputFactory2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ctc.wstx.api.WstxInputProperties;
import com.ctc.wstx.api.WstxOutputProperties;
import com.ctc.wstx.exc.WstxLazyException;
import com.ctc.wstx.stax.WstxInputFactory;
import com.ctc.wstx.stax.WstxOutputFactory;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.support.CachingObfuscatingWriter;
import com.github.robtimus.obfuscation.support.CaseSensitivity;
import com.github.robtimus.obfuscation.support.CountingReader;
import com.github.robtimus.obfuscation.support.LimitAppendable;
import com.github.robtimus.obfuscation.xml.Lookup.MessageProvider;
import com.github.robtimus.obfuscation.xml.XMLObfuscator.ElementConfigurer.ContentType;
import com.github.robtimus.obfuscation.xml.XMLObfuscator.ElementConfigurer.ObfuscationMode;

/**
 * An obfuscator that obfuscates XML elements in {@link CharSequence CharSequences} or the contents of {@link Reader Readers}.
 * An {@code XMLObfuscator} will only obfuscate text, and ignore leading and trailing whitespace.
 * It will never obfuscate element tag names, comments, etc.
 * <p>
 * By default, if an obfuscator is configured for an element, it will be used to obfuscate the text of all nested elements as well. This can be turned
 * off using {@link Builder#withContentTypesByDefault(ContentType, ContentType...)} and/or
 * {@link ElementConfigurer#withContentTypes(ContentType, ContentType...)}. This will allow the nested elements to use their own obfuscators.
 * <p>
 * Note: preferably, obfuscation is done in such a way that the original structure and formatting is maintained. However, some functionality does not
 * allow for this to happen. If this functionality is needed, obfuscation will instead generate new, obfuscated XML documents. The resulting
 * obfuscated XML documents may slightly differ from the original. Methods in class {@link Builder} will mention it if they result in generating new
 * XML documents.
 *
 * @author Rob Spoor
 */
public final class XMLObfuscator extends Obfuscator {

    private static final Logger LOGGER = LoggerFactory.getLogger(XMLObfuscator.class);

    private static final XMLInputFactory INPUT_FACTORY = createInputFactory();
    private static final XMLOutputFactory OUTPUT_FACTORY = createOutputFactory();

    private final Lookup<ElementConfig> elements;
    private final String elementsRepresentation;

    private final Lookup<AttributeConfig> attributes;
    private final String attributesRepresentation;

    private final String malformedXMLWarning;

    private final long limit;
    private final String truncatedIndicator;

    private final boolean generateXML;

    private XMLObfuscator(Builder builder) {
        elements = builder.elements();
        elementsRepresentation = builder.elementsRepresentation();

        attributes = builder.attributes();
        attributesRepresentation = builder.attributesRepresentation();

        malformedXMLWarning = builder.malformedXMLWarning;

        limit = builder.limit;
        truncatedIndicator = builder.truncatedIndicator;

        generateXML = builder.generateXML;
    }

    static XMLInputFactory createInputFactory() {
        // Explicitly use Woodstox; any other implementation may not produce the correct locations
        XMLInputFactory inputFactory = new WstxInputFactory();
        setPropertyIfSupported(inputFactory, XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
        setPropertyIfSupported(inputFactory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); //$NON-NLS-1$
        setPropertyIfSupported(inputFactory, XMLConstants.ACCESS_EXTERNAL_STYLESHEET, ""); //$NON-NLS-1$
        setPropertyIfSupported(inputFactory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setPropertyIfSupported(inputFactory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        setPropertyIfSupported(inputFactory, WstxInputProperties.P_DTD_RESOLVER, (XMLResolver) (publicID, systemID, baseURI, namespace) -> {
            throw new XMLStreamException(Messages.XMLObfuscator.externalDTDsNotSupported(systemID));
        });
        return inputFactory;
    }

    static void setPropertyIfSupported(XMLInputFactory inputFactory, String name, Object value) {
        if (inputFactory.isPropertySupported(name)) {
            inputFactory.setProperty(name, value);
        } else {
            String message = Messages.XMLObfuscator.unsupportedProperty(name);
            LOGGER.warn(message);
        }
    }

    private static XMLOutputFactory createOutputFactory() {
        // Explicitly use Woodstox, to be consistent with the input factory
        XMLOutputFactory2 outputFactory = new WstxOutputFactory();
        outputFactory.configureForSpeed();
        // Needed to get namespace declarations in the resulting XML
        outputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
        outputFactory.setProperty(WstxOutputProperties.P_USE_DOUBLE_QUOTES_IN_XML_DECL, true);
        return outputFactory;
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
        if (generateXML) {
            obfuscateTextWriting(s, start, end, destination);
        } else {
            obfuscateTextIndexed(s, start, end, destination);
        }
    }

    @Override
    public void obfuscateText(Reader input, Appendable destination) throws IOException {
        if (generateXML) {
            obfuscateTextWriting(input, destination);
        } else {
            obfuscateTextIndexed(input, destination);
        }
    }

    private void obfuscateTextWriting(CharSequence s, int start, int end, Appendable destination) throws IOException {
        @SuppressWarnings("resource")
        Reader reader = reader(s, start, end);
        LimitAppendable appendable = appendAtMost(destination, limit);
        // No need to consume the reader, as it's backed by the CharSequence
        obfuscateTextWriting(reader, appendable, false);
        if (appendable.limitExceeded() && truncatedIndicator != null) {
            destination.append(String.format(truncatedIndicator, end - start));
        }
    }

    private void obfuscateTextWriting(Reader input, Appendable destination) throws IOException {
        @SuppressWarnings("resource")
        CountingReader countingReader = counting(input);
        LimitAppendable appendable = appendAtMost(destination, limit);
        // Consume the reader so countingReader.count() will give the correct result
        obfuscateTextWriting(countingReader, appendable, true);
        if (appendable.limitExceeded() && truncatedIndicator != null) {
            destination.append(String.format(truncatedIndicator, countingReader.count()));
        }
    }

    private void obfuscateTextWriting(Reader reader, LimitAppendable destination, boolean consumeReader) throws IOException {
        try {
            WritingObfuscatingXMLParser parser = createWritingParser(reader, destination);
            parser.initialize();
            try {
                while (parser.hasNext() && !destination.limitExceeded()) {
                    parser.processNext();
                }
            } finally {
                parser.flush();
            }
            if (consumeReader) {
                discardAll(reader);
            }
        } catch (XMLStreamException | WstxLazyException e) {
            LOGGER.warn(Messages.XMLObfuscator.malformedXML.warning(), e);
            if (malformedXMLWarning != null) {
                destination.append(malformedXMLWarning);
            }
        }
    }

    private WritingObfuscatingXMLParser createWritingParser(Reader input, LimitAppendable destination) {
        XMLStreamReader xmlStreamReader = createXmlStreamReader(input);
        XMLStreamWriter xmlStreamWriter = createXmlStreamWriter(destination);
        return new WritingObfuscatingXMLParser(xmlStreamReader, xmlStreamWriter, elements, attributes);
    }

    private void obfuscateTextIndexed(CharSequence s, int start, int end, Appendable destination) throws IOException {
        @SuppressWarnings("resource")
        Reader reader = reader(s, start, end);
        LimitAppendable appendable = appendAtMost(destination, limit);
        obfuscateTextIndexed(reader, new Source.OfCharSequence(s), start, end, appendable);
        if (appendable.limitExceeded() && truncatedIndicator != null) {
            destination.append(String.format(truncatedIndicator, end - start));
        }
    }

    private void obfuscateTextIndexed(Reader input, Appendable destination) throws IOException {
        @SuppressWarnings("resource")
        CountingReader countingReader = counting(input);
        Source.OfReader source = new Source.OfReader(countingReader, LOGGER);
        @SuppressWarnings("resource")
        Reader reader = copyTo(countingReader, source);
        LimitAppendable appendable = appendAtMost(destination, limit);
        obfuscateTextIndexed(reader, source, 0, -1, appendable);
        if (appendable.limitExceeded() && truncatedIndicator != null) {
            destination.append(String.format(truncatedIndicator, countingReader.count()));
        }
    }

    private void obfuscateTextIndexed(Reader input, Source source, int start, int end, LimitAppendable destination) throws IOException {
        IndexedObfuscatingXMLParser parser = createIndexedParser(input, source, start, end, destination);
        try {
            while (parser.hasNext() && !destination.limitExceeded()) {
                parser.processNext();
            }
            parser.appendRemainder();
        } catch (XMLStreamException | WstxLazyException e) {
            LOGGER.warn(Messages.XMLObfuscator.malformedXML.warning(), e);
            parser.finishLatestText();
            if (malformedXMLWarning != null) {
                destination.append(malformedXMLWarning);
            }
        }
    }

    private IndexedObfuscatingXMLParser createIndexedParser(Reader input, Source source, int start, int end, LimitAppendable destination) {
        XMLStreamReader xmlStreamReader = createXmlStreamReader(input);
        return new IndexedObfuscatingXMLParser(xmlStreamReader, source, start, end, destination, elements);
    }

    private XMLStreamReader createXmlStreamReader(Reader input) {
        try {
            return INPUT_FACTORY.createXMLStreamReader(input);
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("resource")
    private XMLStreamWriter createXmlStreamWriter(Appendable destination) {
        try {
            return OUTPUT_FACTORY.createXMLStreamWriter(writer(destination));
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
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
                && attributes.equals(other.attributes)
                && Objects.equals(malformedXMLWarning, other.malformedXMLWarning)
                && limit == other.limit
                && Objects.equals(truncatedIndicator, other.truncatedIndicator)
                && generateXML == other.generateXML;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + elements.hashCode();
        result = prime * result + attributes.hashCode();
        result = prime * result + Objects.hashCode(malformedXMLWarning);
        result = prime * result + Long.hashCode(limit);
        result = prime * result + Objects.hashCode(truncatedIndicator);
        result = prime * result + Boolean.hashCode(generateXML);
        return result;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return getClass().getName()
                + "[elements=" + elementsRepresentation
                + ",attributes=" + attributesRepresentation
                + ",malformedXMLWarning=" + malformedXMLWarning
                + ",limit=" + limit
                + ",truncatedIndicator=" + truncatedIndicator
                + ",generateXML=" + generateXML
                + "]";
    }

    /**
     * Returns a builder that will create {@code XMLObfuscators}.
     *
     * @return A builder that will create {@code XMLObfuscators}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder for {@link XMLObfuscator XMLObfuscators}.
     *
     * @author Rob Spoor
     */
    public static final class Builder {

        private final Lookup.Builder<ElementConfig> elements;
        private final StringBuilder elementsRepresentation;

        private final Lookup.Builder<AttributeConfig> attributes;
        private final StringBuilder attributesRepresentation;

        private String malformedXMLWarning;

        private long limit;
        private String truncatedIndicator;

        // default settings
        private CaseSensitivity defaultCaseSensitivity;
        private Set<ContentType> defaultContentTypes;
        private ObfuscationMode forNestedElementsByDefault;

        // calculated settings
        private boolean generateXML;

        private final LocalNameElementConfigurer localNameElementConfigurer;
        private final QNameElementConfigurer qualifiedNameElementConfigurer;
        private final LocalNameAttributeConfigurer localNameAttributeConfigurer;
        private final QNameAttributeConfigurer qualifiedNameAttributeConfigurer;
        private final LimitConfigurer limitConfigurer;

        private Builder() {
            elements = Lookup.builder(Lookup.MessageProvider.ELEMENT);
            elementsRepresentation = new StringBuilder().append('{');

            attributes = Lookup.builder(Lookup.MessageProvider.ATTRIBUTE);
            attributesRepresentation = new StringBuilder().append('{');

            malformedXMLWarning = Messages.XMLObfuscator.malformedXML.text();

            limit = Long.MAX_VALUE;
            truncatedIndicator = "... (total: %d)"; //$NON-NLS-1$

            defaultCaseSensitivity = CASE_SENSITIVE;
            defaultContentTypes = EnumSet.of(ContentType.ALL);
            forNestedElementsByDefault = ObfuscationMode.INHERIT;

            generateXML = false;

            localNameElementConfigurer = new LocalNameElementConfigurer();
            qualifiedNameElementConfigurer = new QNameElementConfigurer();
            localNameAttributeConfigurer = new LocalNameAttributeConfigurer();
            qualifiedNameAttributeConfigurer = new QNameAttributeConfigurer();
            limitConfigurer = new LimitConfigurer();
        }

        /**
         * Adds an element to obfuscate.
         * This method is equivalent to calling for {@link #withElement(String, Obfuscator, Consumer)} with a {@link Consumer} that does nothing.
         *
         * @param element The local name of the element.
         * @param obfuscator The obfuscator to use for obfuscating the element.
         * @return This object.
         * @throws NullPointerException If the given element name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If an element with the same local name and the same case sensitivity was already added.
         */
        public Builder withElement(String element, Obfuscator obfuscator) {
            addElement(element, obfuscator, null);
            return this;
        }

        /**
         * Adds an element to obfuscate.
         * This element will use the defaults set using {@link #caseSensitiveByDefault()}, {@link #caseInsensitiveByDefault()},
         * {@link #withContentTypesByDefault(ContentType, ContentType...)} and {@link #forNestedElementsByDefault(ObfuscationMode)}, unless explicitly
         * replaced by the given {@link Consumer}.
         *
         * @param element The local name of the element.
         * @param obfuscator The obfuscator to use for obfuscating the element.
         * @param configurer A {@link Consumer} that can be used to update its argument, to override any setting for the element.
         * @return This object.
         * @throws NullPointerException If the given element name, obfuscator or {@link Consumer} is {@code null}.
         * @throws IllegalArgumentException If an element with the same local name and the same case sensitivity was already added.
         * @since 2.0
         */
        public Builder withElement(String element, Obfuscator obfuscator, Consumer<LocalNameElementConfigurer> configurer) {
            Objects.requireNonNull(configurer);
            addElement(element, obfuscator, configurer);
            return this;
        }

        /**
         * Adds an element to obfuscate.
         * This method is equivalent to calling for {@link #withElement(QName, Obfuscator, Consumer)} with a {@link Consumer} that does nothing.
         *
         * @param element The qualified name of the element.
         * @param obfuscator The obfuscator to use for obfuscating the element.
         * @return This object.
         * @throws NullPointerException If the given element name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If an element with the same qualified name was already added.
         */
        public Builder withElement(QName element, Obfuscator obfuscator) {
            addElement(element, obfuscator, null);
            return this;
        }

        /**
         * Adds an element to obfuscate.
         * This element will use the defaults set using {@link #caseSensitiveByDefault()}, {@link #caseInsensitiveByDefault()},
         * {@link #withContentTypesByDefault(ContentType, ContentType...)} and {@link #forNestedElementsByDefault(ObfuscationMode)}, unless explicitly
         * replaced by the given {@link Consumer}.
         * Any element added using this method will take precedence over elements added using {@link #withElement(String, Obfuscator)} or
         * {@link #withElement(String, Obfuscator, Consumer)}.
         *
         * @param element The qualified name of the element.
         * @param obfuscator The obfuscator to use for obfuscating the element.
         * @param configurer A {@link Consumer} that can be used to update its argument, to override any setting for the element.
         * @return This object.
         * @throws NullPointerException If the given element name, obfuscator or {@link Consumer} is {@code null}.
         * @throws IllegalArgumentException If an element with the same qualified name was already added.
         * @since 2.0
         */
        public Builder withElement(QName element, Obfuscator obfuscator, Consumer<QNameElementConfigurer> configurer) {
            Objects.requireNonNull(configurer);
            addElement(element, obfuscator, configurer);
            return this;
        }

        private void addElement(String element, Obfuscator obfuscator, Consumer<LocalNameElementConfigurer> configurer) {
            addElement(element, obfuscator, localNameElementConfigurer, configurer,
                    (e, c) -> elements.add(element, c.newConfig(obfuscator), c.caseSensitivity));
        }

        private void addElement(QName element, Obfuscator obfuscator, Consumer<QNameElementConfigurer> configurer) {
            addElement(element, obfuscator, qualifiedNameElementConfigurer, configurer,
                    (e, c) -> elements.add(element, c.newConfig(obfuscator)));
        }

        private <N, C extends ElementConfigurer<C>> void addElement(N element, Obfuscator obfuscator,
                                                                    C elementConfigurer, Consumer<C> configurer,
                                                                    BiConsumer<N, C> elementAdder) {
            Objects.requireNonNull(element);
            Objects.requireNonNull(obfuscator);
            try {
                elementConfigurer.initialize(this);
                if (configurer != null) {
                    configurer.accept(elementConfigurer);
                }

                elementAdder.accept(element, elementConfigurer);

                addElementRepresentation(element, obfuscator, elementConfigurer);
            } finally {
                elementConfigurer.reset();
            }
        }

        @SuppressWarnings("nls")
        private <N, C extends ElementConfigurer<C>> void addElementRepresentation(N element, Obfuscator obfuscator, C elementConfigurer) {
            if (elementsRepresentation.length() > 1) {
                elementsRepresentation.append(", ");
            }
            elementsRepresentation.append(element).append("=[");
            elementConfigurer.appendElementRepresentation(elementsRepresentation, obfuscator);
            elementsRepresentation.append("]");
        }

        /**
         * Adds an attribute to obfuscate. This will cause any occurrence of the attribute to be obfuscated, regardless of their elements.
         * This method is equivalent to calling for {@link #withAttribute(String, Obfuscator, Consumer)} with a {@link Consumer} that does nothing.
         * <p>
         * Note: because locations of attributes are not easily available, XML obfuscators will generate new, obfuscated documents when attributes
         * need to be obfuscated.
         *
         * @param attribute The local name of the attribute.
         * @param obfuscator The obfuscator to use for obfuscating the attribute.
         * @return This object.
         * @throws NullPointerException If the given attribute name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If an attribute with the same local name and the same case sensitivity was already added.
         * @since 1.2
         */
        public Builder withAttribute(String attribute, Obfuscator obfuscator) {
            addAttribute(attribute, obfuscator, null);
            return this;
        }

        /**
         * Adds an attribute to obfuscate. This will cause any occurrence of the attribute to be obfuscated, regardless of their elements.
         * This attribute will use the defaults set using {@link #caseSensitiveByDefault()} and {@link #caseInsensitiveByDefault()}, unless explicitly
         * replaced by the given {@link Consumer}. This consumer can also be used to define obfuscators for occurrences of the attribute in specific
         * elements.
         * <p>
         * Note: because locations of attributes are not easily available, XML obfuscators will generate new, obfuscated documents when attributes
         * need to be obfuscated.
         *
         * @param attribute The local name of the attribute.
         * @param obfuscator The obfuscator to use for obfuscating the attribute.
         * @param configurer A {@link Consumer} that can be used to update its argument, to override any setting for the attribute.
         * @return This object.
         * @throws NullPointerException If the given attribute name, obfuscator or {@link Consumer} is {@code null}.
         * @throws IllegalArgumentException If an attribute with the same local name and the same case sensitivity was already added.
         * @since 2.0
         */
        public Builder withAttribute(String attribute, Obfuscator obfuscator, Consumer<LocalNameAttributeConfigurer> configurer) {
            Objects.requireNonNull(configurer);
            addAttribute(attribute, obfuscator, configurer);
            return this;
        }

        /**
         * Adds an attribute to obfuscate. This will cause any occurrence of the attribute to be obfuscated, regardless of their elements.
         * This method is equivalent to calling for {@link #withAttribute(QName, Obfuscator, Consumer)} with a {@link Consumer} that does nothing.
         * <p>
         * Note: because locations of attributes are not easily available, XML obfuscators will generate new, obfuscated documents when attributes
         * need to be obfuscated.
         *
         * @param attribute The qualified name of the attribute.
         * @param obfuscator The obfuscator to use for obfuscating the attribute.
         * @return This object.
         * @throws NullPointerException If the given attribute name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If an attribute with the same qualified name was already added.
         * @since 1.2
         */
        public Builder withAttribute(QName attribute, Obfuscator obfuscator) {
            addAttribute(attribute, obfuscator, null);
            return this;
        }

        /**
         * Adds an attribute to obfuscate. This will cause any occurrence of the attribute to be obfuscated, regardless of their elements.
         * The given {@link Consumer} can be used to define obfuscators for occurrences of the attribute in specific elements.
         * Any attribute added using this method will take precedence over attributes added using {@link #withAttribute(String, Obfuscator)} or
         * {@link #withAttribute(String, Obfuscator, Consumer)}.
         * <p>
         * Note: because locations of attributes are not easily available, XML obfuscators will generate new, obfuscated documents when attributes
         * need to be obfuscated.
         *
         * @param attribute The qualified name of the attribute.
         * @param obfuscator The obfuscator to use for obfuscating the attribute.
         * @param configurer A {@link Consumer} that can be used to update its argument, to override any setting for the attribute.
         * @return This object.
         * @throws NullPointerException If the given attribute name, obfuscator or {@link Consumer} is {@code null}.
         * @throws IllegalArgumentException If an attribute with the same qualified name was already added.
         * @since 2.0
         */
        public Builder withAttribute(QName attribute, Obfuscator obfuscator, Consumer<QNameAttributeConfigurer> configurer) {
            Objects.requireNonNull(configurer);
            addAttribute(attribute, obfuscator, configurer);
            return this;
        }

        private void addAttribute(String attribute, Obfuscator obfuscator, Consumer<LocalNameAttributeConfigurer> configurer) {
            addAttribute(attribute, obfuscator, localNameAttributeConfigurer, configurer,
                    (a, c) -> attributes.add(attribute, c.newConfig(obfuscator), c.caseSensitivity));
        }

        private void addAttribute(QName attribute, Obfuscator obfuscator, Consumer<QNameAttributeConfigurer> configurer) {
            addAttribute(attribute, obfuscator, qualifiedNameAttributeConfigurer, configurer,
                    (a, c) -> attributes.add(a, c.newConfig(obfuscator)));
        }

        private <N, C extends AttributeConfigurer<C>> void addAttribute(N attribute, Obfuscator obfuscator,
                                                                        C attrituteConfigurer, Consumer<C> configurer,
                                                                        BiConsumer<N, C> attributeAdder) {
            Objects.requireNonNull(attribute);
            Objects.requireNonNull(obfuscator);
            try {
                attrituteConfigurer.initialize(this);
                if (configurer != null) {
                    configurer.accept(attrituteConfigurer);
                }

                attributeAdder.accept(attribute, attrituteConfigurer);

                addAttributeRepresentation(attribute, obfuscator, attrituteConfigurer);
            } finally {
                attrituteConfigurer.reset();
            }

            generateXML();
        }

        @SuppressWarnings("nls")
        private <N, C extends AttributeConfigurer<C>> void addAttributeRepresentation(N element, Obfuscator obfuscator, C attributeConfigurer) {
            if (attributesRepresentation.length() > 1) {
                attributesRepresentation.append(", ");
            }
            attributesRepresentation.append(element).append("=[");
            attributeConfigurer.appendAttributeRepresentation(attributesRepresentation, obfuscator);
            attributesRepresentation.append("]");
        }

        /**
         * Sets the default case sensitivity for new elements and attributes to {@link CaseSensitivity#CASE_SENSITIVE}. This is the default setting.
         * <p>
         * Note that this will not change the case sensitivity of any element or attribute that was already added.
         *
         * @return This object.
         */
        public Builder caseSensitiveByDefault() {
            defaultCaseSensitivity = CASE_SENSITIVE;
            return this;
        }

        /**
         * Sets the default case sensitivity for new elements and attributes to {@link CaseSensitivity#CASE_INSENSITIVE}.
         * <p>
         * Note that this will not change the case sensitivity of any element or attribute that was already added.
         *
         * @return This object.
         */
        public Builder caseInsensitiveByDefault() {
            defaultCaseSensitivity = CaseSensitivity.CASE_INSENSITIVE;
            return this;
        }

        /**
         * Sets several content types for which elements should be obfuscated by default.
         * <p>
         * Note that this will not change what will be obfuscated for any element that was already added.
         *
         * @param contentType The first content type to set.
         * @param additionalContentTypes Additional content types to set.
         * @return This object.
         * @throws NullPointerException If any of the given content types is {@code null}.
         * @since 2.0
         */
        public Builder withContentTypesByDefault(ContentType contentType, ContentType... additionalContentTypes) {
            defaultContentTypes.clear();
            defaultContentTypes.add(contentType);
            Collections.addAll(defaultContentTypes, additionalContentTypes);
            return this;
        }

        /**
         * Indicates how to handle nested elements. The default is {@link ObfuscationMode#INHERIT}.
         * This can be overridden per element using {@link ElementConfigurer#forNestedElements(ObfuscationMode)}
         * <p>
         * Note that this will not change what will be obfuscated for any element that was already added.
         *
         * @param obfuscationMode The obfuscation mode that determines how to handle nested elements.
         * @return This object.
         * @throws NullPointerException If the given obfuscation mode is {@code null}.
         * @since 1.4
         */
        public Builder forNestedElementsByDefault(ObfuscationMode obfuscationMode) {
            forNestedElementsByDefault = Objects.requireNonNull(obfuscationMode);
            return this;
        }

        /**
         * Sets the warning to include if an {@link XMLStreamException} is thrown.
         * This can be used to override the default message. Use {@code null} to omit the warning.
         *
         * @param warning The warning to include.
         * @return This object.
         */
        public Builder withMalformedXMLWarning(String warning) {
            malformedXMLWarning = warning;
            return this;
        }

        /**
         * Sets the limit for the obfuscated result.
         *
         * @param limit The limit to use.
         * @return This object.
         * @throws IllegalArgumentException If the given limit is negative.
         * @since 1.1
         */
        public Builder limitTo(long limit) {
            setLimit(limit, null);
            return this;
        }

        /**
         * Sets the limit for the obfuscated result.
         *
         * @param limit The limit to use.
         * @param configurer A {@link Consumer} that can be used to update its argument, to set any limit-specific properties.
         * @return This object.
         * @throws IllegalArgumentException If the given limit is negative.
         * @since 2.0
         */
        public Builder limitTo(long limit, Consumer<LimitConfigurer> configurer) {
            Objects.requireNonNull(configurer);
            setLimit(limit, configurer);
            return this;
        }

        private void setLimit(long limit, Consumer<LimitConfigurer> configurer) {
            if (limit < 0) {
                throw new IllegalArgumentException(limit + " < 0"); //$NON-NLS-1$
            }
            try {
                limitConfigurer.truncatedIndicator = truncatedIndicator;
                if (configurer != null) {
                    configurer.accept(limitConfigurer);
                }

                this.limit = limit;
                this.truncatedIndicator = limitConfigurer.truncatedIndicator;
            } finally {
                limitConfigurer.reset();
            }
        }

        /**
         * Indicates that XML obfuscators will always generate new, obfuscated documents. This method can be called when generating obfuscated
         * documents is preferred, and no other method triggers the generation of obfuscated documents.
         *
         * @return This object.
         * @since 1.3
         */
        public Builder generateXML() {
            generateXML = true;
            return this;
        }

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

        private Lookup<ElementConfig> elements() {
            return elements.build();
        }

        private String elementsRepresentation() {
            elementsRepresentation.append('}');
            String result = elementsRepresentation.toString();
            elementsRepresentation.deleteCharAt(elementsRepresentation.length() - 1);
            return result;
        }

        private Lookup<AttributeConfig> attributes() {
            return attributes.build();
        }

        private String attributesRepresentation() {
            attributesRepresentation.append('}');
            String result = attributesRepresentation.toString();
            attributesRepresentation.deleteCharAt(attributesRepresentation.length() - 1);
            return result;
        }

        /**
         * Creates a new {@code XMLObfuscator} with the elements and obfuscators added to this builder.
         *
         * @return The created {@code XMLObfuscator}.
         */
        public XMLObfuscator build() {
            return new XMLObfuscator(this);
        }
    }

    /**
     * An object that can be used to configure an element that should be obfuscated.
     *
     * @author Rob Spoor
     */
    public abstract static sealed class ElementConfigurer<C extends ElementConfigurer<C>> {

        private final Set<ContentType> contentTypes = EnumSet.noneOf(ContentType.class);

        private ObfuscationMode forNestedElements;

        private ElementConfigurer() {
        }

        /**
         * Sets several content types for which the element should be obfuscated.
         *
         * @param contentType The first content type to set.
         * @param additionalContentTypes Additional content types to set.
         * @return This object.
         * @throws NullPointerException If any of the given content types is {@code null}.
         * @since 2.0
         */
        public C withContentTypes(ContentType contentType, ContentType... additionalContentTypes) {
            contentTypes.clear();
            contentTypes.add(contentType);
            Collections.addAll(contentTypes, additionalContentTypes);
            return self();
        }

        /**
         * Indicates how to handle nested elements. The default is {@link ObfuscationMode#INHERIT}.
         *
         * @param obfuscationMode The obfuscation mode that determines how to handle nested elements.
         * @return This object.
         * @throws NullPointerException If the given obfuscation mode is {@code null}.
         * @since 1.4
         */
        public C forNestedElements(ObfuscationMode obfuscationMode) {
            forNestedElements = Objects.requireNonNull(obfuscationMode);
            return self();
        }

        @SuppressWarnings("unchecked")
        private C self() {
            return (C) this;
        }

        ElementConfig newConfig(Obfuscator obfuscator) {
            Set<ContentType> deAliasedContentTypes = contentTypes.stream()
                    .flatMap(contentType -> ContentType.DE_ALIASED_TYPES.get(contentType).stream())
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(ContentType.class)));
            return new ElementConfig(deAliasedContentTypes, obfuscator, forNestedElements);
        }

        void initialize(Builder builder) {
            contentTypes.clear();
            contentTypes.addAll(builder.defaultContentTypes);
            forNestedElements = builder.forNestedElementsByDefault;
        }

        @SuppressWarnings({ "nls", "squid:S1192" })
        void appendElementRepresentation(StringBuilder target, Obfuscator obfuscator) {
            target.append("contentTypes=").append(contentTypes);
            target.append("obfuscator=").append(obfuscator);
            target.append(",forNestedElements=").append(forNestedElements);
        }

        void reset() {
            contentTypes.clear();
            forNestedElements = null;
        }

        /**
         * The possible content types.
         *
         * @author Rob Spoor
         * @since 2.0
         */
        public enum ContentType {
            /**
             * Represents text content.
             */
            TEXT,
            /**
             * Represents nested elements.
             */
            NESTED_ELEMENT,
            /**
             * Represents all possible content.
             * This is an alias for combining {@link #TEXT} and {@link #NESTED_ELEMENT}.
             */
            ALL,
            ;

            private static final Map<ContentType, Set<ContentType>> DE_ALIASED_TYPES = deAliasedTypes();

            private static Map<ContentType, Set<ContentType>> deAliasedTypes() {
                Map<ContentType, Set<ContentType>> result = new EnumMap<>(ContentType.class);
                result.put(TEXT, EnumSet.of(TEXT));
                result.put(NESTED_ELEMENT, EnumSet.of(NESTED_ELEMENT));

                result.put(ALL, EnumSet.of(TEXT, NESTED_ELEMENT));

                return result;
            }
        }

        /**
         * The possible ways to deal with nested elements.
         *
         * @author Rob Spoor
         * @since 1.4
         */
        public enum ObfuscationMode {
            /**
             * Use the obfuscator for the text of the element itself as well as the text of all nested elements.
             **/
            INHERIT,

            /**
             * Use the obfuscator for the text of the element itself as well as the text of all nested elements.
             * If a nested element has its own obfuscator defined this will be used instead.
             **/
            INHERIT_OVERRIDABLE,
        }
    }

    /**
     * An object that can be used to configure an element that should be obfuscated based on its local non-qualified name.
     *
     * @author Rob Spoor
     * @since 2.0
     */
    public static final class LocalNameElementConfigurer extends ElementConfigurer<LocalNameElementConfigurer> {

        private CaseSensitivity caseSensitivity;

        private LocalNameElementConfigurer() {
        }

        /**
         * Sets the case sensitivity for the element to {@link CaseSensitivity#CASE_SENSITIVE}.
         *
         * @return This object.
         * @since 2.0
         */
        public LocalNameElementConfigurer caseSensitive() {
            caseSensitivity = CaseSensitivity.CASE_SENSITIVE;
            return this;
        }

        /**
         * Sets the case sensitivity for the element to {@link CaseSensitivity#CASE_INSENSITIVE}.
         *
         * @return This object.
         * @since 2.0
         */
        public LocalNameElementConfigurer caseInsensitive() {
            caseSensitivity = CaseSensitivity.CASE_INSENSITIVE;
            return this;
        }

        @Override
        void initialize(Builder builder) {
            super.initialize(builder);
            caseSensitivity = builder.defaultCaseSensitivity;
        }

        @Override
        @SuppressWarnings({ "nls", "squid:S1192" })
        void appendElementRepresentation(StringBuilder target, Obfuscator obfuscator) {
            if (caseSensitivity == CaseSensitivity.CASE_INSENSITIVE) {
                target.append("caseInsensitive,");
            }
            super.appendElementRepresentation(target, obfuscator);
        }

        @Override
        void reset() {
            super.reset();
            caseSensitivity = null;
        }
    }

    /**
     * An object that can be used to configure an element that should be obfuscated based on its qualified name.
     *
     * @author Rob Spoor
     * @since 2.0
     */
    public static final class QNameElementConfigurer extends ElementConfigurer<QNameElementConfigurer> {

        private QNameElementConfigurer() {
        }
    }

    /**
     * An object that can be used to configure an attribute that should be obfuscated.
     *
     * @author Rob Spoor
     * @since 1.2
     */
    public abstract static sealed class AttributeConfigurer<C extends AttributeConfigurer<C>> {

        private CaseSensitivity defaultCaseSensitivity;

        private final Lookup.Builder<Obfuscator> attributeElements;
        private StringBuilder elementsRepresentation;

        private final LocalNameAttributeElementConfigurer localNameAttributeElementConfigurer;

        private AttributeConfigurer() {
            attributeElements = Lookup.builder(MessageProvider.ELEMENT);
            localNameAttributeElementConfigurer = new LocalNameAttributeElementConfigurer();
        }

        /**
         * Sets the obfuscator to use for occurrences of the attribute for a specific element.
         * <p>
         * This method is equivalent to calling for {@link #forElement(String, Obfuscator, Consumer)} with a {@link Consumer} that does nothing.
         *
         * @param element The local name of the element.
         * @param obfuscator The obfuscator to use for obfuscating the attribute.
         * @return This object.
         * @throws NullPointerException If the given element name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If an element with the same local name and the same case sensitivity was already added for the attribute.
         * @since 1.3
         */
        public C forElement(String element, Obfuscator obfuscator) {
            addElement(element, obfuscator, null);
            return self();
        }

        /**
         * Sets the obfuscator to use for occurrences of the attribute for a specific element.
         * This element will use the defaults set using {@link Builder#caseSensitiveByDefault()} and {@link Builder#caseInsensitiveByDefault()},
         * unless explicitly replaced by the given {@link Consumer}.
         *
         * @param element The local name of the element.
         * @param obfuscator The obfuscator to use for obfuscating the attribute.
         * @param configurer A {@link Consumer} that can be used to update its argument, to override any setting for the element.
         * @return This object.
         * @throws NullPointerException If the given element name, obfuscator or case sensitivity is {@code null}.
         * @throws IllegalArgumentException If an element with the same local name and the same case sensitivity was already added for the attribute.
         * @since 1.3
         */
        public C forElement(String element, Obfuscator obfuscator, Consumer<LocalNameAttributeElementConfigurer> configurer) {
            Objects.requireNonNull(configurer);
            addElement(element, obfuscator, configurer);
            return self();
        }

        private void addElement(String element, Obfuscator obfuscator, Consumer<LocalNameAttributeElementConfigurer> configurer) {
            Objects.requireNonNull(element);
            Objects.requireNonNull(obfuscator);
            try {
                localNameAttributeElementConfigurer.initialize(this);
                if (configurer != null) {
                    configurer.accept(localNameAttributeElementConfigurer);
                }

                attributeElements.add(element, obfuscator, localNameAttributeElementConfigurer.caseSensitivity);

                addElementRepresentation(element, obfuscator, localNameAttributeElementConfigurer.caseSensitivity);
            } finally {
                localNameAttributeElementConfigurer.reset();
            }
        }

        @SuppressWarnings("nls")
        private void addElementRepresentation(String element, Obfuscator obfuscator, CaseSensitivity caseSensitivity) {
            if (elementsRepresentation.length() > 1) {
                elementsRepresentation.append(", ");
            }
            elementsRepresentation.append(element).append("=[");
            if (caseSensitivity == CaseSensitivity.CASE_INSENSITIVE) {
                elementsRepresentation.append("caseInsensitive,");
            }
            elementsRepresentation.append("obfuscator=").append(obfuscator);
            elementsRepresentation.append("]");
        }

        /**
         * Sets the obfuscator to use for occurrences of the attribute for a specific element.
         * Any element added using this method will take precedence over elements added using {@link #forElement(String, Obfuscator)} or
         * {@link #forElement(String, Obfuscator, Consumer)}.
         *
         * @param element The qualified name of the element.
         * @param obfuscator The obfuscator to use for obfuscating the attribute.
         * @return This object.
         * @throws NullPointerException If the given element name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If an element with the same qualified name was already added for the attribute.
         * @since 1.3
         */
        public C forElement(QName element, Obfuscator obfuscator) {
            Objects.requireNonNull(element);
            Objects.requireNonNull(obfuscator);

            attributeElements.add(element, obfuscator);

            addElementRepresentation(element, obfuscator);

            return self();
        }

        @SuppressWarnings("nls")
        private void addElementRepresentation(QName element, Obfuscator obfuscator) {
            if (elementsRepresentation.length() > 1) {
                elementsRepresentation.append(", ");
            }
            elementsRepresentation.append(element).append("=[");
            elementsRepresentation.append("obfuscator=").append(obfuscator);
            elementsRepresentation.append("]");
        }

        @SuppressWarnings("unchecked")
        private C self() {
            return (C) this;
        }

        AttributeConfig newConfig(Obfuscator obfuscator) {
            return new AttributeConfig(obfuscator, attributeElements.build());
        }

        void initialize(Builder builder) {
            defaultCaseSensitivity = builder.defaultCaseSensitivity;
            elementsRepresentation = new StringBuilder().append('{');
        }

        @SuppressWarnings("nls")
        void appendAttributeRepresentation(StringBuilder target, Obfuscator obfuscator) {
            target.append("obfuscator=").append(obfuscator);
            if (elementsRepresentation.length() > 1) {
                // elementsRepresentation starts with a [, add an ending ]
                target.append(",elements=").append(elementsRepresentation).append(']');
            }
        }

        void reset() {
            defaultCaseSensitivity = null;
            attributeElements.clear();
            elementsRepresentation = null;
        }
    }

    /**
     * An object that can be used to configure an attribute that should be obfuscated based on its local non-qualified name.
     *
     * @author Rob Spoor
     * @since 2.0
     */
    public static final class LocalNameAttributeConfigurer extends AttributeConfigurer<LocalNameAttributeConfigurer> {

        private CaseSensitivity caseSensitivity;

        private LocalNameAttributeConfigurer() {
        }

        /**
         * Sets the case sensitivity for the attribute to {@link CaseSensitivity#CASE_SENSITIVE}.
         *
         * @return This object.
         * @since 2.0
         */
        public LocalNameAttributeConfigurer caseSensitive() {
            caseSensitivity = CaseSensitivity.CASE_SENSITIVE;
            return this;
        }

        /**
         * Sets the case sensitivity for the attribute to {@link CaseSensitivity#CASE_INSENSITIVE}.
         *
         * @return This object.
         * @since 2.0
         */
        public LocalNameAttributeConfigurer caseInsensitive() {
            caseSensitivity = CaseSensitivity.CASE_INSENSITIVE;
            return this;
        }

        @Override
        void initialize(Builder builder) {
            super.initialize(builder);
            caseSensitivity = builder.defaultCaseSensitivity;
        }

        @Override
        @SuppressWarnings("nls")
        void appendAttributeRepresentation(StringBuilder target, Obfuscator obfuscator) {
            if (caseSensitivity == CaseSensitivity.CASE_INSENSITIVE) {
                target.append("caseInsensitive,");
            }
            super.appendAttributeRepresentation(target, obfuscator);
        }

        @Override
        void reset() {
            super.reset();
            caseSensitivity = null;
        }
    }

    /**
     * An object that can be used to configure an attribute that should be obfuscated based on its qualified name.
     *
     * @author Rob Spoor
     * @since 2.0
     */
    public static final class QNameAttributeConfigurer extends AttributeConfigurer<QNameAttributeConfigurer> {

        private QNameAttributeConfigurer() {
        }
    }

    /**
     * An object that can be used to configure an attribute-specific element that should be obfuscated based on its local non-qualified name.
     *
     * @author Rob Spoor
     * @since 2.0
     */
    public static final class LocalNameAttributeElementConfigurer {

        private CaseSensitivity caseSensitivity;

        private LocalNameAttributeElementConfigurer() {
        }

        /**
         * Sets the case sensitivity for the element to {@link CaseSensitivity#CASE_SENSITIVE}.
         *
         * @return This object.
         * @since 2.0
         */
        public LocalNameAttributeElementConfigurer caseSensitive() {
            caseSensitivity = CaseSensitivity.CASE_SENSITIVE;
            return this;
        }

        /**
         * Sets the case sensitivity for the element to {@link CaseSensitivity#CASE_INSENSITIVE}.
         *
         * @return This object.
         * @since 2.0
         */
        public LocalNameAttributeElementConfigurer caseInsensitive() {
            caseSensitivity = CaseSensitivity.CASE_INSENSITIVE;
            return this;
        }

        void initialize(AttributeConfigurer<?> attributeConfigurer) {
            caseSensitivity = attributeConfigurer.defaultCaseSensitivity;
        }

        void reset() {
            caseSensitivity = null;
        }
    }

    /**
     * An object that can be used to configure handling when the obfuscated result exceeds a pre-defined limit.
     *
     * @author Rob Spoor
     * @since 1.1
     */
    public static final class LimitConfigurer {

        private String truncatedIndicator;

        private LimitConfigurer() {
        }

        /**
         * Sets the indicator to use when the obfuscated result is truncated due to the limit being exceeded.
         * There can be one place holder for the total number of characters. Defaults to {@code ... (total: %d)}.
         * Use {@code null} to omit the indicator.
         *
         * @param pattern The pattern to use as indicator.
         * @return This object.
         */
        public LimitConfigurer withTruncatedIndicator(String pattern) {
            this.truncatedIndicator = pattern;
            return this;
        }

        private void reset() {
            this.truncatedIndicator = null;
        }
    }
}
