%% Copyright (C) 2011 The Android Open Source Project
%%
%% Licensed under the Apache License, Version 2.0 (the "License");
%% you may not use this file except in compliance with the License.
%% You may obtain a copy of the License at
%%
%% http://www.apache.org/licenses/LICENSE-2.0
%%
%% Unless required by applicable law or agreed to in writing, software
%% distributed under the License is distributed on an "AS IS" BASIS,
%% WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
%% See the License for the specific language governing permissions and
%% limitations under the License.

:- package gerrit.
'$init' :- init.


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% init:
%%
%%   Initialize the module's private state. These typically take the form of global
%%   aliased hashes carrying "constant" data about the current change for any
%%   predicate that needs to obtain it.
%%
init :-
  define_hash(commit_labels),
  define_hash(current_user).

define_hash(A) :- hash_exists(A), !, hash_clear(A).
define_hash(A) :- atom(A), !, new_hash(_, [alias(A)]).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% commit_label/2:
%%
%% During rule evaluation of a change, this predicate is defined to
%% be a table of labels that pertain to the commit of interest.
%%
%%   commit_label( label('Code-Review', 2), user(12345789) ).
%%   commit_label( label('Verified', -1), user(8181) ).
%%
:- public commit_label/2.
%%
commit_label(L, User) :- L = label(H, _),
  atom(H),
  !,
  hash_get(commit_labels, H, Cached),
  ( [] == Cached ->
    get_commit_labels(_),
    hash_get(commit_labels, H, Rs), !
    ;
    Rs = Cached
  ),
  scan_commit_labels(Rs, L, User)
  .
commit_label(Label, User) :-
  get_commit_labels(Rs),
  scan_commit_labels(Rs, Label, User).

scan_commit_labels([R | Rs], L, U) :- R = commit_label(L, U).
scan_commit_labels([_ | Rs], L, U) :- scan_commit_labels(Rs, L, U).
scan_commit_labels([], _, _) :- fail.

get_commit_labels(Rs) :-
  hash_contains_key(commit_labels, '$all'),
  !,
  hash_get(commit_labels, '$all', Rs)
  .
get_commit_labels(Rs) :-
  '$load_commit_labels'(Rs),
  set_commit_labels(Rs).

set_commit_labels(Rs) :-
  define_hash(commit_labels),
  hash_put(commit_labels, '$all', Rs),
  index_commit_labels(Rs).

index_commit_labels([]).
index_commit_labels([R | Rs]) :-
  R = commit_label(label(H, _), _),
  atom(H),
  !,
  hash_get(commit_labels, H, Tmp),
  hash_put(commit_labels, H, [R | Tmp]),
  index_commit_labels(Rs)
  .
index_commit_labels([_ | Rs]) :-
  index_commit_labels(Rs).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% user_label_range/4:
%%
%%   Lookup the range allowed to be used.
%%
user_label_range(Label, Who, Min, Max) :-
  Who = user(_), !,
  atom(Label),
  current_user(Who, User),
  '$user_label_range'(Label, User, Min, Max).
user_label_range(Label, test_user(Name), Min, Max) :-
  clause(user:test_grant(Label, test_user(Name), range(Min, Max)), _)
  .


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% not_same/2:
%%
:- public not_same/2.
%%
not_same(ok(A), ok(B)) :- !, A \= B.
not_same(label(_, ok(A)), label(_, ok(B))) :- !, A \= B.
not_same(_, _).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% can_submit/2:
%%
%%   Executes the SubmitRule for each solution until one where all of the
%%   states has the format label(_, ok(_)) is found, then cut away any
%%   remaining choice points leaving this as the last solution.
%%
:- public can_submit/2.
%%
can_submit(SubmitRule, S) :-
  call_submit_rule(SubmitRule, Tmp),
  Tmp =.. [submit | Ls],
  ( is_all_ok(Ls) -> S = ok(Tmp), ! ; S = not_ready(Tmp) ).

call_submit_rule(P:X, Arg) :- !, F =.. [X, Arg], P:F.
call_submit_rule(X, Arg) :- !, F =.. [X, Arg], F.

is_all_ok([]).
is_all_ok([label(_, ok(__)) | Ls]) :- is_all_ok(Ls).
is_all_ok(_) :- fail.


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% locate_submit_rule/1:
%%
%%   Finds a submit_rule depending on what rules are available.
%%   If none are available, use default_submit/1.
%%
:- public locate_submit_rule/1.
%%

locate_submit_rule(RuleName) :-
  '$compiled_predicate'(user, submit_rule, 1),
  !,
  RuleName = user:submit_rule
  .
locate_submit_rule(RuleName) :-
  clause(user:submit_rule(_), _),
  !,
  RuleName = user:submit_rule
  .
locate_submit_rule(RuleName) :-
  RuleName = gerrit:default_submit.


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% default_submit/1:
%%
:- public default_submit/1.
%%
default_submit(P) :-
  get_legacy_approval_types(ApprovalTypes),
  default_submit(ApprovalTypes, P).

% Apply the old "all approval categories must be satisfied"
% loop by scanning over all of the approval types to build
% up the submit record.
%
default_submit(ApprovalTypes, P) :-
  default_submit(ApprovalTypes, [], Tmp),
  reverse(Tmp, Ls),
  P =.. [ submit | Ls].

default_submit([], Out, Out).
default_submit([Type | Types], Tmp, Out) :-
  approval_type(Label, Id, Fun, Min, Max) = Type,
  legacy_submit_rule(Fun, Label, Id, Min, Max, Status),
  R = label(Label, Status),
  default_submit(Types, [R | Tmp], Out).


%% legacy_submit_rule:
%%
%% Apply the old -2..+2 style logic.
%%
legacy_submit_rule('MaxWithBlock', Label, Id, Min, Max, T) :- !, max_with_block(Label, Min, Max, T).
legacy_submit_rule('MaxNoBlock', Label, Id, Min, Max, T) :- !, max_no_block(Label, Max, T).
legacy_submit_rule('NoBlock', Label, Id, Min, Max, T) :- true.
legacy_submit_rule('NoOp', Label, Id, Min, Max, T) :- true.
legacy_submit_rule(Fun, Label, Id, Min, Max, T) :- T = impossible(unsupported(Fun)).


%% max_with_block:
%%
%% - The minimum is never used.
%% - At least one maximum is used.
%%
:- public max_with_block/4.
%%
max_with_block(Label, Min, Max, reject(Who)) :-
  check_label_range_permission(Label, Min, ok(Who)),
  !
  .
max_with_block(Label, Min, Max, ok(Who)) :-
  \+ check_label_range_permission(Label, Min, ok(_)),
  check_label_range_permission(Label, Max, ok(Who)),
  !
  .
max_with_block(Label, Min, Max, need(Max)) :-
  true
  .
%TODO Uncomment this clause when group suggesting is possible.
%max_with_block(Label, Min, Max, need(Max, Group)) :-
%  \+ check_label_range_permission(Label, Max, ok(_)),
%  check_label_range_permission(Label, Max, ask(Group))
%  .
%max_with_block(Label, Min, Max, impossible(no_access)) :-
%  \+ check_label_range_permission(Label, Max, ask(Group))
%  .


%% max_no_block:
%%
%% - At least one maximum is used.
%%
max_no_block(Label, Max, ok(Who)) :-
  check_label_range_permission(Label, Max, ok(Who)),
  !
  .
max_no_block(Label, Max, need(Max)) :-
  true
  .
%TODO Uncomment this clause when group suggesting is possible.
%max_no_block(Label, Max, need(Max, Group)) :-
%  check_label_range_permission(Label, Max, ask(Group))
%  .
%max_no_block(Label, Max, impossible(no_access)) :-
%  \+ check_label_range_permission(Label, Max, ask(Group))
%  .


%% check_label_range_permission:
%%
check_label_range_permission(Label, ExpValue, ok(Who)) :-
  commit_label(label(Label, ExpValue), Who),
  user_label_range(Label, Who, Min, Max),
  Min @=< ExpValue, ExpValue @=< Max
  .
%TODO Uncomment this clause when group suggesting is possible.
%check_label_range_permission(Label, ExpValue, ask(Group)) :-
%  grant_range(Label, Group, Min, Max),
%  Min @=< ExpValue, ExpValue @=< Max
%  .


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% filter_submit_results/3:
%%
%%   Executes the submit_filter against the given list of results,
%%   returns a list of filtered results.
%%
:- public filter_submit_results/3.
%%
filter_submit_results(Filter, In, Out) :-
    filter_submit_results(Filter, In, [], Tmp),
    reverse(Tmp, Out).
filter_submit_results(Filter, [I | In], Tmp, Out) :-
    arg(1, I, R),
    call_submit_filter(Filter, R, S),
    !,
    S =.. [submit | Ls],
    ( is_all_ok(Ls) -> T = ok(S) ; T = not_ready(S) ),
    filter_submit_results(Filter, In, [T | Tmp], Out).
filter_submit_results(Filter, [_ | In], Tmp, Out) :-
   filter_submit_results(Filter, In, Tmp, Out), 
   !
   .
filter_submit_results(Filter, [], Out, Out).

call_submit_filter(P:X, R, S) :- !, F =.. [X, R, S], P:F.
call_submit_filter(X, R, S) :- F =.. [X, R, S], F.


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% locate_submit_filter/1:
%%
%%   Finds a submit_filter if available.
%%
:- public locate_submit_filter/1.
%%
locate_submit_filter(FilterName) :-
  '$compiled_predicate'(user, submit_filter, 2),
  !,
  FilterName = user:submit_filter
  .
locate_submit_filter(FilterName) :-
  clause(user:submit_filter(_,_), _),
  FilterName = user:submit_filter
  .


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% find_label/3:
%%
%%   Finds labels successively and fails when there are no more results.
%%
:- public find_label/3.
%%
find_label([], _, _) :- !, fail.
find_label(List, Name, Label) :-
  List = [_ | _],
  !,
  find_label2(List, Name, Label).
find_label(S, Name, Label) :-
  S =.. [submit | Ls],
  find_label2(Ls, Name, Label).

find_label2([L | _ ], Name, L) :- L = label(Name, _).
find_label2([_ | Ls], Name, L) :- find_label2(Ls, Name, L).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%
%% remove_label/3:
%%
%%   Removes all occurances of label(Name, Status).
%%
:- public remove_label/3.
%%
remove_label([], _, []) :- !.
remove_label(List, Label, Out) :-
  List = [_ | _],
  !,
  subtract1(List, Label, Out).
remove_label(S, Label, Out) :-
  S =.. [submit | Ls],
  subtract1(Ls, Label, Tmp),
  Out =.. [submit | Tmp].

subtract1([], _, []) :- !.
subtract1([E | L], E, R) :- !, subtract1(L, E, R).
subtract1([H | L], E, [H | R]) :- subtract1(L, E, R).


%% commit_author/1:
%%
:- public commit_author/1.
%%
commit_author(Author) :-
  commit_author(Author, _, _).


%% commit_committer/1:
%%
:- public commit_committer/1.
%%
commit_committer(Committer) :-
  commit_committer(Committer, _, _).


%% commit_delta/1:
%%
:- public commit_delta/1.
%%
commit_delta(Regex) :-
  once(commit_delta(Regex, _, _, _)).


%% commit_delta/3:
%%
:- public commit_delta/3.
%%
commit_delta(Regex, Type, Path) :-
  commit_delta(Regex, TmpType, NewPath, OldPath),
  split_commit_delta(TmpType, NewPath, OldPath, Type, Path).

split_commit_delta(rename, NewPath, OldPath, delete, OldPath).
split_commit_delta(rename, NewPath, OldPath, add, NewPath) :- !.
split_commit_delta(copy, NewPath, OldPath, add, NewPath) :- !.
split_commit_delta(Type, Path, _, Type, Path).


%% commit_msg_regex/1:
%%
:- public commit_msg_regex/1.
%%
commit_msg_regex(Regex) :-
  commit_msg(Msg),
  regex(Regex, Msg).
