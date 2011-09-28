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


import javax.naming.Context;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.sasl.AuthorizeCallback;

import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.LdapPrincipal;
import org.apache.directory.server.kerberos.shared.store.PrincipalStoreEntry;
import org.apache.directory.server.kerberos.shared.store.operations.GetPrincipal;
import org.apache.directory.server.ldap.LdapSession;
import org.apache.directory.server.ldap.handlers.bind.AbstractSaslCallbackHandler;
import org.apache.directory.server.ldap.handlers.bind.SaslConstants;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.message.internal.InternalBindRequest;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class GssapiCallbackHandler extends AbstractSaslCallbackHandler
{
    private static final Logger LOG = LoggerFactory.getLogger( GssapiCallbackHandler.class );


    /**
     * Creates a new instance of GssapiCallbackHandler.
     *
     * @param session the mina IO session
     * @param bindRequest the bind message
     * @param directoryService the directory service core
     */
    public GssapiCallbackHandler( LdapSession ldapSession, CoreSession adminSession, InternalBindRequest bindRequest )
    {
        super( adminSession.getDirectoryService(), bindRequest );
        this.ldapSession = ldapSession;
        this.adminSession = adminSession;
    }


    protected EntryAttribute lookupPassword( String username, String password )
    {
        // do nothing, password not used by GSSAPI
        return null;
    }


    protected void authorize( AuthorizeCallback authorizeCB ) throws Exception
    {
        LOG.debug( "Processing conversion of principal name to DN." );

        String username = authorizeCB.getAuthorizationID();

        // find the user's entry
        GetPrincipal getPrincipal = new GetPrincipal( new KerberosPrincipal( username ) );
        PrincipalStoreEntry entry = ( PrincipalStoreEntry ) getPrincipal.execute( adminSession, new DN( ldapSession
            .getLdapServer().getSearchBaseDn() ) );
        String bindDn = entry.getDistinguishedName();

        LOG.debug( "Converted username {} to DN {}.", username, bindDn );

        LdapPrincipal ldapPrincipal = new LdapPrincipal( new DN( entry.getDistinguishedName() ),
            AuthenticationLevel.STRONG, StringTools.EMPTY_BYTES );
        ldapSession.putSaslProperty( SaslConstants.SASL_AUTHENT_USER, ldapPrincipal );
        ldapSession.putSaslProperty( Context.SECURITY_PRINCIPAL, bindDn );

        authorizeCB.setAuthorizedID( bindDn );
        authorizeCB.setAuthorized( true );
    }
}
