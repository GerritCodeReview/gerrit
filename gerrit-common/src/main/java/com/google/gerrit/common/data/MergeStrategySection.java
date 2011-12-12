// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.common.data;

/**
 * It indicates the merge strategy to use for a refs pattern when submitting a
 * change.
 */
public class MergeStrategySection extends RefConfigSection {

  public static enum SubmitType {
    FAST_FORWARD_ONLY,

    MERGE_IF_NECESSARY,

    MERGE_ALWAYS,

    CHERRY_PICK;
  }

  protected SubmitType submitType;

  protected boolean useContentMerge;
  
  protected boolean isDeleted;

  public MergeStrategySection() {
  }

  public MergeStrategySection(String refPattern) {
    super(refPattern);
  }

  public SubmitType getSubmitType() {
    return submitType;
  }

  public boolean isUseContentMerge() {
    return useContentMerge;
  }

  public void setSubmitType(final SubmitType type) {
    submitType = type;
  }
  
  public void setUseContentMerge(final boolean cm) {
    useContentMerge = cm;
  }

  public boolean isDeleted() {
    return isDeleted;
  }

  public void setDeleted(boolean isDeleted) {
    this.isDeleted = isDeleted;
  }

  public String toString() {
    return "MergeStrategySection[" + getName() + "]";
  }

  @Override
  public boolean equals(Object obj) {
    boolean equals = false;

    if (obj instanceof MergeStrategySection
        && useContentMerge == ((MergeStrategySection) obj).useContentMerge
        && submitType.equals(((MergeStrategySection) obj).submitType)) {
      equals = true;
    }

    return equals;
  }
}
