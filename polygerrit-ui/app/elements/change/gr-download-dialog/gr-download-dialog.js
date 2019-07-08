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

  Polymer({
    is: 'gr-download-dialog',

    /**
     * Fired when the user presses the close button.
     *
     * @event close
     */

    properties: {
      change: Object,
      patchNum: String,
      config: Object,
      loggedIn: {
        type: Boolean,
        value: false,
        observer: '_loggedInChanged',
      },

      _schemes: {
        type: Array,
        value: function() { return []; },
        computed: '_computeSchemes(change, patchNum)',
        observer: '_schemesChanged',
      },
      _selectedScheme: String,
    },

    hostAttributes: {
      role: 'dialog',
    },

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

    focus: function() {
      if (this._schemes.length) {
        this.$$('.copyToClipboard').focus();
      } else {
        this.$.download.focus();
      }
    },

    getFocusStops: function() {
      var links = this.$$('#archives').querySelectorAll('a');
      return {
        start: this.$.closeButton,
        end: links[links.length - 1],
      };
    },

    _loggedInChanged: function(loggedIn) {
      if (!loggedIn) { return; }
      this.$.restAPI.getPreferences().then(function(prefs) {
        if (prefs.download_scheme) {
          // Note (issue 5180): normalize the download scheme with lower-case.
          this._selectedScheme = prefs.download_scheme.toLowerCase();
        }
      }.bind(this));
    },

    _computeDownloadCommands: function(change, patchNum, _selectedScheme) {
      var commandObj;
      for (var rev in change.revisions) {
        if (change.revisions[rev]._number == patchNum &&
            change.revisions[rev].fetch.hasOwnProperty(_selectedScheme)) {
          commandObj = change.revisions[rev].fetch[_selectedScheme].commands;
          break;
        }
      }
      var commands = [];
      for (var title in commandObj) {
        commands.push({
          title: title,
          command: commandObj[title],
        });
      }
      return commands;
    },

    _computeZipDownloadLink: function(change, patchNum) {
      return this._computeDownloadLink(change, patchNum, true);
    },

    _computeZipDownloadFilename: function(change, patchNum) {
      return this._computeDownloadFilename(change, patchNum, true);
    },

    _computeDownloadLink: function(change, patchNum, zip) {
      return this.changeBaseURL(change._number, patchNum) + '/patch?' +
          (zip ? 'zip' : 'download');
    },

    _computeDownloadFilename: function(change, patchNum, zip) {
      var shortRev;
      for (var rev in change.revisions) {
        if (change.revisions[rev]._number == patchNum) {
          shortRev = rev.substr(0, 7);
          break;
        }
      }
      return shortRev + '.diff.' + (zip ? 'zip' : 'base64');
    },

    _computeArchiveDownloadLink: function(change, patchNum, format) {
      return this.changeBaseURL(change._number, patchNum) +
          '/archive?format=' + format;
    },

    _computeSchemes: function(change, patchNum) {
      for (var rev in change.revisions) {
        if (change.revisions[rev]._number == patchNum) {
          var fetch = change.revisions[rev].fetch;
          if (fetch) {
            return Object.keys(fetch).sort();
          }
          break;
        }
      }
      return [];
    },

    _computeSchemeSelected: function(scheme, selectedScheme) {
      return scheme == selectedScheme;
    },

    _handleSchemeTap: function(e) {
      e.preventDefault();
      var el = Polymer.dom(e).rootTarget;
      this._selectedScheme = el.getAttribute('data-scheme');
      if (this.loggedIn) {
        this.$.restAPI.savePreferences({download_scheme: this._selectedScheme});
      }
    },

    _handleInputTap: function(e) {
      e.preventDefault();
      Polymer.dom(e).rootTarget.select();
    },

    _handleCloseTap: function(e) {
      e.preventDefault();
      this.fire('close', null, {bubbles: false});
    },

    _schemesChanged: function(schemes) {
      if (schemes.length == 0) { return; }
      if (schemes.indexOf(this._selectedScheme) == -1) {
        this._selectedScheme = schemes.sort()[0];
      }
    },

    _copyToClipboard: function(e) {
      e.target.parentElement.querySelector('.copyCommand').select();
      document.execCommand('copy');
      getSelection().removeAllRanges();
      e.target.textContent = 'done';
      setTimeout(function() { e.target.textContent = 'copy'; }, 1000);
    },
  });
})();
