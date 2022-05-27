/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {CommentRange} from './core';
import {ChangeInfo} from './rest-api';

export declare interface ChecksPluginApi {
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

  /**
   * Updates an individual result.
   *
   * This can be used for lazy loading detailed information. For example, if you
   * are using the `check-result-expanded` endpoint, then you can load more
   * result details when the user expands a result row.
   *
   * The parameter `run` is only used to *find* the correct run for updating the
   * result. It will only be used for comparing `change`, `patchset`, `attempt`
   * and `checkName`. Its properties other than `results` will not be updated.
   *
   * For us being able to identify the result that you want to update you have
   * to set the `externalId` property. An undefined `externalId` will result in
   * an error.
   */
  updateResult(run: CheckRun, result: CheckResult): void;
}

export declare interface ChecksApiConfig {
  /**
   * How often should the provider be called for new CheckData while the user
   * navigates change related pages and the browser tab remains visible?
   * Set to 0 to disable polling. Default is 60 seconds.
   */
  fetchPollingIntervalSeconds: number;
}

export declare interface ChangeData {
  changeNumber: number;
  patchsetNumber: number;
  patchsetSha: string;
  repo: string;
  commitMessage?: string;
  changeInfo: ChangeInfo;
}

export declare interface ChecksProvider {
  /**
   * Gerrit calls this method when ...
   * - ... the change or diff page is loaded.
   * - ... the user switches back to a Gerrit tab with a change or diff page.
   * - ... while the tab is visible in a regular polling interval, see
   *       ChecksApiConfig.
   */
  fetch(change: ChangeData): Promise<FetchResponse>;
}

export declare interface FetchResponse {
  responseCode: ResponseCode;

  /** Only relevant when the responseCode is ERROR. */
  errorMessage?: string;

  /**
   * Only relevant when the responseCode is NOT_LOGGED_IN.
   * Gerrit displays a "Login" button and calls this callback when the user
   * clicks on the button.
   */
  loginCallback?: () => void;

  /**
   * Top-level actions that are not associated with a specific run or result.
   * Will be shown as buttons in the header of the Checks tab.
   */
  actions?: Action[];

  /**
   * Shown prominently in the change summary below the run chips.
   */
  summaryMessage?: string;

  /**
   * Top-level links that are not associated with a specific run or result.
   * Will be shown as icons in the header of the Checks tab.
   */
  links?: Link[];

  runs?: CheckRun[];
}

export enum ResponseCode {
  OK = 'OK',
  ERROR = 'ERROR',
  NOT_LOGGED_IN = 'NOT_LOGGED_IN',
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
export declare interface CheckRun {
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
   * TBD: Check data providers may decide that runs and results are applicable
   * to a newer patchset, even if they were produced for an older, e.g. because
   * only the commit message was changed. Maybe that warrants the addition of
   * another optional field, e.g. `original_patchset`.
   */
  patchset?: number;
  /**
   * The UI will focus on just the latest attempt per run. Former attempts are
   * accessible, but initially collapsed/hidden. Lower number means older
   * attempt. Every run has its own attempt numbering, so attempt 3 of run A is
   * not directly related to attempt 3 of run B.
   *
   * The attempt number must be >=0. Only if you have just one RUNNABLE attempt,
   * then you can leave it undefined.
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

  // The following 3 properties are independent of this run *instance*. They
  // just describe what the check is about and will be identical for other
  // attempts or patchsets or changes.

  /**
   * The unique name of the check. There can’t be two runs with the same
   * change/patchset/attempt/checkName combination.
   * Multiple attempts of the same run must have the same checkName.
   * It should be expected that this string is cut off at ~30 chars in the UI.
   * The full name will then be shown in a tooltip.
   */
  checkName: string;
  /**
   * Optional description of the check. Only shown as a tooltip or in a
   * hovercard.
   */
  checkDescription?: string;
  /**
   * Optional http link to an external page with more detailed information about
   * this run. Must begin with 'http'.
   */
  checkLink?: string;

  /**
   * RUNNABLE:  Not run (yet). Mostly useful for runs that the user can trigger
   *            (see actions) and for indicating that a check was not run at a
   *            later attempt. Cannot contain results.
   * RUNNING:   The run is in progress.
   * SCHEDULED: Refinement of RUNNING: The run was triggered, but is not yet
   *            running. It may have to wait for resources or for some other run
   *            to finish. The UI treats this mostly identical to RUNNING, but
   *            uses a differnt icon.
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

  /**
   * Optional reference to a Gerrit label (e.g. "Verified") that this result
   * influences. Allows the user to understand and navigate the relationship
   * between check runs/results and submit requirements,
   * see also https://gerrit-review.googlesource.com/c/homepage/+/279176.
   */
  labelName?: string;

  /**
   * Optional callbacks to the plugin. Must be implemented individually by
   * each plugin. The most important actions (which get special UI treatment)
   * are:
   * "Run" for RUNNABLE and COMPLETED runs.
   * "Cancel" for RUNNING and SCHEDULED runs.
   */
  actions?: Action[];

  scheduledTimestamp?: Date;
  startedTimestamp?: Date;
  /**
   * For RUNNING runs this is considered to be an estimate of when the run will
   * be finished.
   */
  finishedTimestamp?: Date;

  /**
   * List of results produced by this run.
   * RUNNABLE runs must not have results.
   * RUNNING runs can contain (intermediate) results.
   * Nesting the results in runs enforces that:
   * - A run can have 0-n results.
   * - A result is associated with exactly one run.
   */
  results?: CheckResult[];
}

export declare interface Action {
  name: string;
  tooltip?: string;
  /**
   * Primary actions will get a more prominent treatment in the UI. For example
   * primary actions might be rendered as buttons versus just menu entries in
   * an overflow menu.
   */
  primary?: boolean;
  /**
   * Summary actions will get an even more prominent treatment in the UI. They
   * will show up in the checks summary right below the commit message. This
   * only affects top-level actions (i.e. actions in FetchResponse).
   */
  summary?: boolean;
  /**
   * Renders the action button in a disabled state. That can be useful for
   * actions that are present most of the time, but sometimes don't apply. Then
   * a grayed out button with a tooltip makes it easier for the user to
   * understand why an expected action is not available. The tooltip should then
   * contain a message about why the disabled state was set, not just about what
   * the action would normally do.
   */
  disabled?: boolean;
  callback: ActionCallback;
}

export type ActionCallback = (
  change: number,
  patchset: number,
  /**
   * Identical to 'attempt' property of CheckRun. Not set for top-level
   * actions.
   */
  attempt: number | undefined,
  /**
   * Identical to 'externalId' property of CheckRun. Not set for top-level
   * actions.
   */
  externalId: string | undefined,
  /**
   * Identical to 'checkName' property of CheckRun. Not set for top-level
   * actions.
   */
  checkName: string | undefined,
  /** Identical to 'name' property of Action entity. */
  actionName: string
  /**
   * If the callback does not return a promise, then the user will get no
   * feedback from the Gerrit UI. This is adequate when the plugin opens a
   * dialog for example. If a Promise<ActionResult> is returned, then Gerrit
   * will show toasts for user feedback, see ActionResult below.
   */
) => Promise<ActionResult> | undefined;

/**
 * Until the Promise<ActionResult> resolves (max. 5 seconds) Gerrit will show a
 * toast with the message `Triggering action '${action.name}' ...`.
 *
 * When the promise resolves (within 5 seconds) then Gerrit will replace the
 * toast with a new one with the message `${message}` and show it for 5 seconds.
 * If `message` is empty or undefined, then the `Triggering ...` toast will just
 * be hidden and no further toast will be shown.
 */
export declare interface ActionResult {
  /** An empty errorMessage means success. */
  message?: string;
  /**
   * If true, then ChecksProvider.fetch() is called. Has the same affect as if
   * the plugin would call announceUpdate(). So just for convenience.
   */
  shouldReload?: boolean;
  /** DEPRECATED: Use `message` instead. */
  errorMessage?: string;
}

/** See CheckRun.status for documentation. */
export enum RunStatus {
  RUNNABLE = 'RUNNABLE',
  RUNNING = 'RUNNING',
  SCHEDULED = 'SCHEDULED',
  COMPLETED = 'COMPLETED',
}

export declare interface CheckResult {
  /**
   * An optional opaque identifier not used by Gerrit directly, but might be
   * used by plugin extensions and callbacks.
   */
  externalId?: string;

  /**
   * SUCCESS: Indicates that some build, test or check is passing. A COMPLETED
   *          run without results will also be treated as "passing" and will get
   *          an artificial SUCCESS result. But you can also make this explicit,
   *          which also allows one run to have multiple "passing" results,
   *          maybe along with results of other categories.
   * INFO:    The user will typically not bother to look into this category,
   *          only for looking up something that they are searching for. Can be
   *          used for reporting secondary metrics and analysis, or a wider
   *          range of artifacts produced by the checks system.
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
   * MB size. The UI is not limiting this. Data providing plugins are
   * responsible for not killing the browser. :-)
   *
   * For now this is just a plain unformatted string. The only formatting
   * applied is the one that Gerrit also applies to human comments. TBD: Both
   * human comments and check result messages should get richer formatting
   * options.
   */
  message?: string;

  /**
   * Tags allow a plugins to further categorize a result, e.g. making a list
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
  tags?: Tag[];

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
  links?: Link[];

  /**
   * Link to lines of code. The referenced path must be part of this patchset.
   *
   * Only one code pointer is supported. If the array contains, more than one
   * pointer, then all the other pointers will be ignored. Support for multiple
   * code pointers will only added on demand.
   */
  codePointers?: CodePointer[];

  /**
   * Callbacks to the plugin. Must be implemented individually by each
   * plugin. Actions are rendered as buttons. If there are more than two actions
   * per result, then further actions are put into an overflow menu. Sort order
   * is defined by the data provider.
   *
   * Examples:
   * Acknowledge/Dismiss, Delete, Report a bug, Report as not useful,
   * Make blocking, Downgrade severity.
   */
  actions?: Action[];
}

export enum Category {
  SUCCESS = 'SUCCESS',
  INFO = 'INFO',
  WARNING = 'WARNING',
  ERROR = 'ERROR',
}

export declare interface Tag {
  name: string;
  tooltip?: string;
  color?: TagColor;
}

// TBD: Clarify standard colors for common tags.
export enum TagColor {
  GRAY = 'gray',
  YELLOW = 'yellow',
  PINK = 'pink',
  PURPLE = 'purple',
  CYAN = 'cyan',
  BROWN = 'brown',
}

export declare interface Link {
  /** Must begin with 'http'. */
  url: string;
  tooltip?: string;
  /**
   * Primary links will get a more prominent treatment in the UI, e.g. being
   * always visible in the results table or also showing up in the change page
   * summary of checks.
   */
  primary: boolean;
  icon: LinkIcon;
}

export declare interface CodePointer {
  path: string;
  range: CommentRange;
}

export enum LinkIcon {
  EXTERNAL = 'external',
  IMAGE = 'image',
  HISTORY = 'history',
  DOWNLOAD = 'download',
  DOWNLOAD_MOBILE = 'download_mobile',
  HELP_PAGE = 'help_page',
  REPORT_BUG = 'report_bug',
  CODE = 'code',
  FILE_PRESENT = 'file_present',
}
