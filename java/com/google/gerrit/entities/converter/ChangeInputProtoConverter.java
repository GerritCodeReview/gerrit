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

import com.google.errorprone.annotations.Immutable;
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
import com.google.protobuf.Parser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Proto converter between {@link ChangeInput} and {@link
 * com.google.gerrit.proto.Entities.ChangeInput}.
 */
@Immutable
public enum ChangeInputProtoConverter implements ProtoConverter<Entities.ChangeInput, ChangeInput> {
  INSTANCE;

  private final ProtoConverter<Entities.MergeInput, MergeInput> mergeInputConverter =
      MergeInputProtoConverter.INSTANCE;
  private final ProtoConverter<Entities.ApplyPatchInput, ApplyPatchInput> applyPatchInputConverter =
      ApplyPatchInputProtoConverter.INSTANCE;
  private final ProtoConverter<Entities.AccountInput, AccountInput> accountInputConverter =
      AccountInputProtoConverter.INSTANCE;
  private final ProtoConverter<Entities.NotifyInfo, NotifyInfo> notifyInfoConverter =
      NotifyInfoProtoConverter.INSTANCE;

  @Override
  public Entities.ChangeInput toProto(ChangeInput changeInput) {
    Entities.ChangeInput.Builder builder = Entities.ChangeInput.newBuilder();
    if (changeInput.project != null) {
      builder.setProject(changeInput.project);
    }
    if (changeInput.branch != null) {
      builder.setBranch(changeInput.branch);
    }
    if (changeInput.subject != null) {
      builder.setSubject(changeInput.subject);
    }
    if (changeInput.topic != null) {
      builder.setTopic(changeInput.topic);
    }
    if (changeInput.status != null) {
      builder.setStatus(Entities.ChangeStatus.forNumber(changeInput.status.getValue()));
    }
    if (changeInput.isPrivate != null) {
      builder.setIsPrivate(changeInput.isPrivate);
    }
    if (changeInput.workInProgress != null) {
      builder.setWorkInProgress(changeInput.workInProgress);
    }
    if (changeInput.baseChange != null) {
      builder.setBaseChange(changeInput.baseChange);
    }
    if (changeInput.baseCommit != null) {
      builder.setBaseCommit(changeInput.baseCommit);
    }
    if (changeInput.newBranch != null) {
      builder.setNewBranch(changeInput.newBranch);
    }
    if (changeInput.validationOptions != null) {
      builder.putAllValidationOptions(changeInput.validationOptions);
    }
    if (changeInput.customKeyedValues != null) {
      builder.putAllCustomKeyedValues(changeInput.customKeyedValues);
    }
    if (changeInput.merge != null) {
      builder.setMerge(mergeInputConverter.toProto(changeInput.merge));
    }
    if (changeInput.patch != null) {
      builder.setPatch(applyPatchInputConverter.toProto(changeInput.patch));
    }
    if (changeInput.author != null) {
      builder.setAuthor(accountInputConverter.toProto(changeInput.author));
    }
    builder.setNotify(Entities.NotifyHandling.forNumber(changeInput.notify.getValue()));

    List<ListChangesOption> responseFormatOptions = changeInput.responseFormatOptions;
    if (responseFormatOptions != null) {
      for (ListChangesOption option : responseFormatOptions) {
        builder.addResponseFormatOptions(Entities.ListChangesOption.forNumber(option.getValue()));
      }
    }

    if (changeInput.notifyDetails != null) {
      Map<RecipientType, NotifyInfo> notifyDetails = changeInput.notifyDetails;
      for (Map.Entry<RecipientType, NotifyInfo> entry : notifyDetails.entrySet()) {
        Entities.RecipientType recipientType =
            Entities.RecipientType.forNumber(entry.getKey().getValue());
        builder.putNotifyDetails(
            recipientType.name(), notifyInfoConverter.toProto(entry.getValue()));
      }
    }
    return builder.build();
  }

  @Override
  public ChangeInput fromProto(Entities.ChangeInput proto) {
    ChangeInput changeInput =
        new ChangeInput(proto.getProject(), proto.getBranch(), proto.getSubject());
    if (proto.hasTopic()) {
      changeInput.topic = proto.getTopic();
    }
    if (proto.hasStatus()) {
      changeInput.status = ChangeStatus.valueOf(proto.getStatus().name());
    }
    if (proto.hasIsPrivate()) {
      changeInput.isPrivate = proto.getIsPrivate();
    }
    if (proto.hasWorkInProgress()) {
      changeInput.workInProgress = proto.getWorkInProgress();
    }
    if (proto.hasBaseChange()) {
      changeInput.baseChange = proto.getBaseChange();
    }
    if (proto.hasBaseCommit()) {
      changeInput.baseCommit = proto.getBaseCommit();
    }
    if (proto.hasNewBranch()) {
      changeInput.newBranch = proto.getNewBranch();
    }
    if (proto.getValidationOptionsCount() > 0) {
      changeInput.validationOptions = proto.getValidationOptionsMap();
    }
    if (proto.getCustomKeyedValuesCount() > 0) {
      changeInput.customKeyedValues = proto.getCustomKeyedValuesMap();
    }
    if (proto.hasMerge()) {
      changeInput.merge = mergeInputConverter.fromProto(proto.getMerge());
    }
    if (proto.hasPatch()) {
      changeInput.patch = applyPatchInputConverter.fromProto(proto.getPatch());
    }
    if (proto.hasAuthor()) {
      changeInput.author = accountInputConverter.fromProto(proto.getAuthor());
    }
    if (proto.getResponseFormatOptionsCount() > 0) {
      changeInput.responseFormatOptions = new ArrayList<>();
      for (Entities.ListChangesOption option : proto.getResponseFormatOptionsList()) {
        changeInput.responseFormatOptions.add(ListChangesOption.valueOf(option.name()));
      }
    }

    changeInput.notify = NotifyHandling.valueOf(proto.getNotify().name());

    if (proto.getNotifyDetailsCount() > 0) {
      changeInput.notifyDetails = new HashMap<>();
      for (Map.Entry<String, Entities.NotifyInfo> entry : proto.getNotifyDetailsMap().entrySet()) {
        changeInput.notifyDetails.put(
            RecipientType.valueOf(entry.getKey()), notifyInfoConverter.fromProto(entry.getValue()));
      }
    }

    return changeInput;
  }

  @Override
  public Parser<Entities.ChangeInput> getParser() {
    return Entities.ChangeInput.parser();
  }
}
