/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {hasOwnProperty} from '../../../utils/common-util';
import {
  ChangeInfo,
  LabelNameToValueMap,
  ReviewInput,
  RevisionInfo,
} from '../../../types/common';
import {GrAdminApi} from '../../plugins/gr-admin-api/gr-admin-api';
import {
  JsApiService,
  EventCallback,
  ShowChangeDetail,
  ShowRevisionActionsDetail,
} from './gr-js-api-types';
import {EventType, TargetElement} from '../../../api/plugin';
import {ParsedChangeInfo} from '../../../types/types';
import {MenuLink} from '../../../api/admin';
import {Finalizable} from '../../../services/registry';
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

  canSubmitChange(change: ChangeInfo, revision?: RevisionInfo | null) {
    const submitCallbacks = this._getEventCallbacks(EventType.SUBMIT_CHANGE);
    const cancelSubmit = submitCallbacks.some(callback => {
      try {
        return callback(change, revision) === false;
      } catch (err: unknown) {
        this.reporting.error(
          'GrJsApiInterface',
          new Error('canSubmitChange callback error'),
          err
        );
      }
      return false;
    });

    return !cancelSubmit;
  }

  /** For testing only. */
  _removeEventCallbacks() {
    for (const type of Object.values(EventType)) {
      eventCallbacks[type] = [];
    }
  }

  async handleShowChange(detail: ShowChangeDetail) {
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
    const patchNum = detail.patchNum;
    const info = detail.info;

    let revision;
    for (const rev of Object.values(change.revisions || {})) {
      if (rev._number === patchNum) {
        revision = rev;
        break;
      }
    }

    for (const cb of this._getEventCallbacks(EventType.SHOW_CHANGE)) {
      try {
        cb(change, revision, info);
      } catch (err: unknown) {
        this.reporting.error(
          'GrJsApiInterface',
          new Error('showChange callback error'),
          err
        );
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
        this.reporting.error(
          'GrJsApiInterface',
          new Error('showRevisionActions callback error'),
          err
        );
      }
    }
  }

  handleCommitMessage(change: ChangeInfo | ParsedChangeInfo, msg: string) {
    for (const cb of this._getEventCallbacks(EventType.COMMIT_MSG_EDIT)) {
      try {
        cb(change, msg);
      } catch (err: unknown) {
        this.reporting.error(
          'GrJsApiInterface',
          new Error('commitMessage callback error'),
          err
        );
      }
    }
  }

  async handleLabelChange(detail: {change?: ParsedChangeInfo}) {
    await this.waitForPluginsToLoad();
    for (const cb of this._getEventCallbacks(EventType.LABEL_CHANGE)) {
      try {
        cb(detail.change);
      } catch (err: unknown) {
        this.reporting.error(
          'GrJsApiInterface',
          new Error('labelChange callback error'),
          err
        );
      }
    }
  }

  modifyRevertMsg(change: ChangeInfo, revertMsg: string, origMsg: string) {
    for (const cb of this._getEventCallbacks(EventType.REVERT)) {
      try {
        revertMsg = cb(change, revertMsg, origMsg) as string;
      } catch (err: unknown) {
        this.reporting.error(
          'GrJsApiInterface',
          new Error('modifyRevertMsg callback error'),
          err
        );
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
        this.reporting.error(
          'GrJsApiInterface',
          new Error('modifyRevertSubmissionMsg callback error'),
          err
        );
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
        this.reporting.error(
          'GrJsApiInterface',
          new Error('getReviewPostRevert callback error'),
          err
        );
      }
    }
    return review;
  }

  _getEventCallbacks(type: EventType) {
    return eventCallbacks[type] || [];
  }
}
