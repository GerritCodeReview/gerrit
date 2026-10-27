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

import static com.google.common.base.Equivalence.identity;
import static java.util.stream.Collectors.toList;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.DagTagSetHolderProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Collection;
import java.util.List;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

public class DagTagSetHolder {
  private final Object buildLock = new Object();
  private final Project.NameKey projectName;

  @Nullable private volatile DagTagSet tagSet;

  DagTagSetHolder(Project.NameKey projectName) {
    this.projectName = projectName;
  }

  Project.NameKey getProjectName() {
    return projectName;
  }

  DagTagSet getTagSet() {
    return tagSet;
  }

  void setTagSet(DagTagSet tagSet) {
    this.tagSet = tagSet;
  }

  public DagTagMatcher matcher(
      DagTagCache cache, Repository db, Collection<Ref> include, Iterable<Ref> tags)
      throws IOException {
    include = include.stream().filter(r -> !DagTagSet.skip(r)).collect(toList());

    DagTagSet tagSet = this.tagSet;
    if (tagSet == null) {
      tagSet = build(cache, db);
    }

    DagTagMatcher m = new DagTagMatcher(this, cache, db, include, tagSet, tags);
    if (m.isComplete()) {
      return m;
    }
    DagTagSet refreshed = refresh(cache, db);
    if (!identity().equivalent(refreshed, tagSet)) {
      m = new DagTagMatcher(this, cache, db, include, refreshed, tags);
    }
    if (!m.isComplete()) {
      // The refresh read the refs after this lookup did, so the refs of this lookup are older
      // than the DAG, e.g. read before a push that another lookup already applied. Another
      // refresh would not change that: answer from their commits instead.
      m = DagTagMatcher.readingCommitsOfOutOfDateRefs(m);
    }
    return m;
  }

  private DagTagSet build(DagTagCache cache, Repository db) throws IOException {
    synchronized (buildLock) {
      DagTagSet tagSet = this.tagSet;
      if (tagSet == null) {
        tagSet = new DagTagSet(projectName);
        if (!tagSet.build(db)) {
          throw new InterruptedIOException("interrupted while building tags of " + projectName);
        }
        this.tagSet = tagSet;
        cache.put(projectName, this);
      }
      return tagSet;
    }
  }

  /**
   * Builds a new DAG from the full history and publishes it, if refs changed since the current one
   * was built. A thread that had to wait for the lock checks the DAG another thread just published,
   * so that concurrent requests do not repeat each other's build.
   */
  private DagTagSet refresh(DagTagCache cache, Repository db) throws IOException {
    synchronized (buildLock) {
      DagTagSet cur = this.tagSet;
      if (cur == null) {
        return build(cache, db);
      }
      // Read under the lock, so that a lookup that waited for another one's rebuild finds the DAG
      // up to date with its refs, or with newer ones.
      List<Ref> refs =
          db.getRefDatabase()
              .getRefsByPrefixWithExclusions(RefDatabase.ALL, DagTagSet.SKIPPABLE_REF_PREFIXES);
      if (cur.outOfDate(db, refs).isEmpty()) {
        return cur;
      }
      DagTagSet updated = new DagTagSet(projectName);
      if (!updated.build(db)) {
        throw new InterruptedIOException("interrupted while refreshing tags of " + projectName);
      }
      this.tagSet = updated;
      cache.put(projectName, this);
      return updated;
    }
  }

  @VisibleForTesting
  DagTagSet refreshForTesting(DagTagCache cache, Repository db) throws IOException {
    return refresh(cache, db);
  }

  enum Serializer implements CacheSerializer<DagTagSetHolder> {
    INSTANCE;

    @Override
    public byte[] serialize(DagTagSetHolder object) {
      DagTagSetHolderProto.Builder b =
          DagTagSetHolderProto.newBuilder().setProjectName(object.projectName.get());
      DagTagSet tagSet = object.tagSet;
      if (tagSet != null) {
        b.setTags(DagTagSet.Serializer.toProto(tagSet));
      }
      return Protos.toByteArray(b.build());
    }

    @Override
    public DagTagSetHolder deserialize(byte[] in) {
      DagTagSetHolderProto proto = Protos.parseUnchecked(DagTagSetHolderProto.parser(), in);
      DagTagSetHolder holder = new DagTagSetHolder(Project.nameKey(proto.getProjectName()));
      if (proto.hasTags()) {
        holder.tagSet = DagTagSet.Serializer.fromProto(proto.getTags());
      }
      return holder;
    }
  }
}
