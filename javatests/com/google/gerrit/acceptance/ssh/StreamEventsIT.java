// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;

@NoHttpd
@UseSsh
@Sandboxed
@SuppressWarnings("unused")
public class StreamEventsIT extends AbstractDaemonTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final long TEST_TIMEOUT_SEC = 10;
  private static final String TEST_REVIEW_COMMENT = "any comment";
  private BufferedReader streamEventsReader = null;

  @Override
  protected void initSsh() throws Exception {
    super.initSsh();
    streamEventsReader = adminSshSession.execAndReturnReader("gerrit stream-events");
  }

  @After
  public void closeStreamEvents() throws IOException {
    streamEventsReader.close();
  }

  @Test
  public void commentOnPatchSetShowsUpInStreamEvents() throws Exception {
    reviewChangeAndAbandon(new ReviewInput().patchSetLevelComment(TEST_REVIEW_COMMENT));
    assertThat(getEventsContaining(TEST_REVIEW_COMMENT)).hasSize(1);
  }

  @Test
  public void commentOnChangeShowsUpInStreamEvents() throws Exception {
    reviewChangeAndAbandon(new ReviewInput().message(TEST_REVIEW_COMMENT));
    assertThat(getEventsContaining(TEST_REVIEW_COMMENT)).hasSize(1);
  }

  private void reviewChangeAndAbandon(ReviewInput reviewInput) throws Exception {
    ChangeApi changeApi = gApi.changes().id(createChange().getChange().getId().get());
    changeApi.current().review(reviewInput);
    changeApi.abandon(); // Just for triggering one more stream event after the review() call
  }

  private List<String> getEventsContaining(String reviewComment) throws IOException {
    List<String> events = new ArrayList<>();
    while (streamEventsReader.ready()) {
      String event = streamEventsReader.readLine();
      if (event.contains(reviewComment)) {
        events.add(event);
      }
    }
    return events;
  }
}
