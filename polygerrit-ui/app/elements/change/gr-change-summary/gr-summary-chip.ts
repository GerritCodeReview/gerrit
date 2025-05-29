/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-icon/gr-icon';
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {getAppContext} from '../../../services/app-context';
import {fireShowTab} from '../../../utils/event-util';
import {Tab} from '../../../constants/constants';
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

  @property({type: Boolean})
  iconFilled = false;

  @property()
  styleType = SummaryChipStyles.UNDEFINED;

  @property()
  category?: CommentTabState;

  @property({type: Boolean})
  clickable?: boolean;

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
        gr-icon {
          font-size: var(--line-height-small);
        }
        .summaryChip.info {
          border-color: var(--info-foreground);
          background: var(--info-background);
        }
        button.summaryChip.info:hover {
          background: var(--info-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .summaryChip.info:focus-within {
          background: var(--info-background-focus);
        }
        .summaryChip.info gr-icon {
          color: var(--info-foreground);
        }
        .summaryChip.warning {
          border-color: var(--warning-foreground);
          background: var(--warning-background);
        }
        button.summaryChip.warning:hover {
          background: var(--warning-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .summaryChip.warning:focus-within {
          background: var(--warning-background-focus);
        }
        .summaryChip.warning gr-icon {
          color: var(--warning-foreground);
        }
        .summaryChip.check {
          border-color: var(--gray-foreground);
          background: var(--gray-background);
        }
        button.summaryChip.check:hover {
          background: var(--gray-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .summaryChip.check:focus-within {
          background: var(--gray-background-focus);
        }
        .summaryChip.check gr-icon {
          color: var(--gray-foreground);
        }
      `,
    ];
  }

  override render() {
    const chipClass = `summaryChip font-small ${this.styleType}`;
    if (this.clickable) {
      return html`<button class=${chipClass} @click=${this.handleClick}>
        ${this.renderIconAndSlot()}
      </button>`;
    } else {
      return html`<span class=${chipClass}>${this.renderIconAndSlot()}</span>`;
    }
  }

  renderIconAndSlot() {
    return html` ${this.icon &&
      html`<gr-icon ?filled=${this.iconFilled} icon=${this.icon}></gr-icon>`}
      <slot></slot>`;
  }

  private handleClick(e: MouseEvent) {
    e.stopPropagation();
    e.preventDefault();
    this.reporting.reportInteraction('comment chip click', {
      category: this.category,
    });
    fireShowTab(this, Tab.COMMENT_THREADS, true, {
      commentTab: this.category,
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-summary-chip': GrSummaryChip;
  }
}
