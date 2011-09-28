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
package org.apache.directory.server.kerberos.protocol;


import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class KerberosTcpProtocolCodecFactory implements ProtocolCodecFactory
{
    private static final KerberosTcpProtocolCodecFactory INSTANCE = new KerberosTcpProtocolCodecFactory();


    /**
     * Returns the singleton {@link KerberosTcpProtocolCodecFactory}.
     *
     * @return The singleton {@link KerberosTcpProtocolCodecFactory}.
     */
    public static KerberosTcpProtocolCodecFactory getInstance()
    {
        return INSTANCE;
    }


    private KerberosTcpProtocolCodecFactory()
    {
        // Private constructor prevents instantiation outside this class.
    }


    public ProtocolEncoder getEncoder( IoSession session )
    {
        // Create a new encoder.
        return new KerberosTcpEncoder();
    }


    public ProtocolDecoder getDecoder( IoSession session )
    {
        // Create a new decoder.
        return new KerberosTcpDecoder();
    }
}
