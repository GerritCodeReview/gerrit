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

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.config.DownloadConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GetArchive implements RestReadView<RevisionResource> {
  @Singleton
  public static class AllowedFormats {
    final ImmutableMap<String, ArchiveFormat> extensions;
    final Set<ArchiveFormat> allowed;

    @Inject
    AllowedFormats(DownloadConfig cfg) {
      Map<String, ArchiveFormat> exts = new HashMap<>();
      for (ArchiveFormat format : cfg.getArchiveFormats()) {
        for (String ext : format.getSuffixes()) {
          exts.put(ext, format);
        }
        exts.put(format.name().toLowerCase(), format);
      }
      extensions = ImmutableMap.copyOf(exts);

      // Zip is not supported because it may be interpreted by a Java plugin as a
      // valid JAR file, whose code would have access to cookies on the domain.
      allowed = Sets.filter(
          cfg.getArchiveFormats(),
          new Predicate<ArchiveFormat>() {
            @Override
            public boolean apply(ArchiveFormat format) {
              return (format != ArchiveFormat.ZIP);
            }
          });
    }

    public Set<ArchiveFormat> getAllowed() {
      return allowed;
    }

    public ImmutableMap<String, ArchiveFormat> getExtensions() {
      return extensions;
    }
  }

  private final GitRepositoryManager repoManager;
  private final AllowedFormats allowedFormats;

  @Option(name = "--format")
  private String format;

  @Inject
  GetArchive(GitRepositoryManager repoManager, AllowedFormats allowedFormats) {
    this.repoManager = repoManager;
    this.allowedFormats = allowedFormats;
  }

  @Override
  public BinaryResult apply(RevisionResource rsrc) throws BadRequestException,
      IOException, MethodNotAllowedException {
    if (Strings.isNullOrEmpty(format)) {
      throw new BadRequestException("format is not specified");
    }
    final ArchiveFormat f = allowedFormats.extensions.get("." + format);
    if (f == null) {
      throw new BadRequestException("unknown archive format");
    }
    if (f == ArchiveFormat.ZIP) {
      throw new MethodNotAllowedException("zip format is disabled");
    }
    boolean close = true;
    final Repository repo = repoManager
        .openRepository(rsrc.getControl().getProject().getNameKey());
    try {
      final RevCommit commit;
      String name;
      try (RevWalk rw = new RevWalk(repo)) {
        commit = rw.parseCommit(ObjectId.fromString(
            rsrc.getPatchSet().getRevision().get()));
        name = name(f, rw, commit);
      }

      BinaryResult bin = new BinaryResult() {
        @Override
        public void writeTo(OutputStream out) throws IOException {
          try {
            new ArchiveCommand(repo)
                .setFormat(f.name())
                .setTree(commit.getTree())
                .setOutputStream(out)
                .call();
          } catch (GitAPIException e) {
            throw new IOException(e);
          }
        }

        @Override
        public void close() throws IOException {
          repo.close();
        }
      };

      bin.disableGzip()
          .setContentType(f.getMimeType())
          .setAttachmentName(name);

      close = false;
      return bin;
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
