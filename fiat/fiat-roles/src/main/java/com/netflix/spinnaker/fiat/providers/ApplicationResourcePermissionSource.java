/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.fiat.providers;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

public final class ApplicationResourcePermissionSource
    implements ResourcePermissionSource<Application> {

  @Override
  @Nonnull
  public Permissions getPermissions(@Nonnull Application resource) {
    Permissions storedPermissions = resource.getPermissions();
    if (storedPermissions == null || !storedPermissions.isRestricted()) {
      return Permissions.EMPTY;
    }

    Map<Authorization, Set<String>> authorizations =
        Arrays.stream(Authorization.values()).collect(toMap(identity(), storedPermissions::get));

    // CREATE permissions are not allowed on the resource level.
    authorizations.remove(Authorization.CREATE);

    return Permissions.Builder.factory(authorizations).build();
  }
}
