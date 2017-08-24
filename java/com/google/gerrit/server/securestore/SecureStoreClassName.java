package com.google.gerrit.server.securestore;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Retention;

@Retention(RUNTIME)
@BindingAnnotation
public @interface SecureStoreClassName {}
