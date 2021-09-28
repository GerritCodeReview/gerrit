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

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.IncludedInRefs;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.kohsuke.args4j.Option;

public class CommitsIncludedInRefs implements RestReadView<ProjectResource> {
  protected final IncludedInRefs includedInRefs;

  protected Set<String> commits = new HashSet<>();
  protected Set<String> refs = new HashSet<>();

  @Option(
      name = "--commit",
      aliases = {"-c"},
      required = true,
      metaVar = "COMMIT",
      usage = "commit sha1")
  protected void addCommit(String commit) {
    commits.add(commit);
  }

  @Option(
      name = "--ref",
      aliases = {"-r"},
      required = true,
      metaVar = "REF",
      usage = "full ref name")
  protected void addRef(String ref) {
    refs.add(ref);
  }

  public void addCommits(Collection<String> commits) {
    this.commits.addAll(commits);
  }

  public void addRefs(Collection<String> refs) {
    this.refs.addAll(refs);
  }

  @Inject
  public CommitsIncludedInRefs(IncludedInRefs includedInRefs) {
    this.includedInRefs = includedInRefs;
  }

  @Override
  public Response<Map<String, Set<String>>> apply(ProjectResource resource)
      throws ResourceConflictException, BadRequestException, IOException,
          PermissionBackendException, ResourceNotFoundException, AuthException {
    if (commits.isEmpty()) {
      throw new BadRequestException("commit is required");
    }
    if (refs.isEmpty()) {
      throw new BadRequestException("ref is required");
    }
    return Response.ok(includedInRefs.apply(resource.getNameKey(), commits, refs));
  }
}
