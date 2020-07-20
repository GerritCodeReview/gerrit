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
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.BranchOrderSection;
import com.google.gerrit.entities.ConfiguredMimeTypes;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.NotifyConfig;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.StoredCommentLinkInfo;
import com.google.gerrit.server.config.PluginConfig;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Cached representation of values parsed from {@link ProjectConfig}.
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
   * Returns the group reference for a {@link com.google.gerrit.entities.AccountGroup.UUID}, if the
   * group is used by at least one rule.
   */
  public Optional<GroupReference> getGroup(AccountGroup.UUID uuid) {
    return Optional.ofNullable(getGroups().get(uuid));
  }

  /**
   * Returns the group reference for matching the given {@code name}, if the group is used by at
   * least one rule.
   */
  public Optional<GroupReference> getGroupByName(@Nullable String name) {
    if (name == null) {
      return Optional.empty();
    }
    return getGroups().values().stream().filter(g -> name.equals(g.getName())).findAny();
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

  /**
   * Returns {@link SubscribeSection} keyed by the {@link
   * com.google.gerrit.entities.Project.NameKey} they reference.
   */
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

  /**
   * Returns the {@link Config} that got parsed from the {@code plugins} section of {@code
   * project.config}. The returned instance is a defensive copy of the cached value. Returns an
   * empty config in case we find no config for the given plugin name. This is useful when calling
   * {@code PluginConfig#withInheritance(ProjectState.Factory)}
   */
  public PluginConfig getPluginConfig(String pluginName) {
    if (getPluginConfigs().containsKey(pluginName)) {
      Config config = new Config();
      try {
        config.fromText(getPluginConfigs().get(pluginName));
      } catch (ConfigInvalidException e) {
        throw new IllegalStateException("invalid plugin config for " + pluginName, e);
      }
      return PluginConfig.create(pluginName, config, Optional.of(this));
    }
    return PluginConfig.create(pluginName, new Config(), Optional.of(this));
  }

  abstract ImmutableMap<String, String> getPluginConfigs();

  public static Builder builder() {
    return new AutoValue_CachedProjectConfig.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setProject(Project value);

    public abstract Builder setGroups(ImmutableMap<AccountGroup.UUID, GroupReference> value);

    public abstract Builder setAccountsSection(AccountsSection value);

    public abstract Builder setAccessSections(ImmutableMap<String, AccessSection> value);

    public abstract Builder setBranchOrderSection(Optional<BranchOrderSection> value);

    public abstract Builder setContributorAgreements(
        ImmutableMap<String, ContributorAgreement> value);

    public abstract Builder setNotifySections(ImmutableMap<String, NotifyConfig> value);

    public abstract Builder setLabelSections(ImmutableMap<String, LabelType> value);

    public abstract Builder setMimeTypes(ConfiguredMimeTypes value);

    public abstract Builder setSubscribeSections(
        ImmutableMap<Project.NameKey, SubscribeSection> value);

    public abstract Builder setCommentLinkSections(
        ImmutableMap<String, StoredCommentLinkInfo> value);

    public abstract Builder setRulesId(Optional<ObjectId> value);

    public abstract Builder setRevision(Optional<ObjectId> value);

    public abstract Builder setMaxObjectSizeLimit(long value);

    public abstract Builder setCheckReceivedObjects(boolean value);

    public abstract Builder setExtensionPanelSections(
        ImmutableMap<String, ImmutableList<String>> value);

    public Builder setExtensionPanelSections(Map<String, List<String>> value) {
      ImmutableMap.Builder<String, ImmutableList<String>> b = ImmutableMap.builder();
      value.entrySet().forEach(e -> b.put(e.getKey(), ImmutableList.copyOf(e.getValue())));
      return setExtensionPanelSections(b.build());
    }

    abstract ImmutableMap.Builder<String, String> pluginConfigsBuilder();

    public Builder addPluginConfig(String key, String value) {
      pluginConfigsBuilder().put(key, value);
      return this;
    }

    public abstract CachedProjectConfig build();
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
