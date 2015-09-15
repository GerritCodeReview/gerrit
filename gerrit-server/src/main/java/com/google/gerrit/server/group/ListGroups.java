// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.group;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.client.ListGroupsOption;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.GetGroups;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupComparator;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/** List groups visible to the calling user. */
public class ListGroups implements RestReadView<TopLevelResource> {

  private final static String MESSAGE_ONE_PROJECT_REQUIRED =
      "You should have no more than one --project with --suggestion";

  protected final GroupCache groupCache;

  private final List<ProjectControl> projects = new ArrayList<>();
  private final Set<AccountGroup.UUID> groupsToInspect = Sets.newHashSet();
  private final GroupControl.Factory groupControlFactory;
  private final GroupControl.GenericFactory genericGroupControlFactory;
  private final Provider<IdentifiedUser> identifiedUser;
  private final IdentifiedUser.GenericFactory userFactory;
  private final Provider<GetGroups> accountGetGroups;
  private final GroupJson json;
  private final GroupBackend groupBackend;

  private EnumSet<ListGroupsOption> options =
      EnumSet.noneOf(ListGroupsOption.class);
  private boolean visibleToAll;
  private Account.Id user;
  private boolean owned;
  private int limit;
  private int start;
  private String matchSubstring;
  private String suggest;

  @Option(name = "--project", aliases = {"-p"},
      usage = "projects for which the groups should be listed")
  public void addProject(ProjectControl project) {
    projects.add(project);
  }

  @Option(name = "--visible-to-all",
      usage = "to list only groups that are visible to all registered users")
  public void setVisibleToAll(boolean visibleToAll) {
    this.visibleToAll = visibleToAll;
  }

  @Option(name = "--user", aliases = {"-u"},
      usage = "user for which the groups should be listed")
  public void setUser(Account.Id user) {
    this.user = user;
  }

  @Option(name = "--owned", usage = "to list only groups that are owned by the"
      + " specified user or by the calling user if no user was specifed")
  public void setOwned(boolean owned) {
    this.owned = owned;
  }

  @Option(name = "--suggest", usage = "to get a suggestion of groups")
  public void setSuggest(String suggest) {
    this.suggest = suggest;
  }

  @Option(name = "-q", usage = "group to inspect")
  public void addGroup(AccountGroup.UUID id) {
    groupsToInspect.add(id);
  }

  @Option(name = "--limit", aliases = {"-n"}, metaVar = "CNT",
      usage = "maximum number of groups to list")
  public void setLimit(int limit) {
    this.limit = limit;
  }

  @Option(name = "--start", aliases = {"-S"}, metaVar = "CNT",
      usage = "number of groups to skip")
  public void setStart(int start) {
    this.start = start;
  }

  @Option(name = "--match", aliases = {"-m"}, metaVar = "MATCH",
      usage = "match group substring")
  public void setMatchSubstring(String matchSubstring) {
    this.matchSubstring = matchSubstring;
  }

  @Option(name = "-o", usage = "Output options per group")
  void addOption(ListGroupsOption o) {
    options.add(o);
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  void setOptionFlagsHex(String hex) {
    options.addAll(ListGroupsOption.fromBits(Integer.parseInt(hex, 16)));
  }

  @Inject
  protected ListGroups(final GroupCache groupCache,
      final GroupControl.Factory groupControlFactory,
      final GroupControl.GenericFactory genericGroupControlFactory,
      final Provider<IdentifiedUser> identifiedUser,
      final IdentifiedUser.GenericFactory userFactory,
      final Provider<GetGroups> accountGetGroups, GroupJson json,
      GroupBackend groupBackend) {
    this.groupCache = groupCache;
    this.groupControlFactory = groupControlFactory;
    this.genericGroupControlFactory = genericGroupControlFactory;
    this.identifiedUser = identifiedUser;
    this.userFactory = userFactory;
    this.accountGetGroups = accountGetGroups;
    this.json = json;
    this.groupBackend = groupBackend;
  }

  public void setOptions(EnumSet<ListGroupsOption> options) {
    this.options = options;
  }

  public Account.Id getUser() {
    return user;
  }

  public List<ProjectControl> getProjects() {
    return projects;
  }

  @Override
  public SortedMap<String, GroupInfo> apply(TopLevelResource resource)
      throws OrmException {
    SortedMap<String, GroupInfo> output = Maps.newTreeMap();
    for (GroupInfo info : get()) {
      output.put(MoreObjects.firstNonNull(
          info.name,
          "Group " + Url.decode(info.id)), info);
      info.name = null;
    }
    return output;
  }

  public List<GroupInfo> get() throws OrmException {
    List<AccountGroup> groupList;

    if (!Strings.isNullOrEmpty(suggest)) {
      if (projects.size() > 1) {
        throw new OrmException(MESSAGE_ONE_PROJECT_REQUIRED);
      } else {
        List<GroupReference> groupsRefs = Lists.newArrayList(Iterables.limit(
            groupBackend.suggest(
                suggest, projects.size() > 0 ? projects.get(0) : null),
            limit <= 0 ? 10 : Math.min(limit, 10)));
        Map<AccountGroup.UUID, AccountGroup> groups = Maps.newHashMap();
        for (GroupReference groupRef : groupsRefs) {
          AccountGroup group = groupCache.get(groupRef.getUUID());
          if (group != null) {
            groups.put(group.getGroupUUID(), group);
          }
        }
        groupList = filterGroups(groups.values());
      }
    } else {
      if (user != null) {
        if (owned) {
          return getGroupsOwnedBy(userFactory.create(user));
        } else {
          return accountGetGroups.get().apply(
              new AccountResource(userFactory.create(user)));
        }
      } else {
        if (owned) {
          return getGroupsOwnedBy(identifiedUser.get());
        } else {
          if (!projects.isEmpty()) {
            Map<AccountGroup.UUID, AccountGroup> groups = Maps.newHashMap();
            for (final ProjectControl projectControl : projects) {
              final Set<GroupReference> groupsRefs = projectControl.getAllGroups();
              for (final GroupReference groupRef : groupsRefs) {
                final AccountGroup group = groupCache.get(groupRef.getUUID());
                if (group != null) {
                  groups.put(group.getGroupUUID(), group);
                }
              }
            }
            groupList = filterGroups(groups.values());
          } else {
            groupList = filterGroups(groupCache.all());
          }
        }
      }
    }

    List<GroupInfo> groupInfos = Lists.newArrayListWithCapacity(groupList.size());
    int found = 0;
    int foundIndex = 0;
    for (AccountGroup group : groupList) {
      if (foundIndex++ < start) {
        continue;
      }
      if (limit > 0 && ++found > limit) {
        break;
      }
      groupInfos.add(json.addOptions(options).format(
          GroupDescriptions.forAccountGroup(group)));
    }
    return groupInfos;
  }

  private List<GroupInfo> getGroupsOwnedBy(IdentifiedUser user)
      throws OrmException {
    List<GroupInfo> groups = Lists.newArrayList();
    int found = 0;
    int foundIndex = 0;
    for (AccountGroup g : filterGroups(groupCache.all())) {
      GroupControl ctl = groupControlFactory.controlFor(g);
      try {
        if (genericGroupControlFactory.controlFor(user, g.getGroupUUID())
            .isOwner()) {
          if (foundIndex++ < start) {
            continue;
          }
          if (limit > 0 && ++found > limit) {
            break;
          }
          groups.add(json.addOptions(options).format(ctl.getGroup()));
        }
      } catch (NoSuchGroupException e) {
        continue;
      }
    }
    return groups;
  }

  private List<AccountGroup> filterGroups(final Iterable<AccountGroup> groups) {
    final List<AccountGroup> filteredGroups = Lists.newArrayList();
    final boolean isAdmin =
        identifiedUser.get().getCapabilities().canAdministrateServer();
    for (final AccountGroup group : groups) {
      if (!Strings.isNullOrEmpty(matchSubstring)) {
        if (!group.getName().toLowerCase(Locale.US)
            .contains(matchSubstring.toLowerCase(Locale.US))) {
          continue;
        }
      }
      if (!isAdmin) {
        final GroupControl c = groupControlFactory.controlFor(group);
        if (!c.isVisible()) {
          continue;
        }
      }
      if (visibleToAll && !group.isVisibleToAll()) {
        continue;
      }
      if (!groupsToInspect.isEmpty()
          && !groupsToInspect.contains(group.getGroupUUID())) {
        continue;
      }
      filteredGroups.add(group);
    }
    Collections.sort(filteredGroups, new GroupComparator());
    return filteredGroups;
  }
}
