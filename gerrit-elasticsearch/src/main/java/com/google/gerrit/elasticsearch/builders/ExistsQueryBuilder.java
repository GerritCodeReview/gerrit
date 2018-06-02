// Copyright (C) 2018 The Android Open Source Project, 2009-2015 Elasticsearch
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

import java.io.IOException;

/**
 * Constructs a query that only match on documents that the field has a value in them.
 *
 * <p>A trimmed down version of org.elasticsearch.index.query.ExistsQueryBuilder.
 */
class ExistsQueryBuilder extends QueryBuilder {

  private final String name;

  ExistsQueryBuilder(String name) {
    this.name = name;
  }

  @Override
  protected void doXContent(XContentBuilder builder) throws IOException {
    builder.startObject("exists");
    builder.field("field", name);
    builder.endObject();
  }
}
