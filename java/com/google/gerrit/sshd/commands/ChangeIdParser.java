// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.sshd.BaseCommand.UnloggedFailure;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class ChangeIdParser {
  private final Provider<InternalChangeQuery> queryProvider;

  @Inject
  ChangeIdParser(Provider<InternalChangeQuery> queryProvider) {
    this.queryProvider = queryProvider;
  }

  public Change parseChangeId(String token, ProjectState projectState, String branch)
      throws UnloggedFailure {
    if (token.matches(Change.CHANGE_ID_PATTERN)) {
      InternalChangeQuery query = queryProvider.get();
      BranchNameKey b = BranchNameKey.create(projectState.getNameKey(), branch);
      List<ChangeData> matches = query.byBranchKey(b, Change.Key.parse(token));

      switch (matches.size()) {
        case 1:
          return matches.iterator().next().change();
        case 0:
          throw error("\"" + token + "\" no such patch set");
        default:
          throw error("\"" + token + "\" matches multiple patch sets");
      }
    }

    throw error("\"" + token + "\" is not a valid change");
  }

  public static UnloggedFailure error(String msg) {
    return new UnloggedFailure(1, msg);
  }
}
