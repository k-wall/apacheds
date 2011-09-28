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

package org.apache.directory.server.dns.store.jndi;


import java.util.Set;

import org.apache.directory.server.dns.DnsException;
import org.apache.directory.server.dns.messages.QuestionRecord;
import org.apache.directory.server.dns.messages.ResourceRecord;


/**
 * Interface for search strategies.  The DNS protocol may search a single
 * base DN for resource records or use a catalog to lookup resource records
 * in multiple zones.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 549458 $, $Date: 2007-06-21 14:45:19 +0200 (Jeu, 21 jui 2007) $
 */
interface SearchStrategy
{
    /**
     * Returns a set of {@link ResourceRecord}s, given a DNS {@link QuestionRecord}.
     *
     * @param question
     * @return The set of {@link ResourceRecord}s.
     * @throws Exception
     */
    Set<ResourceRecord> getRecords( QuestionRecord question ) throws DnsException;
}
