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

package com.google.gerrit.server.project;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import org.eclipse.jgit.lib.Config;

import java.util.List;
import java.util.Set;

public class CommentLinkProvider implements Provider<List<CommentLinkInfo>> {
  private final Config cfg;

  @Inject
  CommentLinkProvider(@GerritServerConfig Config cfg) {
    this.cfg = cfg;
  }

  @Override
  public List<CommentLinkInfo> get() {
    Set<String> subsections = cfg.getSubsections(ProjectConfig.COMMENTLINK);
    List<CommentLinkInfo> cls =
        Lists.newArrayListWithCapacity(subsections.size());
    for (String name : subsections) {
      CommentLinkInfo cl = ProjectConfig.buildCommentLink(cfg, name, true);
      if (cl.isOverrideOnly()) {
        throw new ProvisionException(
            "commentlink " + name + " empty except for \"enabled\"");
      }
      cls.add(cl);
    }
    return ImmutableList.copyOf(cls);
  }
}
