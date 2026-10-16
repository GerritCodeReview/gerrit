// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.util.time.TimeUtil;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.After;
import org.junit.Test;

public class AuthorizedKeysTest {
  private static final String KEY1 =
      "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQCgug5VyMXQGnem2H1KVC4/HcRcD4zzBqS"
          + "uJBRWVonSSoz3RoAZ7bWXCVVGwchtXwUURD689wFYdiPecOrWOUgeeyRq754YWRhU+W28"
          + "vf8IZixgjCmiBhaL2gt3wff6pP+NXJpTSA4aeWE5DfNK5tZlxlSxqkKOS8JRSUeNQov5T"
          + "w== john.doe@example.com";
  private static final String KEY1_WITH_NEWLINES =
      "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQCgug5VyMXQGnem2H1KVC4/HcRcD4zzBqS\n"
          + "uJBRWVonSSoz3RoAZ7bWXCVVGwchtXwUURD689wFYdiPecOrWOUgeeyRq754YWRhU+W28\n"
          + "vf8IZixgjCmiBhaL2gt3wff6pP+NXJpTSA4aeWE5DfNK5tZlxlSxqkKOS8JRSUeNQov5T\n"
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
  private static final String KEY5 =
      "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQCgBRKGhiXvY6D9sM+Vbth5Kate57YF7kD"
          + "rqIyUiYIMJK93/AXc8qR/J/p3OIFQAxvLz1qozAur3j5HaiwvxVU19IiSA0vafdhaDLRi"
          + "zRuEL5e/QOu9yGq9xkWApCmg6edpWAHG+Bx4AldU78MiZvzoB7gMMdxc9RmZ1gYj/DjxV"
          + "w== john.doe@example.com";

  private final Account.Id accountId = Account.id(1);

  @After
  public void resetClock() {
    TimeUtil.resetCurrentMillisSupplier();
  }

  @Test
  public void test() throws Exception {
    List<Optional<AccountSshKey>> keys = new ArrayList<>();
    StringBuilder expected = new StringBuilder();
    assertSerialization(keys, expected);
    assertParse(expected, keys);

    expected.append(addKey(keys, KEY1));
    assertSerialization(keys, expected);
    assertParse(expected, keys);

    expected.append(addKey(keys, KEY2));
    assertSerialization(keys, expected);
    assertParse(expected, keys);

    expected.append(addInvalidKey(keys, KEY3));
    assertSerialization(keys, expected);
    assertParse(expected, keys);

    expected.append(addKey(keys, KEY4));
    assertSerialization(keys, expected);
    assertParse(expected, keys);

    expected.append(addDeletedKey(keys));
    assertSerialization(keys, expected);
    assertParse(expected, keys);

    expected.append(addKey(keys, KEY5));
    assertSerialization(keys, expected);
    assertParse(expected, keys);
  }

  @Test
  public void parseWindowsLineEndings() throws Exception {
    List<Optional<AccountSshKey>> keys = new ArrayList<>();
    StringBuilder authorizedKeys = new StringBuilder();
    authorizedKeys.append(toWindowsLineEndings(addKey(keys, KEY1)));
    assertParse(authorizedKeys, keys);

    authorizedKeys.append(toWindowsLineEndings(addKey(keys, KEY2)));
    assertParse(authorizedKeys, keys);

    authorizedKeys.append(toWindowsLineEndings(addInvalidKey(keys, KEY3)));
    assertParse(authorizedKeys, keys);

    authorizedKeys.append(toWindowsLineEndings(addKey(keys, KEY4)));
    assertParse(authorizedKeys, keys);

    authorizedKeys.append(toWindowsLineEndings(addDeletedKey(keys)));
    assertParse(authorizedKeys, keys);

    authorizedKeys.append(toWindowsLineEndings(addKey(keys, KEY5)));
    assertParse(authorizedKeys, keys);
  }

  @Test
  public void validity() throws Exception {
    AccountSshKey key = new AccountSshKey(accountId, -1, KEY1);
    assertThat(key.valid()).isFalse();
    key = new AccountSshKey(accountId, 0, KEY1);
    assertThat(key.valid()).isFalse();
    key = new AccountSshKey(accountId, 1, KEY1);
    assertThat(key.valid()).isTrue();
  }

  @Test
  public void getters() throws Exception {
    AccountSshKey key = new AccountSshKey(accountId, 1, KEY1);
    assertThat(key.sshPublicKey()).isEqualTo(KEY1);

    List<String> keyParts = Splitter.on(" ").splitToList(KEY1);
    assertThat(key.algorithm()).isEqualTo(keyParts.get(0));
    assertThat(key.encodedKey()).isEqualTo(keyParts.get(1));
    assertThat(key.comment()).isEqualTo(keyParts.get(2));
  }

  @Test
  public void keyWithNewLines() throws Exception {
    AccountSshKey key = new AccountSshKey(accountId, 1, KEY1_WITH_NEWLINES);
    assertThat(key.sshPublicKey()).isEqualTo(KEY1);

    List<String> keyParts = Splitter.on(" ").splitToList(KEY1);
    assertThat(key.algorithm()).isEqualTo(keyParts.get(0));
    assertThat(key.encodedKey()).isEqualTo(keyParts.get(1));
    assertThat(key.comment()).isEqualTo(keyParts.get(2));
  }

  @Test
  public void expiryTimeIsParsed() {
    assertExpiryTime("20300102Z", Instant.parse("2030-01-02T00:00:00Z"));
    assertExpiryTime("203001021530Z", Instant.parse("2030-01-02T15:30:00Z"));
    assertExpiryTime("20300102153045Z", Instant.parse("2030-01-02T15:30:45Z"));
    assertExpiryTime(
        "20300102", LocalDate.of(2030, 1, 2).atStartOfDay(ZoneId.systemDefault()).toInstant());
  }

  @Test
  public void keyIsExpiredAfterExpiryTime() {
    AccountSshKey key =
        AuthorizedKeys.parseKey(accountId, 1, "expiry-time=\"20300102030405Z\" " + KEY1);
    Instant expiry = Instant.parse("2030-01-02T03:04:05Z");
    TimeUtil.setCurrentMillisSupplier(expiry::toEpochMilli);
    assertThat(key.isExpired()).isFalse();
    TimeUtil.setCurrentMillisSupplier(() -> expiry.plusSeconds(1).toEpochMilli());
    assertThat(key.isExpired()).isTrue();
  }

  @Test
  public void keyWithoutExpiryTimeIsNotExpired() {
    assertThat(AuthorizedKeys.parseKey(accountId, 1, KEY1).isExpired()).isFalse();
  }

  @Test
  public void invalidExpiryTimeIsKeptAsOption() {
    for (String value : ImmutableList.of("soon", "20300231Z", "2030010", "")) {
      String line = "expiry-time=\"" + value + "\" " + KEY1;
      AccountSshKey key = AuthorizedKeys.parseKey(accountId, 1, line);
      assertThat(key.expiryTime()).isEmpty();
      assertThat(key.otherOptions()).containsExactly("expiry-time", value);
      assertThat(AuthorizedKeys.format(key)).isEqualTo(line);
    }
  }

  @Test
  public void optionsSurviveSerialization() {
    AccountSshKey key =
        AuthorizedKeys.parseKey(
            accountId,
            1,
            "no-pty,!agent-forwarding,expiry-time=\"20300102Z\",command=\"echo a b\" " + KEY1);
    assertThat(key.sshPublicKey()).isEqualTo(KEY1);
    assertThat(key.expiryTime()).hasValue(Instant.parse("2030-01-02T00:00:00Z"));
    assertThat(key.otherOptions())
        .containsExactly("no-pty", "true", "!agent-forwarding", "false", "command", "echo a b");

    String line = AuthorizedKeys.format(key);
    assertThat(AuthorizedKeys.parseKey(accountId, 1, line)).isEqualTo(key);
    assertThat(AuthorizedKeys.format(AuthorizedKeys.parseKey(accountId, 1, line))).isEqualTo(line);
  }

  @Test
  public void optionsOfInvalidKeySurviveSerialization() {
    String content =
        AuthorizedKeys.INVALID_KEY_COMMENT_PREFIX
            + "expiry-time=\"20300102000000Z\",no-pty "
            + KEY1
            + "\n";
    List<Optional<AccountSshKey>> keys = AuthorizedKeys.parse(accountId, content);
    assertThat(keys.get(0).get().valid()).isFalse();
    assertThat(keys.get(0).get().expiryTime()).hasValue(Instant.parse("2030-01-02T00:00:00Z"));
    assertThat(AuthorizedKeys.serialize(keys)).isEqualTo(content);
  }

  @Test
  public void keyWithoutOptionsIsSerializedAsIs() {
    assertThat(AuthorizedKeys.format(AuthorizedKeys.parseKey(accountId, 1, KEY1))).isEqualTo(KEY1);
  }

  @Test
  public void unparsableKeyIsKeptAsIs() {
    String unparsable = "ssh-rsa not-base64!! john.doe@example.com";
    AccountSshKey key = AuthorizedKeys.parseKey(accountId, 1, unparsable);
    assertThat(key.otherOptions()).isEmpty();
    assertThat(key.expiryTime()).isEmpty();
    assertThat(AuthorizedKeys.format(key)).isEqualTo(unparsable);
    assertThat(AuthorizedKeys.format(key.asInvalid())).isEqualTo(unparsable);
  }

  private void assertExpiryTime(String value, Instant expected) {
    AccountSshKey key =
        AuthorizedKeys.parseKey(accountId, 1, "expiry-time=\"" + value + "\" " + KEY1);
    assertThat(key.expiryTime()).hasValue(expected);
    assertThat(key.otherOptions()).isEmpty();
  }

  private static String toWindowsLineEndings(String s) {
    return s.replace("\n", "\r\n");
  }

  private static void assertSerialization(
      List<Optional<AccountSshKey>> keys, StringBuilder expected) {
    assertThat(AuthorizedKeys.serialize(keys)).isEqualTo(expected.toString());
  }

  private static void assertParse(
      StringBuilder authorizedKeys, List<Optional<AccountSshKey>> expectedKeys) {
    Account.Id accountId = Account.id(1);
    List<Optional<AccountSshKey>> parsedKeys =
        AuthorizedKeys.parse(accountId, authorizedKeys.toString());
    assertThat(parsedKeys).containsExactlyElementsIn(expectedKeys);
    int seq = 1;
    for (Optional<AccountSshKey> sshKey : parsedKeys) {
      if (sshKey.isPresent()) {
        assertThat(sshKey.get().accountId()).isEqualTo(accountId);
        assertThat(sshKey.get().seq()).isEqualTo(seq);
      }
      seq++;
    }
  }

  /**
   * Adds the given public key as new SSH key to the given list.
   *
   * @return the expected line for this key in the authorized_keys file
   */
  private static String addKey(List<Optional<AccountSshKey>> keys, String pub) {
    AccountSshKey key = new AccountSshKey(Account.id(1), keys.size() + 1, pub);
    keys.add(Optional.of(key));
    return key.sshPublicKey() + "\n";
  }

  /**
   * Adds the given public key as invalid SSH key to the given list.
   *
   * @return the expected line for this key in the authorized_keys file
   */
  private static String addInvalidKey(List<Optional<AccountSshKey>> keys, String pub) {
    AccountSshKey key = new AccountSshKey(Account.id(1), keys.size() + 1, pub, false);
    keys.add(Optional.of(key));
    return AuthorizedKeys.INVALID_KEY_COMMENT_PREFIX + key.sshPublicKey() + "\n";
  }

  /**
   * Adds a deleted SSH key to the given list.
   *
   * @return the expected line for this key in the authorized_keys file
   */
  private static String addDeletedKey(List<Optional<AccountSshKey>> keys) {
    keys.add(Optional.empty());
    return AuthorizedKeys.DELETED_KEY_COMMENT + "\n";
  }
}
