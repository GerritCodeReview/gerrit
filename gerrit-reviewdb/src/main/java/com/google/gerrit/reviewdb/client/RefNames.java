// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.reviewdb.client;


/** Constants and utilities for Gerrit-specific ref names. */
public class RefNames {
  public static final String REFS = "refs/";

  public static final String REFS_HEADS = "refs/heads/";

  public static final String REFS_TAGS = "refs/tags/";

  public static final String REFS_CHANGES = "refs/changes/";

  /** Note tree listing commits we refuse {@code refs/meta/reject-commits} */
  public static final String REFS_REJECT_COMMITS = "refs/meta/reject-commits";

  /** Configuration settings for a project {@code refs/meta/config} */
  public static final String REFS_CONFIG = "refs/meta/config";

  /** Preference settings for a user {@code refs/users} */
  public static final String REFS_USERS = "refs/users/";

  /** Default user preference settings */
  public static final String REFS_USERS_DEFAULT = RefNames.REFS_USERS + "default";

  /** Configurations of project-specific dashboards (canned search queries). */
  public static final String REFS_DASHBOARDS = "refs/meta/dashboards/";

  /** Draft inline comments of a user on a change */
  public static final String REFS_DRAFT_COMMENTS = "refs/draft-comments/";

  /** A change starred by a user */
  public static final String REFS_STARRED_CHANGES = "refs/starred-changes/";

  /**
   * Prefix applied to merge commit base nodes.
   * <p>
   * References in this directory should take the form
   * {@code refs/cache-automerge/xx/yyyy...} where xx is
   * the first two digits of the merge commit's object
   * name, and yyyyy... is the remaining 38. The reference
   * should point to a treeish that is the automatic merge
   * result of the merge commit's parents.
   */
  public static final String REFS_CACHE_AUTOMERGE = "refs/cache-automerge/";

  /** Suffix of a meta ref in the notedb. */
  public static final String META_SUFFIX = "/meta";

  public static final String EDIT_PREFIX = "edit-";

  public static String fullName(String ref) {
    return ref.startsWith(REFS) ? ref : REFS_HEADS + ref;
  }

  public static final String shortName(String ref) {
    if (ref.startsWith(REFS_HEADS)) {
      return ref.substring(REFS_HEADS.length());
    } else if (ref.startsWith(REFS_TAGS)) {
      return ref.substring(REFS_TAGS.length());
    }
    return ref;
  }

  public static String refsUsers(Account.Id accountId) {
    StringBuilder r = new StringBuilder();
    r.append(REFS_USERS);
    int account = accountId.get();
    int m = account % 100;
    if (m < 10) {
      r.append('0');
    }
    r.append(m);
    r.append('/');
    r.append(account);
    return r.toString();
  }

  public static String refsDraftComments(Account.Id accountId,
      Change.Id changeId) {
    StringBuilder r = buildRefsDraftCommentsPrefix(accountId);
    r.append(changeId.get());
    return r.toString();
  }

  public static String refsDraftCommentsPrefix(Account.Id accountId) {
    return buildRefsDraftCommentsPrefix(accountId).toString();
  }

  public static StringBuilder buildRefsDraftCommentsPrefix(
      Account.Id accountId) {
    StringBuilder r = new StringBuilder();
    r.append(REFS_DRAFT_COMMENTS);
    int n = accountId.get() % 100;
    if (n < 10) {
      r.append('0');
    }
    r.append(n);
    r.append('/');
    r.append(accountId.get());
    r.append('-');
    return r;
  }

  public static String refsStarredChanges(Account.Id accountId,
      Change.Id changeId) {
    StringBuilder r = buildRefsPrefix(REFS_STARRED_CHANGES, accountId);
    r.append(changeId.get());
    return r.toString();
  }

  public static String refsStarredChangesPrefix(Account.Id accountId) {
    return buildRefsPrefix(REFS_STARRED_CHANGES, accountId).toString();
  }

  private static StringBuilder buildRefsPrefix(String prefix,
      Account.Id accountId) {
    StringBuilder r = new StringBuilder();
    r.append(prefix);
    int n = accountId.get() % 100;
    if (n < 10) {
      r.append('0');
    }
    r.append(n);
    r.append('/');
    r.append(accountId.get());
    r.append('/');
    return r;
  }

  /**
   * Returns reference for this change edit with sharded user and change number:
   * refs/users/UU/UUUU/edit-CCCC/P.
   *
   * @param accountId account id
   * @param changeId change number
   * @param psId patch set number
   * @return reference for this change edit
   */
  public static String refsEdit(Account.Id accountId, Change.Id changeId,
      PatchSet.Id psId) {
    return refsEditPrefix(accountId, changeId) + psId.get();
  }

  /**
   * Returns reference prefix for this change edit with sharded user and
   * change number: refs/users/UU/UUUU/edit-CCCC/.
   *
   * @param accountId account id
   * @param changeId change number
   * @return reference prefix for this change edit
   */
  public static String refsEditPrefix(Account.Id accountId, Change.Id changeId) {
    return new StringBuilder(refsUsers(accountId))
      .append('/')
      .append(EDIT_PREFIX)
      .append(changeId.get())
      .append('/')
      .toString();
  }

  private RefNames() {
  }
}
