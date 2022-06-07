/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {getPluginLoader} from './gr-plugin-loader';
import {hasOwnProperty} from '../../../utils/common-util';
import {
  ChangeInfo,
  LabelNameToValueMap,
  ReviewInput,
  RevisionInfo,
} from '../../../types/common';
import {GrAnnotationActionsInterface} from './gr-annotation-actions-js-api';
import {GrAdminApi} from '../../plugins/gr-admin-api/gr-admin-api';
import {
  JsApiService,
  EventCallback,
  ShowChangeDetail,
  ShowRevisionActionsDetail,
} from './gr-js-api-types';
import {EventType, TargetElement} from '../../../api/plugin';
import {DiffLayer, HighlightJS, ParsedChangeInfo} from '../../../types/types';
import {MenuLink} from '../../../api/admin';
import {Finalizable} from '../../../services/registry';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';

const elements: {[key: string]: HTMLElement} = {};
const eventCallbacks: {[key: string]: EventCallback[]} = {};

export class GrJsApiInterface implements JsApiService, Finalizable {
  constructor(readonly reporting: ReportingService) {}

  finalize() {}

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  handleEvent(type: EventType, detail: any) {
    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        switch (type) {
          case EventType.HISTORY:
            this._handleHistory(detail);
            break;
          case EventType.SHOW_CHANGE:
            this._handleShowChange(detail);
            break;
          case EventType.COMMENT:
            this._handleComment(detail);
            break;
          case EventType.LABEL_CHANGE:
            this._handleLabelChange(detail);
            break;
          case EventType.SHOW_REVISION_ACTIONS:
            this._handleShowRevisionActions(detail);
            break;
          case EventType.HIGHLIGHTJS_LOADED:
            this._handleHighlightjsLoaded(detail);
            break;
          default:
            console.warn(
              'handleEvent called with unsupported event type:',
              type
            );
            break;
        }
      });
  }

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
          new Error('canSubmitChange callback error'),
          undefined,
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

  // TODO(TS): The HISTORY event and its handler seem unused.
  _handleHistory(detail: {path: string}) {
    for (const cb of this._getEventCallbacks(EventType.HISTORY)) {
      try {
        cb(detail.path);
      } catch (err: unknown) {
        this.reporting.error(
          new Error('handleHistory callback error'),
          undefined,
          err
        );
      }
    }
  }

  _handleShowChange(detail: ShowChangeDetail) {
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
          new Error('showChange callback error'),
          undefined,
          err
        );
      }
    }
  }

  _handleShowRevisionActions(detail: ShowRevisionActionsDetail) {
    const registeredCallbacks = this._getEventCallbacks(
      EventType.SHOW_REVISION_ACTIONS
    );
    for (const cb of registeredCallbacks) {
      try {
        cb(detail.revisionActions, detail.change);
      } catch (err: unknown) {
        this.reporting.error(
          new Error('showRevisionActions callback error'),
          undefined,
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
          new Error('commitMessage callback error'),
          undefined,
          err
        );
      }
    }
  }

  // TODO(TS): The COMMENT event and its handler seem unused.
  _handleComment(detail: {node: Node}) {
    for (const cb of this._getEventCallbacks(EventType.COMMENT)) {
      try {
        cb(detail.node);
      } catch (err: unknown) {
        this.reporting.error(
          new Error('comment callback error'),
          undefined,
          err
        );
      }
    }
  }

  _handleLabelChange(detail: {change: ChangeInfo}) {
    for (const cb of this._getEventCallbacks(EventType.LABEL_CHANGE)) {
      try {
        cb(detail.change);
      } catch (err: unknown) {
        this.reporting.error(
          new Error('labelChange callback error'),
          undefined,
          err
        );
      }
    }
  }

  _handleHighlightjsLoaded(detail: {hljs: HighlightJS}) {
    for (const cb of this._getEventCallbacks(EventType.HIGHLIGHTJS_LOADED)) {
      try {
        cb(detail.hljs);
      } catch (err: unknown) {
        this.reporting.error(
          new Error('HighlightjsLoaded callback error'),
          undefined,
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
          new Error('modifyRevertMsg callback error'),
          undefined,
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
          new Error('modifyRevertSubmissionMsg callback error'),
          undefined,
          err
        );
      }
    }
    return revertSubmissionMsg;
  }

  getDiffLayers(path: string) {
    const layers: DiffLayer[] = [];
    for (const cb of this._getEventCallbacks(EventType.ANNOTATE_DIFF)) {
      const annotationApi = cb as unknown as GrAnnotationActionsInterface;
      try {
        const layer = annotationApi.createLayer(path);
        if (layer) layers.push(layer);
      } catch (err: unknown) {
        this.reporting.error(
          new Error('getDiffLayers callback error'),
          undefined,
          err
        );
      }
    }
    return layers;
  }

  disposeDiffLayers(path: string) {
    for (const cb of this._getEventCallbacks(EventType.ANNOTATE_DIFF)) {
      try {
        const annotationApi = cb as unknown as GrAnnotationActionsInterface;
        annotationApi.disposeLayer(path);
      } catch (err: unknown) {
        this.reporting.error(
          new Error('disposeDiffLayers callback error'),
          undefined,
          err
        );
      }
    }
  }

  /**
   * Retrieves coverage data possibly provided by a plugin.
   *
   * Will wait for plugins to be loaded. If multiple plugins offer a coverage
   * provider, the first one is returned. If no plugin offers a coverage provider,
   * will resolve to null.
   */
  getCoverageAnnotationApis(): Promise<GrAnnotationActionsInterface[]> {
    return getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        const providers: GrAnnotationActionsInterface[] = [];
        this._getEventCallbacks(EventType.ANNOTATE_DIFF).forEach(cb => {
          const annotationApi = cb as unknown as GrAnnotationActionsInterface;
          const provider = annotationApi.getCoverageProvider();
          if (provider) providers.push(annotationApi);
        });
        return providers;
      });
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
          new Error('getReviewPostRevert callback error'),
          undefined,
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
