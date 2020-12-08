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
import com.google.gerrit.server.query.change.ChangeData;
import java.io.IOException;
import java.io.Reader;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.After;
import org.junit.Test;

@NoHttpd
@UseSsh
@Sandboxed
@SuppressWarnings("unused")
public class StreamEventsIT extends AbstractDaemonTest {
  private static final Duration MAX_DURATION_FOR_RECEIVING_EVENTS = Duration.ofSeconds(10);
  private static final String TEST_REVIEW_COMMENT = "any comment";
  private Reader streamEventsReader;
  private StringBuilder eventsOutput;

  @Override
  protected void initSsh() throws Exception {
    super.initSsh();
    streamEventsReader = adminSshSession.execAndReturnReader("gerrit stream-events");
    eventsOutput = new StringBuilder();
  }

  @After
  public void closeStreamEvents() throws IOException {
    streamEventsReader.close();
  }

  @Test
  public void commentOnChangeShowsUpInStreamEvents() throws Exception {
    ChangeData change = createChange().getChange();
    ChangeApi changeApi = gApi.changes().id(change.getId().get());
    changeApi.current().review(new ReviewInput().message(TEST_REVIEW_COMMENT));

    waitForEvent(() -> readEventsContaining(TEST_REVIEW_COMMENT).size() == 1);
  }

  private void waitForEvent(Supplier<Boolean> waitCondition) throws InterruptedException {
    waitUntil(() -> waitCondition.get(), MAX_DURATION_FOR_RECEIVING_EVENTS);
  }

  private List<String> readEventsContaining(String reviewComment) {
    try {
      char[] cbuf = new char[1];
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
