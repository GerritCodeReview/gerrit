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

package com.google.gerrit.server.change;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;

import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

class GetArchive implements RestReadView<RevisionResource> {
  private static final Map<String, ArchiveFormat> formats = ArchiveFormat.init();
  private final GitRepositoryManager repoManager;

  @Option(name = "--format")
  private String format;

  @Inject
  GetArchive(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  @Override
  public BinaryResult apply(RevisionResource rsrc)
      throws BadRequestException, IOException {
    if (Strings.isNullOrEmpty(format)) {
      throw new BadRequestException("format is not specified");
    }
    final ArchiveFormat f = formats.get("." + format);
    if (f == null) {
      throw new BadRequestException("unknown archive format");
    }
    boolean close = true;
    final Repository repo = repoManager
        .openRepository(rsrc.getControl().getProject().getNameKey());
    try {
      final RevWalk rw = new RevWalk(repo);
      try {
        final RevCommit commit =
            rw.parseCommit(ObjectId.fromString(rsrc.getPatchSet()
                .getRevision().get()));
        BinaryResult bin = new BinaryResult() {
          @Override
          public void writeTo(OutputStream out) throws IOException {
            try {
              new ArchiveCommand(repo)
                  .setFormat(f.name())
                  .setTree(commit.getTree())
                  .setOutputStream(out).call();
            } catch (GitAPIException e) {
              throw new IOException(e);
            }
          }

          @Override
          public void close() throws IOException {
            rw.close();
            repo.close();
          }
        };

        bin.disableGzip()
            .setContentType(f.getMimeType())
            .setAttachmentName(name(f, rw, commit));

        close = false;
        return bin;
      } finally {
        if (close) {
          rw.close();
        }
      }
    } finally {
      if (close) {
        repo.close();
      }
    }
  }

  private static String name(ArchiveFormat format, RevWalk rw, RevCommit commit)
      throws IOException {
    return String.format("%s%s",
        rw.getObjectReader().abbreviate(commit,7).name(),
        format.getDefaultSuffix());
  }
}
