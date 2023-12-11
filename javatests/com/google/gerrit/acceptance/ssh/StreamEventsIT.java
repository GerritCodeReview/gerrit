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
import static com.google.gerrit.entities.Patch.PATCHSET_LEVEL;

import com.google.common.base.Splitter;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.server.query.change.ChangeData;
import java.io.IOException;
import java.io.Reader;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
@UseSsh
@Sandboxed
public class StreamEventsIT extends AbstractDaemonTest {
  private static final Duration MAX_DURATION_FOR_RECEIVING_EVENTS = Duration.ofSeconds(2);
  private static final String TEST_REVIEW_COMMENT = "any comment";
  private static final String TEST_REVIEW_DRAFT_COMMENT = "any draft comment";
  private Reader streamEventsReader;
  private ChangeData change;

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
    waitForEvent(() -> pollEventsContaining("comment-added", TEST_REVIEW_COMMENT).size() == 1);
  }

  @Test
  public void publishedDraftPatchSetLevelCommentShowsUpInStreamEvents() throws Exception {
    change = createChange().getChange();

    String firstDraftComment = String.format("%s 1", TEST_REVIEW_DRAFT_COMMENT);
    String secondDraftComment = String.format("%s 2", TEST_REVIEW_DRAFT_COMMENT);

    draftReviewChange(PATCHSET_LEVEL, firstDraftComment);
    draftReviewChange(PATCHSET_LEVEL, secondDraftComment);
    publishDraftReviews();

    waitForEvent(
        () ->
            pollEventsContaining("comment-added", firstDraftComment, secondDraftComment).size()
                == 1);
  }

  @Test
  public void batchRefsUpdatedShowSeparatelyInStreamEvents() throws Exception {
    String refName = createChange().getChange().currentPatchSet().refName();
    AtomicInteger numberOfFoundEvents = new AtomicInteger(0);
    waitForEvent(
        () ->
            numberOfFoundEvents.addAndGet(
                    pollEventsContaining(
                            "ref-updated", refName.substring(0, refName.lastIndexOf('/')))
                        .size())
                == 2);
  }

  @Test
  @GerritConfig(name = "event.stream-events.enableBatchRefUpdatedEvents", value = "true")
  public void batchRefsUpdatedShowsInStreamEvents() throws Exception {
    ChangeData change = createChange().getChange();
    String patchsetRefName = change.currentPatchSet().refName();
    String metaRefName = RefNames.changeMetaRef(change.getId());
    waitForEvent(
        () -> pollEventsContaining("batch-ref-updated", patchsetRefName, metaRefName).size() == 1);
  }

  @Test
  @GerritConfig(name = "event.stream-events.enableRefUpdatedEvents", value = "true")
  @GerritConfig(name = "event.stream-events.enableBatchRefUpdatedEvents", value = "false")
  @GerritConfig(name = "event.stream-events.enableDraftCommentEvents", value = "true")
  public void draftCommentRefsShowInStreamEventsWithRefUpdated() throws Exception {
    change = createChange().getChange();

    draftReviewChange(PATCHSET_LEVEL, String.format("%s 1", TEST_REVIEW_DRAFT_COMMENT));

    waitForEvent(() -> pollEventsContaining("ref-updated", "refs/draft-comments/").size() == 1);
  }

  @Test(expected = InterruptedException.class)
  @GerritConfig(name = "event.stream-events.enableRefUpdatedEvents", value = "true")
  @GerritConfig(name = "event.stream-events.enableBatchRefUpdatedEvents", value = "false")
  @GerritConfig(name = "event.stream-events.enableDraftCommentEvents", value = "false")
  public void draftCommentRefsDontShowInStreamEventsWithRefUpdated() throws Exception {
    change = createChange().getChange();

    draftReviewChange(PATCHSET_LEVEL, String.format("%s 1", TEST_REVIEW_DRAFT_COMMENT));

    waitForEvent(() -> pollEventsContaining("ref-updated", "refs/draft-comments/").size() == 1);
  }

  @Test
  @GerritConfig(name = "event.stream-events.enableBatchRefUpdatedEvents", value = "true")
  @GerritConfig(name = "event.stream-events.enableRefUpdatedEvents", value = "false")
  @GerritConfig(name = "event.stream-events.enableDraftCommentEvents", value = "true")
  public void draftCommentRefsShowInStreamEventsWithBatchRefUpdated() throws Exception {
    change = createChange().getChange();

    draftReviewChange(PATCHSET_LEVEL, String.format("%s 1", TEST_REVIEW_DRAFT_COMMENT));

    waitForEvent(
        () -> pollEventsContaining("batch-ref-updated", "refs/draft-comments/").size() == 1);
  }

  @Test(expected = InterruptedException.class)
  @GerritConfig(name = "event.stream-events.enableBatchRefUpdatedEvents", value = "true")
  @GerritConfig(name = "event.stream-events.enableRefUpdatedEvents", value = "false")
  @GerritConfig(name = "event.stream-events.enableDraftCommentEvents", value = "false")
  public void draftCommentRefsDontShowInStreamEventsWithBatchRefUpdated() throws Exception {
    change = createChange().getChange();

    draftReviewChange(PATCHSET_LEVEL, String.format("%s 1", TEST_REVIEW_DRAFT_COMMENT));

    waitForEvent(
        () -> pollEventsContaining("batch-ref-updated", "refs/draft-comments/").size() == 1);
  }

  @Test
  @GerritConfig(name = "event.stream-events.enableRefUpdatedEvents", value = "true")
  @GerritConfig(name = "event.stream-events.enableBatchRefUpdatedEvents", value = "false")
  @GerritConfig(name = "event.stream-events.enableDraftCommentEvents", value = "true")
  public void draftCommentRefsDeletionShowInStreamEventsUponPublishing() throws Exception {
    change = createChange().getChange();

    draftReviewChange(PATCHSET_LEVEL, String.format("%s 1", TEST_REVIEW_DRAFT_COMMENT));
    publishDraftReviews();

    waitForEvent(
        () ->
            pollEventsContaining(
                        "ref-updated",
                        "refs/draft-comments/",
                        "\"newRev\":\"" + ObjectId.zeroId().name() + "\"")
                    .size()
                == 1);
  }

  private void waitForEvent(Supplier<Boolean> waitCondition) throws InterruptedException {
    waitUntil(() -> waitCondition.get(), MAX_DURATION_FOR_RECEIVING_EVENTS);
  }

  private void reviewChange(ReviewInput reviewInput) throws Exception {
    ChangeApi changeApi = gApi.changes().id(createChange().getChange().getId().get());
    changeApi.current().review(reviewInput);
  }

  private void draftReviewChange(String path, String reviewMessage) throws Exception {
    DraftInput draftInput = new DraftInput();
    draftInput.message = reviewMessage;
    draftInput.path = path;
    ChangeApi changeApi = gApi.changes().id(change.getId().get());
    changeApi.current().createDraft(draftInput).get();
  }

  private void publishDraftReviews() throws Exception {
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.tag = "new_tag";
    reviewInput.drafts = DraftHandling.PUBLISH;
    gApi.changes().id(change.getId().get()).current().review(reviewInput);
  }

  private List<String> pollEventsContaining(String eventType, String... expectedContent) {
    try {
      char[] cbuf = new char[2048];
      StringBuilder eventsOutput = new StringBuilder();
      while (streamEventsReader.ready()) {
        int read = streamEventsReader.read(cbuf);
        eventsOutput.append(Arrays.copyOfRange(cbuf, 0, read));
      }
      return StreamSupport.stream(
              Splitter.on('\n').trimResults().split(eventsOutput.toString()).spliterator(), false)
          .filter(
              event ->
                  event.contains(String.format("\"type\":\"%s\"", eventType))
                      && Stream.of(expectedContent).allMatch(event::contains))
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
