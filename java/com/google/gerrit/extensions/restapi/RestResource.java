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

package com.google.gerrit.extensions.restapi;

import java.sql.Timestamp;

/**
 * Generic resource handle defining arguments to views.
 *
 * <p>Resource handle returned by {@link RestCollection} and passed to a {@link RestView} such as
 * {@link RestReadView} or {@link RestModifyView}.
 */
public interface RestResource {

  /** A resource with a last modification date. */
  public interface HasLastModified {
    /** @return time for the Last-Modified header. HTTP truncates the header value to seconds. */
    Timestamp getLastModified();
  }

  /** A resource with an ETag. */
  public interface HasETag {
    String getETag();
  }
}
