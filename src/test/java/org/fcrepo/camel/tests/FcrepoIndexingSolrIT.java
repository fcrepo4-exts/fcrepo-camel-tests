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
import static java.util.UUID.randomUUID;
import static org.apache.http.impl.client.HttpClients.createDefault;
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
import java.net.URI;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.marmotta.ldcache.api.LDCachingBackend;
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
 * @since Sept 20, 2016
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class FcrepoIndexingSolrIT extends AbstractOSGiIT {

    private static Logger LOGGER = getLogger(FcrepoIndexingSolrIT.class);

    private final CloseableHttpClient httpClient = createDefault();

    @Configuration
    public Option[] config() {
        final ConfigurationManager cm = new ConfigurationManager();
        final String fcrepoPort = cm.getProperty("fcrepo.dynamic.test.port");
        final String jmsPort = cm.getProperty("fcrepo.dynamic.jms.port");
        final String ldpathPort = cm.getProperty("fcrepo.dynamic.ldpath-solr.port");
        final String rmiRegistryPort = cm.getProperty("karaf.rmiRegistry.port");
        final String rmiServerPort = cm.getProperty("karaf.rmiServer.port");
        final String fcrepoBaseUrl = "http://localhost:" + fcrepoPort + "/fcrepo/rest";
        final String sshPort = cm.getProperty("karaf.ssh.port");
        final String brokerUrl = "tcp://localhost:" + jmsPort;
        final String solrBaseUrl = "http://localhost:" + fcrepoPort + "/solr/testCore";
        final String ldpathBaseUrl = "http://localhost:" + ldpathPort + "/ldpath";
        final String ldcacheDir = cm.getProperty("project.build.directory") + "/" + randomUUID().toString();

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
            features(maven().groupId("org.apache.camel.karaf").artifactId("apache-camel")
                        .type("xml").classifier("features").versionAsInProject(), "camel-blueprint", "camel-jackson"),
            features(maven().groupId("org.fcrepo.camel").artifactId("toolbox-features")
                        .type("xml").classifier("features").versionAsInProject(),
                            "fcrepo-service-activemq", "fcrepo-indexing-solr"),

            systemProperty("fcrepo.port").value(fcrepoPort),
            systemProperty("ldpath.port").value(ldpathPort),

            editConfigurationFilePut("etc/org.apache.karaf.management.cfg", "rmiRegistryPort", rmiRegistryPort),
            editConfigurationFilePut("etc/org.apache.karaf.management.cfg", "rmiServerPort", rmiServerPort),
            editConfigurationFilePut("etc/org.apache.karaf.shell.cfg", "sshPort", sshPort),
            editConfigurationFilePut("etc/org.fcrepo.camel.service.activemq.cfg", "jms.brokerUrl", brokerUrl),
            editConfigurationFilePut("etc/org.fcrepo.camel.service.cfg", "fcrepo.baseUrl", fcrepoBaseUrl),
            editConfigurationFilePut("etc/org.fcrepo.camel.indexing.solr.cfg", "solr.baseUrl", solrBaseUrl),
            editConfigurationFilePut("etc/org.fcrepo.camel.indexing.solr.cfg", "solr.commitWithin", "1000"),
            editConfigurationFilePut("etc/org.fcrepo.camel.indexing.solr.cfg", "ldpath.service.baseUrl",
                    ldpathBaseUrl),
            editConfigurationFilePut("etc/org.fcrepo.camel.ldpath.cfg", "fcrepo.authPort", fcrepoPort),
            editConfigurationFilePut("etc/org.fcrepo.camel.ldpath.cfg", "fcrepo.baseUrl", fcrepoBaseUrl),
            editConfigurationFilePut("etc/org.fcrepo.camel.ldpath.cfg", "rest.port", ldpathPort),
            editConfigurationFilePut("etc/org.fcrepo.camel.ldcache.file.cfg", "ldcache.directory",
                    ldcacheDir)
       };
    }

    @Test
    public void testInstallation() throws Exception {
        assertTrue(featuresService.isInstalled(featuresService.getFeature("camel-core")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-camel")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-indexing-solr")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-service-activemq")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-service-camel")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-service-ldcache-file")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-ldpath")));
    }

    @Test
    public void testSolrIndexingService() throws Exception {
        // make sure that the camel context has started up.
        assertNotNull(getOsgiService(CamelContext.class, "(camel.context.name=FcrepoLDPathContext)", 10000));
        assertNotNull(getOsgiService(CamelContext.class, "(camel.context.name=FcrepoSolrIndexer)", 10000));
        assertNotNull(getOsgiService(LDCachingBackend.class, "(osgi.jndi.service.name=fcrepo/LDCacheBackend)", 10000));

        // make sure the ldpath service is available.
        try {
            while (get("http://localhost:" + System.getProperty("ldpath.port") + "/ldpath/").isEmpty()) {
                sleep(1000);
            }
        } catch (final InterruptedException ex) {
            LOGGER.warn("Interrupted, waiting for ldpath service to start up: {}", ex.getMessage());
        }

        final String baseUrl = "http://localhost:" + System.getProperty("fcrepo.port") + "/fcrepo/rest";
        final String solrBaseUrl = "http://localhost:" + System.getProperty("fcrepo.port") + "/solr/testCore/select";

        // Create four resources
        final String url1 = post(baseUrl);
        final String url2 = post(baseUrl);
        final String url3 = post(url1);
        final String url4 = post(url2);

        try {
            while (solrCount(solrBaseUrl, url1) == 0 ||
                    solrCount(solrBaseUrl, url2) == 0 ||
                    solrCount(solrBaseUrl, url3) == 0 ||
                    solrCount(solrBaseUrl, url4) == 0) {
                sleep(1000);
            }
        } catch (final InterruptedException ex) {
            LOGGER.warn("Interrupted, waiting for solr synchronization: {}", ex.getMessage());
        }

        assertTrue(solrCount(solrBaseUrl, url1) > 0);
        assertTrue(solrCount(solrBaseUrl, url2) > 0);
        assertTrue(solrCount(solrBaseUrl, url3) > 0);
        assertTrue(solrCount(solrBaseUrl, url4) > 0);

        // Delete two resources (and a third via containment)
        delete(url1);
        delete(url4);

        try {
            while (solrCount(solrBaseUrl, url1) > 0 ||
                    solrCount(solrBaseUrl, url3) > 0 ||
                    solrCount(solrBaseUrl, url4) > 0) {
                sleep(1000);
            }
        } catch (final InterruptedException ex) {
            LOGGER.warn("Interrupted, waiting for solr synchronization: {}", ex.getMessage());
        }

        assertTrue(solrCount(solrBaseUrl, url1) == 0);
        assertTrue(solrCount(solrBaseUrl, url2) > 0);
        assertTrue(solrCount(solrBaseUrl, url3) == 0);
        assertTrue(solrCount(solrBaseUrl, url4) == 0);
    }

    public Integer solrCount(final String endpoint, final String id) throws Exception {
        final ObjectMapper mapper = new ObjectMapper();

        final URIBuilder builder = new URIBuilder(endpoint);
        final URI uri = builder.addParameter("wt", "json")
                            .addParameter("q", "id:\"" + id + "\"").build();

        final String result = get(uri);
        return mapper.readTree(result).get("response").get("numFound").asInt();
    }
}
