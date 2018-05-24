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

import com.google.gerrit.elasticsearch.bulk.DeleteRequest;
import com.google.gerrit.elasticsearch.bulk.IndexRequest;
import com.google.gerrit.elasticsearch.bulk.UpdateRequest;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.Schema;

class ElasticBulkRequest<V> {

  private final FillArgs fillArgs;
  private final String index;
  private final Schema<V> schema;

  ElasticBulkRequest(FillArgs fillArgs, String index, Schema<V> schema) {
    this.fillArgs = fillArgs;
    this.index = index;
    this.schema = schema;
  }

  String deleteRequest(String type, String id) {
    return new DeleteRequest(id, index, type).getRequest();
  }

  String indexRequest(String type, String id) {
    return new IndexRequest(id, index, type).getRequest();
  }

  String updateRequest(V v) {
    return new UpdateRequest<>(fillArgs, schema, v).getRequest();
  }
}
