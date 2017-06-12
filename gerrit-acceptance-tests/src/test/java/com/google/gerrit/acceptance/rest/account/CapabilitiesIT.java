// Copyright (C) 2013 The Android Open Source Project
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
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.common.data.GlobalCapability.ACCESS_DATABASE;
import static com.google.gerrit.common.data.GlobalCapability.ADMINISTRATE_SERVER;
import static com.google.gerrit.common.data.GlobalCapability.BATCH_CHANGES_LIMIT;
import static com.google.gerrit.common.data.GlobalCapability.DEFAULT_MAX_BATCH_CHANGES_LIMIT;
import static com.google.gerrit.common.data.GlobalCapability.DEFAULT_MAX_QUERY_LIMIT;
import static com.google.gerrit.common.data.GlobalCapability.PRIORITY;
import static com.google.gerrit.common.data.GlobalCapability.QUERY_LIMIT;
import static com.google.gerrit.common.data.GlobalCapability.RUN_AS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.Test;

public class CapabilitiesIT extends AbstractDaemonTest {

  @Test
  public void testCapabilitiesUser() throws Exception {
    Iterable<String> all =
        Iterables.filter(
            GlobalCapability.getAllNames(),
            c -> !ADMINISTRATE_SERVER.equals(c) && !PRIORITY.equals(c));

    allowGlobalCapabilities(REGISTERED_USERS, all);
    try {
      RestResponse r = userRestSession.get("/accounts/self/capabilities");
      r.assertOK();
      CapabilityInfo info =
          (new Gson()).fromJson(r.getReader(), new TypeToken<CapabilityInfo>() {}.getType());
      for (String c : GlobalCapability.getAllNames()) {
        if (ADMINISTRATE_SERVER.equals(c)) {
          assertThat(info.administrateServer).isFalse();
        } else if (BATCH_CHANGES_LIMIT.equals(c)) {
          assertThat(info.batchChangesLimit.min).isEqualTo((short) 0);
          assertThat(info.batchChangesLimit.max).isEqualTo((short) DEFAULT_MAX_BATCH_CHANGES_LIMIT);
        } else if (PRIORITY.equals(c)) {
          assertThat(info.priority).isFalse();
        } else if (QUERY_LIMIT.equals(c)) {
          assertThat(info.queryLimit.min).isEqualTo((short) 0);
          assertThat(info.queryLimit.max).isEqualTo((short) DEFAULT_MAX_QUERY_LIMIT);
        } else {
          assert_()
              .withFailureMessage(String.format("capability %s was not granted", c))
              .that((Boolean) CapabilityInfo.class.getField(c).get(info))
              .isTrue();
        }
      }
    } finally {
      removeGlobalCapabilities(REGISTERED_USERS, all);
    }
  }

  @Test
  public void testCapabilitiesAdmin() throws Exception {
    RestResponse r = adminRestSession.get("/accounts/self/capabilities");
    r.assertOK();
    CapabilityInfo info =
        (new Gson()).fromJson(r.getReader(), new TypeToken<CapabilityInfo>() {}.getType());
    for (String c : GlobalCapability.getAllNames()) {
      if (BATCH_CHANGES_LIMIT.equals(c)) {
        // It does not have default value for any user as it can override the
        // 'receive.batchChangesLimit'. It needs to be granted explicitly.
        assertThat(info.batchChangesLimit).isNull();
      } else if (PRIORITY.equals(c)) {
        assertThat(info.priority).isFalse();
      } else if (QUERY_LIMIT.equals(c)) {
        assert_().withFailureMessage("missing queryLimit").that(info.queryLimit).isNotNull();
        assertThat(info.queryLimit.min).isEqualTo((short) 0);
        assertThat(info.queryLimit.max).isEqualTo((short) DEFAULT_MAX_QUERY_LIMIT);
      } else if (ACCESS_DATABASE.equals(c)) {
        assertThat(info.accessDatabase).isFalse();
      } else if (RUN_AS.equals(c)) {
        assertThat(info.runAs).isFalse();
      } else {
        assert_()
            .withFailureMessage(String.format("capability %s was not granted", c))
            .that((Boolean) CapabilityInfo.class.getField(c).get(info))
            .isTrue();
      }
    }
  }
}
