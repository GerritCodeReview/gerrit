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

package com.google.gerrit.elasticsearch.bulk;

import static java.util.stream.Collectors.toList;

import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.gerrit.elasticsearch.builders.XContentBuilder;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.Schema.Values;
import java.io.IOException;

public class UpdateRequest<V> extends BulkRequest {

  private final Schema<V> schema;
  private final V v;

  public UpdateRequest(Schema<V> schema, V v) {
    this.schema = schema;
    this.v = v;
  }

  @Override
  protected String getRequest() {
    try (XContentBuilder closeable = new XContentBuilder()) {
      XContentBuilder builder = closeable.startObject();
      for (Values<V> values : schema.buildFields(v)) {
        String name = values.getField().getName();
        if (values.getField().isRepeatable()) {
          builder.field(name, Streams.stream(values.getValues()).collect(toList()));
        } else {
          Object element = Iterables.getOnlyElement(values.getValues(), "");
          if (shouldAddElement(element)) {
            builder.field(name, element);
          }
        }
      }
      return builder.endObject().string() + System.lineSeparator();
    } catch (IOException e) {
      return e.toString();
    }
  }

  private boolean shouldAddElement(Object element) {
    return !(element instanceof String) || !((String) element).isEmpty();
  }
}
