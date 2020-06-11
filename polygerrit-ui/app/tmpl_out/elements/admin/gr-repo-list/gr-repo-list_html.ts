import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrRepoList} from '../../../../elements/admin/gr-repo-list/gr-repo-list';

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

export class GrRepoListCheck extends GrRepoList
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['gr-list-view'] = null!;
      useVars(el);
      el.createNew = this._createNewCapability;
      el.filter = this._filter;
      el.itemsPerPage = this._reposPerPage;
      el.items = this._repos;
      el.loading = this._loading;
      el.offset = this._offset;
      el.addEventListener('create-clicked', this._handleCreateClicked.bind(this));
      el.path = this._path;
    }
    {
      const el: HTMLElementTagNameMap['table'] = null!;
      useVars(el);
      el.setAttribute('id', `list`);
      el.setAttribute('class', `genericList`);
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
      el.setAttribute('class', `repositoryBrowser topHeader`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `changesLink topHeader`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `topHeader readOnly`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `description topHeader`);
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
      for(const item of this._shownRepos!)
      {
        {
          const el: HTMLElementTagNameMap['tr'] = null!;
          useVars(el);
          el.setAttribute('class', `table`);
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `name`);
        }
        {
          const el: HTMLElementTagNameMap['a'] = null!;
          useVars(el);
          el.setAttribute('href', `${this._computeRepoUrl(__f(item)!.name)}`);
        }
        setTextContent(`${__f(item)!.name}`);

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
                  ${__f(link)!.name}
                `);

          }
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `changesLink`);
        }
        {
          const el: HTMLElementTagNameMap['a'] = null!;
          useVars(el);
          el.setAttribute('href', `${this._computeChangesLink(__f(item)!.name)}`);
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `readOnly`);
        }
        setTextContent(`${this._readOnly(item)}`);

        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `description`);
        }
        setTextContent(`${__f(item)!.description}`);

      }
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
      el.setAttribute('class', `confirmDialog`);
      el.disabled = !this._hasNewRepoName;
      el.confirmLabel = `Create`;
      el.addEventListener('confirm', this._handleCreateRepo.bind(this));
      el.addEventListener('cancel', this._handleCloseCreate.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `header`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `main`);
    }
    {
      const el: HTMLElementTagNameMap['gr-create-repo-dialog'] = null!;
      useVars(el);
      el.hasNewRepoName = this._hasNewRepoName;
      this._hasNewRepoName = el.hasNewRepoName;
      el.setAttribute('id', `createNewModal`);
    }
  }
}

