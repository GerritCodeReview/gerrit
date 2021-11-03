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

import com.google.gerrit.extensions.api.projects.CreateChangeFromDiffInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;

public class CreateChangeFromDiff
    implements RestModifyView<ProjectResource, CreateChangeFromDiffInput> {

  private final GitRepositoryManager gitManager;

  @Inject
  CreateChangeFromDiff(GitRepositoryManager gitManager) {
    this.gitManager = gitManager;
  }

  @Override
  public Response<String> apply(ProjectResource rsrc, CreateChangeFromDiffInput input)
      throws Exception {
    try (Repository git = gitManager.openRepository(rsrc.getNameKey())) {

      Git g = new Git(git);

      g.add().addFilepattern("name").call();
      g.commit().setMessage("PreImage").call();
      g.apply()
          .setPatch(new ByteArrayInputStream(input.diff.getBytes(Charset.defaultCharset())))
          .call();
      g.apply()
          .setPatch(new ByteArrayInputStream(input.diff.getBytes(Charset.defaultCharset())))
          .call();
    }
    return Response.ok();
  }
}
