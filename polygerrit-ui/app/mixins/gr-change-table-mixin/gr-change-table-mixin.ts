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

import {PolymerElement} from '@polymer/polymer';
import {Constructor} from '../../utils/common-util';
import {property} from '@polymer/decorators';
import {ServerInfo} from '../../types/common';

/**
 * @polymer
 * @mixinFunction
 */
export const ChangeTableMixin = <T extends Constructor<PolymerElement>>(
  superClass: T
) => {
  /**
   * @polymer
   * @mixinClass
   */
  class Mixin extends superClass {
    @property({type: Array})
    readonly columnNames: string[] = [
      'Subject',
      'Status',
      'Owner',
      'Assignee',
      'Reviewers',
      'Comments',
      'Repo',
      'Branch',
      'Updated',
      'Size',
    ];

    isColumnHidden(columnToCheck?: string, columnsToDisplay?: string[]) {
      if (!columnsToDisplay || !columnToCheck) {
        return false;
      }
      return !columnsToDisplay.includes(columnToCheck);
    }

    /**
     * Is the column disabled by a server config or experiment? For example the
     * assignee feature might be disabled and thus the corresponding column is
     * also disabled.
     *
     */
    isColumnEnabled(column: string, config: ServerInfo, experiments: string[]) {
      if (!config || !config.change) return true;
      if (column === 'Assignee') return !!config.change.enable_assignee;
      if (column === 'Comments') return experiments.includes('comments-column');
      return true;
    }

    /**
     * @return enabled columns, see isColumnEnabled().
     */
    getEnabledColumns(
      columns: string[],
      config: ServerInfo,
      experiments: string[]
    ) {
      return columns.filter(col =>
        this.isColumnEnabled(col, config, experiments)
      );
    }

    /**
     * The Project column was renamed to Repo, but some users may have
     * preferences that use its old name. If that column is found, rename it
     * before use.
     *
     * @return If the column was renamed, returns a new array
     * with the corrected name. Otherwise, it returns the original param.
     */
    renameProjectToRepoColumn(columns: string[]) {
      const projectIndex = columns.indexOf('Project');
      if (projectIndex === -1) {
        return columns;
      }
      const newColumns = [...columns];
      newColumns[projectIndex] = 'Repo';
      return newColumns;
    }
  }

  return Mixin as T & Constructor<ChangeTableMixinInterface>;
};

export interface ChangeTableMixinInterface {
  readonly columnNames: string[];
  isColumnHidden(columnToCheck?: string, columnsToDisplay?: string[]): boolean;
  isColumnEnabled(
    column: string,
    config: ServerInfo,
    experiments: string[]
  ): boolean;
  getEnabledColumns(
    columns: string[],
    config: ServerInfo,
    experiments: string[]
  ): string[];
  renameProjectToRepoColumn(columns: string[]): string[];
}
