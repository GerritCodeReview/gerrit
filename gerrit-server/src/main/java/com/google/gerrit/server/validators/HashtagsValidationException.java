// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.validators;

import java.util.Set;

public class HashtagsValidationException extends ValidationException {
  private static final long serialVersionUID = 2L;
  private Set<String> invalidHashtags;

  public HashtagsValidationException(String reason) {
    super(reason);
  }

  public HashtagsValidationException(String reason, Set<String> invalidHashtags) {
    super(reason);
    this.invalidHashtags = invalidHashtags;
  }

  public HashtagsValidationException(String reason, Throwable why) {
    super(reason, why);
  }

  public HashtagsValidationException(String reason,
      Set<String> invalidHashtags, Throwable why) {
    super(reason, why);
    this.invalidHashtags = invalidHashtags;
  }

  public Set<String> getInvalidHashtags() {
    return this.invalidHashtags;
  }
}
