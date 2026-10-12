/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../shared/gr-button/gr-button';
import '../shared/gr-dropdown-list/gr-dropdown-list';
import '../shared/gr-tooltip-content/gr-tooltip-content';
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {classMap} from 'lit/directives/class-map.js';
import {when} from 'lit/directives/when.js';
import {CheckRun, checksModelToken} from '../../models/checks/checks-model';
import {
  ALL_ATTEMPTS,
  AttemptChoice,
  attemptChoiceLabel,
  isAttemptChoice,
  LATEST_ATTEMPT,
  sortAttemptChoices,
  stringToAttemptChoice,
} from '../../models/checks/checks-util';
import {changeModelToken} from '../../models/change/change-model';
import {resolve} from '../../models/dependency';
import {PatchSetNumber} from '../../types/common';
import {assert, assertIsDefined, unique} from '../../utils/common-util';
import {subscribe} from '../lit/subscription-controller';
import {DropdownItem} from '../shared/gr-dropdown-list/gr-dropdown-list';

/**
 * Below this width the selector switches to its compact layout, because the
 * full trigger texts and the "Go To Latest Patchset" button do not fit.
 */
const MIN_FULL_WIDTH_PX = 160;

/**
 * Lets the user choose the patchset and the attempt that the Checks tab shows
 * runs and results for.
 */
@customElement('gr-checks-patchset-select')
export class GrChecksPatchsetSelect extends LitElement {
  /**
   * Compact layout for narrow containers such as the collapsed runs panel: The
   * patchset dropdown has a short trigger text, the attempt dropdown is not
   * rendered, and "Go To Latest Patchset" is shortened to "Latest".
   *
   * The compact layout is also used when the available width is too small
   * for the full layout, see `narrow`.
   */
  @property({type: Boolean})
  compact = false;

  /** Whether the available width is below `MIN_FULL_WIDTH_PX`. */
  @state()
  narrow = false;

  @state()
  runs: CheckRun[] = [];

  @state()
  checksPatchsetNumber: PatchSetNumber | undefined = undefined;

  @state()
  latestPatchsetNumber: PatchSetNumber | undefined = undefined;

  @state()
  selectedAttempt: AttemptChoice = LATEST_ATTEMPT;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getChecksModel = resolve(this, checksModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChecksModel().allRunsSelectedPatchset$,
      x => (this.runs = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().checksSelectedPatchsetNumber$,
      x => (this.checksPatchsetNumber = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().checksSelectedAttemptNumber$,
      x => (this.selectedAttempt = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().latestPatchNum$,
      x => (this.latestPatchsetNumber = x)
    );
    // The host is a block level flex container, so its width is the width
    // that is available, regardless of the content being rendered. The update
    // is deferred, because re-rendering changes the height of the host, which
    // must not happen while resize observations are being delivered.
    new ResizeObserver(entries => {
      const width = entries[entries.length - 1].contentRect.width;
      requestAnimationFrame(() => {
        this.narrow = width > 0 && width < MIN_FULL_WIDTH_PX;
      });
    }).observe(this);
  }

  private isCompact() {
    return this.compact || this.narrow;
  }

  static override get styles() {
    return css`
      :host {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--spacing-s) var(--spacing-m);
      }
      gr-dropdown-list {
        border: 1px solid var(--border-color);
        border-radius: var(--border-radius);
        padding: 0 var(--spacing-m);
      }
      gr-dropdown-list.compact {
        padding: 0 var(--spacing-xs);
      }
      gr-button {
        --gr-button-padding: var(--spacing-s) var(--spacing-m);
      }
      gr-button.compact {
        --gr-button-padding: var(--spacing-s) var(--spacing-xs);
      }
    `;
  }

  override render() {
    const compact = this.isCompact();
    const attemptItems = this.createAttemptDropdownItems();
    const notLatest =
      !!this.checksPatchsetNumber &&
      this.checksPatchsetNumber !== this.latestPatchsetNumber;
    return html`
      <gr-dropdown-list
        class=${classMap({patchsetSelect: true, compact})}
        value=${(this.checksPatchsetNumber || this.latestPatchsetNumber) ?? 0}
        .items=${this.createPatchsetDropdownItems()}
        @value-change=${this.onPatchsetSelected}
      ></gr-dropdown-list>
      ${when(
        !compact && attemptItems.length > 0,
        () => html`<gr-dropdown-list
          class="attemptSelect"
          value=${this.selectedAttempt ?? 0}
          .items=${attemptItems}
          @value-change=${this.onAttemptSelected}
        ></gr-dropdown-list>`
      )}
      ${when(notLatest, () => this.renderGoToLatest(compact))}
    `;
  }

  private renderGoToLatest(compact: boolean) {
    if (!compact) {
      return html`<gr-button
        class="goToLatest"
        link
        @click=${this.goToLatestPatchset}
        >Go To Latest Patchset</gr-button
      >`;
    }
    return html`<gr-tooltip-content has-tooltip title="Go To Latest Patchset">
      <gr-button
        class="goToLatest compact"
        link
        aria-label="Go To Latest Patchset"
        @click=${this.goToLatestPatchset}
        >Latest</gr-button
      >
    </gr-tooltip-content>`;
  }

  private onAttemptSelected(e: CustomEvent<{value: string | undefined}>) {
    const attempt = stringToAttemptChoice(e.detail.value);
    assertIsDefined(attempt, `unexpected attempt choice ${e.detail.value}`);
    this.getChecksModel().updateStateSetAttempt(attempt);
  }

  private onPatchsetSelected(e: CustomEvent<{value: string}>) {
    const patchset = Number(e.detail.value) as PatchSetNumber;
    assert(Number.isInteger(patchset), `patchset must be integer: ${patchset}`);
    this.getChecksModel().updateStateSetPatchset(patchset);
  }

  private goToLatestPatchset() {
    assertIsDefined(this.latestPatchsetNumber, 'latestPatchsetNumber');
    this.getChecksModel().updateStateSetPatchset(this.latestPatchsetNumber);
  }

  private createAttemptDropdownItems() {
    if (this.runs.every(run => run.isSingleAttempt)) return [];
    const attempts: AttemptChoice[] = this.runs
      .map(run => run.attempt ?? 0)
      .filter(isAttemptChoice)
      .filter(unique);
    attempts.push(LATEST_ATTEMPT);
    attempts.push(ALL_ATTEMPTS);
    const items: DropdownItem[] = attempts.sort(sortAttemptChoices).map(a => {
      return {
        value: a,
        text: attemptChoiceLabel(a),
      };
    });
    return items;
  }

  private createPatchsetDropdownItems() {
    if (!this.latestPatchsetNumber) return [];
    return Array.from(Array(this.latestPatchsetNumber), (_, i) => {
      assertIsDefined(this.latestPatchsetNumber, 'latestPatchsetNumber');
      const index = this.latestPatchsetNumber - i;
      const postfix = index === this.latestPatchsetNumber ? ' (latest)' : '';
      return {
        value: `${index}`,
        text: `Patchset ${index}${postfix}`,
        triggerText: this.isCompact() ? `PS ${index}` : undefined,
      };
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-patchset-select': GrChecksPatchsetSelect;
  }
}
