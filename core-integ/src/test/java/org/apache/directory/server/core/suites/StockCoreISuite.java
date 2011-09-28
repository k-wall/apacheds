/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.server.core.suites;


import org.apache.directory.server.core.authz.AuthorizationServiceAsNonAdminIT;
import org.apache.directory.server.core.changelog.DefaultChangeLogIT;
import org.apache.directory.server.core.collective.CollectiveAttributeServiceIT;
import org.apache.directory.server.core.configuration.PartitionConfigurationIT;
import org.apache.directory.server.core.event.EventServiceIT;
import org.apache.directory.server.core.integ.FrameworkSuite;
import org.apache.directory.server.core.jndi.AddIT;
import org.apache.directory.server.core.jndi.CreateContextIT;
import org.apache.directory.server.core.jndi.DIRSERVER169IT;
import org.apache.directory.server.core.jndi.DIRSERVER783IT;
import org.apache.directory.server.core.jndi.DIRSERVER791IT;
import org.apache.directory.server.core.jndi.DestroyContextIT;
import org.apache.directory.server.core.jndi.ExtensibleObjectIT;
import org.apache.directory.server.core.jndi.ListIT;
import org.apache.directory.server.core.jndi.ModifyContextIT;
import org.apache.directory.server.core.jndi.ObjStateFactoryIT;
import org.apache.directory.server.core.jndi.RFC2713IT;
import org.apache.directory.server.core.jndi.ReferralIT;
import org.apache.directory.server.core.jndi.RootDSEIT;
import org.apache.directory.server.core.jndi.UniqueMemberIT;
import org.apache.directory.server.core.jndi.referral.AddReferralIT;
import org.apache.directory.server.core.jndi.referral.CompareReferralIT;
import org.apache.directory.server.core.jndi.referral.DeleteReferralIT;
import org.apache.directory.server.core.jndi.referral.ModifyReferralIT;
import org.apache.directory.server.core.jndi.referral.MoveAndRenameReferralIT;
import org.apache.directory.server.core.jndi.referral.MoveReferralIT;
import org.apache.directory.server.core.jndi.referral.RenameReferralIT;
import org.apache.directory.server.core.jndi.referral.SearchReferralIT;
import org.apache.directory.server.core.normalization.NormalizationServiceIT;
import org.apache.directory.server.core.operational.OperationalAttributeServiceIT;
import org.apache.directory.server.core.operations.bind.SimpleBindIT;
import org.apache.directory.server.core.operations.compare.CompareDirserver1139IT;
import org.apache.directory.server.core.operations.lookup.LookupIT;
import org.apache.directory.server.core.operations.modify.ModifyAddIT;
import org.apache.directory.server.core.operations.modify.ModifyDelIT;
import org.apache.directory.server.core.operations.modify.ModifyMultipleChangesIT;
import org.apache.directory.server.core.operations.search.DIRSERVER759IT;
import org.apache.directory.server.core.operations.search.SearchIT;
import org.apache.directory.server.core.partition.PartitionIT;
import org.apache.directory.server.core.prefs.PreferencesIT;
import org.apache.directory.server.core.schema.MetaAttributeTypeHandlerIT;
import org.apache.directory.server.core.schema.MetaComparatorHandlerIT;
import org.apache.directory.server.core.schema.MetaMatchingRuleHandlerIT;
import org.apache.directory.server.core.schema.MetaNormalizerHandlerIT;
import org.apache.directory.server.core.schema.MetaObjectClassHandlerIT;
import org.apache.directory.server.core.schema.MetaSchemaHandlerIT;
import org.apache.directory.server.core.schema.MetaSyntaxCheckerHandlerIT;
import org.apache.directory.server.core.schema.MetaSyntaxHandlerIT;
import org.apache.directory.server.core.schema.ObjectClassCreateIT;
import org.apache.directory.server.core.schema.SchemaPersistenceIT;
import org.apache.directory.server.core.schema.SchemaServiceIT;
import org.apache.directory.server.core.schema.SubschemaSubentryIT;
import org.apache.directory.server.core.sp.LdapClassLoaderIT;
import org.apache.directory.server.core.subtree.BadSubentryServiceIT;
import org.apache.directory.server.core.subtree.SubentryServiceEntryModificationHandlingIT;
import org.apache.directory.server.core.subtree.SubentryServiceIT;
import org.apache.directory.server.core.subtree.SubentryServiceObjectClassChangeHandlingIT;
import org.apache.directory.server.core.trigger.SubentryServiceForTriggersIT;
import org.apache.directory.server.core.trigger.TriggerInterceptorIT;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;


/**
 * Document me!
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
@RunWith ( FrameworkSuite.class )
@Suite.SuiteClasses ( {
        CollectiveAttributeServiceIT.class,
        EventServiceIT.class,
        AddIT.class,

        CreateContextIT.class,
        DestroyContextIT.class,
        
        DIRSERVER169IT.class,
        DIRSERVER759IT.class,
        DIRSERVER783IT.class,
        DIRSERVER791IT.class,
        
        ListIT.class,
        ObjStateFactoryIT.class,
        ExtensibleObjectIT.class,
        RFC2713IT.class,
        RootDSEIT.class,
        SearchIT.class,
        UniqueMemberIT.class,
        PreferencesIT.class,
        TriggerInterceptorIT.class,
        LdapClassLoaderIT.class,
        NormalizationServiceIT.class,
        DefaultChangeLogIT.class,        
        CompareDirserver1139IT.class,

        ModifyContextIT.class,
        ModifyDelIT.class,
        ModifyAddIT.class,
        ModifyMultipleChangesIT.class,
        
        LookupIT.class,
        SimpleBindIT.class,
        
        MoveAndRenameReferralIT.class,
        DeleteReferralIT.class,
        SearchReferralIT.class,
        CompareReferralIT.class,
        MoveReferralIT.class,
        ModifyReferralIT.class,
        RenameReferralIT.class,
        AddReferralIT.class,
        ReferralIT.class,
        
        AuthorizationServiceAsNonAdminIT.class,
        
        SchemaPersistenceIT.class,
        SubschemaSubentryIT.class,
        SubentryServiceForTriggersIT.class,
        BadSubentryServiceIT.class,
        SubentryServiceEntryModificationHandlingIT.class,
        SubentryServiceObjectClassChangeHandlingIT.class,
        SubentryServiceIT.class,

        MetaNormalizerHandlerIT.class,
        MetaSchemaHandlerIT.class,
        MetaMatchingRuleHandlerIT.class,
        MetaAttributeTypeHandlerIT.class,
        MetaSyntaxHandlerIT.class,
        MetaComparatorHandlerIT.class,
        MetaSyntaxCheckerHandlerIT.class,
        MetaObjectClassHandlerIT.class,
        
        ObjectClassCreateIT.class,

        SchemaServiceIT.class,
        
        PartitionIT.class,
        PartitionConfigurationIT.class,
        
        OperationalAttributeServiceIT.class
        } )
public class StockCoreISuite
{
}
