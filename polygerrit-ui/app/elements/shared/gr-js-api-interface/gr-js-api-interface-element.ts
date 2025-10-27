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
  EventCallback,
  JsApiService,
  ShowChangeDetail,
  ShowDiffDetail,
  ShowRevisionActionsDetail,
} from './gr-js-api-types';
import {EventType, TargetElement} from '../../../api/plugin';
import {Finalizable, ParsedChangeInfo} from '../../../types/types';
import {MenuLink} from '../../../api/admin';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {Provider} from '../../../models/dependency';
import {EmojiSuggestion} from '../gr-suggestion-textarea/gr-suggestion-textarea';

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

  canSubmitChange(change: ChangeInfo, revision?: RevisionInfo | null) {
    const submitCallbacks = this._getEventCallbacks(EventType.SUBMIT_CHANGE);
    const cancelSubmit = submitCallbacks.some(callback => {
      try {
        return callback(change, revision) === false;
      } catch (err: unknown) {
        this.reportError(err, EventType.SUBMIT_CHANGE);
      }
      return false;
    });

    return !cancelSubmit;
  }

  async handleBeforeChangeAction(
    key: string,
    change?: ChangeInfo
  ): Promise<boolean> {
    let okay = true;
    for (const cb of this._getEventCallbacks(EventType.BEFORE_CHANGE_ACTION)) {
      try {
        okay = (await cb(key, change)) && okay;
      } catch (err: unknown) {
        this.reportError(err, EventType.BEFORE_CHANGE_ACTION);
      }
    }
    return okay;
  }

  async handleBeforePublishEdit(change: ChangeInfo): Promise<boolean> {
    await this.waitForPluginsToLoad();
    let okay = true;
    for (const cb of this._getEventCallbacks(EventType.BEFORE_PUBLISH_EDIT)) {
      try {
        okay = (await cb(change)) && okay;
      } catch (err: unknown) {
        this.reportError(err, EventType.BEFORE_PUBLISH_EDIT);
      }
    }
    return okay;
  }

  handlePublishEdit(change: ChangeInfo, revision?: RevisionInfo | null) {
    for (const cb of this._getEventCallbacks(EventType.PUBLISH_EDIT)) {
      try {
        cb(change, revision);
      } catch (err: unknown) {
        this.reportError(err, EventType.PUBLISH_EDIT);
      }
    }
  }

  /** For testing only. */
  _removeEventCallbacks() {
    for (const type of Object.values(EventType)) {
      eventCallbacks[type] = [];
    }
  }

  async handleViewChange(view: string) {
    await this.waitForPluginsToLoad();
    for (const cb of this._getEventCallbacks(EventType.VIEW_CHANGE)) {
      try {
        cb(view);
      } catch (err: unknown) {
        this.reportError(err, EventType.VIEW_CHANGE);
      }
    }
  }

  async handleShowChange(detail: ShowChangeDetail) {
    if (!detail.change) return;
    await this.waitForPluginsToLoad();
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

    let revision;
    let baseRevision;
    for (const rev of Object.values(change.revisions || {})) {
      if (rev._number === patchNum) {
        revision = rev;
      }
      if (rev._number === basePatchNum) {
        baseRevision = rev;
      }
    }

    for (const cb of this._getEventCallbacks(EventType.SHOW_CHANGE)) {
      try {
        cb(change, revision, info, baseRevision ?? PARENT);
      } catch (err: unknown) {
        this.reportError(err, EventType.SHOW_CHANGE);
      }
    }
  }

  async handleBeforeReplySent(
    change: ChangeInfo | ParsedChangeInfo,
    reviewInput: ReviewInput
  ): Promise<boolean> {
    await this.waitForPluginsToLoad();
    let okay = true;
    for (const cb of this._getEventCallbacks(EventType.BEFORE_REPLY_SENT)) {
      try {
        okay = (await cb(change, reviewInput)) && okay;
      } catch (err: unknown) {
        this.reportError(err, EventType.BEFORE_REPLY_SENT);
      }
    }
    return okay;
  }

  async handleReplySent() {
    await this.waitForPluginsToLoad();
    for (const cb of this._getEventCallbacks(EventType.REPLY_SENT)) {
      try {
        cb();
      } catch (err: unknown) {
        this.reportError(err, EventType.REPLY_SENT);
      }
    }
  }

  async handleShowRevisionActions(detail: ShowRevisionActionsDetail) {
    await this.waitForPluginsToLoad();
    const registeredCallbacks = this._getEventCallbacks(
      EventType.SHOW_REVISION_ACTIONS
    );
    for (const cb of registeredCallbacks) {
      try {
        cb(detail.revisionActions, detail.change);
      } catch (err: unknown) {
        this.reportError(err, EventType.SHOW_REVISION_ACTIONS);
      }
    }
  }

  async handleBeforeCommitMessage(
    change: ChangeInfo | ParsedChangeInfo,
    msg: string
  ): Promise<boolean> {
    let okay = true;
    for (const cb of this._getEventCallbacks(
      EventType.BEFORE_COMMIT_MSG_EDIT
    )) {
      try {
        okay = (await cb(change, msg)) && okay;
      } catch (err: unknown) {
        this.reportError(err, EventType.BEFORE_COMMIT_MSG_EDIT);
      }
    }
    return okay;
  }

  handleCommitMessage(change: ChangeInfo | ParsedChangeInfo, msg: string) {
    for (const cb of this._getEventCallbacks(EventType.COMMIT_MSG_EDIT)) {
      try {
        cb(change, msg);
      } catch (err: unknown) {
        this.reportError(err, EventType.COMMIT_MSG_EDIT);
      }
    }
  }

  async handleLabelChange(detail: {change?: ParsedChangeInfo}) {
    await this.waitForPluginsToLoad();
    for (const cb of this._getEventCallbacks(EventType.LABEL_CHANGE)) {
      try {
        cb(detail.change);
      } catch (err: unknown) {
        this.reportError(err, EventType.LABEL_CHANGE);
      }
    }
  }

  modifyEmojis(emojis?: EmojiSuggestion[]) {
    for (const cb of this._getEventCallbacks(EventType.CUSTOM_EMOJIS)) {
      try {
        emojis = cb(emojis);
      } catch (err: unknown) {
        this.reportError(err, EventType.CUSTOM_EMOJIS);
      }
    }
    return emojis;
  }

  modifyRevertMsg(change: ChangeInfo, revertMsg: string, origMsg: string) {
    for (const cb of this._getEventCallbacks(EventType.REVERT)) {
      try {
        revertMsg = cb(change, revertMsg, origMsg) as string;
      } catch (err: unknown) {
        this.reportError(err, EventType.REVERT);
      }
    }
    return revertMsg;
  }

  modifyRevertSubmissionMsg(
    change: ChangeInfo,
    revertSubmissionMsg: string,
    origMsg: string
  ) {
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

  getReviewPostRevert(change?: ChangeInfo): ReviewInput {
    let review: ReviewInput = {};
    for (const cb of this._getEventCallbacks(EventType.POST_REVERT)) {
      try {
        const r = cb(change);
        if (hasOwnProperty(r, 'labels')) {
          review = r as ReviewInput;
        } else {
          review = {labels: r as LabelNameToValueMap};
        }
      } catch (err: unknown) {
        this.reportError(err, EventType.POST_REVERT);
      }
    }
    return review;
  }

  async handleShowDiff(detail: ShowDiffDetail): Promise<void> {
    await this.waitForPluginsToLoad();
    for (const cb of this._getEventCallbacks(EventType.SHOW_DIFF)) {
      try {
        cb(detail.change, detail.patchRange, detail.fileRange);
      } catch (err: unknown) {
        this.reportError(err, EventType.SHOW_DIFF);
      }
    }
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
