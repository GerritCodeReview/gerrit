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
import '../../../styles/gr-font-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-download-commands/gr-download-commands';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-download-dialog_html';
import {changeBaseURL, getRevisionKey} from '../../../utils/change-util';
import {customElement, property, computed, observe} from '@polymer/decorators';
import {
  ChangeInfo,
  DownloadInfo,
  PatchSetNum,
  RevisionInfo,
} from '../../../types/common';
import {GrDownloadCommands} from '../../shared/gr-download-commands/gr-download-commands';
import {GrButton} from '../../shared/gr-button/gr-button';
import {hasOwnProperty} from '../../../utils/common-util';
import {GrOverlayStops} from '../../shared/gr-overlay/gr-overlay';
import {fireAlert, fireEvent} from '../../../utils/event-util';
import {addShortcut} from '../../../utils/dom-util';

export interface GrDownloadDialog {
  $: {
    download: HTMLAnchorElement;
    downloadCommands: GrDownloadCommands;
    closeButton: GrButton;
  };
}

@customElement('gr-download-dialog')
export class GrDownloadDialog extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the user presses the close button.
   *
   * @event close
   */

  @property({type: Object})
  change: ChangeInfo | undefined;

  @property({type: Object})
  config?: DownloadInfo;

  @property({type: String})
  patchNum: PatchSetNum | undefined;

  @property({type: String})
  _selectedScheme?: string;

  /** Called in disconnectedCallback. */
  private cleanups: (() => void)[] = [];

  override disconnectedCallback() {
    super.disconnectedCallback();
    for (const cleanup of this.cleanups) cleanup();
    this.cleanups = [];
  }

  override connectedCallback() {
    super.connectedCallback();
    for (const key of ['1', '2', '3', '4', '5']) {
      this.cleanups.push(
        addShortcut(this, {key}, e => this._handleNumberKey(e))
      );
    }
  }

  @computed('change', 'patchNum')
  get _schemes() {
    // Polymer 2: check for undefined
    if (this.change === undefined || this.patchNum === undefined) {
      return [];
    }

    for (const rev of Object.values(this.change.revisions || {})) {
      if (rev._number === this.patchNum) {
        const fetch = rev.fetch;
        if (fetch) {
          return Object.keys(fetch).sort();
        }
        break;
      }
    }
    return [];
  }

  _handleNumberKey(e: KeyboardEvent) {
    const index = Number(e.key) - 1;
    const commands = this._computeDownloadCommands(
      this.change,
      this.patchNum,
      this._selectedScheme
    );
    if (index > commands.length) return;
    navigator.clipboard.writeText(commands[index].command).then(() => {
      fireAlert(this, `${commands[index].title} command copied to clipboard`);
      fireEvent(this, 'close');
    });
  }

  override ready() {
    super.ready();
    this._ensureAttribute('role', 'dialog');
  }

  override focus() {
    if (this._schemes.length) {
      this.$.downloadCommands.focusOnCopy();
    } else {
      this.$.download.focus();
    }
  }

  getFocusStops(): GrOverlayStops {
    return {
      start: this.$.downloadCommands.$.downloadTabs,
      end: this.$.closeButton,
    };
  }

  _computeDownloadCommands(
    change?: ChangeInfo,
    patchNum?: PatchSetNum,
    selectedScheme?: string
  ) {
    let commandObj;
    if (!change || !selectedScheme) return [];
    for (const rev of Object.values(change.revisions || {})) {
      if (
        rev._number === patchNum &&
        rev &&
        rev.fetch &&
        hasOwnProperty(rev.fetch, selectedScheme)
      ) {
        commandObj = rev.fetch[selectedScheme].commands;
        break;
      }
    }
    const commands = [];
    for (const [title, command] of Object.entries(commandObj ?? {})) {
      commands.push({title, command});
    }
    return commands;
  }

  _computeZipDownloadLink(change?: ChangeInfo, patchNum?: PatchSetNum) {
    return this._computeDownloadLink(change, patchNum, true);
  }

  _computeZipDownloadFilename(change?: ChangeInfo, patchNum?: PatchSetNum) {
    return this._computeDownloadFilename(change, patchNum, true);
  }

  _computeDownloadLink(
    change?: ChangeInfo,
    patchNum?: PatchSetNum,
    zip?: boolean
  ) {
    // Polymer 2: check for undefined
    if (change === undefined || patchNum === undefined) {
      return '';
    }
    return (
      changeBaseURL(change.project, change._number, patchNum) +
      '/patch?' +
      (zip ? 'zip' : 'download')
    );
  }

  _computeDownloadFilename(
    change?: ChangeInfo,
    patchNum?: PatchSetNum,
    zip?: boolean
  ) {
    // Polymer 2: check for undefined
    if (change === undefined || patchNum === undefined) {
      return '';
    }

    const rev = getRevisionKey(change, patchNum) ?? '';
    const shortRev = rev.substr(0, 7);

    return shortRev + '.diff.' + (zip ? 'zip' : 'base64');
  }

  _computeHidePatchFile(change?: ChangeInfo, patchNum?: PatchSetNum) {
    // Polymer 2: check for undefined
    if (change === undefined || patchNum === undefined) {
      return false;
    }
    for (const rev of Object.values(change.revisions || {})) {
      if (rev._number === patchNum) {
        const parentLength =
          rev.commit && rev.commit.parents ? rev.commit.parents.length : 0;
        return parentLength === 0 || parentLength > 1;
      }
    }
    return false;
  }

  _computeArchiveDownloadLink(
    change?: ChangeInfo,
    patchNum?: PatchSetNum,
    format?: string
  ) {
    // Polymer 2: check for undefined
    if (
      change === undefined ||
      patchNum === undefined ||
      format === undefined
    ) {
      return '';
    }
    return (
      changeBaseURL(change.project, change._number, patchNum) +
      '/archive?format=' +
      format
    );
  }

  _computePatchSetQuantity(revisions?: {[revisionId: string]: RevisionInfo}) {
    if (!revisions) {
      return 0;
    }
    return Object.keys(revisions).length;
  }

  _handleCloseTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fireEvent(this, 'close');
  }

  @observe('_schemes')
  _schemesChanged(schemes: string[]) {
    if (schemes.length === 0) {
      return;
    }
    if (!this._selectedScheme || !schemes.includes(this._selectedScheme)) {
      this._selectedScheme = schemes.sort()[0];
    }
  }

  _computeShowDownloadCommands(schemes: string[]) {
    return schemes.length ? '' : 'hidden';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-download-dialog': GrDownloadDialog;
  }
}
