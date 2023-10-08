/*
 * WritingObfuscatingXMLParser.java
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

import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.skipLeadingWhitespace;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.skipTrailingWhitespace;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.codehaus.stax2.DTDInfo;
import org.codehaus.stax2.XMLStreamReader2;
import com.github.robtimus.obfuscation.xml.XMLObfuscator.ElementConfigurer.ObfuscationMode;

//Do not implement XMLStreamParser, the mechanism is too different
final class WritingObfuscatingXMLParser {

    private final XMLStreamReader xmlStreamReader;
    private final XMLStreamWriter xmlStreamWriter;

    private final Map<String, ElementConfig> elements;
    private final Map<QName, ElementConfig> qualifiedElements;
    private final Map<String, AttributeConfig> attributes;
    private final Map<QName, AttributeConfig> qualifiedAttributes;

    private final Deque<ObfuscatedElement> currentElements = new ArrayDeque<>();
    private final StringBuilder currentText = new StringBuilder();
    private TextType currentTextType = TextType.NONE;
    private boolean obfuscateCurrentText;

    WritingObfuscatingXMLParser(XMLStreamReader xmlStreamReader, XMLStreamWriter xmlStreamWriter,
            Map<String, ElementConfig> elements, Map<QName, ElementConfig> qualifiedElements,
            Map<String, AttributeConfig> attributes, Map<QName, AttributeConfig> qualifiedAttributes) {

        this.xmlStreamReader = xmlStreamReader;
        this.xmlStreamWriter = xmlStreamWriter;
        this.elements = elements;
        this.qualifiedElements = qualifiedElements;
        this.attributes = attributes;
        this.qualifiedAttributes = qualifiedAttributes;
    }

    void initialize() throws XMLStreamException {
        startDocument();
    }

    boolean hasNext() throws XMLStreamException {
        return xmlStreamReader.hasNext();
    }

    void processNext() throws XMLStreamException {
        int event = nextEvent();
        switch (event) {
            case XMLStreamConstants.START_ELEMENT:
                startElement();
                break;
            case XMLStreamConstants.END_ELEMENT:
                endElement();
                break;
            case XMLStreamConstants.PROCESSING_INSTRUCTION:
                processingInstruction();
                break;
            case XMLStreamConstants.CHARACTERS:
                characters();
                break;
            case XMLStreamConstants.COMMENT:
                comment();
                break;
            case XMLStreamConstants.SPACE:
                space();
                break;
            // case XMLStreamConstants.START_DOCUMENT handled in initialize
            case XMLStreamConstants.END_DOCUMENT:
                endDocument();
                break;
            // case XMLStreamConstants.ENTITY_REFERENCE not supported; entities are resolved
            // case XMLStreamConstants.ATTRIBUTE handled in startElement
            case XMLStreamConstants.DTD:
                dtd();
                break;
            case XMLStreamConstants.CDATA:
                cdata();
                break;
            // case XMLStreamConstants.NAMESPACE handled in startElement
            // case XMLStreamConstants.NOTATION_DECLARATION not supported
            // case XMLStreamConstants.ENTITY_DECLARATION not supported
            default:
                break;
        }
    }

    private int nextEvent() throws XMLStreamException {
        try {
            int event = xmlStreamReader.next();
            if (event != XMLStreamConstants.CHARACTERS && event != XMLStreamConstants.CDATA) {
                finishLatestText();
            }
            return event;

        } catch (XMLStreamException e) {
            finishLatestText();
            throw e;
        }
    }

    private void startElement() throws XMLStreamException {
        QName name = xmlStreamReader.getName();

        ObfuscatedElement currentElement = currentElements.peekLast();
        if (currentElement == null || currentElement.allowsOverriding()) {
            // either not obfuscating any element, or the element allows overriding obfuscation - check the element itself
            ElementConfig config = configForElement(name);
            if (config != null) {
                currentElement = new ObfuscatedElement(config);
                currentElements.addLast(currentElement);
                currentElement.depth++;
            } else if (currentElement != null) {
                currentElement.depth++;
            }
        } else {
            // nested in an element that's being obfuscated
            currentElement.depth++;
        }

        xmlStreamWriter.writeStartElement(name.getPrefix(), name.getLocalPart(), name.getNamespaceURI());

        writeAttributes(name);
    }

    private void writeAttributes(QName elementName) throws XMLStreamException {
        int attributeCount = xmlStreamReader.getAttributeCount();
        for (int i = 0; i < attributeCount; i++) {
            QName attributeName = xmlStreamReader.getAttributeName(i);
            String attributeValue = xmlStreamReader.getAttributeValue(i);

            AttributeConfig attributeConfig = configForAttribute(attributeName);
            if (attributeConfig != null) {
                attributeValue = attributeConfig.obfuscator(elementName).obfuscateText(attributeValue).toString();
            }
            xmlStreamWriter.writeAttribute(attributeName.getPrefix(), attributeName.getNamespaceURI(), attributeName.getLocalPart(), attributeValue);
        }
    }

    private ElementConfig configForElement(QName elementName) {
        ElementConfig config = qualifiedElements.get(elementName);
        if (config == null) {
            config = elements.get(elementName.getLocalPart());
        }
        return config;
    }

    private AttributeConfig configForAttribute(QName attributeName) {
        AttributeConfig config = qualifiedAttributes.get(attributeName);
        if (config == null) {
            config = attributes.get(attributeName.getLocalPart());
        }
        return config;
    }

    private void endElement() throws XMLStreamException {
        xmlStreamWriter.writeEndElement();

        if (!currentElements.isEmpty()) {
            ObfuscatedElement currentElement = currentElements.getLast();
            currentElement.depth--;
            if (currentElement.depth == 0) {
                // done with the element
                currentElements.removeLast();
            }
            // else nested in an element that's being obfuscated
        }
        // else currently no element is being obfuscated
    }

    private void processingInstruction() throws XMLStreamException {
        String target = xmlStreamReader.getPITarget();
        String data = xmlStreamReader.getPIData();
        if (data == null || data.isEmpty()) {
            xmlStreamWriter.writeProcessingInstruction(target);
        } else {
            xmlStreamWriter.writeProcessingInstruction(target, data);
        }
    }

    private void characters() throws XMLStreamException {
        if (currentTextType != TextType.CHARACTERS) {
            // including CDATA -> CHARACTERS
            finishLatestText();
        }

        String text = xmlStreamReader.getText();
        if (currentElements.isEmpty()) {
            // not obfuscating anything
            xmlStreamWriter.writeCharacters(text);
            return;
        }
        ObfuscatedElement currentElement = currentElements.getLast();
        if (!currentElement.obfuscateNestedElements() && currentElement.depth != 1) {
            // nested inside an element that is configured to not have nested elements obfuscated, don't obfuscate
            xmlStreamWriter.writeCharacters(text);
            return;
        }
        if (!currentElement.config.performObfuscation) {
            // the obfuscator is Obfuscator.none(), which means we don't need to obfuscate
            xmlStreamWriter.writeCharacters(text);
            return;
        }
        // the text should be obfuscated, but not at this time
        currentText.append(text);
        currentTextType = TextType.CHARACTERS;
        obfuscateCurrentText = true;
    }

    private void comment() throws XMLStreamException {
        String comment = xmlStreamReader.getText();
        xmlStreamWriter.writeComment(comment);
    }

    private void space() throws XMLStreamException {
        String space = xmlStreamReader.getText();
        xmlStreamWriter.writeCharacters(space);
    }

    private void startDocument() throws XMLStreamException {
        String encoding = xmlStreamReader.getCharacterEncodingScheme();
        String version = xmlStreamReader.getVersion();
        xmlStreamWriter.writeStartDocument(encoding, version);
    }

    private void endDocument() throws XMLStreamException {
        // write the end document even if the destination's limit has been reached
        xmlStreamWriter.writeEndDocument();
    }

    private void dtd() throws XMLStreamException {
        // Woodstox returns the internal subset, but expects a full DTD - build it ourselves
        String dtd = getDTD((XMLStreamReader2) xmlStreamReader);
        xmlStreamWriter.writeDTD(dtd);
    }

    @SuppressWarnings("nls")
    static String getDTD(XMLStreamReader2 xmlStreamReader) throws XMLStreamException {
        DTDInfo dtdInfo = xmlStreamReader.getDTDInfo();
        String publicId = dtdInfo.getDTDPublicId();
        String systemId = dtdInfo.getDTDSystemId();
        String rootName = dtdInfo.getDTDRootName();
        String internalSubset = dtdInfo.getDTDInternalSubset();

        StringBuilder dtd = new StringBuilder()
                .append("<!DOCTYPE ")
                .append(rootName);
        if (publicId != null) {
            dtd.append(" PUBLIC ").append('"').append(publicId).append('"');
            if (systemId != null) {
                dtd.append(' ').append('"').append(systemId).append('"');
            }
        } else if (systemId != null) {
            dtd.append(" SYSTEM ").append('"').append(systemId).append('"');
        }
        if (internalSubset != null && !internalSubset.isEmpty()) {
            dtd.append(" [").append(internalSubset).append(']');
        }
        dtd.append('>');
        return dtd.toString();
    }

    private void cdata() throws XMLStreamException {
        if (currentTextType != TextType.CDATA) {
            // including CHARACTERS -> CDATA
            finishLatestText();
        }

        // always capture all CDATA text, to prevent multiple CDATA blocks after each other if the text becomes too large
        currentText.append(xmlStreamReader.getText());
        currentTextType = TextType.CDATA;

        if (currentElements.isEmpty()) {
            // not obfuscating anything
            obfuscateCurrentText = false;
            return;
        }
        ObfuscatedElement currentElement = currentElements.getLast();
        if (!currentElement.obfuscateNestedElements() && currentElement.depth != 1) {
            // nested inside an element that is configured to not have nested elements obfuscated, don't obfuscate
            obfuscateCurrentText = false;
            return;
        }
        if (!currentElement.config.performObfuscation) {
            // the obfuscator is Obfuscator.none(), which means we don't need to obfuscate
            obfuscateCurrentText = false;
            return;
        }
        // the text should be obfuscated, but not at this time
        obfuscateCurrentText = true;
    }

    private void finishLatestText() throws XMLStreamException {
        if (currentTextType == TextType.NONE) {
            // no need to finish anything
            return;
        }
        if (obfuscateCurrentText) {
            ObfuscatedElement currentElement = currentElements.getLast();
            int textLength = currentText.length();
            int obfuscationStart = skipLeadingWhitespace(currentText, 0, textLength);
            int obfuscationEnd = skipTrailingWhitespace(currentText, obfuscationStart, textLength);

            if (obfuscationStart == 0 && obfuscationEnd == textLength) {
                // obfuscate all
                String obfuscatedText = currentElement.config.obfuscator.obfuscateText(currentText, obfuscationStart, obfuscationEnd).toString();
                currentTextType.writeText(xmlStreamWriter, obfuscatedText);
            } else if (obfuscationStart == obfuscationEnd) {
                // obfuscate nothing
                currentTextType.writeText(xmlStreamWriter, currentText.toString());
            } else {
                StringBuilder text = new StringBuilder();
                text.append(currentText, 0, obfuscationStart);
                currentElement.config.obfuscator.obfuscateText(currentText, obfuscationStart, obfuscationEnd, text);
                text.append(currentText, obfuscationEnd, textLength);
                currentTextType.writeText(xmlStreamWriter, text.toString());
            }
        } else {
            // CDATA; CHARACTERS that don't need obfuscation are written out immediately
            currentTextType.writeText(xmlStreamWriter, currentText.toString());
        }
        currentText.delete(0, currentText.length());
        currentTextType = TextType.NONE;
        obfuscateCurrentText = false;
    }

    void flush() throws XMLStreamException {
        xmlStreamWriter.flush();
    }

    private static final class ObfuscatedElement {

        private final ElementConfig config;
        private int depth;

        private ObfuscatedElement(ElementConfig config) {
            this.config = config;
            this.depth = 0;
        }

        private boolean allowsOverriding() {
            return config.forNestedElements != ObfuscationMode.INHERIT;
        }

        private boolean obfuscateNestedElements() {
            return config.forNestedElements != ObfuscationMode.EXCLUDE;
        }
    }

    private enum TextType {
        CHARACTERS(XMLStreamWriter::writeCharacters),
        CDATA(XMLStreamWriter::writeCData),
        NONE,
        ;

        private final TextWriter textWriter;

        TextType() {
            this(null);
        }

        TextType(TextWriter textWriter) {
            this.textWriter = textWriter;
        }

        void writeText(XMLStreamWriter xmlStreamWriter, String text) throws XMLStreamException {
            textWriter.writeText(xmlStreamWriter, text);
        }

        private interface TextWriter {

            void writeText(XMLStreamWriter xmlStreamWriter, String text) throws XMLStreamException;
        }
    }
}
