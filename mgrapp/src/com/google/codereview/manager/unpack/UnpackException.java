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

package com.google.codereview.manager.unpack;

import com.google.codereview.internal.UpdateReceivedBundle.UpdateReceivedBundleRequest.CodeType;

/** Indicates unpacking failed and the bundle cannot be processed. */
class UnpackException extends Exception {
  final CodeType status;
  final String details;

  UnpackException(final CodeType s, final String msg) {
    super(msg, null);
    status = s;
    details = msg;
  }

  UnpackException(final CodeType s, final String msg, final Throwable why) {
    super(msg, why);
    status = s;
    details = msg;
  }
}
