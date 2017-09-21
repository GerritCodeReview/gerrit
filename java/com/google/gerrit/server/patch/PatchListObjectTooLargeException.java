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

package com.google.gerrit.server.patch;

/**
 * Exception thrown when the PatchList could not be computed because previous attempts failed with
 * {@code LargeObjectException}. This is not thrown on the first computation.
 */
public class PatchListObjectTooLargeException extends PatchListNotAvailableException {
  private static final long serialVersionUID = 1L;

  public PatchListObjectTooLargeException(String message) {
    super(message);
  }
}
