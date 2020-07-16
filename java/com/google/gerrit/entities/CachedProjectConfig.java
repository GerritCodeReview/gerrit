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

package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Cached representation of values parsed from {@link
 * com.google.gerrit.server.project.ProjectConfig}.
 *
 * <p>This class is immutable and thread-safe.
 */
@AutoValue
public abstract class CachedProjectConfig {
  public abstract Project getProject();

  public abstract ImmutableMap<AccountGroup.UUID, GroupReference> getGroups();

  /** Returns a set of all groups used by this configuration. */
  public ImmutableSet<AccountGroup.UUID> getAllGroupUUIDs() {
    return getGroups().keySet();
  }

  /**
   * Returns the group reference for a {@link AccountGroup.UUID}, if the group is used by at least
   * one rule.
   */
  public Optional<GroupReference> getGroup(AccountGroup.UUID uuid) {
    return Optional.ofNullable(getGroups().get(uuid));
  }

  /** Returns the account section containing visibility information about accounts. */
  public abstract AccountsSection getAccountsSection();

  /** Returns a map of {@link AccessSection}s keyed by their name. */
  public abstract ImmutableMap<String, AccessSection> getAccessSections();

  /** Returns the {@link AccessSection} with to the given name. */
  public Optional<AccessSection> getAccessSection(String refName) {
    return Optional.ofNullable(getAccessSections().get(refName));
  }

  /** Returns all {@link AccessSection} names. */
  public ImmutableSet<String> getAccessSectionNames() {
    return ImmutableSet.copyOf(getAccessSections().keySet());
  }

  /**
   * Returns the {@link BranchOrderSection} containing the order in which branches should be shown.
   */
  public abstract Optional<BranchOrderSection> getBranchOrderSection();

  /** Returns the {@link ContributorAgreement}s keyed by their name. */
  public abstract ImmutableMap<String, ContributorAgreement> getContributorAgreements();

  /** Returns the {@link NotifyConfig}s keyed by their name. */
  public abstract ImmutableMap<String, NotifyConfig> getNotifySections();

  /** Returns the {@link LabelType}s keyed by their name. */
  public abstract ImmutableMap<String, LabelType> getLabelSections();

  /** Returns configured {@link ConfiguredMimeTypes}s. */
  public abstract ConfiguredMimeTypes getMimeTypes();

  /** Returns {@link SubscribeSection} keyed by the {@link Project.NameKey} they reference. */
  public abstract ImmutableMap<Project.NameKey, SubscribeSection> getSubscribeSections();

  /** Returns {@link StoredCommentLinkInfo} keyed by their name. */
  public abstract ImmutableMap<String, StoredCommentLinkInfo> getCommentLinkSections();

  /** Returns the blob ID of the {@code rules.pl} file, if present. */
  public abstract Optional<ObjectId> getRulesId();

  // TODO(hiesel): This should not have to be an Optional.
  /** Returns the SHA1 of the {@code refs/meta/config} branch. */
  public abstract Optional<ObjectId> getRevision();

  /** Returns the maximum allowed object size. */
  public abstract long getMaxObjectSizeLimit();

  /** Returns {@code true} if received objects should be checked for validity. */
  public abstract boolean getCheckReceivedObjects();

  /** Returns a list of panel sections keyed by title. */
  public abstract ImmutableMap<String, ImmutableList<String>> getExtensionPanelSections();

  public ImmutableList<SubscribeSection> getSubscribeSections(BranchNameKey branch) {
    return filterSubscribeSectionsByBranch(getSubscribeSections().values(), branch);
  }

  public static Builder builder() {
    return new AutoValue_CachedProjectConfig.Builder();
  }

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setProject(Project value);

    public abstract Builder setAccountsSection(AccountsSection value);

    public abstract Builder setBranchOrderSection(Optional<BranchOrderSection> value);

    public Builder addGroup(GroupReference groupReference) {
      groupsBuilder().put(groupReference.getUUID(), groupReference);
      return this;
    }

    public Builder addAccessSection(AccessSection accessSection) {
      accessSectionsBuilder().put(accessSection.getName(), accessSection);
      return this;
    }

    public Builder addContributorAgreement(ContributorAgreement contributorAgreement) {
      contributorAgreementsBuilder().put(contributorAgreement.getName(), contributorAgreement);
      return this;
    }

    public Builder addNotifySection(NotifyConfig notifyConfig) {
      notifySectionsBuilder().put(notifyConfig.getName(), notifyConfig);
      return this;
    }

    public Builder addLabelSection(LabelType labelType) {
      labelSectionsBuilder().put(labelType.getName(), labelType);
      return this;
    }

    public abstract Builder setMimeTypes(ConfiguredMimeTypes value);

    public Builder addSubscribeSection(SubscribeSection subscribeSection) {
      subscribeSectionsBuilder().put(subscribeSection.project(), subscribeSection);
      return this;
    }

    public Builder addCommentLinkSection(StoredCommentLinkInfo storedCommentLinkInfo) {
      commentLinkSectionsBuilder().put(storedCommentLinkInfo.getName(), storedCommentLinkInfo);
      return this;
    }

    public abstract Builder setRulesId(Optional<ObjectId> value);

    public abstract Builder setRevision(Optional<ObjectId> value);

    public abstract Builder setMaxObjectSizeLimit(long value);

    public abstract Builder setCheckReceivedObjects(boolean value);

    public abstract ImmutableMap.Builder<String, ImmutableList<String>>
        extensionPanelSectionsBuilder();

    public Builder setExtensionPanelSections(Map<String, List<String>> value) {
      value
          .entrySet()
          .forEach(
              e ->
                  extensionPanelSectionsBuilder()
                      .put(e.getKey(), ImmutableList.copyOf(e.getValue())));
      return this;
    }

    public abstract CachedProjectConfig build();

    protected abstract ImmutableMap.Builder<AccountGroup.UUID, GroupReference> groupsBuilder();

    protected abstract ImmutableMap.Builder<String, AccessSection> accessSectionsBuilder();

    protected abstract ImmutableMap.Builder<String, ContributorAgreement>
        contributorAgreementsBuilder();

    protected abstract ImmutableMap.Builder<String, NotifyConfig> notifySectionsBuilder();

    protected abstract ImmutableMap.Builder<String, LabelType> labelSectionsBuilder();

    protected abstract ImmutableMap.Builder<Project.NameKey, SubscribeSection>
        subscribeSectionsBuilder();

    protected abstract ImmutableMap.Builder<String, StoredCommentLinkInfo>
        commentLinkSectionsBuilder();
  }

  private static ImmutableList<SubscribeSection> filterSubscribeSectionsByBranch(
      Collection<SubscribeSection> allSubscribeSections, BranchNameKey branch) {
    ImmutableList.Builder<SubscribeSection> ret = ImmutableList.builder();
    for (SubscribeSection s : allSubscribeSections) {
      if (s.appliesTo(branch)) {
        ret.add(s);
      }
    }
    return ret.build();
  }
}
