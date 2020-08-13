// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.change;

/** An aggregation of methods on a specific, published comment. */
public interface PerCommentOperations {

  /**
   * Retrieves the published comment.
   *
   * <p><strong>Note:</strong> This call will fail with an exception if the requested comment
   * doesn't exist or if it is a comment of another type.
   *
   * @return the corresponding {@code TestComment}
   */
  TestHumanComment get();
}
