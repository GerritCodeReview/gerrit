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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.proto.Api.AccessCheckInfo;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import java.util.stream.Collectors;
import org.apache.http.message.BasicHeader;
import org.junit.Test;

public class GsonProtoSerializerIT extends AbstractDaemonTest {
  private static BasicHeader ACCEPT_STAR_HEADER = new BasicHeader("Accept", "*/*");
  private static final Gson gson = new Gson();

  /** We ignore the first five characters of the Http entity response ")]}'\n" */
  private static final int JSON_BEGIN = 5;

  @Test
  public void jsonAccessCheckInfo() throws Exception {
    String endpoint =
        String.format("/projects/%s/check.access?pp=1&account=%s", allProjects, user.id());
    RestResponse response = adminRestSession.getWithHeader(endpoint, ACCEPT_STAR_HEADER);
    JsonObject json =
        gson.fromJson(response.getEntityContent().substring(JSON_BEGIN), JsonObject.class);
    assertJsonContainsFieldsOf(json, AccessCheckInfo.getDescriptor());
  }

  private void assertJsonContainsFieldsOf(JsonObject json, Descriptor protoDescriptor) {
    assertThat(json.keySet())
        .containsExactlyElementsIn(
            protoDescriptor.getFields().stream()
                .map(FieldDescriptor::getName)
                .collect(Collectors.toList()));
  }
}
