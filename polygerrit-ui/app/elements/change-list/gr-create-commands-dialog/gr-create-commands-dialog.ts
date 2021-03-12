/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-shell-command/gr-shell-command';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {customElement, property} from '@polymer/decorators';
import {htmlTemplate} from './gr-create-commands-dialog_html';

enum Commands {
  CREATE = 'git commit',
  AMEND = 'git commit --amend',
  PUSH_PREFIX = 'git push origin HEAD:refs/for/',
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-commands-dialog': GrCreateCommandsDialog;
  }
}

export interface GrCreateCommandsDialog {
  $: {
    commandsOverlay: GrOverlay;
  };
}

@customElement('gr-create-commands-dialog')
export class GrCreateCommandsDialog extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String})
  branch?: string;

  @property({type: String})
  readonly _createNewCommitCommand = Commands.CREATE;

  @property({type: String})
  readonly _amendExistingCommitCommand = Commands.AMEND;

  @property({
    type: String,
    computed: '_computePushCommand(branch)',
  })
  _pushCommand?: string;

  open() {
    this.$.commandsOverlay.open();
  }

  _handleClose() {
    this.$.commandsOverlay.close();
  }

  _computePushCommand(branch: string): string {
    return Commands.PUSH_PREFIX + branch;
  }
}
