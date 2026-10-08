/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../shared/gr-button/gr-button';
import '../shared/gr-dropdown-list/gr-dropdown-list';
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
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
 * Lets the user choose the patchset and the attempt that the Checks tab shows
 * runs and results for.
 */
@customElement('gr-checks-patchset-select')
export class GrChecksPatchsetSelect extends LitElement {
  /**
   * Compact mode for narrow containers such as the collapsed runs panel: Only
   * the patchset dropdown is rendered, with a short trigger text.
   */
  @property({type: Boolean, reflect: true})
  compact = false;

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
      :host([compact]) gr-dropdown-list {
        padding: 0 var(--spacing-xs);
      }
      gr-button {
        --gr-button-padding: var(--spacing-s) var(--spacing-m);
      }
    `;
  }

  override render() {
    const attemptItems = this.createAttemptDropdownItems();
    const notLatest =
      !!this.checksPatchsetNumber &&
      this.checksPatchsetNumber !== this.latestPatchsetNumber;
    return html`
      <gr-dropdown-list
        class="patchsetSelect"
        value=${(this.checksPatchsetNumber || this.latestPatchsetNumber) ?? 0}
        .items=${this.createPatchsetDropdownItems()}
        @value-change=${this.onPatchsetSelected}
      ></gr-dropdown-list>
      ${when(
        !this.compact && attemptItems.length > 0,
        () => html`<gr-dropdown-list
          class="attemptSelect"
          value=${this.selectedAttempt ?? 0}
          .items=${attemptItems}
          @value-change=${this.onAttemptSelected}
        ></gr-dropdown-list>`
      )}
      ${when(
        !this.compact && notLatest,
        () => html`<gr-button @click=${this.goToLatestPatchset} link
          >Go To Latest Patchset</gr-button
        >`
      )}
    `;
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
        triggerText: this.compact ? `PS ${index}` : undefined,
      };
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-patchset-select': GrChecksPatchsetSelect;
  }
}
