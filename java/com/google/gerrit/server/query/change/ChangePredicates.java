// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.index.change.ChangeField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Predicates that match against {@link ChangeData}. */
public class ChangePredicates {
  private ChangePredicates() {}

  /**
   * Returns a predicate that matches changes where the provided {@link Account.Id} is in the
   * attention set.
   */
  public static Predicate<ChangeData> attentionSet(Account.Id id) {
    return new ChangeIndexPredicate(ChangeField.ATTENTION_SET_USERS, id.toString());
  }

  /**
   * Returns a predicate that matches changes that are assigned to the provided {@link Account.Id}.
   */
  public static Predicate<ChangeData> assignee(Account.Id id) {
    return new ChangeIndexPredicate(ChangeField.ASSIGNEE, id.toString());
  }

  /**
   * Returns a predicate that matches changes that are a revert of the provided {@link Change.Id}.
   */
  public static Predicate<ChangeData> revertOf(Change.Id revertOf) {
    return new ChangeIndexPredicate(ChangeField.REVERT_OF, revertOf.toString());
  }

  /**
   * Returns a predicate that matches changes that have a comment authored by the provided {@link
   * Account.Id}.
   */
  public static Predicate<ChangeData> commentBy(Account.Id id) {
    return new ChangeIndexPredicate(ChangeField.COMMENTBY, id.toString());
  }

  /**
   * Returns a predicate that matches changes where the provided {@link Account.Id} has a pending
   * change edit.
   */
  public static Predicate<ChangeData> editBy(Account.Id id) {
    return new ChangeIndexPredicate(ChangeField.EDITBY, id.toString());
  }

  /**
   * Returns a predicate that matches changes where the provided {@link Account.Id} has a pending
   * draft comment.
   */
  public static Predicate<ChangeData> draftBy(Account.Id id) {
    return new ChangeIndexPredicate(ChangeField.DRAFTBY, id.toString());
  }

  /**
   * Returns a predicate that matches changes that were reviewed by any of the provided {@link
   * Account.Id}.
   */
  public static Predicate<ChangeData> reviewedBy(Collection<Account.Id> ids) {
    List<Predicate<ChangeData>> predicates = new ArrayList<>(ids.size());
    for (Account.Id id : ids) {
      predicates.add(new ChangeIndexPredicate(ChangeField.REVIEWEDBY, id.toString()));
    }
    return Predicate.or(predicates);
  }

  /** Returns a predicate that matches changes that were not yet reviewed. */
  public static Predicate<ChangeData> unreviewed() {
    return Predicate.not(
        new ChangeIndexPredicate(ChangeField.REVIEWEDBY, ChangeField.NOT_REVIEWED.toString()));
  }

  /** Returns a predicate that matches the change with the provided {@link Change.Id}. */
  public static Predicate<ChangeData> id(Change.Id id) {
    return new ChangeIndexPredicate(
        ChangeField.LEGACY_ID, ChangeQueryBuilder.FIELD_CHANGE, id.toString());
  }

  /** Returns a predicate that matches changes owned by the provided {@link Account.Id}. */
  public static Predicate<ChangeData> owner(Account.Id id) {
    return new ChangeIndexPredicate(ChangeField.OWNER, id.toString());
  }

  /**
   * Returns a predicate that matches changes that are a cherry pick of the provided {@link
   * Change.Id}.
   */
  public static Predicate<ChangeData> cherryPickOf(Change.Id id) {
    return new ChangeIndexPredicate(ChangeField.CHERRY_PICK_OF_CHANGE, id.toString());
  }

  /**
   * Returns a predicate that matches changes that are a cherry pick of the provided {@link
   * PatchSet.Id}.
   */
  public static Predicate<ChangeData> cherryPickOf(PatchSet.Id psId) {
    return Predicate.and(
        cherryPickOf(psId.changeId()),
        new ChangeIndexPredicate(ChangeField.CHERRY_PICK_OF_PATCHSET, String.valueOf(psId.get())));
  }
}
