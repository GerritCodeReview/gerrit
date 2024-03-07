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

import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.config.IndexResource;
import com.google.gerrit.server.config.IndexVersionResource;
import com.google.gerrit.server.restapi.config.IndexInfo.IndexVersionInfo;

public class GetIndexVersion implements RestReadView<IndexVersionResource> {

  @Override
  public Response<IndexVersionInfo> apply(IndexVersionResource rsrc)
      throws ResourceNotFoundException {
    IndexResource indexRsrc = rsrc.getIndexResource();
    IndexInfo indexInfo =
        IndexInfo.fromIndexCollection(
            indexRsrc.getName(), Iterables.getOnlyElement(indexRsrc.getIndexCollections()));
    IndexVersionInfo indexVersionInfo = indexInfo.getVersions().get(rsrc.getVersion());
    if (indexVersionInfo != null) {
      return Response.ok(indexVersionInfo);
    }
    throw new ResourceNotFoundException();
  }
}
