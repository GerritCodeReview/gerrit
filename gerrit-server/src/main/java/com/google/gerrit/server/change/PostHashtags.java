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

import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.Set;

@Singleton
public class PostHashtags implements RestModifyView<ChangeResource, HashtagsInput> {
  private HashtagsUtil hashtagsUtil;

  @Inject
  PostHashtags(HashtagsUtil hashtagsUtil) {
    this.hashtagsUtil = hashtagsUtil;
  }

  @Override
  public Response<? extends Set<String>> apply(ChangeResource req, HashtagsInput input)
      throws AuthException, OrmException, IOException, BadRequestException,
      ResourceConflictException {

    try {
      return Response.ok(hashtagsUtil.setHashtags(
          req.getControl(), input, true, true));
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage());
    } catch (ValidationException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }
}
