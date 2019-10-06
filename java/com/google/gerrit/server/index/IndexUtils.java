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

package com.google.gerrit.server.index;

import static com.google.gerrit.server.index.change.ChangeField.CHANGE;
import static com.google.gerrit.server.index.change.ChangeField.LEGACY_ID_STR;
import static com.google.gerrit.server.index.change.ChangeField.PROJECT;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.project.ProjectField;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.account.AccountField;
import com.google.gerrit.server.index.group.GroupField;
import com.google.gerrit.server.query.change.GroupBackedUser;
import java.io.IOException;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Set of index-related utility methods. */
public final class IndexUtils {

  /** Mark an index version as ready to serve queries. */
  public static void setReady(SitePaths sitePaths, String name, int version, boolean ready) {
    try {
      GerritIndexStatus cfg = new GerritIndexStatus(sitePaths);
      cfg.setReady(name, version, ready);
      cfg.save();
    } catch (ConfigInvalidException | IOException e) {
      throw new StorageException(e);
    }
  }

  /**
   * Returns a sanitized set of fields for account index queries by removing fields that the current
   * index version doesn't support and accounting for numeric vs. string primary keys. The primary
   * key situation is temporary and should be removed after the migration is done.
   */
  public static Set<String> accountFields(QueryOptions opts, boolean useLegacyNumericFields) {
    return accountFields(opts.fields(), useLegacyNumericFields);
  }

  /**
   * Returns a sanitized set of fields for account index queries by removing fields that the current
   * index version doesn't support and accounting for numeric vs. string primary keys. The primary
   * key situation is temporary and should be removed after the migration is done.
   */
  public static Set<String> accountFields(Set<String> fields, boolean useLegacyNumericFields) {
    String idFieldName =
        useLegacyNumericFields ? AccountField.ID.getName() : AccountField.ID_STR.getName();
    return fields.contains(idFieldName) ? fields : Sets.union(fields, ImmutableSet.of(idFieldName));
  }

  /**
   * Returns a sanitized set of fields for change index queries by removing fields that the current
   * index version doesn't support.
   */
  public static Set<String> changeFields(QueryOptions opts) {
    // Ensure we request enough fields to construct a ChangeData. We need both
    // change ID and project, which can either come via the Change field or
    // separate fields.
    Set<String> fs = opts.fields();
    if (fs.contains(CHANGE.getName())) {
      // A Change is always sufficient.
      return fs;
    }
    if (fs.contains(PROJECT.getName()) && fs.contains(LEGACY_ID_STR.getName())) {
      return fs;
    }
    return Sets.union(fs, ImmutableSet.of(LEGACY_ID_STR.getName(), PROJECT.getName()));
  }

  /**
   * Returns a sanitized set of fields for group index queries by removing fields that the index
   * doesn't support and accounting for numeric vs. string primary keys. The primary key situation
   * is temporary and should be removed after the migration is done.
   */
  public static Set<String> groupFields(QueryOptions opts) {
    Set<String> fs = opts.fields();
    return fs.contains(GroupField.UUID.getName())
        ? fs
        : Sets.union(fs, ImmutableSet.of(GroupField.UUID.getName()));
  }

  /** Returns a index-friendly representation of a {@link CurrentUser} to be used in queries. */
  public static String describe(CurrentUser user) {
    if (user.isIdentifiedUser()) {
      return user.getAccountId().toString();
    }
    if (user instanceof GroupBackedUser) {
      return "group:" + user.getEffectiveGroups().getKnownGroups().iterator().next().toString();
    }
    return user.toString();
  }

  /**
   * Returns a sanitized set of fields for project index queries by removing fields that the index
   * doesn't support.
   */
  public static Set<String> projectFields(QueryOptions opts) {
    Set<String> fs = opts.fields();
    return fs.contains(ProjectField.NAME.getName())
        ? fs
        : Sets.union(fs, ImmutableSet.of(ProjectField.NAME.getName()));
  }

  private IndexUtils() {
    // hide default constructor
  }
}
