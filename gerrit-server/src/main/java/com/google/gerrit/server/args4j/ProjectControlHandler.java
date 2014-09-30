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

package com.google.gerrit.server.args4j;

import com.google.gerrit.common.ProjectUtil;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ProjectControlHandler extends OptionHandler<ProjectControl> {
  private static final Logger log = LoggerFactory
      .getLogger(ProjectControlHandler.class);
  private final ProjectControl.GenericFactory projectControlFactory;
  private final Provider<CurrentUser> user;

  @Inject
  public ProjectControlHandler(
      final ProjectControl.GenericFactory projectControlFactory,
      Provider<CurrentUser> user,
      @Assisted final CmdLineParser parser, @Assisted final OptionDef option,
      @Assisted final Setter<ProjectControl> setter) {
    super(parser, option, setter);
    this.projectControlFactory = projectControlFactory;
    this.user = user;
  }

  @Override
  public final int parseArguments(final Parameters params)
      throws CmdLineException {
    String projectName = params.getParameter(0);

    while (projectName.endsWith("/")) {
      projectName = projectName.substring(0, projectName.length() - 1);
    }

    while (projectName.startsWith("/")) {
      // Be nice and drop the leading "/" if supplied by an absolute path.
      // We don't have a file system hierarchy, just a flat namespace in
      // the database's Project entities. We never encode these with a
      // leading '/' but users might accidentally include them in Git URLs.
      //
      projectName = projectName.substring(1);
    }

    String nameWithoutSuffix = ProjectUtil.stripGitSuffix(projectName);
    Project.NameKey nameKey = new Project.NameKey(nameWithoutSuffix);

    final ProjectControl control;
    try {
      control = projectControlFactory.validateFor(
          nameKey,
          ProjectControl.OWNER | ProjectControl.VISIBLE,
          user.get());
    } catch (NoSuchProjectException e) {
      throw new CmdLineException(owner, e.getMessage());
    } catch (IOException e) {
      log.warn("Cannot load project " + nameWithoutSuffix, e);
      throw new CmdLineException(
          owner,
          new NoSuchProjectException(nameKey).getMessage());
    }

    setter.addValue(control);
    return 1;
  }

  @Override
  public final String getDefaultMetaVariable() {
    return "PROJECT";
  }
}
