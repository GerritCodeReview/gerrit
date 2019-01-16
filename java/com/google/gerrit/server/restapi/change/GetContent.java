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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.change.FileResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.ComparisonType;
import com.google.gerrit.server.patch.Text;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

public class GetContent implements RestReadView<FileResource> {
  private final GitRepositoryManager gitManager;
  private final PatchSetUtil psUtil;
  private final FileContentUtil fileContentUtil;
  private final ProjectCache projectCache;

  @Option(name = "--parent")
  private Integer parent;

  @Inject
  GetContent(
      GitRepositoryManager gitManager,
      PatchSetUtil psUtil,
      FileContentUtil fileContentUtil,
      ProjectCache projectCache) {
    this.gitManager = gitManager;
    this.psUtil = psUtil;
    this.fileContentUtil = fileContentUtil;
    this.projectCache = projectCache;
  }

  @Override
  public BinaryResult apply(FileResource rsrc)
      throws ResourceNotFoundException, IOException, BadRequestException, StorageException {
    String path = rsrc.getPatchKey().get();
    if (Patch.COMMIT_MSG.equals(path)) {
      String msg = getMessage(rsrc.getRevision().getChangeResource().getNotes());
      return BinaryResult.create(msg)
          .setContentType(FileContentUtil.TEXT_X_GERRIT_COMMIT_MESSAGE)
          .base64();
    } else if (Patch.MERGE_LIST.equals(path)) {
      byte[] mergeList = getMergeList(rsrc.getRevision().getChangeResource().getNotes());
      return BinaryResult.create(mergeList)
          .setContentType(FileContentUtil.TEXT_X_GERRIT_MERGE_LIST)
          .base64();
    }
    return fileContentUtil.getContent(
        projectCache.checkedGet(rsrc.getRevision().getProject()),
        ObjectId.fromString(rsrc.getRevision().getPatchSet().getRevision().get()),
        path,
        parent);
  }

  private String getMessage(ChangeNotes notes) throws StorageException, IOException {
    Change.Id changeId = notes.getChangeId();
    PatchSet ps = psUtil.current(notes);
    if (ps == null) {
      throw new NoSuchChangeException(changeId);
    }

    try (Repository git = gitManager.openRepository(notes.getProjectName());
        RevWalk revWalk = new RevWalk(git)) {
      RevCommit commit = revWalk.parseCommit(ObjectId.fromString(ps.getRevision().get()));
      return commit.getFullMessage();
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(changeId, e);
    }
  }

  private byte[] getMergeList(ChangeNotes notes) throws StorageException, IOException {
    Change.Id changeId = notes.getChangeId();
    PatchSet ps = psUtil.current(notes);
    if (ps == null) {
      throw new NoSuchChangeException(changeId);
    }

    try (Repository git = gitManager.openRepository(notes.getProjectName());
        RevWalk revWalk = new RevWalk(git)) {
      return Text.forMergeList(
              ComparisonType.againstAutoMerge(),
              revWalk.getObjectReader(),
              ObjectId.fromString(ps.getRevision().get()))
          .getContent();
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(changeId, e);
    }
  }
}
