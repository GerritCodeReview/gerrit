// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.index.testing;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.server.index.AbstractIndexModule;
import com.google.gerrit.server.index.VersionManager;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.group.GroupIndex;

/** Module to bind {@link FakeIndexModule}. */
public class FakeIndexModule extends AbstractIndexModule {
  public static FakeIndexModule singleVersionAllLatest(int threads, boolean secondary) {
    return new FakeIndexModule(ImmutableMap.of(), threads, secondary);
  }

  public static FakeIndexModule singleVersionWithExplicitVersions(
      ImmutableMap<String, Integer> versions, int threads, boolean secondary) {
    return new FakeIndexModule(versions, threads, secondary);
  }

  public static FakeIndexModule latestVersion(boolean secondary) {
    return new FakeIndexModule(/* singleVersions= */ null, -1 /* direct executor */, secondary);
  }

  private FakeIndexModule(
      ImmutableMap<String, Integer> singleVersions, int threads, boolean secondary) {
    super(singleVersions, threads, secondary);
  }

  @Override
  protected Class<? extends AccountIndex> getAccountIndex() {
    return AbstractFakeIndex.FakeAccountIndex.class;
  }

  @Override
  protected Class<? extends ChangeIndex> getChangeIndex() {
    return AbstractFakeIndex.FakeChangeIndex.class;
  }

  @Override
  protected Class<? extends GroupIndex> getGroupIndex() {
    return AbstractFakeIndex.FakeGroupIndex.class;
  }

  @Override
  protected Class<? extends ProjectIndex> getProjectIndex() {
    return AbstractFakeIndex.FakeProjectIndex.class;
  }

  @Override
  protected Class<? extends VersionManager> getVersionManager() {
    return FakeIndexVersionManager.class;
  }
}
