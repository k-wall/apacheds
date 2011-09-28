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
package org.apache.directory.server.kerberos.shared.io.decoder;


import java.util.Enumeration;

import org.apache.directory.server.kerberos.shared.messages.value.KerberosTime;
import org.apache.directory.server.kerberos.shared.messages.value.LastRequest;
import org.apache.directory.server.kerberos.shared.messages.value.LastRequestEntry;
import org.apache.directory.server.kerberos.shared.messages.value.LastRequestType;
import org.apache.directory.shared.asn1.der.DEREncodable;
import org.apache.directory.shared.asn1.der.DERGeneralizedTime;
import org.apache.directory.shared.asn1.der.DERInteger;
import org.apache.directory.shared.asn1.der.DERSequence;
import org.apache.directory.shared.asn1.der.DERTaggedObject;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 589780 $, $Date: 2007-10-29 19:14:59 +0100 (Lun, 29 oct 2007) $
 */
public class LastRequestDecoder
{
    /**
     * LastReq ::=   SEQUENCE OF SEQUENCE {
     * lr-type[0]               INTEGER,
     * lr-value[1]              KerberosTime
     * }
     */
    protected static LastRequest decodeSequence( DERSequence sequence )
    {
        LastRequestEntry[] entries = new LastRequestEntry[sequence.size()];

        int ii = 0;
        for ( Enumeration<DEREncodable> e = sequence.getObjects(); e.hasMoreElements(); )
        {
            DERSequence object = ( DERSequence ) e.nextElement();
            LastRequestEntry entry = decode( object );
            entries[ii] = entry;
            ii++;
        }

        return new LastRequest( entries );
    }


    protected static LastRequestEntry decode( DERSequence sequence )
    {
        LastRequestType type = LastRequestType.NONE;
        KerberosTime value = null;

        for ( Enumeration<DEREncodable> e = sequence.getObjects(); e.hasMoreElements(); )
        {
            DERTaggedObject object = ( DERTaggedObject ) e.nextElement();
            int tag = object.getTagNo();
            DEREncodable derObject = object.getObject();

            switch ( tag )
            {
                case 0:
                    DERInteger tag0 = ( DERInteger ) derObject;
                    type = LastRequestType.getTypeByOrdinal( tag0.intValue() );
                    break;
                case 1:
                    DERGeneralizedTime tag1 = ( DERGeneralizedTime ) derObject;
                    value = KerberosTimeDecoder.decode( tag1 );
                    break;
            }
        }

        return new LastRequestEntry( type, value );
    }
}
