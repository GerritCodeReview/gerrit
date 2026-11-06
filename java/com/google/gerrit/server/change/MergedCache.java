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

package com.google.gerrit.server.change;

import com.google.gerrit.entities.Project;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;

/** Cache of Git commit merges in a project. */
public interface MergedCache {
  /**
   * Gets the cached {@link RevWalk#isMergedInto} answer for two commits in a project, computing and
   * caching the answer if it is not already cached.
   *
   * <p>Cache misses are computed using the caller's walk. Callers must not rely on the traversal
   * state or filter changes of an uncached ancestry check.
   *
   * @param project name of the project
   * @param rw caller's walk, which must be confined to the calling thread
   * @param commit left argument to {@link RevWalk#isMergedInto}
   * @param into right argument to {@link RevWalk#isMergedInto}
   * @return whether {@code commit} is merged into {@code into}
   * @throws IOException if a commit is missing or cannot be read, or the ancestry check fails
   */
  boolean isMergedInto(Project.NameKey project, RevWalk rw, ObjectId commit, ObjectId into)
      throws IOException;
}
