// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.GarbageCollectionResult;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GarbageCollection;
import com.google.gerrit.server.project.GarbageCollect.Input;
import com.google.gerrit.server.project.ProjectJson.ProjectInfo;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@RequiresCapability(GlobalCapability.RUN_GC)
public class GarbageCollect implements RestModifyView<ProjectResource, Input> {
  public static class Input {
  }

  private GarbageCollection.Factory garbageCollectionFactory;

  @Inject
  GarbageCollect(GarbageCollection.Factory garbageCollectionFactory) {
    this.garbageCollectionFactory = garbageCollectionFactory;
  }

  @Override
  public Properties apply(ProjectResource rsrc, Input input)
      throws ResourceNotFoundException, ResourceConflictException {
    final GarbageCollectionResult result =
        garbageCollectionFactory.create().run(
            Collections.singletonList(rsrc.getNameKey()));
    if (result.hasErrors()) {
      GarbageCollectionResult.Error e = result.getErrors().get(0);
      switch (e.getType()) {
        case REPOSITORY_NOT_FOUND:
          throw new ResourceNotFoundException(rsrc.getName());
        case GC_ALREADY_SCHEDULED:
          throw new ResourceConflictException("garbage collection was already scheduled");
        case GC_FAILED:
        default:
          throw new ResourceConflictException("gc failed");
      }
    }
    return result.getStatistics().get(rsrc.getName());
  }

  public static class Recursive implements RestModifyView<ProjectResource, Input> {
    public static class Output {
      Map<String, Properties> statistics;
      Map<String, String> errors;
    }

    private final GarbageCollection.Factory garbageCollectionFactory;
    private final Provider<ListChildProjects> childProjects;
    private boolean dryRun;

    @Inject
    public Recursive(GarbageCollection.Factory garbageCollectionFactory,
        Provider<ListChildProjects> childProjects) {
      this.garbageCollectionFactory = garbageCollectionFactory;
      this.childProjects = childProjects;
    }

    public void setDryRun(boolean dryRun) {
      this.dryRun = dryRun;
    }

    @Override
    public Output apply(ProjectResource rsrc, Input input) {
      List<Project.NameKey> projectsToGc = Lists.newArrayList();
      ListChildProjects listChildProjects = childProjects.get();
      listChildProjects.setRecursive(true);
      projectsToGc.addAll(Lists.transform(listChildProjects.apply(rsrc),
          new Function<ProjectInfo, Project.NameKey>() {
            @Override
            public Project.NameKey apply(ProjectInfo info) {
              return new Project.NameKey(info.name);
            }
          }));
      projectsToGc.add(rsrc.getNameKey());

      GarbageCollectionResult result;
      if (dryRun) {
        result = garbageCollectionFactory.create().dryRun(projectsToGc);
      } else {
        result = garbageCollectionFactory.create().run(projectsToGc);
      }

      Output out = new Output();
      out.statistics = result.getStatistics();
      if (result.hasErrors()) {
        out.errors = Maps.newHashMapWithExpectedSize(result.getErrors().size());
        for (GarbageCollectionResult.Error e : result.getErrors()) {
          out.errors.put(e.getProjectName().get(), getErrorMessage(e));
        }
      }
      return out;
    }

    private String getErrorMessage(GarbageCollectionResult.Error e) {
      switch (e.getType()) {
        case REPOSITORY_NOT_FOUND:
          return "not found";
        case GC_ALREADY_SCHEDULED:
          return "gc was already scheduled";
        case GC_FAILED:
          return "gc failed";
        default:
          return "gc failed: " + e.getType();
      }
    }
  }
}
