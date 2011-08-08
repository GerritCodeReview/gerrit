// Copyright (c) 2012, Code Aurora Forum. All rights reserved.

// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
//       copyright notice, this list of conditions and the following
//       disclaimer in the documentation and/or other materials provided
//       with the distribution.
//     * Neither the name of Code Aurora Forum, Inc. nor the names of its
//       contributors may be used to endorse or promote products derived
//       from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
// WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
// ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
// BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
// BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
// WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
// OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
// IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.google.gerrit.httpd.rpc.project;


import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class ProjectAncestors extends Handler<List<Project.NameKey>> {
  interface Factory {
    ProjectAncestors create(@Assisted Project.NameKey projectName);
  }

  private final ProjectControl.Factory projectControlFactory;
  private final Project.NameKey projectName;

  @Inject
  ProjectAncestors(final ProjectControl.Factory projectControlFactory,
      @Assisted final Project.NameKey projectName) {
    this.projectControlFactory = projectControlFactory;

    this.projectName = projectName;
  }

  @Override
  public ArrayList<Project.NameKey> call() throws NoSuchProjectException {
    LinkedHashSet ancestors = new LinkedHashSet();
    final ProjectControl pc =
        projectControlFactory.validateFor(projectName, ProjectControl.OWNER
            | ProjectControl.VISIBLE);
    ProjectState projectState = pc.getProjectState().getParentState();
    Project.NameKey prj;
    while (projectState != null) {
      prj = projectState.getProject().getNameKey();
      if (!ancestors.add(prj)) {
        break;
      }
      projectState = projectState.getParentState();
    }
    return new ArrayList<Project.NameKey>(ancestors);
  }
}
