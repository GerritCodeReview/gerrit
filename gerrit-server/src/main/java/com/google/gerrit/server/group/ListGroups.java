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

import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.VisibleGroups;
import com.google.gerrit.server.group.GetGroup.GroupInfo;
import com.google.gerrit.server.ioutil.ColumnFormatter;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** List groups visible to the calling user. */
public class ListGroups implements RestReadView<TopLevelResource> {

  private final GroupCache groupCache;
  private final VisibleGroups.Factory visibleGroupsFactory;
  private final IdentifiedUser.GenericFactory userFactory;

  @Option(name = "--project", aliases = {"-p"},
      usage = "projects for which the groups should be listed")
  private final List<ProjectControl> projects = new ArrayList<ProjectControl>();

  @Option(name = "--visible-to-all", usage = "to list only groups that are visible to all registered users")
  private boolean visibleToAll;

  @Option(name = "--type", usage = "type of group")
  private AccountGroup.Type groupType;

  @Option(name = "--user", aliases = {"-u"},
      usage = "user for which the groups should be listed")
  private Account.Id user;

  @Option(name = "--verbose", aliases = {"-v"},
      usage = "verbose output format with tab-separated columns for the " +
          "group name, UUID, description, type, owner group name, " +
          "owner group UUID, and whether the group is visible to all")
  private boolean verboseOutput;

  @Option(name = "-m", metaVar = "MATCH", usage = "match group substring")
  private String matchSubstring;

  @Inject
  protected ListGroups(final GroupCache groupCache,
      final VisibleGroups.Factory visibleGroupsFactory,
      final IdentifiedUser.GenericFactory userFactory) {
    this.groupCache = groupCache;
    this.visibleGroupsFactory = visibleGroupsFactory;
    this.userFactory = userFactory;
  }

  public Account.Id getUser() {
    return user;
  }

  public List<ProjectControl> getProjects() {
    return projects;
  }

  @Override
  public Object apply(TopLevelResource resource) throws AuthException,
      BadRequestException, ResourceConflictException, Exception {
    return display(null);
  }

  public JsonElement display(OutputStream displayOutputStream)
      throws NoSuchGroupException {
    PrintWriter stdout = null;
    if (displayOutputStream != null) {
      try {
        stdout = new PrintWriter(new BufferedWriter(
            new OutputStreamWriter(displayOutputStream, "UTF-8")));
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException("JVM lacks UTF-8 encoding", e);
      }
    }

    try {
      final VisibleGroups visibleGroups = visibleGroupsFactory.create();
      visibleGroups.setOnlyVisibleToAll(visibleToAll);
      visibleGroups.setGroupType(groupType);
      visibleGroups.setMatch(matchSubstring);
      final List<AccountGroup> groupList;
      if (!projects.isEmpty()) {
        groupList = visibleGroups.get(projects);
      } else if (user != null) {
        groupList = visibleGroups.get(userFactory.create(user));
      } else {
        groupList = visibleGroups.get();
      }

      if (stdout == null) {
        final Map<String, GroupInfo> output = Maps.newTreeMap();
        for (AccountGroup g : groupList) {
          GroupInfo info = new GroupInfo(GroupDescriptions.forAccountGroup(g));
          output.put(Objects.firstNonNull(info.name, "Group " + info.id), info);
          info.name = null;
        }
        return OutputFormat.JSON.newGson().toJsonTree(output,
            new TypeToken<Map<String, GroupInfo>>() {}.getType());
      } else {
        final ColumnFormatter formatter = new ColumnFormatter(stdout, '\t');
        for (final AccountGroup g : groupList) {
          formatter.addColumn(g.getName());
          if (verboseOutput) {
            formatter.addColumn(g.getGroupUUID().get());
            formatter.addColumn(
                g.getDescription() != null ? g.getDescription() : "");
            formatter.addColumn(g.getType().toString());
            final AccountGroup owningGroup =
                groupCache.get(g.getOwnerGroupUUID());
            formatter.addColumn(
                owningGroup != null ? owningGroup.getName() : "n/a");
            formatter.addColumn(g.getOwnerGroupUUID().get());
            formatter.addColumn(Boolean.toString(g.isVisibleToAll()));
          }
          formatter.nextLine();
        }
        formatter.finish();
        return null;
      }
    } finally {
      if (stdout != null) {
        stdout.flush();
      }
    }
  }
}
