// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.entities;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;

public class LabelTypesTest {
  private static final LabelType CODE_REVIEW =
      LabelType.create("Code-Review", ImmutableList.of(LabelValue.create((short) 0, "No score")));
  private static final LabelType VERIFIED =
      LabelType.create("Verified", ImmutableList.of(LabelValue.create((short) 0, "No score")));
  private static final LabelType CI_PASS =
      LabelType.create("CI-Pass", ImmutableList.of(LabelValue.create((short) 0, "No score")));

  @Test
  public void emptyLabelTypes() {
    LabelTypes labelTypes = new LabelTypes(Collections.emptyList());
    assertThat(labelTypes.getLabelTypes()).isEmpty();
    assertThat(labelTypes.byLabel("Code-Review")).isEmpty();
    assertThat(labelTypes.byLabel(LabelId.create("Code-Review"))).isEmpty();
  }

  @Test
  public void getLabelTypesReturnsImmutableListInOrder() {
    LabelTypes labelTypes = new LabelTypes(ImmutableList.of(CODE_REVIEW, VERIFIED, CI_PASS));
    assertThat(labelTypes.getLabelTypes())
        .containsExactly(CODE_REVIEW, VERIFIED, CI_PASS)
        .inOrder();

    assertThrows(
        UnsupportedOperationException.class, () -> labelTypes.getLabelTypes().add(CODE_REVIEW));
  }

  @Test
  public void byLabelCaseInsensitiveString() {
    LabelTypes labelTypes = new LabelTypes(ImmutableList.of(CODE_REVIEW, VERIFIED));
    assertThat(labelTypes.byLabel("Code-Review")).hasValue(CODE_REVIEW);
    assertThat(labelTypes.byLabel("code-review")).hasValue(CODE_REVIEW);
    assertThat(labelTypes.byLabel("CODE-REVIEW")).hasValue(CODE_REVIEW);
    assertThat(labelTypes.byLabel("VERIFIED")).hasValue(VERIFIED);
    assertThat(labelTypes.byLabel("Non-Existent")).isEmpty();
  }

  @Test
  public void byLabelCaseInsensitiveLabelId() {
    LabelTypes labelTypes = new LabelTypes(ImmutableList.of(CODE_REVIEW, VERIFIED));
    assertThat(labelTypes.byLabel(LabelId.create("Code-Review"))).hasValue(CODE_REVIEW);
    assertThat(labelTypes.byLabel(LabelId.create("code-review"))).hasValue(CODE_REVIEW);
    assertThat(labelTypes.byLabel(LabelId.create("Unknown"))).isEmpty();
  }

  @Test
  public void duplicateLabelNamesRetainsLastOccurrence() {
    LabelType codeReview1 =
        LabelType.create(
            "Code-Review", ImmutableList.of(LabelValue.create((short) 0, "Initial score")));
    LabelType codeReview2 =
        LabelType.create(
            "Code-Review", ImmutableList.of(LabelValue.create((short) 1, "Updated score")));

    LabelTypes labelTypes = new LabelTypes(ImmutableList.of(codeReview1, codeReview2));
    assertThat(labelTypes.getLabelTypes()).containsExactly(codeReview1, codeReview2).inOrder();
    assertThat(labelTypes.byLabel("Code-Review")).hasValue(codeReview2);
  }

  @Test
  public void duplicateLabelNamesDifferingCaseRetainsLastOccurrence() {
    LabelType lower =
        LabelType.create("code-review", ImmutableList.of(LabelValue.create((short) 0, "Lower")));
    LabelType upper =
        LabelType.create("CODE-REVIEW", ImmutableList.of(LabelValue.create((short) 1, "Upper")));

    LabelTypes labelTypes = new LabelTypes(ImmutableList.of(lower, upper));
    assertThat(labelTypes.byLabel("Code-Review")).hasValue(upper);
  }

  @Test
  public void nameComparatorSortsByConfiguredOrder() {
    LabelTypes labelTypes = new LabelTypes(ImmutableList.of(CODE_REVIEW, VERIFIED, CI_PASS));
    Comparator<String> comparator = labelTypes.nameComparator();

    List<String> names = Arrays.asList("CI-Pass", "Code-Review", "Verified");
    names.sort(comparator);
    assertThat(names).containsExactly("Code-Review", "Verified", "CI-Pass").inOrder();
  }

  @Test
  public void nameComparatorPlacesUnknownLabelsAtEndInAlphabeticalOrder() {
    LabelTypes labelTypes = new LabelTypes(ImmutableList.of(VERIFIED, CODE_REVIEW));
    Comparator<String> comparator = labelTypes.nameComparator();

    List<String> names = Arrays.asList("Unknown-Z", "Code-Review", "Unknown-A", "Verified");
    names.sort(comparator);
    assertThat(names)
        .containsExactly("Verified", "Code-Review", "Unknown-A", "Unknown-Z")
        .inOrder();
  }

  @Test
  public void nameComparatorWithEmptyLabelTypesSortsAlphabetically() {
    LabelTypes labelTypes = new LabelTypes(Collections.emptyList());
    Comparator<String> comparator = labelTypes.nameComparator();

    List<String> names = Arrays.asList("Z", "B", "A");
    names.sort(comparator);
    assertThat(names).containsExactly("A", "B", "Z").inOrder();
  }

  @Test
  public void nameComparatorReturnsZeroForIdenticalNames() {
    LabelTypes labelTypes = new LabelTypes(ImmutableList.of(CODE_REVIEW, VERIFIED));
    Comparator<String> comparator = labelTypes.nameComparator();

    assertThat(comparator.compare("Code-Review", "Code-Review")).isEqualTo(0);
    assertThat(comparator.compare("Unknown", "Unknown")).isEqualTo(0);
  }

  @SuppressWarnings({"EqualsNull", "EqualsIncompatibleType"})
  @Test
  public void equalsAndHashCodeContract() {
    LabelTypes labelTypes1 = new LabelTypes(ImmutableList.of(CODE_REVIEW, VERIFIED));
    LabelTypes labelTypes2 = new LabelTypes(ImmutableList.of(CODE_REVIEW, VERIFIED));
    LabelTypes labelTypes3 = new LabelTypes(ImmutableList.of(VERIFIED, CODE_REVIEW));

    assertThat(labelTypes1.equals(labelTypes1)).isTrue();
    assertThat(labelTypes1.equals(labelTypes2)).isTrue();
    assertThat(labelTypes2.equals(labelTypes1)).isTrue();
    assertThat(labelTypes1.hashCode()).isEqualTo(labelTypes2.hashCode());

    assertThat(labelTypes1.equals(labelTypes3)).isFalse();
    assertThat(labelTypes1.equals(null)).isFalse();
    assertThat(labelTypes1.equals("some string")).isFalse();
  }

  @Test
  public void testToString() {
    LabelTypes labelTypes = new LabelTypes(ImmutableList.of(CODE_REVIEW, VERIFIED));
    assertThat(labelTypes.toString()).isEqualTo(ImmutableList.of(CODE_REVIEW, VERIFIED).toString());
  }
}
