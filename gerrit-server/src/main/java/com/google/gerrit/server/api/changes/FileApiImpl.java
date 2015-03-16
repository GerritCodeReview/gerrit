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

package com.google.gerrit.server.api.changes;

import com.google.gerrit.extensions.api.changes.FileApi;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.FileResource;
import com.google.gerrit.server.change.GetContent;
import com.google.gerrit.server.change.GetDiff;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;

class FileApiImpl implements FileApi {
  interface Factory {
    FileApiImpl create(FileResource r);
  }

  private final GetContent getContent;
  private final Provider<GetDiff> getDiff;
  private final FileResource file;

  @Inject
  FileApiImpl(GetContent getContent,
      Provider<GetDiff> getDiff,
      @Assisted FileResource file) {
    this.getContent = getContent;
    this.getDiff = getDiff;
    this.file = file;
  }

  @Override
  public BinaryResult content() throws RestApiException {
    try {
      return getContent.apply(file);
    } catch (NoSuchChangeException | IOException | OrmException e) {
      throw new RestApiException("Cannot retrieve file content", e);
    }
  }

  @Override
  public DiffInfo diff() throws RestApiException {
    try {
      return getDiff.get().apply(file).value();
    } catch (IOException | InvalidChangeOperationException | OrmException e) {
      throw new RestApiException("Cannot retrieve diff", e);
    }
  }

  @Override
  public DiffInfo diff(String base) throws RestApiException {
    try {
      return getDiff.get().setBase(base).apply(file).value();
    } catch (IOException | InvalidChangeOperationException | OrmException e) {
      throw new RestApiException("Cannot retrieve diff", e);
    }
  }
}
