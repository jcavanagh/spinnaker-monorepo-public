/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.igor.gcb;

import static org.mockito.Mockito.*;

import com.google.auth.oauth2.GoogleCredentials;
import com.netflix.spinnaker.igor.config.GoogleCloudBuildProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GoogleCloudBuildAccountFactoryTest {
  private final GoogleCredentialsService googleCredentialsService =
      mock(GoogleCredentialsService.class);
  private final GoogleCloudBuildClient.Factory googleCloudBuildClientFactory =
      mock(GoogleCloudBuildClient.Factory.class);
  private final GoogleCloudBuildCache.Factory googleCloudBuildCacheFactory =
      mock(GoogleCloudBuildCache.Factory.class);
  private final GoogleCloudBuildParser googleCloudBuildParser = new GoogleCloudBuildParser();

  private final GoogleCredentials googleCredentials = mock(GoogleCredentials.class);
  private final GoogleCloudBuildClient googleCloudBuildClient = mock(GoogleCloudBuildClient.class);

  private final GoogleCloudBuildAccountFactory googleCloudBuildAccountFactory =
      new GoogleCloudBuildAccountFactory(
          googleCredentialsService,
          googleCloudBuildClientFactory,
          googleCloudBuildCacheFactory,
          googleCloudBuildParser);

  @Test
  public void applicationDefaultCredentials() {
    GoogleCloudBuildProperties.Account accountConfig =
        GoogleCloudBuildProperties.Account.builder()
            .name("test-account")
            .project("test-project")
            .jsonKey("")
            .build();

    when(googleCredentialsService.getApplicationDefault()).thenReturn(googleCredentials);
    when(googleCloudBuildClientFactory.create(eq(googleCredentials), any(String.class)))
        .thenReturn(googleCloudBuildClient);

    GoogleCloudBuildAccount account = googleCloudBuildAccountFactory.build(accountConfig);

    verify(googleCredentialsService).getApplicationDefault();
    verify(googleCredentialsService, never()).getFromKey(any());
    verify(googleCloudBuildClientFactory).create(eq(googleCredentials), any(String.class));
  }

  @Test
  public void jsonCredentials() {
    GoogleCloudBuildProperties.Account accountConfig =
        GoogleCloudBuildProperties.Account.builder()
            .name("test-account")
            .project("test-project")
            .jsonKey("/path/to/file")
            .build();

    when(googleCredentialsService.getFromKey("/path/to/file")).thenReturn(googleCredentials);
    when(googleCloudBuildClientFactory.create(eq(googleCredentials), any(String.class)))
        .thenReturn(googleCloudBuildClient);

    GoogleCloudBuildAccount account = googleCloudBuildAccountFactory.build(accountConfig);

    verify(googleCredentialsService, never()).getApplicationDefault();
    verify(googleCredentialsService).getFromKey("/path/to/file");
    verify(googleCloudBuildClientFactory).create(eq(googleCredentials), any(String.class));
  }
}
