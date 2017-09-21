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

import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/** Cache for mergeability of commits into destination branches. */
public interface MergeabilityCache {
  class NotImplemented implements MergeabilityCache {
    @Override
    public boolean get(
        ObjectId commit,
        Ref intoRef,
        SubmitType submitType,
        String mergeStrategy,
        Branch.NameKey dest,
        Repository repo) {
      throw new UnsupportedOperationException("Mergeability checking disabled");
    }

    @Override
    public Boolean getIfPresent(
        ObjectId commit, Ref intoRef, SubmitType submitType, String mergeStrategy) {
      throw new UnsupportedOperationException("Mergeability checking disabled");
    }
  }

  boolean get(
      ObjectId commit,
      Ref intoRef,
      SubmitType submitType,
      String mergeStrategy,
      Branch.NameKey dest,
      Repository repo);

  Boolean getIfPresent(ObjectId commit, Ref intoRef, SubmitType submitType, String mergeStrategy);
}
