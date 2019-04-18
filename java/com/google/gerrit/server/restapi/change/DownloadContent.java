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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.change.FileResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.kohsuke.args4j.Option;

public class DownloadContent implements RestReadView<FileResource> {
  private final FileContentUtil fileContentUtil;
  private final ProjectCache projectCache;

  @Option(name = "--parent")
  private Integer parent;

  @Inject
  DownloadContent(FileContentUtil fileContentUtil, ProjectCache projectCache) {
    this.fileContentUtil = fileContentUtil;
    this.projectCache = projectCache;
  }

  @Override
  public BinaryResult apply(FileResource rsrc)
      throws ResourceNotFoundException, IOException, NoSuchChangeException {
    String path = rsrc.getPatchKey().get();
    RevisionResource rev = rsrc.getRevision();
    ObjectId revstr = ObjectId.fromString(rev.getPatchSet().getRevision().get());
    return fileContentUtil.downloadContent(
        projectCache.checkedGet(rev.getProject()), revstr, path, parent);
  }
}
