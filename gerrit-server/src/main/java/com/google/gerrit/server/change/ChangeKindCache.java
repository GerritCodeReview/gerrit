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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.project.ProjectState;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

/**
 * Cache of {@link ChangeKind} per commit.
 * <p>
 * This is immutable conditioned on the merge strategy (unless the JGit strategy
 * implementation changes, which might invalidate old entries).
 */
public interface ChangeKindCache {
  ChangeKind getChangeKind(ProjectState project, Repository repo,
      ObjectId prior, ObjectId next);

  ChangeKind getChangeKind(ReviewDb db, Change change, PatchSet patch);
}
