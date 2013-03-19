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

import com.google.gerrit.common.data.GarbageCollectionResult;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.git.GarbageCollection;
import com.google.gerrit.server.project.GarbageCollect.Input;
import com.google.inject.Inject;

import java.util.Collections;
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
}
