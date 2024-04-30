package com.google.gerrit.common;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** An annotation used to mark Java entities that have equivalent proto representations. */
@Retention(RUNTIME)
@Target({TYPE})
public @interface ConvertibleToProto {}
