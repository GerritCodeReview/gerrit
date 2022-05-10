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

@customElement('gr-change-list-hashtag-flow')
export class GrChangeListHashtagFlow extends LitElement {
  @state() private selectedChanges: ChangeInfo[] = [];

  @state() private hashtagToAdd: Hashtag = '' as Hashtag;

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
          --border-color: var(--gray-800);
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
        }
        .chip {
          padding: var(--spacing-s) var(--spacing-xl);
          border-radius: 10px;
          width: fit-content;
          cursor: pointer;
        }
        .chip:not(.selected) {
          border: var(--spacing-xxs) solid var(--gray-300);
        }
        .chip.selected {
          color: var(--blue-800);
          background-color: var(--blue-50);
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

  override connectedCallback(): void {
    super.connectedCallback();
    subscribe(
      this,
      this.getBulkActionsModel().selectedChanges$,
      selectedChanges => {
        this.selectedChanges = selectedChanges;
      }
    );
  }

  override render() {
    const isFlowDisabled = this.selectedChanges.length === 0;
    return html`
      <gr-button
        id="start-flow"
        flatten
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
              ${when(
                this.selectedChanges.some(change => change.hashtags?.length),
                () => this.renderExistingHashtagsMode(),
                () => this.renderNoExistingHashtagsMode()
              )}
            </div>
          `
        )}
      </iron-dropdown>
    `;
  }

  private renderExistingHashtagsMode() {
    const hashtags = this.selectedChanges
      .flatMap(change => change.hashtags ?? [])
      .filter(notUndefined)
      .filter(unique);
    const removeDisabled =
      this.selectedExistingHashtags.size === 0 ||
      this.overallProgress === ProgressStatus.RUNNING;
    const applyToAllDisabled = this.selectedExistingHashtags.size !== 1;
    return html`
      <div class="chips">
        ${hashtags.map(name => this.renderExistingHashtagChip(name))}
      </div>
      <div class="footer">
        <div class="loadingOrError">${this.renderLoadingOrError()}</div>
        <div class="buttons">
          <gr-button
            id="apply-to-all-button"
            flatten
            ?disabled=${applyToAllDisabled}
            @click=${this.applyHashtagToAll}
            >Apply to all</gr-button
          >
          <gr-button
            id="remove-hashtags-button"
            flatten
            ?disabled=${removeDisabled}
            @click=${this.removeHashtags}
            >Remove</gr-button
          >
        </div>
      </div>
    `;
  }

  private renderExistingHashtagChip(name: Hashtag) {
    const chipClasses = {
      chip: true,
      selected: this.selectedExistingHashtags.has(name),
    };
    return html`
      <span
        role="button"
        aria-label=${name as string}
        class=${classMap(chipClasses)}
        @click=${() => this.toggleExistingHashtagSelected(name)}
      >
        ${name}
      </span>
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

  private renderNoExistingHashtagsMode() {
    const isCreateNewHashtagDisabled =
      this.hashtagToAdd === '' ||
      this.existingHashtagSuggestions.includes(this.hashtagToAdd) ||
      this.overallProgress === ProgressStatus.RUNNING;
    const isApplyHashtagDisabled =
      this.hashtagToAdd === '' ||
      !this.existingHashtagSuggestions.includes(this.hashtagToAdd) ||
      this.overallProgress === ProgressStatus.RUNNING;
    return html`
      <!--
        The .query function needs to be bound to this because lit's autobind
        seems to work only for @event handlers.
        'this.getHashtagSuggestions.bind(this)' gets in trouble with our linter
        even though the bind is necessary here, so an anonymous function is used
        instead.
      -->
      <gr-autocomplete
        .text=${this.hashtagToAdd}
        .query=${(query: string) => this.getHashtagSuggestions(query)}
        show-blue-focus-border
        placeholder="Type hashtag name to create or filter hashtags"
        @text-changed=${(e: ValueChangedEvent<Hashtag>) =>
          (this.hashtagToAdd = e.detail.value)}
      ></gr-autocomplete>
      <div class="footer">
        <div class="loadingOrError">${this.renderLoadingOrError()}</div>
        <div class="buttons">
          <gr-button
            id="create-new-hashtag-button"
            flatten
            @click=${() => this.addHashtag('Creating hashtag...')}
            .disabled=${isCreateNewHashtagDisabled}
            >Create new hashtag</gr-button
          >
          <gr-button
            id="apply-hashtag-button"
            flatten
            @click=${() => this.addHashtag('Applying hashtag...')}
            .disabled=${isApplyHashtagDisabled}
            >Apply</gr-button
          >
        </div>
      </div>
    `;
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
    this.hashtagToAdd = '' as Hashtag;
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

  private removeHashtags() {
    this.loadingText = `Removing hashtag${
      this.selectedExistingHashtags.size > 1 ? 's' : ''
    }...`;
    this.trackPromises(
      this.selectedChanges
        .filter(
          change =>
            change.hashtags &&
            change.hashtags.some(hashtag =>
              this.selectedExistingHashtags.has(hashtag)
            )
        )
        .map(change =>
          this.restApiService.setChangeHashtag(change._number, {
            remove: Array.from(this.selectedExistingHashtags.values()),
          })
        )
    );
  }

  private applyHashtagToAll() {
    this.loadingText = 'Applying hashtag to all';
    this.trackPromises(
      this.selectedChanges.map(change =>
        this.restApiService.setChangeHashtag(change._number, {
          add: Array.from(this.selectedExistingHashtags.values()),
        })
      )
    );
  }

  private addHashtag(loadingText: string) {
    this.loadingText = loadingText;
    this.trackPromises(
      this.selectedChanges.map(change =>
        this.restApiService.setChangeHashtag(change._number, {
          add: [this.hashtagToAdd],
        })
      )
    );
  }

  private async trackPromises(promises: Promise<Hashtag[]>[]) {
    this.overallProgress = ProgressStatus.RUNNING;
    const results = await allSettled(promises);
    if (results.every(result => result.status === 'fulfilled')) {
      this.overallProgress = ProgressStatus.SUCCESSFUL;
      this.closeDropdown();
      // TODO: fire reload of dashboard
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
