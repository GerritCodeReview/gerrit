// Copyright (C) 2011 The Android Open Source Project
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

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class TagMatcher {
  final Map<String, ObjectId> toUpdate = new HashMap<>();
  final TagSetHolder holder;
  final TagCache cache;
  final Repository db;
  final Collection<Ref> include;
  TagSet tagSet;
  final Set<ObjectId> reachableTags;

  TagMatcher(
      TagSetHolder holder,
      TagCache cache,
      Repository db,
      Collection<Ref> include,
      TagSet tagSet,
      Iterable<Ref> tags) {
    this.holder = holder;
    this.cache = cache;
    this.db = db;
    this.include = include;
    this.tagSet = tagSet;
    reachableTags = tagSet.getReachableTags(db, this, include, tags);
  }

  public boolean isReachable(Ref tagRef) throws IOException {
    tagRef = db.getRefDatabase().peel(tagRef);
    ObjectId tagObj = tagRef.getPeeledObjectId();
    if (tagObj == null) {
      tagObj = tagRef.getObjectId();
      if (tagObj == null) {
        return false;
      }
    }

    return reachableTags.contains(tagObj);
  }
}
