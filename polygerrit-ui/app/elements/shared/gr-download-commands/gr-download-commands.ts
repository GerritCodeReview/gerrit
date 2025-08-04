/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-shell-command/gr-shell-command';
import {queryAndAssert} from '../../../utils/common-util';
import {GrShellCommand} from '../gr-shell-command/gr-shell-command';
import {materialStyles} from '../../../styles/gr-material-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, nothing} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {fire} from '../../../utils/event-util';
import {BindValueChangeEvent} from '../../../types/events';
import {resolve} from '../../../models/dependency';
import {userModelToken} from '../../../models/user/user-model';
import {subscribe} from '../../lit/subscription-controller';
import '@material/web/tabs/secondary-tab';
import '@material/web/tabs/tabs';
import {MdTabs} from '@material/web/tabs/tabs';

declare global {
  interface HTMLElementEventMap {
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
      materialStyles,
      sharedStyles,
      css`
        md-tabs {
          height: 3rem;
          margin-bottom: var(--spacing-m);
        }
        md-secondary-tab {
          max-width: 15rem;
          text-transform: uppercase;
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
    // md-tabs won't work if the index is -1, which happens on initial
    // page load and then corrects its self.
    if (selectedIndex < 0) return nothing;
    return html`
      <md-tabs
        id="downloadTabs"
        class=${this.computeShowTabs()}
        .activeTabIndex=${selectedIndex}
        @change=${this.handleTabChange}
      >
        ${this.schemes.map(scheme => this.renderMdSecondaryTab(scheme))}
      </md-tabs>
    `;
  }

  private renderDescription() {
    if (!this.description) return;
    return html`<div class="description">${this.description}</div>`;
  }

  private renderMdSecondaryTab(scheme: string) {
    return html`
      <md-secondary-tab data-scheme=${scheme}>${scheme}</md-secondary-tab>
    `;
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

  private handleTabChange(e: Event) {
    const activeTabIndex = (e.target as MdTabs).activeTabIndex;
    const scheme = this.schemes[activeTabIndex];
    if (scheme && scheme !== this.selectedScheme) {
      this.selectedScheme = scheme;
      fire(this, 'selected-scheme-changed', {value: scheme});
      if (this.loggedIn) {
        this.getUserModel().updatePreferences({
          download_scheme: this.selectedScheme,
        });
      }
    }
  }

  private computeTooltip(index: number) {
    return index <= 8 && this.showKeyboardShortcutTooltips
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
