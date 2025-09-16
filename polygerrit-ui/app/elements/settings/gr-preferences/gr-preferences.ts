/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-button/gr-button';
import {AccountDetailInfo, PreferencesInput} from '../../../types/common';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, nothing} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {convertToString} from '../../../utils/string-util';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {userModelToken} from '../../../models/user/user-model';
import {
  AppTheme,
  DateFormat,
  DiffViewMode,
  EmailFormat,
  EmailStrategy,
  TimeFormat,
} from '../../../constants/constants';
import {getAppContext} from '../../../services/app-context';
import {KnownExperimentId} from '../../../services/flags/flags';
import {areNotificationsEnabled} from '../../../utils/worker-util';
import {getDocUrl} from '../../../utils/url-util';
import {configModelToken} from '../../../models/config/config-model';
import {SuggestionsProvider} from '../../../api/suggestions';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import '@material/web/checkbox/checkbox';
import {MdCheckbox} from '@material/web/checkbox/checkbox';
import {materialStyles} from '../../../styles/gr-material-styles';
import '@material/web/select/outlined-select';
import '@material/web/select/select-option';

/**
 * This provides an interface to show settings for a user profile
 * as defined in PreferencesInfo.
 */
@customElement('gr-preferences')
export class GrPreferences extends LitElement {
  @query('#allowBrowserNotifications')
  allowBrowserNotifications?: MdCheckbox;

  @query('#allowSuggestCodeWhileCommenting')
  allowSuggestCodeWhileCommenting?: HTMLInputElement;

  @query('#allowAiCommentAutocompletion')
  allowAiCommentAutocompletion?: HTMLInputElement;

  @query('#relativeDateInChangeTable')
  relativeDateInChangeTable!: MdCheckbox;

  @query('#showSizeBarsInFileList') showSizeBarsInFileList!: MdCheckbox;

  @query('#publishCommentsOnPush') publishCommentsOnPush!: MdCheckbox;

  @query('#workInProgressByDefault') workInProgressByDefault!: MdCheckbox;

  @query('#disableKeyboardShortcuts')
  disableKeyboardShortcuts!: MdCheckbox;

  @query('#disableTokenHighlighting')
  disableTokenHighlighting!: MdCheckbox;

  @query('#insertSignedOff') insertSignedOff!: MdCheckbox;

  @state() prefs?: PreferencesInput;

  @state() private originalPrefs?: PreferencesInput;

  @state() account?: AccountDetailInfo;

  @state() private docsBaseUrl = '';

  @state()
  suggestionsProvider?: SuggestionsProvider;

  readonly getUserModel = resolve(this, userModelToken);

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getPluginLoader = resolve(this, pluginLoaderToken);

  // private but used in test
  readonly flagsService = getAppContext().flagsService;

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().preferences$,
      prefs => {
        this.originalPrefs = prefs;
        this.prefs = {...prefs};
      }
    );
    subscribe(
      this,
      () => this.getUserModel().account$,
      acc => {
        this.account = acc;
      }
    );
    subscribe(
      this,
      () => this.getConfigModel().docsBaseUrl$,
      docsBaseUrl => (this.docsBaseUrl = docsBaseUrl)
    );
    if (
      this.flagsService.isEnabled(KnownExperimentId.ML_SUGGESTED_EDIT_V2) ||
      this.flagsService.isEnabled(KnownExperimentId.COMMENT_AUTOCOMPLETION)
    ) {
      subscribe(
        this,
        () => this.getPluginLoader().pluginsModel.suggestionsPlugins$,
        // We currently support results from only 1 provider.
        suggestionsPlugins =>
          (this.suggestionsProvider = suggestionsPlugins?.[0]?.provider)
      );
    }
  }

  static override get styles() {
    return [
      sharedStyles,
      menuPageStyles,
      grFormStyles,
      materialStyles,
      css`
        :host {
          border: none;
          margin-bottom: var(--spacing-xxl);
        }
        h2 {
          font-family: var(--header-font-family);
          font-size: var(--font-size-h2);
          font-weight: var(--font-weight-h2);
          line-height: var(--line-height-h2);
        }
      `,
    ];
  }

  override render() {
    return html`
      <h2 id="Preferences" class=${this.hasUnsavedChanges() ? 'edited' : ''}>
        Preferences
      </h2>
      <fieldset id="preferences">
        <div id="preferences" class="gr-form-styles">
          <section>
            <label class="title" for="themeSelect">Theme</label>
            <span class="value">
              <md-outlined-select
                .value=${this.prefs?.theme ?? AppTheme.AUTO}
                @change=${(e: Event) => {
                  const select = e.target as HTMLSelectElement;
                  this.prefs!.theme = select.value as AppTheme;
                  this.requestUpdate();
                }}
              >
                <md-select-option value="AUTO">
                  <div slot="headline">Auto (based on OS prefs)</div>
                </md-select-option>
                <md-select-option value="LIGHT">
                  <div slot="headline">Light</div>
                </md-select-option>
                <md-select-option value="DARK">
                  <div slot="headline">Dark</div>
                </md-select-option>
              </md-outlined-select>
            </span>
          </section>
          <section>
            <label class="title" for="changesPerPageSelect"
              >Changes per page</label
            >
            <span class="value">
              <md-outlined-select
                .value=${convertToString(this.prefs?.changes_per_page)}
                @change=${(e: Event) => {
                  const select = e.target as HTMLSelectElement;
                  this.prefs!.changes_per_page = Number(select.value) as
                    | 10
                    | 25
                    | 50
                    | 100;
                  this.requestUpdate();
                }}
              >
                <md-select-option value="10">
                  <div slot="headline">10 rows per page</div>
                </md-select-option>
                <md-select-option value="25">
                  <div slot="headline">25 rows per page</div>
                </md-select-option>
                <md-select-option value="50">
                  <div slot="headline">50 rows per page</div>
                </md-select-option>
                <md-select-option value="100">
                  <div slot="headline">100 rows per page</div>
                </md-select-option>
              </md-outlined-select>
            </span>
          </section>
          <section>
            <label class="title" for="dateTimeFormatSelect"
              >Date/time format</label
            >
            <span class="value">
              <md-outlined-select
                .value=${convertToString(this.prefs?.date_format)}
                @change=${(e: Event) => {
                  const select = e.target as HTMLSelectElement;
                  this.prefs!.date_format = select.value as DateFormat;
                  this.requestUpdate();
                }}
              >
                <md-select-option value="STD">
                  <div slot="headline">Jun 3 ; Jun 3, 2016</div>
                </md-select-option>
                <md-select-option value="US">
                  <div slot="headline">06/03 ; 06/03/16</div>
                </md-select-option>
                <md-select-option value="ISO">
                  <div slot="headline">06-03 ; 2016-06-03</div>
                </md-select-option>
                <md-select-option value="EURO">
                  <div slot="headline">3. Jun ; 03.06.2016</div>
                </md-select-option>
                <md-select-option value="UK">
                  <div slot="headline">03/06 ; 03/06/2016</div>
                </md-select-option>
              </md-outlined-select>
              <md-outlined-select
                .value=${convertToString(this.prefs?.time_format)}
                @change=${(e: Event) => {
                  const select = e.target as HTMLSelectElement;
                  this.prefs!.time_format = select.value as TimeFormat;
                  this.requestUpdate();
                }}
              >
                <md-select-option value="HHMM_12">
                  <div slot="headline">4:10 PM</div>
                </md-select-option>
                <md-select-option value="HHMM_24">
                  <div slot="headline">16:10</div>
                </md-select-option>
              </md-outlined-select>
            </span>
          </section>
          <section>
            <label class="title" for="emailNotificationsSelect"
              >Email notifications</label
            >
            <span class="value">
              <md-outlined-select
                .value=${convertToString(this.prefs?.email_strategy)}
                @change=${(e: Event) => {
                  const select = e.target as HTMLSelectElement;
                  this.prefs!.email_strategy = select.value as EmailStrategy;
                  this.requestUpdate();
                }}
              >
                <md-select-option value="CC_ON_OWN_COMMENTS">
                  <div slot="headline">Every comment</div>
                </md-select-option>
                <md-select-option value="ENABLED">
                  <div slot="headline">Only comments left by others</div>
                </md-select-option>
                <md-select-option value="ATTENTION_SET_ONLY">
                  <div slot="headline">Only when I am in the attention set</div>
                </md-select-option>
              </md-outlined-select>
            </span>
          </section>
          <section>
            <label class="title" for="emailFormatSelect">Email format</label>
            <span class="value">
              <md-outlined-select
                .value=${convertToString(this.prefs?.email_format)}
                @change=${(e: Event) => {
                  const select = e.target as HTMLSelectElement;
                  this.prefs!.email_format = select.value as EmailFormat;
                  this.requestUpdate();
                }}
              >
                <md-select-option value="HTML_PLAINTEXT">
                  <div slot="headline">HTML and plaintext</div>
                </md-select-option>
                <md-select-option value="PLAINTEXT">
                  <div slot="headline">Plaintext only</div>
                </md-select-option>
              </md-outlined-select>
            </span>
          </section>
          ${this.renderBrowserNotifications()}
          ${this.renderGenerateSuggestionWhenCommenting()}
          ${this.renderAiCommentAutocompletion()}
          ${this.renderDefaultBaseForMerges()}
          <section>
            <label class="title" for="relativeDateInChangeTable"
              >Show Relative Dates In Changes Table</label
            >
            <span class="value">
              <md-checkbox
                id="relativeDateInChangeTable"
                ?checked=${!!this.prefs?.relative_date_in_change_table}
                @change=${() => {
                  this.prefs!.relative_date_in_change_table =
                    this.relativeDateInChangeTable.checked;
                  this.requestUpdate();
                }}
              ></md-checkbox>
            </span>
          </section>
          <section>
            <span class="title">Diff view</span>
            <span class="value">
              <md-outlined-select
                .value=${convertToString(this.prefs?.diff_view)}
                @change=${(e: Event) => {
                  const select = e.target as HTMLSelectElement;
                  this.prefs!.diff_view = select.value as DiffViewMode;
                  this.requestUpdate();
                }}
              >
                <md-select-option value="SIDE_BY_SIDE">
                  <div slot="headline">Side by side</div>
                </md-select-option>
                <md-select-option value="UNIFIED_DIFF">
                  <div slot="headline">Unified diff</div>
                </md-select-option>
              </md-outlined-select>
            </span>
          </section>
          <section>
            <label for="showSizeBarsInFileList" class="title"
              >Show size bars in file list</label
            >
            <span class="value">
              <md-checkbox
                id="showSizeBarsInFileList"
                ?checked=${!!this.prefs?.size_bar_in_change_table}
                @change=${() => {
                  this.prefs!.size_bar_in_change_table =
                    this.showSizeBarsInFileList.checked;
                  this.requestUpdate();
                }}
              ></md-checkbox>
            </span>
          </section>
          <section>
            <label for="publishCommentsOnPush" class="title"
              >Publish comments on push</label
            >
            <span class="value">
              <md-checkbox
                id="publishCommentsOnPush"
                ?checked=${!!this.prefs?.publish_comments_on_push}
                @change=${() => {
                  this.prefs!.publish_comments_on_push =
                    this.publishCommentsOnPush.checked;
                  this.requestUpdate();
                }}
              ></md-checkbox>
            </span>
          </section>
          <section>
            <label for="workInProgressByDefault" class="title"
              >Set new changes to "work in progress" by default</label
            >
            <span class="value">
              <md-checkbox
                id="workInProgressByDefault"
                ?checked=${!!this.prefs?.work_in_progress_by_default}
                @change=${() => {
                  this.prefs!.work_in_progress_by_default =
                    this.workInProgressByDefault.checked;
                  this.requestUpdate();
                }}
              ></md-checkbox>
            </span>
          </section>
          <section>
            <label for="disableKeyboardShortcuts" class="title"
              >Disable all keyboard shortcuts</label
            >
            <span class="value">
              <md-checkbox
                id="disableKeyboardShortcuts"
                ?checked=${!!this.prefs?.disable_keyboard_shortcuts}
                @change=${() => {
                  this.prefs!.disable_keyboard_shortcuts =
                    this.disableKeyboardShortcuts.checked;
                  this.requestUpdate();
                }}
              ></md-checkbox>
            </span>
          </section>
          <section>
            <label for="disableTokenHighlighting" class="title"
              >Disable token highlighting on hover</label
            >
            <span class="value">
              <md-checkbox
                id="disableTokenHighlighting"
                ?checked=${!!this.prefs?.disable_token_highlighting}
                @change=${() => {
                  this.prefs!.disable_token_highlighting =
                    this.disableTokenHighlighting.checked;
                  this.requestUpdate();
                }}
              ></md-checkbox>
            </span>
          </section>
          <section>
            <label for="insertSignedOff" class="title">
              Insert Signed-off-by Footer For Inline Edit Changes
            </label>
            <span class="value">
              <md-checkbox
                id="insertSignedOff"
                ?checked=${!!this.prefs?.signed_off_by}
                @change=${() => {
                  this.prefs!.signed_off_by = this.insertSignedOff.checked;
                  this.requestUpdate();
                }}
              ></md-checkbox>
            </span>
          </section>
        </div>
        <gr-button
          id="savePrefs"
          @click=${async () => {
            await this.save();
          }}
          ?disabled=${!this.hasUnsavedChanges()}
          >Save Changes</gr-button
        >
      </fieldset>
    `;
  }

  // When the experiment is over, move this back to render(),
  // removing this function.
  private renderBrowserNotifications() {
    if (
      !this.flagsService.isEnabled(
        KnownExperimentId.PUSH_NOTIFICATIONS_DEVELOPER
      ) &&
      !areNotificationsEnabled(this.account)
    )
      return nothing;
    return html` <section id="allowBrowserNotificationsSection">
      <div class="title">
        <label for="allowBrowserNotifications"
          >Allow browser notifications</label
        >
        <a
          href=${getDocUrl(
            this.docsBaseUrl,
            'user-attention-set.html#_browser_notifications'
          )}
          target="_blank"
          rel="noopener noreferrer"
        >
          <gr-icon icon="help" title="read documentation"></gr-icon>
        </a>
      </div>
      <span class="value">
        <md-checkbox
          id="allowBrowserNotifications"
          ?checked=${!!this.prefs?.allow_browser_notifications}
          @change=${() => {
            this.prefs!.allow_browser_notifications =
              this.allowBrowserNotifications!.checked;
            this.requestUpdate();
          }}
        ></md-checkbox>
      </span>
    </section>`;
  }

  // When the experiment is over, move this back to render(),
  // removing this function.
  private renderGenerateSuggestionWhenCommenting() {
    if (
      !this.flagsService.isEnabled(KnownExperimentId.ML_SUGGESTED_EDIT_V2) ||
      this.flagsService.isEnabled(
        KnownExperimentId.ML_SUGGESTED_EDIT_UNCHECK_BY_DEFAULT
      ) ||
      !this.suggestionsProvider
    )
      return nothing;
    return html`
      <section id="allowSuggestCodeWhileCommentingSection">
        <div class="title">
          <label for="allowSuggestCodeWhileCommenting"
            >AI suggested fixes while commenting</label
          >
          <a
            href=${this.suggestionsProvider.getDocumentationLink?.() ||
            getDocUrl(
              this.docsBaseUrl,
              'user-suggest-edits.html#_generate_suggestion'
            )}
            target="_blank"
            rel="noopener noreferrer"
          >
            <gr-icon icon="help" title="read documentation"></gr-icon>
          </a>
        </div>
        <span class="value">
          <input
            id="allowSuggestCodeWhileCommenting"
            type="checkbox"
            ?checked=${!!this.prefs?.allow_suggest_code_while_commenting}
            @change=${() => {
              this.prefs!.allow_suggest_code_while_commenting =
                this.allowSuggestCodeWhileCommenting!.checked;
              this.requestUpdate();
            }}
          />
        </span>
      </section>
    `;
  }

  // When the experiment is over, move this back to render(),
  // removing this function.
  private renderAiCommentAutocompletion() {
    if (
      !this.flagsService.isEnabled(KnownExperimentId.COMMENT_AUTOCOMPLETION) ||
      !this.suggestionsProvider
    )
      return nothing;
    return html`
      <section id="allowAiCommentAutocompletionSection">
        <div class="title">
          <label for="allowAiCommentAutocompletion"
            >AI suggested text completions while commenting</label
          >
        </div>
        <span class="value">
          <input
            id="allowAiCommentAutocompletion"
            type="checkbox"
            ?checked=${!!this.prefs?.allow_autocompleting_comments}
            @change=${() => {
              this.prefs!.allow_autocompleting_comments =
                this.allowAiCommentAutocompletion!.checked;
              this.requestUpdate();
            }}
          />
        </span>
      </section>
    `;
  }

  // When this is fixed and can be re-enabled, move this back to render()
  // and remove function.
  private renderDefaultBaseForMerges() {
    if (!this.prefs?.default_base_for_merges) return nothing;
    return nothing;
    // TODO: Re-enable respecting the default_base_for_merges preference.
    // See corresponding TODO in change-model.
    // return html`
    //   <section>
    //     <span class="title">Default Base For Merges</span>
    //     <span class="value">
    //       <md-outlined-select
    //         .value=${convertToString(
    //            this.prefs?.default_base_for_merges
    //          )}
    //         @change=${(e: Event) => {
    //           const select = e.target as HTMLSelectElement;
    //           this.prefs!.default_base_for_merges = select.value as DefaultBase;
    //           this.requestUpdate();
    //         }}
    //       >
    //         <md-select-option value="AUTO_MERGE">
    //           <div slot="headline">Auto Merge</div>
    //         </md-select-option>
    //         <md-select-option value="FIRST_PARENT">
    //           <div slot="headline">First Parent</div>
    //         </md-select-option>
    //       </md-outlined-select>
    //     </span>
    //   </section>
    // `;
  }

  // private but used in test
  hasUnsavedChanges() {
    // We have to wrap boolean values in Boolean() to ensure undefined values
    // use false rather than undefined.
    return (
      this.originalPrefs?.theme !== this.prefs?.theme ||
      this.originalPrefs?.changes_per_page !== this.prefs?.changes_per_page ||
      this.originalPrefs?.date_format !== this.prefs?.date_format ||
      this.originalPrefs?.time_format !== this.prefs?.time_format ||
      this.originalPrefs?.email_strategy !== this.prefs?.email_strategy ||
      this.originalPrefs?.email_format !== this.prefs?.email_format ||
      Boolean(this.originalPrefs?.allow_browser_notifications) !==
        Boolean(this.prefs?.allow_browser_notifications) ||
      Boolean(this.originalPrefs?.allow_suggest_code_while_commenting) !==
        Boolean(this.prefs?.allow_suggest_code_while_commenting) ||
      Boolean(this.originalPrefs?.allow_autocompleting_comments) !==
        Boolean(this.prefs?.allow_autocompleting_comments) ||
      this.originalPrefs?.default_base_for_merges !==
        this.prefs?.default_base_for_merges ||
      Boolean(this.originalPrefs?.relative_date_in_change_table) !==
        Boolean(this.prefs?.relative_date_in_change_table) ||
      this.originalPrefs?.diff_view !== this.prefs?.diff_view ||
      Boolean(this.originalPrefs?.size_bar_in_change_table) !==
        Boolean(this.prefs?.size_bar_in_change_table) ||
      Boolean(this.originalPrefs?.publish_comments_on_push) !==
        Boolean(this.prefs?.publish_comments_on_push) ||
      Boolean(this.originalPrefs?.work_in_progress_by_default) !==
        Boolean(this.prefs?.work_in_progress_by_default) ||
      Boolean(this.originalPrefs?.disable_keyboard_shortcuts) !==
        Boolean(this.prefs?.disable_keyboard_shortcuts) ||
      Boolean(this.originalPrefs?.disable_token_highlighting) !==
        Boolean(this.prefs?.disable_token_highlighting) ||
      Boolean(this.originalPrefs?.signed_off_by) !==
        Boolean(this.prefs?.signed_off_by)
    );
  }

  async save() {
    if (!this.prefs) return;
    await this.getUserModel().updatePreferences(this.prefs);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-preferences': GrPreferences;
  }
}
