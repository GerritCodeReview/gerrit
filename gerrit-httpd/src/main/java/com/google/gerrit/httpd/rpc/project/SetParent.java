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
// limitations under the License.

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.common.errors.OperationNotExecutedException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.PerformSetParent;
import com.google.gerrit.server.project.PerformSetParentImpl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.List;

/** RPC to set project parent **/
public class SetParent extends Handler<VoidResult> {
  interface Factory {
    SetParent create(String parentName, List<Project.NameKey> childProjects);
  }

  private final String parentName;
  private final List<Project.NameKey> childProjects;

  @Inject
  private PerformSetParentImpl.Factory performSetParent;

  @Inject
  SetParent(final ProjectCache projectCache, @Assisted final String parentName,
      @Assisted List<Project.NameKey> childProjects) {
    this.parentName = parentName;
    this.childProjects = childProjects;
  }

  @Override
  public VoidResult call() throws OrmException, NoSuchProjectException,
      OperationNotExecutedException {
    final Project.NameKey parentNameKey = new Project.NameKey(parentName);

    final PerformSetParent perfSetParent =
        performSetParent.create(parentNameKey, childProjects);
    perfSetParent.setParent();

    return VoidResult.INSTANCE;
  }
}
