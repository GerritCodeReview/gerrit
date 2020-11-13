/**
 * @license
 * Copyright (C) 2020 The Android Open Source Settings
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// IMPORTANT !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
// The entire API is currently in DRAFT state.
// Changes to all methods and objects are expected.
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

export interface GrChecksApiInterface {
  /**
   * Must only be called once. You cannot register twice. You cannot unregister.
   */
  register(provider: ChecksProvider, config?: ChecksApiConfig): void;

  /**
   * Forces Gerrit to call fetch() on the registered provider. Can be called
   * when the provider has gotten an update and does not want wait for the next
   * polling interval to pass.
   */
  announceUpdate(): void;
}

export interface ChecksApiConfig {
  /**
   * How often should the provider be called for new CheckData while the user
   * user navigates change related pages and the browser tab remains visible?
   * Set to 0 to disable polling. Default is 60 seconds.
   */
  fetchPollingIntervalSeconds: number;
}

export interface ChecksProvider {
  /**
   * Gerrit calls this method when ...
   * - ... the change or diff page is loaded.
   * - ... the user switches back to a Gerrit tab with a change or diff page.
   * - ... while the tab is visible in a regular polling interval, see
   *       ChecksApiConfig.
   */
  fetch(change: number, patchset: number): Promise<FetchResponse>;
}

export interface FetchResponse {
  responseCode: ResponseCode;

  /** Only relevant when the responseCode is ERROR. */
  errorMessage?: string;

  /**
   * Only relevant when the responseCode is NOT_LOGGED_IN.
   * Gerrit displays a "Login" button and opens a new tab with this URL when the
   * user clicks on the button.
   */
  loginUrl?: string;

  runs: CheckRun[];
  results: CheckResult[];
}

export enum ResponseCode {
  OK,
  ERROR,
  NOT_LOGGED_IN,
}

/**
 * A CheckRun models an entity that has start/end timestamps and can be in
 * either of the states RUNNABLE, RUNNING, COMPLETED. By itself it cannot model
 * an error, neither can it be failed or successful by itself. A run can be
 * associated with 0 to n results (see below). So until runs are completed the
 * runs are more interesting for the user: What is going on at the moment? When
 * runs are completed the users' interest shifts to results: What do I have to
 * fix? The only actions that can be associated with runs are RUN and CANCEL.
 */
export interface CheckRun {
  /**
   * Gerrit requests check runs and results from the plugin by change number and
   * patchset number. So these two properties can as well be left empty when
   * returning results to the Gerrit UI and are thus optional.
   */
  change?: number;
  /**
   * Typically only runs for the latest patchset are requested and presented.
   * Older runs and their results are only available on request, e.g. by
   * switching to another patchset in a dropdown
   *
   * TBD: CI data providers may decide that runs and results are applicable to a
   * newer patchset, even if they were produced for an older, e.g. because only
   * the commit message was changed. Maybe that warrants the addition of another
   * optional field, e.g. `original_patchset`.
   */
  patchset?: number;
  /**
   * The UI will focus on just the latest attempt per run. Former attempts are
   * accessible, but initially collapsed/hidden. Lower number means older
   * attempt. Every run has its own attempt numbering, so attempt 3 of run A is
   * not directly related to attempt 3 of run B.
   *
   * RUNNABLE runs must use `undefined` as attempt.
   * COMPLETED and RUNNING runs must use an attempt number >=0.
   *
   * TBD: Optionally providing aggregate information about former attempts will
   * probably be a useful feature, but we are deferring the exact data modeling
   * of that to later.
   */
  attempt?: number;

  /**
   * An optional opaque identifier not used by Gerrit directly, but might be
   * used by plugin extensions and callbacks.
   */
  externalId?: string;

  /**
   * RUNNABLE:  Not run (yet). Mostly useful for runs that the user can trigger
   *            (see actions).
   * RUNNING:   Subsumes "scheduled".
   * COMPLETED: The attempt of the run has finished. Does not indicate at all
   *            whether the run was successful or not. Outcomes can and should
   *            be modeled using the CheckResult entity.
   */
  status: RunStatus;
  /**
   * Optional short description of the run status. This is a plain string
   * without styling or formatting options. It will only be shown as a tooltip
   * or in a hovercard.
   *
   * Examples:
   * "40 tests running, 30 completed: 0 failing so far",
   * "Scheduled 5 minutes ago, not running yet".
   */
  statusDescription?: string;
  /**
   * Optional http link to an external page with more detailed information about
   * the run status. Must begin with 'http'.
   */
  statusLink?: string;

  // The following 3 properties are independent of this run *instance*. They
  // just describe what the run is about and will be identical for other
  // attempts or patchsets or changes.

  /**
   * The unique name of the run. There can’t be two runs with the same
   * change/patchset/attempt/name combination.
   * Multiple attempts of the same run must have the same name.
   * It should be expected that this string is cut off at ~30 chars in the UI.
   * The full name will then be shown in a tooltip.
   */
  name: string;
  /**
   * Optional description of the run. Only shown as a tooltip or in a hovercard.
   */
  description?: string;
  /**
   * Optional http link to an external page with more detailed information about
   * this run. Must begin with 'http'.
   */
  link?: string;

  /**
   * Callbacks to the CI plugin. Must be implemented individually by each
   * plugin. The most important actions (which get special UI treatment) are:
   * "Run" for RUNNABLE and COMPLETED runs.
   * "Cancel" for RUNNING runs.
   *
   * TBD: Define the details of how callbacks are called. Passing an ID to a
   * predefined API method? Or are actual javascript functions being passed and
   * called?
   */
  actions: Action[];

  scheduledTimestamp?: Date;
  startedTimestamp?: Date;
  finishedTimestamp?: Date;
}

export interface Action {
  name: string;
  tooltip?: string;
  callback: (
    externalId: string,
    runName: string,
    actionName: string
  ) => Promise<ActionResult>;
}

export interface ActionResult {
  /** An empty errorMessage means success. */
  errorMessage?: string;
}

export enum RunStatus {
  RUNNABLE,
  RUNNING,
  COMPLETED,
}

interface CheckResult {
  /**
   * Reference to the run that this result belongs to. Each run can have 0-n
   * results. Each result must be associated with exactly 1 run.
   */
  checkRunName: string;
  checkRunAttempt: number;

  /**
   * An optional opaque identifier not used by Gerrit directly, but might be
   * used by plugin extensions and callbacks.
   */
  externalId?: string;

  /**
   * INFO:    The user will typically not bother to look into this category,
   *          only for looking up something that they are searching for. Can be
   *          used for reporting secondary metrics and analysis, or a wider
   *          range of artifacts produced by the CI system.
   * WARNING: A warning is something that should be read before submitting the
   *          change. The user should not ignore it, but it is also not blocking
   *          submit. It has a similar level of importance as an unresolved
   *          comment.
   * ERROR:   An error indicates that the change must not or cannot be submitted
   *          without fixing the problem. Errors will be visualized very
   *          prominently to the user.
   *
   * The ‘tags’ field below can be used for further categorization, e.g. for
   * distinguishing FAILED vs TIMED_OUT.
   */
  category: Category;

  /**
   * Short description of the check result.
   *
   * It should be expected that this string might be cut off at ~80 chars in the
   * UI. The full description will then be shown in a tooltip.
   * This is a plain string without styling or formatting options.
   *
   * Examples:
   * MessageConverterTest failed with: 'kermit' expected, but got 'ernie'.
   * Binary size of javascript bundle has increased by 27%.
   */
  summary: string;

  /**
   * Exhaustive optional message describing the check result.
   * Will be initially collapsed. Might potentially be very long, e.g. a log of
   * MB size. The UI is not limiting this. CI data providers are responsible for
   * not killing the browser. :-)
   *
   * For now this is just a plain unformatted string. The only formatting
   * applied is the one that Gerrit also applies to human comments. TBD: Both
   * human comments and check result messages should get richer formatting
   * options.
   */
  message?: string;

  /**
   * Optional reference to a Gerrit label (e.g. "Verified") that this result
   * influences. Allows the user to understand and navigate the relationship
   * between CI results and submit requirements,
   * see also https://gerrit-review.googlesource.com/c/homepage/+/279176.
   */
  label?: string;

  /**
   * Tags allow a CI System to further categorize a result, e.g. making a list
   * of results filterable by the end-user.
   * The name is free-form, but there is a predefined set of TagColors to
   * choose from with a recommendation of color for common tags, see below.
   *
   * Examples:
   * PASS, FAIL, SCHEDULED, OBSOLETE, SKIPPED, TIMED_OUT, INFRA_ERROR, FLAKY
   * WIN, MAC, LINUX
   * BUILD, TEST, LINT
   * INTEGRATION, E2E, SCREENSHOT
   */
  tags: Tag[];

  /**
   * Links provide an opportunity for the end-user to easily access details and
   * artifacts. Links are displayed by an icon+tooltip only. They don’t have a
   * name, making them clearly distinguishable from tags and actions.
   *
   * There is a fixed set of LinkIcons to choose from, see below.
   *
   * Examples:
   * Link to test log.
   * Link to result artifacts such as images and screenshots.
   * Link to downloadable artifacts such as ZIP or APK files.
   */
  links: Link[];

  /**
   * Callbacks to the CI plugin. Must be implemented individually by each
   * plugin. Actions are rendered as buttons. If there are more than two actions
   * per result, then further actions are put into an overflow menu. Sort order
   * is defined by the data provider.
   *
   * Examples:
   * Acknowledge/Dismiss, Delete, Report a bug, Report as not useful,
   * Make blocking, Downgrade severity.
   */
  actions: Action[];
}

export enum Category {
  INFO,
  WARNING,
  ERROR,
}

export interface Tag {
  name: string;
  tooltip?: string;
  color?: TagColor;
}

enum TagColor {
  GRAY,
  // TBD: Add more ...
  // TBD: Clarify standard colors for common tags.
}

export interface Link {
  /** Must begin with 'http'. */
  url: string;
  tooltip?: string;
  icon: LinkIcon;
}

enum LinkIcon {
  EXTERNAL,
  DOWNLOAD,
  // TBD: Add more ...
}
