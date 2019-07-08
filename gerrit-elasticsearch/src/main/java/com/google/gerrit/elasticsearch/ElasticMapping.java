// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.elasticsearch;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.Schema;
import java.util.Map;

class ElasticMapping {
  static MappingProperties createMapping(Schema<?> schema, ElasticQueryAdapter adapter) {
    ElasticMapping.Builder mapping = new ElasticMapping.Builder(adapter);
    for (FieldDef<?, ?> field : schema.getFields().values()) {
      String name = field.getName();
      FieldType<?> fieldType = field.getType();
      if (fieldType == FieldType.EXACT) {
        mapping.addExactField(name);
      } else if (fieldType == FieldType.TIMESTAMP) {
        mapping.addTimestamp(name);
      } else if (fieldType == FieldType.INTEGER
          || fieldType == FieldType.INTEGER_RANGE
          || fieldType == FieldType.LONG) {
        mapping.addNumber(name);
      } else if (fieldType == FieldType.FULL_TEXT) {
        mapping.addStringWithAnalyzer(name);
      } else if (fieldType == FieldType.PREFIX || fieldType == FieldType.STORED_ONLY) {
        mapping.addString(name);
      } else {
        throw new IllegalStateException("Unsupported field type: " + fieldType.getName());
      }
    }
    return mapping.build();
  }

  static class Builder {
    private final ElasticQueryAdapter adapter;
    private final ImmutableMap.Builder<String, FieldProperties> fields =
        new ImmutableMap.Builder<>();

    Builder(ElasticQueryAdapter adapter) {
      this.adapter = adapter;
    }

    MappingProperties build() {
      MappingProperties properties = new MappingProperties();
      properties.properties = fields.build();
      return properties;
    }

    Builder addExactField(String name) {
      FieldProperties key = new FieldProperties(adapter.exactFieldType());
      key.index = adapter.indexProperty();
      FieldProperties properties;
      properties = new FieldProperties(adapter.exactFieldType());
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
      fields.put(name, new FieldProperties(adapter.stringFieldType()));
      return this;
    }

    Builder addStringWithAnalyzer(String name) {
      FieldProperties key = new FieldProperties(adapter.stringFieldType());
      key.analyzer = "custom_with_char_filter";
      fields.put(name, key);
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
    String analyzer;
    Map<String, FieldProperties> fields;

    FieldProperties(String type) {
      this.type = type;
    }
  }
}
