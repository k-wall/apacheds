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


import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.constants.MetaSchemaConstants;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.LdapInvalidDnException;
import org.apache.directory.shared.ldap.exception.LdapUnwillingToPerformException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.name.RDN;
import org.apache.directory.shared.ldap.schema.ObjectClass;
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
public class ObjectClassSynchronizer extends AbstractRegistrySynchronizer
{
    /** A logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger( ObjectClassSynchronizer.class );


    /**
     * Creates a new instance of ObjectClassSynchronizer.
     *
     * @param schemaManager The global schemaManager
     * @throws Exception If the initialization failed
     */
    public ObjectClassSynchronizer( SchemaManager schemaManager ) throws Exception
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
        String oid = getOid( entry );
        ObjectClass oc = factory.getObjectClass( schemaManager, targetEntry, schemaManager.getRegistries(),
            getSchemaName( name ) );
        String schemaName = getSchemaName( entry.getDn() );

        if ( isSchemaEnabled( schemaName ) )
        {
            schemaManager.unregisterObjectClass( oid );
            schemaManager.add( oc );

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

        // The parent DN must be ou=objectclasses,cn=<schemaName>,ou=schema
        checkParent( parentDn, schemaManager, SchemaConstants.OBJECT_CLASS );

        // The new schemaObject's OID must not already exist
        checkOidIsUnique( entry );

        // Build the new ObjectClass from the given entry
        String schemaName = getSchemaName( dn );

        ObjectClass objectClass = factory.getObjectClass( schemaManager, entry, schemaManager.getRegistries(),
            schemaName );

        // At this point, the constructed ObjectClass has not been checked against the 
        // existing Registries. It may be broken (missing SUP, or such), it will be checked
        // there, if the schema and the ObjectClass are both enabled.
        Schema schema = schemaManager.getLoadedSchema( schemaName );

        if ( schema.isEnabled() && objectClass.isEnabled() )
        {
            if ( schemaManager.add( objectClass ) )
            {
                LOG.debug( "Added {} into the enabled schema {}", dn.getName(), schemaName );
            }
            else
            {
                // We have some error : reject the addition and get out
                String msg = I18n.err( I18n.ERR_373, entry.getDn().getName(), 
                    StringTools.listToString( schemaManager.getErrors() ) );
                LOG.info( msg );
                throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, msg );
            }

        }
        else
        {
            LOG.debug( "The ObjectClass {} cannot be added in the disabled schema {}.", objectClass, schemaName );
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

        // The parent DN must be ou=objectclasses,cn=<schemaName>,ou=schema
        checkParent( parentDn, schemaManager, SchemaConstants.OBJECT_CLASS );

        // Get the ObjectClass from the given entry ( it has been grabbed from the server earlier)
        String schemaName = getSchemaName( entry.getDn() );
        
        // Get the schema 
        Schema schema = schemaManager.getLoadedSchema( schemaName );

        if ( schema.isDisabled() )
        {
            // The schema is disabled, nothing to do.
            LOG.debug( "The ObjectClass {} cannot be removed from the disabled schema {}.", 
                dn.getName(), schemaName );
            
            return;
        }
        
        // Test that the Oid exists
        ObjectClass objectClass = ( ObjectClass ) checkOidExists( entry );

        if ( schema.isEnabled() && objectClass.isEnabled() )
        {
            if ( schemaManager.delete( objectClass ) )
            {
                LOG.debug( "Removed {} from the schema {}", objectClass, schemaName );
            }
            else
            {
                // We have some error : reject the deletion and get out
                String msg = I18n.err( I18n.ERR_374, entry.getDn().getName(),
                    StringTools.listToString( schemaManager.getErrors() ) );
                LOG.info( msg );
                throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, msg );
            }
        }
        else
        {
            LOG.debug( "Removed {} from the disabled schema {}", objectClass, schemaName );
        }
    }


    /**
     * {@inheritDoc}
     */
    public void rename( ServerEntry entry, RDN newRdn, boolean cascade ) throws Exception
    {
        String schemaName = getSchemaName( entry.getDn() );
        ObjectClass oldOc = factory.getObjectClass( schemaManager, entry, schemaManager.getRegistries(), schemaName );

        // Dependency constraints are not managed by this class
        //        Set<ServerEntry> dependees = dao.listObjectClassDependents( oldOc );
        //        
        //        if ( dependees != null && dependees.size() > 0 )
        //        {
        //            throw new LdapUnwillingToPerformException( "The objectClass with OID " + oldOc.getOid()
        //                + " cannot be deleted until all entities" 
        //                + " using this objectClass have also been deleted.  The following dependees exist: " 
        //                + getOids( dependees ), 
        //                ResultCodeEnum.UNWILLING_TO_PERFORM );
        //        }

        ServerEntry targetEntry = ( ServerEntry ) entry.clone();
        String newOid = ( String ) newRdn.getNormValue();
        targetEntry.put( MetaSchemaConstants.M_OID_AT, newOid );

        // Inject the new DN
        DN newDn = new DN( targetEntry.getDn() );
        newDn.remove( newDn.size() - 1 );
        newDn.add( newRdn );

        checkOidIsUnique( newOid );
        ObjectClass oc = factory.getObjectClass( schemaManager, targetEntry, schemaManager.getRegistries(), schemaName );

        if ( isSchemaEnabled( schemaName ) )
        {
            // Check that the entry has no descendant
            if ( schemaManager.getObjectClassRegistry().hasDescendants( oldOc.getOid() ) )
            {
                String msg = I18n.err( I18n.ERR_375, entry.getDn().getName(), newDn );

                throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, msg );
            }

            schemaManager.unregisterObjectClass( oldOc.getOid() );
            schemaManager.add( oc );
        }
        else
        {
            unregisterOids( oldOc );
            registerOids( oc );
        }
    }


    public void moveAndRename( DN oriChildName, DN newParentName, RDN newRdn, boolean deleteOldRn,
        ServerEntry entry, boolean cascade ) throws Exception
    {
        checkNewParent( newParentName );
        String oldSchemaName = getSchemaName( oriChildName );
        ObjectClass oldOc = factory.getObjectClass( schemaManager, entry, schemaManager.getRegistries(), oldSchemaName );

        // this class does not handle dependencies
        //        Set<ServerEntry> dependees = dao.listObjectClassDependents( oldOc );
        //        if ( dependees != null && dependees.size() > 0 )
        //        {
        //            throw new LdapUnwillingToPerformException( "The objectClass with OID " + oldOc.getOid()
        //                + " cannot be deleted until all entities" 
        //                + " using this objectClass have also been deleted.  The following dependees exist: " 
        //                + getOids( dependees ), 
        //                ResultCodeEnum.UNWILLING_TO_PERFORM );
        //        }

        String newSchemaName = getSchemaName( newParentName );
        ServerEntry targetEntry = ( ServerEntry ) entry.clone();
        String newOid = ( String ) newRdn.getNormValue();
        checkOidIsUnique( newOid );
        targetEntry.put( MetaSchemaConstants.M_OID_AT, newOid );
        ObjectClass oc = factory.getObjectClass( schemaManager, targetEntry, schemaManager.getRegistries(),
            newSchemaName );

        if ( isSchemaEnabled( oldSchemaName ) )
        {
            schemaManager.unregisterObjectClass( oldOc.getOid() );
        }
        else
        {
            unregisterOids( oldOc );
        }

        if ( isSchemaEnabled( newSchemaName ) )
        {
            schemaManager.add( oc );
        }
        else
        {
            registerOids( oc );
        }
    }


    public void move( DN oriChildName, DN newParentName, ServerEntry entry, boolean cascade ) throws Exception
    {
        checkNewParent( newParentName );
        String oldSchemaName = getSchemaName( oriChildName );
        String newSchemaName = getSchemaName( newParentName );
        ObjectClass oldAt = factory.getObjectClass( schemaManager, entry, schemaManager.getRegistries(), oldSchemaName );

        // dependencies are not managed by this class
        //        Set<ServerEntry> dependees = dao.listObjectClassDependents( oldAt );
        //        if ( dependees != null && dependees.size() > 0 )
        //        {s
        //            throw new LdapUnwillingToPerformException( "The objectClass with OID " + oldAt.getOid() 
        //                + " cannot be deleted until all entities" 
        //                + " using this objectClass have also been deleted.  The following dependees exist: " 
        //                + getOids( dependees ), 
        //                ResultCodeEnum.UNWILLING_TO_PERFORM );
        //        }

        ObjectClass oc = factory.getObjectClass( schemaManager, entry, schemaManager.getRegistries(), newSchemaName );

        if ( isSchemaEnabled( oldSchemaName ) )
        {
            schemaManager.unregisterObjectClass( oldAt.getOid() );
        }
        else
        {
            unregisterOids( oldAt );
        }

        if ( isSchemaEnabled( newSchemaName ) )
        {
            schemaManager.add( oc );
        }
        else
        {
            registerOids( oc );
        }
    }


    private void checkNewParent( DN newParent ) throws LdapException
    {
        if ( newParent.size() != 3 )
        {
            throw new LdapInvalidDnException(
                ResultCodeEnum.NAMING_VIOLATION,
                "The parent dn of a objectClass should be at most 3 name components in length." );
        }

        RDN rdn = newParent.getRdn();

        if ( !schemaManager.getAttributeTypeRegistry().getOidByName( rdn.getNormType() ).equals(
            SchemaConstants.OU_AT_OID ) )
        {
            throw new LdapInvalidDnException( ResultCodeEnum.NAMING_VIOLATION,
                I18n.err( I18n.ERR_376 ) );
        }

        if ( !( ( String ) rdn.getNormValue() ).equalsIgnoreCase( SchemaConstants.OBJECT_CLASSES_AT ) )
        {
            throw new LdapInvalidDnException( ResultCodeEnum.NAMING_VIOLATION,
                I18n.err( I18n.ERR_377 ) );
        }
    }
}
