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

import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.ChangeJson.HashtagInfo;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Set;

@Singleton
public class GetHashtags implements RestReadView<ChangeResource> {
  private HashtagsUtil hashtagsUtil;

  @Inject
  GetHashtags(HashtagsUtil hashtagsUtil) {
    this.hashtagsUtil = hashtagsUtil;
  }

  @Override
  public Response<? extends Set<HashtagInfo>> apply(ChangeResource req)
      throws OrmException {
    return Response.ok(hashtagsUtil.getHashtagsInfo(req.getControl()));
  }
}
