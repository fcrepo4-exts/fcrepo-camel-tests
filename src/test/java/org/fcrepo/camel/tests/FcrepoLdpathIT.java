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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.UUID.randomUUID;
import static org.junit.Assert.assertFalse;
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
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
public class FcrepoLdpathIT extends AbstractOSGiIT {

    private static Logger LOGGER = getLogger(FcrepoLdpathIT.class);

    private final ObjectMapper MAPPER = new ObjectMapper();

    @Configuration
    public Option[] config() {
        final ConfigurationManager cm = new ConfigurationManager();
        final String fcrepoPort = cm.getProperty("fcrepo.dynamic.test.port");
        final String ldpathPort = cm.getProperty("fcrepo.dynamic.ldpath.port");
        final String rmiRegistryPort = cm.getProperty("karaf.rmiRegistry.port");
        final String rmiServerPort = cm.getProperty("karaf.rmiServer.port");
        final String fcrepoBaseUrl = "http://localhost:" + fcrepoPort + "/fcrepo/rest";
        final String sshPort = cm.getProperty("karaf.ssh.port");
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
                        .type("xml").classifier("features").versionAsInProject(), "camel-blueprint"),
            features(maven().groupId("org.fcrepo.camel").artifactId("toolbox-features")
                        .type("xml").classifier("features").versionAsInProject(), "fcrepo-ldpath",
                            "fcrepo-service-ldcache-file"),

            systemProperty("karaf.ldpath.port").value(ldpathPort),
            systemProperty("fcrepo.port").value(fcrepoPort),

            editConfigurationFilePut("etc/org.apache.karaf.management.cfg", "rmiRegistryPort", rmiRegistryPort),
            editConfigurationFilePut("etc/org.apache.karaf.management.cfg", "rmiServerPort", rmiServerPort),
            editConfigurationFilePut("etc/org.apache.karaf.shell.cfg", "sshPort", sshPort),
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
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-ldpath")));
        assertTrue(featuresService.isInstalled(featuresService.getFeature("fcrepo-service-ldcache-file")));
    }

    @Test
    public void testLdpathService() throws Exception {
        // Make sure camel has started up
        final CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.name=FcrepoLDPathContext)", 10000);
        assertNotNull(ctx);

        final String baseUrl = "http://localhost:" + System.getProperty("fcrepo.port") + "/fcrepo/rest";
        final String url1 = post(baseUrl, getTurtle("Object 1"), "text/turtle").replace(baseUrl, "");
        final String url2 = post(baseUrl, getTurtle("Object 2"), "text/turtle").replace(baseUrl, "");
        final String url3 = post(baseUrl + url1, getTurtle("Object 3"), "text/turtle").replace(baseUrl, "");
        final String url4 = post(baseUrl + url2, getTurtle("Object 4"), "text/turtle").replace(baseUrl, "");

        final String ldpathUrl = "http://localhost:" + System.getProperty("karaf.ldpath.port") + "/ldpath";

        final String response1 = get(ldpathUrl + url1);
        @SuppressWarnings("unchecked")
        final List<Map<String, List<String>>> data1 = MAPPER.readValue(response1, List.class);

        assertFalse(data1.isEmpty());
        assertTrue(data1.get(0).containsKey("label"));
        assertTrue(data1.get(0).containsKey("type"));
        assertTrue(data1.get(0).containsKey("id"));
        assertTrue(data1.get(0).get("id").contains(baseUrl + url1));
        assertTrue(data1.get(0).get("label").contains("Object 1"));
        assertTrue(data1.get(0).get("type").contains("http://fedora.info/definitions/v4/repository#Container"));
        assertTrue(data1.get(0).get("type").contains("http://fedora.info/definitions/v4/repository#Resource"));
        assertTrue(data1.get(0).get("type").contains("http://pcdm.org/models#Object"));
        assertTrue(data1.get(0).get("hasParent").contains(baseUrl + "/"));

        final String response2 = get(ldpathUrl + url2);
        @SuppressWarnings("unchecked")
        final List<Map<String, List<String>>> data2 = MAPPER.readValue(response2, List.class);

        assertFalse(data2.isEmpty());
        assertTrue(data2.get(0).containsKey("label"));
        assertTrue(data2.get(0).containsKey("type"));
        assertTrue(data2.get(0).containsKey("id"));
        assertTrue(data2.get(0).get("id").contains(baseUrl + url2));
        assertTrue(data2.get(0).get("label").contains("Object 2"));
        assertTrue(data2.get(0).get("type").contains("http://fedora.info/definitions/v4/repository#Container"));
        assertTrue(data2.get(0).get("type").contains("http://fedora.info/definitions/v4/repository#Resource"));
        assertTrue(data2.get(0).get("type").contains("http://pcdm.org/models#Object"));
        assertTrue(data2.get(0).get("hasParent").contains(baseUrl + "/"));

        final String response3 = get(ldpathUrl + url3);
        @SuppressWarnings("unchecked")
        final List<Map<String, List<String>>> data3 = MAPPER.readValue(response3, List.class);

        assertFalse(data3.isEmpty());
        assertTrue(data3.get(0).containsKey("label"));
        assertTrue(data3.get(0).containsKey("type"));
        assertTrue(data3.get(0).containsKey("id"));
        assertTrue(data3.get(0).get("id").contains(baseUrl + url3));
        assertTrue(data3.get(0).get("label").contains("Object 3"));
        assertTrue(data3.get(0).get("type").contains("http://fedora.info/definitions/v4/repository#Container"));
        assertTrue(data3.get(0).get("type").contains("http://fedora.info/definitions/v4/repository#Resource"));
        assertTrue(data3.get(0).get("type").contains("http://pcdm.org/models#Object"));
        assertTrue(data3.get(0).get("hasParent").contains(baseUrl + url1));

        final String response4 = get(ldpathUrl + url4);
        @SuppressWarnings("unchecked")
        final List<Map<String, List<String>>> data4 = MAPPER.readValue(response4, List.class);

        assertFalse(data4.isEmpty());
        assertTrue(data4.get(0).containsKey("label"));
        assertTrue(data4.get(0).containsKey("type"));
        assertTrue(data4.get(0).containsKey("id"));
        assertTrue(data4.get(0).get("id").contains(baseUrl + url4));
        assertTrue(data4.get(0).get("label").contains("Object 4"));
        assertTrue(data4.get(0).get("type").contains("http://fedora.info/definitions/v4/repository#Container"));
        assertTrue(data4.get(0).get("type").contains("http://fedora.info/definitions/v4/repository#Resource"));
        assertTrue(data4.get(0).get("type").contains("http://pcdm.org/models#Object"));
        assertTrue(data4.get(0).get("hasParent").contains(baseUrl + url2));
    }

    private InputStream getTurtle(final String title) {
        final String turtle = "PREFIX pcdm: <http://pcdm.org/models#>\n" +
                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n\n" +
                "<> a pcdm:Object ; rdfs:label \"" + title + "\" .\n";
        return new ByteArrayInputStream(turtle.getBytes(UTF_8));
    }
}
