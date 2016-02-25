// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.common.TimeUtil.roundToSecond;
import static com.google.gerrit.server.notedb.ChangeBundle.Source.NOTE_DB;
import static com.google.gerrit.server.notedb.ChangeBundle.Source.REVIEW_DB;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultiset;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.IntKey;
import com.google.gwtorm.server.OrmException;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A bundle of all entities rooted at a single {@link Change} entity.
 * <p>
 * See the {@link Change} Javadoc for a depiction of this tree. Bundles may be
 * compared using {@link #differencesFrom(ChangeBundle)}, which normalizes out
 * the minor implementation differences between ReviewDb and NoteDb.
 */
public class ChangeBundle {
  public enum Source {
    REVIEW_DB, NOTE_DB;
  }

  public static ChangeBundle fromReviewDb(ReviewDb db, Change.Id id)
      throws OrmException {
    db.changes().beginTransaction(id);
    try {
      return new ChangeBundle(
          db.changes().get(id),
          db.changeMessages().byChange(id),
          db.patchSets().byChange(id),
          db.patchSetApprovals().byChange(id),
          db.patchComments().byChange(id),
          Source.REVIEW_DB);
    } finally {
      db.rollback();
    }
  }

  public static ChangeBundle fromNotes(PatchLineCommentsUtil plcUtil,
      ChangeNotes notes) throws OrmException {
    return new ChangeBundle(
        notes.getChange(),
        notes.getChangeMessages(),
        notes.getPatchSets().values(),
        notes.getApprovals().values(),
        Iterables.concat(
            plcUtil.draftByChange(null, notes),
            plcUtil.publishedByChange(null, notes)),
        Source.NOTE_DB);
  }

  private static Map<ChangeMessage.Key, ChangeMessage> changeMessageMap(
      Iterable<ChangeMessage> in) {
    Map<ChangeMessage.Key, ChangeMessage> out = new TreeMap<>(
        new Comparator<ChangeMessage.Key>() {
          @Override
          public int compare(ChangeMessage.Key a, ChangeMessage.Key b) {
            return ComparisonChain.start()
                .compare(a.getParentKey().get(), b.getParentKey().get())
                .compare(a.get(), b.get())
                .result();
          }
        });
    for (ChangeMessage cm : in) {
      out.put(cm.getKey(), cm);
    }
    return out;
  }

  private static Map<PatchSet.Id, PatchSet> patchSetMap(Iterable<PatchSet> in) {
    Map<PatchSet.Id, PatchSet> out = new TreeMap<>(
        new Comparator<PatchSet.Id>() {
          @Override
          public int compare(PatchSet.Id a, PatchSet.Id b) {
            return patchSetIdChain(a, b).result();
          }
        });
    for (PatchSet ps : in) {
      out.put(ps.getId(), ps);
    }
    return out;
  }

  private static Map<PatchSetApproval.Key, PatchSetApproval>
      patchSetApprovalMap(Iterable<PatchSetApproval> in) {
    Map<PatchSetApproval.Key, PatchSetApproval> out = new TreeMap<>(
        new Comparator<PatchSetApproval.Key>() {
          @Override
          public int compare(PatchSetApproval.Key a, PatchSetApproval.Key b) {
            return patchSetIdChain(a.getParentKey(), b.getParentKey())
                .compare(a.getAccountId().get(), b.getAccountId().get())
                .compare(a.getLabelId(), b.getLabelId())
                .result();
          }
        });
    for (PatchSetApproval psa : in) {
      out.put(psa.getKey(), psa);
    }
    return out;
  }

  private static Map<PatchLineComment.Key, PatchLineComment>
      patchLineCommentMap(Iterable<PatchLineComment> in) {
    Map<PatchLineComment.Key, PatchLineComment> out = new TreeMap<>(
        new Comparator<PatchLineComment.Key>() {
          @Override
          public int compare(PatchLineComment.Key a, PatchLineComment.Key b) {
            Patch.Key pka = a.getParentKey();
            Patch.Key pkb = b.getParentKey();
            return patchSetIdChain(pka.getParentKey(), pkb.getParentKey())
                .compare(pka.get(), pkb.get())
                .compare(a.get(), b.get())
                .result();
          }
        });
    for (PatchLineComment plc : in) {
      out.put(plc.getKey(), plc);
    }
    return out;
  }

  private static ComparisonChain patchSetIdChain(PatchSet.Id a, PatchSet.Id b) {
    return ComparisonChain.start()
        .compare(a.getParentKey().get(), b.getParentKey().get())
        .compare(a.get(), b.get());
  }

  private static void checkColumns(Class<?> clazz, Integer... expected) {
    Set<Integer> ids = new TreeSet<>();
    for (Field f : clazz.getDeclaredFields()) {
      Column col = f.getAnnotation(Column.class);
      if (col != null) {
        ids.add(col.id());
      }
    }
    Set<Integer> expectedIds = Sets.newTreeSet(Arrays.asList(expected));
    checkState(ids.equals(expectedIds),
        "Unexpected column set for %s: %s != %s",
        clazz.getSimpleName(), ids, expectedIds);
  }

  static {
    // Initialization-time checks that the column set hasn't changed since the
    // last time this file was updated.
    checkColumns(Change.Id.class, 1);
    checkColumns(Change.class, 1, 2, 3, 4, 5, 7, 8, 10, 12, 13, 14, 17, 18);
    checkColumns(ChangeMessage.Key.class, 1, 2);
    checkColumns(ChangeMessage.class, 1, 2, 3, 4, 5);
    checkColumns(PatchSet.Id.class, 1, 2);
    checkColumns(PatchSet.class, 1, 2, 3, 4, 5, 6, 8);
    checkColumns(PatchSetApproval.Key.class, 1, 2, 3);
    checkColumns(PatchSetApproval.class, 1, 2, 3);
    checkColumns(PatchLineComment.Key.class, 1, 2);
    checkColumns(PatchLineComment.class, 1, 2, 3, 4, 5, 6, 7, 8, 9);
  }

  private final Change change;
  private final ImmutableMap<ChangeMessage.Key, ChangeMessage> changeMessages;
  private final ImmutableMap<PatchSet.Id, PatchSet> patchSets;
  private final ImmutableMap<PatchSetApproval.Key, PatchSetApproval>
      patchSetApprovals;
  private final ImmutableMap<PatchLineComment.Key, PatchLineComment>
      patchLineComments;
  private final Source source;

  @VisibleForTesting
  ChangeBundle(
      Change change,
      Iterable<ChangeMessage> changeMessages,
      Iterable<PatchSet> patchSets,
      Iterable<PatchSetApproval> patchSetApprovals,
      Iterable<PatchLineComment> patchLineComments,
      Source source) {
    this.change = checkNotNull(change);
    this.changeMessages = ImmutableMap.copyOf(changeMessageMap(changeMessages));
    this.patchSets = ImmutableMap.copyOf(patchSetMap(patchSets));
    this.patchSetApprovals =
        ImmutableMap.copyOf(patchSetApprovalMap(patchSetApprovals));
    this.patchLineComments =
        ImmutableMap.copyOf(patchLineCommentMap(patchLineComments));
    this.source = checkNotNull(source);

    for (ChangeMessage.Key k : this.changeMessages.keySet()) {
      checkArgument(k.getParentKey().equals(change.getId()));
    }
    for (PatchSet.Id id : this.patchSets.keySet()) {
      checkArgument(id.getParentKey().equals(change.getId()));
    }
    for (PatchSetApproval.Key k : this.patchSetApprovals.keySet()) {
      checkArgument(k.getParentKey().getParentKey().equals(change.getId()));
    }
    for (PatchLineComment.Key k : this.patchLineComments.keySet()) {
      checkArgument(k.getParentKey().getParentKey().getParentKey()
          .equals(change.getId()));
    }
  }

  public Change getChange() {
    return change;
  }

  public ImmutableList<String> differencesFrom(ChangeBundle o) {
    List<String> diffs = new ArrayList<>();
    diffChanges(diffs, this, o);
    diffChangeMessages(diffs, this, o);
    diffPatchSets(diffs, this, o);
    diffPatchSetApprovals(diffs, this, o);
    diffPatchLineComments(diffs, this, o);
    return ImmutableList.copyOf(diffs);
  }

  private static void diffChanges(List<String> diffs, ChangeBundle bundleA,
      ChangeBundle bundleB) {
    Change a = bundleA.change;
    Change b = bundleB.change;
    String desc = a.getId().equals(b.getId()) ? describe(a.getId()) : "Changes";
    diffColumns(diffs, Change.class, desc, bundleA, a, bundleB, b);
  }

  private static void diffChangeMessages(List<String> diffs,
      ChangeBundle bundleA, ChangeBundle bundleB) {
    if (bundleA.source == REVIEW_DB && bundleB.source == REVIEW_DB) {
      // Both came from ReviewDb: check all fields exactly.
      Map<ChangeMessage.Key, ChangeMessage> as = bundleA.changeMessages;
      Map<ChangeMessage.Key, ChangeMessage> bs = bundleB.changeMessages;

      for (ChangeMessage.Key k : diffKeySets(diffs, as, bs)) {
        ChangeMessage a = as.get(k);
        ChangeMessage b = bs.get(k);
        String desc = describe(k);
        diffColumns(diffs, ChangeMessage.class, desc, bundleA, a, bundleB, b);
      }
    } else {
      // At least one is from NoteDb, so we need to normalize UUIDs and
      // timestamps for both.
      Multiset<NormalizedChangeMessage> as = bundleA.normalizeChangeMessages();
      Multiset<NormalizedChangeMessage> bs = bundleB.normalizeChangeMessages();
      Set<NormalizedChangeMessage> union = new TreeSet<>();
      for (NormalizedChangeMessage m
          : Iterables.concat(as.elementSet(), bs.elementSet())) {
        union.add(m);
      }
      for (NormalizedChangeMessage m : union) {
        int ac = as.count(m);
        int bc = bs.count(m);
        if (ac != bc) {
          diffs.add("ChangeMessage present "
              + times(ac) + " in A but " + times(bc) + " in B: " + m);
        }
      }
    }
  }

  private static String times(int n) {
    return n + " time" + (n != 1 ? "s" : "");
  }

  private static void diffPatchSets(List<String> diffs, ChangeBundle bundleA,
      ChangeBundle bundleB) {
    Map<PatchSet.Id, PatchSet> as = bundleA.patchSets;
    Map<PatchSet.Id, PatchSet> bs = bundleB.patchSets;
    for (PatchSet.Id id : diffKeySets(diffs, as, bs)) {
      PatchSet a = as.get(id);
      PatchSet b = bs.get(id);
      String desc = describe(id);
      diffColumns(diffs, PatchSet.class, desc, bundleA, a, bundleB, b);
    }
  }

  private static void diffPatchSetApprovals(List<String> diffs,
      ChangeBundle bundleA, ChangeBundle bundleB) {
    Map<PatchSetApproval.Key, PatchSetApproval> as = bundleA.patchSetApprovals;
    Map<PatchSetApproval.Key, PatchSetApproval> bs = bundleB.patchSetApprovals;
    for (PatchSetApproval.Key k : diffKeySets(diffs, as, bs)) {
      PatchSetApproval a = as.get(k);
      PatchSetApproval b = bs.get(k);
      String desc = describe(k);
      diffColumns(diffs, PatchSetApproval.class, desc, bundleA, a, bundleB, b);
    }
  }

  private static void diffPatchLineComments(List<String> diffs,
      ChangeBundle bundleA, ChangeBundle bundleB) {
    Map<PatchLineComment.Key, PatchLineComment> as = bundleA.patchLineComments;
    Map<PatchLineComment.Key, PatchLineComment> bs = bundleB.patchLineComments;
    for (PatchLineComment.Key k : diffKeySets(diffs, as, bs)) {
      PatchLineComment a = as.get(k);
      PatchLineComment b = bs.get(k);
      String desc = describe(k);
      diffColumns(diffs, PatchLineComment.class, desc, bundleA, a, bundleB, b);
    }
  }

  private static <T> Set<T> diffKeySets(List<String> diffs, Map<T, ?> a,
      Map<T, ?> b) {
    Set<T> as = a.keySet();
    Set<T> bs = b.keySet();
    if (as.isEmpty() && bs.isEmpty()) {
      return as;
    }
    String clazz = keyClass((!as.isEmpty() ? as : bs).iterator().next());

    Set<T> aNotB = Sets.difference(as, bs);
    Set<T> bNotA = Sets.difference(bs, as);
    if (aNotB.isEmpty() && bNotA.isEmpty()) {
      return as;
    }
    diffs.add(clazz + " sets differ: " + aNotB + " only in A; "
        + bNotA + " only in B");
    return Sets.intersection(as, bs);
  }

  private static <T> void diffColumns(List<String> diffs, Class<T> clazz,
      String desc, ChangeBundle bundleA, T a, ChangeBundle bundleB, T b) {
    for (Field f : clazz.getDeclaredFields()) {
      Column col = f.getAnnotation(Column.class);
      if (col == null) {
        continue;
      }
      f.setAccessible(true);
      try {
        if (Timestamp.class.isAssignableFrom(f.getType())) {
          diffTimestamps(diffs, desc, bundleA, a, bundleB, b, f.getName());
        } else {
          diffValues(diffs, desc, f.get(a), f.get(b), f.getName());
        }
      } catch (IllegalAccessException e) {
        throw new IllegalArgumentException(e);
      }
    }
  }

  private static void diffTimestamps(List<String> diffs, String desc,
      ChangeBundle bundleA, Object a, ChangeBundle bundleB, Object b,
      String field) {
    checkArgument(a.getClass() == b.getClass());
    Class<?> clazz = a.getClass();

    Timestamp ta, tb;
    try {
      Field f = clazz.getDeclaredField(field);
      checkArgument(f.getAnnotation(Column.class) != null);
      f.setAccessible(true);
      ta = (Timestamp) f.get(a);
      tb = (Timestamp) f.get(b);
    } catch (IllegalAccessException | NoSuchFieldException
        | SecurityException e) {
      throw new IllegalArgumentException(e);
    }
    if (bundleA.source == REVIEW_DB && bundleB.source == NOTE_DB) {
      ta = roundToSecond(ta);
      checkArgument(tb.equals(roundToSecond(tb)),
          "%s from NoteDb has non-rounded %s timestamp: %s",
          desc, field, tb);
    } else if (bundleA.source == NOTE_DB && bundleB.source == REVIEW_DB) {
      tb = roundToSecond(tb);
      checkArgument(ta.equals(roundToSecond(ta)),
          "%s from NoteDb has non-rounded %s timestamp: %s",
          desc, field, ta);
    }
    diffValues(diffs, desc, ta, tb, field);
  }

  private static void diffValues(List<String> diffs, String desc, Object va,
      Object vb, String name) {
    if (!Objects.equals(va, vb)) {
      diffs.add(
          name + " differs for " + desc + ": {" + va + "} != {" + vb + "}");
    }
  }

  private static String describe(Object key) {
    return keyClass(key) + " " + key;
  }

  private static String keyClass(Object obj) {
    Class<?> clazz = obj.getClass();
    String name = clazz.getSimpleName();
    checkArgument(name.equals("Key") || name.equals("Id"),
        "not an Id/Key class: %s", name);
    return clazz.getEnclosingClass().getSimpleName() + "." + name;
  }

  @AutoValue
  static abstract class NormalizedChangeMessage
      implements Comparable<NormalizedChangeMessage> {
    private static final Ordering<Comparable<?>> NULLS_FIRST =
        Ordering.natural().nullsFirst();

    private static NormalizedChangeMessage create(ChangeMessage msg) {
      return new AutoValue_ChangeBundle_NormalizedChangeMessage(
          msg.getKey().getParentKey(),
          msg.getAuthor(),
          TimeUtil.roundToSecond(msg.getWrittenOn()),
          msg.getMessage(),
          msg.getPatchSetId());
    }

    private static Integer intKey(IntKey<?> k) {
      return k != null ? k.get() : null;
    }

    abstract Change.Id changeId();
    @Nullable abstract Account.Id author();
    abstract Timestamp writtenOn();
    @Nullable abstract String message();
    @Nullable abstract PatchSet.Id patchset();

    @Override
    public int compareTo(NormalizedChangeMessage o) {
      return ComparisonChain.start()
          .compare(changeId().get(), o.changeId().get())
          .compare(intKey(patchset()), intKey(o.patchset()), NULLS_FIRST)
          .compare(writtenOn(), o.writtenOn())
          .compare(intKey(author()), intKey(o.author()), NULLS_FIRST)
          .compare(message(), o.message(), NULLS_FIRST)
          .result();
    }
  }

  private Multiset<NormalizedChangeMessage> normalizeChangeMessages() {
    Multiset<NormalizedChangeMessage> normalized = TreeMultiset.create();
    for (ChangeMessage msg : changeMessages.values()) {
      normalized.add(NormalizedChangeMessage.create(msg));
    }
    return normalized;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ChangeBundle)) {
      return false;
    }
    return differencesFrom((ChangeBundle) o).isEmpty();
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        change.getId(),
        normalizeChangeMessages(),
        patchSets.keySet(),
        patchSetApprovals.keySet(),
        patchLineComments.keySet());
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{id=" + change.getId()
        + ", ChangeMessage[" + changeMessages.size() + "]"
        + ", PatchSet[" + patchSets.size() + "]"
        + ", PatchSetApproval[" + patchSetApprovals.size() + "]"
        + ", PatchLineComment[" + patchLineComments.size() + "]"
        + "}";
  }
}
