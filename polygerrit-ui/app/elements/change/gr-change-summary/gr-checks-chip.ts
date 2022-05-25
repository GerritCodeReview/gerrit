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
import {Category, RunStatus} from '../../../api/checks';
import {iconFor, isStatus, labelFor} from '../../../models/checks/checks-util';
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
        iron-icon {
          width: var(--line-height-small);
          height: var(--line-height-small);
          vertical-align: top;
        }
        .checksChip a iron-icon.launch {
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
        .checksChip.error iron-icon {
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
        .checksChip.warning iron-icon {
          color: var(--warning-foreground);
        }
        .checksChip.info-outline {
          border-color: var(--info-foreground);
          background: var(--info-background);
        }
        .checksChip.info-outline:hover {
          background: var(--info-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.info-outline:focus-within {
          background: var(--info-background-focus);
        }
        .checksChip.info-outline iron-icon {
          color: var(--info-foreground);
        }
        .checksChip.check-circle-outline {
          border-color: var(--success-foreground);
          background: var(--success-background);
        }
        .checksChip.check-circle-outline:hover {
          background: var(--success-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.check-circle-outline:focus-within {
          background: var(--success-background-focus);
        }
        .checksChip.check-circle-outline iron-icon {
          color: var(--success-foreground);
        }
        .checksChip.timelapse,
        .checksChip.scheduled {
          border-color: var(--gray-foreground);
          background: var(--gray-background);
        }
        .checksChip.timelapse:hover,
        .checksChip.scheduled:hover {
          background: var(--gray-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.timelapse:focus-within,
        .checksChip.scheduled:focus-within {
          background: var(--gray-background-focus);
        }
        .checksChip.timelapse iron-icon,
        .checksChip.scheduled iron-icon {
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
    const chipClass = `checksChip font-small ${icon}`;
    const chipClassFullLength = `${chipClass} hoverFullLength`;
    const grIcon = `gr-icons:${icon}`;
    // 15 is roughly the number of chars for the chip exceeding its 120px width.
    return html`
      ${this.text.length > 15
        ? html` ${this.renderChip(chipClassFullLength, ariaLabel, grIcon)}`
        : ''}
      ${this.renderChip(chipClass, ariaLabel, grIcon)}
    `;
  }

  private renderChip(clazz: string, ariaLabel: string, icon: string) {
    return html`
      <div class=${clazz} role="link" tabindex="0" aria-label=${ariaLabel}>
        <iron-icon icon=${icon}></iron-icon>
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
          ><iron-icon class="launch" icon="gr-icons:launch"></iron-icon
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
