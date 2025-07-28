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

package com.google.gerrit.extensions.api.changes;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.extensions.common.AiCodeReviewInput;
import java.io.IOException;

/**
 * Performs a call to the AI model to get the review suggestion from AI.
 *
 * <p>An implementation of this interface must provide a connection to the AI model mentioned in
 * input parameter
 */
@ExtensionPoint
public interface AiCodeReviewProvider {
  String getAiReview(AiCodeReviewInput input) throws IOException;
}
