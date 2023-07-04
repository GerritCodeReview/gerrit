package com.google.gerrit.proto;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(value = RetentionPolicy.RUNTIME)
public @interface ProtoField {
  int protoTag();
}
