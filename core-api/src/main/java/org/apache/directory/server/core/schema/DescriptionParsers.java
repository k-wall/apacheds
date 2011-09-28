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
package org.apache.directory.server.core.schema;


import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.constants.MetaSchemaConstants;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.LdapInvalidAttributeValueException;
import org.apache.directory.shared.ldap.exception.LdapUnwillingToPerformException;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.DITContentRule;
import org.apache.directory.shared.ldap.schema.DITStructureRule;
import org.apache.directory.shared.ldap.schema.LdapSyntax;
import org.apache.directory.shared.ldap.schema.MatchingRule;
import org.apache.directory.shared.ldap.schema.MatchingRuleUse;
import org.apache.directory.shared.ldap.schema.NameForm;
import org.apache.directory.shared.ldap.schema.ObjectClass;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.parsers.AttributeTypeDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.parsers.DITContentRuleDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.parsers.DITStructureRuleDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.parsers.LdapComparatorDescription;
import org.apache.directory.shared.ldap.schema.parsers.LdapComparatorDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.parsers.LdapSyntaxDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.parsers.MatchingRuleDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.parsers.MatchingRuleUseDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.parsers.NameFormDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.parsers.NormalizerDescription;
import org.apache.directory.shared.ldap.schema.parsers.NormalizerDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.parsers.ObjectClassDescriptionSchemaParser;
import org.apache.directory.shared.ldap.schema.parsers.SyntaxCheckerDescription;
import org.apache.directory.shared.ldap.schema.parsers.SyntaxCheckerDescriptionSchemaParser;


/**
 * Parses descriptions using a number of different parsers for schema descriptions.
 * Also checks to make sure some things are valid as it's parsing paramters of
 * certain entity types.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class DescriptionParsers
{
    /** Empty arrays of SchemaOjects */
    private static final LdapComparatorDescription[] EMPTY_COMPARATORS         = new LdapComparatorDescription[0];
    private static final NormalizerDescription[]     EMPTY_NORMALIZERS         = new NormalizerDescription[0];
    private static final SyntaxCheckerDescription[]  EMPTY_SYNTAX_CHECKERS     = new SyntaxCheckerDescription[0];
    private static final LdapSyntax[]                EMPTY_SYNTAXES            = new LdapSyntax[0];
    private static final MatchingRule[]              EMPTY_MATCHING_RULES      = new MatchingRule[0];
    private static final AttributeType[]             EMPTY_ATTRIBUTE_TYPES     = new AttributeType[0];
    private static final ObjectClass[]               EMPTY_OBJECT_CLASSES      = new ObjectClass[0];
    private static final MatchingRuleUse[]           EMPTY_MATCHING_RULE_USES  = new MatchingRuleUse[0];
    private static final DITStructureRule[]          EMPTY_DIT_STRUCTURE_RULES = new DITStructureRule[0];
    private static final DITContentRule[]            EMPTY_DIT_CONTENT_RULES   = new DITContentRule[0];
    private static final NameForm[]                  EMPTY_NAME_FORMS          = new NameForm[0];

    /** The SchemaObject description's parsers */
    private final LdapComparatorDescriptionSchemaParser   comparatorParser      = new LdapComparatorDescriptionSchemaParser();
    private final NormalizerDescriptionSchemaParser       normalizerParser       = new NormalizerDescriptionSchemaParser();
    private final SyntaxCheckerDescriptionSchemaParser    syntaxCheckerParser    = new SyntaxCheckerDescriptionSchemaParser();
    private final LdapSyntaxDescriptionSchemaParser       syntaxParser           = new LdapSyntaxDescriptionSchemaParser();
    private final MatchingRuleDescriptionSchemaParser     matchingRuleParser     = new MatchingRuleDescriptionSchemaParser();
    private final AttributeTypeDescriptionSchemaParser    attributeTypeParser    = new AttributeTypeDescriptionSchemaParser();
    private final ObjectClassDescriptionSchemaParser      objectClassParser      = new ObjectClassDescriptionSchemaParser();
    private final MatchingRuleUseDescriptionSchemaParser  matchingRuleUseParser  = new MatchingRuleUseDescriptionSchemaParser();
    private final DITStructureRuleDescriptionSchemaParser ditStructureRuleParser = new DITStructureRuleDescriptionSchemaParser();
    private final DITContentRuleDescriptionSchemaParser   ditContentRuleParser   = new DITContentRuleDescriptionSchemaParser();
    private final NameFormDescriptionSchemaParser         nameFormParser         = new NameFormDescriptionSchemaParser();

    /** The SchemaManager instance */
    private final SchemaManager schemaManager;

    /**
     * Creates a description parser.
     * 
     * @param globalRegistries the registries to use while creating new schema entities
     */
    public DescriptionParsers( SchemaManager schemaManager )
    {
        this.schemaManager = schemaManager;
    }


    /**
     * Parse the SyntaxCheckers description
     *
     * @param attr The attribute containing the SC description
     * @return The array of SyntaxCheckerDescription instances
     * @throws LdapInvalidAttributeValueException If something went wrong
     */
    public SyntaxCheckerDescription[] parseSyntaxCheckers( EntryAttribute attr ) throws LdapInvalidAttributeValueException
    {
        if ( ( attr == null ) || ( attr.size() == 0 ) )
        {
            return EMPTY_SYNTAX_CHECKERS;
        }

        SyntaxCheckerDescription[] syntaxCheckerDescriptions = new SyntaxCheckerDescription[attr.size()];

        int pos = 0;

        for ( Value<?> value : attr )
        {
            try
            {
                syntaxCheckerDescriptions[pos++] = syntaxCheckerParser
                    .parseSyntaxCheckerDescription( value.getString() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, I18n.err( I18n.ERR_405, 
                        value ) );
                iave.initCause( e );
                throw iave;
            }
        }

        return syntaxCheckerDescriptions;
    }


    public NormalizerDescription[] parseNormalizers( EntryAttribute attr ) throws LdapInvalidAttributeValueException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_NORMALIZERS;
        }

        NormalizerDescription[] normalizerDescriptions = new NormalizerDescription[attr.size()];

        int pos = 0;

        for ( Value<?> value : attr )
        {
            try
            {
                normalizerDescriptions[pos++] = normalizerParser.parseNormalizerDescription( value.getString() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, I18n.err( I18n.ERR_406, 
                        value.getString() ) );
                iave.initCause( e );
                throw iave;
            }
        }

        return normalizerDescriptions;
    }


    public LdapComparatorDescription[] parseComparators( EntryAttribute attr ) throws LdapInvalidAttributeValueException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_COMPARATORS;
        }

        LdapComparatorDescription[] comparatorDescriptions = new LdapComparatorDescription[attr.size()];

        int pos = 0;

        for ( Value<?> value : attr )
        {
            try
            {
                comparatorDescriptions[pos++] = comparatorParser.parseComparatorDescription( value.getString() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, I18n.err( I18n.ERR_407,
                        value.getString() ) );
                iave.initCause( e );
                throw iave;
            }
        }

        return comparatorDescriptions;
    }


    /**
     * Parses a set of attributeTypeDescriptions held within an attribute into 
     * schema entities.
     * 
     * @param attr the attribute containing attributeTypeDescriptions
     * @return the set of attributeType objects for the descriptions 
     * @throws LdapException if there are problems parsing the descriptions
     */
    public AttributeType[] parseAttributeTypes( EntryAttribute attr ) throws LdapException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_ATTRIBUTE_TYPES;
        }

        AttributeType[] attributeTypes = new AttributeType[attr.size()];

        int pos = 0;

        for ( Value<?> value : attr )
        {
            AttributeType attributeType = null;

            try
            {
                attributeType = attributeTypeParser.parseAttributeTypeDescription( value.getString() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, I18n.err( I18n.ERR_408,
                        value.getString() ) );
                iave.initCause( e );
                throw iave;
            }

            // if the supertype is provided make sure it exists in some schema
            if ( ( attributeType.getSuperiorOid() != null ) && !schemaManager.getAttributeTypeRegistry().contains( attributeType.getSuperiorOid() ) )
            {
                throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM,
                    I18n.err( I18n.ERR_409, attributeType.getSuperiorOid() ) );
            }

            // if the syntax is provided by the description make sure it exists in some schema
            if ( attributeType.getSyntaxOid() != null && !schemaManager.getLdapSyntaxRegistry().contains( attributeType.getSyntaxOid() ) )
            {
                throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM,
                    I18n.err( I18n.ERR_410, attributeType.getSyntaxOid() ) );
            }

            // if the matchingRule is provided make sure it exists in some schema
            if ( attributeType.getEqualityOid() != null && !schemaManager.getMatchingRuleRegistry().contains( attributeType.getEqualityOid() ) )
            {
                throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM,
                    I18n.err( I18n.ERR_411, attributeType.getEqualityOid() ) );
            }

            // if the matchingRule is provided make sure it exists in some schema
            if ( attributeType.getOrderingOid() != null && !schemaManager.getMatchingRuleRegistry().contains( attributeType.getOrderingOid() ) )
            {
                throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM,
                    I18n.err( I18n.ERR_412, attributeType.getOrderingOid() ) );
            }

            // if the matchingRule is provided make sure it exists in some schema
            if ( attributeType.getSubstringOid() != null && !schemaManager.getMatchingRuleRegistry().contains( attributeType.getSubstringOid() ) )
            {
                throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM,
                    I18n.err( I18n.ERR_413, attributeType.getSubstringOid() ) );
            }

            // if the equality matching rule is null and no super type is specified then there is
            // definitely no equality matchingRule that can be resolved.  We cannot use an attribute
            // without a matchingRule for search or for building indices not to mention lookups.
            if ( attributeType.getEqualityOid() == null )
            {
                if ( attributeType.getSuperiorOid() == null )
                {
                    throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, I18n.err( I18n.ERR_414 ) );
                }
                else
                {
                    AttributeType superType = schemaManager
                        .lookupAttributeTypeRegistry( attributeType.getSuperiorOid() );

                    if ( superType == null )
                    {
                        throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, I18n.err( I18n.ERR_415 ) );
                    }
                }
            }

            // a syntax is mandatory for an attributeType and if not provided by the description 
            // must be provided from some ancestor in the attributeType hierarchy; without either
            // of these the description definitely cannot resolve a syntax and cannot be allowed.
            // if a supertype exists then it must resolve a proper syntax somewhere in the hierarchy.
            if ( attributeType.getSyntaxOid() == null && attributeType.getSuperiorOid() == null )
            {
                throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, I18n.err( I18n.ERR_416 ) );
            }

            List<Throwable> errors = new ArrayList<Throwable>();

            attributeType.setRegistries( schemaManager.getRegistries() );

            /*
            // Inject the schema
            
            if ( ( attributeType.getExtensions() == null )
                || ( attributeType.getExtensions().get( MetaSchemaConstants.X_SCHEMA ) == null ) )
            {
                throw new LdapUnwillingToPerformException(
                    "Cannot permit the addition of an attributeType not associated with a schema ",
                    ResultCodeEnum.UNWILLING_TO_PERFORM );
            }

            String schemaName = attributeType.getExtensions().get( MetaSchemaConstants.X_SCHEMA ).get( 0 );
            attributeType.setSchemaName( schemaName );
            */

            attributeTypes[pos++] = attributeType;
        }

        return attributeTypes;
    }


    /**
     * Parses a set of objectClassDescriptions held within an attribute into 
     * schema entities.
     * 
     * @param attr the attribute containing objectClassDescriptions
     * @return the set of objectClass objects for the descriptions 
     * @throws LdapException if there are problems parsing the descriptions
     */
    public ObjectClass[] parseObjectClasses( EntryAttribute attr ) throws LdapException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_OBJECT_CLASSES;
        }

        ObjectClass[] objectClasses = new ObjectClass[attr.size()];

        int pos = 0;

        for ( Value<?> value : attr )
        {
            ObjectClass objectClass = null;

            try
            {
                objectClass = objectClassParser.parseObjectClassDescription( value.getString() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, I18n.err( I18n.ERR_417, 
                        value.getString() ) );
                iave.initCause( e );
                throw iave;
            }

            // if the super objectClasses are provided make sure it exists in some schema
            if ( objectClass.getSuperiorOids() != null && objectClass.getSuperiorOids().size() > 0 )
            {
                for ( String superiorOid : objectClass.getSuperiorOids() )
                {
                    if ( superiorOid.equals( SchemaConstants.TOP_OC_OID )
                        || superiorOid.equalsIgnoreCase( SchemaConstants.TOP_OC ) )
                    {
                        continue;
                    }

                    if ( !schemaManager.getObjectClassRegistry().contains( superiorOid ) )
                    {
                        throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM,
                            I18n.err( I18n.ERR_418, superiorOid ) );
                    }
                }
            }

            // if the may list is provided make sure attributes exists in some schema
            if ( objectClass.getMayAttributeTypeOids() != null && objectClass.getMayAttributeTypeOids().size() > 0 )
            {
                for ( String mayAttrOid : objectClass.getMayAttributeTypeOids() )
                {
                    if ( !schemaManager.getAttributeTypeRegistry().contains( mayAttrOid ) )
                    {
                        throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM,
                            I18n.err( I18n.ERR_419, mayAttrOid ) );
                    }
                }
            }

            // if the must list is provided make sure attributes exists in some schema
            if ( objectClass.getMustAttributeTypeOids() != null && objectClass.getMustAttributeTypeOids().size() > 0 )
            {
                for ( String mustAttrOid : objectClass.getMustAttributeTypeOids() )
                {
                    if ( !schemaManager.getAttributeTypeRegistry().contains( mustAttrOid ) )
                    {
                        throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM,
                            I18n.err( I18n.ERR_420, mustAttrOid ) );
                    }
                }
            }

            List<Throwable> errors = new ArrayList<Throwable>();
            objectClass.setRegistries( schemaManager.getRegistries() );

            objectClasses[pos++] = objectClass;
        }

        return objectClasses;
    }


    /**
     * Parses a set of matchingRuleUseDescriptions held within an attribute into 
     * schema entities.
     * 
     * @param attr the attribute containing matchingRuleUseDescriptions
     * @return the set of matchingRuleUse objects for the descriptions 
     * @throws LdapException if there are problems parsing the descriptions
     */
    public MatchingRuleUse[] parseMatchingRuleUses( EntryAttribute attr ) throws LdapException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_MATCHING_RULE_USES;
        }

        MatchingRuleUse[] matchingRuleUses = new MatchingRuleUse[attr.size()];

        int pos = 0;

        for ( Value<?> value : attr )
        {
            MatchingRuleUse matchingRuleUse = null;

            try
            {
                matchingRuleUse = matchingRuleUseParser.parseMatchingRuleUseDescription( value.getString() );
                matchingRuleUse.setSpecification( value.getString() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, I18n.err( I18n.ERR_421, 
                        value.getString() ) );
                iave.initCause( e );
                throw iave;
            }

            matchingRuleUses[pos++] = matchingRuleUse;
        }

        return matchingRuleUses;
    }


    /**
     * Parses a set of ldapSyntaxes held within an attribute into 
     * schema entities.
     * 
     * @param attr the attribute containing ldapSyntaxes
     * @return the set of Syntax objects for the descriptions 
     * @throws LdapException if there are problems parsing the descriptions
     */
    public LdapSyntax[] parseLdapSyntaxes( EntryAttribute attr ) throws LdapException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_SYNTAXES;
        }

        LdapSyntax[] syntaxes = new LdapSyntax[attr.size()];

        int pos = 0;

        for ( Value<?> value : attr )
        {
            LdapSyntax ldapSyntax = null;

            try
            {
                ldapSyntax = syntaxParser.parseLdapSyntaxDescription( value.getString() );
                ldapSyntax.setSpecification( value.getString() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, I18n.err( I18n.ERR_422, 
                        value.getString() ) );
                iave.initCause( e );
                throw iave;
            }

            if ( !schemaManager.getSyntaxCheckerRegistry().contains( ldapSyntax.getOid() ) )
            {
                throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM, I18n.err( I18n.ERR_423 ) );
            }

            ldapSyntax.setHumanReadable( isHumanReadable( ldapSyntax ) );
            syntaxes[pos++] = ldapSyntax;
        }

        return syntaxes;
    }


    /**
     * Parses a set of matchingRuleDescriptions held within an attribute into 
     * schema entities.
     * 
     * @param attr the attribute containing matchingRuleDescriptions
     * @return the set of matchingRule objects for the descriptions 
     * @throws LdapException if there are problems parsing the descriptions
     */
    public MatchingRule[] parseMatchingRules( EntryAttribute attr ) throws LdapException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_MATCHING_RULES;
        }

        MatchingRule[] matchingRules = new MatchingRule[attr.size()];

        int pos = 0;

        for ( Value<?> value : attr )
        {
            MatchingRule matchingRule = null;

            try
            {
                matchingRule = matchingRuleParser.parseMatchingRuleDescription( value.getString() );
                matchingRule.setSpecification( value.getString() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, I18n.err( I18n.ERR_424, 
                        value.getString() ) );
                iave.initCause( e );
                throw iave;
            }

            if ( !schemaManager.getLdapSyntaxRegistry().contains( matchingRule.getSyntaxOid() ) )
            {
                throw new LdapUnwillingToPerformException( ResultCodeEnum.UNWILLING_TO_PERFORM,
                    I18n.err( I18n.ERR_425, matchingRule.getSyntaxOid() ) );
            }

            matchingRules[pos++] = matchingRule;
        }

        return matchingRules;
    }


    /**
     * Parses a set of dITStructureRuleDescriptions held within an attribute into 
     * schema entities.
     * 
     * @param attr the attribute containing dITStructureRuleDescriptions
     * @return the set of DITStructureRule objects for the descriptions 
     * @throws LdapException if there are problems parsing the descriptions
     */
    public DITStructureRule[] parseDitStructureRules( EntryAttribute attr ) throws LdapException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_DIT_STRUCTURE_RULES;
        }

        DITStructureRule[] ditStructureRules = new DITStructureRule[attr.size()];

        int pos = 0;

        for ( Value<?> value : attr )
        {
            DITStructureRule ditStructureRule = null;

            try
            {
                ditStructureRule = ditStructureRuleParser.parseDITStructureRuleDescription( value.getString() );
                ditStructureRule.setSpecification( value.getString() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, I18n.err( I18n.ERR_426, 
                        value.getString() ) );
                iave.initCause( e );
                throw iave;
            }

            ditStructureRules[pos++] = ditStructureRule;
        }

        return ditStructureRules;
    }


    /**
     * Parses a set of dITContentRuleDescriptions held within an attribute into 
     * schema entities.
     * 
     * @param attr the attribute containing dITContentRuleDescriptions
     * @return the set of DITContentRule objects for the descriptions 
     * @throws LdapException if there are problems parsing the descriptions
     */
    public DITContentRule[] parseDitContentRules( EntryAttribute attr ) throws LdapException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_DIT_CONTENT_RULES;
        }

        DITContentRule[] ditContentRules = new DITContentRule[attr.size()];

        int pos = 0;

        for ( Value<?> value : attr )
        {
            DITContentRule ditContentRule = null;

            try
            {
                ditContentRule = ditContentRuleParser.parseDITContentRuleDescription( value.getString() );
                ditContentRule.setSpecification( value.getString() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, I18n.err( I18n.ERR_427, 
                        value.getString() ) );
                iave.initCause( e );
                throw iave;
            }

            ditContentRules[pos++] = ditContentRule;
        }

        return ditContentRules;
    }


    /**
     * Parses a set of nameFormDescriptions held within an attribute into 
     * schema entities.
     * 
     * @param attr the attribute containing nameFormDescriptions
     * @return the set of NameFormRule objects for the descriptions 
     * @throws LdapException if there are problems parsing the descriptions
     */
    public NameForm[] parseNameForms( EntryAttribute attr ) throws LdapException
    {
        if ( attr == null || attr.size() == 0 )
        {
            return EMPTY_NAME_FORMS;
        }

        NameForm[] nameForms = new NameForm[attr.size()];

        int pos = 0;

        for ( Value<?> value : attr )
        {
            NameForm nameForm = null;

            try
            {
                nameForm = nameFormParser.parseNameFormDescription( value.getString() );
                nameForm.setSpecification( value.getString() );
            }
            catch ( ParseException e )
            {
                LdapInvalidAttributeValueException iave = new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, I18n.err( I18n.ERR_428, 
                        value.getString() ) );
                iave.initCause( e );
                throw iave;
            }

            nameForms[pos++] = nameForm;
        }

        return nameForms;
    }


    /**
     * Checks to see if the syntax description is human readable by checking 
     * for the presence of the X-IS-HUMAN_READABLE schema extension.
     * 
     * @param desc the ldapSyntax 
     * @return true if the syntax is human readable, false otherwise
     */
    private boolean isHumanReadable( LdapSyntax ldapSyntax )
    {
        List<String> values = ldapSyntax.getExtensions().get( MetaSchemaConstants.X_IS_HUMAN_READABLE );

        if ( values == null || values.size() == 0 )
        {
            return false;
        }
        else
        {
            String value = values.get( 0 );
            if ( value.equals( "TRUE" ) )
            {
                return true;
            }
            else
            {
                return false;
            }
        }
    }
}
