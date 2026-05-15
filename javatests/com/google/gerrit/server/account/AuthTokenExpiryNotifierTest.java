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

package com.google.gerrit.server.account;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.Test;

public class AuthTokenExpiryNotifierTest {
  private static final Instant NOW = Instant.now();
  private static final Instant LOWER_BOUND = NOW.minus(1, ChronoUnit.DAYS);

  @Test
  public void shouldNotifyExpiredReturnsTrueWhenTokenExpiredWithinLast24Hours() {
    // Token expired 12 hours ago
    Instant expirationDate = NOW.minus(12, ChronoUnit.HOURS);

    assertThat(AuthTokenExpiryNotifier.shouldNotifyExpired(expirationDate)).isTrue();
  }

  @Test
  public void shouldNotifyExpiredReturnsFalseWhenTokenExpiredMoreThan24HoursAgo() {
    // Token expired 30 hours ago (more than 24 hours)
    Instant expirationDate = NOW.minus(30, ChronoUnit.HOURS);

    assertThat(AuthTokenExpiryNotifier.shouldNotifyExpired(expirationDate)).isFalse();
  }

  @Test
  public void shouldNotifyExpiredReturnsFalseWhenTokenNotYetExpired() {
    // Token expires in the future
    Instant expirationDate = NOW.plus(7, ChronoUnit.DAYS);

    assertThat(AuthTokenExpiryNotifier.shouldNotifyExpired(expirationDate)).isFalse();
  }

  @Test
  public void shouldNotifyExpiredReturnsTrueWhenTokenJustExpired() {
    // Token expired exactly NOW
    Instant expirationDate = NOW;

    assertThat(AuthTokenExpiryNotifier.shouldNotifyExpired(expirationDate)).isTrue();
  }

  @Test
  public void shouldNotifyExpiredReturnsTrueAtBoundary() {
    // Token expired exactly 24 hours ago (at the boundary)
    Instant expirationDate = LOWER_BOUND;

    assertThat(AuthTokenExpiryNotifier.shouldNotifyExpired(expirationDate)).isTrue();
  }
}
