// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({METHOD})
@Retention(RUNTIME)
public @interface TestProjectInput {
  // Fields from ProjectInput for creating the project.

  String parent() default "";

  boolean createEmptyCommit() default true;

  String description() default "";

  // These may be null in a ProjectInput, but annotations do not allow null
  // default values. Thus these defaults should match ProjectConfig.
  SubmitType submitType() default SubmitType.MERGE_IF_NECESSARY;

  InheritableBoolean useContributorAgreements() default InheritableBoolean.INHERIT;

  InheritableBoolean useSignedOffBy() default InheritableBoolean.INHERIT;

  InheritableBoolean useContentMerge() default InheritableBoolean.INHERIT;

  InheritableBoolean requireChangeId() default InheritableBoolean.INHERIT;

  // Fields specific to acceptance test behavior.

  /** Username to use for initial clone, passed to {@link AccountCreator}. */
  String cloneAs() default "admin";
}
