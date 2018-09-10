/**
@license
Copyright (C) 2016 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/gr-patch-set-behavior/gr-patch-set-behavior.js';
import '../../../behaviors/rest-client-behavior/rest-client-behavior.js';
import '../../shared/gr-download-commands/gr-download-commands.js';
import '../../../styles/shared-styles.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        background-color: var(--dialog-background-color);
        display: block;
      }
      section {
        display: flex;
        padding: .5em 1.5em;
      }
      section:not(:first-of-type) {
        border-top: 1px solid var(--border-color);
      }
      .flexContainer {
        display: flex;
        justify-content: space-between;
        padding-top: .75em;
      }
      .footer {
        justify-content: flex-end;
      }
      .closeButtonContainer {
        align-items: flex-end;
        display: flex;
        flex: 0;
        justify-content: flex-end;
      }
      .patchFiles,
      .archivesContainer {
        padding-bottom: .5em;
      }
      .patchFiles {
        margin-right: 2em;
      }
      .patchFiles a,
      .archives a {
        display: inline-block;
        margin-right: 1em;
      }
      .patchFiles a:last-of-type,
      .archives a:last-of-type {
        margin-right: 0;
      }
      .title {
        flex: 1;
        font-family: var(--font-family-bold);
      }
      .hidden {
        display: none;
      }
    </style>
    <section>
      <span class="title">
        Patch set [[patchNum]] of [[_computePatchSetQuantity(change.revisions)]]
      </span>
    </section>
    <section class\$="[[_computeShowDownloadCommands(_schemes)]]">
      <gr-download-commands id="downloadCommands" commands="[[_computeDownloadCommands(change, patchNum, _selectedScheme)]]" schemes="[[_schemes]]" selected-scheme="{{_selectedScheme}}"></gr-download-commands>
    </section>
    <section class="flexContainer">
      <div class="patchFiles">
        <label>Patch file</label>
        <div>
          <a id="download" href\$="[[_computeDownloadLink(change, patchNum)]]" download="">
            [[_computeDownloadFilename(change, patchNum)]]
          </a>
          <a href\$="[[_computeZipDownloadLink(change, patchNum)]]" download="">
            [[_computeZipDownloadFilename(change, patchNum)]]
          </a>
        </div>
      </div>
      <div class="archivesContainer" hidden\$="[[!config.archives.length]]" hidden="">
        <label>Archive</label>
        <div id="archives" class="archives">
          <template is="dom-repeat" items="[[config.archives]]" as="format">
            <a href\$="[[_computeArchiveDownloadLink(change, patchNum, format)]]" download="">
              [[format]]
            </a>
          </template>
        </div>
      </div>
    </section>
    <section class="footer">
      <span class="closeButtonContainer">
        <gr-button id="closeButton" link="" on-tap="_handleCloseTap">Close</gr-button>
      </span>
    </section>
`,

  is: 'gr-download-dialog',

  /**
   * Fired when the user presses the close button.
   *
   * @event close
   */

  properties: {
    /** @type {{ revisions: Array }} */
    change: Object,
    patchNum: String,
    /** @type {?} */
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
    Gerrit.PatchSetBehavior,
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
    for (const rev of Object.values(change.revisions || {})) {
      if (this.patchNumEquals(rev._number, patchNum) &&
          rev.fetch.hasOwnProperty(_selectedScheme)) {
        commandObj = rev.fetch[_selectedScheme].commands;
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

  /**
   * @param {!Object} change
   * @param {number|string} patchNum
   *
   * @return {string}
   */
  _computeZipDownloadLink(change, patchNum) {
    return this._computeDownloadLink(change, patchNum, true);
  },

  /**
   * @param {!Object} change
   * @param {number|string} patchNum
   *
   * @return {string}
   */
  _computeZipDownloadFilename(change, patchNum) {
    return this._computeDownloadFilename(change, patchNum, true);
  },

  /**
   * @param {!Object} change
   * @param {number|string} patchNum
   * @param {boolean=} opt_zip
   *
   * @return {string} Not sure why there was a mismatch
   */
  _computeDownloadLink(change, patchNum, opt_zip) {
    return this.changeBaseURL(change._number, patchNum) + '/patch?' +
        (opt_zip ? 'zip' : 'download');
  },

  /**
   * @param {!Object} change
   * @param {number|string} patchNum
   * @param {boolean=} opt_zip
   *
   * @return {string}
   */
  _computeDownloadFilename(change, patchNum, opt_zip) {
    let shortRev = '';
    for (const rev in change.revisions) {
      if (this.patchNumEquals(change.revisions[rev]._number, patchNum)) {
        shortRev = rev.substr(0, 7);
        break;
      }
    }
    return shortRev + '.diff.' + (opt_zip ? 'zip' : 'base64');
  },

  _computeArchiveDownloadLink(change, patchNum, format) {
    return this.changeBaseURL(change._number, patchNum) +
        '/archive?format=' + format;
  },

  _computeSchemes(change, patchNum) {
    for (const rev of Object.values(change.revisions || {})) {
      if (this.patchNumEquals(rev._number, patchNum)) {
        const fetch = rev.fetch;
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

  _computeShowDownloadCommands(schemes) {
    return schemes.length ? '' : 'hidden';
  }
});
