package com.flashticket.payment_service.dto.common;

import com.flashticket.payment_service.dto.message.PromotionDiscount;
import jakarta.validation.Validator;
import java.lang.reflect.TypeVariable;
import java.time.Instant;
import java.util.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** Strict contract boundary, independent of the application's general-purpose JSON mapper. */
public final class ContractCodec {
  private final JsonMapper mapper;
  private final Validator validator;

  public ContractCodec(JsonMapper mapper, Validator validator) {
    this.mapper =
        mapper
            .rebuild()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .build();
    this.validator = Objects.requireNonNull(validator);
  }

  public <T> T read(String json, Class<T> type) {
    return decode(json, mapper.constructType(type));
  }

  public <T> T read(String json, TypeReference<T> type) {
    return decode(json, mapper.constructType(type));
  }

  private <T> T decode(String json, JavaType type) {
    try {
      return convert(mapper.readTree(json), type);
    } catch (ContractValidationException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw invalid("$", "INVALID_JSON");
    }
  }

  public String write(Object value) {
    validate(value);
    return mapper.writeValueAsString(value);
  }

  public JsonNode toTree(Object value) {
    validate(value);
    return mapper.valueToTree(value);
  }

  public <T> T fromTree(JsonNode node, Class<T> type) {
    return convert(node, mapper.constructType(type));
  }

  public <T> T fromTree(JsonNode node, TypeReference<T> type) {
    return convert(node, mapper.constructType(type));
  }

  private <T> T convert(JsonNode node, JavaType type) {
    try {
      if (node == null || node.isNull()) throw invalid("$", "REQUIRED");
      shape(node, type, "$", Set.of());
      T value = mapper.treeToValue(node, type);
      validate(value);
      return value;
    } catch (ContractValidationException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw invalid("$", "INVALID_VALUE");
    }
  }

  private void validate(Object value) {
    if (value == null) throw invalid("$", "REQUIRED");
    var errors = validator.validate(value);
    if (!errors.isEmpty()) {
      var error =
          errors.stream()
              .min(Comparator.comparing(e -> e.getPropertyPath().toString()))
              .orElseThrow();
      throw invalid(error.getPropertyPath().toString(), "CONSTRAINT_VIOLATION");
    }
  }

  private void shape(JsonNode node, JavaType type, String path, Set<String> additional) {
    if (node.isNull()) return;
    Class<?> raw = type.getRawClass();
    if (raw == String.class) {
      require(node.isString(), path);
      return;
    }
    if (raw == Instant.class) {
      require(node.isString() && node.asString().endsWith("Z"), path);
      try {
        Instant.parse(node.asString());
      } catch (java.time.format.DateTimeParseException ex) {
        throw invalid(path, "INVALID_VALUE");
      }
      return;
    }
    if (raw == UUID.class) {
      require(
          node.isString()
              && node.asString()
                  .matches(
                      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"),
          path);
      return;
    }
    if (raw == Integer.class || raw == int.class) {
      require(node.isIntegralNumber() && node.canConvertToInt(), path);
      return;
    }
    if (raw == Boolean.class || raw == boolean.class) {
      require(node.isBoolean(), path);
      return;
    }
    if (raw.isEnum()) {
      require(node.isString(), path);
      boolean known =
          Arrays.stream(raw.getEnumConstants())
              .anyMatch(value -> ((Enum<?>) value).name().equals(node.asString()));
      if (!known) throw invalid(path, "INVALID_VALUE");
      return;
    }
    if (type.isCollectionLikeType()) {
      require(node.isArray(), path);
      int i = 0;
      for (var element : node) {
        if (element.isNull()) throw invalid(path + "[" + i + "]", "REQUIRED");
        shape(element, type.getContentType(), path + "[" + i++ + "]", Set.of());
      }
      return;
    }
    if (raw == PromotionDiscount.class) {
      require(node.isObject() && node.has("kind") && node.get("kind").isString(), path);
      Class<?> subtype =
          switch (node.get("kind").asString()) {
            case "FIXED" -> PromotionDiscount.Fixed.class;
            case "PERCENT" -> PromotionDiscount.Percent.class;
            default -> throw invalid(path + ".kind", "INVALID_KIND");
          };
      shape(node, mapper.constructType(subtype), path, Set.of("kind"));
      return;
    }
    require(raw.isRecord() && node.isObject(), path);
    var names = new HashSet<>(additional);
    for (var component : raw.getRecordComponents()) {
      String name = component.getName();
      names.add(name);
      if (!node.has(name)) throw invalid(path + "." + name, "REQUIRED_FIELD");
      JavaType child =
          component.getGenericType() instanceof TypeVariable<?> variable
              ? type.getBindings().findBoundType(variable.getName())
              : mapper.constructType(component.getGenericType());
      if (child == null) throw invalid(path + "." + name, "UNTYPED_DATA");
      shape(node.get(name), child, path + "." + name, Set.of());
    }
    for (String name : node.propertyNames())
      if (!names.contains(name)) throw invalid(path + "." + name, "UNKNOWN_FIELD");
  }

  private static void require(boolean condition, String path) {
    if (!condition) throw invalid(path, "INVALID_TYPE");
  }

  private static ContractValidationException invalid(String field, String code) {
    return new ContractValidationException(field, code);
  }
}
