// Copyright (C) 2010 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.server.config;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.Retention;

/**
 * Used to populate the groups of users that are allowed to run
 * receive-pack on the server.
 *
 * Gerrit.config example:
 *
 * <pre>
 * [receive]
 *     allowGroup = RECEIVE_GROUP_ALLOWED
 * </pre>
 */
@Retention(RUNTIME)
@BindingAnnotation
public @interface GitReceivePackGroups {
}
