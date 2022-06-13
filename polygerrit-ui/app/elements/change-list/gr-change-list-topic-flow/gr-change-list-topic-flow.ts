/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing} from 'lit';
import {customElement, query, state} from 'lit/decorators';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {resolve} from '../../../models/dependency';
import {ChangeInfo, TopicName} from '../../../types/common';
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

@customElement('gr-change-list-topic-flow')
export class GrChangeListTopicFlow extends LitElement {
  @state() private selectedChanges: ChangeInfo[] = [];

  @state() private topicToAdd: TopicName = '' as TopicName;

  @state() private existingTopicSuggestions: TopicName[] = [];

  @state() private loadingText?: string;

  @state() private errorText?: string;

  /** dropdown status is tracked here to lazy-load the inner DOM contents */
  @state() private isDropdownOpen = false;

  @state() private overallProgress: ProgressStatus = ProgressStatus.NOT_STARTED;

  @query('iron-dropdown') private dropdown?: IronDropdownElement;

  private selectedExistingTopics: Set<TopicName> = new Set();

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
    return html`
      <gr-button
        id="start-flow"
        flatten
        down-arrow
        @click=${this.toggleDropdown}
        .disabled=${isFlowDisabled}
        >Topic</gr-button
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
                this.selectedChanges.some(change => change.topic),
                () => this.renderExistingTopicsMode(),
                () => this.renderNoExistingTopicsMode()
              )}
            </div>
          `
        )}
      </iron-dropdown>
    `;
  }

  private disableApplyToAllButton() {
    if (this.selectedExistingTopics.size !== 1) return true;
    // Ensure there is one selected change that does not have this topic
    // already
    return !this.selectedChanges
      .map(change => change.topic)
      .filter(unique)
      .some(topic => !topic || !this.selectedExistingTopics.has(topic));
  }

  private renderExistingTopicsMode() {
    const topics = this.selectedChanges
      .map(change => change.topic)
      .filter(notUndefined)
      .filter(unique);
    const removeDisabled =
      this.selectedExistingTopics.size === 0 ||
      this.overallProgress === ProgressStatus.RUNNING;
    return html`
      <div class="chips">
        ${topics.map(name => this.renderExistingTopicChip(name))}
      </div>
      <div class="footer">
        <div class="loadingOrError">${this.renderLoadingOrError()}</div>
        <div class="buttons">
          <gr-button
            id="apply-to-all-button"
            flatten
            ?disabled=${this.disableApplyToAllButton()}
            @click=${this.applyTopicToAll}
            >Apply${this.selectedChanges.length > 1
              ? ' to all'
              : nothing}</gr-button
          >
          <gr-button
            id="remove-topics-button"
            flatten
            ?disabled=${removeDisabled}
            @click=${this.removeTopics}
            >Remove</gr-button
          >
        </div>
      </div>
    `;
  }

  private renderExistingTopicChip(name: TopicName) {
    const chipClasses = {
      chip: true,
      selected: this.selectedExistingTopics.has(name),
    };
    return html`
      <span
        role="button"
        aria-label=${name as string}
        class=${classMap(chipClasses)}
        @click=${() => this.toggleExistingTopicSelected(name)}
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

  private renderNoExistingTopicsMode() {
    const isCreateNewTopicDisabled =
      this.topicToAdd === '' ||
      this.existingTopicSuggestions.includes(this.topicToAdd) ||
      this.overallProgress === ProgressStatus.RUNNING;
    const isApplyTopicDisabled =
      this.topicToAdd === '' ||
      !this.existingTopicSuggestions.includes(this.topicToAdd) ||
      this.overallProgress === ProgressStatus.RUNNING;
    return html`
      <!--
        The .query function needs to be bound to this because lit's autobind
        seems to work only for @event handlers.
        'this.getTopicSuggestions.bind(this)' gets in trouble with our linter
        even though the bind is necessary here, so an anonymous function is used
        instead.
      -->
      <gr-autocomplete
        .text=${this.topicToAdd}
        .query=${(query: string) => this.getTopicSuggestions(query)}
        show-blue-focus-border
        placeholder="Type topic name to create or filter topics"
        @text-changed=${(e: ValueChangedEvent<TopicName>) =>
          (this.topicToAdd = e.detail.value)}
      ></gr-autocomplete>
      <div class="footer">
        <div class="loadingOrError">${this.renderLoadingOrError()}</div>
        <div class="buttons">
          <gr-button
            id="create-new-topic-button"
            flatten
            @click=${() => this.addTopic('Creating topic...')}
            .disabled=${isCreateNewTopicDisabled}
            >Create new topic</gr-button
          >
          <gr-button
            id="apply-topic-button"
            flatten
            @click=${() => this.addTopic('Applying topic...')}
            .disabled=${isApplyTopicDisabled}
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
    this.topicToAdd = '' as TopicName;
    this.selectedExistingTopics = new Set();
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

  private async getTopicSuggestions(
    query: string
  ): Promise<AutocompleteSuggestion[]> {
    const suggestions = await this.restApiService.getChangesWithSimilarTopic(
      query
    );
    this.existingTopicSuggestions = (suggestions ?? [])
      .map(change => change.topic)
      .filter(notUndefined)
      .filter(unique);
    return this.existingTopicSuggestions.map(topic => {
      return {name: topic, value: topic};
    });
  }

  private removeTopics() {
    this.loadingText = `Removing topic${
      this.selectedExistingTopics.size > 1 ? 's' : ''
    }...`;
    this.trackPromises(
      this.selectedChanges
        .filter(
          change =>
            change.topic && this.selectedExistingTopics.has(change.topic)
        )
        .map(change => this.restApiService.setChangeTopic(change._number, ''))
    );
  }

  private applyTopicToAll() {
    this.loadingText = 'Applying to all';
    this.trackPromises(
      this.selectedChanges.map(change =>
        this.restApiService.setChangeTopic(
          change._number,
          Array.from(this.selectedExistingTopics.values())[0]
        )
      )
    );
  }

  private addTopic(loadingText: string) {
    this.loadingText = loadingText;
    this.trackPromises(
      this.selectedChanges.map(change =>
        this.restApiService.setChangeTopic(change._number, this.topicToAdd)
      )
    );
  }

  private async trackPromises(promises: Promise<string>[]) {
    this.overallProgress = ProgressStatus.RUNNING;
    const results = await allSettled(promises);
    if (results.every(result => result.status === 'fulfilled')) {
      this.overallProgress = ProgressStatus.SUCCESSFUL;
      this.dropdown?.close();
      this.isDropdownOpen = false;
      fireReload(this);
    } else {
      this.overallProgress = ProgressStatus.FAILED;
      // TODO: when some are rejected, show error and Cancel button
    }
  }

  private toggleExistingTopicSelected(name: TopicName) {
    if (this.selectedExistingTopics.has(name)) {
      this.selectedExistingTopics.delete(name);
    } else {
      this.selectedExistingTopics.add(name);
    }
    this.requestUpdate();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-topic-flow': GrChangeListTopicFlow;
  }
}
