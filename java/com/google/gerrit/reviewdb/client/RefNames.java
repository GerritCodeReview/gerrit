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

  public static final String REFS_META = "refs/meta/";

  /** Note tree listing commits we refuse {@code refs/meta/reject-commits} */
  public static final String REFS_REJECT_COMMITS = "refs/meta/reject-commits";

  /** Configuration settings for a project {@code refs/meta/config} */
  public static final String REFS_CONFIG = "refs/meta/config";

  /** Note tree listing external IDs */
  public static final String REFS_EXTERNAL_IDS = "refs/meta/external-ids";

  /** Magic user branch in All-Users {@code refs/users/self} */
  public static final String REFS_USERS_SELF = "refs/users/self";

  /** Default user preference settings */
  public static final String REFS_USERS_DEFAULT = RefNames.REFS_USERS + "default";

  /** Configurations of project-specific dashboards (canned search queries). */
  public static final String REFS_DASHBOARDS = "refs/meta/dashboards/";

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

  /*
   * The following refs contain an account ID and should be visible only to that account.
   *
   * Parsing the account ID from the ref is implemented in Account.Id#fromRef(String). This ensures
   * that VisibleRefFilter hides those refs from other users.
   *
   * This applies to:
   * - User branches (e.g. 'refs/users/23/1011123')
   * - Draft comment refs (e.g. 'refs/draft-comments/73/67473/1011123')
   * - Starred changes refs (e.g. 'refs/starred-changes/73/67473/1011123')
   */

  /** Preference settings for a user {@code refs/users} */
  public static final String REFS_USERS = "refs/users/";

  /** NoteDb ref for a group {@code refs/groups} */
  public static final String REFS_GROUPS = "refs/groups/";

  /** NoteDb ref for the NoteMap of all group names */
  public static final String REFS_GROUPNAMES = "refs/meta/group-names";

  /**
   * NoteDb ref for deleted groups {@code refs/deleted-groups}. This ref namespace is foreseen as an
   * attic for deleted groups (it's reserved but not used yet)
   */
  public static final String REFS_DELETED_GROUPS = "refs/deleted-groups/";

  /** Draft inline comments of a user on a change */
  public static final String REFS_DRAFT_COMMENTS = "refs/draft-comments/";

  /** A change starred by a user */
  public static final String REFS_STARRED_CHANGES = "refs/starred-changes/";

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
    StringBuilder r = newStringBuilder().append(REFS_CHANGES);
    return shard(id.get(), r).append(META_SUFFIX).toString();
  }

  public static String robotCommentsRef(Change.Id id) {
    StringBuilder r = newStringBuilder().append(REFS_CHANGES);
    return shard(id.get(), r).append(ROBOT_COMMENTS_SUFFIX).toString();
  }

  public static boolean isNoteDbMetaRef(String ref) {
    if (ref.startsWith(REFS_CHANGES)
        && (ref.endsWith(META_SUFFIX) || ref.endsWith(ROBOT_COMMENTS_SUFFIX))) {
      return true;
    }
    if (ref.startsWith(REFS_DRAFT_COMMENTS) || ref.startsWith(REFS_STARRED_CHANGES)) {
      return true;
    }
    return false;
  }

  public static String refsGroups(AccountGroup.UUID groupUuid) {
    return REFS_GROUPS + shardUuid(groupUuid.get());
  }

  public static String refsDeletedGroups(AccountGroup.UUID groupUuid) {
    return REFS_DELETED_GROUPS + shardUuid(groupUuid.get());
  }

  public static String refsUsers(Account.Id accountId) {
    StringBuilder r = newStringBuilder().append(REFS_USERS);
    return shard(accountId.get(), r).toString();
  }

  public static String refsDraftComments(Change.Id changeId, Account.Id accountId) {
    return buildRefsPrefix(REFS_DRAFT_COMMENTS, changeId.get()).append(accountId.get()).toString();
  }

  public static String refsDraftCommentsPrefix(Change.Id changeId) {
    return buildRefsPrefix(REFS_DRAFT_COMMENTS, changeId.get()).toString();
  }

  public static String refsStarredChanges(Change.Id changeId, Account.Id accountId) {
    return buildRefsPrefix(REFS_STARRED_CHANGES, changeId.get()).append(accountId.get()).toString();
  }

  public static String refsStarredChangesPrefix(Change.Id changeId) {
    return buildRefsPrefix(REFS_STARRED_CHANGES, changeId.get()).toString();
  }

  private static StringBuilder buildRefsPrefix(String prefix, int id) {
    StringBuilder r = newStringBuilder().append(prefix);
    return shard(id, r).append('/');
  }

  public static String refsCacheAutomerge(String hash) {
    return REFS_CACHE_AUTOMERGE + hash.substring(0, 2) + '/' + hash.substring(2);
  }

  public static String shard(int id) {
    if (id < 0) {
      return null;
    }
    return shard(id, newStringBuilder()).toString();
  }

  private static StringBuilder shard(int id, StringBuilder sb) {
    int n = id % 100;
    if (n < 10) {
      sb.append('0');
    }
    sb.append(n);
    sb.append('/');
    sb.append(id);
    return sb;
  }

  private static String shardUuid(String uuid) {
    if (uuid == null || uuid.length() < 2) {
      throw new IllegalArgumentException("UUIDs must consist of at least two characters");
    }
    return uuid.substring(0, 2) + '/' + uuid;
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
    return ref != null && ref.startsWith(REFS_USERS) && ref.contains(EDIT_PREFIX);
  }

  public static boolean isRefsUsers(String ref) {
    return ref.startsWith(REFS_USERS);
  }

  /**
   * Whether the ref is a group branch that stores NoteDb data of a group. Returns {@code true} for
   * all refs that start with {@code refs/groups/}.
   */
  public static boolean isRefsGroups(String ref) {
    return ref.startsWith(REFS_GROUPS);
  }

  /**
   * Whether the ref is a group branch that stores NoteDb data of a deleted group. Returns {@code
   * true} for all refs that start with {@code refs/deleted-groups/}.
   */
  public static boolean isRefsDeletedGroups(String ref) {
    return ref.startsWith(REFS_DELETED_GROUPS);
  }

  /**
   * Whether the ref is used for storing group data in NoteDb. Returns {@code true} for all group
   * branches, refs/meta/group-names and deleted group branches.
   */
  public static boolean isGroupRef(String ref) {
    return isRefsGroups(ref) || isRefsDeletedGroups(ref) || REFS_GROUPNAMES.equals(ref);
  }

  /** Whether the ref is the configuration branch, i.e. {@code refs/meta/config}, for a project. */
  public static boolean isConfigRef(String ref) {
    return REFS_CONFIG.equals(ref);
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

  static String parseShardedUuidFromRefPart(String name) {
    if (name == null) {
      return null;
    }

    String[] parts = name.split("/");
    int n = parts.length;
    if (n != 2) {
      return null;
    }

    // First 2 chars.
    if (parts[0].length() != 2) {
      return null;
    }

    // Full UUID.
    String uuid = parts[1];
    if (!uuid.startsWith(parts[0])) {
      return null;
    }

    return uuid;
  }

  /**
   * Skips a sharded ref part at the beginning of the name.
   *
   * <p>E.g.: "01/1" -> "", "01/1/" -> "/", "01/1/2" -> "/2", "01/1-edit" -> "-edit"
   *
   * @param name ref part name
   * @return the rest of the name, {@code null} if the ref name part doesn't start with a valid
   *     sharded ID
   */
  static String skipShardedRefPart(String name) {
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

    return name.substring(2 + 1 + ie); // 2 for the length of the shard, 1 for the '/'
  }

  /**
   * Parses an ID that follows a sharded ref part at the beginning of the name.
   *
   * <p>E.g.: "01/1/2" -> 2, "01/1/2/4" -> 2, ""01/1/2-edit" -> 2
   *
   * @param name ref part name
   * @return ID that follows the sharded ref part at the beginning of the name, {@code null} if the
   *     ref name part doesn't start with a valid sharded ID or if no valid ID follows the sharded
   *     ref part
   */
  static Integer parseAfterShardedRefPart(String name) {
    String rest = skipShardedRefPart(name);
    if (rest == null || !rest.startsWith("/")) {
      return null;
    }

    rest = rest.substring(1);

    int ie;
    for (ie = 0; ie < rest.length(); ie++) {
      if (!Character.isDigit(rest.charAt(ie))) {
        break;
      }
    }
    if (ie == 0) {
      return null;
    }
    return Integer.parseInt(rest.substring(0, ie));
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

  private static StringBuilder newStringBuilder() {
    // Many refname types in this file are always are longer than the default of 16 chars, so
    // presize StringBuilders larger by default. This hurts readability less than accurate
    // calculations would, at a negligible cost to memory overhead.
    return new StringBuilder(64);
  }

  private RefNames() {}
}
