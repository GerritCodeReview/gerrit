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

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.BindingAnnotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation for interfaces that accept auto-registered implementations.
 *
 * <p>Interfaces that accept automatically registered implementations into their {@link DynamicSet}
 * must be tagged with this annotation.
 *
 * <p>Plugins or extensions that implement an {@code @ExtensionPoint} interface should use the
 * {@link Listen} annotation to automatically register.
 *
 * @see Listen
 */
@Target({ElementType.TYPE})
@Retention(RUNTIME)
@BindingAnnotation
public @interface ExtensionPoint {}
