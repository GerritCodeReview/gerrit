package com.google.gerrit.server.project;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Retention;

@BindingAnnotation
@Retention(RUNTIME)
public @interface SubmitRequirementExecutor {}
