/*
 * Copyright (C) 2015 Denis Forveille titou10.titou10@gmail.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.titou10.jtb.qm.solace;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import org.slf4j.LoggerFactory;
import org.titou10.jtb.config.gen.SessionDef;
import org.titou10.jtb.jms.qm.DestinationData;
import org.titou10.jtb.jms.qm.JMSPropertyKind;
import org.titou10.jtb.jms.qm.QManager;
import org.titou10.jtb.jms.qm.QManagerProperty;
import org.titou10.jtb.jms.qm.QueueData;
import org.titou10.jtb.jms.qm.TopicData;
import org.titou10.jtb.qm.solace.semp.SempQueueData;
import org.titou10.jtb.qm.solace.semp.SempQueueResponse;
import org.titou10.jtb.qm.solace.semp.SempTopicData;
import org.titou10.jtb.qm.solace.semp.SempTopicResponse;

import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;

/**
 * 
 * Implements Solace
 * 
 * @author Denis Forveille
 *
 */
public class SolaceQManager extends QManager {

   private static final org.slf4j.Logger      log             = LoggerFactory.getLogger(SolaceQManager.class);

   private static final String                CR              = "\n";

   private static final String                MESSAGE_VPN     = "VPN";
   private static final String                MGMT_URL        = "mgmt_url";
   private static final String                MGMT_USERNAME   = "mgmt_username";
   private static final String                MGMT_PASSWORD   = "mgmt_password";
   private static final String                BROWSER_TIMEOUT = "browserTimeout";

   private final Map<Integer, ManagementInfo> managementInfos = new HashMap<>();

   private static final String                HELP_TEXT;

   private List<QManagerProperty>             parameters      = new ArrayList<QManagerProperty>();

   public SolaceQManager() {
      log.debug("Instantiate Solace");

      parameters.add(new QManagerProperty(MESSAGE_VPN, true, JMSPropertyKind.STRING, false, "VPN name", "default"));
      parameters.add(new QManagerProperty(MGMT_URL,
                                          true,
                                          JMSPropertyKind.STRING,
                                          false,
                                          "MGMT url (eg 'http://localhost:8080','https://localhost:8943)",
                                          ""));
      parameters.add(new QManagerProperty(MGMT_USERNAME, true, JMSPropertyKind.STRING, false, "MGMT username", ""));
      parameters.add(new QManagerProperty(MGMT_PASSWORD, true, JMSPropertyKind.STRING, true, "MGMT password", ""));
      parameters
               .add(new QManagerProperty(BROWSER_TIMEOUT,
                                         true,
                                         JMSPropertyKind.INT,
                                         false,
                                         "The maximum time in milliseconds for a QueueBrowser Enumeration.hasMoreElements() to wait for a message "
                                                + "to arrive in the Browser’s local message buffer before returning. If there is already a message waiting, "
                                                + "Enumeration.hasMoreElements() returns immediately.",
                                         "250"));

   }

   @Override
   public Connection connect(SessionDef sessionDef, boolean showSystemObjects, String clientID) throws Exception {
      log.info("connecting to {} - {}", sessionDef.getName(), clientID);

      // Extract properties
      Map<String, String> mapProperties = extractProperties(sessionDef);

      String vpn = mapProperties.get(MESSAGE_VPN);
      String mgmtUrl = mapProperties.get(MGMT_URL);
      String mgmtUsername = mapProperties.get(MGMT_USERNAME);
      String mgmtPassword = mapProperties.get(MGMT_PASSWORD);
      int browserTimeout = Integer.parseInt(mapProperties.get(BROWSER_TIMEOUT));
      if (browserTimeout < 250) {
         throw new Exception("Browser timeout must be an integer greater than or equal to 250.");
      }

      // JMS Connections

      SolConnectionFactory cf = SolJmsUtility.createConnectionFactory();
      cf.setHost(sessionDef.getHost());
      cf.setPort(sessionDef.getPort());
      cf.setVPN(vpn);
      cf.setUsername(sessionDef.getActiveUserid());
      cf.setPassword(sessionDef.getActivePassword());
      cf.setDirectTransport(false);
      cf.setBrowserTimeoutInMS(browserTimeout);

      Connection jmsConnection = cf.createConnection();
      jmsConnection.setClientID(clientID);
      jmsConnection.start();

      // Store per connection related data
      Integer hash = jmsConnection.hashCode();
      managementInfos.put(hash, new ManagementInfo(vpn, mgmtUrl, mgmtUsername, mgmtPassword));

      return jmsConnection;
   }

   @Override
   public void close(Connection jmsConnection) throws JMSException {
      log.debug("close connection {}", jmsConnection);

      try {
         jmsConnection.close();
      } catch (Exception e) {
         log.warn("Exception occurred while closing jmsConnection. Ignore it. Msg={}", e.getMessage());
      }
   }

   @Override
   public DestinationData discoverDestinations(Connection jmsConnection, boolean showSystemObjects) throws Exception {
      log.debug("discoverDestinations : {} - {}", jmsConnection, showSystemObjects);

      Integer hash = jmsConnection.hashCode();
      ManagementInfo managementInfo = managementInfos.get(hash);

      // Build Queues/Topics lists
      SortedSet<QueueData> listQueueData = new TreeSet<>();
      SortedSet<TopicData> listTopicData = new TreeSet<>();

      HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
      Jsonb jsonb = JsonbBuilder.create();

      String authHeader = "Basic " + Base64.getEncoder()
               .encodeToString((managementInfo.getMgmtUsername() + ":" + managementInfo.getMgmtPassword()).getBytes());

      HttpRequest request = HttpRequest.newBuilder().uri(managementInfo.getSempQueuesUri()).GET().timeout(Duration.ofMinutes(1))
               .header("Content-Type", "application/json").header("Authorization", authHeader).build();
      HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

      String body = response.body();
      log.warn("statusCode={}", response.statusCode());
      log.warn("response={}", body);

      SempQueueResponse queues = jsonb.fromJson(body, SempQueueResponse.class);
      for (SempQueueData q : queues.data) {
         log.warn("q={}", q.queueName);
         listQueueData.add(new QueueData(q.queueName));
      }

      // ----------

      request = HttpRequest.newBuilder().uri(managementInfo.getSempTopicsUri()).GET().timeout(Duration.ofMinutes(1))
               .header("Content-Type", "application/json").header("Authorization", authHeader).build();
      response = httpClient.send(request, BodyHandlers.ofString());

      body = response.body();
      log.warn("statusCode={}", response.statusCode());
      log.warn("response={}", body);

      SempTopicResponse topics = jsonb.fromJson(body, SempTopicResponse.class);
      for (SempTopicData t : topics.data) {
         log.warn("q={}", t.topicEndpointName);
         listTopicData.add(new TopicData(t.topicEndpointName));
      }

      return new DestinationData(listQueueData, listTopicData);
   }

   // -------
   // Helpers
   // -------

   @Override
   public String getHelpText() {
      return HELP_TEXT;
   }

   static {
      StringBuilder sb = new StringBuilder(2048);
      sb.append("Extra JARS :").append(CR);
      sb.append("------------").append(CR);
      sb.append(CR);

      HELP_TEXT = sb.toString();
   }

   // ------------------------
   // Standard Getters/Setters
   // ------------------------

   @Override
   public List<QManagerProperty> getQManagerProperties() {
      return parameters;
   }

}
