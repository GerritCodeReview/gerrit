import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrRepoDetailList} from '../../../../elements/admin/gr-repo-detail-list/gr-repo-detail-list';

export interface PolymerDomRepeatEventModel<T> {
  /**
   * The item corresponding to the element in the dom-repeat.
   */
  item: T;

  /**
   * The index of the element in the dom-repeat.
   */
  index: number;
  get: (name: string) => T;
  set: (name: string, val: T) => void;
}

declare function wrapInPolymerDomRepeatEvent<T, U>(event: T, item: U): T & {model: PolymerDomRepeatEventModel<U>};
declare function setTextContent(content: unknown): void;
declare function useVars(...args: unknown[]): void;

type UnionToIntersection<T> = (
  T extends any ? (v: T) => void : never
  ) extends (v: infer K) => void
  ? K
  : never;

type AddNonDefinedProperties<T, P> = {
  [K in keyof P]: K extends keyof T ? T[K] : undefined;
};

type FlatUnion<T, TIntersect> = T extends any
  ? AddNonDefinedProperties<T, TIntersect>
  : never;

type AllUndefined<T> = {
  [P in keyof T]: undefined;
}

type UnionToAllUndefined<T> = T extends any ? AllUndefined<T> : any

type Flat<T> = FlatUnion<T, UnionToIntersection<UnionToAllUndefined<T>>>;

declare function __f<T>(obj: T): Flat<NonNullable<T>>;

declare function pc<T>(obj: T): PolymerDeepPropertyChange<T, T>;

declare function convert<T, U extends T>(obj: T): U;

export class GrRepoDetailListCheck extends GrRepoDetailList
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['gr-list-view'] = null!;
      useVars(el);
      el.createNew = this._loggedIn;
      el.filter = this._filter;
      el.itemsPerPage = this._itemsPerPage;
      el.items = this._items;
      el.loading = this._loading;
      el.offset = this._offset;
      el.addEventListener('create-clicked', this._handleCreateClicked.bind(this));
      el.path = this._getPath(this._repo, this.detailType);
    }
    {
      const el: HTMLElementTagNameMap['table'] = null!;
      useVars(el);
      el.setAttribute('id', `list`);
      el.setAttribute('class', `genericList gr-form-styles`);
    }
    {
      const el: HTMLElementTagNameMap['tbody'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
      el.setAttribute('class', `headerRow`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `name topHeader`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `revision topHeader`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `message topHeader ${this._hideIfBranch(this.detailType)}`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `tagger topHeader ${this._hideIfBranch(this.detailType)}`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `repositoryBrowser topHeader`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `delete topHeader`);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
      el.setAttribute('id', `loading`);
      el.setAttribute('class', `loadingMsg ${this.computeLoadingClass(this._loading)}`);
    }
    {
      const el: HTMLElementTagNameMap['td'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tbody'] = null!;
      useVars(el);
      el.setAttribute('class', `${this.computeLoadingClass(this._loading)}`);
    }
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const item of this._shownItems!)
      {
        {
          const el: HTMLElementTagNameMap['tr'] = null!;
          useVars(el);
          el.setAttribute('class', `table`);
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `${this.detailType} name`);
        }
        {
          const el: HTMLElementTagNameMap['a'] = null!;
          useVars(el);
          el.setAttribute('href', `${this._computeFirstWebLink(item)}`);
        }
        setTextContent(`
                ${this._stripRefs(__f(item)!.ref, this.detailType)}
              `);

        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `${this.detailType} revision ${this._computeCanEditClass(__f(item)!.ref, this.detailType, this._isOwner)}`);
        }
        {
          const el: HTMLElementTagNameMap['span'] = null!;
          useVars(el);
          el.setAttribute('class', `revisionNoEditing`);
        }
        setTextContent(` ${__f(item)!.revision} `);

        {
          const el: HTMLElementTagNameMap['span'] = null!;
          useVars(el);
          el.setAttribute('class', `revisionEdit ${this._computeEditingClass(this._isEditing)}`);
        }
        {
          const el: HTMLElementTagNameMap['span'] = null!;
          useVars(el);
          el.setAttribute('class', `revisionWithEditing`);
        }
        setTextContent(` ${__f(item)!.revision} `);

        {
          const el: HTMLElementTagNameMap['gr-button'] = null!;
          useVars(el);
          el.link = true;
          el.addEventListener('click', e => this._handleEditRevision.bind(this, wrapInPolymerDomRepeatEvent(e, item))());
          el.setAttribute('class', `editBtn`);
        }
        {
          const el: HTMLElementTagNameMap['iron-input'] = null!;
          useVars(el);
          el.bindValue = this._revisedRef;
          this._revisedRef = convert(el.bindValue);
          el.setAttribute('class', `editItem`);
        }
        {
          const el: HTMLElementTagNameMap['iron-input'] = null!;
          useVars(el);
          el.bindValue = this._revisedRef;
          this._revisedRef = convert(el.bindValue);
        }
        {
          const el: HTMLElementTagNameMap['gr-button'] = null!;
          useVars(el);
          el.link = true;
          el.addEventListener('click', e => this._handleCancelRevision.bind(this, wrapInPolymerDomRepeatEvent(e, item))());
          el.setAttribute('class', `cancelBtn editItem`);
        }
        {
          const el: HTMLElementTagNameMap['gr-button'] = null!;
          useVars(el);
          el.link = true;
          el.addEventListener('click', e => this._handleSaveRevision.bind(this, wrapInPolymerDomRepeatEvent(e, item))());
          el.setAttribute('class', `saveBtn editItem`);
          el.disabled = !this._revisedRef;
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `message ${this._hideIfBranch(this.detailType)}`);
        }
        setTextContent(`
              ${this._computeMessage(__f(item)!.message)}
            `);

        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `tagger ${this._hideIfBranch(this.detailType)}`);
        }
        {
          const el: HTMLElementTagNameMap['div'] = null!;
          useVars(el);
          el.setAttribute('class', `tagger ${this._computeHideTagger(__f(item)!.tagger)}`);
        }
        {
          const el: HTMLElementTagNameMap['gr-account-link'] = null!;
          useVars(el);
          el.account = __f(item)!.tagger;
        }
        {
          const el: HTMLElementTagNameMap['gr-date-formatter'] = null!;
          useVars(el);
          el.hasTooltip = true;
          el.dateStr = __f(__f(item)!.tagger)!.date;
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `repositoryBrowser`);
        }
        {
          const el: HTMLElementTagNameMap['dom-repeat'] = null!;
          useVars(el);
        }
        {
          const index = 0;
          const itemsIndexAs = 0;
          useVars(index, itemsIndexAs);
          for(const link of this._computeWeblink(item)!)
          {
            {
              const el: HTMLElementTagNameMap['a'] = null!;
              useVars(el);
              el.setAttribute('href', `${__f(link)!.url}`);
              el.setAttribute('class', `webLink`);
            }
            setTextContent(`
                  (${__f(link)!.name})
                `);

          }
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `delete`);
        }
        {
          const el: HTMLElementTagNameMap['gr-button'] = null!;
          useVars(el);
          el.link = true;
          el.setAttribute('class', `deleteButton ${this._computeHideDeleteClass(this._isOwner, __f(item)!.can_delete)}`);
          el.addEventListener('click', e => this._handleDeleteItem.bind(this, wrapInPolymerDomRepeatEvent(e, item))());
        }
      }
    }
    {
      const el: HTMLElementTagNameMap['gr-overlay'] = null!;
      useVars(el);
      el.setAttribute('id', `overlay`);
    }
    {
      const el: HTMLElementTagNameMap['gr-confirm-delete-item-dialog'] = null!;
      useVars(el);
      el.setAttribute('class', `confirmDialog`);
      el.addEventListener('confirm', this._handleDeleteItemConfirm.bind(this));
      el.addEventListener('cancel', this._handleConfirmDialogCancel.bind(this));
      el.item = this._refName;
      el.itemTypeName = this._computeItemName(this.detailType);
    }
    {
      const el: HTMLElementTagNameMap['gr-overlay'] = null!;
      useVars(el);
      el.setAttribute('id', `createOverlay`);
    }
    {
      const el: HTMLElementTagNameMap['gr-dialog'] = null!;
      useVars(el);
      el.setAttribute('id', `createDialog`);
      el.disabled = !this._hasNewItemName;
      el.confirmLabel = `Create`;
      el.addEventListener('confirm', this._handleCreateItem.bind(this));
      el.addEventListener('cancel', this._handleCloseCreate.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `header`);
    }
    setTextContent(`
        Create ${this._computeItemName(this.detailType)}
      `);

    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `main`);
    }
    {
      const el: HTMLElementTagNameMap['gr-create-pointer-dialog'] = null!;
      useVars(el);
      el.setAttribute('id', `createNewModal`);
      el.detailType = this._computeItemName(this.detailType);
      el.hasNewItemName = this._hasNewItemName;
      this._hasNewItemName = el.hasNewItemName;
      el.itemDetail = this.detailType;
      el.repoName = this._repo;
    }
  }
}

