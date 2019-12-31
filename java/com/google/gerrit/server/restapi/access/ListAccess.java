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

package com.google.gerrit.server.restapi.access;

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.restapi.project.GetAccess;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.kohsuke.args4j.Option;

/**
 * REST endpoint to list members of the {@link AccessCollection}.
 *
 * <p>This REST endpoint handles {@code GET /access/} requests.
 */
public class ListAccess implements RestReadView<TopLevelResource> {

  @Option(
      name = "--project",
      aliases = {"-p"},
      metaVar = "PROJECT",
      usage = "projects for which the access rights should be returned")
  private List<String> projects = new ArrayList<>();

  private final GetAccess getAccess;

  @Inject
  public ListAccess(GetAccess getAccess) {
    this.getAccess = getAccess;
  }

  @Override
  public Response<Map<String, ProjectAccessInfo>> apply(TopLevelResource resource)
      throws Exception {
    Map<String, ProjectAccessInfo> access = new TreeMap<>();
    for (String p : projects) {
      access.put(p, getAccess.apply(Project.nameKey(p)));
    }
    return Response.ok(access);
  }
}
