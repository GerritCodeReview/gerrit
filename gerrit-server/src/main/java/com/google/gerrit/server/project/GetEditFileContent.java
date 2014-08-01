// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.ChangeEditResource;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;

@Singleton
public class GetEditFileContent implements RestReadView<ChangeEditResource> {
  private final FileContentUtil fileContentUtil;
  private final ChangeEditUtil editUtil;

  @Inject
  GetEditFileContent(FileContentUtil fileContentUtil,
      ChangeEditUtil editUtil) {
    this.fileContentUtil = fileContentUtil;
    this.editUtil = editUtil;
  }

  @Override
  public BinaryResult apply(ChangeEditResource rsrc)
      throws ResourceNotFoundException, IOException,
      InvalidChangeOperationException {
    try {
      return fileContentUtil.getContent(
            rsrc.getChangeEdit().getChange().getProject(),
            rsrc.getChangeEdit().getRevision().get(),
            rsrc.getPath());
    } catch (ResourceNotFoundException rnfe) {
      PatchSet psBase = editUtil.getBasePatchSet(rsrc.getChangeEdit());
      return fileContentUtil.getContent(
          rsrc.getChangeEdit().getChange().getProject(),
          psBase.getRevision().get(),
          rsrc.getPath());
    }
  }
}
