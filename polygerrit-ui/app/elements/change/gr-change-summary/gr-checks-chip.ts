/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {getAppContext} from '../../../services/app-context';
import {Category, RunStatus} from '../../../api/checks';
import {iconFor, isStatus, labelFor} from '../../../models/checks/checks-util';
import {fontStyles} from '../../../styles/gr-font-styles';
import {Interaction} from '../../../constants/reporting';
import {iconStyles} from '../../../styles/gr-icon-styles';

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
      iconStyles,
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
        .material-icon {
          font-size: var(--line-height-small);
        }
        .checksChip a .material-icon.launch {
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
        .checksChip.error .material-icon {
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
        .checksChip.warning .material-icon {
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
        .checksChip.info .material--icon {
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
        .checksChip.check_circle .material-icon {
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
        .checksChip.timelapse .material-icon,
        .checksChip.pending_actions .material-icon {
          color: var(--gray-foreground);
        }
      `,
    ];
  }

  override render() {
    if (!this.text) return;
    if (!this.statusOrCategory) return;
    const icon = iconFor(this.statusOrCategory);
    const label = labelFor(this.statusOrCategory);
    const count = Number(this.text);
    let ariaLabel = label;
    if (!isNaN(count)) {
      const type = isStatus(this.statusOrCategory) ? 'run' : 'result';
      const plural = count > 1 ? 's' : '';
      ariaLabel = `${this.text} ${label} ${type}${plural}`;
    }
    const chipClass = `checksChip font-small ${icon.icon}`;
    const chipClassFullLength = `${chipClass} hoverFullLength`;
    // 15 is roughly the number of chars for the chip exceeding its 120px width.
    return html`
      ${this.text.length > 15
        ? html` ${this.renderChip(chipClassFullLength, ariaLabel, icon)}`
        : ''}
      ${this.renderChip(chipClass, ariaLabel, icon)}
    `;
  }

  private renderChip(
    clazz: string,
    ariaLabel: string,
    icon: {icon: string; filled?: boolean}
  ) {
    return html`
      <div class=${clazz} role="link" tabindex="0" aria-label=${ariaLabel}>
        <span class="material-icon ${icon.filled ? 'filled' : ''}"
          >${icon.icon}</span
        >
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
          @click=${this.onLinkClick}
          @keydown=${this.onLinkKeyDown}
          aria-label="Link to check details"
          ><span class="material-icon launch">open_in_new</span></a
        >
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
