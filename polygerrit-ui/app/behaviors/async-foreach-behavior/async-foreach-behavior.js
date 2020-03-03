<!--
@license
Copyright (C) 2017 The Android Open Source Project

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

  /** @polymerBehavior Gerrit.AsyncForeachBehavior */
  Gerrit.AsyncForeachBehavior = {
    /**
     * @template T
     * @param {!Array<T>} array
     * @param {!Function} fn An iteratee function to be passed each element of
     *     the array in order. Must return a promise, and the following
     *     iteration will not begin until resolution of the promise returned by
     *     the previous iteration.
     *
     *     An optional second argument to fn is a callback that will halt the
     *     loop if called.
     * @return {!Promise<undefined>}
     */
    asyncForeach(array, fn) {
      if (!array.length) { return Promise.resolve(); }
      let stop = false;
      const stopCallback = () => { stop = true; };
      return fn(array[0], stopCallback).then(exit => {
        if (stop) { return Promise.resolve(); }
        return this.asyncForeach(array.slice(1), fn);
      });
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
    Gerrit.AsyncForeachMixin = base =>
      class extends base {
      };
  }
})(window);
</script>
