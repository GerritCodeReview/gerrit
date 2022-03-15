import {html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {ProgressStatus} from '../../../constants/constants';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {resolve} from '../../../models/dependency';
import {ChangeInfo, NumericChangeId} from '../../../types/common';
import {subscribe} from '../../lit/subscription-controller';

@customElement('gr-change-list-bulk-action-table')
export class GrChangeListBulkActionTable extends LitElement {
  @property({type: Object, reflect: false})
  getColumnValues: (change: ChangeInfo) => string[] = () => [];

  @property({type: Array})
  columnTitles: string[] = [];

  @state() selectedChanges: ChangeInfo[] = [];

  @state() progress: Map<NumericChangeId, ProgressStatus> = new Map();

  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  override connectedCallback() {
    super.connectedCallback();
    subscribe(
      this,
      this.getBulkActionsModel().selectedChanges$,
      selectedChanges => {
        this.selectedChanges = selectedChanges;
        this.progress = new Map(
          this.selectedChanges.map(change => [
            change._number,
            ProgressStatus.NOT_STARTED,
          ])
        );
      }
    );
  }

  override render() {
    return html`
      <table>
        <thead>
          <tr>
            ${this.columnTitles.map(title => html`<th>${title}</th>`)}
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          ${this.selectedChanges.map(
            change =>
              html`
                <tr>
                  ${this.getColumnValues(change).map(
                    column => html`<td>${column}</td>`
                  )}
                  <td>Status: ${this.progress.get(change._number)}</td>
                </tr>
              `
          )}
        </tbody>
      </table>
    `;
  }

  public trackBulkActionProgress(
    inFlightActions: Promise<Response | undefined>[]
  ) {
    for (let index = 0; index < inFlightActions.length; index++) {
      const changeNum = this.selectedChanges[index]._number;
      inFlightActions[index]
        .then(() => {
          this.progress.set(changeNum, ProgressStatus.SUCCESSFUL);
          this.requestUpdate();
        })
        .catch(() => {
          this.progress.set(changeNum, ProgressStatus.FAILED);
          this.requestUpdate();
        });
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-bulk-action-table': GrChangeListBulkActionTable;
  }
}
