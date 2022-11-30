/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-button/gr-button';
import '../gr-key-binding-display/gr-key-binding-display';
import {sharedStyles} from '../../../styles/shared-styles';
import {fontStyles} from '../../../styles/gr-font-styles';
import {LitElement, css, html} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {
  ShortcutSection,
  SectionView,
  shortcutsServiceToken,
  ShortcutViewListener,
} from '../../../services/shortcuts/shortcuts-service';
import {resolve} from '../../../models/dependency';

declare global {
  interface HTMLElementTagNameMap {
    'gr-keyboard-shortcuts-dialog': GrKeyboardShortcutsDialog;
  }
}

interface SectionShortcut {
  section: ShortcutSection;
  shortcuts?: SectionView;
}

@customElement('gr-keyboard-shortcuts-dialog')
export class GrKeyboardShortcutsDialog extends LitElement {
  /**
   * Fired when the user presses the close button.
   *
   * @event close
   */

  // private but used in tests
  @state() left?: SectionShortcut[];

  // private but used in tests
  @state() right?: SectionShortcut[];

  private readonly shortcutListener: ShortcutViewListener;

  private readonly getShortcutsService = resolve(this, shortcutsServiceToken);

  constructor() {
    super();
    this.shortcutListener = (d?: Map<ShortcutSection, SectionView>) =>
      this.onDirectoryUpdated(d);
  }

  static override get styles() {
    return [
      sharedStyles,
      fontStyles,
      css`
        :host {
          display: block;
          max-height: 100vh;
          min-width: 60vw;
        }
        main {
          display: flex;
          padding: 0 var(--spacing-xxl) var(--spacing-xxl);
        }
        .column {
          flex: 50%;
        }
        header {
          padding: var(--spacing-l) var(--spacing-xxl);
          align-items: center;
          border-bottom: 1px solid var(--border-color);
          display: flex;
          justify-content: space-between;
        }
        table caption {
          padding: var(--spacing-l) var(--spacing-s);
        }
        td {
          padding: var(--spacing-xs) 0;
          vertical-align: middle;
          width: 200px;
        }
        td:first-child,
        th:first-child {
          padding-right: var(--spacing-m);
          text-align: right;
        }
        td:last-child,
        th:last-child {
          text-align: left;
        }
        td:last-child {
          color: var(--deemphasized-text-color);
        }
        th {
          color: var(--deemphasized-text-color);
        }
        .modifier {
          font-weight: var(--font-weight-normal);
        }
      `,
    ];
  }

  override render() {
    return html`
      <header>
        <h3 class="heading-2">Keyboard shortcuts</h3>
        <gr-button link="" @click=${this.handleCloseTap}>Close</gr-button>
      </header>
      <main>
        <div class="column">
          ${this.left?.map(section => this.renderSection(section))}
        </div>
        <div class="column">
          ${this.right?.map(section => this.renderSection(section))}
        </div>
      </main>
      <footer></footer>
    `;
  }

  private renderSection(section: SectionShortcut) {
    return html`<table>
      <caption class="heading-3">
        ${section.section}
      </caption>
      <thead>
        <tr>
          <th><strong>Action</strong></th>
          <th><strong>Key</strong></th>
        </tr>
      </thead>
      <tbody>
        ${section.shortcuts?.map(
          shortcut => html`<tr>
            <td>${shortcut.text}</td>
            <td>
              <gr-key-binding-display .binding=${shortcut.binding}>
              </gr-key-binding-display>
            </td>
          </tr>`
        )}
      </tbody>
    </table>`;
  }

  override connectedCallback() {
    super.connectedCallback();
    this.getShortcutsService().addListener(this.shortcutListener);
  }

  override disconnectedCallback() {
    this.getShortcutsService().removeListener(this.shortcutListener);
    super.disconnectedCallback();
  }

  private handleCloseTap(e: MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('close', {
        composed: true,
        bubbles: false,
      })
    );
  }

  onDirectoryUpdated(directory?: Map<ShortcutSection, SectionView>) {
    if (!directory) {
      return;
    }
    const left = [] as SectionShortcut[];
    const right = [] as SectionShortcut[];

    if (directory.has(ShortcutSection.EVERYWHERE)) {
      left.push({
        section: ShortcutSection.EVERYWHERE,
        shortcuts: directory.get(ShortcutSection.EVERYWHERE),
      });
    }

    if (directory.has(ShortcutSection.NAVIGATION)) {
      left.push({
        section: ShortcutSection.NAVIGATION,
        shortcuts: directory.get(ShortcutSection.NAVIGATION),
      });
    }

    if (directory.has(ShortcutSection.ACTIONS)) {
      right.push({
        section: ShortcutSection.ACTIONS,
        shortcuts: directory.get(ShortcutSection.ACTIONS),
      });
    }

    if (directory.has(ShortcutSection.REPLY_DIALOG)) {
      left.push({
        section: ShortcutSection.REPLY_DIALOG,
        shortcuts: directory.get(ShortcutSection.REPLY_DIALOG),
      });
    }

    if (directory.has(ShortcutSection.FILE_LIST)) {
      left.push({
        section: ShortcutSection.FILE_LIST,
        shortcuts: directory.get(ShortcutSection.FILE_LIST),
      });
    }

    if (directory.has(ShortcutSection.DIFFS)) {
      right.push({
        section: ShortcutSection.DIFFS,
        shortcuts: directory.get(ShortcutSection.DIFFS),
      });
    }

    this.right = right;
    this.left = left;
  }
}
