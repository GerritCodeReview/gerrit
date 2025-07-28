// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.changes.AiCodeReviewProvider;
import com.google.gerrit.extensions.common.AiCodeReviewInput;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class AiCodeReview implements RestModifyView<ChangeResource, AiCodeReviewInput> {
  private final DynamicMap<AiCodeReviewProvider> aiCodeReviewsProvider;

  @Inject
  public AiCodeReview(DynamicMap<AiCodeReviewProvider> aiCodeReviewsProvider) {
    this.aiCodeReviewsProvider = aiCodeReviewsProvider;
  }

  @Override
  public Response<String> apply(ChangeResource resource, AiCodeReviewInput input)
      throws AuthException, BadRequestException, IOException, ResourceConflictException {
    validateInput(input);

    String aiCodeReviewItem =
        aiCodeReviewsProvider.get(input.pluginName, input.model).getAiReview(input);

    return Response.ok(aiCodeReviewItem);
  }

  private void validateInput(AiCodeReviewInput input) throws BadRequestException {
    if (Strings.isNullOrEmpty(input.prompt)
        || Strings.isNullOrEmpty(input.model)
        || Strings.isNullOrEmpty(input.pluginName)) {
      throw new BadRequestException("Input fields model, prompt and plugin_name are mandatory");
    }
  }
}
