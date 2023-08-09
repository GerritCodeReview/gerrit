// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.json.OutputFormat;
import org.kohsuke.args4j.Option;

/**
 * Base class for {@link ListProjects} implementations.
 *
 * <p>Defines the options that are supported by the list projects REST endpoint.
 */
public abstract class AbstractListProjects implements ListProjects {
  @Override
  @Option(name = "--format", usage = "(deprecated) output format")
  public abstract void setFormat(OutputFormat fmt);

  @Override
  @Option(
      name = "--show-branch",
      aliases = {"-b"},
      usage = "displays the sha of each project in the specified branch")
  public abstract void addShowBranch(String branch);

  @Override
  @Option(
      name = "--tree",
      aliases = {"-t"},
      usage =
          "displays project inheritance in a tree-like format\n"
              + "this option does not work together with the show-branch option")
  public abstract void setShowTree(boolean showTree);

  @Override
  @Option(name = "--type", usage = "type of project")
  public abstract void setFilterType(FilterType type);

  @Override
  @Option(
      name = "--description",
      aliases = {"-d"},
      usage = "include description of project in list")
  public abstract void setShowDescription(boolean showDescription);

  @Override
  @Option(name = "--all", usage = "display all projects that are accessible by the calling user")
  public abstract void setAll(boolean all);

  @Override
  @Option(
      name = "--state",
      aliases = {"-s"},
      usage = "filter by project state")
  public abstract void setState(com.google.gerrit.extensions.client.ProjectState state);

  @Override
  @Option(
      name = "--limit",
      aliases = {"-n"},
      metaVar = "CNT",
      usage = "maximum number of projects to list")
  public abstract void setLimit(int limit);

  @Override
  @Option(
      name = "--start",
      aliases = {"-S"},
      metaVar = "CNT",
      usage = "number of projects to skip")
  public abstract void setStart(int start);

  @Override
  @Option(
      name = "--prefix",
      aliases = {"-p"},
      metaVar = "PREFIX",
      usage = "match project prefix")
  public abstract void setMatchPrefix(String matchPrefix);

  @Override
  @Option(
      name = "--match",
      aliases = {"-m"},
      metaVar = "MATCH",
      usage = "match project substring")
  public abstract void setMatchSubstring(String matchSubstring);

  @Override
  @Option(name = "-r", metaVar = "REGEX", usage = "match project regex")
  public abstract void setMatchRegex(String matchRegex);

  @Override
  @Option(
      name = "--has-acl-for",
      metaVar = "GROUP",
      usage = "displays only projects on which access rights for this group are directly assigned")
  public abstract void setGroupUuid(AccountGroup.UUID groupUuid);

  @Override
  public Response<Object> apply(TopLevelResource resource) throws Exception {
    return Response.ok(apply());
  }
}
