/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.converters;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryOperation;
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@CloudFoundryOperation(AtomicOperations.CLONE_SERVER_GROUP)
@Component
public class CloneCloudFoundryServerGroupAtomicOperationConverter
    extends DeployCloudFoundryServerGroupAtomicOperationConverter {
  public CloneCloudFoundryServerGroupAtomicOperationConverter(
      @Qualifier("cloudFoundryOperationPoller") OperationPoller operationPoller,
      ArtifactCredentialsRepository credentialsRepository,
      CredentialsRepository<DockerRegistryNamedAccountCredentials>
          dockerRegistryCredentialsRepository) {
    super(operationPoller, credentialsRepository, dockerRegistryCredentialsRepository);
  }
}
