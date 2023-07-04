package com.google.gerrit.proto;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(value = RetentionPolicy.RUNTIME)
public @interface FieldDoc {

  int protoTag();

  boolean optional() default false;
}
