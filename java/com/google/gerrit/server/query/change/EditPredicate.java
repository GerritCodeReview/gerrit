// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.query.PostFilterPredicate;
import com.google.gerrit.server.git.GitRepositoryManager;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class EditPredicate extends PostFilterPredicate<ChangeData> {
  private final GitRepositoryManager repoManager;

  private final Account.Id accountId;

  public EditPredicate(
      String operator, String value, Account.Id accountId, GitRepositoryManager repoManager) {
    super(operator, value);
    this.repoManager = repoManager;
    this.accountId = accountId;
  }

  @Override
  public boolean match(ChangeData cd) {
    try (Repository repo = repoManager.openRepository(cd.project())) {
      for (Ref ref : repo.getRefDatabase().getRefsByPrefix(RefNames.refsEditPrefix(accountId))) {
        Change.Id changeId = Change.Id.fromEditRefPart(ref.getName());
        if (changeId.equals(cd.getId())) {
          return true;
        }
      }
      return false;
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public List<ChangeData> matchMany(List<ChangeData> in) {
    ImmutableList.Builder<ChangeData> result = ImmutableList.builder();
    Map<Project.NameKey, List<ChangeData>> byProject =
        in.stream().collect(Collectors.groupingBy(ChangeData::project));
    for (Project.NameKey project : byProject.keySet()) {
      List<ChangeData> cds = byProject.get(project);
      try (Repository repo = repoManager.openRepository(project)) {
        List<Ref> editRefs =
            repo.getRefDatabase().getRefsByPrefix(RefNames.refsEditPrefix(accountId));
        Set<Change.Id> changeIdsWithEdits =
            editRefs.stream()
                .map(Ref::getName)
                .map(Change.Id::fromEditRefPart)
                .collect(Collectors.toSet());
        cds.stream().filter(cd -> changeIdsWithEdits.contains(cd.getId())).forEach(result::add);
      } catch (IOException e) {
        throw new StorageException(e);
      }
    }
    return result.build();
  }

  @Override
  public int getCost() {
    return 1;
  }
}
