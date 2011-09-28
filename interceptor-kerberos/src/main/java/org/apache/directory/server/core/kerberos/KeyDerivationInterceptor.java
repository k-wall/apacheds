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
package org.apache.directory.server.core.kerberos;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingException;

import org.apache.directory.server.core.authn.AuthenticationInterceptor;
import org.apache.directory.server.core.authz.AciAuthorizationInterceptor;
import org.apache.directory.server.core.authz.DefaultAuthorizationInterceptor;
import org.apache.directory.server.core.collective.CollectiveAttributeInterceptor;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.event.EventInterceptor;
import org.apache.directory.server.core.exception.ExceptionInterceptor;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.Interceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.normalization.NormalizationInterceptor;
import org.apache.directory.server.core.operational.OperationalAttributeInterceptor;
import org.apache.directory.server.core.referral.ReferralInterceptor;
import org.apache.directory.server.core.schema.SchemaInterceptor;
import org.apache.directory.server.core.subtree.SubentryInterceptor;
import org.apache.directory.server.core.trigger.TriggerInterceptor;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.kerberos.shared.crypto.encryption.EncryptionType;
import org.apache.directory.server.kerberos.shared.crypto.encryption.KerberosKeyFactory;
import org.apache.directory.server.kerberos.shared.crypto.encryption.RandomKeyFactory;
import org.apache.directory.server.kerberos.shared.exceptions.KerberosException;
import org.apache.directory.server.kerberos.shared.io.encoder.EncryptionKeyEncoder;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptionKey;
import org.apache.directory.server.kerberos.shared.store.KerberosAttribute;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.BinaryValue;
import org.apache.directory.shared.ldap.entry.StringValue;
import org.apache.directory.shared.ldap.entry.DefaultServerAttribute;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.entry.ServerModification;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapAuthenticationException;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * An {@link Interceptor} that creates symmetric Kerberos keys for users.  When a
 * 'userPassword' is added or modified, the 'userPassword' and 'krb5PrincipalName'
 * are used to derive Kerberos keys.  If the 'userPassword' is the special keyword
 * 'randomKey', a random key is generated and used as the Kerberos key.
 * 
 * @org.apache.xbean.XBean
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class KeyDerivationInterceptor extends BaseInterceptor
{
    /** The log for this class. */
    private static final Logger log = LoggerFactory.getLogger( KeyDerivationInterceptor.class );

    /** The service name. */
    public static final String NAME = "keyDerivationService";

    /**
     * Define the interceptors to bypass upon user lookup.
     */
    private static final Collection<String> USERLOOKUP_BYPASS;
    static
    {
        Set<String> c = new HashSet<String>();
        c.add( NormalizationInterceptor.class.getName() );
        c.add( AuthenticationInterceptor.class.getName() );
        c.add( ReferralInterceptor.class.getName() );
        c.add( AciAuthorizationInterceptor.class.getName() );
        c.add( DefaultAuthorizationInterceptor.class.getName() );
        c.add( ExceptionInterceptor.class.getName() );
        c.add( OperationalAttributeInterceptor.class.getName() );
        c.add( SchemaInterceptor.class.getName() );
        c.add( SubentryInterceptor.class.getName() );
        c.add( CollectiveAttributeInterceptor.class.getName() );
        c.add( EventInterceptor.class.getName() );
        c.add( TriggerInterceptor.class.getName() );
        USERLOOKUP_BYPASS = Collections.unmodifiableCollection( c );
    }


    /**
     * Intercept the addition of the 'userPassword' and 'krb5PrincipalName' attributes.  Use the 'userPassword'
     * and 'krb5PrincipalName' attributes to derive Kerberos keys for the principal.  If the 'userPassword' is
     * the special keyword 'randomKey', set random keys for the principal.  Set the key version number (kvno)
     * to '0'.
     */
    public void add( NextInterceptor next, AddOperationContext addContext ) throws Exception
    {
        DN normName = addContext.getDn();

        ServerEntry entry = addContext.getEntry();

        if ( ( entry.get( SchemaConstants.USER_PASSWORD_AT ) != null ) && 
            ( entry.get( KerberosAttribute.KRB5_PRINCIPAL_NAME_AT ) != null ) )
        {
            log.debug( "Adding the entry '{}' for DN '{}'.", entry, normName.getName() );

            BinaryValue userPassword = (BinaryValue)entry.get( SchemaConstants.USER_PASSWORD_AT ).get();
            String strUserPassword = userPassword.getString();

            if ( log.isDebugEnabled() )
            {
                StringBuffer sb = new StringBuffer();
                sb.append( "'" + strUserPassword + "' ( " );
                sb.append( userPassword );
                sb.append( " )" );
                log.debug( "Adding Attribute id : 'userPassword',  Values : [ {} ]", sb.toString() );
            }

            Value<?> principalNameValue = entry.get( KerberosAttribute.KRB5_PRINCIPAL_NAME_AT ).get();
            
            String principalName = principalNameValue.getString();

            log.debug( "Got principal '{}' with userPassword '{}'.", principalName, strUserPassword );

            Map<EncryptionType, EncryptionKey> keys = generateKeys( principalName, strUserPassword );

            entry.put( KerberosAttribute.KRB5_PRINCIPAL_NAME_AT, principalName );
            entry.put( KerberosAttribute.KRB5_KEY_VERSION_NUMBER_AT, "0" );

            entry.put( getKeyAttribute( addContext.getSession().getDirectoryService().getSchemaManager(), keys ) );

            log.debug( "Adding modified entry '{}' for DN '{}'.", entry, normName
                .getName() );
        }

        next.add( addContext );
    }


    /**
     * Intercept the modification of the 'userPassword' attribute.  Perform a lookup to check for an
     * existing principal name and key version number (kvno).  If a 'krb5PrincipalName' is not in
     * the modify request, attempt to use an existing 'krb5PrincipalName' attribute.  If a kvno
     * exists, increment the kvno; otherwise, set the kvno to '0'.
     * 
     * If both a 'userPassword' and 'krb5PrincipalName' can be found, use the 'userPassword' and
     * 'krb5PrincipalName' attributes to derive Kerberos keys for the principal.
     * 
     * If the 'userPassword' is the special keyword 'randomKey', set random keys for the principal.
     */
    public void modify( NextInterceptor next, ModifyOperationContext modContext ) throws Exception
    {
        ModifySubContext subContext = new ModifySubContext();

        detectPasswordModification( modContext, subContext );

        if ( subContext.getUserPassword() != null )
        {
            lookupPrincipalAttributes( modContext, subContext );
        }

        if ( subContext.isPrincipal() && subContext.hasValues() )
        {
            deriveKeys( modContext, subContext );
        }

        next.modify( modContext );
    }


    /**
     * Detect password modification by checking the modify request for the 'userPassword'.  Additionally,
     * check to see if a 'krb5PrincipalName' was provided.
     *
     * @param modContext
     * @param subContext
     * @throws NamingException
     */
    void detectPasswordModification( ModifyOperationContext modContext, ModifySubContext subContext )
        throws Exception
    {
        List<Modification> mods = modContext.getModItems();

        String operation = null;

        // Loop over attributes being modified to pick out 'userPassword' and 'krb5PrincipalName'.
        for ( Modification mod:mods )
        {
            if ( log.isDebugEnabled() )
            {
                switch ( mod.getOperation() )
                {
                    case ADD_ATTRIBUTE:
                        operation = "Adding";
                        break;
                        
                    case REMOVE_ATTRIBUTE:
                        operation = "Removing";
                        break;
                        
                    case REPLACE_ATTRIBUTE:
                        operation = "Replacing";
                        break;
                }
            }

            EntryAttribute attr = mod.getAttribute();

            if ( attr.instanceOf( SchemaConstants.USER_PASSWORD_AT ) )
            {
                Object firstValue = attr.get();
                String password = null;

                if ( firstValue instanceof StringValue )
                {
                    password = ((StringValue)firstValue).getString();
                    log.debug( "{} Attribute id : 'userPassword',  Values : [ '{}' ]", operation, password );
                }
                else if ( firstValue instanceof BinaryValue )
                {
                    password = ((BinaryValue)firstValue).getString();

                    if ( log.isDebugEnabled() )
                    {
                        StringBuffer sb = new StringBuffer();
                        sb.append( "'" + password + "' ( " );
                        sb.append( StringTools.dumpBytes( ((BinaryValue)firstValue).getBytes() ).trim() );
                        sb.append( " )" );
                        log.debug( "{} Attribute id : 'userPassword',  Values : [ {} ]", operation, sb.toString() );
                    }
                }

                subContext.setUserPassword( password );
                log.debug( "Got userPassword '{}'.", subContext.getUserPassword() );
            }

            if ( attr.instanceOf( KerberosAttribute.KRB5_PRINCIPAL_NAME_AT ) )
            {
                subContext.setPrincipalName( attr.getString() );
                log.debug( "Got principal '{}'.", subContext.getPrincipalName() );
            }
        }
    }


    /**
     * Lookup the principal's attributes that are relevant to executing key derivation.
     *
     * @param modContext
     * @param subContext
     * @throws NamingException
     */
    void lookupPrincipalAttributes( ModifyOperationContext modContext, ModifySubContext subContext )
        throws Exception
    {
        DN principalDn = modContext.getDn();

        LookupOperationContext lookupContext = modContext.newLookupContext( principalDn );
        lookupContext.setByPassed( USERLOOKUP_BYPASS );
        lookupContext.setAttrsId( new String[] 
        { 
            SchemaConstants.OBJECT_CLASS_AT, 
            KerberosAttribute.KRB5_PRINCIPAL_NAME_AT, 
            KerberosAttribute.KRB5_KEY_VERSION_NUMBER_AT 
        } );
        
        ClonedServerEntry userEntry = modContext.lookup( lookupContext );

        if ( userEntry == null )
        {
            throw new LdapAuthenticationException( I18n.err( I18n.ERR_512, principalDn ) );
        }

        EntryAttribute objectClass = userEntry.getOriginalEntry().get( SchemaConstants.OBJECT_CLASS_AT );
        
        if ( !objectClass.contains( SchemaConstants.KRB5_PRINCIPAL_OC ) )
        {
            return;
        }
        else
        {
            subContext.isPrincipal( true );
            log.debug( "DN {} is a Kerberos principal.  Will attempt key derivation.", principalDn.getName() );
        }

        if ( subContext.getPrincipalName() == null )
        {
            EntryAttribute principalAttribute = userEntry.getOriginalEntry().get( KerberosAttribute.KRB5_PRINCIPAL_NAME_AT );
            String principalName = principalAttribute.getString();
            subContext.setPrincipalName( principalName );
            log.debug( "Found principal '{}' from lookup.", principalName );
        }

        EntryAttribute keyVersionNumberAttr = userEntry.getOriginalEntry().get( KerberosAttribute.KRB5_KEY_VERSION_NUMBER_AT );

        if ( keyVersionNumberAttr == null )
        {
            subContext.setNewKeyVersionNumber( 0 );
            log.debug( "Key version number was null, setting to 0." );
        }
        else
        {
            int oldKeyVersionNumber = Integer.valueOf( keyVersionNumberAttr.getString() );
            int newKeyVersionNumber = oldKeyVersionNumber + 1;
            subContext.setNewKeyVersionNumber( newKeyVersionNumber );
            log.debug( "Found key version number '{}', setting to '{}'.", oldKeyVersionNumber, newKeyVersionNumber );
        }
    }


    /**
     * Use the 'userPassword' and 'krb5PrincipalName' attributes to derive Kerberos keys for the principal.
     * 
     * If the 'userPassword' is the special keyword 'randomKey', set random keys for the principal.
     *
     * @param modContext
     * @param subContext
     */
    void deriveKeys( ModifyOperationContext modContext, ModifySubContext subContext ) throws Exception
    {
        List<Modification> mods = modContext.getModItems();

        String principalName = subContext.getPrincipalName();
        String userPassword = subContext.getUserPassword();
        int kvno = subContext.getNewKeyVersionNumber();

        log.debug( "Got principal '{}' with userPassword '{}'.", principalName, userPassword );

        Map<EncryptionType, EncryptionKey> keys = generateKeys( principalName, userPassword );

        List<Modification> newModsList = new ArrayList<Modification>();

        // Make sure we preserve any other modification items.
        for ( Modification mod:mods )
        {
            newModsList.add( mod );
        }
        
        SchemaManager schemaManager = modContext.getSession()
            .getDirectoryService().getSchemaManager();

        // Add our modification items.
        newModsList.add( 
            new ServerModification( 
                ModificationOperation.REPLACE_ATTRIBUTE, 
                new DefaultServerAttribute(
                    KerberosAttribute.KRB5_PRINCIPAL_NAME_AT, 
                    schemaManager.lookupAttributeTypeRegistry( KerberosAttribute.KRB5_PRINCIPAL_NAME_AT ),
                    principalName ) ) );
        newModsList.add( 
            new ServerModification( 
                ModificationOperation.REPLACE_ATTRIBUTE, 
                new DefaultServerAttribute(
                    KerberosAttribute.KRB5_KEY_VERSION_NUMBER_AT, 
                    schemaManager.lookupAttributeTypeRegistry( KerberosAttribute.KRB5_KEY_VERSION_NUMBER_AT ),
                    Integer.toString( kvno ) ) ) );
        
        EntryAttribute attribute = getKeyAttribute( modContext.getSession()
            .getDirectoryService().getSchemaManager(), keys );
        newModsList.add( new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, attribute ) );

        modContext.setModItems( newModsList );
    }


    private EntryAttribute getKeyAttribute( SchemaManager schemaManager, Map<EncryptionType, EncryptionKey> keys ) throws Exception
    {
        EntryAttribute keyAttribute = 
            new DefaultServerAttribute( KerberosAttribute.KRB5_KEY_AT, 
                schemaManager.lookupAttributeTypeRegistry( KerberosAttribute.KRB5_KEY_AT ) );

        Iterator<EncryptionKey> it = keys.values().iterator();

        while ( it.hasNext() )
        {
            try
            {
                keyAttribute.add( EncryptionKeyEncoder.encode( it.next() ) );
            }
            catch ( IOException ioe )
            {
                log.error( I18n.err( I18n.ERR_122 ), ioe );
            }
        }

        return keyAttribute;
    }


    private Map<EncryptionType, EncryptionKey> generateKeys( String principalName, String userPassword )
    {
        if ( userPassword.equalsIgnoreCase( "randomKey" ) )
        {
            // Generate random key.
            try
            {
                return RandomKeyFactory.getRandomKeys();
            }
            catch ( KerberosException ke )
            {
                log.debug( ke.getLocalizedMessage(), ke );
                return null;
            }
        }
        else
        {
            // Derive key based on password and principal name.
            return KerberosKeyFactory.getKerberosKeys( principalName, userPassword );
        }
    }

    class ModifySubContext
    {
        private boolean isPrincipal = false;
        private String principalName;
        private String userPassword;
        private int newKeyVersionNumber = -1;


        boolean isPrincipal()
        {
            return isPrincipal;
        }


        void isPrincipal( boolean isPrincipal )
        {
            this.isPrincipal = isPrincipal;
        }


        String getPrincipalName()
        {
            return principalName;
        }


        void setPrincipalName( String principalName )
        {
            this.principalName = principalName;
        }


        String getUserPassword()
        {
            return userPassword;
        }


        void setUserPassword( String userPassword )
        {
            this.userPassword = userPassword;
        }


        int getNewKeyVersionNumber()
        {
            return newKeyVersionNumber;
        }


        void setNewKeyVersionNumber( int newKeyVersionNumber )
        {
            this.newKeyVersionNumber = newKeyVersionNumber;
        }


        boolean hasValues()
        {
            return userPassword != null && principalName != null && newKeyVersionNumber > -1;
        }
    }
}
