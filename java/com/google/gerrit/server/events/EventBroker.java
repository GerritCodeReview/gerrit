    }
    fireEventForUnrestrictedListeners(event);
  }

  protected void fireEvent(Project.NameKey project, ProjectEvent event) {
    setInstanceIdWhenEmpty(event);
    for (PluginSetEntryContext<UserScopedEventListener> c : listeners) {

      CurrentUser user = c.call(UserScopedEventListener::getUser);
      if (isVisibleTo(project, user)) {
        c.run(l -> l.onEvent(event));
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected void fireEvent(BranchNameKey branchName, RefEvent event)
      throws PermissionBackendException {
    setInstanceIdWhenEmpty(event);
    for (PluginSetEntryContext<UserScopedEventListener> c : listeners) {
      CurrentUser user = c.call(UserScopedEventListener::getUser);
      if (isVisibleTo(branchName, user)) {
        c.run(l -> l.onEvent(event));
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected void fireEvent(Event event) throws PermissionBackendException {
    setInstanceIdWhenEmpty(event);
    for (PluginSetEntryContext<UserScopedEventListener> c : listeners) {
      CurrentUser user = c.call(UserScopedEventListener::getUser);
      if (isVisibleTo(event, user)) {
        c.run(l -> l.onEvent(event));
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected void setInstanceIdWhenEmpty(Event event) {
    if (Strings.isNullOrEmpty(event.instanceId)) {
      event.instanceId = gerritInstanceId;
    }
  }

  protected boolean isVisibleTo(Project.NameKey project, CurrentUser user) {
    try {
      if (!projectCache.get(project).isPresent()) {
        return false;
      }

      return permissionBackend.user(user).project(project).test(ProjectPermission.ACCESS);
    } catch (PermissionBackendException e) {
      return false;
    }
  }

  protected boolean isVisibleTo(Change change, CurrentUser user) throws PermissionBackendException {
    if (change == null) {
      return false;
    }
    if (!projectCache.get(change.getProject()).isPresent()) {
      return false;
    }
    return permissionBackend
        .user(user)
        .change(notesFactory.createChecked(change))
        .test(ChangePermission.READ);
  }

  protected boolean isVisibleTo(BranchNameKey branchName, CurrentUser user)
      throws PermissionBackendException {
    if (!projectCache.get(branchName.project()).isPresent()) {
      return false;
    }
    return permissionBackend.user(user).ref(branchName).test(RefPermission.READ);
  }

  protected boolean isVisibleTo(Event event, CurrentUser user) throws PermissionBackendException {
    if (event instanceof RefEvent) {
      RefEvent refEvent = (RefEvent) event;
      String ref = refEvent.getRefName();
      if (PatchSet.isChangeRef(ref)) {
        Change.Id cid = PatchSet.Id.fromRef(ref).changeId();
        try {
          Change change = notesFactory.createChecked(refEvent.getProjectNameKey(), cid).getChange();
          return isVisibleTo(change, user);
        } catch (NoSuchChangeException e) {
          logger.atFine().log(
              "Change %s cannot be found, falling back on ref visibility check", cid.get());
        }
      }
      return isVisibleTo(refEvent.getBranchNameKey(), user);
    } else if (event instanceof ProjectEvent) {
      return isVisibleTo(((ProjectEvent) event).getProjectNameKey(), user);
    }
    return true;
  }
}
