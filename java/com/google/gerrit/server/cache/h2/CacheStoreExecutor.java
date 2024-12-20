package com.google.gerrit.server.cache.h2;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Retention;

@Retention(RUNTIME)
@BindingAnnotation
public @interface CacheStoreExecutor {}
