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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.restapi.change.QueryChanges;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.List;
import org.junit.Test;

@NoHttpd
public class QueryChangeIT extends AbstractDaemonTest {

  @Inject private Provider<QueryChanges> queryChangesProvider;

  @Test
  @SuppressWarnings("unchecked")
  public void multipleQueriesInOneRequestCanContainSameChange() throws Exception {
    String cId1 = createChange().getChangeId();
    String cId2 = createChange().getChangeId();
    int numericId1 = gApi.changes().id(cId1).get()._number;
    int numericId2 = gApi.changes().id(cId2).get()._number;

    gApi.changes().id(cId2).setWorkInProgress();

    QueryChanges queryChanges = queryChangesProvider.get();

    queryChanges.addQuery("is:open");
    queryChanges.addQuery("is:wip");

    List<List<ChangeInfo>> result =
        (List<List<ChangeInfo>>) queryChanges.apply(TopLevelResource.INSTANCE).value();
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).hasSize(2);
    assertThat(result.get(1)).hasSize(1);

    List<Integer> firstResultIds =
        ImmutableList.of(result.get(0).get(0)._number, result.get(0).get(1)._number);
    assertThat(firstResultIds).containsExactly(numericId1, numericId2);
    assertThat(result.get(1).get(0)._number).isEqualTo(numericId2);
  }
}
