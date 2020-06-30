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

package com.google.gerrit.server.project;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.BranchOrderSection;
import com.google.gerrit.server.git.NotifyConfig;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Cached representation of values parsed from {@link ProjectConfig}.
 *
 * <p>This class is immutable and thread-safe. // TODO(hiesel): Add JavaDoc for all members
 */
@AutoValue
public abstract class CachedProjectConfig {
  public abstract Project getProject();

  public abstract ImmutableMap<AccountGroup.UUID, GroupReference> getGroupList();

  /** @return set of all groups used by this configuration. */
  public ImmutableSet<AccountGroup.UUID> getAllGroupUUIDs() {
    return getGroupList().keySet();
  }

  /** @return the group reference, if the group is used by at least one rule. */
  public GroupReference getGroup(AccountGroup.UUID uuid) {
    return getGroupList().get(uuid);
  }

  public abstract AccountsSection getAccountsSection();

  public abstract ImmutableMap<String, AccessSection> getAccessSections();

  public AccessSection getAccessSection(String refName) {
    return getAccessSections().get(refName);
  }

  public ImmutableSet<String> getAccessSectionNames() {
    return ImmutableSet.copyOf(getAccessSections().keySet());
  }

  @Nullable
  public abstract BranchOrderSection getBranchOrderSection();

  public abstract ImmutableMap<String, ContributorAgreement> getContributorAgreements();

  public abstract ImmutableMap<String, NotifyConfig> getNotifySections();

  public Collection<NotifyConfig> getNotifyConfigs() {
    return getNotifySections().values();
  }

  public abstract ImmutableMap<String, LabelType> getLabelSections();

  public abstract ConfiguredMimeTypes getMimeTypes();

  public abstract ImmutableMap<Project.NameKey, SubscribeSection> getSubscribeSections();

  public abstract ImmutableMap<String, StoredCommentLinkInfo> getCommentLinkSections();

  @Nullable
  public abstract ObjectId getRulesId();

  // TODO(hiesel): This should not have to be nullable.
  @Nullable
  public abstract ObjectId getRevision();

  public abstract long getMaxObjectSizeLimit();

  public abstract boolean getCheckReceivedObjects();

  public abstract ImmutableSet<String> getSectionsWithUnknownPermissions();

  public abstract boolean getHasLegacyPermissions();

  public abstract ImmutableMap<String, ImmutableList<String>> getExtensionPanelSections();

  public static Builder builder() {
    return new AutoValue_CachedProjectConfig.Builder();
  }

  public ImmutableList<SubscribeSection> getSubscribeSections(BranchNameKey branch) {
    return ProjectConfigUtil.filterSubscribeSectionsByBranch(
        getSubscribeSections().values(), branch);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setProject(Project value);

    public abstract Builder setGroupList(ImmutableMap<AccountGroup.UUID, GroupReference> value);

    public abstract Builder setAccountsSection(AccountsSection value);

    public abstract Builder setAccessSections(ImmutableMap<String, AccessSection> value);

    public abstract Builder setBranchOrderSection(@Nullable BranchOrderSection value);

    public abstract Builder setContributorAgreements(
        ImmutableMap<String, ContributorAgreement> value);

    public abstract Builder setNotifySections(ImmutableMap<String, NotifyConfig> value);

    public abstract Builder setLabelSections(ImmutableMap<String, LabelType> value);

    public abstract Builder setMimeTypes(ConfiguredMimeTypes value);

    public abstract Builder setSubscribeSections(
        ImmutableMap<Project.NameKey, SubscribeSection> value);

    public abstract Builder setCommentLinkSections(
        ImmutableMap<String, StoredCommentLinkInfo> value);

    public abstract Builder setRulesId(@Nullable ObjectId value);

    public abstract Builder setRevision(@Nullable ObjectId value);

    public abstract Builder setMaxObjectSizeLimit(long value);

    public abstract Builder setCheckReceivedObjects(boolean value);

    public abstract Builder setSectionsWithUnknownPermissions(ImmutableSet<String> value);

    public abstract Builder setHasLegacyPermissions(boolean value);

    public abstract Builder setExtensionPanelSections(
        ImmutableMap<String, ImmutableList<String>> value);

    public Builder setExtensionPanelSections(Map<String, List<String>> value) {
      ImmutableMap.Builder<String, ImmutableList<String>> b = ImmutableMap.builder();
      value.entrySet().forEach(e -> b.put(e.getKey(), ImmutableList.copyOf(e.getValue())));
      return setExtensionPanelSections(b.build());
    }

    public abstract CachedProjectConfig build();
  }
}
