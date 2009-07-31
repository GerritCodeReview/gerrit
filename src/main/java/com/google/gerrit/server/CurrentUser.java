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

package com.google.gerrit.server;

import com.google.inject.servlet.RequestScoped;
import com.google.inject.servlet.SessionScoped;

/**
 * Information about the currently logged in user.
 * <p>
 * This is a {@link RequestScoped} or {@link SessionScoped} property managed by
 * Guice, depending on the environment. Application code should just assume it
 * is {@code RequestScoped}.
 *
 * @see IdentifiedUser
 */
public abstract class CurrentUser {
  /** An anonymous user, the identity is not known. */
  public static final CurrentUser ANONYMOUS = new Anonymous();

  private static class Anonymous extends CurrentUser {
    private Anonymous() {
    }

    @Override
    public String toString() {
      return "ANONYMOUS";
    }
  }
}
