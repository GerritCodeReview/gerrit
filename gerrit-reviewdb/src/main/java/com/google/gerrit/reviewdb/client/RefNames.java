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
  public static final String HEAD = "HEAD";

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

  /** Magic user branch in All-Users {@code refs/users/self} */
  public static final String REFS_USERS_SELF = "refs/users/self";

  /** Default user preference settings */
  public static final String REFS_USERS_DEFAULT = RefNames.REFS_USERS + "default";

  /** Configurations of project-specific dashboards (canned search queries). */
  public static final String REFS_DASHBOARDS = "refs/meta/dashboards/";

  /** Draft inline comments of a user on a change */
  public static final String REFS_DRAFT_COMMENTS = "refs/draft-comments/";

  /** A change starred by a user */
  public static final String REFS_STARRED_CHANGES = "refs/starred-changes/";

  /** Sequence counters in NoteDb. */
  public static final String REFS_SEQUENCES = "refs/sequences/";

  /**
   * Prefix applied to merge commit base nodes.
   *
   * <p>References in this directory should take the form {@code refs/cache-automerge/xx/yyyy...}
   * where xx is the first two digits of the merge commit's object name, and yyyyy... is the
   * remaining 38. The reference should point to a treeish that is the automatic merge result of the
   * merge commit's parents.
   */
  public static final String REFS_CACHE_AUTOMERGE = "refs/cache-automerge/";

  /** Suffix of a meta ref in the NoteDb. */
  public static final String META_SUFFIX = "/meta";

  /** Suffix of a ref that stores robot comments in the NoteDb. */
  public static final String ROBOT_COMMENTS_SUFFIX = "/robot-comments";

  public static final String EDIT_PREFIX = "edit-";

  public static String fullName(String ref) {
    return (ref.startsWith(REFS) || ref.equals(HEAD)) ? ref : REFS_HEADS + ref;
  }

  public static final String shortName(String ref) {
    if (ref.startsWith(REFS_HEADS)) {
      return ref.substring(REFS_HEADS.length());
    } else if (ref.startsWith(REFS_TAGS)) {
      return ref.substring(REFS_TAGS.length());
    }
    return ref;
  }

  public static String changeMetaRef(Change.Id id) {
    StringBuilder r = new StringBuilder();
    r.append(REFS_CHANGES);
    r.append(shard(id.get()));
    r.append(META_SUFFIX);
    return r.toString();
  }

  public static String robotCommentsRef(Change.Id id) {
    StringBuilder r = new StringBuilder();
    r.append(REFS_CHANGES);
    r.append(shard(id.get()));
    r.append(ROBOT_COMMENTS_SUFFIX);
    return r.toString();
  }

  public static String refsUsers(Account.Id accountId) {
    StringBuilder r = new StringBuilder();
    r.append(REFS_USERS);
    r.append(shard(accountId.get()));
    return r.toString();
  }

  public static String refsDraftComments(Change.Id changeId, Account.Id accountId) {
    StringBuilder r = buildRefsPrefix(REFS_DRAFT_COMMENTS, changeId.get());
    r.append(accountId.get());
    return r.toString();
  }

  public static String refsDraftCommentsPrefix(Change.Id changeId) {
    return buildRefsPrefix(REFS_DRAFT_COMMENTS, changeId.get()).toString();
  }

  public static String refsStarredChanges(Change.Id changeId, Account.Id accountId) {
    StringBuilder r = buildRefsPrefix(REFS_STARRED_CHANGES, changeId.get());
    r.append(accountId.get());
    return r.toString();
  }

  public static String refsStarredChangesPrefix(Change.Id changeId) {
    return buildRefsPrefix(REFS_STARRED_CHANGES, changeId.get()).toString();
  }

  private static StringBuilder buildRefsPrefix(String prefix, int id) {
    StringBuilder r = new StringBuilder();
    r.append(prefix);
    r.append(shard(id));
    r.append('/');
    return r;
  }

  public static String refsCacheAutomerge(String hash) {
    return REFS_CACHE_AUTOMERGE + hash.substring(0, 2) + '/' + hash.substring(2);
  }

  public static String shard(int id) {
    if (id < 0) {
      return null;
    }
    StringBuilder r = new StringBuilder();
    int n = id % 100;
    if (n < 10) {
      r.append('0');
    }
    r.append(n);
    r.append('/');
    r.append(id);
    return r.toString();
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
  public static String refsEdit(Account.Id accountId, Change.Id changeId, PatchSet.Id psId) {
    return refsEditPrefix(accountId, changeId) + psId.get();
  }

  /**
   * Returns reference prefix for this change edit with sharded user and change number:
   * refs/users/UU/UUUU/edit-CCCC/.
   *
   * @param accountId account id
   * @param changeId change number
   * @return reference prefix for this change edit
   */
  public static String refsEditPrefix(Account.Id accountId, Change.Id changeId) {
    return refsEditPrefix(accountId) + changeId.get() + '/';
  }

  public static String refsEditPrefix(Account.Id accountId) {
    return refsUsers(accountId) + '/' + EDIT_PREFIX;
  }

  public static boolean isRefsEdit(String ref) {
    return ref.startsWith(REFS_USERS) && ref.contains(EDIT_PREFIX);
  }

  public static boolean isRefsUsers(String ref) {
    return ref.startsWith(REFS_USERS);
  }

  static Integer parseShardedRefPart(String name) {
    if (name == null) {
      return null;
    }

    String[] parts = name.split("/");
    int n = parts.length;
    if (n < 2) {
      return null;
    }

    // Last 2 digits.
    int le;
    for (le = 0; le < parts[0].length(); le++) {
      if (!Character.isDigit(parts[0].charAt(le))) {
        return null;
      }
    }
    if (le != 2) {
      return null;
    }

    // Full ID.
    int ie;
    for (ie = 0; ie < parts[1].length(); ie++) {
      if (!Character.isDigit(parts[1].charAt(ie))) {
        if (ie == 0) {
          return null;
        }
        break;
      }
    }

    int shard = Integer.parseInt(parts[0]);
    int id = Integer.parseInt(parts[1].substring(0, ie));

    if (id % 100 != shard) {
      return null;
    }
    return id;
  }

  static Integer parseRefSuffix(String name) {
    if (name == null) {
      return null;
    }
    int i = name.length();
    while (i > 0) {
      char c = name.charAt(i - 1);
      if (c == '/') {
        break;
      } else if (!Character.isDigit(c)) {
        return null;
      }
      i--;
    }
    if (i == 0) {
      return null;
    }
    return Integer.valueOf(name.substring(i, name.length()));
  }

  private RefNames() {}
}
