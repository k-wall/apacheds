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
package org.apache.directory.server.ldap.handlers.bind.digestMD5;


import java.util.HashSet;
import java.util.Set;

import javax.naming.Context;
import javax.security.sasl.AuthorizeCallback;

import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.LdapPrincipal;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.ldap.LdapSession;
import org.apache.directory.server.ldap.handlers.bind.AbstractSaslCallbackHandler;
import org.apache.directory.server.ldap.handlers.bind.SaslConstants;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.FilterParser;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.message.internal.InternalBindRequest;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.AttributeTypeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class DigestMd5CallbackHandler extends AbstractSaslCallbackHandler
{
    private static final Logger LOG = LoggerFactory.getLogger( DigestMd5CallbackHandler.class );

    private String bindDn;
    private String userPassword;


    /**
     * Creates a new instance of DigestMd5CallbackHandler.
     *
     * @param session the mina IoSession
     * @param bindRequest the bind message
     * @param directoryService the directory service core
     */
    public DigestMd5CallbackHandler( LdapSession ldapSession, CoreSession adminSession, InternalBindRequest bindRequest )
    {
        super( adminSession.getDirectoryService(), bindRequest );
        this.ldapSession = ldapSession;
        this.adminSession = adminSession;
    }


    // TODO - should return not be a byte[]
    protected EntryAttribute lookupPassword( String username, String realm )
    {
        try
        {
            ExprNode filter = FilterParser.parse( "(uid=" + username + ")" );
            Set<AttributeTypeOptions> returningAttributes = new HashSet<AttributeTypeOptions>();
            
            AttributeType passwordAT = adminSession.getDirectoryService().getSchemaManager().lookupAttributeTypeRegistry( SchemaConstants.USER_PASSWORD_AT );
            returningAttributes.add( new AttributeTypeOptions( passwordAT) );
            bindDn = (String)ldapSession.getSaslProperty( SaslConstants.SASL_USER_BASE_DN );
            
            DN baseDn = new DN( bindDn );

            EntryFilteringCursor cursor = adminSession.search( 
                baseDn, 
                SearchScope.SUBTREE, 
                filter, 
                AliasDerefMode.DEREF_ALWAYS, 
                returningAttributes );
            
            cursor.beforeFirst();
            
            ClonedServerEntry entry = null;
            
            while ( cursor.next() )
            {
                entry = cursor.get();
                LdapPrincipal ldapPrincipal = new LdapPrincipal( 
                    entry.getDn(), 
                    AuthenticationLevel.STRONG, 
                    entry.get( SchemaConstants.USER_PASSWORD_AT ).getBytes() );
                ldapSession.putSaslProperty( SaslConstants.SASL_AUTHENT_USER, ldapPrincipal );
            }

            return entry.get( passwordAT );
        }
        catch ( Exception e )
        {
            return null;
        }
    }


    protected void authorize( AuthorizeCallback authorizeCB )
    {
        if ( LOG.isDebugEnabled() )
        {
            LOG.debug( "Converted username " + getUsername() + " to DN " + bindDn + " with password " + userPassword + "." );
        }

        ldapSession.putSaslProperty( Context.SECURITY_PRINCIPAL, bindDn );

        authorizeCB.setAuthorizedID( bindDn );
        authorizeCB.setAuthorized( true );
    }
}
