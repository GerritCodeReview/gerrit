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

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Cached representation of values parsed from {@link
 * com.google.gerrit.server.project.ProjectConfig}.
 *
 * <p>This class is immutable and thread-safe.
 *
 * @param accountsSection Returns the account section containing visibility information about
 *     accounts.
 * @param accessSections Returns a map of {@link AccessSection}s keyed by their name.
 * @param branchOrderSection Returns the {@link BranchOrderSection} containing the order in which
 *     branches should be shown.
 * @param contributorAgreements Returns the {@link ContributorAgreement}s keyed by their name.
 * @param notifySections Returns the {@link NotifyConfig}s keyed by their name.
 * @param labelSections Returns the {@link LabelType}s keyed by their name.
 * @param submitRequirementSections Returns the {@link SubmitRequirement}s keyed by their name.
 * @param mimeTypes Returns configured {@link ConfiguredMimeTypes}s.
 * @param subscribeSections Returns {@link SubscribeSection} keyed by the {@link Project.NameKey}
 *     they reference.
 * @param commentLinkSections Returns {@link StoredCommentLinkInfo} keyed by their name.
 * @param rulesId Returns the blob ID of the {@code rules.pl} file, if present.
 * @param revision Returns the SHA1 of the {@code refs/meta/config} branch.
 * @param maxObjectSizeLimit Returns the maximum allowed object size.
 * @param checkReceivedObjects Returns {@code true} if received objects should be checked for
 *     validity.
 * @param extensionPanelSections Returns a list of panel sections keyed by title.
 */
public record CachedProjectConfig(
    Project project,
    ImmutableMap<AccountGroup.UUID, GroupReference> groups,
    AccountsSection accountsSection,
    ImmutableSortedMap<String, AccessSection> accessSections,
    Optional<BranchOrderSection> branchOrderSection,
    ImmutableMap<String, ContributorAgreement> contributorAgreements,
    ImmutableMap<String, NotifyConfig> notifySections,
    ImmutableMap<String, LabelType> labelSections,
    ImmutableMap<String, SubmitRequirement> submitRequirementSections,
    ConfiguredMimeTypes mimeTypes,
    ImmutableMap<Project.NameKey, SubscribeSection> subscribeSections,
    ImmutableMap<String, StoredCommentLinkInfo> commentLinkSections,
    Optional<ObjectId> rulesId,
    Optional<ObjectId> revision,
    long maxObjectSizeLimit,
    boolean checkReceivedObjects,
    ImmutableMap<String, ImmutableList<String>> extensionPanelSections,
    ImmutableMap<String, String> pluginConfigs,
    ImmutableMap<String, String> projectLevelConfigs,
    ImmutableMap<String, ImmutableConfig> parsedProjectLevelConfigs) {
  public CachedProjectConfig {
    requireNonNull(project, "project");
    requireNonNull(groups, "groups");
    requireNonNull(accountsSection, "accountsSection");
    requireNonNull(accessSections, "accessSections");
    requireNonNull(branchOrderSection, "branchOrderSection");
    requireNonNull(contributorAgreements, "contributorAgreements");
    requireNonNull(notifySections, "notifySections");
    requireNonNull(labelSections, "labelSections");
    requireNonNull(submitRequirementSections, "submitRequirementSections");
    requireNonNull(mimeTypes, "mimeTypes");
    requireNonNull(subscribeSections, "subscribeSections");
    requireNonNull(commentLinkSections, "commentLinkSections");
    requireNonNull(rulesId, "rulesId");
    requireNonNull(revision, "revision");
    requireNonNull(extensionPanelSections, "extensionPanelSections");
    requireNonNull(pluginConfigs, "pluginConfigs");
    requireNonNull(projectLevelConfigs, "projectLevelConfigs");
    requireNonNull(parsedProjectLevelConfigs, "parsedProjectLevelConfigs");
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Returns the group reference for a {@link AccountGroup.UUID}, if the group is used by at least
   * one rule.
   */
  public Optional<GroupReference> getGroup(AccountGroup.UUID uuid) {
    return Optional.ofNullable(groups().get(uuid));
  }

  /**
   * Returns the group reference for matching the given {@code name}, if the group is used by at
   * least one rule.
   */
  public Optional<GroupReference> getGroupByName(@Nullable String name) {
    if (name == null) {
      return Optional.empty();
    }
    return groups().values().stream().filter(g -> name.equals(g.getName())).findAny();
  }

  /** Returns the {@link AccessSection} with to the given name. */
  public Optional<AccessSection> getAccessSection(String refName) {
    return Optional.ofNullable(accessSections().get(refName));
  }

  /** Returns all {@link AccessSection} names. */
  public ImmutableSet<String> getAccessSectionNames() {
    return ImmutableSet.copyOf(accessSections().keySet());
  }

  // TODO(hiesel): This should not have to be an Optional.

  public ImmutableList<SubscribeSection> getSubscribeSections(BranchNameKey branch) {
    return filterSubscribeSectionsByBranch(subscribeSections().values(), branch);
  }

  public static Builder builder() {
    return new AutoBuilder_CachedProjectConfig_Builder();
  }

  public Builder toBuilder() {
    return new AutoBuilder_CachedProjectConfig_Builder(this);
  }

  @AutoBuilder
  public abstract static class Builder {
    public abstract Builder setProject(Project value);

    public abstract Builder setAccountsSection(AccountsSection value);

    public abstract Builder setBranchOrderSection(Optional<BranchOrderSection> value);

    @CanIgnoreReturnValue
    public Builder addGroup(GroupReference groupReference) {
      AccountGroup.UUID groupUUID =
          Optional.ofNullable(groupReference.getUUID()).orElse(AccountGroup.UUID.EMPTY_UUID);
      groupsBuilder().put(groupUUID, GroupReference.create(groupUUID, groupReference.getName()));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addAccessSection(AccessSection accessSection) {
      accessSectionsBuilder().put(accessSection.getName(), accessSection);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addContributorAgreement(ContributorAgreement contributorAgreement) {
      contributorAgreementsBuilder().put(contributorAgreement.name(), contributorAgreement);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addNotifySection(NotifyConfig notifyConfig) {
      notifySectionsBuilder().put(notifyConfig.getName(), notifyConfig);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addLabelSection(LabelType labelType) {
      labelSectionsBuilder().put(labelType.name(), labelType);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addSubmitRequirementSection(SubmitRequirement submitRequirement) {
      submitRequirementSectionsBuilder().put(submitRequirement.name(), submitRequirement);
      return this;
    }

    public abstract Builder setMimeTypes(ConfiguredMimeTypes value);

    @CanIgnoreReturnValue
    public Builder addSubscribeSection(SubscribeSection subscribeSection) {
      subscribeSectionsBuilder().put(subscribeSection.project(), subscribeSection);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addCommentLinkSection(StoredCommentLinkInfo storedCommentLinkInfo) {
      commentLinkSectionsBuilder().put(storedCommentLinkInfo.name(), storedCommentLinkInfo);
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

    abstract ImmutableMap.Builder<String, String> pluginConfigsBuilder();

    @CanIgnoreReturnValue
    public Builder addPluginConfig(String pluginName, String pluginConfig) {
      pluginConfigsBuilder().put(pluginName, pluginConfig);
      return this;
    }

    abstract ImmutableMap.Builder<String, String> projectLevelConfigsBuilder();

    abstract ImmutableMap.Builder<String, ImmutableConfig> parsedProjectLevelConfigsBuilder();

    @CanIgnoreReturnValue
    public Builder addProjectLevelConfig(String configFileName, String config) {
      projectLevelConfigsBuilder().put(configFileName, config);
      try {
        parsedProjectLevelConfigsBuilder().put(configFileName, ImmutableConfig.parse(config));
      } catch (ConfigInvalidException e) {
        logger.atInfo().withCause(e).log("Config for %s not parsable", configFileName);
      }
      return this;
    }

    public abstract CachedProjectConfig build();

    protected abstract ImmutableMap.Builder<AccountGroup.UUID, GroupReference> groupsBuilder();

    protected abstract ImmutableSortedMap.Builder<String, AccessSection> accessSectionsBuilder();

    protected abstract ImmutableMap.Builder<String, ContributorAgreement>
        contributorAgreementsBuilder();

    protected abstract ImmutableMap.Builder<String, NotifyConfig> notifySectionsBuilder();

    protected abstract ImmutableMap.Builder<String, LabelType> labelSectionsBuilder();

    protected abstract ImmutableMap.Builder<String, SubmitRequirement>
        submitRequirementSectionsBuilder();

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
