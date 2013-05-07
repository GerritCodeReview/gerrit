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

import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;

import org.apache.commons.codec.binary.Base64;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class GetContent implements RestReadView<FileResource> {
  private final GitRepositoryManager repoManager;

  @Inject
  GetContent(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  @Override
  public byte[] apply(FileResource rsrc) throws ResourceNotFoundException,
      IOException {
    Project.NameKey project = rsrc.getControl().getProject().getNameKey();
    Repository repo = repoManager.openRepository(project);
    try {
      RevWalk rw = new RevWalk(repo);
      try {
        RevCommit commit =
            rw.parseCommit(ObjectId.fromString(rsrc.getPatchSet().getRevision()
                .get()));
        TreeWalk tw =
            TreeWalk.forPath(rw.getObjectReader(), rsrc.getPatchKey().get(),
                commit.getTree().getId());
        if (tw == null) {
          throw new ResourceNotFoundException();
        }

        try {
          ObjectLoader loader = repo.open(tw.getObjectId(0));
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          loader.copyTo(out);
          return Base64.encodeBase64(out.toByteArray());
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
