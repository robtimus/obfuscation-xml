/*
 * WritingObfuscatingXMLParserTest.java
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

import static com.github.robtimus.obfuscation.xml.WritingObfuscatingXMLParser.getDTD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import org.codehaus.stax2.XMLStreamReader2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.ctc.wstx.api.WstxInputProperties;

@SuppressWarnings("nls")
class WritingObfuscatingXMLParserTest {

    @Nested
    @DisplayName("getDTD")
    class GetDTD {

        // Examples taken from https://en.wikipedia.org/wiki/Document_type_definition

        @Test
        @DisplayName("with public id and system id")
        void testWithPublicIdAndSystemId() throws XMLStreamException {
            String xml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
                    "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    </html>""";

            // Allow http DTDs and use an XMLResolver that returns null, to allow external access
            XMLStreamReader2 xmlStreamReader = createXmlStreamReader(xml, "http", (publicID, systemID, baseURI, namespace) -> null);
            skipToDTD(xmlStreamReader);

            String dtd = getDTD(xmlStreamReader);

            String expected = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" "
                    + "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">";

            assertEquals(expected, dtd);
        }

        @Test
        @DisplayName("with public id, system id and internal")
        void testWithPublicIdAndSystemIdAndInternal() throws XMLStreamException {
            String xml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
                    "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd" [
                      <!-- an internal subset can be embedded here -->
                    ]>\
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    </html>""";

            // Allow http DTDs and use an XMLResolver that returns null, to allow external access
            XMLStreamReader2 xmlStreamReader = createXmlStreamReader(xml, "http", (publicID, systemID, baseURI, namespace) -> null);
            skipToDTD(xmlStreamReader);

            String dtd = getDTD(xmlStreamReader);

            String expected = """
                    <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" \
                    "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd" [
                      <!-- an internal subset can be embedded here -->
                    ]>""";

            assertEquals(expected, dtd);
        }

        @Test
        @DisplayName("with internal")
        void testWithInternal() throws XMLStreamException {
            String xml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!DOCTYPE html [
                      <!-- an internal subset can be embedded here -->
                    ]>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    </html>""";

            XMLStreamReader2 xmlStreamReader = createXmlStreamReader(xml);
            skipToDTD(xmlStreamReader);

            String dtd = getDTD(xmlStreamReader);

            String expected = """
                    <!DOCTYPE html [
                      <!-- an internal subset can be embedded here -->
                    ]>""";

            assertEquals(expected, dtd);
        }

        @Test
        @DisplayName("minimal")
        void testMinimal() throws XMLStreamException {
            String xml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!DOCTYPE html>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    </html>""";

            XMLStreamReader2 xmlStreamReader = createXmlStreamReader(xml);
            skipToDTD(xmlStreamReader);

            String dtd = getDTD(xmlStreamReader);

            String expected = "<!DOCTYPE html>";

            assertEquals(expected, dtd);
        }

        @Test
        @DisplayName("system id")
        void testWithSystemId() throws XMLStreamException {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                    <!DOCTYPE people_list SYSTEM "example.dtd">
                    <people_list />""";

            String dtdContents = "<!ELEMENT people_list EMPTY>";

            XMLResolver dtdResolver = (publicID, systemID, baseURI, namespace) -> new StringReader(dtdContents);

            XMLStreamReader2 xmlStreamReader = createXmlStreamReader(xml, null, dtdResolver);
            skipToDTD(xmlStreamReader);

            String dtd = getDTD(xmlStreamReader);

            String expected = "<!DOCTYPE people_list SYSTEM \"example.dtd\">";

            assertEquals(expected, dtd);
        }

        private XMLStreamReader2 createXmlStreamReader(String xml) throws XMLStreamException {
            return createXmlStreamReader(xml, null, null);
        }

        private XMLStreamReader2 createXmlStreamReader(String xml, String externalDtdSchemas, XMLResolver dtdResolver) throws XMLStreamException {
            XMLInputFactory inputFactory = XMLObfuscator.createInputFactory();
            if (externalDtdSchemas != null) {
                XMLObfuscator.setPropertyIfSupported(inputFactory, XMLConstants.ACCESS_EXTERNAL_DTD, externalDtdSchemas);
            }
            inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, true);
            if (dtdResolver != null) {
                inputFactory.setProperty(WstxInputProperties.P_DTD_RESOLVER, dtdResolver);
            }
            return (XMLStreamReader2) inputFactory.createXMLStreamReader(new StringReader(xml));
        }

        private void skipToDTD(XMLStreamReader2 xmlStreamReader) throws XMLStreamException {
            while (xmlStreamReader.getEventType() != XMLStreamConstants.DTD && xmlStreamReader.hasNext()) {
                xmlStreamReader.next();
            }
            assertEquals(XMLStreamConstants.DTD, xmlStreamReader.getEventType());
        }
    }
}
