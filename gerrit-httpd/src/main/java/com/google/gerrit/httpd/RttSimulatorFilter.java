// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.httpd;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Random;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

@Singleton
class RttSimulatorFilter implements Filter {
  private static final Logger log = LoggerFactory.getLogger(RttSimulatorFilter.class);

  static class Module extends ServletModule {
    @Override
    protected void configureServlets() {
      filter("/*").through(RttSimulatorFilter.class);
    }
  }

  private final Random rng;
  private final long minRttMs;
  private final long maxRttMs;
  private final long range;

  @Inject
  RttSimulatorFilter(@GerritServerConfig Config cfg) {
    rng = new Random();
    minRttMs = ConfigUtil.getTimeUnit(cfg,
        "developer", "rtt", "min",
        200, MILLISECONDS);
    maxRttMs = ConfigUtil.getTimeUnit(cfg,
        "developer", "rtt", "max",
        250, MILLISECONDS);
    range = Math.max(0, maxRttMs - minRttMs);
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse res,
      FilterChain chain) throws IOException, ServletException {
    long delay = minRttMs + jitter();
    log.warn(String.format("simulating RTT of %d ms", delay));
    try {
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      log.warn("woke up early", e);
    }
    chain.doFilter(req, res);
  }

  private long jitter() {
    return (long) (rng.nextDouble() * range);
  }

  @Override
  public void init(FilterConfig cfg) throws ServletException {
  }

  @Override
  public void destroy() {
  }
}
