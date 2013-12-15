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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RevisionEdit;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.io.OutputStream;

public class GetContent implements RestReadView<FileResource> {
  private final GitRepositoryManager repoManager;
  private final Provider<CurrentUser> user;

  @Inject
  GetContent(GitRepositoryManager repoManager, Provider<CurrentUser> user) {
    this.repoManager = repoManager;
    this.user = user;
  }

  @Override
  public BinaryResult apply(FileResource rsrc)
      throws ResourceNotFoundException, IOException, AuthException {
    Project.NameKey project = rsrc
        .getRevision().getControl().getProject().getNameKey();
    return apply(project,
        getRevision(project, rsrc.getRevision().getPatchSet()),
        rsrc.getPatchKey().get());
  }

  public BinaryResult apply(Project.NameKey project, String revstr, String path)
      throws ResourceNotFoundException, IOException {
    Repository repo = repoManager.openRepository(project);
    try {
      RevWalk rw = new RevWalk(repo);
      try {
        RevCommit commit =
            rw.parseCommit(repo.resolve(revstr));
        TreeWalk tw =
            TreeWalk.forPath(rw.getObjectReader(), path,
                commit.getTree().getId());
        if (tw == null) {
          throw new ResourceNotFoundException();
        }
        try {
          final ObjectLoader object = repo.open(tw.getObjectId(0));
          @SuppressWarnings("resource")
          BinaryResult result = new BinaryResult() {
            @Override
            public void writeTo(OutputStream os) throws IOException {
              object.copyTo(os);
            }
          };
          return result.setContentLength(object.getSize()).base64();
        } finally {
          tw.release();
        }
      } finally {
        rw.release();
      }
    } finally {
      repo.close();
    }
  }

  private String getRevision(Project.NameKey project, PatchSet patchSet)
      throws IOException, AuthException {
    if (patchSet.getId().isEdit()) {
      if (!user.get().isIdentifiedUser()) {
        throw new AuthException(
            "drafts only available to authenticated users");
      }
      IdentifiedUser me = (IdentifiedUser)user.get();
      RevisionEdit edit = new RevisionEdit(me, patchSet.getId());
      Repository repo = repoManager.openRepository(project);
      try {
        return edit.getRevision(repo);
      } finally {
        repo.close();
      }
    } else {
      return patchSet.getRevision().get();
    }
  }
}
