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

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.StreamingResponse;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public class GetContent implements RestReadView<FileResource> {
  private final GitRepositoryManager repoManager;

  @Inject
  GetContent(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  @Override
  public StreamingResponse apply(FileResource rsrc)
      throws ResourceNotFoundException, IOException {
    // TODO(dborowitz): Implement for draft revisions.
    rsrc.getRevision().checkPublished();
    Project.NameKey project =
        rsrc.getRevision().getControl().getProject().getNameKey();
    Repository repo = repoManager.openRepository(project);
    try {
      RevWalk rw = new RevWalk(repo);
      try {
        RevCommit commit =
            rw.parseCommit(ObjectId.fromString(rsrc.getRevision().getPatchSet()
                .getRevision().get()));
        TreeWalk tw =
            TreeWalk.forPath(rw.getObjectReader(), rsrc.getPatchKey().get(),
                commit.getTree().getId());
        if (tw == null) {
          throw new ResourceNotFoundException();
        }
        try {
          final ObjectLoader loader = repo.open(tw.getObjectId(0));
          return new StreamingResponse() {
            @Override
            public String getContentType() {
              return "text/plain;charset=UTF-8";
            }

            @Override
            public void stream(OutputStream out) throws IOException {
              PrintWriter writer =
                  new PrintWriter(new OutputStreamWriter(out, Charsets.UTF_8));
              try {
                loader.copyTo(BaseEncoding.base64().encodingStream(writer));
              } finally {
                writer.flush();
              }
            }
          };
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
