// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.kohsuke.args4j.Option;

public class DownloadContent implements RestReadView<FileResource> {
  private final FileContentUtil fileContentUtil;

  @Option(name = "--parent")
  private Integer parent;

  @Inject
  DownloadContent(FileContentUtil fileContentUtil) {
    this.fileContentUtil = fileContentUtil;
  }

  @Override
  public BinaryResult apply(FileResource rsrc)
      throws ResourceNotFoundException, IOException, NoSuchChangeException, OrmException {
    String path = rsrc.getPatchKey().get();
    ProjectState projectState =
        rsrc.getRevision().getControl().getProjectControl().getProjectState();
    ObjectId revstr = ObjectId.fromString(rsrc.getRevision().getPatchSet().getRevision().get());
    return fileContentUtil.downloadContent(projectState, revstr, path, parent);
  }
}
