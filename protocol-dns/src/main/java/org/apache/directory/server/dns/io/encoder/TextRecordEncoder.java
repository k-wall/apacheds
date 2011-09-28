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

package org.apache.directory.server.dns.io.encoder;


import org.apache.directory.server.dns.messages.ResourceRecord;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.apache.mina.core.buffer.IoBuffer;


/**
 * 3.3.14. TXT RDATA format
 * 
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *     /                   TXT-DATA                    /
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * 
 * where:
 * 
 * TXT-DATA        One or more <character-string>s.
 * 
 * TXT RRs are used to hold descriptive text.  The semantics of the text
 * depends on the domain where it is found.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 725712 $, $Date: 2008-12-11 16:32:04 +0100 (Jeu, 11 déc 2008) $
 */
public class TextRecordEncoder extends ResourceRecordEncoder
{
    protected void putResourceRecordData( IoBuffer byteBuffer, ResourceRecord record )
    {
        putCharacterString( byteBuffer, record.get( DnsAttribute.CHARACTER_STRING ) );
    }
}
