package com.google.gerrit.acceptance.api.configs;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.common.CacheInfo;

import org.junit.Test;

@NoHttpd
public class ConfigIT extends AbstractDaemonTest {
  @Test
  public void caches() {
    Iterables.any(gApi.configs().caches().list(),
        new Predicate<CacheInfo>() {
          public boolean apply(CacheInfo cache) {
            return cache.name.equals("accounts");
          }
        });
  }
}
