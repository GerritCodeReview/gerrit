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

package com.google.gerrit.server.restapi.config;

import static com.google.gerrit.common.data.GlobalCapability.MAINTAIN_SERVER;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.IndexResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@RequiresCapability(MAINTAIN_SERVER)
@Singleton
public class IndexCollection implements ChildCollection<ConfigResource, IndexResource> {
  private final DynamicMap<RestView<IndexResource>> views;
  private final Provider<ListIndexes> list;
  private final Collection<IndexDefinition<?, ?, ?>> defs;

  @Inject
  IndexCollection(
      DynamicMap<RestView<IndexResource>> views,
      Provider<ListIndexes> list,
      Collection<IndexDefinition<?, ?, ?>> defs) {
    this.views = views;
    this.list = list;
    this.defs = defs;
  }

  @Override
  public IndexResource parse(ConfigResource parent, IdString id) throws ResourceNotFoundException {
    if (id.toString().toLowerCase(Locale.US).equals("all")) {
      ImmutableList.Builder<com.google.gerrit.index.IndexCollection<?, ?, ?>> allIndexes =
          ImmutableList.builder();
      for (IndexDefinition<?, ?, ?> def : defs) {
        allIndexes.add(def.getIndexCollection());
      }
      return new IndexResource(allIndexes.build());
    }

    List<String> segments = Splitter.on('~').splitToList(id.toString());
    if (segments.size() < 1 || 2 < segments.size()) {
      throw new ResourceNotFoundException(id);
    }
    String indexName = segments.get(0);
    Integer version = segments.size() == 2 ? Integer.valueOf(segments.get(1)) : null;

    for (IndexDefinition<?, ?, ?> def : defs) {
      if (def.getName().equals(indexName)) {
        return new IndexResource(def.getIndexCollection(), version);
      }
    }
    throw new ResourceNotFoundException("Unknown index requested: " + indexName);
  }

  @Override
  public RestView<ConfigResource> list() throws RestApiException {
    return list.get();
  }

  @Override
  public DynamicMap<RestView<IndexResource>> views() {
    return views;
  }
}
