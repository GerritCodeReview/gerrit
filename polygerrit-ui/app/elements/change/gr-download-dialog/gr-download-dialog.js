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

      _schemes: {
        type: Array,
        value() { return []; },
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

    focus() {
      if (this._schemes.length) {
        this.$.downloadCommands.focusOnCopy();
      } else {
        this.$.download.focus();
      }
    },

    getFocusStops() {
      const links = this.$$('#archives').querySelectorAll('a');
      return {
        start: this.$.closeButton,
        end: links[links.length - 1],
      };
    },

    _computeDownloadCommands(change, patchNum, _selectedScheme) {
      let commandObj;
      for (const rev in change.revisions) {
        if ((change.revisions[rev]._number === parseInt(patchNum, 10) ||
            (change.revisions[rev]._number === 0 && patchNum === 'edit')) &&
            change.revisions[rev].fetch.hasOwnProperty(_selectedScheme)) {
          commandObj = change.revisions[rev].fetch[_selectedScheme].commands;
          break;
        }
      }
      const commands = [];
      for (const title in commandObj) {
        if (!commandObj.hasOwnProperty(title)) { continue; }
        commands.push({
          title,
          command: commandObj[title],
        });
      }
      return commands;
    },

    _computeZipDownloadLink(change, patchNum) {
      return this._computeDownloadLink(change, patchNum, true);
    },

    _computeZipDownloadFilename(change, patchNum) {
      return this._computeDownloadFilename(change, patchNum, true);
    },

    _computeDownloadLink(change, patchNum, zip) {
      return this.changeBaseURL(change._number, patchNum) + '/patch?' +
          (zip ? 'zip' : 'download');
    },

    _computeDownloadFilename(change, patchNum, zip) {
      let shortRev;
      for (const rev in change.revisions) {
        if (change.revisions[rev]._number === parseInt(patchNum, 10)) {
          shortRev = rev.substr(0, 7);
          break;
        }
      }
      return shortRev + '.diff.' + (zip ? 'zip' : 'base64');
    },

    _computeArchiveDownloadLink(change, patchNum, format) {
      return this.changeBaseURL(change._number, patchNum) +
          '/archive?format=' + format;
    },

    _computeSchemes(change, patchNum) {
      for (const rev in change.revisions) {
        if (change.revisions[rev]._number === parseInt(patchNum, 10) ||
            (change.revisions[rev]._number === 0 && patchNum === 'edit')) {
          const fetch = change.revisions[rev].fetch;
          if (fetch) {
            return Object.keys(fetch).sort();
          }
          break;
        }
      }
      return [];
    },

    _computePatchSetQuantity(revisions) {
      if (!revisions) { return 0; }
      return Object.keys(revisions).length;
    },

    _handleCloseTap(e) {
      e.preventDefault();
      this.fire('close', null, {bubbles: false});
    },

    _schemesChanged(schemes) {
      if (schemes.length === 0) { return; }
      if (!schemes.includes(this._selectedScheme)) {
        this._selectedScheme = schemes.sort()[0];
      }
    },
  });
})();
