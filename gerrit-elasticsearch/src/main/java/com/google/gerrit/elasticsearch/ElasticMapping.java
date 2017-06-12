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
