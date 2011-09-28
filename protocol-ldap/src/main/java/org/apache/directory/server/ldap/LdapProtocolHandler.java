/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */
package org.apache.directory.server.ldap;


import org.apache.directory.shared.ldap.message.ExtendedRequestImpl;
import org.apache.directory.shared.ldap.message.ResponseCarryingMessageException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.message.control.Control;
import org.apache.directory.shared.ldap.message.extended.NoticeOfDisconnect;
import org.apache.directory.shared.ldap.message.internal.InternalExtendedRequest;
import org.apache.directory.shared.ldap.message.internal.InternalRequest;
import org.apache.directory.shared.ldap.message.internal.InternalResultResponse;
import org.apache.directory.shared.ldap.message.internal.InternalResultResponseRequest;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.handler.demux.DemuxingIoHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The MINA IoHandler implementation extending {@link DemuxingIoHandler} for 
 * the LDAP protocol.  THe {@link LdapServer} creates this multiplexing 
 * {@link IoHandler} handler and populates it with subordinate handlers for
 * the various kinds of LDAP {@link InternalRequest} messages.  This is done in the
 * setXxxHandler() methods of the LdapServer where Xxxx is Add, Modify, etc.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
class LdapProtocolHandler extends DemuxingIoHandler
{
    /** The logger */
    private static final Logger LOG = LoggerFactory.getLogger( LdapProtocolHandler.class );
    
    /** the {@link LdapServer} this handler is associated with */
    private final LdapServer ldapServer;


    /**
     * Creates a new instance of LdapProtocolHandler.
     *
     * @param ldapServer
     */
    LdapProtocolHandler( LdapServer ldapServer )
    {
        this.ldapServer = ldapServer;
    }


    /*
     * (non-Javadoc)
     * @see org.apache.mina.common.IoHandlerAdapter#sessionCreated(org.apache.mina.common.IoSession)
     */
    public void sessionCreated( IoSession session ) throws Exception
    {
        LdapSession ldapSession = new LdapSession( session );
        ldapServer.getLdapSessionManager().addLdapSession( ldapSession );
    }


    /**
     * This method is called when a session is closed. If we have some 
     * cleanup to do, it's done there.
     * 
     * @param session the closing session
     */
    public void sessionClosed( IoSession session )
    {
        // Get the associated LdapSession
        LdapSession ldapSession = ldapServer.getLdapSessionManager().removeLdapSession( session );
        
        // Clean it up !
        cleanUpSession( ldapSession );
    }

    
    /**
     * Explicitly handles {@link LdapSession} and {@link IoSession} cleanup tasks.
     *
     * @param ldapSession the LdapSession to cleanup after being removed from 
     * the {@link LdapSessionManager}
     */
    private void cleanUpSession( LdapSession ldapSession )
    {
        if ( ldapSession == null )
        {
            LOG.warn( "Null LdapSession given to cleanUpSession." );
            return;
        }
        
        LOG.debug( "Cleaning the {} session", ldapSession );
        
        if ( ldapSession != null )
        {
            // Abandon all the requests
            ldapSession.abandonAllOutstandingRequests();
        }
        
        if ( ! ldapSession.getIoSession().isClosing() || ldapSession.getIoSession().isConnected() )
        {
            try
            {
                ldapSession.getIoSession().close( true );
            }
            catch ( Throwable t )
            {
                LOG.warn( "Failed to close IoSession for LdapSession." );
            }
        }
    }

    
    /*
     * (non-Javadoc)
     * @see org.apache.mina.handler.demux.DemuxingIoHandler#messageReceived(org.apache.mina.common.IoSession, java.lang.Object)
     */
    public void messageSent( IoSession session, Object message ) throws Exception
    {
        // Do nothing : we have to ignore this message, otherwise we get an exception,
        // thanks to the way MINA 2 works ...
    }


    /*
     * (non-Javadoc)
     * @see org.apache.mina.handler.demux.DemuxingIoHandler#messageReceived(org.apache.mina.common.IoSession, java.lang.Object)
     */
    public void messageReceived( IoSession session, Object message ) throws Exception
    {
        // Translate SSLFilter messages into LDAP extended request
        // defined in RFC #2830, 'Lightweight Directory Access Protocol (v3):
        // Extension for Transport Layer Security'.
        // 
        // The RFC specifies the payload should be empty, but we use
        // it to notify the TLS state changes.  This hack should be
        // OK from the viewpointd of security because StartTLS
        // handler should react to only SESSION_UNSECURED message
        // and degrade authentication level to 'anonymous' as specified
        // in the RFC, and this is no threat.

        if ( message == SslFilter.SESSION_SECURED )
        {
            InternalExtendedRequest req = new ExtendedRequestImpl( 0 );
            req.setOid( "1.3.6.1.4.1.1466.20037" );
            req.setPayload( "SECURED".getBytes( "ISO-8859-1" ) );
            message = req;
        }
        else if ( message == SslFilter.SESSION_UNSECURED )
        {
            InternalExtendedRequest req = new ExtendedRequestImpl( 0 );
            req.setOid( "1.3.6.1.4.1.1466.20037" );
            req.setPayload( "UNSECURED".getBytes( "ISO-8859-1" ) );
            message = req;
        }

        if ( ( ( InternalRequest ) message ).getControls().size() > 0 && message instanceof InternalResultResponseRequest )
        {
            InternalResultResponseRequest req = ( InternalResultResponseRequest ) message;
            
            for ( Control control : req.getControls().values() )
            {
                if ( control.isCritical() && ! ldapServer.getSupportedControls().contains( control.getOid() ) )
                {
                    InternalResultResponse resp = req.getResultResponse();
                    resp.getLdapResult().setErrorMessage( "Unsupport critical control: " + control.getOid() );
                    resp.getLdapResult().setResultCode( ResultCodeEnum.UNAVAILABLE_CRITICAL_EXTENSION );
                    session.write( resp );
                    return;
                }
            }
        }

        super.messageReceived( session, message );
    }


    /*
     * (non-Javadoc)
     * @see org.apache.mina.common.IoHandlerAdapter#exceptionCaught(org.apache.mina.common.IoSession, java.lang.Throwable)
     */
    public void exceptionCaught( IoSession session, Throwable cause )
    {
        if ( cause.getCause() instanceof ResponseCarryingMessageException )
        {
            ResponseCarryingMessageException rcme = ( ResponseCarryingMessageException ) cause.getCause();

            if ( rcme.getResponse() != null )
            {
                session.write( rcme.getResponse() );
                return;
            }                
        }
        
        LOG.warn( "Unexpected exception forcing session to close: sending disconnect notice to client.", cause );

        session.write( NoticeOfDisconnect.PROTOCOLERROR );
        LdapSession ldapSession = this.ldapServer.getLdapSessionManager().removeLdapSession( session );
        cleanUpSession( ldapSession );
        session.close( true );
    }
}