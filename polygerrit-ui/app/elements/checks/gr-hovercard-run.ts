/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {fontStyles} from '../../styles/gr-font-styles';
import {customElement, property} from 'lit/decorators';
import './gr-checks-action';
import {CheckRun} from '../../models/checks/checks-model';
import {
  AttemptDetail,
  iconFor,
  runActions,
  worstCategory,
} from '../../models/checks/checks-util';
import {durationString, fromNow} from '../../utils/date-util';
import {RunStatus} from '../../api/checks';
import {ordinal} from '../../utils/string-util';
import {HovercardMixin} from '../../mixins/hovercard-mixin/hovercard-mixin';
import {css, html, LitElement} from 'lit';
import {checksStyles} from './gr-checks-styles';

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = HovercardMixin(LitElement);

@customElement('gr-hovercard-run')
export class GrHovercardRun extends base {
  @property({type: Object})
  run?: CheckRun;

  static override get styles() {
    return [
      fontStyles,
      checksStyles,
      base.styles || [],
      css`
        #container {
          min-width: 356px;
          max-width: 356px;
          padding: var(--spacing-xl) 0 var(--spacing-m) 0;
        }
        .row {
          display: flex;
          margin-top: var(--spacing-s);
        }
        .attempts.row {
          flex-wrap: wrap;
        }
        .chipRow {
          display: flex;
          margin-top: var(--spacing-s);
        }
        .chip {
          background: var(--gray-background);
          color: var(--gray-foreground);
          border-radius: 20px;
          padding: var(--spacing-xs) var(--spacing-m) var(--spacing-xs)
            var(--spacing-s);
        }
        .title {
          color: var(--deemphasized-text-color);
          margin-right: var(--spacing-m);
        }
        div.section {
          margin: 0 var(--spacing-xl) var(--spacing-m) var(--spacing-xl);
          display: flex;
        }
        div.sectionIcon {
          flex: 0 0 30px;
        }
        div.chip iron-icon {
          width: 16px;
          height: 16px;
          /* Positioning of a 16px icon in the middle of a 20px line. */
          position: relative;
          top: 2px;
        }
        div.sectionIcon iron-icon {
          position: relative;
          top: 2px;
          width: 20px;
          height: 20px;
        }
        div.sectionIcon iron-icon.small {
          position: relative;
          top: 6px;
          width: 16px;
          height: 16px;
        }
        div.sectionContent iron-icon.link {
          color: var(--link-color);
        }
        div.sectionContent .attemptIcon iron-icon,
        div.sectionContent iron-icon.small {
          width: 16px;
          height: 16px;
          margin-right: var(--spacing-s);
          /* Positioning of a 16px icon in the middle of a 20px line. */
          position: relative;
          top: 2px;
        }
        div.sectionContent .attemptIcon iron-icon {
          margin-right: 0;
        }
        .attemptIcon,
        .attemptNumber {
          margin-right: var(--spacing-s);
          color: var(--deemphasized-text-color);
          text-align: center;
          width: 24px;
          font-size: var(--font-size-small);
        }
        div.action {
          border-top: 1px solid var(--border-color);
          margin-top: var(--spacing-m);
          padding: var(--spacing-m) var(--spacing-xl) 0;
        }
      `,
    ];
  }

  override render() {
    if (!this.run) return '';
    const icon = this.computeIcon();
    return html`
      <div id="container" role="tooltip" tabindex="-1">
        <div class="section">
          <div
            ?hidden=${!this.run || this.run.status === RunStatus.RUNNABLE}
            class="chipRow"
          >
            <div class="chip">
              <iron-icon icon="gr-icons:${this.computeChipIcon()}"></iron-icon>
              <span>${this.run.status}</span>
            </div>
          </div>
        </div>
        <div class="section">
          <div class="sectionIcon" ?hidden=${icon.length === 0}>
            <iron-icon class=${icon} icon="gr-icons:${icon}"></iron-icon>
          </div>
          <div class="sectionContent">
            <h3 class="name heading-3">
              <span>${this.run.checkName}</span>
            </h3>
          </div>
        </div>
        ${this.renderStatusSection()} ${this.renderAttemptSection()}
        ${this.renderTimestampSection()} ${this.renderDescriptionSection()}
        ${this.renderActions()}
      </div>
    `;
  }

  private renderStatusSection() {
    if (!this.run || (!this.run.statusLink && !this.run.statusDescription))
      return;

    return html`
      <div class="section">
        <div class="sectionIcon">
          <iron-icon class="small" icon="gr-icons:info-outline"></iron-icon>
        </div>
        <div class="sectionContent">
          ${this.run.statusLink
            ? html` <div class="row">
                <div class="title">Status</div>
                <div>
                  <a href=${this.run.statusLink} target="_blank"
                    ><iron-icon
                      aria-label="external link to check status"
                      class="small link"
                      icon="gr-icons:launch"
                    ></iron-icon
                    >${this.computeHostName(this.run.statusLink)}
                  </a>
                </div>
              </div>`
            : ''}
          ${this.run.statusDescription
            ? html` <div class="row">
                <div class="title">Message</div>
                <div>${this.run.statusDescription}</div>
              </div>`
            : ''}
        </div>
      </div>
    `;
  }

  private renderAttemptSection() {
    if (this.hideAttempts()) return;
    const attempts = this.computeAttempts();
    return html`
      <div class="section">
        <div class="sectionIcon">
          <iron-icon class="small" icon="gr-icons:arrow-forward"></iron-icon>
        </div>
        <div class="sectionContent">
          <div class="attempts row">
            <div class="title">Attempt</div>
            ${attempts.map(a => this.renderAttempt(a))}
          </div>
        </div>
      </div>
    `;
  }

  private renderAttempt(attempt: AttemptDetail) {
    return html`
      <div>
        <div class="attemptIcon">
          <iron-icon
            class=${attempt.icon}
            icon="gr-icons:${attempt.icon}"
          ></iron-icon>
        </div>
        <div class="attemptNumber">${ordinal(attempt.attempt)}</div>
      </div>
    `;
  }

  private renderTimestampSection() {
    if (
      !this.run ||
      (!this.run.startedTimestamp &&
        !this.run.scheduledTimestamp &&
        !this.run.finishedTimestamp)
    )
      return;

    const scheduled =
      this.run.scheduledTimestamp && !this.run.startedTimestamp
        ? html`<div class="row">
            <div class="title">Scheduled</div>
            <div>${fromNow(this.run.scheduledTimestamp)}</div>
          </div>`
        : '';

    const started = this.run.startedTimestamp
      ? html`<div class="row">
          <div class="title">Started</div>
          <div>${fromNow(this.run.startedTimestamp)}</div>
        </div>`
      : '';

    const finished =
      this.run.finishedTimestamp && this.run.status === RunStatus.COMPLETED
        ? html`<div class="row">
            <div class="title">Ended</div>
            <div>${fromNow(this.run.finishedTimestamp)}</div>
          </div>`
        : '';

    const completed =
      this.run.startedTimestamp &&
      this.run.finishedTimestamp &&
      this.run.status === RunStatus.COMPLETED
        ? html`<div class="row">
            <div class="title">Completion</div>
            <div>
              ${durationString(
                this.run.startedTimestamp,
                this.run.finishedTimestamp,
                true
              )}
            </div>
          </div>`
        : '';

    const eta =
      this.run.finishedTimestamp && this.run.status === RunStatus.RUNNING
        ? html`<div class="row">
            <div class="title">ETA</div>
            <div>
              ${durationString(new Date(), this.run.finishedTimestamp, true)}
            </div>
          </div>`
        : '';

    return html`
      <div class="section">
        <div class="sectionIcon">
          <iron-icon class="small" icon="gr-icons:schedule"></iron-icon>
        </div>
        <div class="sectionContent">
          ${scheduled} ${started} ${finished} ${completed} ${eta}
        </div>
      </div>
    `;
  }

  private renderDescriptionSection() {
    if (!this.run || (!this.run.checkLink && !this.run.checkDescription))
      return;
    return html`
      <div class="section">
        <div class="sectionIcon">
          <iron-icon class="small" icon="gr-icons:link"></iron-icon>
        </div>
        <div class="sectionContent">
          ${this.run.checkDescription
            ? html` <div class="row">
                <div class="title">Description</div>
                <div>${this.run.checkDescription}</div>
              </div>`
            : ''}
          ${this.run.checkLink
            ? html` <div class="row">
                <div class="title">Documentation</div>
                <div>
                  <a href=${this.run.checkLink} target="_blank"
                    ><iron-icon
                      aria-label="external link to check documentation"
                      class="small link"
                      icon="gr-icons:launch"
                    ></iron-icon
                    >${this.computeHostName(this.run.checkLink)}
                  </a>
                </div>
              </div>`
            : ''}
        </div>
      </div>
    `;
  }

  private renderActions() {
    const actions = runActions(this.run);
    return actions.map(
      action =>
        html`
          <div class="action">
            <gr-checks-action
              context="hovercard"
              .eventTarget=${this._target}
              .action=${action}
            ></gr-checks-action>
          </div>
        `
    );
  }

  computeIcon() {
    if (!this.run) return '';
    const category = worstCategory(this.run);
    if (category) return iconFor(category);
    return this.run.status === RunStatus.COMPLETED
      ? iconFor(RunStatus.COMPLETED)
      : '';
  }

  computeAttempts(): AttemptDetail[] {
    const details = this.run?.attemptDetails ?? [];
    const more =
      details.length > 7 ? [{icon: 'more-horiz', attempt: undefined}] : [];
    return [...more, ...details.slice(-7)];
  }

  private computeChipIcon() {
    if (this.run?.status === RunStatus.COMPLETED) {
      return 'check';
    }
    if (this.run?.status === RunStatus.RUNNING) {
      return iconFor(RunStatus.RUNNING);
    }
    if (this.run?.status === RunStatus.SCHEDULED) {
      return iconFor(RunStatus.SCHEDULED);
    }
    return '';
  }

  private computeHostName(link?: string) {
    return link ? new URL(link).hostname : '';
  }

  private hideAttempts() {
    const attemptCount = this.run?.attemptDetails?.length;
    return attemptCount === undefined || attemptCount < 2;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-hovercard-run': GrHovercardRun;
  }
}
