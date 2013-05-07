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

import static com.google.common.base.Charsets.UTF_8;

import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class GetContent implements RestReadView<PatchResource> {
  private final GitRepositoryManager repoManager;

  @Inject
  GetContent(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  @Override
  public String apply(PatchResource rsrc) throws ResourceNotFoundException {
    // TODO(dborowitz): Implement for draft revisions.
    rsrc.getRevision().checkPublished();
    Project.NameKey project = rsrc.getControl().getProject().getNameKey();
    try {
      Repository repo = repoManager.openRepository(project);
      try {
        RevWalk rw = new RevWalk(repo);
        try {
          RevCommit commit =
              rw.parseCommit(ObjectId.fromString(rsrc.getPatchSet()
                  .getRevision().get()));
          RevTree tree = commit.getTree();
          TreeWalk tw = new TreeWalk(repo);
          try {
            tw.addTree(tree);
            tw.setRecursive(true);
            tw.setFilter(PathFilter.create(rsrc.getPatchKey().get()));
            if (!tw.next()) {
              throw new ResourceNotFoundException();
            }
            ObjectId objectId = tw.getObjectId(0);
            ObjectLoader loader = repo.open(objectId);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            loader.copyTo(out);
            return out.toString(UTF_8.name());
          } finally {
            tw.release();
          }
        } finally {
          rw.release();
        }
      } finally {
        repo.close();
      }
    } catch (IOException e) {
      throw new ResourceNotFoundException();
    }
  }
}
