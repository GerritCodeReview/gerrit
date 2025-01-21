// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.project.testing;

import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import java.util.Arrays;
import java.util.Optional;

public class TestLabels {
  public static final String CODE_REVIEW_LABEL_DESCRIPTION = "Code review label description";
  public static final String VERIFIED_LABEL_DESCRIPTION = "Verified label description";

  public static LabelType codeReview() {
    return label(
        LabelId.CODE_REVIEW,
        CODE_REVIEW_LABEL_DESCRIPTION,
        value(2, "Looks good to me, approved"),
        value(1, "Looks good to me, but someone else must approve"),
        value(0, "No score"),
        value(-1, "I would prefer this is not submitted as is"),
        value(-2, "This shall not be submitted"));
  }

  public static LabelType codeReviewWithBlock() {
    return label(
        LabelId.CODE_REVIEW,
        CODE_REVIEW_LABEL_DESCRIPTION,
        LabelFunction.MAX_WITH_BLOCK,
        value(2, "Looks good to me, approved"),
        value(1, "Looks good to me, but someone else must approve"),
        value(0, "No score"),
        value(-1, "I would prefer this is not submitted as is"),
        value(-2, "This shall not be submitted"));
  }

  public static LabelType verified() {
    return label(
        LabelId.VERIFIED,
        VERIFIED_LABEL_DESCRIPTION,
        value(1, LabelId.VERIFIED),
        value(0, "No score"),
        value(-1, "Fails"));
  }

  public static LabelType verifiedWithBlock() {
    return label(
        LabelId.VERIFIED,
        VERIFIED_LABEL_DESCRIPTION,
        LabelFunction.MAX_WITH_BLOCK,
        value(1, LabelId.VERIFIED),
        value(0, "No score"),
        value(-1, "Fails"));
  }

  public static LabelType patchSetLock() {
    LabelType.Builder label =
        labelBuilder(
            "Patch-Set-Lock", value(1, "Patch Set Locked"), value(0, "Patch Set Unlocked"));
    label.setPatchSetLockFunction();
    return label.build();
  }

  public static LabelValue value(int value, String text) {
    return LabelValue.create((short) value, text);
  }

  public static LabelType label(String name, String description, LabelValue... values) {
    return labelBuilder(name, values).setDescription(Optional.of(description)).build();
  }

  public static LabelType label(
      String name, String description, LabelFunction labelFunction, LabelValue... values) {
    return labelBuilder(name, values)
        .setFunction(labelFunction)
        .setDescription(Optional.of(description))
        .build();
  }

  public static LabelType label(String name, LabelValue... values) {
    return labelBuilder(name, values).build();
  }

  public static LabelType.Builder labelBuilder(String name, LabelValue... values) {
    return LabelType.builder(name, Arrays.asList(values));
  }

  private TestLabels() {}
}
