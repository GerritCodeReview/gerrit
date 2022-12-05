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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.CharMatcher;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.git.ObjectIds;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.change.HashtagsUtil;
import com.google.gerrit.server.index.change.ChangeField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Predicates that match against {@link ChangeData}. */
public class ChangePredicates {
  private ChangePredicates() {}

  /**
   * Returns a predicate that matches changes where the provided {@link
   * com.google.gerrit.entities.Account.Id} is in the attention set.
   */
  public static Predicate<ChangeData> attentionSet(Account.Id id) {
    return new ChangeIndexPredicate(ChangeField.ATTENTION_SET_USERS, id.toString());
  }

  /**
   * Returns a predicate that matches changes that are assigned to the provided {@link
   * com.google.gerrit.entities.Account.Id}.
   */
  public static Predicate<ChangeData> assignee(Account.Id id) {
    return new ChangeIndexPredicate(ChangeField.ASSIGNEE_SPEC, id.toString());
  }

  /**
   * Returns a predicate that matches changes that are a revert of the provided {@link
   * com.google.gerrit.entities.Change.Id}.
   */
  public static Predicate<ChangeData> revertOf(Change.Id revertOf) {
    return new ChangeIndexCardinalPredicate(ChangeField.REVERT_OF, revertOf.toString(), 1);
  }

  /**
   * Returns a predicate that matches changes that have a comment authored by the provided {@link
   * com.google.gerrit.entities.Account.Id}.
   */
  public static Predicate<ChangeData> commentBy(Account.Id id) {
    return new ChangeIndexPredicate(ChangeField.COMMENTBY_SPEC, id.toString());
  }

  /**
   * Returns a predicate that matches changes where the provided {@link
   * com.google.gerrit.entities.Account.Id} has a pending change edit.
   */
  public static Predicate<ChangeData> editBy(Account.Id id) {
    return new ChangeIndexPredicate(ChangeField.EDITBY_SPEC, id.toString());
  }

  /**
   * Returns a predicate that matches changes where the provided {@link
   * com.google.gerrit.entities.Account.Id} has a pending draft comment.
   */
  public static Predicate<ChangeData> draftBy(CommentsUtil commentsUtil, Account.Id id) {
    Set<Predicate<ChangeData>> changeIdPredicates =
        commentsUtil.getChangesWithDrafts(id).stream()
            .map(ChangePredicates::idStr)
            .collect(toImmutableSet());
    return changeIdPredicates.isEmpty()
        ? ChangeIndexPredicate.none()
        : Predicate.or(changeIdPredicates);
  }

  /**
   * Returns a predicate that matches changes where the provided {@link
   * com.google.gerrit.entities.Account.Id} has starred changes with {@code label}.
   */
  public static Predicate<ChangeData> starBy(
      StarredChangesUtil starredChangesUtil, Account.Id id, String label) {
    Set<Predicate<ChangeData>> starredChanges =
        starredChangesUtil.byAccountId(id, label).stream()
            .map(ChangePredicates::idStr)
            .collect(toImmutableSet());
    return starredChanges.isEmpty() ? ChangeIndexPredicate.none() : Predicate.or(starredChanges);
  }

  /**
   * Returns a predicate that matches changes that were reviewed by any of the provided {@link
   * com.google.gerrit.entities.Account.Id}.
   */
  public static Predicate<ChangeData> reviewedBy(Collection<Account.Id> ids) {
    List<Predicate<ChangeData>> predicates = new ArrayList<>(ids.size());
    for (Account.Id id : ids) {
      predicates.add(new ChangeIndexPredicate(ChangeField.REVIEWEDBY_SPEC, id.toString()));
    }
    return Predicate.or(predicates);
  }

  /** Returns a predicate that matches changes that were not yet reviewed. */
  public static Predicate<ChangeData> unreviewed() {
    return Predicate.not(
        new ChangeIndexPredicate(ChangeField.REVIEWEDBY_SPEC, ChangeField.NOT_REVIEWED.toString()));
  }

  /**
   * Returns a predicate that matches the change with the provided {@link
   * com.google.gerrit.entities.Change.Id}.
   */
  public static Predicate<ChangeData> idStr(Change.Id id) {
    return new ChangeIndexCardinalPredicate(
        ChangeField.LEGACY_ID_STR, ChangeQueryBuilder.FIELD_CHANGE, id.toString(), 1);
  }

  /**
   * Returns a predicate that matches changes owned by the provided {@link
   * com.google.gerrit.entities.Account.Id}.
   */
  public static Predicate<ChangeData> owner(Account.Id id) {
    return new ChangeIndexCardinalPredicate(ChangeField.OWNER_SPEC, id.toString(), 5000);
  }

  /**
   * Returns a predicate that matches changes where the latest patch set was uploaded by the
   * provided {@link com.google.gerrit.entities.Account.Id}.
   */
  public static Predicate<ChangeData> uploader(Account.Id id) {
    return new ChangeIndexPredicate(ChangeField.UPLOADER_SPEC, id.toString());
  }

  /**
   * Returns a predicate that matches changes that are a cherry pick of the provided {@link
   * com.google.gerrit.entities.Change.Id}.
   */
  public static Predicate<ChangeData> cherryPickOf(Change.Id id) {
    return new ChangeIndexPredicate(ChangeField.CHERRY_PICK_OF_CHANGE, id.toString());
  }

  /**
   * Returns a predicate that matches changes that are a cherry pick of the provided {@link
   * com.google.gerrit.entities.PatchSet.Id}.
   */
  public static Predicate<ChangeData> cherryPickOf(PatchSet.Id psId) {
    return Predicate.and(
        cherryPickOf(psId.changeId()),
        new ChangeIndexPredicate(ChangeField.CHERRY_PICK_OF_PATCHSET, String.valueOf(psId.get())));
  }

  /**
   * Returns a predicate that matches changes in the provided {@link
   * com.google.gerrit.entities.Project.NameKey}.
   */
  public static Predicate<ChangeData> project(Project.NameKey id) {
    return new ChangeIndexCardinalPredicate(ChangeField.PROJECT_SPEC, id.get(), 1_000_000);
  }

  /** Returns a predicate that matches changes targeted at the provided {@code refName}. */
  public static Predicate<ChangeData> ref(String refName) {
    return new ChangeIndexCardinalPredicate(ChangeField.REF_SPEC, refName, 10_000);
  }

  /** Returns a predicate that matches changes in the provided {@code topic}. */
  public static Predicate<ChangeData> exactTopic(String topic) {
    return new ChangeIndexPredicate(ChangeField.EXACT_TOPIC, topic);
  }

  /** Returns a predicate that matches changes in the provided {@code topic}. */
  public static Predicate<ChangeData> fuzzyTopic(String topic) {
    return new ChangeIndexPredicate(ChangeField.FUZZY_TOPIC, topic);
  }

  /** Returns a predicate that matches changes in the provided {@code topic}. Used with prefixes */
  public static Predicate<ChangeData> prefixTopic(String topic) {
    return new ChangeIndexPredicate(ChangeField.PREFIX_TOPIC, topic);
  }

  /** Returns a predicate that matches changes submitted in the provided {@code changeSet}. */
  public static Predicate<ChangeData> submissionId(String changeSet) {
    return new ChangeIndexPredicate(ChangeField.SUBMISSIONID_SPEC, changeSet);
  }

  /** Returns a predicate that matches changes that modified the provided {@code path}. */
  public static Predicate<ChangeData> path(String path) {
    return new ChangeIndexPredicate(ChangeField.PATH_SPEC, path);
  }

  /** Returns a predicate that matches changes tagged with the provided {@code hashtag}. */
  public static Predicate<ChangeData> hashtag(String hashtag) {
    // Use toLowerCase without locale to match behavior in ChangeField.
    return new ChangeIndexPredicate(
        ChangeField.HASHTAG_SPEC, HashtagsUtil.cleanupHashtag(hashtag).toLowerCase());
  }

  /** Returns a predicate that matches changes tagged with the provided {@code hashtag}. */
  public static Predicate<ChangeData> fuzzyHashtag(String hashtag) {
    // Use toLowerCase without locale to match behavior in ChangeField.
    return new ChangeIndexPredicate(
        ChangeField.FUZZY_HASHTAG, HashtagsUtil.cleanupHashtag(hashtag).toLowerCase());
  }

  /**
   * Returns a predicate that matches changes in the provided {@code hashtag}. Used with prefixes
   */
  public static Predicate<ChangeData> prefixHashtag(String hashtag) {
    // Use toLowerCase without locale to match behavior in ChangeField.
    return new ChangeIndexPredicate(
        ChangeField.PREFIX_HASHTAG, HashtagsUtil.cleanupHashtag(hashtag).toLowerCase());
  }

  /** Returns a predicate that matches changes that modified the provided {@code file}. */
  public static Predicate<ChangeData> file(ChangeQueryBuilder.Arguments args, String file) {
    Predicate<ChangeData> eqPath = path(file);
    if (!args.getSchema().hasField(ChangeField.FILE_PART_SPEC)) {
      return eqPath;
    }
    return Predicate.or(eqPath, new ChangeIndexPredicate(ChangeField.FILE_PART_SPEC, file));
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
    return new ChangeIndexPredicate(ChangeField.FOOTER_SPEC, footer.toLowerCase(Locale.US));
  }

  /**
   * Returns a predicate that matches changes with the provided {@code footer} name in their commit
   * message.
   */
  public static Predicate<ChangeData> hasFooter(String footerName) {
    return new ChangeIndexPredicate(ChangeField.FOOTER_NAME, footerName);
  }

  /**
   * Returns a predicate that matches changes that modified files in the provided {@code directory}.
   */
  public static Predicate<ChangeData> directory(String directory) {
    return new ChangeIndexPredicate(
        ChangeField.DIRECTORY_SPEC, CharMatcher.is('/').trimFrom(directory).toLowerCase(Locale.US));
  }

  /** Returns a predicate that matches changes with the provided {@code trackingId}. */
  public static Predicate<ChangeData> trackingId(String trackingId) {
    return new ChangeIndexCardinalPredicate(ChangeField.TR_SPEC, trackingId, 5);
  }

  /** Returns a predicate that matches changes authored by the provided {@code exactAuthor}. */
  public static Predicate<ChangeData> exactAuthor(String exactAuthor) {
    return new ChangeIndexPredicate(
        ChangeField.EXACT_AUTHOR_SPEC, exactAuthor.toLowerCase(Locale.US));
  }

  /** Returns a predicate that matches changes authored by the provided {@code author}. */
  public static Predicate<ChangeData> author(String author) {
    return new ChangeIndexPredicate(ChangeField.AUTHOR_PARTS_SPEC, author);
  }

  /**
   * Returns a predicate that matches changes where the patch set was committed by {@code
   * exactCommitter}.
   */
  public static Predicate<ChangeData> exactCommitter(String exactCommitter) {
    return new ChangeIndexPredicate(
        ChangeField.EXACT_COMMITTER_SPEC, exactCommitter.toLowerCase(Locale.US));
  }

  /**
   * Returns a predicate that matches changes where the patch set was committed by {@code
   * committer}.
   */
  public static Predicate<ChangeData> committer(String comitter) {
    return new ChangeIndexPredicate(
        ChangeField.COMMITTER_PARTS_SPEC, comitter.toLowerCase(Locale.US));
  }

  /** Returns a predicate that matches changes whose ID starts with the provided {@code id}. */
  public static Predicate<ChangeData> idPrefix(String id) {
    return new ChangeIndexCardinalPredicate(ChangeField.ID, id, 5);
  }

  /**
   * Returns a predicate that matches changes in a project that has the provided {@code prefix} in
   * its name.
   */
  public static Predicate<ChangeData> projectPrefix(String prefix) {
    return new ChangeIndexPredicate(ChangeField.PROJECTS_SPEC, prefix);
  }

  /**
   * Returns a predicate that matches changes where a patch set has the provided {@code commitId}
   * either as prefix or as full {@link org.eclipse.jgit.lib.ObjectId}.
   */
  public static Predicate<ChangeData> commitPrefix(String commitId) {
    if (commitId.length() == ObjectIds.STR_LEN) {
      return new ChangeIndexCardinalPredicate(ChangeField.EXACT_COMMIT_SPEC, commitId, 5);
    }
    return new ChangeIndexCardinalPredicate(ChangeField.COMMIT_SPEC, commitId, 5);
  }

  /**
   * Returns a predicate that matches changes where the provided {@code message} appears in the
   * commit message. Uses full-text search semantics.
   */
  public static Predicate<ChangeData> message(String message) {
    return new ChangeIndexPredicate(ChangeField.COMMIT_MESSAGE, message);
  }

  public static Predicate<ChangeData> subject(String subject) {
    return new ChangeIndexPredicate(ChangeField.SUBJECT_SPEC, subject);
  }

  /**
   * Returns a predicate that matches changes where the provided {@code comment} appears in any
   * comment on any patch set of the change. Uses full-text search semantics.
   */
  public static Predicate<ChangeData> comment(String comment) {
    return new ChangeIndexPredicate(ChangeField.COMMENT_SPEC, comment);
  }

  /**
   * Returns a predicate that matches with changes having a specific submit rule evaluating to a
   * certain result. Value should be in the form of "$ruleName=$status" with $ruleName equals to
   * '$plugin_name~$rule_name' and $rule_name equals to the name of the class that implements the
   * {@link com.google.gerrit.server.rules.SubmitRule}. For gerrit core rules, $ruleName should be
   * in the form of 'gerrit~$rule_name'.
   */
  public static Predicate<ChangeData> submitRuleStatus(String value) {
    return new ChangeIndexPredicate(ChangeField.SUBMIT_RULE_RESULT, value);
  }

  /**
   * Returns a predicate that matches with changes that are pure reverts if {@code value} is equal
   * to "1", or non-pure reverts if {@code value} is "0".
   */
  public static Predicate<ChangeData> pureRevert(String value) {
    return new ChangeIndexPredicate(ChangeField.IS_PURE_REVERT_SPEC, value);
  }

  /**
   * Returns a predicate that matches with changes that are submittable if {@code value} is equal to
   * "1", or non-submittable if {@code value} is "0".
   *
   * <p>The computation of this field is based on the evaluation of {@link
   * com.google.gerrit.entities.SubmitRequirement}s.
   */
  public static Predicate<ChangeData> isSubmittable(String value) {
    return new ChangeIndexPredicate(ChangeField.IS_SUBMITTABLE_SPEC, value);
  }
}
