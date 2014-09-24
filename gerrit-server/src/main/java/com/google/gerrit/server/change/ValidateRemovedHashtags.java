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
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

@Singleton
public class ValidateRemovedHashtags implements HashtagValidationListener {
  private static final Logger log = LoggerFactory
      .getLogger(ValidateRemovedHashtags.class);

  private final HashtagsUtil hashtagsUtil;

  @Inject
  ValidateRemovedHashtags(HashtagsUtil hashtagsUtil) {
    this.hashtagsUtil = hashtagsUtil;
  }

  @Override
  public void validateHashtags(Change change, Set<String> toAdd,
      Set<String> toRemove) throws ValidationException {
    try {
      if (toRemove != null && !toRemove.isEmpty()) {
        Set<String> intersection =
            Sets.intersection(
                hashtagsUtil.getHashtagsInCurrentPSCommitMessage(change),
                toRemove);
        if (!intersection.isEmpty()) {
          throw new ValidationException(String.format(
              "Need delete [ %s ] from commit message firstly",
              intersection.toArray()));
        }
      }
    } catch (OrmException | IOException e) {
      log.error("Can not valid removed hashtags", e);
      throw new ValidationException("Can not valid removed hashtags", e);
    }
  }
}
