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

package com.google.gerrit.server.util;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.parboiled.BaseParser;
import org.parboiled.Parboiled;
import org.parboiled.Rule;
import org.parboiled.annotations.BuildParseTree;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.ParseTreeUtils;
import org.parboiled.support.ParsingResult;

public class ParboiledTest {

  private static final String EXPECTED =
      "[Expression] '42'\n"
          + "  [Term] '42'\n"
          + "    [Factor] '42'\n"
          + "      [Number] '42'\n"
          + "        [0..9] '4'\n"
          + "        [0..9] '2'\n"
          + "    [zeroOrMore]\n"
          + "  [zeroOrMore]\n";

  private CalculatorParser parser;

  @Before
  public void setUp() {
    parser = Parboiled.createParser(CalculatorParser.class);
  }

  @Test
  public void test() {
    ParsingResult<String> result = new ReportingParseRunner<String>(parser.Expression()).run("42");
    assertThat(result.isSuccess()).isTrue();
    // next test is optional; we could stop here.
    assertThat(ParseTreeUtils.printNodeTree(result)).isEqualTo(EXPECTED);
  }

  @BuildParseTree
  static class CalculatorParser extends BaseParser<Object> {
    Rule Expression() {
      return sequence(Term(), zeroOrMore(anyOf("+-"), Term()));
    }

    Rule Term() {
      return sequence(Factor(), zeroOrMore(anyOf("*/"), Factor()));
    }

    Rule Factor() {
      return firstOf(Number(), sequence('(', Expression(), ')'));
    }

    Rule Number() {
      return oneOrMore(charRange('0', '9'));
    }
  }
}
