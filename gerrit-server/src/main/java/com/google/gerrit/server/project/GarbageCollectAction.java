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

import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.git.GarbageCollection;
import com.google.inject.Inject;

public class GarbageCollectAction extends GarbageCollect implements
    UiAction<ProjectResource> {
  @Inject
  GarbageCollectAction(GarbageCollection.Factory garbageCollectionFactory) {
    super(garbageCollectionFactory);
  }

  @Override
  public UiAction.Description getDescription(ProjectResource rsrc) {
    return new UiAction.Description()
    .setLabel("Run GC")
    .setTitle("Triggers the Git Garbage Collection for this project.");
  }
}
