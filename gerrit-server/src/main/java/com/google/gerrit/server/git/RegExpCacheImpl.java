package com.google.gerrit.server.git;

import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.project.RegExpCache;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

@Singleton
public class RegExpCacheImpl implements RegExpCache {
  private static final String CACHE_NAME = "regexp";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<String, String>> type =
            new TypeLiteral<Cache<String, String>>() {};
        core(type, CACHE_NAME).populateWith(Loader.class);

        bind(RegExpCache.class).to(RegExpCacheImpl.class);
      }
    };
  }

  private final Cache<String, String> cache;

  @Inject
  public RegExpCacheImpl(@Named(CACHE_NAME) final Cache<String, String> cache) {
    this.cache = cache;
  }


  @Override
  public String get(String pattern) {
    return cache.get(pattern);
  }

  static class Loader extends EntryCreator<String, String> {
    @Override
    public String createEntry(String pattern) {
      return RefControl.shortestExampleCalc(pattern);
    }
  }
}
