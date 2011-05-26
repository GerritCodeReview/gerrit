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
import com.google.gerrit.server.config.WildProjectName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.sshd.AdminCommand;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
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
  private ProjectCache projectCache;

  @Inject
  private MetaDataUpdate.User metaDataUpdateFactory;

  @Inject
  @WildProjectName
  private Project.NameKey wildProject;

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

  private void updateParents() throws UnloggedFailure, Failure {
    final StringBuilder err = new StringBuilder();
    List<ProjectControl> empty = new ArrayList<ProjectControl>(0);

    Set<Project.NameKey> setKeys = getKeySet(setParents);
    Set<Project.NameKey> rmKeys = getKeySet(rmParents);
    Set<Project.NameKey> addKeys = getKeySet(addParents);

    for (final ProjectControl cCtrl : children) {
      Project child = cCtrl.getProject();
      if (wildProject.equals(child.getNameKey())) {
        // Don't allow the wild card project to have a parent.
        //
        err.append("error: Cannot set parent of '" + child.getNameKey().get()
            + "'\n");
        continue;
      }

      Set<Project.NameKey> parents = child.getParents();

      if (clear) {
        parents.clear();
      }

      if (setParents.size() > 0) {
        parents.clear();
        parents.addAll(setKeys);
      }

      parents.removeAll(rmKeys);
      parents.addAll(addKeys);
      err.append(updateProjectParents(child.getNameKey(), parents));
    }

    if (err.length() > 0) {
      while (err.charAt(err.length() - 1) == '\n') {
        err.setLength(err.length() - 1);
      }
      throw new UnloggedFailure(1, err.toString());
    }
  }

  private Set<Project.NameKey> getKeySet(List<ProjectControl> controls) {
    Set<Project.NameKey> keys = new HashSet<Project.NameKey>(controls.size());
    for (final ProjectControl ctrl : controls) {
      keys.add(ctrl.getProject().getNameKey());
    }
    return keys;
  }

  public String updateProjectParents(Project.NameKey key,
      Set<Project.NameKey> parents) throws Failure {
    String name = key.get();
    final StringBuilder parentNames = new StringBuilder();

    for (Project.NameKey parent : parents) {
      String parentName = parent.get();
      if (projectCache.get(parent).getAncestors().contains(key)
          || key.equals(parent)) {
        // Try to avoid creating a cycle in the parent pointers.
        //
        return "error: Cycle exists between '" + name + "' and '"
            + parentName + "'\n";
      }
      parentNames.append(" ").append(parentName);
    }

    try {
      MetaDataUpdate md = metaDataUpdateFactory.create(key);
      try {
        ProjectConfig config = ProjectConfig.read(md);
        config.getProject().setParents(parents);
        md.setMessage("Inherit access from" + parentNames.toString() + "\n");
        if (!config.commit(md)) {
          return "error: Could not update project " + name + "\n";
        }
      } finally {
        md.close();
      }
    } catch (RepositoryNotFoundException notFound) {
      return "error: Project " + name + " not found\n";
    } catch (IOException e) {
      throw new Failure(1, "Cannot update project " + name, e);
    } catch (ConfigInvalidException e) {
      throw new Failure(1, "Cannot update project " + name, e);
    }

    return "";
  }
}
