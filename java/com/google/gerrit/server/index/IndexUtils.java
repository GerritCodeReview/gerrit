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
import static com.google.gerrit.server.index.change.ChangeField.LEGACY_ID;
import static com.google.gerrit.server.index.change.ChangeField.PROJECT;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.project.ProjectField;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.index.account.AccountField;
import com.google.gerrit.server.index.group.GroupField;
import com.google.gerrit.server.query.change.SingleGroupUser;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.errors.ConfigInvalidException;

public final class IndexUtils {
  public static final ImmutableMap<String, String> CUSTOM_CHAR_MAPPING =
      ImmutableMap.of("_", " ", ".", " ");

  public static final Function<Exception, IOException> MAPPER =
      new Function<Exception, IOException>() {
        @Override
        public IOException apply(Exception in) {
          if (in instanceof IOException) {
            return (IOException) in;
          } else if (in instanceof ExecutionException && in.getCause() instanceof IOException) {
            return (IOException) in.getCause();
          } else {
            return new IOException(in);
          }
        }
      };

  public static void setReady(SitePaths sitePaths, String name, int version, boolean ready)
      throws IOException {
    try {
      GerritIndexStatus cfg = new GerritIndexStatus(sitePaths);
      cfg.setReady(name, version, ready);
      cfg.save();
    } catch (ConfigInvalidException e) {
      throw new IOException(e);
    }
  }

  public static boolean getReady(SitePaths sitePaths, String name, int version) throws IOException {
    try {
      GerritIndexStatus cfg = new GerritIndexStatus(sitePaths);
      return cfg.getReady(name, version);
    } catch (ConfigInvalidException e) {
      throw new IOException(e);
    }
  }

  public static Set<String> accountFields(QueryOptions opts) {
    return accountFields(opts.fields());
  }

  public static Set<String> accountFields(Set<String> fields) {
    return fields.contains(AccountField.ID.getName())
        ? fields
        : Sets.union(fields, ImmutableSet.of(AccountField.ID.getName()));
  }

  public static Set<String> changeFields(QueryOptions opts) {
    // Ensure we request enough fields to construct a ChangeData. We need both
    // change ID and project, which can either come via the Change field or
    // separate fields.
    Set<String> fs = opts.fields();
    if (fs.contains(CHANGE.getName())) {
      // A Change is always sufficient.
      return fs;
    }
    if (fs.contains(PROJECT.getName()) && fs.contains(LEGACY_ID.getName())) {
      return fs;
    }
    return Sets.union(fs, ImmutableSet.of(LEGACY_ID.getName(), PROJECT.getName()));
  }

  public static Set<String> groupFields(QueryOptions opts) {
    Set<String> fs = opts.fields();
    return fs.contains(GroupField.UUID.getName())
        ? fs
        : Sets.union(fs, ImmutableSet.of(GroupField.UUID.getName()));
  }

  public static String describe(CurrentUser user) {
    if (user.isIdentifiedUser()) {
      return user.getAccountId().toString();
    }
    if (user instanceof SingleGroupUser) {
      return "group:" + user.getEffectiveGroups().getKnownGroups().iterator().next().toString();
    }
    return user.toString();
  }

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
