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

  Polymer({
    is: 'gr-editor-view',

    /**
     * Fired when the title of the page should change.
     *
     * @event title-change
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
      _loggedIn: Boolean,
      _path: String,
      _content: String,
      _newContent: String,
      _saveDisabled: {
        type: Boolean,
        value: true,
        computed: '_computeSaveDisabled(_content, _newContent)',
      },
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.PatchSetBehavior,
      Gerrit.PathListBehavior,
    ],

    attached() {
      this._getLoggedIn().then(loggedIn => { this._loggedIn = loggedIn; });
    },

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
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
      promises.push(this._getFileContent(this._changeNum, this._path)
          .then(fileContent => {
            this._content = fileContent;
            this._newContent = fileContent;
          }));
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

    _getFileContent(changeNum, path) {
      return this.$.restAPI.getFileInChangeEdit(changeNum, path).then(res => {
        if (!res.ok) {
          if (res.status === 404) {
            // No edits have been made yet.
            return this.$.restAPI.getFileInChangeEdit(changeNum, path, true)
                .then(res => res.text);
          }
          return '';
        }
        return res.text;
      });
    },

    _saveEdit() {
      return this.$.restAPI.saveChangeEdit(this._changeNum, this._path,
          this._newContent).then(res => {
            if (!res.ok) { return; }
            this._viewEditInChangeView();
          });
    },

    _computeSaveDisabled(content, newContent) {
      return content === newContent;
    },

    _handleCancelTap() {
      // TODO(kaspern): Add a confirm dialog if there are unsaved changes.
      this._viewEditInChangeView();
    },
  });
})();
