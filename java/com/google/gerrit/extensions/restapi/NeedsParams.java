// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.extensions.restapi;

import com.google.common.collect.ListMultimap;

/**
 * Optional interface for {@link RestCollection}.
 *
 * <p>Collections that implement this interface can get to know about the request parameters. The
 * request parameters are passed only if the collection is the endpoint, e.g. {@code
 * /changes/?q=abc} would trigger, but {@code /changes/100/?q=abc} does not.
 */
public interface NeedsParams {
  /**
   * Sets the request parameter.
   *
   * @param params the request parameter
   */
  void setParams(ListMultimap<String, String> params) throws RestApiException;
}
