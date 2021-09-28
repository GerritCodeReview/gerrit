// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import com.google.gerrit.extensions.api.changes.IncludedInRefsInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.IncludedInRefs;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.CommitResource;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Option;

public class CommitIncludedInRefs implements RestReadView<CommitResource> {
  protected final IncludedInRefs includedInRefs;

  @Option(
      name = "--ref",
      aliases = {"-r"},
      metaVar = "REF",
      usage = "ref string")
  protected List<String> refs;

  public void addRefs(List<String> refs) {
    if (this.refs == null) {
      this.refs = new ArrayList<>();
    }
    this.refs.addAll(refs);
  }

  @Inject
  public CommitIncludedInRefs(IncludedInRefs includedInRefs) {
    this.includedInRefs = includedInRefs;
  }

  @Override
  public Response<IncludedInRefsInfo> apply(CommitResource resource)
      throws ResourceConflictException, BadRequestException, IOException,
          PermissionBackendException {
    return Response.ok(
        includedInRefs.apply(resource.getProjectState().getNameKey(), resource.getCommit(), refs));
  }
}
