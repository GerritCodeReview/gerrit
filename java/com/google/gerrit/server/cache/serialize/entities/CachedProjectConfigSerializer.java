// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.cache.serialize.entities;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.AccountsSection;
import com.google.gerrit.entities.CachedProjectConfig;
import com.google.gerrit.entities.ConfiguredMimeTypes;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import java.util.Optional;

/** Helper to (de)serialize values for caches. */
public class CachedProjectConfigSerializer {
  public static CachedProjectConfig deserialize(Cache.CachedProjectConfigProto proto) {
    CachedProjectConfig.Builder builder =
        CachedProjectConfig.builder()
            .setProject(ProjectSerializer.deserialize(proto.getProject()))
            .setMaxObjectSizeLimit(proto.getMaxObjectSizeLimit())
            .setCheckReceivedObjects(proto.getCheckReceivedObjects());
    if (proto.hasBranchOrderSection()) {
      builder.setBranchOrderSection(
          Optional.of(BranchOrderSectionSerializer.deserialize(proto.getBranchOrderSection())));
    }
    ImmutableList<ConfiguredMimeTypes.TypeMatcher> matchers =
        proto.getMimeTypesList().stream()
            .map(ConfiguredMimeTypeSerializer::deserialize)
            .collect(toImmutableList());
    builder.setMimeTypes(ConfiguredMimeTypes.create(matchers));
    if (!proto.getRulesId().isEmpty()) {
      builder.setRulesId(
          Optional.of(ObjectIdConverter.create().fromByteString(proto.getRulesId())));
    }
    if (!proto.getRevision().isEmpty()) {
      builder.setRevision(
          Optional.of(ObjectIdConverter.create().fromByteString(proto.getRevision())));
    }
    proto
        .getExtensionPanelsMap()
        .entrySet()
        .forEach(
            panelSection -> {
              builder
                  .extensionPanelSectionsBuilder()
                  .put(
                      panelSection.getKey(),
                      panelSection.getValue().getSectionList().stream().collect(toImmutableList()));
            });
    ImmutableList<PermissionRule> accounts =
        proto.getAccountsSectionList().stream()
            .map(PermissionRuleSerializer::deserialize)
            .collect(toImmutableList());
    builder.setAccountsSection(AccountsSection.create(accounts));

    proto.getGroupListList().stream()
        .map(GroupReferenceSerializer::deserialize)
        .forEach(builder::addGroup);
    proto.getAccessSectionsList().stream()
        .map(AccessSectionSerializer::deserialize)
        .forEach(builder::addAccessSection);
    proto.getContributorAgreementsList().stream()
        .map(ContributorAgreementSerializer::deserialize)
        .forEach(builder::addContributorAgreement);
    proto.getNotifyConfigsList().stream()
        .map(NotifyConfigSerializer::deserialize)
        .forEach(builder::addNotifySection);
    proto.getLabelSectionsList().stream()
        .map(LabelTypeSerializer::deserialize)
        .forEach(builder::addLabelSection);
    proto.getSubscribeSectionsList().stream()
        .map(SubscribeSectionSerializer::deserialize)
        .forEach(builder::addSubscribeSection);
    proto.getCommentLinksList().stream()
        .map(StoredCommentLinkInfoSerializer::deserialize)
        .forEach(builder::addCommentLinkSection);
    proto
        .getPluginConfigsMap()
        .entrySet()
        .forEach(e -> builder.addPluginConfig(e.getKey(), e.getValue()));
    proto
        .getProjectLevelConfigsMap()
        .entrySet()
        .forEach(e -> builder.addProjectLevelConfig(e.getKey(), e.getValue()));

    return builder.build();
  }

  public static Cache.CachedProjectConfigProto serialize(CachedProjectConfig autoValue) {
    Cache.CachedProjectConfigProto.Builder builder =
        Cache.CachedProjectConfigProto.newBuilder()
            .setProject(ProjectSerializer.serialize(autoValue.getProject()))
            .setMaxObjectSizeLimit(autoValue.getMaxObjectSizeLimit())
            .setCheckReceivedObjects(autoValue.getCheckReceivedObjects());

    if (autoValue.getBranchOrderSection().isPresent()) {
      builder.setBranchOrderSection(
          BranchOrderSectionSerializer.serialize(autoValue.getBranchOrderSection().get()));
    }
    autoValue.getMimeTypes().matchers().stream()
        .map(ConfiguredMimeTypeSerializer::serialize)
        .forEach(builder::addMimeTypes);

    if (autoValue.getRulesId().isPresent()) {
      builder.setRulesId(ObjectIdConverter.create().toByteString(autoValue.getRulesId().get()));
    }
    if (autoValue.getRevision().isPresent()) {
      builder.setRevision(ObjectIdConverter.create().toByteString(autoValue.getRevision().get()));
    }

    autoValue
        .getExtensionPanelSections()
        .entrySet()
        .forEach(
            panelSection -> {
              builder.putExtensionPanels(
                  panelSection.getKey(),
                  Cache.CachedProjectConfigProto.ExtensionPanelSectionProto.newBuilder()
                      .addAllSection(panelSection.getValue())
                      .build());
            });
    autoValue.getAccountsSection().getSameGroupVisibility().stream()
        .map(PermissionRuleSerializer::serialize)
        .forEach(builder::addAccountsSection);

    autoValue.getGroups().values().stream()
        .map(GroupReferenceSerializer::serialize)
        .forEach(builder::addGroupList);
    autoValue.getAccessSections().values().stream()
        .map(AccessSectionSerializer::serialize)
        .forEach(builder::addAccessSections);
    autoValue.getContributorAgreements().values().stream()
        .map(ContributorAgreementSerializer::serialize)
        .forEach(builder::addContributorAgreements);
    autoValue.getNotifySections().values().stream()
        .map(NotifyConfigSerializer::serialize)
        .forEach(builder::addNotifyConfigs);
    autoValue.getLabelSections().values().stream()
        .map(LabelTypeSerializer::serialize)
        .forEach(builder::addLabelSections);
    autoValue.getSubscribeSections().values().stream()
        .map(SubscribeSectionSerializer::serialize)
        .forEach(builder::addSubscribeSections);
    autoValue.getCommentLinkSections().values().stream()
        .map(StoredCommentLinkInfoSerializer::serialize)
        .forEach(builder::addCommentLinks);
    builder.putAllPluginConfigs(autoValue.getPluginConfigs());
    builder.putAllProjectLevelConfigs(autoValue.getProjectLevelConfigs());

    return builder.build();
  }

  private CachedProjectConfigSerializer() {}
}
