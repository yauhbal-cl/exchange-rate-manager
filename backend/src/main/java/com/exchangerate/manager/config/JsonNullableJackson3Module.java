package com.exchangerate.manager.config;

import org.openapitools.jackson.nullable.JsonNullable;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Spring Boot 4 / Spring Framework 7 moved to Jackson 3 (the {@code tools.jackson.*} artifacts),
 * but {@code org.openapitools:jackson-databind-nullable} — the library openapi-generator's
 * "spring" generator uses for nullable OpenAPI properties (see generated
 * {@code CurrencyUsageEntry#lastQueriedAt}) — only ships a Jackson 2 ({@code com.fasterxml.jackson.*})
 * module. A Jackson 2 {@code Module} bean is invisible to the app's actual Jackson 3
 * {@code ObjectMapper}, so without this class {@link JsonNullable} fields serialize as their raw
 * internal shape ({@code {"present":false}}) instead of a plain {@code null}, breaking the
 * OpenAPI contract for every nullable response field.
 *
 * <p>This is a minimal Jackson-3-native re-implementation of the same behavior: a present-but-null
 * or undefined {@link JsonNullable} always serializes as JSON {@code null} (never omitted — the
 * field stays in the payload, matching the OpenAPI contract's nullable-not-optional semantics),
 * and on read, an explicit JSON {@code null} or a missing property both become
 * {@link JsonNullable#of(Object) JsonNullable.of(null)} / {@link JsonNullable#undefined()}
 * respectively.
 */
public class JsonNullableJackson3Module extends SimpleModule {

    public JsonNullableJackson3Module() {
        super(JsonNullableJackson3Module.class.getName());
        addSerializer(JsonNullable.class, new Serializer());
        addDeserializer(JsonNullable.class, new Deserializer());
    }

    @SuppressWarnings("rawtypes")
    private static final class Serializer extends ValueSerializer<JsonNullable> {

        private final JavaType contentType;
        private final ValueSerializer<Object> contentSerializer;

        Serializer() {
            this(null, null);
        }

        private Serializer(JavaType contentType, ValueSerializer<Object> contentSerializer) {
            this.contentType = contentType;
            this.contentSerializer = contentSerializer;
        }

        @Override
        public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
            JavaType wrapperType = property != null
                    ? property.getType()
                    : ctxt.constructType(JsonNullable.class);
            JavaType resolvedContentType = wrapperType.containedTypeCount() > 0
                    ? wrapperType.containedType(0)
                    : ctxt.constructType(Object.class);
            ValueSerializer<Object> resolvedContentSerializer =
                    ctxt.findContentValueSerializer(resolvedContentType, property);
            return new Serializer(resolvedContentType, resolvedContentSerializer);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void serialize(JsonNullable value, JsonGenerator gen, SerializationContext ctxt) {
            Object contained = value.isPresent() ? value.get() : null;
            if (contained == null) {
                gen.writeNull();
            } else if (contentSerializer != null) {
                contentSerializer.serialize(contained, gen, ctxt);
            } else {
                ctxt.writeValue(gen, contained);
            }
        }

        @Override
        public boolean isEmpty(SerializationContext ctxt, JsonNullable value) {
            return false;
        }
    }

    @SuppressWarnings("rawtypes")
    private static final class Deserializer extends ValueDeserializer<JsonNullable> {

        private final ValueDeserializer<?> contentDeserializer;

        Deserializer() {
            this(null);
        }

        private Deserializer(ValueDeserializer<?> contentDeserializer) {
            this.contentDeserializer = contentDeserializer;
        }

        @Override
        public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
            JavaType wrapperType = property != null
                    ? property.getType()
                    : ctxt.constructType(JsonNullable.class);
            JavaType resolvedContentType = wrapperType.containedTypeCount() > 0
                    ? wrapperType.containedType(0)
                    : ctxt.constructType(Object.class);
            ValueDeserializer<Object> resolvedContentDeserializer =
                    ctxt.findContextualValueDeserializer(resolvedContentType, property);
            return new Deserializer(resolvedContentDeserializer);
        }

        @Override
        public JsonNullable deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            Object contained = contentDeserializer != null
                    ? contentDeserializer.deserialize(p, ctxt)
                    : ctxt.readValue(p, Object.class);
            return JsonNullable.of(contained);
        }

        @Override
        public Object getAbsentValue(DeserializationContext ctxt) {
            return JsonNullable.undefined();
        }

        @Override
        public JsonNullable getNullValue(DeserializationContext ctxt) {
            return JsonNullable.of(null);
        }

        @Override
        public Object getEmptyValue(DeserializationContext ctxt) {
            return JsonNullable.undefined();
        }
    }
}
