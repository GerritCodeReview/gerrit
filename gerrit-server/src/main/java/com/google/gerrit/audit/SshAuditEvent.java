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
package com.google.gerrit.audit;

import com.google.common.collect.Multimap;
import com.google.gerrit.server.CurrentUser;

public class SshAuditEvent extends AuditEvent {

  public SshAuditEvent(String sessionId, CurrentUser who, String what,
      long when, Multimap<String, ?> params, Object result) {
    super(sessionId, who, what, when, params, result);
  }
}
