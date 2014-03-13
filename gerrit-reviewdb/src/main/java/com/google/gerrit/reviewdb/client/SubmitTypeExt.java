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

package com.google.gerrit.reviewdb.client;

import com.google.gerrit.extensions.common.SubmitType;

import java.util.Objects;

public class SubmitTypeExt {

  public static enum ContentMerge {
    TRUE,
    FALSE,
    DEFAULT;
  }

  private final SubmitType submitType;
  private final ContentMerge contentMerge;

  public SubmitTypeExt(SubmitType submitType, ContentMerge contentMerge) {
    this.submitType = submitType;
    this.contentMerge = contentMerge;
  }

  public SubmitType getSubmitType() {
    return submitType;
  }

  public ContentMerge getContentMerge() {
    return contentMerge;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SubmitTypeExt)) {
      return false;
    }
    SubmitTypeExt other = (SubmitTypeExt) o;
    return submitType == other.submitType
        && contentMerge == other.contentMerge;
  }

  @Override
  public int hashCode() {
    return Objects.hash(submitType, contentMerge);
  }
}
