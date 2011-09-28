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

import org.apache.directory.server.kerberos.shared.messages.value.HostAddress;
import org.apache.directory.server.kerberos.shared.messages.value.types.HostAddrType;
import org.apache.directory.server.kerberos.shared.messages.value.HostAddresses;
import org.apache.directory.shared.asn1.der.DEREncodable;
import org.apache.directory.shared.asn1.der.DERInteger;
import org.apache.directory.shared.asn1.der.DEROctetString;
import org.apache.directory.shared.asn1.der.DERSequence;
import org.apache.directory.shared.asn1.der.DERTaggedObject;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 587242 $, $Date: 2007-10-22 22:42:40 +0200 (Lun, 22 oct 2007) $
 */
public class HostAddressDecoder
{
    /**
     * HostAddress ::=     SEQUENCE  {
     *                     addr-type[0]             INTEGER,
     *                     address[1]               OCTET STRING
     * }
     */
    protected static HostAddress decode( DERSequence sequence )
    {
        HostAddrType type = HostAddrType.ADDRTYPE_INET;
        byte[] value = null;

        for ( Enumeration<DEREncodable> e = sequence.getObjects(); e.hasMoreElements(); )
        {
            DERTaggedObject object = ( DERTaggedObject ) e.nextElement();
            int tag = object.getTagNo();
            DEREncodable derObject = object.getObject();

            switch ( tag )
            {
                case 0:
                    DERInteger addressType = ( DERInteger ) derObject;
                    type = HostAddrType.getTypeByOrdinal( addressType.intValue() );
                    break;
                    
                case 1:
                    DEROctetString address = ( DEROctetString ) derObject;
                    value = address.getOctets();
                    break;
            }
        }

        return new HostAddress( type, value );
    }


    /**
     * HostAddresses ::=   SEQUENCE OF SEQUENCE {
     *                     addr-type[0]             INTEGER,
     *                     address[1]               OCTET STRING
     * }
     */
    protected static HostAddresses decodeSequence( DERSequence sequence )
    {
        HostAddress[] addresses = new HostAddress[sequence.size()];

        int ii = 0;
        
        for ( Enumeration<DEREncodable> e = sequence.getObjects(); e.hasMoreElements(); )
        {
            DERSequence object = ( DERSequence ) e.nextElement();
            HostAddress address = decode( object );
            addresses[ii] = address;
            ii++;
        }

        return new HostAddresses( addresses );
    }
}
