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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.change.RevisionJson;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.EnumSet;

/**
 * REST endpoint to get a revision / patch-set of a change.
 *
 * <p>This REST endpoint handles {@code GET
 * /changes/<change-identifier>/revisions/<revision-identifier>} requests.
 */
@Singleton
public class GetRevision implements RestReadView<RevisionResource> {
  private final RevisionJson.Factory json;

  @Inject
  GetRevision(RevisionJson.Factory json) {
    this.json = json;
  }

  @Override
  public Response<RevisionInfo> apply(RevisionResource rsrc)
      throws PatchListNotAvailableException, GpgException, IOException, PermissionBackendException {
    return Response.ok(
        json.create(EnumSet.allOf(ListChangesOption.class))
            .getRevisionInfo(rsrc.getChangeResource().getChangeData(), rsrc.getPatchSet()));
  }
}
