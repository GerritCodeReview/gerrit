// Copyright (C) 2012 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.extensions.annotations;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation for auto-registered extension point implementations.
 *
 * <p>Plugins or extensions using auto-registration should apply this annotation to any non-abstract
 * class that implements an unnamed extension point, such as a notification listener. Gerrit will
 * automatically determine which extension points to apply based on the interfaces the type
 * implements.
 *
 * @see Export
 */
@Target({ElementType.TYPE})
@Retention(RUNTIME)
@BindingAnnotation
public @interface Listen {}
