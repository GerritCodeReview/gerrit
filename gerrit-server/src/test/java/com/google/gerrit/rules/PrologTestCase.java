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

import com.google.inject.Guice;
import com.google.inject.Module;

import com.googlecode.prolog_cafe.compiler.CompileException;
import com.googlecode.prolog_cafe.lang.JavaObjectTerm;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;
import com.googlecode.prolog_cafe.lang.VariableTerm;

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/** Base class for any tests written in Prolog. */
public abstract class PrologTestCase extends TestCase {
  private static final SymbolTerm test_1 = SymbolTerm.intern("test", 1);

  private String pkg;
  private boolean hasSetup;
  private boolean hasTeardown;
  private List<Term> tests;
  protected PrologEnvironment env;

  protected void load(String pkg, String prologResource, Module... modules)
      throws CompileException, IOException {
    ArrayList<Module> moduleList = new ArrayList<Module>();
    moduleList.add(new PrologModule());
    moduleList.addAll(Arrays.asList(modules));

    PrologEnvironment.Factory factory =
        Guice.createInjector(moduleList).getInstance(
            PrologEnvironment.Factory.class);
    env = factory.create(getClass().getClassLoader());
    env.setMaxDatabaseSize(16 * 1024);
    env.setEnabled(Prolog.Feature.IO, true);

    consult(getClass(), prologResource);

    this.pkg = pkg;
    hasSetup = has("setup");
    hasTeardown = has("teardown");

    StructureTerm head = new StructureTerm(":",
        SymbolTerm.intern(pkg),
        new StructureTerm(test_1, new VariableTerm()));

    tests = new ArrayList<Term>();
    for (Term[] pair : env.all(Prolog.BUILTIN, "clause", head, new VariableTerm())) {
      tests.add(pair[0]);
    }
    assertTrue("has tests", tests.size() > 0);
  }

  protected void consult(Class<?> clazz, String prologResource)
      throws CompileException, IOException {
    InputStream in = clazz.getResourceAsStream(prologResource);
    if (in == null) {
      throw new FileNotFoundException(prologResource);
    }
    try {
      SymbolTerm pathTerm = SymbolTerm.create(prologResource);
      JavaObjectTerm inTerm =
          new JavaObjectTerm(new PushbackReader(new BufferedReader(
              new InputStreamReader(in, "UTF-8")), Prolog.PUSHBACK_SIZE));
      if (!env.execute(Prolog.BUILTIN, "consult_stream", pathTerm, inTerm)) {
        throw new CompileException("Cannot consult " + prologResource);
      }
    } finally {
      in.close();
    }
  }

  private boolean has(String name) {
    StructureTerm head = SymbolTerm.create(pkg, name, 0);
    return env.execute(Prolog.BUILTIN, "clause", head, new VariableTerm());
  }

  public void testRunPrologTestCases() {
    int errors = 0;
    long start = System.currentTimeMillis();

    for (Term test : tests) {
      System.out.format("Prolog %-60s ...", removePackage(test));
      System.out.flush();

      if (hasSetup) {
        call("setup");
      }

      List<Term> all = env.all(Prolog.BUILTIN, "call", test);

      if (hasTeardown) {
        call("teardown");
      }

      System.out.println(all.size() == 1 ? "OK" : "FAIL");

      if (all.size() > 0 && !test.equals(all.get(0))) {
        for (Term t : all) {
          Term head = ((StructureTerm) removePackage(t)).args()[0];
          Term[] args = ((StructureTerm) head).args();
          System.out.print("  Result: ");
          for (int i = 0; i < args.length; i++) {
            if (0 < i) {
              System.out.print(", ");
            }
            System.out.print(args[i]);
          }
          System.out.println();
        }
        System.out.println();
      }

      if (all.size() != 1) {
       errors++;
      }
    }

    long end = System.currentTimeMillis();
    System.out.println("-------------------------------");
    System.out.format("Prolog tests: %d, Failures: %d, Time elapsed %.3f sec",
        tests.size(), errors, (end - start) / 1000.0);
    System.out.println();

    assertEquals("No Errors", 0, errors);
  }

  private void call(String name) {
    StructureTerm head = SymbolTerm.create(pkg, name, 0);
    if (!env.execute(Prolog.BUILTIN, "call", head)) {
      fail("Cannot invoke " + pkg + ":" + name);
    }
  }

  private Term removePackage(Term test) {
    Term name = test;
    if (name.isStructure() && ":".equals(((StructureTerm) name).name())) {
      name = ((StructureTerm) name).args()[1];
    }
    return name;
  }
}
