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
import java.util.List;

/** Server wide capabilities. Represented as {@link Permission} objects. */
public class GlobalCapability {
  /**
   * Denotes the server's administrators.
   * <p>
   * This is similar to UNIX root, or Windows SYSTEM account. Any user that
   * has this capability can perform almost any other action, or can grant
   * themselves the power to perform any other action on the site. Most of
   * the other capabilities and permissions fall-back to the predicate
   * "OR user has capablity ADMINISTRATE_SERVER".
   */
  public static final String ADMINISTRATE_SERVER = "administrateServer";

  /** Can create any account on the server. */
  public static final String CREATE_ACCOUNT = "createAccount";

  /** Can create any group on the server. */
  public static final String CREATE_GROUP = "createGroup";

  /** Can create any project on the server. */
  public static final String CREATE_PROJECT = "createProject";

  /** Can flush any cache except the active web_sessions cache. */
  public static final String FLUSH_CACHES = "flushCaches";

  /** Can terminate any task using the kill command. */
  public static final String KILL_TASK = "killTask";

  /** Queue a user can access to submit their tasks to. */
  public static final String PRIORITY = "priority";

  /** Maximum result limit per executed query. */
  public static final String QUERY_LIMIT = "queryLimit";

  /** Forcefully restart replication to any configured destination. */
  public static final String START_REPLICATION = "startReplication";

  /** Can view the server's current cache states. */
  public static final String VIEW_CACHES = "viewCaches";

  /** Can view open connections to the server's SSH port. */
  public static final String VIEW_CONNECTIONS = "viewConnections";

  /** Can view all pending tasks in the queue (not just the filtered set). */
  public static final String VIEW_QUEUE = "viewQueue";

  private static final List<String> NAMES_LC;

  static {
    NAMES_LC = new ArrayList<String>();
    NAMES_LC.add(ADMINISTRATE_SERVER.toLowerCase());
    NAMES_LC.add(CREATE_ACCOUNT.toLowerCase());
    NAMES_LC.add(CREATE_GROUP.toLowerCase());
    NAMES_LC.add(CREATE_PROJECT.toLowerCase());
    NAMES_LC.add(FLUSH_CACHES.toLowerCase());
    NAMES_LC.add(KILL_TASK.toLowerCase());
    NAMES_LC.add(PRIORITY.toLowerCase());
    NAMES_LC.add(QUERY_LIMIT.toLowerCase());
    NAMES_LC.add(START_REPLICATION.toLowerCase());
    NAMES_LC.add(VIEW_CACHES.toLowerCase());
    NAMES_LC.add(VIEW_CONNECTIONS.toLowerCase());
    NAMES_LC.add(VIEW_QUEUE.toLowerCase());
  }

  /** @return true if the name is recognized as a capability name. */
  public static boolean isCapability(String varName) {
    return NAMES_LC.contains(varName.toLowerCase());
  }

  /** @return true if the capability should have a range attached. */
  public static boolean hasRange(String varName) {
    return QUERY_LIMIT.equalsIgnoreCase(varName);
  }

  /** @return the valid range for the capability if it has one, otherwise null. */
  public static PermissionRange.WithDefaults getRange(String varName) {
    if (QUERY_LIMIT.equalsIgnoreCase(varName)) {
      return new PermissionRange.WithDefaults(
          varName,
          0, Integer.MAX_VALUE,
          0, 500);
    }
    return null;
  }

  private GlobalCapability() {
    // Utility class, do not create instances.
  }
}
