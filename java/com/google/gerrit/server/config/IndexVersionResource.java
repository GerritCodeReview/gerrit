// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server.config;

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexDefinition;
import com.google.inject.TypeLiteral;

public class IndexVersionResource implements RestResource {
  public static final TypeLiteral<RestView<IndexVersionResource>> INDEX_VERSION_KIND =
      new TypeLiteral<>() {};

  private final IndexDefinition<?, ?, ?> def;
  private final Index<?, ?> index;

  public IndexVersionResource(IndexDefinition<?, ?, ?> def, Index<?, ?> index) {
    this.def = def;
    this.index = index;
  }

  public IndexDefinition<?, ?, ?> getIndexDefinition() {
    return def;
  }

  public Index<?, ?> getIndex() {
    return index;
  }
}
