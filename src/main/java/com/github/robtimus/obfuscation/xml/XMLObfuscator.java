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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
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
import com.ctc.wstx.exc.WstxLazyException;
import com.ctc.wstx.stax.WstxInputFactory;
import com.ctc.wstx.stax.WstxOutputFactory;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.support.CachingObfuscatingWriter;
import com.github.robtimus.obfuscation.support.CaseSensitivity;
import com.github.robtimus.obfuscation.support.CountingReader;
import com.github.robtimus.obfuscation.support.LimitAppendable;
import com.github.robtimus.obfuscation.support.MapBuilder;

/**
 * An obfuscator that obfuscates XML elements in {@link CharSequence CharSequences} or the contents of {@link Reader Readers}.
 * An {@code XMLObfuscator} will only obfuscate text, and ignore leading and trailing whitespace.
 * It will never obfuscate element tag names, comments, etc.
 * <p>
 * By default, if an obfuscator is configured for an element, it will be used to obfuscate the text of all nested elements as well. This can be turned
 * off using {@link Builder#excludeNestedElementsByDefault()} and/or {@link ElementConfigurer#excludeNestedElements()}. This will allow the nested
 * elements to use their own obfuscators.
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

    private final Map<String, ElementConfig> elements;
    private final Map<QName, ElementConfig> qualifiedElements;

    private final Map<String, AttributeConfig> attributes;
    private final Map<QName, AttributeConfig> qualifiedAttributes;

    private final String malformedXMLWarning;

    private final long limit;
    private final String truncatedIndicator;

    private final boolean useXmlWriter;

    private XMLObfuscator(ObfuscatorBuilder builder) {
        elements = builder.elements();
        qualifiedElements = builder.qualifiedElements();

        attributes = builder.attributes();
        qualifiedAttributes = builder.qualifiedAttributes();

        malformedXMLWarning = builder.malformedXMLWarning;

        limit = builder.limit;
        truncatedIndicator = builder.truncatedIndicator;

        useXmlWriter = builder.useXmlWriter;
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

    private static void setPropertyIfSupported(XMLInputFactory inputFactory, String name, Object value) {
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
        if (useXmlWriter) {
            obfuscateTextWriting(s, start, end, destination);
        } else {
            obfuscateTextIndexed(s, start, end, destination);
        }
    }

    @Override
    public void obfuscateText(Reader input, Appendable destination) throws IOException {
        if (useXmlWriter) {
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
        return new WritingObfuscatingXMLParser(xmlStreamReader, xmlStreamWriter, elements, qualifiedElements, attributes, qualifiedAttributes);
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
        return new IndexedObfuscatingXMLParser(xmlStreamReader, source, start, end, destination, elements, qualifiedElements);
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
                && qualifiedElements.equals(other.qualifiedElements)
                && attributes.equals(other.attributes)
                && qualifiedAttributes.equals(other.qualifiedAttributes)
                && Objects.equals(malformedXMLWarning, other.malformedXMLWarning)
                && limit == other.limit
                && Objects.equals(truncatedIndicator, other.truncatedIndicator);
        // useXmlWriter is calculated, not included
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + elements.hashCode();
        result = prime * result + qualifiedElements.hashCode();
        result = prime * result + attributes.hashCode();
        result = prime * result + qualifiedAttributes.hashCode();
        result = prime * result + Objects.hashCode(malformedXMLWarning);
        result = prime * result + Long.hashCode(limit);
        result = prime * result + Objects.hashCode(truncatedIndicator);
        // useXmlWriter is calculated, not included
        return result;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return getClass().getName()
                + "[elements=" + elements
                + ",qualifiedElements=" + qualifiedElements
                + ",attributes=" + attributes
                + ",qualifiedAttributes=" + qualifiedAttributes
                + ",malformedXMLWarning=" + malformedXMLWarning
                + ",limit=" + limit
                + ",truncatedIndicator=" + truncatedIndicator
                // useXmlWriter is calculated, not included
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
    public interface Builder {

        /**
         * Adds an element to obfuscate.
         * <p>
         * This method is an alias for {@link #withElement(String, Obfuscator, CaseSensitivity)} with the last specified default case sensitivity
         * using {@link #caseSensitiveByDefault()} or {@link #caseInsensitiveByDefault()}. The default is {@link CaseSensitivity#CASE_SENSITIVE}.
         *
         * @param element The local name of the element.
         * @param obfuscator The obfuscator to use for obfuscating the element.
         * @return An object that can be used to configure the element, or continue building {@link XMLObfuscator XMLObfuscators}.
         * @throws NullPointerException If the given element name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If an element with the same local name and the same case sensitivity was already added.
         */
        ElementConfigurer withElement(String element, Obfuscator obfuscator);

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
        ElementConfigurer withElement(String element, Obfuscator obfuscator, CaseSensitivity caseSensitivity);

        /**
         * Adds an element to obfuscate.
         * Any element added using this method will take precedence over elements added using {@link #withElement(String, Obfuscator)} or
         * {@link #withElement(String, Obfuscator, CaseSensitivity)}.
         *
         * @param element The qualified name of the element.
         * @param obfuscator The obfuscator to use for obfuscating the element.
         * @return An object that can be used to configure the element, or continue building {@link XMLObfuscator XMLObfuscators}.
         * @throws NullPointerException If the given element name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If an element with the same qualified name was already added.
         */
        ElementConfigurer withElement(QName element, Obfuscator obfuscator);

        /**
         * Adds an attribute to obfuscate. This will cause any occurrence of the attribute to be obfuscated, regardless of their elements.
         * The returned object can be used to define obfuscators for occurrences of the attribute in specific elements.
         * <p>
         * This method is an alias for {@link #withAttribute(String, Obfuscator, CaseSensitivity)} with the last specified default case sensitivity
         * using {@link #caseSensitiveByDefault()} or {@link #caseInsensitiveByDefault()}. The default is {@link CaseSensitivity#CASE_SENSITIVE}.
         * <p>
         * Note: because locations of attributes are not easily available, XML obfuscators will generate new, obfuscated documents when attributes
         * need to be obfuscated.
         *
         * @param attribute The local name of the attribute.
         * @param obfuscator The obfuscator to use for obfuscating the attribute.
         * @return An object that can be used to configure the attribute, or continue building {@link XMLObfuscator XMLObfuscators}.
         * @throws NullPointerException If the given attribute name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If an attribute with the same local name and the same case sensitivity was already added.
         * @since 1.2
         */
        AttributeConfigurer withAttribute(String attribute, Obfuscator obfuscator);

        /**
         * Adds an attribute to obfuscate. This will cause any occurrence of the attribute to be obfuscated, regardless of their elements.
         * The returned object can be used to define obfuscators for occurrences of the attribute in specific elements.
         * <p>
         * Note: because locations of attributes are not easily available, XML obfuscators will generate new, obfuscated documents when attributes
         * need to be obfuscated.
         *
         * @param attribute The local name of the attribute.
         * @param obfuscator The obfuscator to use for obfuscating the attribute.
         * @param caseSensitivity The case sensitivity for the attribute.
         * @return An object that can be used to configure the attribute, or continue building {@link XMLObfuscator XMLObfuscators}.
         * @throws NullPointerException If the given attribute name, obfuscator or case sensitivity is {@code null}.
         * @throws IllegalArgumentException If an attribute with the same local name and the same case sensitivity was already added.
         * @since 1.2
         */
        AttributeConfigurer withAttribute(String attribute, Obfuscator obfuscator, CaseSensitivity caseSensitivity);

        /**
         * Adds an attribute to obfuscate. This will cause any occurrence of the attribute to be obfuscated, regardless of their elements.
         * The returned object can be used to define obfuscators for occurrences of the attribute in specific elements.
         * Any attribute added using this method will take precedence over attributes added using {@link #withAttribute(String, Obfuscator)} or
         * {@link #withAttribute(String, Obfuscator, CaseSensitivity)}.
         * <p>
         * Note: because locations of attributes are not easily available, XML obfuscators will generate new, obfuscated documents when attributes
         * need to be obfuscated.
         *
         * @param attribute The qualified name of the attribute.
         * @param obfuscator The obfuscator to use for obfuscating the attribute.
         * @return An object that can be used to configure the attribute, or continue building {@link XMLObfuscator XMLObfuscators}.
         * @throws NullPointerException If the given attribute name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If an attribute with the same qualified name was already added.
         * @since 1.2
         */
        AttributeConfigurer withAttribute(QName attribute, Obfuscator obfuscator);

        /**
         * Sets the default case sensitivity for new elements and attributes to {@link CaseSensitivity#CASE_SENSITIVE}. This is the default setting.
         * <p>
         * Note that this will not change the case sensitivity of any element or attribute that was already added.
         *
         * @return This object.
         */
        Builder caseSensitiveByDefault();

        /**
         * Sets the default case sensitivity for new elements and attributes to {@link CaseSensitivity#CASE_INSENSITIVE}.
         * <p>
         * Note that this will not change the case sensitivity of any element or attribute that was already added.
         *
         * @return This object.
         */
        Builder caseInsensitiveByDefault();

        /**
         * Indicates that by default nested elements will not be obfuscated.
         * This method is shorthand for calling {@link #excludeNestedElementsByDefault()}.
         * <p>
         * Note that this will not change what will be obfuscated for any element that was already added.
         *
         * @return This object.
         */
        default Builder textOnlyByDefault() {
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
        Builder excludeNestedElementsByDefault();

        /**
         * Indicates that by default nested elements will be obfuscated (default).
         * This method is shorthand for calling {@link #includeNestedElementsByDefault()}.
         * <p>
         * Note that this will not change what will be obfuscated for any element that was already added.
         *
         * @return This object.
         */
        default Builder allByDefault() {
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
        Builder includeNestedElementsByDefault();

        /**
         * Sets the warning to include if an {@link XMLStreamException} is thrown.
         * This can be used to override the default message. Use {@code null} to omit the warning.
         *
         * @param warning The warning to include.
         * @return This object.
         */
        Builder withMalformedXMLWarning(String warning);

        /**
         * Sets the limit for the obfuscated result.
         *
         * @param limit The limit to use.
         * @return An object that can be used to configure the handling when the obfuscated result exceeds a pre-defined limit,
         *         or continue building {@link XMLObfuscator XMLObfuscators}.
         * @throws IllegalArgumentException If the given limit is negative.
         * @since 1.1
         */
        LimitConfigurer limitTo(long limit);

        /**
         * This method allows the application of a function to this builder.
         * <p>
         * Any exception thrown by the function will be propagated to the caller.
         *
         * @param <R> The type of the result of the function.
         * @param f The function to apply.
         * @return The result of applying the function to this builder.
         */
        default <R> R transform(Function<? super Builder, ? extends R> f) {
            return f.apply(this);
        }

        /**
         * Creates a new {@code XMLObfuscator} with the elements and obfuscators added to this builder.
         *
         * @return The created {@code XMLObfuscator}.
         */
        XMLObfuscator build();
    }

    /**
     * An object that can be used to configure an element that should be obfuscated.
     *
     * @author Rob Spoor
     */
    public interface ElementConfigurer extends Builder {

        /**
         * Indicates that elements nested in elements with the current name will not be obfuscated.
         * This method is shorthand for calling both {@link #excludeNestedElements()}.
         *
         * @return An object that can be used to configure the element, or continue building {@link XMLObfuscator XMLObfuscators}.
         */
        default ElementConfigurer textOnly() {
            return excludeNestedElements();
        }

        /**
         * Indicates that elements nested in elements with the current name will not be obfuscated.
         *
         * @return An object that can be used to configure the element, or continue building {@link XMLObfuscator XMLObfuscators}.
         */
        ElementConfigurer excludeNestedElements();

        /**
         * Indicates that elements nested in elements with the current name will be obfuscated.
         * This method is shorthand for calling {@link #includeNestedElements()}.
         *
         * @return An object that can be used to configure the element, or continue building {@link XMLObfuscator XMLObfuscators}.
         */
        default ElementConfigurer all() {
            return includeNestedElements();
        }

        /**
         * Indicates that elements nested in elements with the current name will be obfuscated.
         *
         * @return An object that can be used to configure the element, or continue building {@link XMLObfuscator XMLObfuscators}.
         */
        ElementConfigurer includeNestedElements();
    }

    /**
     * An object that can be used to configure an attribute that should be obfuscated.
     *
     * @author Rob Spoor
     * @since 1.2
     */
    public interface AttributeConfigurer extends Builder {

        /**
         * Sets the obfuscator to use for occurrences of the attribute for a specific element.
         * <p>
         * This method is an alias for {@link #forElement(String, Obfuscator, CaseSensitivity)} with the last specified default case sensitivity
         * using {@link #caseSensitiveByDefault()} or {@link #caseInsensitiveByDefault()}. The default is {@link CaseSensitivity#CASE_SENSITIVE}.
         *
         * @param element The local name of the element.
         * @param obfuscator The obfuscator to use for obfuscating the attribute.
         * @return An object that can be used to configure the attribute, or continue building {@link XMLObfuscator XMLObfuscators}.
         * @throws NullPointerException If the given element name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If an element with the same local name and the same case sensitivity was already added for the attribute.
         * @since 1.3
         */
        AttributeConfigurer forElement(String element, Obfuscator obfuscator);

        /**
         * Sets the obfuscator to use for occurrences of the attribute for a specific element.
         *
         * @param element The local name of the element.
         * @param obfuscator The obfuscator to use for obfuscating the attribute.
         * @param caseSensitivity The case sensitivity for the element.
         * @return An object that can be used to configure the attribute, or continue building {@link XMLObfuscator XMLObfuscators}.
         * @throws NullPointerException If the given element name, obfuscator or case sensitivity is {@code null}.
         * @throws IllegalArgumentException If an element with the same local name and the same case sensitivity was already added for the attribute.
         * @since 1.3
         */
        AttributeConfigurer forElement(String element, Obfuscator obfuscator, CaseSensitivity caseSensitivity);

        /**
         * Sets the obfuscator to use for occurrences of the attribute for a specific element.
         * Any element added using this method will take precedence over elements added using {@link #forElement(String, Obfuscator)} or
         * {@link #forElement(String, Obfuscator, CaseSensitivity)}.
         *
         * @param element The qualified name of the element.
         * @param obfuscator The obfuscator to use for obfuscating the attribute.
         * @return An object that can be used to configure the attribute, or continue building {@link XMLObfuscator XMLObfuscators}.
         * @throws NullPointerException If the given element name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If an element with the same qualified name was already added for the attribute.
         * @since 1.3
         */
        AttributeConfigurer forElement(QName element, Obfuscator obfuscator);
    }

    /**
     * An object that can be used to configure handling when the obfuscated result exceeds a pre-defined limit.
     *
     * @author Rob Spoor
     * @since 1.1
     */
    public interface LimitConfigurer extends Builder {

        /**
         * Sets the indicator to use when the obfuscated result is truncated due to the limit being exceeded.
         * There can be one place holder for the total number of characters. Defaults to {@code ... (total: %d)}.
         * Use {@code null} to omit the indicator.
         *
         * @param pattern The pattern to use as indicator.
         * @return An object that can be used to configure the handling when the obfuscated result exceeds a pre-defined limit,
         *         or continue building {@link XMLObfuscator XMLObfuscators}.
         */
        LimitConfigurer withTruncatedIndicator(String pattern);
    }

    private static final class ObfuscatorBuilder implements ElementConfigurer, AttributeConfigurer, LimitConfigurer {

        private final MapBuilder<ElementConfig> elements;
        private final Map<QName, ElementConfig> qualifiedElements;

        private final MapBuilder<AttributeConfig> attributes;
        private final Map<QName, AttributeConfig> qualifiedAttributes;

        private String malformedXMLWarning;

        private long limit;
        private String truncatedIndicator;

        // default settings
        private CaseSensitivity defaultCaseSensitivity;
        private boolean obfuscateNestedElementsByDefault;

        // per element / attribute settings
        private String element;
        private QName qualifiedElement;
        private String attribute;
        private QName qualifiedAttribute;
        private Obfuscator obfuscator;
        private CaseSensitivity caseSensitivity;
        private boolean obfuscateNestedElements;
        private MapBuilder<Obfuscator> attributeElements;
        private Map<QName, Obfuscator> qualifiedAttributeElements;

        // calculated settings
        private boolean useXmlWriter;

        private ObfuscatorBuilder() {
            elements = new MapBuilder<>();
            qualifiedElements = new HashMap<>();

            attributes = new MapBuilder<>();
            qualifiedAttributes = new HashMap<>();

            malformedXMLWarning = Messages.XMLObfuscator.malformedXML.text();

            limit = Long.MAX_VALUE;
            truncatedIndicator = "... (total: %d)"; //$NON-NLS-1$

            defaultCaseSensitivity = CASE_SENSITIVE;
            obfuscateNestedElementsByDefault = true;

            useXmlWriter = false;
        }

        @Override
        public ElementConfigurer withElement(String element, Obfuscator obfuscator) {
            return withElement(element, obfuscator, defaultCaseSensitivity);
        }

        @Override
        public ElementConfigurer withElement(String element, Obfuscator obfuscator, CaseSensitivity caseSensitivity) {
            addLastElementOrAttribute();

            elements.testEntry(element, caseSensitivity);

            this.element = element;
            this.obfuscator = obfuscator;
            this.caseSensitivity = caseSensitivity;
            this.obfuscateNestedElements = obfuscateNestedElementsByDefault;

            return this;
        }

        @Override
        public ElementConfigurer withElement(QName element, Obfuscator obfuscator) {
            addLastElementOrAttribute();

            Objects.requireNonNull(element);
            Objects.requireNonNull(obfuscator);

            if (qualifiedElements.containsKey(element)) {
                throw new IllegalArgumentException(Messages.XMLObfuscator.duplicateElement(element));
            }

            this.qualifiedElement = element;
            this.obfuscator = obfuscator;
            this.caseSensitivity = null;
            this.obfuscateNestedElements = obfuscateNestedElementsByDefault;

            return this;
        }

        @Override
        public AttributeConfigurer withAttribute(String attribute, Obfuscator obfuscator) {
            return withAttribute(attribute, obfuscator, defaultCaseSensitivity);
        }

        @Override
        public AttributeConfigurer withAttribute(String attribute, Obfuscator obfuscator, CaseSensitivity caseSensitivity) {
            addLastElementOrAttribute();

            attributes.testEntry(attribute, caseSensitivity);

            this.attribute = attribute;
            this.obfuscator = obfuscator;
            this.caseSensitivity = caseSensitivity;

            this.attributeElements = new MapBuilder<>();
            this.qualifiedAttributeElements = new HashMap<>();

            useXmlWriter = true;

            return this;
        }

        @Override
        public AttributeConfigurer withAttribute(QName attribute, Obfuscator obfuscator) {
            addLastElementOrAttribute();

            Objects.requireNonNull(attribute);
            Objects.requireNonNull(obfuscator);

            if (qualifiedAttributes.containsKey(attribute)) {
                throw new IllegalArgumentException(Messages.XMLObfuscator.duplicateAttribute(attribute));
            }

            this.qualifiedAttribute = attribute;
            this.obfuscator = obfuscator;
            this.caseSensitivity = null;

            this.attributeElements = new MapBuilder<>();
            this.qualifiedAttributeElements = new HashMap<>();

            useXmlWriter = true;

            return this;
        }

        @Override
        public AttributeConfigurer forElement(String element, Obfuscator obfuscator) {
            return forElement(element, obfuscator, defaultCaseSensitivity);
        }

        @Override
        public AttributeConfigurer forElement(String element, Obfuscator obfuscator, CaseSensitivity caseSensitivity) {
            attributeElements.withEntry(element, obfuscator, caseSensitivity);

            return this;
        }

        @Override
        public AttributeConfigurer forElement(QName element, Obfuscator obfuscator) {
            Objects.requireNonNull(element);
            Objects.requireNonNull(obfuscator);

            if (qualifiedAttributeElements.containsKey(element)) {
                throw new IllegalArgumentException(Messages.XMLObfuscator.duplicateElement(element));
            }

            qualifiedAttributeElements.put(element, obfuscator);

            return this;
        }

        @Override
        public Builder caseSensitiveByDefault() {
            defaultCaseSensitivity = CASE_SENSITIVE;
            return this;
        }

        @Override
        public Builder caseInsensitiveByDefault() {
            defaultCaseSensitivity = CaseSensitivity.CASE_INSENSITIVE;
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

        @Override
        public LimitConfigurer limitTo(long limit) {
            if (limit < 0) {
                throw new IllegalArgumentException(limit + " < 0"); //$NON-NLS-1$
            }
            this.limit = limit;
            return this;
        }

        @Override
        public LimitConfigurer withTruncatedIndicator(String pattern) {
            this.truncatedIndicator = pattern;
            return this;
        }

        private Map<String, ElementConfig> elements() {
            return elements.build();
        }

        private Map<QName, ElementConfig> qualifiedElements() {
            return Collections.unmodifiableMap(new HashMap<>(qualifiedElements));
        }

        private Map<String, AttributeConfig> attributes() {
            return attributes.build();
        }

        private Map<QName, AttributeConfig> qualifiedAttributes() {
            return Collections.unmodifiableMap(new HashMap<>(qualifiedAttributes));
        }

        private Map<String, Obfuscator> attributeElements() {
            return attributeElements.build();
        }

        private Map<QName, Obfuscator> qualifiedAttributeElements() {
            return Collections.unmodifiableMap(new HashMap<>(qualifiedAttributeElements));
        }

        private void addLastElementOrAttribute() {
            if (attribute != null) {
                AttributeConfig attributeConfig = new AttributeConfig(obfuscator, attributeElements(), qualifiedAttributeElements());
                attributes.withEntry(attribute, attributeConfig, caseSensitivity);
            } else if (qualifiedAttribute != null) {
                AttributeConfig attributeConfig = new AttributeConfig(obfuscator, attributeElements(), qualifiedAttributeElements());
                qualifiedAttributes.put(qualifiedAttribute, attributeConfig);
            } else if (element != null) {
                ElementConfig elementConfig = new ElementConfig(obfuscator, obfuscateNestedElements);
                elements.withEntry(element, elementConfig, caseSensitivity);
            } else if (qualifiedElement != null) {
                ElementConfig elementConfig = new ElementConfig(obfuscator, obfuscateNestedElements);
                qualifiedElements.put(qualifiedElement, elementConfig);
            }

            element = null;
            qualifiedElement = null;
            attribute = null;
            qualifiedAttribute = null;
            obfuscator = null;
            caseSensitivity = defaultCaseSensitivity;
            obfuscateNestedElements = obfuscateNestedElementsByDefault;
        }

        @Override
        public XMLObfuscator build() {
            addLastElementOrAttribute();

            return new XMLObfuscator(this);
        }
    }
}
