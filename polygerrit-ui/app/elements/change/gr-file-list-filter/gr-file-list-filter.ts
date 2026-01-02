/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {fire} from '../../../utils/event-util';
import {sharedStyles} from '../../../styles/shared-styles';
import '@material/web/menu/menu';
import {MdMenu} from '@material/web/menu/menu';
import '@material/web/checkbox/checkbox';
import {MdCheckbox} from '@material/web/checkbox/checkbox';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-tooltip-content/gr-tooltip-content';

@customElement('gr-file-list-filter')
export class GrFileListFilter extends LitElement {
  /**
   * significant parts of this component are based on gr-copy-links
   */
  @property({type: Array})
  allFileExtensions: string[] = [];

  @property({type: Object})
  fileExtensionCounts: {[extension: string]: number} = {};

  @property({type: Array})
  hiddenFileExtensions: string[] = [];

  @property({type: String})
  horizontalAlign: 'left' | 'right' = 'left';

  @property({type: Number})
  verticalOffset = 10;

  @state() isDropdownOpen = false;

  @query('#filterMenu')
  filterMenu?: MdMenu;

  static override get styles() {
    return [
      sharedStyles,
      css`
        .filterContainer {
          display: flex;
          align-items: center;
          position: relative;
        }
        .filterBtn.active {
          --gr-button-color: var(--primary-button-background-color);
          font-weight: var(--font-weight-bold);
        }
        md-menu {
          --md-menu-container-color: var(--dialog-background-color);
          --md-menu-top-space: 0px;
          --md-menu-bottom-space: 0px;
        }
        .dropdown-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: var(--spacing-s) var(--spacing-l);
          border-bottom: 1px solid var(--border-color);
          font-size: var(--font-size-small);
          font-weight: var(--font-weight-medium);
          color: var(--deemphasized-text-color);
          min-width: 200px;
        }
        .filter-actions {
          display: flex;
          align-items: center;
          gap: var(--spacing-xs);
        }
        .filter-actions gr-button {
          --gr-button-padding: 0 4px;
          font-size: var(--font-size-small);
        }
        .action-separator {
          color: var(--border-color);
        }
        .dropdown-content {
          padding: var(--spacing-xs) 0;
          max-height: 70vh;
          overflow-y: auto;
        }
        .filter-item {
          display: flex;
          align-items: center;
          padding: 0 var(--spacing-l);
          height: 32px;
          cursor: pointer;
          white-space: nowrap;
        }
        .filter-item:hover {
          background-color: var(--hover-background-color);
        }
        md-checkbox {
          --md-checkbox-state-layer-size: 32px;
          margin-right: var(--spacing-m);
        }
        .ext-count {
          color: var(--deemphasized-text-color);
          margin-left: var(--spacing-s);
        }
      `,
    ];
  }

  private computeButtonText(): string {
    if (this.hiddenFileExtensions.length === 0) {
      return 'File Filter';
    }
    const visibleCount =
      this.allFileExtensions.length - this.hiddenFileExtensions.length;
    return `File Filter (${visibleCount}/${this.allFileExtensions.length})`;
  }

  private computeTooltip(): string {
    if (this.hiddenFileExtensions.length === 0) {
      return 'Filter files by extension';
    }
    const count = this.hiddenFileExtensions.length;
    return `Filter files by extension (${count} extension${
      count > 1 ? 's' : ''
    } hidden)`;
  }

  override render() {
    if (this.allFileExtensions.length === 0) return nothing;
    const isFilterActive = this.hiddenFileExtensions.length > 0;
    return html`
      <span class="filterContainer">
        <gr-tooltip-content has-tooltip title=${this.computeTooltip()}>
          <gr-button
            id="filterBtn"
            link
            class=${isFilterActive ? 'filterBtn active' : 'filterBtn'}
            @click=${this.handleFilterTap}
          >
            ${this.computeButtonText()}
          </gr-button>
        </gr-tooltip-content>
        <md-menu
          id="filterMenu"
          .menuCorner=${this.horizontalAlign === 'left'
            ? 'start-start'
            : 'end-start'}
          .anchorCorner=${this.horizontalAlign === 'left'
            ? 'start-end'
            : 'end-end'}
          .yOffset=${this.verticalOffset}
          default-focus="none"
          tabindex="-1"
          ?quick=${true}
          @opened=${() => {
            this.isDropdownOpen = true;
          }}
          @closed=${() => {
            this.isDropdownOpen = false;
          }}
        >
          <div class="dropdown-header">
            <span>Filter by extension</span>
            <div class="filter-actions">
              <gr-button
                link
                class="selectAllBtn"
                @click=${this.handleSelectAll}
              >
                All
              </gr-button>
              <span class="action-separator">|</span>
              <gr-button
                link
                class="deselectAllBtn"
                @click=${this.handleDeselectAll}
              >
                None
              </gr-button>
            </div>
          </div>
          <div class="dropdown-content">
            ${this.allFileExtensions.map(ext =>
              this.renderExtensionCheckbox(ext)
            )}
          </div>
        </md-menu>
      </span>
    `;
  }

  private renderExtensionCheckbox(ext: string) {
    const isChecked = !this.hiddenFileExtensions.includes(ext);
    const count = this.fileExtensionCounts[ext] ?? 0;
    const label = ext === '' ? 'No extension' : ext;
    return html`
      <div
        class="filter-item"
        @click=${(e: Event) => this.handleExtensionRowClick(e, ext)}
      >
        <md-checkbox
          ?checked=${isChecked}
          @change=${(e: Event) => this.handleExtensionChange(e, ext)}
          touch-target="wrapper"
        ></md-checkbox>
        <span>${label} <span class="ext-count">(${count})</span></span>
      </div>
    `;
  }

  private handleFilterTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.toggleDropdown(e.currentTarget as HTMLElement);
  }

  toggleDropdown(button?: HTMLElement) {
    if (button) {
      this.filterMenu!.anchorElement = button;
    }
    this.isDropdownOpen ? this.closeDropdown() : this.openDropdown();
  }

  private closeDropdown() {
    this.filterMenu?.close();
  }

  openDropdown(button?: HTMLElement) {
    if (button) {
      this.filterMenu!.anchorElement = button;
    }
    this.filterMenu?.show();
  }

  private handleSelectAll(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fire(this, 'hidden-file-extensions-changed', {value: []});
  }

  private handleDeselectAll(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fire(this, 'hidden-file-extensions-changed', {
      value: [...this.allFileExtensions],
    });
  }

  private handleExtensionRowClick(e: Event, ext: string) {
    if (e.target instanceof MdCheckbox) return;
    e.preventDefault();
    e.stopPropagation();
    this.toggleExtension(ext);
  }

  private handleExtensionChange(_e: Event, ext: string) {
    this.toggleExtension(ext);
  }

  private toggleExtension(ext: string) {
    let newHidden = [...this.hiddenFileExtensions];
    if (newHidden.includes(ext)) {
      newHidden = newHidden.filter(e => e !== ext);
    } else {
      newHidden.push(ext);
    }
    fire(this, 'hidden-file-extensions-changed', {value: newHidden});
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-file-list-filter': GrFileListFilter;
  }
}
