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

package com.google.gerrit.extensions.api.changes;

import java.util.Map;

import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;

public interface RevisionApi {
  void delete() throws RestApiException;
  void review(ReviewInput in) throws RestApiException;

  /** {@code submit} with {@link SubmitInput#waitForMerge} set to true. */
  void submit() throws RestApiException;
  void submit(SubmitInput in) throws RestApiException;
  void publish() throws RestApiException;
  ChangeApi cherryPick(CherryPickInput in) throws RestApiException;
  ChangeApi rebase() throws RestApiException;
  boolean canRebase();

  Map<String, FileInfo> files();
  FileApi file(String filePath);

  /**
   * A default implementation which allows source compatibility
   * when adding new methods to the interface.
   **/
  public class NotImplemented implements RevisionApi {
    @Override
    public void delete() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void review(ReviewInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void submit() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void submit(SubmitInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void publish() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi cherryPick(CherryPickInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi rebase() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public boolean canRebase() {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, FileInfo> files() {
      throw new NotImplementedException();
    }

    @Override
    public FileApi file(String filePath) {
      throw new NotImplementedException();
    }
  }
}
