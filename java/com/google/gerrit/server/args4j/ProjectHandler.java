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

import static com.google.gerrit.util.cli.Localizable.localizable;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.ProjectUtil;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Optional;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class ProjectHandler extends OptionHandler<ProjectState> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ProjectCache projectCache;
  private final PermissionBackend permissionBackend;

  @Inject
  public ProjectHandler(
      ProjectCache projectCache,
      PermissionBackend permissionBackend,
      @Assisted final CmdLineParser parser,
      @Assisted final OptionDef option,
      @Assisted final Setter<ProjectState> setter) {
    super(parser, option, setter);
    this.projectCache = projectCache;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public final int parseArguments(Parameters params) throws CmdLineException {
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
    Project.NameKey nameKey = Project.nameKey(nameWithoutSuffix);

    Optional<ProjectState> state;
    try {
      state = projectCache.get(nameKey);
      if (!state.isPresent()) {
        throw new CmdLineException(owner, localizable("project %s not found"), nameWithoutSuffix);
      }
      // Hidden projects(permitsRead = false) should only be accessible by the project owners.
      // READ_CONFIG is checked here because it's only allowed to project owners(ACCESS may also
      // be allowed for other users). Allowing project owners to access here will help them to view
      // and update the config of hidden projects easily.
      ProjectPermission permissionToCheck =
          state.get().statePermitsRead() ? ProjectPermission.ACCESS : ProjectPermission.READ_CONFIG;
      permissionBackend.currentUser().project(nameKey).check(permissionToCheck);
    } catch (AuthException e) {
      throw new CmdLineException(
          owner, localizable(new NoSuchProjectException(nameKey, e).getMessage()));
    } catch (PermissionBackendException e) {
      logger.atWarning().withCause(e).log("Cannot load project %s", nameWithoutSuffix);
      throw new CmdLineException(
          owner, localizable(new NoSuchProjectException(nameKey).getMessage()));
    }

    setter.addValue(state.get());
    return 1;
  }

  @Override
  public final String getDefaultMetaVariable() {
    return "PROJECT";
  }
}
