/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '../../../styles/shared-styles';
import '../gr-button/gr-button';
import '../gr-select/gr-select';
import {DiffPreferencesInfo, IgnoreWhitespaceType} from '../../../types/diff';
import {getAppContext} from '../../../services/app-context';
import {subscribe} from '../../lit/subscription-controller';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html} from 'lit';
import {customElement, query, state} from 'lit/decorators';
import {convertToString} from '../../../utils/string-util';
import {fire} from '../../../utils/event-util';
import {ValueChangedEvent} from '../../../types/events';
import {GrSelect} from '../gr-select/gr-select';

@customElement('gr-diff-preferences')
export class GrDiffPreferences extends LitElement {
  @query('#contextLineSelect') private contextLineSelect?: HTMLInputElement;

  @query('#columnsInput') private columnsInput?: HTMLInputElement;

  @query('#tabSizeInput') private tabSizeInput?: HTMLInputElement;

  @query('#fontSizeInput') private fontSizeInput?: HTMLInputElement;

  @query('#lineWrappingInput') private lineWrappingInput?: HTMLInputElement;

  @query('#showTabsInput') private showTabsInput?: HTMLInputElement;

  @query('#showTrailingWhitespaceInput')
  private showTrailingWhitespaceInput?: HTMLInputElement;

  @query('#automaticReviewInput')
  private automaticReviewInput?: HTMLInputElement;

  @query('#syntaxHighlightInput')
  private syntaxHighlightInput?: HTMLInputElement;

  @query('#ignoreWhiteSpace') private ignoreWhiteSpace?: HTMLInputElement;

  // Used in gr-diff-preferences-dialog
  @query('#contextSelect') contextSelect?: GrSelect;

  @state() diffPrefs?: DiffPreferencesInfo;

  @state() private originalDiffPrefs?: DiffPreferencesInfo;

  private readonly userModel = getAppContext().userModel;

  constructor() {
    super();
    subscribe(
      this,
      () => this.userModel.diffPreferences$,
      diffPreferences => {
        if (!diffPreferences) return;
        this.originalDiffPrefs = diffPreferences;
        this.diffPrefs = {...diffPreferences};
      }
    );
  }

  static override get styles() {
    return [sharedStyles, formStyles];
  }

  override render() {
    return html`
      <div id="diffPreferences" class="gr-form-styles">
        <section>
          <label for="contextLineSelect" class="title">Context</label>
          <span class="value">
            <gr-select
              id="contextSelect"
              .bindValue=${convertToString(this.diffPrefs?.context)}
              @change=${this.handleDiffContextChanged}
            >
              <select id="contextLineSelect">
                <option value="3">3 lines</option>
                <option value="10">10 lines</option>
                <option value="25">25 lines</option>
                <option value="50">50 lines</option>
                <option value="75">75 lines</option>
                <option value="100">100 lines</option>
                <option value="-1">Whole file</option>
              </select>
            </gr-select>
          </span>
        </section>
        <section>
          <label for="lineWrappingInput" class="title">Fit to screen</label>
          <span class="value">
            <input
              id="lineWrappingInput"
              type="checkbox"
              ?checked=${this.diffPrefs?.line_wrapping}
              @change=${this.handleLineWrappingTap}
            />
          </span>
        </section>
        <section>
          <label for="columnsInput" class="title">Diff width</label>
          <span class="value">
            <iron-input
              .allowedPattern=${'[0-9]'}
              .bindValue=${convertToString(this.diffPrefs?.line_length)}
              @change=${this.handleDiffLineLengthChanged}
            >
              <input id="columnsInput" type="number" />
            </iron-input>
          </span>
        </section>
        <section>
          <label for="tabSizeInput" class="title">Tab width</label>
          <span class="value">
            <iron-input
              .allowedPattern=${'[0-9]'}
              .bindValue=${convertToString(this.diffPrefs?.tab_size)}
              @change=${this.handleDiffTabSizeChanged}
            >
              <input id="tabSizeInput" type="number" />
            </iron-input>
          </span>
        </section>
        <section>
          <label for="fontSizeInput" class="title">Font size</label>
          <span class="value">
            <iron-input
              .allowedPattern=${'[0-9]'}
              .bindValue=${convertToString(this.diffPrefs?.font_size)}
              @change=${this.handleDiffFontSizeChanged}
            >
              <input id="fontSizeInput" type="number" />
            </iron-input>
          </span>
        </section>
        <section>
          <label for="showTabsInput" class="title">Show tabs</label>
          <span class="value">
            <input
              id="showTabsInput"
              type="checkbox"
              ?checked=${this.diffPrefs?.show_tabs}
              @change=${this.handleShowTabsTap}
            />
          </span>
        </section>
        <section>
          <label for="showTrailingWhitespaceInput" class="title"
            >Show trailing whitespace</label
          >
          <span class="value">
            <input
              id="showTrailingWhitespaceInput"
              type="checkbox"
              ?checked=${this.diffPrefs?.show_whitespace_errors}
              @change=${this.handleShowTrailingWhitespaceTap}
            />
          </span>
        </section>
        <section>
          <label for="syntaxHighlightInput" class="title"
            >Syntax highlighting</label
          >
          <span class="value">
            <input
              id="syntaxHighlightInput"
              type="checkbox"
              ?checked=${this.diffPrefs?.syntax_highlighting}
              @change=${this.handleSyntaxHighlightTap}
            />
          </span>
        </section>
        <section>
          <label for="automaticReviewInput" class="title"
            >Automatically mark viewed files reviewed</label
          >
          <span class="value">
            <input
              id="automaticReviewInput"
              type="checkbox"
              ?checked=${!this.diffPrefs?.manual_review}
              @change=${this.handleAutomaticReviewTap}
            />
          </span>
        </section>
        <section>
          <div class="pref">
            <label for="ignoreWhiteSpace" class="title"
              >Ignore Whitespace</label
            >
            <span class="value">
              <gr-select
                .bindValue=${convertToString(this.diffPrefs?.ignore_whitespace)}
                @change=${this.handleDiffIgnoreWhitespaceChanged}
              >
                <select id="ignoreWhiteSpace">
                  <option value="IGNORE_NONE">None</option>
                  <option value="IGNORE_TRAILING">Trailing</option>
                  <option value="IGNORE_LEADING_AND_TRAILING">
                    Leading &amp; trailing
                  </option>
                  <option value="IGNORE_ALL">All</option>
                </select>
              </gr-select>
            </span>
          </div>
        </section>
      </div>
    `;
  }

  private readonly handleDiffContextChanged = () => {
    this.diffPrefs!.context = Number(this.contextLineSelect!.value);
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleLineWrappingTap = () => {
    this.diffPrefs!.line_wrapping = this.lineWrappingInput!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleDiffLineLengthChanged = () => {
    this.diffPrefs!.line_length = Number(this.columnsInput!.value);
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleDiffTabSizeChanged = () => {
    this.diffPrefs!.tab_size = Number(this.tabSizeInput!.value);
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleDiffFontSizeChanged = () => {
    this.diffPrefs!.font_size = Number(this.fontSizeInput!.value);
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleShowTabsTap = () => {
    this.diffPrefs!.show_tabs = this.showTabsInput!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  // private but used in test
  readonly handleShowTrailingWhitespaceTap = () => {
    this.diffPrefs!.show_whitespace_errors =
      this.showTrailingWhitespaceInput!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleSyntaxHighlightTap = () => {
    this.diffPrefs!.syntax_highlighting = this.syntaxHighlightInput!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleAutomaticReviewTap = () => {
    this.diffPrefs!.manual_review = !this.automaticReviewInput!.checked;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  private readonly handleDiffIgnoreWhitespaceChanged = () => {
    this.diffPrefs!.ignore_whitespace = this.ignoreWhiteSpace!
      .value as IgnoreWhitespaceType;
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  };

  hasUnsavedChanges() {
    // We have to wrap boolean values in Boolean() to ensure undefined values
    // use false rather than undefined.
    return (
      Boolean(this.originalDiffPrefs?.syntax_highlighting) !==
        Boolean(this.diffPrefs?.syntax_highlighting) ||
      this.originalDiffPrefs?.context !== this.diffPrefs?.context ||
      Boolean(this.originalDiffPrefs?.line_wrapping) !==
        Boolean(this.diffPrefs?.line_wrapping) ||
      this.originalDiffPrefs?.line_length !== this.diffPrefs?.line_length ||
      this.originalDiffPrefs?.tab_size !== this.diffPrefs?.tab_size ||
      this.originalDiffPrefs?.font_size !== this.diffPrefs?.font_size ||
      this.originalDiffPrefs?.ignore_whitespace !==
        this.diffPrefs?.ignore_whitespace ||
      Boolean(this.originalDiffPrefs?.show_tabs) !==
        Boolean(this.diffPrefs?.show_tabs) ||
      Boolean(this.originalDiffPrefs?.show_whitespace_errors) !==
        Boolean(this.diffPrefs?.show_whitespace_errors) ||
      Boolean(this.originalDiffPrefs?.manual_review) !==
        Boolean(this.diffPrefs?.manual_review)
    );
  }

  async save() {
    if (!this.diffPrefs) return;
    await this.userModel.updateDiffPreference(this.diffPrefs);
    fire(this, 'has-unsaved-changes-changed', {
      value: this.hasUnsavedChanges(),
    });
  }
}

declare global {
  interface HTMLElementEventMap {
    'has-unsaved-changes-changed': ValueChangedEvent<boolean>;
  }
  interface HTMLElementTagNameMap {
    'gr-diff-preferences': GrDiffPreferences;
  }
}
