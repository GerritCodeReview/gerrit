// Copyright (C) 2026 Monty Taylor
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

package com.google.gerrit.server.change;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

/** Advertises indexed dependency support only after the administrator enables the rollout. */
@Singleton
public class IndexedChangeDependenciesSupport {
  private final ChangeIndexCollection indexes;
  private final boolean enabled;

  @Inject
  public IndexedChangeDependenciesSupport(
      ChangeIndexCollection indexes, @GerritServerConfig Config config) {
    this.indexes = indexes;
    enabled = config.getBoolean("change", "enableIndexedDependencies", false);
  }

  public boolean isEnabled() {
    ChangeIndex index = indexes.getSearchIndex();
    return enabled
        && index != null
        && index.getSchema().hasField(ChangeField.CURRENT_REVISION_PARENTS_SPEC);
  }
}
