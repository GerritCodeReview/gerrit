/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {getAppContext} from '../../../services/app-context';
import {fireShowPrimaryTab} from '../../../utils/event-util';
import {PrimaryTab} from '../../../constants/constants';
import {CommentTabState} from '../../../types/events';
import {fontStyles} from '../../../styles/gr-font-styles';

export enum SummaryChipStyles {
  INFO = 'info',
  WARNING = 'warning',
  CHECK = 'check',
  UNDEFINED = '',
}

@customElement('gr-summary-chip')
export class GrSummaryChip extends LitElement {
  @property()
  icon = '';

  @property()
  styleType = SummaryChipStyles.UNDEFINED;

  @property()
  category?: CommentTabState;

  private readonly reporting = getAppContext().reportingService;

  static override get styles() {
    return [
      sharedStyles,
      fontStyles,
      css`
        .summaryChip {
          color: var(--chip-color);
          cursor: pointer;
          display: inline-block;
          padding: var(--spacing-xxs) var(--spacing-m) var(--spacing-xxs)
            var(--spacing-s);
          margin-right: var(--spacing-s);
          border-radius: 12px;
          border: 1px solid gray;
          vertical-align: top;
          /* centered position of 20px chips in 24px line-height inline flow */
          vertical-align: top;
          position: relative;
          top: 2px;
        }
        iron-icon {
          width: var(--line-height-small);
          height: var(--line-height-small);
          vertical-align: top;
        }
        .summaryChip.warning {
          border-color: var(--warning-foreground);
          background: var(--warning-background);
        }
        .summaryChip.warning:hover {
          background: var(--warning-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .summaryChip.warning:focus-within {
          background: var(--warning-background-focus);
        }
        .summaryChip.warning iron-icon {
          color: var(--warning-foreground);
        }
        .summaryChip.check {
          border-color: var(--gray-foreground);
          background: var(--gray-background);
        }
        .summaryChip.check:hover {
          background: var(--gray-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .summaryChip.check:focus-within {
          background: var(--gray-background-focus);
        }
        .summaryChip.check iron-icon {
          color: var(--gray-foreground);
        }
      `,
    ];
  }

  override render() {
    const chipClass = `summaryChip font-small ${this.styleType}`;
    const grIcon = this.icon ? `gr-icons:${this.icon}` : '';
    return html`<button class=${chipClass} @click=${this.handleClick}>
      ${this.icon && html`<iron-icon icon=${grIcon}></iron-icon>`}
      <slot></slot>
    </button>`;
  }

  private handleClick(e: MouseEvent) {
    e.stopPropagation();
    e.preventDefault();
    this.reporting.reportInteraction('comment chip click', {
      category: this.category,
    });
    fireShowPrimaryTab(this, PrimaryTab.COMMENT_THREADS, true, {
      commentTab: this.category,
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-summary-chip': GrSummaryChip;
  }
}
