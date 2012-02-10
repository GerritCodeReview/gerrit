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
 * It indicates the change submit action to use for a refs pattern.
 */
public class SubmitActionSection extends RefConfigSection {

  public static enum SubmitType {
    FAST_FORWARD_ONLY,

    MERGE_IF_NECESSARY,

    MERGE_ALWAYS,

    CHERRY_PICK;
  }

  public static enum UseContentMerge {
    TRUE,

    FALSE;
  }

  protected SubmitType submitType;

  protected UseContentMerge useContentMerge;

  public SubmitActionSection() {
  }

  public SubmitActionSection(String refPattern) {
    super(refPattern);
  }

  public SubmitType getSubmitType() {
    return submitType;
  }

  public UseContentMerge isUseContentMerge() {
    return useContentMerge;
  }

  public void setSubmitType(final SubmitType type) {
    submitType = type;
  }

  public void setUseContentMerge(final UseContentMerge cm) {
    useContentMerge = cm;
  }

  public String toString() {
    return "SubmitActionSection[" + getName() + "]";
  }

  @Override
  public boolean equals(Object obj) {
    boolean equals = false;

    if (obj instanceof SubmitActionSection
        && ((name == null && ((SubmitActionSection) obj).name == null) || //
        (name != null && name.equals(name)))
        && ((useContentMerge == null && ((SubmitActionSection) obj).useContentMerge == null) || //
        (useContentMerge != null && useContentMerge
            .equals(((SubmitActionSection) obj).useContentMerge)))
        && ((submitType == null && ((SubmitActionSection) obj).submitType == null) || //
        (submitType != null && submitType
            .equals(((SubmitActionSection) obj).submitType)))) {
      equals = true;
    }

    return equals;
  }

  @Override
  public int hashCode() {
    return name != null ? name.hashCode() : "".hashCode();
  }
}
