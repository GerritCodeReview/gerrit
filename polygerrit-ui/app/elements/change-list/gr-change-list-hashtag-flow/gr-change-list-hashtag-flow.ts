/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing} from 'lit';
import {customElement, query, state} from 'lit/decorators';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {resolve} from '../../../models/dependency';
import {ChangeInfo, Hashtag} from '../../../types/common';
import {subscribe} from '../../lit/subscription-controller';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '@polymer/iron-dropdown/iron-dropdown';
import {IronDropdownElement} from '@polymer/iron-dropdown/iron-dropdown';
import {getAppContext} from '../../../services/app-context';
import {notUndefined} from '../../../types/types';
import {unique} from '../../../utils/common-util';
import {AutocompleteSuggestion} from '../../shared/gr-autocomplete/gr-autocomplete';
import {when} from 'lit/directives/when';
import {ValueChangedEvent} from '../../../types/events';
import {classMap} from 'lit/directives/class-map';
import {spinnerStyles} from '../../../styles/gr-spinner-styles';
import {ProgressStatus} from '../../../constants/constants';
import {allSettled} from '../../../utils/async-util';
import {fireReload} from '../../../utils/event-util';
import {fireAlert} from '../../../utils/event-util';
import {pluralize} from '../../../utils/string-util';

@customElement('gr-change-list-hashtag-flow')
export class GrChangeListHashtagFlow extends LitElement {
  @state() private selectedChanges: ChangeInfo[] = [];

  @state() private hashtagToApply: Hashtag = '' as Hashtag;

  @state() private existingHashtagSuggestions: Hashtag[] = [];

  @state() private loadingText?: string;

  @state() private errorText?: string;

  /** dropdown status is tracked here to lazy-load the inner DOM contents */
  @state() private isDropdownOpen = false;

  @state() private overallProgress: ProgressStatus = ProgressStatus.NOT_STARTED;

  @query('iron-dropdown') private dropdown?: IronDropdownElement;

  private selectedExistingHashtags: Set<Hashtag> = new Set();

  private getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      spinnerStyles,
      css`
        iron-dropdown {
          box-shadow: var(--elevation-level-2);
          width: 400px;
          background-color: var(--dialog-background-color);
          border-radius: 4px;
        }
        [slot='dropdown-content'] {
          padding: var(--spacing-xl) var(--spacing-l) var(--spacing-l);
        }
        gr-autocomplete {
          --prominent-border-color: var(--gray-800);
        }
        .footer {
          display: flex;
          justify-content: space-between;
          align-items: baseline;
        }
        .buttons {
          padding-top: var(--spacing-m);
          display: flex;
          justify-content: flex-end;
          gap: var(--spacing-m);
        }
        .chips {
          display: flex;
          flex-wrap: wrap;
          gap: 6px;
          padding-bottom: var(--spacing-l);
        }
        .chip {
          padding: var(--spacing-s) var(--spacing-xl);
          border-radius: 10px;
          width: fit-content;
          cursor: pointer;
        }
        .chip:not(.selected) {
          border: var(--spacing-xxs) solid var(--gray-300);
          background: none;
        }
        .chip.selected {
          border: 0;
          color: var(--selected-foreground);
          background-color: var(--selected-chip-background);
          margin: var(--spacing-xxs);
        }
        .loadingOrError {
          display: flex;
          gap: var(--spacing-s);
        }

        /* The basics of .loadingSpin are defined in spinnerStyles. */
        .loadingSpin {
          vertical-align: top;
          position: relative;
          top: 3px;
        }
      `,
    ];
  }

  constructor() {
    super();
    subscribe(
      this,
      () => this.getBulkActionsModel().selectedChanges$,
      selectedChanges => {
        this.selectedChanges = selectedChanges;
      }
    );
  }

  override render() {
    const isFlowDisabled = this.selectedChanges.length === 0;
    const isCreateNewHashtagDisabled =
      this.hashtagToApply === '' ||
      this.existingHashtagSuggestions.includes(this.hashtagToApply) ||
      this.selectedExistingHashtags.size !== 0 ||
      this.overallProgress === ProgressStatus.RUNNING;
    return html`
      <gr-button
        id="start-flow"
        flatten
        down-arrow
        @click=${this.toggleDropdown}
        .disabled=${isFlowDisabled}
        >Hashtag</gr-button
      >
      <iron-dropdown
        .horizontalAlign=${'auto'}
        .verticalAlign=${'auto'}
        .verticalOffset=${24}
        @opened-changed=${(e: CustomEvent) =>
          (this.isDropdownOpen = e.detail.value)}
      >
        ${when(
          this.isDropdownOpen,
          () => html`
            <div slot="dropdown-content">
              ${this.renderExistingHashtags()}
              <!--
                The .query function needs to be bound to this because lit's
                autobind seems to work only for @event handlers.
              -->
              <gr-autocomplete
                .text=${this.hashtagToApply}
                .query=${(query: string) => this.getHashtagSuggestions(query)}
                show-blue-focus-border
                placeholder="Type hashtag name to create or filter hashtags"
                @text-changed=${(e: ValueChangedEvent<Hashtag>) =>
                  (this.hashtagToApply = e.detail.value)}
              ></gr-autocomplete>
              <div class="footer">
                <div class="loadingOrError" role="progressbar">
                  ${this.renderLoadingOrError()}
                </div>
                <div class="buttons">
                  <gr-button
                    id="create-new-hashtag-button"
                    flatten
                    @click=${() =>
                      this.applyHashtags('Creating hashtag...', true)}
                    .disabled=${isCreateNewHashtagDisabled}
                    >Create new hashtag</gr-button
                  >
                  <gr-button
                    id="apply-hashtag-button"
                    flatten
                    @click=${() => this.applyHashtags('Applying hashtag...')}
                    .disabled=${this.isApplyHashtagDisabled()}
                    >Apply</gr-button
                  >
                </div>
              </div>
            </div>
          `
        )}
      </iron-dropdown>
    `;
  }

  private renderExistingHashtags() {
    const hashtags = this.selectedChanges
      .flatMap(change => change.hashtags ?? [])
      .filter(notUndefined)
      .filter(unique);
    return html`
      <div class="chips">
        ${hashtags.map(name => this.renderExistingHashtagChip(name))}
      </div>
    `;
  }

  private renderExistingHashtagChip(name: Hashtag) {
    const chipClasses = {
      chip: true,
      selected: this.selectedExistingHashtags.has(name),
    };
    return html`
      <button
        role="listbox"
        aria-label=${`${name as string} selection`}
        class=${classMap(chipClasses)}
        @click=${() => this.toggleExistingHashtagSelected(name)}
      >
        ${name}
      </button>
    `;
  }

  private renderLoadingOrError() {
    if (this.overallProgress === ProgressStatus.RUNNING) {
      return html`
        <span class="loadingSpin"></span>
        <span class="loadingText">${this.loadingText}</span>
      `;
    } else if (this.errorText !== undefined) {
      return html`<div class="error">${this.errorText}</div>`;
    }
    return nothing;
  }

  private isApplyHashtagDisabled() {
    const allHashtagsToAdd = [
      ...this.selectedExistingHashtags.values(),
      ...(this.hashtagToApply === '' ? [] : [this.hashtagToApply]),
    ];
    const allHashtagsAlreadyAdded = allHashtagsToAdd.every(hashtag =>
      this.selectedChanges.every(change => change.hashtags?.includes(hashtag))
    );
    const allHashtagsAreNew =
      this.selectedExistingHashtags.size === 0 &&
      (this.hashtagToApply === '' ||
        !this.existingHashtagSuggestions.includes(this.hashtagToApply));
    return (
      allHashtagsAreNew ||
      allHashtagsAlreadyAdded ||
      this.overallProgress === ProgressStatus.RUNNING
    );
  }

  private toggleDropdown() {
    if (this.isDropdownOpen) {
      this.closeDropdown();
    } else {
      this.reset();
      this.openDropdown();
    }
  }

  private reset() {
    this.hashtagToApply = '' as Hashtag;
    this.selectedExistingHashtags = new Set();
    this.overallProgress = ProgressStatus.NOT_STARTED;
    this.errorText = undefined;
  }

  private closeDropdown() {
    this.isDropdownOpen = false;
    this.dropdown?.close();
  }

  private openDropdown() {
    this.isDropdownOpen = true;
    this.dropdown?.open();
  }

  private async getHashtagSuggestions(
    query: string
  ): Promise<AutocompleteSuggestion[]> {
    const suggestions = await this.restApiService.getChangesWithSimilarHashtag(
      query
    );
    this.existingHashtagSuggestions = (suggestions ?? [])
      .flatMap(change => change.hashtags ?? [])
      .filter(notUndefined)
      .filter(unique);
    return this.existingHashtagSuggestions.map(hashtag => {
      return {name: hashtag, value: hashtag};
    });
  }

  private applyHashtags(loadingText: string, creatingHashtag?: boolean) {
    let alert = '';
    const allHashtagsToApply = [
      ...this.selectedExistingHashtags.values(),
      ...(this.hashtagToApply === '' ? [] : [this.hashtagToApply]),
    ];
    if (creatingHashtag) {
      alert = `${allHashtagsToApply[0]} created`;
    } else {
      if (allHashtagsToApply.length > 1) {
        alert = `${allHashtagsToApply.length} hashtags added to changes`;
      } else {
        alert = `${pluralize(this.selectedChanges.length, 'Change')} added to ${
          allHashtagsToApply[0]
        }`;
      }
    }
    this.loadingText = loadingText;
    this.trackPromises(
      this.selectedChanges.map(change =>
        this.restApiService.setChangeHashtag(change._number, {
          add: allHashtagsToApply,
        })
      ),
      alert
    );
  }

  private async trackPromises(promises: Promise<Hashtag[]>[], alert: string) {
    this.overallProgress = ProgressStatus.RUNNING;
    const results = await allSettled(promises);
    if (results.every(result => result.status === 'fulfilled')) {
      this.overallProgress = ProgressStatus.SUCCESSFUL;
      this.closeDropdown();
      if (alert) {
        fireAlert(this, alert);
      }
      fireReload(this);
    } else {
      this.overallProgress = ProgressStatus.FAILED;
      // TODO: when some are rejected, show error and Cancel button
    }
  }

  private toggleExistingHashtagSelected(name: Hashtag) {
    if (this.selectedExistingHashtags.has(name)) {
      this.selectedExistingHashtags.delete(name);
    } else {
      this.selectedExistingHashtags.add(name);
    }
    this.requestUpdate();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-hashtag-flow': GrChangeListHashtagFlow;
  }
}
