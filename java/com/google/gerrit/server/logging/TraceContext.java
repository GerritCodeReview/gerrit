// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.logging;

import static com.google.common.base.Preconditions.checkNotNull;

public class TraceContext implements AutoCloseable {
  private final String tagName;
  private final String tagValue;
  private final boolean removeOnClose;

  public TraceContext(String tagName, Object tagValue) {
    this.tagName = checkNotNull(tagName, "tag name is required");
    this.tagValue = checkNotNull(tagValue, "tag value is required").toString();
    this.removeOnClose = LoggingContext.getInstance().addTag(this.tagName, this.tagValue);
  }

  @Override
  public void close() {
    if (removeOnClose) {
      LoggingContext.getInstance().removeTag(tagName, tagValue);
    }
  }
}
