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

  const SAVING_MESSAGE = 'Saving changes...';
  const SAVED_MESSAGE = 'All changes saved';
  const SAVE_FAILED_MSG = 'Failed to save changes';

  Polymer({
    is: 'gr-editor-view',

    /**
     * Fired when the title of the page should change.
     *
     * @event title-change
     */

    /**
     * Fired to notify the user of
     *
     * @event show-alert
     */

    properties: {
      /**
       * URL params passed from the router.
       */
      params: {
        type: Object,
        observer: '_paramsChanged',
      },

      _change: Object,
      _changeEditDetail: Object,
      _changeNum: String,
      _path: String,
      _type: String,
      _content: String,
      _newContent: String,
      _saving: {
        type: Boolean,
        value: false,
      },
      _saveDisabled: {
        type: Boolean,
        value: true,
        computed: '_computeSaveDisabled(_content, _newContent, _saving)',
      },
      _prefs: Object,
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.PatchSetBehavior,
      Gerrit.PathListBehavior,
    ],

    listeners: {
      'content-change': '_handleContentChange',
    },

    attached() {
      this._getEditPrefs().then(prefs => { this._prefs = prefs; });
    },

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
    },

    _getEditPrefs() {
      return this.$.restAPI.getEditPreferences();
    },

    _paramsChanged(value) {
      if (value.view !== Gerrit.Nav.View.EDIT) { return; }

      this._changeNum = value.changeNum;
      this._path = value.path;

      // NOTE: This may be called before attachment (e.g. while parentElement is
      // null). Fire title-change in an async so that, if attachment to the DOM
      // has been queued, the event can bubble up to the handler in gr-app.
      this.async(() => {
        const title = `Editing ${this.computeTruncatedPath(this._path)}`;
        this.fire('title-change', {title});
      });

      const promises = [];

      promises.push(this._getChangeDetail(this._changeNum));
      promises.push(this._getFileData(this._changeNum, this._path));
      return Promise.all(promises);
    },

    _getChangeDetail(changeNum) {
      return this.$.restAPI.getDiffChangeDetail(changeNum).then(change => {
        this._change = change;
      });
    },

    _handlePathChanged(e) {
      const path = e.detail;
      if (path === this._path) { return Promise.resolve(); }
      return this.$.restAPI.renameFileInChangeEdit(this._changeNum,
          this._path, path).then(res => {
            if (!res.ok) { return; }
            this._viewEditInChangeView();
          });
    },

    _viewEditInChangeView() {
      Gerrit.Nav.navigateToChange(this._change, this.EDIT_NAME);
    },

    _getFileData(changeNum, path) {
      return this.$.restAPI.getFileInChangeEdit(changeNum, path).then(res => {
        if (!res.ok) { return; }

        this._type = res.type || '';
        this._newContent = res.content || '';
        this._content = res.content || '';
      });
    },

    _saveEdit() {
      this._saving = true;
      this._showAlert(SAVING_MESSAGE);
      return this.$.restAPI.saveChangeEdit(this._changeNum, this._path,
          this._newContent).then(res => {
            this._saving = false;
            this._showAlert(res.ok ? SAVED_MESSAGE : SAVE_FAILED_MSG);
          });
    },

    _showAlert(message) {
      this.dispatchEvent(new CustomEvent('show-alert', {
        detail: {message},
        bubbles: true,
      }));
    },

    _computeSaveDisabled(content, newContent, saving) {
      if (saving) { return true; }
      return content === newContent;
    },

    _handleCancelTap() {
      // TODO(kaspern): Add a confirm dialog if there are unsaved changes.
      this._viewEditInChangeView();
    },

    _handleContentChange(e) {
      if (e.detail.value) { this.set('_newContent', e.detail.value); }
    },
  });
})();
