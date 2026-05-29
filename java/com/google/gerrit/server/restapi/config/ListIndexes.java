// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import java.util.Collection;

@RequiresCapability(MAINTAIN_SERVER)
public class ListIndexes implements RestReadView<ConfigResource> {
  private final Collection<IndexDefinition<?, ?, ?>> defs;

  @Inject
  public ListIndexes(Collection<IndexDefinition<?, ?, ?>> defs) {
    this.defs = defs;
  }

  private ImmutableList<IndexInfo> getIndexInfos() {
    ImmutableList.Builder<IndexInfo> indexInfos = ImmutableList.builder();
    for (IndexDefinition<?, ?, ?> def : defs) {
      indexInfos.add(IndexInfo.fromIndexDefinition(def));
    }
    return indexInfos.build();
  }

  @Override
  public Response<Object> apply(ConfigResource rsrc) {
    return Response.ok(getIndexInfos());
  }
}
