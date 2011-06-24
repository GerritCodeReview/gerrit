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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.Project;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.util.Collection;

class TagSetHolder {
  private final Object buildLock = new Object();
  private final Project.NameKey projectName;
  private volatile TagSet tags;

  TagSetHolder(Project.NameKey projectName) {
    this.projectName = projectName;
  }

  Project.NameKey getProjectName() {
    return projectName;
  }

  TagSet getTagSet() {
    return tags;
  }

  void setTagSet(TagSet tags) {
    this.tags = tags;
  }

  TagMatcher matcher(Repository db, Collection<Ref> include) {
    TagSet tags = this.tags;
    if (tags == null) {
      tags = build(db);
    }

    TagMatcher m = new TagMatcher(this, db, include, tags, false);
    tags.prepare(m);
    if (!m.newRefs.isEmpty() || !m.lostRefs.isEmpty()) {
      tags = rebuild(db, tags, m);

      m = new TagMatcher(this, db, include, tags, true);
      tags.prepare(m);
    }
    return m;
  }

  void rebuildForNewTags(TagMatcher m) {
    m.tags = rebuild(m.db, m.tags, null);

    m.mask.clear();
    m.newRefs.clear();
    m.lostRefs.clear();
    m.tags.prepare(m);
  }

  private TagSet build(Repository db) {
    synchronized (buildLock) {
      TagSet tags = this.tags;
      if (tags == null) {
        tags = new TagSet(projectName);
        tags.build(db, null, null);
        this.tags = tags;
      }
      return tags;
    }
  }

  private TagSet rebuild(Repository db, TagSet old, TagMatcher m) {
    synchronized (buildLock) {
      TagSet cur = this.tags;
      if (cur == old) {
        cur = new TagSet(projectName);
        cur.build(db, old, m);
        this.tags = cur;
      }
      return cur;
    }
  }
}
