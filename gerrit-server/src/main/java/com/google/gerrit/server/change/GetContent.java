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

import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.io.OutputStream;

@Singleton
public class GetContent implements RestReadView<FileResource> {
  private final GitRepositoryManager repoManager;

  @Inject
  GetContent(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  @Override
  public BinaryResult apply(FileResource rsrc)
      throws ResourceNotFoundException, IOException {
    return apply(rsrc.getRevision().getControl().getProject().getNameKey(),
        rsrc.getRevision().getPatchSet().getRevision().get(),
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
          tw.close();
        }
      } finally {
        rw.close();
      }
    } finally {
      repo.close();
    }
  }
}
