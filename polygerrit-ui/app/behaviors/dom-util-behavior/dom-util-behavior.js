<!--
@license
Copyright (C) 2018 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
<script>
(function(window) {
  'use strict';

  window.Gerrit = window.Gerrit || {};

  /** @polymerBehavior Gerrit.DomUtilBehavior */
  Gerrit.DomUtilBehavior = {
    /**
     * Are any ancestors of the element (or the element itself) members of the
     * given class.
     *
     * @param {!Element} element
     * @param {string} className
     * @param {Element=} opt_stopElement If provided, stop traversing the
     *     ancestry when the stop element is reached. The stop element's class
     *     is not checked.
     * @return {boolean}
     */
    descendedFromClass(element, className, opt_stopElement) {
      let isDescendant = element.classList.contains(className);
      while (!isDescendant && element.parentElement &&
          (!opt_stopElement || element.parentElement !== opt_stopElement)) {
        isDescendant = element.classList.contains(className);
        element = element.parentElement;
      }
      return isDescendant;
    },
  };

  // eslint-disable-next-line no-unused-vars
  function defineEmptyMixin() {
    // This is a temporary function.
    // Polymer linter doesn't process correctly the following code:
    // class MyElement extends Polymer.mixinBehaviors([legacyBehaviors], ...) {...}
    // To workaround this issue, the mock mixin is declared in this method.
    // In the following changes, legacy behaviors will be converted to mixins.

    /**
     * @polymer
     * @mixinFunction
     */
    Gerrit.DomUtilMixin = base =>
      class extends base {
      };
  }
})(window);
</script>
