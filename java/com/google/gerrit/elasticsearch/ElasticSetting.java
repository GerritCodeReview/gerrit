// Copyright (C) 2018 The Android Open Source Project
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

class ElasticSetting {
  /** The custom char mappings of "." to " " and "_" to " " in the form of UTF-8 */
  private static final ImmutableMap<String, String> CUSTOM_CHAR_MAPPING =
      ImmutableMap.of("\\u002E", "\\u0020", "\\u005F", "\\u0020");

  static SettingProperties createSetting() {
    ElasticSetting.Builder settings = new ElasticSetting.Builder();
    settings.addCharFilter();
    settings.addAnalyzer();
    return settings.build();
  }

  static class Builder {
    private final ImmutableMap.Builder<String, FieldProperties> fields =
        new ImmutableMap.Builder<>();

    SettingProperties build() {
      SettingProperties properties = new SettingProperties();
      properties.analysis = fields.build();
      return properties;
    }

    void addCharFilter() {
      FieldProperties charMapping = new FieldProperties("mapping");
      charMapping.mappings = getCustomCharMappings(CUSTOM_CHAR_MAPPING);

      FieldProperties charFilter = new FieldProperties();
      charFilter.customMapping = charMapping;
      fields.put("char_filter", charFilter);
    }

    void addAnalyzer() {
      FieldProperties customAnalyzer = new FieldProperties("custom");
      customAnalyzer.tokenizer = "standard";
      customAnalyzer.charFilter = new String[] {"custom_mapping"};
      customAnalyzer.filter = new String[] {"lowercase"};

      FieldProperties analyzer = new FieldProperties();
      analyzer.customWithCharFilter = customAnalyzer;
      fields.put("analyzer", analyzer);
    }

    private static String[] getCustomCharMappings(ImmutableMap<String, String> map) {
      int mappingIndex = 0;
      int numOfMappings = map.size();
      String[] mapping = new String[numOfMappings];
      for (Map.Entry<String, String> e : map.entrySet()) {
        mapping[mappingIndex++] = e.getKey() + "=>" + e.getValue();
      }
      return mapping;
    }
  }

  static class SettingProperties {
    Map<String, FieldProperties> analysis;
  }

  static class FieldProperties {
    String tokenizer;
    String type;
    String[] charFilter;
    String[] filter;
    String[] mappings;
    FieldProperties customMapping;
    FieldProperties customWithCharFilter;

    FieldProperties() {}

    FieldProperties(String type) {
      this.type = type;
    }
  }
}
