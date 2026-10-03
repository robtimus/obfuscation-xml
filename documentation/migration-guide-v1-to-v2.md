# Migrating from version 1.x to 2.0

## XMLObfuscator.Builder

`XMLObfuscator.Builder` is no longer an interface but instead a final class. If you are creating mocks or implementing it directly you need to use actual instances created through `XMLObfucsator.builder()`.

### withElement

`XMLObfuscator.Builder.withElement` no longer returns an `ElementConfigurer`. Instead it is overloaded to take a `Consumer<LocalNameElementConfigurer>` or a `Consumer<QNameElementConfigurer>`, depending on which overload is used. If you called any `ElementConfigurer` methods you need to provide a lambda instead. For example:

```java
/* old:
XMLObfuscator.builder()
        .withElement("foo", obfuscator)
                .forNestedElements(ObfucsationMode.INHERIT)
 */
XMLObfuscator.builder()
        .withElement("foo", obfuscator, element -> element
                .forNestedElements(ObfucsationMode.INHERIT))
```

#### Case sensitivity

`XMLObfuscator.Builder.withElement` no longer accepts a `CaseSensitivity` argument. You need to use methods `caseSensitive()` and `caseInsensitive()` of new class `LocalNameElementConfigurer` instead. For example:

```java
/* old:
XMLObfuscator.builder()
        .withElement("foo", obfuscator, CaseSensitivity.CASE_INSENSITIVE)
 */
XMLObfuscator.builder()
        .withElement("foo", obfuscator, LocalNameElementConfigurer::caseInsensitive)
```

### withAttribute

`XMLObfuscator.Builder.withAttribute` no longer returns an `AttributeConfigurer`. Instead it is overloaded to take a `Consumer<LocalNameAttributeConfigurer>` or a `Consumer<QNameAttributeConfigurer>`, depending on which overload is used. If you called any `AttributeConfigurer` methods you need to provide a lambda instead. For example:

```java
/* old:
XMLObfuscator.builder()
        .withAttribute("foo", obfuscator)
                .forElement("bar", obfuscator2)
 */
XMLObfuscator.builder()
        .withAttribute("foo", obfuscator, attribute -> attribute
                .forElement("bar", obfuscator2))
```

#### Case sensitivity

`XMLObfuscator.Builder.withAttribute` no longer accepts a `CaseSensitivity` argument. You need to use methods `caseSensitive()` and `caseInsensitive()` of new class `LocalNameAtributeConfigurer` instead. For example:

```java
/* old:
XMLObfuscator.builder()
        .withAttribute("foo", obfuscator, CaseSensitivity.CASE_INSENSITIVE)
 */
XMLObfuscator.builder()
        .withAttribute("foo", obfuscator, LocalNameAttributeConfigurer::caseInsensitive)
```

### textOnlyByDefault, excludeNestedElementsByDefault, allByDefault

`XMLObfuscator.Builder.textOnlyByDefault`, `XMLObfuscator.Builder.excludeNestedElementsByDefault` and `XMLObfuscator.Builder.allByDefault` have been removed. You need to use new method `withContentTypesByDefault` instead. For example:

```java
/*
XMLObfuscator.builder()
        .textOnlyByDefault()
 */
XMLObfuscator.builder()
        .withContentTypesByDefault(ContentType.TEXT)
```

```java
/*
XMLObfuscator.builder()
        .excludeNestedElementsByDefault()
 */
XMLObfuscator.builder()
        .withContentTypesByDefault(ContentType.TEXT)
```

```java
/*
XMLObfuscator.builder()
        .allByDefault()
 */
XMLObfuscator.builder()
        .withContentTypesByDefault(ContentType.ALL)
```

### includeNestedElementsByDefault

`XMLObfuscator.Builder.includeNestedElementsByDefault` has been removed. You need to combine methods `forNestedElementsByDefault` with new method `withContentTypesByDefault` instead. For example:

```java
/*
XMLObfuscator.builder()
        .includeNestedElementsByDefault()
 */
XMLObfuscator.builder()
        .withContentTypesByDefault(ContentType.ALL)
        .forNestedElementsByDefault(ObfuscationMode.INHERIT)
```

Note that the defaults already use `ContentType.ALL` and `ObfuscationMode.INHERIT`.

### limitTo

`XMLObfuscator.Builder.limitTo` no longer returns a `LimitConfigurer`. Instead it is overloaded to take a `Consumer<LimitConfigurer>`. If you called any `LimitConfigurer` methods you need to provide a lambda instead. For example:

```java
/* old:
XMLObfuscator.builder()
        .limitTo(1024)
                .withTruncatedIndicator("<truncated>")
 */
XMLObfuscator.builder()
        .limitTo(1024, limit -> limit
                .withTruncatedIndicator("<truncated>"))
```

## XMLObfuscator.ElementConfigurer

`XMLObfuscator.ElementConfigurer` is no longer an interface but instead an abstract sealed class with subclasses `XMLObfuscator.LocalNameElementConfigurer` and `XMLObfuscator.QNameElementConfigurer`. If you are creating mocks or implementing it directly you need to use actual instances passed to the `Consumer` argument of `XMLObfuscator.Builder.withElement`.

### textOnly, excludeNestedElements, all

`XMLObfuscator.ElementConfigurer.textOnly`, `XMLObfuscator.ElementConfigurer.excludeNestedElements` and `XMLObfuscator.ElementConfigurer.all` have been removed. You need to use new method `withContentTypes` instead. For example:

```java
/*
XMLObfuscator.builder()
        .withElement("foo", obfuscator)
                .textOnlyByDefault()
 */
XMLObfuscator.builder()
        .withElement("foo", obfuscator, element -> element
                .withContentTypes(ContentType.TEXT))
```

```java
/*
XMLObfuscator.builder()
        .withElement("foo", obfuscator)
                .excludeNestedElements()
 */
XMLObfuscator.builder()
        .withElement("foo", obfuscator, element -> element
                .withContentTypes(ContentType.TEXT))
```

```java
/*
XMLObfuscator.builder()
        .withElement("foo", obfuscator)
               .all()
 */
XMLObfuscator.builder()
        .withElement("foo", obfuscator, element -> element
                .withContentTypes(ContentType.ALL))
```

### includeNestedElements

`XMLObfuscator.ElementConfigurer.includeNestedElements` has been removed. You need to combine methods `forNestedElements` with new method `withContentTypes` instead. For example:

```java
/*
XMLObfuscator.builder()
        .withElement("foo", obfuscator)
                .includeNestedElements())
 */
XMLObfuscator.builder()
        .withElement("foo", obfuscator, element -> element
                .withContentTypes(ContentType.ALL)
                .forNestedElements(ObfuscationMode.INHERIT))
```

## XMLObfuscator.AttributeConfigurer

`XMLObfuscator.AttributeConfigurer` is no longer an interface but instead an abstract sealed class with subclasses `XMLObfuscator.LocalNameAttributeConfigurer` and `XMLObfuscator.QNameAttributeConfigurer`. If you are creating mocks or implementing it directly you need to use actual instances passed to the `Consumer` argument of `XMLObfuscator.Builder.withAttribute`.

### forElement case sensitivity

`XMLObfuscator.AttributeConfigurer.forElement` no longer accepts a `CaseSensitivity` argument. You need to use methods `caseSensitive()` and `caseInsensitive()` of new class `LocalNameAttributeElementConfigurer` instead. For example:

```java
/* old:
XMLObfuscator.builder()
        .withAttribute("foo", obfuscator)
                .forElement("bar", obfuscator2, CaseSensitivity.CASE_INSENSITIVE)
 */
XMLObfuscator.builder()
        .withAttribute("foo", obfuscator, attribute -> attribute
                .forElement("bar", obfuscator2, LocalNameAttributeElementConfigurer::caseInsensitive))
```

## XMLObfuscator.LimitConfigurer

`XMLObfuscator.LimitConfigurer` is no longer an interface but instead a final class. If you are creating mocks or implementing it directly you need to use actual instances passed to the `Consumer` argument of `XMLObfuscator.Builder.limitTo`.

## XMLObfuscator.ElementConfigurer.ObfuscationMode

### EXCLUDE

Constant `ObfuscationMode.ElementConfigurer.EXCLUDE` has been removed. You need to use new method `withContentTypesByDefault` and/or `withContentTypes` as documented above instead. For example:

```java
/* old
XMLObfuscator.builder()
        ..forNestedElementsByDefault(ObfuscationMode.EXCLUDE)
 */
XMLObfuscator.builder()
        .withContentTypesByDefault(ContentType.TEXT)
```

```java
/*
XMLObfuscator.builder()
        .withElement("foo", obfuscator)
                .forNestedElements(ObfuscationMode.EXCLUDE)
 */
XMLObfuscator.builder()
        .withElement("foo", obfuscator, element -> element
                .withContentTypes(ContentType.TEXT))
```
