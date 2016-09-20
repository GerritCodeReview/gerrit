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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.common.TimeUtil.roundToSecond;
import static com.google.gerrit.reviewdb.server.ReviewDbUtil.intKeyOrdering;
import static com.google.gerrit.server.notedb.ChangeBundle.Source.NOTE_DB;
import static com.google.gerrit.server.notedb.ChangeBundle.Source.REVIEW_DB;

import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilderImpl;
import com.google.gwtorm.client.Column;
import com.google.gwtorm.server.OrmException;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
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
        notes.getReviewers(),
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

  // Unlike the *Map comparators, which are intended to make key lists diffable,
  // this comparator sorts first on timestamp, then on every other field.
  private static final Ordering<ChangeMessage> CHANGE_MESSAGE_ORDER =
      new Ordering<ChangeMessage>() {
        final Ordering<Comparable<?>> nullsFirst =
            Ordering.natural().nullsFirst();

        @Override
        public int compare(ChangeMessage a, ChangeMessage b) {
          return ComparisonChain.start()
              .compare(a.getWrittenOn(), b.getWrittenOn())
              .compare(a.getKey().getParentKey().get(),
                  b.getKey().getParentKey().get())
              .compare(psId(a), psId(b), nullsFirst)
              .compare(a.getAuthor(), b.getAuthor(), intKeyOrdering())
              .compare(a.getMessage(), b.getMessage(), nullsFirst)
              .result();
        }

        private Integer psId(ChangeMessage m) {
          return m.getPatchSetId() != null ? m.getPatchSetId().get() : null;
        }
      };

  private static ImmutableList<ChangeMessage> changeMessageList(
      Iterable<ChangeMessage> in) {
    return CHANGE_MESSAGE_ORDER.immutableSortedCopy(in);
  }

  private static TreeMap<PatchSet.Id, PatchSet> patchSetMap(Iterable<PatchSet> in) {
    TreeMap<PatchSet.Id, PatchSet> out = new TreeMap<>(
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

    checkColumns(Change.class,
        1, 2, 3, 4, 5, 7, 8, 10, 12, 13, 14, 17, 18, 101);
    checkColumns(ChangeMessage.Key.class, 1, 2);
    checkColumns(ChangeMessage.class, 1, 2, 3, 4, 5, 6);
    checkColumns(PatchSet.Id.class, 1, 2);
    checkColumns(PatchSet.class, 1, 2, 3, 4, 5, 6, 8);
    checkColumns(PatchSetApproval.Key.class, 1, 2, 3);
    checkColumns(PatchSetApproval.class, 1, 2, 3, 6);
    checkColumns(PatchLineComment.Key.class, 1, 2);
    checkColumns(PatchLineComment.class, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
  }

  private final Change change;
  private final ImmutableList<ChangeMessage> changeMessages;
  private final ImmutableSortedMap<PatchSet.Id, PatchSet> patchSets;
  private final ImmutableMap<PatchSetApproval.Key, PatchSetApproval>
      patchSetApprovals;
  private final ImmutableMap<PatchLineComment.Key, PatchLineComment>
      patchLineComments;
  private final ReviewerSet reviewers;
  private final Source source;

  public ChangeBundle(
      Change change,
      Iterable<ChangeMessage> changeMessages,
      Iterable<PatchSet> patchSets,
      Iterable<PatchSetApproval> patchSetApprovals,
      Iterable<PatchLineComment> patchLineComments,
      ReviewerSet reviewers,
      Source source) {
    this.change = checkNotNull(change);
    this.changeMessages = changeMessageList(changeMessages);
    this.patchSets = ImmutableSortedMap.copyOfSorted(patchSetMap(patchSets));
    this.patchSetApprovals =
        ImmutableMap.copyOf(patchSetApprovalMap(patchSetApprovals));
    this.patchLineComments =
        ImmutableMap.copyOf(patchLineCommentMap(patchLineComments));
    this.reviewers = checkNotNull(reviewers);
    this.source = checkNotNull(source);

    for (ChangeMessage m : this.changeMessages) {
      checkArgument(m.getKey().getParentKey().equals(change.getId()));
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

  public ImmutableCollection<ChangeMessage> getChangeMessages() {
    return changeMessages;
  }

  public ImmutableCollection<PatchSet> getPatchSets() {
    return patchSets.values();
  }

  public ImmutableCollection<PatchSetApproval> getPatchSetApprovals() {
    return patchSetApprovals.values();
  }

  public ImmutableCollection<PatchLineComment> getPatchLineComments() {
    return patchLineComments.values();
  }

  public ReviewerSet getReviewers() {
    return reviewers;
  }

  public Source getSource() {
    return source;
  }

  public ImmutableList<String> differencesFrom(ChangeBundle o) {
    List<String> diffs = new ArrayList<>();
    diffChanges(diffs, this, o);
    diffChangeMessages(diffs, this, o);
    diffPatchSets(diffs, this, o);
    diffPatchSetApprovals(diffs, this, o);
    diffReviewers(diffs, this, o);
    diffPatchLineComments(diffs, this, o);
    return ImmutableList.copyOf(diffs);
  }

  private Timestamp getFirstPatchSetTime() {
    if (patchSets.isEmpty()) {
      return change.getCreatedOn();
    }
    return patchSets.firstEntry().getValue().getCreatedOn();
  }

  private Timestamp getLatestTimestamp() {
    Ordering<Timestamp> o = Ordering.natural().nullsFirst();
    Timestamp ts = null;
    for (ChangeMessage cm : getChangeMessages()) {
      ts = o.max(ts, cm.getWrittenOn());
    }
    for (PatchSet ps : getPatchSets()) {
      ts = o.max(ts, ps.getCreatedOn());
    }
    for (PatchSetApproval psa : getPatchSetApprovals()) {
      ts = o.max(ts, psa.getGranted());
    }
    for (PatchLineComment plc : getPatchLineComments()) {
      // Ignore draft comments, as they do not show up in the change meta graph.
      if (plc.getStatus() != PatchLineComment.Status.DRAFT) {
        ts = o.max(ts, plc.getWrittenOn());
      }
    }
    return firstNonNull(ts, change.getLastUpdatedOn());
  }

  private Map<PatchSetApproval.Key, PatchSetApproval>
      filterPatchSetApprovals() {
    return limitToValidPatchSets(
        patchSetApprovals, PatchSetApproval.Key::getParentKey);
  }

  private Map<PatchLineComment.Key, PatchLineComment>
      filterPatchLineComments() {
    return limitToValidPatchSets(
        patchLineComments,
        k -> k.getParentKey().getParentKey());
  }

  private <K, V> Map<K, V> limitToValidPatchSets(Map<K, V> in,
      Function<K, PatchSet.Id> func) {
    return Maps.filterKeys(
        in, Predicates.compose(validPatchSetPredicate(), func));
  }

  private Predicate<PatchSet.Id> validPatchSetPredicate() {
    Predicate<PatchSet.Id> upToCurrent = upToCurrentPredicate();
    return p -> upToCurrent.apply(p) && patchSets.containsKey(p);
  }

  private Collection<ChangeMessage> filterChangeMessages() {
    final Predicate<PatchSet.Id> validPatchSet = validPatchSetPredicate();
    return Collections2.filter(changeMessages, m -> {
          PatchSet.Id psId = m.getPatchSetId();
          if (psId == null) {
            return true;
          }
          return validPatchSet.apply(psId);
        });
  }

  private Predicate<PatchSet.Id> upToCurrentPredicate() {
    PatchSet.Id current = change.currentPatchSetId();
    if (current == null) {
      return Predicates.alwaysFalse();
    }
    final int max = current.get();
    return new Predicate<PatchSet.Id>() {
      @Override
      public boolean apply(PatchSet.Id in) {
        return in.get() <= max;
      }
    };
  }

  private Map<PatchSet.Id, PatchSet> filterPatchSets() {
    return Maps.filterKeys(patchSets, upToCurrentPredicate());
  }

  private static void diffChanges(List<String> diffs, ChangeBundle bundleA,
      ChangeBundle bundleB) {
    Change a = bundleA.change;
    Change b = bundleB.change;
    String desc = a.getId().equals(b.getId()) ? describe(a.getId()) : "Changes";

    boolean excludeCreatedOn = false;
    boolean excludeCurrentPatchSetId = false;
    boolean excludeTopic = false;
    Timestamp aUpdated = a.getLastUpdatedOn();
    Timestamp bUpdated = b.getLastUpdatedOn();

    boolean excludeSubject = false;
    boolean excludeOrigSubj = false;
    // Subject is not technically a nullable field, but we observed some null
    // subjects in the wild on googlesource.com, so treat null as empty.
    String aSubj = Strings.nullToEmpty(a.getSubject());
    String bSubj = Strings.nullToEmpty(b.getSubject());

    // Allow created timestamp in NoteDb to be either the created timestamp of
    // the change, or the timestamp of the first remaining patch set.
    //
    // Ignore subject if the NoteDb subject starts with the ReviewDb subject.
    // The NoteDb subject is read directly from the commit, whereas the ReviewDb
    // subject historically may have been truncated to fit in a SQL varchar
    // column.
    //
    // Ignore original subject on the ReviewDb side when comparing to NoteDb.
    // This field may have any number of values:
    //  - It may be null, if the change has had no new patch sets pushed since
    //    migrating to schema 103.
    //  - It may match the first patch set subject, if the change was created
    //    after migrating to schema 103.
    //  - It may match the subject of the first patch set that was pushed after
    //    the migration to schema 103, even though that is neither the subject
    //    of the first patch set nor the subject of the last patch set. (See
    //    Change#setCurrentPatchSet as of 43b10f86 for this behavior.) This
    //    subject of an intermediate patch set is not available to the
    //    ChangeBundle; we would have to get the subject from the repo, which is
    //    inconvenient at this point.
    //
    // Ignore original subject on the ReviewDb side if it equals the subject of
    // the current patch set.
    //
    // For all of the above subject comparisons, first trim any leading spaces
    // from the NoteDb strings. (We actually do represent the leading spaces
    // faithfully during conversion, but JGit's FooterLine parser trims them
    // when reading.)
    //
    // Ignore empty topic on the ReviewDb side if it is null on the NoteDb side.
    //
    // Ignore currentPatchSetId on NoteDb side if ReviewDb does not point to a
    // valid patch set.
    //
    // Use max timestamp of all ReviewDb entities when comparing with NoteDb.
    if (bundleA.source == REVIEW_DB && bundleB.source == NOTE_DB) {
      excludeCreatedOn = !timestampsDiffer(
          bundleA, bundleA.getFirstPatchSetTime(), bundleB, b.getCreatedOn());
      aSubj = cleanReviewDbSubject(aSubj);
      excludeCurrentPatchSetId =
          !bundleA.validPatchSetPredicate().apply(a.currentPatchSetId());
      excludeSubject = bSubj.startsWith(aSubj) || excludeCurrentPatchSetId;
      excludeOrigSubj = true;
      String aTopic = trimLeadingOrNull(a.getTopic());
      excludeTopic = Objects.equals(aTopic, b.getTopic())
          || "".equals(aTopic) && b.getTopic() == null;
      aUpdated = bundleA.getLatestTimestamp();
    } else if (bundleA.source == NOTE_DB && bundleB.source == REVIEW_DB) {
      excludeCreatedOn = !timestampsDiffer(
          bundleA, a.getCreatedOn(), bundleB, bundleB.getFirstPatchSetTime());
      bSubj = cleanReviewDbSubject(bSubj);
      excludeCurrentPatchSetId =
          !bundleB.validPatchSetPredicate().apply(b.currentPatchSetId());
      excludeSubject = aSubj.startsWith(bSubj) || excludeCurrentPatchSetId;
      excludeOrigSubj = true;
      String bTopic = trimLeadingOrNull(b.getTopic());
      excludeTopic = Objects.equals(bTopic, a.getTopic())
          || a.getTopic() == null && "".equals(bTopic);
      bUpdated = bundleB.getLatestTimestamp();
    }

    String subjectField = "subject";
    String updatedField = "lastUpdatedOn";
    List<String> exclude = Lists.newArrayList(
        subjectField, updatedField, "noteDbState", "rowVersion");
    if (excludeCreatedOn) {
      exclude.add("createdOn");
    }
    if (excludeCurrentPatchSetId) {
      exclude.add("currentPatchSetId");
    }
    if (excludeOrigSubj) {
      exclude.add("originalSubject");
    }
    if (excludeTopic) {
      exclude.add("topic");
    }
    diffColumnsExcluding(diffs, Change.class, desc, bundleA, a, bundleB, b,
        exclude);

    // Allow last updated timestamps to either be exactly equal (within slop),
    // or the NoteDb timestamp to be equal to the latest entity timestamp in the
    // whole ReviewDb bundle (within slop).
    if (timestampsDiffer(bundleA, a.getLastUpdatedOn(),
          bundleB, b.getLastUpdatedOn())) {
      diffTimestamps(diffs, desc, bundleA, aUpdated, bundleB, bUpdated,
          "effective last updated time");
    }
    if (!excludeSubject) {
      diffValues(diffs, desc, aSubj, bSubj, subjectField);
    }
  }

  private static String trimLeadingOrNull(String s) {
    return s != null ? CharMatcher.whitespace().trimLeadingFrom(s) : null;
  }

  private static String cleanReviewDbSubject(String s) {
    s = CharMatcher.is(' ').trimLeadingFrom(s);

    // An old JGit bug failed to extract subjects from commits with "\r\n"
    // terminators: https://bugs.eclipse.org/bugs/show_bug.cgi?id=400707
    // Changes created with this bug may have "\r\n" converted to "\r " and the
    // entire commit in the subject. The version of JGit used to read NoteDb
    // changes parses these subjects correctly, so we need to clean up old
    // ReviewDb subjects before comparing.
    int rn = s.indexOf("\r \r ");
    if (rn >= 0) {
      s = s.substring(0, rn);
    }
    return s;
  }

  /**
   * Set of fields that must always exactly match between ReviewDb and NoteDb.
   * <p>
   * Used to limit the worst-case quadratic search when pairing off matching
   * messages below.
   */
  @AutoValue
  abstract static class ChangeMessageCandidate {
    static ChangeMessageCandidate create(ChangeMessage cm) {
      return new AutoValue_ChangeBundle_ChangeMessageCandidate(
          cm.getAuthor(),
          cm.getMessage(),
          cm.getTag());
    }

    @Nullable abstract Account.Id author();
    @Nullable abstract String message();
    @Nullable abstract String tag();

    // Exclude:
    //  - patch set, which may be null on ReviewDb side but not NoteDb
    //  - UUID, which is always different between ReviewDb and NoteDb
    //  - writtenOn, which is fuzzy
  }

  private static void diffChangeMessages(List<String> diffs,
      ChangeBundle bundleA, ChangeBundle bundleB) {
    if (bundleA.source == REVIEW_DB && bundleB.source == REVIEW_DB) {
      // Both came from ReviewDb: check all fields exactly.
      Map<ChangeMessage.Key, ChangeMessage> as =
          changeMessageMap(bundleA.filterChangeMessages());
      Map<ChangeMessage.Key, ChangeMessage> bs =
          changeMessageMap(bundleB.filterChangeMessages());

      for (ChangeMessage.Key k : diffKeySets(diffs, as, bs)) {
        ChangeMessage a = as.get(k);
        ChangeMessage b = bs.get(k);
        String desc = describe(k);
        diffColumns(diffs, ChangeMessage.class, desc, bundleA, a, bundleB, b);
      }
      return;
    }
    Change.Id id = bundleA.getChange().getId();
    checkArgument(id.equals(bundleB.getChange().getId()));

    // Try to pair up matching ChangeMessages from each side, and succeed only
    // if both collections are empty at the end. Quadratic in the worst case,
    // but easy to reason about.
    List<ChangeMessage> as = new LinkedList<>(bundleA.filterChangeMessages());

    Multimap<ChangeMessageCandidate, ChangeMessage> bs =
        LinkedListMultimap.create();
    for (ChangeMessage b : bundleB.filterChangeMessages()) {
      bs.put(ChangeMessageCandidate.create(b), b);
    }

    Iterator<ChangeMessage> ait = as.iterator();
    A: while (ait.hasNext()) {
      ChangeMessage a = ait.next();
      Iterator<ChangeMessage> bit =
          bs.get(ChangeMessageCandidate.create(a)).iterator();
      while (bit.hasNext()) {
        ChangeMessage b = bit.next();
        if (changeMessagesMatch(bundleA, a, bundleB, b)) {
          ait.remove();
          bit.remove();
          continue A;
        }
      }
    }

    if (as.isEmpty() && bs.isEmpty()) {
      return;
    }
    StringBuilder sb = new StringBuilder("ChangeMessages differ for Change.Id ")
        .append(id).append('\n');
    if (!as.isEmpty()) {
      sb.append("Only in A:");
      for (ChangeMessage cm : as) {
        sb.append("\n  ").append(cm);
      }
      if (!bs.isEmpty()) {
        sb.append('\n');
      }
    }
    if (!bs.isEmpty()) {
      sb.append("Only in B:");
      for (ChangeMessage cm : CHANGE_MESSAGE_ORDER.sortedCopy(bs.values())) {
        sb.append("\n  ").append(cm);
      }
    }
    diffs.add(sb.toString());
  }

  private static boolean changeMessagesMatch(
      ChangeBundle bundleA, ChangeMessage a,
      ChangeBundle bundleB, ChangeMessage b) {
    List<String> tempDiffs = new ArrayList<>();
    String temp = "temp";

    boolean excludePatchSet = false;
    if (bundleA.source == REVIEW_DB && bundleB.source == NOTE_DB) {
      excludePatchSet = a.getPatchSetId() == null;
    } else if (bundleA.source == NOTE_DB && bundleB.source == REVIEW_DB) {
      excludePatchSet = b.getPatchSetId() == null;
    }

    List<String> exclude = Lists.newArrayList("key");
    if (excludePatchSet) {
      exclude.add("patchset");
    }

    diffColumnsExcluding(
        tempDiffs, ChangeMessage.class, temp, bundleA, a, bundleB, b, exclude);
    return tempDiffs.isEmpty();
  }

  private static void diffPatchSets(List<String> diffs, ChangeBundle bundleA,
      ChangeBundle bundleB) {
    Map<PatchSet.Id, PatchSet> as = bundleA.filterPatchSets();
    Map<PatchSet.Id, PatchSet> bs = bundleB.filterPatchSets();
    for (PatchSet.Id id : diffKeySets(diffs, as, bs)) {
      PatchSet a = as.get(id);
      PatchSet b = bs.get(id);
      String desc = describe(id);
      String pushCertField = "pushCertificate";
      diffColumnsExcluding(diffs, PatchSet.class, desc, bundleA, a, bundleB, b,
          pushCertField);
      diffValues(diffs, desc, trimPushCert(a), trimPushCert(b), pushCertField);
    }
  }

  private static String trimPushCert(PatchSet ps) {
    if (ps.getPushCertificate() == null) {
      return null;
    }
    return CharMatcher.is('\n').trimTrailingFrom(ps.getPushCertificate());
  }

  private static void diffPatchSetApprovals(List<String> diffs,
      ChangeBundle bundleA, ChangeBundle bundleB) {
    Map<PatchSetApproval.Key, PatchSetApproval> as =
          bundleA.filterPatchSetApprovals();
    Map<PatchSetApproval.Key, PatchSetApproval> bs =
        bundleB.filterPatchSetApprovals();
    for (PatchSetApproval.Key k : diffKeySets(diffs, as, bs)) {
      PatchSetApproval a = as.get(k);
      PatchSetApproval b = bs.get(k);
      String desc = describe(k);
      diffColumns(diffs, PatchSetApproval.class, desc, bundleA, a, bundleB, b);
    }
  }

  private static void diffReviewers(List<String> diffs,
      ChangeBundle bundleA, ChangeBundle bundleB) {
    diffSets(
        diffs, bundleA.reviewers.all(), bundleB.reviewers.all(), "reviewer");
  }

  private static void diffPatchLineComments(List<String> diffs,
      ChangeBundle bundleA, ChangeBundle bundleB) {
    Map<PatchLineComment.Key, PatchLineComment> as =
        bundleA.filterPatchLineComments();
    Map<PatchLineComment.Key, PatchLineComment> bs =
        bundleB.filterPatchLineComments();
    for (PatchLineComment.Key k : diffKeySets(diffs, as, bs)) {
      PatchLineComment a = as.get(k);
      PatchLineComment b = bs.get(k);
      String desc = describe(k);
      diffColumns(diffs, PatchLineComment.class, desc, bundleA, a, bundleB, b);
    }
  }

  private static <T> Set<T> diffKeySets(List<String> diffs, Map<T, ?> a,
      Map<T, ?> b) {
    if (a.isEmpty() && b.isEmpty()) {
      return a.keySet();
    }
    String clazz =
        keyClass((!a.isEmpty() ? a.keySet() : b.keySet()).iterator().next());
    return diffSets(diffs, a.keySet(), b.keySet(), clazz);
  }

  private static <T> Set<T> diffSets(List<String> diffs, Set<T> as,
      Set<T> bs, String desc) {
    if (as.isEmpty() && bs.isEmpty()) {
      return as;
    }

    Set<T> aNotB = Sets.difference(as, bs);
    Set<T> bNotA = Sets.difference(bs, as);
    if (aNotB.isEmpty() && bNotA.isEmpty()) {
      return as;
    }
    diffs.add(desc + " sets differ: " + aNotB + " only in A; "
        + bNotA + " only in B");
    return Sets.intersection(as, bs);
  }

  private static <T> void diffColumns(List<String> diffs, Class<T> clazz,
      String desc, ChangeBundle bundleA, T a, ChangeBundle bundleB, T b) {
    diffColumnsExcluding(diffs, clazz, desc, bundleA, a, bundleB, b);
  }

  private static <T> void diffColumnsExcluding(List<String> diffs,
      Class<T> clazz, String desc, ChangeBundle bundleA, T a,
      ChangeBundle bundleB, T b, String... exclude) {
    diffColumnsExcluding(diffs, clazz, desc, bundleA, a, bundleB, b,
        Arrays.asList(exclude));
  }

  private static <T> void diffColumnsExcluding(List<String> diffs,
      Class<T> clazz, String desc, ChangeBundle bundleA, T a,
      ChangeBundle bundleB, T b, Iterable<String> exclude) {
    Set<String> toExclude = Sets.newLinkedHashSet(exclude);
    for (Field f : clazz.getDeclaredFields()) {
      Column col = f.getAnnotation(Column.class);
      if (col == null) {
        continue;
      } else if (toExclude.remove(f.getName())) {
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
    checkArgument(toExclude.isEmpty(),
        "requested columns to exclude not present in %s: %s",
        clazz.getSimpleName(), toExclude);
  }

  private static void diffTimestamps(List<String> diffs, String desc,
      ChangeBundle bundleA, Object a, ChangeBundle bundleB, Object b,
      String field) {
    checkArgument(a.getClass() == b.getClass());
    Class<?> clazz = a.getClass();

    Timestamp ta;
    Timestamp tb;
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
    diffTimestamps(diffs, desc, bundleA, ta, bundleB, tb, field);
  }

  private static void diffTimestamps(List<String> diffs, String desc,
      ChangeBundle bundleA, Timestamp ta, ChangeBundle bundleB, Timestamp tb,
      String fieldDesc) {
    if (bundleA.source == bundleB.source || ta == null || tb == null) {
      diffValues(diffs, desc, ta, tb, fieldDesc);
    } else if (bundleA.source == NOTE_DB) {
      diffTimestamps(
          diffs, desc,
          bundleA.getChange(), ta,
          bundleB.getChange(), tb,
          fieldDesc);
    } else {
      diffTimestamps(
          diffs, desc,
          bundleB.getChange(), tb,
          bundleA.getChange(), ta,
          fieldDesc);
    }
  }

  private static boolean timestampsDiffer(ChangeBundle bundleA, Timestamp ta,
      ChangeBundle bundleB, Timestamp tb) {
    List<String> tempDiffs = new ArrayList<>(1);
    diffTimestamps(tempDiffs, "temp", bundleA, ta, bundleB, tb, "temp");
    return !tempDiffs.isEmpty();
  }

  private static void diffTimestamps(List<String> diffs, String desc,
      Change changeFromNoteDb, Timestamp tsFromNoteDb,
      Change changeFromReviewDb, Timestamp tsFromReviewDb,
      String field) {
    // Because ChangeRebuilder may batch events together that are several
    // seconds apart, the timestamp in NoteDb may actually be several seconds
    // *earlier* than the timestamp in ReviewDb that it was converted from.
    checkArgument(tsFromNoteDb.equals(roundToSecond(tsFromNoteDb)),
        "%s from NoteDb has non-rounded %s timestamp: %s",
        desc, field, tsFromNoteDb);

    if (tsFromReviewDb.before(changeFromReviewDb.getCreatedOn())
        && tsFromNoteDb.equals(changeFromNoteDb.getCreatedOn())) {
      // Timestamp predates change creation. These are truncated to change
      // creation time during NoteDb conversion, so allow this if the timestamp
      // in NoteDb matches the createdOn time in NoteDb.
      return;
    }


    long delta = tsFromReviewDb.getTime() - tsFromNoteDb.getTime();
    long max = ChangeRebuilderImpl.MAX_WINDOW_MS;
    if (delta < 0 || delta > max) {
      diffs.add(
          field + " differs for " + desc + " in NoteDb vs. ReviewDb:"
          + " {" + tsFromNoteDb + "} != {" + tsFromReviewDb + "}");
    }
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
    checkArgument(name.endsWith("Key") || name.endsWith("Id"),
        "not an Id/Key class: %s", name);
    if (name.equals("Key") || name.equals("Id")) {
      return clazz.getEnclosingClass().getSimpleName() + "." + name;
    } else if (name.startsWith("AutoValue_")) {
      return name.substring(name.lastIndexOf('_') + 1);
    }
    return name;
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
