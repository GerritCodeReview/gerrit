package com.google.gerrit.sshd;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation tagged on a concrete Command that will run indefinitely.
 * <p>
 * Currently this annotation is only enforced by DispatchCommand after it has
 * created the command object, but before it populates it or starts execution.
 */
@Target( {ElementType.TYPE})
@Retention(RUNTIME)
public @interface StreamCommand {
}
