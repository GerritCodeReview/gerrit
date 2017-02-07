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

package com.google.gerrit.testutil;

import com.google.gerrit.testutil.log.LogUtil;
import java.util.ArrayList;
import java.util.Iterator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;

/**
 * Testcase capturing associated logs and allowing to assert on them.
 *
 * <p>For a test case SomeNameTest, the log for SomeName gets captured. Assertions on logs run
 * against the coptured log events from this logger. After the tests, the logger are set back to
 * their original settings.
 */
public abstract class LoggingMockingTestCase extends MockingTestCase {
  private String loggerName;
  private LogUtil.LoggerSettings loggerSettings;
  private java.util.Collection<LoggingEvent> loggedEvents;

  /**
   * Assert a logged event with a given string.
   *
   * <p>If such a event is found, it is removed from the captured logs.
   *
   * @param needle The string to look for.
   */
  protected final void assertLogMessageContains(String needle) {
    LoggingEvent hit = null;
    Iterator<LoggingEvent> iter = loggedEvents.iterator();
    while (hit == null && iter.hasNext()) {
      LoggingEvent event = iter.next();
      if (event.getRenderedMessage().contains(needle)) {
        hit = event;
      }
    }
    assertNotNull("Could not find log message containing '" + needle + "'", hit);
    assertTrue(
        "Could not remove log message containing '" + needle + "'", loggedEvents.remove(hit));
  }

  /**
   * Assert a logged event whose throwable contains a given string
   *
   * <p>If such a event is found, it is removed from the captured logs.
   *
   * @param needle The string to look for.
   */
  protected final void assertLogThrowableMessageContains(String needle) {
    LoggingEvent hit = null;
    Iterator<LoggingEvent> iter = loggedEvents.iterator();
    while (hit == null && iter.hasNext()) {
      LoggingEvent event = iter.next();
      if (event.getThrowableInformation().getThrowable().toString().contains(needle)) {
        hit = event;
      }
    }
    assertNotNull("Could not find log message with a Throwable containing '" + needle + "'", hit);
    assertTrue(
        "Could not remove log message with a Throwable containing '" + needle + "'",
        loggedEvents.remove(hit));
  }

  /** Assert that all logged events have been asserted */
  // As the PowerMock runner does not pass through runTest, we inject log
  // verification through @After
  @After
  public final void assertNoUnassertedLogEvents() {
    if (loggedEvents.size() > 0) {
      LoggingEvent event = loggedEvents.iterator().next();
      String msg = "Found untreated logged events. First one is:\n";
      msg += event.getRenderedMessage();
      if (event.getThrowableInformation() != null) {
        msg += "\n" + event.getThrowableInformation().getThrowable();
      }
      fail(msg);
    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    loggedEvents = new ArrayList<>();

    // The logger we're interested is class name without the trailing "Test".
    // While this is not the most general approach it is sufficient for now,
    // and we can improve later to allow tests to specify which loggers are
    // to check.
    loggerName = this.getClass().getCanonicalName();
    loggerName = loggerName.substring(0, loggerName.length() - 4);
    loggerSettings = LogUtil.logToCollection(loggerName, loggedEvents);
  }

  @Override
  protected void runTest() throws Throwable {
    super.runTest();
    // Plain JUnit runner does not pick up @After, so we add it here
    // explicitly. Note, that we cannot put this into tearDown, as failure
    // to verify mocks would bail out and might leave open resources from
    // subclasses open.
    assertNoUnassertedLogEvents();
  }

  @Override
  public void tearDown() throws Exception {
    if (loggerName != null && loggerSettings != null) {
      Logger logger = LogManager.getLogger(loggerName);
      loggerSettings.pushOntoLogger(logger);
    }
    super.tearDown();
  }
}
