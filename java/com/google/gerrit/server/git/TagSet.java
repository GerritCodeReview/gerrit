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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto.TagSetProto;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto.TagSetProto.CachedRefProto;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto.TagSetProto.TagProto;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

class TagSet {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Project.NameKey projectName;
  private final Map<String, CachedRef> refs;
  private final ObjectIdOwnerMap<Tag> tags;

  TagSet(Project.NameKey projectName) {
    this(projectName, new HashMap<>(), new ObjectIdOwnerMap<>());
  }

  TagSet(Project.NameKey projectName, HashMap<String, CachedRef> refs, ObjectIdOwnerMap<Tag> tags) {
    this.projectName = projectName;
    this.refs = refs;
    this.tags = tags;
  }

  Project.NameKey getProjectName() {
    return projectName;
  }

  Tag lookupTag(AnyObjectId id) {
    return tags.get(id);
  }

  // Test methods have obtuse names in addition to annotations, since they expose mutable state
  // which would be easy to corrupt.
  @VisibleForTesting
  Map<String, CachedRef> getRefsForTesting() {
    return refs;
  }

  @VisibleForTesting
  ObjectIdOwnerMap<Tag> getTagsForTesting() {
    return tags;
  }

  boolean updateFastForward(String refName, ObjectId oldValue, ObjectId newValue) {
    CachedRef ref = refs.get(refName);
    if (ref != null) {
      // compareAndSet works on reference equality, but this operation
      // wants to use object equality. Switch out oldValue with cur so the
      // compareAndSet will function correctly for this operation.
      //
      ObjectId cur = ref.get();
      if (cur.equals(oldValue)) {
        return ref.compareAndSet(cur, newValue);
      }
    }
    return false;
  }

  void prepare(TagMatcher m) {
    @SuppressWarnings("resource")
    RevWalk rw = null;
    try {
      for (Ref currentRef : m.include) {
        if (currentRef.isSymbolic()) {
          continue;
        }
        if (currentRef.getObjectId() == null) {
          continue;
        }

        CachedRef savedRef = refs.get(currentRef.getName());
        if (savedRef == null) {
          // If the reference isn't known to the set, return null
          // and force the caller to rebuild the set in a new copy.
          m.newRefs.add(currentRef);
          continue;
        }

        // The reference has not been moved. It can be used as-is.
        ObjectId savedObjectId = savedRef.get();
        if (currentRef.getObjectId().equals(savedObjectId)) {
          m.mask.set(savedRef.flag);
          continue;
        }

        // Check on-the-fly to see if the branch still reaches the tag.
        // This is very likely for a branch that fast-forwarded.
        try {
          if (rw == null) {
            rw = new RevWalk(m.db);
            rw.setRetainBody(false);
          }

          RevCommit savedCommit = rw.parseCommit(savedObjectId);
          RevCommit currentCommit = rw.parseCommit(currentRef.getObjectId());
          if (rw.isMergedInto(savedCommit, currentCommit)) {
            // Fast-forward. Safely update the reference in-place.
            savedRef.compareAndSet(savedObjectId, currentRef.getObjectId());
            m.mask.set(savedRef.flag);
            continue;
          }

          // The branch rewound. Walk the list of commits removed from
          // the reference. If any matches to a tag, this has to be removed.
          boolean err = false;
          rw.reset();
          rw.markStart(savedCommit);
          rw.markUninteresting(currentCommit);
          rw.sort(RevSort.TOPO, true);
          RevCommit c;
          while ((c = rw.next()) != null) {
            Tag tag = tags.get(c);
            if (tag != null && tag.refFlags.get(savedRef.flag)) {
              m.lostRefs.add(new TagMatcher.LostRef(tag, savedRef.flag));
              err = true;
            }
          }
          if (!err) {
            // All of the tags are still reachable. Update in-place.
            savedRef.compareAndSet(savedObjectId, currentRef.getObjectId());
            m.mask.set(savedRef.flag);
          }

        } catch (IOException err) {
          // Defer a cache update until later. No conclusion can be made
          // based on an exception reading from the repository storage.
          logger.atWarning().withCause(err).log("Error checking tags of %s", projectName);
        }
      }
    } finally {
      if (rw != null) {
        rw.close();
      }
    }
  }

  void build(Repository git, TagSet old, TagMatcher m) {
    if (old != null && m != null && refresh(old, m)) {
      return;
    }

    try (TagWalk rw = new TagWalk(git)) {
      rw.setRetainBody(false);
      for (Ref ref : git.getRefDatabase().getRefs()) {
        if (skip(ref)) {
          continue;

        } else if (isTag(ref)) {
          // For a tag, remember where it points to.
          try {
            addTag(rw, git.getRefDatabase().peel(ref));
          } catch (IOException e) {
            addTag(rw, ref);
          }

        } else {
          // New reference to include in the set.
          addRef(rw, ref);
        }
      }

      // Traverse the complete history. Copy any flags from a commit to
      // all of its ancestors. This automatically updates any Tag object
      // as the TagCommit and the stored Tag object share the same
      // underlying bit set.
      TagCommit c;
      while ((c = (TagCommit) rw.next()) != null) {
        BitSet mine = c.refFlags;
        int pCnt = c.getParentCount();
        for (int pIdx = 0; pIdx < pCnt; pIdx++) {
          ((TagCommit) c.getParent(pIdx)).refFlags.or(mine);
        }
      }
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Error building tags for repository %s", projectName);
    }
  }

  static TagSet fromProto(TagSetProto proto) {
    ObjectIdConverter idConverter = ObjectIdConverter.create();

    HashMap<String, CachedRef> refs = Maps.newHashMapWithExpectedSize(proto.getRefCount());
    proto
        .getRefMap()
        .forEach(
            (n, cr) ->
                refs.put(n, new CachedRef(cr.getFlag(), idConverter.fromByteString(cr.getId()))));
    ObjectIdOwnerMap<Tag> tags = new ObjectIdOwnerMap<>();
    proto
        .getTagList()
        .forEach(
            t ->
                tags.add(
                    new Tag(
                        idConverter.fromByteString(t.getId()),
                        BitSet.valueOf(t.getFlags().asReadOnlyByteBuffer()))));
    return new TagSet(Project.nameKey(proto.getProjectName()), refs, tags);
  }

  TagSetProto toProto() {
    ObjectIdConverter idConverter = ObjectIdConverter.create();
    TagSetProto.Builder b = TagSetProto.newBuilder().setProjectName(projectName.get());
    refs.forEach(
        (n, cr) ->
            b.putRef(
                n,
                CachedRefProto.newBuilder()
                    .setId(idConverter.toByteString(cr.get()))
                    .setFlag(cr.flag)
                    .build()));
    tags.forEach(
        t ->
            b.addTag(
                TagProto.newBuilder()
                    .setId(idConverter.toByteString(t))
                    .setFlags(ByteString.copyFrom(t.refFlags.toByteArray()))
                    .build()));
    return b.build();
  }

  private boolean refresh(TagSet old, TagMatcher m) {
    if (m.newRefs.isEmpty()) {
      // No new references is a simple update. Copy from the old set.
      copy(old, m);
      return true;
    }

    // Only permit a refresh if all new references start from the tip of
    // an existing references. This happens some of the time within a
    // Gerrit Code Review server, perhaps about 50% of new references.
    // Since a complete rebuild is so costly, try this approach first.

    Map<ObjectId, Integer> byObj = new HashMap<>();
    for (CachedRef r : old.refs.values()) {
      ObjectId id = r.get();
      if (!byObj.containsKey(id)) {
        byObj.put(id, r.flag);
      }
    }

    for (Ref newRef : m.newRefs) {
      ObjectId id = newRef.getObjectId();
      if (id == null || refs.containsKey(newRef.getName())) {
        continue;
      } else if (!byObj.containsKey(id)) {
        return false;
      }
    }

    copy(old, m);

    for (Ref newRef : m.newRefs) {
      ObjectId id = newRef.getObjectId();
      if (id == null || refs.containsKey(newRef.getName())) {
        continue;
      }

      int srcFlag = byObj.get(id);
      int newFlag = refs.size();
      refs.put(newRef.getName(), new CachedRef(newRef, newFlag));

      for (Tag tag : tags) {
        if (tag.refFlags.get(srcFlag)) {
          tag.refFlags.set(newFlag);
        }
      }
    }

    return true;
  }

  private void copy(TagSet old, TagMatcher m) {
    refs.putAll(old.refs);

    for (Tag srcTag : old.tags) {
      BitSet mine = new BitSet();
      mine.or(srcTag.refFlags);
      tags.add(new Tag(srcTag, mine));
    }

    for (TagMatcher.LostRef lost : m.lostRefs) {
      Tag mine = tags.get(lost.tag);
      if (mine != null) {
        mine.refFlags.clear(lost.flag);
      }
    }
  }

  private void addTag(TagWalk rw, Ref ref) {
    ObjectId id = ref.getPeeledObjectId();
    if (id == null) {
      id = ref.getObjectId();
    }

    if (!tags.contains(id)) {
      BitSet flags;
      try {
        flags = ((TagCommit) rw.parseCommit(id)).refFlags;
      } catch (IncorrectObjectTypeException notCommit) {
        flags = new BitSet();
      } catch (IOException e) {
        logger.atWarning().withCause(e).log("Error on %s of %s", ref.getName(), projectName);
        flags = new BitSet();
      }
      tags.add(new Tag(id, flags));
    }
  }

  private void addRef(TagWalk rw, Ref ref) {
    try {
      TagCommit commit = (TagCommit) rw.parseCommit(ref.getObjectId());
      rw.markStart(commit);

      int flag = refs.size();
      commit.refFlags.set(flag);
      refs.put(ref.getName(), new CachedRef(ref, flag));
    } catch (IncorrectObjectTypeException notCommit) {
      // No need to spam the logs.
      // Quite many refs will point to non-commits.
      // For instance, refs from refs/cache-automerge
      // will often end up here.
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Error on %s of %s", ref.getName(), projectName);
    }
  }

  static boolean skip(Ref ref) {
    return ref.isSymbolic()
        || ref.getObjectId() == null
        || PatchSet.isChangeRef(ref.getName())
        || RefNames.isNoteDbMetaRef(ref.getName())
        || ref.getName().startsWith(RefNames.REFS_CACHE_AUTOMERGE);
  }

  private static boolean isTag(Ref ref) {
    return ref.getName().startsWith(Constants.R_TAGS);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("projectName", projectName)
        .add("refs", refs)
        .add("tags", tags)
        .toString();
  }

  static final class Tag extends ObjectIdOwnerMap.Entry {
    @VisibleForTesting final BitSet refFlags;

    Tag(AnyObjectId id, BitSet flags) {
      super(id);
      this.refFlags = flags;
    }

    boolean has(BitSet mask) {
      return refFlags.intersects(mask);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).addValue(name()).add("refFlags", refFlags).toString();
    }
  }

  @VisibleForTesting
  static final class CachedRef extends AtomicReference<ObjectId> {
    private static final long serialVersionUID = 1L;

    final int flag;

    CachedRef(Ref ref, int flag) {
      this(flag, ref.getObjectId());
    }

    CachedRef(int flag, ObjectId id) {
      this.flag = flag;
      set(id);
    }

    @Override
    public String toString() {
      ObjectId id = get();
      return MoreObjects.toStringHelper(this)
          .addValue(id != null ? id.name() : "null")
          .add("flag", flag)
          .toString();
    }
  }

  private static final class TagWalk extends RevWalk {
    TagWalk(Repository git) {
      super(git);
    }

    @Override
    protected TagCommit createCommit(AnyObjectId id) {
      return new TagCommit(id);
    }
  }

  private static final class TagCommit extends RevCommit {
    final BitSet refFlags;

    TagCommit(AnyObjectId id) {
      super(id);
      refFlags = new BitSet();
    }
  }
}
