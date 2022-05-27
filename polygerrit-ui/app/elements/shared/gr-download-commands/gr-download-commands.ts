/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Subscription} from 'rxjs';
import '@polymer/paper-tabs/paper-tab';
import '@polymer/paper-tabs/paper-tabs';
import '../gr-shell-command/gr-shell-command';
import {getAppContext} from '../../../services/app-context';
import {queryAndAssert} from '../../../utils/common-util';
import {GrShellCommand} from '../gr-shell-command/gr-shell-command';
import {paperStyles} from '../../../styles/gr-paper-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html, css} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {fire} from '../../../utils/event-util';
import {BindValueChangeEvent} from '../../../types/events';

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
  // TODO(TS): maybe default to [] as only used in dom-repeat
  @property({type: Array})
  commands?: Command[];

  // private but used in test
  @state() loggedIn = false;

  @property({type: Array})
  schemes: string[] = [];

  @property({type: String})
  selectedScheme?: string;

  @property({type: Boolean, attribute: 'show-keyboard-shortcut-tooltips'})
  showKeyboardShortcutTooltips = false;

  private readonly restApiService = getAppContext().restApiService;

  // Private but used in tests.
  readonly userModel = getAppContext().userModel;

  private subscriptions: Subscription[] = [];

  override connectedCallback() {
    super.connectedCallback();
    this.restApiService.getLoggedIn().then(loggedIn => {
      this.loggedIn = loggedIn;
    });
    this.subscriptions.push(
      this.userModel.preferences$.subscribe(prefs => {
        if (prefs?.download_scheme) {
          // Note (issue 5180): normalize the download scheme with lower-case.
          this.selectedScheme = prefs.download_scheme.toLowerCase();
          fire(this, 'selected-scheme-changed', {value: this.selectedScheme});
        }
      })
    );
  }

  override disconnectedCallback() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
    super.disconnectedCallback();
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
        }
        label,
        input {
          display: block;
        }
        label {
          font-weight: var(--font-weight-bold);
        }
        .schemes {
          display: flex;
          justify-content: space-between;
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
      ${this.renderCommands()}
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
        this.userModel.updatePreferences({
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
