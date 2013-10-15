// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.util.TimeUtil;

public class AuditEvent {

  public static final String UNKNOWN_SESSION_ID = "000000000000000000000000000";
  protected static final Multimap<String, ?> EMPTY_PARAMS = HashMultimap.create();

  public final String sessionId;
  public final CurrentUser who;
  public final long when;
  public final String what;
  public final Multimap<String, ?> params;
  public final Object result;
  public final long timeAtStart;
  public final long elapsed;
  public final UUID uuid;

  public static class UUID {

    protected final String uuid;

    protected UUID() {
      uuid = String.format("audit:%s", java.util.UUID.randomUUID().toString());
    }

    public UUID(final String n) {
      uuid = n;
    }

    public String get() {
      return uuid;
    }

    @Override
    public int hashCode() {
      return uuid.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (!(obj instanceof UUID)) {
        return false;
      }

      return uuid.equals(((UUID) obj).uuid);
    }
  }

  /**
   * Creates a new audit event with results
   *
   * @param sessionId session id the event belongs to
   * @param who principal that has generated the event
   * @param what object of the event
   * @param when time-stamp of when the event started
   * @param params parameters of the event
   * @param result result of the event
   */
  public AuditEvent(String sessionId, CurrentUser who, String what, long when,
      Multimap<String, ?> params, Object result) {
    Preconditions.checkNotNull(what, "what is a mandatory not null param !");

    this.sessionId = Objects.firstNonNull(sessionId, UNKNOWN_SESSION_ID);
    this.who = who;
    this.what = what;
    this.when = when;
    this.timeAtStart = this.when;
    this.params = Objects.firstNonNull(params, EMPTY_PARAMS);
    this.uuid = new UUID();
    this.result = result;
    this.elapsed = TimeUtil.nowMs() - timeAtStart;
  }

  @Override
  public int hashCode() {
    return uuid.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;

    AuditEvent other = (AuditEvent) obj;
    return this.uuid.equals(other.uuid);
  }

  @Override
  public String toString() {
    return String.format("AuditEvent UUID:%s, SID:%s, TS:%d, who:%s, what:%s",
        uuid.get(), sessionId, when, who, what);
  }
}
