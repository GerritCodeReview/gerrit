// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CommentTimestampAdapterTest extends GerritBaseTests {
  /** Arbitrary time outside of a DST transition, as an ISO instant. */
  private static final String NON_DST_STR = "2017-02-07T10:20:30.123Z";

  /** Arbitrary time outside of a DST transition, as a reasonable Java 8 representation. */
  private static final ZonedDateTime NON_DST = ZonedDateTime.parse(NON_DST_STR);

  /** {@link #NON_DST_STR} truncated to seconds. */
  private static final String NON_DST_STR_TRUNC = "2017-02-07T10:20:30Z";

  /** Arbitrary time outside of a DST transition, as an unreasonable Timestamp representation. */
  private static final Timestamp NON_DST_TS = Timestamp.from(NON_DST.toInstant());

  /** {@link #NON_DST_TS} truncated to seconds. */
  private static final Timestamp NON_DST_TS_TRUNC =
      Timestamp.from(ZonedDateTime.parse(NON_DST_STR_TRUNC).toInstant());

  /**
   * Real live ms since epoch timestamp of a comment that was posted during the PDT to PST
   * transition in November 2013.
   */
  private static final long MID_DST_MS = 1383466224175L;

  /**
   * Ambiguous string representation of {@link #MID_DST_MS} that was actually stored in NoteDb for
   * this comment.
   */
  private static final String MID_DST_STR = "Nov 3, 2013 1:10:24 AM";

  private TimeZone systemTimeZone;
  private Gson legacyGson;
  private Gson gson;

  @Before
  public void setUp() {
    systemTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));

    // Match ChangeNoteUtil#gson as of 4e1f02db913d91f2988f559048e513e6093a1bce
    legacyGson = new GsonBuilder().setPrettyPrinting().create();
    gson = ChangeNoteJson.newGson();
  }

  @After
  public void tearDown() {
    TimeZone.setDefault(systemTimeZone);
  }

  @Test
  public void legacyGsonBehavesAsExpectedDuringDstTransition() {
    long oneHourMs = TimeUnit.HOURS.toMillis(1);

    String beforeJson = "\"Nov 3, 2013 12:10:24 AM\"";
    Timestamp beforeTs = new Timestamp(MID_DST_MS - oneHourMs);
    assertThat(legacyGson.toJson(beforeTs)).isEqualTo(beforeJson);

    String ambiguousJson = '"' + MID_DST_STR + '"';
    Timestamp duringTs = new Timestamp(MID_DST_MS);
    assertThat(legacyGson.toJson(duringTs)).isEqualTo(ambiguousJson);

    Timestamp afterTs = new Timestamp(MID_DST_MS + oneHourMs);
    assertThat(legacyGson.toJson(afterTs)).isEqualTo(ambiguousJson);

    Timestamp beforeTsTruncated = new Timestamp(beforeTs.getTime() / 1000 * 1000);
    assertThat(legacyGson.fromJson(beforeJson, Timestamp.class)).isEqualTo(beforeTsTruncated);

    // Gson just picks one, and it happens to be the one after the PST transition.
    Timestamp afterTsTruncated = new Timestamp(afterTs.getTime() / 1000 * 1000);
    assertThat(legacyGson.fromJson(ambiguousJson, Timestamp.class)).isEqualTo(afterTsTruncated);
  }

  @Test
  public void legacyAdapterViaZonedDateTime() {
    assertThat(legacyGson.toJson(NON_DST_TS)).isEqualTo("\"Feb 7, 2017 2:20:30 AM\"");
  }

  @Test
  public void legacyAdapterCanParseOutputOfNewAdapter() {
    String instantJson = gson.toJson(NON_DST_TS);
    assertThat(instantJson).isEqualTo('"' + NON_DST_STR_TRUNC + '"');
    Timestamp result = legacyGson.fromJson(instantJson, Timestamp.class);
    assertThat(result).isEqualTo(NON_DST_TS_TRUNC);
  }

  @Test
  public void newAdapterCanParseOutputOfLegacyAdapter() {
    String legacyJson = legacyGson.toJson(NON_DST_TS);
    assertThat(legacyJson).isEqualTo("\"Feb 7, 2017 2:20:30 AM\"");
    assertThat(gson.fromJson(legacyJson, Timestamp.class))
        .isEqualTo(new Timestamp(NON_DST_TS.getTime() / 1000 * 1000));
  }

  @Test
  public void newAdapterDisagreesWithLegacyAdapterDuringDstTransition() {
    String duringJson = legacyGson.toJson(new Timestamp(MID_DST_MS));
    Timestamp duringTs = legacyGson.fromJson(duringJson, Timestamp.class);

    // This is unfortunate, but it's just documenting the current behavior, there is no real good
    // solution here. The goal is that all these changes will be rebuilt with proper UTC instant
    // strings shortly after the new adapter is live.
    Timestamp newDuringTs = gson.fromJson(duringJson, Timestamp.class);
    assertThat(newDuringTs.toString()).isEqualTo(duringTs.toString());
    assertThat(newDuringTs).isNotEqualTo(duringTs);
  }

  @Test
  public void newAdapterRoundTrip() {
    String json = gson.toJson(NON_DST_TS);
    // Round-trip lossily truncates ms, but that's ok.
    assertThat(json).isEqualTo('"' + NON_DST_STR_TRUNC + '"');
    assertThat(gson.fromJson(json, Timestamp.class)).isEqualTo(NON_DST_TS_TRUNC);
  }

  @Test
  public void nullSafety() {
    assertThat(gson.toJson(null, Timestamp.class)).isEqualTo("null");
    assertThat(gson.fromJson("null", Timestamp.class)).isNull();
  }

  @Test
  public void newAdapterRoundTripOfWholeComment() {
    Comment c =
        new Comment(
            new Comment.Key("uuid", "filename", 1),
            Account.id(100),
            NON_DST_TS,
            (short) 0,
            "message",
            "serverId",
            false);
    c.lineNbr = 1;
    c.revId = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

    String json = gson.toJson(c);
    assertThat(json).contains("\"writtenOn\": \"" + NON_DST_STR_TRUNC + "\",");

    Comment result = gson.fromJson(json, Comment.class);
    // Round-trip lossily truncates ms, but that's ok.
    assertThat(result.writtenOn).isEqualTo(NON_DST_TS_TRUNC);
    result.writtenOn = NON_DST_TS;
    assertThat(result).isEqualTo(c);
  }
}
