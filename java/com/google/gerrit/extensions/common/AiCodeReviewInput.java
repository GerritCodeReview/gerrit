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

package com.google.gerrit.extensions.common;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.BadRequestException;
import java.util.List;

/** Input for the AI code review REST API that provides code review suggestions by AI. */
public class AiCodeReviewInput {
  /** List of AI models to be used to request AI code review for the current change. */
  public List<String> models;

  /**
   * Prompt containing information about a request for AI to perform a code review, including
   * details of the change.
   */
  public String prompt;

  /**
   * The AI plugin to request a code review. This enables installing multiple plugins implementing
   * the AICodeReview extension point simultaneously.
   */
  public String pluginName;

  /**
   * Validates the {@link AiCodeReviewInput} provided to the AI code review API.
   *
   * <p>This method ensures that all mandatory fields are present and non-empty:
   *
   * <ul>
   *   <li>{@code models} must not be {@code null} or empty.
   *   <li>{@code prompt} must not be {@code null} or empty.
   *   <li>{@code pluginName} must not be {@code null} or empty.
   * </ul>
   *
   * <p>If any of these validations fail, a {@link BadRequestException} is thrown.
   *
   * @param input the input object containing AI code review request details
   * @throws BadRequestException if any required field is missing or invalid
   */
  public void validate(AiCodeReviewInput input) throws BadRequestException {
    if (Strings.isNullOrEmpty(input.prompt)
        || input.models == null
        || input.models.isEmpty()
        || Strings.isNullOrEmpty(input.pluginName)) {
      throw new BadRequestException("Input fields models, prompt and plugin_name are mandatory");
    }
  }
}
