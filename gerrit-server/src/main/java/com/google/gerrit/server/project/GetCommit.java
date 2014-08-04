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

import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.GitPerson;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;

@Singleton
public class GetCommit implements RestReadView<CommitResource> {
  private final GitRepositoryManager repoManager;

  @Inject
  GetCommit(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  @Override
  public CommitInfo apply(CommitResource rsrc)
      throws ResourceNotFoundException, IOException {
    Repository repo = repoManager.openRepository(rsrc.getNameKey());
    try {
      RevWalk rw = new RevWalk(repo);
      try {
        return toCommitInfo(rw, rsrc.getCommitId());
      } catch (MissingObjectException | IncorrectObjectTypeException e) {
        throw new ResourceNotFoundException(rsrc.getCommitId().name());
      } finally {
        rw.release();
      }
    } finally {
      repo.close();
    }
  }

  private CommitInfo toCommitInfo(RevWalk rw, ObjectId commitId)
      throws ResourceNotFoundException, IOException {
    RevObject obj = rw.parseAny(commitId);
    if (!(obj instanceof RevCommit)) {
      throw new ResourceNotFoundException(commitId.name());
    }
    RevCommit commit = (RevCommit) obj;
    rw.parseBody(commit);

    CommitInfo info = new CommitInfo();
    info.commit = commit.getName();
    info.author = toGitPerson(commit.getAuthorIdent());
    info.committer = toGitPerson(commit.getCommitterIdent());
    info.subject = commit.getShortMessage();
    info.message = commit.getFullMessage();
    info.parents = new ArrayList<>(commit.getParentCount());
    for (int i = 0; i < commit.getParentCount(); i++) {
      RevCommit p = commit.getParent(i);
      rw.parseHeaders(p);
      CommitInfo parentInfo = new CommitInfo();
      parentInfo.commit = p.getName();
      parentInfo.subject = p.getShortMessage();
      info.parents.add(parentInfo);
    }
    return info;
  }

  private static GitPerson toGitPerson(PersonIdent ident) {
    GitPerson gp = new GitPerson();
    gp.name = ident.getName();
    gp.email = ident.getEmailAddress();
    gp.date = new Timestamp(ident.getWhen().getTime());
    gp.tz = ident.getTimeZoneOffset();
    return gp;
  }
}
