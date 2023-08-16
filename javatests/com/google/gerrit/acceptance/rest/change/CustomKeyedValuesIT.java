// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.server.notedb.ChangeUpdate.MAX_CUSTOM_KEYED_VALUES;
import static com.google.gerrit.server.notedb.ChangeUpdate.MAX_CUSTOM_KEYED_VALUE_LENGTH;
import static com.google.gerrit.server.notedb.ChangeUpdate.MAX_CUSTOM_KEY_LENGTH;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.truth.MapSubject;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.changes.CustomKeyedValuesInput;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.inject.Inject;
import org.junit.Test;

public class CustomKeyedValuesIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void getNoCustomKeyedValues() throws Exception {
    // Get on a change with no custom keyed values returns an empty list.
    PushOneCommit.Result r = createChange();
    assertThatGet(r).isEmpty();
  }

  @Test
  public void parsesInputCorrectly() throws Exception {
    PushOneCommit.Result r = createChange();
    String endpoint = "/changes/" + r.getChangeId() + "/custom_keyed_values";
    CustomKeyedValuesInput input = new CustomKeyedValuesInput();
    input.add = ImmutableMap.of("key", "value");
    RestResponse response = adminRestSession.post(endpoint, input);
    response.assertOK();

    assertThatGet(r).containsExactly("key", "value");
  }

  @Test
  public void addSingleCustomKeyedValue() throws Exception {
    PushOneCommit.Result r = createChange();
    ChangeMessageInfo last = getLastMessage(r);

    addCustomKeyedValues(r, ImmutableMap.of("key1", "value1"));
    assertThatGet(r).containsExactly("key1", "value1");
    assertNoNewMessageSince(r, last);

    addCustomKeyedValues(r, ImmutableMap.of("key2", "value2"));
    assertThatGet(r).containsExactly("key1", "value1", "key2", "value2");
    assertNoNewMessageSince(r, last);
  }

  @Test
  public void addInvalidCustomKeyedValue() throws Exception {
    PushOneCommit.Result r = createChange();

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> addCustomKeyedValues(r, ImmutableMap.of("key=", "value")));
    assertThat(thrown).hasMessageThat().contains("custom keys may not contain equals");
  }

  @Test
  public void addMultipleCustomKeyedValues() throws Exception {
    PushOneCommit.Result r = createChange();
    ChangeMessageInfo last = getLastMessage(r);
    addCustomKeyedValues(r, ImmutableMap.of("key1", "value1", "key2", "value2"));
    assertThatGet(r).containsExactly("key1", "value1", "key2", "value2");
    assertNoNewMessageSince(r, last);

    addCustomKeyedValues(r, ImmutableMap.of("key3", "value3"));
    assertThatGet(r).containsExactly("key1", "value1", "key2", "value2", "key3", "value3");
    assertNoNewMessageSince(r, last);
  }

  @Test
  public void addAlreadyExistingCustomKeyedValue() throws Exception {
    PushOneCommit.Result r = createChange();
    ChangeMessageInfo last = getLastMessage(r);
    addCustomKeyedValues(r, ImmutableMap.of("key1", "value1"));
    assertThatGet(r).containsExactly("key1", "value1");
    assertNoNewMessageSince(r, last);

    addCustomKeyedValues(r, ImmutableMap.of("key1", "value2"));
    assertThatGet(r).containsExactly("key1", "value2");
    assertNoNewMessageSince(r, last);
  }

  @Test
  public void removeSingleCustomKeyedValue() throws Exception {
    PushOneCommit.Result r = createChange();
    ChangeMessageInfo last = getLastMessage(r);
    addCustomKeyedValues(r, ImmutableMap.of("key1", "value1"));
    assertThatGet(r).containsExactly("key1", "value1");
    assertNoNewMessageSince(r, last);

    removeCustomKeys(r, ImmutableSet.of("key1"));
    assertThatGet(r).containsExactly();
    assertNoNewMessageSince(r, last);

    // Removing a single custom keyed value returns the other custom keyed values.
    addCustomKeyedValues(r, ImmutableMap.of("key1", "value1", "key2", "value2"));
    assertThatGet(r).containsExactly("key1", "value1", "key2", "value2");
    assertNoNewMessageSince(r, last);

    removeCustomKeys(r, ImmutableSet.of("key1"));
    assertThatGet(r).containsExactly("key2", "value2");
    assertNoNewMessageSince(r, last);
  }

  @Test
  public void removeMultipleCustomKeys() throws Exception {
    PushOneCommit.Result r = createChange();
    ChangeMessageInfo last = getLastMessage(r);
    addCustomKeyedValues(r, ImmutableMap.of("key1", "value1", "key2", "value2"));
    assertThatGet(r).containsExactly("key1", "value1", "key2", "value2");
    assertNoNewMessageSince(r, last);
    removeCustomKeys(r, ImmutableSet.of("key1", "key2"));
    assertThatGet(r).containsExactly();
    assertNoNewMessageSince(r, last);

    addCustomKeyedValues(r, ImmutableMap.of("key1", "value1", "key2", "value2", "key3", "value3"));
    assertThatGet(r).containsExactly("key1", "value1", "key2", "value2", "key3", "value3");
    assertNoNewMessageSince(r, last);
    removeCustomKeys(r, ImmutableSet.of("key1", "key2"));
    assertThatGet(r).containsExactly("key3", "value3");
    assertNoNewMessageSince(r, last);
  }

  @Test
  public void removeNotExistingCustomKey() throws Exception {
    PushOneCommit.Result r = createChange();
    ChangeMessageInfo last = getLastMessage(r);
    removeCustomKeys(r, ImmutableSet.of("key1"));
    assertThatGet(r).isEmpty();
    assertNoNewMessageSince(r, last);

    addCustomKeyedValues(r, ImmutableMap.of("key1", "value1"));
    assertThatGet(r).containsExactly("key1", "value1");
    assertNoNewMessageSince(r, last);
    removeCustomKeys(r, ImmutableSet.of("key2"));
    assertThatGet(r).containsExactly("key1", "value1");
    assertNoNewMessageSince(r, last);

    addCustomKeyedValues(r, ImmutableMap.of("key1", "value1", "key2", "value2", "key3", "value3"));
    assertThatGet(r).containsExactly("key1", "value1", "key2", "value2", "key3", "value3");
    assertNoNewMessageSince(r, last);
    removeCustomKeys(r, ImmutableSet.of("key4"));
    assertThatGet(r).containsExactly("key1", "value1", "key2", "value2", "key3", "value3");
    assertNoNewMessageSince(r, last);
  }

  @Test
  public void addAndRemove() throws Exception {
    PushOneCommit.Result r = createChange();
    ChangeMessageInfo last = getLastMessage(r);
    addCustomKeyedValues(r, ImmutableMap.of("key1", "value1", "key2", "value2"));
    assertThatGet(r).containsExactly("key1", "value1", "key2", "value2");
    assertNoNewMessageSince(r, last);

    // Adding and removing the same key updates it
    CustomKeyedValuesInput input = new CustomKeyedValuesInput();
    input.add = ImmutableMap.of("key1", "value3");
    input.remove = ImmutableSet.of("key1");
    change(r).setCustomKeyedValues(input);
    assertThatGet(r).containsExactly("key1", "value3", "key2", "value2");
    assertNoNewMessageSince(r, last);

    // Adding and removing same key with same value is a no-op.
    input = new CustomKeyedValuesInput();
    input.add = ImmutableMap.of("key1", "value3");
    input.remove = ImmutableSet.of("key1");
    change(r).setCustomKeyedValues(input);
    assertThatGet(r).containsExactly("key1", "value3", "key2", "value2");
    assertNoNewMessageSince(r, last);

    // Adding and removing separate keys should work as expected.
    input = new CustomKeyedValuesInput();
    input.add = ImmutableMap.of("key4", "value4");
    input.remove = ImmutableSet.of("key1");
    change(r).setCustomKeyedValues(input);
    assertThatGet(r).containsExactly("key4", "value4", "key2", "value2");
    assertNoNewMessageSince(r, last);
  }

  @Test
  public void addCustomKeyedValuesWithoutPermissionNotAllowed() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class, () -> addCustomKeyedValues(r, ImmutableMap.of("key1", "value1")));
    assertThat(thrown).hasMessageThat().contains("edit custom keyed values not permitted");
  }

  @Test
  public void addCustomKeyedValueKeyTooLongNotAllowed() throws Exception {
    PushOneCommit.Result r = createChange();
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                addCustomKeyedValues(
                    r, ImmutableMap.of("k".repeat(MAX_CUSTOM_KEY_LENGTH + 1), "value1")));
    assertThat(thrown).hasMessageThat().contains("Custom Key is too long.");
  }

  @Test
  public void addCustomKeyedValueValueTooLongNotAllowed() throws Exception {
    PushOneCommit.Result r = createChange();
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                addCustomKeyedValues(
                    r, ImmutableMap.of("key1", "v".repeat(MAX_CUSTOM_KEYED_VALUE_LENGTH + 1))));
    assertThat(thrown).hasMessageThat().contains("Custom Keyed value is too long.");
  }

  @Test
  public void addCustomKeyedValueTooManyKeyedValuesNotAllowed() throws Exception {
    PushOneCommit.Result r = createChange();
    ImmutableMap.Builder<String, String> input = ImmutableMap.builder();
    for (int i = 0; i <= MAX_CUSTOM_KEYED_VALUES; i++) {
      input.put("key" + i, "value" + i);
    }
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> addCustomKeyedValues(r, input.build()));
    assertThat(thrown).hasMessageThat().contains("Too many custom keyed values.");
  }

  private MapSubject assertThatGet(PushOneCommit.Result r) throws Exception {
    return assertThat(change(r).getCustomKeyedValues());
  }

  private void addCustomKeyedValues(PushOneCommit.Result r, ImmutableMap<String, String> toAdd)
      throws Exception {
    CustomKeyedValuesInput input = new CustomKeyedValuesInput();
    input.add = toAdd;
    change(r).setCustomKeyedValues(input);
  }

  private void removeCustomKeys(PushOneCommit.Result r, ImmutableSet<String> toRemove)
      throws Exception {
    CustomKeyedValuesInput input = new CustomKeyedValuesInput();
    input.remove = toRemove;
    change(r).setCustomKeyedValues(input);
  }

  private void assertNoNewMessageSince(PushOneCommit.Result r, ChangeMessageInfo expected)
      throws Exception {
    requireNonNull(expected);
    ChangeMessageInfo last = getLastMessage(r);
    assertThat(last.message).isEqualTo(expected.message);
    assertThat(last.id).isEqualTo(expected.id);
  }

  private ChangeMessageInfo getLastMessage(PushOneCommit.Result r) throws Exception {
    ChangeMessageInfo lastMessage = Iterables.getLast(change(r).get().messages, null);
    assertWithMessage(lastMessage.message).that(lastMessage).isNotNull();
    return lastMessage;
  }
}
