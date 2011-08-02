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

package com.google.gerrit.server;

import com.google.gerrit.reviewdb.AtomicEntry;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.git.MergeOp;
import com.google.gwtorm.client.OrmException;

import org.eclipse.jgit.lib.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class AtomicUtil {

  /**
   * Tells whether the change is part of an atomic group or not.
   *
   * @param db
   * @param change
   * @return
   * @throws OrmException
   */
  public static boolean isAtomic(ReviewDb db, Change change)
      throws OrmException {

    List<AtomicEntry> entries = new ArrayList<AtomicEntry>();

    entries = db.atomicEntries().bySourceChangeId(change.getId()).toList();
    entries.addAll(db.atomicEntries().bySuperChangeId(change.getId()).toList());

    return entries.size() > 0;
  }

  /**
   * Tells whether the change is source of an atomic group or not.
   *
   * @param db
   * @param change
   * @return
   * @throws OrmException
   */
  public static boolean isSource(ReviewDb db, ObjectId objectId)
      throws OrmException {
    List<AtomicEntry> entries = new ArrayList<AtomicEntry>();
    entries = db.atomicEntries().bySourceSha1(objectId.getName()).toList();
    return entries.size() > 0;
  }

  /**
   * Returns the list with all changes within the atomic group which the change
   * passed belongs to. If any change atomic within the group is unknown, which
   * means gerrit only knows its sha1, null is returned.
   *
   * @param db
   * @param change
   * @return
   * @throws OrmException
   */
  public static List<Change> getAtomicGroupByAnyMember(final ReviewDb db,
      final Change change) throws OrmException {

    // Find out who is the top super change
    List<AtomicEntry> entry =
        db.atomicEntries().bySourceChangeId(change.getId()).toList();

    Change.Id topChangeId = change.getId();

    if (entry.size() > 0) {
      AtomicEntry sup = entry.get(0);
      while (sup != null) {
        entry =
            db.atomicEntries().bySourceChangeId(sup.getId().getParentKey())
                .toList();
        if (entry.size() > 0) {
          sup = entry.get(0);
        } else {
          topChangeId = sup.getId().getParentKey();
          sup = null;
        }
      }
    }

    return getAtomicGroupBySuperChange(db, topChangeId);
  }

  /**
   * Returns the list with all changes within the atomic group the change passed
   * is the super change. Null is returned in the case of any change is unknown
   * for gerrit environment, which means gerrit does know only the sha1 of that
   * commit, but does not the change correspondent to that sha1.
   *
   * @param db
   * @param superChangeId
   * @return
   * @throws OrmException
   */
  public static List<Change> getAtomicGroupBySuperChange(final ReviewDb db,
      final Change.Id superChangeId) throws OrmException {
    final List<Change> atomicGroup = new ArrayList<Change>();

    final Change superChange = db.changes().get(superChangeId);
    atomicGroup.add(superChange);

    List<AtomicEntry> supList =
        db.atomicEntries().bySuperChangeId(superChangeId).toList();

    for (AtomicEntry entry : supList) {
      if (entry.getSourceChangeId() != null) {
        final List<Change> subList =
            getAtomicGroupBySuperChange(db, entry.getSourceChangeId());
        if (subList != null) {
          atomicGroup.addAll(subList);
        } else {
          return null;
        }
      } else {
        return null;
      }
    }

    return atomicGroup;
  }

  /**
   * It gets one change instance having submitted pending super status and
   * involved in atomic group having as "super" change the same one that a
   * specified change.
   *
   * @param db ReviewDb instance.
   * @param change Specified change defining the common "super" change.
   * @return Change instance if one found with conditions required.
   * @throws OrmException Error reading store data of changes and atomic
   *         entries.
   */
  public static Change getOnePendingSameSuper(final ReviewDb db,
      final Change change) throws OrmException {
    Change onePendingSameSuper = null;

    final List<AtomicEntry> entriesChangeAsSource =
        db.atomicEntries().bySourceChangeId(change.getId()).toList();

    if (!entriesChangeAsSource.isEmpty()) {
      final List<AtomicEntry> entriesToSameSuper =
          db.atomicEntries().bySuperChangeId(
              entriesChangeAsSource.get(0).getId().getParentKey()).toList();

      for (final AtomicEntry entry : entriesToSameSuper) {
        final Change c = db.changes().get(entry.getSourceChangeId());
        if (c.getStatus() == Change.Status.SUBMITTED_PENDING_ATOMIC) {
          onePendingSameSuper = c;
          break;
        }
      }
    }

    return onePendingSameSuper;
  }

  /**
   * Check if all changes within the atomic group of the change passed is in
   * submitted or submitted pending_sources or submitted_pending_super state.
   * Excepting the argument change.
   *
   * @param db
   * @param change
   * @return
   * @throws OrmException
   */
  public static boolean isAllSubmitted(ReviewDb db, Change change)
      throws OrmException {
    List<Change> atomicGroup = getAtomicGroupByAnyMember(db, change);
    if (atomicGroup != null) {
      for (Change entryChange : atomicGroup) {
        if (entryChange.getStatus() != Change.Status.SUBMITTED
            && entryChange.getStatus() != Change.Status.SUBMITTED_PENDING_ATOMIC) {
          if (!entryChange.equals(change)) {
            // do not consider the own attribute
            // change status
            return false;
          }
        }
      }
    } else {
      return false;
    }
    return true;
  }

  /**
   * Check if all changes which belongs to the atomic group of the change passed
   * is mergeable
   *
   * @param db
   * @param change
   * @return
   * @throws OrmException
   */
  public static boolean isAllMergeable(final MergeOp.Factory opFactory,
      final ReviewDb db, final Change change) throws OrmException {
    for (Change entryChange : getAtomicGroupByAnyMember(db, change)) {
      ChangeUtil.testMerge(opFactory, entryChange);
      if (!entryChange.isMergeable()) {
        return false;
      }
    }
    return true;
  }

}
