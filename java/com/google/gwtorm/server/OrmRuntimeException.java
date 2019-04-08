// Copyright 2008 Google Inc.
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

package com.google.gwtorm.server;

/** Any data store read or write error. */
@SuppressWarnings("serial")
public class OrmRuntimeException extends RuntimeException {
  public OrmRuntimeException(final String message) {
    super(message);
  }

  public OrmRuntimeException(final String message, final Throwable why) {
    super(message, why);
  }

  public OrmRuntimeException(final Throwable why) {
    super(why);
  }
}
