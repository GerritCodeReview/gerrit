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

package com.google.gerrit.server.index.account;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.testutil.GerritBaseTests;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class AccountFieldTest extends GerritBaseTests {
  @Test
  public void refStateFieldValues() throws Exception {
    AllUsersName allUsersName = new AllUsersName(AllUsersNameProvider.DEFAULT);
    Account account = new Account(new Account.Id(1), TimeUtil.nowTs());
    String metaId = "0e39795bb25dc914118224995c53c5c36923a461";
    account.setMetaId(metaId);
    Iterable<byte[]> values =
        AccountField.REF_STATE.get(
            new AccountState(
                allUsersName, account, ImmutableSet.of(), ImmutableSet.of(), ImmutableMap.of()));
    assertThat(values).hasSize(1);
    String expectedValue =
        allUsersName.get() + ":" + RefNames.refsUsers(account.getId()) + ":" + metaId;
    assertThat(Iterables.getOnlyElement(values)).isEqualTo(expectedValue.getBytes(UTF_8));
  }

  @Test
  public void externalIdStateFieldValues() throws Exception {
    Account.Id id = new Account.Id(1);
    Account account = new Account(id, TimeUtil.nowTs());
    ExternalId extId1 =
        ExternalId.create(
            ExternalId.Key.create(ExternalId.SCHEME_MAILTO, "foo.bar@example.com"),
            id,
            "foo.bar@example.com",
            null,
            ObjectId.fromString("1b9a0cf038ea38a0ab08617c39aa8e28413a27ca"));
    ExternalId extId2 =
        ExternalId.create(
            ExternalId.Key.create(ExternalId.SCHEME_USERNAME, "foo"),
            id,
            null,
            "secret",
            ObjectId.fromString("5b3a73dc9a668a5b89b5f049225261e3e3291d1a"));
    Iterable<byte[]> values =
        AccountField.EXTERNAL_ID_STATE.get(
            new AccountState(
                null,
                account,
                ImmutableSet.of(),
                ImmutableSet.of(extId1, extId2),
                ImmutableMap.of()));
    Set<String> stringValues =
        StreamSupport.stream(values.spliterator(), false)
            .map(v -> new String(v, UTF_8))
            .collect(toSet());
    String expectedValue1 = extId1.key().sha1().name() + ":" + extId1.blobId().name();
    String expectedValue2 = extId2.key().sha1().name() + ":" + extId2.blobId().name();
    assertThat(stringValues).containsExactly(expectedValue1, expectedValue2);
  }
}
