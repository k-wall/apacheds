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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.OperationManager;
import org.apache.directory.server.core.entry.ServerEntryUtils;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.partition.ByPassConstants;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.constants.MetaSchemaConstants;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.DefaultServerAttribute;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.entry.ServerModification;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.LdapInvalidDnException;
import org.apache.directory.shared.ldap.exception.LdapUnwillingToPerformException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.name.RDN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.SchemaObject;
import org.apache.directory.shared.ldap.schema.SchemaObjectType;
import org.apache.directory.shared.ldap.schema.SchemaObjectWrapper;
import org.apache.directory.shared.ldap.schema.loader.ldif.SchemaEntityFactory;
import org.apache.directory.shared.ldap.schema.registries.AttributeTypeRegistry;
import org.apache.directory.shared.ldap.schema.registries.DefaultSchemaObjectRegistry;
import org.apache.directory.shared.ldap.schema.registries.Registries;
import org.apache.directory.shared.ldap.schema.registries.Schema;
import org.apache.directory.shared.ldap.util.DateUtils;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class handle modifications made on a global schema. Modifications made
 * on SchemaObjects are handled by the specific shcemaObject synchronizers.
 * 
 * @TODO poorly implemented - revisit the SchemaChangeHandler for this puppy
 * and do it right.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class SchemaSynchronizer implements RegistrySynchronizer
{
    /** A logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger( SchemaSynchronizer.class );

    private final SchemaEntityFactory factory;
    //private final PartitionSchemaLoader loader;
    
    private final SchemaManager schemaManager;
    
    /** The global registries */
    private final Registries registries;
    
    /** The m-disable AttributeType */
    private final AttributeType disabledAT;
    
    /** The CN attributeType */
    private final AttributeType cnAT;
    
    /** The m-dependencies AttributeType */
    private final AttributeType dependenciesAT;
    
    /** The modifiersName AttributeType */
    private final AttributeType modifiersNameAT;
    
    /** The modifyTimestamp AttributeType */
    private final AttributeType modifyTimestampAT;
    
    /** A static DN referencing ou=schema */
    private final DN ouSchemaDN;

    /**
     * Creates and initializes a new instance of Schema synchronizer
     *
     * @param registries The Registries
     * @param loader The schema loader
     * @throws Exception If something went wrong
     */
    public SchemaSynchronizer( SchemaManager schemaManager ) throws Exception
    {
        this.registries = schemaManager.getRegistries();
        this.schemaManager = schemaManager;
        disabledAT = registries.getAttributeTypeRegistry().lookup( MetaSchemaConstants.M_DISABLED_AT );
        factory = new SchemaEntityFactory();
        cnAT = registries.getAttributeTypeRegistry().lookup( SchemaConstants.CN_AT );
        dependenciesAT = registries.getAttributeTypeRegistry()
            .lookup( MetaSchemaConstants.M_DEPENDENCIES_AT );
        modifiersNameAT = registries.getAttributeTypeRegistry().lookup( SchemaConstants.MODIFIERS_NAME_AT );
        modifyTimestampAT = registries.getAttributeTypeRegistry().lookup( SchemaConstants.MODIFY_TIMESTAMP_AT );
        
        ouSchemaDN = new DN( SchemaConstants.OU_SCHEMA );
        ouSchemaDN.normalize( registries.getAttributeTypeRegistry().getNormalizerMapping() );
    }


    /**
     * The only modification done on a schema element is on the m-disabled 
     * attributeType
     * 
     * Depending in the existence of this attribute in the previous entry, we will
     * have to update the entry or not.
     */
    public boolean modify( ModifyOperationContext opContext, ServerEntry targetEntry, boolean cascade ) throws Exception
    {
        ServerEntry entry = opContext.getEntry();
        List<Modification> mods = opContext.getModItems(); 
        boolean hasModification = SCHEMA_UNCHANGED;
        
        // Check if the entry has a m-disabled attribute 
        EntryAttribute disabledInEntry = entry.get( disabledAT );
        Modification disabledModification = ServerEntryUtils.getModificationItem( mods, disabledAT );
        
        // The attribute might be present, but that does not mean we will change it.
        // If it's absent, and if we have it in the previous entry, that mean we want
        // to enable the schema
        if ( disabledModification != null )
        {
            // We are trying to modify the m-disabled attribute. 
            ModificationOperation modification = disabledModification.getOperation();
            EntryAttribute attribute = disabledModification.getAttribute();
            
            hasModification = modifyDisable( opContext, modification, attribute, disabledInEntry );
        }
        else if ( disabledInEntry != null )
        {
            hasModification = modifyDisable( opContext, ModificationOperation.REMOVE_ATTRIBUTE, null, disabledInEntry );
        }
            
        
        return hasModification;
    }


    public void moveAndRename( DN oriChildName, DN newParentName, RDN newRn, boolean deleteOldRn, ServerEntry entry, boolean cascaded ) throws LdapException
    {

    }


    /**
     * Handles the addition of a metaSchema object to the schema partition.
     * 
     * @param name the dn of the new metaSchema object
     * @param entry the attributes of the new metaSchema object
     */
    public void add( ServerEntry entry ) throws Exception
    {
        DN dn = entry.getDn();
        DN parentDn = ( DN ) dn.clone();
        parentDn.remove( parentDn.size() - 1 );
        parentDn.normalize( registries.getAttributeTypeRegistry().getNormalizerMapping() );

        if ( !parentDn.equals( ouSchemaDN ) )
        {
            throw new LdapInvalidDnException( ResultCodeEnum.NAMING_VIOLATION, I18n.err( I18n.ERR_380, ouSchemaDN.getName(),
                    parentDn.getNormName() ) );
        }

        // check if the new schema is enabled or disabled
        boolean isEnabled = false;
        EntryAttribute disabled = entry.get( disabledAT );
        
        if ( disabled == null )
        {
            // If the attribute is absent, then the schema is enabled by default
            isEnabled = true;
        }
        else if ( ! disabled.contains( "TRUE" ) )
        {
            isEnabled = true;
        }
        
        // check to see that all dependencies are resolved and loaded if this
        // schema is enabled, otherwise check that the dependency schemas exist
        checkForDependencies( isEnabled, entry );
        
        /*
         * There's a slight problem that may result when adding a metaSchema
         * object if the addition of the physical entry fails.  If the schema
         * is enabled when added in the condition tested below, that schema
         * is added to the global registries.  We need to add this so subsequent
         * schema entity additions are loaded into the registries as they are
         * added to the schema partition.  However if the metaSchema object 
         * addition fails then we're left with this schema object looking like
         * it is enabled in the registries object's schema hash.  The effects
         * of this are unpredictable.
         * 
         * This whole problem is due to the inability of these handlers to 
         * react to a failed operation.  To fix this we would need some way
         * for these handlers to respond to failed operations and revert their
         * effects on the registries.
         * 
         * TODO: might want to add a set of failedOnXXX methods to the adapter
         * where on failure the schema service calls the schema manager and it
         * calls the appropriate methods on the respective handler.  This way
         * the schema manager can rollback registry changes when LDAP operations
         * fail.
         */

        if ( isEnabled )
        {
            Schema schema = factory.getSchema( entry );
            schemaManager.load( schema );
        }
    }


    /**
     * Called to react to the deletion of a metaSchema object.  This method
     * simply removes the schema from the loaded schema map of the global 
     * registries.  
     * 
     * @param name the dn of the metaSchema object being deleted
     * @param entry the attributes of the metaSchema object 
     */
    public void delete( ServerEntry entry, boolean cascade ) throws Exception
    {
        EntryAttribute cn = entry.get( cnAT );
        String schemaName = cn.getString();

        // Before allowing a schema object to be deleted we must check
        // to make sure it's not depended upon by another schema
        Set<String> dependents = schemaManager.listDependentSchemaNames( schemaName );
        
        if ( ( dependents != null ) && ! dependents.isEmpty() )
        {
            String msg = I18n.err( I18n.ERR_381, dependents ); 
            LOG.warn( msg );
            throw new LdapUnwillingToPerformException(
                ResultCodeEnum.UNWILLING_TO_PERFORM,
                msg );
        }
        
        // no need to check if schema is enabled or disabled here
        // if not in the loaded set there will be no negative effect
        schemaManager.unload( schemaName );
    }



    /**
     * Responds to the rdn (commonName) of the metaSchema object being 
     * changed.  Changes all the schema entities associated with the 
     * renamed schema so they now map to a new schema name.
     * 
     * @param name the dn of the metaSchema object renamed
     * @param entry the entry of the metaSchema object before the rename
     * @param newRdn the new commonName of the metaSchema object
     */
    public void rename( ServerEntry entry, RDN newRdn, boolean cascade ) throws Exception
    {
        String rdnAttribute = newRdn.getUpType();
        String rdnAttributeOid = registries.getAttributeTypeRegistry().getOidByName( rdnAttribute );

        if ( ! rdnAttributeOid.equals( cnAT.getOid() ) )
        {
            throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM,
                I18n.err( I18n.ERR_382, rdnAttribute ) );
        }

        /*
         * This operation has to do the following:
         * 
         * [1] check and make sure there are no dependent schemas on the 
         *     one being renamed - if so an exception should result
         *      
         * [2] make non-schema object registries modify the mapping 
         *     for their entities: non-schema object registries contain
         *     objects that are not SchemaObjects and hence do not carry
         *     their schema within the object as a property
         *     
         * [3] make schema object registries do the same but the way
         *     they do them will be different since these objects will
         *     need to be replaced or will require a setter for the 
         *     schema name
         */
        
        // step [1]
        /*
        String schemaName = getSchemaName( entry.getDn() );
        Set<String> dependents = schemaManager.listDependentSchemaNames( schemaName );
        if ( ! dependents.isEmpty() )
        {
            throw new LdapUnwillingToPerformException( 
                "Cannot allow a rename on " + schemaName + " schema while it has depentents.",
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }

        // check if the new schema is enabled or disabled
        boolean isEnabled = false;
        EntryAttribute disabled = entry.get( disabledAT );
        
        if ( disabled == null )
        {
            isEnabled = true;
        }
        else if ( ! disabled.get().equals( "TRUE" ) )
        {
            isEnabled = true;
        }

        if ( ! isEnabled )
        {
            return;
        }

        // do steps 2 and 3 if the schema has been enabled and is loaded
        
        // step [2] 
        String newSchemaName = ( String ) newRdn.getUpValue();
        registries.getComparatorRegistry().renameSchema( schemaName, newSchemaName );
        registries.getNormalizerRegistry().renameSchema( schemaName, newSchemaName );
        registries.getSyntaxCheckerRegistry().renameSchema( schemaName, newSchemaName );
        
        // step [3]
        renameSchema( registries.getAttributeTypeRegistry(), schemaName, newSchemaName );
        renameSchema( registries.getDitContentRuleRegistry(), schemaName, newSchemaName );
        renameSchema( registries.getDitStructureRuleRegistry(), schemaName, newSchemaName );
        renameSchema( registries.getMatchingRuleRegistry(), schemaName, newSchemaName );
        renameSchema( registries.getMatchingRuleUseRegistry(), schemaName, newSchemaName );
        renameSchema( registries.getNameFormRegistry(), schemaName, newSchemaName );
        renameSchema( registries.getObjectClassRegistry(), schemaName, newSchemaName );
        renameSchema( registries.getLdapSyntaxRegistry(), schemaName, newSchemaName );
        */
    }
    

    /**
     * Moves are not allowed for metaSchema objects so this always throws an
     * UNWILLING_TO_PERFORM LdapException.
     */
    public void moveAndRename( DN oriChildName, DN newParentName, String newRn, boolean deleteOldRn, 
        ServerEntry entry, boolean cascade ) throws LdapUnwillingToPerformException
    {
        throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM,
            I18n.err( I18n.ERR_383 ) );
    }


    /**
     * Moves are not allowed for metaSchema objects so this always throws an
     * UNWILLING_TO_PERFORM LdapException.
     */
    public void move( DN oriChildName, DN newParentName, 
        ServerEntry entry, boolean cascade ) throws LdapUnwillingToPerformException
    {
        throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM,
            I18n.err( I18n.ERR_383 ) );
    }

    
    // -----------------------------------------------------------------------
    // private utility methods
    // -----------------------------------------------------------------------

    
    /**
     * Modify the Disable flag (the flag can be set to true or false).
     * 
     * We can ADD, REMOVE or MODIFY this flag. The following matrix expose what will be the consequences
     * of this operation, depending on the current state
     * 
     * <pre>
     *                 +-------------------+--------------------+--------------------+
     *     op/state    |       TRUE        |       FALSE        |       ABSENT       |
     * +-------+-------+----------------------------------------+--------------------+
     * | ADD   | TRUE  | do nothing        | do nothing         | disable the schema |
     * |       +-------+-------------------+--------------------+--------------------+
     * |       | FALSE | do nothing        | do nothing         | do nothing         |
     * +-------+-------+-------------------+--------------------+--------------------+
     * |REMOVE | N/A   | enable the schema | do nothing         | do nothing         |
     * +-------+-------+-------------------+--------------------+--------------------+
     * |MODIFY | TRUE  | do nothing        | disable the schema | disable the schema |
     * |       +-------+-------------------+--------------------+--------------------+
     * |       | FALSE | enable the schema | do nothing         |  do nothing        |
     * +-------+-------+-------------------+--------------------+--------------------+
     * </pre>
     */
    private boolean modifyDisable( ModifyOperationContext opContext, ModificationOperation modOp, 
        EntryAttribute disabledInMods, EntryAttribute disabledInEntry ) throws Exception
    {
        DN name = opContext.getDn();
        
        switch ( modOp )
        {
            /*
             * If the user is adding a new m-disabled attribute to an enabled schema, 
             * we check that the value is "TRUE" and disable that schema if so.
             */
            case ADD_ATTRIBUTE :
                if ( disabledInEntry == null )
                {
                    if ( "TRUE".equalsIgnoreCase( disabledInMods.getString() ) )
                    {
                        return disableSchema( opContext.getSession(), getSchemaName( name ) );
                    }
                }
                
                break;

            /*
             * If the user is removing the m-disabled attribute we check if the schema is currently 
             * disabled.  If so we enable the schema.
             */
            case REMOVE_ATTRIBUTE :
                if ( ( disabledInEntry != null ) && ( "TRUE".equalsIgnoreCase( disabledInEntry.getString() ) ) )
                {
                    return enableSchema( getSchemaName( name ) );
                }
                
                break;

            /*
             * If the user is replacing the m-disabled attribute we check if the schema is 
             * currently disabled and enable it if the new state has it as enabled.  If the
             * schema is not disabled we disable it if the mods set m-disabled to true.
             */
            case REPLACE_ATTRIBUTE :
                
                boolean isCurrentlyDisabled = false;
                
                if ( disabledInEntry != null )
                {
                    isCurrentlyDisabled = "TRUE".equalsIgnoreCase( disabledInEntry.getString() );
                }
                
                boolean isNewStateDisabled = false;
               
                if ( disabledInMods != null )
                {
                    Value<?> val = disabledInMods.get();
                    
                    if ( val == null )
                    {
                        isNewStateDisabled = false;
                    }
                    else
                    {
                        isNewStateDisabled = "TRUE".equalsIgnoreCase( val.getString() );
                    }
                }

                if ( isCurrentlyDisabled && !isNewStateDisabled )
                {
                    return enableSchema( getSchemaName( name ) );
                }

                if ( !isCurrentlyDisabled && isNewStateDisabled )
                {
                    return disableSchema( opContext.getSession(), getSchemaName( name ) );
                }
                
                break;
                
            default:
                throw new IllegalArgumentException( I18n.err( I18n.ERR_384, modOp ) );
        }
        
        return SCHEMA_UNCHANGED;
    }


    private String getSchemaName( DN schema )
    {
        return ( String ) schema.getRdn().getNormValue();
    }

    
    /**
     * Build the DN to access a schemaObject path for a specific schema 
     */
    private DN buildDn( SchemaObjectType schemaObjectType, String schemaName ) throws LdapInvalidDnException
    {
        
        DN path = new DN( 
            SchemaConstants.OU_SCHEMA,
            "cn=" + schemaName,
            schemaObjectType.getRdn()
            );
        
        return path;
    }
    
    
    /**
     * Disable a schema and update all of its schemaObject 
     */
    private void disable( SchemaObject schemaObject, CoreSession session, Registries registries )
        throws Exception
    {
        Schema schema = registries.getLoadedSchema( schemaObject.getSchemaName() );
        List<Modification> modifications = new ArrayList<Modification>();
        
        // The m-disabled AT
        EntryAttribute disabledAttr = new DefaultServerAttribute( disabledAT, "FALSE" );
        Modification disabledMod = new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, disabledAttr );
        
        modifications.add( disabledMod );
        
        // The modifiersName AT
        EntryAttribute modifiersNameAttr = 
            new DefaultServerAttribute( modifiersNameAT, session.getEffectivePrincipal().getName() );
        Modification modifiersNameMod = new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, modifiersNameAttr );
        
        modifications.add( modifiersNameMod );
        
        // The modifyTimestamp AT
        EntryAttribute modifyTimestampAttr = 
            new DefaultServerAttribute( modifyTimestampAT, DateUtils.getGeneralizedTime() );
        Modification modifyTimestampMod = new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, modifyTimestampAttr );
        
        modifications.add( modifyTimestampMod );
        
        // Call the modify operation
        DN dn = buildDn( schemaObject.getObjectType(), schemaObject.getName() );
        
        ModifyOperationContext modifyContext = new ModifyOperationContext( session, dn, modifications );
        modifyContext.setByPassed( ByPassConstants.BYPASS_ALL_COLLECTION );

        OperationManager operationManager = 
            session.getDirectoryService().getOperationManager();
        
        operationManager.modify( modifyContext );
        
        // Now iterate on all the schemaObject under this schema
        for ( SchemaObjectWrapper schemaObjectWrapper : schema.getContent() )
        {
            
        }
    }

    private boolean disableSchema( CoreSession session, String schemaName ) throws Exception
    {
        Schema schema = registries.getLoadedSchema( schemaName );

        if ( schema == null )
        {
            // This is not possible. We can't enable a schema which is not loaded.
            String msg = I18n.err( I18n.ERR_85, schemaName );
            LOG.error( msg );
            throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, msg );
        }
        
        return schemaManager.disable( schemaName );

        /*
        // First check that the schema is not already disabled
        Map<String, Schema> schemas = registries.getLoadedSchemas();
        
        Schema schema = schemas.get( schemaName );
        
        if ( ( schema == null ) || schema.isDisabled() )
        {
            // The schema is disabled, do nothing
            return SCHEMA_UNCHANGED;
        }
        
        Set<String> dependents = schemaManager.listEnabledDependentSchemaNames( schemaName );
        
        if ( ! dependents.isEmpty() )
        {
            throw new LdapUnwillingToPerformException(
                "Cannot disable schema with enabled dependents: " + dependents,
                ResultCodeEnum.UNWILLING_TO_PERFORM );
        }
        
        schema.disable();
        
        // Use brute force right now : iterate through all the schemaObjects
        // searching for those associated with the disabled schema
        disableAT( session, schemaName );
        
        Set<SchemaObjectWrapper> content = registries.getLoadedSchema( schemaName ).getContent(); 

        for ( SchemaObjectWrapper schemaWrapper : content )
        {
            SchemaObject schemaObject = schemaWrapper.get();
            
            System.out.println( "Disabling " + schemaObject.getName() );
        }
        
        return SCHEMA_MODIFIED;
        */
    }
    
    
    private void disableAT( CoreSession session, String schemaName )
    {
        AttributeTypeRegistry atRegistry = registries.getAttributeTypeRegistry();
        
        for ( AttributeType attributeType : atRegistry )
        {
            if ( schemaName.equalsIgnoreCase( attributeType.getSchemaName() ) )
            {
                if ( attributeType.isDisabled() )
                {
                    continue;
                }
                
                EntryAttribute disable = new DefaultServerAttribute( disabledAT, "TRUE"  );
                Modification modification = 
                    new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, disable );
                
                //session.modify( dn, mods, ignoreReferral, log )
            }
        }
    }


    /**
     * Enabling a schema consist on switching all of its schema element to enable.
     * We have to do it on a temporary registries.
     */
    private boolean enableSchema( String schemaName ) throws Exception
    {
        Schema schema = registries.getLoadedSchema( schemaName );

        if ( schema == null )
        {
            // We have to load the schema before enabling it.
            schemaManager.loadDisabled( schemaName );
        }
        
        return schemaManager.enable( schemaName );
    }


    /**
     * Checks to make sure the dependencies either exist for disabled metaSchemas,
     * or exist and are loaded (enabled) for enabled metaSchemas.
     * 
     * @param isEnabled whether or not the new metaSchema is enabled
     * @param entry the Attributes for the new metaSchema object
     * @throws NamingException if the dependencies do not resolve or are not
     * loaded (enabled)
     */
    private void checkForDependencies( boolean isEnabled, ServerEntry entry ) throws Exception
    {
        EntryAttribute dependencies = entry.get( this.dependenciesAT );

        if ( dependencies == null )
        {
            return;
        }
        
        if ( isEnabled )
        {
            // check to make sure all the dependencies are also enabled
            Map<String,Schema> loaded = registries.getLoadedSchemas();
            
            for ( Value<?> value:dependencies )
            {
                String dependency = value.getString();
                
                if ( ! loaded.containsKey( dependency ) )
                {
                    throw new LdapUnwillingToPerformException( 
                        ResultCodeEnum.UNWILLING_TO_PERFORM, "Unwilling to perform operation on enabled schema with disabled or missing dependencies: " 
                        + dependency );
                }
            }
        }
        else
        {
            for ( Value<?> value:dependencies )
            {
                String dependency = value.getString();
                
                if ( schemaManager.getLoadedSchema( StringTools.toLowerCase( dependency ) ) == null )
                {
                    throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, 
                        I18n.err( I18n.ERR_385, dependency ) );
                }
            }
        }
    }

    
    /**
     * Used to iterate through SchemaObjects in a DefaultSchemaObjectRegistry and rename
     * their schema property to a new schema name.
     * 
     * @param registry the registry whose objects are changed
     * @param originalSchemaName the original schema name
     * @param newSchemaName the new schema name
     */
    private void renameSchema( DefaultSchemaObjectRegistry<? extends SchemaObject> registry, String originalSchemaName, String newSchemaName ) 
    {
        Iterator<? extends SchemaObject> list = registry.iterator();
        while ( list.hasNext() )
        {
            SchemaObject obj = list.next();
            if ( obj.getSchemaName().equalsIgnoreCase( originalSchemaName ) )
            {
                obj.setSchemaName( newSchemaName );
            }
        }
    }
}
