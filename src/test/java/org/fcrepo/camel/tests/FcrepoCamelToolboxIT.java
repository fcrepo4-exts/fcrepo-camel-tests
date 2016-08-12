/*
 * Copyright 2015 DuraSpace, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.camel.karaf;

import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureConsole;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.ConfigurationManager;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.slf4j.Logger;

/**
 * @author Aaron Coburn
 * @since May 2, 2016
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class FcrepoCamelToolboxIT extends AbstractOSGiIT {

    private static Logger LOGGER = getLogger(FcrepoCamelToolboxIT.class);

    @Configuration
    public Option[] config() {
        final ConfigurationManager cm = new ConfigurationManager();
        final String fcrepoPort = cm.getProperty("fcrepo.dynamic.test.port");
        final String jmsPort = cm.getProperty("fcrepo.dynamic.jms.port");
        final String reindexingPort = cm.getProperty("fcrepo.dynamic.reindexing.port");
        final String rmiRegistryPort = cm.getProperty("karaf.rmiRegistry.port");
        final String rmiServerPort = cm.getProperty("karaf.rmiServer.port");
        final String fcrepoBaseUrl = "http://localhost:" + fcrepoPort + "/fcrepo/rest";
        final String sshPort = cm.getProperty("karaf.ssh.port");
        final String emptyTopic = "broker:topic:test";
        final String brokerUrl = "tcp://localhost:" + jmsPort;
        final String triplestoreBaseUrl = "http://localhost:" + fcrepoPort + "/fuseki/test/update";

        return new Option[] {
            karafDistributionConfiguration()
                .frameworkUrl(maven().groupId("org.apache.karaf").artifactId("apache-karaf")
                        .versionAsInProject().type("zip"))
                .unpackDirectory(new File("target", "exam"))
                .useDeployFolder(false),
            logLevel(LogLevel.WARN),
            keepRuntimeFolder(),
            configureConsole().ignoreLocalConsole(),
            features(maven().groupId("org.apache.karaf.features").artifactId("standard")
                        .versionAsInProject().classifier("features").type("xml"), "scr"),
            features(maven().groupId("org.apache.camel.karaf").artifactId("apache-camel")
                        .type("xml").classifier("features").versionAsInProject(), "camel-blueprint"),
            features(maven().groupId("org.fcrepo.camel").artifactId("toolbox-features")
                        .type("xml").classifier("features").versionAsInProject(), "fcrepo-indexing-triplestore",
                    "fcrepo-indexing-solr", "fcrepo-reindexing", "fcrepo-serialization",
                    "fcrepo-fixity", "fcrepo-audit-triplestore", "fcrepo-service-activemq"),

            systemProperty("karaf.reindexing.port").value(reindexingPort),
            systemProperty("fcrepo.port").value(fcrepoPort),

            editConfigurationFilePut("etc/org.apache.karaf.management.cfg", "rmiRegistryPort", rmiRegistryPort),
            editConfigurationFilePut("etc/org.apache.karaf.management.cfg", "rmiServerPort", rmiServerPort),
            editConfigurationFilePut("etc/org.apache.karaf.shell.cfg", "sshPort", sshPort),
            editConfigurationFilePut("etc/org.fcrepo.camel.indexing.triplestore.cfg", "triplestore.baseUrl",
                    triplestoreBaseUrl),
            editConfigurationFilePut("etc/org.fcrepo.camel.indexing.solr.cfg", "input.stream", emptyTopic),
            editConfigurationFilePut("etc/org.fcrepo.camel.reindexing.cfg", "rest.port", reindexingPort),
            editConfigurationFilePut("etc/org.fcrepo.camel.serialization.cfg", "serialization.descriptions",
                    "data/tmp/descriptions"),
            editConfigurationFilePut("etc/org.fcrepo.camel.service.cfg", "fcrepo.brokerUrl", fcrepoBaseUrl),
            editConfigurationFilePut("etc/org.fcrepo.camel.service.activemq.cfg", "jms.brokerUrl", brokerUrl)
       };
    }

    @Test
    public void testInstallation() throws Exception {
        assertTrue(featuresService.isInstalled(featuresService.getFeature("camel-core")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-camel")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-indexing-triplestore")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-indexing-solr")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-reindexing")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-serialization")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-audit-triplestore")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-fixity")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-service-activemq")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-service-camel")));
    }
}
