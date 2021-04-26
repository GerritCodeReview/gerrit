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

import static com.google.gerrit.acceptance.WaitUtil.waitUntil;

import com.google.common.base.Splitter;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import java.io.IOException;
import java.io.Reader;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
@UseSsh
@Sandboxed
public class StreamEventsIT extends AbstractDaemonTest {
  private static final Duration MAX_DURATION_FOR_RECEIVING_EVENTS = Duration.ofSeconds(2);
  private static final String TEST_REVIEW_COMMENT = "any comment";
  private StringBuilder eventsOutput = new StringBuilder();
  private Reader streamEventsReader;

  @Before
  public void setup() throws Exception {
    streamEventsReader = adminSshSession.execAndReturnReader("gerrit stream-events");
  }

  @After
  public void closeStreamEvents() throws IOException {
    streamEventsReader.close();
  }

  @Test
  public void commentOnChangeShowsUpInStreamEvents() throws Exception {
    reviewChange(new ReviewInput().message(TEST_REVIEW_COMMENT));
    waitForEvent(() -> pollEventsContaining(TEST_REVIEW_COMMENT).size() == 1);
  }

  private void waitForEvent(Supplier<Boolean> waitCondition) throws InterruptedException {
    waitUntil(() -> waitCondition.get(), MAX_DURATION_FOR_RECEIVING_EVENTS);
  }

  private void reviewChange(ReviewInput reviewInput) throws Exception {
    ChangeApi changeApi = gApi.changes().id(createChange().getChange().getId().get());
    changeApi.current().review(reviewInput);
  }

  private List<String> pollEventsContaining(String reviewComment) {
    try {
      char[] cbuf = new char[2048];
      while (streamEventsReader.ready()) {
        streamEventsReader.read(cbuf);
        eventsOutput.append(cbuf);
      }
      return StreamSupport.stream(
              Splitter.on('\n').trimResults().split(eventsOutput.toString()).spliterator(), false)
          .filter(event -> event.contains(reviewComment))
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
