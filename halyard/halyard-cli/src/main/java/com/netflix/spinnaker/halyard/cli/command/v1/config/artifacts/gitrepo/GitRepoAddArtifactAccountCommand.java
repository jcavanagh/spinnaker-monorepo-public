/*
 * Copyright 2019 Armory, Inc.
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
 *
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.gitrepo;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.account.AbstractAddArtifactAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.gitrepo.GitRepoArtifactAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactAccount;

@Parameters(separators = "=")
public class GitRepoAddArtifactAccountCommand extends AbstractAddArtifactAccountCommand {
  @Parameter(names = "--username", description = "Git username")
  private String username;

  @Parameter(names = "--password", password = true, description = "Git password")
  private String password;

  @Parameter(
      names = "--username-password-file",
      converter = LocalFileConverter.class,
      description = "File containing \"username:password\" to use for Git authentication")
  private String usernamePasswordFile;

  @Parameter(names = "--token", password = true, description = "Git token")
  private String token;

  @Parameter(
      names = "--token-file",
      converter = LocalFileConverter.class,
      description = "File containing a Git authentication token")
  private String tokenFile;

  @Parameter(
      names = "--ssh-private-key-file-path",
      converter = LocalFileConverter.class,
      description = "Path to the ssh private key in PEM format")
  private String sshPrivateKeyFilePath;

  @Parameter(
      names = "--ssh-private-key-passphrase",
      password = true,
      description = "Passphrase for encrypted private key")
  private String sshPrivateKeyPassphrase;

  @Parameter(
      names = "--ssh-known-hosts-file-path",
      converter = LocalFileConverter.class,
      description = "File containing the known and trusted SSH hosts.")
  private String sshKnownHostsFilePath;

  @Parameter(
      names = "--ssh-trust-unknown-hosts",
      description = "Setting this to true allows Spinnaker to authenticate with unknown hosts")
  private Boolean sshTrustUnknownHosts;

  @Override
  protected ArtifactAccount buildArtifactAccount(String accountName) {
    GitRepoArtifactAccount artifactAccount = new GitRepoArtifactAccount().setName(accountName);
    artifactAccount
        .setUsername(username)
        .setPassword(password)
        .setUsernamePasswordFile(usernamePasswordFile)
        .setToken(token)
        .setTokenFile(tokenFile)
        .setSshPrivateKeyFilePath(sshPrivateKeyFilePath)
        .setSshPrivateKeyPassphrase(sshPrivateKeyPassphrase)
        .setSshKnownHostsFilePath(sshKnownHostsFilePath)
        .setSshTrustUnknownHosts(sshTrustUnknownHosts);
    return artifactAccount;
  }

  @Override
  protected ArtifactAccount emptyArtifactAccount() {
    return new GitRepoArtifactAccount();
  }

  @Override
  protected String getArtifactProviderName() {
    return "gitrepo";
  }
}
