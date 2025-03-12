/*
 * Copyright 2024 OpsMx, Inc.
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

package com.netflix.spinnaker.rosco.providers.azure;

import com.netflix.spinnaker.rosco.Main;
import com.netflix.spinnaker.rosco.providers.azure.config.RoscoAzureConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {Main.class})
@TestPropertySource(
    properties = {
      "spring.application.name=rosco",
      "azure.enabled=true",
      "rosco.config-dir=/some/path"
    })
public class AzureStartupTest {

  @Autowired AzureBakeHandler azureBakeHandler;

  @Autowired RoscoAzureConfiguration.AzureBakeryDefaults azureBakeryDefaults;

  @Autowired RoscoAzureConfiguration.AzureConfigurationProperties azureConfigurationProperties;

  @Test
  public void startupTest() {}
}
