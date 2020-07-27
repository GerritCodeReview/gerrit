/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '../../../styles/shared-styles.js';
import '../../shared/gr-download-commands/gr-download-commands.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-download-dialog_html.js';
import {patchNumEquals} from '../../../utils/patch-set-util.js';
import {changeBaseURL} from '../../../utils/change-util.js';

/**
 * @extends PolymerElement
 */
class GrDownloadDialog extends GestureEventListeners(
    LegacyElementMixin(PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-download-dialog'; }
  /**
   * Fired when the user presses the close button.
   *
   * @event close
   */

  static get properties() {
    return {
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
    };
  }

  /** @override */
  ready() {
    super.ready();
    this._ensureAttribute('role', 'dialog');
  }

  focus() {
    if (this._schemes.length) {
      this.$.downloadCommands.focusOnCopy();
    } else {
      this.$.download.focus();
    }
  }

  getFocusStops() {
    const links = this.shadowRoot
        .querySelector('#archives').querySelectorAll('a');
    return {
      start: this.$.closeButton,
      end: links[links.length - 1],
    };
  }

  _computeDownloadCommands(change, patchNum, _selectedScheme) {
    let commandObj;
    if (!change) return [];
    for (const rev of Object.values(change.revisions || {})) {
      if (patchNumEquals(rev._number, patchNum) &&
          rev && rev.fetch && rev.fetch.hasOwnProperty(_selectedScheme)) {
        commandObj = rev.fetch[_selectedScheme].commands;
        break;
      }
    }
    const commands = [];
    for (const title in commandObj) {
      if (!commandObj || !commandObj.hasOwnProperty(title)) { continue; }
      commands.push({
        title,
        command: commandObj[title],
      });
    }
    return commands;
  }

  /**
   * @param {!Object} change
   * @param {number|string} patchNum
   *
   * @return {string}
   */
  _computeZipDownloadLink(change, patchNum) {
    return this._computeDownloadLink(change, patchNum, true);
  }

  /**
   * @param {!Object} change
   * @param {number|string} patchNum
   *
   * @return {string}
   */
  _computeZipDownloadFilename(change, patchNum) {
    return this._computeDownloadFilename(change, patchNum, true);
  }

  /**
   * @param {!Object} change
   * @param {number|string} patchNum
   * @param {boolean=} opt_zip
   *
   * @return {string} Not sure why there was a mismatch
   */
  _computeDownloadLink(change, patchNum, opt_zip) {
    // Polymer 2: check for undefined
    if ([change, patchNum].includes(undefined)) {
      return '';
    }
    return changeBaseURL(change.project, change._number, patchNum) +
        '/patch?' + (opt_zip ? 'zip' : 'download');
  }

  /**
   * @param {!Object} change
   * @param {number|string} patchNum
   * @param {boolean=} opt_zip
   *
   * @return {string}
   */
  _computeDownloadFilename(change, patchNum, opt_zip) {
    // Polymer 2: check for undefined
    if ([change, patchNum].includes(undefined)) {
      return '';
    }

    let shortRev = '';
    for (const rev in change.revisions) {
      if (patchNumEquals(change.revisions[rev]._number, patchNum)) {
        shortRev = rev.substr(0, 7);
        break;
      }
    }
    return shortRev + '.diff.' + (opt_zip ? 'zip' : 'base64');
  }

  _computeHidePatchFile(change, patchNum) {
    // Polymer 2: check for undefined
    if ([change, patchNum].includes(undefined)) {
      return false;
    }
    for (const rev of Object.values(change.revisions || {})) {
      if (patchNumEquals(rev._number, patchNum)) {
        const parentLength = rev.commit && rev.commit.parents ?
          rev.commit.parents.length : 0;
        return parentLength == 0;
      }
    }
    return false;
  }

  _computeArchiveDownloadLink(change, patchNum, format) {
    // Polymer 2: check for undefined
    if ([change, patchNum, format].includes(undefined)) {
      return '';
    }
    return changeBaseURL(change.project, change._number, patchNum) +
        '/archive?format=' + format;
  }

  _computeSchemes(change, patchNum) {
    // Polymer 2: check for undefined
    if ([change, patchNum].includes(undefined)) {
      return [];
    }

    for (const rev of Object.values(change.revisions || {})) {
      if (patchNumEquals(rev._number, patchNum)) {
        const fetch = rev.fetch;
        if (fetch) {
          return Object.keys(fetch).sort();
        }
        break;
      }
    }
    return [];
  }

  _computePatchSetQuantity(revisions) {
    if (!revisions) { return 0; }
    return Object.keys(revisions).length;
  }

  _handleCloseTap(e) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('close', {
      composed: true, bubbles: true,
    }));
  }

  _schemesChanged(schemes) {
    if (schemes.length === 0) { return; }
    if (!schemes.includes(this._selectedScheme)) {
      this._selectedScheme = schemes.sort()[0];
    }
  }

  _computeShowDownloadCommands(schemes) {
    return schemes.length ? '' : 'hidden';
  }
}

customElements.define(GrDownloadDialog.is, GrDownloadDialog);
