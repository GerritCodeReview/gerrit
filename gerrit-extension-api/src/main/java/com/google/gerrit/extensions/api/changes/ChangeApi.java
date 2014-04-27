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

import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ListChangesOption;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;

import java.util.EnumSet;

public interface ChangeApi {
  String id();

  RevisionApi current() throws RestApiException;
  RevisionApi revision(int id) throws RestApiException;
  RevisionApi revision(String id) throws RestApiException;

  void abandon() throws RestApiException;
  void abandon(AbandonInput in) throws RestApiException;

  void restore() throws RestApiException;
  void restore(RestoreInput in) throws RestApiException;

  ChangeApi revert() throws RestApiException;
  ChangeApi revert(RevertInput in) throws RestApiException;

  void addReviewer(AddReviewerInput in) throws RestApiException;
  void addReviewer(String in) throws RestApiException;

  ChangeInfo get(EnumSet<ListChangesOption> options) throws RestApiException;

  /** {@code get} with {@link ListChangesOption} set to ALL. */
  ChangeInfo get() throws RestApiException;
  /** {@code get} with {@link ListChangesOption} set to NONE. */
  ChangeInfo info() throws RestApiException;

  /**
   * A default implementation which allows source compatibility
   * when adding new methods to the interface.
   **/
  public class NotImplemented implements ChangeApi {
    @Override
    public String id() {
      throw new NotImplementedException();
    }

    @Override
    public RevisionApi current() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public RevisionApi revision(int id) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public RevisionApi revision(String id) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void abandon() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void abandon(AbandonInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void restore() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void restore(RestoreInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi revert() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeApi revert(RevertInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void addReviewer(AddReviewerInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void addReviewer(String in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeInfo get(EnumSet<ListChangesOption> options) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeInfo get() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChangeInfo info() throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
