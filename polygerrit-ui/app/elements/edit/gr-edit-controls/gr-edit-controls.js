// Copyright (C) 2017 The Android Open Source Project
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
(function() {
  'use strict';

  const Actions = {
    EDIT: {label: 'Edit', key: 'edit'},
    /* TODO(kaspern): Implement these actions.
    DELETE: {label: 'Delete', key: 'delete'},
    RENAME: {label: 'Rename', key: 'rename'},
    REVERT: {label: 'Revert', key: 'revert'},
    CHECKOUT: {label: 'Check out', key: 'checkout'},
    */
  };

  Polymer({
    is: 'gr-edit-controls',
    properties: {
      change: Object,

      _actions: {
        type: Array,
        value() { return Object.values(Actions); },
      },
      _path: {
        type: String,
        value: '',
      },
      _query: {
        type: Function,
        value() {
          return this._queryFiles.bind(this);
        },
      },
    },

    behaviors: [
      Gerrit.PatchSetBehavior,
    ],

    _handleTap(e) {
      e.preventDefault();
      const action = Polymer.dom(e).localTarget.id;
      // TODO(kaspern): Add all actions to this switch.
      switch (action) {
        case Actions.EDIT.key:
          this.openEditDialog();
          return;
      }
    },

    openEditDialog(opt_path) {
      if (opt_path) { this._path = opt_path; }
      return this._showDialog(this.$.editDialog);
    },

    /**
     * Given a path string, checks that it is a valid file path.
     * @param {string} path
     * @return {boolean}
     */
    _isValidPath(path) {
      return path.length && !path.endsWith('/');
    },

    _showDialog(dialog) {
      return this.$.overlay.open().then(() => {
        dialog.classList.toggle('invisible', false);
        dialog.querySelector('.input').focus();
        this.async(() => { this.$.overlay.center(); }, 1);
      });
    },

    _closeDialog(dialog) {
      dialog.querySelectorAll('gr-autocomplete')
          .forEach(input => { input.text = ''; });
      dialog.classList.toggle('invisible', true);
      return this.$.overlay.close();
    },

    _handleDialogCancel(e) {
      this._closeDialog(Polymer.dom(e).localTarget);
    },

    _handleEditConfirm(e) {
      const url = Gerrit.Nav.getEditUrlForDiff(this.change, this._path);
      Gerrit.Nav.navigateToRelativeUrl(url);
      this._closeDialog(Polymer.dom(e).localTarget);
    },

    _queryFiles(input) {
      return this.$.restAPI.queryChangeFiles(this.change._number,
          this.EDIT_NAME, input).then(res => res.map(file => {
            return {name: file};
          }));
    },
  });
})();