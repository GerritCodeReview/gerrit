// Copyright (C) 2022 The Android Open Source Project, 2009-2015 Elasticsearch
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

package com.google.gerrit.elasticsearch.builders;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import java.io.IOException;

/**
 * A trimmed down and modified version of org.elasticsearch.search.searchafter.SearchAfterBuilder.
 */
public final class SearchAfterBuilder {
  private JsonArray sortValues;

  public SearchAfterBuilder(JsonArray sortValues) {
    this.sortValues = sortValues;
  }

  public void innerToXContent(XContentBuilder builder) throws IOException {
    builder.startArray("search_after");
    for (int i = 0; i < sortValues.size(); i++) {
      JsonPrimitive value = sortValues.get(i).getAsJsonPrimitive();
      if (value.isNumber()) {
        builder.value(value.getAsLong());
      } else if (value.isBoolean()) {
        builder.value(value.getAsBoolean());
      } else {
        builder.value(value.getAsString());
      }
    }
    builder.endArray();
  }
}
