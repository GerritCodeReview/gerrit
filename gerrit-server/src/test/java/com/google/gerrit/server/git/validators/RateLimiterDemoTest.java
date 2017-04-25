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

package com.google.gerrit.server.git.validators;

import com.google.common.util.concurrent.RateLimiter;
import junit.framework.TestCase;
import org.junit.Test;

/**
 * TODO remove this class before submitting this change
 *
 * <p>This is no test but a demo of the difference between default RateLimiter and one with a higher
 * maxBurstSeconds which can only be used by accessing the non-public constructor of the hidden
 * implementation class using reflection.
 *
 * <p>RateLimiter allows to store unused permits earned by not consuming permits for a while. These
 * stored permits can be used to send bursts of requests exceeding the average rate until these
 * stored permits are consumed. If the permitted rate is smaller than 1.0 the default implementation
 * doesn't allow for any bursts since it hard-codes the maximum time which can be used to collect
 * stored permits to 1 seconds.
 */
public class RateLimiterDemoTest extends TestCase {
  private RateLimiter limiter;
  private static double permitsPerSecond = 0.5;

  @Test
  public void testBurstyRateLimiter() throws Exception {
    int maxBurstSeconds = 10;
    limiter =
        RateLimitUploadListener.createSmoothBurstyRateLimiter(permitsPerSecond, maxBurstSeconds);
    explore(permitsPerSecond, maxBurstSeconds);
  }

  @Test
  public void testRateLimiter() throws Exception {
    limiter = RateLimiter.create(permitsPerSecond);
    explore(permitsPerSecond, 1);
  }

  private void explore(double permitsPerSecond, int maxBurstSeconds) throws InterruptedException {
    int interval = (int) Math.round(1 / permitsPerSecond);
    System.out.println("limiter: " + limiter.getClass().getName());
    System.out.println(
        "permitsPerSecond="
            + permitsPerSecond
            + ", maxBurstSeconds="
            + maxBurstSeconds
            + ", maxBurstSize="
            + permitsPerSecond * maxBurstSeconds);
    do {
      measure(interval);
      interval = interval * 2;
    } while (interval < 8 * maxBurstSeconds);
  }

  private void measure(int waitSeconds) throws InterruptedException {
    Thread.sleep(waitSeconds * 1000);
    int i = 0;
    while (limiter.tryAcquire()) {
      i++;
    }
    System.out.println("wait=" + waitSeconds + "s, permits=" + i);
  }
}
