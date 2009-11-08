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

import static com.google.gerrit.common.data.PatchScriptSettings.Whitespace.IGNORE_NONE;

import com.google.gerrit.common.data.PatchScriptSettings.Whitespace;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.EvictionPolicy;
import com.google.gerrit.server.cache.SelfPopulatingCache;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectWriter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Provides a cached list of {@link PatchListEntry}. */
@Singleton
public class PatchListCacheImpl implements PatchListCache {
  private static final String CACHE_NAME = "diff";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<PatchListKey, PatchList>> type =
            new TypeLiteral<Cache<PatchListKey, PatchList>>() {};
        disk(type, CACHE_NAME) //
            .memoryLimit(128) // very large items, cache only a few
            .evictionPolicy(EvictionPolicy.LRU) // prefer most recent
        ;
        bind(PatchListCacheImpl.class);
        bind(PatchListCache.class).to(PatchListCacheImpl.class);
      }
    };
  }

  private final GitRepositoryManager repoManager;
  private final SelfPopulatingCache<PatchListKey, PatchList> self;

  @Inject
  PatchListCacheImpl(final GitRepositoryManager grm,
      @Named(CACHE_NAME) final Cache<PatchListKey, PatchList> raw) {
    repoManager = grm;
    self = new SelfPopulatingCache<PatchListKey, PatchList>(raw) {
      @Override
      protected PatchList createEntry(final PatchListKey key) throws Exception {
        return compute(key);
      }
    };
  }

  public PatchList get(final PatchListKey key) {
    return self.get(key);
  }

  public PatchList get(final Change change, final PatchSet patchSet) {
    return get(change, patchSet, IGNORE_NONE);
  }

  public PatchList get(final Change change, final PatchSet patchSet,
      final Whitespace whitespace) {
    final Project.NameKey projectKey = change.getProject();
    final ObjectId a = null;
    final ObjectId b = ObjectId.fromString(patchSet.getRevision().get());
    return get(new PatchListKey(projectKey, a, b, whitespace));
  }

  private PatchList compute(final PatchListKey key)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    final Repository repo = repoManager.openRepository(key.projectKey.get());
    try {
      return readPatchList(key, repo);
    } finally {
      repo.close();
    }
  }

  private PatchList readPatchList(final PatchListKey key, final Repository repo)
      throws IOException {
    final RevCommit b = new RevWalk(repo).parseCommit(key.getNewId());
    final AnyObjectId a = aFor(key, repo, b);

    final List<String> args = new ArrayList<String>();
    args.add("git");
    args.add("--git-dir=.");
    args.add("diff-tree");
    args.add("-M");
    switch (key.getWhitespace()) {
      case IGNORE_NONE:
        break;
      case IGNORE_SPACE_AT_EOL:
        args.add("--ignore-space-at-eol");
        break;
      case IGNORE_SPACE_CHANGE:
        args.add("--ignore-space-change");
        break;
      case IGNORE_ALL_SPACE:
        args.add("--ignore-all-space");
        break;
      default:
        throw new IOException("Unsupported whitespace " + key.getWhitespace());
    }
    if (a == null /* want combined diff */) {
      args.add("--cc");
      args.add(b.name());
    } else {
      args.add("--unified=1");
      args.add(a.name());
      args.add(b.name());
    }

    final org.eclipse.jgit.patch.Patch p = new org.eclipse.jgit.patch.Patch();
    final Process diffProcess = exec(repo, args);
    try {
      diffProcess.getOutputStream().close();
      diffProcess.getErrorStream().close();

      final InputStream in = diffProcess.getInputStream();
      try {
        p.parse(in);
      } finally {
        in.close();
      }
    } finally {
      try {
        final int rc = diffProcess.waitFor();
        if (rc != 0) {
          throw new IOException("git diff-tree exited abnormally: " + rc);
        }
      } catch (InterruptedException ie) {
      }
    }

    final int cnt = p.getFiles().size();
    final PatchListEntry[] entries = new PatchListEntry[cnt];
    for (int i = 0; i < cnt; i++) {
      entries[i] = new PatchListEntry(p.getFiles().get(i));
    }
    return new PatchList(a, b, entries);
  }

  private static AnyObjectId aFor(final PatchListKey key,
      final Repository repo, final RevCommit b) throws IOException {
    if (key.getOldId() != null) {
      return key.getOldId();
    }

    switch (b.getParentCount()) {
      case 0:
        return emptyTree(repo);
      case 1:
        return b.getParent(0);
      default:
        // merge commit, return null to force combined diff behavior
        return null;
    }
  }

  private static Process exec(final Repository repo, final List<String> args)
      throws IOException {
    final String[] argv = args.toArray(new String[args.size()]);
    return Runtime.getRuntime().exec(argv, null, repo.getDirectory());
  }

  private static ObjectId emptyTree(final Repository repo) throws IOException {
    return new ObjectWriter(repo).writeCanonicalTree(new byte[0]);
  }
}
