/*
 * Copyright (C) 2019 Denis Forveille titou10.titou10@gmail.com
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

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.titou10.jtb.qm.solace.semp.SempJndiTopicData;

/**
 * 
 * Store SEMP connection info
 * 
 * @author Denis Forveille
 *
 */
public final class SEMPContext {

   private static final Duration          HTTP_TIMEOUT          = Duration.ofSeconds(30L);

   private static final String            SEMP_CONFIG_URI       = "/SEMP/v2/config/msgVpns/%s";
   private static final String            SEMP_COUNT_PARAM      = "count=1000";

   private static final String            SEMP_QUEUES_LIST      = "%s" + SEMP_CONFIG_URI + "/queues?select=queueName&"
                                                                  + SEMP_COUNT_PARAM;
   private static final String            SEMP_JNDI_TOPICS_LIST = "%s" + SEMP_CONFIG_URI + "/jndiTopics?" + SEMP_COUNT_PARAM;

   private static final String            SEMP_QUEUE_INFO       = "%s" + SEMP_CONFIG_URI + "/queues/%s";
   private static final String            SEMP_TOPIC_INFO       = "%s" + SEMP_CONFIG_URI + "/topicEndpoints/%s";

   private String                         vpn;
   private String                         mgmtUrl;

   private HttpRequest                    sempListQueuesRequest;
   private HttpRequest                    sempListJndiTopicsRequest;

   private String                         authHeader;

   private Map<String, SempJndiTopicData> mapJndiTopicData      = new HashMap<>();                                           // topicName,
                                                                                                                             // SempJndiTopicData

   // -------------------------
   // Constructor
   // -------------------------
   public SEMPContext(String vpn, String mgmtUrl, String mgmtUsername, String mgmtPassword) {
      this.vpn = vpn;
      this.mgmtUrl = mgmtUrl;

      this.authHeader = "Basic " + Base64.getEncoder().encodeToString((mgmtUsername + ":" + mgmtPassword).getBytes());

      this.sempListQueuesRequest = HttpRequest.newBuilder().uri(URI.create(String.format(SEMP_QUEUES_LIST, mgmtUrl, vpn))).GET()
               .timeout(HTTP_TIMEOUT).header("Content-Type", "application/json").header("Authorization", authHeader).build();

      this.sempListJndiTopicsRequest = HttpRequest.newBuilder().uri(URI.create(String.format(SEMP_JNDI_TOPICS_LIST, mgmtUrl, vpn)))
               .GET().timeout(HTTP_TIMEOUT).header("Content-Type", "application/json").header("Authorization", authHeader).build();

   }

   // ------------------------
   // Helpers
   // ------------------------
   public HttpRequest buildQueueInfoRequest(String queueName) {
      return HttpRequest.newBuilder().uri(URI.create(String.format(SEMP_QUEUE_INFO, mgmtUrl, vpn, queueName))).GET()
               .timeout(Duration.ofMinutes(1)).header("Content-Type", "application/json").header("Authorization", authHeader)
               .build();
   }

   public HttpRequest buildTopicInfoRequest(String topicName) {
      SempJndiTopicData sempJndiTopicData = mapJndiTopicData.get(topicName);
      return HttpRequest.newBuilder().uri(URI.create(String.format(SEMP_TOPIC_INFO, mgmtUrl, vpn, sempJndiTopicData.physicalName)))
               .GET().timeout(Duration.ofMinutes(1)).header("Content-Type", "application/json").header("Authorization", authHeader)
               .build();
   }

   public void putJndiTopicData(SempJndiTopicData sempJndiTopicData) {
      mapJndiTopicData.put(sempJndiTopicData.physicalName, sempJndiTopicData);
   }

   public SempJndiTopicData getJndiTopicData(String topicName) {
      return mapJndiTopicData.get(topicName);
   }

   // ------------------------
   // Standard Getters/Setters
   // ------------------------

   public HttpRequest getSempListQueuesRequest() {
      return sempListQueuesRequest;
   }

   public HttpRequest getSempListJndiTopicsRequest() {
      return sempListJndiTopicsRequest;
   }

}
