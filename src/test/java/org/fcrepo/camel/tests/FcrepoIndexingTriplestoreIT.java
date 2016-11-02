/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
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

import static java.lang.Thread.sleep;
import static java.net.URLEncoder.encode;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.impl.client.HttpClients.createDefault;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
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
public class FcrepoIndexingTriplestoreIT extends AbstractOSGiIT {

    private static Logger LOGGER = getLogger(FcrepoIndexingTriplestoreIT.class);

    private final CloseableHttpClient httpClient = createDefault();

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
                        .type("xml").classifier("features").versionAsInProject(), "scr"),
            features(maven().groupId("org.apache.activemq").artifactId("activemq-karaf")
                        .type("xml").classifier("features").versionAsInProject()),
            features(maven().groupId("org.apache.camel.karaf").artifactId("apache-camel")
                        .type("xml").classifier("features").versionAsInProject(), "camel-blueprint", "camel-jackson"),
            features(maven().groupId("org.fcrepo.camel").artifactId("toolbox-features")
                        .type("xml").classifier("features").versionAsInProject(),
                            "fcrepo-service-activemq", "fcrepo-indexing-triplestore"),

            systemProperty("karaf.reindexing.port").value(reindexingPort),
            systemProperty("fcrepo.port").value(fcrepoPort),

            editConfigurationFilePut("etc/org.apache.karaf.management.cfg", "rmiRegistryPort", rmiRegistryPort),
            editConfigurationFilePut("etc/org.apache.karaf.management.cfg", "rmiServerPort", rmiServerPort),
            editConfigurationFilePut("etc/org.apache.karaf.shell.cfg", "sshPort", sshPort),
            editConfigurationFilePut("etc/org.fcrepo.camel.service.activemq.cfg", "jms.brokerUrl", brokerUrl),
            editConfigurationFilePut("etc/org.fcrepo.camel.service.cfg", "fcrepo.baseUrl", fcrepoBaseUrl),
            editConfigurationFilePut("etc/org.fcrepo.camel.indexing.triplestore.cfg", "triplestore.baseUrl",
                    triplestoreBaseUrl)
       };
    }

    @Test
    public void testInstallation() throws Exception {
        assertTrue(featuresService.isInstalled(featuresService.getFeature("camel-core")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-camel")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-indexing-triplestore")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-service-activemq")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-service-camel")));
    }

    @Test
    public void testTriplestoreIndexingService() throws Exception {

        // make sure that the camel context has started up.
        final CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.name=FcrepoTriplestoreIndexer)",
                10000);
        assertNotNull(ctx);

        final String baseUrl = "http://localhost:" + System.getProperty("fcrepo.port") + "/fcrepo/rest";
        final String fusekiBaseUrl = "http://localhost:" + System.getProperty("fcrepo.port") + "/fuseki/test/query";
        final String url1 = post(baseUrl);
        final String url2 = post(baseUrl);
        final String url3 = post(url1);
        final String url4 = post(url2);

        try {
            while (triplestoreCount(fusekiBaseUrl, url1) == 0 ||
                    triplestoreCount(fusekiBaseUrl, url2) == 0 ||
                    triplestoreCount(fusekiBaseUrl, url3) == 0 ||
                    triplestoreCount(fusekiBaseUrl, url4) == 0) {
                sleep(1 * 1000);
            }
        } catch (final InterruptedException ex) {
            LOGGER.warn("Interrupted, waiting for triplestore synchronization: {}", ex.getMessage());
        }

        assertTrue(triplestoreCount(fusekiBaseUrl, url1) > 0);
        assertTrue(triplestoreCount(fusekiBaseUrl, url2) > 0);
        assertTrue(triplestoreCount(fusekiBaseUrl, url3) > 0);
        assertTrue(triplestoreCount(fusekiBaseUrl, url4) > 0);
    }

    private Integer triplestoreCount(final String endpoint, final String subject) throws Exception {
        final String query = "SELECT (COUNT(*) AS ?n) WHERE { <" + subject + "> ?o ?p . }";
        final String url = endpoint + "?query=" + encode(query, "UTF-8") + "&output=json";
        final ObjectMapper mapper = new ObjectMapper();
        try (final CloseableHttpResponse response = httpClient.execute(new HttpGet(url))) {
            assertEquals(SC_OK, response.getStatusLine().getStatusCode());
            return Integer.valueOf(mapper.readTree(response.getEntity().getContent())
                    .get("results").get("bindings").get(0).get("n").get("value").asText(), 10);
        }
    }
}
