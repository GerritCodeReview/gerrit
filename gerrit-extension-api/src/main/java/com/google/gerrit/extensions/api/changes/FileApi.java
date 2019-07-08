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

package com.google.gerrit.extensions.api.changes;

import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;

public interface FileApi {
  BinaryResult content() throws RestApiException;

  /** Diff against the revision's parent version of the file. */
  DiffInfo diff() throws RestApiException;

  /** @param base revision id of the revision to be used as the diff base */
  DiffInfo diff(String base) throws RestApiException;

  /** @param parent 1-based parent number to diff against */
  DiffInfo diff(int parent) throws RestApiException;

  /**
   * Creates a request to retrieve the diff. On the returned request formatting options for the diff
   * can be set.
   */
  DiffRequest diffRequest() throws RestApiException;

  abstract class DiffRequest {
    private String base;
    private Integer context;
    private Boolean intraline;
    private Whitespace whitespace;

    public abstract DiffInfo get() throws RestApiException;

    public DiffRequest withBase(String base) {
      this.base = base;
      return this;
    }

    public DiffRequest withContext(int context) {
      this.context = context;
      return this;
    }

    public DiffRequest withIntraline(boolean intraline) {
      this.intraline = intraline;
      return this;
    }

    public DiffRequest withWhitespace(Whitespace whitespace) {
      this.whitespace = whitespace;
      return this;
    }

    public String getBase() {
      return base;
    }

    public Integer getContext() {
      return context;
    }

    public Boolean getIntraline() {
      return intraline;
    }

    public Whitespace getWhitespace() {
      return whitespace;
    }
  }

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements FileApi {
    @Override
    public BinaryResult content() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public DiffInfo diff() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public DiffInfo diff(String base) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public DiffInfo diff(int parent) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public DiffRequest diffRequest() throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
