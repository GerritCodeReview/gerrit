// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.ListGroupsOption;
import com.google.gerrit.extensions.client.ListOption;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.QueryResult;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.InternalGroupDescription;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.group.GroupQueryBuilder;
import com.google.gerrit.server.query.group.GroupQueryProcessor;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.kohsuke.args4j.Option;

public class QueryGroups implements RestReadView<TopLevelResource> {
  private final GroupQueryBuilder queryBuilder;
  private final GroupQueryProcessor queryProcessor;
  private final GroupJson json;

  private String query;
  private int limit;
  private int start;
  private EnumSet<ListGroupsOption> options = EnumSet.noneOf(ListGroupsOption.class);

  // TODO(ekempin): --query in ListGroups is marked as deprecated, once it is
  // removed we want to rename --query2 to --query here.
  /** --query (-q) is already used by {@link ListGroups} */
  @Option(
      name = "--query2",
      aliases = {"-q2"},
      usage = "group query")
  public void setQuery(String query) {
    this.query = query;
  }

  @Option(
      name = "--limit",
      aliases = {"-n"},
      metaVar = "CNT",
      usage = "maximum number of groups to list")
  public void setLimit(int limit) {
    this.limit = limit;
  }

  @Option(
      name = "--start",
      aliases = {"-S"},
      metaVar = "CNT",
      usage = "number of groups to skip")
  public void setStart(int start) {
    this.start = start;
  }

  @Option(name = "-o", usage = "Output options per group")
  public void addOption(ListGroupsOption o) {
    options.add(o);
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  public void setOptionFlagsHex(String hex) {
    options.addAll(ListOption.fromBits(ListGroupsOption.class, Integer.parseInt(hex, 16)));
  }

  @Inject
  protected QueryGroups(
      GroupQueryBuilder queryBuilder, GroupQueryProcessor queryProcessor, GroupJson json) {
    this.queryBuilder = queryBuilder;
    this.queryProcessor = queryProcessor;
    this.json = json;
  }

  @Override
  public List<GroupInfo> apply(TopLevelResource resource)
      throws BadRequestException, MethodNotAllowedException, StorageException,
          PermissionBackendException {
    if (Strings.isNullOrEmpty(query)) {
      throw new BadRequestException("missing query field");
    }

    if (queryProcessor.isDisabled()) {
      throw new MethodNotAllowedException("query disabled");
    }

    if (start != 0) {
      queryProcessor.setStart(start);
    }

    if (limit != 0) {
      queryProcessor.setUserProvidedLimit(limit);
    }

    try {
      QueryResult<InternalGroup> result = queryProcessor.query(queryBuilder.parse(query));
      List<InternalGroup> groups = result.entities();

      ArrayList<GroupInfo> groupInfos = Lists.newArrayListWithCapacity(groups.size());
      json.addOptions(options);
      for (InternalGroup group : groups) {
        groupInfos.add(json.format(new InternalGroupDescription(group)));
      }
      if (!groupInfos.isEmpty() && result.more()) {
        groupInfos.get(groupInfos.size() - 1)._moreGroups = true;
      }
      return groupInfos;
    } catch (QueryParseException e) {
      throw new BadRequestException(e.getMessage());
    }
  }
}
