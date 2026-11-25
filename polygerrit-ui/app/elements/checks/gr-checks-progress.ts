/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {durationString} from '../../utils/date-util';

/** How often to redraw the ring, in milliseconds. */
const UPDATE_INTERVAL_MS = 10 * 1000;

/** Radius of the ring in the 16x16 viewBox. */
const RADIUS = 6;

/**
 * Shows how far along a running check is, as a ring that fills up clockwise
 * between the start of the run and its ETA. If the run takes longer than
 * estimated, the ring stays full and turns to the warning color.
 *
 * It is used instead of the status icon of a running check, so it is sized
 * like an icon.
 */
@customElement('gr-checks-progress')
export class GrChecksProgress extends LitElement {
  @property({type: Object})
  startedTimestamp?: Date;

  /** When the run is expected to finish (its ETA). */
  @property({type: Object})
  finishedTimestamp?: Date;

  private timer?: number;

  static override get styles() {
    return [
      css`
        :host {
          display: inline-block;
          font-size: 20px;
          width: 1em;
          height: 1em;
          line-height: 1;
          vertical-align: top;
        }
        svg {
          display: block;
          width: 100%;
          height: 100%;
          transform: rotate(-90deg);
        }
        circle {
          fill: none;
          stroke-width: 2.5;
        }
        .track {
          stroke: var(--gray-foreground);
          opacity: 0.3;
        }
        .done {
          stroke: var(--success-foreground);
        }
        .late {
          stroke: var(--warning-foreground);
        }
      `,
    ];
  }

  override connectedCallback() {
    super.connectedCallback();
    this.timer = window.setInterval(
      () => this.requestUpdate(),
      UPDATE_INTERVAL_MS
    );
  }

  override disconnectedCallback() {
    window.clearInterval(this.timer);
    super.disconnectedCallback();
  }

  override render() {
    if (!this.startedTimestamp || !this.finishedTimestamp) return nothing;
    const now = new Date();
    const start = this.startedTimestamp.getTime();
    const end = this.finishedTimestamp.getTime();
    const late = now.getTime() >= end;
    const fraction =
      late || end <= start
        ? 1
        : Math.max(0, (now.getTime() - start) / (end - start));
    const label = late
      ? 'Running longer than estimated'
      : `${Math.floor(fraction * 100)}% done, ETA: ${durationString(
          now,
          this.finishedTimestamp
        )}`;
    const length = 2 * Math.PI * RADIUS;
    return html`
      <svg viewBox="0 0 16 16" role="img" aria-label=${label}>
        <title>${label}</title>
        <circle class="track" cx="8" cy="8" r=${RADIUS}></circle>
        <circle
          class=${late ? 'late' : 'done'}
          cx="8"
          cy="8"
          r=${RADIUS}
          stroke-dasharray="${fraction * length} ${length}"
        ></circle>
      </svg>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-progress': GrChecksProgress;
  }
}
