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

package com.google.gerrit.sshd.args4j;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class ProjectControlHandler extends OptionHandler<ProjectControl> {
  private final ProjectControl.Factory projectControlFactory;

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Inject
  public ProjectControlHandler(
      final ProjectControl.Factory projectControlFactory,
      @Assisted final CmdLineParser parser, @Assisted final OptionDef option,
      @Assisted final Setter setter) {
    super(parser, option, setter);
    this.projectControlFactory = projectControlFactory;
  }

  @Override
  public final int parseArguments(final Parameters params)
      throws CmdLineException {
    final String token = params.getParameter(0);
    String projectName = token;

    if (projectName.endsWith(".git")) {
      // Be nice and drop the trailing ".git" suffix, which we never keep
      // in our database, but clients might mistakenly provide anyway.
      //
      projectName = projectName.substring(0, projectName.length() - 4);
    }

    if (projectName.startsWith("/")) {
      // Be nice and drop the leading "/" if supplied by an absolute path.
      // We don't have a file system hierarchy, just a flat namespace in
      // the database's Project entities. We never encode these with a
      // leading '/' but users might accidentally include them in Git URLs.
      //
      projectName = projectName.substring(1);
    }

    final ProjectControl control;
    try {
      control =
          projectControlFactory.validateFor(new Project.NameKey(projectName));
    } catch (NoSuchProjectException e) {
      throw new CmdLineException(owner, "'" + token + "': not a Gerrit project");
    }

    setter.addValue(control);
    return 1;
  }

  @Override
  public final String getDefaultMetaVariable() {
    return "PROJECT";
  }
}
