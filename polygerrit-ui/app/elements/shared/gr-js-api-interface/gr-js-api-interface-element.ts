/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {getPluginLoader} from './gr-plugin-loader';
import {hasOwnProperty} from '../../../utils/common-util';
import {
  ChangeInfo,
  LabelNameToValuesMap,
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
import {appContext} from '../../../services/app-context';
import {MenuLink} from '../../../api/admin';

const elements: {[key: string]: HTMLElement} = {};
const eventCallbacks: {[key: string]: EventCallback[]} = {};

export class GrJsApiInterface implements JsApiService {
  private readonly reporting = appContext.reportingService;

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
      } catch (err) {
        this.reporting.error(err);
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
      } catch (err) {
        this.reporting.error(err);
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
      } catch (err) {
        this.reporting.error(err);
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
      } catch (err) {
        this.reporting.error(err);
      }
    }
  }

  handleCommitMessage(change: ChangeInfo | ParsedChangeInfo, msg: string) {
    for (const cb of this._getEventCallbacks(EventType.COMMIT_MSG_EDIT)) {
      try {
        cb(change, msg);
      } catch (err) {
        this.reporting.error(err);
      }
    }
  }

  // TODO(TS): The COMMENT event and its handler seem unused.
  _handleComment(detail: {node: Node}) {
    for (const cb of this._getEventCallbacks(EventType.COMMENT)) {
      try {
        cb(detail.node);
      } catch (err) {
        this.reporting.error(err);
      }
    }
  }

  _handleLabelChange(detail: {change: ChangeInfo}) {
    for (const cb of this._getEventCallbacks(EventType.LABEL_CHANGE)) {
      try {
        cb(detail.change);
      } catch (err) {
        this.reporting.error(err);
      }
    }
  }

  _handleHighlightjsLoaded(detail: {hljs: HighlightJS}) {
    for (const cb of this._getEventCallbacks(EventType.HIGHLIGHTJS_LOADED)) {
      try {
        cb(detail.hljs);
      } catch (err) {
        this.reporting.error(err);
      }
    }
  }

  modifyRevertMsg(change: ChangeInfo, revertMsg: string, origMsg: string) {
    for (const cb of this._getEventCallbacks(EventType.REVERT)) {
      try {
        revertMsg = cb(change, revertMsg, origMsg) as string;
      } catch (err) {
        this.reporting.error(err);
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
      } catch (err) {
        this.reporting.error(err);
      }
    }
    return revertSubmissionMsg;
  }

  getDiffLayers(path: string, changeNum: number) {
    const layers: DiffLayer[] = [];
    for (const cb of this._getEventCallbacks(EventType.ANNOTATE_DIFF)) {
      const annotationApi = (cb as unknown) as GrAnnotationActionsInterface;
      try {
        const layer = annotationApi.getLayer(path, changeNum);
        layers.push(layer);
      } catch (err) {
        this.reporting.error(err);
      }
    }
    return layers;
  }

  disposeDiffLayers(path: string) {
    for (const cb of this._getEventCallbacks(EventType.ANNOTATE_DIFF)) {
      try {
        const annotationApi = (cb as unknown) as GrAnnotationActionsInterface;
        annotationApi.disposeLayer(path);
      } catch (err) {
        this.reporting.error(err);
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
          const annotationApi = (cb as unknown) as GrAnnotationActionsInterface;
          const provider = annotationApi.getCoverageProvider();
          if (provider) providers.push(annotationApi);
        });
        return providers;
      });
  }

  getAdminMenuLinks(): MenuLink[] {
    const links: MenuLink[] = [];
    for (const cb of this._getEventCallbacks(EventType.ADMIN_MENU_LINKS)) {
      const adminApi = (cb as unknown) as GrAdminApi;
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
          review = {labels: r as LabelNameToValuesMap};
        }
      } catch (err) {
        this.reporting.error(err);
      }
    }
    return review;
  }

  _getEventCallbacks(type: EventType) {
    return eventCallbacks[type] || [];
  }
}
