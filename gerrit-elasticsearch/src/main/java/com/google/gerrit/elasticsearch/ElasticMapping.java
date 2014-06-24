/*
 * Copyright 2016 CollabNet, Inc. All rights reserved.
 * http://www.collab.net
 */

package com.google.gerrit.elasticsearch;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

class ElasticMapping {
  static class Builder {
    private final ImmutableMap.Builder<String, FieldProperties> fields =
        new ImmutableMap.Builder<>();

    MappingProperties build() {
      MappingProperties properties = new MappingProperties();
      properties.properties = fields.build();
      return properties;
    }

    Builder addExactField(String name) {
      FieldProperties key = new FieldProperties("string");
      key.index = "not_analyzed";
      FieldProperties properties = new FieldProperties("string");
      properties.fields = ImmutableMap.of("key", key);
      fields.put(name, properties);
      return this;
    }

    Builder addTimestamp(String name) {
      FieldProperties properties = new FieldProperties("date");
      properties.type = "date";
      properties.format = "dateOptionalTime";
      fields.put(name, properties);
      return this;
    }

    Builder addNumber(String name) {
      fields.put(name, new FieldProperties("long"));
      return this;
    }

    Builder addString(String name) {
      fields.put(name, new FieldProperties("string"));
      return this;
    }

    Builder add(String name, String type) {
      fields.put(name, new FieldProperties(type));
      return this;
    }
  }

  static class MappingProperties {
    Map<String, FieldProperties> properties;
  }

  static class FieldProperties {
    String type;
    String index;
    String format;
    Map<String, FieldProperties> fields;

    FieldProperties(String type) {
      this.type = type;
    }
  }
}
