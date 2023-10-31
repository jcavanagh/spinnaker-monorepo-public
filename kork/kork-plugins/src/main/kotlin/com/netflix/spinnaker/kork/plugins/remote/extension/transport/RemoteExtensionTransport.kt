/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.kork.plugins.remote.extension.transport

import com.netflix.spinnaker.kork.annotations.Beta

/**
 * The transport on which to address the remote extension.
 */
@Beta
interface RemoteExtensionTransport {

  /**
   * Invoke the remote extension.  The expectation is that the remote extension is an async process
   * with a callback, so we do not wait for a particular response here - but the underlying
   * [RemoteExtensionTransport] implementation may throw an exception if something unexpected,
   * like a connection error, occurs.
   */
  fun invoke(remoteExtensionPayload: RemoteExtensionPayload)

  /**
   * Write to the remote extension.  This is a synchronous process which requires a response of
   * [RemoteExtensionResponse] type.  This may be used as a substitute for [invoke] when
   * implementations require a synchronous process.
   */
  fun write(remoteExtensionPayload: RemoteExtensionPayload): RemoteExtensionResponse {
    return NoOpRemoteExtensionResponse()
  }

  /**
   * Read from the remote extension.  This is a synchronous process which requires a response of
   * [RemoteExtensionResponse].
   */
  fun read(remoteExtensionQuery: RemoteExtensionQuery): RemoteExtensionResponse {
    return NoOpRemoteExtensionResponse()
  }
}
