# obfuscation-xml

Provides functionality for obfuscating XML documents. This can be useful for logging such documents, e.g. as part of request/response logging, where sensitive content like passwords should not be logged as-is.

To create an XML obfuscator, simply create a builder, add elements to it, and let it build the final obfuscator:

    Obfuscator obfuscator = XMLObfuscator.builder()
            .withElement("password", Obfuscator.fixedLength(3))
            .build();

An XML obfuscator will only obfuscate text, and ignore leading and trailing whitespace, including inside CDATA sections. It will never obfuscate element tag names, comments, etc.

## Obfuscation of nested elements

By default, if an obfuscator is configured for an element, it will be used to obfuscate the text of all nested elements as well. This can be turned on or off for all elements, or per element. For example:

    Obfuscator obfuscator = XMLObfuscator.builder()
            .textOnlyByDefault()
            .withElement("password", Obfuscator.fixedLength(3))
            .withElement("complex", Obfuscator.fixedLength(3))
                    .includeNestedElements() // override the default setting
            .build();

## Handling malformed XML

If malformed XML is encountered, obfuscation aborts. It will add a message to the result indicating that obfuscation was aborted. This message can be changed or turned off when creating XML obfuscators:

    Obfuscator obfuscator = XMLObfuscator.builder()
            .withElement("password", Obfuscator.fixedLength(3))
            // use null to turn it off
            .withMalformedXMLWarning("<invalid XML>")
            .build();
