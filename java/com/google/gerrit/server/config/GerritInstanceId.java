package com.google.gerrit.server.config;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Retention;

/**
 * Marker on a {@link String} holding the instance id for this server.
 *
 * <p>Note that the String may be null, if the administrator has not configured the value. Clients
 * must handle such cases explicitly.
 */
@Retention(RUNTIME)
@BindingAnnotation
public @interface GerritInstanceId {}
