// Copyright (C) 2025 The Android Open Source Project
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

import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.server.config.IndexVersionResource;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.CleanupChangesIndex;
import com.google.inject.Inject;

public class CleanupIndexVersion implements RestModifyView<IndexVersionResource, Input> {

  private final CleanupChangesIndex cleanup;

  @Inject
  CleanupIndexVersion(CleanupChangesIndex cleanup) {
    this.cleanup = cleanup;
  }

  @Override
  public Response<?> apply(IndexVersionResource rsrc, Input input)
      throws ResourceNotFoundException, MethodNotAllowedException {
    IndexDefinition<?, ?, ?> def = rsrc.getIndexDefinition();
    int version = rsrc.getIndex().getSchema().getVersion();
    Index<?, ?> index = def.getIndexCollection().getWriteIndex(version);
    if (!(index instanceof ChangeIndex)) {
      throw new MethodNotAllowedException(
          String.format("Cleanup is not supported for the %s index", def.getName()));
    }
    var unused = cleanup.cleanupAsync((ChangeIndex) index);
    return Response.accepted(
        String.format("Cleanup of %s index version %d submitted", def.getName(), version));
  }
}
