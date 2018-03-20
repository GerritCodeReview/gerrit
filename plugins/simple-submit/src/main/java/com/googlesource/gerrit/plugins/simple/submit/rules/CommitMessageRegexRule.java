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

package com.googlesource.gerrit.plugins.simple.submit.rules;

import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitRecord.Status;
import com.google.gerrit.common.data.SubmitRequirement;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.re2j.Pattern;
import com.googlesource.gerrit.plugins.simple.submit.Constants;
import com.googlesource.gerrit.plugins.simple.submit.EasyPreSubmitModule;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public class CommitMessageRegexRule implements SubmitRule {
  @Inject private EasyPreSubmitModule plugin;

  @Override
  public Collection<SubmitRecord> evaluate(ChangeData changeData, SubmitRuleOptions options) {
    PluginConfig config = plugin.getConfig(changeData);
    String commitMessageRegex = config.getString(Constants.COMMIT_MESSAGE_REGEX, "");
    System.out.println("Regex: " + commitMessageRegex);
    if (commitMessageRegex == null || commitMessageRegex.isEmpty()) {
      return Collections.emptyList();
    }

    try {
      String commitMessage = changeData.commitMessage();
      Pattern pattern = Pattern.compile(commitMessageRegex);
      if (pattern.matcher(commitMessage).find()) {
        return Collections.emptyList();
      }
    } catch (IOException | OrmException e) {
      e.printStackTrace();
    }

    SubmitRecord sr = new SubmitRecord();
    sr.status = Status.NOT_READY;
    SubmitRequirement req =
        new SubmitRequirement(
            "Commit message format",
            "The commit message does not follow the format imposed by this repository.",
            null);
    sr.requirements = Collections.singletonList(req);
    return Collections.singletonList(sr);
  }
}
