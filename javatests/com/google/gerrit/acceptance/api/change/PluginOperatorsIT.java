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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.index.query.Matchable;
import com.google.gerrit.index.query.OperatorPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.restapi.change.QueryChanges;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.List;
import org.junit.Test;

public class PluginOperatorsIT extends AbstractDaemonTest {
  @Inject private Provider<QueryChanges> queryChangesProvider;

  @Test
  public void getChangeWithIsOperator() throws Exception {
    QueryChanges queryChanges = queryChangesProvider.get();
    queryChanges.addQuery("is:changeNumberEven_myplugin");

    String oddChangeId = createChange().getChangeId();
    String evenChangeId = createChange().getChangeId();
    BadRequestException exception =
        assertThrows(BadRequestException.class, () -> getChanges(queryChanges));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("Unrecognized value: changeNumberEven_myplugin");

    try (AutoCloseable ignored = installPlugin("myplugin", IsOperatorModule.class)) {
      List<?> changes = getChanges(queryChanges);
      assertThat(changes).hasSize(1);

      ChangeInfo c = (ChangeInfo) changes.get(0);
      String outputChangeId = c.changeId;
      assertThat(outputChangeId).isEqualTo(evenChangeId);
      assertThat(outputChangeId).isNotEqualTo(oddChangeId);
    }
  }

  protected static class IsOperatorModule extends AbstractModule {
    @Override
    public void configure() {
      bind(ChangeQueryBuilder.ChangeIsOperandFactory.class)
          .annotatedWith(Exports.named("changeNumberEven"))
          .to(SampleIsOperand.class);
    }
  }

  private static class SampleIsOperand implements ChangeQueryBuilder.ChangeIsOperandFactory {
    @Override
    public Predicate<ChangeData> create(ChangeQueryBuilder builder) throws QueryParseException {
      return new IsSamplePredicate();
    }
  }

  private static class IsSamplePredicate extends OperatorPredicate<ChangeData>
      implements Matchable<ChangeData> {

    public IsSamplePredicate() {
      super("is", "changeNumberEven");
    }

    @Override
    public boolean match(ChangeData changeData) {
      int id = changeData.getId().get();
      return id % 2 == 0;
    }

    @Override
    public int getCost() {
      return 0;
    }
  }

  private List<?> getChanges(QueryChanges queryChanges)
      throws AuthException, PermissionBackendException, BadRequestException {
    return queryChanges.apply(TopLevelResource.INSTANCE).value();
  }
}
