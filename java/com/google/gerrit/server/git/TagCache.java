// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.entities.Project;
import com.google.inject.Module;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * Decides which tags are visible to a user who cannot read all refs: a tag is visible if a ref the
 * user can read reaches it.
 *
 * <p>{@code cache.git_tags.implementation} selects the implementation: {@link BitmapTagCache} (the
 * default) or {@link DagTagCache}. Each persists to a cache of its own, so that switching never
 * reads entries the other implementation wrote.
 */
public interface TagCache {
  /** Values of {@code cache.git_tags.implementation}. */
  enum Implementation {
    BITMAP,
    DAG
  }

  /** Answers reachability of tags for one lookup. */
  interface Matcher {
    boolean isReachable(Ref tag) throws IOException;
  }

  static Module module(Config cfg) {
    return switch (implementation(cfg)) {
      case BITMAP -> BitmapTagCache.module();
      case DAG -> DagTagCache.module();
    };
  }

  static Implementation implementation(Config cfg) {
    return cfg.getEnum("cache", "git_tags", "implementation", Implementation.BITMAP);
  }

  /**
   * Returns a matcher for which of {@code tags} are reachable from one of {@code refs}. Only the
   * tags in {@code tags} may be passed to {@link Matcher#isReachable(Ref)}.
   */
  Matcher matcher(
      Project.NameKey project, Repository repo, Collection<Ref> refs, Collection<Ref> tags)
      throws IOException;

  /**
   * Advise the cache that a reference fast-forwarded.
   *
   * @deprecated has no effect, the cache detects moved references on the next lookup.
   */
  @Deprecated
  default void updateFastForward(
      Project.NameKey name, String refName, ObjectId oldValue, ObjectId newValue) {}
}
