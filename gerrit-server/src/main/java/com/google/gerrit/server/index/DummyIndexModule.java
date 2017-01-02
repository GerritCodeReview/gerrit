// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.DummyChangeIndex;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.AbstractModule;

public class DummyIndexModule extends AbstractModule {
  private static class DummyChangeIndexFactory implements ChangeIndex.Factory {
    @Override
    public ChangeIndex create(Schema<ChangeData> schema) {
      throw new UnsupportedOperationException();
    }
  }

  private static class DummyAccountIndexFactory implements AccountIndex.Factory {
    @Override
    public AccountIndex create(Schema<AccountState> schema) {
      throw new UnsupportedOperationException();
    }
  }

  private static class DummyGroupIndexFactory implements GroupIndex.Factory {
    @Override
    public GroupIndex create(Schema<AccountGroup> schema) {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  protected void configure() {
    install(new IndexModule(1));
    bind(IndexConfig.class).toInstance(IndexConfig.createDefault());
    bind(Index.class).toInstance(new DummyChangeIndex());
    bind(AccountIndex.Factory.class).toInstance(new DummyAccountIndexFactory());
    bind(ChangeIndex.Factory.class).toInstance(new DummyChangeIndexFactory());
    bind(GroupIndex.Factory.class).toInstance(new DummyGroupIndexFactory());
  }
}
