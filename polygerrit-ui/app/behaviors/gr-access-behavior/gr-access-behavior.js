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

  /** @polymerBehavior Gerrit.AccessBehavior */
  Gerrit.AccessBehavior = {
    properties: {
      permissionValues: {
        type: Object,
        readOnly: true,
        value: {
          abandon: {
            id: 'abandon',
            name: 'Abandon',
          },
          addPatchSet: {
            id: 'addPatchSet',
            name: 'Add Patch Set',
          },
          create: {
            id: 'create',
            name: 'Create Reference',
          },
          createTag: {
            id: 'createTag',
            name: 'Create Annotated Tag',
          },
          createSignedTag: {
            id: 'createSignedTag',
            name: 'Create Signed Tag',
          },
          delete: {
            id: 'delete',
            name: 'Delete Reference',
          },
          deleteChanges: {
            id: 'deleteChanges',
            name: 'Delete Changes',
          },
          deleteOwnChanges: {
            id: 'deleteOwnChanges',
            name: 'Delete Own Changes',
          },
          editAssignee: {
            id: 'editAssignee',
            name: 'Edit Assignee',
          },
          editHashtags: {
            id: 'editHashtags',
            name: 'Edit Hashtags',
          },
          editTopicName: {
            id: 'editTopicName',
            name: 'Edit Topic Name',
          },
          forgeAuthor: {
            id: 'forgeAuthor',
            name: 'Forge Author Identity',
          },
          forgeCommitter: {
            id: 'forgeCommitter',
            name: 'Forge Committer Identity',
          },
          forgeServerAsCommitter: {
            id: 'forgeServerAsCommitter',
            name: 'Forge Server Identity',
          },
          owner: {
            id: 'owner',
            name: 'Owner',
          },
          publishDrafts: {
            id: 'publishDrafts',
            name: 'Publish Drafts',
          },
          push: {
            id: 'push',
            name: 'Push',
          },
          pushMerge: {
            id: 'pushMerge',
            name: 'Push Merge Commit',
          },
          read: {
            id: 'read',
            name: 'Read',
          },
          rebase: {
            id: 'rebase',
            name: 'Rebase',
          },
          removeReviewer: {
            id: 'removeReviewer',
            name: 'Remove Reviewer',
          },
          submit: {
            id: 'submit',
            name: 'Submit',
          },
          submitAs: {
            id: 'submitAs',
            name: 'Submit (On Behalf Of)',
          },
          toggleWipState: {
            id: 'toggleWipState',
            name: 'Toggle Work In Progress State',
          },
          viewPrivateChanges: {
            id: 'viewPrivateChanges',
            name: 'View Private Changes',
          },
        },
      },
    },

    /**
     * @param {!Object} obj
     * @return {!Array} returns a sorted array sorted by the id of the original
     *    object.
     */
    toSortedArray(obj) {
      if (!obj) { return []; }
      return Object.keys(obj)
          .map(key => {
            return {
              id: key,
              value: obj[key],
            };
          })
          .sort((a, b) =>
            // Since IDs are strings, use localeCompare.
            a.id.localeCompare(b.id)
          );
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
    Gerrit.AccessMixin = base =>
      class extends base {
        static get properties() {
          return {
            permissionValues: {
              type: Object,
            },
          };
        }
      };
  }
})(window);
</script>
