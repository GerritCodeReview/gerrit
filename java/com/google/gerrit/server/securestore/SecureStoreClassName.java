package com.google.gerrit.server.securestore;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import javax.inject.Qualifier;

@Retention(RUNTIME)
@Qualifier
public @interface SecureStoreClassName {}
