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

package com.google.gerrit.server.git;

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
  public void testParseSimple() {
    try {
      QueryList ql = QueryList.parse(F_SIMPLE, null);
      assertTrue(Q_P.equals(ql.getQuery(N_FOO)));
      assertTrue(Q_B.equals(ql.getQuery(N_BAR)));
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testParseWHeader() {
    try {
      QueryList ql = QueryList.parse(HEADER + F_SIMPLE, null);
      assertTrue(Q_P.equals(ql.getQuery(N_FOO)));
      assertTrue(Q_B.equals(ql.getQuery(N_BAR)));
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testParseWComments() {
    try {
      QueryList ql = QueryList.parse(C1 + F_SIMPLE + C2, null);
      assertTrue(Q_P.equals(ql.getQuery(N_FOO)));
      assertTrue(Q_B.equals(ql.getQuery(N_BAR)));
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testParseFooComment() {
    try {
      QueryList ql = QueryList.parse("#" + L_FOO + L_BAR, null);
      assertTrue(null == ql.getQuery(N_FOO));
      assertTrue(Q_B.equals(ql.getQuery(N_BAR)));
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testParsePaddedFronts() {
    try {
      QueryList ql = QueryList.parse(F_PAD_F, null);
      assertTrue(Q_P.equals(ql.getQuery(N_FOO)));
      assertTrue(Q_B.equals(ql.getQuery(N_BAR)));
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testParsePaddedEnds() {
    try {
      QueryList ql = QueryList.parse(F_PAD_E, null);
      assertTrue(Q_P.equals(ql.getQuery(N_FOO)));
      assertTrue(Q_B.equals(ql.getQuery(N_BAR)));
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testParseComplex() {
    try {
      QueryList ql = QueryList.parse(L_COMPLEX, null);
      assertTrue(Q_COMPLEX.equals(ql.getQuery(N_FOO)));
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testParseBad() {
    try {
      QueryList ql = QueryList.parse(L_BAD, null);
      assertTrue(false);
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  @Test
  public void testAsText() {
    String text = HEADER + "#\n" + F_PROPER;
    try {
      QueryList ql = QueryList.parse(F_SIMPLE, null);
      String asText = ql.asText();
      assertTrue(text.equals(asText));

      ql = QueryList.parse(asText, null);
      asText = ql.asText();
      assertTrue(text.equals(asText));
    } catch (Exception e) {
      assertTrue(false);
    }
  }
}
