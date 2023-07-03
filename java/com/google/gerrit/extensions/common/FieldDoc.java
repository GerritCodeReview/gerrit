package com.google.gerrit.extensions.common;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(value = RetentionPolicy.RUNTIME)
public @interface FieldDoc {
  String doc();

  int protoTag();

  boolean optional() default false;
}
