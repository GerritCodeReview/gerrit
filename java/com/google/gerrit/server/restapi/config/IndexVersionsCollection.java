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

package com.google.gerrit.server.restapi.config;

import static com.google.gerrit.common.data.GlobalCapability.MAINTAIN_SERVER;

import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexCollection;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.server.config.IndexResource;
import com.google.gerrit.server.config.IndexVersionResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@RequiresCapability(MAINTAIN_SERVER)
@Singleton
public class IndexVersionsCollection
    implements ChildCollection<IndexResource, IndexVersionResource> {

  private final DynamicMap<RestView<IndexVersionResource>> views;
  private final Provider<ListIndexVersions> list;

  @Inject
  IndexVersionsCollection(
      DynamicMap<RestView<IndexVersionResource>> views, Provider<ListIndexVersions> list) {
    this.views = views;
    this.list = list;
  }

  @Override
  public RestView<IndexResource> list() throws RestApiException {
    return list.get();
  }

  @Override
  public IndexVersionResource parse(IndexResource parent, IdString id)
      throws ResourceNotFoundException, Exception {
    try {
      int version = Integer.parseInt(id.get());
      IndexDefinition<?, ?, ?> def = parent.getIndexDefinition();
      IndexCollection<?, ?, ?> indexCollection = def.getIndexCollection();
      Index<?, ?> index = indexCollection.getWriteIndex(version);
      if (index == null) {
        Index<?, ?> searchIndex = indexCollection.getSearchIndex();
        if (searchIndex.getSchema().getVersion() == version) {
          index = searchIndex;
        }
      }
      if (index != null) {
        return new IndexVersionResource(def, index);
      }
    } catch (NumberFormatException e) {
      throw new ResourceNotFoundException("'" + id.get() + "' is not a number", e);
    }

    throw new ResourceNotFoundException();
  }

  @Override
  public DynamicMap<RestView<IndexVersionResource>> views() {
    return views;
  }
}
