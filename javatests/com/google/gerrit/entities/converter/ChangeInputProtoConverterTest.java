/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gerrit.entities.converter;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.MergeInput;
import com.google.gerrit.proto.Entities;
import com.google.inject.TypeLiteral;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Test;

public class ChangeInputProtoConverterTest {
  private final ChangeInputProtoConverter changeInputProtoConverter =
      ChangeInputProtoConverter.INSTANCE;
  private final MergeInputProtoConverter mergeInputProtoConverter =
      MergeInputProtoConverter.INSTANCE;
  private final AccountInputProtoConverter accountInputProtoConverter =
      AccountInputProtoConverter.INSTANCE;

  // Helper method that creates a MergeInput with all possible value.
  private MergeInput createMergeInput() {
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = "test-source";
    mergeInput.sourceBranch = "test-source-branch";
    mergeInput.strategy = "test-strategy";
    mergeInput.allowConflicts = true;
    return mergeInput;
  }

  // Helper method that creates a AccountInput with all possible value.
  private AccountInput createAccountInput() {
    AccountInput accountInput = new AccountInput();
    accountInput.username = "test-username";
    accountInput.displayName = "test-displayName";
    accountInput.name = "test-name";
    accountInput.email = "test-email";
    accountInput.sshKey = "test-ssh-key";
    accountInput.httpPassword = "test-http-password";
    accountInput.groups = ImmutableList.of("test-group");
    return accountInput;
  }

  // Helper method that creates a ChangeInput with all possible value.
  private ChangeInput createChangeInput() {
    ChangeInput changeInput = new ChangeInput("test-project", "test-branch", "test-subject");
    changeInput.topic = "test-topic";
    changeInput.status = ChangeStatus.NEW;
    changeInput.isPrivate = true;
    changeInput.workInProgress = true;
    changeInput.baseChange = "test-base-change";
    changeInput.baseCommit = "test-base-commit";
    changeInput.newBranch = true;

    Map<String, String> validationOptions = new HashMap<>();
    validationOptions.put("test-key", "test-value");
    changeInput.validationOptions = validationOptions;

    Map<String, String> customKeyedValues = new HashMap<>();
    customKeyedValues.put("test-key", "test-value");
    changeInput.customKeyedValues = customKeyedValues;

    changeInput.merge = createMergeInput();

    ApplyPatchInput applyPatchInput = new ApplyPatchInput();
    applyPatchInput.patch = "test-patch";
    changeInput.patch = applyPatchInput;

    changeInput.author = createAccountInput();

    changeInput.responseFormatOptions = new ArrayList<>();
    changeInput.responseFormatOptions.addAll(
        ImmutableList.of(ListChangesOption.LABELS, ListChangesOption.DETAILED_LABELS));

    changeInput.notify = NotifyHandling.OWNER;

    Map<RecipientType, NotifyInfo> notifyDetails = new HashMap<>();
    NotifyInfo notifyInfo = new NotifyInfo(ImmutableList.of("account1", "account2"));
    notifyDetails.put(RecipientType.TO, notifyInfo);
    changeInput.notifyDetails = notifyDetails;
    return changeInput;
  }

  private void assertAccountInputEquals(AccountInput expected, AccountInput actual) {
    assertThat(
            (expected == null && actual == null)
                || (Objects.equals(expected.username, actual.username)
                    && Objects.equals(expected.name, actual.name)
                    && Objects.equals(expected.displayName, actual.displayName)
                    && Objects.equals(expected.email, actual.email)
                    && Objects.equals(expected.sshKey, actual.sshKey)
                    && Objects.equals(expected.httpPassword, actual.httpPassword)
                    && Objects.equals(expected.groups, actual.groups)))
        .isTrue();
  }

  private void assertMergeInputEquals(MergeInput expected, MergeInput actual) {
    assertThat(
            (expected == null && actual == null)
                || (Objects.equals(expected.source, actual.source)
                    && Objects.equals(expected.sourceBranch, actual.sourceBranch)
                    && Objects.equals(expected.strategy, actual.strategy)
                    && expected.allowConflicts == actual.allowConflicts))
        .isTrue();
  }

  private void assertChangeInputEquals(ChangeInput expected, ChangeInput actual) {
    assertThat(
            Objects.equals(expected.project, actual.project)
                && Objects.equals(expected.branch, actual.branch)
                && Objects.equals(expected.subject, actual.subject)
                && Objects.equals(expected.topic, actual.topic)
                && Objects.equals(expected.status, actual.status)
                && Objects.equals(expected.isPrivate, actual.isPrivate)
                && Objects.equals(expected.workInProgress, actual.workInProgress)
                && Objects.equals(expected.baseChange, actual.baseChange)
                && Objects.equals(expected.baseCommit, actual.baseCommit)
                && Objects.equals(expected.newBranch, actual.newBranch)
                && Objects.equals(expected.validationOptions, actual.validationOptions)
                && Objects.equals(expected.customKeyedValues, actual.customKeyedValues)
                && Objects.equals(expected.responseFormatOptions, actual.responseFormatOptions)
                && Objects.equals(expected.notify, actual.notify)
                && Objects.equals(expected.notifyDetails, actual.notifyDetails))
        .isTrue();
    assertThat(
            (expected.patch == null && actual.patch == null)
                || Objects.equals(expected.patch.patch, actual.patch.patch))
        .isTrue();
    assertAccountInputEquals(expected.author, actual.author);
    assertMergeInputEquals(expected.merge, actual.merge);
  }

  @Test
  public void mandatoryValuesConvertedToProto() {
    ChangeInput changeInput = new ChangeInput("test-project", "test-branch", "test-subject");

    Entities.ChangeInput proto = changeInputProtoConverter.toProto(changeInput);

    Entities.ChangeInput expectedProto =
        Entities.ChangeInput.newBuilder()
            .setProject("test-project")
            .setBranch("test-branch")
            .setSubject("test-subject")
            .setNotify(Entities.NotifyHandling.ALL)
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProto() {

    Entities.ChangeInput proto = changeInputProtoConverter.toProto(createChangeInput());

    Entities.ChangeInput.Builder expectedProto =
        Entities.ChangeInput.newBuilder()
            .setProject("test-project")
            .setBranch("test-branch")
            .setSubject("test-subject")
            .setTopic("test-topic")
            .setStatus(Entities.ChangeStatus.NEW)
            .setBaseChange("test-base-change")
            .setBaseCommit("test-base-commit")
            .setNewBranch(true)
            .setIsPrivate(true)
            .setWorkInProgress(true)
            .setPatch(Entities.ApplyPatchInput.newBuilder().setPatch("test-patch").build());

    Map<String, String> validationOptions = new HashMap<>();
    validationOptions.put("test-key", "test-value");
    expectedProto.putAllValidationOptions(validationOptions);

    Map<String, String> customKeyedValues = new HashMap<>();
    customKeyedValues.put("test-key", "test-value");
    expectedProto.putAllCustomKeyedValues(customKeyedValues);

    expectedProto.setMerge(mergeInputProtoConverter.toProto(createMergeInput()));
    expectedProto.setAuthor(accountInputProtoConverter.toProto(createAccountInput()));

    expectedProto.addAllResponseFormatOptions(
        ImmutableList.of(
            Entities.ListChangesOption.LABELS, Entities.ListChangesOption.DETAILED_LABELS));
    expectedProto.setNotify(Entities.NotifyHandling.OWNER);
    Map<String, Entities.NotifyInfo> notifyDetailsProto = new HashMap<>();
    Entities.NotifyInfo.Builder notifyInfoBuilder =
        Entities.NotifyInfo.newBuilder().addAllAccounts(ImmutableList.of("account1", "account2"));
    notifyDetailsProto.put(RecipientType.TO.name(), notifyInfoBuilder.build());
    expectedProto.putAllNotifyDetails(notifyDetailsProto);

    assertThat(proto).isEqualTo(expectedProto.build());
  }

  @Test
  public void mandatoryValuesConvertedToProtoAndBackAgain() {
    ChangeInput changeInput = new ChangeInput("test-project", "test-branch", "test-subject");

    ChangeInput convertedChangeInput =
        changeInputProtoConverter.fromProto(changeInputProtoConverter.toProto(changeInput));

    assertChangeInputEquals(changeInput, convertedChangeInput);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    ChangeInput changeInput = createChangeInput();

    ChangeInput convertedChangeInput =
        changeInputProtoConverter.fromProto(changeInputProtoConverter.toProto(changeInput));

    assertChangeInputEquals(changeInput, convertedChangeInput);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void methodsExistAsExpected() {
    assertThatSerializedClass(ChangeInput.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("project", String.class)
                .put("branch", String.class)
                .put("subject", String.class)
                .put("topic", String.class)
                .put("status", ChangeStatus.class)
                .put("isPrivate", Boolean.class)
                .put("workInProgress", Boolean.class)
                .put("baseChange", String.class)
                .put("baseCommit", String.class)
                .put("newBranch", Boolean.class)
                .put("validationOptions", new TypeLiteral<Map<String, String>>() {}.getType())
                .put("customKeyedValues", new TypeLiteral<Map<String, String>>() {}.getType())
                .put("merge", MergeInput.class)
                .put("patch", ApplyPatchInput.class)
                .put("author", AccountInput.class)
                .put(
                    "responseFormatOptions",
                    new TypeLiteral<List<ListChangesOption>>() {}.getType())
                .put("notify", NotifyHandling.class)
                .put(
                    "notifyDetails", new TypeLiteral<Map<RecipientType, NotifyInfo>>() {}.getType())
                .build());
  }
}
