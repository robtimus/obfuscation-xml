# obfuscation-xml
[![Maven Central](https://img.shields.io/maven-central/v/com.github.robtimus/obfuscation-xml)](https://search.maven.org/artifact/com.github.robtimus/obfuscation-xml)
[![Build Status](https://github.com/robtimus/obfuscation-xml/actions/workflows/build.yml/badge.svg)](https://github.com/robtimus/obfuscation-xml/actions/workflows/build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Aobfuscation-xml&metric=alert_status)](https://sonarcloud.io/summary/overall?id=com.github.robtimus%3Aobfuscation-xml)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Aobfuscation-xml&metric=coverage)](https://sonarcloud.io/summary/overall?id=com.github.robtimus%3Aobfuscation-xml)
[![Known Vulnerabilities](https://snyk.io/test/github/robtimus/obfuscation-xml/badge.svg)](https://snyk.io/test/github/robtimus/obfuscation-xml)

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

## Obfuscation of attributes

If needed, the values of attributes can be obfuscated as well as text. For example:

    Obfuscator obfuscator = XMLObfuscator.builder()
            // obfuscate any "password" attribute
            .withAttribute("password", Obfuscator.fixedLength(3))
            // only obfuscate "username" attributes of "request" elements
            // the Obfuscator.none() will be applied (so no obfuscation) for any other occurrence
            .withAttribute("username", Obfuscator.none())
                .forElement("request", Obfuscator.fixedLength(3))
            .build();

Note that if attributes need to be obfuscated, XML obfuscators perform obfuscating by generating new, obfuscated XML documents. The resulting obfuscated XML documents may slightly differ from the original.

## Handling malformed XML

If malformed XML is encountered, obfuscation aborts. It will add a message to the result indicating that obfuscation was aborted. This message can be changed or turned off when creating XML obfuscators:

    Obfuscator obfuscator = XMLObfuscator.builder()
            .withElement("password", Obfuscator.fixedLength(3))
            // use null to turn it off
            .withMalformedXMLWarning("<invalid XML>")
            .build();
