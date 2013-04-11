// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gwtexpui.linker.server;

import javax.servlet.http.HttpServletRequest;

/** A selection rule for a permutation property. */
public interface Rule {
  /** @return the property name, for example {@code "user.agent"}. */
  public String getName();

  /**
   * Compute the value for this property, given the current request.
   * <p>
   * This rule method must compute the proper permutation value, matching what
   * the GWT module XML files use for this property. The rule may use any state
   * available in the current servlet request.
   * <p>
   * If this method returns {@code null} server side selection will be aborted
   * and selection for all properties will be handled on the client side by the
   * {@code nocache.js} file.
   *
   * @param req the request
   * @return the value for the property; null if the value cannot be determined
   *         on the server side.
   */
  public String select(HttpServletRequest req);
}
