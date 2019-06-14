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
package org.titou10.jtb.qm.solace.semp;

/**
 * 
 * Representation of a Topic from SEMP
 * 
 * @author Denis Forveille
 *
 */
public class SempTopicData {

   public Boolean consumerAckPropagationEnabled;
   public String  deadMsgQueue;
   public Boolean egressEnabled;
   public Boolean ingressEnabled;
   public Long    maxBindCount;
   public Long    maxDeliveredUnackedMsgsPerFlow;
   public Integer maxMsgSize;
   public Long    maxRedeliveryCount;
   public Long    maxSpoolUsage;
   public Long    maxTtl;
   public Boolean rejectLowPriorityMsgEnabled;
   public Long    rejectLowPriorityMsgLimit;
   public Boolean respectMsgPriorityEnabled;
   public Boolean respectTtlEnabled;
   public String  topicEndpointName;

   // -------------------------
   // Constructor
   // -------------------------
   public SempTopicData() {
      // JSON-B
   }
}
