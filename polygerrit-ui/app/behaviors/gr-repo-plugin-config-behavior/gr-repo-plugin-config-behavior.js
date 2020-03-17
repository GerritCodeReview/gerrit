<!--
@license
Copyright (C) 2019 The Android Open Source Project

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

  /** @polymerBehavior Gerrit.RepoPluginConfig*/
  Gerrit.RepoPluginConfig = {
    // Should be kept in sync with
    // gerrit/java/com/google/gerrit/extensions/api/projects/ProjectConfigEntryType.java.
    ENTRY_TYPES: {
      ARRAY: 'ARRAY',
      BOOLEAN: 'BOOLEAN',
      INT: 'INT',
      LIST: 'LIST',
      LONG: 'LONG',
      STRING: 'STRING',
    },
    PLUGIN_CONFIG_CHANGED: 'plugin-config-changed',
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
    Gerrit.RepoPluginConfigMixin = base =>
      class extends base {
      };
  }
})(window);
</script>
