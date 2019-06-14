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

import java.net.URI;

/**
 * 
 * Store SEMP connection info
 * 
 * @author Denis Forveille
 *
 */
public final class ManagementInfo {

   private static final String SEMP_QUEUES_LIST = "%s/SEMP/v2/config/msgVpns/%s/queues?select=queueName&count=1000";
   private static final String SEMP_TOPICS_LIST = "%s/SEMP/v2/config/msgVpns/%s/topicEndpoints?select=topicEndpointName&count=1000";

   private String              vpn;
   private String              mgmtUrl;
   private String              mgmtUsername;
   private String              mgmtPassword;

   private URI                 sempQueuesUri;
   private URI                 sempTopicsUri;

   // -------------------------
   // Constructor
   // -------------------------
   public ManagementInfo(String vpn, String mgmtUrl, String mgmtUsername, String mgmtPassword) {
      this.vpn = vpn;
      this.mgmtUrl = mgmtUrl;
      this.mgmtUsername = mgmtUsername;
      this.mgmtPassword = mgmtPassword;

      this.sempQueuesUri = URI.create(String.format(SEMP_QUEUES_LIST, mgmtUrl, vpn));
      this.sempTopicsUri = URI.create(String.format(SEMP_TOPICS_LIST, mgmtUrl, vpn));
   }

   // ------------------------
   // Standard Getters/Setters
   // ------------------------

   public String getVpn() {
      return vpn;
   }

   public String getMgmtUrl() {
      return mgmtUrl;
   }

   public String getMgmtUsername() {
      return mgmtUsername;
   }

   public String getMgmtPassword() {
      return mgmtPassword;
   }

   public URI getSempQueuesUri() {
      return sempQueuesUri;
   }

   public URI getSempTopicsUri() {
      return sempTopicsUri;
   }

}
