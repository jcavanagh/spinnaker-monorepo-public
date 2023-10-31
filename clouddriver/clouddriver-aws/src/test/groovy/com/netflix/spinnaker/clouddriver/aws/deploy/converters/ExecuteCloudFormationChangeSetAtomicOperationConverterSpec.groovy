/*
 * Copyright 2019 Adevinta.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ExecuteCloudFormationChangeSetDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.ExecuteCloudFormationChangeSetAtomicOperation
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class ExecuteCloudFormationChangeSetAtomicOperationConverterSpec extends Specification {

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  ExecuteCloudFormationChangeSetAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new ExecuteCloudFormationChangeSetAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(NetflixAmazonCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "ExecuteCloudFormationChangeSetConverter return ExecuteCloudFormationChangeSetDescription"() {
    setup:
    def input = [stackName      : "stack",
                 changeSetName  : "changeset",
                 region         : "eu-west-1",
                 credentials    : "credentials"]

    when:
    ExecuteCloudFormationChangeSetDescription description = converter.convertDescription(input)

    then:
    description instanceof ExecuteCloudFormationChangeSetDescription
    description.stackName == "stack"
    description.changeSetName == "changeset"
    description.region == "eu-west-1"

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof ExecuteCloudFormationChangeSetAtomicOperation
  }
}
