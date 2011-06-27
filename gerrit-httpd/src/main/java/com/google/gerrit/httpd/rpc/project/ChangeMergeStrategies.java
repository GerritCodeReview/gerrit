// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.common.data.MergeStrategySection;
import com.google.gerrit.common.errors.InvalidNameException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.NoSuchRefException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmConcurrencyException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

public class ChangeMergeStrategies extends Handler<VoidResult> {
  interface Factory {
    ChangeMergeStrategies create(@Assisted Project.NameKey projectName,
        @Assisted ObjectId base,
        @Assisted List<MergeStrategySection> sectionList,
        @Nullable @Assisted String message);
  }

  private final ProjectControl.Factory projectControlFactory;
  private final ProjectCache projectCache;
  private final MetaDataUpdate.User metaDataUpdateFactory;

  private final Project.NameKey projectName;
  private final ObjectId base;
  private List<MergeStrategySection> sectionList;
  private String message;

  @Inject
  ChangeMergeStrategies(
      final ProjectMergeStrategiesFactory.Factory projectMergeStrategiesFactory,
      final ProjectControl.Factory projectControlFactory,
      final ProjectCache projectCache,
      final MetaDataUpdate.User metaDataUpdateFactory,

      @Assisted final Project.NameKey projectName,
      @Assisted final ObjectId base,
      @Assisted List<MergeStrategySection> sectionList,
      @Nullable @Assisted String message) {
    this.projectControlFactory = projectControlFactory;
    this.projectCache = projectCache;
    this.metaDataUpdateFactory = metaDataUpdateFactory;

    this.projectName = projectName;
    this.base = base;
    this.sectionList = sectionList;
    this.message = message;
  }

  @Override
  public VoidResult call() throws NoSuchRefException, NoSuchProjectException,
      IOException, ConfigInvalidException, OrmConcurrencyException,
      InvalidNameException {
    final ProjectControl projectControl =
        projectControlFactory.controlFor(projectName);

    final MetaDataUpdate md;
    try {
      md = metaDataUpdateFactory.create(projectName);
    } catch (RepositoryNotFoundException notFound) {
      throw new NoSuchProjectException(projectName);
    }

    try {
      final ProjectConfig config = ProjectConfig.read(md, base);
      Set<String> toDelete = scanSectionNames(config);

      for (MergeStrategySection section : distinctMergeSections(sectionList)) {
        final String name = section.getName();
        if (!projectControl.controlForRef(name).isOwner()) {
          throw new NoSuchRefException(section.getName());
        }

        RefControl.validateRefPattern(name);

        config.replace(section);
        toDelete.remove(section.getName());
      }

      for (String name : toDelete) {
        if (projectControl.controlForRef(name).isOwner()) {
          config.remove(config.getMergeStrategySection(name));
        }
      }

      if (message != null && !message.isEmpty()) {
        if (!message.endsWith("\n")) {
          message += "\n";
        }
        md.setMessage(message);
      } else {
        md.setMessage("Modify merge strategy\n");
      }

      if (config.commit(md)) {
        projectCache.evict(config.getProject());
        return VoidResult.INSTANCE;
      } else {
        throw new OrmConcurrencyException("Cannot update " + projectName);
      }
    } finally {
      md.close();
    }
  }

  private static Set<String> scanSectionNames(final ProjectConfig config) {
    final Set<String> names = new HashSet<String>();
    for (MergeStrategySection section : config.getMergeStrategySections()) {
      names.add(section.getName());
    }
    return names;
  }

  private static Set<MergeStrategySection> distinctMergeSections(
      final List<MergeStrategySection> src) {
    final Set<MergeStrategySection> mergeSections =
        new HashSet<MergeStrategySection>();
    for (MergeStrategySection section : src) {
      mergeSections.add(section);
    }
    return mergeSections;
  }
}
