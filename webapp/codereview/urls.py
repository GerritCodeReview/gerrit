# Copyright 2008 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""URL mappings for Gerrit."""

# NOTE: Must import *, since Django looks for things here, e.g. handler500.
from django.conf.urls.defaults import *

urlpatterns = patterns(
    'codereview',
    (r'^dev_init$', 'dev_init.dev_init'),
    (r'^proto/_token$', 'proto_server.token'),
    (r'^proto/([^/]*)/([^/]*)$', 'proto_server.serve'),
    (r'^$', 'views.index'),
    (r'^hello$', 'views.hello'),
    (r'^all$', 'views.all'),
    (r'^mine$', 'views.mine'),
    (r'^all_unclaimed$', 'views.all_unclaimed'),
    (r'^unclaimed$', 'views.unclaimed'),
    (r'^starred$', 'views.starred'),
    (r'^r/([0-9a-f]{4,40})$', 'views.revision_redirect'),
    (r'^(\d+)$', 'views.show'),
    (r'^(\d+)/show$', 'views.show'),
    (r'^(\d+)/delete$', 'views.delete'),
    (r'^(\d+)/publish$', 'views.publish'),
    (r'^(\d+)/merge/(\d+)$', 'views.merge'),
    (r'^(\d+)/ajax_patchset/(\d+)$', 'views.ajax_patchset'),
    (r'^download/change(\d+)_(\d+)_(\d+|z[0-9a-f]{40})\.diff', 'views.download_patch'),
    (r'^download/bundle(\d+)_(\d+)$', 'views.download_bundle'),
    (r'^(\d+)/patch/(\d+)/(\d+|z[0-9a-f]{40})$', 'views.patch'),
    (r'^(\d+)/diff/(\d+)/(\d+|z[0-9a-f]{40})$', 'views.diff'),
    (r'^(\d+)/diff2/(\d+):(\d+)/(\d+|z[0-9a-f]{40})$', 'views.diff2'),
    (r'^(\d+)/diff_skipped_lines/(\d+)/(\d+|z[0-9a-f]{40})/(\d+)/(\d+)/([tb])$',
     'views.diff_skipped_lines'),
    (r'^(\d+)/diff2_skipped_lines/(\d+):(\d+)/(\d+|z[0-9a-f]{40})/(\d+)/(\d+)/([tb])$',
     'views.diff2_skipped_lines'),
    (r'^star$', 'views.star'),
    (r'^unstar$', 'views.unstar'),
    (r'^user/(.+)$', 'views.show_user'),
    (r'^ajax_user_mine/(.+)/(\d+)$', 'views.ajax_user_mine'),
    (r'^ajax_user_review/(.+)/(\d+)$', 'views.ajax_user_review'),
    (r'^ajax_user_closed/(.+)/(\d+)$', 'views.ajax_user_closed'),
    (r'^inline_draft$', 'views.inline_draft'),
    (r'^settings$', 'settings.settings'),
    (r'^settings/welcome$', 'settings.settings_welcome'),
    (r'^user_popup/(.+)$', 'views.user_popup'),
    (r'^admin$', 'views.admin'),
    (r'^admin/settings$', 'views.admin_settings'),
    (r'^admin/settings/analytics$', 'views.admin_settings_analytics'),
    (r'^admin/settings/from_email$', 'views.admin_settings_from_email'),
    (r'^admin/settings/from_email_test$', 'views.admin_settings_from_email_test'),
    (r'^admin/settings/merge_log_email$', 'views.admin_settings_merge_log_email'),
    (r'^admin/settings/canonical_url$', 'views.admin_settings_canonical_url'),
    (r'^admin/settings/source_browser_url$',
                'views.admin_settings_source_browser_url'),
    (r'^admin/users$', 'people.admin_users'),
    (r'^admin/people_info$', 'people.admin_people_info'),
    (r'^admin/user/(.+)$', 'people.admin_user'),
    (r'^admin/groups$', 'people.admin_groups'),
    (r'^admin/group_new$', 'people.admin_group_new'),
    (r'^admin/group/(.+)$', 'people.admin_group'),
    (r'^admin/group_delete/(.+)$', 'people.admin_group_delete'),
    (r'^admin/projects$', 'project.project_list'),
    (r'^admin/project_new$', 'project.project_new'),
    (r'^admin/project/(.+)$', 'project.project_edit'),
    (r'^admin/project_delete/(.+)$', 'project.project_delete'),
    (r'^admin/datastore_delete$', 'views.admin_datastore_delete'),
    (r'^admin/datastore_upgrade$', 'views.admin_datastore_upgrade'),
    )
