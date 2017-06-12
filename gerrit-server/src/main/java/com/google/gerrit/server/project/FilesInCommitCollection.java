// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class FilesInCommitCollection implements ChildCollection<CommitResource, FileResource> {
  private final DynamicMap<RestView<FileResource>> views;
  private final GitRepositoryManager repoManager;

  @Inject
  FilesInCommitCollection(
      DynamicMap<RestView<FileResource>> views, GitRepositoryManager repoManager) {
    this.views = views;
    this.repoManager = repoManager;
  }

  @Override
  public RestView<CommitResource> list() throws ResourceNotFoundException {
    throw new ResourceNotFoundException();
  }

  @Override
  public FileResource parse(CommitResource parent, IdString id)
      throws ResourceNotFoundException, IOException {
    if (Patch.isMagic(id.get())) {
      return new FileResource(parent.getProject(), parent.getCommit(), id.get());
    }
    return FileResource.create(repoManager, parent.getProject(), parent.getCommit(), id.get());
  }

  @Override
  public DynamicMap<RestView<FileResource>> views() {
    return views;
  }
}
