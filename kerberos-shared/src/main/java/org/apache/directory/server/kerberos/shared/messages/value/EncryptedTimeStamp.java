/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server.kerberos.shared.messages.value;


import org.apache.directory.server.kerberos.shared.messages.Encodable;


/**
 * Pre-authentication encrypted timestamp.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 540371 $, $Date: 2007-05-22 02:00:43 +0200 (Mar, 22 mai 2007) $
 */
public class EncryptedTimeStamp implements Encodable
{
    private KerberosTime timeStamp;
    private int microSeconds; //optional


    /**
     * Creates a new instance of EncryptedTimeStamp.
     *
     * @param timeStamp
     * @param microSeconds
     */
    public EncryptedTimeStamp( KerberosTime timeStamp, int microSeconds )
    {
        this.timeStamp = timeStamp;
        this.microSeconds = microSeconds;
    }


    /**
     * Returns the {@link KerberosTime}.
     *
     * @return The {@link KerberosTime}.
     */
    public KerberosTime getTimeStamp()
    {
        return timeStamp;
    }


    /**
     * Returns the microseconds.
     *
     * @return The microseconds.
     */
    public int getMicroSeconds()
    {
        return microSeconds;
    }
}
