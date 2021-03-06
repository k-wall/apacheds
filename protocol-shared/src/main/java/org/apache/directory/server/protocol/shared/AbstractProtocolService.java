/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.server.protocol.shared;


import java.util.HashSet;
import java.util.Set;

import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.protocol.shared.transport.Transport;
import org.apache.mina.transport.socket.DatagramAcceptor;
import org.apache.mina.transport.socket.SocketAcceptor;


/**
 * An abstract base class for a ProtocolService. The start/stop methods have
 * not been implemented.
 * 
 * @org.apache.xbean.XBean
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public abstract class AbstractProtocolService implements ProtocolService
{
    /** A flag set to indicate if the server is started or not */ 
    private boolean started;
    
    /** A flag set to tell if the server is enabled or not */
    private boolean enabled;
    
    /** The server ID */
    private String serviceId;
    
    /** The service name */
    private String serviceName;
    
    /** The service transports. We may have more than one */
    protected Set<Transport> transports = new HashSet<Transport>();
    
    /** directory service core where protocol data is backed */
    private DirectoryService directoryService;


    public DirectoryService getDirectoryService()
    {
        return directoryService;
    }


    /**
     * @org.apache.xbean.Property hidden="true"
     */
    public void setDirectoryService( DirectoryService directoryService )
    {
        this.directoryService = directoryService;
    }


    public boolean isStarted()
    {
        return started;
    }


    /**
     * @org.apache.xbean.Property hidden="true"
     * 
     * @param started The state of this server
     */
    protected void setStarted( boolean started )
    {
        this.started = started;
    }


    public boolean isEnabled()
    {
        return enabled;
    }


    /**
     * {@inheritDoc}
     */
    public void setEnabled( boolean enabled )
    {
        this.enabled = enabled;
    }


    public String getServiceId()
    {
        return serviceId;
    }


    /**
     * @org.apache.xbean.Property hidden="true"
     */
    public void setServiceId( String serviceId )
    {
        this.serviceId = serviceId;
    }


    /**
     * @return The server name
     */
    public String getServiceName()
    {
        return serviceName;
    }


    /**
     * @org.apache.xbean.Property hidden="true"
     * 
     * Set the current server's name.
     * @param name The server name
     */
    public void setServiceName( String name )
    {
        this.serviceName = name;
    }
    
    
    /**
     * @return the transport
     */
    public Transport[] getTransports()
    {
        return transports.toArray( new Transport[]{} );
    }


    /**
     * Set the underlying transports
     * @param transport The transports
     */
    public void setTransports( Transport... transports )
    {
        for ( Transport transport : transports ) 
        {
            this.transports.add( transport );
            
            if ( transport.getAcceptor() == null )
            {
                transport.init();
            }
        }
    }
    
    
    /**
     * Add underlying transports
     * @param transport The transports
     */
    public void addTransports( Transport... transports )
    {
        for ( Transport transport : transports )
        {
            this.transports.add( transport );
            
            if ( transport.getAcceptor() == null )
            {
                transport.init();
            }
        }
    }
    
    
    /**
     * {@inheritDoc}
     */
    public DatagramAcceptor getDatagramAcceptor( Transport udpTransport )
    {
        return (DatagramAcceptor)udpTransport.getAcceptor();
    }


    /**
     * {@inheritDoc}
     */
    public SocketAcceptor getSocketAcceptor( Transport tcpTransport )
    {
        return (SocketAcceptor)tcpTransport.getAcceptor();
    }
}
