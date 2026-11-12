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
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

public class DagTagSetHolder {
  /**
   * How long after a full rebuild of an incomplete DAG that came back incomplete again, e.g.
   * because an object is missing for good, refs are applied as updates before trying again.
   */
  private static final Duration INCOMPLETE_REBUILD_INTERVAL = Duration.ofHours(1);

  private final Object buildLock = new Object();
  private final Project.NameKey projectName;

  @Nullable private volatile DagTagSet tagSet;

  /** When a full rebuild last came back incomplete, if it did. Guarded by {@link #buildLock}. */
  @Nullable private Instant incompleteRebuild;

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
   * Applies every ref the DAG is out of date for, read under the lock, to a copy of the current DAG
   * and publishes it, or returns the DAG as is if there is none.
   *
   * <p>A thread that had to wait for the lock applies its updates to the DAG another thread just
   * published, not to the one it looked at, so that concurrent requests extend each other's work
   * instead of repeating it. Refs that no longer exist are dropped as part of the same update. A
   * DAG that read errors left incomplete is rebuilt from the full history instead, see {@link
   * #INCOMPLETE_REBUILD_INTERVAL}.
   */
  private DagTagSet refresh(DagTagCache cache, Repository db) throws IOException {
    synchronized (buildLock) {
      DagTagSet cur = this.tagSet;
      if (cur == null) {
        return build(cache, db);
      }
      // Update every ref the DAG is out of date for, not only the lookup's: refs that no lookup
      // includes would otherwise keep their decorations on commits that a gc may remove. Read
      // under the lock, so that they are never older than what another lookup published while
      // this one waited.
      List<Ref> refs =
          db.getRefDatabase()
              .getRefsByPrefixWithExclusions(RefDatabase.ALL, DagTagSet.SKIPPABLE_REF_PREFIXES);
      Map<String, ObjectId> idByRef = cur.outOfDate(db, refs);
      if (idByRef.isEmpty()) {
        // Another lookup with the same out-of-date refs refreshed while this one waited.
        return cur;
      }
      // A DAG that read errors left incomplete is only rebuilt once refs changed. If the rebuild is
      // incomplete again, the error may be permanent: apply updates to it, which keeps it marked
      // incomplete, and only try a full rebuild again after a while, so that every push does not
      // walk the full history under the lock.
      Instant now = Instant.now();
      if (cur.isIncomplete()
          && (incompleteRebuild == null
              || !now.isBefore(incompleteRebuild.plus(INCOMPLETE_REBUILD_INTERVAL)))) {
        DagTagSet rebuilt = new DagTagSet(projectName);
        if (!rebuilt.build(db, null, null)) {
          throw new InterruptedIOException("interrupted while rebuilding tags of " + projectName);
        }
        incompleteRebuild = rebuilt.isIncomplete() ? now : null;
        this.tagSet = rebuilt;
        cache.put(projectName, this);
        return rebuilt;
      }
      Set<String> existing = new HashSet<>();
      for (Ref r : refs) {
        existing.add(r.getName());
      }
      DagTagSet updated = new DagTagSet(projectName);
      if (!updated.build(db, cur, idByRef, existing)) {
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
