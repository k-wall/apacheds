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


import java.net.InetAddress;
import java.net.InetSocketAddress;

import javax.security.auth.kerberos.KerberosPrincipal;

import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.kerberos.kdc.KdcServer;
import org.apache.directory.server.kerberos.kdc.authentication.AuthenticationContext;
import org.apache.directory.server.kerberos.kdc.authentication.AuthenticationService;
import org.apache.directory.server.kerberos.kdc.ticketgrant.TicketGrantingContext;
import org.apache.directory.server.kerberos.kdc.ticketgrant.TicketGrantingService;
import org.apache.directory.server.kerberos.shared.KerberosMessageType;
import org.apache.directory.server.kerberos.shared.exceptions.ErrorType;
import org.apache.directory.server.kerberos.shared.exceptions.KerberosException;
import org.apache.directory.server.kerberos.shared.messages.ErrorMessage;
import org.apache.directory.server.kerberos.shared.messages.ErrorMessageModifier;
import org.apache.directory.server.kerberos.shared.messages.KdcRequest;
import org.apache.directory.server.kerberos.shared.messages.value.KerberosTime;
import org.apache.directory.server.kerberos.shared.store.PrincipalStore;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The Kerberos protocol handler for MINA which handles requests for the authentication
 * service and the ticket granting service of the KDC.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 901657 $, $Date: 2010-01-21 12:27:15 +0100 (Jeu, 21 jan 2010) $
 */
public class KerberosProtocolHandler implements IoHandler
{
    private static final Logger log = LoggerFactory.getLogger( KerberosProtocolHandler.class );

    private KdcServer config;
    private PrincipalStore store;
    private static final String CONTEXT_KEY = "context";
    

    /**
     * Creates a new instance of KerberosProtocolHandler.
     *
     * @param config
     * @param store
     */
    public KerberosProtocolHandler( KdcServer config, PrincipalStore store )
    {
        this.config = config;
        this.store = store;
    }


    public void sessionCreated( IoSession session ) throws Exception
    {
        if ( log.isDebugEnabled() )
        {
            log.debug( "{} CREATED:  {}", session.getRemoteAddress(), session.getTransportMetadata() );
        }
    }


    public void sessionOpened( IoSession session )
    {
        if ( log.isDebugEnabled() )
        {
            log.debug( "{} OPENED", session.getRemoteAddress() );
        }
    }


    public void sessionClosed( IoSession session )
    {
        if ( log.isDebugEnabled() )
        {
            log.debug( "{} CLOSED", session.getRemoteAddress() );
        }
    }


    public void sessionIdle( IoSession session, IdleStatus status )
    {
        if ( log.isDebugEnabled() )
        {
            log.debug( "{} IDLE ({})", session.getRemoteAddress(), status );
        }
    }


    public void exceptionCaught( IoSession session, Throwable cause )
    {
        log.error( session.getRemoteAddress() + " EXCEPTION", cause );
        session.close( true );
    }


    public void messageReceived( IoSession session, Object message )
    {
        if ( log.isDebugEnabled() )
        {
            log.debug( "{} RCVD:  {}", session.getRemoteAddress(), message );
        }

        InetAddress clientAddress = ( ( InetSocketAddress ) session.getRemoteAddress() ).getAddress();
        KdcRequest request = ( KdcRequest ) message;

        KerberosMessageType messageType = request.getMessageType();

        try
        {
            switch ( messageType )
            {
                case AS_REQ :
                    AuthenticationContext authContext = new AuthenticationContext();
                    authContext.setConfig( config );
                    authContext.setStore( store );
                    authContext.setClientAddress( clientAddress );
                    authContext.setRequest( request );
                    session.setAttribute( CONTEXT_KEY, authContext );

                    AuthenticationService.execute( authContext );

                    session.write( authContext.getReply() );
                    break;

                case TGS_REQ:
                    TicketGrantingContext tgsContext = new TicketGrantingContext();
                    tgsContext.setConfig( config );
                    tgsContext.setStore( store );
                    tgsContext.setClientAddress( clientAddress );
                    tgsContext.setRequest( request );
                    session.setAttribute( CONTEXT_KEY, tgsContext );

                    TicketGrantingService.execute( tgsContext );

                    session.write( tgsContext.getReply() );
                    break;

                case AS_REP:
                case TGS_REP:
                    throw new KerberosException( ErrorType.KRB_AP_ERR_BADDIRECTION );

                default:
                    throw new KerberosException( ErrorType.KRB_AP_ERR_MSG_TYPE );
            }
        }
        catch ( KerberosException ke )
        {
            String messageText = ke.getLocalizedMessage() + " (" + ke.getErrorCode() + ")";

            if ( log.isDebugEnabled() )
            {
                log.warn( messageText, ke );
            }
            else
            {
                log.warn( messageText );
            }

            ErrorMessage error = getErrorMessage( config.getServicePrincipal(), ke );

            if ( log.isDebugEnabled() )
            {
                logErrorMessage( error );
            }

            session.write( error );
        }
        catch ( Exception e )
        {
        e.printStackTrace();
            log.error( I18n.err( I18n.ERR_152, e.getLocalizedMessage() ), e );

            session.write( getErrorMessage( config.getServicePrincipal(), new KerberosException(
                ErrorType.KDC_ERR_SVC_UNAVAILABLE ) ) );
        }
    }


    public void messageSent( IoSession session, Object message )
    {
        if ( log.isDebugEnabled() )
        {
            log.debug( "{} SENT:  {}", session.getRemoteAddress(), message );
        }
    }


    protected ErrorMessage getErrorMessage( KerberosPrincipal principal, KerberosException exception )
    {
        ErrorMessageModifier modifier = new ErrorMessageModifier();

        KerberosTime now = new KerberosTime();

        modifier.setErrorCode( exception.getErrorCode() );
        modifier.setExplanatoryText( exception.getLocalizedMessage() );
        modifier.setServerPrincipal( principal );
        modifier.setServerTime( now );
        modifier.setServerMicroSecond( 0 );
        modifier.setExplanatoryData( exception.getExplanatoryData() );

        return modifier.getErrorMessage();
    }


    protected void logErrorMessage( ErrorMessage error )
    {
        try
        {
            StringBuffer sb = new StringBuffer();

            sb.append( "Responding to request with error:" );
            sb.append( "\n\t" + "explanatory text:      " + error.getExplanatoryText() );
            sb.append( "\n\t" + "error code:            " + error.getErrorCode() );
            sb.append( "\n\t" + "clientPrincipal:       " + error.getClientPrincipal() );
            sb.append( "\n\t" + "client time:           " + error.getClientTime() );
            sb.append( "\n\t" + "serverPrincipal:       " + error.getServerPrincipal() );
            sb.append( "\n\t" + "server time:           " + error.getServerTime() );

            log.debug( sb.toString() );
        }
        catch ( Exception e )
        {
            // This is a monitor.  No exceptions should bubble up.
            log.error( I18n.err( I18n.ERR_155 ), e );
        }
    }
}
