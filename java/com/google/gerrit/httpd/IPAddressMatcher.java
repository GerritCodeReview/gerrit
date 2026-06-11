// Copyright (C) 2026 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.httpd;

import org.apache.commons.net.util.SubnetUtils;

class IPAddressMatcher {

	static boolean matchesCidr(String ipAddress, String cidr) {
		if (cidr.contains(":")) {
			// Apache Commons Net 3.6 lacks IPv6 support.
			throw new IllegalArgumentException("IPv6 address are not supported");
		} else {
			// Use Commons Net 3.6 for IPv4
				var subnet = new SubnetUtils(cidr);
				return subnet.getInfo().isInRange(ipAddress);
		}
	}
}
