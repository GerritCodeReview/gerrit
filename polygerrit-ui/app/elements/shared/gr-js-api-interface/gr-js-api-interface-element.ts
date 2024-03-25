/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {hasOwnProperty} from '../../../utils/common-util';
import {
  ChangeInfo,
  LabelNameToValueMap,
  PARENT,
  ReviewInput,
  RevisionInfo,
} from '../../../types/common';
import {GrAdminApi} from '../../plugins/gr-admin-api/gr-admin-api';
import {
  JsApiService,
  EventCallback,
  ShowChangeDetail,
  ShowDiffDetail,
  ShowRevisionActionsDetail,
} from './gr-js-api-types';
import {EventType, TargetElement} from '../../../api/plugin';
import {
  Finalizable,
  ParsedChangeInfo,
  EditRevisionInfo,
} from '../../../types/types';
import {MenuLink} from '../../../api/admin';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {Provider} from '../../../models/dependency';

const elements: {[key: string]: HTMLElement} = {};
const eventCallbacks: {[key: string]: EventCallback[]} = {};

export class GrJsApiInterface implements JsApiService, Finalizable {
  constructor(
    private waitForPluginsToLoad: Provider<Promise<void>>,
    readonly reporting: ReportingService
  ) {}

  finalize() {}

  addElement(key: TargetElement, el: HTMLElement) {
    elements[key] = el;
  }

  getElement(key: TargetElement) {
    return elements[key];
  }

  addEventCallback(eventName: EventType, callback: EventCallback) {
    if (!eventCallbacks[eventName]) {
      eventCallbacks[eventName] = [];
    }
    eventCallbacks[eventName].push(callback);
  }

  async canSubmitChange(change: ChangeInfo, revision?: RevisionInfo | null) {
    const returnValues = await this.handleEvent(EventType.SUBMIT_CHANGE, cb =>
      cb(change, revision)
    );
    console.log(
      `${Date.now() % 100000} asdf ${returnValues} ${returnValues.length}`
    );
    const cancelSubmit = returnValues.some(r => r === false);
    console.log(`${Date.now() % 100000} asdf ${cancelSubmit} ${!cancelSubmit}`);
    return !cancelSubmit;
  }

  /** For testing only. */
  _removeEventCallbacks() {
    for (const type of Object.values(EventType)) {
      eventCallbacks[type] = [];
    }
  }

  async handleShowChange(detail: ShowChangeDetail) {
    if (!detail.change) return;
    // Note (issue 8221) Shallow clone the change object and add a mergeable
    // getter with deprecation warning. This makes the change detail appear as
    // though SKIP_MERGEABLE was not set, so that plugins that expect it can
    // still access.
    //
    // This clone and getter can be removed after plugins migrate to use
    // info.mergeable.
    //
    // assign on getter with existing property will report error
    // see Issue: 12286
    const change = {
      ...detail.change,
      get mergeable() {
        console.warn(
          'Accessing change.mergeable from SHOW_CHANGE is ' +
            'deprecated! Use info.mergeable instead.'
        );
        return detail.info && detail.info.mergeable;
      },
    };
    const {patchNum, info, basePatchNum} = detail;

    let revision: RevisionInfo | EditRevisionInfo;
    let baseRevision: RevisionInfo | EditRevisionInfo;
    for (const rev of Object.values(change.revisions || {})) {
      if (rev._number === patchNum) {
        revision = rev;
      }
      if (rev._number === basePatchNum) {
        baseRevision = rev;
      }
    }

    await this.handleEvent(EventType.SHOW_CHANGE, cb =>
      cb(change, revision, info, baseRevision ?? PARENT)
    );
  }

  async handleShowRevisionActions(detail: ShowRevisionActionsDetail) {
    await this.handleEvent(EventType.SHOW_REVISION_ACTIONS, cb =>
      cb(detail.revisionActions, detail.change)
    );
  }

  async handleCommitMessage(
    change: ChangeInfo | ParsedChangeInfo,
    msg: string
  ) {
    await this.handleEvent(EventType.COMMIT_MSG_EDIT, cb => cb(change, msg));
  }

  async handleLabelChange(detail: {change?: ParsedChangeInfo}) {
    await this.handleEvent(EventType.LABEL_CHANGE, cb => cb(detail.change));
  }

  async modifyRevertMsg(
    change: ChangeInfo,
    revertMsg: string,
    origMsg: string
  ) {
    // We are not using `this.handleEvent()` here, because `revertMsg` is
    // repeatedly modified by each callback.
    await this.waitForPluginsToLoad();
    for (const cb of this._getEventCallbacks(EventType.REVERT)) {
      try {
        revertMsg = cb(change, revertMsg, origMsg) as string;
      } catch (err: unknown) {
        this.reportError(err, EventType.REVERT);
      }
    }
    return revertMsg;
  }

  async modifyRevertSubmissionMsg(
    change: ChangeInfo,
    revertSubmissionMsg: string,
    origMsg: string
  ) {
    // We are not using `this.handleEvent()` here, because `revertSubmissionMsg`
    // is repeatedly modified by each callback.
    await this.waitForPluginsToLoad();
    for (const cb of this._getEventCallbacks(EventType.REVERT_SUBMISSION)) {
      try {
        revertSubmissionMsg = cb(
          change,
          revertSubmissionMsg,
          origMsg
        ) as string;
      } catch (err: unknown) {
        this.reportError(err, EventType.REVERT_SUBMISSION);
      }
    }
    return revertSubmissionMsg;
  }

  getAdminMenuLinks(): MenuLink[] {
    const links: MenuLink[] = [];
    for (const cb of this._getEventCallbacks(EventType.ADMIN_MENU_LINKS)) {
      const adminApi = cb as unknown as GrAdminApi;
      links.push(...adminApi.getMenuLinks());
    }
    return links;
  }

  async getReviewPostRevert(
    change?: ChangeInfo
  ): Promise<ReviewInput | undefined> {
    const returnValues = await this.handleEvent(EventType.POST_REVERT, cb =>
      cb(change)
    );
    if (returnValues.length === 0) return {};
    const r = returnValues[0];
    return hasOwnProperty(r, 'labels') ? r : {labels: r as LabelNameToValueMap};
  }

  async handleShowDiff(detail: ShowDiffDetail): Promise<void> {
    await this.handleEvent(EventType.SHOW_DIFF, cb =>
      cb(detail.change, detail.patchRange, detail.fileRange)
    );
  }

  async handleEvent(
    type: EventType,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    handler: (callback: EventCallback) => any
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  ): Promise<any[]> {
    const returnValues = [];
    await this.waitForPluginsToLoad();
    for (const cb of this._getEventCallbacks(type)) {
      try {
        returnValues.push(handler(cb));
      } catch (err: unknown) {
        this.reportError(err, type);
      }
    }
    return returnValues;
  }

  reportError(err: unknown, type: EventType) {
    this.reporting.error(
      'GrJsApiInterface',
      new Error(`plugin event callback error for type "${type}"`),
      err
    );
  }

  _getEventCallbacks(type: EventType) {
    return eventCallbacks[type] || [];
  }
}
