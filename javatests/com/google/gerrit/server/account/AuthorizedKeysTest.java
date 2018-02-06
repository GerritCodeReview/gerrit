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

import com.google.gerrit.reviewdb.client.Account;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

  private final Account.Id accountId = new Account.Id(1);

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
    AccountSshKey key = AccountSshKey.create(accountId, -1, KEY1);
    assertThat(key.valid()).isFalse();
    key = AccountSshKey.create(accountId, 0, KEY1);
    assertThat(key.valid()).isFalse();
    key = AccountSshKey.create(accountId, 1, KEY1);
    assertThat(key.valid()).isTrue();
  }

  @Test
  public void getters() throws Exception {
    AccountSshKey key = AccountSshKey.create(accountId, 1, KEY1);
    assertThat(key.sshPublicKey()).isEqualTo(KEY1);
    assertThat(key.algorithm()).isEqualTo(KEY1.split(" ")[0]);
    assertThat(key.encodedKey()).isEqualTo(KEY1.split(" ")[1]);
    assertThat(key.comment()).isEqualTo(KEY1.split(" ")[2]);
  }

  @Test
  public void keyWithNewLines() throws Exception {
    AccountSshKey key = AccountSshKey.create(accountId, 1, KEY1_WITH_NEWLINES);
    assertThat(key.sshPublicKey()).isEqualTo(KEY1);
    assertThat(key.algorithm()).isEqualTo(KEY1.split(" ")[0]);
    assertThat(key.encodedKey()).isEqualTo(KEY1.split(" ")[1]);
    assertThat(key.comment()).isEqualTo(KEY1.split(" ")[2]);
  }

  private static String toWindowsLineEndings(String s) {
    return s.replaceAll("\n", "\r\n");
  }

  private static void assertSerialization(
      List<Optional<AccountSshKey>> keys, StringBuilder expected) {
    assertThat(AuthorizedKeys.serialize(keys)).isEqualTo(expected.toString());
  }

  private static void assertParse(
      StringBuilder authorizedKeys, List<Optional<AccountSshKey>> expectedKeys) {
    Account.Id accountId = new Account.Id(1);
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
    AccountSshKey key = AccountSshKey.create(new Account.Id(1), keys.size() + 1, pub);
    keys.add(Optional.of(key));
    return key.sshPublicKey() + "\n";
  }

  /**
   * Adds the given public key as invalid SSH key to the given list.
   *
   * @return the expected line for this key in the authorized_keys file
   */
  private static String addInvalidKey(List<Optional<AccountSshKey>> keys, String pub) {
    AccountSshKey key = AccountSshKey.createInvalid(new Account.Id(1), keys.size() + 1, pub);
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
