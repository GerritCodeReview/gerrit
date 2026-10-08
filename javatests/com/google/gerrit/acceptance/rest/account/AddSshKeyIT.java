// Copyright (C) 2026 The Android Open Source Project
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
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.extensions.common.SshKeyInfo;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

@UseSsh
public class AddSshKeyIT extends AbstractDaemonTest {
  private static final String KEY1 =
      "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQCgug5VyMXQGnem2H1KVC4/HcRcD4zzBqS"
          + "uJBRWVonSSoz3RoAZ7bWXCVVGwchtXwUURD689wFYdiPecOrWOUgeeyRq754YWRhU+W28"
          + "vf8IZixgjCmiBhaL2gt3wff6pP+NXJpTSA4aeWE5DfNK5tZlxlSxqkKOS8JRSUeNQov5T"
          + "w== john.doe@example.com";
  private static final String KEY2 =
      "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQDm5yP7FmEoqzQRDyskX+9+N0q9GrvZeh5"
          + "RG52EUpE4ms/Ujm3ewV1LoGzc/lYKJAIbdcZQNJ9+06EfWZaIRA3oOwAPe1eCnX+aLr8E"
          + "6Tw2gDMQOGc5e9HfyXpC2pDvzauoZNYqLALOG3y/1xjo7IH8GYRS2B7zO/Mf9DdCcCKSf"
          + "w== john.doe@example.com";
  private static final String KEY3 =
      "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQCaS7RHEcZ/zjl9hkWkqnm29RNr2OQ/TZ5"
          + "jk2qBVMH3BgzPsTsEs+7ag9tfD8OCj+vOcwm626mQBZoR2e3niHa/9gnHBHFtOrGfzKbp"
          + "RjTWtiOZbB9HF+rqMVD+Dawo/oicX/dDg7VAgOFSPothe6RMhbgWf84UcK5aQd5eP5y+t"
          + "Q== john.doe@example.com";
  private static final String KEY4 =
      "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQDIJzW9BaAeO+upFletwwEBnGS15lJmS5i"
          + "08/NiFef0jXtNNKcLtnd13bq8jOi5VA2is0bwof1c8YbwcvUkdFa8RL5aXoyZBpfYZsWs"
          + "/YBLZGiHy5rjooMZQMnH37A50cBPnXr0AQz0WRBxLDBDyOZho+O/DfYAKv4rzPSQ3yw4+"
          + "w== john.doe@example.com";

  private static final String KEY5_NO_COMMENT =
      "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQCgBRKGhiXvY6D9sM+Vbth5Kate57YF7kD"
          + "rqIyUiYIMJK93/AXc8qR/J/p3OIFQAxvLz1qozAur3j5HaiwvxVU19IiSA0vafdhaDLRi"
          + "zRuEL5e/QOu9yGq9xkWApCmg6edpWAHG+Bx4AldU78MiZvzoB7gMMdxc9RmZ1gYj/DjxV"
          + "w==";

  @Inject private VersionedAuthorizedKeys.Accessor authorizedKeys;

  @Test
  public void addKeyWithoutLifetimeNeverExpires() throws Exception {
    SshKeyInfo info = add(KEY1, "");
    assertThat(info.expiration).isNull();
  }

  @Test
  public void addKeyWithLifetimeSetsExpiration() throws Exception {
    Instant before = Instant.now();
    SshKeyInfo info = add(KEY2, "?lifetime=1d");
    assertExpiresIn(info, Duration.ofDays(1), before);

    List<AccountSshKey> keys = authorizedKeys.getKeys(admin.id());
    AccountSshKey stored = keys.get(keys.size() - 1);
    assertThat(stored.expirationDate()).hasValue(info.expiration.toInstant());
    assertThat(stored.isExpired()).isFalse();
  }

  @Test
  public void invalidLifetimeIsRejected() throws Exception {
    adminRestSession
        .postRaw("/accounts/self/sshkeys?lifetime=0", RawInputUtil.create(KEY3))
        .assertBadRequest();
    adminRestSession
        .postRaw("/accounts/self/sshkeys?lifetime=bogus", RawInputUtil.create(KEY3))
        .assertBadRequest();
  }

  @Test
  @GerritConfig(name = "auth.maxSshKeyLifetime", value = "7d")
  public void maxLifetimeIsAppliedAsDefault() throws Exception {
    Instant before = Instant.now();
    SshKeyInfo info = add(KEY3, "");
    assertExpiresIn(info, Duration.ofDays(7), before);
  }

  @Test
  @GerritConfig(name = "auth.maxSshKeyLifetime", value = "7d")
  public void lifetimeExceedingMaxIsRejected() throws Exception {
    Instant before = Instant.now();
    adminRestSession
        .postRaw("/accounts/self/sshkeys?lifetime=8d", RawInputUtil.create(KEY4))
        .assertBadRequest();
    assertExpiresIn(add(KEY4, "?lifetime=6d"), Duration.ofDays(6), before);
  }

  @Test
  public void expiredKeyIsReportedAsExpired() throws Exception {
    Instant past = Instant.now().minusSeconds(60);
    AccountSshKey key = authorizedKeys.addKey(admin.id(), KEY1 + " expired", Optional.of(past));
    assertThat(key.isExpired()).isTrue();
    assertThat(authorizedKeys.getKey(admin.id(), key.seq()).isExpired()).isTrue();
  }

  @Test
  public void addingExistingKeyIsRejected() throws Exception {
    add(KEY5_NO_COMMENT + " first-comment", "?lifetime=1d");
    assertRejected(
        KEY5_NO_COMMENT + " first-comment",
        "?lifetime=30d",
        VersionedAuthorizedKeys.KEY_ALREADY_EXISTS);
  }

  @Test
  public void addingExistingKeyWithDifferentCommentIsRejected() throws Exception {
    add(KEY5_NO_COMMENT + " first-comment", "");
    assertRejected(
        KEY5_NO_COMMENT + " second-comment", "", VersionedAuthorizedKeys.KEY_ALREADY_EXISTS);
  }

  @Test
  public void addingExpiredKeyIsRejectedAsPreviouslyUsed() throws Exception {
    authorizedKeys.addKey(admin.id(), KEY2 + " dup", Optional.of(Instant.now().minusSeconds(60)));
    assertRejected(KEY2 + " dup", "", VersionedAuthorizedKeys.KEY_USED_PREVIOUSLY);
    assertRejected(KEY2 + " other-comment", "", VersionedAuthorizedKeys.KEY_USED_PREVIOUSLY);
  }

  @Test
  public void addingDeletedKeyWithExpirationIsRejectedAsPreviouslyUsed() throws Exception {
    SshKeyInfo info = add(KEY3, "?lifetime=1d");
    adminRestSession.delete("/accounts/self/sshkeys/" + info.seq).assertNoContent();
    assertRejected(KEY3, "", VersionedAuthorizedKeys.KEY_USED_PREVIOUSLY);
    assertRejected(KEY3 + " other-comment", "", VersionedAuthorizedKeys.KEY_USED_PREVIOUSLY);
  }

  /** The expiration is truncated to whole seconds, so it may be up to a second early. */
  private static void assertExpiresIn(SshKeyInfo info, Duration lifetime, Instant requestedAt) {
    assertThat(info.expiration).isNotNull();
    Instant expiration = info.expiration.toInstant();
    assertThat(expiration).isAtLeast(requestedAt.plus(lifetime).minusSeconds(1));
    assertThat(expiration).isAtMost(Instant.now().plus(lifetime));
  }

  private void assertRejected(String key, String query, String message) throws Exception {
    RestResponse res =
        adminRestSession.postRaw("/accounts/self/sshkeys" + query, RawInputUtil.create(key));
    res.assertBadRequest();
    assertThat(res.getEntityContent()).contains(message);
  }

  private SshKeyInfo add(String key, String query) throws Exception {
    RestResponse res =
        adminRestSession.postRaw("/accounts/self/sshkeys" + query, RawInputUtil.create(key));
    res.assertCreated();
    return newGson().fromJson(res.getReader(), SshKeyInfo.class);
  }
}
