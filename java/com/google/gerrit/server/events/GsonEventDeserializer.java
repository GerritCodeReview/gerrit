package com.google.gerrit.server.events;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE_PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@BindingAnnotation
@Retention(RUNTIME)
@Target({TYPE_PARAMETER, FIELD})
public @interface GsonEventDeserializer {}
