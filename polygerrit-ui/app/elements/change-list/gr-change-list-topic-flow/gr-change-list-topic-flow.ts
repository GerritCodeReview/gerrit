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
import {pluralize} from '../../../utils/string-util';
import {ProgressStatus} from '../../../constants/constants';
import {allSettled} from '../../../utils/async-util';

@customElement('gr-change-list-topic-flow')
export class GrChangeListTopicFlow extends LitElement {
  @state() private selectedChanges: ChangeInfo[] = [];

  @state() private topicToAdd: TopicName = '' as TopicName;

  @state() private topicsToRemove: Set<TopicName> = new Set();

  @state() private existingTopicSuggestions: TopicName[] = [];

  @state() private loadingText?: string;

  @state() private errorText?: string;

  /** dropdown status is tracked here to lazy-load the inner DOM contents */
  @state() private isDropdownOpen = false;

  @state() private overallProgress: ProgressStatus = ProgressStatus.NOT_STARTED;

  @query('iron-dropdown') private dropdown?: IronDropdownElement;

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
    return html`
      <gr-button id="start-flow" flatten @click=${this.toggleDropdown}
        >Topic</gr-button
      >
      <iron-dropdown
        .horizontalAlign=${'auto'}
        .verticalAlign=${'auto'}
        .verticalOffset=${24 /* roughly line height in pixels */}
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

  private renderExistingTopicsMode() {
    const topics = this.selectedChanges
      .map(change => change.topic)
      .filter(notUndefined)
      .filter(unique);
    const removeDisabled =
      this.topicsToRemove.size === 0 ||
      this.overallProgress === ProgressStatus.RUNNING;
    const applyToAllDisabled = this.topicsToRemove.size !== 1;
    return html`
      <div class="chips">
        ${topics.map(name => this.renderTopicRemoveChip(name))}
      </div>
      <div class="footer">
        <div class="loadingOrError">${this.renderLoadingOrError()}</div>
        <div class="buttons">
          <gr-button
            id="apply-to-all-button"
            flatten
            ?disabled=${applyToAllDisabled}
            @click=${this.applyTopicToAll}
            >Apply to all</gr-button
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

  private renderTopicRemoveChip(name: TopicName) {
    const chipClasses = {
      chip: true,
      selected: this.topicsToRemove.has(name),
    };
    return html`
      <span
        class=${classMap(chipClasses)}
        @click=${() => this.toggleTopicToRemove(name)}
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
      this.isDropdownOpen = false;
      this.dropdown?.close();
    } else {
      this.topicToAdd = '' as TopicName;
      this.topicsToRemove = new Set();
      this.overallProgress = ProgressStatus.NOT_STARTED;
      this.errorText = undefined;
      this.isDropdownOpen = true;
      this.dropdown?.open();
    }
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
    this.loadingText = `Removing ${pluralize(
      this.topicsToRemove.size,
      'topic'
    )}...`;
    this.trackPromises(
      this.selectedChanges
        .filter(change => change.topic && this.topicsToRemove.has(change.topic))
        .map(change => this.restApiService.setChangeTopic(change._number, ''))
    );
  }

  private applyTopicToAll() {
    this.loadingText = 'Applying to all';
    this.trackPromises(
      this.selectedChanges.map(change =>
        this.restApiService.setChangeTopic(
          change._number,
          Array.from(this.topicsToRemove.values())[0]
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
      // TODO: fire reload of dashboard
    } else {
      this.overallProgress = ProgressStatus.FAILED;
      // TODO: when some are rejected, show error and Cancel button
    }
  }

  private toggleTopicToRemove(name: TopicName) {
    if (this.topicsToRemove.has(name)) {
      this.topicsToRemove.delete(name);
    } else {
      this.topicsToRemove.add(name);
    }
    this.requestUpdate();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-topic-flow': GrChangeListTopicFlow;
  }
}
