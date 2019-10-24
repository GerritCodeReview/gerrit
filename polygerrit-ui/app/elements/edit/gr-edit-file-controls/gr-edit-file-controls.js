/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
(function() {
  'use strict';

  Polymer({
    is: 'gr-edit-file-controls',

    /**
     * Fired when an action in the overflow menu is tapped.
     *
     * @event file-action-tap
     */

    properties: {
      filePath: String,
      _allFileActions: {
        type: Array,
        value: () => Object.values(GrEditConstants.Actions),
      },
      _fileActions: {
        type: Array,
        computed: '_computeFileActions(_allFileActions)',
      },
    },

    _handleActionTap(e) {
      e.preventDefault();
      e.stopPropagation();
      this._dispatchFileAction(e.detail.id, this.filePath);
    },

    _dispatchFileAction(action, path) {
      this.dispatchEvent(new CustomEvent(
          'file-action-tap',
          {detail: {action, path}, bubbles: true, composed: true}));
    },

    _computeFileActions(actions) {
      // TODO(kaspern): conditionally disable some actions based on file status.
      return actions.map(action => {
        return {
          name: action.label,
          id: action.id,
        };
      });
    },
  });
})();
