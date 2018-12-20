// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.mail.EmailHeader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Data container for entities that go into the final email message. */
class OutgoingEmailMessage {
  private final Map<String, EmailHeader> headers = new LinkedHashMap<>();
  private final List<SoyTemplate> bodyParts = new ArrayList<>();
  private final Map<String, Object> soyContext = new HashMap<>();
  private final Map<String, Object> soyContextEmailData = new HashMap<>();

  OutgoingEmailMessage() {
    soyContext.put("email", soyContextEmailData);
  }

  void append(SoyTemplate template) {
    bodyParts.add(template);
  }

  boolean hasHeader(String name) {
    return headers.containsKey(name);
  }

  void removeHeader(String name) {
    headers.remove(name);
  }

  void addHeader(String name, EmailHeader header) {
    headers.put(name, header);
  }

  EmailHeader header(String name) {
    return headers.get(name);
  }

  void fillVariable(String key, Object value) {
    soyContext.put(key, value);
  }

  void fillEmailVariable(String key, Object value) {
    soyContextEmailData.put(key, value);
  }

  ImmutableMap<String, Object> soyContext() {
    return ImmutableMap.copyOf(soyContext);
  }

  ImmutableList<SoyTemplate> bodyParts() {
    return ImmutableList.copyOf(bodyParts);
  }

  ImmutableMap<String, EmailHeader> headers() {
    return ImmutableMap.copyOf(headers);
  }
}
