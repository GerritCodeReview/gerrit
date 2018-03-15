// Copyright (C) 2018 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.simple.submit;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.googlesource.gerrit.plugins.simple.submit.rules.CommitMessageRegexRule;
import com.googlesource.gerrit.plugins.simple.submit.rules.NoUnresolvedCommentsRule;
import com.googlesource.gerrit.plugins.simple.submit.rules.RequireApprovalRule;
import java.util.Collections;

public class EasyPreSubmitModule extends AbstractModule {
  @Inject private PluginConfigFactory pluginConfigFactory;

  @Override
  protected void configure() {
    // Approved (+2) and verified by one user at least
    bind(ProjectConfigEntry.class)
        .annotatedWith(Exports.named(Constants.APPROVERS_LIST))
        .toInstance(
            new ProjectConfigEntry(
                "Approvers",
                "",
                Collections.singletonList(""),
                true,
                "Who can approve the changes?"));

    bind(ProjectConfigEntry.class)
        .annotatedWith(Exports.named(Constants.APPROVAL_REQUIRED))
        .toInstance(new ProjectConfigEntry("Require the change to be approved", false));

    // Author can approve/validate own
    bind(ProjectConfigEntry.class)
        .annotatedWith(Exports.named(Constants.REQUIRE_NON_AUTHOR_APPROVAL))
        .toInstance(
            new ProjectConfigEntry(
                "Require non-author approval?",
                true,
                "Can the change author approve its own change?"));

    DynamicSet.bind(binder(), SubmitRule.class).to(RequireApprovalRule.class).in(Scopes.SINGLETON);

    //
    //
    //
    // No unresolved comments
    bind(ProjectConfigEntry.class)
        .annotatedWith(Exports.named(Constants.BLOCK_IF_UNRESOLVED_COMMENTS))
        .toInstance(
            new ProjectConfigEntry("Block submission if comments are unresolved", true, ""));

    DynamicSet.bind(binder(), SubmitRule.class)
        .to(NoUnresolvedCommentsRule.class)
        .in(Scopes.SINGLETON);

    //
    //
    //
    // Regex on the commit message (first line / contains a bug ID…)
    bind(ProjectConfigEntry.class)
        .annotatedWith(Exports.named(Constants.COMMIT_MESSAGE_REGEX))
        .toInstance(
            new ProjectConfigEntry(
                "Commit message must match this regex (empty to disable)",
                "",
                false,
                "This option is intended for advanced users."));

    DynamicSet.bind(binder(), SubmitRule.class)
        .to(CommitMessageRegexRule.class)
        .in(Scopes.SINGLETON);

    //
    //
    //
    // Require CI approval
    bind(ProjectConfigEntry.class)
        .annotatedWith(Exports.named(Constants.CI_IS_MANDATORY))
        .toInstance(
            new ProjectConfigEntry(
                "Require CI approval", "", true, "Is the CI mandatory to submit the change?"));
  }

  public PluginConfig getConfig(ChangeData changeData) {
    try {
      return pluginConfigFactory.getFromProjectConfig(changeData.project(), Constants.PLUGIN_NAME);
    } catch (NoSuchProjectException e) {
      e.printStackTrace();
      return null;
    }
  }
}
