// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.common.AccountExternalIdInfo;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ExternalIdIT extends AbstractDaemonTest {
  @Test
  public void getExternalIDs() throws Exception {
    Collection<AccountExternalId> expectedIds =
        accountCache.get(user.getId()).getExternalIds();

    List<AccountExternalIdInfo> expectedIdInfos = new ArrayList<>();
    for (AccountExternalId id : expectedIds) {
      id.setCanDelete(!id.getExternalId().equals("username:user"));
      id.setTrusted(true);
      expectedIdInfos.add(toInfo(id));
    }

    RestResponse response = userRestSession.get("/accounts/self/external.ids");
    response.assertOK();

    List<AccountExternalIdInfo> results =
        newGson().fromJson(response.getReader(),
            new TypeToken<List<AccountExternalIdInfo>>() {}.getType());

    Collections.sort(expectedIdInfos);
    Collections.sort(results);
    assertThat(results).containsExactlyElementsIn(expectedIdInfos);
  }

  private static AccountExternalIdInfo toInfo(AccountExternalId id) {
    AccountExternalIdInfo info = new AccountExternalIdInfo();
    info.identity = id.getExternalId();
    info.emailAddress = id.getEmailAddress();
    info.trusted = id.isTrusted() ? true : null;
    info.canDelete = id.canDelete() ? true : null;
    return info;
  }
}
