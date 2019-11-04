/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.bacon.pnc;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.GroupCommandDefinition;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.jboss.pnc.bacon.common.cli.AbstractCommand;
import org.jboss.pnc.bacon.common.cli.AbstractGetSpecificCommand;
import org.jboss.pnc.bacon.common.cli.AbstractListCommand;
import org.jboss.pnc.bacon.pnc.client.PncClientHelper;
import org.jboss.pnc.client.BuildClient;
import org.jboss.pnc.client.ClientException;
import org.jboss.pnc.client.RemoteCollection;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.pnc.enums.RebuildMode;
import org.jboss.pnc.rest.api.parameters.BuildParameters;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@GroupCommandDefinition(name = "build", description = "build", groupCommands = {
        BuildCli.Start.class,
        BuildCli.Cancel.class,
        BuildCli.List.class,
        BuildCli.ListBuildArtifacts.class,
        BuildCli.ListDependencies.class,
        BuildCli.Get.class,
        BuildCli.DownloadSources.class
})
public class BuildCli extends AbstractCommand {


    private static BuildClient clientCache;

    private static BuildClient getClient() {
        if (clientCache == null) {
            clientCache = new BuildClient(PncClientHelper.getPncConfiguration());
        }
        return clientCache;
    }

    @CommandDefinition(name = "start", description = "Start a new build")
    public class Start extends AbstractCommand {

        @Argument(required = true, description = "Build Config ID")
        private String buildConfigId;

        @Option(name = "rebuild-mode")
        private String rebuildMode;
        @Option(name = "keep-pod-on-failure", description = "Default: false", defaultValue = "false")
        private String keepPodOnFailure;
        @Option(name = "timestamp-alignment", description = "Default: false", defaultValue = "false")
        private String timestampAlignment;
        @Option(name = "temporary-build", description = "Temporary build, default: false", defaultValue = "false")
        private String temporaryBuild;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {

            BuildParameters buildParams = new BuildParameters();
            buildParams.setRebuildMode(RebuildMode.valueOf(rebuildMode));
            buildParams.setKeepPodOnFailure(Boolean.parseBoolean(keepPodOnFailure));
            buildParams.setTimestampAlignment(Boolean.parseBoolean(timestampAlignment));
            buildParams.setTemporaryBuild(Boolean.parseBoolean(temporaryBuild));

            return super.executeHelper(commandInvocation, () -> {
                System.out.println(BuildConfigCli.getClient().trigger(buildConfigId, buildParams));
            });
        }
    }

    @CommandDefinition(name = "cancel", description = "Cancel build")
    public class Cancel extends AbstractCommand {

        @Argument(required = true, description = "Build ID")
        private String buildId;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {

            return super.executeHelper(commandInvocation, () -> {
                getClient().cancel(buildId);
            });
        }
    }

    @CommandDefinition(name = "list", description = "List builds")
    public class List extends AbstractListCommand<Build> {

        @Override
        public RemoteCollection<Build> getAll(String sort, String query) throws RemoteResourceException {
            return getClient().getAll(null, null, Optional.ofNullable(sort), Optional.ofNullable(query));
        }
    }

    @CommandDefinition(name = "list-built-artifacts", description = "List built artifacts")
    public class ListBuildArtifacts extends AbstractListCommand<Artifact> {

        @Argument(required = true, description = "Build ID")
        private String buildId;

        @Override
        public RemoteCollection<Artifact> getAll(String sort, String query) throws RemoteResourceException {
            return getClient().getBuiltArtifacts(buildId, Optional.ofNullable(sort), Optional.ofNullable(query));
        }
    }

    @CommandDefinition(name = "list-dependencies", description = "List dependencies")
    public class ListDependencies extends AbstractListCommand<Artifact> {

        @Argument(required = true, description = "Build ID")
        private String buildId;

        @Override
        public RemoteCollection<Artifact> getAll(String sort, String query) throws RemoteResourceException {
            return getClient().getDependencyArtifacts(buildId, Optional.ofNullable(sort), Optional.ofNullable(query));
        }
    }

    @CommandDefinition(name = "get", description = "Get build")
    public class Get extends AbstractGetSpecificCommand<Build> {

        @Override
        public Build getSpecific(String id) throws ClientException {
            return getClient().getSpecific(id);
        }
    }

    @CommandDefinition(name = "download-sources", description = "Download SCM sources used for the build")
    public class DownloadSources extends AbstractCommand {

        @Argument(required = true, description = "Id of build")
        private String id;


        @Override
        public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {

            return super.executeHelper(commandInvocation, () -> {

                String filename = id + "-sources.tar.gz";

                Response response = getClient().getInternalScmArchiveLink(id);

                InputStream in = (InputStream) response.getEntity();

                Path path = Paths.get(filename);

                try {
                    Files.copy(in, path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
