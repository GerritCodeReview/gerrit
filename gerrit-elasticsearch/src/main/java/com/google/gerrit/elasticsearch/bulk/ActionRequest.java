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

import com.google.gerrit.elasticsearch.ElasticQueryAdapter;
import com.google.gson.JsonObject;

abstract class ActionRequest extends BulkRequest {

  private final String action;
  private final String id;
  private final String index;
  private final String type;
  private final ElasticQueryAdapter adapter;

  protected ActionRequest(
      String action, String id, String index, String type, ElasticQueryAdapter adapter) {
    this.action = action;
    this.id = id;
    this.index = index;
    this.type = type;
    this.adapter = adapter;
  }

  @Override
  protected String getRequest() {
    JsonObject properties = new JsonObject();
    properties.addProperty("_id", id);
    properties.addProperty("_index", index);
    adapter.setType(properties, type);

    JsonObject jsonAction = new JsonObject();
    jsonAction.add(action, properties);
    return jsonAction.toString() + System.lineSeparator();
  }
}
