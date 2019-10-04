// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.ValidationError;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class DestinationListTest {
  public static final String R_FOO = "refs/heads/foo";
  public static final String R_BAR = "refs/heads/bar";

  public static final String P_MY = "myproject";
  public static final String P_SLASH = "my/project/with/slashes";
  public static final String P_COMPLEX = " a/project/with spaces and \ttabs ";

  public static final String L_FOO = R_FOO + "\t" + P_MY + "\n";
  public static final String L_BAR = R_BAR + "\t" + P_SLASH + "\n";
  public static final String L_FOO_PAD_F = " " + R_FOO + "\t" + P_MY + "\n";
  public static final String L_FOO_PAD_E = R_FOO + " \t" + P_MY + "\n";
  public static final String L_COMPLEX = R_FOO + "\t" + P_COMPLEX + "\n";
  public static final String L_BAD = R_FOO + "\n";

  public static final String HEADER = "# Ref\tProject\n";
  public static final String HEADER_PROPER = "# Ref         \tProject\n";
  public static final String C1 = "# A Simple Comment\n";
  public static final String C2 = "# Comment with a tab\t and multi # # #\n";

  public static final String F_SIMPLE = L_FOO + L_BAR;
  public static final String F_PROPER = L_BAR + L_FOO; // alpha order
  public static final String F_PAD_F = L_FOO_PAD_F + L_BAR;
  public static final String F_PAD_E = L_FOO_PAD_E + L_BAR;

  public static final String LABEL = "label";
  public static final String LABEL2 = "another";

  public static final BranchNameKey B_FOO = dest(P_MY, R_FOO);
  public static final BranchNameKey B_BAR = dest(P_SLASH, R_BAR);
  public static final BranchNameKey B_COMPLEX = dest(P_COMPLEX, R_FOO);

  public static final Set<BranchNameKey> D_SIMPLE = new HashSet<>();

  static {
    D_SIMPLE.clear();
    D_SIMPLE.add(B_FOO);
    D_SIMPLE.add(B_BAR);
  }

  private static BranchNameKey dest(String project, String ref) {
    return BranchNameKey.create(Project.nameKey(project), ref);
  }

  @Test
  public void testParseSimple() throws Exception {
    DestinationList dl = new DestinationList();
    dl.parseLabel(LABEL, F_SIMPLE, null);
    Set<BranchNameKey> branches = dl.getDestinations(LABEL);
    assertThat(branches).containsExactlyElementsIn(D_SIMPLE);
  }

  @Test
  public void testParseWHeader() throws Exception {
    DestinationList dl = new DestinationList();
    dl.parseLabel(LABEL, HEADER + F_SIMPLE, null);
    Set<BranchNameKey> branches = dl.getDestinations(LABEL);
    assertThat(branches).containsExactlyElementsIn(D_SIMPLE);
  }

  @Test
  public void testParseWComments() throws Exception {
    DestinationList dl = new DestinationList();
    dl.parseLabel(LABEL, C1 + F_SIMPLE + C2, null);
    Set<BranchNameKey> branches = dl.getDestinations(LABEL);
    assertThat(branches).containsExactlyElementsIn(D_SIMPLE);
  }

  @Test
  public void testParseFooComment() throws Exception {
    DestinationList dl = new DestinationList();
    dl.parseLabel(LABEL, "#" + L_FOO + L_BAR, null);
    Set<BranchNameKey> branches = dl.getDestinations(LABEL);
    assertThat(branches).doesNotContain(B_FOO);
    assertThat(branches).contains(B_BAR);
  }

  @Test
  public void testParsePaddedFronts() throws Exception {
    DestinationList dl = new DestinationList();
    dl.parseLabel(LABEL, F_PAD_F, null);
    Set<BranchNameKey> branches = dl.getDestinations(LABEL);
    assertThat(branches).containsExactlyElementsIn(D_SIMPLE);
  }

  @Test
  public void testParsePaddedEnds() throws Exception {
    DestinationList dl = new DestinationList();
    dl.parseLabel(LABEL, F_PAD_E, null);
    Set<BranchNameKey> branches = dl.getDestinations(LABEL);
    assertThat(branches).containsExactlyElementsIn(D_SIMPLE);
  }

  @Test
  public void testParseComplex() throws Exception {
    DestinationList dl = new DestinationList();
    dl.parseLabel(LABEL, L_COMPLEX, null);
    Set<BranchNameKey> branches = dl.getDestinations(LABEL);
    assertThat(branches).contains(B_COMPLEX);
  }

  @Test
  public void testParseBad() throws IOException {
    List<ValidationError> errors = new ArrayList<>();
    new DestinationList().parseLabel(LABEL, L_BAD, errors::add);
    assertThat(errors)
        .containsExactly(new ValidationError("destinationslabel", 1, "missing tab delimiter"));
  }

  @Test
  public void testParse2Labels() throws Exception {
    DestinationList dl = new DestinationList();
    dl.parseLabel(LABEL, F_SIMPLE, null);
    Set<BranchNameKey> branches = dl.getDestinations(LABEL);
    assertThat(branches).containsExactlyElementsIn(D_SIMPLE);

    dl.parseLabel(LABEL2, L_COMPLEX, null);
    branches = dl.getDestinations(LABEL);
    assertThat(branches).containsExactlyElementsIn(D_SIMPLE);
    branches = dl.getDestinations(LABEL2);
    assertThat(branches).contains(B_COMPLEX);
  }

  @Test
  public void testAsText() throws Exception {
    String text = HEADER_PROPER + "#\n" + F_PROPER;
    DestinationList dl = new DestinationList();
    dl.parseLabel(LABEL, F_SIMPLE, null);
    String asText = dl.asText(LABEL);
    assertThat(text).isEqualTo(asText);

    dl.parseLabel(LABEL2, asText, null);
    assertThat(text).isEqualTo(dl.asText(LABEL2));
  }
}
