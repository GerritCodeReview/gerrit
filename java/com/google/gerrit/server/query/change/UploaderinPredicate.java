// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.index.query.PostFilterPredicate;
import com.google.gerrit.server.IdentifiedUser;

/**
 * Predicate that matches changes where the latest patch set was uploaded by a user in the provided
 * group.
 */
public class UploaderinPredicate extends PostFilterPredicate<ChangeData> {
  protected final IdentifiedUser.GenericFactory userFactory;
  protected final AccountGroup.UUID uuid;

  public UploaderinPredicate(IdentifiedUser.GenericFactory userFactory, AccountGroup.UUID uuid) {
    super(ChangeQueryBuilder.FIELD_UPLOADERIN, uuid.get());
    this.userFactory = userFactory;
    this.uuid = uuid;
  }

  @Override
  public boolean match(ChangeData cd) {
    PatchSet latestPatchSet = cd.currentPatchSet();
    if (latestPatchSet == null) {
      return false;
    }
    IdentifiedUser uploader = userFactory.create(latestPatchSet.uploader());
    return uploader.getEffectiveGroups().contains(uuid);
  }

  @Override
  public int getCost() {
    return 2;
  }
}
