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

import java.net.HttpURLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
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
import org.titou10.jtb.qm.solace.semp.SempResponse;
import org.titou10.jtb.qm.solace.semp.SempTopicData;
import org.titou10.jtb.qm.solace.utils.PType;

import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;

/**
 * 
 * Implements Solace Q Provider
 * 
 * @author Denis Forveille
 *
 */
public class SolaceQManager extends QManager {

   private static final org.slf4j.Logger   log                    = LoggerFactory.getLogger(SolaceQManager.class);

   private static final String             CR                     = "\n";
   private static final String             HELP_TEXT;

   // HTTP REST stuff
   private static final HttpClient         HTTP_CLIENT            = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).build();

   // JSON-B stuff
   private static final Jsonb              JSONB                  = JsonbBuilder.create();
   private static final PType              JSONB_LIST_Q_DATA      = new PType(List.class, SempQueueData.class);
   private static final PType              JSONB_RESP_Q_DATA      = new PType(SempResponse.class, SempQueueData.class);
   private static final PType              JSONB_RESP_LIST_Q_DATA = new PType(SempResponse.class, JSONB_LIST_Q_DATA);
   private static final PType              JSONB_LIST_T_DATA      = new PType(List.class, SempTopicData.class);
   private static final PType              JSONB_RESP_T_DATA      = new PType(SempResponse.class, SempTopicData.class);
   private static final PType              JSONB_RESP_LIST_T_DATA = new PType(SempResponse.class, JSONB_LIST_T_DATA);

   // Properties
   private List<QManagerProperty>          parameters             = new ArrayList<QManagerProperty>();

   private static final String             MESSAGE_VPN            = "VPN";
   private static final String             MGMT_URL               = "mgmt_url";
   private static final String             MGMT_USERNAME          = "mgmt_username";
   private static final String             MGMT_PASSWORD          = "mgmt_password";
   private static final String             BROWSER_TIMEOUT        = "browser_timeout";

   // Operations
   private final Map<Integer, SEMPContext> sempContexts           = new HashMap<>();

   public SolaceQManager() {
      log.debug("Instantiate Solace");

      parameters.add(new QManagerProperty(MESSAGE_VPN, true, JMSPropertyKind.STRING, false, "VPN name", "default"));
      parameters.add(new QManagerProperty(MGMT_URL,
                                          true,
                                          JMSPropertyKind.STRING,
                                          false,
                                          "MGMT url (eg 'http://localhost:8080','https://localhost:8943)",
                                          ""));
      parameters.add(new QManagerProperty(MGMT_USERNAME, true, JMSPropertyKind.STRING, false, "SEMP user name", ""));
      parameters.add(new QManagerProperty(MGMT_PASSWORD, true, JMSPropertyKind.STRING, true, "SEMP user password", ""));
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
      sempContexts.put(hash, new SEMPContext(vpn, mgmtUrl, mgmtUsername, mgmtPassword));

      return jmsConnection;
   }

   @Override
   public void close(Connection jmsConnection) throws JMSException {
      log.debug("close connection {}", jmsConnection);

      Integer hash = jmsConnection.hashCode();
      sempContexts.remove(hash);

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
      SEMPContext sempContext = sempContexts.get(hash);

      // Build Queues list
      SortedSet<QueueData> listQueueData = new TreeSet<>();

      HttpResponse<String> response = HTTP_CLIENT.send(sempContext.getSempListQueuesRequest(), BodyHandlers.ofString());

      String body = response.body();
      log.warn("statusCode={}", response.statusCode());
      if (response.statusCode() != HttpURLConnection.HTTP_OK) {
         String msg = "Bad return code received from Solace SEMP when retrieving Queue List: " + response.statusCode();
         log.error(msg);
         throw new Exception(msg);
      }

      SempResponse<List<SempQueueData>> queues = JSONB.fromJson(body, JSONB_RESP_LIST_Q_DATA);
      for (SempQueueData q : queues.data) {
         log.warn("q={}", q.queueName);
         listQueueData.add(new QueueData(q.queueName));
      }

      // Build Topics lists
      SortedSet<TopicData> listTopicData = new TreeSet<>();

      response = HTTP_CLIENT.send(sempContext.getSempListTopicsRequest(), BodyHandlers.ofString());

      body = response.body();
      log.warn("statusCode={}", response.statusCode());
      if (response.statusCode() != HttpURLConnection.HTTP_OK) {
         String msg = "Bad return code received from Solace SEMP when retrieving Topic List: " + response.statusCode();
         log.error(msg);
         throw new Exception(msg);
      }

      SempResponse<List<SempTopicData>> topics = JSONB.fromJson(body, JSONB_RESP_LIST_T_DATA);
      for (SempTopicData t : topics.data) {
         log.warn("q={}", t.topicEndpointName);
         listTopicData.add(new TopicData(t.topicEndpointName));
      }

      return new DestinationData(listQueueData, listTopicData);
   }

   @Override
   public Map<String, Object> getQueueInformation(Connection jmsConnection, String queueName) {

      Integer hash = jmsConnection.hashCode();
      SEMPContext sempContext = sempContexts.get(hash);

      SortedMap<String, Object> properties = new TreeMap<>();

      try {
         HttpResponse<String> response = HTTP_CLIENT.send(sempContext.buildQueueInfoRequest(queueName), BodyHandlers.ofString());
         String body = response.body();
         log.warn("statusCode={}", response.statusCode());
         if (response.statusCode() != HttpURLConnection.HTTP_OK) {
            log.error("Bad return code received from Solace SEMP when retrieving Queue information for '{}' : {}",
                      queueName,
                      response.statusCode());
            return properties;
         }

         SempResponse<SempQueueData> qResp = JSONB.fromJson(body, JSONB_RESP_Q_DATA);
         SempQueueData qData = qResp.data;

         properties.put("accessType", qData.accessType);
         properties.put("consumerAckPropagationEnabled", qData.consumerAckPropagationEnabled);
         properties.put("deadMsgQueue", qData.deadMsgQueue);
         properties.put("egressEnabled", qData.egressEnabled);
         properties.put("maxBindCount", qData.maxBindCount);
         properties.put("maxDeliveredUnackedMsgsPerFlow ", qData.maxDeliveredUnackedMsgsPerFlow);
         properties.put("maxMsgSize", qData.maxMsgSize);
         properties.put("maxMsgSpoolUsage", qData.maxMsgSpoolUsage);
         properties.put("maxRedeliveryCount", qData.maxRedeliveryCount);
         properties.put("maxTtl", qData.maxTtl);
         properties.put("permission", qData.permission);
         properties.put("rejectLowPriorityMsgEnabled", qData.rejectLowPriorityMsgEnabled);
         properties.put("rejectLowPriorityMsgLimit", qData.rejectLowPriorityMsgLimit);
         properties.put("rejectMsgToSenderOnDiscardBehavior", qData.rejectMsgToSenderOnDiscardBehavior);
         properties.put("respectMsgPriorityEnabled", qData.respectMsgPriorityEnabled);
         properties.put("respectTtlEnabled", qData.respectTtlEnabled);

      } catch (Exception e) {
         log.error("Exception occurred in getQueueInformation()", e);
      }

      return properties;
   }

   @Override
   public Map<String, Object> getTopicInformation(Connection jmsConnection, String topicName) {

      Integer hash = jmsConnection.hashCode();
      SEMPContext sempContext = sempContexts.get(hash);

      TreeMap<String, Object> properties = new TreeMap<>();
      try {
         HttpResponse<String> response = HTTP_CLIENT.send(sempContext.buildTopicInfoRequest(topicName), BodyHandlers.ofString());
         String body = response.body();
         log.warn("statusCode={}", response.statusCode());
         if (response.statusCode() != HttpURLConnection.HTTP_OK) {
            log.error("Bad return code received from Solace SEMP when retrieving Topic information for '{}' : {}",
                      topicName,
                      response.statusCode());
            return properties;
         }

         SempResponse<SempTopicData> qResp = JSONB.fromJson(body, JSONB_RESP_T_DATA);
         SempTopicData tData = qResp.data;

         properties.put("accessType", tData.accessType);
         properties.put("consumerAckPropagationEnabled", tData.consumerAckPropagationEnabled);
         properties.put("deadMsgQueue", tData.deadMsgQueue);
         properties.put("egressEnabled", tData.egressEnabled);
         properties.put("ingressEnabled", tData.ingressEnabled);
         properties.put("maxBindCount", tData.maxBindCount);
         properties.put("maxDeliveredUnackedMsgsPerFlow", tData.maxDeliveredUnackedMsgsPerFlow);
         properties.put("maxMsgSize", tData.maxMsgSize);
         properties.put("maxRedeliveryCount", tData.maxRedeliveryCount);
         properties.put("maxSpoolUsage", tData.maxSpoolUsage);
         properties.put("maxTtl", tData.maxTtl);
         properties.put("permission", tData.permission);
         properties.put("rejectLowPriorityMsgEnabled", tData.rejectLowPriorityMsgEnabled);
         properties.put("rejectLowPriorityMsgLimit", tData.rejectLowPriorityMsgLimit);
         properties.put("respectMsgPriorityEnabled", tData.respectMsgPriorityEnabled);
         properties.put("respectTtlEnabled", tData.respectTtlEnabled);

      } catch (Exception e) {
         log.error("Exception occurred in getTopicInformation()", e);
      }

      return properties;
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
      sb.append("No extra jar is needed as JMSToolBox is bundled with the latest Solace client jars").append(CR);
      sb.append(CR);
      sb.append("Information").append(CR);
      sb.append("------------").append(CR);
      sb.append("Internally, JMSToolBox uses the SEMP feature of Solace to interacy with the SOlace server").append(CR);
      sb.append("For more information: https://docs.solace.com/SEMP/Using-SEMP.htm").append(CR);
      sb.append(CR);
      sb.append("Connection:").append(CR);
      sb.append("-----------").append(CR);
      sb.append("Host          : Solace server host name (eg localhost)").append(CR);
      sb.append("Port          : Solace server port (eg. 55555)").append(CR);
      sb.append("User/Password : User allowed to connect to get a JMS Connection ");
      sb.append(CR);
      sb.append(CR);
      sb.append("Properties:").append(CR);
      sb.append("-----------").append(CR);
      sb.append("- VPN             : Name of the VPN (eg 'default'").append(CR);
      sb.append(CR);
      sb.append("- mgmt_url        : URL (scheme+host+port) of the SEMP management interface (eg http://localhost:8080").append(CR);
      sb.append("- mgmt_username   : SEMP user name").append(CR);
      sb.append("- mgmt_password   : SEMP user password").append(CR);
      sb.append(CR);
      sb.append("- browser_timeout : The maximum time in ms for a QueueBrowser to wait for a message to arrive in the Browser’s local message buffer before returning.")
               .append(CR);
      sb.append("                     If there is already a message waiting, Enumeration.hasMoreElements() returns immediately.")
               .append(CR);
      sb.append("                     Minimum value: 250").append(CR);
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
