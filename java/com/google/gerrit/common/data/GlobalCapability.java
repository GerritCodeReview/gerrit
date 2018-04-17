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

package com.google.gerrit.common.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Server wide capabilities. Represented as {@link Permission} objects. */
public class GlobalCapability {
  /** Ability to access the database (with gsql). */
  public static final String ACCESS_DATABASE = "accessDatabase";

  /**
   * Denotes the server's administrators.
   *
   * <p>This is similar to UNIX root, or Windows SYSTEM account. Any user that has this capability
   * can perform almost any other action, or can grant themselves the power to perform any other
   * action on the site. Most of the other capabilities and permissions fall-back to the predicate
   * "OR user has capability ADMINISTRATE_SERVER".
   */
  public static final String ADMINISTRATE_SERVER = "administrateServer";

  /** Maximum number of changes that may be pushed in a batch. */
  public static final String BATCH_CHANGES_LIMIT = "batchChangesLimit";

  /**
   * Default maximum number of changes that may be pushed in a batch, 0 means no limit. This is just
   * used as a suggestion for prepopulating the field in the access UI.
   */
  public static final int DEFAULT_MAX_BATCH_CHANGES_LIMIT = 0;

  /** Can create any account on the server. */
  public static final String CREATE_ACCOUNT = "createAccount";

  /** Can create any group on the server. */
  public static final String CREATE_GROUP = "createGroup";

  /** Can create any project on the server. */
  public static final String CREATE_PROJECT = "createProject";

  /**
   * Denotes who may email change reviewers and watchers.
   *
   * <p>This can be used to deny build bots from emailing reviewers and people who watch the change.
   * Instead, only the authors of the change and those who starred it will be emailed. The allow
   * rules are evaluated before deny rules, however the default is to allow emailing, if no explicit
   * rule is matched.
   */
  public static final String EMAIL_REVIEWERS = "emailReviewers";

  /** Can flush any cache except the active web_sessions cache. */
  public static final String FLUSH_CACHES = "flushCaches";

  /** Can terminate any task using the kill command. */
  public static final String KILL_TASK = "killTask";

  /**
   * Can perform limited server maintenance.
   *
   * <p>Includes tasks such as reindexing changes and flushing caches that may need to be performed
   * regularly. Does <strong>not</strong> grant arbitrary read/write/ACL management permissions as
   * does {@link #ADMINISTRATE_SERVER}.
   */
  public static final String MAINTAIN_SERVER = "maintainServer";

  /** Can modify any account on the server. */
  public static final String MODIFY_ACCOUNT = "modifyAccount";

  /** Queue a user can access to submit their tasks to. */
  public static final String PRIORITY = "priority";

  /** Maximum result limit per executed query. */
  public static final String QUERY_LIMIT = "queryLimit";

  /** Default result limit per executed query. */
  public static final int DEFAULT_MAX_QUERY_LIMIT = 500;

  /** Ability to impersonate another user. */
  public static final String RUN_AS = "runAs";

  /** Can run the Git garbage collection. */
  public static final String RUN_GC = "runGC";

  /** Can perform streaming of Gerrit events. */
  public static final String STREAM_EVENTS = "streamEvents";

  /** Can view all accounts, regardless of {@code accounts.visibility}. */
  public static final String VIEW_ALL_ACCOUNTS = "viewAllAccounts";

  /** Can view the server's current cache states. */
  public static final String VIEW_CACHES = "viewCaches";

  /** Can view open connections to the server's SSH port. */
  public static final String VIEW_CONNECTIONS = "viewConnections";

  /** Can view all installed plugins. */
  public static final String VIEW_PLUGINS = "viewPlugins";

  /** Can view all pending tasks in the queue (not just the filtered set). */
  public static final String VIEW_QUEUE = "viewQueue";

  /** Can query permissions for any (project, user) pair */
  public static final String VIEW_ACCESS = "viewAccess";

  private static final List<String> NAMES_ALL;
  private static final List<String> NAMES_LC;
  private static final String[] RANGE_NAMES = {
    QUERY_LIMIT, BATCH_CHANGES_LIMIT,
  };

  static {
    NAMES_ALL = new ArrayList<>();
    NAMES_ALL.add(ACCESS_DATABASE);
    NAMES_ALL.add(ADMINISTRATE_SERVER);
    NAMES_ALL.add(BATCH_CHANGES_LIMIT);
    NAMES_ALL.add(CREATE_ACCOUNT);
    NAMES_ALL.add(CREATE_GROUP);
    NAMES_ALL.add(CREATE_PROJECT);
    NAMES_ALL.add(EMAIL_REVIEWERS);
    NAMES_ALL.add(FLUSH_CACHES);
    NAMES_ALL.add(KILL_TASK);
    NAMES_ALL.add(MAINTAIN_SERVER);
    NAMES_ALL.add(MODIFY_ACCOUNT);
    NAMES_ALL.add(PRIORITY);
    NAMES_ALL.add(QUERY_LIMIT);
    NAMES_ALL.add(RUN_AS);
    NAMES_ALL.add(RUN_GC);
    NAMES_ALL.add(STREAM_EVENTS);
    NAMES_ALL.add(VIEW_ALL_ACCOUNTS);
    NAMES_ALL.add(VIEW_CACHES);
    NAMES_ALL.add(VIEW_CONNECTIONS);
    NAMES_ALL.add(VIEW_PLUGINS);
    NAMES_ALL.add(VIEW_QUEUE);
    NAMES_ALL.add(VIEW_ACCESS);

    NAMES_LC = new ArrayList<>(NAMES_ALL.size());
    for (String name : NAMES_ALL) {
      NAMES_LC.add(name.toLowerCase());
    }
  }

  /** @return all valid capability names. */
  public static Collection<String> getAllNames() {
    return Collections.unmodifiableList(NAMES_ALL);
  }

  /** @return true if the name is recognized as a capability name. */
  public static boolean isGlobalCapability(String varName) {
    return NAMES_LC.contains(varName.toLowerCase());
  }

  /** @return true if the capability should have a range attached. */
  public static boolean hasRange(String varName) {
    for (String n : RANGE_NAMES) {
      if (n.equalsIgnoreCase(varName)) {
        return true;
      }
    }
    return false;
  }

  public static List<String> getRangeNames() {
    return Collections.unmodifiableList(Arrays.asList(RANGE_NAMES));
  }

  /** @return the valid range for the capability if it has one, otherwise null. */
  public static PermissionRange.WithDefaults getRange(String varName) {
    if (QUERY_LIMIT.equalsIgnoreCase(varName)) {
      return new PermissionRange.WithDefaults(
          varName, 0, Integer.MAX_VALUE, 0, DEFAULT_MAX_QUERY_LIMIT);
    }
    if (BATCH_CHANGES_LIMIT.equalsIgnoreCase(varName)) {
      return new PermissionRange.WithDefaults(
          varName, 0, Integer.MAX_VALUE, 0, DEFAULT_MAX_BATCH_CHANGES_LIMIT);
    }
    return null;
  }

  private GlobalCapability() {
    // Utility class, do not create instances.
  }
}
