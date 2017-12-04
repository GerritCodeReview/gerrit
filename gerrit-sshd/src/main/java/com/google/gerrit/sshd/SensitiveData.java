package com.google.gerrit.sshd;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation tagged on a field of a ssh command to indicate the value must be hidden from log.
 */
@Target({FIELD})
@Retention(RUNTIME)
public @interface SensitiveData {
}
