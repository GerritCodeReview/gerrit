package com.google.gerrit.acceptance;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.Retention;

@Retention(RUNTIME)
@BindingAnnotation
public @interface HttpListenAddress {
}
