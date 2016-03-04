// Copyright (C) 2016 The Android Open Source Project
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

  var COMMIT_MESSAGE_PATH = '/COMMIT_MSG';

  Polymer({
    is: 'gr-file-list',

    properties: {
      patchNum: String,
      changeNum: String,
      comments: Object,
      files: Array,
      selectedIndex: {
        type: Number,
        notify: true,
      },
      keyEventTarget: {
        type: Object,
        value: function() { return document.body; },
      },

      _loggedIn: {
        type: Boolean,
        value: false,
      },
      _drafts: Object,
      _reviewed: {
        type: Array,
        value: function() { return []; },
      },
      _xhrPromise: Object,  // Used for testing.
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.RESTClientBehavior,
    ],

    reload: function() {
      if (!this.changeNum || !this.patchNum) {
        return Promise.resolve();
      }
      return Promise.all([
        this.$.filesXHR.generateRequest().completes,
        app.accountReady.then(function() {
          this._loggedIn = app.loggedIn;
          if (!app.loggedIn) { return; }
          this.$.draftsXHR.generateRequest();
          this.$.reviewedXHR.generateRequest();
        }.bind(this)),
      ]);
    },

    _computeFilesURL: function(changeNum, patchNum) {
      return this.changeBaseURL(changeNum, patchNum) + '/files';
    },

    _computeCommentsString: function(comments, patchNum, path) {
      var patchComments = (comments[path] || []).filter(function(c) {
        return c.patch_set == patchNum;
      });
      var num = patchComments.length;
      if (num == 0) { return ''; }
      if (num == 1) { return '1 comment'; }
      if (num > 1) { return num + ' comments'; }
    },

    _computeReviewedURL: function(changeNum, patchNum) {
      return this.changeBaseURL(changeNum, patchNum) + '/files?reviewed';
    },

    _computeReviewed: function(file, _reviewed) {
      return _reviewed.indexOf(file.__path) != -1;
    },

    _handleReviewedChange: function(e) {
      var path = Polymer.dom(e).rootTarget.getAttribute('data-path');
      var index = this._reviewed.indexOf(path);
      var reviewed = index != -1;
      if (reviewed) {
        this.splice('_reviewed', index, 1);
      } else {
        this.push('_reviewed', path);
      }

      var method = reviewed ? 'DELETE' : 'PUT';
      var url = this.changeBaseURL(this.changeNum, this.patchNum) +
          '/files/' + encodeURIComponent(path) + '/reviewed';
      this._send(method, url).catch(function(err) {
        alert('Couldn’t change file review status. Check the console ' +
            'and contact the PolyGerrit team for assistance.');
        throw err;
      }.bind(this));
    },

    _computeDraftsURL: function(changeNum, patchNum) {
      return this.changeBaseURL(changeNum, patchNum) + '/drafts';
    },

    _computeDraftsString: function(drafts, path) {
      var num = (drafts[path] || []).length;
      if (num == 0) { return ''; }
      if (num == 1) { return '1 draft'; }
      if (num > 1) { return num + ' drafts'; }
    },

    _handleResponse: function(e, req) {
      var result = e.detail.response;
      var paths = Object.keys(result).sort();
      var files = [];
      for (var i = 0; i < paths.length; i++) {
        var info = result[paths[i]];
        info.__path = paths[i];
        info.lines_inserted = info.lines_inserted || 0;
        info.lines_deleted = info.lines_deleted || 0;
        files.push(info);
      }
      this.files = files;
    },

    _handleKey: function(e) {
      if (this.shouldSupressKeyboardShortcut(e)) { return; }

      switch (e.keyCode) {
        case 74:  // 'j'
          e.preventDefault();
          this.selectedIndex =
              Math.min(this.files.length - 1, this.selectedIndex + 1);
          break;
        case 75:  // 'k'
          e.preventDefault();
          this.selectedIndex = Math.max(0, this.selectedIndex - 1);
          break;
        case 219:  // '['
          e.preventDefault();
          this._openSelectedFile(this.files.length - 1);
          break;
        case 221:  // ']'
          e.preventDefault();
          this._openSelectedFile(0);
          break;
        case 13:  // <enter>
        case 79:  // 'o'
          e.preventDefault();
          this._openSelectedFile();
          break;
      }
    },

    _openSelectedFile: function(opt_index) {
      if (opt_index != null) {
        this.selectedIndex = opt_index;
      }
      page.show(this._computeDiffURL(this.changeNum, this.patchNum,
          this.files[this.selectedIndex].__path));
    },

    _computeFileSelected: function(index, selectedIndex) {
      return index == selectedIndex;
    },

    _computeFileStatus: function(status) {
      return status || 'M';
    },

    _computeDiffURL: function(changeNum, patchNum, path) {
      return '/c/' + changeNum + '/' + patchNum + '/' + path;
    },

    _computeFileDisplayName: function(path) {
      return path == COMMIT_MESSAGE_PATH ? 'Commit message' : path;
    },

    _computeClass: function(baseClass, path) {
      var classes = [baseClass];
      if (path == COMMIT_MESSAGE_PATH) {
        classes.push('invisible');
      }
      return classes.join(' ');
    },

    _send: function(method, url) {
      var xhr = document.createElement('gr-request');
      this._xhrPromise = xhr.send({
        method: method,
        url: url,
      });
      return this._xhrPromise;
    },
  });
})();
