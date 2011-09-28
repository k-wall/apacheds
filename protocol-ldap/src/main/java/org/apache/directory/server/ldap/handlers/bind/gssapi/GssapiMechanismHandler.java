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
package org.apache.directory.server.ldap.handlers.bind.gssapi;


import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslServer;

import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.kerberos.shared.crypto.encryption.EncryptionType;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptionKey;
import org.apache.directory.server.kerberos.shared.store.PrincipalStoreEntry;
import org.apache.directory.server.kerberos.shared.store.operations.GetPrincipal;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.ldap.LdapSession;
import org.apache.directory.server.ldap.handlers.bind.AbstractMechanismHandler;
import org.apache.directory.server.ldap.handlers.bind.SaslConstants;
import org.apache.directory.server.protocol.shared.ServiceConfigurationException;
import org.apache.directory.shared.ldap.constants.SupportedSaslMechanisms;
import org.apache.directory.shared.ldap.message.internal.InternalBindRequest;
import org.apache.directory.shared.ldap.name.DN;


/**
 * The GSSAPI Sasl mechanism handler.
 *
 * @org.apache.xbean.XBean
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class GssapiMechanismHandler extends AbstractMechanismHandler
{
    public SaslServer handleMechanism( LdapSession ldapSession, InternalBindRequest bindRequest ) throws Exception
    {
        SaslServer ss = ( SaslServer ) ldapSession.getSaslProperty( SaslConstants.SASL_SERVER );

        if ( ss == null )
        {
            //Subject subject = ( Subject ) ldapSession.getIoSession().getAttribute( "saslSubject" );

            Subject subject = getSubject( ldapSession.getLdapServer() );
            final String saslHost = ( String ) ldapSession.getSaslProperty( SaslConstants.SASL_HOST );
            final Map<String, String> saslProps = ( Map<String, String> ) ldapSession
                .getSaslProperty( SaslConstants.SASL_PROPS );

            CoreSession adminSession = ldapSession.getLdapServer().getDirectoryService().getAdminSession();

            final CallbackHandler callbackHandler = new GssapiCallbackHandler( ldapSession, adminSession, bindRequest );

            ss = ( SaslServer ) Subject.doAs( subject, new PrivilegedExceptionAction<SaslServer>()
            {
                public SaslServer run() throws Exception
                {
                    return Sasl.createSaslServer( SupportedSaslMechanisms.GSSAPI, SaslConstants.LDAP_PROTOCOL,
                        saslHost, saslProps, callbackHandler );
                }
            } );

            ldapSession.putSaslProperty( SaslConstants.SASL_SERVER, ss );
        }

        return ss;
    }


    /**
     * {@inheritDoc}
     */
    public void init( LdapSession ldapSession )
    {
        // Store the host in the ldap session
        String saslHost = ldapSession.getLdapServer().getSaslHost();
        ldapSession.putSaslProperty( SaslConstants.SASL_HOST, saslHost );

        Map<String, String> saslProps = new HashMap<String, String>();
        saslProps.put( Sasl.QOP, ldapSession.getLdapServer().getSaslQopString() );
        //saslProps.put( "com.sun.security.sasl.digest.realm", getActiveRealms( ldapSession.getLdapServer() ) );
        ldapSession.putSaslProperty( SaslConstants.SASL_PROPS, saslProps );
    }


    /**
     * Remove the Host, UserBaseDn, props and Mechanism property.
     * 
     * @param ldapSession the Ldapsession instance
     */
    public void cleanup( LdapSession ldapSession )
    {
        // Inject the Sasl Filter
        insertSaslFilter( ldapSession );

        // and remove the useless informations
        ldapSession.removeSaslProperty( SaslConstants.SASL_HOST );
        ldapSession.removeSaslProperty( SaslConstants.SASL_USER_BASE_DN );
        ldapSession.removeSaslProperty( SaslConstants.SASL_MECH );
        ldapSession.removeSaslProperty( SaslConstants.SASL_PROPS );
        ldapSession.removeSaslProperty( SaslConstants.SASL_AUTHENT_USER );
    }


    private Subject getSubject( LdapServer ldapServer ) throws Exception
    {
        String servicePrincipalName = ldapServer.getSaslPrincipal();
        KerberosPrincipal servicePrincipal = new KerberosPrincipal( servicePrincipalName );
        GetPrincipal getPrincipal = new GetPrincipal( servicePrincipal );

        PrincipalStoreEntry entry = null;

        try
        {
            entry = findPrincipal( ldapServer, getPrincipal );
        }
        catch ( ServiceConfigurationException sce )
        {
            String message = I18n.err( I18n.ERR_659, servicePrincipalName, ldapServer.getSearchBaseDn() );
            throw new ServiceConfigurationException( message, sce );
        }

        if ( entry == null )
        {
            String message = I18n.err( I18n.ERR_659, servicePrincipalName, ldapServer.getSearchBaseDn() );
            throw new ServiceConfigurationException( message );
        }

        Subject subject = new Subject();

        for ( EncryptionType encryptionType : entry.getKeyMap().keySet() )
        {
            EncryptionKey key = entry.getKeyMap().get( encryptionType );

            byte[] keyBytes = key.getKeyValue();
            int type = key.getKeyType().getOrdinal();
            int kvno = key.getKeyVersion();

            KerberosKey serviceKey = new KerberosKey( servicePrincipal, keyBytes, type, kvno );

            subject.getPrivateCredentials().add( serviceKey );
        }

        return subject;
    }


    private PrincipalStoreEntry findPrincipal( LdapServer ldapServer, GetPrincipal getPrincipal ) throws Exception
    {
        CoreSession adminSession = ldapServer.getDirectoryService().getAdminSession();
        return ( PrincipalStoreEntry ) getPrincipal.execute( adminSession, new DN( ldapServer.getSearchBaseDn() ) );
    }
}
