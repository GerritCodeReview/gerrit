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
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.index.Index;
import com.google.gerrit.server.config.IndexVersionResource;
import com.google.gerrit.server.restapi.config.SnapshotIndexVersion.Input;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@RequiresCapability(MAINTAIN_SERVER)
@Singleton
public class SnapshotIndexVersion implements RestModifyView<IndexVersionResource, Input> {
  private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

  @Override
  public Response<?> apply(IndexVersionResource rsrc, Input input)
      throws IOException, ResourceNotFoundException {
    String id = input.id;
    if (id == null) {
      id = LocalDateTime.now(ZoneId.systemDefault()).format(formatter);
    }
    Index<?, ?> index = rsrc.getIndex();
    var unused = index.snapshot(id);
    SnapshotInfo info = new SnapshotInfo();
    info.id = id;
    return Response.ok(info);
  }

  public static class Input {
    String id;
  }
}
