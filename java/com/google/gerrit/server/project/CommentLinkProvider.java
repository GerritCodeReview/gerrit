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
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.StoredCommentLinkInfo;
import com.google.gerrit.extensions.api.projects.CommentLinkInfo;
import com.google.gerrit.server.config.ConfigUpdatedEvent;
import com.google.gerrit.server.config.ConfigUpdatedEvent.ConfigUpdateEntry;
import com.google.gerrit.server.config.ConfigUpdatedEvent.UpdateResult;
import com.google.gerrit.server.config.GerritConfigListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

@Singleton
public class CommentLinkProvider implements Provider<List<CommentLinkInfo>>, GerritConfigListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private volatile List<CommentLinkInfo> commentLinks;

  @Inject
  CommentLinkProvider(@GerritServerConfig Config cfg) {
    this.commentLinks = parseConfig(cfg);
  }

  private List<CommentLinkInfo> parseConfig(Config cfg) {
    Set<String> subsections = cfg.getSubsections(ProjectConfig.COMMENTLINK);
    ImmutableList.Builder<CommentLinkInfo> cls =
        ImmutableList.builderWithExpectedSize(subsections.size());
    for (String name : subsections) {
      try {
        StoredCommentLinkInfo cl = ProjectConfig.buildCommentLink(cfg, name);
        if (cl.getOverrideOnly()) {
          logger.atWarning().log("commentlink %s empty except for \"enabled\"", name);
          continue;
        }
        cls.add(cl.toInfo());
      } catch (IllegalArgumentException e) {
        logger.atWarning().log("invalid commentlink: %s", e.getMessage());
      }
    }
    return cls.build();
  }

  @Override
  public List<CommentLinkInfo> get() {
    return commentLinks;
  }

  @Override
  public Multimap<UpdateResult, ConfigUpdateEntry> configUpdated(ConfigUpdatedEvent event) {
    if (event.isSectionUpdated(ProjectConfig.COMMENTLINK)) {
      commentLinks = parseConfig(event.getNewConfig());
      return event.accept(ProjectConfig.COMMENTLINK);
    }
    return ConfigUpdatedEvent.NO_UPDATES;
  }
}
