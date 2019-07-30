// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.project.ChildProjectResource;
import com.google.gerrit.server.project.ProjectJson;
import com.google.inject.Inject;
import org.kohsuke.args4j.Option;

public class GetChildProject implements RestReadView<ChildProjectResource> {
  @Option(name = "--recursive", usage = "to list child projects recursively")
  public void setRecursive(boolean recursive) {
    this.recursive = recursive;
  }

  private final ProjectJson json;
  private boolean recursive;

  @Inject
  GetChildProject(ProjectJson json) {
    this.json = json;
  }

  @Override
  public Response<ProjectInfo> apply(ChildProjectResource rsrc) throws ResourceNotFoundException {
    if (recursive || rsrc.isDirectChild()) {
      return Response.ok(json.format(rsrc.getChild().getProject()));
    }
    throw new ResourceNotFoundException(rsrc.getChild().getName());
  }
}
