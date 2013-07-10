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
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.FileTypeRegistry;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.Text;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;

public class GetContentType extends ContentBase {
  private final FileTypeRegistry registry;

  @Inject
  GetContentType(GitRepositoryManager repoManager, Provider<CurrentUser> user,
      final FileTypeRegistry ftr) {
    this.repoManager = repoManager;
    this.user = user;
    this.registry = ftr;
  }

  @Override
  public String apply(FileResource rsrc) throws ResourceNotFoundException,
      IOException, AuthException {
    Project.NameKey project =
        rsrc.getRevision().getControl().getProject().getNameKey();
    Repository repo = repoManager.openRepository(project);
    try {
      RevWalk rw = new RevWalk(repo);
      try {
        RevCommit commit = getCommit(rsrc, repo, rw);
        TreeWalk tw =
            TreeWalk.forPath(rw.getObjectReader(), rsrc.getPatchKey().get(),
                commit.getTree().getId());
        if (tw == null) {
          throw new ResourceNotFoundException();
        }
        try {
          return registry.getMimeType(rsrc.getPatchKey().getFileName(),
              Text.asByteArray(repo.open(tw.getObjectId(0)))).toString();
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
}
