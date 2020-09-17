package com.google.gerrit.server.submit;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marker on {@link SubscriptionGraph.Factory} that honors gerrit configuration regarding superproject updates.
 */
@Retention(RUNTIME)
@BindingAnnotation
public @interface ConfiguredSubscriptionGraph {}
