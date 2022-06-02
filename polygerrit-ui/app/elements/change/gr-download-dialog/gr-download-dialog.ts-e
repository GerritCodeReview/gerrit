/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-download-commands/gr-download-commands';
import {changeBaseURL, getRevisionKey} from '../../../utils/change-util';
import {ChangeInfo, DownloadInfo, PatchSetNum} from '../../../types/common';
import {GrDownloadCommands} from '../../shared/gr-download-commands/gr-download-commands';
import {GrButton} from '../../shared/gr-button/gr-button';
import {hasOwnProperty, queryAndAssert} from '../../../utils/common-util';
import {GrOverlayStops} from '../../shared/gr-overlay/gr-overlay';
import {fireAlert, fireEvent} from '../../../utils/event-util';
import {addShortcut} from '../../../utils/dom-util';
import {fontStyles} from '../../../styles/gr-font-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, html, css} from 'lit';
import {customElement, property, state, query} from 'lit/decorators';
import {assertIsDefined} from '../../../utils/common-util';
import {PaperTabsElement} from '@polymer/paper-tabs/paper-tabs';
import {BindValueChangeEvent} from '../../../types/events';

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

  @property({type: Object})
  change: ChangeInfo | undefined;

  @property({type: Object})
  config?: DownloadInfo;

  @property({type: String})
  patchNum: PatchSetNum | undefined;

  @state() private selectedScheme?: string;

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
        addShortcut(this, {key}, e => this.handleNumberKey(e))
      );
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
          <a id="download" .href=${this.computeDownloadLink()} download>
            ${this.computeDownloadFilename()}
          </a>
          <a .href=${this.computeDownloadLink(true)} download>
            ${this.computeDownloadFilename(true)}
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

  private handleNumberKey(e: KeyboardEvent) {
    const index = Number(e.key) - 1;
    const commands = this.computeDownloadCommands();
    if (index > commands.length) return;
    navigator.clipboard.writeText(commands[index].command).then(() => {
      fireAlert(this, `${commands[index].title} command copied to clipboard`);
      fireEvent(this, 'close');
    });
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

  getFocusStops(): GrOverlayStops {
    assertIsDefined(this.downloadCommands, 'downloadCommands');
    assertIsDefined(this.closeButton, 'closeButton');
    const downloadTabs = queryAndAssert<PaperTabsElement>(
      this.downloadCommands,
      '#downloadTabs'
    );
    return {
      start: downloadTabs,
      end: this.closeButton,
    };
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

  private computeDownloadLink(zip?: boolean) {
    if (this.change === undefined || this.patchNum === undefined) {
      return '';
    }
    return (
      changeBaseURL(this.change.project, this.change._number, this.patchNum) +
      '/patch?' +
      (zip ? 'zip' : 'download')
    );
  }

  private computeDownloadFilename(zip?: boolean) {
    if (this.change === undefined || this.patchNum === undefined) {
      return '';
    }

    const rev = getRevisionKey(this.change, this.patchNum) ?? '';
    const shortRev = rev.substr(0, 7);

    return shortRev + '.diff.' + (zip ? 'zip' : 'base64');
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
    fireEvent(this, 'close');
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
