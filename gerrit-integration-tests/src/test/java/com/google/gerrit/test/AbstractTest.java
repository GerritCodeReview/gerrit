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

package com.google.gerrit.test;

import com.google.gerrit.test.util.LogFile;

import com.jcraft.jsch.JSchException;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTest {

  private static final Logger log = LoggerFactory.getLogger(AbstractTest.class);

  @Rule
  public final TestName name = new TestName();

  private static boolean loggerInitialized = false;

  @BeforeClass
  public static void initLog() throws JSchException {
    if (!loggerInitialized) {
      LogFile.start();
      loggerInitialized = true;
    }
  }

  @Before
  public void logTestName() {
    log.info("test = " + getClass().getName() + "#" + name.getMethodName());
  }
}