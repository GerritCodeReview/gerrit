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
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.replay;

import com.google.gerrit.git.ValidationError;
import java.io.IOException;
import junit.framework.TestCase;
import org.junit.Test;

public class QueryListTest extends TestCase {
  public static final String Q_P = "project:foo";
  public static final String Q_B = "branch:bar";
  public static final String Q_COMPLEX = "branch:bar AND peers:'is:open\t'";

  public static final String N_FOO = "foo";
  public static final String N_BAR = "bar";

  public static final String L_FOO = N_FOO + "\t" + Q_P + "\n";
  public static final String L_BAR = N_BAR + "\t" + Q_B + "\n";
  public static final String L_FOO_PROP = N_FOO + "   \t" + Q_P + "\n";
  public static final String L_BAR_PROP = N_BAR + "   \t" + Q_B + "\n";
  public static final String L_FOO_PAD_F = " " + N_FOO + "\t" + Q_P + "\n";
  public static final String L_FOO_PAD_E = N_FOO + " \t" + Q_P + "\n";
  public static final String L_BAR_PAD_F = N_BAR + "\t " + Q_B + "\n";
  public static final String L_BAR_PAD_E = N_BAR + "\t" + Q_B + " \n";
  public static final String L_COMPLEX = N_FOO + "\t" + Q_COMPLEX + "\t \n";
  public static final String L_BAD = N_FOO + "\n";

  public static final String HEADER = "# Name\tQuery\n";
  public static final String C1 = "# A Simple Comment\n";
  public static final String C2 = "# Comment with a tab\t and multi # # #\n";

  public static final String F_SIMPLE = L_FOO + L_BAR;
  public static final String F_PROPER = L_BAR_PROP + L_FOO_PROP; // alpha order
  public static final String F_PAD_F = L_FOO_PAD_F + L_BAR_PAD_F;
  public static final String F_PAD_E = L_FOO_PAD_E + L_BAR_PAD_E;

  @Test
  public void testParseSimple() throws Exception {
    QueryList ql = QueryList.parse(F_SIMPLE, null);
    assertThat(ql.getQuery(N_FOO)).isEqualTo(Q_P);
    assertThat(ql.getQuery(N_BAR)).isEqualTo(Q_B);
  }

  @Test
  public void testParseWHeader() throws Exception {
    QueryList ql = QueryList.parse(HEADER + F_SIMPLE, null);
    assertThat(ql.getQuery(N_FOO)).isEqualTo(Q_P);
    assertThat(ql.getQuery(N_BAR)).isEqualTo(Q_B);
  }

  @Test
  public void testParseWComments() throws Exception {
    QueryList ql = QueryList.parse(C1 + F_SIMPLE + C2, null);
    assertThat(ql.getQuery(N_FOO)).isEqualTo(Q_P);
    assertThat(ql.getQuery(N_BAR)).isEqualTo(Q_B);
  }

  @Test
  public void testParseFooComment() throws Exception {
    QueryList ql = QueryList.parse("#" + L_FOO + L_BAR, null);
    assertThat(ql.getQuery(N_FOO)).isNull();
    assertThat(ql.getQuery(N_BAR)).isEqualTo(Q_B);
  }

  @Test
  public void testParsePaddedFronts() throws Exception {
    QueryList ql = QueryList.parse(F_PAD_F, null);
    assertThat(ql.getQuery(N_FOO)).isEqualTo(Q_P);
    assertThat(ql.getQuery(N_BAR)).isEqualTo(Q_B);
  }

  @Test
  public void testParsePaddedEnds() throws Exception {
    QueryList ql = QueryList.parse(F_PAD_E, null);
    assertThat(ql.getQuery(N_FOO)).isEqualTo(Q_P);
    assertThat(ql.getQuery(N_BAR)).isEqualTo(Q_B);
  }

  @Test
  public void testParseComplex() throws Exception {
    QueryList ql = QueryList.parse(L_COMPLEX, null);
    assertThat(ql.getQuery(N_FOO)).isEqualTo(Q_COMPLEX);
  }

  @Test(expected = IOException.class)
  public void testParseBad() throws Exception {
    ValidationError.Sink sink = createNiceMock(ValidationError.Sink.class);
    replay(sink);
    QueryList.parse(L_BAD, sink);
  }

  @Test
  public void testAsText() throws Exception {
    String expectedText = HEADER + "#\n" + F_PROPER;
    QueryList ql = QueryList.parse(F_SIMPLE, null);
    String asText = ql.asText();
    assertThat(asText).isEqualTo(expectedText);

    ql = QueryList.parse(asText, null);
    asText = ql.asText();
    assertThat(asText).isEqualTo(expectedText);
  }
}
