/*
 * Copyright 2015 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.security

import com.netflix.spinnaker.kork.common.Header
import org.slf4j.MDC
import spock.lang.Specification

class AuthenticatedRequestSpec extends Specification {
  void "should extract user details by priority (Principal > MDC)"() {
    when:
    MDC.clear()
    MDC.put(Header.USER.header, "spinnaker-user")

    then:
    AuthenticatedRequest.getSpinnakerUser().get() == "spinnaker-user"
    AuthenticatedRequest.getSpinnakerUser(new User([email: "spinnaker-other"])).get() == "spinnaker-other"
    AuthenticatedRequest.getSpinnakerUser(new org.springframework.security.core.userdetails.User("spinnaker-other", "", [])).get() == "spinnaker-other"
  }

  void "should extract allowed account details by priority (Principal > MDC)"() {
    when:
    MDC.clear()
    MDC.put(Header.ACCOUNTS.header, "account1,account2")

    then:
    AuthenticatedRequest.getSpinnakerAccounts().get() == "account1,account2"
    AuthenticatedRequest.getSpinnakerAccounts(new User(allowedAccounts: ["account3", "account4"])).get() == "account3,account4"
    AuthenticatedRequest.getSpinnakerAccounts(
      new org.springframework.security.core.userdetails.User(
        "username",
        "",
        AllowedAccountsAuthorities.buildAllowedAccounts(["account3", "account4"]))).get() == "account3,account4"
  }

  void "should have no user/allowed account details if no MDC or Principal available"() {
    when:
    MDC.clear()

    then:
    !AuthenticatedRequest.getSpinnakerUser().present
    !AuthenticatedRequest.getSpinnakerAccounts().present
  }

  void "should propagate user/allowed account details"() {
    when:
    MDC.put(Header.USER.header, "spinnaker-user")
    MDC.put(Header.ACCOUNTS.header, "account1,account2")

    def closure = AuthenticatedRequest.propagate({
      assert AuthenticatedRequest.getSpinnakerUser().get() == "spinnaker-user"
      assert AuthenticatedRequest.getSpinnakerAccounts().get() == "account1,account2"
      return true
    })

    MDC.put(Header.USER.header, "spinnaker-another-user")
    MDC.put(Header.ACCOUNTS.header, "account1,account3")
    closure.call()

    then:
    // ensure MDC context is restored
    MDC.get(Header.USER.header) == "spinnaker-another-user"
    MDC.get(Header.ACCOUNTS.header) == "account1,account3"

    when:
    MDC.clear()

    then:
    closure.call()
    MDC.clear()
  }

  void "should propagate headers"() {
    when:
    MDC.clear()
    MDC.put(Header.USER.header, "spinnaker-another-user")
    MDC.put(Header.makeCustomHeader("cloudprovider"), "aws")

    then:
    Map allheaders = AuthenticatedRequest.getAuthenticationHeaders()
    allheaders == [
            'X-SPINNAKER-USER': Optional.of("spinnaker-another-user"),
            'X-SPINNAKER-ACCOUNTS': Optional.empty(),
            'X-SPINNAKER-CLOUDPROVIDER': Optional.of("aws")]
  }

  void "should not fail when no headers are set"() {
    when:
    MDC.clear()
    MDC.put("X-SPINNAKER-MY-ATTRIBUTE", null)

    then:
    Map allheaders = AuthenticatedRequest.getAuthenticationHeaders()
    allheaders == [
      'X-SPINNAKER-USER'        : Optional.empty(),
      'X-SPINNAKER-ACCOUNTS'    : Optional.empty(),
      'X-SPINNAKER-MY-ATTRIBUTE': Optional.empty()]
  }

  void "should propagate user and headers in decorator"() {
    when:
    AuthenticatedRequest.clear()
    AuthenticatedRequest.user = 'fry'
    AuthenticatedRequest.application = 'express'
    AuthenticatedRequest.requestId = '2000'
    def closure = AuthenticatedRequestDecorator.wrap {
      assert AuthenticatedRequest.spinnakerUser.get() == 'fry'
      assert AuthenticatedRequest.spinnakerApplication.get() == 'express'
      assert AuthenticatedRequest.spinnakerRequestId.get() == '2000'
    }
    AuthenticatedRequest.user = 'amy'
    AuthenticatedRequest.application = 'mars'
    AuthenticatedRequest.requestId = '3000'
    closure.run()
    then:
    // ensure previous context restored
    AuthenticatedRequest.spinnakerUser.get() == 'amy'
    AuthenticatedRequest.spinnakerApplication.get() == 'mars'
    AuthenticatedRequest.spinnakerRequestId.get() == '3000'
    when:
    AuthenticatedRequest.clear()
    then:
    closure.run()
  }
}
