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
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class CommitsCollection implements ChildCollection<ProjectResource, CommitResource> {
  private final DynamicMap<RestView<CommitResource>> views;
  private final GitRepositoryManager repoManager;
  private final Provider<ReviewDb> db;

  @Inject
  public CommitsCollection(
      DynamicMap<RestView<CommitResource>> views,
      GitRepositoryManager repoManager,
      Provider<ReviewDb> db) {
    this.views = views;
    this.repoManager = repoManager;
    this.db = db;
  }

  @Override
  public RestView<ProjectResource> list() throws ResourceNotFoundException {
    throw new ResourceNotFoundException();
  }

  @Override
  public CommitResource parse(ProjectResource parent, IdString id)
      throws ResourceNotFoundException, IOException {
    ObjectId objectId;
    try {
      objectId = ObjectId.fromString(id.get());
    } catch (IllegalArgumentException e) {
      throw new ResourceNotFoundException(id);
    }

    try (Repository repo = repoManager.openRepository(parent.getNameKey());
        RevWalk rw = new RevWalk(repo)) {
      RevCommit commit = rw.parseCommit(objectId);
      rw.parseBody(commit);
      if (!parent.getControl().canReadCommit(db.get(), repo, commit)) {
        throw new ResourceNotFoundException(id);
      }
      for (int i = 0; i < commit.getParentCount(); i++) {
        rw.parseBody(rw.parseCommit(commit.getParent(i)));
      }
      return new CommitResource(parent, commit);
    } catch (MissingObjectException | IncorrectObjectTypeException e) {
      throw new ResourceNotFoundException(id);
    }
  }

  @Override
  public DynamicMap<RestView<CommitResource>> views() {
    return views;
  }
}
