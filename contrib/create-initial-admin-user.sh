#!/bin/bash

# Todo
# 1) squelching errors when refs don't exist would be good
# 2) support HTTP password
# 3) real options
# 4) could adapt this for other accounts and groups, but less value since you can
#    use APIs once you have an admin user
# 5) this might assume a lot still

get_note_key() { # external_id > note_key
    echo -n "$1" | shasum - | cut -f1 -d" "
}

SCRATCH_DIR=$1
ALL_USERS_PATH=$2
SSH_KEY_PATH=$3

if [ "$USER" != $(stat -c '%U' "$GERRIT_SITE_DIR") ] ; then
    echo "Must run as gerrit server user"
    exit 1
fi

if ! [ -d "$ALL_USERS_PATH" ] ; then
    echo "Must provide the path to the All-Users.git server copy"
    exit 1
fi

mkdir -p "$SCRATCH_DIR"
cd "$SCRATCH_DIR"
git init
git remote add origin "$ALL_USERS_PATH"

# Add 'gerrit:' and 'username:' external ids
git fetch origin refs/meta/external-ids && git checkout FETCH_HEAD || git checkout --orphan external-ids
ACCOUNT_SHA=$(get_note_key 'gerrit:gerrit_admin')
git config --file "$ACCOUNT_SHA" --add externalId."gerrit:gerrit_admin".accountId 1000000
git config --file "$ACCOUNT_SHA" --add externalId."gerrit:gerrit_admin".email 'gerrit_admin@example.com'

USERNAME_SHA=$(get_note_key 'username:gerrit_admin')
git config --file "$USERNAME_SHA" --add externalId."username:gerrit_admin".accountId 1000000
git config --file "$USERNAME_SHA" --add externalId."username:gerrit_admin".email 'gerrit_admin@example.com'

git add "$USERNAME_SHA" "$ACCOUNT_SHA"
git commit -m 'Add admin account external id'
git push origin HEAD:refs/meta/external-ids

# Add name, preferred email, and SSH public key (if given)
git fetch origin refs/users/00/1000000 && git checkout FETCH_HEAD || git checkout --orphan user-ref
rm *
git config --file account.config account.fullName "Gerrit admin"
git config --file account.config account.preferredEmail 'gerrit_admin@example.com'
git add account.config
if [ -e "$SSH_KEY_PATH" ] ; then
    cp "$SSH_KEY_PATH" authorized_keys
    git add authorized_keys
fi
git commit -m 'Add admin account keys and config'
git push origin HEAD:refs/users/00/1000000

# Add to admin group
git fetch origin refs/meta/group-names
git checkout FETCH_HEAD
ADMIN_SHA=$(for file in $(ls) ; do git config --file $file --get group.name Administrators > /dev/null && echo $file ; done)
ADMIN_UUID=$(git config --file "$ADMIN_SHA" group.uuid)
ADMIN_REF="refs/groups/$(echo "$ADMIN_UUID" | head -c2)/$ADMIN_UUID"
git fetch origin "$ADMIN_REF"
git checkout FETCH_HEAD
echo 1000000 >> members
git add members
git commit -m 'Add admin account to Admins group'
git push origin HEAD:"$ADMIN_REF"
