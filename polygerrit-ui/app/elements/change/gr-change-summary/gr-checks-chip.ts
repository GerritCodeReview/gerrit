/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {getAppContext} from '../../../services/app-context';
import {Category, RunStatus} from '../../../api/checks';
import {
  ChecksIcon,
  iconFor,
  isStatus,
  labelFor,
} from '../../../models/checks/checks-util';
import {fontStyles} from '../../../styles/gr-font-styles';
import {Interaction} from '../../../constants/reporting';

@customElement('gr-checks-chip')
export class GrChecksChip extends LitElement {
  @property()
  statusOrCategory?: Category | RunStatus;

  @property()
  text = '';

  @property({type: Array})
  links: string[] = [];

  private readonly reporting = getAppContext().reportingService;

  static override get styles() {
    return [
      fontStyles,
      sharedStyles,
      css`
        :host {
          display: inline-block;
          position: relative;
          white-space: nowrap;
        }
        .checksChip {
          color: var(--chip-color);
          cursor: pointer;
          display: inline-block;
          margin-right: var(--spacing-s);
          padding: var(--spacing-xxs) var(--spacing-m) var(--spacing-xxs)
            var(--spacing-s);
          border-radius: 12px;
          border: 1px solid gray;
          /* centered position of 20px chips in 24px line-height inline flow */
          vertical-align: top;
          position: relative;
          top: 2px;
        }
        .checksChip.hoverFullLength {
          position: absolute;
          z-index: 1;
          display: none;
        }
        .checksChip.hoverFullLength .text {
          max-width: 500px;
        }
        :host(:hover) .checksChip.hoverFullLength {
          display: inline-block;
        }
        .checksChip .text {
          display: inline-block;
          max-width: 120px;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          vertical-align: top;
        }
        gr-icon {
          font-size: var(--line-height-small);
        }
        .checksChip a gr-icon.launch {
          color: var(--link-color);
        }
        .checksChip.error {
          color: var(--error-foreground);
          border-color: var(--error-foreground);
          background: var(--error-background);
        }
        .checksChip.error:hover {
          background: var(--error-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.error:focus-within {
          background: var(--error-background-focus);
        }
        .checksChip.error gr-icon {
          color: var(--error-foreground);
        }
        .checksChip.warning {
          border-color: var(--warning-foreground);
          background: var(--warning-background);
        }
        .checksChip.warning:hover {
          background: var(--warning-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.warning:focus-within {
          background: var(--warning-background-focus);
        }
        .checksChip.warning gr-icon {
          color: var(--warning-foreground);
        }
        .checksChip.info {
          border-color: var(--info-foreground);
          background: var(--info-background);
        }
        .checksChip.info:hover {
          background: var(--info-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.info:focus-within {
          background: var(--info-background-focus);
        }
        .checksChip.info gr-icon {
          color: var(--info-foreground);
        }
        .checksChip.check_circle {
          border-color: var(--success-foreground);
          background: var(--success-background);
        }
        .checksChip.check_circle:hover {
          background: var(--success-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.check_circle:focus-within {
          background: var(--success-background-focus);
        }
        .checksChip.check_circle gr-icon {
          color: var(--success-foreground);
        }
        .checksChip.timelapse,
        .checksChip.scheduled {
          border-color: var(--gray-foreground);
          background: var(--gray-background);
        }
        .checksChip.timelapse:hover,
        .checksChip.pending_actions:hover {
          background: var(--gray-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.timelapse:focus-within,
        .checksChip.pending_actions:focus-within {
          background: var(--gray-background-focus);
        }
        .checksChip.timelapse gr-icon,
        .checksChip.pending_actions gr-icon {
          color: var(--gray-foreground);
        }
      `,
    ];
  }

  override render() {
    if (!this.text) return;
    if (!this.statusOrCategory) return;
    const icon = iconFor(this.statusOrCategory);
    const ariaLabel = this.computeAriaLabel();
    const chipClass = `checksChip font-small ${icon.name}`;
    const chipClassFullLength = `${chipClass} hoverFullLength`;
    // 15 is roughly the number of chars for the chip exceeding its 120px width.
    return html`
      ${this.text.length > 15
        ? html` ${this.renderChip(chipClassFullLength, ariaLabel, icon)}`
        : ''}
      ${this.renderChip(chipClass, ariaLabel, icon)}
    `;
  }

  private computeAriaLabel() {
    if (!this.statusOrCategory) return '';
    const label = labelFor(this.statusOrCategory);
    const type = isStatus(this.statusOrCategory) ? 'run' : 'result';
    const count = Number(this.text);
    const isCountChip = !isNaN(count);
    if (isCountChip) {
      const plural = count > 1 ? 's' : '';
      return `${this.text} ${label} ${type}${plural}`;
    }
    return `${label} for check ${this.text}`;
  }

  private renderChip(clazz: string, ariaLabel: string, icon: ChecksIcon) {
    return html`
      <div class=${clazz} role="link" tabindex="0" aria-label=${ariaLabel}>
        <gr-icon icon=${icon.name} ?filled=${icon.filled}></gr-icon>
        ${this.renderLinks()}
        <div class="text">${this.text}</div>
      </div>
    `;
  }

  private renderLinks() {
    return this.links.map(
      link => html`
        <a
          href=${link}
          target="_blank"
          rel="noopener noreferrer"
          @click=${this.onLinkClick}
          @keydown=${this.onLinkKeyDown}
          aria-label="Link to check details"
          ><gr-icon icon="open_in_new" class="launch"></gr-icon
        ></a>
      `
    );
  }

  private onLinkKeyDown(e: KeyboardEvent) {
    // Prevents onChipKeyDown() from reacting to <a> link keyboard events.
    e.stopPropagation();
  }

  private onLinkClick(e: MouseEvent) {
    // Prevents onChipClick() from reacting to <a> link clicks.
    e.stopPropagation();
    this.reporting.reportInteraction(Interaction.CHECKS_CHIP_LINK_CLICKED, {
      text: this.text,
      status: this.statusOrCategory,
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-chip': GrChecksChip;
  }
}
