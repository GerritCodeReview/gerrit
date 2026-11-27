// Copyright (C) 2026 The Android Open Source Project
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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.common.RawInputUtil;
import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Test;

public class AttachmentsIT extends AbstractDaemonTest {
  private static final byte[] PNG =
      new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3};

  @Test
  public void disabledByDefault() throws Exception {
    String change = createChange().getChangeId();
    adminRestSession
        .postRaw("/changes/" + change + "/attachments", RawInputUtil.create(PNG, "image/png"))
        .assertForbidden();
  }

  @Test
  @GerritConfig(name = "attachments.enabled", value = "true")
  public void uploadAndRead() throws Exception {
    String change = createChange().getChangeId();
    String id = upload(change, PNG);
    String url = "/changes/" + change + "/attachments/" + id;
    RestResponse download = adminRestSession.get(url);
    download.assertOK();
    assertThat(download.getHeader("Content-Type")).contains("image/png");
    ByteBuffer contents = download.getRawContent();
    byte[] actual = new byte[contents.remaining()];
    contents.get(actual);
    assertThat(actual).isEqualTo(PNG);
    assertThat(upload(change, PNG)).isEqualTo(id);
    adminRestSession.get("/changes/" + change + "/attachments/" + "a".repeat(40)).assertNotFound();
    adminRestSession.get("/changes/" + change + "/attachments/../../meta").assertNotFound();
  }

  @Test
  @GerritConfig(name = "attachments.enabled", value = "true")
  public void anonymousUploadIsRejected() throws Exception {
    String change = createChange().getChangeId();
    anonymousRestSession
        .postRaw("/changes/" + change + "/attachments", RawInputUtil.create(PNG, "image/png"))
        .assertForbidden();
  }

  @Test
  @GerritConfig(name = "attachments.enabled", value = "true")
  public void cannotReadImageOnPrivateChange() throws Exception {
    String change = createChange().getChangeId();
    String id = upload(change, PNG);
    gApi.changes().id(change).setPrivate(true, null);
    anonymousRestSession.get("/changes/" + change + "/attachments/" + id).assertNotFound();
  }

  @Test
  @GerritConfig(name = "attachments.enabled", value = "true")
  public void rejectsNonImages() throws Exception {
    String url = "/changes/" + createChange().getChangeId() + "/attachments";
    adminRestSession
        .postRaw(url, RawInputUtil.create("<svg/>".getBytes(UTF_8), "image/png"))
        .assertBadRequest();
  }

  @Test
  @GerritConfig(name = "attachments.enabled", value = "true")
  public void concurrentUploadsPreserveBothImages() throws Exception {
    String change = createChange().getChangeId();
    CyclicBarrier start = new CyclicBarrier(2);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<String> first = pool.submit(() -> uploadConcurrently(change, (byte) 1, start));
      Future<String> second = pool.submit(() -> uploadConcurrently(change, (byte) 2, start));
      String firstId = first.get();
      String secondId = second.get();
      assertThat(firstId).isNotEqualTo(secondId);
      adminRestSession.get("/changes/" + change + "/attachments/" + firstId).assertOK();
      adminRestSession.get("/changes/" + change + "/attachments/" + secondId).assertOK();
    }
  }

  @Test
  @GerritConfig(name = "attachments.enabled", value = "true")
  public void limitsDistinctImagesPerChange() throws Exception {
    String change = createChange().getChangeId();
    String firstId = null;
    for (int i = 0; i < 20; i++) {
      byte[] image = PNG.clone();
      image[image.length - 1] = (byte) i;
      String id = upload(change, image);
      if (i == 0) firstId = id;
    }
    byte[] extra = PNG.clone();
    extra[extra.length - 1] = 20;
    adminRestSession
        .postRaw("/changes/" + change + "/attachments", RawInputUtil.create(extra, "image/png"))
        .assertBadRequest();
    byte[] first = PNG.clone();
    first[first.length - 1] = 0;
    assertThat(upload(change, first)).isEqualTo(firstId);
  }

  @Test
  @GerritConfig(name = "attachments.enabled", value = "true")
  @GerritConfig(name = "attachments.allowedGroup", value = "Administrators")
  public void groupGateAllowsMembers() throws Exception {
    String change = createChange().getChangeId();
    assertThat(upload(change, PNG)).isNotEmpty();
    userRestSession
        .postRaw("/changes/" + change + "/attachments", RawInputUtil.create(PNG, "image/png"))
        .assertForbidden();
  }

  @Test
  @GerritConfig(name = "attachments.enabled", value = "true")
  @GerritConfig(name = "attachments.allowedGroup", value = "Nonexistent group")
  public void groupGateRejectsNonMembers() throws Exception {
    String url = "/changes/" + createChange().getChangeId() + "/attachments";
    adminRestSession.postRaw(url, RawInputUtil.create(PNG, "image/png")).assertForbidden();
  }

  private String uploadConcurrently(String change, byte lastByte, CyclicBarrier start)
      throws Exception {
    byte[] image = PNG.clone();
    image[image.length - 1] = lastByte;
    start.await();
    RestResponse response =
        server
            .createRestSession(admin)
            .postRaw(
                "/changes/" + change + "/attachments", RawInputUtil.create(image, "image/png"));
    response.assertOK();
    return JsonParser.parseReader(response.getReader()).getAsJsonObject().get("id").getAsString();
  }

  private String upload(String change, byte[] bytes) throws Exception {
    RestResponse response =
        adminRestSession.postRaw(
            "/changes/" + change + "/attachments", RawInputUtil.create(bytes, "image/png"));
    response.assertOK();
    return JsonParser.parseReader(response.getReader()).getAsJsonObject().get("id").getAsString();
  }
}
