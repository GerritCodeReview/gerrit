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

import {dedupingMixin} from '@polymer/polymer/lib/utils/mixin.js';
import {PolymerElement} from '@polymer/polymer';
import {Constructor} from '../../utils/common-util';
import {property} from '@polymer/decorators';
import {ServerInfo} from '../../types/common';

/**
 * @polymer
 * @mixinFunction
 */
export const ChangeTableMixin = dedupingMixin(
  <T extends Constructor<PolymerElement>>(
    superClass: T
  ): T & Constructor<ChangeTableMixinInterface> => {
    /**
     * @polymer
     * @mixinClass
     */
    class Mixin extends superClass {
      @property({type: Array, readOnly: true})
      columnNames: string[] = [
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

      /**
       * Returns the complement to the given column array
       *
       */
      getComplementColumns(columns: string[]) {
        return this.columnNames.filter(column => !columns.includes(column));
      }

      isColumnHidden(
        columnToCheck: string | undefined,
        columnsToDisplay: string[] | undefined
      ) {
        if ([columnsToDisplay, columnToCheck].includes(undefined)) {
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
      isColumnEnabled(
        column: string,
        config: ServerInfo,
        experiments: string[]
      ) {
        if (!config || !config.change) return true;
        if (column === 'Assignee') return !!config.change.enable_assignee;
        if (column === 'Comments')
          return experiments.includes('comments-column');
        if (column === 'Reviewers') return !!config.change.enable_attention_set;
        return true;
      }

      /**
       * @return {!Array<string>} enabled columns, see isColumnEnabled().
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
       * @return {!Array<string>} If the column was renamed, returns a new array
       *     with the corrected name. Otherwise, it returns the original param.
       */
      getVisibleColumns(columns: string[]) {
        const projectIndex = columns.indexOf('Project');
        if (projectIndex === -1) {
          return columns;
        }
        const newColumns = [...columns];
        newColumns[projectIndex] = 'Repo';
        return newColumns;
      }
    }

    return Mixin;
  }
);

export interface ChangeTableMixinInterface {
  getComplementColumns(columns: string[]): string[];
  isColumnHidden(
    columnToCheck: string | undefined,
    columnsToDisplay: string[] | undefined
  ): boolean;
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
  getVisibleColumns(columns: string[]): string[];
}
