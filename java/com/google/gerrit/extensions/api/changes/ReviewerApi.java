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

import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Map;

public interface ReviewerApi {

  Map<String, Short> votes() throws RestApiException;

  void deleteVote(String label) throws RestApiException;

  void deleteVote(DeleteVoteInput input) throws RestApiException;

  void remove() throws RestApiException;

  void remove(DeleteReviewerInput input) throws RestApiException;

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements ReviewerApi {
    @Override
    public Map<String, Short> votes() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void deleteVote(String label) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void deleteVote(DeleteVoteInput input) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void remove() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void remove(DeleteReviewerInput input) throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
