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

package com.google.gerrit.rules;

import static org.easymock.EasyMock.expect;

import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.Util;
import com.google.inject.AbstractModule;
import com.googlecode.prolog_cafe.exceptions.CompileException;
import com.googlecode.prolog_cafe.exceptions.ReductionLimitException;
import com.googlecode.prolog_cafe.lang.JavaObjectTerm;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import java.io.PushbackReader;
import java.io.StringReader;
import java.util.Arrays;
import org.easymock.EasyMock;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class GerritCommonTest extends PrologTestCase {
  @Before
  public void setUp() throws Exception {
    load(
        "gerrit",
        "gerrit_common_test.pl",
        new AbstractModule() {
          @Override
          protected void configure() {
            Config cfg = new Config();
            cfg.setInt("rules", null, "reductionLimit", 1300);
            cfg.setInt("rules", null, "compileReductionLimit", (int) 1e6);
            bind(PrologEnvironment.Args.class)
                .toInstance(new PrologEnvironment.Args(null, null, null, null, null, null, cfg));
          }
        });
  }

  @Override
  protected void setUpEnvironment(PrologEnvironment env) {
    LabelTypes labelTypes = new LabelTypes(Arrays.asList(Util.codeReview(), Util.verified()));
    ChangeControl ctl = EasyMock.createMock(ChangeControl.class);
    expect(ctl.getLabelTypes()).andStubReturn(labelTypes);
    EasyMock.replay(ctl);
    env.set(StoredValues.CHANGE_CONTROL, ctl);
  }

  @Test
  public void testGerritCommon() {
    runPrologBasedTests();
  }

  @Test
  public void testReductionLimit() throws CompileException {
    PrologEnvironment env = envFactory.create(machine);
    setUpEnvironment(env);

    String script = "loopy :- b(5).\n" + "b(N) :- N > 0, !, S = N - 1, b(S).\n" + "b(_) :- true.\n";

    SymbolTerm nameTerm = SymbolTerm.create("testReductionLimit");
    JavaObjectTerm inTerm =
        new JavaObjectTerm(new PushbackReader(new StringReader(script), Prolog.PUSHBACK_SIZE));
    if (!env.execute(Prolog.BUILTIN, "consult_stream", nameTerm, inTerm)) {
      throw new CompileException("Cannot consult " + nameTerm);
    }

    exception.expect(ReductionLimitException.class);
    exception.expectMessage("exceeded reduction limit of 1300");
    env.once(
        Prolog.BUILTIN,
        "call",
        new StructureTerm(":", SymbolTerm.create("user"), SymbolTerm.create("loopy")));
  }
}
