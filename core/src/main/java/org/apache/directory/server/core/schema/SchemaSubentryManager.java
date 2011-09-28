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
package org.apache.directory.server.core.schema;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.naming.directory.DirContext;

import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.exception.LdapUnwillingToPerformException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.DITContentRule;
import org.apache.directory.shared.ldap.schema.DITStructureRule;
import org.apache.directory.shared.ldap.schema.LdapSyntax;
import org.apache.directory.shared.ldap.schema.MatchingRule;
import org.apache.directory.shared.ldap.schema.MatchingRuleUse;
import org.apache.directory.shared.ldap.schema.NameForm;
import org.apache.directory.shared.ldap.schema.ObjectClass;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.parsers.LdapComparatorDescription;
import org.apache.directory.shared.ldap.schema.parsers.NormalizerDescription;
import org.apache.directory.shared.ldap.schema.parsers.SyntaxCheckerDescription;
import org.apache.directory.shared.ldap.schema.registries.SchemaLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class SchemaSubentryManager
{
    /** A logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger( SchemaSubentryManager.class );

    // indices of handlers and object ids into arrays
    private static final int COMPARATOR_INDEX = 0;
    private static final int NORMALIZER_INDEX = 1;
    private static final int SYNTAX_CHECKER_INDEX = 2;
    private static final int SYNTAX_INDEX = 3;
    private static final int MATCHING_RULE_INDEX = 4;
    private static final int ATTRIBUTE_TYPE_INDEX = 5;
    private static final int OBJECT_CLASS_INDEX = 6;
    private static final int MATCHING_RULE_USE_INDEX = 7;
    private static final int DIT_STRUCTURE_RULE_INDEX = 8;
    private static final int DIT_CONTENT_RULE_INDEX = 9;
    private static final int NAME_FORM_INDEX = 10;

    private static final Set<String> VALID_OU_VALUES = new HashSet<String>();

    /** The schemaManager */
    private final SchemaManager schemaManager;
    
    private final SchemaSubentryModifier subentryModifier;
    
    /** The description parsers */
    private final DescriptionParsers parsers;
    
    /** 
     * Maps the OID of a subschemaSubentry operational attribute to the index of 
     * the handler in the schemaObjectHandlers array.
     */ 
    private final Map<String, Integer> opAttr2handlerIndex = new HashMap<String, Integer>( 11 );
    private static final String CASCADING_ERROR =
            "Cascading has not yet been implemented: standard operation is in effect.";

    static 
    {
        VALID_OU_VALUES.add( SchemaConstants.NORMALIZERS_AT.toLowerCase() );
        VALID_OU_VALUES.add( SchemaConstants.COMPARATORS_AT.toLowerCase() );
        VALID_OU_VALUES.add( SchemaConstants.SYNTAX_CHECKERS_AT.toLowerCase() );
        VALID_OU_VALUES.add( "syntaxes".toLowerCase() );
        VALID_OU_VALUES.add( SchemaConstants.MATCHING_RULES_AT.toLowerCase() );
        VALID_OU_VALUES.add( SchemaConstants.MATCHING_RULE_USE_AT.toLowerCase() );
        VALID_OU_VALUES.add( SchemaConstants.ATTRIBUTE_TYPES_AT.toLowerCase() );
        VALID_OU_VALUES.add( SchemaConstants.OBJECT_CLASSES_AT.toLowerCase() );
        VALID_OU_VALUES.add( SchemaConstants.NAME_FORMS_AT.toLowerCase() );
        VALID_OU_VALUES.add( SchemaConstants.DIT_CONTENT_RULES_AT.toLowerCase() );
        VALID_OU_VALUES.add( SchemaConstants.DIT_STRUCTURE_RULES_AT.toLowerCase() );
    }


    public SchemaSubentryManager( SchemaManager schemaManager, SchemaLoader loader )
        throws Exception
    {
        this.schemaManager = schemaManager;
        this.subentryModifier = new SchemaSubentryModifier( schemaManager );
        this.parsers = new DescriptionParsers( schemaManager );
        
        String comparatorsOid = schemaManager.getAttributeTypeRegistry().getOidByName( SchemaConstants.COMPARATORS_AT );
        opAttr2handlerIndex.put( comparatorsOid, COMPARATOR_INDEX );

        String normalizersOid = schemaManager.getAttributeTypeRegistry().getOidByName( SchemaConstants.NORMALIZERS_AT );
        opAttr2handlerIndex.put( normalizersOid, NORMALIZER_INDEX );

        String syntaxCheckersOid = schemaManager.getAttributeTypeRegistry().getOidByName( SchemaConstants.SYNTAX_CHECKERS_AT );
        opAttr2handlerIndex.put( syntaxCheckersOid, SYNTAX_CHECKER_INDEX );

        String ldapSyntaxesOid = schemaManager.getAttributeTypeRegistry().getOidByName( SchemaConstants.LDAP_SYNTAXES_AT );
        opAttr2handlerIndex.put( ldapSyntaxesOid, SYNTAX_INDEX );

        String matchingRulesOid = schemaManager.getAttributeTypeRegistry().getOidByName( SchemaConstants.MATCHING_RULES_AT );
        opAttr2handlerIndex.put( matchingRulesOid, MATCHING_RULE_INDEX );

        String attributeTypesOid = schemaManager.getAttributeTypeRegistry().getOidByName( SchemaConstants.ATTRIBUTE_TYPES_AT );
        opAttr2handlerIndex.put( attributeTypesOid, ATTRIBUTE_TYPE_INDEX );

        String objectClassesOid = schemaManager.getAttributeTypeRegistry().getOidByName( SchemaConstants.OBJECT_CLASSES_AT );
        opAttr2handlerIndex.put( objectClassesOid, OBJECT_CLASS_INDEX );

        String matchingRuleUseOid = schemaManager.getAttributeTypeRegistry().getOidByName( SchemaConstants.MATCHING_RULE_USE_AT );
        opAttr2handlerIndex.put( matchingRuleUseOid, MATCHING_RULE_USE_INDEX );

        String ditStructureRulesOid = schemaManager.getAttributeTypeRegistry().getOidByName( SchemaConstants.DIT_STRUCTURE_RULES_AT );
        opAttr2handlerIndex.put( ditStructureRulesOid, DIT_STRUCTURE_RULE_INDEX );

        String ditContentRulesOid = schemaManager.getAttributeTypeRegistry().getOidByName( SchemaConstants.DIT_CONTENT_RULES_AT );
        opAttr2handlerIndex.put( ditContentRulesOid, DIT_CONTENT_RULE_INDEX );

        String nameFormsOid = schemaManager.getAttributeTypeRegistry().getOidByName( SchemaConstants.NAME_FORMS_AT );
        opAttr2handlerIndex.put( nameFormsOid, NAME_FORM_INDEX );
    }

    
    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaChangeManager#modifySchemaSubentry(org.apache.directory.server.core.interceptor.context.ModifyOperationContext, org.apache.directory.server.core.entry.ServerEntry, org.apache.directory.server.core.entry.ServerEntry, boolean)
     */
    public void modifySchemaSubentry( ModifyOperationContext opContext, boolean doCascadeModify ) throws Exception 
    {
        for ( Modification mod : opContext.getModItems() )
        {
            String opAttrOid = schemaManager.getAttributeTypeRegistry().getOidByName( mod.getAttribute().getId() );
            
            EntryAttribute serverAttribute = mod.getAttribute();

            switch ( mod.getOperation() )
            {
                case ADD_ATTRIBUTE :
                    modifyAddOperation( opContext, opAttrOid, serverAttribute, doCascadeModify );
                    break;
                    
                case REMOVE_ATTRIBUTE :
                    modifyRemoveOperation( opContext, opAttrOid, serverAttribute, doCascadeModify );
                    break; 
                    
                case REPLACE_ATTRIBUTE :
                    throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, 
                        I18n.err( I18n.ERR_283 ) );
                
                default:
                    throw new IllegalStateException( I18n.err( I18n.ERR_284, mod.getOperation() ) );
            }
        }
    }
    
    
    /* (non-Javadoc)
     * @see org.apache.directory.server.core.schema.SchemaChangeManager#modifySchemaSubentry(org.apache.directory.server.core.interceptor.context.ModifyOperationContext, org.apache.directory.shared.ldap.name.DN, int, org.apache.directory.server.core.entry.ServerEntry, org.apache.directory.server.core.entry.ServerEntry, org.apache.directory.server.core.entry.ServerEntry, boolean)
     */
    public void modifySchemaSubentry( ModifyOperationContext opContext, DN name, int modOp, ServerEntry mods, 
        ServerEntry subentry, ServerEntry targetSubentry, boolean doCascadeModify ) throws Exception
    {
        Set<AttributeType> attributeTypes = mods.getAttributeTypes();
        
        switch ( modOp )
        {
            case( DirContext.ADD_ATTRIBUTE ):
                for ( AttributeType attributeType:attributeTypes )
                {
                    modifyAddOperation( opContext, attributeType.getOid(), 
                        mods.get( attributeType ), doCascadeModify );
                }
            
                break;
                
            case( DirContext.REMOVE_ATTRIBUTE ):
                for ( AttributeType attributeType:attributeTypes )
                {
                    modifyRemoveOperation( opContext, attributeType.getOid(), 
                        mods.get( attributeType ), doCascadeModify );
                }
            
                break;
                
            case( DirContext.REPLACE_ATTRIBUTE ):
                throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, 
                    I18n.err( I18n.ERR_283 ) );
            
            default:
                throw new IllegalStateException( I18n.err( I18n.ERR_284, modOp ) );
        }
    }
    

    /**
     * Handles the modify remove operation on the subschemaSubentry for schema entities. 
     * 
     * @param opAttrOid the numeric id of the operational attribute modified
     * @param mods the attribute with the modifications
     * @param doCascadeModify determines if a cascading operation should be performed
     * to effect all dependents on the changed entity
     * @throws Exception if there are problems updating the registries and the
     * schema partition
     */
    private void modifyRemoveOperation( ModifyOperationContext opContext, String opAttrOid, 
        EntryAttribute mods, boolean doCascadeModify ) throws Exception
    {
        int index = opAttr2handlerIndex.get( opAttrOid );
        
        switch( index )
        {
            case( COMPARATOR_INDEX ):
                LdapComparatorDescription[] comparatorDescriptions = parsers.parseComparators( mods );
                
                for ( LdapComparatorDescription comparatorDescription : comparatorDescriptions )
                {
                    subentryModifier.delete( opContext, comparatorDescription );
                }
                break;
            case( NORMALIZER_INDEX ):
                NormalizerDescription[] normalizerDescriptions = parsers.parseNormalizers( mods );
                
                for ( NormalizerDescription normalizerDescription : normalizerDescriptions )
                {
                    subentryModifier.delete( opContext, normalizerDescription );
                }
                break;
            case( SYNTAX_CHECKER_INDEX ):
                SyntaxCheckerDescription[] syntaxCheckerDescriptions = parsers.parseSyntaxCheckers( mods );
                
                for ( SyntaxCheckerDescription syntaxCheckerDescription : syntaxCheckerDescriptions )
                {
                    subentryModifier.delete( opContext, syntaxCheckerDescription );
                }
                break;
            case( SYNTAX_INDEX ):
                LdapSyntax[] syntaxes = parsers.parseLdapSyntaxes( mods );
                
                for ( LdapSyntax syntax : syntaxes )
                {
                    subentryModifier.deleteSchemaObject( opContext, syntax );
                }
                break;
            case( MATCHING_RULE_INDEX ):
                MatchingRule[] mrs = parsers.parseMatchingRules( mods );
                
                for ( MatchingRule mr : mrs )
                {
                    subentryModifier.deleteSchemaObject( opContext, mr );
                }
                break;
            case( ATTRIBUTE_TYPE_INDEX ):
                AttributeType[] ats = parsers.parseAttributeTypes( mods );
                
                for ( AttributeType at : ats )
                {
                    subentryModifier.deleteSchemaObject( opContext, at );
                }
                break;
            case( OBJECT_CLASS_INDEX ):
                ObjectClass[] ocs = parsers.parseObjectClasses( mods );

                for ( ObjectClass oc : ocs )
                {
                    subentryModifier.deleteSchemaObject( opContext, oc );
                }
                break;
            case( MATCHING_RULE_USE_INDEX ):
                MatchingRuleUse[] mrus = parsers.parseMatchingRuleUses( mods );
                
                for ( MatchingRuleUse mru : mrus )
                {
                    subentryModifier.deleteSchemaObject( opContext, mru );
                }
                break;
            case( DIT_STRUCTURE_RULE_INDEX ):
                DITStructureRule[] dsrs = parsers.parseDitStructureRules( mods );
                
                for ( DITStructureRule dsr : dsrs )
                {
                    subentryModifier.deleteSchemaObject( opContext, dsr );
                }
                break;
            case( DIT_CONTENT_RULE_INDEX ):
                DITContentRule[] dcrs = parsers.parseDitContentRules( mods );
                
                for ( DITContentRule dcr : dcrs )
                {
                    subentryModifier.deleteSchemaObject( opContext, dcr );
                }
                break;
            case( NAME_FORM_INDEX ):
                NameForm[] nfs = parsers.parseNameForms( mods );
                
                for ( NameForm nf : nfs )
                {
                    subentryModifier.deleteSchemaObject( opContext, nf );
                }
                break;
            default:
                throw new IllegalStateException( I18n.err( I18n.ERR_285, index ) );
        }
    }
    
    
    /**
     * Handles the modify add operation on the subschemaSubentry for schema entities. 
     * 
     * @param opAttrOid the numeric id of the operational attribute modified
     * @param mods the attribute with the modifications
     * @param doCascadeModify determines if a cascading operation should be performed
     * to effect all dependents on the changed entity
     * @throws Exception if there are problems updating the registries and the
     * schema partition
     */
    private void modifyAddOperation( ModifyOperationContext opContext, String opAttrOid, 
        EntryAttribute mods, boolean doCascadeModify ) throws Exception
    {
        if ( doCascadeModify )
        {
            LOG.error( CASCADING_ERROR );
        }

        int index = opAttr2handlerIndex.get( opAttrOid );
        
        switch( index )
        {
            case( COMPARATOR_INDEX ):
                LdapComparatorDescription[] comparatorDescriptions = parsers.parseComparators( mods );
                
                for ( LdapComparatorDescription comparatorDescription : comparatorDescriptions )
                {
                    subentryModifier.add( opContext, comparatorDescription );
                }
                
                break;
                
            case( NORMALIZER_INDEX ):
                NormalizerDescription[] normalizerDescriptions = parsers.parseNormalizers( mods );
                
                for ( NormalizerDescription normalizerDescription : normalizerDescriptions )
                {
                    subentryModifier.add( opContext, normalizerDescription );
                }
                
                break;
                
            case( SYNTAX_CHECKER_INDEX ):
                SyntaxCheckerDescription[] syntaxCheckerDescriptions = parsers.parseSyntaxCheckers( mods );
                
                for ( SyntaxCheckerDescription syntaxCheckerDescription : syntaxCheckerDescriptions )
                {
                    subentryModifier.add( opContext, syntaxCheckerDescription );
                }
                
                break;
                
            case( SYNTAX_INDEX ):
                LdapSyntax[] syntaxes = parsers.parseLdapSyntaxes( mods );
                
                for ( LdapSyntax syntax : syntaxes )
                {
                    subentryModifier.addSchemaObject( opContext, syntax );
                }
                
                break;
                
            case( MATCHING_RULE_INDEX ):
                MatchingRule[] mrs = parsers.parseMatchingRules( mods );
                
                for ( MatchingRule mr : mrs )
                {
                    subentryModifier.addSchemaObject( opContext, mr );
                }
                
                break;
                
            case( ATTRIBUTE_TYPE_INDEX ):
                AttributeType[] ats = parsers.parseAttributeTypes( mods );
                
                for ( AttributeType at : ats )
                {
                    subentryModifier.addSchemaObject( opContext, at );
                }
                
                break;
                
            case( OBJECT_CLASS_INDEX ):
                ObjectClass[] ocs = parsers.parseObjectClasses( mods );

                for ( ObjectClass oc : ocs )
                {
                    subentryModifier.addSchemaObject( opContext, oc );
                }
                
                break;
                
            case( MATCHING_RULE_USE_INDEX ):
                MatchingRuleUse[] mrus = parsers.parseMatchingRuleUses( mods );
                
                for ( MatchingRuleUse mru : mrus )
                {
                    subentryModifier.addSchemaObject( opContext, mru );
                }
                
                break;
                
            case( DIT_STRUCTURE_RULE_INDEX ):
                DITStructureRule[] dsrs = parsers.parseDitStructureRules( mods );
                
                for ( DITStructureRule dsr : dsrs )
                {
                    subentryModifier.addSchemaObject( opContext, dsr );
                }
                
                break;
                
            case( DIT_CONTENT_RULE_INDEX ):
                DITContentRule[] dcrs = parsers.parseDitContentRules( mods );
                
                for ( DITContentRule dcr : dcrs )
                {
                    subentryModifier.addSchemaObject( opContext, dcr );
                }
                
                break;
                
            case( NAME_FORM_INDEX ):
                NameForm[] nfs = parsers.parseNameForms( mods );
                
                for ( NameForm nf : nfs )
                {
                    subentryModifier.addSchemaObject( opContext, nf );
                }
                
                break;
                
            default:
                throw new IllegalStateException( I18n.err( I18n.ERR_285, index ) );
        }
    }
}
