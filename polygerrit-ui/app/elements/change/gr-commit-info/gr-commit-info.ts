/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '../../../styles/shared-styles';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-commit-info_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property, computed} from '@polymer/decorators';
import {ChangeInfo, CommitInfo, ServerInfo} from '../../../types/common';

declare global {
  interface HTMLElementTagNameMap {
    'gr-commit-info': GrCommitInfo;
  }
}

@customElement('gr-commit-info')
export class GrCommitInfo extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  // TODO(TS): can not use `?` here as @computed require dependencies as
  // not optional
  @property({type: Object})
  change: ChangeInfo | undefined;

  // TODO(TS): maybe limit to StandaloneCommitInfo if never pass in
  // with commit inside RevisionInfo
  @property({type: Object})
  commitInfo: CommitInfo | undefined;

  @property({type: Object})
  serverConfig: ServerInfo | undefined;

  @computed('change', 'commitInfo', 'serverConfig')
  get _showWebLink(): boolean {
    if (!this.change || !this.commitInfo || !this.serverConfig) {
      return false;
    }

    const weblink = this._getWeblink(
      this.change,
      this.commitInfo,
      this.serverConfig
    );
    return !!weblink && !!weblink.url;
  }

  @computed('change', 'commitInfo', 'serverConfig')
  get _webLink(): string | undefined {
    if (!this.change || !this.commitInfo || !this.serverConfig) {
      return '';
    }

    // TODO(TS): if getPatchSetWeblink always return a valid WebLink,
    // can remove the fallback here
    const {url} =
      this._getWeblink(this.change, this.commitInfo, this.serverConfig) || {};
    return url;
  }

  _getWeblink(change: ChangeInfo, commitInfo: CommitInfo, config: ServerInfo) {
    return GerritNav.getPatchSetWeblink(change.project, commitInfo.commit, {
      weblinks: commitInfo.web_links,
      config,
    });
  }

  _computeShortHash(
    change?: ChangeInfo,
    commitInfo?: CommitInfo,
    serverConfig?: ServerInfo
  ) {
    if (!change || !commitInfo || !serverConfig) {
      return '';
    }

    const {name} = this._getWeblink(change, commitInfo, serverConfig) || {};
    return name;
  }
}
