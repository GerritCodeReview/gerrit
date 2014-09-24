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

package com.google.gerrit.server.change;

import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.validators.HashtagValidationListener;
import com.google.gerrit.server.validators.HashtagsValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.Set;

@Singleton
public class ValidateRemovedHashtags implements HashtagValidationListener {
  private final HashtagsUtil hashtagsUtil;

  @Inject
  ValidateRemovedHashtags(HashtagsUtil hashtagsUtil) {
    this.hashtagsUtil = hashtagsUtil;
  }

  @Override
  public void validateHashtags(Change change, Set<String> toAdd,
      Set<String> toRemove) throws HashtagsValidationException   {

    if (change != null && toRemove != null && !toRemove.isEmpty()) {
      Set<String> cmHashtags = null;
      try {
        cmHashtags = hashtagsUtil.getHashtagsInCurrentPSCommitMessage(change);
      } catch (OrmException | IOException e) {
        throw new HashtagsValidationException(String.format(
            "Cannot get hashtags specified in the commit message of Change %s",
            change.getId().get()), e);
      }

      if (cmHashtags != null
          && !cmHashtags.isEmpty()
          && !Sets.intersection(cmHashtags, toRemove).isEmpty()) {
        throw new HashtagsValidationException(
            "Hashtags specified in the commit message cannot be removed");
      }
    }
  }
}
