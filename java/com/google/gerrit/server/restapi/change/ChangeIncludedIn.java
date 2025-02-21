// Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.extensions.api.changes.IncludedInInfo;
import com.google.gerrit.extensions.config.ExternalChangeIncludedIn;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.IncludedIn;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class ChangeIncludedIn implements RestReadView<ChangeResource> {
  private final IncludedIn includedIn;
  private final PluginSetContext<ExternalChangeIncludedIn> externalChangeIncludedIn;
  private final PatchSetUtil psUtil;

  @Inject
  ChangeIncludedIn(
      IncludedIn includedIn,
      PluginSetContext<ExternalChangeIncludedIn> externalChangeIncludedIn,
      PatchSetUtil psUtil) {
    this.includedIn = includedIn;
    this.externalChangeIncludedIn = externalChangeIncludedIn;
    this.psUtil = psUtil;
  }

  @Override
  public Response<IncludedInInfo> apply(ChangeResource rsrc)
      throws RestApiException, IOException, PermissionBackendException {
    String revisionId = psUtil.current(rsrc.getNotes()).commitId().name();
    IncludedInInfo includedInInfo = includedIn.apply(rsrc.getProject(), revisionId);
    ListMultimap<String, String> external = MultimapBuilder.hashKeys().arrayListValues().build();
    externalChangeIncludedIn.runEach(
        ext -> {
          ListMultimap<String, String> extIncludedIns =
              ext.getIncludedIn(
                  rsrc.getId().get(),
                  rsrc.getProject().get(),
                  revisionId,
                  includedInInfo.tags,
                  includedInInfo.branches);
          if (extIncludedIns != null) {
            external.putAll(extIncludedIns);
          }
        });
    if (!external.isEmpty()) {
      if (includedInInfo.external != null) {
        includedInInfo.external.putAll(external.asMap());
      } else {
        includedInInfo.external = external.asMap();
      }
    }
    return Response.ok(includedInInfo);
  }
}
