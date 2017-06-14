// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.git;

/**
 * Wrapper for {@link org.eclipse.jgit.errors.LargeObjectException}. Since
 * org.eclipse.jgit.errors.LargeObjectException is a {@link RuntimeException} the GerritJsonServlet
 * would treat it as internal failure and as result the web ui would just show 'Internal Server
 * Error'. Wrapping org.eclipse.jgit.errors.LargeObjectException into a normal {@link Exception}
 * allows to display a proper error message.
 */
public class LargeObjectException extends Exception {

  private static final long serialVersionUID = 1L;

  public LargeObjectException(
      String message, org.eclipse.jgit.errors.LargeObjectException cause) {
    super(message, cause);
  }
}
