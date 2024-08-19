/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-download-commands/gr-download-commands';
import {changeBaseURL, getRevisionKey} from '../../../utils/change-util';
import {DownloadInfo, PatchSetNum} from '../../../types/common';
import {GrDownloadCommands} from '../../shared/gr-download-commands/gr-download-commands';
import {GrButton} from '../../shared/gr-button/gr-button';
import {copyToClipbard, hasOwnProperty} from '../../../utils/common-util';
import {fire} from '../../../utils/event-util';
import {fontStyles} from '../../../styles/gr-font-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, html, css} from 'lit';
import {customElement, state, query} from 'lit/decorators.js';
import {assertIsDefined} from '../../../utils/common-util';
import {BindValueChangeEvent} from '../../../types/events';
import {ShortcutController} from '../../lit/shortcut-controller';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {changeModelToken} from '../../../models/change/change-model';
import {ParsedChangeInfo} from '../../../types/types';
import {configModelToken} from '../../../models/config/config-model';
import {shorten} from '../../../utils/patch-set-util';

type DownloadKind = 'zip' | 'raw' | 'base64';

@customElement('gr-download-dialog')
export class GrDownloadDialog extends LitElement {
  /**
   * Fired when the user presses the close button.
   *
   * @event close
   */

  @query('#download') protected download?: HTMLAnchorElement;

  @query('#downloadCommands') protected downloadCommands?: GrDownloadCommands;

  @query('#closeButton') protected closeButton?: GrButton;

  @state() change?: ParsedChangeInfo;

  @state() config?: DownloadInfo;

  @state() patchNum?: PatchSetNum;

  @state() selectedScheme?: string;

  private readonly shortcuts = new ShortcutController(this);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getConfigModel = resolve(this, configModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChangeModel().change$,
      x => (this.change = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().patchNum$,
      x => (this.patchNum = x)
    );
    subscribe(
      this,
      () => this.getConfigModel().download$,
      x => (this.config = x)
    );
    for (const key of ['1', '2', '3', '4', '5']) {
      this.shortcuts.addLocal({key}, e => this.handleNumberKey(e));
    }
  }

  static override get styles() {
    return [
      fontStyles,
      sharedStyles,
      css`
        :host {
          display: block;
          padding: var(--spacing-m) 0;
        }
        section {
          display: flex;
          padding: var(--spacing-m) var(--spacing-xl);
        }
        .flexContainer {
          display: flex;
          justify-content: space-between;
          padding-top: var(--spacing-m);
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
          padding-bottom: var(--spacing-m);
        }
        .patchFiles {
          margin-right: var(--spacing-xxl);
        }
        .patchFiles a,
        .archives a {
          display: inline-block;
          margin-right: var(--spacing-l);
        }
        .patchFiles a:last-of-type,
        .archives a:last-of-type {
          margin-right: 0;
        }
        gr-download-commands {
          width: min(80vw, 1200px);
        }
      `,
    ];
  }

  override render() {
    const revisions = this.change?.revisions;
    return html`
      <section>
        <h3 class="heading-3">
          Patch set ${this.patchNum} of
          ${revisions ? Object.keys(revisions).length : 0}
        </h3>
      </section>
      ${this.renderDownloadCommands()}
      <section class="flexContainer">
        ${this.renderPatchFiles()} ${this.renderArchives()}
      </section>
      <section class="footer">
        <span class="closeButtonContainer">
          <gr-button
            id="closeButton"
            link
            @click=${(e: Event) => {
              this.handleCloseTap(e);
            }}
            >Close</gr-button
          >
        </span>
      </section>
    `;
  }

  private renderDownloadCommands() {
    const cssClass = this.schemes.length ? '' : 'hidden';

    return html`
      <section class=${cssClass}>
        <gr-download-commands
          id="downloadCommands"
          .commands=${this.computeDownloadCommands()}
          .schemes=${this.schemes}
          .selectedScheme=${this.selectedScheme}
          show-keyboard-shortcut-tooltips
          @selected-scheme-changed=${(e: BindValueChangeEvent) => {
            this.selectedScheme = e.detail.value;
          }}
          @item-copied=${(e: Event) => {
            this.handleCloseTap(e);
          }}
        ></gr-download-commands>
      </section>
    `;
  }

  private renderPatchFiles() {
    if (this.computeHidePatchFile()) return;

    return html`
      <div class="patchFiles">
        <label>Patch file</label>
        <div>
          <a id="download" .href=${this.computeDownloadLink('raw')} download>
            ${this.computeDownloadFilename('raw')}
          </a>
          <a .href=${this.computeDownloadLink('base64')} download>
            ${this.computeDownloadFilename('base64')}
          </a>
          <a .href=${this.computeDownloadLink('zip')} download>
            ${this.computeDownloadFilename('zip')}
          </a>
        </div>
      </div>
    `;
  }

  private renderArchives() {
    if (!this.config?.archives.length) return;

    return html`
      <div class="archivesContainer">
        <label>Archive</label>
        <div id="archives" class="archives">
          ${this.config.archives.map(format => this.renderArchivesLink(format))}
        </div>
      </div>
    `;
  }

  private renderArchivesLink(format: string) {
    return html`
      <a .href=${this.computeArchiveDownloadLink(format)} download>
        ${format}
      </a>
    `;
  }

  override firstUpdated(changedProperties: PropertyValues) {
    super.firstUpdated(changedProperties);
    if (!this.getAttribute('role')) this.setAttribute('role', 'dialog');
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('change') || changedProperties.has('patchNum')) {
      this.schemesChanged();
    }
  }

  get schemes() {
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

  private async handleNumberKey(e: KeyboardEvent) {
    const index = Number(e.key) - 1;
    const commands = this.computeDownloadCommands();
    if (index > commands.length) return;
    await copyToClipbard(
      commands[index].command,
      `${commands[index].title} command`
    );
    fire(this, 'close', {});
  }

  override focus() {
    if (this.schemes.length) {
      assertIsDefined(this.downloadCommands, 'downloadCommands');
      this.updateComplete.then(() => this.downloadCommands!.focusOnCopy());
    } else {
      assertIsDefined(this.download, 'download');
      this.download.focus();
    }
  }

  private computeDownloadCommands() {
    let commandObj;
    if (!this.change || !this.selectedScheme) return [];
    for (const rev of Object.values(this.change.revisions || {})) {
      if (
        rev._number === this.patchNum &&
        rev &&
        rev.fetch &&
        hasOwnProperty(rev.fetch, this.selectedScheme)
      ) {
        commandObj = rev.fetch[this.selectedScheme].commands;
        break;
      }
    }
    const commands = [];
    for (const [title, command] of Object.entries(commandObj ?? {})) {
      commands.push({title, command});
    }
    return commands;
  }

  private computeDownloadLink(kind: DownloadKind) {
    if (this.change === undefined || this.patchNum === undefined) {
      return '';
    }
    let urlParam;
    switch (kind) {
      case 'zip':
        urlParam = '&zip';
        break;
      case 'raw':
        urlParam = '&raw';
        break;
      case 'base64':
        urlParam = '';
        break;
    }
    return (
      changeBaseURL(this.change.project, this.change._number, this.patchNum) +
      '/patch?download' +
      urlParam
    );
  }

  private computeDownloadFilename(kind: DownloadKind) {
    if (this.change === undefined || this.patchNum === undefined) {
      return '';
    }

    let ext;
    switch (kind) {
      case 'zip':
        ext = '.zip';
        break;
      case 'raw':
        ext = '';
        break;
      case 'base64':
        ext = '.base64';
        break;
    }

    const rev = getRevisionKey(this.change, this.patchNum) ?? '';
    return shorten(rev)! + '.diff' + ext;
  }

  // private but used in test
  computeHidePatchFile() {
    if (this.change === undefined || this.patchNum === undefined) {
      return false;
    }
    for (const rev of Object.values(this.change.revisions || {})) {
      if (rev._number === this.patchNum) {
        const parentLength =
          rev.commit && rev.commit.parents ? rev.commit.parents.length : 0;
        return parentLength === 0 || parentLength > 1;
      }
    }
    return false;
  }

  // private but used in test
  computeArchiveDownloadLink(format?: string) {
    if (
      this.change === undefined ||
      this.patchNum === undefined ||
      format === undefined
    ) {
      return '';
    }
    return (
      changeBaseURL(this.change.project, this.change._number, this.patchNum) +
      '/archive?format=' +
      format
    );
  }

  private handleCloseTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fire(this, 'close', {});
  }

  private schemesChanged() {
    if (this.schemes.length === 0) {
      return;
    }
    if (!this.selectedScheme || !this.schemes.includes(this.selectedScheme)) {
      this.selectedScheme = this.schemes.sort()[0];
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-download-dialog': GrDownloadDialog;
  }
}
