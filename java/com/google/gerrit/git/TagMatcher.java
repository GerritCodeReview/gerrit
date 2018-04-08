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

package com.google.gerrit.git;

import com.google.gerrit.git.TagSet.Tag;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class TagMatcher {
  final BitSet mask = new BitSet();
  final List<Ref> newRefs = new ArrayList<>();
  final List<LostRef> lostRefs = new ArrayList<>();
  final TagSetHolder holder;
  final TagCache cache;
  final Repository db;
  final Collection<Ref> include;
  TagSet tags;
  final boolean updated;
  private boolean rebuiltForNewTags;

  TagMatcher(
      TagSetHolder holder,
      TagCache cache,
      Repository db,
      Collection<Ref> include,
      TagSet tags,
      boolean updated) {
    this.holder = holder;
    this.cache = cache;
    this.db = db;
    this.include = include;
    this.tags = tags;
    this.updated = updated;
  }

  public boolean isReachable(Ref tagRef) {
    tagRef = db.peel(tagRef);

    ObjectId tagObj = tagRef.getPeeledObjectId();
    if (tagObj == null) {
      tagObj = tagRef.getObjectId();
      if (tagObj == null) {
        return false;
      }
    }

    Tag tag = tags.lookupTag(tagObj);
    if (tag == null) {
      if (rebuiltForNewTags) {
        return false;
      }

      rebuiltForNewTags = true;
      holder.rebuildForNewTags(cache, this);
      return isReachable(tagRef);
    }

    return tag.has(mask);
  }

  static class LostRef {
    final Tag tag;
    final int flag;

    LostRef(Tag tag, int flag) {
      this.tag = tag;
      this.flag = flag;
    }
  }
}
