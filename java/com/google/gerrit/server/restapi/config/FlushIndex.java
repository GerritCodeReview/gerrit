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

import static com.google.gerrit.common.data.GlobalCapability.MAINTAIN_SERVER;

import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.server.config.IndexResource;
import com.google.inject.Singleton;
import java.io.IOException;

@RequiresCapability(MAINTAIN_SERVER)
@Singleton
public class FlushIndex implements RestModifyView<IndexResource, Input> {

  @Override
  public Response<?> apply(IndexResource resource, Input input)
      throws AuthException, BadRequestException, ResourceConflictException, IOException {

    IndexDefinition<?, ?, ?> def = resource.getIndexDefinition();
    for (Index<?, ?> index : def.getIndexCollection().getWriteIndexes()) {
      index.flushAndCommit();
    }

    return Response.none();
  }
}
