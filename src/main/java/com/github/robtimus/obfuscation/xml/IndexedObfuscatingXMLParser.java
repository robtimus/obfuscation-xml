/*
 * IndexedObfuscatingXMLParser.java
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

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.codehaus.stax2.LocationInfo;
import com.github.robtimus.obfuscation.Obfuscator;

// Do not implement XMLStreamParser, the mechanism is too different
final class IndexedObfuscatingXMLParser {

    private static final String CDATA_START = "<![CDATA["; //$NON-NLS-1$
    private static final String CDATA_END = "]]>"; //$NON-NLS-1$

    private final XMLStreamReader xmlStreamReader;
    private final LocationInfo locationInfo;

    private final Source source;
    private final Appendable destination;

    private final Map<String, ElementConfig> elements;
    private final Map<QName, ElementConfig> qualifiedElements;

    private final int textOffset;
    private final int textEnd;
    private int textIndex;
    private int latestEndIndex;

    private final Deque<ObfuscatedElement> currentElements = new ArrayDeque<>();

    IndexedObfuscatingXMLParser(XMLStreamReader xmlStreamReader, Source source, int start, int end, Appendable destination,
            Map<String, ElementConfig> elements, Map<QName, ElementConfig> qualifiedElements) {

        this.xmlStreamReader = xmlStreamReader;
        this.locationInfo = (LocationInfo) xmlStreamReader;
        this.source = source;
        this.textOffset = start;
        this.textEnd = end;
        this.textIndex = start;
        this.destination = destination;
        this.elements = elements;
        this.qualifiedElements = qualifiedElements;
    }

    boolean hasNext() throws XMLStreamException {
        return xmlStreamReader.hasNext();
    }

    void processNext() throws XMLStreamException, IOException {
        int event = xmlStreamReader.next();
        int startIndex = (int) locationInfo.getStartingCharOffset() + textOffset;
        latestEndIndex = (int) locationInfo.getEndingCharOffset() + textOffset;

        processEvent(event, startIndex, latestEndIndex);
    }

    private void processEvent(int event, int startIndex, int endIndex) throws IOException {
        switch (event) {
            case XMLStreamConstants.START_ELEMENT:
                finishLatestText(startIndex);
                startElement(startIndex, endIndex);
                textIndex = endIndex;
                break;
            case XMLStreamConstants.END_ELEMENT:
                finishLatestText(startIndex);
                endElement(startIndex, endIndex);
                textIndex = endIndex;
                break;
            case XMLStreamConstants.CHARACTERS:
            case XMLStreamConstants.CDATA:
                // text can come in multiple events; don't finish the latest text, just update the index
                textIndex = text(startIndex, endIndex);
                break;
            default:
                finishLatestText(startIndex);
                appendUnobfuscated(startIndex, endIndex);
                textIndex = endIndex;
                break;
        }
    }

    private void startElement(int startIndex, int endIndex) throws IOException {
        ObfuscatedElement currentElement = currentElements.peekLast();
        if (currentElement == null || !currentElement.config.obfuscateNestedElements) {
            // either not obfuscating any element, or the element should not obfuscate nested elements - check the element itself
            ElementConfig config = configForCurrentElement();
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

        appendUnobfuscated(startIndex, endIndex);
    }

    private ElementConfig configForCurrentElement() {
        ElementConfig config = null;
        if (!qualifiedElements.isEmpty()) {
            QName elementName = xmlStreamReader.getName();
            config = qualifiedElements.get(elementName);
        }
        if (config == null) {
            String elementName = xmlStreamReader.getLocalName();
            config = elements.get(elementName);
        }
        return config;
    }

    private void endElement(int startIndex, int endIndex) throws IOException {
        if (containsAtIndex(startIndex, "</")) { //$NON-NLS-1$
            appendUnobfuscated(startIndex, endIndex);
        }
        // else not </, so it's a self-closing element; don't append it twice

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

    private int text(int startIndex, int endIndex) throws IOException {
        if (currentElements.isEmpty()) {
            // not obfuscating anything
            appendUnobfuscated(startIndex, endIndex);
            return endIndex;
        }
        ObfuscatedElement currentElement = currentElements.getLast();
        if (!currentElement.config.obfuscateNestedElements && currentElement.depth != 1) {
            // nested inside an element that is configured to not have nested elements obfuscated, don't obfuscate
            appendUnobfuscated(startIndex, endIndex);
            return endIndex;
        }
        if (!currentElement.config.performObfuscation) {
            // the obfuscator is Obfuscator.none(), which means we don't need to obfuscate
            appendUnobfuscated(startIndex, endIndex);
            return endIndex;
        }
        // the text should be obfuscated, but not at this time
        return textIndex;
    }

    private void finishLatestText(int currentEventStart) throws IOException {
        if (textIndex >= currentEventStart) {
            // no need to finish anything
            return;
        }
        if (currentElements.isEmpty()) {
            appendUnobfuscated(textIndex, currentEventStart);
            textIndex = currentEventStart;
        } else {
            // no need to check if obfuscation is necessary
            // the text method should have already ensured that if obfuscation shouldn't be enabled, this branch of code should not be reached
            ObfuscatedElement currentElement = currentElements.getLast();
            obfuscateText(textIndex, currentEventStart, currentElement.config.obfuscator);
        }
    }

    void finishLatestText() throws IOException {
        finishLatestText(latestEndIndex);
    }

    private void obfuscateText(int startIndex, int endIndex, Obfuscator obfuscator) throws IOException {
        int obfuscationStart = source.skipLeadingWhitespace(startIndex, endIndex);
        int obfuscationEnd = source.skipTrailingWhitespace(obfuscationStart, endIndex);

        if (containsAtIndex(obfuscationStart, CDATA_START) && containsAtIndex(obfuscationEnd - CDATA_END.length(), CDATA_END)) {
            obfuscationStart += CDATA_START.length();
            obfuscationEnd -= CDATA_END.length();

            obfuscationStart = source.skipLeadingWhitespace(obfuscationStart, obfuscationEnd);
            obfuscationEnd = source.skipTrailingWhitespace(obfuscationStart, obfuscationEnd);
        }

        if (startIndex < obfuscationStart) {
            source.appendTo(startIndex, obfuscationStart, destination);
        }
        if (obfuscationStart < obfuscationEnd) {
            source.obfuscateText(obfuscationStart, obfuscationEnd, obfuscator, destination);
        }
        if (obfuscationEnd < endIndex) {
            source.appendTo(obfuscationEnd, endIndex, destination);
        }
    }

    private void appendUnobfuscated(int startIndex, int endIndex) throws IOException {
        source.appendTo(startIndex, endIndex, destination);
    }

    private boolean containsAtIndex(int index, String content) {
        if (index + content.length() > source.length()) {
            return false;
        }
        for (int i = 0, j = index; i < content.length(); i++, j++) {
            if (content.charAt(i) != source.charAt(j)) {
                return false;
            }
        }
        return true;
    }

    void appendRemainder() throws IOException {
        textIndex = source.appendRemainder(textIndex, textEnd, destination);
    }

    private static final class ObfuscatedElement {

        private final ElementConfig config;
        private int depth;

        private ObfuscatedElement(ElementConfig config) {
            this.config = config;
            this.depth = 0;
        }
    }
}
