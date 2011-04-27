// Copyright (C) 2010 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.sshd.commands;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ProjectParent;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.ProjectUtil;
import com.google.gerrit.sshd.AdminCommand;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmDuplicateKeyException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@AdminCommand
final class AdminSetParents extends BaseCommand {
  @Option(name = "--clear", aliases = {"-c"}, usage = "clear all parent projects (except for wildcard)")
  private boolean clear;

  @Option(name = "--set", aliases = {"-s"}, metaVar = "NAME", usage = "set parent project(s)")
  private List<ProjectControl> setParents = new ArrayList<ProjectControl>();

  @Option(name = "--add", aliases = {"-a"}, metaVar = "NAME", usage = "add parent project(s)")
  private List<ProjectControl> addParents = new ArrayList<ProjectControl>();

  @Option(name = "--remove", aliases = {"-r"}, metaVar = "NAME", usage = "remove parent project(s)")
  private List<ProjectControl> rmParents = new ArrayList<ProjectControl>();

  @Argument(index = 0, required = true, multiValued = true, metaVar = "NAME", usage = "projects to modify")
  private List<ProjectControl> children = new ArrayList<ProjectControl>();

  @Inject
  private ReviewDb db;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private ProjectUtil util;

  @Inject
  private IdentifiedUser currentUser;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        updateParents();
      }
    });
  }

  private void updateParents() throws OrmException, UnloggedFailure {
    final StringBuilder err = new StringBuilder();
    List<ProjectControl> empty = new ArrayList<ProjectControl>(0);

    for (final ProjectControl cCtrl : children) {
      if (clear) {
        err.append(setParents(cCtrl, empty));
      }

      if (setParents.size() > 0) {
        err.append(setParents(cCtrl, setParents));
      }

      // Delete first since old parents might prevent new ones from being added
      for (final ProjectControl pCtrl : rmParents) {
        err.append(removeParent(cCtrl, pCtrl));
      }
      for (final ProjectControl pCtrl : addParents) {
        err.append(util.addParent(cCtrl, pCtrl));
      }
    }

    // Invalidate all projects in cache since inherited rights were changed.
    //
    projectCache.evictAll();

    if (err.length() > 0) {
      while (err.charAt(err.length() - 1) == '\n') {
        err.setLength(err.length() - 1);
      }
      throw new UnloggedFailure(1, err.toString());
    }
  }

  private String setParents(ProjectControl cCtrl, List<ProjectControl> pCtrl)
      throws OrmException {
    final StringBuilder err = new StringBuilder();
    final Project.NameKey cKey = cCtrl.getProject().getNameKey();

    Set<ProjectParent> remove = new HashSet(
      db.projectParents().byProject(cKey).toList());

    Set<ProjectControl> add = new HashSet(pCtrl.size());
    for (ProjectControl pc: pCtrl) {
      ProjectParent pp = new ProjectParent(cKey, pc.getProject().getNameKey());
      if (! remove.remove(pp)) {
        add.add(pc);
      }
    }

    // Delete first since old parents might prevent new ones from being added
    for (ProjectParent pp: remove) {
      err.append(removeParent(cCtrl,
        projectCache.get(pp.getParentKey()).controlFor(currentUser)));
    }

    for (ProjectControl pc: add) {
      err.append(util.addParent(cCtrl, pc));
    }
    return err.toString();
  }

  private String removeParent(ProjectControl cCtrl, ProjectControl pCtrl)
      throws OrmException {
    final Project.NameKey cKey = cCtrl.getProject().getNameKey();
    final Project.NameKey pKey = pCtrl.getProject().getNameKey();
    final ProjectParent.Key key = new ProjectParent.Key(cKey, pKey);

    final ProjectParent pp = db.projectParents().get(key);
    if (pp == null) {
      final String cname = cCtrl.getProject().getName();
      final String pname = pCtrl.getProject().getName();
      return "error: Project '" + cname + "' is not a child of '" + pname + "'\n";
    }

    db.projectParents().delete(Collections.singleton(pp));

    return "";
  }
}
