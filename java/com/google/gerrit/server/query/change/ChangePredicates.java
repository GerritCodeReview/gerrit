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

import com.google.common.base.CharMatcher;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.change.HashtagsUtil;
import com.google.gerrit.server.index.change.ChangeField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

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

  /** Returns a predicate that matches the change with the provided {@link Change.Id}. */
  public static Predicate<ChangeData> idStr(Change.Id id) {
    return new ChangeIndexPredicate(
        ChangeField.LEGACY_ID_STR, ChangeQueryBuilder.FIELD_CHANGE, id.toString());
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

  /** Returns a predicate that matches changes in the provided {@link Project.NameKey}. */
  public static Predicate<ChangeData> project(Project.NameKey id) {
    return new ChangeIndexPredicate(ChangeField.PROJECT, id.get());
  }

  /** Returns a predicate that matches changes targeted at the provided {@code refName}. */
  public static Predicate<ChangeData> ref(String refName) {
    return new ChangeIndexPredicate(ChangeField.REF, refName);
  }

  /** Returns a predicate that matches changes in the provided {@code topic}. */
  public static Predicate<ChangeData> exactTopic(String topic) {
    return new ChangeIndexPredicate(ChangeField.EXACT_TOPIC, topic);
  }

  /** Returns a predicate that matches changes submitted in the provided {@code changeSet}. */
  public static Predicate<ChangeData> submissionId(String changeSet) {
    return new ChangeIndexPredicate(ChangeField.SUBMISSIONID, changeSet);
  }

  /** Returns a predicate that matches changes that modified the provided {@code path}. */
  public static Predicate<ChangeData> path(String path) {
    return new ChangeIndexPredicate(ChangeField.PATH, path);
  }

  /** Returns a predicate that matches changes tagged with the provided {@code hashtag}. */
  public static Predicate<ChangeData> hashtag(String hashtag) {
    // Use toLowerCase without locale to match behavior in ChangeField.
    return new ChangeIndexPredicate(
        ChangeField.HASHTAG, HashtagsUtil.cleanupHashtag(hashtag).toLowerCase());
  }

  /** Returns a predicate that matches changes that modified the provided {@code file}. */
  public static Predicate<ChangeData> file(ChangeQueryBuilder.Arguments args, String file) {
    Predicate<ChangeData> eqPath = path(file);
    if (!args.getSchema().hasField(ChangeField.FILE_PART)) {
      return eqPath;
    }
    return Predicate.or(eqPath, new ChangeIndexPredicate(ChangeField.FILE_PART, file));
  }

  /**
   * Returns a predicate that matches changes with the provided {@code footer} in their commit
   * message.
   */
  public static Predicate<ChangeData> footer(String footer) {
    int indexEquals = footer.indexOf('=');
    int indexColon = footer.indexOf(':');

    // footer key cannot contain '='
    if (indexEquals > 0 && (indexEquals < indexColon || indexColon < 0)) {
      footer = footer.substring(0, indexEquals) + ": " + footer.substring(indexEquals + 1);
    }
    return new ChangeIndexPredicate(ChangeField.FOOTER, footer.toLowerCase(Locale.US));
  }

  /**
   * Returns a predicate that matches changes that modified files in the provided {@code directory}.
   */
  public static Predicate<ChangeData> directory(String directory) {
    return new ChangeIndexPredicate(
        ChangeField.DIRECTORY, CharMatcher.is('/').trimFrom(directory).toLowerCase(Locale.US));
  }

  /** Returns a predicate that matches changes with the provided {@code trackingId}. */
  public static Predicate<ChangeData> trackingId(String trackingId) {
    return new ChangeIndexPredicate(ChangeField.TR, trackingId);
  }

  /** Returns a predicate that matches changes authored by the provided {@code exactAuthor}. */
  public static Predicate<ChangeData> exactAuthor(String exactAuthor) {
    return new ChangeIndexPredicate(ChangeField.EXACT_AUTHOR, exactAuthor.toLowerCase(Locale.US));
  }

  /**
   * Returns a predicate that matches changes where the patch set was committed by {@code
   * exactCommitter}.
   */
  public static Predicate<ChangeData> exactCommitter(String exactCommitter) {
    return new ChangeIndexPredicate(
        ChangeField.EXACT_COMMITTER, exactCommitter.toLowerCase(Locale.US));
  }
}
