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

package com.google.gerrit.server.restapi.project;

import com.google.gerrit.entities.Patch;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.change.FileInfoJson;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.project.CommitResource;
import com.google.gerrit.server.project.FileResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import org.eclipse.jgit.revwalk.RevCommit;
import org.kohsuke.args4j.Option;

/**
 * like {@link FilesCollection}, but for commits that are specified as hex ID, rather than branch
 * names.
 */
@Singleton
public class FilesInCommitCollection implements ChildCollection<CommitResource, FileResource> {
  private final DynamicMap<RestView<FileResource>> views;
  private final Provider<ListFiles> list;
  private final GitRepositoryManager repoManager;

  @Inject
  FilesInCommitCollection(
      DynamicMap<RestView<FileResource>> views,
      Provider<ListFiles> list,
      GitRepositoryManager repoManager) {
    this.views = views;
    this.list = list;
    this.repoManager = repoManager;
  }

  @Override
  public RestView<CommitResource> list() throws ResourceNotFoundException {
    return list.get();
  }

  @Override
  public FileResource parse(CommitResource parent, IdString id)
      throws ResourceNotFoundException, IOException {
    if (Patch.isMagic(id.get())) {
      return new FileResource(parent.getProjectState(), parent.getCommit(), id.get());
    }
    return FileResource.create(repoManager, parent.getProjectState(), parent.getCommit(), id.get());
  }

  @Override
  public DynamicMap<RestView<FileResource>> views() {
    return views;
  }

  public static final class ListFiles implements RestReadView<CommitResource> {
    /**
     * The 1-based parent number. If zero, the default base commit will be used, which is the only
     * parent for commits having one parent or the auto-merge commit otherwise.
     */
    @Option(name = "--parent", metaVar = "parent-number")
    int parentNum;

    private final FileInfoJson fileInfoJson;

    @Inject
    public ListFiles(FileInfoJson fileInfoJson) {
      this.fileInfoJson = fileInfoJson;
    }

    public ListFiles setParent(int parentNum) {
      this.parentNum = parentNum;
      return this;
    }

    @Override
    public Response<Map<String, FileInfo>> apply(CommitResource resource)
        throws ResourceConflictException, PatchListNotAvailableException {
      RevCommit commit = resource.getCommit();
      return Response.ok(
          fileInfoJson.getFileInfoMap(resource.getProjectState().getNameKey(), commit, parentNum));
    }
  }
}
