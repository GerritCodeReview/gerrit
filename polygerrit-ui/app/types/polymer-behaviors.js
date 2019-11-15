/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * For the purposes of template type checking, externs should be added for
 * anything set on the window object. Note that sub-properties of these
 * declared properties are considered something separate.
 *
 * This file is only for template type checking, not used in Gerrit code.
 */

/* eslint-disable no-var */
/* eslint-disable no-unused-vars */

function PolymerMixins() {
  // This function must not be called.
  // Due to an issue in polymer linter the linter can't
  // process correctly some behaviors from Polymer library.
  // To workaround this issue, here we define a minimal mixin to allow
  // linter process our code correctly. You can add more properties to mixins
  // if needed.

  // Important! Use mixins from these file only inside JSDoc comments.
  // Do not use it in the real code

  /**
   * @polymer
   * @mixinFunction
   * */
  Polymer.IronFitMixin = base =>
    class extends base {
      static get properties() {
        return {
          positionTarget: Object,
        };
      }
    };
}
