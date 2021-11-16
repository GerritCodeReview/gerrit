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
import '../../shared/gr-button/gr-button';
import '../gr-key-binding-display/gr-key-binding-display';
import {sharedStyles} from '../../../styles/shared-styles';
import {fontStyles} from '../../../styles/gr-font-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {
  ShortcutSection,
  SectionView,
} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {getAppContext} from '../../../services/app-context';
import {ShortcutViewListener} from '../../../services/shortcuts/shortcuts-service';

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

  @property({type: Array})
  _left?: SectionShortcut[];

  @property({type: Array})
  _right?: SectionShortcut[];

  private readonly shortcutListener: ShortcutViewListener;

  private readonly shortcuts = getAppContext().shortcutsService;

  constructor() {
    super();
    this.shortcutListener = (d?: Map<ShortcutSection, SectionView>) =>
      this._onDirectoryUpdated(d);
  }

  static override get styles() {
    return [
      sharedStyles,
      fontStyles,
      css`
        :host {
          display: block;
          max-height: 100vh;
          overflow-y: auto;
        }
        header {
          padding: var(--spacing-l);
        }
        main {
          display: flex;
          padding: 0 var(--spacing-xxl) var(--spacing-xxl);
        }
        .column {
          flex: 50%;
        }
        header {
          align-items: center;
          border-bottom: 1px solid var(--border-color);
          display: flex;
          justify-content: space-between;
        }
        table caption {
          font-weight: var(--font-weight-bold);
          padding-top: var(--spacing-l);
          text-align: left;
        }
        tr {
          height: 32px;
        }
        td {
          padding: var(--spacing-xs) 0;
        }
        td:first-child,
        th:first-child {
          padding-right: var(--spacing-m);
          text-align: right;
          width: 160px;
          color: var(--deemphasized-text-color);
        }
        td:second-child {
          min-width: 200px;
        }
        th {
          color: var(--deemphasized-text-color);
          text-align: left;
        }
        .header {
          font-weight: var(--font-weight-bold);
          padding-top: var(--spacing-l);
        }
        .modifier {
          font-weight: var(--font-weight-normal);
        }
      `,
    ];
  }

  override render() {
    return html`<header>
        <h3 class="heading-3">Keyboard shortcuts</h3>
        <gr-button link="" @click=${this.handleCloseTap}>Close</gr-button>
      </header>
      <main>
        <div class="column">
          ${this._left?.map(section => this.renderSection(section))}
        </div>
        <div class="column">
          ${this._right?.map(section => this.renderSection(section))}
        </div>
      </main>
      <footer></footer>`;
  }

  private renderSection(section: SectionShortcut) {
    return html`<table>
      <caption>
        ${section.section}
      </caption>
      <thead>
        <tr>
          <th>Key</th>
          <th>Action</th>
        </tr>
      </thead>
      <tbody>
        ${section.shortcuts?.map(
          shortcut => html`<tr>
            <td>
              <gr-key-binding-display .binding=${shortcut.binding}>
              </gr-key-binding-display>
            </td>
            <td>${shortcut.text}</td>
          </tr>`
        )}
      </tbody>
    </table>`;
  }

  override connectedCallback() {
    super.connectedCallback();
    this.shortcuts.addListener(this.shortcutListener);
  }

  override disconnectedCallback() {
    this.shortcuts.removeListener(this.shortcutListener);
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

  _onDirectoryUpdated(directory?: Map<ShortcutSection, SectionView>) {
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
      right.push({
        section: ShortcutSection.REPLY_DIALOG,
        shortcuts: directory.get(ShortcutSection.REPLY_DIALOG),
      });
    }

    if (directory.has(ShortcutSection.FILE_LIST)) {
      right.push({
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

    this._right = right;
    this._left = left;
  }
}
