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

import static java.util.stream.Collectors.toList;

import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.gerrit.elasticsearch.builders.XContentBuilder;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.Schema.Values;
import com.google.gson.JsonObject;
import java.io.IOException;

class ElasticBulkRequest<V> {

  private final FillArgs fillArgs;
  private final String index;
  private final Schema<V> schema;

  ElasticBulkRequest(FillArgs fillArgs, String index, Schema<V> schema) {
    this.fillArgs = fillArgs;
    this.index = index;
    this.schema = schema;
  }

  String toAction(String type, String id, String action) {
    JsonObject properties = new JsonObject();
    properties.addProperty("_id", id);
    properties.addProperty("_index", index);
    properties.addProperty("_type", type);

    JsonObject jsonAction = new JsonObject();
    jsonAction.add(action, properties);
    return jsonAction.toString() + System.lineSeparator();
  }

  String toDoc(V v) throws IOException {
    try (XContentBuilder closeable = new XContentBuilder()) {
      XContentBuilder builder = closeable.startObject();
      for (Values<V> values : schema.buildFields(v, fillArgs)) {
        String name = values.getField().getName();
        if (values.getField().isRepeatable()) {
          builder.field(
              name,
              Streams.stream(values.getValues())
                  .filter(e -> shouldAddElement(e))
                  .collect(toList()));
        } else {
          Object element = Iterables.getOnlyElement(values.getValues(), "");
          if (shouldAddElement(element)) {
            builder.field(name, element);
          }
        }
      }
      return builder.endObject().string() + System.lineSeparator();
    }
  }

  private boolean shouldAddElement(Object element) {
    return !(element instanceof String) || !((String) element).isEmpty();
  }
}
