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

import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.index.Index;
import com.google.gerrit.server.config.IndexResource;
import com.google.gerrit.server.restapi.config.SnapshotIndex.Input;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.time.Instant;
import java.util.Collection;

@RequiresCapability(MAINTAIN_SERVER)
@Singleton
public class SnapshotIndex implements RestModifyView<IndexResource, Input> {

  @Override
  public Response<String> apply(IndexResource rsrc, Input input) throws IOException {
    Collection<Index<?, ?>> indexes = rsrc.getIndexes();
    for (Index<?, ?> index : indexes) {
      try {
        index.snapshot(input.getId());
      } catch (FileAlreadyExistsException e) {
        return Response.withStatusCode(409, "Snapshot with same ID already exists.");
      }
    }
    return Response.ok();
  }

  public static class Input {
    private String id;

    public String getId() {
      return id == null ? String.valueOf(Instant.now().getEpochSecond()) : id;
    }
  }
}
