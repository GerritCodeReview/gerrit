// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.git.InvalidRepositoryException;
import com.google.gerrit.server.GerritServer;

import net.sf.ehcache.constructs.blocking.CacheEntryFactory;

import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.patch.FileHeader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DiffCacheEntryFactory implements CacheEntryFactory {
  private final GerritServer server;

  public DiffCacheEntryFactory(final GerritServer gs) {
    server = gs;
  }

  public Object createEntry(Object genericKey) throws Exception {
    final DiffCacheKey key = (DiffCacheKey) genericKey;
    final Repository db = open(key.getProjectKey());
    final List<String> args = new ArrayList<String>();
    args.add("git");
    args.add("--git-dir=.");
    args.add("diff-tree");
    if (key.getSourceFileName() != null) {
      args.add("-M");
    }
    args.add("--full-index");

    if (key.getOldId() == null) {
      args.add("--cc");
    } else {
      args.add("--unified=1");
      args.add(key.getOldId().name());
    }
    args.add(key.getNewId().name());
    args.add("--");
    args.add(key.getFileName());
    if (key.getSourceFileName() != null) {
      args.add(key.getSourceFileName());
    }

    final Process proc =
        Runtime.getRuntime().exec(args.toArray(new String[args.size()]), null,
            db.getDirectory());
    final FileHeader file;
    try {
      final org.spearce.jgit.patch.Patch p = new org.spearce.jgit.patch.Patch();
      proc.getOutputStream().close();
      proc.getErrorStream().close();
      p.parse(proc.getInputStream());
      proc.getInputStream().close();

      if (p.getFiles().isEmpty()) {
        return new DiffCacheContent();

      } else if (p.getFiles().size() != 1) {
        throw new IOException("unexpected file count: " + key);
      }

      file = p.getFiles().get(0);
    } finally {
      try {
        if (proc.waitFor() != 0) {
          throw new IOException("git diff-tree exited abnormally: " + key);
        }
      } catch (InterruptedException ie) {
      }
    }

    return DiffCacheContent.create(db, key, file);
  }

  private Repository open(final Project.NameKey key)
      throws InvalidRepositoryException {
    return server.getRepositoryCache().get(key.get());
  }
}
