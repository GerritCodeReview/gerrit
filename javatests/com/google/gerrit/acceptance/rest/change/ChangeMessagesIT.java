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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.TestTimeUtil;
import java.util.Iterator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(ConfigSuite.class)
public class ChangeMessagesIT extends AbstractDaemonTest {
  private String systemTimeZone;

  @Before
  public void setTimeForTesting() {
    systemTimeZone = System.setProperty("user.timezone", "US/Eastern");
    TestTimeUtil.resetWithClockStep(1, SECONDS);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
    System.setProperty("user.timezone", systemTimeZone);
  }

  @Test
  public void messagesNotReturnedByDefault() throws Exception {
    String changeId = createChange().getChangeId();
    postMessage(changeId, "Some nits need to be fixed.");
    ChangeInfo c = info(changeId);
    assertThat(c.messages).isNull();
  }

  @Test
  public void defaultMessage() throws Exception {
    String changeId = createChange().getChangeId();
    ChangeInfo c = get(changeId);
    assertThat(c.messages).isNotNull();
    assertThat(c.messages).hasSize(1);
    assertThat(c.messages.iterator().next().message).isEqualTo("Uploaded patch set 1.");
  }

  @Test
  public void messagesReturnedInChronologicalOrder() throws Exception {
    String changeId = createChange().getChangeId();
    String firstMessage = "Some nits need to be fixed.";
    postMessage(changeId, firstMessage);
    String secondMessage = "I like this feature.";
    postMessage(changeId, secondMessage);
    ChangeInfo c = get(changeId);
    assertThat(c.messages).isNotNull();
    assertThat(c.messages).hasSize(3);
    Iterator<ChangeMessageInfo> it = c.messages.iterator();
    assertThat(it.next().message).isEqualTo("Uploaded patch set 1.");
    assertMessage(firstMessage, it.next().message);
    assertMessage(secondMessage, it.next().message);
  }

  @Test
  public void postMessageWithTag() throws Exception {
    String changeId = createChange().getChangeId();
    String tag = "jenkins";
    String msg = "Message with tag.";
    postMessage(changeId, msg, tag);
    ChangeInfo c = get(changeId);
    assertThat(c.messages).isNotNull();
    assertThat(c.messages).hasSize(2);
    Iterator<ChangeMessageInfo> it = c.messages.iterator();
    assertThat(it.next().message).isEqualTo("Uploaded patch set 1.");
    ChangeMessageInfo actual = it.next();
    assertMessage(msg, actual.message);
    assertThat(actual.tag).isEqualTo(tag);
  }

  private void assertMessage(String expected, String actual) {
    assertThat(actual).isEqualTo("Patch Set 1:\n\n" + expected);
  }

  private void postMessage(String changeId, String msg) throws Exception {
    postMessage(changeId, msg, null);
  }

  private void postMessage(String changeId, String msg, String tag) throws Exception {
    ReviewInput in = new ReviewInput();
    in.message = msg;
    in.tag = tag;
    gApi.changes().id(changeId).current().review(in);
  }
}
