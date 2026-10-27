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

import static java.util.stream.Collectors.toList;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

public class TagSetHolder {
  /** Retries of a lookup whose refs moved while the DAG was updated, before a full walk. */
  private static final int MAX_REFRESHES = 3;

  private final Object buildLock = new Object();
  private final Project.NameKey projectName;

  @Nullable private volatile TagSet tagSet;

  TagSetHolder(Project.NameKey projectName) {
    this.projectName = projectName;
  }

  Project.NameKey getProjectName() {
    return projectName;
  }

  TagSet getTagSet() {
    return tagSet;
  }

  void setTagSet(TagSet tagSet) {
    this.tagSet = tagSet;
  }

  public TagMatcher matcher(
      TagCache cache, Repository db, Collection<Ref> include, Iterable<Ref> tags)
      throws IOException {
    include = include.stream().filter(r -> !TagSet.skip(r)).collect(toList());

    TagSet tagSet = this.tagSet;
    if (tagSet == null) {
      tagSet = build(cache, db);
    }

    TagMatcher m = new TagMatcher(this, cache, db, include, tagSet, tags);
    for (int i = 0; i < MAX_REFRESHES && !m.isComplete(); i++) {
      tagSet = refresh(cache, db, m);
      m = new TagMatcher(this, cache, db, include, tagSet, tags);
    }
    if (!m.isComplete()) {
      // Refs keep moving while the DAG is updated, or they cannot be read: treat the remaining
      // out-of-date refs as not reaching any tag, so that tags are only ever hidden, not leaked.
      m = TagMatcher.withoutUnknownRefs(m);
    }
    return m;
  }

  private TagSet build(TagCache cache, Repository db) throws IOException {
    synchronized (buildLock) {
      TagSet tagSet = this.tagSet;
      if (tagSet == null) {
        tagSet = new TagSet(projectName);
        if (!tagSet.build(db, null, null)) {
          throw new InterruptedIOException("interrupted while building tags of " + projectName);
        }
        this.tagSet = tagSet;
        cache.put(projectName, this);
      }
      return tagSet;
    }
  }

  /**
   * Applies the refs {@code m} found out of date to a copy of the current DAG and publishes it.
   *
   * <p>A thread that had to wait for the lock applies its updates to the DAG another thread just
   * published, not to the one it looked at, so that concurrent requests extend each other's work
   * instead of repeating it. Refs that no longer exist are dropped as part of the same update.
   */
  private TagSet refresh(TagCache cache, Repository db, TagMatcher m) throws IOException {
    synchronized (buildLock) {
      TagSet cur = this.tagSet;
      if (cur == null) {
        return build(cache, db);
      }
      TagSet updated = new TagSet(projectName);
      if (!updated.build(db, cur, m)) {
        throw new InterruptedIOException("interrupted while refreshing tags of " + projectName);
      }
      Set<String> existing = new HashSet<>();
      for (Ref r :
          db.getRefDatabase()
              .getRefsByPrefixWithExclusions(RefDatabase.ALL, TagSet.SKIPPABLE_REF_PREFIXES)) {
        existing.add(r.getName());
      }
      var unused = updated.removeDeletedRefs(existing);
      this.tagSet = updated;
      cache.put(projectName, this);
      return updated;
    }
  }

  @VisibleForTesting
  TagSet refreshForTesting(TagCache cache, Repository db, TagMatcher m) throws IOException {
    return refresh(cache, db, m);
  }

  enum Serializer implements CacheSerializer<TagSetHolder> {
    INSTANCE;

    @Override
    public byte[] serialize(TagSetHolder object) {
      TagSetHolderProto.Builder b =
          TagSetHolderProto.newBuilder().setProjectName(object.projectName.get());
      TagSet tagSet = object.tagSet;
      if (tagSet != null) {
        b.setTags(TagSet.Serializer.toProto(tagSet));
      }
      return Protos.toByteArray(b.build());
    }

    @Override
    public TagSetHolder deserialize(byte[] in) {
      TagSetHolderProto proto = Protos.parseUnchecked(TagSetHolderProto.parser(), in);
      TagSetHolder holder = new TagSetHolder(Project.nameKey(proto.getProjectName()));
      if (proto.hasTags()) {
        holder.tagSet = TagSet.Serializer.fromProto(proto.getTags());
      }
      return holder;
    }
  }
}
