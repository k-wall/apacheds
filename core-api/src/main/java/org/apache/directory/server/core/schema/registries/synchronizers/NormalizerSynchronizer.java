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
package org.apache.directory.server.core.schema.registries.synchronizers;


import java.util.ArrayList;
import java.util.List;

import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.constants.MetaSchemaConstants;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.LdapInvalidDnException;
import org.apache.directory.shared.ldap.exception.LdapSchemaViolationException;
import org.apache.directory.shared.ldap.exception.LdapUnwillingToPerformException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.name.RDN;
import org.apache.directory.shared.ldap.schema.Normalizer;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.registries.Schema;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class NormalizerSynchronizer extends AbstractRegistrySynchronizer
{
    /** A logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger( NormalizerSynchronizer.class );


    /**
     * Creates a new instance of NormalizerSynchronizer.
     *
     * @param registries The global registries
     * @throws Exception If the initialization failed
     */
    public NormalizerSynchronizer( SchemaManager schemaManager ) throws Exception
    {
        super( schemaManager );
    }


    /**
     * {@inheritDoc}
     */
    public boolean modify( ModifyOperationContext opContext, ServerEntry targetEntry, boolean cascade )
        throws Exception
    {
        DN name = opContext.getDn();
        ServerEntry entry = opContext.getEntry();
        String schemaName = getSchemaName( name );
        String oldOid = getOid( entry );
        Normalizer normalizer = factory.getNormalizer( schemaManager, targetEntry, schemaManager.getRegistries(),
            schemaName );

        if ( isSchemaEnabled( schemaName ) )
        {
            normalizer.setSchemaName( schemaName );

            schemaManager.unregisterNormalizer( oldOid );
            schemaManager.add( normalizer );

            return SCHEMA_MODIFIED;
        }

        return SCHEMA_UNCHANGED;
    }


    /**
     * {@inheritDoc}
     */
    public void add( ServerEntry entry ) throws Exception
    {
        DN dn = entry.getDn();
        DN parentDn = ( DN ) dn.clone();
        parentDn.remove( parentDn.size() - 1 );

        // The parent DN must be ou=normalizers,cn=<schemaName>,ou=schema
        checkParent( parentDn, schemaManager, SchemaConstants.NORMALIZER );

        // The new schemaObject's OID must not already exist
        checkOidIsUniqueForNormalizer( entry );

        // Build the new Normalizer from the given entry
        String schemaName = getSchemaName( dn );

        Normalizer normalizer = factory.getNormalizer( schemaManager, entry, schemaManager.getRegistries(), schemaName );

        // At this point, the constructed Normalizer has not been checked against the 
        // existing Registries. It will be checked there, if the schema and the 
        // Normalizer are both enabled.
        Schema schema = schemaManager.getLoadedSchema( schemaName );
        List<Throwable> errors = new ArrayList<Throwable>();

        if ( schema.isEnabled() && normalizer.isEnabled() )
        {
            if ( schemaManager.add( normalizer ) )
            {
                LOG.debug( "Added {} into the enabled schema {}", dn.getName(), schemaName );
            }
            else
            {
                String msg = I18n.err( I18n.ERR_364, entry.getDn().getName(), 
                    StringTools.listToString( errors ) );
                LOG.info( msg );
            throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, msg );
            }
        }
        else
        {
            // At least, we associates the Normalizer with the schema
            schemaManager.getRegistries().associateWithSchema( errors, normalizer );

            if ( !errors.isEmpty() )
            {
                String msg = I18n.err( I18n.ERR_365, entry.getDn().getName(),
                    StringTools.listToString( errors ) );

                throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, msg );
            }

            LOG.debug( "The normalizer {} cannot be added in schema {}", dn.getName(), schemaName );
        }
    }


    /**
     * {@inheritDoc}
     */
    public void delete( ServerEntry entry, boolean cascade ) throws Exception
    {
        DN dn = entry.getDn();
        DN parentDn = ( DN ) dn.clone();
        parentDn.remove( parentDn.size() - 1 );

        // The parent DN must be ou=normalizers,cn=<schemaName>,ou=schema
        checkParent( parentDn, schemaManager, SchemaConstants.NORMALIZER );

        // Get the Normalizer from the given entry ( it has been grabbed from the server earlier)
        String schemaName = getSchemaName( entry.getDn() );
        Normalizer normalizer = factory.getNormalizer( schemaManager, entry, schemaManager.getRegistries(), schemaName );

        String oid = normalizer.getOid();

        if ( isSchemaEnabled( schemaName ) )
        {
            if ( schemaManager.getRegistries().isReferenced( normalizer ) )
            {
                String msg = I18n.err( I18n.ERR_366, entry.getDn().getName(), getReferenced( normalizer ) );
                LOG.warn( msg );
                throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, msg );
            }

            // As the normalizer has the same OID than its attached MR, it won't
            // be loaded into the schemaManager if it's disabled
            deleteFromSchema( normalizer, schemaName );
        }

        if ( schemaManager.getNormalizerRegistry().contains( oid ) )
        {
            schemaManager.unregisterNormalizer( oid );
            LOG.debug( "Removed {} from the enabled schema {}", normalizer, schemaName );
        }
        else
        {
            LOG.debug( "Removed {} from the enabled schema {}", normalizer, schemaName );
        }
    }


    /**
     * {@inheritDoc}
     */
    public void rename( ServerEntry entry, RDN newRdn, boolean cascade ) throws Exception
    {
        String oldOid = getOid( entry );
        String schemaName = getSchemaName( entry.getDn() );

        if ( schemaManager.getMatchingRuleRegistry().contains( oldOid ) )
        {
            throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM,
                I18n.err( I18n.ERR_367, oldOid ) );
        }

        String newOid = ( String ) newRdn.getNormValue();
        checkOidIsUniqueForNormalizer( newOid );

        if ( isSchemaEnabled( schemaName ) )
        {
            // Inject the new OID
            ServerEntry targetEntry = ( ServerEntry ) entry.clone();
            targetEntry.put( MetaSchemaConstants.M_OID_AT, newOid );

            // Inject the new DN
            DN newDn = new DN( targetEntry.getDn() );
            newDn.remove( newDn.size() - 1 );
            newDn.add( newRdn );
            targetEntry.setDn( newDn );

            Normalizer normalizer = factory.getNormalizer( schemaManager, targetEntry, schemaManager.getRegistries(),
                schemaName );
            schemaManager.unregisterNormalizer( oldOid );
            schemaManager.add( normalizer );
        }
    }


    public void moveAndRename( DN oriChildName, DN newParentName, RDN newRdn, boolean deleteOldRn,
        ServerEntry entry, boolean cascade ) throws Exception
    {
        checkNewParent( newParentName );
        String oldOid = getOid( entry );
        String oldSchemaName = getSchemaName( oriChildName );
        String newSchemaName = getSchemaName( newParentName );

        if ( schemaManager.getMatchingRuleRegistry().contains( oldOid ) )
        {
            throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM,
                I18n.err( I18n.ERR_367, oldOid ) );
        }

        String oid = ( String ) newRdn.getNormValue();
        checkOidIsUniqueForNormalizer( oid );
        Normalizer normalizer = factory.getNormalizer( schemaManager, entry, schemaManager.getRegistries(),
            newSchemaName );

        if ( isSchemaEnabled( oldSchemaName ) )
        {
            schemaManager.unregisterNormalizer( oldOid );
        }

        if ( isSchemaEnabled( newSchemaName ) )
        {
            schemaManager.add( normalizer );
        }
    }


    public void move( DN oriChildName, DN newParentName, ServerEntry entry, boolean cascade ) throws Exception
    {
        checkNewParent( newParentName );
        String oid = getOid( entry );
        String oldSchemaName = getSchemaName( oriChildName );
        String newSchemaName = getSchemaName( newParentName );

        if ( schemaManager.getMatchingRuleRegistry().contains( oid ) )
        {
            throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM,
                I18n.err( I18n.ERR_368, oid ) );
        }

        Normalizer normalizer = factory.getNormalizer( schemaManager, entry, schemaManager.getRegistries(),
            newSchemaName );

        if ( isSchemaEnabled( oldSchemaName ) )
        {
            schemaManager.unregisterNormalizer( oid );
        }

        if ( isSchemaEnabled( newSchemaName ) )
        {
            schemaManager.add( normalizer );
        }
    }


    private void checkOidIsUniqueForNormalizer( String oid ) throws Exception
    {
        if ( schemaManager.getNormalizerRegistry().contains( oid ) )
        {
            throw new LdapSchemaViolationException( ResultCodeEnum.OTHER,
                I18n.err( I18n.ERR_369, oid ) );
        }
    }


    private void checkOidIsUniqueForNormalizer( ServerEntry entry ) throws Exception
    {
        String oid = getOid( entry );

        if ( schemaManager.getNormalizerRegistry().contains( oid ) )
        {
            throw new LdapSchemaViolationException( ResultCodeEnum.OTHER,
                I18n.err( I18n.ERR_369, oid ) );
        }
    }


    private void checkNewParent( DN newParent ) throws LdapException
    {
        if ( newParent.size() != 3 )
        {
            throw new LdapInvalidDnException( ResultCodeEnum.NAMING_VIOLATION, I18n.err( I18n.ERR_370 ) );
        }

        RDN rdn = newParent.getRdn();

        if ( !schemaManager.getAttributeTypeRegistry().getOidByName( rdn.getNormType() ).equals(
            SchemaConstants.OU_AT_OID ) )
        {
            throw new LdapInvalidDnException( ResultCodeEnum.NAMING_VIOLATION, I18n.err( I18n.ERR_371 ) );
        }

        if ( !( ( String ) rdn.getNormValue() ).equalsIgnoreCase( SchemaConstants.NORMALIZERS_AT ) )
        {
            throw new LdapInvalidDnException( ResultCodeEnum.NAMING_VIOLATION, I18n.err( I18n.ERR_372 ) );
        }
    }
}
