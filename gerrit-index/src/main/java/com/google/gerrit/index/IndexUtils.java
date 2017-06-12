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

package com.google.gerrit.index;

import static com.google.gerrit.server.index.change.ChangeField.CHANGE;
import static com.google.gerrit.server.index.change.ChangeField.LEGACY_ID;
import static com.google.gerrit.server.index.change.ChangeField.PROJECT;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.QueryOptions;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;

public final class IndexUtils {
  public static final Map<String, String> CUSTOM_CHAR_MAPPING = ImmutableMap.of("_", " ", ".", " ");

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

  public static Set<String> fields(QueryOptions opts) {
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

  private IndexUtils() {
    // hide default constructor
  }
}
