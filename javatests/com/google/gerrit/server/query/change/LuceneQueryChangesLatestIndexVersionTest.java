// Copyright (C) 2019 The Android Open Source Project
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

import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.changes.AddToAttentionSetInput;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.InMemoryRepositoryManager.Repo;
import com.google.gerrit.testing.IndexConfig;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class LuceneQueryChangesLatestIndexVersionTest extends LuceneQueryChangesTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    return IndexConfig.createForLucene();
  }

  @Test
  public void attentionSet() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));

    AddToAttentionSetInput input =
        new AddToAttentionSetInput(user.getAccountId().toString(), "some reason");
    gApi.changes().id(change1.getChangeId()).addToAttentionSet(input);

    assertQuery("attention:" + user.getUserName().get(), change1);
    assertQuery("-attention:" + user.getAccountId().toString(), change2);
  }
}
