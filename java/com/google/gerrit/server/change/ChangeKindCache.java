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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.server.query.change.ChangeData;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Cache of {@link ChangeKind} per commit.
 *
 * <p>This is immutable conditioned on the merge strategy (unless the JGit strategy implementation
 * changes, which might invalidate old entries).
 */
public interface ChangeKindCache {
  ChangeKind getChangeKind(
      Project.NameKey project,
      @Nullable RevWalk rw,
      @Nullable Config repoConfig,
      ObjectId prior,
      ObjectId next);

  ChangeKind getChangeKind(Change change, PatchSet patch);

  ChangeKind getChangeKind(
      @Nullable RevWalk rw, @Nullable Config repoConfig, ChangeData cd, PatchSet patch);
}
