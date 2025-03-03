/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/paper-tabs/paper-tab';
import '@polymer/paper-tabs/paper-tabs';
import '../gr-shell-command/gr-shell-command';
import {queryAndAssert} from '../../../utils/common-util';
import {GrShellCommand} from '../gr-shell-command/gr-shell-command';
import {paperStyles} from '../../../styles/gr-paper-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html, css} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {fire} from '../../../utils/event-util';
import {BindValueChangeEvent} from '../../../types/events';
import {resolve} from '../../../models/dependency';
import {userModelToken} from '../../../models/user/user-model';
import {subscribe} from '../../lit/subscription-controller';

declare global {
  interface HTMLElementEventMap {
    'selected-changed': CustomEvent<{value: number}>;
    'selected-scheme-changed': BindValueChangeEvent;
  }
  interface HTMLElementTagNameMap {
    'gr-download-commands': GrDownloadCommands;
  }
}

export interface Command {
  title: string;
  command: string;
}

@customElement('gr-download-commands')
export class GrDownloadCommands extends LitElement {
  @property({type: Array})
  commands: Command[] = [];

  // private but used in test
  @state() loggedIn = false;

  @property({type: Array})
  schemes: string[] = [];

  @property({type: String})
  selectedScheme?: string;

  // description of selected scheme
  @property({type: String})
  description?: string;

  @property({type: Boolean, attribute: 'show-keyboard-shortcut-tooltips'})
  showKeyboardShortcutTooltips = false;

  // Private but used in tests.
  readonly getUserModel = resolve(this, userModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().loggedIn$,
      x => (this.loggedIn = x)
    );
    subscribe(
      this,
      () => this.getUserModel().preferences$,
      prefs => {
        if (prefs?.download_scheme) {
          // Note (issue 5180): normalize the download scheme with lower-case.
          this.selectedScheme = prefs.download_scheme.toLowerCase();
          fire(this, 'selected-scheme-changed', {value: this.selectedScheme});
        }
      }
    );
  }

  static override get styles() {
    return [
      paperStyles,
      sharedStyles,
      css`
        paper-tabs {
          height: 3rem;
          margin-bottom: var(--spacing-m);
          --paper-tabs-selection-bar-color: var(--link-color);
        }
        paper-tab {
          max-width: 15rem;
          text-transform: uppercase;
          --paper-tab-ink: var(--link-color);
          --paper-font-common-base_-_font-family: var(--header-font-family);
          --paper-font-common-base_-_-webkit-font-smoothing: initial;
          --paper-tab-content_-_margin-bottom: var(--spacing-s);
          /* paper-tabs uses 700 here, which can look awkward */
          --paper-tab-content-focused_-_font-weight: var(--font-weight-h3);
          --paper-tab-content-focused_-_background: var(
            --gray-background-focus
          );
          --paper-tab-content-unselected_-_opacity: 1;
          --paper-tab-content-unselected_-_color: var(
            --deemphasized-text-color
          );
        }
        label,
        input {
          display: block;
        }
        label {
          font-weight: var(--font-weight-medium);
        }
        .schemes {
          display: flex;
          justify-content: space-between;
        }
        .description {
          margin-bottom: var(--spacing-m);
        }
        .commands {
          display: flex;
          flex-direction: column;
        }
        gr-shell-command {
          margin-bottom: var(--spacing-m);
        }
        .hidden {
          display: none;
        }
      `,
    ];
  }

  override render() {
    return html`
      <div class="schemes">${this.renderDownloadTabs()}</div>
      ${this.renderDescription()} ${this.renderCommands()}
    `;
  }

  private renderDownloadTabs() {
    const selectedIndex =
      this.schemes.findIndex(scheme => scheme === this.selectedScheme) || 0;
    return html`
      <paper-tabs
        id="downloadTabs"
        class=${this.computeShowTabs()}
        .selected=${selectedIndex}
        @selected-changed=${this.handleTabChange}
      >
        ${this.schemes.map(scheme => this.renderPaperTab(scheme))}
      </paper-tabs>
    `;
  }

  private renderDescription() {
    if (!this.description) return;
    return html`<div class="description">${this.description}</div>`;
  }

  private renderPaperTab(scheme: string) {
    return html` <paper-tab data-scheme=${scheme}>${scheme}</paper-tab> `;
  }

  private renderCommands() {
    return html`
      <div class="commands" ?hidden=${!this.schemes.length}></div>
        ${this.commands?.map((command, index) =>
          this.renderShellCommand(command, index)
        )}
      </div>
    `;
  }

  private renderShellCommand(command: Command, index: number) {
    return html`
      <gr-shell-command
        class=${this.computeClass(command.title)}
        .label=${command.title}
        .command=${command.command}
        .tooltip=${this.computeTooltip(index)}
      ></gr-shell-command>
    `;
  }

  async focusOnCopy() {
    await this.updateComplete;
    await queryAndAssert<GrShellCommand>(
      this,
      'gr-shell-command'
    ).focusOnCopy();
  }

  private handleTabChange = (e: CustomEvent<{value: number}>) => {
    const scheme = this.schemes[e.detail.value];
    if (scheme && scheme !== this.selectedScheme) {
      this.selectedScheme = scheme;
      fire(this, 'selected-scheme-changed', {value: scheme});
      if (this.loggedIn) {
        this.getUserModel().updatePreferences({
          download_scheme: this.selectedScheme,
        });
      }
    }
  };

  private computeTooltip(index: number) {
    return index <= 4 && this.showKeyboardShortcutTooltips
      ? `Keyboard shortcut: ${index + 1}`
      : '';
  }

  private computeShowTabs() {
    return this.schemes.length > 1 ? '' : 'hidden';
  }

  // TODO: maybe unify with strToClassName from dom-util
  private computeClass(title: string) {
    // Only retain [a-z] chars, so "Cherry Pick" becomes "cherrypick".
    return '_label_' + title.replace(/[^a-z]+/gi, '').toLowerCase();
  }
}
