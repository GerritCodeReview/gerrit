// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.reviewdb.client;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.CompoundKey;
import com.google.gwtorm.client.StringKey;

/** External tracking id associated with a {@link Change} */
public final class TrackingId {
  public static final int TRACKING_ID_MAX_CHAR = 32;
  public static final int TRACKING_SYSTEM_MAX_CHAR = 10;

  /** External tracking id */
  public static class Id extends StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, length = TrackingId.TRACKING_ID_MAX_CHAR)
    protected String id;

    protected Id() {}

    public Id(String id) {
      this.id = id;
    }

    @Override
    public String get() {
      return id;
    }

    @Override
    protected void set(String newValue) {
      id = newValue;
    }
  }

  /** Name of external tracking system */
  public static class System extends StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, length = TrackingId.TRACKING_SYSTEM_MAX_CHAR)
    protected String system;

    protected System() {}

    public System(String s) {
      this.system = s;
    }

    @Override
    public String get() {
      return system;
    }

    @Override
    protected void set(String newValue) {
      system = newValue;
    }
  }

  public static class Key extends CompoundKey<Change.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected Change.Id changeId;

    @Column(id = 2)
    protected Id trackingKey;

    @Column(id = 3)
    protected System trackingSystem;

    protected Key() {
      changeId = new Change.Id();
      trackingKey = new Id();
      trackingSystem = new System();
    }

    protected Key(Change.Id ch, Id id, System s) {
      changeId = ch;
      trackingKey = id;
      trackingSystem = s;
    }

    @Override
    public Change.Id getParentKey() {
      return changeId;
    }

    public TrackingId.Id getTrackingId() {
      return trackingKey;
    }

    public TrackingId.System getTrackingSystem() {
      return trackingSystem;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {trackingKey, trackingSystem};
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  protected TrackingId() {}

  public TrackingId(Change.Id ch, TrackingId.Id id, TrackingId.System s) {
    key = new Key(ch, id, s);
  }

  public TrackingId(Change.Id ch, String id, String s) {
    key = new Key(ch, new TrackingId.Id(id), new TrackingId.System(s));
  }

  public TrackingId.Key getKey() {
    return key;
  }

  public Change.Id getChangeId() {
    return key.changeId;
  }

  public String getTrackingId() {
    return key.trackingKey.get();
  }

  public String getSystem() {
    return key.trackingSystem.get();
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof TrackingId) {
      final TrackingId tr = (TrackingId) obj;
      return key.equals(tr.key);
    }
    return false;
  }
}
