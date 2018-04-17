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

package com.google.gerrit.server.restapi.group;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.client.ListGroupsOption;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.InternalGroupDescription;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.project.ProjectAccessor;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.restapi.account.GetGroups;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.Option;

/** List groups visible to the calling user. */
public class ListGroups implements RestReadView<TopLevelResource> {
  private static final Comparator<GroupDescription.Internal> GROUP_COMPARATOR =
      Comparator.comparing(GroupDescription.Basic::getName);

  protected final GroupCache groupCache;

  private final List<ProjectAccessor> projects = new ArrayList<>();
  private final Set<AccountGroup.UUID> groupsToInspect = new HashSet<>();
  private final GroupControl.Factory groupControlFactory;
  private final GroupControl.GenericFactory genericGroupControlFactory;
  private final Provider<IdentifiedUser> identifiedUser;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ProjectAccessor.Factory projectAccessorFactory;
  private final GetGroups accountGetGroups;
  private final GroupJson json;
  private final GroupBackend groupBackend;
  private final Groups groups;
  private final GroupsCollection groupsCollection;

  private EnumSet<ListGroupsOption> options = EnumSet.noneOf(ListGroupsOption.class);
  private boolean visibleToAll;
  private Account.Id user;
  private boolean owned;
  private int limit;
  private int start;
  private String matchSubstring;
  private String matchRegex;
  private String suggest;
  private String ownedBy;

  @Option(
    name = "--project",
    aliases = {"-p"},
    usage = "projects for which the groups should be listed"
  )
  public void addProject(ProjectState project) {
    projects.add(projectAccessorFactory.create(project));
  }

  @Option(
    name = "--visible-to-all",
    usage = "to list only groups that are visible to all registered users"
  )
  public void setVisibleToAll(boolean visibleToAll) {
    this.visibleToAll = visibleToAll;
  }

  @Option(
    name = "--user",
    aliases = {"-u"},
    usage = "user for which the groups should be listed"
  )
  public void setUser(Account.Id user) {
    this.user = user;
  }

  @Option(
    name = "--owned",
    usage =
        "to list only groups that are owned by the"
            + " specified user or by the calling user if no user was specifed"
  )
  public void setOwned(boolean owned) {
    this.owned = owned;
  }

  /**
   * Add a group to inspect.
   *
   * @param uuid UUID of the group
   * @deprecated use {@link #addGroup(AccountGroup.UUID)}.
   */
  @Deprecated
  @Option(
    name = "--query",
    aliases = {"-q"},
    usage = "group to inspect (deprecated: use --group/-g instead)"
  )
  void addGroup_Deprecated(AccountGroup.UUID uuid) {
    addGroup(uuid);
  }

  @Option(
    name = "--group",
    aliases = {"-g"},
    usage = "group to inspect"
  )
  public void addGroup(AccountGroup.UUID uuid) {
    groupsToInspect.add(uuid);
  }

  @Option(
    name = "--limit",
    aliases = {"-n"},
    metaVar = "CNT",
    usage = "maximum number of groups to list"
  )
  public void setLimit(int limit) {
    this.limit = limit;
  }

  @Option(
    name = "--start",
    aliases = {"-S"},
    metaVar = "CNT",
    usage = "number of groups to skip"
  )
  public void setStart(int start) {
    this.start = start;
  }

  @Option(
    name = "--match",
    aliases = {"-m"},
    metaVar = "MATCH",
    usage = "match group substring"
  )
  public void setMatchSubstring(String matchSubstring) {
    this.matchSubstring = matchSubstring;
  }

  @Option(
    name = "--regex",
    aliases = {"-r"},
    metaVar = "REGEX",
    usage = "match group regex"
  )
  public void setMatchRegex(String matchRegex) {
    this.matchRegex = matchRegex;
  }

  @Option(
    name = "--suggest",
    aliases = {"-s"},
    usage = "to get a suggestion of groups"
  )
  public void setSuggest(String suggest) {
    this.suggest = suggest;
  }

  @Option(name = "-o", usage = "Output options per group")
  void addOption(ListGroupsOption o) {
    options.add(o);
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  void setOptionFlagsHex(String hex) {
    options.addAll(ListGroupsOption.fromBits(Integer.parseInt(hex, 16)));
  }

  @Option(name = "--owned-by", usage = "list groups owned by the given group uuid")
  public void setOwnedBy(String ownedBy) {
    this.ownedBy = ownedBy;
  }

  @Inject
  protected ListGroups(
      GroupCache groupCache,
      GroupControl.Factory groupControlFactory,
      GroupControl.GenericFactory genericGroupControlFactory,
      Provider<IdentifiedUser> identifiedUser,
      IdentifiedUser.GenericFactory userFactory,
      ProjectAccessor.Factory projectAccessorFactory,
      GetGroups accountGetGroups,
      GroupsCollection groupsCollection,
      GroupJson json,
      GroupBackend groupBackend,
      Groups groups) {
    this.groupCache = groupCache;
    this.groupControlFactory = groupControlFactory;
    this.genericGroupControlFactory = genericGroupControlFactory;
    this.identifiedUser = identifiedUser;
    this.userFactory = userFactory;
    this.projectAccessorFactory = projectAccessorFactory;
    this.accountGetGroups = accountGetGroups;
    this.json = json;
    this.groupBackend = groupBackend;
    this.groups = groups;
    this.groupsCollection = groupsCollection;
  }

  public void setOptions(EnumSet<ListGroupsOption> options) {
    this.options = options;
  }

  public Account.Id getUser() {
    return user;
  }

  public List<ProjectAccessor> getProjects() {
    return projects;
  }

  @Override
  public SortedMap<String, GroupInfo> apply(TopLevelResource resource)
      throws OrmException, RestApiException, IOException, ConfigInvalidException {
    SortedMap<String, GroupInfo> output = new TreeMap<>();
    for (GroupInfo info : get()) {
      output.put(MoreObjects.firstNonNull(info.name, "Group " + Url.decode(info.id)), info);
      info.name = null;
    }
    return output;
  }

  public List<GroupInfo> get()
      throws OrmException, RestApiException, IOException, ConfigInvalidException {
    if (!Strings.isNullOrEmpty(suggest)) {
      return suggestGroups();
    }

    if (!Strings.isNullOrEmpty(matchSubstring) && !Strings.isNullOrEmpty(matchRegex)) {
      throw new BadRequestException("Specify one of m/r");
    }

    if (ownedBy != null) {
      return getGroupsOwnedBy(ownedBy);
    }

    if (owned) {
      return getGroupsOwnedBy(user != null ? userFactory.create(user) : identifiedUser.get());
    }

    if (user != null) {
      return accountGetGroups.apply(new AccountResource(userFactory.create(user)));
    }

    return getAllGroups();
  }

  private List<GroupInfo> getAllGroups() throws OrmException, IOException, ConfigInvalidException {
    Pattern pattern = getRegexPattern();
    Stream<GroupDescription.Internal> existingGroups =
        getAllExistingGroups()
            .filter(group -> isRelevant(pattern, group))
            .map(this::loadGroup)
            .flatMap(Streams::stream)
            .filter(this::isVisible)
            .sorted(GROUP_COMPARATOR)
            .skip(start);
    if (limit > 0) {
      existingGroups = existingGroups.limit(limit);
    }
    List<GroupDescription.Internal> relevantGroups = existingGroups.collect(toImmutableList());
    List<GroupInfo> groupInfos = Lists.newArrayListWithCapacity(relevantGroups.size());
    for (GroupDescription.Internal group : relevantGroups) {
      groupInfos.add(json.addOptions(options).format(group));
    }
    return groupInfos;
  }

  private Stream<GroupReference> getAllExistingGroups() throws IOException, ConfigInvalidException {
    if (!projects.isEmpty()) {
      return projects
          .stream()
          .map(ProjectAccessor::getAllGroups)
          .flatMap(Collection::stream)
          .distinct();
    }
    return groups.getAllGroupReferences();
  }

  private List<GroupInfo> suggestGroups() throws OrmException, BadRequestException {
    if (conflictingSuggestParameters()) {
      throw new BadRequestException(
          "You should only have no more than one --project and -n with --suggest");
    }
    List<GroupReference> groupRefs =
        Lists.newArrayList(
            Iterables.limit(
                groupBackend.suggest(
                    suggest,
                    projects
                        .stream()
                        .findFirst()
                        .map(ProjectAccessor::getProjectState)
                        .orElse(null)),
                limit <= 0 ? 10 : Math.min(limit, 10)));

    List<GroupInfo> groupInfos = Lists.newArrayListWithCapacity(groupRefs.size());
    for (GroupReference ref : groupRefs) {
      GroupDescription.Basic desc = groupBackend.get(ref.getUUID());
      if (desc != null) {
        groupInfos.add(json.addOptions(options).format(desc));
      }
    }
    return groupInfos;
  }

  private boolean conflictingSuggestParameters() {
    if (Strings.isNullOrEmpty(suggest)) {
      return false;
    }
    if (projects.size() > 1) {
      return true;
    }
    if (visibleToAll) {
      return true;
    }
    if (user != null) {
      return true;
    }
    if (owned) {
      return true;
    }
    if (ownedBy != null) {
      return true;
    }
    if (start != 0) {
      return true;
    }
    if (!groupsToInspect.isEmpty()) {
      return true;
    }
    if (!Strings.isNullOrEmpty(matchSubstring)) {
      return true;
    }
    if (!Strings.isNullOrEmpty(matchRegex)) {
      return true;
    }
    return false;
  }

  private List<GroupInfo> filterGroupsOwnedBy(Predicate<GroupDescription.Internal> filter)
      throws OrmException, IOException, ConfigInvalidException {
    Pattern pattern = getRegexPattern();
    Stream<? extends GroupDescription.Internal> foundGroups =
        groups
            .getAllGroupReferences()
            .filter(group -> isRelevant(pattern, group))
            .map(this::loadGroup)
            .flatMap(Streams::stream)
            .filter(this::isVisible)
            .filter(filter)
            .sorted(GROUP_COMPARATOR)
            .skip(start);
    if (limit > 0) {
      foundGroups = foundGroups.limit(limit);
    }
    List<GroupDescription.Internal> ownedGroups = foundGroups.collect(toImmutableList());
    List<GroupInfo> groupInfos = new ArrayList<>(ownedGroups.size());
    for (GroupDescription.Internal group : ownedGroups) {
      groupInfos.add(json.addOptions(options).format(group));
    }
    return groupInfos;
  }

  private Optional<GroupDescription.Internal> loadGroup(GroupReference groupReference) {
    return groupCache.get(groupReference.getUUID()).map(InternalGroupDescription::new);
  }

  private List<GroupInfo> getGroupsOwnedBy(String id)
      throws OrmException, RestApiException, IOException, ConfigInvalidException {
    String uuid = groupsCollection.parse(id).getGroupUUID().get();
    return filterGroupsOwnedBy(group -> group.getOwnerGroupUUID().get().equals(uuid));
  }

  private List<GroupInfo> getGroupsOwnedBy(IdentifiedUser user)
      throws OrmException, IOException, ConfigInvalidException {
    return filterGroupsOwnedBy(group -> isOwner(user, group));
  }

  private boolean isOwner(CurrentUser user, GroupDescription.Internal group) {
    try {
      return genericGroupControlFactory.controlFor(user, group.getGroupUUID()).isOwner();
    } catch (NoSuchGroupException e) {
      return false;
    }
  }

  private Pattern getRegexPattern() {
    return Strings.isNullOrEmpty(matchRegex) ? null : Pattern.compile(matchRegex);
  }

  private boolean isRelevant(Pattern pattern, GroupReference group) {
    if (!Strings.isNullOrEmpty(matchSubstring)) {
      if (!group.getName().toLowerCase(Locale.US).contains(matchSubstring.toLowerCase(Locale.US))) {
        return false;
      }
    } else if (pattern != null) {
      if (!pattern.matcher(group.getName()).matches()) {
        return false;
      }
    }
    return groupsToInspect.isEmpty() || groupsToInspect.contains(group.getUUID());
  }

  private boolean isVisible(GroupDescription.Internal group) {
    if (visibleToAll && !group.isVisibleToAll()) {
      return false;
    }
    GroupControl c = groupControlFactory.controlFor(group);
    return c.isVisible();
  }
}
