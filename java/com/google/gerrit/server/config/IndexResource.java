// Copyright (C) 2023 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexCollection;
import com.google.inject.TypeLiteral;
import java.util.Collection;
import java.util.List;

public class IndexResource extends ConfigResource {
  public static final TypeLiteral<RestView<IndexResource>> INDEX_KIND = new TypeLiteral<>() {};

  private final Collection<Index<?, ?>> indexes;

  public IndexResource(IndexCollection<?, ?, ?> indexes, @Nullable Integer version)
      throws ResourceNotFoundException {
    if (version == null) {
      this.indexes = ImmutableList.copyOf(indexes.getWriteIndexes());
    } else {
      Index<?, ?> index = indexes.getWriteIndex(version);
      if (index == null) {
        throw new ResourceNotFoundException(
            String.format("Unknown index version requested: %d", version));
      }
      this.indexes = ImmutableList.of(index);
    }
  }

  public IndexResource(List<IndexCollection<?, ?, ?>> indexes) {
    ImmutableList.Builder<Index<?, ?>> allIndexes = ImmutableList.builder();
    for (IndexCollection<?, ?, ?> index : indexes) {
      allIndexes.addAll(index.getWriteIndexes());
    }
    this.indexes = allIndexes.build();
  }

  public Collection<Index<?, ?>> getIndexes() {
    return indexes;
  }
}
