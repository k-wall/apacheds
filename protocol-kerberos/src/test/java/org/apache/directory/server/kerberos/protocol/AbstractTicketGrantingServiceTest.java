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


import java.io.IOException;
import java.security.SecureRandom;

import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosPrincipal;

import org.apache.directory.server.kerberos.shared.KerberosConstants;
import org.apache.directory.server.kerberos.shared.KerberosMessageType;
import org.apache.directory.server.kerberos.shared.crypto.checksum.ChecksumHandler;
import org.apache.directory.server.kerberos.shared.crypto.checksum.ChecksumType;
import org.apache.directory.server.kerberos.shared.crypto.encryption.CipherTextHandler;
import org.apache.directory.server.kerberos.shared.crypto.encryption.EncryptionType;
import org.apache.directory.server.kerberos.shared.crypto.encryption.KeyUsage;
import org.apache.directory.server.kerberos.shared.crypto.encryption.RandomKeyFactory;
import org.apache.directory.server.kerberos.shared.exceptions.KerberosException;
import org.apache.directory.server.kerberos.shared.io.encoder.ApplicationRequestEncoder;
import org.apache.directory.server.kerberos.shared.io.encoder.KdcRequestEncoder;
import org.apache.directory.server.kerberos.shared.messages.ApplicationRequest;
import org.apache.directory.server.kerberos.shared.messages.KdcRequest;
import org.apache.directory.server.kerberos.shared.messages.components.Authenticator;
import org.apache.directory.server.kerberos.shared.messages.components.AuthenticatorModifier;
import org.apache.directory.server.kerberos.shared.messages.components.EncTicketPart;
import org.apache.directory.server.kerberos.shared.messages.components.EncTicketPartModifier;
import org.apache.directory.server.kerberos.shared.messages.components.Ticket;
import org.apache.directory.server.kerberos.shared.messages.value.ApOptions;
import org.apache.directory.server.kerberos.shared.messages.value.Checksum;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptedData;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptionKey;
import org.apache.directory.server.kerberos.shared.messages.value.KerberosTime;
import org.apache.directory.server.kerberos.shared.messages.value.PaData;
import org.apache.directory.server.kerberos.shared.messages.value.PrincipalName;
import org.apache.directory.server.kerberos.shared.messages.value.RequestBody;
import org.apache.directory.server.kerberos.shared.messages.value.TransitedEncoding;
import org.apache.directory.server.kerberos.shared.messages.value.flags.TicketFlag;
import org.apache.directory.server.kerberos.shared.messages.value.flags.TicketFlags;
import org.apache.directory.server.kerberos.shared.messages.value.types.PaDataType;
import org.apache.directory.server.kerberos.shared.messages.value.types.PrincipalNameType;


/**
 * Abstract base class for Ticket-Granting Service (TGS) tests, with utility methods
 * for generating message components.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$        Ticket ticket = ticketModifier.getTicket();


 */
public abstract class AbstractTicketGrantingServiceTest
{
    protected CipherTextHandler lockBox;
    protected static final SecureRandom random = new SecureRandom();

    /** Session attributes that must be verified. */
    protected EncryptionKey sessionKey;
    protected EncryptionKey subSessionKey;
    protected int sequenceNumber;
    protected KerberosTime now;
    protected int clientMicroSeconds = 0;


    protected Ticket getTgt( KerberosPrincipal clientPrincipal, KerberosPrincipal serverPrincipal, String serverPassword )
        throws Exception
    {
        EncryptionKey serverKey = getEncryptionKey( serverPrincipal, serverPassword );
        return getTicket( clientPrincipal, serverPrincipal, serverKey );
    }


    /**
     * Returns an encryption key derived from a principal name and passphrase.
     *
     * @param principal
     * @param passPhrase
     * @return The server's {@link EncryptionKey}.
     */
    protected EncryptionKey getEncryptionKey( KerberosPrincipal principal, String passPhrase )
    {
        KerberosKey kerberosKey = new KerberosKey( principal, passPhrase.toCharArray(), "DES" );
        byte[] keyBytes = kerberosKey.getEncoded();
        return new EncryptionKey( EncryptionType.DES_CBC_MD5, keyBytes );
    }


    /**
     * Build the service ticket.  The service ticket contains the session key generated
     * by the KDC for the client and service to use.  The service will unlock the
     * authenticator with the session key from the ticket.  The principal in the ticket
     * must equal the authenticator client principal.
     * 
     * If set in the AP Options, the Ticket can also be sealed with the session key.
     * 
     * @param clientPrincipal
     * @param serverPrincipal
     * @param serverKey 
     * @return The {@link Ticket}.
     * @throws KerberosException
     */
    protected Ticket getTicket( KerberosPrincipal clientPrincipal, KerberosPrincipal serverPrincipal,
        EncryptionKey serverKey ) throws KerberosException
    {
        EncTicketPartModifier encTicketModifier = new EncTicketPartModifier();

        TicketFlags ticketFlags = new TicketFlags();
        ticketFlags.setFlag( TicketFlag.RENEWABLE );
        encTicketModifier.setFlags( ticketFlags );

        EncryptionKey sessionKey = RandomKeyFactory.getRandomKey( EncryptionType.DES_CBC_MD5 );

        encTicketModifier.setSessionKey( sessionKey );
        encTicketModifier.setClientPrincipal( clientPrincipal );
        encTicketModifier.setTransitedEncoding( new TransitedEncoding() );
        encTicketModifier.setAuthTime( new KerberosTime() );

        long now = System.currentTimeMillis();
        KerberosTime endTime = new KerberosTime( now + KerberosTime.DAY );
        encTicketModifier.setEndTime( endTime );

        KerberosTime renewTill = new KerberosTime( now + KerberosTime.WEEK );
        encTicketModifier.setRenewTill( renewTill );

        EncTicketPart encTicketPart = encTicketModifier.getEncTicketPart();

        EncryptedData encryptedTicketPart = lockBox.seal( serverKey, encTicketPart, KeyUsage.NUMBER2 );

        Ticket ticket = new Ticket( KerberosConstants.KERBEROS_V5, serverPrincipal, encryptedTicketPart );

        ticket.setEncTicketPart( encTicketPart );

        return ticket;
    }


    protected EncTicketPartModifier getTicketArchetype( KerberosPrincipal clientPrincipal ) throws KerberosException
    {
        EncTicketPartModifier encTicketModifier = new EncTicketPartModifier();

        TicketFlags ticketFlags = new TicketFlags();
        ticketFlags.setFlag( TicketFlag.RENEWABLE );
        encTicketModifier.setFlags( ticketFlags );

        EncryptionKey sessionKey = RandomKeyFactory.getRandomKey( EncryptionType.DES_CBC_MD5 );

        encTicketModifier.setSessionKey( sessionKey );
        encTicketModifier.setClientPrincipal( clientPrincipal );
        encTicketModifier.setTransitedEncoding( new TransitedEncoding() );
        encTicketModifier.setAuthTime( new KerberosTime() );

        long now = System.currentTimeMillis();
        KerberosTime endTime = new KerberosTime( now + KerberosTime.DAY );
        encTicketModifier.setEndTime( endTime );

        KerberosTime renewTill = new KerberosTime( now + KerberosTime.WEEK );
        encTicketModifier.setRenewTill( renewTill );

        return encTicketModifier;
    }


    protected Ticket getTicket( EncTicketPartModifier encTicketModifier, KerberosPrincipal serverPrincipal,
        EncryptionKey serverKey ) throws KerberosException
    {
        EncTicketPart encTicketPart = encTicketModifier.getEncTicketPart();

        EncryptedData encryptedTicketPart = lockBox.seal( serverKey, encTicketPart, KeyUsage.NUMBER2 );

        Ticket ticket = new Ticket();
        ticket.setTktVno( 5 );
        ticket.setServerPrincipal( serverPrincipal );
        ticket.setEncPart( encryptedTicketPart );

        ticket.setEncTicketPart( encTicketPart );

        return ticket;
    }


    protected KdcRequest getKdcRequest( Ticket tgt, RequestBody requestBody ) throws Exception
    {
        return getKdcRequest( tgt, requestBody, ChecksumType.RSA_MD5 );
    }


    /**
     * Create a KdcRequest, suitable for requesting a service Ticket.
     */
    protected KdcRequest getKdcRequest( Ticket tgt, RequestBody requestBody, ChecksumType checksumType )
        throws Exception
    {
        // Get the session key from the service ticket.
        sessionKey = tgt.getEncTicketPart().getSessionKey();

        // Generate a new sequence number.
        sequenceNumber = random.nextInt();
        now = new KerberosTime();

        EncryptedData authenticator = getAuthenticator( tgt.getEncTicketPart().getClientPrincipal(), requestBody, checksumType );

        PaData[] paData = getPreAuthenticationData( tgt, authenticator );

        return new KdcRequest( 5, KerberosMessageType.TGS_REQ, paData, requestBody );
    }


    /**
     * Build the authenticator.  The authenticator communicates the sub-session key the
     * service will use to unlock the private message.  The service will unlock the
     * authenticator with the session key from the ticket.  The authenticator client
     * principal must equal the principal in the ticket.  
     *
     * @param clientPrincipal
     * @return The {@link EncryptedData} containing the {@link Authenticator}.
     * @throws KerberosException
     */
    protected EncryptedData getAuthenticator( KerberosPrincipal clientPrincipal, RequestBody requestBody,
        ChecksumType checksumType ) throws IOException, KerberosException
    {
        AuthenticatorModifier authenticatorModifier = new AuthenticatorModifier();

        clientMicroSeconds = random.nextInt();

        authenticatorModifier.setVersionNumber( 5 );
        authenticatorModifier.setClientPrincipal( clientPrincipal );
        authenticatorModifier.setClientTime( now );
        authenticatorModifier.setClientMicroSecond( clientMicroSeconds );
        authenticatorModifier.setSubSessionKey( subSessionKey );
        authenticatorModifier.setSequenceNumber( sequenceNumber );

        Checksum checksum = getBodyChecksum( requestBody, checksumType );
        authenticatorModifier.setChecksum( checksum );

        Authenticator authenticator = authenticatorModifier.getAuthenticator();

        EncryptedData encryptedAuthenticator = lockBox.seal( sessionKey, authenticator, KeyUsage.NUMBER7 );

        return encryptedAuthenticator;
    }


    protected Checksum getBodyChecksum( RequestBody requestBody, ChecksumType checksumType ) throws IOException,
        KerberosException
    {
        KdcRequestEncoder bodyEncoder = new KdcRequestEncoder();
        byte[] bodyBytes = bodyEncoder.encodeRequestBody( requestBody );

        ChecksumHandler checksumHandler = new ChecksumHandler();
        return checksumHandler.calculateChecksum( checksumType, bodyBytes, null, KeyUsage.NUMBER8 );
    }


    /**
     * Make new AP_REQ, aka the "auth header," and package it into pre-authentication data.
     *
     * @param ticket
     * @param authenticator
     * @return
     * @throws IOException
     */
    protected PaData[] getPreAuthenticationData( Ticket ticket, EncryptedData authenticator )
        throws IOException
    {
        ApplicationRequest applicationRequest = new ApplicationRequest();
        applicationRequest.setMessageType( KerberosMessageType.AP_REQ );
        applicationRequest.setProtocolVersionNumber( 5 );
        applicationRequest.setApOptions( new ApOptions() );
        applicationRequest.setTicket( ticket );
        applicationRequest.setEncPart( authenticator );

        ApplicationRequestEncoder encoder = new ApplicationRequestEncoder();
        byte[] encodedApReq = encoder.encode( applicationRequest );

        PaData[] paData = new PaData[1];

        PaData preAuth = new PaData();
        preAuth.setPaDataType( PaDataType.PA_TGS_REQ );
        preAuth.setPaDataValue( encodedApReq );

        paData[0] = preAuth;

        return paData;
    }


    protected PrincipalName getPrincipalName( String name )
    {
        PrincipalName principalName = new PrincipalName();
        principalName.addName( name );
        principalName.setNameType( PrincipalNameType.KRB_NT_PRINCIPAL );

        return principalName;
    }
}
