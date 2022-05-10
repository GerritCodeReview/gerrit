// Copyright (C) 2012 The Android Open Source Project
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

import static com.google.gerrit.server.account.GroupBackends.GROUP_REF_NAME_COMPARATOR;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Counter2;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.StartupCheck;
import com.google.gerrit.server.StartupException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.plugincontext.PluginSetEntryContext;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

/**
 * Universal implementation of the GroupBackend that works with the injected set of GroupBackends.
 */
@Singleton
public class UniversalGroupBackend implements GroupBackend {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Field<String> SYSTEM_FIELD =
      Field.ofString("system", Metadata.Builder::groupSystem).build();

  private final PluginSetContext<GroupBackend> backends;
  private final Counter1<String> handlesCount;
  private final Counter1<String> getCount;
  private final Counter2<String, Integer> suggestCount;
  private final Counter2<String, Boolean> containsCount;
  private final Counter2<String, Boolean> containsAnyCount;
  private final Counter2<String, Integer> intersectionCount;
  private final Counter2<String, Integer> knownGroupsCount;

  @Inject
  UniversalGroupBackend(PluginSetContext<GroupBackend> backends, MetricMaker metricMaker) {
    this.backends = backends;
    this.handlesCount =
        metricMaker.newCounter(
            "group/handles_count", new Description("Calls to GroupBackend.handles"), SYSTEM_FIELD);
    this.getCount =
        metricMaker.newCounter(
            "group/get_count", new Description("Calls to GroupBackend.get"), SYSTEM_FIELD);
    this.suggestCount =
        metricMaker.newCounter(
            "group/suggest_count",
            new Description("Calls to GroupBackend.suggest"),
            SYSTEM_FIELD,
            Field.ofInteger("num_suggested", (meta, value) -> {}).build());
    this.containsCount =
        metricMaker.newCounter(
            "group/contains_count",
            new Description("Calls to GroupMemberships.contains"),
            SYSTEM_FIELD,
            Field.ofBoolean("contains", (meta, value) -> {}).build());
    this.containsAnyCount =
        metricMaker.newCounter(
            "group/contains_any_count",
            new Description("Calls to GroupMemberships.containsAnyOf"),
            SYSTEM_FIELD,
            Field.ofBoolean("contains_any_of", (meta, value) -> {}).build());
    this.intersectionCount =
        metricMaker.newCounter(
            "group/intersection_count",
            new Description("Calls to GroupMemberships.intersection"),
            SYSTEM_FIELD,
            Field.ofInteger("num_intersection", (meta, value) -> {}).build());
    this.knownGroupsCount =
        metricMaker.newCounter(
            "group/known_groups_count",
            new Description("Calls to GroupMemberships.getKnownGroups"),
            SYSTEM_FIELD,
            Field.ofInteger("num_known_groups", (meta, value) -> {}).build());
  }

  @Nullable
  private GroupBackend backend(AccountGroup.UUID uuid) {
    if (uuid != null) {
      for (PluginSetEntryContext<GroupBackend> c : backends) {
        if (Boolean.TRUE.equals(c.call(b -> b.handles(uuid)))) {
          return c.get();
        }
      }
    }
    return null;
  }

  @Override
  public boolean handles(AccountGroup.UUID uuid) {
    GroupBackend b = backend(uuid);
    if (b == null) {
      return false;
    }
    handlesCount.increment(name(b));
    return true;
  }

  @Override
  public GroupDescription.Basic get(AccountGroup.UUID uuid) {
    if (uuid == null) {
      return null;
    }
    GroupBackend b = backend(uuid);
    if (b == null) {
      logger.atFine().log("Unknown GroupBackend for UUID: %s", uuid);
      return null;
    }
    getCount.increment(name(b));
    return b.get(uuid);
  }

  @Override
  public Collection<GroupReference> suggest(String name, ProjectState project) {
    Set<GroupReference> groups = Sets.newTreeSet(GROUP_REF_NAME_COMPARATOR);
    backends.runEach(
        g -> {
          Collection<GroupReference> suggestions = g.suggest(name, project);
          suggestCount.increment(name(g), suggestions.size());
          groups.addAll(suggestions);
        });
    return groups;
  }

  @Override
  public GroupMembership membershipsOf(CurrentUser user) {
    return new UniversalGroupMembership(user);
  }

  private class UniversalGroupMembership implements GroupMembership {
    private final Map<GroupBackend, GroupMembership> memberships;

    private UniversalGroupMembership(CurrentUser user) {
      ImmutableMap.Builder<GroupBackend, GroupMembership> builder = ImmutableMap.builder();
      backends.runEach(g -> builder.put(g, g.membershipsOf(user)));
      this.memberships = builder.build();
    }

    @Nullable
    private Map.Entry<GroupBackend, GroupMembership> membership(AccountGroup.UUID uuid) {
      if (uuid != null) {
        for (Map.Entry<GroupBackend, GroupMembership> m : memberships.entrySet()) {
          if (m.getKey().handles(uuid)) {
            return m;
          }
        }
      }
      logger.atFine().log("Unknown GroupMembership for UUID: %s", uuid);
      return null;
    }

    @Override
    public boolean contains(AccountGroup.UUID uuid) {
      if (uuid == null) {
        return false;
      }
      Map.Entry<GroupBackend, GroupMembership> m = membership(uuid);
      if (m == null) {
        return false;
      }
      boolean contains = m.getValue().contains(uuid);
      containsCount.increment(name(m.getKey()), contains);
      return contains;
    }

    @Override
    public boolean containsAnyOf(Iterable<AccountGroup.UUID> uuids) {
      ListMultimap<Map.Entry<GroupBackend, GroupMembership>, AccountGroup.UUID> lookups =
          MultimapBuilder.hashKeys().arrayListValues().build();
      for (AccountGroup.UUID uuid : uuids) {
        if (uuid == null) {
          continue;
        }
        Map.Entry<GroupBackend, GroupMembership> m = membership(uuid);
        if (m == null) {
          continue;
        }
        lookups.put(m, uuid);
      }
      for (Map.Entry<GroupBackend, GroupMembership> groupBackends : lookups.asMap().keySet()) {

        GroupMembership m = groupBackends.getValue();
        Collection<AccountGroup.UUID> ids = lookups.asMap().get(groupBackends);
        if (ids.size() == 1) {
          if (m.contains(Iterables.getOnlyElement(ids))) {
            containsAnyCount.increment(name(groupBackends.getKey()), true);
            return true;
          }
        } else if (m.containsAnyOf(ids)) {
          containsAnyCount.increment(name(groupBackends.getKey()), true);
          return true;
        }
        // We would have returned if contains was true.
        containsAnyCount.increment(name(groupBackends.getKey()), false);
      }
      return false;
    }

    @Override
    public Set<AccountGroup.UUID> intersection(Iterable<AccountGroup.UUID> uuids) {
      ListMultimap<Map.Entry<GroupBackend, GroupMembership>, AccountGroup.UUID> lookups =
          MultimapBuilder.hashKeys().arrayListValues().build();
      for (AccountGroup.UUID uuid : uuids) {
        if (uuid == null) {
          continue;
        }
        Map.Entry<GroupBackend, GroupMembership> m = membership(uuid);
        if (m == null) {
          logger.atFine().log("Unknown GroupMembership for UUID: %s", uuid);
          continue;
        }
        lookups.put(m, uuid);
      }
      Set<AccountGroup.UUID> groups = new HashSet<>();
      for (Map.Entry<GroupBackend, GroupMembership> groupBackend : lookups.asMap().keySet()) {
        Set<AccountGroup.UUID> intersection =
            groupBackend.getValue().intersection(lookups.asMap().get(groupBackend));
        intersectionCount.increment(name(groupBackend.getKey()), intersection.size());
        groups.addAll(intersection);
      }
      return groups;
    }

    @Override
    public Set<AccountGroup.UUID> getKnownGroups() {
      Set<AccountGroup.UUID> groups = new HashSet<>();
      for (Map.Entry<GroupBackend, GroupMembership> entry : memberships.entrySet()) {
        Set<AccountGroup.UUID> knownGroups = entry.getValue().getKnownGroups();
        knownGroupsCount.increment(name(entry.getKey()), knownGroups.size());
        groups.addAll(knownGroups);
      }
      return groups;
    }
  }

  @Override
  public boolean isVisibleToAll(AccountGroup.UUID uuid) {
    for (PluginSetEntryContext<GroupBackend> c : backends) {
      if (Boolean.TRUE.equals(c.call(b -> b.handles(uuid)))) {
        return c.call(b -> b.isVisibleToAll(uuid));
      }
    }
    return false;
  }

  private static String name(GroupBackend backend) {
    if (backend == null) {
      return "none";
    }
    return backend.getClass().getSimpleName();
  }

  public static class ConfigCheck implements StartupCheck {
    private final Config cfg;
    private final UniversalGroupBackend universalGroupBackend;

    @Inject
    ConfigCheck(@GerritServerConfig Config cfg, UniversalGroupBackend groupBackend) {
      this.cfg = cfg;
      this.universalGroupBackend = groupBackend;
    }

    @Override
    public void check() throws StartupException {
      String invalid =
          cfg.getSubsections("groups").stream()
              .filter(
                  sub -> {
                    AccountGroup.UUID uuid = AccountGroup.uuid(sub);
                    GroupBackend groupBackend = universalGroupBackend.backend(uuid);
                    return groupBackend == null || groupBackend.get(uuid) == null;
                  })
              .map(u -> "'" + u + "'")
              .collect(joining(","));

      if (!invalid.isEmpty()) {
        throw new StartupException(
            String.format(
                "Subsections for 'groups' in gerrit.config must be valid group"
                    + " UUIDs. The following group UUIDs could not be resolved: "
                    + invalid
                    + " Please remove/fix these 'groups' subsections in"
                    + " gerrit.config."));
      }
    }
  }
}
