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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

public class GetHead implements RestReadView<ProjectResource> {

  private GitRepositoryManager repoManager;

  @Inject
  GetHead(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  @Override
  public String apply(ProjectResource rsrc) throws AuthException,
      ResourceNotFoundException, IOException {
    Repository repo = null;
    try {
      repo = repoManager.openRepository(rsrc.getNameKey());
      Ref head = repo.getRef(Constants.HEAD);
      if (head == null) {
        throw new ResourceNotFoundException(Constants.HEAD);
      } else if (head.isSymbolic()) {
        String n = head.getTarget().getName();
        if (rsrc.getControl().controlForRef(n).isVisible()) {
          return n;
        }
        throw new AuthException("not allowed to see HEAD");
      } else if (head.getObjectId() != null) {
        RevWalk rw = new RevWalk(repo);
        try {
          RevCommit commit = rw.parseCommit(head.getObjectId());
          if (rsrc.getControl().canReadCommit(rw, commit)) {
            return head.getObjectId().name();
          }
          throw new AuthException("not allowed to see HEAD");
        } catch (MissingObjectException | IncorrectObjectTypeException e) {
          if (rsrc.getControl().isOwner()) {
            return head.getObjectId().name();
          }
          throw new AuthException("not allowed to see HEAD");
        } finally {
          rw.close();
        }
      }
      throw new ResourceNotFoundException(Constants.HEAD);
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException(rsrc.getName());
    } finally {
      if (repo != null) {
        repo.close();
      }
    }
  }
}
